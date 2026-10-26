package com.gathin.tvhome;

import static com.gathin.tvhome.SeasonMotions.drawGlow;
import static com.gathin.tvhome.SeasonMotions.glow;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The motion of Jahreskreis' occasions from Halloween to Christmas (spec § 4.2, § 4.5) over their pictures
 * (launcher/worlds/seasons/occasions_b.py), as the mockup's painters have it (jahreskreis-mockup.html,
 * PAINT.halloween/martin/nikolaus/advent/xmas): the carved faces of the Halloween pumpkins flickering like candles and
 * four bats flying past the moon, the St. Martin lanterns swinging on their sticks, snow falling at the St. Nicholas
 * door and its lantern flickering (the 5th and the 6th alike), on Advent as many candles burning as Sundays of Advent
 * have passed with the lights twinkling and snow falling in the window, at Christmas the tree's lights twinkling and
 * snow falling.
 *
 * <p>Built like {@link OccasionMotionsA}: the mockup's seeded particles (SeasonMotions.particles), shapes in the
 * 192 x 108 units of the overlay at the places occasions_b.py paints them (the same numbers, named alike), paints,
 * shaders and paths made in layout and never per frame, nothing drawn before the first tick, nothing moves behind the
 * dock or in the header on the top left. Each picture is whole without its motion (reduced motion): the faces and
 * lanterns are painted lit, the Advent candles unlit.
 */
final class OccasionMotionsB {
    private OccasionMotionsB() {
    }

    /** The occasion's motion on this day (Advent's candles), or null for a look that is not one of these six. */
    static LookMotion of(Look look, SeasonsToday today) {
        if (look == null) return null;
        switch (look) {
            case HALLOWEEN: return new Halloween();
            case ST_MARTIN: return new Lanterns();
            case NIKOLAUS_EVE:
            case NIKOLAUS: return new Nikolaus();
            case ADVENT: return new Advent(today != null ? today.adventCandles : 0);
            case CHRISTMAS: return new Christmas();
            default: return null;
        }
    }

    /**
     * Halloween: the three pumpkins' carved faces (occasions_b.py: PUMPKINS, FACE_*) glow up and down like a candle
     * inside, each at its own pace, slow and soft (never a strobe), with a warm glow round them; and the mockup's four
     * bats (seed 17) flying right past the moon, flapping, coming in softly right of the header.
     */
    static final class Halloween implements LookMotion {
        static final float[] PUMPKIN_X = {108f, 133f, 157.5f}, PUMPKIN_R = {6.4f, 8.8f, 5.6f};
        static final float GROUND = 75.8f;
        /** The face in fractions of the radius round (x, GROUND - .82 r): eyes and nose, then the mouth closed by a
         * curve through FACE_CURVE back to its start. */
        static final float[][] FACE_PARTS = {{-0.48f, -0.08f, -0.26f, -0.42f, -0.10f, -0.08f},
                {0.10f, -0.08f, 0.26f, -0.42f, 0.48f, -0.08f}, {-0.06f, 0.02f, 0f, -0.12f, 0.06f, 0.02f}};
        static final float[] FACE_MOUTH = {-0.55f, 0.20f, -0.40f, 0.45f, -0.30f, 0.30f, -0.18f, 0.50f, -0.05f, 0.34f,
                0.08f, 0.52f, 0.20f, 0.34f, 0.32f, 0.48f, 0.44f, 0.30f, 0.55f, 0.20f};
        static final float[] FACE_CURVE = {0f, 0.78f};
        /** The glow round a face as a multiple of its pumpkin's radius. */
        static final float GLOW = 1.6f;

        static final int BATS = 4;
        /** Where bats fly (fractions of the width, past the right edge), their rows (below the pills on the top right,
         * PersonalMotions.PILLS, past the moon) and a bat's half span (s, the mockup's w * .012: a wing is 2 s long). */
        static final float BAT_LEFT = 0.45f, BAT_RIGHT = 1.08f, BAT_TOP = 0.25f, BAT_ROWS = 0.2f, BAT_BOB = 0.02f;
        static final float BAT_S = 0.012f, BAT_SPEED = 0.03f, BAT_FADE = 0.06f;

        private final float[][] q = SeasonMotions.particles(BATS, 17);
        private final float[] flick = new float[PUMPKIN_X.length];
        private final float[] bx = new float[BATS], by = new float[BATS], ba = new float[BATS], flap = new float[BATS];
        private final Path[] faces = new Path[PUMPKIN_X.length];
        private final float[] faceX = new float[PUMPKIN_X.length], faceY = new float[PUMPKIN_X.length];
        private boolean ready;
        private SeasonsOverlay o;
        private Paint face, light, bat;
        private Path wing;
        private float dockY, batS;

        /**
         * How bright face i burns at t (0.56 .. 1): three slow waves, as a candle breathes, never faster than about
         * once a second and so never a strobe.
         */
        static float flicker(double t, int i) {
            return (float) (0.78 + 0.12 * Math.sin(t * 2.1 + i * 2.1) + 0.07 * Math.sin(t * 3.7 + i * 1.3)
                    + 0.03 * Math.sin(t * 6.3 + i));
        }

        /** A bat's x (fraction of the width) at t: flying right from BAT_LEFT, coming in again on the left. */
        static float batX(float x0, float s, double t) {
            double span = BAT_RIGHT - BAT_LEFT;
            double x = (x0 * span + t * BAT_SPEED * (1 + s)) % span;
            if (x < 0) x += span;
            return (float) (BAT_LEFT + x);
        }

        /** A bat's alpha at x: coming in softly right of BAT_LEFT, so none pops up next to the header. */
        static float batAlpha(float x) {
            return Math.max(0f, Math.min(1f, (x - BAT_LEFT) / BAT_FADE));
        }

        /** The lowest a bat reaches, as a fraction of the height (16:9). */
        static float batReach() {
            return BAT_TOP + BAT_ROWS + BAT_BOB + BAT_S * 0.6f * 16f / 9f;
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            if (face == null) {
                face = new Paint(Paint.ANTI_ALIAS_FLAG);
                face.setColor(0xFFFFE070);
                light = glow(0xFFFFA030);
                bat = new Paint(Paint.ANTI_ALIAS_FLAG);
                bat.setColor(0xFF0A0612);
                wing = new Path();
                for (int i = 0; i < faces.length; i++) faces[i] = new Path();
            }
            dockY = o.uy(SeasonMotions.DOCK * 108);
            float left = w, top = h, right = 0;
            for (int i = 0; i < PUMPKIN_X.length; i++) {
                float r = PUMPKIN_R[i], x = PUMPKIN_X[i], cy = GROUND - r * 0.82f;
                faceX[i] = o.ux(x);
                faceY[i] = o.uy(cy);
                Path p = faces[i];
                p.reset();
                for (float[] part : FACE_PARTS) {
                    p.moveTo(o.ux(x + part[0] * r), o.uy(cy + part[1] * r));
                    for (int k = 2; k < part.length; k += 2) p.lineTo(o.ux(x + part[k] * r), o.uy(cy + part[k + 1] * r));
                    p.close();
                }
                p.moveTo(o.ux(x + FACE_MOUTH[0] * r), o.uy(cy + FACE_MOUTH[1] * r));
                for (int k = 2; k < FACE_MOUTH.length; k += 2) {
                    p.lineTo(o.ux(x + FACE_MOUTH[k] * r), o.uy(cy + FACE_MOUTH[k + 1] * r));
                }
                p.quadTo(o.ux(x + FACE_CURVE[0] * r), o.uy(cy + FACE_CURVE[1] * r), o.ux(x + FACE_MOUTH[0] * r),
                        o.uy(cy + FACE_MOUTH[1] * r));
                p.close();
                float g = GLOW * r;
                left = Math.min(left, o.ux(x - g));
                right = Math.max(right, o.ux(x + g));
                top = Math.min(top, o.uy(cy - g));
            }
            // The faces and their glow, cut at the dock's top edge.
            o.regions[0].set((int) Math.floor(left) - 1, (int) Math.floor(top) - 1, (int) Math.ceil(right) + 1,
                    (int) Math.ceil(dockY));
            batS = BAT_S * 192 * o.unit;
            o.regions[1].set((int) Math.floor(o.ux(BAT_LEFT * 192) - 2 * batS) - 2,
                    (int) Math.floor(o.uy((BAT_TOP - BAT_BOB) * 108) - batS) - 2, w,
                    (int) Math.ceil(o.uy((BAT_TOP + BAT_ROWS + BAT_BOB) * 108) + batS) + 2);
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            for (int i = 0; i < flick.length; i++) flick[i] = flicker(t, i);
            for (int i = 0; i < BATS; i++) {
                float[] p = q[i];
                float fx = batX(p[0], p[2], t);
                bx[i] = o.ux(fx * 192);
                by[i] = o.uy((BAT_TOP + p[1] * BAT_ROWS + BAT_BOB * (float) Math.sin(t * 3 + i)) * 108);
                ba[i] = batAlpha(fx);
                // The mockup's wing beat: the upper edge of the wings swings from above the body to below it.
                flap[i] = (float) Math.sin(t * 14 + i) * 0.6f;
            }
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || face == null) return;
            c.save();
            c.clipRect(0f, 0f, c.getWidth(), dockY);
            for (int i = 0; i < faces.length; i++) {
                float f = flick[i];
                drawGlow(c, light, faceX[i], faceY[i], GLOW * PUMPKIN_R[i] * o.unit, 0.32f * f);
                face.setAlpha((int) (255 * (0.2f + 0.6f * (f - 0.56f) / 0.44f)));
                c.drawPath(faces[i], face);
            }
            c.restore();
            float s = batS;
            for (int i = 0; i < BATS; i++) {
                if (ba[i] <= 0f) continue;
                float f = flap[i];
                // The mockup's bat: both wings from the body out to 2 s, their lower edge fixed, the upper one beating.
                wing.rewind();
                wing.moveTo(0f, 0f);
                wing.quadTo(-s, -s * f, -2 * s, 0f);
                wing.quadTo(-s, s * 0.3f, 0f, s * 0.3f);
                wing.quadTo(s, s * 0.3f, 2 * s, 0f);
                wing.quadTo(s, -s * f, 0f, 0f);
                bat.setAlpha((int) (255 * ba[i]));
                c.save();
                c.translate(bx[i], by[i]);
                c.drawPath(wing, bat);
                c.restore();
            }
        }
    }

    /**
     * St. Martin: each child's lantern (occasions_b.py: CHILDREN, LANTERN_*) swings gently on its string below the
     * stick's tip, at the walk's pace (the mockup's 2.4 rad/s, each a little later than the one before), with its glow;
     * the painted lantern lies under it, so the swing stays small.
     */
    static final class Lanterns implements LookMotion {
        static final float[] CHILD_X = {84f, 98f, 111.5f, 125f, 139f, 152.5f, 166f};
        static final float[] CHILD_H = {11.5f, 13f, 10.5f, 12.5f, 11f, 13.5f, 12f};
        static final float GROUND = 75.9f, TIP_DX = 4.6f, TIP_UP = 2.6f, STRING = 1.0f, R = 2.3f;
        /** The largest swing either way, in degrees, and its pace. */
        static final float SWING = 5f;
        static final double RATE = 2.4, STEP = 0.8;
        private static final int[] COLOURS = {0xFFFFCF5A, 0xFFFF8A5C, 0xFF9FD8FF};
        private static final int[] LIGHT = {0xFFFFF0C0, 0xFFFFD0B0, 0xFFE0F4FF};

        private final float[] angle = new float[CHILD_X.length];
        private boolean ready;
        private SeasonsOverlay o;
        private final Paint[] body = new Paint[COLOURS.length], halo = new Paint[COLOURS.length];
        private Paint string, rib, rim;
        private RectF ribInner, ribOuter, rimTop, rimBottom;
        private float r, len;

        /** Lantern i's swing at t in degrees: within ±SWING. */
        static float swing(double t, int i) {
            return SWING * (float) Math.sin(t * RATE + i * STEP);
        }

        static float tipX(int i) {
            return CHILD_X[i] + TIP_DX;
        }

        static float tipY(int i) {
            return GROUND - CHILD_H[i] - TIP_UP;
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            r = R * o.unit;
            len = (STRING + R) * o.unit;
            if (string == null) {
                string = new Paint(Paint.ANTI_ALIAS_FLAG);
                string.setColor(0xFF2A2A38);
                rib = new Paint(Paint.ANTI_ALIAS_FLAG);
                rib.setStyle(Paint.Style.STROKE);
                rib.setColor(0x595A3A1A);
                rim = new Paint(Paint.ANTI_ALIAS_FLAG);
                rim.setColor(0xFF2A1A10);
                for (int k = 0; k < COLOURS.length; k++) {
                    halo[k] = glow(COLOURS[k]);
                    body[k] = new Paint(Paint.ANTI_ALIAS_FLAG);
                }
                ribInner = new RectF();
                ribOuter = new RectF();
                rimTop = new RectF();
                rimBottom = new RectF();
            }
            // In the lantern's own frame: the tip at the origin, the lantern's centre (0, len) below it; a little
            // larger than the painted one, so the painted one stays covered as it swings.
            float rr = r * 1.06f;
            for (int k = 0; k < COLOURS.length; k++) {
                body[k].setShader(new RadialGradient(-0.15f * rr, len + 0.1f * rr, 1.1f * rr,
                        new int[]{LIGHT[k], COLOURS[k], 0xFF7A4A2A}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            }
            string.setStrokeWidth(Math.max(1f, 0.14f * o.unit));
            rib.setStrokeWidth(Math.max(1f, 0.1f * o.unit));
            ribInner.set(-0.35f * rr, len - rr, 0.35f * rr, len + rr);
            ribOuter.set(-0.72f * rr, len - rr, 0.72f * rr, len + rr);
            rimTop.set(-0.55f * rr, len - rr - 0.35f * o.unit, 0.55f * rr, len - rr + 0.2f * o.unit);
            rimBottom.set(-0.5f * rr, len + rr - 0.25f * o.unit, 0.5f * rr, len + rr + 0.25f * o.unit);
            float top = h, bottom = 0;
            for (int i = 0; i < CHILD_X.length; i++) {
                top = Math.min(top, o.uy(tipY(i)));
                bottom = Math.max(bottom, o.uy(tipY(i)) + len + 2.6f * r);
            }
            float reach = 2.6f * r;
            o.regions[0].set((int) Math.floor(o.ux(tipX(0)) - reach), (int) Math.floor(top - reach),
                    (int) Math.ceil(o.ux(tipX(CHILD_X.length - 1)) + reach), (int) Math.ceil(bottom));
        }

        /** The lowest any lantern's glow reaches, in units: above the dock. */
        static float lowest() {
            float low = 0f;
            for (int i = 0; i < CHILD_X.length; i++) low = Math.max(low, tipY(i) + STRING + R + 2.6f * R);
            return low;
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            for (int i = 0; i < angle.length; i++) angle[i] = swing(t, i);
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || string == null) return;
            for (int i = 0; i < CHILD_X.length; i++) {
                int k = i % COLOURS.length;
                c.save();
                c.translate(o.ux(tipX(i)), o.uy(tipY(i)));
                // Hanging from the tip on its string, it swings about the tip.
                c.rotate(angle[i]);
                drawGlow(c, halo[k], 0f, len, 2.6f * r, 0.3f);
                c.drawLine(0f, 0f, 0f, len - r, string);
                c.drawCircle(0f, len, r * 1.06f, body[k]);
                c.drawOval(ribInner, rib);
                c.drawOval(ribOuter, rib);
                c.drawRect(rimTop, rim);
                c.drawRect(rimBottom, rim);
                c.restore();
            }
        }
    }

    /**
     * St. Nicholas (the eve and the day): the mockup's forty-five snowflakes (seed 97) falling before the door, and the
     * lantern by it (occasions_b.py: NIK_LANTERN_X/Y) flickering in its glow.
     */
    static final class Nikolaus implements LookMotion {
        static final int FLAKES = 45;
        static final float LANTERN_X = 165f, LANTERN_Y = 33.8f, LANTERN_GLOW = 13f;

        private final SeasonMotions.Fall snow = new SeasonMotions.Fall(SeasonMotions.Fall.SNOW, FLAKES, 97);
        private boolean ready;
        private SeasonsOverlay o;
        private Paint light;
        private float flicker = 1f;

        int flakes() {
            return snow.count();
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            if (light == null) light = glow(0xFFFFBE5A);
            // The snow's band (regions[0]) holds the lantern as well.
            snow.layout(o, w, h);
        }

        @Override public void frame(long now) {
            if (o == null) return;
            snow.frame(now);
            flicker = OccasionMotionsA.Valentine.flicker(now / 1000.0);
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || light == null) return;
            drawGlow(c, light, o.ux(LANTERN_X), o.uy(LANTERN_Y), LANTERN_GLOW * o.unit * (0.9f + 0.1f * flicker),
                    0.18f + 0.5f * (flicker - 0.75f));
            snow.draw(c);
        }
    }

    /**
     * Advent: as many of the wreath's four candles burn as Sundays of Advent have passed ({@link #flamesFor}), their
     * flames flickering (OccasionMotionsA.Valentine.flicker, each at its own beat) under a warm glow, at the wicks
     * occasions_b.py paints (CANDLES, CANDLE_H, FLAME_UP); the string of lights along the top twinkles (the mockup's
     * .55 + .45 sin(2.5t + 1.3i)), and the mockup's forty snowflakes (seed 81) fall behind the window's four panes
     * only ({@link #panes}): never over the frame, its cross, the paper star or the sill, and ending above the
     * candles' wicks.
     */
    static final class Advent implements LookMotion {
        /** The candles (occasions_b.py: CANDLES, CANDLE_H, FLAME_UP): x and foot, units, lit in this order; the wick
         * stands WICK above the top. */
        static final float[] FLAME_X = {116.2f, 125.4f, 134.6f, 143.8f};
        static final float[] CANDLE_FOOT = {67.4f, 66.4f, 66.4f, 67.4f};
        static final float CANDLE_H = 16f, FLAME_UP = 2.6f, WICK = 1.0f;
        /** Each candle's flame (units). */
        static final float[] FLAME_Y = {CANDLE_FOOT[0] - CANDLE_H - FLAME_UP, CANDLE_FOOT[1] - CANDLE_H - FLAME_UP,
                CANDLE_FOOT[2] - CANDLE_H - FLAME_UP, CANDLE_FOOT[3] - CANDLE_H - FLAME_UP};
        /** A flame's half width and half height (the mockup's w * .005, h * .022) and its glow (w * .045), units. */
        static final float FLAME_W = 0.005f * 192, FLAME_H = 0.022f * 108, FLAME_GLOW = 0.045f * 192;
        /** The string of lights (occasions_b.py: LIGHTS_*, lights_y): bulbs at x FROM + k * STEP. */
        static final float LIGHTS_FROM = 82f, LIGHTS_STEP = 7.6f, LIGHT_GLOW = 0.012f * 192;
        static final int LIGHTS = 15, WINDOW_FLAKES = 40;
        /** The window (occasions_b.py: ADV_WIN_*), units; its frame reaches FRAME inside the opening (the painted bars
         * are 3 wide on its edges) and the cross is CROSS wide, both as advent() paints them. */
        static final float WIN_X = 107.5f, WIN_Y = 25f, WIN_W = 57.6f, WIN_H = 33f, FRAME = 1.5f, CROSS = 1.5f;
        /** The paper star hanging in the room before the upper right pane (occasions_b.py: PAPER_STAR, its points
         * 3.84 out), units; the snow keeps STAR_R round it clear. */
        static final float STAR_X = WIN_X + WIN_W * 0.72f, STAR_Y = WIN_Y + WIN_H * 0.3f, STAR_R = 4.2f;
        private static final int[] LIGHT_COLOURS = {0xFFFFB46E, 0xFFFFCD6E, 0xFFFFE68C};

        private final int flames;
        private final float[] size = new float[FLAME_X.length], lean = new float[FLAME_X.length];
        private final float[] on = new float[LIGHTS];
        private final SeasonMotions.Fall snow = snowInPanes();
        private boolean ready;
        private SeasonsOverlay o;
        private Paint flame, warm;
        private final Paint[] bulb = new Paint[LIGHT_COLOURS.length];
        private RectF oval;
        private Path clip;

        Advent(int candles) {
            flames = flamesFor(candles);
        }

        /** How many flames burn for this many Sundays of Advent: one per Sunday, none outside, four at most. */
        static int flamesFor(int candles) {
            return Math.max(0, Math.min(FLAME_X.length, candles));
        }

        int flames() {
            return flames;
        }

        /** Where the panes' snow ends (units): above the highest candle's wick, which stands in front of the window. */
        static float snowBottom() {
            float top = Float.MAX_VALUE;
            for (float foot : CANDLE_FOOT) top = Math.min(top, foot - CANDLE_H - WICK);
            return Math.min(top, WIN_Y + WIN_H - FRAME);
        }

        /**
         * The four panes the snow is seen through, {left, top, right, bottom} in units: inside the frame, either side
         * of the cross, the lower two cut off above the candles' wicks ({@link #snowBottom}).
         */
        static float[][] panes() {
            float l = WIN_X + FRAME, r = WIN_X + WIN_W - FRAME, t = WIN_Y + FRAME, b = snowBottom();
            float mx = WIN_X + WIN_W / 2, my = WIN_Y + WIN_H / 2, half = CROSS / 2;
            return new float[][]{{l, t, mx - half, my - half}, {mx + half, t, r, my - half},
                    {l, my + half, mx - half, b}, {mx + half, my + half, r, b}};
        }

        /** The mockup's forty flakes, falling only across the panes (a little above them, so they come in from the top). */
        static SeasonMotions.Fall snowInPanes() {
            return new SeasonMotions.Fall(SeasonMotions.Fall.SNOW, WINDOW_FLAKES, 81).within(
                    (WIN_X + FRAME) / 192f, (WIN_Y - 2f) / 108f, (WIN_X + WIN_W - FRAME) / 192f, snowBottom() / 108f);
        }

        /** The string of lights' sag at x (units), the mockup's h * .05 + sin(x / w * TAU * 3) * h * .02. */
        static float lightsY(float x) {
            return 5.4f + (float) Math.sin(x / 192f * WorldOverlay.TAU * 3) * 2.16f;
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            if (flame == null) {
                flame = new Paint(Paint.ANTI_ALIAS_FLAG);
                flame.setColor(0xFFFFD27A);
                warm = glow(0xFFFFBE5A);
                for (int k = 0; k < bulb.length; k++) bulb[k] = glow(LIGHT_COLOURS[k]);
                oval = new RectF();
                clip = new Path();
            }
            // Snow behind the panes: the fall within the window, drawn through a clip of the four panes only.
            snow.layout(o, w, h);
            clip.rewind();
            for (float[] p : panes()) clip.addRect(o.ux(p[0]), o.uy(p[1]), o.ux(p[2]), o.uy(p[3]), Path.Direction.CW);
            // The star hangs in the room, before the glass: a hole in the panes (even-odd, the panes do not overlap).
            clip.addCircle(o.ux(STAR_X), o.uy(STAR_Y), STAR_R * o.unit, Path.Direction.CW);
            clip.setFillType(Path.FillType.EVEN_ODD);
            o.regions[0].set((int) Math.floor(o.ux(WIN_X + FRAME)), (int) Math.floor(o.uy(WIN_Y + FRAME)),
                    (int) Math.ceil(o.ux(WIN_X + WIN_W - FRAME)), (int) Math.ceil(o.uy(snowBottom())));
            float g = FLAME_GLOW * o.unit;
            if (flames > 0) {
                o.regions[1].set((int) Math.floor(o.ux(FLAME_X[0]) - g), (int) Math.floor(o.uy(FLAME_Y[1]) - g),
                        (int) Math.ceil(o.ux(FLAME_X[flames - 1]) + g), (int) Math.ceil(o.uy(FLAME_Y[0]) + g));
            }
            float lg = LIGHT_GLOW * o.unit;
            o.regions[2].set((int) Math.floor(o.ux(LIGHTS_FROM) - lg), 0, w, (int) Math.ceil(o.uy(5.4f + 2.16f + 1f) + lg));
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            snow.frame(now);
            for (int i = 0; i < flames; i++) {
                size[i] = OccasionMotionsA.Valentine.flicker(t + i * 0.37);
                lean[i] = (float) Math.sin(t * 5.3 + i * 1.9) * 0.15f;
            }
            for (int k = 0; k < LIGHTS; k++) on[k] = 0.55f + 0.45f * (float) Math.sin(t * 2.5 + k * 1.3);
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || flame == null) return;
            c.save();
            c.clipPath(clip);
            snow.draw(c);
            c.restore();
            for (int i = 0; i < flames; i++) {
                SeasonMotions.drawFlame(c, flame, warm, oval, o.ux(FLAME_X[i]), o.uy(FLAME_Y[i]), FLAME_W * o.unit,
                        FLAME_H * o.unit, FLAME_GLOW * o.unit, size[i], lean[i], 1f);
            }
            for (int k = 0; k < LIGHTS; k++) {
                float x = LIGHTS_FROM + k * LIGHTS_STEP;
                drawGlow(c, bulb[k % bulb.length], o.ux(x), o.uy(lightsY(x) + 1f), LIGHT_GLOW * o.unit, 0.6f * on[k]);
            }
        }
    }

    /**
     * Christmas: the mockup's fifty snowflakes (seed 23) falling, the tree's thirty lights (the mockup's rng(31), as
     * occasions_b.py paints them) twinkling (.5 + .5 sin(3t + 1.7i)) and the star on top glowing softly.
     */
    static final class Christmas implements LookMotion {
        static final int FLAKES = 50, LIGHTS = 30;
        /** The tree (occasions_b.py: TREE_*), units: its middle, top, base and half width at the base; at .75 of the
         * mockup's size, so its star stays below the pills on the top right. */
        static final float TREE_X = 138f, TREE_TOP = 28.875f, TREE_BASE = 70.2f, TREE_SPREAD = 18.72f;
        /** The star's centre, a light's glow and the star's (w * .028, smaller than the mockup's .05 so it fades out
         * below the pills on the top right; occasions_b.py: STAR_HALO likewise). */
        static final float STAR_Y = TREE_TOP - 1.08f, LIGHT_GLOW = 0.01f * 192, STAR_GLOW = 0.028f * 192;
        private static final int[] COLOURS = {0xFFFFC882, 0xFFFFDA82, 0xFFFFEC96};

        private final SeasonMotions.Fall snow = new SeasonMotions.Fall(SeasonMotions.Fall.SNOW, FLAKES, 23);
        private final float[][] lights = treeLights();
        private final float[] on = new float[LIGHTS];
        private boolean ready;
        private SeasonsOverlay o;
        private final Paint[] glow = new Paint[COLOURS.length];
        private Paint star;
        private float starOn = 1f;

        int flakes() {
            return snow.count();
        }

        /**
         * The mockup's thirty lights, (x, y) in units: its rng(31) two values at a time (the same sequence as
         * SeasonMotions.particles(15, 31) read in order), y = top + H (.1 + .85 r), x = middle + (2 r - 1) .9 half(y).
         */
        static float[][] treeLights() {
            float[][] q = SeasonMotions.particles(LIGHTS / 2, 31);
            float[][] out = new float[LIGHTS][2];
            float height = TREE_BASE - TREE_TOP;
            for (int i = 0; i < LIGHTS; i++) {
                float ry = q[i / 2][(i % 2) * 2], rx = q[i / 2][(i % 2) * 2 + 1];
                float y = TREE_TOP + height * (0.1f + ry * 0.85f);
                float half = (y - TREE_TOP) / height * TREE_SPREAD;
                out[i][0] = TREE_X + (rx * 2 - 1) * half * 0.9f;
                out[i][1] = y;
            }
            return out;
        }

        @Override public void layout(SeasonsOverlay o, int w, int h) {
            this.o = o;
            if (star == null) {
                for (int k = 0; k < COLOURS.length; k++) glow[k] = glow(COLOURS[k]);
                star = glow(0xFFFFE696);
            }
            // The snow's band (regions[0]) holds the whole tree and its star.
            snow.layout(o, w, h);
        }

        @Override public void frame(long now) {
            if (o == null) return;
            double t = now / 1000.0;
            snow.frame(now);
            for (int i = 0; i < LIGHTS; i++) on[i] = 0.5f + 0.5f * (float) Math.sin(t * 3 + i * 1.7);
            starOn = 0.75f + 0.25f * (float) Math.sin(t * 1.5);
            ready = true;
        }

        @Override public void draw(Canvas c) {
            if (!ready || star == null) return;
            drawGlow(c, star, o.ux(TREE_X), o.uy(STAR_Y), STAR_GLOW * o.unit, 0.35f * starOn);
            for (int i = 0; i < LIGHTS; i++) {
                drawGlow(c, glow[i % COLOURS.length], o.ux(lights[i][0]), o.uy(lights[i][1]), LIGHT_GLOW * o.unit,
                        0.6f * on[i]);
            }
            snow.draw(c);
        }
    }
}
