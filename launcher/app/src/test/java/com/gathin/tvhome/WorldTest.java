package com.gathin.tvhome;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WorldTest {
    @Test public void unknownValueIsClean() { assertSame(World.CLEAN, World.parse("")); assertSame(World.CLEAN, World.parse("narnia")); assertSame(World.CLEAN, World.parse(null)); }
    @Test public void idsRoundTrip() { for (World w : World.values()) assertSame(w, World.parse(w.id)); }
    @Test public void colourKeysMapAsSpecified() {
        assertSame(World.CLEAN, World.forKeyCode(KeyEvent.KEYCODE_PROG_GREEN));
        assertSame(World.HOGWARTS, World.forKeyCode(KeyEvent.KEYCODE_PROG_YELLOW));
        assertSame(World.MARVEL, World.forKeyCode(KeyEvent.KEYCODE_PROG_RED));
        assertSame(World.STARWARS, World.forKeyCode(KeyEvent.KEYCODE_PROG_BLUE));
        assertNull(World.forKeyCode(KeyEvent.KEYCODE_DPAD_CENTER));
    }
    @Test public void digitsFollowTheEnumWithSeasonsOnOne() {
        assertSame(World.CLEAN, World.forKeyCode(KeyEvent.KEYCODE_0));
        assertSame(World.SEASONS, World.forKeyCode(KeyEvent.KEYCODE_1));
        assertSame(World.HOGWARTS, World.forKeyCode(KeyEvent.KEYCODE_2));
        assertSame(World.MARVEL, World.forKeyCode(KeyEvent.KEYCODE_3));
        assertSame(World.STARWARS, World.forKeyCode(KeyEvent.KEYCODE_4));
        assertSame(World.SUPERNATURAL, World.forKeyCode(KeyEvent.KEYCODE_5));
        assertNull(World.forKeyCode(KeyEvent.KEYCODE_6));   // free for the next world
        assertNull(World.forKeyCode(KeyEvent.KEYCODE_9));
    }
    @Test public void supernaturalHasNoColourKey() {
        for (int k = KeyEvent.KEYCODE_PROG_RED; k <= KeyEvent.KEYCODE_PROG_BLUE; k++) assertNotSame(World.SUPERNATURAL, World.forKeyCode(k));
        for (int scan = 398; scan <= 401; scan++) assertNotSame(World.SUPERNATURAL, World.forScanCode(scan));
    }
    @Test public void supernaturalValuesAsSpecified() {
        assertSame(World.SUPERNATURAL, World.parse("supernatural"));
        assertEquals(R.string.world_supernatural, World.SUPERNATURAL.labelRes);
        assertEquals(0xFFE0A24A, World.SUPERNATURAL.keyColor);
        assertEquals(0xFFE0A24A, World.SUPERNATURAL.accent);
        assertEquals(0xFFE0A24A, World.SUPERNATURAL.ambientColor);
        assertEquals(30, World.SUPERNATURAL.ambientAlphaPercent);
        assertEquals(0xFF0B0D10, World.SUPERNATURAL.backgroundColor);
        assertEquals(0, World.SUPERNATURAL.heroPrefixRes);
        assertTrue(World.SUPERNATURAL.forcesIconOnlyPills());
    }
    /** Supernatural's words come after the day's greeting, the same in every language: on the road by day, a hunt by night. */
    @Test public void supernaturalGreetsAfterTheGreeting() {
        assertEquals("", World.SUPERNATURAL.greetingPrefix(true));
        assertEquals("", World.SUPERNATURAL.greetingPrefix(false));
        assertEquals("Back on the road", World.SUPERNATURAL.greetingSuffix(true));
        assertEquals("Something's out there", World.SUPERNATURAL.greetingSuffix(false));
        // The others keep theirs, day or night.
        assertEquals("Äußerer Rand", World.STARWARS.greetingSuffix(true));
        assertEquals("Äußerer Rand", World.STARWARS.greetingSuffix(false));
        assertEquals("", World.HOGWARTS.greetingSuffix(false));
        assertEquals("", World.CLEAN.greetingSuffix(true));
    }
    /**
     * Until its transition arrives (Task 8) Supernatural takes the neutral paths everywhere a world branches: its own
     * pictures (Task 3), its own overlay (Task 4), its own pills, dock and frame (Task 5), its flicker (Task 6: it
     * jumps, so no spring and no wait of its own) and its headlights at an app start (Task 7, the tile itself stays),
     * but a plain fade in; never another world's skin.
     */
    @Test public void supernaturalFallsBackToNeutralPaths() {
        World w = World.SUPERNATURAL;
        assertEquals(R.drawable.world_supernatural_day, WorldScene.resFor(w, true));
        assertEquals(R.drawable.world_supernatural_night, WorldScene.resFor(w, false));
        assertTrue(WorldOverlay.hasOverlay(w, false));
        assertFalse(WorldChrome.ownsNetworkIcon(w));
        assertNull(WorldFocusMotion.springFor(w, false));
        assertEquals(0L, WorldFocusMotion.frameDelayMs(w, true));
        assertEquals(1200L, WorldLaunchMotion.durationMs(w));
        assertTrue(WorldLaunchMotion.shouldPlay(w, true, false));
        assertEquals(1f, WorldLaunchMotion.sceneZoom(w, 500), 0f);
        assertEquals(1f, WorldLaunchMotion.tileAlpha(w, 500), 0f);
        assertEquals(WorldTransition.peak(World.CLEAN), WorldTransition.peak(w), 0f);
        assertEquals(0f, WorldTransition.revealLine(w, 400), 0f);
        assertEquals(1f, WorldTransition.sceneZoom(w, 400, true), 0f);
        assertEquals(0xFF0B0D10, WorldTransition.coverColor(w));
        assertEquals("Netflix", WorldTypography.heroLabel(w, "Netflix"));
    }
    /** Supernatural's own chrome (spec § 4.3): brass pills with the star in a circle on Settings, a warm amber frame. */
    @Test public void supernaturalHasBrassPillsAndAWarmFrame() {
        World w = World.SUPERNATURAL;
        assertEquals(R.drawable.pill_bg_supernatural, WorldChrome.pillBackground(w));
        assertEquals(R.drawable.ic_world_sn_settings, WorldChrome.settingsIcon(w));
        assertTrue(WorldChrome.warmFocusGlow(w));
        // The others keep theirs: Hogwarts its star, Marvel and Star Wars the plain gear, none of them the warm glow.
        assertEquals(R.drawable.ic_world_hog_star, WorldChrome.settingsIcon(World.HOGWARTS));
        assertEquals(0, WorldChrome.settingsIcon(World.MARVEL));
        assertEquals(0, WorldChrome.settingsIcon(World.STARWARS));
        assertEquals(0, WorldChrome.settingsIcon(World.CLEAN));
        for (World other : World.values()) {
            if (other != w) assertFalse(other.name(), WorldChrome.warmFocusGlow(other));
        }
        assertEquals(R.drawable.pill_bg, WorldChrome.pillBackground(World.CLEAN));
        assertEquals(R.drawable.pill_bg, WorldChrome.pillBackground(World.SEASONS));
    }
    @Test public void seasonsHasNoColourKey() {
        for (int k = KeyEvent.KEYCODE_PROG_RED; k <= KeyEvent.KEYCODE_PROG_BLUE; k++) assertNotSame(World.SEASONS, World.forKeyCode(k));
        for (int scan = 398; scan <= 401; scan++) assertNotSame(World.SEASONS, World.forScanCode(scan));
    }
    @Test public void unknownIdStaysClean() { assertSame(World.CLEAN, World.parse("narnia")); assertSame(World.SEASONS, World.parse("seasons")); }
    @Test public void onlyTheThemedWorldsHaveOwnMotion() {
        assertFalse(World.CLEAN.hasOwnMotion());
        assertFalse(World.SEASONS.hasOwnMotion());
        assertTrue(World.HOGWARTS.hasOwnMotion());
        assertTrue(World.MARVEL.hasOwnMotion());
        assertTrue(World.STARWARS.hasOwnMotion());
        assertTrue(World.SUPERNATURAL.hasOwnMotion());
    }
    @Test public void seasonsValuesAsSpecified() {
        assertEquals(0xFFE8A33D, World.SEASONS.keyColor);
        assertEquals(0, World.SEASONS.accent);
        assertEquals(0, World.SEASONS.ambientColor);
        assertEquals(0, World.SEASONS.ambientAlphaPercent);
        assertEquals(0xFF0D0F14, World.SEASONS.backgroundColor);
        assertEquals(0, World.SEASONS.heroPrefixRes);
        assertEquals("", World.SEASONS.greetingPrefix(true));
        assertEquals("", World.SEASONS.greetingSuffix(true));
    }
    /** Seasons is a world with its own scene: icon-only pills as in the others. */
    @Test public void seasonsShowsIconOnlyPills() { assertTrue(World.SEASONS.forcesIconOnlyPills()); }
    /** The scene is Seasons' background: no app-colour glow over it. Every other world keeps config's ambient_glow. */
    @Test public void seasonsDrawsNoAmbientGlow() {
        assertFalse(World.SEASONS.showsAmbientGlow(true));
        assertFalse(World.SEASONS.showsAmbientGlow(false));
        for (World w : World.values()) {
            if (w == World.SEASONS) continue;
            assertTrue(w.showsAmbientGlow(true));
            assertFalse(w.showsAmbientGlow(false));
        }
    }
    @Test public void scanCodesMapLikeKeys() { assertSame(World.MARVEL, World.forScanCode(398)); assertSame(World.CLEAN, World.forScanCode(399)); assertSame(World.HOGWARTS, World.forScanCode(400)); assertSame(World.STARWARS, World.forScanCode(401)); assertNull(World.forScanCode(1)); }
    @Test public void hogwartsGreetsLumosByDayNoxByNight() { assertEquals("Lumos", World.HOGWARTS.greetingPrefix(true)); assertEquals("Nox", World.HOGWARTS.greetingPrefix(false)); assertEquals("", World.CLEAN.greetingPrefix(true)); assertEquals("Äußerer Rand", World.STARWARS.greetingSuffix(true)); }
    @Test public void onlyCleanKeepsTextPills() { assertFalse(World.CLEAN.forcesIconOnlyPills()); assertTrue(World.MARVEL.forcesIconOnlyPills()); }

    @Test public void dockStyleNeedsPlainDockRow() {
        assertTrue(World.dockStyleSupported(true, false, false, 0));
        assertFalse(World.dockStyleSupported(false, false, false, 0));
        assertFalse(World.dockStyleSupported(true, true, false, 0));
        assertFalse(World.dockStyleSupported(true, false, true, 0));
        assertFalse(World.dockStyleSupported(true, false, false, 1));
    }
}
