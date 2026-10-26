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

    final Look look;
    /** The personal days showing today, birthdays first, then anniversaries, then wedding days. */
    final List<PersonalDay> personal;
    /** The Advent candles burning today, 0 outside Advent. */
    final int adventCandles;

    private SeasonsToday(Look look, List<PersonalDay> personal, int adventCandles) {
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
        return new SeasonsToday(look, Collections.unmodifiableList(showing), SeasonsCalendar.adventCandles(d));
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
}
