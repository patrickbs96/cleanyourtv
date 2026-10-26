package com.gathin.tvhome;

import android.view.KeyEvent;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeyBindingsTest {
    @Test public void toAppKeysEmitsTapDoubleAndHold() {
        FakePrefs p = new FakePrefs();
        int n = KeyBindings.add(p, "Netflix", 0, 695);
        KeyBindings.set(p, n, "tap", "com.netflix.ninja");
        KeyBindings.set(p, n, "double", "com.google.android.youtube.tv");
        assertEquals(List.of("scan:695:com.netflix.ninja", "double:scan:695:com.google.android.youtube.tv"),
                KeyBindings.toAppKeys(p));
        KeyBindings.set(p, n, "hold", "inputs");
        assertEquals("hold:scan:695:inputs", KeyBindings.toAppKeys(p).get(2));
    }

    @Test public void keycodeWinsOverScancodeWhenKnown() {
        FakePrefs p = new FakePrefs();
        int n = KeyBindings.add(p, "Gelb", 185, 400);
        KeyBindings.set(p, n, "tap", "inputs");
        assertEquals(List.of("185:inputs"), KeyBindings.toAppKeys(p));
    }

    @Test public void emptySlotsEmitNothing() {
        FakePrefs p = new FakePrefs();
        KeyBindings.add(p, "Leer", 0, 700);
        assertTrue(KeyBindings.toAppKeys(p).isEmpty());
    }

    @Test public void anEmptyTargetClearsASlot() {
        FakePrefs p = new FakePrefs();
        int n = KeyBindings.add(p, "X", 0, 700);
        KeyBindings.set(p, n, "tap", "com.netflix.ninja");
        KeyBindings.set(p, n, "tap", "");
        assertTrue(KeyBindings.toAppKeys(p).isEmpty());
        assertFalse(p.contains("tap." + n));
    }

    @Test public void removeDropsAllSlots() {
        FakePrefs p = new FakePrefs();
        int n = KeyBindings.add(p, "X", 0, 701);
        KeyBindings.set(p, n, "hold", "inputs");
        KeyBindings.remove(p, n);
        assertTrue(KeyBindings.toAppKeys(p).isEmpty());
        assertTrue(KeyBindings.keys(p).isEmpty());
    }

    @Test public void keysKeepTheirOrderAndNumbers() {
        FakePrefs p = new FakePrefs();
        int a = KeyBindings.add(p, "Netflix", 0, 695);
        int b = KeyBindings.add(p, "Rakuten", 0, 779);
        KeyBindings.remove(p, a);
        int c = KeyBindings.add(p, "Philips", 0, 582);
        assertTrue(c > b);   // a removed number is never handed out again
        List<KeyBindings.Key> keys = KeyBindings.keys(p);
        assertEquals(2, keys.size());
        assertEquals("Rakuten", keys.get(0).name);
        assertEquals(779, keys.get(0).scancode);
        assertEquals("Philips", keys.get(1).name);
        assertEquals(c, keys.get(1).n);
    }

    @Test public void aNameWithTheSeparatorSurvives() {
        FakePrefs p = new FakePrefs();
        int n = KeyBindings.add(p, "A|B", 0, 695);
        assertEquals("A/B", KeyBindings.keys(p).get(0).name);
        KeyBindings.rename(p, n, "  Netflix ");
        assertEquals("Netflix", KeyBindings.keys(p).get(0).name);
    }

    @Test public void findMatchesTheSameButton() {
        FakePrefs p = new FakePrefs();
        int n = KeyBindings.add(p, "Netflix", 0, 695);
        int g = KeyBindings.add(p, "Gelb", 185, 400);
        assertEquals(n, KeyBindings.find(p, 0, 695));
        assertEquals(g, KeyBindings.find(p, 185, 401));
        assertEquals(-1, KeyBindings.find(p, 0, 779));
    }

    @Test public void theHomeKeyHasOnlyADoubleTap() {
        FakePrefs p = new FakePrefs();
        KeyBindings.setHomeDouble(p, "close_apps");
        assertEquals("close_apps", KeyBindings.homeDouble(p));
        assertEquals(List.of("double:" + KeyEvent.KEYCODE_HOME + ":close_apps"), KeyBindings.toAppKeys(p));
        KeyBindings.setHomeDouble(p, "");
        assertTrue(KeyBindings.toAppKeys(p).isEmpty());
    }

    @Test public void bindingsComeAfterTheConfigSoTheyWin() {
        FakePrefs p = new FakePrefs();
        int n = KeyBindings.add(p, "Netflix", 0, 695);
        KeyBindings.set(p, n, "tap", "com.disney.disneyplus");
        List<String> entries = KeyBindings.overlay(List.of("scan:695:com.netflix.ninja", "190:a.b"), p);
        assertEquals(List.of("scan:695:com.netflix.ninja", "190:a.b", "scan:695:com.disney.disneyplus"), entries);
        assertEquals("com.disney.disneyplus", RemoteKeys.parseAppScancodes(entries).get(695));
    }

    @Test public void navigationVolumeBackAndHomeAreNeverLearned() {
        int[] never = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER};
        for (int code : never) assertFalse(String.valueOf(code), KeyBindings.learnable(code, 100));
        assertTrue(KeyBindings.learnable(KeyEvent.KEYCODE_UNKNOWN, 695));
        assertTrue(KeyBindings.learnable(KeyEvent.KEYCODE_PROG_YELLOW, 0));
        assertFalse(KeyBindings.learnable(KeyEvent.KEYCODE_UNKNOWN, 0));   // nothing to tell it apart by
    }

    @Test public void theCaughtPressIsKnownByItsDownTime() {
        KeyBindings.catchPress(0, 695, 1000);
        assertTrue(KeyBindings.caughtPress(0, 695, 1000));    // its repeated DOWNs
        // Its release, once for each key service that filters it (learn's catch branch is exactly this check and
        // changes nothing on an UP): the first service does not clear it for the second.
        assertTrue(KeyBindings.caughtPress(0, 695, 1000));
        assertTrue(KeyBindings.caughtPress(0, 695, 1000));
        assertFalse(KeyBindings.caughtPress(0, 695, 2000));   // a later press of the same button is not swallowed
        assertFalse(KeyBindings.caughtPress(0, 779, 1000));
        KeyBindings.catchPress(0, 779, 3000);                 // the next catch replaces it
        assertFalse(KeyBindings.caughtPress(0, 695, 1000));
    }

    @Test public void learningEndsAfterFifteenSeconds() {
        assertTrue(KeyBindings.learningAt(1000, 1000 + KeyBindings.LEARN_MS - 1));
        assertFalse(KeyBindings.learningAt(1000, 1000 + KeyBindings.LEARN_MS));
        assertFalse(KeyBindings.learningAt(0, 5000));
    }
}
