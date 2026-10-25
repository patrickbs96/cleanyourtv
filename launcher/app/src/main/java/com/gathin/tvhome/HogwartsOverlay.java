package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;

import java.util.Random;

/**
 * Hogwarts' motion: ten floating candles that flicker slowly (6 to 9 s a cycle each) and drift up and down by 2 px
 * over 9 s, each out of step with the others, and by night the twelve stars of the picture twinkling (3 to 4 s). By
 * day, in the Great Hall, only the candles, at 60 % of their size. Calm on purpose: the mockup's 1.4 s flicker
 * was too fast.
 */
final class HogwartsOverlay extends WorldOverlay {
    /** The mockup's candles (vier-welten-v3.html): x and y of the flame in units, glow radius, candle height. */
    private static final float[][] CANDLES = {
            {30, 44, 4f, 5f}, {44, 54, 3.5f, 4.5f}, {56, 40, 4f, 5f}, {66, 52, 3f, 4f}, {24, 60, 3f, 4f},
            {78, 46, 3.5f, 4.5f}, {90, 56, 3f, 4f}, {38, 36, 2.5f, 3.5f}, {108, 48, 3f, 4f}, {12, 50, 2.8f, 3.8f}};
    /** The night picture's stars (make_scene.py NIGHT_STARS): x, y, radius in units. */
    private static final float[][] STARS = {
            {12, 10, .5f}, {30, 22, .4f}, {52, 8, .6f}, {70, 18, .4f}, {88, 6, .5f}, {104, 26, .4f}, {126, 12, .5f},
            {176, 22, .4f}, {160, 40, .4f}, {40, 38, .4f}, {96, 42, .4f}, {186, 8, .5f}};
    private static final float DAY_SCALE = 0.6f;
    private static final long DRIFT_MS = 9000;
    private static final int WAX = 0xFFF3E4B0;

    private final long[] flickerMs = new long[CANDLES.length], flickerOffset = new long[CANDLES.length];
    private final long[] twinkleMs = new long[STARS.length], twinkleOffset = new long[STARS.length];
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wax = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 2 px at 1080 lines, in view pixels. */
    private float drift;

    HogwartsOverlay(Context context, boolean day) {
        super(context, day);
        Random seeded = new Random(0x4A11L);
        for (int i = 0; i < CANDLES.length; i++) {
            flickerMs[i] = 6000 + seeded.nextInt(3001);
            flickerOffset[i] = seeded.nextInt((int) flickerMs[i]);
        }
        for (int i = 0; i < STARS.length; i++) {
            twinkleMs[i] = 3000 + seeded.nextInt(1001);
            twinkleOffset[i] = seeded.nextInt((int) twinkleMs[i]);
        }
        // One glow of radius 1 around the origin, scaled to each candle: the mockup's #ffd27a .9 to #ffb347 0.
        glow.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xE6FFD27A, 0x00FFB347}, null,
                Shader.TileMode.CLAMP));
        core.setColor(0xFFFFF6DC);
        wax.setColor(WAX);
        star.setColor(Color.WHITE);
    }

    @Override
    protected void layout(int w, int h) {
        drift = 2f * h / 1080f;
        float s = day ? DAY_SCALE : 1f;
        float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (float[] c : CANDLES) {
            float r = c[2] * s * unit;
            left = Math.min(left, ux(c[0]) - r);
            right = Math.max(right, ux(c[0]) + r);
            top = Math.min(top, uy(c[1]) - r - drift);
            bottom = Math.max(bottom, uy(c[1]) + Math.max(r, c[3] * s * unit) + drift);
        }
        regions[0].set((int) Math.floor(left) - 1, (int) Math.floor(top) - 1, (int) Math.ceil(right) + 1,
                (int) Math.ceil(bottom) + 1);
        if (day) {
            regions[1].setEmpty();
            return;
        }
        left = Float.MAX_VALUE;
        top = Float.MAX_VALUE;
        right = -Float.MAX_VALUE;
        bottom = -Float.MAX_VALUE;
        for (float[] st : STARS) {
            float r = st[2] * 3f * unit;   // the dot and its glow
            left = Math.min(left, ux(st[0]) - r);
            right = Math.max(right, ux(st[0]) + r);
            top = Math.min(top, uy(st[1]) - r);
            bottom = Math.max(bottom, uy(st[1]) + r);
        }
        regions[1].set((int) Math.floor(left) - 1, (int) Math.floor(top) - 1, (int) Math.ceil(right) + 1,
                (int) Math.ceil(bottom) + 1);
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        if (!day) {
            for (int i = 0; i < STARS.length; i++) {
                float[] st = STARS[i];
                // The mockup's twinkle: 40 % up to full and back.
                float a = 0.4f + 0.6f * wave(phase(now, twinkleMs[i], twinkleOffset[i]));
                float x = ux(st[0]), y = uy(st[1]), r = st[2] * unit;
                star.setAlpha((int) (70 * a));
                c.drawCircle(x, y, r * 2.4f, star);
                star.setAlpha((int) (255 * a));
                c.drawCircle(x, y, r, star);
            }
        }
        float s = day ? DAY_SCALE : 1f;
        for (int i = 0; i < CANDLES.length; i++) {
            float[] cd = CANDLES[i];
            float a = flicker(phase(now, flickerMs[i], flickerOffset[i]));
            // Each candle drifts on the same 9 s cycle, a tenth of it later than the one before.
            float dy = drift * (float) Math.sin(TAU * phase(now, DRIFT_MS, i * DRIFT_MS / CANDLES.length));
            float x = ux(cd[0]), y = uy(cd[1]) + dy;
            float r = cd[2] * s * unit, len = cd[3] * s * unit, half = 0.5f * s * unit;
            // As in the mockup: the glow, the candle over it, the flame's bright core on top.
            glow.setAlpha((int) (255 * a));
            c.save();
            c.translate(x, y);
            c.scale(r, r);
            c.drawCircle(0f, 0f, 1f, glow);
            c.restore();
            c.drawRect(x - half, y, x + half, y + len, wax);
            core.setAlpha((int) (235 * a));
            c.drawCircle(x, y - 0.25f * s * unit, 0.45f * s * unit, core);
        }
    }

    /** The mockup's flicker, stretched to the candle's slow cycle: 0.8, up to 1 at 35 %, down to 0.7 at 65 %, 0.8. */
    static float flicker(float p) {
        if (p < 0.35f) return ease(0.8f, 1f, p / 0.35f);
        if (p < 0.65f) return ease(1f, 0.7f, (p - 0.35f) / 0.3f);
        return ease(0.7f, 0.8f, (p - 0.65f) / 0.35f);
    }

    private static float ease(float from, float to, float t) {
        return from + (to - from) * (0.5f - 0.5f * (float) Math.cos(Math.PI * t));
    }
}
