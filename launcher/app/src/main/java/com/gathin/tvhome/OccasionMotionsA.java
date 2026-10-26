package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The motion of Jahreskreis' first six occasions (spec § 4.2, § 4.5) over their pictures (launcher/worlds/seasons/
 * occasions_a.py), as the mockup's painters have it (jahreskreis-mockup.html, PAINT.newyear/carnival/valentine/easter/
 * mothers/fathers): fireworks over the skyline, streamers swaying and confetti falling on the carnival stage, the
 * Valentine's candle flickering and rose petals drifting down, two butterflies over the Easter eggs, motes of light
 * and now and then a falling petal at the Mother's Day window, clouds and dandelion seeds drifting over the Father's
 * Day meadow. A little livelier than the seasons, still calm; the picture is whole without them (reduced motion).
 *
 * <p>Built like {@link SeasonMotions}: the mockup's seeded particles (SeasonMotions.particles), shapes in the 192 x 108
 * units of the overlay, paints and shaders made in layout and never per frame, nothing drawn before the first tick, and
 * nothing moves behind the dock (its top edge at y 766 of 1080) or, where it would cover the greeting and the clock,
 * in the header on the top left (x below 76, y below 34 units). The bright looks (Valentine, Easter, Mother's and
 * Father's Day) are dimmed by the scene at night; their motion is then drawn at {@link SeasonMotions#NIGHT}.
 */
final class OccasionMotionsA {
    /** The header on the top left (greeting, clock, date, weather) as fractions of the width and height. */
    static final float HEADER_RIGHT = 76f / 192f, HEADER_BOTTOM = 34f / 108f;

    private OccasionMotionsA() {
    }

    /** The occasion's motion, or null for a look that is not one of these six. */
    static LookMotion of(Look look) {
        if (look == null) return null;
        switch (look) {
            case NEW_YEAR: return new Fireworks();
            case CARNIVAL: return new Carnival();
            case VALENTINE: return new Valentine();
            case EASTER: return new Butterflies();
            case MOTHERS_DAY: return new Motes();
            case FATHERS_DAY: return new Meadow();
            default: return null;
        }
    }

    /** How bright a look's motion is drawn: bright looks as dimmed as their scene at night, the others always fully. */
    static float brightness(Look look, boolean day) {
        return look.bright && !day ? SeasonMotions.NIGHT : 1f;
    }

    /** Whether a point (fractions of the width and height) lies in the header on the top left. */
    static boolean inHeader(float x, float y) {
        return x < HEADER_RIGHT && y < HEADER_BOTTOM;
    }

    /**
     * Falling particles drawn as turned rectangles or ovals: confetti and petals (SeasonMotions' fall and sway); also
     * the birthday's confetti (PersonalMotions).
     */
    static final class Falling {
        final float[][] q;
        final float[] x, y, alpha, turn, flip;
        final float speed, spin;
        /** The fall's span across, fractions of the width: by default the whole width (see {@link #across}). */
        private float left = 0f, right = 1f;

        Falling(int n, long seed, float speed, float spin) {
            q = SeasonMotions.particles(n, seed);
            x = new float[n];
            y = new float[n];
            alpha = new float[n];
            turn = new float[n];
            flip = new float[n];
            this.speed = speed;
            this.spin = spin;
        }

        /** Lets the particles fall only between these fractions of the width (sway included); returns this. */
        Falling across(float left, float right) {
            this.left = left + 0.02f;
            this.right = right - 0.02f;
            return this;
        }

        /** The leftmost a particle comes, as a fraction of the width (its sway of .02 included). */
        float leftmost() {
            return left - 0.02f;
        }

        void frame(SeasonsOverlay o, double t) {
            for (int i = 0; i < q.length; i++) {
                float[] p = q[i];
                float fy = SeasonMotions.fallY(p[1], p[2], t, speed);
                float fx = left + p[0] * (right - left) + 0.02f * (float) Math.sin(t * 1.3 + p[3] * WorldOverlay.TAU);
                x[i] = o.ux(fx * 192);
                y[i] = o.uy(fy * 108);
                alpha[i] = SeasonMotions.fallAlpha(fy);
                turn[i] = (float) (Math.toDegrees(t * spin + p[3] * 6) % 360);
                // A piece of paper turning over as it falls: its height shrinks and grows again.
                flip[i] = 0.35f + 0.65f * Math.abs((float) Math.cos(t * 2.1 + p[3] * 9));
            }
        }

        /** Draws the particles as confetti: {@code rect} turned and flipping over, coloured by their size. */
        void drawConfetti(Canvas c, Paint bit, RectF rect, int[] colours, float bright) {
            for (int i = 0; i < q.length; i++) {
                if (alpha[i] <= 0f) continue;
                bit.setColor(colours[Math.min(colours.length - 1, (int) (q[i][2] * colours.length))]);
                bit.setAlpha((int) (255 * alpha[i] * bright));
                c.save();
                c.translate(x[i], y[i]);
                c.rotate(turn[i]);
                c.scale(1f, flip[i]);
                c.drawRect(rect, bit);
                c.restore();
            }
        }
    }

    /**
     * New Year: the mockup's three fireworks (PAINT.newyear) over the skyline, taking turns: one burst every
     * {@link #SPACING} s, at the next of the three places, so each place bursts every {@link #PERIOD} s and the sky is
     * still between two bursts (spec § 4.5, calm). A spark rises for {@link #RISE} s and bursts into a ring of 28 sparks and an inner ring of 14 that spread, sink a
     * little and fade within {@link #BURST} s, under a faint glow of their colour. Short and small (at most
     * {@link #R_MAX} of the width across), so a burst never lights up the screen; right of the header, above the dock.
     */
    static final class Fireworks implements LookMotion {
        static final float[] X = {0.48f, 0.66f, 0.84f}, Y = {0.22f, 0.30f, 0.26f};
        /**
         * One burst every SPACING seconds (the mockup's 3.2 s), the places in turn: each place's cycle is three times as
         * long, shifted by one SPACING per place ((t + offset) % PERIOD), so place 0 bursts at 0 s, 1 at 3.2 s, 2 at 6.4 s.
         */
        static final double SPACING = 3.2, PERIOD = 3 * SPACING;
        static final double[] OFFSET = {0, 2 * SPACING, SPACING};
        /** How long a spark rises and a burst lasts, in seconds; and from how far below its burst a spark rises. */
        static final double RISE = 0.35, BURST = 1.1;
        static final float RISE_FROM = 0.18f;
        /** A burst's largest radius as a fraction of the width, its glow's reach (times the radius), how far it sinks
         * (a fraction of the height) and a spark's radius (a fraction of the width, the mockup's w * .0025). */
        static final float R_MAX = 0.055f, GLOW = 1.2f, DROOP = 0.025f, SPARK = 0.0025f;
        static final int OUTER = 28, INNER = 14;
        private static final int[] COLOURS = {0xFFFF78B4, 0xFFFFD666, 0xFF96C8FF};
        private static final int[] LIGHT = {0xFFFFC8E0, 0xFFFFF0C0, 0xFFDCEBFF};
        private static final float[] COS = new float[OUTER], SIN = new float[OUTER];

        static {
            for (int k = 0; k < OUTER; k++) {
                COS[k] = (float) Math.cos(k * WorldOverlay.TAU / OUTER);
                SIN[k] = (float) Math.sin(k * WorldOverlay.TAU / OUTER);
            }
        }

        /** Per firework this frame: 0 nothing, 1 rising, 2 bursting; where (view pixels), radius, alpha. */
        private final int[] state = new int[X.length];
        private final float[] cx = new float[X.length], cy = new float[X.length], radius = new float[X.length],
                alpha = new float[X.length];
        private boolean ready;
        private SeasonsOverlay o;
        private Paint spark, trail;
        private final Paint[] glow = new Paint[X.length];

        /** Seconds into firework i's cycle at t. */
        static double age(double t, int i) {
            double a = (t + OFFSET[i]) % PERIOD;
            return a < 0 ? a + PERIOD : a;
        }

        /** Whether firework i is rising or bursting at t. */
        static boolean active(double t, int i) {
            return age(t, i) < RISE + BURST;
        }

        /** How far a burst has spread at u (0..1 of its time): fast at first, easing out. */
        static float spread(float u) {
            float v = 1f - Math.max(0f, Math.min(1f, u));
            return 1f - v * v * v;
        }

        /** How bright a burst is at u (0..1 of its time): full at once, then fading to nothing at its end. */
        static float fade(float u) {
            float v = 1f - Math.max(0f, Math.min(1f, u));
            return (float) Math.pow(v, 1.5);
        }

        /** The lowest point any firework reaches, as a fraction of the height (16:9): the start of its rise. */
        static float lowest() {
            float low = 0f;
            for (int i = 0; i < X.length; i++) {
                low = Math.max(low, Math.max(Y[i] + RISE_FROM, Y[i] + (R_MAX * GLOW) * 16f / 9f + DROOP));
            }
            return low;
        }

        /** The leftmost point any firework reaches, as a fraction of the width: its glow's edge. */
        static float leftmost() {
            float left = 1f;
            for (float x : X) left = Math.min(left, x - R_MAX * GLOW);
            return left;
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            if (spark == null) {
                spark = new Paint(Paint.ANTI_ALIAS_FLAG);
                trail = new Paint(Paint.ANTI_ALIAS_FLAG);
                trail.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < X.length; i++) {
                    // One glow of radius 1 round the origin in the burst's colour, scaled to the burst while drawing.
                    glow[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
                    glow[i].setShader(new RadialGradient(0f, 0f, 1f, new int[]{COLOURS[i], COLOURS[i] & 0x00FFFFFF},
                            null, Shader.TileMode.CLAMP));
                }
            }
            trail.setStrokeWidth(Math.max(1f, SPARK * 192 * o.unit));
            float glowR = R_MAX * GLOW * 192 * o.unit, pad = 2f + SPARK * 192 * o.unit;
            for (int i = 0; i < X.length; i++) {
                float x = o.ux(X[i] * 192), y = o.uy(Y[i] * 108);
                o.regions[i].set((int) Math.floor(x - glowR - pad), (int) Math.floor(y - glowR - pad),
                        (int) Math.ceil(x + glowR + pad),
                        (int) Math.ceil(Math.max(o.uy((Y[i] + RISE_FROM) * 108), y + glowR + DROOP * 108 * o.unit)
                                + pad));
            }
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            for (int i = 0; i < X.length; i++) {
                double a = age(t, i);
                float x = o.ux(X[i] * 192);
                if (a < RISE) {
                    // The spark rises, slowing as it nears the top.
                    float u = (float) (a / RISE);
                    state[i] = 1;
                    cx[i] = x;
                    cy[i] = o.uy((Y[i] + RISE_FROM * (1f - spread(u))) * 108);
                    alpha[i] = 0.5f + 0.5f * u;
                } else if (a < RISE + BURST) {
                    float u = (float) ((a - RISE) / BURST);
                    state[i] = 2;
                    cx[i] = x;
                    cy[i] = o.uy((Y[i] + DROOP * u * u) * 108);
                    radius[i] = R_MAX * 192 * o.unit * spread(u);
                    alpha[i] = fade(u);
                } else {
                    state[i] = 0;
                }
            }
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || spark == null) return;
            float dot = SPARK * 192 * o.unit;
            for (int i = 0; i < X.length; i++) {
                if (state[i] == 0) continue;
                if (state[i] == 1) {
                    trail.setColor(LIGHT[i]);
                    trail.setAlpha((int) (140 * alpha[i]));
                    c.drawLine(cx[i], cy[i], cx[i], cy[i] + 6 * dot, trail);
                    spark.setColor(LIGHT[i]);
                    spark.setAlpha((int) (255 * alpha[i]));
                    c.drawCircle(cx[i], cy[i], dot, spark);
                    continue;
                }
                float r = radius[i], a = alpha[i];
                // The faint glow of the burst (the mockup's alpha .18).
                glow[i].setAlpha((int) (255 * 0.18f * a));
                c.save();
                c.translate(cx[i], cy[i]);
                c.scale(r * GLOW + 1f, r * GLOW + 1f);
                c.drawCircle(0f, 0f, 1f, glow[i]);
                c.restore();
                float size = dot * (0.6f + 0.4f * a);
                spark.setColor(COLOURS[i]);
                spark.setAlpha((int) (255 * a));
                for (int k = 0; k < OUTER; k++) c.drawCircle(cx[i] + COS[k] * r, cy[i] + SIN[k] * r, size, spark);
                spark.setColor(LIGHT[i]);
                spark.setAlpha((int) (230 * a));
                for (int k = 0; k < OUTER; k += 2) {
                    c.drawCircle(cx[i] + COS[k] * r * 0.55f, cy[i] + SIN[k] * r * 0.55f, size * 0.8f, spark);
                }
            }
        }
    }

    /**
     * Carnival: seven streamers hanging from the top on the right, right of the header, swaying as the mockup's do
     * (sin(y * 9 + t + i), fixed at their top), and the mockup's sixty pieces of confetti (seed 13) falling, turning and
     * flipping over, between the top and just above the dock.
     */
    static final class Carnival implements LookMotion {
        static final float[] STREAMER_X = {0.46f, 0.54f, 0.62f, 0.70f, 0.78f, 0.86f, 0.94f};
        /** Each streamer's length as a fraction of the height; they hang from just above the top. */
        static final float[] STREAMER_LENGTH = {0.40f, 0.28f, 0.48f, 0.34f, 0.44f, 0.26f, 0.38f};
        static final float STREAMER_TOP = -0.02f, SWAY = 0.03f;
        static final int SEGMENTS = 24, CONFETTI = 60;
        private static final int[] STREAMER_COLOURS = {0xFFFF3B5C, 0xFFFFD23F, 0xFF3EC6FF, 0xFF7CFF6B};
        private static final int[] CONFETTI_COLOURS = {0xFFFF3B5C, 0xFFFFD23F, 0xFF3EC6FF, 0xFF7CFF6B, 0xFFFFFFFF};
        /** A piece of confetti's half width and half height, fractions of the width (the mockup's .008 x .004). */
        static final float BIT_W = 0.004f, BIT_H = 0.002f;

        private final Falling confetti = new Falling(CONFETTI, 13, 0.05f, 3f);
        /** Each streamer's segments as x0, y0, x1, y1 for drawLines. */
        private final float[][] lines = new float[STREAMER_X.length][SEGMENTS * 4];
        private boolean ready;
        private SeasonsOverlay o;
        private Paint streamer, bit;
        private RectF rect;

        /** The lowest a streamer reaches, as a fraction of the height. */
        static float streamerReach() {
            float low = 0f;
            for (float l : STREAMER_LENGTH) low = Math.max(low, STREAMER_TOP + l);
            return low;
        }

        /** How far a streamer sways at y (0 at its top, 1 at its end): fixed where it hangs, free at its end. */
        static float swayAt(float along) {
            return SWAY * Math.max(0f, Math.min(1f, along));
        }

        int confettiCount() {
            return confetti.q.length;
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            if (streamer == null) {
                streamer = new Paint(Paint.ANTI_ALIAS_FLAG);
                streamer.setStyle(Paint.Style.STROKE);
                streamer.setStrokeCap(Paint.Cap.ROUND);
                bit = new Paint(Paint.ANTI_ALIAS_FLAG);
                rect = new RectF();
            }
            streamer.setStrokeWidth(Math.max(1.5f, 0.003f * 192 * o.unit));
            rect.set(-BIT_W * 192 * o.unit, -BIT_H * 192 * o.unit, BIT_W * 192 * o.unit, BIT_H * 192 * o.unit);
            SeasonMotions.fallRegion(o, w, h, BIT_W * 192 * o.unit + 2, o.regions[0]);
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            for (int i = 0; i < STREAMER_X.length; i++) {
                float[] seg = lines[i];
                float len = STREAMER_LENGTH[i];
                float px = 0, py = 0;
                for (int k = 0; k <= SEGMENTS; k++) {
                    float along = k / (float) SEGMENTS, fy = STREAMER_TOP + along * len;
                    float fx = STREAMER_X[i] + swayAt(along) * (float) Math.sin(fy * 9 + t * 0.9 + i)
                            + 0.004f * (float) Math.sin(fy * 40 + i * 2);
                    float x = o.ux(fx * 192), y = o.uy(fy * 108);
                    if (k > 0) {
                        int j = (k - 1) * 4;
                        seg[j] = px;
                        seg[j + 1] = py;
                        seg[j + 2] = x;
                        seg[j + 3] = y;
                    }
                    px = x;
                    py = y;
                }
            }
            confetti.frame(o, t);
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || streamer == null) return;
            for (int i = 0; i < STREAMER_X.length; i++) {
                streamer.setColor(STREAMER_COLOURS[i % STREAMER_COLOURS.length]);
                streamer.setAlpha(210);
                c.drawLines(lines[i], streamer);
            }
            confetti.drawConfetti(c, bit, rect, CONFETTI_COLOURS, 1f);
        }
    }

    /**
     * Valentine's Day: the candle's flame flickers (the mockup's .85 + .15 sin(9t), with a quicker second beat) under
     * its warm glow, at the place occasions_a.py paints it (x .6 of the width, flame at .585 of the height), and the
     * mockup's twelve rose petals (seed 107) drift down slowly, turning.
     */
    static final class Valentine implements LookMotion {
        static final float FLAME_X = 115.2f / 192f, FLAME_Y = 63.2f / 108f;
        /** The flame's half width and half height at full size (the mockup's w * .004, h * .018) and its glow's
         * radius (w * .04), fractions of the width and height. */
        static final float FLAME_W = 0.004f, FLAME_H = 0.018f, GLOW = 0.04f;
        static final int PETALS = 12;

        private final Falling petals = new Falling(PETALS, 107, 0.02f, 1f);
        private boolean ready;
        private SeasonsOverlay o;
        private Paint petal, flame, glow;
        private RectF oval, flameOval;
        private float bright = 1f, flicker = 1f, lean;

        /** The flame's size at t (0.75 .. 1): the mockup's flicker with a quicker, smaller beat on top. */
        static float flicker(double t) {
            return (float) (0.875 + 0.1 * Math.sin(t * 9) + 0.025 * Math.sin(t * 23 + 1));
        }

        int petalCount() {
            return petals.q.length;
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            if (petal == null) {
                petal = new Paint(Paint.ANTI_ALIAS_FLAG);
                petal.setColor(0xFFDC2846);
                flame = new Paint(Paint.ANTI_ALIAS_FLAG);
                flame.setColor(0xFFFFD27A);
                glow = SeasonMotions.glow(0xFFFFBE64);
                oval = new RectF();
                flameOval = new RectF();
            }
            bright = brightness(Look.VALENTINE, o.isDay());
            float rx = 0.006f * 192 * o.unit, ry = 0.004f * 192 * o.unit;
            oval.set(-rx, -ry, rx, ry);
            // The petals' band reaches from the top to just above the dock and holds the candle as well.
            SeasonMotions.fallRegion(o, w, h, rx + 2, o.regions[0]);
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            petals.frame(o, t);
            flicker = flicker(t);
            lean = (float) Math.sin(t * 5.3) * 0.15f;
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || petal == null) return;
            SeasonMotions.drawFlame(c, flame, glow, flameOval, o.ux(FLAME_X * 192), o.uy(FLAME_Y * 108),
                    FLAME_W * 192 * o.unit, FLAME_H * 108 * o.unit, GLOW * 192 * o.unit, flicker, lean, bright);
            for (int i = 0; i < petals.q.length; i++) {
                if (petals.alpha[i] <= 0f) continue;
                petal.setAlpha((int) (255 * 0.8f * petals.alpha[i] * bright));
                c.save();
                c.translate(petals.x[i], petals.y[i]);
                c.rotate(petals.turn[i]);
                c.drawOval(oval, petal);
                c.restore();
            }
        }
    }

    /**
     * Easter: the mockup's two butterflies, yellow and lilac, fluttering over the eggs (x .55 and .8 of the width plus
     * sin(.6t + i) * .06, y .5 + cos(.9t + i) * .04 of the height), their wings opening and closing (|sin(8t + i)|).
     */
    static final class Butterflies implements LookMotion {
        static final float[] BX = {0.55f, 0.80f};
        static final float BY = 0.5f, RANGE_X = 0.06f, RANGE_Y = 0.04f;
        /** A wing's half width open and its half height, fractions of the width (the mockup's .008 and .006, a little
         * larger so they read on the TV). */
        static final float WING_W = 0.011f, WING_H = 0.0085f;
        private static final int[] WINGS = {0xFFFFCF6B, 0xFFB9A3FF};
        private static final int[] LOWER = {0xFFE8A84A, 0xFF9A82E8};

        private final float[] x = new float[BX.length], y = new float[BX.length], open = new float[BX.length];
        private boolean ready;
        private SeasonsOverlay o;
        private Paint wing, body;
        private RectF oval;
        private float bright = 1f;

        /** The lowest point a butterfly reaches, as a fraction of the height (16:9). */
        static float lowest() {
            return BY + RANGE_Y + (WING_H * 1.6f) * 16f / 9f;
        }

        /** The leftmost point a butterfly reaches, as a fraction of the width. */
        static float leftmost() {
            return BX[0] - RANGE_X - 2 * WING_W;
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            if (wing == null) {
                wing = new Paint(Paint.ANTI_ALIAS_FLAG);
                body = new Paint(Paint.ANTI_ALIAS_FLAG);
                body.setColor(0xFF3A2A20);
                oval = new RectF();
            }
            bright = brightness(Look.EASTER, o.isDay());
            float ww = 2 * WING_W * 192 * o.unit + 4, wh = WING_H * 1.6f * 192 * o.unit + 4;
            for (int i = 0; i < BX.length; i++) {
                WorldOverlay.around(o.regions[i], o.ux(BX[i] * 192), o.uy(BY * 108),
                        RANGE_X * 192 * o.unit + ww, RANGE_Y * 108 * o.unit + wh);
            }
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            for (int i = 0; i < BX.length; i++) {
                x[i] = o.ux((BX[i] + RANGE_X * (float) Math.sin(t * 0.6 + i)) * 192);
                y[i] = o.uy((BY + RANGE_Y * (float) Math.cos(t * 0.9 + i)) * 108);
                open[i] = Math.abs((float) Math.sin(t * 8 + i));
            }
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || wing == null) return;
            float ww = WING_W * 192 * o.unit, wh = WING_H * 192 * o.unit, px = o.unit / 10f;
            for (int i = 0; i < BX.length; i++) {
                float f = open[i], half = ww * f + px;
                // The upper wings, then the smaller lower ones, either side of the body, as wide as they are open.
                wing.setColor(WINGS[i]);
                wing.setAlpha((int) (255 * bright));
                for (int side = -1; side <= 1; side += 2) {
                    float cx = x[i] + side * ww * f;
                    oval.set(cx - half, y[i] - wh * 1.1f, cx + half, y[i] + wh * 0.5f);
                    c.drawOval(oval, wing);
                }
                wing.setColor(LOWER[i]);
                wing.setAlpha((int) (255 * bright));
                for (int side = -1; side <= 1; side += 2) {
                    float cx = x[i] + side * ww * f * 0.7f;
                    oval.set(cx - half * 0.7f, y[i], cx + half * 0.7f, y[i] + wh * 1.4f);
                    c.drawOval(oval, wing);
                }
                body.setAlpha((int) (255 * bright));
                oval.set(x[i] - px * 1.5f, y[i] - wh * 0.9f, x[i] + px * 1.5f, y[i] + wh * 1.2f);
                c.drawOval(oval, body);
            }
        }
    }

    /**
     * Mother's Day: fourteen motes of light (seed 117) hanging in the window's light, drifting and twinkling slowly,
     * and now and then a petal from the bunch sailing down onto the table: three petals, each once every
     * {@link #PETAL_PERIOD} s, falling for {@link #PETAL_FALL} s and lying a moment before they fade.
     */
    static final class Motes implements LookMotion {
        static final int MOTES = 14, PETALS = 3;
        /** The window's light: where the motes hang, fractions of the width and height. */
        static final float LEFT = 0.53f, RIGHT = 0.79f, TOP = 0.18f, BOTTOM = 0.60f;
        static final float MOTE = 0.0022f;
        /** The bunch the petals fall from (occasions_a.py: the vase at x 134.4, flowers from y 37 to 53 units), the
         * table they land on (its top from y 71.3, they lie at 74) and their timing in seconds. */
        static final float PETAL_X = 134.4f / 192f, PETAL_FROM = 0.44f, PETAL_TO = 0.685f;
        static final double PETAL_PERIOD = 14, PETAL_FALL = 6, PETAL_REST = 2;
        private static final int[] PETAL_COLOURS = {0xFFE86A92, 0xFFF2EEE6, 0xFFE8BC40};
        private static final double[] PETAL_OFFSET = {0, 4.7, 9.3};

        private final float[][] q = SeasonMotions.particles(MOTES, 117);
        private final float[][] pq = SeasonMotions.particles(PETALS, 119);
        private final float[] mx = new float[MOTES], my = new float[MOTES], ma = new float[MOTES];
        private final float[] px = new float[PETALS], py = new float[PETALS], pa = new float[PETALS],
                pturn = new float[PETALS];
        private boolean ready;
        private SeasonsOverlay o;
        private Paint glow, petal;
        private RectF oval;
        private float bright = 1f, moteR;

        /**
         * Where a petal is at {@code age} seconds into its cycle, as a fraction of the height (NaN while it is not
         * there): falling from the bunch to the table, eased, then lying on it until it fades.
         */
        static float petalY(double age) {
            if (age < 0 || age >= PETAL_FALL + PETAL_REST) return Float.NaN;
            double u = Math.min(1, age / PETAL_FALL);
            return (float) (PETAL_FROM + (PETAL_TO - PETAL_FROM) * (u * u * (3 - 2 * u)));
        }

        /** A petal's alpha at {@code age}: there while it falls, fading while it lies. */
        static float petalAlpha(double age) {
            if (age < 0 || age >= PETAL_FALL + PETAL_REST) return 0f;
            if (age < 0.4) return (float) (age / 0.4);
            if (age <= PETAL_FALL) return 1f;
            return (float) (1 - (age - PETAL_FALL) / PETAL_REST);
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            if (glow == null) {
                glow = new Paint(Paint.ANTI_ALIAS_FLAG);
                glow.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xFFFFF0C8, 0x00FFF0C8}, null,
                        Shader.TileMode.CLAMP));
                petal = new Paint(Paint.ANTI_ALIAS_FLAG);
                oval = new RectF();
            }
            bright = brightness(Look.MOTHERS_DAY, o.isDay());
            moteR = MOTE * 192 * o.unit * 3;
            float rx = 0.005f * 192 * o.unit, ry = 0.0028f * 192 * o.unit;
            oval.set(-rx, -ry, rx, ry);
            o.regions[0].set((int) Math.floor(o.ux((LEFT - 0.02f) * 192) - moteR),
                    (int) Math.floor(o.uy((TOP - 0.03f) * 108) - moteR),
                    (int) Math.ceil(o.ux((RIGHT + 0.02f) * 192) + moteR),
                    (int) Math.ceil(o.uy((BOTTOM + 0.03f) * 108) + moteR));
            float reach = 0.06f * 192 * o.unit + rx;
            o.regions[1].set((int) Math.floor(o.ux(PETAL_X * 192) - reach), (int) Math.floor(o.uy(PETAL_FROM * 108) - rx),
                    (int) Math.ceil(o.ux(PETAL_X * 192) + reach), (int) Math.ceil(o.uy(PETAL_TO * 108) + rx));
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            for (int i = 0; i < MOTES; i++) {
                float[] p = q[i];
                double ph = p[3] * WorldOverlay.TAU;
                float fx = LEFT + p[0] * (RIGHT - LEFT) + 0.015f * (float) Math.sin(t * 0.15 + ph);
                float fy = TOP + p[1] * (BOTTOM - TOP) + 0.02f * (float) Math.sin(t * 0.11 + ph * 1.3);
                mx[i] = o.ux(fx * 192);
                my[i] = o.uy(fy * 108);
                ma[i] = (0.35f + 0.35f * (float) Math.sin(t * 1.1 + ph)) * (0.5f + 0.5f * p[2]);
            }
            for (int i = 0; i < PETALS; i++) {
                double age = (t + PETAL_OFFSET[i]) % PETAL_PERIOD;
                float fy = petalY(age);
                pa[i] = petalAlpha(age);
                if (pa[i] <= 0f) continue;
                float[] p = pq[i];
                double u = Math.min(1, age / PETAL_FALL);
                // Each petal starts somewhere in the bunch and sways as it sails down; lying, it keeps still.
                float fx = PETAL_X + (p[0] - 0.5f) * 0.06f + 0.012f * (float) Math.sin(u * 7 + p[3] * 6);
                px[i] = o.ux(fx * 192);
                py[i] = o.uy(fy * 108);
                pturn[i] = (float) (Math.toDegrees(u * 5 + p[3] * 6) % 360);
            }
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || glow == null) return;
            for (int i = 0; i < MOTES; i++) {
                glow.setAlpha((int) (255 * ma[i] * bright));
                c.save();
                c.translate(mx[i], my[i]);
                c.scale(moteR, moteR);
                c.drawCircle(0f, 0f, 1f, glow);
                c.restore();
            }
            for (int i = 0; i < PETALS; i++) {
                if (pa[i] <= 0f) continue;
                petal.setColor(PETAL_COLOURS[i]);
                petal.setAlpha((int) (255 * pa[i] * bright));
                c.save();
                c.translate(px[i], py[i]);
                c.rotate(pturn[i]);
                c.drawOval(oval, petal);
                c.restore();
            }
        }
    }

    /**
     * Father's Day: the mockup's four clouds (seed 127) drifting slowly to the right (.006 of the width a second) in
     * the sky right of the header, coming in softly at {@link #CLOUD_LEFT}, and ten dandelion seeds (seed 131) floating
     * over the meadow to the right, rising and sinking a little.
     */
    static final class Meadow implements LookMotion {
        static final int CLOUDS = 4, SEEDS = 10;
        /** Where clouds drift (fractions of the width, the right edge plus a cloud's width) and their rows. */
        static final float CLOUD_LEFT = 0.45f, CLOUD_RIGHT = 1.1f, CLOUD_TOP = 0.12f, CLOUD_ROWS = 0.2f;
        static final float CLOUD_SPEED = 0.006f, CLOUD_FADE = 0.1f;
        /** A cloud's puffs: x, y (times .03 of the width) and size (times .025 of the width), the mockup's. */
        private static final float[][] PUFFS = {{0, 0, 1}, {0.7f, 0.1f, 0.8f}, {-0.7f, 0.1f, 0.75f}, {0.2f, -0.3f, 0.7f}};
        /** Where the seeds float, fractions of the width and height. */
        static final float SEED_LEFT = 0.45f, SEED_SPAN = 0.6f, SEED_TOP = 0.44f, SEED_ROWS = 0.15f;

        private final float[][] cq = SeasonMotions.particles(CLOUDS, 127);
        private final float[][] sq = SeasonMotions.particles(SEEDS, 131);
        private final float[] cx = new float[CLOUDS], cy = new float[CLOUDS], ca = new float[CLOUDS];
        private final float[] sx = new float[SEEDS], sy = new float[SEEDS], sa = new float[SEEDS];
        private boolean ready;
        private SeasonsOverlay o;
        private Paint cloud, fluff, core;
        private Path puffs;
        private float bright = 1f, fluffR;

        /** A cloud's x (fraction of the width) at t: drifting right from CLOUD_LEFT, coming in again on the left. */
        static float cloudX(float x0, double t) {
            double span = CLOUD_RIGHT - CLOUD_LEFT;
            double x = (x0 * span + t * CLOUD_SPEED) % span;
            if (x < 0) x += span;
            return (float) (CLOUD_LEFT + x);
        }

        /** A cloud's alpha at x: coming in softly over CLOUD_FADE right of CLOUD_LEFT, so none pops up. */
        static float cloudAlpha(float x) {
            return Math.max(0f, Math.min(1f, (x - CLOUD_LEFT) / CLOUD_FADE));
        }

        /** The lowest a seed floats, as a fraction of the height. */
        static float seedReach() {
            return SEED_TOP + SEED_ROWS + 0.015f + 0.01f;
        }

        /** A cloud's reach round its centre as a fraction of the width: its widest puff's edge. */
        static float cloudReach() {
            return 0.7f * 0.03f + 0.025f * 0.8f;
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            if (cloud == null) {
                cloud = new Paint(Paint.ANTI_ALIAS_FLAG);
                cloud.setColor(0xFFF4F6FA);
                fluff = new Paint(Paint.ANTI_ALIAS_FLAG);
                fluff.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xFFFFFFFF, 0x00FFFFFF}, null,
                        Shader.TileMode.CLAMP));
                core = new Paint(Paint.ANTI_ALIAS_FLAG);
                core.setColor(0xFFF8F4E8);
                puffs = new Path();
            }
            bright = brightness(Look.FATHERS_DAY, o.isDay());
            // One cloud round the origin: its puffs as one path (they overlap without doubling the alpha).
            puffs.reset();
            float u = 192 * o.unit;
            for (float[] p : PUFFS) puffs.addCircle(p[0] * 0.03f * u, p[1] * 0.03f * u, 0.025f * u * p[2], Path.Direction.CW);
            fluffR = 0.004f * u;
            float reach = cloudReach() * u + 2;
            o.regions[0].set((int) Math.floor(o.ux(CLOUD_LEFT * 192) - reach), (int) Math.floor(o.uy(CLOUD_TOP * 108) - reach),
                    w, (int) Math.ceil(o.uy((CLOUD_TOP + CLOUD_ROWS) * 108) + reach));
            o.regions[1].set((int) Math.floor(o.ux(SEED_LEFT * 192) - fluffR - 2),
                    (int) Math.floor(o.uy((SEED_TOP - 0.02f) * 108) - fluffR), w,
                    (int) Math.ceil(o.uy(seedReach() * 108) + fluffR));
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            for (int i = 0; i < CLOUDS; i++) {
                float fx = cloudX(cq[i][0], t);
                cx[i] = o.ux(fx * 192);
                cy[i] = o.uy((CLOUD_TOP + cq[i][1] * CLOUD_ROWS) * 108);
                ca[i] = cloudAlpha(fx);
            }
            for (int i = 0; i < SEEDS; i++) {
                float[] p = sq[i];
                double x = (p[0] * SEED_SPAN + t * 0.012 * (0.5 + p[2])) % SEED_SPAN;
                float fx = SEED_LEFT + (float) x;
                float fy = SEED_TOP + p[1] * SEED_ROWS + 0.015f * (float) Math.sin(t * 0.8 + p[3] * WorldOverlay.TAU);
                sx[i] = o.ux(fx * 192);
                sy[i] = o.uy(fy * 108);
                // In softly on the left, out softly on the right.
                float in = (float) Math.min(1, x / 0.06), out = (float) Math.min(1, (SEED_SPAN - x) / 0.06);
                sa[i] = Math.min(in, out) * (0.55f + 0.3f * p[2]);
            }
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || cloud == null) return;
            for (int i = 0; i < CLOUDS; i++) {
                if (ca[i] <= 0f) continue;
                cloud.setAlpha((int) (255 * 0.5f * ca[i] * bright));
                c.save();
                c.translate(cx[i], cy[i]);
                c.drawPath(puffs, cloud);
                c.restore();
            }
            for (int i = 0; i < SEEDS; i++) {
                if (sa[i] <= 0f) continue;
                fluff.setAlpha((int) (255 * 0.6f * sa[i] * bright));
                c.save();
                c.translate(sx[i], sy[i]);
                c.scale(fluffR, fluffR);
                c.drawCircle(0f, 0f, 1f, fluff);
                c.restore();
                core.setAlpha((int) (255 * sa[i] * bright));
                c.drawCircle(sx[i], sy[i], fluffR * 0.25f, core);
            }
        }
    }
}
