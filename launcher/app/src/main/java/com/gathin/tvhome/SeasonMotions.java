package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The four seasons' motion over their scenes (spec § 4.1, § 4.5), as the mockup's painters have it
 * (jahreskreis-mockup.html, PAINT.winter/spring/summer/autumn): seventy snowflakes, forty cherry blossoms and
 * twenty-six leaves drifting down and swaying (blossoms and leaves turning as they fall), and in summer fourteen
 * fireflies blinking over the sea and eighteen lines of light glittering on the sun's (by night the moon's) path.
 * Calm on purpose: few, slow particles, as the mockup draws them, from its seeded random so they start where they do
 * there.
 *
 * <p>The mockup's positions are fractions of the width and height; they are drawn in the 192 x 108 units of the
 * overlay (its {@code ux}/{@code uy}/{@code unit}), so they sit on the picture as CENTER_CROP places it. Nothing falls
 * behind the dock: the particles fade out just above its top edge (y 766 of 1080) and come in again at the top, and
 * the fireflies and the glitter stay above it as well. By day the motion is as the mockup draws it, by night it is
 * dimmed ({@link #NIGHT}). Paints and shapes are made in layout, never per frame (and not in the constructor, so the
 * motions build in JVM tests).
 */
final class SeasonMotions {
    /** The dock's top edge as a fraction of the height (y 766 of 1080, spec § 3). */
    static final float DOCK = 766f / 1080f;
    /** Where falling things have faded out, just above the dock, and over how much of the height they fade. */
    static final float FALL_END = 0.70f, FALL_FADE = 0.08f;
    /** Falling things start this far above the top (a fraction of the height), so none pops in. */
    static final float FALL_START = -0.05f;
    /** The night versions draw the motion this much as bright as by day. */
    static final float NIGHT = 0.6f;

    private SeasonMotions() {
    }

    /** The season's motion, or null for a look that is not a season. */
    static LookMotion of(Look look) {
        if (look == null) return null;
        switch (look) {
            case WINTER: return new Fall(Fall.SNOW);
            case SPRING: return new Fall(Fall.BLOSSOM);
            case AUTUMN: return new Fall(Fall.LEAF);
            case SUMMER: return new Summer();
            default: return null;
        }
    }

    /**
     * The mockup's particles: {@code rng(seed)} (a linear congruential generator, s = s * 1664525 + 1013904223 mod
     * 2^32, s / 2^32) drawn four at a time into x, y, s (size and speed) and p (phase), each from 0 up to 1.
     */
    static float[][] particles(int n, long seed) {
        long s = seed & 0xFFFFFFFFL;
        float[][] out = new float[n][4];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < 4; k++) {
                s = (s * 1664525L + 1013904223L) & 0xFFFFFFFFL;
                out[i][k] = (float) (s / 4294967296.0);
            }
        }
        return out;
    }

    /**
     * Where a falling particle is at {@code t} seconds, as a fraction of the height: the mockup's fall
     * (q.y + t * speed * (.5 + q.s)) wrapped between {@link #FALL_START} and {@link #FALL_END} instead of the whole
     * screen, at the mockup's speed in heights per second.
     */
    static float fallY(float y0, float s, double t, float speed) {
        double span = FALL_END - FALL_START;
        double y = (y0 * span + t * speed * (0.5 + s)) % span;
        if (y < 0) y += span;
        return (float) (y + FALL_START);
    }

    /** How visible a falling particle is at height {@code y}: fully, then fading out to none at {@link #FALL_END}. */
    static float fallAlpha(float y) {
        return Math.max(0f, Math.min(1f, (FALL_END - y) / FALL_FADE));
    }

    /**
     * The band falling things move in, into {@code r}: the whole width from above the top to just above the dock,
     * widened by {@code reach} (a particle's size in view pixels).
     */
    static void fallRegion(SeasonsOverlay o, int w, int h, float reach, Rect r) {
        r.set(0, Math.max(0, (int) Math.floor(o.uy(FALL_START * 108) - reach)), w,
                Math.min(h, (int) Math.ceil(o.uy(FALL_END * 108) + reach)));
    }

    /** Snow, blossoms or leaves falling: the mockup's fall() with its sway, each kind with its look and count. */
    static final class Fall implements LookMotion {
        static final int SNOW = 0, BLOSSOM = 1, LEAF = 2;
        /** Per kind, from the mockup: count, seed, fall speed (heights per second), spin (radians per second). */
        private static final int[] COUNT = {70, 40, 26};
        private static final long[] SEED = {5, 3, 9};
        private static final float[] SPEED = {0.04f, 0.03f, 0.045f};
        private static final float[] SPIN = {0f, 1f, 1.5f};
        /** Sizes in widths (mockup): snow's radius .0015 + s * .002, a petal's and a leaf's half axes. */
        private static final float[][] SIZE = {{0.0015f, 0.002f}, {0.006f, 0.0035f}, {0.009f, 0.004f}};
        /** Snow white; blossoms pink and pale pink (s above .5); leaves orange, rust and amber by thirds of s. */
        private static final int[][] COLOURS = {{0xFFFFFFFF}, {0xFFFFB7CF, 0xFFFFD1E1},
                {0xFFE0782B, 0xFFC94A1F, 0xFFF0A53A}};
        /** Sideways sway: .02 of the width at 1.3 rad/s, as the mockup's fall(). */
        private static final float SWAY = 0.02f;
        private static final double SWAY_RATE = 1.3;

        final int kind;
        private final float[][] q;
        /** This frame's centres in view pixels, alpha (0..1) and turn in degrees; ready once the first frame ran. */
        private final float[] x, y, alpha, turn;
        private boolean ready;
        private SeasonsOverlay o;
        private Paint paint;
        private RectF oval;
        private float bright = 1f;
        /** Where this fall happens, fractions of the width and height: by default the whole width and the band from
         * FALL_START to FALL_END (see {@link #within}). */
        private float left = 0f, right = 1f, top = FALL_START, bottom = FALL_END;

        Fall(int kind) {
            this(kind, COUNT[kind], SEED[kind]);
        }

        /** This kind with another count and seed: the occasions' snow (OccasionMotionsB), as their mockup draws it. */
        Fall(int kind, int count, long seed) {
            this.kind = kind;
            q = particles(count, seed);
            x = new float[q.length];
            y = new float[q.length];
            alpha = new float[q.length];
            turn = new float[q.length];
        }

        int count() {
            return q.length;
        }

        /**
         * Lets the particles fall only inside this box (fractions of the width and height), at the same speed on
         * screen as in the whole band, fading out at its bottom: e.g. snow behind a window. Returns this fall.
         */
        Fall within(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            return this;
        }

        /** Where a height {@code fy} of the whole band (FALL_START .. FALL_END) lands in a box from top to bottom. */
        static float inBox(float fy, float top, float bottom) {
            return top + (fy - FALL_START) * (bottom - top) / (FALL_END - FALL_START);
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            if (paint == null) {
                paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                oval = new RectF();
            }
            bright = o.isDay() ? 1f : NIGHT;
            float[] size = SIZE[kind];
            // One oval round the origin, turned and moved to each petal or leaf while drawing.
            float rx = size[0] * 192 * o.unit, ry = size[1] * 192 * o.unit;
            oval.set(-rx, -ry, rx, ry);
            float reach = kind == SNOW ? (size[0] + size[1]) * 192 * o.unit : rx;
            fallRegion(o, w, h, reach, o.regions[0]);
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            // A smaller box falls through its band faster, so the particles move as fast on screen (1 for the band).
            float scale = (bottom - top) / (FALL_END - FALL_START);
            for (int i = 0; i < q.length; i++) {
                float[] p = q[i];
                float fy = fallY(p[1], p[2], t, SPEED[kind] / scale);
                float fx = left + p[0] * (right - left)
                        + SWAY * (float) Math.sin(t * SWAY_RATE + p[3] * WorldOverlay.TAU);
                x[i] = o.ux(fx * 192);
                y[i] = o.uy(inBox(fy, top, bottom) * 108);
                // Snow's alpha grows with its size, as the mockup's .5 + q.s * .5; petals and leaves are opaque.
                float a = kind == SNOW ? 0.5f + 0.5f * p[2] : 1f;
                alpha[i] = a * fallAlpha(fy) * bright;
                // Wrapped in double first: days of uptime in degrees would lose whole degrees as a float.
                turn[i] = (float) (Math.toDegrees(t * SPIN[kind] + p[3] * 6) % 360);
            }
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || paint == null) return;
            int[] colours = COLOURS[kind];
            for (int i = 0; i < q.length; i++) {
                if (alpha[i] <= 0f) continue;
                float s = q[i][2];
                int colour = kind == SNOW ? colours[0]
                        : kind == BLOSSOM ? colours[s > 0.5f ? 1 : 0]
                        : colours[Math.min(2, (int) (s * 3))];
                paint.setColor(colour);
                paint.setAlpha((int) (255 * alpha[i]));
                if (kind == SNOW) {
                    float r = (SIZE[SNOW][0] + s * SIZE[SNOW][1]) * 192 * o.unit;
                    c.drawCircle(x[i], y[i], r, paint);
                    continue;
                }
                c.save();
                c.translate(x[i], y[i]);
                c.rotate(turn[i]);
                c.drawOval(oval, paint);
                c.restore();
            }
        }
    }

    /**
     * Summer: the mockup's fourteen fireflies (seed 7) blinking (.5 + .5 sin(2t + p)) and wandering a little, between
     * {@link #FIREFLY_TOP} and {@link #FIREFLY_BOTTOM} of the height, and its eighteen lines of light on the water under
     * the sun (by night the moon) at .66 of the width, each swaying sideways, pressed into the band between the sea
     * line and the dock.
     */
    static final class Summer implements LookMotion {
        static final int FIREFLIES = 14, LINES = 18;
        static final float FIREFLY_TOP = 0.48f, FIREFLY_BOTTOM = 0.67f;
        /** The sea line (.601 of the height in seasons.py), the sun's x, and the glitter's first row and row step. */
        static final float SUN_X = 0.66f, GLITTER_TOP = 0.605f, GLITTER_STEP = 0.0055f;
        /** A firefly's glow radius as a fraction of the width (the mockup's w * .012), and how far one wanders. */
        static final float GLOW = 0.012f, WANDER_X = 0.008f, WANDER_Y = 0.01f;

        private final float[][] q = particles(FIREFLIES, 7);
        private final float[] fx = new float[FIREFLIES], fy = new float[FIREFLIES], fa = new float[FIREFLIES];
        private final float[] lineShift = new float[LINES], lineAlpha = new float[LINES];
        private boolean ready;
        private SeasonsOverlay o;
        private Paint glow, core, line;
        private float bright = 1f, glowR, px;

        /** The lowest a firefly's glow reaches, as a fraction of the height at 16:9: above the dock. */
        static float fireflyReach() {
            return FIREFLY_BOTTOM + WANDER_Y + GLOW * 16f / 9f;
        }

        /** The lowest glitter row's bottom, as a fraction of the height: above the dock too. */
        static float glitterReach() {
            return GLITTER_TOP + (LINES - 1) * GLITTER_STEP + 2f / 1080f;
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            boolean day = o.isDay();
            if (glow == null) {
                glow = new Paint(Paint.ANTI_ALIAS_FLAG);
                core = new Paint(Paint.ANTI_ALIAS_FLAG);
                line = new Paint();
                // One glow of radius 1 round the origin, scaled to each firefly: the mockup's rgba(255,240,150,.7) to 0.
                glow.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xFFFFF096, 0x00FFF096}, null,
                        Shader.TileMode.CLAMP));
                core.setColor(0xFFFFF6C8);
            }
            bright = day ? 1f : NIGHT;
            // Warm light from the sun by day, a cool silver from the moon by night.
            line.setColor(day ? 0xFFFFC882 : 0xFFC8D7FF);
            px = h / 1080f;
            glowR = GLOW * 192 * o.unit;
            o.regions[0].set(0, (int) Math.floor(o.uy((FIREFLY_TOP - WANDER_Y) * 108) - glowR), w,
                    (int) Math.ceil(o.uy((FIREFLY_BOTTOM + WANDER_Y) * 108) + glowR));
            float half = halfWidth(LINES - 1) * 192 * o.unit + 8 * px;
            o.regions[1].set((int) Math.floor(o.ux(SUN_X * 192) - half), (int) Math.floor(o.uy(GLITTER_TOP * 108)) - 1,
                    (int) Math.ceil(o.ux(SUN_X * 192) + half), (int) Math.ceil(o.uy(glitterReach() * 108)) + 1);
        }

        /** Half a glitter row's width as a fraction of the width: narrow at the sea line, wider towards the viewer. */
        static float halfWidth(int i) {
            return 0.03f + i * 0.004f;
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            for (int i = 0; i < FIREFLIES; i++) {
                float[] p = q[i];
                double ph = p[3] * WorldOverlay.TAU;
                fx[i] = o.ux((p[0] + WANDER_X * (float) Math.sin(t * 0.35 + ph)) * 192);
                fy[i] = o.uy((FIREFLY_TOP + p[1] * (FIREFLY_BOTTOM - FIREFLY_TOP)
                        + WANDER_Y * (float) Math.cos(t * 0.27 + ph * 1.1)) * 108);
                fa[i] = (0.5f + 0.5f * (float) Math.sin(t * 2 + ph)) * bright;
            }
            for (int i = 0; i < LINES; i++) {
                // The mockup's sideways sway (sin(2t + i) * 4 px), and a slow shimmer so the path glitters.
                lineShift[i] = (float) Math.sin(t * 2 + i) * 6f * px;
                float shimmer = 0.65f + 0.35f * (float) Math.sin(t * 3 + i * 1.7);
                lineAlpha[i] = Math.max(0f, 0.35f - i * 0.018f) * shimmer * bright;
            }
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || glow == null) return;
            float cx = o.ux(SUN_X * 192), thick = Math.max(1f, 2f * px);
            for (int i = 0; i < LINES; i++) {
                float half = halfWidth(i) * 192 * o.unit, top = o.uy((GLITTER_TOP + i * GLITTER_STEP) * 108);
                line.setAlpha((int) (255 * lineAlpha[i]));
                c.drawRect(cx - half + lineShift[i], top, cx + half + lineShift[i], top + thick, line);
            }
            for (int i = 0; i < FIREFLIES; i++) {
                glow.setAlpha((int) (255 * 0.7f * fa[i]));
                c.save();
                c.translate(fx[i], fy[i]);
                c.scale(glowR, glowR);
                c.drawCircle(0f, 0f, 1f, glow);
                c.restore();
                core.setAlpha((int) (255 * fa[i]));
                c.drawCircle(fx[i], fy[i], glowR * 0.12f, core);
            }
        }
    }
}
