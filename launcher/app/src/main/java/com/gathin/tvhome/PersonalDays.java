package com.gathin.tvhome;

import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The household's personal days and Jahreskreis' own choices, kept in the launcher's SharedPreferences {@link #PREFS}
 * like the key bindings (spec § 4.7): the entries one {@link PersonalDay#encode()} line each under {@link #DAYS}, the
 * Mother's Day and Father's Day switches (off by default), the anniversary crawl text (the string default until it
 * is changed) and the test date. Nothing of it is in the repo; it lives only on the TV and in its backup.
 *
 * The test date ({@link #FORCE_DATE}, set by SeasonsActivity's force_date extra over adb) holds while this process
 * lives, as WorldScene.FORCE_KEY does: Home's recreates keep it, and a new process (force-stop, reboot) is back on
 * the real date.
 */
final class PersonalDays {

    static final String PREFS = "seasons";
    static final String DAYS = "days";
    static final String MOTHERS_DAY = "mothers_day";
    static final String FATHERS_DAY = "fathers_day";
    static final String CRAWL_TEXT = "crawl_text";
    /** The day the anniversary crawl last started (OncePerDay), so it rolls once a day. */
    static final String CRAWL_LAST = "crawl_last";
    /** The day the personal days' confetti last played in the other worlds (OncePerDay, ConfettiBurstView). */
    static final String CONFETTI_LAST = "confetti_last";
    static final String FORCE_DATE = "force_date";
    /** The force_date value that goes back to the real date. */
    static final String AUTO = "auto";

    /** Whether this process has already dropped an old process's FORCE_DATE: once per process, not in the prefs. */
    private static boolean processChecked;

    private PersonalDays() {
    }

    /** The saved entries in their saved order; a broken line is skipped, never thrown on. */
    static List<PersonalDay> load(SharedPreferences prefs) {
        String saved = prefs.getString(DAYS, "");
        if (saved == null || saved.isEmpty()) return Collections.emptyList();
        List<PersonalDay> days = new ArrayList<>();
        for (String line : saved.split("\n")) {
            PersonalDay day = PersonalDay.decode(line);
            if (day != null) days.add(day);
        }
        return days;
    }

    /** Saves the entries at once (the page's next read must see them); none removes the key. */
    static void save(SharedPreferences prefs, List<PersonalDay> days) {
        if (days.isEmpty()) {
            prefs.edit().remove(DAYS).commit();
            return;
        }
        StringBuilder out = new StringBuilder();
        for (PersonalDay day : days) {
            if (out.length() > 0) out.append('\n');
            out.append(day.encode());
        }
        prefs.edit().putString(DAYS, out.toString()).commit();
    }

    static boolean mothersDay(SharedPreferences prefs) {
        return prefs.getBoolean(MOTHERS_DAY, false);
    }

    static boolean fathersDay(SharedPreferences prefs) {
        return prefs.getBoolean(FATHERS_DAY, false);
    }

    /** The crawl text with its {names}, {years} and {episode}; {@code defaultText} (seasons_crawl_default) until set. */
    static String crawlText(SharedPreferences prefs, String defaultText) {
        String text = prefs.getString(CRAWL_TEXT, null);
        return text == null || text.trim().isEmpty() ? defaultText : text;
    }

    /** Saves the crawl text; an empty one or the default itself removes it, so a new default reaches the TV. */
    static void saveCrawlText(SharedPreferences prefs, String text, String defaultText) {
        if (text == null || text.trim().isEmpty() || text.equals(defaultText)) {
            prefs.edit().remove(CRAWL_TEXT).commit();
        } else {
            prefs.edit().putString(CRAWL_TEXT, text).commit();
        }
    }

    /** The day Jahreskreis shows: this process's test date, or {@code realToday}. */
    static LocalDate today(SharedPreferences prefs, LocalDate realToday) {
        if (!processChecked) {
            processChecked = true;
            if (prefs.contains(FORCE_DATE)) prefs.edit().remove(FORCE_DATE).apply();
            return realToday;
        }
        LocalDate forced = parse(prefs.getString(FORCE_DATE, null));
        return forced != null ? forced : realToday;
    }

    /**
     * The test switch: {@code yyyy-MM-dd} saves that date for this process's next Home, {@code auto} goes back to the
     * real date. Anything else is ignored and keeps what was there; the result says whether it was taken. The caller
     * makes Home rebuild.
     */
    static boolean saveForcedDate(SharedPreferences prefs, String isoOrAuto) {
        if (isoOrAuto == null) return false;
        String value = isoOrAuto.trim();
        if (AUTO.equals(value)) {
            processChecked = true;
            prefs.edit().remove(FORCE_DATE).commit();
            return true;
        }
        LocalDate date = parse(value);
        if (date == null) return false;
        // Chosen in this process, so its next Home must keep it rather than take it for an old process's.
        processChecked = true;
        prefs.edit().putString(FORCE_DATE, date.toString()).commit();
        return true;
    }

    /** For tests: as in a new process. */
    static void resetProcessForTest() {
        processChecked = false;
    }

    private static LocalDate parse(String iso) {
        if (iso == null) return null;
        try {
            return LocalDate.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
