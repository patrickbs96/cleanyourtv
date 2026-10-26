package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

/**
 * Plain JVM tests for the motion of Halloween to Christmas: which look moves how, Advent's candles, a calm flicker,
 * the dock and the header.
 */
public class OccasionMotionsBTest {
    private static SeasonsToday today(int y, int m, int d) {
        return SeasonsToday.resolve(LocalDate.of(y, m, d), List.of(), false, false);
    }

    private static final SeasonsToday TODAY = today(2026, 12, 13);
    /** The header on the top left in units, and the dock's top edge. */
    private static final float HEADER_RIGHT = 76f, DOCK = 76.6f;

    @Test public void theSixOccasionsMove() {
        assertTrue(LookMotion.forLook(Look.HALLOWEEN, TODAY) instanceof OccasionMotionsB.Halloween);
        assertTrue(LookMotion.forLook(Look.ST_MARTIN, TODAY) instanceof OccasionMotionsB.Lanterns);
        assertTrue(LookMotion.forLook(Look.NIKOLAUS_EVE, TODAY) instanceof OccasionMotionsB.Nikolaus);
        assertTrue(LookMotion.forLook(Look.NIKOLAUS, TODAY) instanceof OccasionMotionsB.Nikolaus);
        assertTrue(LookMotion.forLook(Look.ADVENT, TODAY) instanceof OccasionMotionsB.Advent);
        assertTrue(LookMotion.forLook(Look.CHRISTMAS, TODAY) instanceof OccasionMotionsB.Christmas);
        for (Look look : new Look[]{Look.HALLOWEEN, Look.ST_MARTIN, Look.NIKOLAUS_EVE, Look.NIKOLAUS, Look.ADVENT,
                Look.CHRISTMAS}) {
            assertTrue(look.id, SeasonsOverlay.ticks(LookMotion.forLook(look, TODAY)));
            assertNotSame(look.id, LookMotion.forLook(look, TODAY), LookMotion.forLook(look, TODAY));
        }
        assertNull(OccasionMotionsB.of(Look.WINTER, TODAY));
        assertNull(OccasionMotionsB.of(Look.NEW_YEAR, TODAY));
        assertNull(OccasionMotionsB.of(null, TODAY));
        // Without a day, Advent still builds, with no candle lit.
        assertEquals(0, ((OccasionMotionsB.Advent) OccasionMotionsB.of(Look.ADVENT, null)).flames());
    }

    /** Spec § 4.2: as many candles burn as Sundays of Advent have passed, one more each Sunday, four at most. */
    @Test public void adventLightsAsManyCandlesAsSundays() {
        for (int n = 0; n <= 4; n++) assertEquals(n, OccasionMotionsB.Advent.flamesFor(n));
        assertEquals(0, OccasionMotionsB.Advent.flamesFor(-1));
        assertEquals(4, OccasionMotionsB.Advent.flamesFor(5));
        // Through Advent 2026 (1st Advent on 29.11.): the motion lights today's candles.
        int[][] days = {{11, 29, 1}, {12, 4, 1}, {12, 7, 2}, {12, 13, 3}, {12, 19, 3}, {12, 20, 4}, {12, 23, 4}};
        for (int[] d : days) {
            SeasonsToday t = today(2026, d[0], d[1]);
            assertEquals(Look.ADVENT, t.look);
            assertEquals(d[2], t.adventCandles);
            OccasionMotionsB.Advent advent = (OccasionMotionsB.Advent) LookMotion.forLook(t.look, t);
            assertEquals(d[0] + "-" + d[1], t.adventCandles, advent.flames());
        }
        // On the 5th and 6th it is Advent too, but St. Nicholas' look shows: no candle flames there.
        for (int d = 5; d <= 6; d++) {
            SeasonsToday t = today(2026, 12, d);
            assertTrue(t.adventCandles > 0);
            assertTrue(LookMotion.forLook(t.look, t) instanceof OccasionMotionsB.Nikolaus);
        }
    }

    /** The flames burn at the painted candles' wicks (occasions_b.py: foot - CANDLE_H - FLAME_UP), lit left to right. */
    @Test public void flamesSitOnThePaintedWicks() {
        float[] feet = {67.4f, 66.4f, 66.4f, 67.4f};
        for (int i = 0; i < feet.length; i++) {
            assertEquals(feet[i] - 16f - 2.6f, OccasionMotionsB.Advent.FLAME_Y[i], 1e-4f);
            if (i > 0) assertTrue(OccasionMotionsB.Advent.FLAME_X[i] > OccasionMotionsB.Advent.FLAME_X[i - 1]);
        }
    }

    /** The carved faces breathe like a candle: between about half and full light, and never jump (no strobe). */
    @Test public void halloweenFacesFlickerCalmly() {
        float lo = 1f, hi = 0f;
        for (int i = 0; i < 3; i++) {
            float last = OccasionMotionsB.Halloween.flicker(0, i);
            for (double t = 1.0 / 30; t < 120; t += 1.0 / 30) {
                float f = OccasionMotionsB.Halloween.flicker(t, i);
                assertTrue(f >= 0.55f && f <= 1.001f);
                assertTrue("jump at " + t + ": " + Math.abs(f - last), Math.abs(f - last) <= 0.03f);
                lo = Math.min(lo, f);
                hi = Math.max(hi, f);
                last = f;
            }
        }
        // It does flicker visibly, though.
        assertTrue(hi - lo > 0.3f);
        // The three faces do not flicker in step.
        assertTrue(Math.abs(OccasionMotionsB.Halloween.flicker(10, 0) - OccasionMotionsB.Halloween.flicker(10, 1))
                > 1e-3f);
    }

    /** The lanterns swing a little either way, at most SWING degrees, and stay above the dock. */
    @Test public void lanternsSwingGently() {
        assertTrue(OccasionMotionsB.Lanterns.SWING <= 6f);
        float most = 0f;
        for (double t = 0; t < 60; t += 0.05) {
            for (int i = 0; i < OccasionMotionsB.Lanterns.CHILD_X.length; i++) {
                float a = OccasionMotionsB.Lanterns.swing(t, i);
                assertTrue(Math.abs(a) <= OccasionMotionsB.Lanterns.SWING + 1e-4f);
                most = Math.max(most, Math.abs(a));
            }
        }
        assertTrue(most > OccasionMotionsB.Lanterns.SWING * 0.9f);
        assertEquals(7, OccasionMotionsB.Lanterns.CHILD_X.length);
        assertTrue(OccasionMotionsB.Lanterns.lowest() <= DOCK);
    }

    /** As many as the mockup draws: 45 flakes at St. Nicholas, 50 at Christmas with 30 lights, four bats. */
    @Test public void particleCountsAreTheMockups() {
        assertEquals(45, new OccasionMotionsB.Nikolaus().flakes());
        assertEquals(50, new OccasionMotionsB.Christmas().flakes());
        assertEquals(30, OccasionMotionsB.Christmas.treeLights().length);
        assertEquals(4, OccasionMotionsB.Halloween.BATS);
        assertEquals(3, OccasionMotionsB.Halloween.PUMPKIN_X.length);
    }

    /** The tree's lights hang on the tree: inside its outline, below the star and above its foot. */
    @Test public void treeLightsHangOnTheTree() {
        float top = OccasionMotionsB.Christmas.TREE_TOP, base = OccasionMotionsB.Christmas.TREE_BASE;
        for (float[] l : OccasionMotionsB.Christmas.treeLights()) {
            assertTrue(l[1] > top && l[1] < base);
            float half = (l[1] - top) / (base - top) * OccasionMotionsB.Christmas.TREE_SPREAD;
            assertTrue(Math.abs(l[0] - OccasionMotionsB.Christmas.TREE_X) <= half * 0.9f + 1e-3f);
        }
    }

    /** Spec § 3: nothing moves behind the dock or over the greeting and clock on the top left. */
    @Test public void motionStaysAboveTheDockAndOutOfTheHeader() {
        float dock = SeasonMotions.DOCK;
        // Pumpkins stand whole above the dock; their glow is cut at its edge while drawing.
        assertTrue(OccasionMotionsB.Halloween.GROUND < DOCK);
        for (int i = 0; i < 3; i++) {
            float x = OccasionMotionsB.Halloween.PUMPKIN_X[i], r = OccasionMotionsB.Halloween.PUMPKIN_R[i];
            assertTrue(x - OccasionMotionsB.Halloween.GLOW * r > HEADER_RIGHT);
        }
        assertTrue(OccasionMotionsB.Halloween.batReach() <= dock);
        assertTrue(OccasionMotionsB.Halloween.BAT_LEFT - 2 * OccasionMotionsB.Halloween.BAT_S > OccasionMotionsA.HEADER_RIGHT);
        assertEquals(0f, OccasionMotionsB.Halloween.batAlpha(OccasionMotionsB.Halloween.BAT_LEFT), 1e-6f);
        for (double t = 0; t < 500; t += 3.1) {
            float x = OccasionMotionsB.Halloween.batX(0.3f, 0.5f, t);
            assertTrue(x >= OccasionMotionsB.Halloween.BAT_LEFT && x < OccasionMotionsB.Halloween.BAT_RIGHT);
        }
        // Lanterns right of the header.
        assertTrue(OccasionMotionsB.Lanterns.tipX(0) - 2.6f * OccasionMotionsB.Lanterns.R > HEADER_RIGHT);
        // Advent: flames and their glow above the dock, the lights and the window right of the header.
        for (float y : OccasionMotionsB.Advent.FLAME_Y) assertTrue(y + OccasionMotionsB.Advent.FLAME_GLOW < DOCK);
        assertTrue(OccasionMotionsB.Advent.LIGHTS_FROM - OccasionMotionsB.Advent.LIGHT_GLOW > HEADER_RIGHT);
        assertTrue(OccasionMotionsB.Advent.WIN_X > HEADER_RIGHT);
        assertTrue(OccasionMotionsB.Advent.WIN_Y + OccasionMotionsB.Advent.WIN_H < DOCK);
        // Christmas: the tree right of the header, its lights above the dock.
        assertTrue(OccasionMotionsB.Christmas.TREE_X - OccasionMotionsB.Christmas.TREE_SPREAD > HEADER_RIGHT);
        assertTrue(OccasionMotionsB.Christmas.TREE_BASE + OccasionMotionsB.Christmas.LIGHT_GLOW < DOCK);
        // St. Nicholas' lantern glow above the dock and right of the header.
        assertTrue(OccasionMotionsB.Nikolaus.LANTERN_Y + OccasionMotionsB.Nikolaus.LANTERN_GLOW < DOCK);
        assertTrue(OccasionMotionsB.Nikolaus.LANTERN_X - OccasionMotionsB.Nikolaus.LANTERN_GLOW > HEADER_RIGHT);
    }
}
