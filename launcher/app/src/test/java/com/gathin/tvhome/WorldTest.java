package com.gathin.tvhome;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    @Test public void digitsOneToFourPickTheWorldsInMenuOrder() {
        assertSame(World.CLEAN, World.forKeyCode(KeyEvent.KEYCODE_1));
        assertSame(World.HOGWARTS, World.forKeyCode(KeyEvent.KEYCODE_2));
        assertSame(World.MARVEL, World.forKeyCode(KeyEvent.KEYCODE_3));
        assertSame(World.STARWARS, World.forKeyCode(KeyEvent.KEYCODE_4));
        assertNull(World.forKeyCode(KeyEvent.KEYCODE_0));
        assertNull(World.forKeyCode(KeyEvent.KEYCODE_5));   // free for a fifth world
        assertNull(World.forKeyCode(KeyEvent.KEYCODE_9));
    }
    @Test public void scanCodesMapLikeKeys() { assertSame(World.MARVEL, World.forScanCode(398)); assertSame(World.CLEAN, World.forScanCode(399)); assertSame(World.HOGWARTS, World.forScanCode(400)); assertSame(World.STARWARS, World.forScanCode(401)); assertNull(World.forScanCode(1)); }
    @Test public void hogwartsGreetsLumosByDayNoxByNight() { assertEquals("Lumos", World.HOGWARTS.greetingPrefix(true)); assertEquals("Nox", World.HOGWARTS.greetingPrefix(false)); assertEquals("", World.CLEAN.greetingPrefix(true)); assertEquals("Äußerer Rand", World.STARWARS.greetingSuffix()); }
    @Test public void onlyCleanKeepsTextPills() { assertFalse(World.CLEAN.forcesIconOnlyPills()); assertTrue(World.MARVEL.forcesIconOnlyPills()); }

    @Test public void dockStyleNeedsPlainDockRow() {
        assertTrue(World.dockStyleSupported(true, false, false, 0));
        assertFalse(World.dockStyleSupported(false, false, false, 0));
        assertFalse(World.dockStyleSupported(true, true, false, 0));
        assertFalse(World.dockStyleSupported(true, false, true, 0));
        assertFalse(World.dockStyleSupported(true, false, false, 1));
    }
}
