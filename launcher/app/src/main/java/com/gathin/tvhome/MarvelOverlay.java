package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;

/**
 * Marvel's motion: the reactor on the right at 86 % across and half way down, 10 % of the screen's height across,
 * with ten segments and three rings turning once in 18 s around a core that pulses every 2 s; by night the
 * anamorphic streak at 46 % of the height breathing over 4 s (opacity 0.55 to 0.9); and the beacon on the tall
 * tower pulsing every 2 s. pulse(REACTOR_KICK) flares the core once.
 */
final class MarvelOverlay extends WorldOverlay {
    private static final long TURN_MS = 18_000, CORE_MS = 2000, STREAK_MS = 4000, BEACON_MS = 2000;
    private static final long KICK_MS = 700;
    /** The beacon on the tall tower in units (make_scene.py BEACON) and its glow radius. */
    private static final float BEACON_X = 113.6f, BEACON_Y = 30.2f, BEACON_R = 3.2f;
    /** The streak's centre line and its warm ellipse (make_scene.py night: 96, 49.7, 40 x 7; line 0.8 high). */
    private static final float STREAK_Y = 49.7f, STREAK_RX = 40f, STREAK_RY = 7f, LINE_H = 0.8f;
    private static final int BLUE = 0xFF9FDCFF, PALE = 0xFFBFEFFF;

    /** The ten segments in the reactor's own 100-unit box around its centre (the mockup's SVG, rotated by 36°). */
    private final Path segments = new Path();
    private final Paint ringOuter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringMid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringInner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint segment = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coreGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beacon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint streak = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float reactorX, reactorY, reactorScale;
    /** When the last kick started; far enough back that none shows before the first. */
    private long kickAt = -KICK_MS;

    MarvelOverlay(Context context, boolean day) {
        super(context, day);
        Matrix turn = new Matrix();
        Path one = new Path();
        one.addRoundRect(new RectF(-3f, -42f, 3f, -32f), 1f, 1f, Path.Direction.CW);
        for (int i = 0; i < 10; i++) {
            turn.setRotate(36f * i);
            segments.addPath(one, turn);
        }
        stroke(ringOuter, 2f, 0.9f);
        stroke(ringMid, 1f, 0.5f);
        stroke(ringInner, 3f, 0.8f);
        segment.setColor(PALE);
        segment.setAlpha((int) (255 * 0.95f));
        // The mockup's core: white, pale blue at 40 %, rgba(120,200,255,.6) at 70 %, gone at the edge.
        coreGlow.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xFFFFFFFF, PALE, 0x9978C8FF, 0x0078C8FF},
                new float[]{0f, 0.4f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        beacon.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xFFFFFFFF, 0x8C9FDCFF, 0x339FDCFF, 0x009FDCFF},
                new float[]{0f, 0.3f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        // The streak's ellipse (#ffe6c0 .7 to #ff8c46 .18 to nothing); the paint's alpha does the breathing.
        streak.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xB3FFE6C0, 0x2EFF8C46, 0x00FF8C46},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    private static void stroke(Paint p, float width, float alpha) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(BLUE);
        p.setAlpha((int) (255 * alpha));
    }

    @Override
    void pulse(int what) {
        // Standing still (reduced motion, animations off), no frames would follow to fade the flare out again.
        if (what != REACTOR_KICK || !isRunning()) return;
        kickAt = SystemClock.uptimeMillis();
    }

    @Override
    protected void layout(int w, int h) {
        float d = 0.10f * h;
        reactorX = 0.86f * w;
        reactorY = 0.5f * h;
        reactorScale = d / 100f;
        // The reactor with room for a kick's larger core.
        around(regions[0], reactorX, reactorY, d * 0.5f + 2f, d * 0.5f + 2f);
        around(regions[1], ux(BEACON_X), uy(BEACON_Y), BEACON_R * unit + 1f, BEACON_R * unit + 1f);
        if (day) {
            regions[2].setEmpty();
            return;
        }
        // The thin line across the whole width, brightest in the middle, as in the picture.
        line.setShader(new LinearGradient(0f, 0f, w, 0f, new int[]{0x00FFD9A0, 0xFFFFF5E5, 0x00FFD9A0}, null,
                Shader.TileMode.CLAMP));
        regions[2].set(0, (int) Math.floor(uy(STREAK_Y - STREAK_RY)) - 1, w,
                (int) Math.ceil(uy(STREAK_Y + STREAK_RY)) + 1);
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        if (!day) {
            int a = (int) (255 * (0.55f + 0.35f * wave(phase(now, STREAK_MS, 0))));
            streak.setAlpha(a);
            c.save();
            c.translate(ux(96f), uy(STREAK_Y));
            c.scale(STREAK_RX * unit, STREAK_RY * unit);
            c.drawCircle(0f, 0f, 1f, streak);
            c.restore();
            line.setAlpha(a);
            float half = LINE_H * unit / 2f;
            c.drawRect(0f, uy(STREAK_Y) - half, getWidth(), uy(STREAK_Y) + half, line);
        }

        // The beacon: from a quarter up to full and back every 2 s, a little weaker by day as in the picture.
        float b = (0.25f + 0.75f * wave(phase(now, BEACON_MS, 0))) * (day ? 0.6f : 1f);
        beacon.setAlpha((int) (255 * b));
        c.save();
        c.translate(ux(BEACON_X), uy(BEACON_Y));
        float br = BEACON_R * unit;
        c.scale(br, br);
        c.drawCircle(0f, 0f, 1f, beacon);
        c.restore();

        c.save();
        c.translate(reactorX, reactorY);
        c.scale(reactorScale, reactorScale);
        c.save();
        c.rotate(360f * phase(now, TURN_MS, 0));
        c.drawCircle(0f, 0f, 46f, ringOuter);
        c.drawCircle(0f, 0f, 40f, ringMid);
        c.drawPath(segments, segment);
        c.drawCircle(0f, 0f, 26f, ringInner);
        c.restore();
        // The core: the mockup's pulse (0.75 to full), and a kick that flares it larger for a moment.
        float pulse = 0.75f + 0.25f * wave(phase(now, CORE_MS, 0));
        float kick = now - kickAt < KICK_MS ? 1f - (now - kickAt) / (float) KICK_MS : 0f;
        coreGlow.setAlpha((int) (255 * Math.min(1f, pulse + kick)));
        // A kick flares the core to 1.6 times its size (spec § 4.6), shrinking back over KICK_MS.
        float r = 14f * (1f + 0.6f * kick);
        c.scale(r, r);
        c.drawCircle(0f, 0f, 1f, coreGlow);
        c.restore();
    }
}
