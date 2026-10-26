package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for when a world's overlay may move: never in Clean, never with reduced motion or behind anything. */
public class OverlayPolicyTest {
    /** Seasons' overlay takes the world path (it moves over its scene); until its looks arrive it has none. */
    @Test public void seasonsRunsLikeAWorld() { assertTrue(WorldOverlay.shouldRun(World.SEASONS, true, false, true, false)); assertFalse(WorldOverlay.shouldRun(World.SEASONS, true, true, true, false)); }
    @Test public void cleanNeverRuns() { assertFalse(WorldOverlay.shouldRun(World.CLEAN, true, false, true, false)); }
    @Test public void reducedMotionStops() { assertFalse(WorldOverlay.shouldRun(World.HOGWARTS, true, true, true, false)); }
    @Test public void animationsOffStops() { assertFalse(WorldOverlay.shouldRun(World.MARVEL, false, false, true, false)); }
    @Test public void pausedOrScreensaverStops() { assertFalse(WorldOverlay.shouldRun(World.STARWARS, true, false, false, false)); assertFalse(WorldOverlay.shouldRun(World.STARWARS, true, false, true, true)); }
    @Test public void normalCaseRuns() { assertTrue(WorldOverlay.shouldRun(World.STARWARS, true, false, true, false)); }
}
