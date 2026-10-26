package com.gathin.tvhome;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The one look that applies on a day (spec § 4.4): a personal day first (birthday before anniversary before wedding
 * day), then the shortest active occasion, then the season.
 */
final class SeasonsToday {

    /** What changed between two looks at a day (MainActivity.refreshSeasons): nothing; only what the overlay shows
     * (a name or start year edited, the look the same); or the day or the look, which the scene follows too. */
    enum Change { NONE, DETAILS, DAY_OR_LOOK }

    /** The day this is for. */
    final LocalDate date;
    final Look look;
    /** The personal days showing today, birthdays first, then anniversaries, then wedding days. */
    final List<PersonalDay> personal;
    /** The Advent candles burning today, 0 outside Advent. */
    final int adventCandles;

    private SeasonsToday(LocalDate date, Look look, List<PersonalDay> personal, int adventCandles) {
        this.date = date;
        this.look = look;
        this.personal = personal;
        this.adventCandles = adventCandles;
    }

    static SeasonsToday resolve(LocalDate d, List<PersonalDay> days, boolean mothersDay, boolean fathersDay) {
        List<PersonalDay> showing = new ArrayList<>();
        if (days != null) {
            for (PersonalDay p : days) {
                if (p != null && p.shows(d)) showing.add(p);
            }
        }
        // Stable sort: entries of one kind keep the order they were entered in.
        showing.sort(Comparator.comparingInt(p -> p.kind.ordinal()));
        Look look;
        if (!showing.isEmpty()) {
            look = showing.get(0).kind.look();
        } else {
            Look occasion = SeasonsCalendar.occasion(d, mothersDay, fathersDay);
            look = occasion != null ? occasion : SeasonsCalendar.season(d);
        }
        return new SeasonsToday(d, look, Collections.unmodifiableList(showing), SeasonsCalendar.adventCandles(d));
    }

    /** The names of today's entries of the leading kind, joined by " & "; empty without a personal day. */
    String names() {
        if (personal.isEmpty()) return "";
        PersonalDay.Kind lead = personal.get(0).kind;
        StringBuilder out = new StringBuilder();
        for (PersonalDay p : personal) {
            if (p.kind != lead) continue;
            if (out.length() > 0) out.append(" & ");
            out.append(p.names);
        }
        return out.toString();
    }

    /** The years of today's leading entry (the age, the anniversary or wedding count); 0 without a personal day. */
    int years() {
        return personal.isEmpty() ? 0 : personal.get(0).years(date);
    }

    /**
     * Whether this day shows the same as {@code other}: the same look, Advent candles and personal entries (each
     * one's kind, names and years, in order), so its overlay, greeting and accent would not change.
     */
    boolean showsSameAs(SeasonsToday other) {
        if (other == null || look != other.look || adventCandles != other.adventCandles) return false;
        if (personal.size() != other.personal.size()) return false;
        for (int i = 0; i < personal.size(); i++) {
            PersonalDay a = personal.get(i), b = other.personal.get(i);
            if (a.kind != b.kind || !a.names.equals(b.names) || a.years(date) != b.years(other.date)) return false;
        }
        return true;
    }

    /**
     * What changed from the last look Home showed ({@code last} on {@code lastDay}) to {@code next} on {@code day}:
     * a new day or look (the scene fades, the motion is new), only the details (the overlay's names or year count:
     * an entry edited on the settings page the same day), or nothing.
     */
    static Change change(LocalDate lastDay, SeasonsToday last, LocalDate day, SeasonsToday next) {
        if (last == null || lastDay == null || !lastDay.equals(day) || next.look != last.look) {
            return Change.DAY_OR_LOOK;
        }
        return next.showsSameAs(last) ? Change.NONE : Change.DETAILS;
    }
}
