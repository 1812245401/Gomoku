package com.gomoku;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

public class BoardView extends View {

    public static final int BOARD_SIZE = 15;

    /*
     * 预览棋盘（识谱预览用）的编辑模式。纯 UI 状态，不涉及任何识别逻辑。
     */
    public static final int EDIT_NONE = 0;   /* 点空点补子（对局即原落子行为） */
    public static final int EDIT_DELETE = 1; /* 点已有棋子 → 删子 */

    private float cellSize;
    private float padding;

    private Paint boardPaint;
    private Paint linePaint;
    private Paint borderPaint;
    private Paint starPaint;
    private Paint blackPaint;
    private Paint whitePaint;
    private Paint whiteStrokePaint;
    private Paint textPaint;
    private Paint analysisPaint;
    private Paint analysisRingPaint;
    private Paint bestRingPaint;
    private Paint numberPaint;
    private Paint moveNumberPaint;
    private Paint shadowPaint;
    private Paint lastMovePaint;
    /* Cache immutable gradients; update only their local transform per stone. */
    private LinearGradient boardShader;
    private RadialGradient blackStoneShader;
    private RadialGradient whiteStoneShader;
    private final Matrix blackShaderMatrix = new Matrix();
    private final Matrix whiteShaderMatrix = new Matrix();
    private float touchDownX;
    private float touchDownY;
    private boolean touchMoved;
    private final float touchSlop;

    private final int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private final List<int[]> moveHistory = new ArrayList<>();

    private int currentIndex = -1;
    private boolean isBlackTurn = true;
    private boolean analyzing = false;
    private boolean showNumbers = false;

    /*
     * 预览/编辑（识谱预览棋盘专用）：纯 UI 状态，不涉及任何识别逻辑。
     *   previewMode  = true 时进入识谱预览编辑：补子不带手序号、不自动换手。
     *   previewColor = 「补子」落下的颜色（1=黑 2=白）。
     *   editMode     = 点按行为：EDIT_NONE 补子 / EDIT_DELETE 删子。
     */
    private boolean previewMode = false;
    private int previewColor = 1;
    private int editMode = EDIT_NONE;


    private final float[][] analysisScore = new float[BOARD_SIZE][BOARD_SIZE];
    private final String[][] analysisText = new String[BOARD_SIZE][BOARD_SIZE];
    private int bestCol = -1;
    private int bestRow = -1;

    private int animatedCol = -1;
    private int animatedRow = -1;
    private float animatedScale = 1f;

    private OnTurnChangeListener listener;

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
    }

    private void initPaints() {
        boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.argb(145, 75, 56, 36));
        linePaint.setStrokeWidth(1.2f);
        linePaint.setStyle(Paint.Style.STROKE);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.argb(90, 93, 66, 40));
        borderPaint.setStrokeWidth(1.5f);
        borderPaint.setStyle(Paint.Style.STROKE);

        starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPaint.setColor(Color.rgb(72, 52, 34));
        starPaint.setStyle(Paint.Style.FILL);

        blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPaint.setStyle(Paint.Style.FILL);

        whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setStyle(Paint.Style.FILL);
        blackStoneShader = new RadialGradient(
                -0.34f, -0.38f, 1.12f,
                new int[]{Color.rgb(92, 92, 92), Color.rgb(32, 32, 34), Color.rgb(8, 8, 9)},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP);
        whiteStoneShader = new RadialGradient(
                -0.35f, -0.40f, 1.15f,
                new int[]{Color.WHITE, Color.rgb(241, 241, 241), Color.rgb(205, 205, 205)},
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP);
        blackPaint.setShader(blackStoneShader);
        whitePaint.setShader(whiteStoneShader);

        whiteStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whiteStrokePaint.setStyle(Paint.Style.STROKE);
        whiteStrokePaint.setColor(Color.argb(120, 50, 50, 50));
        whiteStrokePaint.setStrokeWidth(1.2f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.argb(185, 76, 61, 45));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF,
                android.graphics.Typeface.NORMAL));

        analysisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        analysisPaint.setStyle(Paint.Style.FILL);

        analysisRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        analysisRingPaint.setStyle(Paint.Style.STROKE);
        analysisRingPaint.setStrokeWidth(2f);

        bestRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bestRingPaint.setStyle(Paint.Style.STROKE);
        bestRingPaint.setStrokeWidth(2.5f);
        bestRingPaint.setColor(Color.rgb(218, 74, 61));

        numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        numberPaint.setColor(Color.WHITE);
        numberPaint.setTextAlign(Paint.Align.CENTER);
        numberPaint.setFakeBoldText(true);

        moveNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        moveNumberPaint.setTextAlign(Paint.Align.CENTER);
        moveNumberPaint.setFakeBoldText(true);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.argb(55, 0, 0, 0));
        shadowPaint.setStyle(Paint.Style.FILL);

        lastMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastMovePaint.setStyle(Paint.Style.STROKE);
        lastMovePaint.setStrokeWidth(2f);
        lastMovePaint.setColor(Color.rgb(218, 74, 61));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        int size;
        if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
            size = Math.min(width, height);
        } else if (widthMode == MeasureSpec.EXACTLY) {
            size = width;
        } else if (heightMode == MeasureSpec.EXACTLY) {
            size = height;
        } else {
            size = width > 0 ? width : (height > 0 ? height : 480);
        }

        size = Math.max(1, size);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        padding = w * 0.085f;
        cellSize = (w - padding * 2f) / (BOARD_SIZE - 1);
        boardShader = new LinearGradient(
                0, 0, w, h, Color.rgb(238, 207, 161), Color.rgb(224, 181, 125),
                Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float left = padding;
        float top = padding;
        float right = padding + (BOARD_SIZE - 1) * cellSize;
        float bottom = top + (BOARD_SIZE - 1) * cellSize;

        boardPaint.setShader(boardShader);
        canvas.drawRect(0, 0, getWidth(), getHeight(), boardPaint);

        canvas.drawRect(left - cellSize * 0.18f, top - cellSize * 0.18f,
                right + cellSize * 0.18f, bottom + cellSize * 0.18f, borderPaint);

        for (int i = 0; i < BOARD_SIZE; i++) {
            float x = left + i * cellSize;
            float y = top + i * cellSize;

            canvas.drawLine(left, y, right, y, linePaint);
            canvas.drawLine(x, top, x, bottom, linePaint);
        }

        int[][] stars = {
                {3, 3}, {11, 3},
                {3, 11}, {11, 11},
                {7, 7}
        };
        float starRadius = Math.max(2.2f, cellSize * 0.075f);
        for (int[] p : stars) {
            canvas.drawCircle(xOf(p[0]), yOf(p[1]), starRadius, starPaint);
        }

        drawCoordinates(canvas);
        drawStones(canvas);
        drawAnalysis(canvas);
        drawMoveNumbers(canvas);
    }

    private void drawCoordinates(Canvas canvas) {
        textPaint.setTextSize(cellSize * 0.27f);
        textPaint.setColor(Color.argb(175, 76, 61, 45));

        String[] letters = {
                "A","B","C","D","E","F","G","H",
                "I","J","K","L","M","N","O"
        };

        for (int i = 0; i < BOARD_SIZE; i++) {
            canvas.drawText(
                    letters[i],
                    xOf(i),
                    padding + 14 * cellSize + cellSize * 0.48f,
                    textPaint);
        }

        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i < BOARD_SIZE; i++) {
            canvas.drawText(
                    String.valueOf(15 - i),
                    padding - cellSize * 0.27f,
                    yOf(i) + cellSize * 0.085f,
                    textPaint);
        }
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void drawStones(Canvas canvas) {
        int displayCount = getDisplayCount();

        for (int k = 0; k < displayCount; k++) {
            int[] m = moveHistory.get(k);
            if (m[0] < 0) continue;

            float cx = xOf(m[0]);
            float cy = yOf(m[1]);
            float scale = 1f;

            if (m[0] == animatedCol && m[1] == animatedRow) {
                scale = animatedScale;
            }

            drawStone(canvas, cx, cy, cellSize * 0.425f * scale, m[2] == 1);
        }

        int last = findLastDisplayedStone(displayCount);
        if (last >= 0) {
            int[] m = moveHistory.get(last);
            float cx = xOf(m[0]);
            float cy = yOf(m[1]);

            lastMovePaint.setStrokeWidth(Math.max(1.5f, cellSize * 0.035f));
            canvas.drawCircle(cx, cy, cellSize * 0.18f, lastMovePaint);
        }
    }

    private void drawStone(Canvas canvas, float cx, float cy, float radius, boolean black) {
        canvas.drawCircle(cx + radius * 0.08f, cy + radius * 0.10f,
                radius * 1.01f, shadowPaint);
        if (black) {
            blackShaderMatrix.setScale(radius, radius);
            blackShaderMatrix.postTranslate(cx, cy);
            blackStoneShader.setLocalMatrix(blackShaderMatrix);
            canvas.drawCircle(cx, cy, radius, blackPaint);
        } else {
            whiteShaderMatrix.setScale(radius, radius);
            whiteShaderMatrix.postTranslate(cx, cy);
            whiteStoneShader.setLocalMatrix(whiteShaderMatrix);
            canvas.drawCircle(cx, cy, radius, whitePaint);
            canvas.drawCircle(cx, cy, radius, whiteStrokePaint);
        }
    }
    private void drawAnalysis(Canvas canvas) {
        if (!analyzing) return;

        for (int col = 0; col < BOARD_SIZE; col++) {
            for (int row = 0; row < BOARD_SIZE; row++) {
                if (board[col][row] != 0) continue;

                /*
                 * 用 text 是否为 null 判断「该点有没有分析数据」。
                 * 不能用 score <= 0.001f：score 存的是胜率(0~1)，
                 * 胜率为 0 的合法候选点会被误判为无数据而不显示。
                 */
                if (analysisText[col][row] == null) continue;

                float cx = xOf(col);
                float cy = yOf(row);
                boolean best = col == bestCol && row == bestRow;

                if (best) {
                    analysisPaint.setColor(Color.argb(42, 215, 67, 58));
                    canvas.drawCircle(cx, cy, cellSize * 0.43f, analysisPaint);

                    bestRingPaint.setStrokeWidth(Math.max(2f, cellSize * 0.04f));
                    canvas.drawCircle(cx, cy, cellSize * 0.39f, bestRingPaint);
                } else {
                    int alpha = Math.min(
                            130,
                            Math.max(28, (int) (analysisScore[col][row] * 115f)));

                    analysisPaint.setColor(Color.argb(alpha, 39, 110, 78));
                    canvas.drawCircle(cx, cy, cellSize * 0.30f, analysisPaint);

                    analysisRingPaint.setColor(Color.argb(
                            Math.min(180, alpha + 30), 39, 110, 78));
                    canvas.drawCircle(cx, cy, cellSize * 0.30f, analysisRingPaint);
                }

                if (analysisText[col][row] != null) {
                    String[] lines = analysisText[col][row].split("\\n");

                    numberPaint.setColor(best
                            ? Color.rgb(150, 38, 32)
                            : Color.rgb(28, 72, 54));

                    if (lines.length >= 2) {
                        // Keep the two values vertically separated so visits never
                        // collapse into a single unreadable line on small screens.
                        numberPaint.setTextSize(Math.max(9f, cellSize * 0.19f));
                        canvas.drawText(lines[0], cx,
                                cy - cellSize * 0.045f, numberPaint);
                        numberPaint.setTextSize(Math.max(7f, cellSize * 0.135f));
                        canvas.drawText(lines[1], cx,
                                cy + cellSize * 0.18f, numberPaint);
                    } else {
                        numberPaint.setTextSize(Math.max(9f, cellSize * 0.19f));
                        canvas.drawText(
                                analysisText[col][row],
                                cx,
                                cy + cellSize * 0.065f,
                                numberPaint);
                    }
                }
            }
        }
    }

    private void drawMoveNumbers(Canvas canvas) {
        if (!showNumbers) return;

        int displayCount = getDisplayCount();

        for (int k = 0; k < displayCount; k++) {
            int[] m = moveHistory.get(k);
            if (m[0] < 0) continue;

            float cx = xOf(m[0]);
            float cy = yOf(m[1]);

            moveNumberPaint.setColor(
                    m[2] == 1 ? Color.WHITE : Color.rgb(35, 35, 35));
            moveNumberPaint.setTextSize(cellSize * 0.29f);
            /*
             * 编号来源：
             *   - 有第 4 位：画真实手序号（> 0 才画；= 0 表示该子不带手序号，跳过）。
             *   - 没有第 4 位（极旧的 3 位历史）：沿用原逻辑画 k+1。
             */
            String label;
            if (m.length >= 4) {
                if (m[3] <= 0) continue;
                label = String.valueOf(m[3]);
            } else {
                label = String.valueOf(k + 1);
            }
            canvas.drawText(
                    label,
                    cx,
                    cy + cellSize * 0.105f,
                    moveNumberPaint);
        }
    }

    private int getDisplayCount() {
        if (currentIndex == -1) return moveHistory.size();
        if (currentIndex == -2) return 0;
        return Math.min(moveHistory.size(), currentIndex + 1);
    }

    private int findLastDisplayedStone(int displayCount) {
        for (int i = displayCount - 1; i >= 0; i--) {
            if (moveHistory.get(i)[0] >= 0) return i;
        }
        return -1;
    }

    private float xOf(int col) {
        return padding + col * cellSize;
    }

    private float yOf(int row) {
        return padding + row * cellSize;
    }

    private void rebuildBoard() {
        for (int col = 0; col < BOARD_SIZE; col++) {
            for (int row = 0; row < BOARD_SIZE; row++) {
                board[col][row] = 0;
            }
        }

        int end = getDisplayCount();

        for (int k = 0; k < end; k++) {
            int[] m = moveHistory.get(k);
            if (m[0] >= 0) {
                board[m[0]][m[1]] = m[2];
            }
        }

        isBlackTurn = (end % 2 == 0);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touchDownX = event.getX();
            touchDownY = event.getY();
            touchMoved = false;
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (Math.hypot(event.getX() - touchDownX, event.getY() - touchDownY)
                    > touchSlop) {
                touchMoved = true;
            }
            return true;
        }
        if (action != MotionEvent.ACTION_UP) return true;
        if (touchMoved) {
            touchMoved = false;
            return true;
        }
        performClick();
        float x = event.getX();
        float y = event.getY();
        if (cellSize <= 0f) return true;
        float boardX = (x - padding) / cellSize;
        float boardY = (y - padding) / cellSize;
        int col = Math.round(boardX);
        int row = Math.round(boardY);
        if (col < 0 || col >= BOARD_SIZE || row < 0 || row >= BOARD_SIZE
                || Math.abs(boardX - col) > 0.62f
                || Math.abs(boardY - row) > 0.62f) return true;
        if (previewMode) {
            if (editMode == EDIT_DELETE) deleteStoneAt(col, row);
            else if (board[col][row] == 0) placeStone(col, row);
            return true;
        }
        /* Tapping an existing stone while reviewing must not delete the future. */
        if (board[col][row] == 0) {
            truncateReviewIfNeeded();
            placeStone(col, row);
        }
        return true;
    }
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
    private void placeStone(int col, int row) {
        int color;
        int number;
        if (previewMode) {
            color = previewColor;
            number = 0; /* 预览补子不带手序号 */
        } else {
            color = isBlackTurn ? 1 : 2;
            number = nextManualNumber();
        }

        board[col][row] = color;
        moveHistory.add(new int[]{col, row, color, number});
        currentIndex = -1;
        if (!previewMode) {
            isBlackTurn = !isBlackTurn;
        }


        clearAnalysis();

        animatedCol = col;
        animatedRow = row;
        animatedScale = 0.78f;

        ValueAnimator animator = ValueAnimator.ofFloat(0.78f, 1f);
        animator.setDuration(125);
        animator.setInterpolator(new DecelerateInterpolator());

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                animatedScale = (Float) animation.getAnimatedValue();
                postInvalidateOnAnimation();
            }
        });

        animator.start();
        invalidate();

        if (!previewMode && listener != null) {
            listener.onTurnChanged(isBlackTurn);
        }
    }

    /*
     * 手落子的手序号 = 当前历史里已用过的最大手序号 + 1。
     *
     * 用途：
     *   - 纯手动对局：历史里没有编号 → 得 1,2,3…；
     *   - 载入带编号的存档/棋谱后继续下：接着最大编号往下数。
     * 手落子也带真实手序号，存 / 读 / 导出三处语义一致。
     */
    private int nextManualNumber() {
        int max = 0;
        for (int i = 0; i < moveHistory.size(); i++) {
            int[] m = moveHistory.get(i);
            if (m != null && m.length >= 4 && m[3] > max) {
                max = m[3];
            }
        }
        return max + 1;
    }

    public void passTurn() {
        truncateReviewIfNeeded();

        int color = isBlackTurn ? 1 : 2;
        moveHistory.add(new int[]{-1, -1, color, nextManualNumber()});

        isBlackTurn = !isBlackTurn;
        clearAnalysis();
        invalidate();

        if (listener != null) listener.onTurnChanged(isBlackTurn);
    }

    private void truncateReviewIfNeeded() {
        if (currentIndex != -1) {
            int keep = getDisplayCount();
            while (moveHistory.size() > keep) {
                moveHistory.remove(moveHistory.size() - 1);
            }
            currentIndex = -1;
            rebuildBoard();
        }
    }

    public void prevMove() {
        if (moveHistory.isEmpty()) return;

        if (currentIndex == -1) {
            currentIndex = moveHistory.size() - 2;
        } else if (currentIndex > 0) {
            currentIndex--;
        } else if (currentIndex == 0) {
            currentIndex = -2;
        } else {
            return;
        }

        rebuildBoard();
        clearAnalysis();
        invalidate();

        if (listener != null) listener.onTurnChanged(isBlackTurn);
    }

    public void nextMove() {
        if (moveHistory.isEmpty()) return;

        if (currentIndex == -2) {
            currentIndex = 0;
        } else if (currentIndex >= 0 && currentIndex < moveHistory.size() - 1) {
            currentIndex++;
        } else if (currentIndex >= 0) {
            currentIndex = -1;
        } else {
            return;
        }

        rebuildBoard();
        clearAnalysis();
        invalidate();

        if (listener != null) listener.onTurnChanged(isBlackTurn);
    }

    public void clearBoard() {
        for (int col = 0; col < BOARD_SIZE; col++) {
            for (int row = 0; row < BOARD_SIZE; row++) {
                board[col][row] = 0;
            }
        }

        moveHistory.clear();
        currentIndex = -1;
        isBlackTurn = true;
        animatedCol = -1;
        animatedRow = -1;

        clearAnalysis();
        analyzing = false;
        invalidate();

        if (listener != null) listener.onTurnChanged(true);
    }

    public void loadHistory(List<int[]> history) {
        moveHistory.clear();

        if (history != null) {
            for (int[] m : history) {
                if (m == null || m.length < 3) continue;
                /*
                 * 第 4 位是该子的手序号（> 0 才有；= 0 或没有第 4 位表示不带手序号）。
                 * 这里整段搬运，存档 / 棋谱里的手序号原样保留。
                 */
                moveHistory.add(new int[]{
                        m[0], m[1], m[2], m.length >= 4 ? m[3] : 0});
            }
        }

        currentIndex = -1;
        rebuildBoard();
        clearAnalysis();
        analyzing = false;
        invalidate();

        if (listener != null) listener.onTurnChanged(isBlackTurn);
    }

    public void setShowNumbers(boolean show) {
        showNumbers = show;
        invalidate();
    }

    public void setAnalyzing(boolean a) {
        analyzing = a;
        if (!a) clearAnalysis();
        invalidate();
    }

    public void clearAnalysis() {
        bestCol = -1;
        bestRow = -1;

        for (int col = 0; col < BOARD_SIZE; col++) {
            for (int row = 0; row < BOARD_SIZE; row++) {
                analysisScore[col][row] = 0f;
                analysisText[col][row] = null;
            }
        }
    }

    public void setBestPoint(int col, int row) {
        bestCol = col;
        bestRow = row;
    }

    public void setAnalysisPoint(int col, int row, float score, String text) {
        if (col >= 0 && col < BOARD_SIZE &&
                row >= 0 && row < BOARD_SIZE) {
            analysisScore[col][row] = score;
            analysisText[col][row] = text;
        }
    }

    public void refreshAnalysis() {
        invalidate();
    }

    public List<int[]> getMoveHistory() {
        if (currentIndex == -2) {
            return new ArrayList<int[]>();
        }

        if (currentIndex < 0) {
            return new ArrayList<int[]>(moveHistory);
        }

        return new ArrayList<int[]>(
                moveHistory.subList(0, currentIndex + 1));
    }

    public List<int[]> getFullHistory() {
        return new ArrayList<int[]>(moveHistory);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public boolean isBlackTurn() {
        return isBlackTurn;
    }

    public void setCurrentIndex(int index) {
        if (moveHistory.isEmpty()) {
            currentIndex = -1;
        } else if (index == -2) {
            currentIndex = -2;
        } else if (index >= 0 && index < moveHistory.size()) {
            currentIndex = index;
        } else {
            currentIndex = -1;
        }
        rebuildBoard();
        invalidate();
    }

    /*
     * ===== 识谱预览棋盘（纯 UI / 交互层）========================
     * 以下方法只服务「识别结果先落在预览棋盘上，再人工校正」这一流程：
     * 删除 / 补子都只改本地 moveHistory，不参与任何识别算法。
     * ==========================================================
     */

    public void setPreviewMode(boolean preview) {
        previewMode = preview;
        invalidate();
    }

    public boolean isPreviewMode() {
        return previewMode;
    }

    public void setEditMode(int mode) {
        editMode = mode;
        invalidate();
    }

    public int getEditMode() {
        return editMode;
    }

    public void setPreviewColor(int color) {
        previewColor = color;
        invalidate();
    }

    public int getPreviewColor() {
        return previewColor;
    }

    /* 删除 (col,row) 上的棋子；成功返回 true。 */
    public boolean deleteStoneAt(int col, int row) {
        if (col < 0 || col >= BOARD_SIZE || row < 0 || row >= BOARD_SIZE) {
            return false;
        }
        for (int i = moveHistory.size() - 1; i >= 0; i--) {
            int[] m = moveHistory.get(i);
            if (m[0] == col && m[1] == row) {
                moveHistory.remove(i);
                rebuildBoard();
                clearAnalysis();
                invalidate();
                if (listener != null) listener.onTurnChanged(isBlackTurn);
                return true;
            }
        }
        return false;
    }

    public interface OnTurnChangeListener {
        void onTurnChanged(boolean isBlack);
    }

    public void setOnTurnChangeListener(OnTurnChangeListener l) {
        listener = l;
    }
}