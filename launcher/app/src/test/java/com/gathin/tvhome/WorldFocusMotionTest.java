package com.gathin.tvhome;

import androidx.dynamicanimation.animation.SpringForce;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void onlyMarvelWaitsForItsBracketsOnTheDock() {
        assertEquals(60L, WorldFocusMotion.frameDelayMs(World.MARVEL, true));
        assertEquals(0L, WorldFocusMotion.frameDelayMs(World.MARVEL, false));
        assertEquals(0L, WorldFocusMotion.frameDelayMs(World.HOGWARTS, true));
        assertEquals(0L, WorldFocusMotion.frameDelayMs(World.CLEAN, true));
    }
}
