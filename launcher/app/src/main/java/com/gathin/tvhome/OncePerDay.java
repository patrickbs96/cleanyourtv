package com.gathin.tvhome;

import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Something Home shows at most once a day (the anniversary crawl, the personal days' confetti): the day it last
 * showed is kept as {@code yyyy-MM-dd} under its own key. Only that same day counts as done; any other day, a test
 * date before it included, or a missing or broken mark, is due.
 */
final class OncePerDay {

    private OncePerDay() {
    }

    /** Whether it is still to show on {@code today}, given the last mark ({@link #mark}; null when none). */
    static boolean due(String lastIso, LocalDate today) {
        if (lastIso == null) return true;
        try {
            return !LocalDate.parse(lastIso.trim()).equals(today);
        } catch (DateTimeParseException e) {
            return true;
        }
    }

    /** The mark for {@code today}, to be saved when it starts showing. */
    static String mark(LocalDate today) {
        return today.toString();
    }

    /**
     * Whether it shows on {@code today}: true once a day, and the mark is saved at once (a recreate right after must
     * see it), so it counts as shown from the moment it starts.
     */
    static boolean take(SharedPreferences prefs, String key, LocalDate today) {
        if (!due(prefs.getString(key, null), today)) return false;
        prefs.edit().putString(key, mark(today)).commit();
        return true;
    }
}
