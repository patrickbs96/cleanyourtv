package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;

import org.junit.Test;

public class SeasonsCalendarTest {

    @Test
    public void easterMatchesKnownYears() {
        assertEquals(LocalDate.of(2026, 4, 5), SeasonsCalendar.easter(2026));
        assertEquals(LocalDate.of(2027, 3, 28), SeasonsCalendar.easter(2027));
        assertEquals(LocalDate.of(2028, 4, 16), SeasonsCalendar.easter(2028));
        assertEquals(LocalDate.of(2030, 4, 21), SeasonsCalendar.easter(2030));
    }

    @Test
    public void firstAdventIsTheFourthSundayBeforeChristmas() {
        assertEquals(LocalDate.of(2026, 11, 29), SeasonsCalendar.firstAdvent(2026));
        assertEquals(LocalDate.of(2027, 11, 28), SeasonsCalendar.firstAdvent(2027));
    }

    @Test
    public void adventCandlesCountTheSundays() {
        assertEquals(1, SeasonsCalendar.adventCandles(LocalDate.of(2026, 11, 29)));
        assertEquals(2, SeasonsCalendar.adventCandles(LocalDate.of(2026, 12, 7)));
        assertEquals(4, SeasonsCalendar.adventCandles(LocalDate.of(2026, 12, 23)));
        assertEquals(0, SeasonsCalendar.adventCandles(LocalDate.of(2026, 12, 24)));
    }

    @Test
    public void movingFeastsIn2027() {
        assertEquals(Look.CARNIVAL, SeasonsCalendar.occasion(LocalDate.of(2027, 2, 4), false, false));
        assertEquals(Look.CARNIVAL, SeasonsCalendar.occasion(LocalDate.of(2027, 2, 8), false, false));
        assertNull(SeasonsCalendar.occasion(LocalDate.of(2027, 2, 9), false, false));
        assertEquals(Look.FATHERS_DAY, SeasonsCalendar.occasion(LocalDate.of(2027, 5, 6), true, true));
        assertEquals(Look.MOTHERS_DAY, SeasonsCalendar.occasion(LocalDate.of(2027, 5, 9), true, true));
        assertNull(SeasonsCalendar.occasion(LocalDate.of(2027, 5, 9), false, false));   // off until switched on
    }

    @Test
    public void seasonsHaveAstronomicalBounds() {
        assertEquals(Look.WINTER, SeasonsCalendar.season(LocalDate.of(2027, 3, 20)));
        assertEquals(Look.SPRING, SeasonsCalendar.season(LocalDate.of(2027, 3, 21)));
        assertEquals(Look.SUMMER, SeasonsCalendar.season(LocalDate.of(2027, 6, 21)));
        assertEquals(Look.AUTUMN, SeasonsCalendar.season(LocalDate.of(2027, 9, 23)));
        assertEquals(Look.WINTER, SeasonsCalendar.season(LocalDate.of(2027, 12, 21)));
    }
}
