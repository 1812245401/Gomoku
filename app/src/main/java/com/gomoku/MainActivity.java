package com.gomoku;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    /*
     * 文件导入 / 导出请求码。
     * REQ_EXPORT_SGF：ACTION_CREATE_DOCUMENT 选定导出目标后回调。
     * REQ_IMPORT_SGF：ACTION_OPEN_DOCUMENT 选定源文件后回调。
     */
    private static final int REQ_EXPORT_SGF = 1001;
    private static final int REQ_IMPORT_SGF = 1002;
    /*
     * REQ_RECOGNIZE：图片识谱（RecognizeActivity）返回识别结果后回调。
     * 结果不走文件，直接在 Intent extra 里带回四元组 move 串。
     */
    private static final int REQ_RECOGNIZE = 1003;

    /*
     * 棋谱 MIME 类型。与 .sgf 附件一致，
     * 便于其它棋谱软件识别；部分系统不认时用通配类型兜底。
     */
    private static final String SGF_MIME = "application/x-go-sgf";

    /*
     * 待导出的 SGF 内容。CREATE_DOCUMENT 回调时用这个字符串写盘。
     */
    private String pendingExportSgf;
    private String pendingExportName;

    private BoardView boardView;

    private Button btnPass, btnClear, btnPrev, btnNext, btnShowNumbers;
    private Button btnEngineKataGo, btnEngineRapfi, btnRenju, btnFreestyle;
    private Button btnStart, btnStop, btnSave, btnMenu;

    private TextView tvStatus, tvTurnStatus;

    private EngineManager engineManager;
    private RecordManager recordManager;

    private boolean engineReady = false;
    private int currentEngine = EngineManager.ENGINE_KATAGO;
    private boolean isRenju = false;
    private boolean isAnalyzing = false;
    private boolean showNumbers = false;
    private boolean destroyed = false;

    /*
     * 当前棋盘是否来自「拍照识谱」。
     * 识别谱禁止保存（btnSave / 保存对话框）、也就不会进入棋谱库，
     * 从而从源头消除「识别谱导出 SGF 非法」的问题。
     * 该标记随自动存档持久化，旋转屏幕 / 重启后仍保持禁止保存状态。
     */
    private boolean recognized = false;

    /*
     * 分别隔离：
     * 1. 不同引擎启动实例
     * 2. 延迟分析任务
     * 3. 不同分析请求的 UI 输出
     */
    private int engineSession = 0;
    private int analyzeSession = 0;
    private long activeAnalysisId = -1;

    private static final Pattern RAPFI_MOVE =
            Pattern.compile("^(\\d{1,2}),(\\d{1,2})");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        UiTheme.applySystemBars(this);
        setContentView(R.layout.activity_main);

        boardView = (BoardView) findViewById(R.id.boardView);

        btnPass = (Button) findViewById(R.id.btnPass);
        btnClear = (Button) findViewById(R.id.btnClear);
        btnPrev = (Button) findViewById(R.id.btnPrev);
        btnNext = (Button) findViewById(R.id.btnNext);
        btnShowNumbers = (Button) findViewById(R.id.btnShowNumbers);

        btnEngineKataGo = (Button) findViewById(R.id.btnEngineKataGo);
        btnEngineRapfi = (Button) findViewById(R.id.btnEngineRapfi);
        btnRenju = (Button) findViewById(R.id.btnRenju);
        btnFreestyle = (Button) findViewById(R.id.btnFreestyle);

        btnStart = (Button) findViewById(R.id.btnStartAnalyze);
        btnStop = (Button) findViewById(R.id.btnStopAnalyze);
        btnSave = (Button) findViewById(R.id.btnSave);
        btnMenu = (Button) findViewById(R.id.btnMenu);

        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvTurnStatus = (TextView) findViewById(R.id.tvTurnStatus);

        recordManager = new RecordManager(this);
        engineManager = new EngineManager(this);

        restoreAutoGame();
        updateTurnText();

        boardView.setOnTurnChangeListener(
                new BoardView.OnTurnChangeListener() {
                    @Override
                    public void onTurnChanged(boolean isBlack) {
                        onPositionChanged();
                    }
                });

        btnPass.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boardView.passTurn();

                if (!isAnalyzing) {
                    tvStatus.setText("已停一手");
                }
            }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopCurrentAnalysis();
                boardView.clearBoard();
                recognized = false;
                saveAutoGame();
                tvStatus.setText("棋盘已清空");
            }
        });

        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * 原 BoardView 在只有一手时 prevMove 会得到 -1，
                 * 而 -1 又代表显示全部。这里处理该边界情况，
                 * 不需要为此替换整个 BoardView。
                 */
                if (boardView.getCurrentIndex() == -1
                        && boardView.getFullHistory().size() == 1) {

                    boardView.setCurrentIndex(-2);
                    boardView.clearAnalysis();
                    boardView.refreshAnalysis();
                    onPositionChanged();
                } else {
                    boardView.prevMove();
                }

                if (!isAnalyzing) {
                    tvStatus.setText("上一手");
                }
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boardView.nextMove();

                if (!isAnalyzing) {
                    tvStatus.setText("下一手");
                }
            }
        });

        btnShowNumbers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNumbers = !showNumbers;
                boardView.setShowNumbers(showNumbers);
                btnShowNumbers.setText(showNumbers ? "编号:开" : "编号:关");
                saveAutoGame();
            }
        });

        btnEngineKataGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentEngine != EngineManager.ENGINE_KATAGO) {
                    switchEngine(EngineManager.ENGINE_KATAGO);
                }
            }
        });

        btnEngineRapfi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentEngine != EngineManager.ENGINE_RAPFI) {
                    switchEngine(EngineManager.ENGINE_RAPFI);
                }
            }
        });

        btnRenju.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRenju) {
                    isRenju = true;
                    saveAutoGame();
                    applyRuleHot();
                }
            }
        });

        btnFreestyle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRenju) {
                    isRenju = false;
                    saveAutoGame();
                    applyRuleHot();
                }
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!engineReady) {
                    return;
                }

                analyzeSession++;
                isAnalyzing = true;
                doAnalyze();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopCurrentAnalysis();
                tvStatus.setText("已停止分析");
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * 拍照识谱得到的棋谱禁止保存（用户需求）：
                 * 从源头阻断，识别谱不会进入棋谱库，也就不会出现导出问题。
                 */
                if (recognized) {
                    Toast.makeText(
                            MainActivity.this,
                            "拍照识谱的棋谱禁止保存",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                showSaveDialog();
            }
        });

        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popup =
                        new PopupMenu(MainActivity.this, btnMenu);

                popup.getMenu().add(0, 1, 0, "我的棋谱");
                popup.getMenu().add(0, 2, 0, "导入棋谱");
                popup.getMenu().add(0, 4, 0, "图片识谱");
                popup.getMenu().add(0, 3, 1, "关于");

                popup.setOnMenuItemClickListener(
                        new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                if (item.getItemId() == 1) {
                                    showRecordsDialog();
                                    return true;
                                }
                                if (item.getItemId() == 2) {
                                    showImportMenu();
                                    return true;
                                }

                                if (item.getItemId() == 3) {
                                    startActivity(
                                            new Intent(
                                                    MainActivity.this,
                                                    AboutActivity.class));
                                    return true;
                                }
                                if (item.getItemId() == 4) {
                                    /*
                                     * 图片识谱：交给 RecognizeActivity。
                                     * 识别逻辑由该 Activity 内的 WebView 调用
                                     * assets/recognize/recognize.js（半步原样切片）完成。
                                     */
                                    startActivityForResult(
                                            new Intent(
                                                    MainActivity.this,
                                                    RecognizeActivity.class),
                                            REQ_RECOGNIZE);
                                    return true;
                                }


                                return false;
                            }
                        });

                popup.show();
            }
        });

        updateButtons();

        tvStatus.setText("正在准备文件...");

        String error = engineManager.prepareFiles();

        if (error != null) {
            tvStatus.setText("文件准备失败：" + error);
        } else {
            restartCurrentEngine();
        }
    }

    private void updateTurnText() {
        tvTurnStatus.setText(
                boardView.isBlackTurn()
                        ? "当前执子：黑方"
                        : "当前执子：白方");
    }

    private void onPositionChanged() {
        updateTurnText();
        saveAutoGame();

        /*
         * 先让已经进入 UI 队列的旧输出失效。
         */
        activeAnalysisId = -1;
        final int session = ++analyzeSession;

        engineManager.stopAnalysis();

        boardView.clearAnalysis();
        boardView.refreshAnalysis();

        if (currentEngine == EngineManager.ENGINE_KATAGO
                && isAnalyzing
                && engineReady) {

            tvStatus.setText("局面已更新，正在重新分析...");

            /*
             * 连点时只分析最后一个局面。
             */
            boardView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (destroyed || session != analyzeSession) return;

                    if (isAnalyzing && engineReady) {
                        doAnalyze();
                    }
                }
            }, 120);

        } else if (isAnalyzing) {
            isAnalyzing = false;
            boardView.setAnalyzing(false);
            tvStatus.setText("局面已变化，请重新点击开始分析");
        }
    }

    private void stopCurrentAnalysis() {
        analyzeSession++;
        activeAnalysisId = -1;
        isAnalyzing = false;

        if (boardView != null) {
            boardView.setAnalyzing(false);
        }

        if (engineManager != null) {
            engineManager.stopAnalysis();
        }
    }

    private void doAnalyze() {
        if (destroyed || !engineReady || !isAnalyzing) return;

        activeAnalysisId = -1;

        boardView.setAnalyzing(true);
        boardView.clearAnalysis();
        boardView.refreshAnalysis();

        tvStatus.setText(
                boardView.isBlackTurn()
                        ? "正在分析黑方..."
                        : "正在分析白方...");

        activeAnalysisId = engineManager.analyze(
                boardView.getMoveHistory(),
                boardView.isBlackTurn());
    }

    private void switchEngine(int engine) {
        currentEngine = engine;
        saveAutoGame();
        restartCurrentEngine();
    }

    private void restartCurrentEngine() {
        stopCurrentAnalysis();

        engineReady = false;
        final int session = ++engineSession;
        final int engineType = currentEngine;

        updateButtons();

        String name =
                engineType == EngineManager.ENGINE_KATAGO
                        ? "KataGo" : "Rapfi";

        tvStatus.setText("正在启动 " + name + "...");

        engineManager.startEngine(
                engineType,
                isRenju,
                createCallback(session, engineType));
    }

    /*
     * 热切换规则：不重启进程、不重载模型。
     * 引擎未就绪时降级为重新启动（保证状态一致）。
     */
    private void applyRuleHot() {
        String name =
                currentEngine == EngineManager.ENGINE_KATAGO
                        ? "KataGo" : "Rapfi";

        if (!engineReady) {
            /* 引擎还没就绪，没有办法热切，退回重启。 */
            restartCurrentEngine();
            return;
        }

        tvStatus.setText(
                name + " 就绪 · "
                        + (isRenju ? "有禁手" : "无禁手"));

        updateButtons();

        stopCurrentAnalysis();
        engineManager.changeRule(isRenju);

        /* 规则变了，当前盘面结论作废：立即按新规则重跑一次。 */
        if (isAnalyzing) {
            doAnalyze();
        }
    }

    private EngineManager.EngineCallback createCallback(
            final int session,
            final int engineType) {

        return new EngineManager.EngineCallback() {
            @Override
            public void onReady() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed || session != engineSession) return;

                        engineReady = true;
                        updateButtons();

                        String name =
                                engineType == EngineManager.ENGINE_KATAGO
                                        ? "KataGo" : "Rapfi";

                        tvStatus.setText(
                                name + " 就绪 · "
                                        + (isRenju ? "有禁手" : "无禁手"));
                    }
                });
            }

            @Override
            public void onAnalysisOutput(
                    final long analysisId,
                    final String line) {

                /*
                 * 所有界面状态的读取、校验和更新都放在 UI 线程。
                 * 这里必须校验输出自带的编号，不能只检查 isAnalyzing。
                 */
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed
                                || session != engineSession
                                || !engineReady
                                || !isAnalyzing
                                || analysisId != activeAnalysisId) {
                            return;
                        }

                        if (engineType == EngineManager.ENGINE_KATAGO) {
                            if (line.trim().startsWith("info move ")) {
                                parseKataGoMulti(line);
                            }
                        } else {
                            parseRapfiBest(line);
                        }
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed || session != engineSession) return;

                        engineReady = false;
                        isAnalyzing = false;
                        activeAnalysisId = -1;
                        analyzeSession++;

                        boardView.setAnalyzing(false);
                        updateButtons();
                        tvStatus.setText(message);
                    }
                });
            }
        };
    }

    /*
     * 在 UI 线程调用。
     * 胜率直接使用引擎输出，不根据黑白手动做 1 - winrate。
     */
    private void parseKataGoMulti(String line) {
        try {
            String[] blocks = line.split("info move\\s+");
            List<Candidate> candidates = new ArrayList<>();

            for (String block : blocks) {
                block = block.trim();

                if (block.isEmpty()) continue;

                String[] tokens = block.split("\\s+");
                String move = tokens[0];

                if ("pass".equalsIgnoreCase(move)
                        || "resign".equalsIgnoreCase(move)
                        || move.length() < 2) {
                    continue;
                }

                float winrate = -1f;
                long visits = 0;
                int order = Integer.MAX_VALUE;

                for (int i = 1; i < tokens.length - 1; i++) {
                    /*
                     * pv 后是变化图，不再当作根节点属性解析。
                     */
                    if ("pv".equals(tokens[i])) break;

                    if ("winrate".equals(tokens[i])) {
                        winrate = Float.parseFloat(tokens[++i]);
                    } else if ("visits".equals(tokens[i])) {
                        visits = Long.parseLong(tokens[++i]);
                    } else if ("order".equals(tokens[i])) {
                        order = Integer.parseInt(tokens[++i]);
                    }
                }

                /*
                 * kata-analyze 的标准 winrate 为 0～1。
                 * 不把异常值猜测为百分比，以免掩盖协议差异。
                 */
                if (Float.isNaN(winrate)
                        || Float.isInfinite(winrate)
                        || winrate < 0f
                        || winrate > 1f) {
                    continue;
                }

                char ch = Character.toUpperCase(move.charAt(0));

                if (ch < 'A' || ch > 'P' || ch == 'I') continue;

                int col = ch - 'A';

                if (ch > 'I') col--;

                int row = 15 - Integer.parseInt(move.substring(1));

                if (col < 0 || col >= 15 || row < 0 || row >= 15) {
                    continue;
                }

                String text = String.format(
                        Locale.US,
                        "%.1f\n%d",
                        winrate * 100f,
                        visits);

                candidates.add(new Candidate(
                        col, row, winrate, visits, order, text));
            }

            if (candidates.isEmpty()) return;

            Candidate best = candidates.get(0);

            for (Candidate candidate : candidates) {
                if (candidate.order < best.order
                        || (candidate.order == best.order
                        && candidate.visits > best.visits)) {
                    best = candidate;
                }
            }

            boardView.clearAnalysis();
            boardView.setBestPoint(best.col, best.row);

            for (Candidate candidate : candidates) {
                boardView.setAnalysisPoint(
                        candidate.col,
                        candidate.row,
                        candidate.score,
                        candidate.text);
            }

            boardView.refreshAnalysis();

            tvStatus.setText(
                    "KataGo · "
                            + (boardView.isBlackTurn() ? "黑方行棋" : "白方行棋")
                            + " · " + candidates.size() + "点");

        } catch (Exception e) {
            Log.e("MainActivity", "parseKataGoMulti", e);
        }
    }

    private void parseRapfiBest(String line) {
        Matcher matcher = RAPFI_MOVE.matcher(line.trim());

        if (!matcher.find()) return;

        try {
            int col = Integer.parseInt(matcher.group(1));
            int row = 14 - Integer.parseInt(matcher.group(2));

            if (col < 0 || col >= 15 || row < 0 || row >= 15) {
                return;
            }

            boardView.clearAnalysis();
            boardView.setBestPoint(col, row);
            boardView.setAnalysisPoint(col, row, 1f, "最佳");
            boardView.refreshAnalysis();

            tvStatus.setText("Rapfi 最佳点");

        } catch (Exception e) {
            Log.e("MainActivity", "parseRapfiBest", e);
        }
    }

    private void saveAutoGame() {
        if (recordManager == null || boardView == null) return;

        recordManager.saveAutoGame(
                boardView.getFullHistory(),
                boardView.getCurrentIndex(),
                showNumbers,
                currentEngine,
                isRenju,
                recognized);
    }

    private void restoreAutoGame() {
        if (recordManager == null || boardView == null) return;

        JSONObject object = recordManager.getAutoGame();

        if (object == null) return;

        try {
            List<int[]> history = GameRecord.deserialize(
                    object.optString("movesData", ""));

            currentEngine = object.optInt(
                    "engine", EngineManager.ENGINE_KATAGO);

            if (currentEngine != EngineManager.ENGINE_KATAGO
                    && currentEngine != EngineManager.ENGINE_RAPFI) {
                currentEngine = EngineManager.ENGINE_KATAGO;
            }

            isRenju = object.optBoolean("renju", false);
            showNumbers = object.optBoolean("showNumbers", false);
            recognized = object.optBoolean("recognized", false);

            boardView.setShowNumbers(showNumbers);
            boardView.loadHistory(history);

            int savedIndex = object.optInt("currentIndex", -1);
            boardView.setCurrentIndex(savedIndex);

            btnShowNumbers.setText(showNumbers ? "编号:开" : "编号:关");

        } catch (Exception e) {
            Log.e("MainActivity", "restoreAutoGame", e);
        }
    }

    private void showSaveDialog() {
        /*
         * 二次拦截（防御）：即使从其它入口调到保存对话框，
         * 只要当前是识别谱也一律拒绝保存。
         */
        if (recognized) {
            Toast.makeText(
                    this,
                    "拍照识谱的棋谱禁止保存",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText input = new EditText(this);

        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("请输入棋谱名称");

        new UiDialog.Builder(this)
                .title("保存棋谱")
                .input(UiTheme.dialogInput(this, input))
                .positive("保存", new UiDialog.OnActionClick() {
                    @Override
                    public void onClick() {

                        String name =
                                input.getText().toString().trim();

                        if (name.isEmpty()) {
                            name = "未命名棋谱";
                        }

                        GameRecord record = new GameRecord(
                                name,
                                boardView.getFullHistory());

                        recordManager.save(record);

                        Toast.makeText(
                                MainActivity.this,
                                "已保存: " + name,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .negative("取消", null)
                .show();
    }

    private void showRecordsDialog() {
        final List<GameRecord> list = recordManager.getAll();

        if (list.isEmpty()) {
            Toast.makeText(
                    this, "暂无保存的棋谱", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[list.size()];
        String[] subs = new String[list.size()];

        for (int i = 0; i < list.size(); i++) {
            GameRecord record = list.get(i);

            items[i] = record.name;
            subs[i] = record.getDateString()
                    + "  ·  " + record.moveCount + "手";
        }

        new UiDialog.Builder(this)
                .title("我的棋谱")
                .items(
                        items,
                        subs,
                        new UiDialog.OnItemClick() {
                            @Override
                            public void onClick(int index) {
                                showRecordOptions(list.get(index));
                            }
                        })
                .negative("关闭", null)
                .show();
    }

    private void showRecordOptions(final GameRecord record) {
        new UiDialog.Builder(this)
                .title(record.name)
                .items(
                        new String[]{"打开", "导出", "删除"},
                        new UiDialog.OnItemClick() {
                            @Override
                            public void onClick(int index) {

                                if (index == 0) {
                                    try {
                                        List<int[]> history =
                                                GameRecord.deserialize(
                                                        record.movesData);

                                        stopCurrentAnalysis();
                                        boardView.loadHistory(history);
                                        recognized = false;
                                        saveAutoGame();

                                        tvStatus.setText(
                                                "已加载: " + record.name);

                                    } catch (Exception e) {
                                        Toast.makeText(
                                                MainActivity.this,
                                                "棋谱加载失败",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                } else if (index == 1) {
                                    exportRecord(record);
                                } else {
                                    recordManager.delete(record.id);

                                    Toast.makeText(
                                            MainActivity.this,
                                            "已删除",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                .dangerIndex(2)
                .negative("取消", null)
                .show();
    }

    private void exportRecord(GameRecord record) {
        List<int[]> moves;

        try {
            moves = GameRecord.deserialize(record.movesData);
        } catch (Exception e) {
            Toast.makeText(
                    MainActivity.this,
                    "棋谱数据解析失败",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        /*
         * 导出为标准 SGF（FF[4], CA[UTF-8]）。
         * 规则沿用当前棋盘的禁手设置。
         */
        String sgf = SgfIO.generate(
                moves,
                record.name,
                null,
                null,
                record.getDateString(),
                isRenju);

        /*
         * 改为“导出到文件”：
         * 走系统文件选择器（SAF），让用户选保存位置与文件名，
         * 回调 onActivityResult 里再真正写盘。
         */
        pendingExportSgf = sgf;
        pendingExportName = record.name + ".sgf";

        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);

        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType(SGF_MIME);
        create.putExtra(Intent.EXTRA_TITLE, pendingExportName);

        try {
            startActivityForResult(create, REQ_EXPORT_SGF);
        } catch (Exception e) {
            /*
             * 个别设备没有文件选择器时，退回分享文本，保证功能可用。
             */
            shareSgfText(pendingExportName, sgf);
        }
    }

    /*
     * 退回方案：系统无法提供文件选择器时，用分享方式给出 SGF 文本。
     */
    private void shareSgfText(String name, String sgf) {
        Intent share = new Intent(Intent.ACTION_SEND);

        share.setType(SGF_MIME);
        share.putExtra(Intent.EXTRA_SUBJECT, name);
        share.putExtra(Intent.EXTRA_TEXT, sgf);

        startActivity(Intent.createChooser(share, "导出棋谱 (SGF)"));
    }

    /*
     * 导入入口：让用户选择「从文件导入」还是「粘贴文本导入」。
     * 原粘贴框能力保留，新增文件选择能力。
     */
    private void showImportMenu() {
        new UiDialog.Builder(this)
                .title("导入棋谱")
                .items(
                        new String[]{
                                "从文件导入 (.sgf)",
                                "粘贴文本导入"
                        },
                        new UiDialog.OnItemClick() {
                            @Override
                            public void onClick(int index) {

                                if (index == 0) {
                                    pickSgfFile();
                                } else {
                                    showImportDialog();
                                }
                            }
                        })
                .negative("取消", null)
                .show();
    }

    /*
     * 打开系统文件选择器，读取 .sgf。
     * 结果在 onActivityResult(REQ_IMPORT_SGF) 中处理。
     */
    private void pickSgfFile() {
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        open.addCategory(Intent.CATEGORY_OPENABLE);
        open.setType("*/*");

        /*
         * SAF 过滤：优先只显示棋谱类型，
         * 部分机型对自定义 MIME 过滤不生效，故仍允许通配类型兜底。
         */
        open.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{SGF_MIME, "text/plain", "application/octet-stream"});

        try {
            startActivityForResult(open, REQ_IMPORT_SGF);
        } catch (Exception e) {
            Toast.makeText(
                    MainActivity.this,
                    "无法打开文件选择器",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /*
     * 从 Uri 读入全部字节并按 UTF-8 解析为字符串。
     */
    private String readTextFromUri(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);

        if (in == null) {
            throw new IllegalStateException("无法打开输入流");
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;

        try {
            while ((len = in.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
        } finally {
            in.close();
        }

        return new String(bos.toByteArray(), "UTF-8");
    }

    /*
     * 向 Uri 写出文本（覆盖）。
     */
    private void writeTextToUri(Uri uri, String text) throws Exception {
        OutputStream out = getContentResolver().openOutputStream(uri, "wt");

        if (out == null) {
            throw new IllegalStateException("无法打开输出流");
        }

        try {
            out.write(text.getBytes("UTF-8"));
            out.flush();
        } finally {
            out.close();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        /*
         * 图片识谱的结果不走文件 URI，必须在下面「data.getData() == null 直接返回」
         * 的过滤之前先截走，否则永远收不到。
         */
        if (requestCode == REQ_RECOGNIZE) {
            if (resultCode == RESULT_OK && data != null) {
                applyRecognizedMoves(data);
            } else {
                tvStatus.setText("已取消图片识谱");
            }
            return;
        }

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();

        if (uri == null) {
            return;
        }

        if (requestCode == REQ_EXPORT_SGF) {
            /*
             * 用户选定了导出目标，把缓存的 SGF 写盘。
             */
            if (pendingExportSgf == null) {
                return;
            }

            try {
                writeTextToUri(uri, pendingExportSgf);

                Toast.makeText(
                        MainActivity.this,
                        "已导出: " + pendingExportName,
                        Toast.LENGTH_SHORT).show();

                tvStatus.setText("已导出 SGF 文件");
            } catch (Exception e) {
                Toast.makeText(
                        MainActivity.this,
                        "导出失败: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            } finally {
                pendingExportSgf = null;
                pendingExportName = null;
            }

        } else if (requestCode == REQ_IMPORT_SGF) {
            /*
             * 用户选定了源文件，读取并导入。
             */
            String text;

            try {
                text = readTextFromUri(uri);
            } catch (Exception e) {
                Toast.makeText(
                        MainActivity.this,
                        "读取文件失败: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            applyImportedSgf(text);
        }
    }

    /*
     * 导入收口：文本 -> 解析 -> 载入棋盘。
     * 文件导入与粘贴导入共用。
     */
    private void applyImportedSgf(String text) {
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(
                    MainActivity.this,
                    "内容为空",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        List<int[]> history;

        /*
         * 只接受标准 SGF（FF[4]）。
         * 不支持旧版纯文本自制格式。
         */
        try {
            history = SgfIO.parse(text);
        } catch (Exception e) {
            history = null;
        }

        if (history == null || history.isEmpty()) {
            Toast.makeText(
                    MainActivity.this,
                    "导入失败：无法识别棋谱格式",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        stopCurrentAnalysis();
        boardView.loadHistory(history);
        recognized = false;
        saveAutoGame();
        tvStatus.setText(
                "导入成功 (" + history.size() + "手)");
    }

    /*
     * 图片识谱收口：四元组 move 串（col,row,color,number）-> 棋盘。
     *
     * 第 4 位 number 是该子的手序号（0 = 不带手序号）。
     * 该值由 RecognizeActivity 原样带回，这里只做搬运，不在这里推算。
     */
    private void applyRecognizedMoves(Intent data) {
        String raw = data.getStringExtra(RecognizeActivity.EXTRA_MOVES);
        String summary = data.getStringExtra(RecognizeActivity.EXTRA_SUMMARY);
        if (raw == null) {
            Toast.makeText(
                    MainActivity.this,
                    "没有可导入的识别结果",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        /* Empty is intentional when the user deleted every preview stone. */
        if (raw.trim().isEmpty()) {
            stopCurrentAnalysis();
            boardView.clearBoard();
            recognized = false;
            saveAutoGame();
            tvStatus.setText("导入成功 (0手)");
            return;
        }
        List<int[]> history = new ArrayList<int[]>();
        String[] parts = raw.split(";");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) continue;
            String[] t = p.split(",");
            if (t.length < 3) continue;
            try {
                int col = Integer.parseInt(t[0].trim());
                int row = Integer.parseInt(t[1].trim());
                int color = Integer.parseInt(t[2].trim());
                int number = t.length >= 4 ? Integer.parseInt(t[3].trim()) : 0;
                history.add(new int[]{col, row, color, number});
            } catch (NumberFormatException ignored) {
                /* 单条坏数据跳过，不影响整体导入 */
            }
        }
        if (history.isEmpty()) {
            Toast.makeText(
                    MainActivity.this,
                    "识别结果为空",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        /*
         * 识谱导入成功后一律把「编号:开」打开（纯 UI 接线）：
         * 用户随后手落的子会从 1 起自动编号（BoardView.nextManualNumber），
         * 开着编号才看得见。
         */
        if (!showNumbers) {
            showNumbers = true;
            boardView.setShowNumbers(true);
            btnShowNumbers.setText("编号:开");
        }

        /*
         * 打标：本局来自拍照识谱 —— 禁止保存。
         * 标记随自动存档持久化，旋转 / 重启后仍保持禁止保存状态。
         */
        recognized = true;

        stopCurrentAnalysis();
        boardView.loadHistory(history);
        saveAutoGame();
        tvStatus.setText(
                "识谱导入成功 (" + history.size() + "子)"
                        + (summary != null && summary.length() > 0
                        ? "\n" + summary
                        : ""));
    }


    private void showImportDialog() {
        final EditText input = new EditText(this);

        input.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        input.setHint("请粘贴 SGF 棋谱内容（(;GM[1]FF[4]...）");
        input.setMinLines(6);

        new UiDialog.Builder(this)
                .title("导入棋谱")
                .input(UiTheme.dialogInput(this, input))
                .positive("导入", new UiDialog.OnActionClick() {
                    @Override
                    public void onClick() {

                        String text =
                                input.getText().toString().trim();

                        applyImportedSgf(text);
                    }
                })
                .negative("取消", null)
                .show();
    }

    private void updateButtons() {
        boolean kataSelected = currentEngine == EngineManager.ENGINE_KATAGO;
        boolean rapfiSelected = currentEngine == EngineManager.ENGINE_RAPFI;
        btnEngineKataGo.setSelected(kataSelected);
        btnEngineRapfi.setSelected(rapfiSelected);
        btnEngineKataGo.setEnabled(!kataSelected);
        btnEngineRapfi.setEnabled(!rapfiSelected);
        btnRenju.setSelected(isRenju);
        btnFreestyle.setSelected(!isRenju);
        btnRenju.setEnabled(!isRenju);
        btnFreestyle.setEnabled(isRenju);
        btnStart.setEnabled(engineReady);
    }
    private static final class Candidate {
        final int col;
        final int row;
        final float score;
        final long visits;
        final int order;
        final String text;

        Candidate(
                int col,
                int row,
                float score,
                long visits,
                int order,
                String text) {

            this.col = col;
            this.row = row;
            this.score = score;
            this.visits = visits;
            this.order = order;
            this.text = text;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveAutoGame();
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        saveAutoGame();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        saveAutoGame();

        destroyed = true;
        engineSession++;
        analyzeSession++;
        activeAnalysisId = -1;

        if (engineManager != null) {
            engineManager.stopEngine();
        }

        super.onDestroy();
    }
}