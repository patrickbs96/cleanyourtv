package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Plain JVM tests for Middle-earth's motion (spec § 4.1, § 4.4): the eye's pulse and its cone's sweep, both still with
 * reduced motion, and that smoke, sparks, glows and the logo keep to their places (header, pills, logo area, dock).
 */
public class MiddleEarthOverlayTest {
    private static final float[] PILLS = PersonalMotions.PILLS;

    /** The cone sweeps over the land about every 18 s, 0.9 radians to either side of straight down. */
    @Test public void coneSweepsEighteenSecondsWide() {
        assertEquals(18000L, MiddleEarthOverlay.CONE_PERIOD_MS, 1000L);
        assertEquals(0.9f, MiddleEarthOverlay.CONE_SWEEP, 0f);
        float most = 0f, least = 0f;
        for (long t = 0; t <= 60_000; t += 7) {
            float a = MiddleEarthOverlay.coneAngle(t, true);
            assertTrue(t + ": " + a, Math.abs(a) <= MiddleEarthOverlay.CONE_SWEEP + 1e-6f);
            most = Math.max(most, a);
            least = Math.min(least, a);
            // One period on, the cone stands where it stood.
            assertEquals(a, MiddleEarthOverlay.coneAngle(t + MiddleEarthOverlay.CONE_PERIOD_MS, true), 1e-4f);
        }
        assertEquals(0.9f, most, 0.01f);
        assertEquals(-0.9f, least, 0.01f);
        // Slowly: never more than a hundredth of a radian from one 33 ms frame to the next.
        for (long t = 0; t <= 20_000; t += WorldOverlay.FRAME_MS) {
            float step = MiddleEarthOverlay.coneAngle(t + WorldOverlay.FRAME_MS, true)
                    - MiddleEarthOverlay.coneAngle(t, true);
            assertTrue(t + ": " + step, Math.abs(step) < 0.012f);
        }
    }

    /** Swept as far as it goes, the cone still points below the horizon: never up into the header, logo or pills. */
    @Test public void coneAlwaysPointsDown() {
        float widest = MiddleEarthOverlay.CONE_SWEEP + MiddleEarthOverlay.coneHalfAngle();
        assertTrue("" + widest, widest < Math.PI / 2 - 0.2);
    }

    /** The eye pulses (spec § 4.1): brighter and dimmer within its range, a few seconds a breath. */
    @Test public void eyePulses() {
        float most = 0f, least = 2f;
        for (long t = 0; t <= 30_000; t += 5) {
            float p = MiddleEarthOverlay.eyePulse(t, true);
            assertTrue(t + ": " + p, p >= MiddleEarthOverlay.EYE_MIN - 1e-6f && p <= 1f + 1e-6f);
            most = Math.max(most, p);
            least = Math.min(least, p);
        }
        assertEquals(1f, most, 0.01f);
        assertEquals(MiddleEarthOverlay.EYE_MIN, least, 0.01f);
        assertTrue(MiddleEarthOverlay.EYE_PULSE_MS >= 2000 && MiddleEarthOverlay.EYE_PULSE_MS <= 4000);
    }

    /**
     * With reduced motion the overlay never runs (WorldOverlay.shouldRun) and draws the night's eye still: one
     * brightness and the cone straight down, whatever the clock says.
     */
    @Test public void eyeStaysStillWithReducedMotion() {
        boolean moving = WorldOverlay.shouldRun(World.MIDDLE_EARTH, true, true, true, false);
        assertFalse(moving);
        for (long t = 0; t <= 120_000; t += 3) {
            assertEquals(0f, MiddleEarthOverlay.coneAngle(t, moving), 0f);
            assertEquals(MiddleEarthOverlay.EYE_REST, MiddleEarthOverlay.eyePulse(t, moving), 0f);
        }
        assertTrue(MiddleEarthOverlay.EYE_REST >= MiddleEarthOverlay.EYE_MIN && MiddleEarthOverlay.EYE_REST <= 1f);
        assertTrue(WorldOverlay.shouldRun(World.MIDDLE_EARTH, true, false, true, false));
    }

    /** The eye's glow, the smoke and the sparks stay below the pills and the header (they end at y 34). */
    @Test public void glowsSmokeAndSparksKeepClearOfHeaderAndPills() {
        float[] eye = MiddleEarthScene.EYE;
        assertTrue(eye[1] - MiddleEarthOverlay.EYE_GLOW_RY >= PILLS[3]);
        float[] chimney = MiddleEarthScene.CHIMNEY_TOP;
        assertTrue(chimney[1] - MiddleEarthOverlay.SMOKE_RISE - MiddleEarthOverlay.SMOKE_R_MAX >= PILLS[3]);
        float[] crater = MiddleEarthScene.CRATER;
        float[] header = MiddleEarthScene.HEADER;
        assertTrue(MiddleEarthOverlay.EMBER_RISE > 3f);
        assertTrue(crater[1] - MiddleEarthOverlay.EMBER_RISE >= header[3]);
        assertTrue(MiddleEarthOverlay.CRATER_GLOW_Y - MiddleEarthOverlay.CRATER_GLOW_RY >= header[3]);
    }

    /** The mist drifts through its band, above the dock (the company's ground) and below the header. */
    @Test public void mistStaysInItsBand() {
        for (float[] band : new float[][]{MiddleEarthScene.MIST_MORNING, MiddleEarthScene.MIST_DAY}) {
            assertTrue(band[1] + MiddleEarthOverlay.MIST_RY <= MiddleEarthScene.DOCK);
            assertTrue(band[0] - MiddleEarthOverlay.MIST_RY > MiddleEarthScene.HEADER[3]);
        }
    }

    /** Ash falls everywhere above the dock but in the header, where the greeting, clock and date stand. */
    @Test public void ashKeepsOutOfTheHeader() {
        assertTrue(MiddleEarthOverlay.inHeader(10f, 10f));
        assertTrue(MiddleEarthOverlay.inHeader(75f, 33f));
        assertFalse(MiddleEarthOverlay.inHeader(80f, 10f));
        assertFalse(MiddleEarthOverlay.inHeader(10f, 40f));
    }

    /**
     * The logo picture in the free area at the top middle (spec § 4.4): centred at x 57 %, y 11 %, at most 18 % wide
     * and 8 % high, aspect kept, in all three moods at the same place.
     */
    @Test public void logoSitsInTheFreeAreaAspectKept() {
        float[] l = MiddleEarthScene.LOGO;
        float cx = 0.57f * MiddleEarthScene.UNITS_W, cy = 0.11f * MiddleEarthScene.UNITS_H;
        float[] out = new float[4];
        MiddleEarthOverlay.logoBox(200, 100, out);   // 2:1, as high as allowed
        assertEquals(l[3], out[3] - out[1], 1e-3f);
        assertEquals(2f * l[3], out[2] - out[0], 1e-3f);
        assertEquals(cx, (out[0] + out[2]) / 2f, 1e-3f);
        assertEquals(cy, (out[1] + out[3]) / 2f, 1e-3f);
        MiddleEarthOverlay.logoBox(1000, 100, out);  // 10:1: narrowed to 18 %, aspect kept
        assertEquals(0.18f * MiddleEarthScene.UNITS_W, out[2] - out[0], 1e-3f);
        assertEquals(0.018f * MiddleEarthScene.UNITS_W, out[3] - out[1], 1e-3f);
        assertEquals(cy, (out[1] + out[3]) / 2f, 1e-3f);
        MiddleEarthOverlay.logoBox(100, 400, out);   // tall: 8 % high, a quarter of that wide
        assertEquals(0.08f * MiddleEarthScene.UNITS_H, out[3] - out[1], 1e-3f);
        float[] free = MiddleEarthScene.LOGO_FREE;
        for (int[] size : new int[][]{{200, 100}, {1000, 100}, {100, 400}, {1, 1}}) {
            MiddleEarthOverlay.logoBox(size[0], size[1], out);
            assertTrue(out[0] >= free[0] - 1e-3f && out[2] <= free[0] + free[2] + 1e-3f);
            assertTrue(out[1] >= free[1] - 1e-3f && out[3] <= free[1] + free[3] + 1e-3f);
            assertFalse(PersonalMotions.underPills(out));
        }
        MiddleEarthOverlay.logoBox(0, 100, out);
        assertEquals(0f, out[2] - out[0], 0f);
    }

    /** Middle-earth has its overlay now (WorldOverlay.create makes a MiddleEarthOverlay). */
    @Test public void middleEarthHasAnOverlay() {
        assertTrue(WorldOverlay.hasOverlay(World.MIDDLE_EARTH, false));
        assertTrue(WorldOverlay.hasOverlay(World.MIDDLE_EARTH, true));
    }
}
