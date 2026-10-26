package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldTransitionTest {
    @Test public void totalStaysUnder900ms() { for (World w : World.values()) assertTrue(WorldTransition.leaveMs(w) + WorldTransition.enterMs(w) <= 900); }

    @Test public void pendingIsConsumedOnlyByTheTargetWorld() {
        FakePrefs p = new FakePrefs(); p.put(WorldTransition.PENDING_KEY, "marvel");
        assertFalse(WorldTransition.consumePending(p, World.HOGWARTS));
        assertTrue(WorldTransition.consumePending(p, World.MARVEL));
        assertFalse(WorldTransition.consumePending(p, World.MARVEL));
    }

    @Test public void halvesAsSpecified() {
        assertEquals(250, WorldTransition.leaveMs(World.CLEAN)); assertEquals(250, WorldTransition.enterMs(World.CLEAN));
        assertEquals(300, WorldTransition.leaveMs(World.HOGWARTS)); assertEquals(600, WorldTransition.enterMs(World.HOGWARTS));
        assertEquals(250, WorldTransition.leaveMs(World.MARVEL)); assertEquals(500, WorldTransition.enterMs(World.MARVEL));
        assertEquals(400, WorldTransition.leaveMs(World.STARWARS)); assertEquals(300, WorldTransition.enterMs(World.STARWARS));
        assertEquals(300, WorldTransition.leaveMs(World.SUPERNATURAL)); assertEquals(500, WorldTransition.enterMs(World.SUPERNATURAL));
    }

    /**
     * Supernatural (spec § 4.3, § 4.5): at night one flash with the wings' shadow, by day the sun's rays flaring up,
     * both in the new Home's half after the old scene has darkened; nothing left of either when the scene stands.
     */
    @Test public void supernaturalFlashesAtNightAndFlaresItsRaysByDay() {
        World w = World.SUPERNATURAL;
        long cut = WorldTransition.leaveMs(w), end = cut + WorldTransition.enterMs(w);
        assertTrue(end <= 900);
        float flashMax = 0f, raysMax = 0f;
        for (long t = 0; t <= end; t += 5) {
            float f = WorldTransition.flashFor(w, t), r = WorldTransition.raysFor(w, t);
            if (t <= cut) {
                assertEquals("flash before the cut at " + t, 0f, f, 0f);
                assertEquals("rays before the cut at " + t, 0f, r, 0f);
            }
            assertTrue(f >= 0f && f <= 1f);
            assertTrue(r >= 0f && r <= WorldTransition.SN_RAYS_PEAK);
            flashMax = Math.max(flashMax, f);
            raysMax = Math.max(raysMax, r);
        }
        assertTrue(flashMax > 0.9f);
        assertEquals(WorldTransition.SN_RAYS_PEAK, raysMax, 0.02f);
        assertEquals(0f, WorldTransition.flashFor(w, end), 0f);
        assertEquals(0f, WorldTransition.raysFor(w, end), 0f);
        // Never the screen white (spec § 2): the flash lights only the sky, at most 40 %, the rays at most half.
        assertTrue(SupernaturalOverlay.SKY_FLASH * flashMax <= 0.40f + 1e-6f);
        assertTrue(WorldTransition.SN_RAYS_PEAK <= 0.5f);
        // A themed world's dim cover in its own night colour, no hard black; no scan edge, no zoom.
        assertEquals(World.SUPERNATURAL.backgroundColor, WorldTransition.coverColor(w));
        assertEquals(WorldTransition.SUPERNATURAL_PEAK, WorldTransition.coverAlpha(w, cut), 1e-4f);
        assertEquals(0f, WorldTransition.revealLine(w, cut + 100), 0f);
        assertEquals(1f, WorldTransition.sceneZoom(w, cut + 100, true), 0f);
        // The other worlds draw neither.
        for (World o : World.values()) {
            if (o == w) continue;
            assertEquals(0f, WorldTransition.flashFor(o, cut + 100), 0f);
            assertEquals(0f, WorldTransition.raysFor(o, cut + 100), 0f);
        }
        assertTrue(WorldTransition.flashFor(w, cut + 100) > 0f);
        assertTrue(WorldTransition.raysFor(w, cut + 100) > 0f);
    }

    @Test public void seasonsTransitionStaysShort() { assertTrue(WorldTransition.leaveMs(World.SEASONS) + WorldTransition.enterMs(World.SEASONS) <= 900); }

    /** Jahreskreis: 250 ms down to black, 350 ms back up, a plain cross-fade (spec § 4.8, § 6). */
    @Test public void seasonsFadesThroughBlack() {
        assertEquals(250, WorldTransition.leaveMs(World.SEASONS));
        assertEquals(350, WorldTransition.enterMs(World.SEASONS));
        assertEquals(1f, WorldTransition.coverAlpha(World.SEASONS, 250), 1e-4f);
        assertEquals(0xFF000000, WorldTransition.coverColor(World.SEASONS));
        assertEquals(0xFF000000, WorldTransition.coverColor(World.CLEAN));
        assertEquals(World.HOGWARTS.backgroundColor, WorldTransition.coverColor(World.HOGWARTS));
        assertEquals(0f, WorldTransition.revealLine(World.SEASONS, 400), 1e-6f);
        assertEquals(1f, WorldTransition.sceneZoom(World.SEASONS, 300, true), 1e-6f);
    }

    @Test public void hardCutWithReducedMotionOrAnimationsOff() {
        assertTrue(WorldTransition.shouldPlay(true, false));
        assertFalse(WorldTransition.shouldPlay(true, true));
        assertFalse(WorldTransition.shouldPlay(false, false));
    }

    /** The cover the leave half ends on is the one the enter half starts from: no jump across recreate(). */
    @Test public void coverIsContinuousAcrossTheRestart() {
        for (World w : World.values()) {
            long cut = WorldTransition.leaveMs(w);
            assertEquals(0f, WorldTransition.coverAlpha(w, 0), 1e-4f);
            assertEquals(WorldTransition.coverAlpha(w, cut - 1), WorldTransition.coverAlpha(w, cut), 0.02f);
            assertEquals(0f, WorldTransition.coverAlpha(w, cut + WorldTransition.enterMs(w)), 1e-4f);
        }
    }

    /** Only Clean (and Seasons, seasonsFadesThroughBlack) goes all the way to black (spec § 4.8); the themed worlds dim. */
    @Test public void onlyCleanGoesFullyBlack() {
        assertEquals(1f, WorldTransition.coverAlpha(World.CLEAN, WorldTransition.leaveMs(World.CLEAN)), 1e-4f);
        for (World w : new World[]{World.HOGWARTS, World.MARVEL, World.STARWARS, World.SUPERNATURAL}) {
            for (long t = 0; t <= WorldTransition.leaveMs(w) + WorldTransition.enterMs(w); t += 10) {
                assertTrue(w + " at " + t, WorldTransition.coverAlpha(w, t) < 0.95f);
            }
        }
    }

    /** Hogwarts' candles light one by one, 60 ms apart, from the middle of the cluster outwards. */
    @Test public void candlesLightFromTheMiddle() {
        float[] xs = {0f, 10f, 4f, 6f}, ys = {0f, 0f, 0f, 0f};
        int[] rank = WorldTransition.igniteRanks(xs, ys);
        // The middle is x = 5: 4 and 6 first, 0 and 10 last; equally far ones in their order.
        assertEquals(0, rank[2]); assertEquals(1, rank[3]); assertEquals(2, rank[0]); assertEquals(3, rank[1]);
        assertEquals(0, WorldTransition.igniteAtMs(0)); assertEquals(540, WorldTransition.igniteAtMs(9));
        assertTrue(WorldTransition.igniteAtMs(9) < WorldTransition.enterMs(World.HOGWARTS));
    }

    /** Star Wars: the old Home ends on 1.25, the new one starts on 1.12 at the cut, not one frame on 1.25. */
    @Test public void starWarsZoomBelongsToTheHalfThatDrawsTheCut() {
        long cut = WorldTransition.leaveMs(World.STARWARS), end = cut + WorldTransition.enterMs(World.STARWARS);
        assertEquals(1f, WorldTransition.sceneZoom(World.STARWARS, 0, false), 1e-4f);
        assertEquals(1.25f, WorldTransition.sceneZoom(World.STARWARS, cut, false), 1e-4f);
        assertEquals(1.12f, WorldTransition.sceneZoom(World.STARWARS, cut, true), 1e-4f);
        // The enter half's first tick is close to where it started: no jump.
        assertEquals(1.12f, WorldTransition.sceneZoom(World.STARWARS, cut + WorldTransition.FRAME_MS, true), 0.03f);
        assertEquals(1f, WorldTransition.sceneZoom(World.STARWARS, end, true), 1e-4f);
        assertEquals(1f, WorldTransition.sceneZoom(World.HOGWARTS, cut, true), 1e-6f);
    }

    /** An enter half whose overlay never draws ends anyway, 300 ms after its length, so colour keys work again. */
    @Test public void theEnterHalfTimesOut() {
        for (World w : World.values()) {
            assertEquals(WorldTransition.enterMs(w) + 300, WorldTransition.enterTimeoutMs(w));
            assertTrue(WorldTransition.enterTimeoutMs(w) > WorldTransition.enterMs(w));
        }
    }

    /** Marvel's HUD boots top to bottom in the enter half: the reveal line runs from the top edge to the bottom. */
    @Test public void marvelRevealsTopToBottom() {
        long cut = WorldTransition.leaveMs(World.MARVEL), end = cut + WorldTransition.enterMs(World.MARVEL);
        assertEquals(0f, WorldTransition.revealLine(World.MARVEL, cut), 1e-4f);
        assertEquals(1f, WorldTransition.revealLine(World.MARVEL, end), 1e-4f);
        assertTrue(WorldTransition.revealLine(World.MARVEL, cut + 100) < WorldTransition.revealLine(World.MARVEL, cut + 300));
    }
}
