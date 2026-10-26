package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Plain JVM tests for where Supernatural's pictures paint what moves over them (SupernaturalScene): the picture rule
 * of spec § 3 (nothing that matters in the header on the top left, under the pills on the top right or behind the
 * dock; the car right of the app name directly above the dock) and the lettering's place for the logo picture.
 */
public class SupernaturalSceneTest {
    private static final float HEADER_RIGHT = 76f, HEADER_BOTTOM = 34f, DOCK = 766f / 1080f * 108;

    private static boolean inHeader(float[] box) {
        return box[0] < HEADER_RIGHT && box[1] < HEADER_BOTTOM;
    }

    private static float[] point(float[] p) {
        return new float[]{p[0], p[1], p[0], p[1]};
    }

    @Test public void theLetteringLiesBelowThePillsAtSixPercentOfTheHeight() {
        float[] box = SupernaturalScene.box(SupernaturalScene.LETTERING);
        assertFalse(PersonalMotions.underPills(box));
        assertFalse(inHeader(box));
        assertTrue("below the pills", box[1] >= PersonalMotions.PILLS[3]);
        assertTrue("above the horizon", box[3] < SupernaturalScene.HORIZON);
        assertEquals(0.06f * 108, SupernaturalScene.LETTERING[3], 0.01f);
        assertEquals(4, SupernaturalScene.LETTERING_SKY_DAY.length);
        assertEquals(4, SupernaturalScene.LETTERING_SKY_NIGHT.length);
    }

    @Test public void theCarStandsRightOfTheAppNameDirectlyAboveTheDock() {
        float[] car = SupernaturalScene.CAR;
        assertTrue("fully above the dock", car[1] <= DOCK);
        assertTrue("close above it", car[1] >= DOCK - 2f);
        assertTrue("right of the app name", car[0] >= 96f);
        assertTrue("fully on screen", car[0] + car[2] <= SupernaturalScene.UNITS_W);
        float[] lamp = SupernaturalScene.CAR_HEADLIGHT;
        assertTrue(lamp[0] >= car[0] && lamp[0] <= car[0] + car[2] && lamp[1] > car[1] - car[3] && lamp[1] < car[1]);
        float[] tail = SupernaturalScene.box(SupernaturalScene.CAR_TAIL_LIGHT);
        assertTrue(tail[0] >= car[0] + car[2] * 0.9f && tail[2] <= car[0] + car[2] + 0.5f && tail[3] < car[1]);
    }

    @Test public void theStarLiesOnTheRoadAboveTheDock() {
        float[] s = SupernaturalScene.STAR;
        assertTrue(s[1] + s[3] * SupernaturalScene.STAR_RAYS <= DOCK);
        assertTrue("ahead of the car", s[0] + s[2] * SupernaturalScene.STAR_RAYS < SupernaturalScene.CAR[0]);
        assertTrue(s[1] > SupernaturalScene.HORIZON);
    }

    @Test public void nothingThatMattersIsUnderThePillsOrInTheHeader() {
        float[][] boxes = {
                SupernaturalScene.box(SupernaturalScene.SIGN),
                SupernaturalScene.box(SupernaturalScene.NEON_MOTEL),
                SupernaturalScene.box(SupernaturalScene.NEON_VACANCY),
                SupernaturalScene.box(SupernaturalScene.CRACK_AREA),
                point(SupernaturalScene.CAR_HEADLIGHT),
                point(SupernaturalScene.CAR_ROOF_GLINT),
                {SupernaturalScene.WARD[0] - SupernaturalScene.WARD[2], SupernaturalScene.WARD[1] - SupernaturalScene.WARD[2],
                        SupernaturalScene.WARD[0] + SupernaturalScene.WARD[2], SupernaturalScene.WARD[1] + SupernaturalScene.WARD[2]},
        };
        for (float[] b : boxes) {
            assertFalse(PersonalMotions.underPills(b));
            assertFalse(inHeader(b));
            assertTrue(b[3] <= DOCK);
        }
        for (float[] m : SupernaturalScene.DOOR_MARKS) {
            assertFalse(PersonalMotions.underPills(point(m)));
        }
    }

    @Test public void motelsLettersSitInItsNeon() {
        float[] n = SupernaturalScene.NEON_MOTEL;
        assertEquals(5, SupernaturalScene.NEON_MOTEL_X.length);
        for (float x : SupernaturalScene.NEON_MOTEL_X) assertTrue(x > n[0] && x < n[0] + n[2]);
    }

    @Test public void theCracksLieInTheirAreaOnTheLeft() {
        float[] a = SupernaturalScene.box(SupernaturalScene.CRACK_AREA);
        for (float[] crack : SupernaturalScene.CRACKS) {
            assertEquals(0, crack.length % 2);
            for (int i = 0; i < crack.length; i += 2) {
                assertTrue(crack[i] >= a[0] && crack[i] <= a[2]);
                assertTrue(crack[i + 1] >= a[1] && crack[i + 1] <= a[3]);
            }
        }
        assertTrue("left of the middle", a[2] < 96f);
    }
}
