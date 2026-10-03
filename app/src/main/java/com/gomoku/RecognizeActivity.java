package com.gomoku;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/*
 * 图片识谱宿主 Activity。
 *
 * 分工（严格遵守「只搬半步代码、不自己写识别逻辑」）：
 *   - 识别算法：100% 来自 assets/recognize/recognize.js（半步五子棋打谱 1.1.9 原样切片）。
 *   - 本类只做：选图 → 解码/EXIF 矫正 → base64 → 丢给 WebView 里的 recognize() → 拿回结果 → 交给棋盘。
 *   - 不再有「是否识别手序编号」勾选框：识别固定跳过手序编号（skipMoveOrder:true），
 *     半步只识别棋盘局面，不再跑「读编号 / 复原手序」，置信度只反映棋子与棋盘质量。
 *   - 识别结果先落到本页下方的「预览棋盘」（BoardView 的预览模式）：
 *     用户可在上面删子 / 补子，确认后「导入到棋盘」。
 *     预览棋盘与其编辑操作全是纯 UI，不参与任何识别算法。
 */
public class RecognizeActivity extends Activity {

    private static final String TAG = "BanbuRecognize";

    /* 选图请求码 */
    private static final int REQ_PICK_IMAGE = 2001;

    /* 回传给 MainActivity 的结果 */
    public static final String EXTRA_MOVES = "banbu_moves";
    public static final String EXTRA_SUMMARY = "banbu_summary";

    /*
     * 传给 recognize 的棋盘尺寸参数（Fc 的第 2 参 t）。
     *
     * 本项目只有 15 路五子棋，不做多路数适配，故固定写死 15（不再提供任何路数选择）。
     * 即使照片拍的是 19 路棋盘，也一律按 15 路交给识别，
     * 识别结果只落在 15×15 网格内（超出范围的点在 handleResult 里被丢弃）。
     */
    private static final int BOARD_SIZE = 15;

    private WebView webView;
    private TextView tvStatus;
    private TextView tvConf;
    private Button btnPick, btnRun, btnImport, btnCancel;

    /* 预览棋盘与其编辑控件（纯 UI，不参与识别） */
    private BoardView boardPreview;
    private Button btnPreviewPlace, btnPreviewDelete;
    private Button btnPreviewColor;
    private int previewColorSel = 1;   /* 1=黑 2=白，预览棋盘「补子」用色 */

    /* 手动框选棋盘区域的遮罩层与控件 */
    private View roiOverlay;
    private RoiCropView roiCrop;
    private Button btnRoi, btnRoiReset, btnRoiConfirm;

    private String pendingBase64;
    private String pendingMime = "image/jpeg";
    private boolean pageReady = false;

    /* 可显示的位图（已矫正/已缩放），供框选控件绘制；与送给识别的 JPEG 同源 */
    private Bitmap previewBitmap;
    /* 用户在框选界面确定的归一化 ROI（0–1）；null 表示未框选、走自动定位 */
    private float[] pendingRoi;

    /*
     * 最近一次识别到的棋子局面（{col,row,color,0}），作为回传兜底。
     */
    private final List<int[]> lastMoves = new ArrayList<int[]>();
    private String lastSummary = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognize);

        webView = (WebView) findViewById(R.id.web_recognize);
        tvStatus = (TextView) findViewById(R.id.tv_recog_status);
        tvConf = (TextView) findViewById(R.id.tv_recog_conf);
        btnPick = (Button) findViewById(R.id.btn_recog_pick);
        btnRun = (Button) findViewById(R.id.btn_recog_run);
        btnImport = (Button) findViewById(R.id.btn_recog_import);
        btnCancel = (Button) findViewById(R.id.btn_recog_cancel);
        boardPreview = (BoardView) findViewById(R.id.board_preview);
        btnPreviewPlace = (Button) findViewById(R.id.btn_preview_place);
        btnPreviewDelete = (Button) findViewById(R.id.btn_preview_delete);
        btnPreviewColor = (Button) findViewById(R.id.btn_preview_color);
        roiOverlay = (View) findViewById(R.id.roi_overlay);
        roiCrop = (RoiCropView) findViewById(R.id.view_roi_crop);
        btnRoi = (Button) findViewById(R.id.btn_recog_roi);
        btnRoiReset = (Button) findViewById(R.id.btn_roi_reset);
        btnRoiConfirm = (Button) findViewById(R.id.btn_roi_confirm);

        setupWebView();
        setupPreviewBoard();

        btnPick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage();
            }
        });

        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecognize();
            }
        });

        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishWithMoves();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        /* 「重新框选」：重新弹出框选遮罩（图片已在，直接进来） */
        btnRoi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRoiOverlay();
            }
        });

        /* 「清除框选」：抹掉当前矩形，等同未框选（走自动定位） */
        btnRoiReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (roiCrop != null) roiCrop.clearSelection();
                tvStatus.setText("已清除框选，可重新拖动框选");
            }
        });

        /* 「确定」：收下框选结果（归一化 ROI），关闭遮罩 */
        btnRoiConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmRoi();
            }
        });

        btnRun.setEnabled(false);
        btnImport.setEnabled(false);
        tvStatus.setText("正在加载半步识别模块…");
        webView.loadUrl(BanbuAssets.pageUrl());
    }

    /* ------------------------------------------------------------------ */
    /* WebView + 本地资源服务器                                            */
    /* ------------------------------------------------------------------ */

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        webView.setBackgroundColor(0x00000000);
        webView.setWebViewClient(new BanbuAssets(this) {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (pendingBase64 == null) {
                    tvStatus.setText("识别模块已就绪，请选择棋局照片");
                } else {
                    tvStatus.setText("识别模块已就绪，点「开始识别」");
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.i(TAG, "[js] " + cm.message());
                return true;
            }
        });
        webView.addJavascriptInterface(new Bridge(), "BanbuBridge");
    }

    public class Bridge {
        @JavascriptInterface
        public void onReady() {
            runOnUiThread(new Runnable() {
                public void run() {
                    pageReady = true;
                }
            });
        }

        @JavascriptInterface
        public void onResult(final String json) {
            runOnUiThread(new Runnable() {
                public void run() {
                    handleResult(json);
                }
            });
        }
    }

    /* ------------------------------------------------------------------ */
    /* 选图                                                               */
    /* ------------------------------------------------------------------ */

    private void pickImage() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(
                    Intent.createChooser(intent, "选择棋局照片"),
                    REQ_PICK_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开相册/文件选择器：" + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_IMAGE) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (prepareImage(uri)) {
            btnRun.setEnabled(true);
            btnImport.setEnabled(false);
            lastMoves.clear();
            lastSummary = "";
            tvStatus.setText("已选择照片，请先框选棋盘区域");
            /* 选图后自动弹出框选；不框选也行，点「确定」即走整图自动定位 */
            showRoiOverlay();
        }
    }

    /*
     * 解码 + 缩放（长边不超过 1600，recognize 内部还会再缩一次）+ EXIF 旋转矫正。
     * EXIF 矫正很重要：竖拍照片若不矫正，行列会互换，识别出的棋局方向会整体错。
     */
    private boolean prepareImage(Uri uri) {
        try {
            /* 换图：旧的可显示位图先释放，ROI 也随新图作废 */
            if (previewBitmap != null) {
                previewBitmap.recycle();
                previewBitmap = null;
            }
            pendingRoi = null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream in0 = getContentResolver().openInputStream(uri);
            if (in0 == null) {
                Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show();
                return false;
            }
            BitmapFactory.decodeStream(in0, null, bounds);
            in0.close();
            int w = bounds.outWidth;
            int h = bounds.outHeight;
            if (w <= 0 || h <= 0) {
                Toast.makeText(this, "无法读取图片尺寸", Toast.LENGTH_SHORT).show();
                return false;
            }
            int sample = 1;
            while (Math.max(w, h) / (sample * 2) >= 1600) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            InputStream in1 = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in1, null, opts);
            in1.close();
            if (bmp == null) {
                Toast.makeText(this, "解码图片失败", Toast.LENGTH_SHORT).show();
                return false;
            }

            int orientation = readExifOrientation(uri);
            Matrix matrix = new Matrix();
            boolean needTransform = false;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                matrix.postRotate(90);
                needTransform = true;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                matrix.postRotate(180);
                needTransform = true;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                matrix.postRotate(270);
                needTransform = true;
            } else if (orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL) {
                matrix.postScale(-1f, 1f);
                needTransform = true;
            } else if (orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL) {
                matrix.postScale(1f, -1f);
                needTransform = true;
            }
            if (needTransform) {
                Bitmap rotated = Bitmap.createBitmap(
                        bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
                if (rotated != bmp) {
                    bmp.recycle();
                    bmp = rotated;
                }
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, bos);
            byte[] jpeg = bos.toByteArray();
            bos.close();
            /* 保留这张已矫正/已缩放的位图交给框选控件显示（不再 recycle） */
            previewBitmap = bmp;
            pendingBase64 = Base64.encodeToString(jpeg, Base64.NO_WRAP);
            pendingMime = "image/jpeg";
            Log.i(TAG, "image ready: sample=" + sample
                    + " orientation=" + orientation
                    + " bytes=" + jpeg.length
                    + " b64=" + pendingBase64.length());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "prepareImage", e);
            Toast.makeText(this, "读取图片失败：" + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private int readExifOrientation(Uri uri) {
        if (Build.VERSION.SDK_INT < 24) {
            /* ExifInterface(InputStream) 需要 API 24；低版本不做矫正，避免误判 */
            return ExifInterface.ORIENTATION_NORMAL;
        }
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) return ExifInterface.ORIENTATION_NORMAL;
            ExifInterface exif = new ExifInterface(in);
            return exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            Log.e(TAG, "readExifOrientation", e);
            return ExifInterface.ORIENTATION_NORMAL;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* 手动框选棋盘区域（纯 UI，坐标换算在 RoiCropView 内）                 */
    /* ------------------------------------------------------------------ */

    /*
     * 弹出框选遮罩：把当前这张（已矫正/已缩放的）位图交给 RoiCropView 显示，
     * 让用户拖出矩形框住棋盘。setBitmap 会清掉上一次的框，等于重新框。
     */
    private void showRoiOverlay() {
        if (previewBitmap == null) {
            Toast.makeText(this, "请先选择照片", Toast.LENGTH_SHORT).show();
            return;
        }
        roiCrop.setBitmap(previewBitmap);
        roiOverlay.setVisibility(View.VISIBLE);
        btnRoi.setEnabled(true);
        tvStatus.setText("请拖出矩形框住棋盘区域，再点「确定」");
    }

    /* 收下框选结果：有效矩形 → 归一化 ROI；没框 → null（走整图自动定位）。 */
    private void confirmRoi() {
        float[] r = roiCrop.getRoiNormalized();
        pendingRoi = r;
        roiOverlay.setVisibility(View.GONE);
        if (r == null) {
            tvStatus.setText("未框选棋盘，将按整图自动定位。点「开始识别」");
        } else {
            tvStatus.setText("已框选棋盘区域，点「开始识别」");
        }
    }

    /* ------------------------------------------------------------------ */
    /* 调用半步识别（唯一的识别入口）                                       */
    /* ------------------------------------------------------------------ */

    private void startRecognize() {
        if (pendingBase64 == null) {
            Toast.makeText(this, "请先选择照片", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!pageReady) {
            Toast.makeText(this, "识别模块还在加载，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        /*
         * 固定跳过手序编号：skipMoveOrder=true。
         * 半步只识别棋盘局面，不跑「读编号 / 复原手序」；识别结果只作为局面落到预览棋盘。
         * 这样 confidence 只反映棋子与棋盘的识别质量，不再被编号判定拖累。
         */
        final boolean skipMoveOrder = true;
        btnRun.setEnabled(false);
        btnImport.setEnabled(false);
        lastMoves.clear();
        lastSummary = "";
        tvStatus.setText("识别中…");

        /* 框选结果拼成第 5 个参数：无框选传空串，host.html 侧按「未框选 → 自动定位」处理 */
        String roiJson = "";
        if (pendingRoi != null && pendingRoi.length == 4
                && pendingRoi[2] > 0f && pendingRoi[3] > 0f) {
            roiJson = String.format(Locale.US,
                    "{\"x\":%.5f,\"y\":%.5f,\"w\":%.5f,\"h\":%.5f}",
                    pendingRoi[0], pendingRoi[1], pendingRoi[2], pendingRoi[3]);
        }
        /* base64 只含 A-Za-z0-9+/=，roiJson 只含数字与 JSON 标点，用单引号包裹都是安全的 */
        /* 固定 15 路：本项目只有 15 路五子棋，不做多路数适配 */
        final int sizeArg = BOARD_SIZE;
        Log.i(TAG, "recognize req size=" + sizeArg
                + " skipMoveOrder=" + skipMoveOrder
                + " roi=" + (roiJson.length() == 0 ? "auto" : roiJson));
        String js = "__banbuRecognize('" + pendingBase64 + "','" + pendingMime
                + "'," + sizeArg + "," + skipMoveOrder
                + ",'" + roiJson + "')";
        webView.evaluateJavascript(js, null);
    }

    /* ------------------------------------------------------------------ */
    /* 结果消费（照搬半步打谱的语义）                                        */
    /* ------------------------------------------------------------------ */

    private void handleResult(String json) {
        if (json == null || json.length() == 0) {
            tvStatus.setText("识别失败：模块没有返回结果");
            btnRun.setEnabled(true);
            return;
        }
        try {
            JSONObject o = new JSONObject(json);
            if (!o.optBoolean("ok", false)) {
                String err = o.optString("error", "未知错误");
                tvStatus.setText("识别失败：" + err);
                btnRun.setEnabled(true);
                return;
            }
            int boardSizeOut = o.optInt("boardSize", 0);
            long ms = o.optLong("ms", 0);
            double confidence = o.optDouble("confidence", -1);
            if (tvConf != null) {
                StringBuilder cb = new StringBuilder();
                cb.append("置信度 ");
                cb.append(confidence >= 0
                        ? String.format(Locale.US, "%.2f", confidence)
                        : "—");
                tvConf.setText(cb.toString());
            }
            Log.i(TAG, "recognize result boardSize=" + boardSizeOut
                    + " confidence=" + confidence
                    + " ms=" + ms);

            /* 1) 棋盘落子（局面） */
            List<int[]> stones = new ArrayList<int[]>();
            JSONArray board = o.optJSONArray("board");
            if (board != null) {
                for (int row = 0; row < board.length(); row++) {
                    JSONArray line = board.optJSONArray(row);
                    if (line == null) continue;
                    for (int col = 0; col < line.length(); col++) {
                        String v = line.optString(col, "");
                        int color = 0;
                        if ("black".equals(v)) {
                            color = 1;
                        } else if ("white".equals(v)) {
                            color = 2;
                        }
                        if (color == 0) continue;
                        if (col >= BoardView.BOARD_SIZE || row >= BoardView.BOARD_SIZE) continue;
                        stones.add(new int[]{col, row, color});
                    }
                }
            }
            if (stones.isEmpty()) {
                tvStatus.setText("没能识别出棋子，请检查照片或重新框选");
                btnRun.setEnabled(true);
                return;
            }

            /* 识别结果一律「先按局面落到预览棋盘」。 */

            /* 2) 局面先落到预览棋盘：按识别到的原顺序落子 */
            lastMoves.clear();
            for (int[] s : stones) {
                lastMoves.add(new int[]{s[0], s[1], s[2], 0});
            }
            if (boardPreview != null) {
                boardPreview.setPreviewMode(true);
                boardPreview.setShowNumbers(true);
                boardPreview.setEditMode(BoardView.EDIT_NONE);
                boardPreview.setPreviewColor(previewColorSel);
                boardPreview.loadHistory(lastMoves);
            }

            lastSummary = "识别 " + stones.size() + " 子"
                    + (ms > 0 ? "，耗时 " + ms + "ms" : "");

            tvStatus.setText(lastSummary + "\n可在预览棋盘上删子/补子，确认后点「导入到棋盘」");
            btnImport.setEnabled(true);
            btnRun.setEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "handleResult", e);
            tvStatus.setText("结果解析失败：" + e.getMessage());
            btnRun.setEnabled(true);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 预览棋盘：识别结果先落这里，用户可删子 / 补子                      */
    /* 全是纯 UI 交互，不参与任何识别算法                                    */
    /* ------------------------------------------------------------------ */

    private void setupPreviewBoard() {
        if (boardPreview == null) return;
        boardPreview.setPreviewMode(true);
        boardPreview.setShowNumbers(true);
        boardPreview.setEditMode(BoardView.EDIT_NONE);
        boardPreview.setPreviewColor(previewColorSel);
        boardPreview.clearBoard();

        /* 「补子」：进入补子模式（点空交叉点落子，用当前补子色） */
        if (btnPreviewPlace != null) {
            btnPreviewPlace.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boardPreview.setEditMode(BoardView.EDIT_NONE);
                    tvStatus.setText("补子模式：点空白交叉点补子（用「落子色」当前颜色）");
                }
            });
        }

        /* 「删子」：进入删子模式（点已有棋子把它去掉） */
        if (btnPreviewDelete != null) {
            btnPreviewDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boardPreview.setEditMode(BoardView.EDIT_DELETE);
                    tvStatus.setText("删子模式：点棋子把它从预览棋盘去掉");
                }
            });
        }

        /* 「落子色」：切换补子用色 黑/白 */
        if (btnPreviewColor != null) {
            btnPreviewColor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    previewColorSel = (previewColorSel == 1) ? 2 : 1;
                    boardPreview.setPreviewColor(previewColorSel);
                    btnPreviewColor.setText(previewColorSel == 1 ? "落子色:黑" : "落子色:白");
                }
            });
        }

    }

    private void finishWithMoves() {
        /* 以预览棋盘的当前历史为准：用户在上面删子 / 补子后的结果 */
        List<int[]> out = (boardPreview != null)
                ? boardPreview.getMoveHistory()
                : new ArrayList<int[]>();
        if (out.isEmpty()) out = lastMoves;
        if (out.isEmpty()) {
            Toast.makeText(this, "还没有可导入的结果", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int[] m : out) {
            if (sb.length() > 0) sb.append(';');
            sb.append(m[0]).append(',')
                    .append(m[1]).append(',')
                    .append(m[2]).append(',')
                    .append(m.length >= 4 ? m[3] : 0);
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_MOVES, sb.toString());
        result.putExtra(EXTRA_SUMMARY, lastSummary);
        setResult(RESULT_OK, result);
        finish();
    }

    /*
     * WebView 释放。
     * 不释放的话，连续进出识谱页会一直残留 WebView 实例及其 JS 上下文
     * （半步识别模块是个几百 KB 的 bundle，泄漏起来很明显）。
     */
    @Override
    protected void onDestroy() {
        try {
            if (webView != null) {
                webView.removeJavascriptInterface("BanbuBridge");
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.destroy();
                webView = null;
            }
        } catch (Exception ignored) {
            /* 释放失败不影响退出 */
        }
        if (previewBitmap != null) {
            previewBitmap.recycle();
            previewBitmap = null;
        }
        if (roiCrop != null) {
            roiCrop.setBitmap(null);
        }
        super.onDestroy();
    }
}