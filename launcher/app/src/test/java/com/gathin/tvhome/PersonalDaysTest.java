package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Plain JVM tests for the personal days on the TV: loading, saving, the switches and the test date. */
public class PersonalDaysTest {
    private static final LocalDate REAL = LocalDate.of(2026, 10, 26);

    @Test
    public void badLinesAreSkipped() {
        FakePrefs p = new FakePrefs();
        p.edit().putString("days", "birthday;12;24;1990;Alex\nnonsense\nbirthday;13;1;1990;X\nwedding;8;19;2027;Alex & Robin").commit();
        assertEquals(2, PersonalDays.load(p).size());
    }

    @Test
    public void nothingSavedIsAnEmptyList() {
        assertTrue(PersonalDays.load(new FakePrefs()).isEmpty());
    }

    @Test
    public void saveAndLoadKeepTheEntriesInOrder() {
        FakePrefs p = new FakePrefs();
        List<PersonalDay> days = Arrays.asList(
                new PersonalDay(PersonalDay.Kind.WEDDING, 8, 19, 2027, "Alex & Robin"),
                new PersonalDay(PersonalDay.Kind.BIRTHDAY, 12, 24, 1990, "Alex; the elder"));
        PersonalDays.save(p, days);
        List<PersonalDay> back = PersonalDays.load(p);
        assertEquals(2, back.size());
        assertEquals(days.get(0).encode(), back.get(0).encode());
        assertEquals("Alex; the elder", back.get(1).names);
        assertFalse(p.getString(PersonalDays.DAYS, "").contains("elder\n"));
    }

    @Test
    public void savingNoEntriesRemovesTheKey() {
        FakePrefs p = new FakePrefs().put(PersonalDays.DAYS, "birthday;1;1;1990;Alex");
        PersonalDays.save(p, new ArrayList<>());
        assertFalse(p.contains(PersonalDays.DAYS));
    }

    @Test
    public void mothersAndFathersDayStartOff() {
        assertFalse(new FakePrefs().getBoolean("mothers_day", false));
        FakePrefs p = new FakePrefs();
        assertFalse(PersonalDays.mothersDay(p));
        assertFalse(PersonalDays.fathersDay(p));
        p.put(PersonalDays.MOTHERS_DAY, true);
        assertTrue(PersonalDays.mothersDay(p));
        assertFalse(PersonalDays.fathersDay(p));
    }

    @Test
    public void crawlTextFallsBackToTheDefault() {
        FakePrefs p = new FakePrefs();
        assertEquals("default", PersonalDays.crawlText(p, "default"));
        PersonalDays.saveCrawlText(p, "Episode {episode}: {names}", "default");
        assertEquals("Episode {episode}: {names}", PersonalDays.crawlText(p, "default"));
        PersonalDays.saveCrawlText(p, "  ", "default");
        assertFalse(p.contains(PersonalDays.CRAWL_TEXT));
        PersonalDays.saveCrawlText(p, "default", "default");
        assertFalse(p.contains(PersonalDays.CRAWL_TEXT));
    }

    @Test
    public void forcedDateHoldsOnlyForThisProcess() {
        PersonalDays.resetProcessForTest();
        FakePrefs prefs = new FakePrefs();
        assertEquals(REAL, PersonalDays.today(prefs, REAL));                     // Home's first start
        assertTrue(PersonalDays.saveForcedDate(prefs, "2026-12-06"));           // am start … --es force_date
        assertEquals(LocalDate.of(2026, 12, 6), PersonalDays.today(prefs, REAL)); // Home rebuilds
        assertEquals(LocalDate.of(2026, 12, 6), PersonalDays.today(prefs, REAL)); // recreate again
        PersonalDays.resetProcessForTest();                                     // force-stop or reboot
        assertEquals(REAL, PersonalDays.today(prefs, REAL));
        assertFalse(prefs.contains(PersonalDays.FORCE_DATE));
        assertEquals(REAL, PersonalDays.today(prefs, REAL));
    }

    @Test
    public void autoClearsTheForcedDate() {
        PersonalDays.resetProcessForTest();
        FakePrefs prefs = new FakePrefs();
        PersonalDays.saveForcedDate(prefs, "2027-08-19");
        assertEquals(LocalDate.of(2027, 8, 19), PersonalDays.today(prefs, REAL));
        assertTrue(PersonalDays.saveForcedDate(prefs, "auto"));
        assertEquals(REAL, PersonalDays.today(prefs, REAL));
        assertFalse(prefs.contains(PersonalDays.FORCE_DATE));
    }

    @Test
    public void aMalformedDateIsIgnoredAndKeepsTheState() {
        PersonalDays.resetProcessForTest();
        FakePrefs prefs = new FakePrefs();
        PersonalDays.saveForcedDate(prefs, "2026-12-06");
        assertFalse(PersonalDays.saveForcedDate(prefs, "2026-13-40"));
        assertFalse(PersonalDays.saveForcedDate(prefs, "06.12.2026"));
        assertFalse(PersonalDays.saveForcedDate(prefs, null));
        assertEquals(LocalDate.of(2026, 12, 6), PersonalDays.today(prefs, REAL));
    }

    @Test
    public void aBrokenSavedDateIsTheRealOne() {
        PersonalDays.resetProcessForTest();
        FakePrefs prefs = new FakePrefs();
        PersonalDays.today(prefs, REAL);
        prefs.put(PersonalDays.FORCE_DATE, "someday");
        assertEquals(REAL, PersonalDays.today(prefs, REAL));
    }
}
