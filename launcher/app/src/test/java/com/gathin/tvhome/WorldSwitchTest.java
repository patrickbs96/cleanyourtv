package com.gathin.tvhome;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** KeyEvent's constructors are stubs in JVM tests, so these go through the int overload the KeyEvent one delegates to. */
public class WorldSwitchTest {
    private static World up(int code, World current, boolean popupOpen) {
        return WorldSwitch.target(KeyEvent.ACTION_UP, code, 0, current, popupOpen);
    }

    @Test public void sameWorldIsNoop() { assertNull(up(KeyEvent.KEYCODE_PROG_RED, World.MARVEL, false)); }
    @Test public void otherColourSwitches() { assertSame(World.HOGWARTS, up(KeyEvent.KEYCODE_PROG_YELLOW, World.CLEAN, false)); }
    @Test public void greenGoesBackToClean() { assertSame(World.CLEAN, up(KeyEvent.KEYCODE_PROG_GREEN, World.STARWARS, false)); }
    @Test public void fourSwitchesToStarWars() { assertSame(World.STARWARS, up(KeyEvent.KEYCODE_4, World.CLEAN, false)); }
    @Test public void popupBlocksADigitToo() { assertNull(up(KeyEvent.KEYCODE_2, World.CLEAN, true)); }
    @Test public void popupBlocksSwitch() { assertNull(up(KeyEvent.KEYCODE_PROG_YELLOW, World.CLEAN, true)); }
    @Test public void downEventIsIgnored() {
        assertNull(WorldSwitch.target(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PROG_BLUE, 0, World.CLEAN, false));
    }
    @Test public void dpadIsIgnored() { assertNull(up(KeyEvent.KEYCODE_DPAD_LEFT, World.CLEAN, false)); }
    @Test public void unknownKeyFallsBackToScanCode() {
        assertSame(World.STARWARS, WorldSwitch.target(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_UNKNOWN, 401, World.CLEAN, false));
    }
    @Test public void scanCodeOnlyCountsForUnknownKeys() {
        assertNull(WorldSwitch.target(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, 401, World.CLEAN, false));
    }
    @Test public void unknownKeyWithOtherScanCodeIsIgnored() {
        assertNull(WorldSwitch.target(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_UNKNOWN, 0, World.CLEAN, false));
    }
}
