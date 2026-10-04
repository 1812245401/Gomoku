package com.gomoku;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/*
 * 棋盘区域框选控件（纯 UI，不含任何识别逻辑）。
 *
 * 作用：把用户拖出来的矩形换算成「相对图片的 0–1 归一化坐标」交给 RecognizeActivity，
 * 再由 RecognizeActivity 拼进 recognize(blob, size, { roi })。roi 的契约来自
 * assets/recognize/recognize.js（x/y/w/h 皆为相对缩放后图片的归一化值），
 * 本控件只负责产坐标，不参与识别 —— 严格守住「只搬半步逻辑、不自写识别」的原则。
 *
 * 交互（向标准框选靠拢，解决「框选不标准」）：
 *   - 空白处拖动 = 新建矩形；
 *   - 拖四角手柄 = 只移动该角（精确对齐棋盘四条边线）；
 *   - 框内拖动  = 整体平移。
 * 归一化换算与识别无关，纹丝未动。
 */
public class RoiCropView extends View {

    private Bitmap bitmap;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint rectStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rectFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /* 图片在控件内的实际绘制矩形（等比缩放 + 居中留边） */
    private final RectF dstRect = new RectF();

    /* 用户框选矩形（控件坐标） */
    private final RectF sel = new RectF();
    private boolean hasSelection = false;

    private float downX, downY;

    /* 交互状态 */
    private static final int HANDLE_NONE = -1;
    private static final int HANDLE_TL = 0;
    private static final int HANDLE_TR = 1;
    private static final int HANDLE_BL = 2;
    private static final int HANDLE_BR = 3;
    private int activeHandle = HANDLE_NONE;
    private boolean movingRect = false;
    private float moveLastX, moveLastY;

    /* 手柄的触摸命中半径 / 绘制半径（px） */
    private final float handleTouch;
    private final float handleDraw;

    public RoiCropView(Context context, AttributeSet attrs) {
        super(context, attrs);

        float d = getResources().getDisplayMetrics().density;
        handleTouch = 24f * d;
        handleDraw = 6f * d;

        dimPaint.setColor(Color.argb(150, 0, 0, 0));

        rectStrokePaint.setStyle(Paint.Style.STROKE);
        rectStrokePaint.setStrokeWidth(2f * d);
        rectStrokePaint.setColor(Color.rgb(164, 215, 186));

        rectFillPaint.setStyle(Paint.Style.FILL);
        rectFillPaint.setColor(Color.argb(35, 164, 215, 186));

        hintPaint.setColor(Color.WHITE);
        hintPaint.setTextSize(14f * d);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setFakeBoldText(true);

        handleFillPaint.setStyle(Paint.Style.FILL);
        handleFillPaint.setColor(Color.WHITE);

        handleStrokePaint.setStyle(Paint.Style.STROKE);
        handleStrokePaint.setStrokeWidth(1.5f * d);
        handleStrokePaint.setColor(Color.rgb(164, 215, 186));

        setClickable(true);
    }

    /** 设置要框选的图片（就是即将送给识别算法的、已矫正/已缩放的那张）。 */
    public void setBitmap(Bitmap b) {
        bitmap = b;
        hasSelection = false;
        sel.setEmpty();
        activeHandle = HANDLE_NONE;
        movingRect = false;
        updateDstRect();
        invalidate();
    }

    public void clearSelection() {
        hasSelection = false;
        sel.setEmpty();
        activeHandle = HANDLE_NONE;
        movingRect = false;
        invalidate();
    }

    /** 当前是否有有效框选。 */
    public boolean hasSelection() {
        return hasSelection && sel.width() > 1f && sel.height() > 1f;
    }

    /**
     * 返回相对图片的归一化 ROI：{x, y, w, h}，取值 0–1。
     * 没有有效框选时返回 null（调用方据此走自动定位）。
     */
    public float[] getRoiNormalized() {
        if (bitmap == null || !hasSelection) return null;

        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        if (bw <= 0f || bh <= 0f) return null;

        float scale = dstRect.width() / bw;
        if (scale <= 0f) return null;

        float x0 = (sel.left - dstRect.left) / scale;
        float y0 = (sel.top - dstRect.top) / scale;
        float x1 = (sel.right - dstRect.left) / scale;
        float y1 = (sel.bottom - dstRect.top) / scale;

        x0 = clamp(x0, 0f, bw);
        y0 = clamp(y0, 0f, bh);
        x1 = clamp(x1, 0f, bw);
        y1 = clamp(y1, 0f, bh);

        float w = x1 - x0;
        float h = y1 - y0;
        if (w < 4f || h < 4f) return null; /* 框太小视为没框 */

        return new float[]{x0 / bw, y0 / bh, w / bw, h / bh};
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private void updateDstRect() {
        dstRect.setEmpty();
        if (bitmap == null) return;

        int vw = getWidth();
        int vh = getHeight();
        if (vw <= 0 || vh <= 0) return;

        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        if (bw <= 0f || bh <= 0f) return;

        float s = Math.min(vw / bw, vh / bh);
        float dw = bw * s;
        float dh = bh * s;
        float left = (vw - dw) / 2f;
        float top = (vh - dh) / 2f;
        dstRect.set(left, top, left + dw, top + dh);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateDstRect();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (bitmap == null) {
            canvas.drawText("请先选择照片", getWidth() / 2f, getHeight() / 2f, hintPaint);
            return;
        }

        if (dstRect.isEmpty()) updateDstRect();
        canvas.drawBitmap(bitmap, null, dstRect, bitmapPaint);

        boolean showRect = hasSelection || (sel.width() > 1f && sel.height() > 1f);
        if (showRect) {
            /* 框外压暗，凸显选中区域 */
            canvas.drawRect(0f, 0f, getWidth(), sel.top, dimPaint);
            canvas.drawRect(0f, sel.bottom, getWidth(), getHeight(), dimPaint);
            canvas.drawRect(0f, sel.top, sel.left, sel.bottom, dimPaint);
            canvas.drawRect(sel.right, sel.top, getWidth(), sel.bottom, dimPaint);

            canvas.drawRect(sel, rectFillPaint);
            canvas.drawRect(sel, rectStrokePaint);

            /* 四角手柄，便于精确对齐棋盘边线 */
            if (hasSelection) {
                drawHandle(canvas, sel.left, sel.top);
                drawHandle(canvas, sel.right, sel.top);
                drawHandle(canvas, sel.left, sel.bottom);
                drawHandle(canvas, sel.right, sel.bottom);
            }
        } else {
            canvas.drawText("拖动框选棋盘区域（框好后可拖四角微调）",
                    getWidth() / 2f, 52f, hintPaint);
        }
    }

    private void drawHandle(Canvas canvas, float cx, float cy) {
        canvas.drawCircle(cx, cy, handleDraw, handleFillPaint);
        canvas.drawCircle(cx, cy, handleDraw, handleStrokePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) return true;
        if (dstRect.isEmpty()) updateDstRect();

        float x = clamp(event.getX(), dstRect.left, dstRect.right);
        float y = clamp(event.getY(), dstRect.top, dstRect.bottom);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                performClick();
                downX = x;
                downY = y;
                if (hasSelection) {
                    activeHandle = hitHandle(x, y);
                    if (activeHandle != HANDLE_NONE) {
                        movingRect = false;
                        return true;
                    }
                    if (sel.contains(x, y)) {
                        movingRect = true;
                        moveLastX = x;
                        moveLastY = y;
                        return true;
                    }
                }
                /* 空白处按下 → 新建矩形 */
                activeHandle = HANDLE_NONE;
                movingRect = false;
                sel.set(x, y, x, y);
                hasSelection = false;
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (activeHandle != HANDLE_NONE) {
                    dragHandle(activeHandle, x, y);
                } else if (movingRect) {
                    moveRect(x, y);
                } else {
                    sel.set(Math.min(downX, x), Math.min(downY, y),
                            Math.max(downX, x), Math.max(downY, y));
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeHandle != HANDLE_NONE) {
                    dragHandle(activeHandle, x, y);
                } else if (movingRect) {
                    moveRect(x, y);
                } else {
                    sel.set(Math.min(downX, x), Math.min(downY, y),
                            Math.max(downX, x), Math.max(downY, y));
                }
                activeHandle = HANDLE_NONE;
                movingRect = false;
                hasSelection = sel.width() > 1f && sel.height() > 1f;
                invalidate();
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    /* 命中哪个角手柄；都不命中返回 HANDLE_NONE */
    private int hitHandle(float x, float y) {
        if (dist(x, y, sel.left, sel.top) <= handleTouch) return HANDLE_TL;
        if (dist(x, y, sel.right, sel.top) <= handleTouch) return HANDLE_TR;
        if (dist(x, y, sel.left, sel.bottom) <= handleTouch) return HANDLE_BL;
        if (dist(x, y, sel.right, sel.bottom) <= handleTouch) return HANDLE_BR;
        return HANDLE_NONE;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /* 拖动某个角：只改该角坐标，再把矩形规范化（保证 left<right、top<bottom） */
    private void dragHandle(int handle, float x, float y) {
        float l = sel.left, t = sel.top, r = sel.right, b = sel.bottom;
        switch (handle) {
            case HANDLE_TL: l = x; t = y; break;
            case HANDLE_TR: r = x; t = y; break;
            case HANDLE_BL: l = x; b = y; break;
            case HANDLE_BR: r = x; b = y; break;
            default: break;
        }
        sel.set(Math.min(l, r), Math.min(t, b), Math.max(l, r), Math.max(t, b));
    }

    /* 整体平移，保证不越出图片绘制区 */
    private void moveRect(float x, float y) {
        float dx = x - moveLastX;
        float dy = y - moveLastY;
        moveLastX = x;
        moveLastY = y;

        float w = sel.width();
        float h = sel.height();
        float nl = sel.left + dx;
        float nt = sel.top + dy;
        if (nl < dstRect.left) nl = dstRect.left;
        if (nt < dstRect.top) nt = dstRect.top;
        if (nl + w > dstRect.right) nl = dstRect.right - w;
        if (nt + h > dstRect.bottom) nt = dstRect.bottom - h;
        sel.set(nl, nt, nl + w, nt + h);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
