package com.gomoku;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EngineManager {

    private static final String TAG = "EngineManager";

    public static final int ENGINE_KATAGO = 0;
    public static final int ENGINE_RAPFI = 1;

    private static final long COMMAND_TIMEOUT_MS = 30000;
    private static final long START_TIMEOUT_MS = 180000;

    private final Context context;

    /*
     * 只有这个线程发送协议命令。
     * 输出读取在 Connection 的独立线程中完成。
     */
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final AtomicInteger commandIds =
            new AtomicInteger(1000);

    private final AtomicLong analysisIds =
            new AtomicLong(0);

    private volatile long engineGeneration = 0;
    private volatile boolean ready = false;

    private volatile int currentEngine = ENGINE_RAPFI;
    private volatile boolean isRenju = true;

    private volatile EngineCallback callback;
    private volatile Connection connection;

    private String usedRapfiBinary;

    public EngineManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public String prepareFiles() {
        try {
            File dir = context.getFilesDir();

            copyIfNeeded("katago", new File(dir, "katago"), true);
            copyIfNeeded(
                    "b24.int8.onnx",
                    new File(dir, "b24.int8.onnx"),
                    false);
            copyIfNeeded(
                    "libonnxruntime.so",
                    new File(dir, "libonnxruntime.so"),
                    false);
            copyIfNeeded(
                    "engine.cfg",
                    new File(dir, "engine.cfg"),
                    false);

            String rapfiName = "pbrain-rapfi";
            File rapfi = new File(dir, rapfiName);

            copyIfNeeded(rapfiName, rapfi, true);

            usedRapfiBinary =
                    rapfi.exists() && rapfi.canExecute()
                            ? rapfiName : null;

            String[] otherFiles = {
                    "config.toml",
                    "mix9svqfreestyle_bsmix.bin.lz4",
                    "mix9svqrenju_bs15_black.bin.lz4",
                    "mix9svqrenju_bs15_white.bin.lz4"
            };

            for (String name : otherFiles) {
                copyIfNeeded(name, new File(dir, name), false);
            }

            if (usedRapfiBinary == null) {
                return "找不到可用的 Rapfi 引擎";
            }

            return null;
        } catch (Exception e) {
            Log.e(TAG, "prepareFiles", e);
            return messageOf(e);
        }
    }

    private void copyIfNeeded(
            String assetName,
            File outputFile,
            boolean executable) throws Exception {

        if (!outputFile.exists() || outputFile.length() < 1000) {
            InputStream input = null;
            OutputStream output = null;

            try {
                input = context.getAssets().open(assetName);
                output = new FileOutputStream(outputFile);

                byte[] buffer = new byte[65536];
                int length;

                while ((length = input.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                }

                output.flush();
            } finally {
                closeQuietly(output);
                closeQuietly(input);
            }
        }

        if (executable) {
            outputFile.setExecutable(true, false);
        }
    }

    public synchronized void stopEngine() {
        engineGeneration++;
        analysisIds.incrementAndGet();

        ready = false;
        callback = null;

        Connection old = connection;
        connection = null;

        if (old != null) {
            old.close();
        }
    }

    /*
     * 热切换规则：进程不重启，只下发一条规则命令。
     * KataGo: kata-set-rule basicrule RENJU|FREESTYLE
     * Rapfi : INFO RULE 2|0
     * 命令在下一条命令到达时生效（KataGo 收到新 GTP 命令即停止旧搜索），
     * 因此返回后即可直接重新分析，无需重载模型。
     */
    public synchronized void changeRule(final boolean renju) {
        isRenju = renju;

        final Connection c = connection;

        if (c == null || !ready || c.closed) {
            return;
        }

        final boolean target = renju;

        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (!isCurrentConnection(c) || !ready) return;

                try {
                    if (c.engineType == ENGINE_KATAGO) {
                        String ruleName = target ? "RENJU" : "FREESTYLE";

                        requireSuccess(c.command(
                                "kata-set-rule basicrule " + ruleName,
                                COMMAND_TIMEOUT_MS));

                        GtpResponse rules = c.command(
                                "kata-get-rules",
                                COMMAND_TIMEOUT_MS);

                        if (rules.success
                                && !rules.text.toUpperCase(Locale.US)
                                        .contains(ruleName)) {
                            throw new Exception(
                                    "规则热切换未生效，当前规则："
                                            + rules.text);
                        }
                    } else {
                        /*
                         * Rapfi 在 thinking 期间会静默丢弃 INFO；先等上一轮
                         * BOARD 结束，再同步规则并用 RESTART 的 OK 作屏障。
                         */
                        if (!c.rapfiDone.await(
                                30, TimeUnit.SECONDS)) {
                            throw new Exception(
                                    "Rapfi 上一轮分析未结束，请重启引擎");
                        }
                        if (!isCurrentConnection(c)) return;
                        c.write("INFO RULE " + (target ? "2" : "0"));
                        c.writeRapfiAndWaitOk(
                                "RESTART", COMMAND_TIMEOUT_MS);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "changeRule", e);

                    if (isCurrentConnection(c) && !c.closed) {
                        ready = false;
                        c.close();
                        c.engineCallback.onError(
                                "切换规则失败：" + messageOf(e));
                    }
                }
            }
        });
    }

    public synchronized void startEngine(
            final int engineType,
            final boolean renju,
            final EngineCallback engineCallback) {

        stopEngine();

        currentEngine = engineType;
        isRenju = renju;
        callback = engineCallback;

        final long generation = engineGeneration;

        executor.execute(new Runnable() {
            @Override
            public void run() {
                Connection c = null;

                try {
                    if (!isCurrentEngine(generation, engineCallback)) {
                        return;
                    }

                    File dir = context.getFilesDir();
                    ProcessBuilder builder;

                    if (engineType == ENGINE_KATAGO) {
                        File engine = requireFile(dir, "katago");
                        File model = requireFile(dir, "b24.int8.onnx");
                        File config = requireFile(dir, "engine.cfg");
                        requireFile(dir, "libonnxruntime.so");

                        builder = new ProcessBuilder(
                                engine.getAbsolutePath(),
                                "gtp",
                                "-model",
                                model.getAbsolutePath(),
                                "-config",
                                config.getAbsolutePath());

                        String oldPath =
                                builder.environment().get("LD_LIBRARY_PATH");

                        builder.environment().put(
                                "LD_LIBRARY_PATH",
                                dir.getAbsolutePath()
                                        + (oldPath == null || oldPath.isEmpty()
                                        ? "" : ":" + oldPath));
                    } else {
                        if (usedRapfiBinary == null) {
                            throw new Exception("找不到可用的 Rapfi 引擎");
                        }

                        builder = new ProcessBuilder(
                                requireFile(dir, usedRapfiBinary)
                                        .getAbsolutePath());
                    }

                    builder.directory(dir);

                    /*
                     * stderr 单独读取，防止引擎日志混进 GTP 响应。
                     */
                    builder.redirectErrorStream(false);

                    Process process = builder.start();

                    c = new Connection(
                            process,
                            engineType,
                            generation,
                            engineCallback);

                    synchronized (EngineManager.this) {
                        if (!isCurrentEngine(generation, engineCallback)) {
                            c.close();
                            return;
                        }

                        connection = c;
                    }

                    c.startReaders();

                    if (engineType == ENGINE_KATAGO) {
                        requireSuccess(c.command(
                                "boardsize 15",
                                START_TIMEOUT_MS));

                        String ruleName = renju ? "RENJU" : "FREESTYLE";

                        requireSuccess(c.command(
                                "kata-set-rule basicrule " + ruleName,
                                COMMAND_TIMEOUT_MS));

                        GtpResponse rules = c.command(
                                "kata-get-rules",
                                COMMAND_TIMEOUT_MS);

                        if (rules.success) {
                            if (!rules.text.toUpperCase(Locale.US)
                                    .contains(ruleName)) {
                                throw new Exception(
                                        "规则设置未生效，当前规则：" + rules.text);
                            }
                        } else {
                            Log.w(
                                    TAG,
                                    "kata-get-rules 不可用，"
                                            + "采用已成功执行的规则设置命令");
                        }
                    } else {
                        c.write("START 15");

                        if (!c.rapfiReady.await(
                                15, TimeUnit.SECONDS)
                                || !c.rapfiOk
                                || c.closed) {
                            throw new Exception("Rapfi 启动失败或超时");
                        }

                        c.write("INFO RULE " + (renju ? "2" : "0"));
                        c.write("INFO TIMEOUT_TURN 8000");
                        c.writeRapfiAndWaitOk(
                                "RESTART", START_TIMEOUT_MS);
                    }

                    if (!isCurrentConnection(c)) {
                        c.close();
                        return;
                    }

                    ready = true;
                    engineCallback.onReady();

                } catch (Exception e) {
                    Log.e(TAG, "startEngine", e);

                    if (isCurrentEngine(generation, engineCallback)) {
                        ready = false;

                        if (c != null) {
                            c.close();
                        }

                        engineCallback.onError(
                                "启动失败：" + messageOf(e));
                    }
                }
            }
        });
    }

    /*
     * 保留原来的入口。
     * 正常棋谱包含停一手，因此行棋方按完整步数决定。
     */
    public long analyze(List<int[]> history) {
        boolean blackTurn = history == null || history.size() % 2 == 0;
        return analyze(history, blackTurn);
    }

    public long analyze(
            List<int[]> history,
            final boolean blackTurn) {

        final List<int[]> snapshot = new ArrayList<>();

        if (history != null) {
            for (int[] move : history) {
                if (move != null && move.length >= 3) {
                    snapshot.add(new int[]{
                            move[0], move[1], move[2]
                    });
                }
            }
        }

        final long requestId = analysisIds.incrementAndGet();
        final Connection c = connection;

        if (c == null || !ready) {
            return requestId;
        }

        /*
         * 立即关闭旧输出，不能等后台任务开始执行后才关闭。
         */
        c.outputAnalysisId = -1;

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isCurrentRequest(c, requestId)) {
                        return;
                    }

                    if (c.engineType == ENGINE_KATAGO) {
                        /*
                         * KataGo 收到下一条 GTP 命令会停止流式分析。
                         * name 是标准只读命令，用它等待旧分析彻底结束。
                         */
                        requireSuccess(c.command(
                                "name",
                                COMMAND_TIMEOUT_MS));

                        if (!isCurrentRequest(c, requestId)) return;

                        requireSuccess(c.command(
                                "clear_board",
                                COMMAND_TIMEOUT_MS));

                        /*
                         * 停一手是分析软件的换方操作：
                         * 棋谱保留，但不向引擎重放真实 pass。
                         */
                        for (int[] move : snapshot) {
                            if (!isCurrentRequest(c, requestId)) return;

                            if (move[0] < 0) {
                                continue;
                            }

                            validateStone(move);

                            String color = move[2] == 1 ? "B" : "W";

                            requireSuccess(c.command(
                                    "play " + color + " "
                                            + toGtpCoordinate(
                                                    move[0], move[1]),
                                    COMMAND_TIMEOUT_MS));
                        }

                        if (!isCurrentRequest(c, requestId)) return;

                        /*
                         * 不能让引擎根据最后一颗实际落子猜测下一方。
                         * 明确指定当前界面的行棋方。
                         */
                        c.beginKataAnalysis(
                                requestId,
                                "kata-analyze "
                                        + (blackTurn ? "B" : "W")
                                        + " 10");
                    } else {
                        /*
                         * Rapfi 分析属于一次性任务。
                         * 等待上一轮最终坐标输出，避免结果串到新请求。
                         */
                        if (!c.rapfiDone.await(
                                30, TimeUnit.SECONDS)) {
                            throw new Exception(
                                    "Rapfi 上一轮分析未结束，请重启引擎");
                        }

                        if (!isCurrentRequest(c, requestId)) return;

                        c.write("INFO RULE " + (isRenju ? "2" : "0"));
                        c.writeRapfiAndWaitOk(
                                "RESTART", COMMAND_TIMEOUT_MS);
                        if (!isCurrentRequest(c, requestId)) return;

                        /*
                         * Rapfi 用 BOARD 中第一颗非 WALL 子判断 SELF 的颜色。
                         * 识谱结果可能从白子开始；这里只调整送给 Rapfi 的
                         * 协议序列，不改识别结果、预览或应用内棋谱顺序。
                         */
                        List<int[]> rapfiHistory =
                                normalizeRapfiHistory(snapshot);

                        c.rapfiDone = new CountDownLatch(1);
                        c.outputAnalysisId = requestId;

                        c.write("BOARD");

                        for (int[] move : rapfiHistory) {
                            if (move[0] < 0) continue;

                            validateStone(move);

                            c.write(
                                    move[0] + ","
                                            + (14 - move[1]) + ","
                                            + move[2]);
                        }

                        c.write("DONE");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "analyze", e);

                    if (isCurrentRequest(c, requestId)) {
                        c.outputAnalysisId = -1;

                        /*
                         * 命令失败后不继续在不确定的局面上分析。
                         */
                        ready = false;
                        c.close();

                        c.engineCallback.onError(
                                "分析失败：" + messageOf(e)
                                        + "。请重新启动引擎");
                    }
                }
            }
        });

        return requestId;
    }

    /*
     * 立即使旧结果失效，然后异步停止 KataGo。
     */
    public void stopAnalysis() {
        analysisIds.incrementAndGet();

        final Connection c = connection;

        if (c == null) return;

        c.outputAnalysisId = -1;

        if (c.engineType != ENGINE_KATAGO || !ready) {
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (!isCurrentConnection(c) || !ready) return;

                try {
                    requireSuccess(c.command(
                            "name", COMMAND_TIMEOUT_MS));
                } catch (Exception e) {
                    if (isCurrentConnection(c) && !c.closed) {
                        Log.e(TAG, "stopAnalysis", e);
                        ready = false;
                        c.close();
                        c.engineCallback.onError(
                                "停止分析失败：" + messageOf(e));
                    }
                }
            }
        });
    }

    public void sendCommand(final String command) {
        if (command == null) return;

        if ("stop".equalsIgnoreCase(command.trim())) {
            stopAnalysis();
            return;
        }

        final Connection c = connection;

        if (c == null || !ready) return;

        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (!isCurrentConnection(c) || !ready) return;

                try {
                    if (c.engineType == ENGINE_KATAGO) {
                        requireSuccess(c.command(
                                command, COMMAND_TIMEOUT_MS));
                    } else {
                        c.write(command);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "sendCommand", e);

                    if (isCurrentConnection(c)) {
                        c.engineCallback.onError(
                                "命令执行失败：" + messageOf(e));
                    }
                }
            }
        });
    }

    private boolean isCurrentEngine(
            long generation,
            EngineCallback cb) {

        return generation == engineGeneration && callback == cb;
    }

    private boolean isCurrentConnection(Connection c) {
        return c != null
                && connection == c
                && isCurrentEngine(c.generation, c.engineCallback);
    }

    private boolean isCurrentRequest(Connection c, long requestId) {
        return ready
                && isCurrentConnection(c)
                && !c.closed
                && requestId == analysisIds.get();
    }

    private static File requireFile(File dir, String name)
            throws Exception {

        File file = new File(dir, name);

        if (!file.exists()) {
            throw new Exception("找不到文件 " + name);
        }

        return file;
    }

    private static void validateStone(int[] move) throws Exception {
        if (move[0] < 0 || move[0] >= 15
                || move[1] < 0 || move[1] >= 15
                || (move[2] != 1 && move[2] != 2)) {
            throw new Exception("棋谱中存在无效坐标或棋子颜色");
        }
    }

    private static List<int[]> normalizeRapfiHistory(
            List<int[]> history) {
        if (history == null || history.isEmpty()) {
            return history;
        }

        int firstStone = -1;
        int firstBlack = -1;

        for (int i = 0; i < history.size(); i++) {
            int[] move = history.get(i);
            if (move == null || move.length < 3
                    || move[0] < 0) {
                continue;
            }
            if (move[2] != 1 && move[2] != 2) {
                continue;
            }
            if (firstStone < 0) {
                firstStone = i;
            }
            if (firstBlack < 0 && move[2] == 1) {
                firstBlack = i;
            }
        }

        if (firstStone < 0 || firstBlack < 0
                || history.get(firstStone)[2] == 1) {
            return history;
        }

        List<int[]> normalized = new ArrayList<>(history.size());
        normalized.add(history.get(firstBlack));
        for (int i = 0; i < history.size(); i++) {
            if (i != firstBlack) {
                normalized.add(history.get(i));
            }
        }
        return normalized;
    }

    private static String toGtpCoordinate(int col, int row) {
        char letter = (char) ('A' + col);

        if (letter >= 'I') {
            letter++;
        }

        return String.valueOf(letter) + (15 - row);
    }

    private static void requireSuccess(GtpResponse response)
            throws Exception {

        if (!response.success) {
            throw new Exception(response.text);
        }
    }

    private static String messageOf(Exception e) {
        String message = e.getMessage();

        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;

        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private final class Connection {

        final Process process;
        final BufferedReader reader;
        final BufferedWriter writer;

        final int engineType;
        final long generation;
        final EngineCallback engineCallback;

        final Map<Integer, PendingResponse> pending =
                new ConcurrentHashMap<>();

        final CountDownLatch rapfiReady = new CountDownLatch(1);

        volatile CountDownLatch rapfiDone = new CountDownLatch(0);
        volatile CountDownLatch rapfiAck = new CountDownLatch(0);
        volatile boolean rapfiOk;
        volatile boolean closed;

        private boolean closeScheduled = false;

        volatile long outputAnalysisId = -1;
        volatile int analysisCommandId = -1;
        volatile long analysisCommandRequestId = -1;

        Connection(
                Process process,
                int engineType,
                long generation,
                EngineCallback engineCallback) throws Exception {

            this.process = process;
            this.engineType = engineType;
            this.generation = generation;
            this.engineCallback = engineCallback;

            reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream()));
        }

        void startReaders() {
            Thread stdout = new Thread(new Runnable() {
                @Override
                public void run() {
                    readOutput();
                }
            }, "Gomoku-engine-stdout");

            stdout.setDaemon(true);
            stdout.start();

            Thread stderr = new Thread(new Runnable() {
                @Override
                public void run() {
                    BufferedReader errors = null;

                    try {
                        errors = new BufferedReader(
                                new InputStreamReader(
                                        process.getErrorStream()));

                        String line;

                        while ((line = errors.readLine()) != null) {
                            Log.d(TAG, "stderr << " + line);
                        }
                    } catch (Exception ignored) {
                    } finally {
                        closeQuietly(errors);
                    }
                }
            }, "Gomoku-engine-stderr");

            stderr.setDaemon(true);
            stderr.start();
        }

        void write(String command) throws Exception {
            if (closed) {
                throw new Exception("引擎已关闭");
            }

            Log.d(TAG, ">> " + command);

            writer.write(command);
            writer.write("\n");
            writer.flush();
        }

        void writeRapfiAndWaitOk(String command, long timeout)
                throws Exception {
            CountDownLatch ack = new CountDownLatch(1);
            rapfiAck = ack;
            try {
                write(command);
                if (!ack.await(timeout, TimeUnit.MILLISECONDS)) {
                    throw new Exception("Rapfi 命令超时：" + command);
                }
                if (closed) {
                    throw new Exception("引擎已关闭");
                }
            } finally {
                if (rapfiAck == ack) {
                    rapfiAck = new CountDownLatch(0);
                }
            }
        }

        GtpResponse command(String command, long timeout)
                throws Exception {

            int id = commandIds.incrementAndGet();
            PendingResponse response = new PendingResponse();

            pending.put(id, response);

            try {
                write(id + " " + command);

                if (!response.done.await(timeout, TimeUnit.MILLISECONDS)) {
                    throw new Exception("GTP 命令超时：" + command);
                }

                if (response.failure != null) {
                    throw new Exception(response.failure);
                }

                return new GtpResponse(
                        response.success,
                        response.text.toString());
            } finally {
                pending.remove(id);
            }
        }

        void beginKataAnalysis(long requestId, String command)
                throws Exception {

            outputAnalysisId = -1;
            analysisCommandRequestId = requestId;
            analysisCommandId = commandIds.incrementAndGet();

            /*
             * 流式分析不能按照普通命令等待最终空行：
             * 有些分支直到停止分析才结束这个响应。
             * 读取线程收到对应成功头之后才打开结果通道。
             */
            write(analysisCommandId + " " + command);
        }

        void readOutput() {
            PendingResponse body = null;

            try {
                String raw;

                while (!closed && (raw = reader.readLine()) != null) {
                    Log.d(TAG, "<< " + raw);

                    String line = raw.trim();

                    if (engineType == ENGINE_RAPFI) {
                        if ("OK".equalsIgnoreCase(line)) {
                            rapfiOk = true;
                            rapfiReady.countDown();
                            rapfiAck.countDown();
                        }

                        long requestId = outputAnalysisId;

                        boolean finalMove =
                                line.matches("^\\d{1,2},\\d{1,2}.*");

                        if (isCurrentRequest(this, requestId)) {
                            engineCallback.onAnalysisOutput(
                                    requestId, raw);
                        }

                        if (finalMove) {
                            rapfiDone.countDown();
                        }

                        continue;
                    }

                    if (line.startsWith("=") || line.startsWith("?")) {
                        int end = 1;

                        while (end < line.length()
                                && Character.isDigit(line.charAt(end))) {
                            end++;
                        }

                        if (end == 1) continue;

                        int id = Integer.parseInt(
                                line.substring(1, end));

                        boolean success = line.charAt(0) == '=';
                        String text = line.substring(end).trim();

                        body = pending.get(id);

                        if (body != null) {
                            body.success = success;
                            appendText(body.text, text);
                        }

                        if (id == analysisCommandId) {
                            long requestId = analysisCommandRequestId;

                            if (success) {
                                if (isCurrentRequest(this, requestId)) {
                                    outputAnalysisId = requestId;
                                }
                            } else {
                                outputAnalysisId = -1;

                                if (isCurrentRequest(this, requestId)) {
                                    ready = false;
                                    engineCallback.onError(
                                            "KataGo 拒绝分析命令：" + text
                                                    + "。请检查是否支持 "
                                                    + "kata-analyze B 10 / W 10");
                                }
                            }
                        }

                        continue;
                    }

                    if (line.isEmpty()) {
                        if (body != null) {
                            body.done.countDown();
                            body = null;
                        }

                        continue;
                    }

                    if (line.startsWith("info move ")) {
                        long requestId = outputAnalysisId;

                        if (isCurrentRequest(this, requestId)) {
                            engineCallback.onAnalysisOutput(
                                    requestId, raw);
                        }

                        continue;
                    }

                    if (body != null) {
                        appendText(body.text, line);
                    }
                }
            } catch (Exception e) {
                if (!closed) {
                    Log.e(TAG, "readOutput", e);
                }
            } finally {
                boolean unexpected = !closed;

                closed = true;
                outputAnalysisId = -1;

                failPending("引擎输出流已关闭");
                rapfiReady.countDown();
                rapfiDone.countDown();
                rapfiAck.countDown();

                closeQuietly(reader);

                if (unexpected && isCurrentConnection(this)) {
                    ready = false;
                    engineCallback.onError("引擎已退出，请重新启动");
                }
            }
        }

        void close() {
            /*
             * 先同步标记失效，确保旧引擎输出立即被忽略。
             * 实际进程销毁交给后台线程。
             */
            synchronized (this) {
                if (closeScheduled) {
                    return;
                }

                closeScheduled = true;
                closed = true;
                outputAnalysisId = -1;
            }

            /*
             * 立即唤醒等待命令响应的后台任务，
             * 不必等进程真正结束。
             */
            failPending("引擎已关闭");
            rapfiReady.countDown();
            rapfiDone.countDown();
            rapfiAck.countDown();

            Thread closer = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        process.destroy();
                    } catch (Exception e) {
                        Log.w(TAG, "销毁引擎进程失败", e);
                    }

                    /*
                     * 不能在主线程关闭可能正被 readLine 占用的 reader。
                     * reader 继续由读取线程在 finally 中关闭。
                     */
                    closeQuietly(writer);
                }
            }, "Gomoku-engine-close");

            closer.setDaemon(true);
            closer.start();
        }

        void failPending(String message) {
            for (PendingResponse response : pending.values()) {
                response.failure = message;
                response.done.countDown();
            }
        }
    }

    private static void appendText(StringBuilder builder, String text) {
        if (text == null || text.isEmpty()) return;

        if (builder.length() > 0) {
            builder.append('\n');
        }

        builder.append(text);
    }

    private static final class PendingResponse {
        final CountDownLatch done = new CountDownLatch(1);
        final StringBuilder text = new StringBuilder();

        volatile boolean success;
        volatile String failure;
    }

    private static final class GtpResponse {
        final boolean success;
        final String text;

        GtpResponse(boolean success, String text) {
            this.success = success;
            this.text = text == null ? "" : text;
        }
    }

    public interface EngineCallback {
        void onReady();

        void onAnalysisOutput(long analysisId, String line);

        void onError(String message);
    }
}