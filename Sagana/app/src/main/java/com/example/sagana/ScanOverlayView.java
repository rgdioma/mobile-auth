package com.example.sagana;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Darkens the camera picture except a square window, with mango corners and a sweeping scan line. */
public class ScanOverlayView extends View {

    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corners = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF window = new RectF();
    private final Path path = new Path();
    private ValueAnimator sweep;
    private float t;
    private boolean found;

    public ScanOverlayView(Context c, AttributeSet a) {
        super(c, a);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        float d = getResources().getDisplayMetrics().density;
        dim.setColor(0x99000000);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        corners.setStyle(Paint.Style.STROKE);
        corners.setStrokeWidth(5 * d);
        corners.setStrokeCap(Paint.Cap.ROUND);
        corners.setColor(0xFFFFB400);
        line.setStrokeWidth(3 * d);
    }

    /** Turn the corners green when a code is read. */
    public void setFound(boolean f) {
        found = f;
        corners.setColor(f ? 0xFF2F7D4A : 0xFFFFB400);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sweep = ValueAnimator.ofFloat(0f, 1f);
        sweep.setDuration(1800);
        sweep.setRepeatMode(ValueAnimator.REVERSE);
        sweep.setRepeatCount(ValueAnimator.INFINITE);
        sweep.setInterpolator(new AccelerateDecelerateInterpolator());
        sweep.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        sweep.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (sweep != null) sweep.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float size = Math.min(w, h) * 0.68f;
        float left = (w - size) / 2f, top = (h - size) / 2f - h * 0.04f;
        window.set(left, top, left + size, top + size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        float r = 22 * d, arm = 34 * d;
        canvas.drawRect(0, 0, getWidth(), getHeight(), dim);
        canvas.drawRoundRect(window, r, r, clear);

        // four rounded corner brackets
        path.reset();
        float l = window.left, tp = window.top, rt = window.right, b = window.bottom;
        path.moveTo(l, tp + arm); path.lineTo(l, tp + r); path.quadTo(l, tp, l + r, tp); path.lineTo(l + arm, tp);
        path.moveTo(rt - arm, tp); path.lineTo(rt - r, tp); path.quadTo(rt, tp, rt, tp + r); path.lineTo(rt, tp + arm);
        path.moveTo(rt, b - arm); path.lineTo(rt, b - r); path.quadTo(rt, b, rt - r, b); path.lineTo(rt - arm, b);
        path.moveTo(l + arm, b); path.lineTo(l + r, b); path.quadTo(l, b, l, b - r); path.lineTo(l, b - arm);
        canvas.drawPath(path, corners);

        if (!found) {
            float y = window.top + 14 * d + (window.height() - 28 * d) * t;
            line.setShader(new LinearGradient(window.left, y, window.right, y,
                    new int[]{0x00C8231B, 0xFFE8432E, 0x00C8231B}, null, Shader.TileMode.CLAMP));
            canvas.drawLine(window.left + 16 * d, y, window.right - 16 * d, y, line);
        }
    }
}
