package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.Test;

public class SeasonsTodayTest {

    // Names are placeholders only.
    static final PersonalDay ALEX = PersonalDay.decode("birthday;12;24;1990;Alex");

    /** The look of a day without personal entries, Mother's and Father's Day off. */
    private static SeasonsToday today(int y, int m, int d) {
        return SeasonsToday.resolve(LocalDate.of(y, m, d), List.of(), false, false);
    }

    @Test
    public void shorterOccasionWins() {
        assertEquals(Look.NIKOLAUS_EVE, today(2026, 12, 5).look);
        assertEquals(Look.NIKOLAUS, today(2026, 12, 6).look);
        assertEquals(Look.ADVENT, today(2026, 12, 7).look);
        assertEquals(Look.EASTER, today(2027, 3, 21).look);    // Palm Sunday beats the first day of spring
        assertEquals(Look.ADVENT, today(2026, 12, 21).look);   // Advent beats winter
    }

    @Test
    public void newYearSpansTheTurnOfTheYear() {
        assertEquals(Look.NEW_YEAR, today(2026, 12, 31).look);
        assertEquals(Look.NEW_YEAR, today(2027, 1, 1).look);
        assertEquals(Look.WINTER, today(2027, 1, 2).look);
    }

    @Test
    public void birthdayBeatsChristmas() {
        SeasonsToday t = SeasonsToday.resolve(LocalDate.of(2026, 12, 24), List.of(ALEX), false, false);
        assertEquals(Look.BIRTHDAY, t.look);
        assertEquals("Alex", t.names());
    }

    @Test
    public void twoBirthdaysShowBothNames() {
        PersonalDay robin = PersonalDay.decode("birthday;12;24;1992;Robin");
        assertEquals("Alex & Robin",
                SeasonsToday.resolve(LocalDate.of(2026, 12, 24), List.of(ALEX, robin), false, false).names());
    }

    @Test
    public void anniversaryCountsYears() {
        PersonalDay a = PersonalDay.decode("anniversary;3;14;2019;Alex & Robin");
        SeasonsToday t = SeasonsToday.resolve(LocalDate.of(2027, 3, 14), List.of(a), false, false);
        assertEquals(Look.ANNIVERSARY, t.look);
        assertEquals(8, a.years(LocalDate.of(2027, 3, 14)));
    }

    @Test
    public void weddingShowsFromTheFirstAnniversary() {
        PersonalDay w = PersonalDay.decode("wedding;8;19;2027;Alex & Robin");
        assertEquals(Look.SUMMER, SeasonsToday.resolve(LocalDate.of(2027, 8, 19), List.of(w), false, false).look);
        assertEquals(Look.WEDDING, SeasonsToday.resolve(LocalDate.of(2028, 8, 19), List.of(w), false, false).look);
    }

    @Test
    public void leapDayBirthdayFallsOnTheTwentyEighth() {
        PersonalDay leap = PersonalDay.decode("birthday;2;29;2000;Alex");
        assertTrue(leap.isOn(LocalDate.of(2027, 2, 28)));
        assertTrue(leap.isOn(LocalDate.of(2028, 2, 29)));
        assertFalse(leap.isOn(LocalDate.of(2028, 2, 28)));
    }

    @Test
    public void encodeRoundTripsAndBrokenLinesAreNull() {
        PersonalDay odd = new PersonalDay(PersonalDay.Kind.WEDDING, 6, 1, 2020, "Alex;100%\nRobin");
        assertEquals("wedding;6;1;2020;Alex%3B100%25%0ARobin", odd.encode());
        PersonalDay back = PersonalDay.decode(odd.encode());
        assertEquals("Alex;100%\nRobin", back.names);
        assertEquals(PersonalDay.Kind.WEDDING, back.kind);
        assertNull(PersonalDay.decode("party;6;1;2020;Alex"));
        assertNull(PersonalDay.decode("birthday;2;30;2020;Alex"));
        assertNull(PersonalDay.decode("birthday;x;1;2020;Alex"));
        assertNull(PersonalDay.decode("birthday;6;1;2020"));
        assertNull(PersonalDay.decode("birthday;6;1;2020;"));
        assertNull(PersonalDay.decode(null));
    }

    @Test
    public void birthdayLeadsWhenTwoKindsMeet() {
        PersonalDay a = PersonalDay.decode("anniversary;12;24;2019;Alex & Robin");
        SeasonsToday t = SeasonsToday.resolve(LocalDate.of(2026, 12, 24), List.of(a, ALEX), false, false);
        assertEquals(Look.BIRTHDAY, t.look);
        assertEquals(2, t.personal.size());
    }
}
