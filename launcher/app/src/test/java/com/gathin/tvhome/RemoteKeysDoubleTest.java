package com.gathin.tvhome;

import android.view.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    // Presses: the decisions of Keys, one event at a time. Netflix: tap Netflix, double YouTube, hold Kodi.
    private static final int NETFLIX = RemoteKeys.scanId(695);
    private static final int RAKUTEN = RemoteKeys.scanId(779);
    private static final int HOME = KeyEvent.KEYCODE_HOME;
    private static final int DOWN = KeyEvent.ACTION_DOWN;
    private static final int UP = KeyEvent.ACTION_UP;

    private static RemoteKeys.Step netflix(RemoteKeys.Presses p, int action, long down, long now, int repeat) {
        return p.onEvent(NETFLIX, action, down, now, repeat, false, "netflix", "kodi", "youtube");
    }

    private static RemoteKeys.Step home(RemoteKeys.Presses p, int action, long down, long now, int repeat) {
        return p.onEvent(HOME, action, down, now, repeat, false, null, null, "close_apps");
    }

    /** Keys' part: a held tap is posted as the Step says. */
    private static void kept(RemoteKeys.Presses p, int id, RemoteKeys.Step step) {
        if (step.holdTap) p.held(id);
    }

    @Test public void aTapIsHeldBackAndRunsAfterTheWindow() {
        RemoteKeys.Presses p = new RemoteKeys.Presses();
        RemoteKeys.Step down = netflix(p, DOWN, 1000, 1000, 0);
        assertTrue(down.consume);
        assertEquals(null, down.run);
        RemoteKeys.Step up = netflix(p, UP, 1000, 1100, 0);
        assertTrue(up.consume);
        assertTrue(up.holdTap);
        assertEquals(null, up.run);
        kept(p, NETFLIX, up);
        assertEquals(NETFLIX, p.pending());
        p.ran(NETFLIX);   // the window passed
        assertEquals(0, p.pending());
        // a press after the tap ran is a new first tap, not a double tap
        assertFalse(netflix(p, DOWN, 1450, 1450, 0).cancel);
        assertTrue(netflix(p, UP, 1450, 1500, 0).holdTap);
    }

    @Test public void aSecondPressInTimeDropsTheTapAndRunsTheDouble() {
        RemoteKeys.Presses p = new RemoteKeys.Presses();
        netflix(p, DOWN, 1000, 1000, 0);
        kept(p, NETFLIX, netflix(p, UP, 1000, 1100, 0));
        RemoteKeys.Step second = netflix(p, DOWN, 1300, 1300, 0);
        assertTrue(second.consume);
        assertTrue(second.cancel);
        assertEquals(0, p.pending());
        RemoteKeys.Step up = netflix(p, UP, 1300, 1380, 0);
        assertEquals("youtube", up.run);
        assertFalse(up.holdTap);
    }

    @Test public void holdingRunsTheHoldEntryAtOnceNotTheDouble() {
        RemoteKeys.Presses p = new RemoteKeys.Presses();
        netflix(p, DOWN, 1000, 1000, 0);
        RemoteKeys.Step up = netflix(p, UP, 1000, 1000 + RemoteKeys.HOLD_MS, 0);
        assertEquals("kodi", up.run);
        assertFalse(up.holdTap);
    }

    @Test public void repeatedDownsOfOneHeldPressAreNoSecondTap() {
        // Some IR/BT drivers send a held button as identical DOWNs with the same downTime.
        RemoteKeys.Presses p = new RemoteKeys.Presses();
        netflix(p, DOWN, 1000, 1000, 0);
        for (long t = 1050; t < 2000; t += 50) {
            RemoteKeys.Step again = netflix(p, DOWN, 1000, t, 0);
            assertTrue(again.consume);
            assertFalse(again.cancel);
            assertEquals(null, again.run);
        }
        assertTrue(netflix(p, DOWN, 1000, 2000, 3).consume);   // and a counted repeat
        assertEquals("kodi", netflix(p, UP, 1000, 2000, 0).run);
    }

    @Test public void heldDoubleKeyWithoutHoldEntryIsATap() {
        RemoteKeys.Presses p = new RemoteKeys.Presses();
        assertTrue(p.onEvent(NETFLIX, DOWN, 1000, 1000, 0, false, "netflix", null, "youtube").consume);
        p.onEvent(NETFLIX, DOWN, 1000, 1100, 0, false, "netflix", null, "youtube");
        RemoteKeys.Step up = p.onEvent(NETFLIX, UP, 1000, 2000, 0, false, "netflix", null, "youtube");
        assertTrue(up.holdTap);
        assertEquals(null, up.run);
    }

    @Test public void homeGoesToTheSystemAndASecondPressIsTheDouble() {
        RemoteKeys.Presses p = new RemoteKeys.Presses();
        assertFalse(home(p, DOWN, 1000, 1000, 0).consume);
        RemoteKeys.Step up = home(p, UP, 1000, 1080, 0);
        assertFalse(up.consume);
        assertFalse(up.holdTap);
        assertTrue(home(p, DOWN, 1300, 1300, 0).consume);
        RemoteKeys.Step secondUp = home(p, UP, 1300, 1360, 0);
        assertTrue(secondUp.consume);
        assertEquals("close_apps", secondUp.run);
        // the next press is the system's again
        assertFalse(home(p, DOWN, 1500, 1500, 0).consume);
    }

    @Test public void heldHomeIsPassedOnWholeAndNeverADouble() {
        RemoteKeys.Presses p = new RemoteKeys.Presses();
        assertFalse(home(p, DOWN, 1000, 1000, 0).consume);
        for (long t = 1050; t < 3000; t += 50) {
            RemoteKeys.Step again = home(p, DOWN, 1000, t, 0);
            assertFalse(again.consume);
            assertEquals(null, again.run);
        }
        assertFalse(home(p, DOWN, 1000, 3000, 5).consume);
        RemoteKeys.Step up = home(p, UP, 1000, 3000, 0);
        assertFalse(up.consume);
        assertEquals(null, up.run);
    }

    @Test public void theRepeatsOfHomesSecondPressStayItsOwn() {
        RemoteKeys.Presses p = new RemoteKeys.Presses();
        home(p, DOWN, 1000, 1000, 0);
        home(p, UP, 1000, 1080, 0);
        assertTrue(home(p, DOWN, 1300, 1300, 0).consume);
        assertTrue(home(p, DOWN, 1300, 1350, 0).consume);
        assertEquals("close_apps", home(p, UP, 1300, 1600, 0).run);
    }

    @Test public void anotherDoubleKeyFlushesTheHeldTap() {
        RemoteKeys.Presses p = new RemoteKeys.Presses();
        netflix(p, DOWN, 1000, 1000, 0);
        kept(p, NETFLIX, netflix(p, UP, 1000, 1100, 0));
        RemoteKeys.Step down = p.onEvent(RAKUTEN, DOWN, 1200, 1200, 0, false, "disney", null, "x");
        assertFalse(down.cancel);   // Netflix's tap is not dropped
        RemoteKeys.Step up = p.onEvent(RAKUTEN, UP, 1200, 1260, 0, false, "disney", null, "x");
        assertTrue(up.flush);       // it runs now, before Rakuten's is held
        assertTrue(up.holdTap);
    }

    @Test public void aReleaseOfAPressNeverSeenGoesOn() {
        RemoteKeys.Presses p = new RemoteKeys.Presses();
        assertFalse(netflix(p, UP, 900, 1000, 0).consume);
    }

    @Test public void aNewPressEndsAnUnfinishedSecondPress() {
        // The second press's release never came (a lost event): the next press starts fresh.
        RemoteKeys.Presses p = new RemoteKeys.Presses();
        netflix(p, DOWN, 1000, 1000, 0);
        kept(p, NETFLIX, netflix(p, UP, 1000, 1100, 0));
        netflix(p, DOWN, 1300, 1300, 0);
        RemoteKeys.Step fresh = netflix(p, DOWN, 5000, 5000, 0);
        assertTrue(fresh.consume);
        assertFalse(fresh.cancel);
        assertTrue(netflix(p, UP, 5000, 5100, 0).holdTap);
        assertEquals(null, netflix(p, UP, 1300, 5200, 0).run);   // the stale release runs nothing
    }

    @Test public void aCanceledSecondPressRunsNothing() {
        RemoteKeys.Presses p = new RemoteKeys.Presses();
        netflix(p, DOWN, 1000, 1000, 0);
        kept(p, NETFLIX, netflix(p, UP, 1000, 1100, 0));
        netflix(p, DOWN, 1300, 1300, 0);
        RemoteKeys.Step up = p.onEvent(NETFLIX, UP, 1300, 1350, 0, true, "netflix", "kodi", "youtube");
        assertTrue(up.consume);
        assertEquals(null, up.run);
    }

    @Test public void aScancodeIdIsNotTheHomeKey() {
        RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(400);
        assertEquals(RemoteKeys.DoubleTap.FIRST, d.onTap(RemoteKeys.scanId(KeyEvent.KEYCODE_HOME), 1000));
    }
}
