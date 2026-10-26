package com.gathin.tvhome;

import androidx.dynamicanimation.animation.SpringForce;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The worlds' dock move (spec § 4.6): Star Wars' hard steps, its typing hero and the springs of the others. */
public class WorldFocusMotionTest {

    @Test
    public void stepsSnapToThirds() {
        assertEquals(0f, WorldFocusMotion.stepsInterpolate(0.2f, 3), 1e-6);
        assertEquals(1f / 3, WorldFocusMotion.stepsInterpolate(0.4f, 3), 1e-6);
        assertEquals(1f, WorldFocusMotion.stepsInterpolate(1f, 3), 1e-6);
    }

    @Test
    public void typingIs45msPerChar() {
        assertEquals(0L, WorldFocusMotion.typingDelayMs(0));
        assertEquals(315L, WorldFocusMotion.typingDelayMs(7));
    }

    @Test
    public void cleanAndStarWarsHaveNoSpringOverride() {
        assertNull(WorldFocusMotion.springFor(World.CLEAN, false));
        assertNull(WorldFocusMotion.springFor(World.STARWARS, false));
        assertNull(WorldFocusMotion.springFor(World.SEASONS, false));   // Seasons keeps Clean's spring
        assertEquals(0L, WorldFocusMotion.frameDelayMs(World.SEASONS, true));
        assertNotNull(WorldFocusMotion.springFor(World.HOGWARTS, false));
    }

    @Test
    public void hogwartsGlidesSofterThanMarvelJumps() {
        SpringForce hog = WorldFocusMotion.springFor(World.HOGWARTS, true);
        SpringForce marvel = WorldFocusMotion.springFor(World.MARVEL, true);
        assertEquals(260f, hog.getStiffness(), 1e-3);
        assertEquals(0.7f, hog.getDampingRatio(), 1e-6);
        assertEquals(520f, marvel.getStiffness(), 1e-3);
        assertEquals(1f, marvel.getDampingRatio(), 1e-6);
    }

    @Test
    public void stepsNeverOvershootAndEndOnTarget() {
        for (float t = 0f; t < 1f; t += 0.01f) {
            float v = WorldFocusMotion.stepsInterpolate(t, 3);
            assertTrue(v >= 0f && v < 1f);
        }
        assertEquals(1f, WorldFocusMotion.stepsInterpolate(1.2f, 3), 1e-6);
        assertEquals(0f, WorldFocusMotion.stepsInterpolate(-0.1f, 3), 1e-6);
    }

    @Test
    public void blipMaterialisesAndEndsAtRest() {
        assertEquals(0.2f, WorldFocusMotion.blipScaleY(0f), 1e-6);
        assertEquals(1.05f, WorldFocusMotion.blipScaleY(0.4f), 1e-6);
        assertEquals(1f, WorldFocusMotion.blipScaleY(1f), 1e-6);
        assertEquals(0.2f, WorldFocusMotion.blipAlpha(0f), 1e-6);
        assertEquals(1f, WorldFocusMotion.blipAlpha(0.4f), 1e-6);
        assertEquals(0.6f, WorldFocusMotion.blipAlpha(0.6f), 1e-6);
        assertEquals(1f, WorldFocusMotion.blipAlpha(1f), 1e-6);
    }

    @Test
    public void typingShowsWholeLabelWithinEffectLimit() {
        // A long label still ends inside the spec's 1.2 s: its step shrinks, the last char always arrives.
        assertTrue(WorldFocusMotion.typingStepMs(40) * 40 <= 1200L);
        assertTrue(WorldFocusMotion.typingStepMs(1000) >= 1L);
        assertEquals(45L, WorldFocusMotion.typingStepMs(12));
        assertTrue(WorldFocusMotion.typingCharDelayMs(39, 40) <= 1200L);
    }

    @Test
    public void typingPathUsesTypingDelay() {
        // The path type() takes: the first char at once, then typingDelayMs for every label that fits the limit.
        assertEquals(0L, WorldFocusMotion.typingCharDelayMs(0, 11));
        for (int i = 0; i < 11; i++) {
            assertEquals(WorldFocusMotion.typingDelayMs(i), WorldFocusMotion.typingCharDelayMs(i, 11));
        }
        assertEquals(0L, WorldFocusMotion.typingCharDelayMs(0, 40));
    }

    /** Supernatural's frame flickers like an unsteady bulb: three dips in at most 150 ms, then steady. */
    @Test
    public void supernaturalFlickerIsThreeDipsWithin150ms() {
        assertTrue(WorldFocusMotion.FLICKER_MS <= 150L);
        assertTrue(WorldFocusMotion.flickers(World.SUPERNATURAL));
        int dips = 0;
        float prev = WorldFocusMotion.flickerAlpha(0);
        boolean falling = false;
        float lowest = 1f;
        for (long ms = 1; ms <= WorldFocusMotion.FLICKER_MS; ms++) {
            float a = WorldFocusMotion.flickerAlpha(ms);
            assertTrue(a >= 0f && a <= 1f);
            lowest = Math.min(lowest, a);
            if (a < prev) falling = true;
            if (a > prev && falling) {
                dips++;
                falling = false;
            }
            prev = a;
        }
        assertEquals(3, dips);
        assertTrue("a flicker you see", lowest <= 0.5f);
        assertTrue("never quite out", lowest > 0f);
        // Then steady: full opacity at the end and ever after.
        assertEquals(1f, WorldFocusMotion.flickerAlpha(WorldFocusMotion.FLICKER_MS), 0f);
        assertEquals(1f, WorldFocusMotion.flickerAlpha(400), 0f);
        assertEquals(1f, WorldFocusMotion.flickerAlpha(-5), 0f);
    }

    /**
     * A held key or a fast D-pad must not strobe: a move that follows the last one within the calm gap jumps steady,
     * the frame flickers again only after a pause.
     */
    @Test
    public void supernaturalFlickerSettlesOnRepeat() {
        WorldFocusMotion.FlickerGate gate = new WorldFocusMotion.FlickerGate();
        assertTrue("the first move flickers", gate.onMove(10_000L));
        // Key repeat: a move every 50 ms for two seconds, none of them flickers.
        for (long t = 10_050L; t <= 12_000L; t += 50L) assertFalse("repeat at " + t, gate.onMove(t));
        // Taps closer than the calm gap stay steady too.
        assertFalse(gate.onMove(12_000L + WorldFocusMotion.FLICKER_CALM_MS - 1));
        // After a pause the next move flickers again, once.
        long later = 12_000L + 3 * WorldFocusMotion.FLICKER_CALM_MS;
        assertTrue(gate.onMove(later));
        assertFalse(gate.onMove(later + 80L));
        // The calm gap is longer than a whole flicker, so two flickers never run into each other.
        assertTrue(WorldFocusMotion.FLICKER_CALM_MS > WorldFocusMotion.FLICKER_MS);
        // reset (focus left the tiles, Home paused): the next move is a first move again.
        gate.reset();
        assertTrue(gate.onMove(later + 100L));
    }

    @Test
    public void onlySupernaturalFlickers() {
        for (World w : World.values()) {
            if (w != World.SUPERNATURAL) assertFalse(w.name(), WorldFocusMotion.flickers(w));
        }
        // It jumps: no spring of its own, and no wait.
        assertNull(WorldFocusMotion.springFor(World.SUPERNATURAL, false));
        assertEquals(0L, WorldFocusMotion.frameDelayMs(World.SUPERNATURAL, true));
    }

    @Test
    public void onlyMarvelWaitsForItsBracketsOnTheDock() {
        assertEquals(60L, WorldFocusMotion.frameDelayMs(World.MARVEL, true));
        assertEquals(0L, WorldFocusMotion.frameDelayMs(World.MARVEL, false));
        assertEquals(0L, WorldFocusMotion.frameDelayMs(World.HOGWARTS, true));
        assertEquals(0L, WorldFocusMotion.frameDelayMs(World.CLEAN, true));
    }
}
