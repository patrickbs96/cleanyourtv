package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Plain JVM tests for Supernatural's motion (spec § 4.2): the lightning's timing and strength, the neon's flicker,
 * and that nothing of it moves with reduced motion.
 */
public class SupernaturalOverlayTest {
    private static final long[] SEEDS = {SupernaturalOverlay.SEED, 0L, 1L, 42L, -7L, 0x5EEDL, Long.MAX_VALUE};

    /** About every 9 s (7 to 11 s apart), each flash at most 400 ms, the first one not before 7 s after the start. */
    @Test public void lightningTimesStayInWindow() {
        assertTrue(SupernaturalOverlay.LIGHTNING_MS <= 400);
        for (long seed : SEEDS) {
            long previous = 0;
            for (int k = 1; k <= 300; k++) {
                long start = SupernaturalOverlay.lightningStart(k, seed);
                long spacing = start - previous;
                assertTrue("seed " + seed + " flash " + k + ": " + spacing, spacing >= 7000 && spacing <= 11000);
                previous = start;
            }
            // Walk the first ten minutes in steps of 5 ms: every lit stretch is one flash and at most 400 ms long.
            long litFrom = -1;
            int flashes = 0;
            for (long t = 0; t <= 600_000; t += 5) {
                boolean lit = SupernaturalOverlay.lightning(t, seed) > 0f;
                if (lit && litFrom < 0) {
                    litFrom = t;
                    flashes++;
                    assertTrue("seed " + seed + " starts at " + t, t >= 7000);
                } else if (!lit && litFrom >= 0) {
                    assertTrue("seed " + seed + " lit for " + (t - litFrom), t - litFrom <= 400);
                    litFrom = -1;
                }
            }
            // 600 s at one every 7 to 11 s.
            assertTrue("seed " + seed + ": " + flashes, flashes >= 54 && flashes <= 86);
        }
    }

    /** The sky brightens to 40 % at most (OLED glare, spec § 2), and only for the flash's moment. */
    @Test public void skyBrighteningStaysAtFortyPercent() {
        float most = 0f;
        for (long t = 0; t <= 120_000; t += 2) {
            float a = SupernaturalOverlay.skyFlashAlpha(t, SupernaturalOverlay.SEED, true);
            assertTrue(t + ": " + a, a >= 0f && a <= 0.40f);
            most = Math.max(most, a);
        }
        assertTrue("the flash shows at all: " + most, most > 0.3f);
        long first = SupernaturalOverlay.lightningStart(1, SupernaturalOverlay.SEED);
        assertEquals(0f, SupernaturalOverlay.skyFlashAlpha(first - 1, SupernaturalOverlay.SEED, true), 0f);
        assertEquals(0f, SupernaturalOverlay.skyFlashAlpha(first + SupernaturalOverlay.LIGHTNING_MS,
                SupernaturalOverlay.SEED, true), 0f);
    }

    /** The flash's envelope: up to full and back to nothing within its 400 ms, never beyond 0..1. */
    @Test public void flashEnvelopeStaysInRange() {
        assertEquals(0f, SupernaturalOverlay.flash(-1), 0f);
        assertEquals(0f, SupernaturalOverlay.flash(SupernaturalOverlay.LIGHTNING_MS), 0f);
        float most = 0f;
        for (long d = 0; d < SupernaturalOverlay.LIGHTNING_MS; d++) {
            float f = SupernaturalOverlay.flash(d);
            assertTrue(d + ": " + f, f >= 0f && f <= 1f);
            most = Math.max(most, f);
        }
        assertEquals(1f, most, 0.01f);
    }

    /**
     * With reduced motion the overlay never runs (WorldOverlay.shouldRun), and while it does not run nothing flashes
     * or flickers: no lightning, no wing shadow, the neon steady as painted.
     */
    @Test public void noLightningWithReducedMotion() {
        boolean moving = WorldOverlay.shouldRun(World.SUPERNATURAL, true, true, true, false);
        assertFalse(moving);
        for (long seed : SEEDS) {
            for (long t = 0; t <= 120_000; t += 3) {
                assertEquals(0f, SupernaturalOverlay.skyFlashAlpha(t, seed, moving), 0f);
                assertEquals(0f, SupernaturalOverlay.motelDim(t, seed, moving), 0f);
                assertEquals(0f, SupernaturalOverlay.vacancyDim(t, seed, moving), 0f);
            }
        }
        assertTrue(WorldOverlay.shouldRun(World.SUPERNATURAL, true, false, true, false));
    }

    /** One MOTEL letter fails now and then, never all of them, and VACANCY flickers; both are on most of the time. */
    @Test public void neonFlickersButMostlyBurns() {
        long seed = SupernaturalOverlay.SEED;
        int motelOff = 0, vacancyOff = 0, samples = 0;
        boolean[] failed = new boolean[SupernaturalScene.NEON_MOTEL_X.length];
        for (long t = 0; t <= 300_000; t += 10, samples++) {
            int letter = SupernaturalOverlay.motelLetter(t, seed);
            assertTrue(letter >= 0 && letter < failed.length);
            float m = SupernaturalOverlay.motelDim(t, seed, true), v = SupernaturalOverlay.vacancyDim(t, seed, true);
            assertTrue(m >= 0f && m <= 1f && v >= 0f && v <= 1f);
            if (m > 0f) {
                motelOff++;
                failed[letter] = true;
            }
            if (v > 0f) vacancyOff++;
        }
        for (int i = 0; i < failed.length; i++) assertTrue("letter " + i + " never fails", failed[i]);
        assertTrue("MOTEL out " + motelOff, motelOff > 0 && motelOff < samples * 0.4);
        assertTrue("VACANCY out " + vacancyOff, vacancyOff > 0 && vacancyOff < samples * 0.2);
    }

    /** The neon's covers stay on the sign, clear of the pills below it (they start at y 23). */
    @Test public void neonStaysOutOfThePills() {
        for (int i = 0; i < SupernaturalScene.NEON_MOTEL_X.length; i++) {
            assertFalse(PersonalMotions.underPills(SupernaturalOverlay.motelLetterBox(i)));
        }
        assertFalse(PersonalMotions.underPills(SupernaturalOverlay.vacancyBox()));
    }

    /**
     * Putting a letter out covers all of its painted ink (NEON_MOTEL_X / NEON_MOTEL_HALF, which the Python test ties
     * to the drawing code) and none of its neighbours', and stays above VACANCY's caps.
     */
    @Test public void letterCoversFitTheirLetters() {
        float[] x = SupernaturalScene.NEON_MOTEL_X, half = SupernaturalScene.NEON_MOTEL_HALF;
        float[] ink = SupernaturalScene.NEON_MOTEL;
        assertEquals(x.length, half.length);
        for (int i = 0; i < x.length; i++) {
            float[] b = SupernaturalOverlay.motelLetterBox(i);
            assertTrue("letter " + i, b[0] < x[i] - half[i] && b[2] > x[i] + half[i]);
            assertTrue("letter " + i, b[1] < ink[1] && b[3] >= ink[1] + ink[3]);
            if (i > 0) assertTrue("letter " + i + " reaches its left neighbour", b[0] > x[i - 1] + half[i - 1]);
            if (i < x.length - 1) {
                assertTrue("letter " + i + " reaches its right neighbour", b[2] < x[i + 1] - half[i + 1]);
            }
            assertTrue("letter " + i + " over VACANCY", b[3] < SupernaturalScene.NEON_VACANCY[1]);
        }
        // M is the widest letter, about 4 units.
        assertEquals(3.8f, 2 * half[0], 0.3f);
    }

    /** The flash lights only the sky: not the motel, its roof, the sign, its pole, the hills or the wings' shadow. */
    @Test public void flashLightsOnlyTheSky() {
        float[][] dark = {
                {150f, 50f}, {118f, 43.6f}, {187f, 50f}, {176f, 47f},    // the motel's wall, the roof's overhangs
                {119f, 15f}, {108f, 21f}, {126f, 11f},                    // the sign board
                {121f, 30f}, {121f, 45f},                                  // its pole
                {40f, 55.9f}, {10f, 55f}, {100f, 56f},                    // the hills and the land at the horizon
                {150f, 25f}, {42f, 25f}};                                   // inside the wings
        for (float[] p : dark) assertFalse(p[0] + ", " + p[1], SupernaturalOverlay.lit(p[0], p[1]));
        float[][] sky = {{60f, 5f}, {186f, 10f}, {10f, 45f}, {96f, 40f}, {100f, 48f}, {114f, 44f}, {190f, 40f}};
        for (float[] p : sky) assertTrue(p[0] + ", " + p[1], SupernaturalOverlay.lit(p[0], p[1]));
        // Nothing below the ridge or the horizon, wherever it is.
        for (float xx = 0f; xx <= 192f; xx += 0.5f) {
            float ridge = SupernaturalScene.hillTop(xx);
            assertTrue(ridge <= SupernaturalScene.HORIZON && ridge > 48f);
            assertFalse(SupernaturalOverlay.lit(xx, ridge + 0.05f));
        }
    }

    /** Two wings spread wide over the sky, mirrored, above the horizon and clear of the dock. */
    @Test public void wingsSpreadOverTheSky() {
        float[] wing = SupernaturalOverlay.wing();
        assertEquals(0, wing.length % 2);
        float left = Float.MAX_VALUE, right = -Float.MAX_VALUE, top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (int i = 0; i < wing.length; i += 2) {
            left = Math.min(left, wing[i]);
            right = Math.max(right, wing[i]);
            top = Math.min(top, wing[i + 1]);
            bottom = Math.max(bottom, wing[i + 1]);
        }
        // One wing reaches from near the middle far to the side; the other is its mirror image round WING_X.
        assertTrue(left > SupernaturalOverlay.WING_X && left < SupernaturalOverlay.WING_X + 6f);
        assertTrue(right - left > 60f);
        assertTrue(top >= 0f && bottom < SupernaturalScene.HORIZON);
    }

    /** The logo picture's place: 6 % of the screen high at most, aspect kept, centred on the lettering. */
    @Test public void logoKeepsItsAspectInTheLetteringsPlace() {
        float[] out = new float[4];
        float[] l = SupernaturalScene.LETTERING;
        float cx = l[0] + l[2] / 2f, cy = l[1] + l[3] / 2f;
        SupernaturalOverlay.logoBox(400, 100, out);   // 4:1
        assertEquals(l[3], out[3] - out[1], 1e-4f);
        assertEquals(4f * l[3], out[2] - out[0], 1e-3f);
        assertEquals(cx, (out[0] + out[2]) / 2f, 1e-4f);
        assertEquals(cy, (out[1] + out[3]) / 2f, 1e-4f);
        SupernaturalOverlay.logoBox(4000, 100, out);  // far too wide: narrowed to the widest place, aspect kept
        assertEquals(SupernaturalOverlay.LOGO_MAX_W, out[2] - out[0], 1e-3f);
        assertEquals(SupernaturalOverlay.LOGO_MAX_W / 40f, out[3] - out[1], 1e-4f);
        assertTrue(out[2] <= SupernaturalScene.UNITS_W);
        assertFalse(PersonalMotions.underPills(out));
        SupernaturalOverlay.logoBox(0, 100, out);
        assertEquals(0f, out[2] - out[0], 0f);
    }

    /** The cover over the painted lettering stays in the sky: below the pills, above the motel's roof, right of the pole. */
    @Test public void letteringCoverStaysInTheSky() {
        float[] cover = SupernaturalOverlay.letteringCover();
        float[] l = SupernaturalScene.LETTERING;
        assertTrue(cover[0] < l[0] && cover[1] <= l[1] && cover[2] > l[0] + l[2] && cover[3] >= l[1] + l[3]);
        assertFalse(PersonalMotions.underPills(cover));
        assertTrue(cover[3] < 43.4f);
        assertTrue(cover[0] > 121.5f);
    }
}
