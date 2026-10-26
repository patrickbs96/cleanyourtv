package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * The motion of Jahreskreis' personal days (spec § 4.3, § 4.5) over their pictures (launcher/worlds/seasons/
 * personal.py), as the mockup's painters have it (jahreskreis-mockup.html, PAINT.birthday, the anniversary painter and PAINT.wedding): on a
 * birthday the cake's four candles flicker, confetti falls all day and today's name stands large under the garland; on
 * an anniversary the projector in the sand throws a flickering holo heart with the number of years in it and the
 * stars twinkle; on a wedding day the arch's lights twinkle, a sparkle wanders over the two rings, petals float and the
 * names stand large over the arch.
 *
 * <p>The names and the year count come from the TV's entries (SeasonsToday.names(), years()), never from the code or
 * the pictures, which hold none. They are laid out once (layout: typeface, size, place) and drawn as they are, shrunk
 * to fit their width when they are long ({@link #fitSize}). Each motion poses itself at t = 0 in layout, so with reduced
 * motion (the overlay never ticks) the names, the number and the heart still show, as one still frame.
 *
 * <p>Built like {@link OccasionMotionsB}: the mockup's seeded particles (SeasonMotions.particles), shapes in the
 * 192 x 108 units of the overlay at the places personal.py paints them (the same numbers, named alike), paints, shaders
 * and paths made in layout and never per frame, nothing moves behind the dock (its top edge at y 766 of 1080) or in
 * the header on the top left. The bow that ties the balloons to the dock is drawn over the dock by
 * {@link BalloonBowView}, not here: this overlay lies under the dock.
 */
final class PersonalMotions {
    /** The header on the top left in units (greeting, clock, date, weather) and the dock's top edge. */
    static final float HEADER_RIGHT = OccasionMotionsA.HEADER_RIGHT * 192, HEADER_BOTTOM = OccasionMotionsA.HEADER_BOTTOM * 108;
    static final float DOCK_Y = SeasonMotions.DOCK * 108;
    /** A line of Outfit's height per em (ascent 1.0 and descent .2625): a label's box is its size times this. */
    static final float LINE = 1.27f;

    private PersonalMotions() {
    }

    /** The personal day's motion with today's names and years, or null for a look that is not a personal day. */
    static LookMotion of(Look look, SeasonsToday today) {
        if (look == null) return null;
        String names = today != null ? today.names() : "";
        switch (look) {
            case BIRTHDAY: return new Birthday(names);
            case ANNIVERSARY: return new Anniversary(today != null ? today.years() : 0);
            case WEDDING: return new Wedding(names);
            default: return null;
        }
    }

    /**
     * The size a line of text is drawn at: {@code wanted}, or smaller in proportion when it would be wider than
     * {@code maxWidth} at that size ({@code widthAtWanted}), so a long name never runs out of its place.
     */
    static float fitSize(float wanted, float widthAtWanted, float maxWidth) {
        if (widthAtWanted <= maxWidth || widthAtWanted <= 0f) return wanted;
        return wanted * maxWidth / widthAtWanted;
    }

    /** The typeface of the names and the number: Outfit Bold (or the locale's system family, fonts.xml). */
    private static Typeface bold(SeasonsOverlay o) {
        return UiFont.get(o.getContext(), R.string.font_bold);
    }

    /**
     * One line of text a motion draws: today's names or the year count, centred on x (units), its box's top at y or
     * its middle on y ({@code middle}), {@code size} units high per em and at most {@code maxWidth} units wide.
     */
    static final class Label {
        final String text;
        final float x, y, size, maxWidth;
        final boolean middle;
        final int colour;
        private Paint paint;
        private Paint.FontMetrics metrics;
        private float px, baseline;

        Label(String text, float x, float y, float size, float maxWidth, boolean middle, int colour) {
            this.text = text != null ? text : "";
            this.x = x;
            this.y = y;
            this.size = size;
            this.maxWidth = maxWidth;
            this.middle = middle;
            this.colour = colour;
        }

        /** Sets typeface, size (shrunk to fit) and baseline for this overlay; once per layout, never per frame. */
        void layout(SeasonsOverlay o, Typeface face) {
            if (paint == null) {
                paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
                paint.setTextAlign(Paint.Align.CENTER);
                metrics = new Paint.FontMetrics();
            }
            paint.setTypeface(face);
            paint.setColor(colour);
            float wanted = size * o.unit;
            paint.setTextSize(wanted);
            float fitted = fitSize(wanted, paint.measureText(text), maxWidth * o.unit);
            paint.setTextSize(fitted);
            // A soft shadow under the letters, as the mockup's text-shadow, so they read on any part of the picture.
            paint.setShadowLayer(fitted * 0.18f, 0f, fitted * 0.05f, 0x73000000);
            paint.getFontMetrics(metrics);
            px = o.ux(x);
            // A shrunk line keeps the middle of its box (its top at y, LINE times its size high).
            baseline = middle ? o.uy(y) - (metrics.ascent + metrics.descent) / 2
                    : o.uy(y) - metrics.ascent + (wanted - fitted) * LINE / 2;
        }

        void draw(Canvas c, float dy, float alpha) {
            if (paint == null || text.isEmpty()) return;
            paint.setAlpha((int) (255 * Math.max(0f, Math.min(1f, alpha))));
            c.drawText(text, px, baseline + dy, paint);
        }
    }

    /**
     * Birthday: the cake's four candles (personal.py: CAKE_*, CANDLE_DX, FLAME_*) flicker each at its own beat, the
     * mockup's seventy pieces of confetti (seed 51) fall all day right of the header, and today's name (or names)
     * stands large under the "BIRTHDAY" garland, right of the cake.
     */
    static final class Birthday implements LookMotion {
        static final float CAKE_X = 99.84f, CAKE_Y = 69.12f;
        /** The plate's right edge: the cake's widest point. */
        static final float CAKE_RIGHT = CAKE_X + 18.24f;
        static final float[] CANDLE_DX = {-9.6f, -3.264f, 3.264f, 9.6f};
        /** A flame's centre, half width and half height (the mockup's w * .004, h * .016) and its glow (w * .02). */
        static final float FLAME_Y = CAKE_Y - 19.98f, FLAME_W = 0.768f, FLAME_H = 1.728f, FLAME_GLOW = 3.84f;
        /** The "BIRTHDAY" garland (personal.py: GARLANDS): its string from X0 to X1, Y0 at the ends, sagging SAG; a
         * pennant hangs at most PENNANT_H below it. */
        static final float GARLAND_X0 = 96f, GARLAND_X1 = 189.12f, GARLAND_Y0 = 18.36f, GARLAND_SAG = 11.88f;
        static final float PENNANT_H = 1.7f * 0.022f * 192;
        /** The name: centred on NAME_X, its top at NAME_TOP, 6 % of the width per em, at most NAME_MAX wide (units). */
        static final float NAME_X = 152f, NAME_TOP = 45.4f, NAME_SIZE = 0.06f * 192, NAME_MAX = 72f;
        static final int CONFETTI = 70;
        /** The confetti falls right of this fraction of the width: clear of the header. */
        static final float CONFETTI_LEFT = 0.40f;
        /** A piece of confetti's half width and half height, fractions of the width (the mockup's .008 x .004). */
        static final float BIT_W = 0.004f, BIT_H = 0.002f;
        private static final int[] COLOURS = {0xFFFF8FB1, 0xFFFFE066, 0xFF8FD3FF, 0xFFB9A3FF, 0xFF7CFF9B};

        private final Label name;
        private final OccasionMotionsA.Falling confetti =
                new OccasionMotionsA.Falling(CONFETTI, 51, 0.05f, 3f).across(CONFETTI_LEFT, 1f);
        private final float[] size = new float[CANDLE_DX.length], lean = new float[CANDLE_DX.length];
        private boolean ready;
        private SeasonsOverlay o;
        private Paint flame, warm, bit;
        private RectF oval, rect;

        Birthday(String names) {
            name = new Label(names, NAME_X, NAME_TOP, NAME_SIZE, NAME_MAX, false, Look.BIRTHDAY.accent);
        }

        /** The text under the garland: today's names. */
        String text() {
            return name.text;
        }

        int confettiCount() {
            return confetti.q.length;
        }

        float confettiLeftmost() {
            return confetti.leftmost();
        }

        /** The garland's string at x (units). */
        static float garlandY(float x) {
            float u = (x - GARLAND_X0) / (GARLAND_X1 - GARLAND_X0);
            return GARLAND_Y0 + 4 * GARLAND_SAG * u * (1 - u);
        }

        /** The lowest the garland's pennants hang between x0 and x1 (units): its string plus a pennant. */
        static float garlandBottom(float x0, float x1) {
            float low = 0f;
            for (int k = 0; k <= 64; k++) low = Math.max(low, garlandY(x0 + (x1 - x0) * k / 64f));
            return low + PENNANT_H;
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            if (flame == null) {
                flame = new Paint(Paint.ANTI_ALIAS_FLAG);
                flame.setColor(0xFFFFD27A);
                warm = SeasonMotions.glow(0xFFFFC85A);
                bit = new Paint(Paint.ANTI_ALIAS_FLAG);
                oval = new RectF();
                rect = new RectF();
            }
            name.layout(o, bold(o));
            float u = 192 * o.unit;
            rect.set(-BIT_W * u, -BIT_H * u, BIT_W * u, BIT_H * u);
            // The confetti's band right of the header holds the candles as well.
            SeasonMotions.fallRegion(o, w, h, BIT_W * u + 2, o.regions[0]);
            o.regions[0].left = Math.max(0, (int) Math.floor(o.ux(confetti.leftmost() * 192) - BIT_W * u) - 2);
            if (!ready) frame(0);
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            confetti.frame(o, t);
            for (int i = 0; i < CANDLE_DX.length; i++) {
                size[i] = OccasionMotionsA.Valentine.flicker(t + i * 0.37);
                lean[i] = (float) Math.sin(t * 5.3 + i * 1.9) * 0.15f;
            }
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || flame == null) return;
            for (int i = 0; i < CANDLE_DX.length; i++) {
                SeasonMotions.drawFlame(c, flame, warm, oval, o.ux(CAKE_X + CANDLE_DX[i]), o.uy(FLAME_Y),
                        FLAME_W * o.unit, FLAME_H * o.unit, FLAME_GLOW * o.unit, size[i], lean[i], 1f);
            }
            name.draw(c, 0f, 1f);
            confetti.drawConfetti(c, bit, rect, COLOURS, 1f);
        }
    }

    /**
     * Anniversary ("two suns"): the projector in the sand (personal.py: PROJ_X, LENS_Y) throws a holo heart (the
     * mockup's heart curve, k = .0029 of the width, a little larger than its .0024 to read on the TV) floating over it, bobbing gently and flickering a little, with
     * scan lines in it and the number of years in its middle; the beam runs from the lens up to the heart's tip and
     * ends there. The mockup's forty stars (rng(61)) twinkle where personal.py paints them, none in the header.
     */
    static final class Anniversary implements LookMotion {
        static final float HEART_X = 157.44f, HEART_Y = 47.52f, HEART_K = 0.0029f * 192, BOB = 0.008f * 108;
        static final float LENS_X = HEART_X, LENS_Y = 75.6f - 2.85f, LENS_HALF = 0.77f;
        /** Half the beam's width where it meets the heart's tip, in heart units. */
        static final float BEAM_HALF = 6f;
        /** The number's size per em and its widest, in heart units. */
        static final float NUMBER_SIZE = 13f, NUMBER_MAX = 16f;
        static final int STARS = 40;
        static final float STARS_TOP = 0.4f, STAR_R = 0.18f;
        private static final int SAMPLES = 96;

        private final Label number;
        private final float[][] q = SeasonMotions.particles(STARS, 61);
        private final float[] twinkle = new float[STARS];
        private final boolean[] shown = new boolean[STARS];
        private boolean ready;
        private SeasonsOverlay o;
        private Paint star, fill, line, wide, scan, beam;
        private Paint halo;
        private Path heart, cone;
        private float[] scanLines;
        private float flicker = 1f, bob, k;

        Anniversary(int years) {
            number = new Label(String.valueOf(years), HEART_X, HEART_Y + numberY() * HEART_K, NUMBER_SIZE * HEART_K,
                    NUMBER_MAX * HEART_K, true, 0xFFD8ECFF);
            for (int i = 0; i < STARS; i++) shown[i] = !inHeader(q[i][0] * 192, q[i][1] * 108 * STARS_TOP);
        }

        /** The number in the heart: the year count. */
        String number() {
            return number.text;
        }

        int starsShown() {
            int n = 0;
            for (boolean s : shown) if (s) n++;
            return n;
        }

        static boolean inHeader(float x, float y) {
            return x < HEADER_RIGHT && y < HEADER_BOTTOM;
        }

        /** The mockup's heart at u (0 .. TAU), in heart units round its middle: x = 16 sin^3 u. */
        static float heartX(double u) {
            double s = Math.sin(u);
            return (float) (16 * s * s * s);
        }

        /** y = -(13 cos u - 5 cos 2u - 2 cos 3u - cos 4u), downwards. */
        static float heartY(double u) {
            return (float) -(13 * Math.cos(u) - 5 * Math.cos(2 * u) - 2 * Math.cos(3 * u) - Math.cos(4 * u));
        }

        /** The heart's tip, its lowest point (u = PI), and the top of its lobes, in heart units. */
        static float heartTip() {
            return heartY(Math.PI);
        }

        static float heartTop() {
            float top = Float.MAX_VALUE;
            for (int i = 0; i <= 720; i++) top = Math.min(top, heartY(i * WorldOverlay.TAU / 720));
            return top;
        }

        /** Where the number's middle sits, in heart units: halfway between the lobes' top and the tip. */
        static float numberY() {
            return (heartTop() + heartTip()) / 2;
        }

        /** Whether a point (heart units) lies inside the heart: its outline as a polygon, even-odd. */
        static boolean inHeart(float x, float y) {
            boolean in = false;
            for (int i = 0, j = SAMPLES - 1; i < SAMPLES; j = i++) {
                double ui = i * WorldOverlay.TAU / SAMPLES, uj = j * WorldOverlay.TAU / SAMPLES;
                float xi = heartX(ui), yi = heartY(ui), xj = heartX(uj), yj = heartY(uj);
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
            }
            return in;
        }

        /** The heart's bob at t, in units: gently up and down (the mockup's sin(1.2 t) * h * .008). */
        static float bob(double t) {
            return BOB * (float) Math.sin(t * 1.2);
        }

        /** Where the beam ends (units, y) for a bob: at the heart's tip. */
        static float beamTop(float bob) {
            return HEART_Y + bob + heartTip() * HEART_K;
        }

        /** How bright the hologram is at t (about .67 .. .97): a calm flicker, never a strobe. */
        static float flicker(double t) {
            return (float) (0.82 + 0.1 * Math.sin(t * 7.3) * Math.sin(t * 2.1) + 0.05 * Math.sin(t * 17 + 1));
        }

        /** A star's alpha at t: the mockup's (.3 + .5 s), twinkling (.7 + .3 sin(2t + 9p)) on top of the painted one. */
        static float twinkle(float s, float p, double t) {
            return (0.3f + 0.5f * s) * 0.6f * (0.5f + 0.5f * (float) Math.sin(t * 2 + p * 9));
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            k = HEART_K * o.unit;
            if (star == null) {
                star = new Paint(Paint.ANTI_ALIAS_FLAG);
                star.setColor(0xFFF4F0FF);
                fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                fill.setColor(0xFF78B4FF);
                line = new Paint(Paint.ANTI_ALIAS_FLAG);
                line.setStyle(Paint.Style.STROKE);
                line.setColor(0xFF9FD0FF);
                line.setStrokeJoin(Paint.Join.ROUND);
                wide = new Paint(line);
                scan = new Paint(Paint.ANTI_ALIAS_FLAG);
                scan.setColor(0xFFA0D2FF);
                beam = new Paint(Paint.ANTI_ALIAS_FLAG);
                halo = SeasonMotions.glow(0xFF7FB8FF);
                heart = new Path();
                cone = new Path();
                // The mockup's scan lines across the heart, every 2.5 heart units from -10 to 15.
                scanLines = new float[11 * 4];
            }
            line.setStrokeWidth(Math.max(1.5f, 0.15f * o.unit));
            wide.setStrokeWidth(Math.max(4f, 0.6f * o.unit));
            scan.setStrokeWidth(Math.max(1f, 0.08f * o.unit));
            // The heart round the origin in view pixels; drawn translated to its place.
            heart.rewind();
            for (int i = 0; i < SAMPLES; i++) {
                double u = i * WorldOverlay.TAU / SAMPLES;
                if (i == 0) heart.moveTo(heartX(u) * k, heartY(u) * k);
                else heart.lineTo(heartX(u) * k, heartY(u) * k);
            }
            heart.close();
            for (int i = 0; i < 11; i++) {
                float y = (-10f + i * 2.5f) * k;
                scanLines[i * 4] = -17 * k;
                scanLines[i * 4 + 1] = y;
                scanLines[i * 4 + 2] = 17 * k;
                scanLines[i * 4 + 3] = y;
            }
            beam.setShader(new LinearGradient(0f, o.uy(LENS_Y), 0f, o.uy(beamTop(0f)), 0x598CC8FF, 0x1A8CC8FF,
                    Shader.TileMode.CLAMP));
            number.layout(o, bold(o));
            o.regions[0].set(0, 0, w, (int) Math.ceil(o.uy(108 * STARS_TOP + STAR_R) + 2));
            float g = 22 * k;
            o.regions[1].set((int) Math.floor(o.ux(HEART_X) - g), (int) Math.floor(o.uy(HEART_Y - BOB) + heartTop() * k - g),
                    (int) Math.ceil(o.ux(HEART_X) + g), (int) Math.ceil(o.uy(LENS_Y + 1f)));
            if (!ready) frame(0);
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            flicker = flicker(t);
            bob = bob(t);
            for (int i = 0; i < STARS; i++) twinkle[i] = shown[i] ? twinkle(q[i][2], q[i][3], t) : 0f;
            // The beam from the lens to the heart's tip, where it is this frame.
            float tip = o.uy(beamTop(bob)), lens = o.uy(LENS_Y);
            float lx = o.ux(LENS_X), hx = o.ux(HEART_X), half = BEAM_HALF * k, lh = LENS_HALF * o.unit;
            cone.rewind();
            cone.moveTo(lx - lh, lens);
            cone.lineTo(hx - half, tip);
            cone.lineTo(hx + half, tip);
            cone.lineTo(lx + lh, lens);
            cone.close();
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || star == null) return;
            float r = Math.max(1f, STAR_R * o.unit);
            for (int i = 0; i < STARS; i++) {
                if (twinkle[i] <= 0f) continue;
                star.setAlpha((int) (255 * twinkle[i]));
                c.drawCircle(o.ux(q[i][0] * 192), o.uy(q[i][1] * 108 * STARS_TOP), r, star);
            }
            float f = flicker, dy = bob * o.unit;
            beam.setAlpha((int) (255 * f));
            c.drawPath(cone, beam);
            float hx = o.ux(HEART_X), hy = o.uy(HEART_Y) + dy;
            SeasonMotions.drawGlow(c, halo, hx, hy + 2 * k, 20 * k, 0.22f * f);
            c.save();
            c.translate(hx, hy);
            fill.setAlpha((int) (255 * 0.18f * f));
            c.drawPath(heart, fill);
            c.save();
            c.clipPath(heart);
            scan.setAlpha((int) (255 * 0.2f * f));
            c.drawLines(scanLines, scan);
            c.restore();
            wide.setAlpha((int) (255 * 0.22f * f));
            c.drawPath(heart, wide);
            line.setAlpha((int) (255 * 0.8f * f));
            c.drawPath(heart, line);
            c.restore();
            number.draw(c, dy, 0.95f * f + 0.05f);
        }
    }

    /**
     * Wedding day: the twenty-two lights along the rose arch (personal.py: ARCH_*, LIGHTS) twinkle (the mockup's
     * .55 + .45 sin(2.4t + 1.1i)), a sparkle wanders over the two rings' upper arcs, one ring after the other, sixteen
     * petals (the mockup's seed 71) float down right of the header, and today's names stand large over the arch.
     */
    static final class Wedding implements LookMotion {
        static final float ARCH_X = 138.24f, ARCH_FOOT = 75.6f, ARCH_W = 26.88f, ARCH_H = 45.36f, ARCH_POST = 0.55f;
        static final int LIGHTS = 22, PETALS = 16;
        static final float LIGHT_GLOW = 0.012f * 192;
        static final float RING_Y = 57.24f, RING_R = 5.76f, RING_DX = 0.55f;
        /** A sparkle's way over both rings, in seconds, and its glow (units). */
        static final double SPARKLE_PERIOD = 4.0;
        static final float SPARKLE_GLOW = 0.012f * 192, SPARKLE_RAY = 1.5f;
        /** The names: centred over the arch, their top at NAMES_TOP, 4.4 % of the width per em, at most NAMES_MAX wide. */
        static final float NAMES_X = ARCH_X, NAMES_TOP = 12.96f, NAMES_SIZE = 0.044f * 192, NAMES_MAX = 96f;
        /** The petals float right of this fraction of the width: clear of the header. */
        static final float PETAL_LEFT = 0.42f;

        private final Label names;
        private final SeasonMotions.Fall petals = new SeasonMotions.Fall(SeasonMotions.Fall.BLOSSOM, PETALS, 71)
                .within(PETAL_LEFT, SeasonMotions.FALL_START, 1f, SeasonMotions.FALL_END);
        private final float[][] lights = new float[LIGHTS][];
        private final float[] on = new float[LIGHTS];
        private final float[] spark = new float[3];
        private boolean ready;
        private SeasonsOverlay o;
        private Paint bulb, glint, ray;
        private float sparkX, sparkY, sparkA;

        Wedding(String today) {
            names = new Label(today, NAMES_X, NAMES_TOP, NAMES_SIZE, NAMES_MAX, false, Look.WEDDING.accent);
            for (int i = 0; i < LIGHTS; i++) lights[i] = archPoint((i + 0.5f) / LIGHTS);
        }

        /** The names over the arch: today's names. */
        String text() {
            return names.text;
        }

        int petalCount() {
            return petals.count();
        }

        float[] petalBox() {
            return petals.box();
        }

        /**
         * A point of the arch's path at u (0..1), {x, y} in units: up the left post, over the half ellipse, down the
         * right post (the mockup's path(u), personal.py arch_point).
         */
        static float[] archPoint(float u) {
            if (u < 0.3f) return new float[]{ARCH_X - ARCH_W, ARCH_FOOT - u / 0.3f * ARCH_H * ARCH_POST};
            if (u > 0.7f) return new float[]{ARCH_X + ARCH_W, ARCH_FOOT - (1 - u) / 0.3f * ARCH_H * ARCH_POST};
            double a = Math.PI + (u - 0.3) / 0.4 * Math.PI;
            return new float[]{ARCH_X + (float) Math.cos(a) * ARCH_W,
                    ARCH_FOOT - ARCH_H * ARCH_POST + (float) Math.sin(a) * ARCH_H * (1 - ARCH_POST)};
        }

        /** The arch's lights: where they hang, as personal.py paints them. */
        float[][] lights() {
            return lights;
        }

        /**
         * Where the sparkle is at t: {x, y, alpha} in units. It runs over the left ring's upper arc, then the right
         * one's, each in half a period, fading in and out on each (sin of its way).
         */
        static float[] sparkle(double t, float[] out) {
            double sp = (t / SPARKLE_PERIOD) % 1.0;
            if (sp < 0) sp += 1;
            int ring = sp < 0.5 ? 0 : 1;
            double s = (sp - ring * 0.5) * 2;
            double a = Math.PI * (1.1 + 0.8 * s);
            float cx = ARCH_X + (ring == 0 ? -1 : 1) * RING_DX * RING_R;
            out[0] = cx + (float) Math.cos(a) * RING_R;
            out[1] = RING_Y + (float) Math.sin(a) * RING_R * 0.9f;
            out[2] = (float) Math.sin(s * Math.PI);
            return out;
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            if (bulb == null) {
                bulb = SeasonMotions.glow(0xFFFFE1A0);
                glint = SeasonMotions.glow(0xFFFFFFE6);
                ray = new Paint(Paint.ANTI_ALIAS_FLAG);
                ray.setColor(0xFFFFFFF0);
                ray.setStrokeCap(Paint.Cap.ROUND);
            }
            ray.setStrokeWidth(Math.max(1.5f, 0.14f * o.unit));
            names.layout(o, bold(o));
            // The petals' band (regions[0]), narrowed to where they fall; the arch with its lights and rings.
            petals.layout(o, w, h);
            o.regions[0].left = Math.max(0, (int) Math.floor(o.ux((PETAL_LEFT - 0.02f) * 192) - 0.006f * 192 * o.unit) - 2);
            float g = LIGHT_GLOW * o.unit;
            o.regions[1].set((int) Math.floor(o.ux(ARCH_X - ARCH_W) - g), (int) Math.floor(o.uy(ARCH_FOOT - ARCH_H) - g),
                    (int) Math.ceil(o.ux(ARCH_X + ARCH_W) + g), (int) Math.ceil(o.uy(lowestLight()) + g));
            if (!ready) frame(0);
        }

        /** The lowest light's centre (units). */
        static float lowestLight() {
            return archPoint(0.5f / LIGHTS)[1];
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            petals.frame(now);
            for (int i = 0; i < LIGHTS; i++) on[i] = 0.55f + 0.45f * (float) Math.sin(t * 2.4 + i * 1.1);
            sparkle(t, spark);
            sparkX = o.ux(spark[0]);
            sparkY = o.uy(spark[1]);
            sparkA = spark[2];
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || bulb == null) return;
            for (int i = 0; i < LIGHTS; i++) {
                SeasonMotions.drawGlow(c, bulb, o.ux(lights[i][0]), o.uy(lights[i][1]), LIGHT_GLOW * o.unit, 0.7f * on[i]);
            }
            if (sparkA > 0.01f) {
                SeasonMotions.drawGlow(c, glint, sparkX, sparkY, SPARKLE_GLOW * o.unit, sparkA);
                float r = SPARKLE_RAY * o.unit * sparkA;
                ray.setAlpha((int) (255 * sparkA));
                c.drawLine(sparkX - r, sparkY, sparkX + r, sparkY, ray);
                c.drawLine(sparkX, sparkY - r, sparkX, sparkY + r, ray);
            }
            petals.draw(c);
            names.draw(c, 0f, 1f);
        }
    }
}
