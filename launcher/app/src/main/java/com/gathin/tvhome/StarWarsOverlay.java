package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;

import java.util.Random;

/**
 * Star Wars' motion: hyperspace streaks in the outer 16 % of the width on both sides (a faint line every 70 px,
 * tilted as the mockup's 100° gradient, drifting at 60 px per 1.1 s and fading towards the middle), seventeen
 * stars twinkling (3 to 4 s), and every 6 to 7 s the hologram flicker: a signal Home answers with a 60 ms dip of
 * the greeting, clock and pills (setHoloFlickerListener).
 */
final class StarWarsOverlay extends WorldOverlay {
    /** The day picture's stars (make_scene.py DAY_STARS): x, y, radius in units. */
    private static final float[][] STARS = {
            {8, 8, .4f}, {22, 24, .35f}, {36, 5, .5f}, {50, 18, .35f}, {66, 10, .4f}, {80, 30, .35f}, {94, 6, .5f},
            {110, 20, .4f}, {128, 36, .35f}, {146, 12, .5f}, {164, 28, .35f}, {182, 8, .4f}, {58, 42, .35f},
            {174, 46, .4f}, {14, 48, .35f}, {120, 50, .35f}, {42, 56, .3f}};
    private static final float EDGE = 0.16f;
    /** In 1080-line pixels: line spacing across the stripes, and the drift of 60 px per 1.1 s. */
    private static final float SPACING = 70f, DRIFT = 60f;
    private static final long DRIFT_MS = 1100;
    /** The stripes run 10° off the vertical (perpendicular to a 100° gradient): x shifts by tan 10° per line down. */
    private static final float SLANT = (float) Math.tan(Math.toRadians(10));
    private static final int MAX_LINES = 64;

    private final long[] twinkleMs = new long[STARS.length], twinkleOffset = new long[STARS.length];
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint streak = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] lines = new float[MAX_LINES * 4];
    private final Random flickerTimes = new Random(0x5A11L);
    private Runnable onHoloFlicker;
    private long nextFlicker;
    /** 1080-line pixels to view pixels; the horizontal step between stripes and how far one leans. */
    private float px, step, lean;

    StarWarsOverlay(Context context, boolean day) {
        super(context, day);
        Random seeded = new Random(0x5747L);
        for (int i = 0; i < STARS.length; i++) {
            twinkleMs[i] = 3000 + seeded.nextInt(1001);
            twinkleOffset[i] = seeded.nextInt((int) twinkleMs[i]);
        }
        star.setColor(0xFFDFE8FF);
        streak.setStyle(Paint.Style.STROKE);
    }

    /** Called on the main thread at each hologram flicker, every 6 to 7 s while the overlay moves; null for none. */
    void setHoloFlickerListener(Runnable onHoloFlicker) {
        this.onHoloFlicker = onHoloFlicker;
    }

    @Override
    protected void onStart(long now) {
        nextFlicker = now + nextFlickerMs();
    }

    @Override
    protected void onFrame(long now) {
        if (now < nextFlicker) return;
        nextFlicker = now + nextFlickerMs();
        if (onHoloFlicker != null) onHoloFlicker.run();
    }

    private long nextFlickerMs() {
        return 6000 + flickerTimes.nextInt(1001);
    }

    @Override
    protected void layout(int w, int h) {
        px = h / 1080f;
        // The spacing is measured across the stripes; along a row they stand 70 / cos 10° apart.
        step = SPACING * px / (float) Math.cos(Math.toRadians(10));
        lean = h * SLANT;
        streak.setStrokeWidth(Math.max(1f, px));
        // The mockup's rgba(150,180,255,.12), full at the screen's edges and gone at 16 % in from them.
        int line = 0x1F96B4FF, none = 0x0096B4FF;
        streak.setShader(new LinearGradient(0f, 0f, w, 0f, new int[]{line, none, none, line},
                new float[]{0f, EDGE, 1f - EDGE, 1f}, Shader.TileMode.CLAMP));
        int edge = (int) Math.ceil(w * EDGE);
        regions[0].set(0, 0, edge, h);
        regions[1].set(w - edge, 0, w, h);
        float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (float[] st : STARS) {
            float r = st[2] * 3f * unit;
            left = Math.min(left, ux(st[0]) - r);
            right = Math.max(right, ux(st[0]) + r);
            top = Math.min(top, uy(st[1]) - r);
            bottom = Math.max(bottom, uy(st[1]) + r);
        }
        // Only the stars between the two edge strips; those inside a strip are redrawn with it.
        regions[2].set(Math.max(edge, (int) Math.floor(left) - 1), (int) Math.floor(top) - 1,
                Math.min(w - edge, (int) Math.ceil(right) + 1), (int) Math.ceil(bottom) + 1);
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        int w = getWidth(), h = getHeight();
        for (int i = 0; i < STARS.length; i++) {
            float[] st = STARS[i];
            float a = 0.4f + 0.6f * wave(phase(now, twinkleMs[i], twinkleOffset[i]));
            float x = ux(st[0]), y = uy(st[1]), r = st[2] * unit;
            star.setAlpha((int) (60 * a));
            c.drawCircle(x, y, r * 2.4f, star);
            star.setAlpha((int) (255 * a));
            c.drawCircle(x, y, r, star);
        }
        if (step <= 0f) return;
        // Leftwards like the mockup's translateX, 60 px every 1.1 s; from the clock itself, so it never jumps.
        float shift = (float) ((now * (double) (DRIFT * px) / DRIFT_MS) % step);
        int edge = (int) Math.ceil(w * EDGE);
        // One pattern over both strips. A stripe's lean reaches past a strip's inner edge, but the shader has
        // faded it out there, so no clipping is needed.
        int n = stripes(0, -lean, edge, shift, h);
        n = stripes(n, w - edge - lean, w, shift, h);
        c.drawLines(lines, 0, n * 4, streak);
    }

    /**
     * Adds the stripes whose foot lies between {@code from} and {@code to} on the pattern moved left by
     * {@code shift}, each leaning right towards its top; returns the new count.
     */
    private int stripes(int n, float from, float to, float shift, int h) {
        float x = (float) Math.ceil((from + shift) / step) * step - shift;
        for (; x <= to && n < MAX_LINES; x += step) {
            lines[n * 4] = x + lean;
            lines[n * 4 + 1] = 0f;
            lines[n * 4 + 2] = x;
            lines[n * 4 + 3] = h;
            n++;
        }
        return n;
    }
}
