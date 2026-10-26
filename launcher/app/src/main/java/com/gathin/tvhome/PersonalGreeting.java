package com.gathin.tvhome;

import android.content.res.Resources;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The personal days' greeting in every world (Jahreskreis spec § 4.3, § 4.6): "Happy Birthday, {names}" in Clean and
 * Seasons, the world's own words around "Alles Gute, {names}" in Hogwarts, Marvel and Star Wars ("Lumos · Alles Gute,
 * Alex"); an anniversary counts its years ("7 Jahre · {names}", "Happy 4th · {names}" when it falls on 4 May), a wedding
 * day its wedding years ("5. Hochzeitstag"). Further entries of the same day follow with " · ". Empty without an entry,
 * so Home takes exactly its usual greeting ({@link #pick}). Only plain values here, so it runs in JVM tests; the
 * templates come from the strings ({@link #templates}).
 */
final class PersonalGreeting {

    private static final String SEPARATOR = " · ";

    /** The texts with their placeholders, from the strings of the app's language. */
    static final class Templates {
        /** Clean and Seasons: "Happy Birthday, %1$s". */
        final String happyBirthday;
        /** The themed worlds, after their word: "Alles Gute, %1$s". */
        final String birthday;
        /** An anniversary on 4 May: "Happy 4th · %1$s". */
        final String fourth;
        /** "1 Jahr · %1$s" and "%1$d Jahre · %2$s". */
        final String anniversaryOne;
        final String anniversary;
        /** "1. Hochzeitstag" and "%1$d. Hochzeitstag". */
        final String weddingOne;
        final String wedding;

        Templates(String happyBirthday, String birthday, String fourth, String anniversaryOne, String anniversary,
                  String weddingOne, String wedding) {
            this.happyBirthday = happyBirthday;
            this.birthday = birthday;
            this.fourth = fourth;
            this.anniversaryOne = anniversaryOne;
            this.anniversary = anniversary;
            this.weddingOne = weddingOne;
            this.wedding = wedding;
        }
    }

    private PersonalGreeting() {
    }

    static Templates templates(Resources r) {
        return new Templates(r.getString(R.string.seasons_greeting_happy_birthday),
                r.getString(R.string.seasons_greeting_birthday), r.getString(R.string.seasons_greeting_fourth),
                r.getString(R.string.seasons_greeting_anniversary_one),
                r.getString(R.string.seasons_greeting_anniversary),
                r.getString(R.string.seasons_greeting_wedding_one), r.getString(R.string.seasons_greeting_wedding));
    }

    /** The greeting for today's entries in world {@code w}; {@code day} picks Hogwarts' Lumos or Nox. */
    static String text(Resources r, World w, SeasonsToday t, boolean day) {
        if (t == null || t.personal.isEmpty()) return "";
        return text(templates(r), w, t, day);
    }

    /** The same from the templates: "" without an entry today. */
    static String text(Templates s, World w, SeasonsToday t, boolean day) {
        if (t == null || t.personal.isEmpty()) return "";
        boolean themed = w.hasOwnMotion();
        // One phrase per kind and count: the birthdays of the day share one ("Alex & Robin"); identical phrases once.
        Set<String> phrases = new LinkedHashSet<>();
        StringBuilder birthdays = new StringBuilder();
        for (PersonalDay p : t.personal) {
            if (p.kind != PersonalDay.Kind.BIRTHDAY) continue;
            if (birthdays.length() > 0) birthdays.append(" & ");
            birthdays.append(p.names);
        }
        if (birthdays.length() > 0) {
            phrases.add(format(themed ? s.birthday : s.happyBirthday, birthdays.toString()));
        }
        for (PersonalDay p : t.personal) {
            if (p.kind == PersonalDay.Kind.ANNIVERSARY) phrases.add(anniversary(s, p, t));
            else if (p.kind == PersonalDay.Kind.WEDDING) phrases.add(wedding(s, p.years(t.date)));
        }
        String text = String.join(SEPARATOR, phrases);
        if (!themed) return text;
        String prefix = w.greetingPrefix(day);
        String suffix = w.greetingSuffix();
        if (!prefix.isEmpty()) text = prefix + SEPARATOR + text;
        return suffix.isEmpty() ? text : text + SEPARATOR + suffix;
    }

    private static String anniversary(Templates s, PersonalDay p, SeasonsToday t) {
        // The date's pun, for any anniversary entered on 4 May.
        if (p.month == 5 && p.day == 4) return format(s.fourth, p.names);
        int years = p.years(t.date);
        if (years < 1) return p.names;   // the start year itself: nothing to count yet
        if (years == 1) return format(s.anniversaryOne, p.names);
        return String.format(Locale.ROOT, s.anniversary, years, p.names);
    }

    private static String wedding(Templates s, int years) {
        // PersonalDay.shows: a wedding day shows from its first anniversary on.
        return years == 1 ? s.weddingOne : String.format(Locale.ROOT, s.wedding, years);
    }

    private static String format(String template, String names) {
        return String.format(Locale.ROOT, template, names);
    }

    /**
     * The greeting Home shows: the personal one before a theme's festival greeting before the world's own (spec
     * § 4.8), the first that is not empty.
     */
    static String pick(String personal, String festival, String world) {
        if (personal != null && !personal.isEmpty()) return personal;
        if (festival != null && !festival.isEmpty()) return festival;
        return world;
    }

    /**
     * The kind whose icon stands next to the clock all day: today's first entry's (birthday before anniversary before
     * wedding day), or null without one. Never in Seasons: its look shows the day right of the clock.
     */
    static PersonalDay.Kind iconKind(World w, SeasonsToday t) {
        if (w == World.SEASONS || t == null || t.personal.isEmpty()) return null;
        return t.personal.get(0).kind;
    }
}
