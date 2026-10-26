package com.gathin.tvhome;

import android.view.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemoteKeysDoubleTest {
    @Test public void doublePrefixParsesLikeAppKeys() {
        Map<Integer, String> m = RemoteKeys.parseDoubleScancodes(List.of(
                "double:scan:695:com.google.android.youtube.tv", "scan:695:com.netflix.ninja"));
        assertEquals("com.google.android.youtube.tv", m.get(695));
        assertEquals(1, m.size());
    }

    @Test public void doubleKeycodesParseLikeAppKeys() {
        Map<Integer, String> m = RemoteKeys.parseDoubleKeys(List.of(
                "double:185:inputs", "185:com.netflix.ninja", "double:scan:695:x", "hold:186:y", "double:3:close_apps"),
                name -> 0);
        assertEquals(2, m.size());
        assertEquals("inputs", m.get(185));
        assertEquals("close_apps", m.get(3));
    }

    @Test public void theOtherParsersLeaveDoubleEntriesAlone() {
        List<String> entries = List.of("double:scan:695:x", "double:185:y");
        assertTrue(RemoteKeys.parseAppScancodes(entries).isEmpty());
        assertTrue(RemoteKeys.parseAppKeys(entries, name -> 0).isEmpty());
        assertTrue(RemoteKeys.parseHoldAppScancodes(entries).isEmpty());
        assertTrue(RemoteKeys.parseHoldAppKeys(entries, name -> 0).isEmpty());
    }

    @Test public void withoutDoubleDropsOnlyDoubleEntries() {
        assertEquals(List.of("scan:695:com.netflix.ninja"),
                RemoteKeys.withoutDouble(List.of("double:scan:695:x", "scan:695:com.netflix.ninja")));
    }

    @Test public void secondTapInsideWindowIsDouble() {
        RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(400);
        assertEquals(RemoteKeys.DoubleTap.FIRST, d.onTap(695, 1000));
        assertEquals(RemoteKeys.DoubleTap.SECOND, d.onTap(695, 1300));
    }

    @Test public void secondTapAfterWindowStartsOver() {
        RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(400);
        d.onTap(695, 1000);
        assertEquals(RemoteKeys.DoubleTap.FIRST, d.onTap(695, 1500));
    }

    @Test public void otherKeyInBetweenResets() {
        RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(400);
        d.onTap(695, 1000);
        assertEquals(RemoteKeys.DoubleTap.FIRST, d.onTap(779, 1100));
    }

    @Test public void aThirdTapStartsANewPair() {
        RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(400);
        d.onTap(695, 1000);
        d.onTap(695, 1200);
        assertEquals(RemoteKeys.DoubleTap.FIRST, d.onTap(695, 1300));
    }

    @Test public void theWindowRunsFromTheRelease() {
        // A slow first press: 300 ms down, the second press 350 ms after the release is still a double tap.
        RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(400);
        d.onTap(695, 1000);
        d.release(695, 1300);
        assertEquals(RemoteKeys.DoubleTap.SECOND, d.onTap(695, 1650));
    }

    @Test public void aKeyWithoutDoubleEntryRunsAtOnceAndResets() {
        RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(400);
        Set<Integer> doubles = Set.of(695);
        assertEquals(RemoteKeys.DoubleTap.FIRST, d.onTap(695, 1000, doubles));
        assertEquals(RemoteKeys.DoubleTap.NONE, d.onTap(20, 1100, doubles));
        assertEquals(RemoteKeys.DoubleTap.FIRST, d.onTap(695, 1200, doubles));
    }

    @Test public void forgetEndsOnlyThatKeysTap() {
        RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(400);
        d.onTap(695, 1000);
        d.forget(779);
        assertEquals(RemoteKeys.DoubleTap.SECOND, d.onTap(695, 1100));
        d.onTap(695, 2000);
        d.forget(695);
        assertEquals(RemoteKeys.DoubleTap.FIRST, d.onTap(695, 2100));
    }

    @Test public void homeIsNeverHeldBack() {
        RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(RemoteKeys.DOUBLE_MS);
        assertEquals(RemoteKeys.DoubleTap.PASS, d.onTap(KeyEvent.KEYCODE_HOME, 1000));
        assertEquals(RemoteKeys.DoubleTap.SECOND, d.onTap(KeyEvent.KEYCODE_HOME, 1300));
        assertEquals(RemoteKeys.DoubleTap.PASS, d.onTap(KeyEvent.KEYCODE_HOME, 2000));
        assertEquals(RemoteKeys.DoubleTap.PASS, d.onTap(KeyEvent.KEYCODE_HOME, 2500));
    }

    @Test public void aScancodeIdIsNotTheHomeKey() {
        RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(400);
        assertEquals(RemoteKeys.DoubleTap.FIRST, d.onTap(RemoteKeys.scanId(KeyEvent.KEYCODE_HOME), 1000));
    }
}
