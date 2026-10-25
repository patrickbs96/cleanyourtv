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
    public void ticksEveryThreePointTwoPercent() {
        assertEquals(31, WorldDockDecoration.tickCount(1000f, 1f));
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
