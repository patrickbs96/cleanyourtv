package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorldDockGeometryTest {

    @Test
    public void houseStripeQuarters() {
        assertEquals(0xFF7A1B1B, WorldDockDecoration.stripeColorAt(0.1f));
        assertEquals(0xFFC9A84C, WorldDockDecoration.stripeColorAt(0.3f));
        assertEquals(0xFF1B4D2E, WorldDockDecoration.stripeColorAt(0.6f));
        assertEquals(0xFF1E3A6B, WorldDockDecoration.stripeColorAt(0.99f));
    }

    @Test
    public void quarterBoundaryStartsTheNextHouse() {
        assertEquals(0xFFC9A84C, WorldDockDecoration.stripeColorAt(0.25f));
    }

    @Test
    public void ticksEveryThreePointTwoPercent() {
        assertEquals(31, WorldDockDecoration.tickCount(1000f, 1f));
    }

    /** Supernatural's salt line: fine white grains with gaps, made once from a fixed seed, so it never crawls. */
    @Test
    public void saltLineIsFineWhiteGrain() {
        int w = WorldDockDecoration.SALT_TILE_PX, h = 3;
        int[] grain = WorldDockDecoration.saltGrain(w, h);
        assertEquals(w * h, grain.length);
        assertTrue(java.util.Arrays.equals(grain, WorldDockDecoration.saltGrain(w, h)));
        int grains = 0;
        for (int px : grain) {
            if (px == 0) continue;
            grains++;
            assertEquals("white only", 0xFFFFFF, px & 0xFFFFFF);
            assertTrue("a grain shows", (px >>> 24) >= 0x40);
        }
        // Grainy, not a solid line and not a sparse dust: between a quarter and two thirds of the pixels.
        assertTrue(grains > w * h / 4);
        assertTrue(grains < w * h * 2 / 3);
        // A lone empty column is a gap between grains; two in a row (the tile repeats, so round the end too) a hole.
        for (int x = 0; x < w; x++) {
            assertTrue("hole at " + x, columnHasSalt(grain, w, h, x) || columnHasSalt(grain, w, h, (x + 1) % w));
        }
    }

    private static boolean columnHasSalt(int[] grain, int w, int h, int x) {
        for (int y = 0; y < h; y++) if (grain[y * w + x] != 0) return true;
        return false;
    }

    @Test
    public void unsupportedConfigFallsBackToClean() {
        // A Config stub is hard to build in a JVM test, so World.dockStyleSupported has an overload with the four values.
        assertFalse(World.dockStyleSupported(true, true, false, 0));
        assertFalse(World.dockStyleSupported(true, false, true, 0));
        assertFalse(World.dockStyleSupported(true, false, false, 1));
        assertTrue(World.dockStyleSupported(true, false, false, 0));
    }
}
