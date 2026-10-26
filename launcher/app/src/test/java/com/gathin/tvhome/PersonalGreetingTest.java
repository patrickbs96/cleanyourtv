package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;

import org.junit.Test;

/**
 * Plain JVM tests for the personal days' greeting in every world (spec § 4.6), its precedence over a theme's festival
 * and the world's greeting, and the once-a-day confetti. Names and dates are placeholders only.
 */
public class PersonalGreetingTest {

    /** The German and English default anniversary greetings (seasons_anniversary_greeting_default). */
    private static final String DE_ANNIVERSARY = "{years} Jahre · {names}";
    private static final String EN_ANNIVERSARY = "{years} years · {names}";

    /** The German and English templates, as in values-de and values. */
    private static final PersonalGreeting.Templates DE = new PersonalGreeting.Templates(
            "Happy Birthday, %1$s", "Alles Gute, %1$s",
            "1 Jahr · %1$s", DE_ANNIVERSARY, "1. Hochzeitstag", "%1$d. Hochzeitstag");
    private static final PersonalGreeting.Templates EN = new PersonalGreeting.Templates(
            "Happy Birthday, %1$s", "Happy birthday, %1$s",
            "1 year · %1$s", EN_ANNIVERSARY, "1 year married", "%1$d years married");

    private static final PersonalDay ALEX_BIRTHDAY = PersonalDay.decode("birthday;3;14;1990;Alex");
    private static final PersonalDay ROBIN_BIRTHDAY = PersonalDay.decode("birthday;3;14;1992;Robin");
    private static final PersonalDay ANNIVERSARY = PersonalDay.decode("anniversary;3;14;2019;Alex & Robin");
    private static final PersonalDay WEDDING = PersonalDay.decode("wedding;8;19;2027;Alex & Robin");

    private static SeasonsToday on(int y, int m, int d, PersonalDay... days) {
        return SeasonsToday.resolve(LocalDate.of(y, m, d), Arrays.asList(days), false, false);
    }

    private static final SeasonsToday PI_DAY_2026 = on(2026, 3, 14, ALEX_BIRTHDAY);

    @Test
    public void noEntryGivesEmptyText() {
        SeasonsToday none = on(2026, 3, 14);
        for (World w : World.values()) {
            assertEquals(w.id, "", PersonalGreeting.text(DE, w, none, true));
            assertEquals(w.id, "", PersonalGreeting.text(DE, w, null, true));
        }
        // An entry on another day shows nothing either.
        assertEquals("", PersonalGreeting.text(DE, World.CLEAN, on(2026, 3, 15, ALEX_BIRTHDAY), true));
    }

    @Test
    public void birthdayInCleanAndSeasons() {
        assertEquals("Happy Birthday, Alex", PersonalGreeting.text(DE, World.CLEAN, PI_DAY_2026, true));
        assertEquals("Happy Birthday, Alex", PersonalGreeting.text(DE, World.SEASONS, PI_DAY_2026, false));
        assertEquals("Happy Birthday, Alex", PersonalGreeting.text(EN, World.CLEAN, PI_DAY_2026, true));
    }

    @Test
    public void birthdayInTheThemedWorldsTakesTheirWords() {
        assertEquals("Lumos · Alles Gute, Alex", PersonalGreeting.text(DE, World.HOGWARTS, PI_DAY_2026, true));
        assertEquals("Nox · Alles Gute, Alex", PersonalGreeting.text(DE, World.HOGWARTS, PI_DAY_2026, false));
        assertEquals("Suit up · Happy birthday, Alex", PersonalGreeting.text(EN, World.MARVEL, PI_DAY_2026, true));
        assertEquals("Alles Gute, Alex · Äußerer Rand",
                PersonalGreeting.text(DE, World.STARWARS, PI_DAY_2026, true));
    }

    /** Supernatural is a themed world: the plain wish, then its day or night words. */
    @Test
    public void birthdayInSupernaturalTakesItsDayOrNightWords() {
        assertEquals("Alles Gute, Alex · Back on the road",
                PersonalGreeting.text(DE, World.SUPERNATURAL, PI_DAY_2026, true));
        assertEquals("Happy birthday, Alex · Something's out there",
                PersonalGreeting.text(EN, World.SUPERNATURAL, PI_DAY_2026, false));
        SeasonsToday anniversary = on(2026, 3, 14, ANNIVERSARY);
        assertEquals("7 Jahre · Alex & Robin · Something's out there",
                PersonalGreeting.text(DE, World.SUPERNATURAL, anniversary, false));
    }

    @Test
    public void twoBirthdaysShareOneGreeting() {
        SeasonsToday both = on(2026, 3, 14, ALEX_BIRTHDAY, ROBIN_BIRTHDAY);
        assertEquals("Happy Birthday, Alex & Robin", PersonalGreeting.text(DE, World.CLEAN, both, true));
    }

    @Test
    public void anniversaryCountsTheYears() {
        SeasonsToday t = on(2026, 3, 14, ANNIVERSARY);
        assertEquals("7 Jahre · Alex & Robin", PersonalGreeting.text(DE, World.CLEAN, t, true));
        assertEquals("7 years · Alex & Robin", PersonalGreeting.text(EN, World.CLEAN, t, true));
        assertEquals("1 Jahr · Alex & Robin",
                PersonalGreeting.text(DE, World.CLEAN, on(2020, 3, 14, ANNIVERSARY), true));
        // The start year itself: no count yet, only the names.
        assertEquals("Alex & Robin", PersonalGreeting.text(DE, World.CLEAN, on(2019, 3, 14, ANNIVERSARY), true));
    }

    @Test
    public void anniversaryTakesTheDefaultTemplate() {
        SeasonsToday t = on(2027, 3, 14, ANNIVERSARY);
        assertEquals("8 Jahre · Alex & Robin", PersonalGreeting.text(DE, World.CLEAN, t, true));
        assertEquals("8 years · Alex & Robin", PersonalGreeting.text(EN, World.SEASONS, t, true));
        // In the themed worlds after their word, as before.
        assertEquals("Lumos · 8 Jahre · Alex & Robin", PersonalGreeting.text(DE, World.HOGWARTS, t, true));
    }

    @Test
    public void anniversaryTakesTheTemplateFromThePrefs() {
        FakePrefs prefs = new FakePrefs();
        PersonalDays.saveAnniversaryGreeting(prefs, "Happy {years}th · {names}", DE_ANNIVERSARY);
        PersonalGreeting.Templates own = DE.withAnniversary(PersonalDays.anniversaryGreeting(prefs, DE_ANNIVERSARY));
        SeasonsToday t = on(2027, 3, 14, ANNIVERSARY);
        assertEquals("Happy 8th · Alex & Robin", PersonalGreeting.text(own, World.CLEAN, t, true));
        assertEquals("Nox · Happy 8th · Alex & Robin", PersonalGreeting.text(own, World.HOGWARTS, t, false));
        // An own template is taken as written, also in the first year (no "1 Jahr" of the default).
        assertEquals("Happy 1th · Alex & Robin",
                PersonalGreeting.text(own, World.CLEAN, on(2020, 3, 14, ANNIVERSARY), true));
        // Further entries of the day still follow with " · ".
        assertEquals("Happy Birthday, Alex · Happy 8th · Alex & Robin",
                PersonalGreeting.text(own, World.CLEAN, on(2027, 3, 14, ANNIVERSARY, ALEX_BIRTHDAY), true));
    }

    @Test
    public void anEmptyPrefGivesTheDefaultTemplate() {
        FakePrefs prefs = new FakePrefs();
        assertEquals(DE_ANNIVERSARY, PersonalDays.anniversaryGreeting(prefs, DE_ANNIVERSARY));
        PersonalDays.saveAnniversaryGreeting(prefs, "Own {names}", DE_ANNIVERSARY);
        assertTrue(prefs.contains(PersonalDays.ANNIVERSARY_GREETING));
        PersonalDays.saveAnniversaryGreeting(prefs, "   ", DE_ANNIVERSARY);
        assertFalse(prefs.contains(PersonalDays.ANNIVERSARY_GREETING));
        PersonalDays.saveAnniversaryGreeting(prefs, "Own {names}", DE_ANNIVERSARY);
        PersonalDays.saveAnniversaryGreeting(prefs, DE_ANNIVERSARY, DE_ANNIVERSARY);
        assertFalse(prefs.contains(PersonalDays.ANNIVERSARY_GREETING));
        prefs.edit().putString(PersonalDays.ANNIVERSARY_GREETING, "").commit();
        PersonalGreeting.Templates s = DE.withAnniversary(PersonalDays.anniversaryGreeting(prefs, DE_ANNIVERSARY));
        assertEquals("8 Jahre · Alex & Robin",
                PersonalGreeting.text(s, World.CLEAN, on(2027, 3, 14, ANNIVERSARY), true));
        // The default keeps its own first year.
        assertEquals("1 Jahr · Alex & Robin",
                PersonalGreeting.text(s, World.CLEAN, on(2020, 3, 14, ANNIVERSARY), true));
    }

    @Test
    public void anUnknownPlaceholderStays() {
        PersonalGreeting.Templates own = DE.withAnniversary("{years} · {names} {heart}");
        assertEquals("8 · Alex & Robin {heart}",
                PersonalGreeting.text(own, World.CLEAN, on(2027, 3, 14, ANNIVERSARY), true));
    }

    @Test
    public void weddingDayCountsFromTheFirstYear() {
        assertEquals("5. Hochzeitstag", PersonalGreeting.text(DE, World.CLEAN, on(2032, 8, 19, WEDDING), true));
        assertEquals("5 years married", PersonalGreeting.text(EN, World.CLEAN, on(2032, 8, 19, WEDDING), true));
        assertEquals("1. Hochzeitstag", PersonalGreeting.text(DE, World.CLEAN, on(2028, 8, 19, WEDDING), true));
        assertEquals("1 year married", PersonalGreeting.text(EN, World.CLEAN, on(2028, 8, 19, WEDDING), true));
        // The wedding day itself shows nothing (PersonalDay.shows).
        assertEquals("", PersonalGreeting.text(DE, World.CLEAN, on(2027, 8, 19, WEDDING), true));
    }

    @Test
    public void furtherEntriesOfTheDayAreAppended() {
        SeasonsToday t = on(2026, 3, 14, ANNIVERSARY, ALEX_BIRTHDAY);
        assertEquals("Happy Birthday, Alex · 7 Jahre · Alex & Robin",
                PersonalGreeting.text(DE, World.CLEAN, t, true));
        assertEquals("Nox · Alles Gute, Alex · 7 Jahre · Alex & Robin",
                PersonalGreeting.text(DE, World.HOGWARTS, t, false));
    }

    @Test
    public void personalBeatsFestivalBeatsWorld() {
        assertEquals("P", PersonalGreeting.pick("P", "F", "W"));
        assertEquals("F", PersonalGreeting.pick("", "F", "W"));
        assertEquals("F", PersonalGreeting.pick(null, "F", "W"));
        assertEquals("W", PersonalGreeting.pick("", "", "W"));
        assertEquals("W", PersonalGreeting.pick(null, null, "W"));
    }

    @Test
    public void festivalLosesToBirthday() {
        String birthday = PersonalGreeting.text(EN, World.CLEAN, PI_DAY_2026, true);
        assertEquals("Happy Birthday, Alex", PersonalGreeting.pick(birthday, "Happy Diwali", "Good morning"));
        // Without an entry the festival's greeting stays as it is today.
        String none = PersonalGreeting.text(EN, World.CLEAN, on(2026, 3, 15), true);
        assertEquals("Happy Diwali", PersonalGreeting.pick(none, "Happy Diwali", "Good morning"));
    }

    @Test
    public void iconFollowsTheFirstEntrysKind() {
        assertEquals(PersonalDay.Kind.BIRTHDAY, PersonalGreeting.iconKind(World.CLEAN,
                on(2026, 3, 14, ANNIVERSARY, ALEX_BIRTHDAY)));
        assertEquals(PersonalDay.Kind.ANNIVERSARY, PersonalGreeting.iconKind(World.MARVEL,
                on(2026, 3, 14, ANNIVERSARY)));
        assertEquals(PersonalDay.Kind.WEDDING, PersonalGreeting.iconKind(World.STARWARS,
                on(2032, 8, 19, WEDDING)));
        assertNull(PersonalGreeting.iconKind(World.CLEAN, on(2026, 3, 15, ALEX_BIRTHDAY)));
        assertNull(PersonalGreeting.iconKind(World.CLEAN, null));
        // Seasons shows the day in its own look, right of the clock: no icon there.
        assertNull(PersonalGreeting.iconKind(World.SEASONS, PI_DAY_2026));
    }

    // ---- the confetti, once a day ---------------------------------------------------------------

    @Test
    public void confettiPlaysOnceADayWithAnEntry() {
        FakePrefs prefs = new FakePrefs();
        assertTrue(ConfettiBurstView.due(prefs, World.CLEAN, PI_DAY_2026, true, false, true));
        // Due is not yet done: the day counts once the burst starts.
        assertNull(prefs.getString(PersonalDays.CONFETTI_LAST, null));
        assertTrue(ConfettiBurstView.take(prefs, PI_DAY_2026, true));
        assertEquals("2026-03-14", prefs.getString(PersonalDays.CONFETTI_LAST, null));
        assertFalse(ConfettiBurstView.due(prefs, World.CLEAN, PI_DAY_2026, true, false, true));
        assertFalse(ConfettiBurstView.take(prefs, PI_DAY_2026, true));
        // A world switch the same day does not play it again.
        assertFalse(ConfettiBurstView.due(prefs, World.HOGWARTS, PI_DAY_2026, true, false, true));
        SeasonsToday nextYear = on(2027, 3, 14, ALEX_BIRTHDAY);
        assertTrue(ConfettiBurstView.due(prefs, World.HOGWARTS, nextYear, true, false, true));
    }

    @Test
    public void confettiLeftBeforeItStartsIsStillDue() {
        // Home paused in the short wait before the burst: nothing played, so the day is not used up.
        FakePrefs prefs = new FakePrefs();
        assertTrue(ConfettiBurstView.due(prefs, World.MARVEL, PI_DAY_2026, true, false, true));
        assertTrue(ConfettiBurstView.due(prefs, World.MARVEL, PI_DAY_2026, true, false, true));
        assertNull(prefs.getString(PersonalDays.CONFETTI_LAST, null));
    }

    @Test
    public void confettiNeedsAnEntryAndNeverPlaysInSeasons() {
        FakePrefs prefs = new FakePrefs();
        assertFalse(ConfettiBurstView.due(prefs, World.CLEAN, on(2026, 3, 15, ALEX_BIRTHDAY), true, false, true));
        assertFalse(ConfettiBurstView.due(prefs, World.CLEAN, null, true, false, true));
        assertFalse(ConfettiBurstView.due(prefs, World.SEASONS, PI_DAY_2026, true, false, true));
        assertFalse(ConfettiBurstView.take(prefs, null, true));
        assertNull(prefs.getString(PersonalDays.CONFETTI_LAST, null));
        // Seasons left the day untouched, so Clean still plays it later the same day.
        assertTrue(ConfettiBurstView.due(prefs, World.CLEAN, PI_DAY_2026, true, false, true));
    }

    @Test
    public void reducedMotionSkipsTheConfettiButCountsTheDay() {
        FakePrefs prefs = new FakePrefs();
        assertFalse(ConfettiBurstView.due(prefs, World.MARVEL, PI_DAY_2026, true, true, true));
        assertEquals("2026-03-14", prefs.getString(PersonalDays.CONFETTI_LAST, null));
        assertFalse(ConfettiBurstView.due(prefs, World.MARVEL, PI_DAY_2026, true, false, true));
        FakePrefs off = new FakePrefs();
        assertFalse(ConfettiBurstView.due(off, World.MARVEL, PI_DAY_2026, false, false, true));
        assertEquals("2026-03-14", off.getString(PersonalDays.CONFETTI_LAST, null));
    }

    @Test
    public void aWrongClockNeitherPlaysNorCountsTheConfetti() {
        FakePrefs prefs = new FakePrefs();
        assertFalse(ConfettiBurstView.due(prefs, World.CLEAN, PI_DAY_2026, true, false, false));
        assertFalse(ConfettiBurstView.due(prefs, World.CLEAN, PI_DAY_2026, true, true, false));
        assertFalse(ConfettiBurstView.take(prefs, PI_DAY_2026, false));
        assertNull(prefs.getString(PersonalDays.CONFETTI_LAST, null));
        // With the right time it plays as usual.
        assertTrue(ConfettiBurstView.due(prefs, World.CLEAN, PI_DAY_2026, true, false, true));
    }

    @Test
    public void confettiColoursAreTheAccentAndTwoNeighbours() {
        int[] c = ConfettiBurstView.colours(0xFFE03131);
        assertEquals(3, c.length);
        assertEquals(0xFFE03131, c[0]);
        for (int x : c) assertEquals(0xFF, x >>> 24);
        assertFalse(c[1] == c[0] || c[2] == c[0] || c[1] == c[2]);
        // Clean has no accent of its own (0): its key colour stands in.
        assertArrayEquals(ConfettiBurstView.colours(World.CLEAN.keyColor), ConfettiBurstView.colours(0));
    }

    @Test
    public void confettiNeighboursAreHueTurns() {
        // Pure red turned by +/-30 degrees: towards orange and towards pink, the same lightness.
        int[] c = ConfettiBurstView.colours(0xFFFF0000);
        assertEquals(0xFFFF8000, c[1]);
        assertEquals(0xFFFF0080, c[2]);
        // A grey has no hue to turn: the neighbours are lighter and darker instead.
        int[] g = ConfettiBurstView.colours(0xFF808080);
        assertTrue(Integer.compareUnsigned(g[1], g[0]) > 0);
        assertTrue(Integer.compareUnsigned(g[2], g[0]) < 0);
    }
}
