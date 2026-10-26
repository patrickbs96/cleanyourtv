package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The worlds' app start (spec § 4.7): who plays it, how long, and that the app starts exactly once. */
public class LaunchGuardTest {

    @Test
    public void cleanAndReducedMotionStartImmediately() {
        assertFalse(WorldLaunchMotion.shouldPlay(World.CLEAN, true, false));
        assertFalse(WorldLaunchMotion.shouldPlay(World.MARVEL, true, true));
        assertFalse(WorldLaunchMotion.shouldPlay(World.MARVEL, false, false));
        assertTrue(WorldLaunchMotion.shouldPlay(World.MARVEL, true, false));
    }

    /** Seasons starts apps as Clean does (Jahreskreis spec § 4.8): no effect, no length. */
    @Test
    public void seasonsStartsLikeClean() {
        assertFalse(WorldLaunchMotion.shouldPlay(World.SEASONS, true, false));
        assertEquals(0L, WorldLaunchMotion.durationMs(World.SEASONS));
    }

    @Test
    public void durationsStayUnderLimit() {
        // Every effect at most 1.2 s (ruling R12 revised): the whites are cut to 200 ms.
        for (World w : World.values()) assertTrue(WorldLaunchMotion.durationMs(w) <= 1200);
        assertEquals(0, WorldLaunchMotion.durationMs(World.CLEAN));
        assertEquals(1200, WorldLaunchMotion.durationMs(World.HOGWARTS));
        assertEquals(1200, WorldLaunchMotion.durationMs(World.MARVEL));
        assertEquals(1200, WorldLaunchMotion.durationMs(World.STARWARS));
        assertEquals(1200, WorldLaunchMotion.durationMs(World.SUPERNATURAL));
        assertEquals(1200, WorldLaunchMotion.durationMs(World.MIDDLE_EARTH));
    }

    /**
     * Middle-earth (Mittelerde spec § 4.3): a lettering of fire runs once round the tile's frame, then the white
     * (200 ms from 1.0 s) and the app at 1.2 s; the tile stays as it is. Reduced motion and animations off start the
     * app at once, as everywhere.
     */
    @Test
    public void middleEarthFireLetteringRunsOnceRoundTheTile() {
        World w = World.MIDDLE_EARTH;
        assertTrue(WorldLaunchMotion.shouldPlay(w, true, false));
        assertFalse(WorldLaunchMotion.shouldPlay(w, true, true));
        assertFalse(WorldLaunchMotion.shouldPlay(w, false, false));
        // The fire's head goes round once, steadily, and is all the way round before the white.
        assertEquals(0f, WorldLaunchMotion.fireHead(0), 1e-6);
        assertEquals(1f, WorldLaunchMotion.fireHead(WorldLaunchMotion.FIRE_LAP_MS), 1e-6);
        assertTrue(WorldLaunchMotion.FIRE_LAP_MS < 1000);
        float last = 0f;
        for (long t = 0; t <= 1200; t += 33) {
            float h = WorldLaunchMotion.fireHead(t);
            assertTrue("head runs back at " + t, h >= last - 1e-6f);
            last = h;
        }
        assertEquals(0.5f, WorldLaunchMotion.fireHead(WorldLaunchMotion.FIRE_LAP_MS / 2), 1e-3);
        // A sign not yet reached is dark; where the head is it burns full, behind it glows on as an ember.
        long mid = WorldLaunchMotion.FIRE_LAP_MS / 2;
        assertEquals(0f, WorldLaunchMotion.glyphHeat(0.6f, mid), 1e-6);
        assertEquals(1f, WorldLaunchMotion.glyphHeat(0.5f, mid), 1e-3);
        assertEquals(WorldLaunchMotion.EMBER, WorldLaunchMotion.glyphHeat(0.05f, mid), 1e-3);
        float near = WorldLaunchMotion.glyphHeat(0.45f, mid);
        assertTrue(near > WorldLaunchMotion.EMBER && near < 1f);
        // Once round, the whole ring flares up to full by the white.
        assertEquals(1f, WorldLaunchMotion.glyphHeat(0.05f, 1000), 1e-6);
        assertEquals(1f, WorldLaunchMotion.glyphHeat(0.99f, 1000), 1e-6);
        for (float pos = 0.01f; pos < 1f; pos += 0.07f) {
            assertTrue(WorldLaunchMotion.glyphHeat(pos, 1200) <= 1f);
            assertTrue(WorldLaunchMotion.glyphHeat(pos, 0) >= 0f);
        }
        // The white as the others', and nothing else of the screen changes.
        assertEquals(0f, WorldLaunchMotion.blendAlpha(w, 999), 1e-6);
        assertTrue(WorldLaunchMotion.blendAlpha(w, 1100) < 1f);
        assertEquals(1f, WorldLaunchMotion.blendAlpha(w, 1200), 1e-6);
        assertEquals(0xFFFFFFFF, WorldLaunchMotion.blendColor(w));
        assertEquals(1.07f, WorldLaunchMotion.tileScaleX(w, 600, 1.07f), 1e-6);
        assertEquals(1.07f, WorldLaunchMotion.tileScaleY(w, 600, 1.07f), 1e-6);
        assertEquals(1f, WorldLaunchMotion.tileAlpha(w, 600), 1e-6);
        assertEquals(1f, WorldLaunchMotion.sceneZoom(w, 600), 1e-6);
    }

    /** The fire's colour: deep red as an ember, through orange to a pale yellow-white where it burns hottest. */
    @Test
    public void fireColourGoesFromEmberToWhiteHot() {
        assertEquals(WorldLaunchMotion.FIRE_EMBER, WorldLaunchMotion.fireColor(0f));
        assertEquals(WorldLaunchMotion.FIRE_FLAME, WorldLaunchMotion.fireColor(0.6f));
        assertEquals(WorldLaunchMotion.FIRE_HOT, WorldLaunchMotion.fireColor(1f));
        assertEquals(WorldLaunchMotion.FIRE_HOT, WorldLaunchMotion.fireColor(2f));
        int c = WorldLaunchMotion.fireColor(0.8f);
        assertEquals(0xFF, c >>> 24);
        // Red stays high, blue grows with the heat.
        assertTrue((c & 0xFF) > (WorldLaunchMotion.FIRE_FLAME & 0xFF));
        assertTrue((c & 0xFF) < (WorldLaunchMotion.FIRE_HOT & 0xFF));
    }

    /**
     * The track the signs run on: once round a w × h frame clockwise from its top left, the signs upright along each
     * side (0° on top, 90° down the right, 180° along the bottom, 270° up the left).
     */
    @Test
    public void fireTrackGoesClockwiseRoundTheFrame() {
        float[] p = new float[3];
        float w = 300f, h = 100f, round = 2 * (w + h);
        WorldLaunchMotion.trackAt(w, h, 0f, p);
        assertArrayEquals(new float[]{0f, 0f, 0f}, p, 1e-4f);
        WorldLaunchMotion.trackAt(w, h, 150f / round, p);
        assertArrayEquals(new float[]{150f, 0f, 0f}, p, 1e-3f);
        WorldLaunchMotion.trackAt(w, h, 350f / round, p);
        assertArrayEquals(new float[]{300f, 50f, 90f}, p, 1e-3f);
        WorldLaunchMotion.trackAt(w, h, 550f / round, p);
        assertArrayEquals(new float[]{150f, 100f, 180f}, p, 1e-3f);
        WorldLaunchMotion.trackAt(w, h, 750f / round, p);
        assertArrayEquals(new float[]{0f, 50f, 270f}, p, 1e-3f);
        WorldLaunchMotion.trackAt(w, h, 1f, p);
        assertArrayEquals(new float[]{0f, 0f, 270f}, p, 1e-3f);
    }

    /**
     * The lettering is made up (spec: no real script, no inscription): a fixed row of its own few signs, as many as fit
     * round the frame, never the same sign twice in a row.
     */
    @Test
    public void fireLetteringIsAFixedRowOfOwnSigns() {
        assertEquals(WorldLaunchMotion.MIN_GLYPHS, WorldLaunchMotion.glyphCount(10f, 20f));
        assertEquals(WorldLaunchMotion.MAX_GLYPHS, WorldLaunchMotion.glyphCount(1e6f, 20f));
        assertEquals(40, WorldLaunchMotion.glyphCount(800f, 20f));
        for (int i = 0; i < WorldLaunchMotion.MAX_GLYPHS; i++) {
            int g = WorldLaunchMotion.glyphAt(i);
            assertTrue(g >= 0 && g < WorldLaunchMotion.GLYPHS);
            if (i > 0) assertTrue("same sign twice at " + i, g != WorldLaunchMotion.glyphAt(i - 1));
        }
        // Every sign shows up in a row of the smallest length.
        boolean[] seen = new boolean[WorldLaunchMotion.GLYPHS];
        for (int i = 0; i < WorldLaunchMotion.MIN_GLYPHS; i++) seen[WorldLaunchMotion.glyphAt(i)] = true;
        for (int g = 0; g < seen.length; g++) assertTrue("sign " + g, seen[g]);
    }

    /** Back while the fire runs: the app does not start, nothing of the effect stays, the next OK plays anew. */
    @Test
    public void backCancelsTheFireLettering() {
        WorldLaunchMotion m = new WorldLaunchMotion();
        int[] starts = {0};
        assertTrue(m.begin(World.MIDDLE_EARTH, () -> starts[0]++));
        m.cancel();
        m.finish();
        assertEquals(0, starts[0]);
        assertFalse(m.isBusy());
        assertTrue(m.begin(World.MIDDLE_EARTH, () -> starts[0]++));
        m.finish();
        assertEquals(1, starts[0]);
    }

    /**
     * Supernatural (spec § 4.3): the car's headlights flare up, the lamp first, then its two cones grow until they
     * fill the screen when the white comes, which is cut as the others' at 1.0 s over 200 ms. The tile stays as it is.
     */
    @Test
    public void supernaturalHeadlightsFlareUpAndFillTheScreen() {
        World w = World.SUPERNATURAL;
        assertTrue(WorldLaunchMotion.shouldPlay(w, true, false));
        assertFalse(WorldLaunchMotion.shouldPlay(w, true, true));
        assertFalse(WorldLaunchMotion.shouldPlay(w, false, false));
        assertEquals(0f, WorldLaunchMotion.lampGlow(0), 1e-6);
        assertEquals(1f, WorldLaunchMotion.lampGlow(WorldLaunchMotion.LAMP_MS), 1e-6);
        assertEquals(0f, WorldLaunchMotion.beamReach(WorldLaunchMotion.BEAM_FROM_MS), 1e-6);
        assertEquals(1f, WorldLaunchMotion.beamReach(1000), 1e-6);
        float last = 0f;
        for (long t = 0; t <= 1200; t += 33) {
            float r = WorldLaunchMotion.beamReach(t);
            assertTrue("reach falls at " + t, r >= last - 1e-5f);
            last = r;
        }
        // The cones are full before the white starts, and the white only comes at 1.0 s.
        assertEquals(0f, WorldLaunchMotion.blendAlpha(w, 999), 1e-6);
        assertTrue(WorldLaunchMotion.blendAlpha(w, 1100) < 1f);
        assertEquals(1f, WorldLaunchMotion.blendAlpha(w, 1200), 1e-6);
        assertEquals(0xFFFFFFFF, WorldLaunchMotion.blendColor(w));
        assertEquals(1.07f, WorldLaunchMotion.tileScaleX(w, 600, 1.07f), 1e-6);
        assertEquals(1.07f, WorldLaunchMotion.tileScaleY(w, 600, 1.07f), 1e-6);
        assertEquals(1f, WorldLaunchMotion.tileAlpha(w, 600), 1e-6);
        assertEquals(1f, WorldLaunchMotion.sceneZoom(w, 600), 1e-6);
    }

    /** Back while the headlights flare: the app does not start, nothing of the effect stays, the next OK plays anew. */
    @Test
    public void backCancelsTheHeadlights() {
        WorldLaunchMotion m = new WorldLaunchMotion();
        int[] starts = {0};
        assertTrue(m.begin(World.SUPERNATURAL, () -> starts[0]++));
        m.cancel();
        m.finish();
        assertEquals(0, starts[0]);
        assertFalse(m.isBusy());
        assertTrue(m.begin(World.SUPERNATURAL, () -> starts[0]++));
        m.finish();
        assertEquals(1, starts[0]);
    }

    @Test
    public void secondPressWhilePlayingIsSwallowed() {
        WorldLaunchMotion m = new WorldLaunchMotion();
        int[] starts = {0};
        assertTrue(m.begin(World.HOGWARTS, () -> starts[0]++));   // begin(): nur der Guard, ohne Views
        assertFalse(m.begin(World.HOGWARTS, () -> starts[0]++));
        m.finish();
        assertEquals(1, starts[0]);
    }

    @Test
    public void finishRunsStartOnlyOnce() {
        // The effect's last frame and the timeout both finish: the app still starts once.
        WorldLaunchMotion m = new WorldLaunchMotion();
        int[] starts = {0};
        assertTrue(m.begin(World.MARVEL, () -> starts[0]++));
        m.finish();
        m.finish();
        assertEquals(1, starts[0]);
        assertFalse(m.isPlaying());
    }

    @Test
    public void cancelDropsTheStart() {
        // Home pressed mid-effect (onPause): the app is not started, and the next press plays anew.
        WorldLaunchMotion m = new WorldLaunchMotion();
        int[] starts = {0};
        assertTrue(m.begin(World.STARWARS, () -> starts[0]++));
        m.cancel();
        m.finish();
        assertEquals(0, starts[0]);
        assertTrue(m.begin(World.STARWARS, () -> starts[0]++));
        m.finish();
        assertEquals(1, starts[0]);
    }

    @Test
    public void startThrowingStillFreesTheGuard() {
        WorldLaunchMotion m = new WorldLaunchMotion();
        assertTrue(m.begin(World.HOGWARTS, () -> { throw new IllegalStateException("no app"); }));
        try {
            m.finish();
        } catch (IllegalStateException expected) {
            // the caller's problem; the guard must not stay shut
        }
        assertFalse(m.isPlaying());
    }

    @Test
    public void blendFollowsTheSpecTimeline() {
        // Hogwarts: black only from 60 % of 1.2 s, full at the start.
        assertEquals(0f, WorldLaunchMotion.blendAlpha(World.HOGWARTS, 700), 1e-6);
        assertEquals(1f, WorldLaunchMotion.blendAlpha(World.HOGWARTS, 1200), 1e-6);
        // Marvel and Star Wars: white from 1.0 s over 200 ms.
        assertEquals(0f, WorldLaunchMotion.blendAlpha(World.MARVEL, 999), 1e-6);
        assertTrue(WorldLaunchMotion.blendAlpha(World.MARVEL, 1100) < 1f);
        assertEquals(1f, WorldLaunchMotion.blendAlpha(World.MARVEL, 1200), 1e-6);
        assertEquals(0f, WorldLaunchMotion.blendAlpha(World.STARWARS, 999), 1e-6);
        assertTrue(WorldLaunchMotion.blendAlpha(World.STARWARS, 1100) < 1f);
        assertEquals(1f, WorldLaunchMotion.blendAlpha(World.STARWARS, 1200), 1e-6);
        assertEquals(0xFF000000, WorldLaunchMotion.blendColor(World.HOGWARTS));
        assertEquals(0xFFFFFFFF, WorldLaunchMotion.blendColor(World.MARVEL));
        // Every themed world has an effect, and it is full at its own end: the app never comes up over a half-faded
        // Home. Clean and Seasons have none (shouldPlay skips them).
        for (World w : World.values()) {
            if (!w.hasOwnMotion()) continue;
            assertTrue(w.name(), WorldLaunchMotion.durationMs(w) > 0);
            assertEquals(w.name(), 1f, WorldLaunchMotion.blendAlpha(w, WorldLaunchMotion.durationMs(w)), 1e-6);
        }
    }

    @Test
    public void tileGrowsAsTheSpecSays() {
        assertEquals(1.07f, WorldLaunchMotion.tileScaleX(World.HOGWARTS, 0, 1.07f), 1e-6);
        assertEquals(1.25f, WorldLaunchMotion.tileScaleX(World.HOGWARTS, 440, 1.07f), 1e-4);
        assertEquals(1.35f, WorldLaunchMotion.tileScaleX(World.MARVEL, 900, 1.07f), 1e-4);
        assertEquals(1.07f, WorldLaunchMotion.tileScaleX(World.STARWARS, 99, 1.07f), 1e-6);
        assertEquals(6f, WorldLaunchMotion.tileScaleX(World.STARWARS, 1100, 1.07f), 1e-4);
        assertEquals(1.6f, WorldLaunchMotion.tileScaleY(World.STARWARS, 1100, 1.07f), 1e-4);
        assertEquals(0f, WorldLaunchMotion.tileAlpha(World.STARWARS, 1100), 1e-4);
        assertEquals(1f, WorldLaunchMotion.tileAlpha(World.MARVEL, 1100), 1e-6);
        assertEquals(1.4f, WorldLaunchMotion.sceneZoom(World.STARWARS, 1100), 1e-4);
        assertEquals(1f, WorldLaunchMotion.sceneZoom(World.HOGWARTS, 1100), 1e-6);
    }

    @Test
    public void bezierMatchesCssEndpointsAndIsMonotonic() {
        float last = 0f;
        for (int i = 0; i <= 20; i++) {
            float v = WorldLaunchMotion.bezier(0.2f, 0.8f, 0.2f, 1f, i / 20f);
            assertTrue(v >= last - 1e-4f);
            last = v;
        }
        assertEquals(0f, WorldLaunchMotion.ease(0f), 1e-6);
        assertEquals(1f, WorldLaunchMotion.ease(1f), 1e-6);
        // CSS ease at half time is about 0.80.
        assertEquals(0.8024f, WorldLaunchMotion.ease(0.5f), 2e-3);
    }

    @Test
    public void onlyVolumePassesWhilePlaying() {
        assertTrue(WorldLaunchMotion.passesThrough(android.view.KeyEvent.KEYCODE_VOLUME_UP));
        assertTrue(WorldLaunchMotion.passesThrough(android.view.KeyEvent.KEYCODE_VOLUME_MUTE));
        assertFalse(WorldLaunchMotion.passesThrough(android.view.KeyEvent.KEYCODE_DPAD_CENTER));
        assertFalse(WorldLaunchMotion.passesThrough(android.view.KeyEvent.KEYCODE_BACK));
    }

    @Test
    public void backCancelsTheEffect() {
        // Ruling R13: Back while the effect plays drops it, as leaving Home does; OK and the D-pad stay swallowed.
        assertTrue(WorldLaunchMotion.cancelsEffect(android.view.KeyEvent.KEYCODE_BACK));
        assertFalse(WorldLaunchMotion.cancelsEffect(android.view.KeyEvent.KEYCODE_DPAD_CENTER));
        assertFalse(WorldLaunchMotion.cancelsEffect(android.view.KeyEvent.KEYCODE_VOLUME_UP));
        WorldLaunchMotion m = new WorldLaunchMotion();
        int[] starts = {0};
        assertTrue(m.begin(World.MARVEL, () -> starts[0]++));
        m.cancel();   // what MainActivity does on Back's release
        m.finish();   // the timeout, had it not been removed
        assertEquals(0, starts[0]);
        assertFalse(m.isBusy());
    }

    @Test
    public void timeoutComesAfterTheEffect() {
        for (World w : World.values()) {
            if (w.isClean()) continue;
            assertEquals(WorldLaunchMotion.durationMs(w) + 200, WorldLaunchMotion.timeoutMs(w));
        }
    }
}
