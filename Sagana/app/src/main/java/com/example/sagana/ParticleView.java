package com.example.sagana;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/** Opening screen: small mango and butter sparkles that drift upward and sway, like steam and rice grains. */
public class ParticleView extends View {

    private static final int COUNT = 34;
    private static final int[] COLORS = {0xFFFFB400, 0xFFFFF3CF, 0xFFFFD27A, 0xCCFFFFFF};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final float[] x = new float[COUNT], y = new float[COUNT], r = new float[COUNT],
            speed = new float[COUNT], phase = new float[COUNT];
    private final int[] color = new int[COUNT];
    private long last;
    private boolean running;
    private float intensity; // 0..1, fades the whole field in

    public ParticleView(Context c, AttributeSet a) {
        super(c, a);
    }

    public void start() {
        running = true;
        last = 0;
        postInvalidateOnAnimation();
    }

    public void stop() {
        running = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < COUNT; i++) spawn(i, w, h, d, true);
    }

    private void spawn(int i, int w, int h, float d, boolean anywhere) {
        x[i] = rnd.nextFloat() * w;
        y[i] = anywhere ? rnd.nextFloat() * h : h + rnd.nextFloat() * 40 * d;
        r[i] = (1.5f + rnd.nextFloat() * 3.5f) * d;
        speed[i] = (18 + rnd.nextFloat() * 46) * d; // px per second
        phase[i] = rnd.nextFloat() * 6.28f;
        color[i] = COLORS[rnd.nextInt(COLORS.length)];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        intensity = Math.min(1f, intensity + dt * 0.8f);
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < COUNT; i++) {
            y[i] -= speed[i] * dt;
            phase[i] += dt * 1.6f;
            if (y[i] < -10 * d) spawn(i, w, h, d, false);
            float sway = (float) Math.sin(phase[i]) * 10 * d;
            // fade near the top so particles dissolve instead of popping out
            float life = Math.max(0f, Math.min(1f, y[i] / (h * 0.35f)));
            paint.setColor(color[i]);
            paint.setAlpha((int) (Math.min(255, alphaOf(color[i])) * life * intensity * 0.85f));
            canvas.drawCircle(x[i] + sway, y[i], r[i], paint);
        }
        if (running) postInvalidateOnAnimation();
    }

    private static int alphaOf(int c) {
        return (c >>> 24) & 0xFF;
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }
}
