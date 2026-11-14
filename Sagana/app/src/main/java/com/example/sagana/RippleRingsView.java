package com.example.sagana;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Opening screen: rings that pulse out from the bowl like a radar,
 * a picture of shared food reaching neighbors nearby.
 */
public class RippleRingsView extends View {

    private static final int RINGS = 3;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator anim;
    private float t;           // 0..1 loop position
    private float centerYFraction = 0.42f;
    private float master;      // fades rings in

    public RippleRingsView(Context c, AttributeSet a) {
        super(c, a);
        float d = getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * d);
        stroke.setColor(0xFFFFFFFF);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFFFFB400);
    }

    /** Where the rings start from, as a fraction of the view height. */
    public void setCenterY(float pxFromTop) {
        if (getHeight() > 0) centerYFraction = pxFromTop / getHeight();
        invalidate();
    }

    public void start() {
        if (anim != null) return;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(2400);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            master = Math.min(1f, master + 0.04f);
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (anim == null) return;
        float d = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f, cy = getHeight() * centerYFraction;
        float r0 = 70 * d, rMax = Math.max(getWidth(), getHeight()) * 0.62f;
        for (int i = 0; i < RINGS; i++) {
            float p = (t + i / (float) RINGS) % 1f;
            // ease out so rings slow as they spread
            float e = 1f - (1f - p) * (1f - p);
            float r = r0 + (rMax - r0) * e;
            int alpha = (int) (110 * (1f - p) * master);
            stroke.setAlpha(alpha);
            canvas.drawCircle(cx, cy, r, stroke);
            fill.setAlpha((int) (22 * (1f - p) * master));
            canvas.drawCircle(cx, cy, r, fill);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
