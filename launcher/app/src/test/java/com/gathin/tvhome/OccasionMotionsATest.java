package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

/** Plain JVM tests for the motion of New Year to Father's Day: which look moves how, timing, the dock and the header. */
public class OccasionMotionsATest {
    private static final SeasonsToday TODAY = SeasonsToday.resolve(LocalDate.of(2027, 1, 1), List.of(), false, false);

    @Test public void theSixOccasionsMove() {
        assertTrue(LookMotion.forLook(Look.NEW_YEAR, TODAY) instanceof OccasionMotionsA.Fireworks);
        assertTrue(LookMotion.forLook(Look.CARNIVAL, TODAY) instanceof OccasionMotionsA.Carnival);
        assertTrue(LookMotion.forLook(Look.VALENTINE, TODAY) instanceof OccasionMotionsA.Valentine);
        assertTrue(LookMotion.forLook(Look.EASTER, TODAY) instanceof OccasionMotionsA.Butterflies);
        assertTrue(LookMotion.forLook(Look.MOTHERS_DAY, TODAY) instanceof OccasionMotionsA.Motes);
        assertTrue(LookMotion.forLook(Look.FATHERS_DAY, TODAY) instanceof OccasionMotionsA.Meadow);
        for (Look look : new Look[]{Look.NEW_YEAR, Look.CARNIVAL, Look.VALENTINE, Look.EASTER, Look.MOTHERS_DAY,
                Look.FATHERS_DAY}) {
            assertTrue(look.id, SeasonsOverlay.ticks(LookMotion.forLook(look, TODAY)));
            assertNotSame(look.id, LookMotion.forLook(look, TODAY), LookMotion.forLook(look, TODAY));
        }
        // The seasons keep theirs; the later occasions are still until their task.
        assertTrue(LookMotion.forLook(Look.WINTER, TODAY) instanceof SeasonMotions.Fall);
        assertNull(OccasionMotionsA.of(Look.WINTER));
        assertNull(OccasionMotionsA.of(null));
        assertSame(LookMotion.NONE, LookMotion.forLook(Look.HALLOWEEN, TODAY));
    }

    /** As many as the mockup draws: 60 pieces of confetti, 12 petals; three fireworks of 28 sparks each. */
    @Test public void particleCountsAreTheMockups() {
        assertEquals(60, new OccasionMotionsA.Carnival().confettiCount());
        assertEquals(12, new OccasionMotionsA.Valentine().petalCount());
        assertEquals(3, OccasionMotionsA.Fireworks.X.length);
        assertEquals(28, OccasionMotionsA.Fireworks.OUTER);
        assertEquals(2, OccasionMotionsA.Butterflies.BX.length);
        assertEquals(4, OccasionMotionsA.Meadow.CLOUDS);
    }

    /** Each firework every 3.2 s, rising and bursting in under 1.5 s, so most of the time the sky is still. */
    @Test public void fireworksAreShortAndComeEveryFewSeconds() {
        assertEquals(3.2, OccasionMotionsA.Fireworks.PERIOD, 1e-9);
        assertTrue(OccasionMotionsA.Fireworks.RISE + OccasionMotionsA.Fireworks.BURST <= 1.5);
        assertEquals(0.0, OccasionMotionsA.Fireworks.age(3.2, 0), 1e-9);
        assertEquals(1.3, OccasionMotionsA.Fireworks.age(0, 1), 1e-9);
        assertEquals(0f, OccasionMotionsA.Fireworks.spread(0f), 1e-6f);
        assertEquals(1f, OccasionMotionsA.Fireworks.spread(1f), 1e-6f);
        assertEquals(1f, OccasionMotionsA.Fireworks.fade(0f), 1e-6f);
        assertEquals(0f, OccasionMotionsA.Fireworks.fade(1f), 1e-6f);
        assertTrue(OccasionMotionsA.Fireworks.spread(0.3f) > 0.5f);
        // Small: a burst's glow spans at most an eighth of the width.
        assertTrue(2 * OccasionMotionsA.Fireworks.R_MAX * OccasionMotionsA.Fireworks.GLOW <= 0.135f);
    }

    /** Spec § 3: nothing moves behind the dock or over the greeting and clock on the top left. */
    @Test public void motionStaysAboveTheDockAndOutOfTheHeader() {
        float dock = SeasonMotions.DOCK;
        assertTrue(OccasionMotionsA.Fireworks.lowest() <= dock);
        assertTrue(OccasionMotionsA.Fireworks.leftmost() > OccasionMotionsA.HEADER_RIGHT);
        assertTrue(OccasionMotionsA.Carnival.streamerReach() <= dock);
        for (float x : OccasionMotionsA.Carnival.STREAMER_X) {
            assertTrue(x - OccasionMotionsA.Carnival.SWAY - 0.004f > OccasionMotionsA.HEADER_RIGHT);
        }
        assertTrue(OccasionMotionsA.Butterflies.lowest() <= dock);
        assertTrue(OccasionMotionsA.Butterflies.leftmost() > OccasionMotionsA.HEADER_RIGHT);
        assertTrue(OccasionMotionsA.Motes.BOTTOM + 0.03f <= dock);
        assertTrue(OccasionMotionsA.Motes.LEFT - 0.02f > OccasionMotionsA.HEADER_RIGHT);
        assertTrue(OccasionMotionsA.Motes.PETAL_TO + 0.01f <= dock);
        assertTrue(OccasionMotionsA.Meadow.seedReach() <= dock);
        assertTrue(OccasionMotionsA.Meadow.CLOUD_LEFT - OccasionMotionsA.Meadow.cloudReach()
                > OccasionMotionsA.HEADER_RIGHT);
        // The candle's flame where occasions_a.py paints it, above the dock.
        assertTrue(OccasionMotionsA.Valentine.FLAME_Y < dock);
        assertTrue(OccasionMotionsA.inHeader(0.1f, 0.1f));
        assertFalse(OccasionMotionsA.inHeader(0.5f, 0.1f));
        assertFalse(OccasionMotionsA.inHeader(0.1f, 0.5f));
    }

    /** The flame flickers between three quarters and full size. */
    @Test public void theCandleFlickersWithinItsSize() {
        for (double t = 0; t < 60; t += 0.033) {
            float f = OccasionMotionsA.Valentine.flicker(t);
            assertTrue(f >= 0.75f - 1e-6f && f <= 1f + 1e-6f);
        }
    }

    /** A Mother's Day petal falls from the bunch onto the table, lies a moment, fades, and is gone until its next turn. */
    @Test public void aPetalFallsOntoTheTableAndFades() {
        assertEquals(OccasionMotionsA.Motes.PETAL_FROM, OccasionMotionsA.Motes.petalY(0), 1e-6f);
        assertEquals(OccasionMotionsA.Motes.PETAL_TO, OccasionMotionsA.Motes.petalY(OccasionMotionsA.Motes.PETAL_FALL),
                1e-6f);
        assertEquals(OccasionMotionsA.Motes.PETAL_TO, OccasionMotionsA.Motes.petalY(7), 1e-6f);
        assertTrue(Float.isNaN(OccasionMotionsA.Motes.petalY(9)));
        assertEquals(1f, OccasionMotionsA.Motes.petalAlpha(3), 1e-6f);
        assertTrue(OccasionMotionsA.Motes.petalAlpha(7) < 1f);
        assertEquals(0f, OccasionMotionsA.Motes.petalAlpha(9), 1e-6f);
        assertTrue(OccasionMotionsA.Motes.PETAL_FALL + OccasionMotionsA.Motes.PETAL_REST
                < OccasionMotionsA.Motes.PETAL_PERIOD);
    }

    /** Clouds drift right and come in softly on the left, never popping up. */
    @Test public void cloudsDriftRightAndComeInSoftly() {
        float x0 = OccasionMotionsA.Meadow.cloudX(0.5f, 0);
        assertEquals(x0 + 0.006f, OccasionMotionsA.Meadow.cloudX(0.5f, 1), 1e-5f);
        for (double t = 0; t < 1000; t += 3.7) {
            float x = OccasionMotionsA.Meadow.cloudX(0.2f, t);
            assertTrue(x >= OccasionMotionsA.Meadow.CLOUD_LEFT && x < OccasionMotionsA.Meadow.CLOUD_RIGHT);
        }
        assertEquals(0f, OccasionMotionsA.Meadow.cloudAlpha(OccasionMotionsA.Meadow.CLOUD_LEFT), 1e-6f);
        assertEquals(1f, OccasionMotionsA.Meadow.cloudAlpha(0.7f), 1e-6f);
    }

    /** At night the bright looks' motion is dimmed with their scene; the dark ones keep theirs. */
    @Test public void brightLooksDimTheirMotionAtNight() {
        assertEquals(SeasonMotions.NIGHT, OccasionMotionsA.brightness(Look.EASTER, false), 1e-6f);
        assertEquals(SeasonMotions.NIGHT, OccasionMotionsA.brightness(Look.FATHERS_DAY, false), 1e-6f);
        assertEquals(1f, OccasionMotionsA.brightness(Look.EASTER, true), 1e-6f);
        assertEquals(1f, OccasionMotionsA.brightness(Look.NEW_YEAR, false), 1e-6f);
        assertEquals(1f, OccasionMotionsA.brightness(Look.CARNIVAL, false), 1e-6f);
    }
}
