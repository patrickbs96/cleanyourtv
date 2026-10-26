package com.gathin.tvhome;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * The calendar behind Jahreskreis: seasons with fixed astronomical bounds and the occasions of the year, fixed and
 * moving (spec § 4.1/4.2). Plain Java, no Android classes.
 */
final class SeasonsCalendar {

    private SeasonsCalendar() {}

    /** Easter Sunday of the Gregorian calendar (Gauß, in the form of Meeus/Jones/Butcher). */
    static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }

    /** The first Sunday of Advent: the Sunday between 27 November and 3 December. */
    static LocalDate firstAdvent(int year) {
        return LocalDate.of(year, 11, 27).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    /** The season of this day: winter 21.12.–20.03., spring 21.03.–20.06., summer 21.06.–22.09., autumn the rest. */
    static Look season(LocalDate d) {
        int md = d.getMonthValue() * 100 + d.getDayOfMonth();
        if (md >= 1221 || md <= 320) return Look.WINTER;
        if (md <= 620) return Look.SPRING;
        if (md <= 922) return Look.SUMMER;
        return Look.AUTUMN;
    }

    /**
     * The occasion active on this day, or null. When two overlap, the shorter one wins (a one-day feast inside a
     * longer one); on equal length the earlier one in {@link Look}'s order. Mother's and Father's Day count only
     * when switched on.
     */
    static Look occasion(LocalDate d, boolean mothersDay, boolean fathersDay) {
        int year = d.getYear();
        LocalDate easter = easter(year);
        Look best = null;
        long bestDays = Long.MAX_VALUE;
        for (Look look : Look.values()) {
            LocalDate from;
            LocalDate to;
            switch (look) {
                case NEW_YEAR:
                    // 31.12.–01.01. crosses the turn of the year; take the span this day can belong to.
                    from = d.getMonthValue() == 1 ? LocalDate.of(year - 1, 12, 31) : LocalDate.of(year, 12, 31);
                    to = from.plusDays(1);
                    break;
                case CARNIVAL:
                    from = easter.minusDays(52);   // Weiberfastnacht
                    to = easter.minusDays(48);     // Rosenmontag
                    break;
                case VALENTINE:
                    from = to = LocalDate.of(year, 2, 14);
                    break;
                case EASTER:
                    from = easter.minusDays(7);    // Palm Sunday
                    to = easter.plusDays(1);       // Easter Monday
                    break;
                case MOTHERS_DAY:
                    if (!mothersDay) continue;
                    from = to = LocalDate.of(year, 5, 1)
                            .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY));
                    break;
                case FATHERS_DAY:
                    if (!fathersDay) continue;
                    from = to = easter.plusDays(39);   // Ascension Day
                    break;
                case HALLOWEEN:
                    from = LocalDate.of(year, 10, 24);
                    to = LocalDate.of(year, 10, 31);
                    break;
                case ST_MARTIN:
                    from = LocalDate.of(year, 11, 1);
                    to = LocalDate.of(year, 11, 11);
                    break;
                case NIKOLAUS_EVE:
                    from = to = LocalDate.of(year, 12, 5);
                    break;
                case NIKOLAUS:
                    from = to = LocalDate.of(year, 12, 6);
                    break;
                case ADVENT:
                    from = firstAdvent(year);
                    to = LocalDate.of(year, 12, 23);
                    break;
                case CHRISTMAS:
                    from = LocalDate.of(year, 12, 24);
                    to = LocalDate.of(year, 12, 26);
                    break;
                default:
                    continue;   // seasons and personal days are not occasions
            }
            if (d.isBefore(from) || d.isAfter(to)) continue;
            long days = ChronoUnit.DAYS.between(from, to) + 1;
            if (days < bestDays) {
                best = look;
                bestDays = days;
            }
        }
        return best;
    }

    /** How many Advent candles burn: 1 from the first Sunday of Advent, one more each Sunday, 0 outside 1st Advent–23.12. */
    static int adventCandles(LocalDate d) {
        LocalDate first = firstAdvent(d.getYear());
        if (d.isBefore(first) || d.isAfter(LocalDate.of(d.getYear(), 12, 23))) return 0;
        return 1 + (int) (ChronoUnit.DAYS.between(first, d) / 7);
    }
}
