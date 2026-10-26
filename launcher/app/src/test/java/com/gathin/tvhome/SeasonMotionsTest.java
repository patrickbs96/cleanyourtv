package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

/** Plain JVM tests for the four seasons' motion: which look moves how, the mockup's particles, the fall and the dock. */
public class SeasonMotionsTest {
    private static final SeasonsToday TODAY = SeasonsToday.resolve(LocalDate.of(2027, 1, 10), List.of(), false, false);

    @Test public void theSeasonsMoveAndTheOtherLooksStayStillForNow() {
        assertEquals(SeasonMotions.Fall.SNOW, ((SeasonMotions.Fall) LookMotion.forLook(Look.WINTER, TODAY)).kind);
        assertEquals(SeasonMotions.Fall.BLOSSOM, ((SeasonMotions.Fall) LookMotion.forLook(Look.SPRING, TODAY)).kind);
        assertEquals(SeasonMotions.Fall.LEAF, ((SeasonMotions.Fall) LookMotion.forLook(Look.AUTUMN, TODAY)).kind);
        assertTrue(LookMotion.forLook(Look.SUMMER, TODAY) instanceof SeasonMotions.Summer);
        for (Look look : new Look[]{Look.WINTER, Look.SPRING, Look.SUMMER, Look.AUTUMN}) {
            assertTrue(look.id, SeasonsOverlay.ticks(LookMotion.forLook(look, TODAY)));
            // A new motion each time, so a look change never shares one between overlays.
            assertNotSame(look.id, LookMotion.forLook(look, TODAY), LookMotion.forLook(look, TODAY));
        }
        assertSame(LookMotion.NONE, LookMotion.forLook(Look.ADVENT, TODAY));
        assertSame(LookMotion.NONE, LookMotion.forLook(null, TODAY));
        assertNull(SeasonMotions.of(Look.BIRTHDAY));
    }

    /** As many as the mockup draws: 70 flakes, 40 blossoms, 26 leaves; summer's 14 fireflies and 18 lines. */
    @Test public void particleCountsAreTheMockups() {
        assertEquals(70, new SeasonMotions.Fall(SeasonMotions.Fall.SNOW).count());
        assertEquals(40, new SeasonMotions.Fall(SeasonMotions.Fall.BLOSSOM).count());
        assertEquals(26, new SeasonMotions.Fall(SeasonMotions.Fall.LEAF).count());
        assertEquals(14, SeasonMotions.Summer.FIREFLIES);
        assertEquals(18, SeasonMotions.Summer.LINES);
    }

    /** The mockup's rng(5): the first particle of the snow is where the mockup has it. */
    @Test public void particlesFollowTheMockupsSeededRandom() {
        float[][] snow = SeasonMotions.particles(70, 5);
        assertEquals(0.23800573f, snow[0][0], 1e-6f);
        assertEquals(0.73208573f, snow[0][1], 1e-6f);
        assertEquals(0.24307512f, snow[0][2], 1e-6f);
        assertEquals(0.85294873f, snow[0][3], 1e-6f);
        for (float[] p : snow) {
            for (float v : p) assertTrue(v >= 0f && v < 1f);
        }
    }

    /** A flake falls at the mockup's speed and comes back in at the top, between the top and just above the dock. */
    @Test public void fallWrapsAboveTheDock() {
        float y0 = SeasonMotions.fallY(0.5f, 0.5f, 0, 0.04f);
        // .04 heights a second at s = .5: one second later .04 lower.
        assertEquals(y0 + 0.04f, SeasonMotions.fallY(0.5f, 0.5f, 1, 0.04f), 1e-5f);
        for (double t = 0; t < 600; t += 0.37) {
            float y = SeasonMotions.fallY(0.9f, 0.3f, t, 0.045f);
            assertTrue(y >= SeasonMotions.FALL_START && y < SeasonMotions.FALL_END);
        }
        assertTrue(SeasonMotions.FALL_END < SeasonMotions.DOCK);
        // Gone at the end, fully there well above it.
        assertEquals(0f, SeasonMotions.fallAlpha(SeasonMotions.FALL_END), 1e-6f);
        assertEquals(1f, SeasonMotions.fallAlpha(0.3f), 1e-6f);
        assertTrue(SeasonMotions.fallAlpha(SeasonMotions.FALL_END - 0.02f) < 0.5f);
    }

    /** Spec § 3: the fireflies' glow and the glitter on the water stay above the dock's top edge. */
    @Test public void summerStaysAboveTheDock() {
        assertTrue(SeasonMotions.Summer.fireflyReach() <= SeasonMotions.DOCK);
        assertTrue(SeasonMotions.Summer.glitterReach() <= SeasonMotions.DOCK);
        // The glitter starts under the sea line (.601 in seasons.py) and widens towards the viewer.
        assertTrue(SeasonMotions.Summer.GLITTER_TOP > 0.601f);
        assertTrue(SeasonMotions.Summer.halfWidth(17) > SeasonMotions.Summer.halfWidth(0));
    }
}
