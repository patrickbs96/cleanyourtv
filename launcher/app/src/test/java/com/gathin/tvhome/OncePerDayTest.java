package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

/** Plain JVM tests for the once-a-day mark (the anniversary crawl, later the confetti). */
public class OncePerDayTest {
    private static final LocalDate DAY = LocalDate.of(2026, 3, 14);

    @Test
    public void nothingMarkedIsDue() {
        assertTrue(OncePerDay.due(null, DAY));
    }

    @Test
    public void markedTodayIsNotDueAgainTodayButTomorrow() {
        String last = OncePerDay.mark(DAY);
        assertFalse(OncePerDay.due(last, DAY));
        assertTrue(OncePerDay.due(last, DAY.plusDays(1)));
    }

    @Test
    public void theMarkIsTheIsoDate() {
        assertEquals("2026-03-14", OncePerDay.mark(DAY));
    }

    @Test
    public void aBrokenOrEmptyMarkIsDue() {
        assertTrue(OncePerDay.due("", DAY));
        assertTrue(OncePerDay.due("  ", DAY));
        assertTrue(OncePerDay.due("yesterday", DAY));
        assertTrue(OncePerDay.due("2026-13-40", DAY));
    }

    @Test
    public void anEarlierTestDateIsDueAgain() {
        // A test date before the last mark (force_date) shows it once more: only the same day counts as done.
        assertTrue(OncePerDay.due(OncePerDay.mark(DAY.plusDays(3)), DAY));
    }

    @Test
    public void aMarkWithSpacesStillCounts() {
        assertFalse(OncePerDay.due(" 2026-03-14 ", DAY));
    }

    @Test
    public void takeMarksOnceADay() {
        FakePrefs p = new FakePrefs();
        assertTrue(OncePerDay.take(p, "crawl_last", DAY));
        assertEquals("2026-03-14", p.getString("crawl_last", null));
        assertFalse(OncePerDay.take(p, "crawl_last", DAY));
        assertTrue(OncePerDay.take(p, "crawl_last", DAY.plusDays(1)));
        assertFalse(OncePerDay.take(p, "crawl_last", DAY.plusDays(1)));
    }

    @Test
    public void aWrongClockMarksNothing() {
        // Started before the network time: the day may be wrong, so it neither shows nor counts as done.
        FakePrefs p = new FakePrefs();
        assertFalse(OncePerDay.take(p, "crawl_last", DAY, false));
        assertEquals(null, p.getString("crawl_last", null));
        // Once the clock is right, the real day still shows it.
        assertTrue(OncePerDay.take(p, "crawl_last", DAY, true));
        assertFalse(OncePerDay.take(p, "crawl_last", DAY, true));
    }

    @Test
    public void keysAreIndependent() {
        FakePrefs p = new FakePrefs();
        assertTrue(OncePerDay.take(p, "crawl_last", DAY));
        assertTrue(OncePerDay.take(p, "confetti_last", DAY));
        assertFalse(OncePerDay.take(p, "confetti_last", DAY));
    }
}
