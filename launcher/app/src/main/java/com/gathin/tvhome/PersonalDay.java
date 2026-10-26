package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.Month;
import java.util.Locale;

/**
 * One personal day of the household: a birthday, an anniversary or a wedding day, with its date, the year it started
 * (for counting) and the name(s) to show (spec § 4.3/4.8). The data lives only on the TV; it is saved as one line per
 * entry, {@code kind;month;day;startYear;names}, see {@link #encode()}.
 */
final class PersonalDay {

    enum Kind {
        BIRTHDAY("birthday"), ANNIVERSARY("anniversary"), WEDDING("wedding");

        /** Saved in the first field of a line. */
        final String id;

        Kind(String id) {
            this.id = id;
        }

        /** The look of this kind in Jahreskreis. */
        Look look() {
            switch (this) {
                case ANNIVERSARY: return Look.ANNIVERSARY;
                case WEDDING: return Look.WEDDING;
                default: return Look.BIRTHDAY;
            }
        }

        static Kind parse(String id) {
            for (Kind k : values()) {
                if (k.id.equals(id)) return k;
            }
            return null;
        }
    }

    final Kind kind;
    final int month;
    final int day;
    final int startYear;
    /** One name or several, e.g. "Alex & Robin", as entered. */
    final String names;

    PersonalDay(Kind kind, int month, int day, int startYear, String names) {
        this.kind = kind;
        this.month = month;
        this.day = day;
        this.startYear = startYear;
        this.names = names;
    }

    /** Whether this entry falls on the day; a 29 February falls on the 28th in other years. */
    boolean isOn(LocalDate d) {
        if (month == 2 && day == 29 && !d.isLeapYear()) {
            return d.getMonthValue() == 2 && d.getDayOfMonth() == 28;
        }
        return d.getMonthValue() == month && d.getDayOfMonth() == day;
    }

    /** The years since the start year, e.g. the age or the anniversary count. */
    int years(LocalDate d) {
        return d.getYear() - startYear;
    }

    /** Whether the day shows today: a wedding from its first anniversary on, an anniversary from its start year. */
    boolean shows(LocalDate d) {
        if (!isOn(d)) return false;
        switch (kind) {
            case WEDDING: return years(d) >= 1;
            case ANNIVERSARY: return years(d) >= 0;
            default: return true;
        }
    }

    /** The saved line; '%', ';' and line breaks in the names become %25, %3B and %0A. */
    String encode() {
        String escaped = names.replace("%", "%25").replace(";", "%3B").replace("\n", "%0A");
        return kind.id + ";" + month + ";" + day + ";" + startYear + ";" + escaped;
    }

    /** The entry of a saved line, or null when the line is broken (unknown kind, bad date, no names). */
    static PersonalDay decode(String line) {
        if (line == null) return null;
        String[] f = line.split(";", -1);
        if (f.length != 5) return null;
        Kind kind = Kind.parse(f[0].trim());
        if (kind == null) return null;
        int month;
        int day;
        int startYear;
        try {
            month = Integer.parseInt(f[1].trim());
            day = Integer.parseInt(f[2].trim());
            startYear = Integer.parseInt(f[3].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (month < 1 || month > 12) return null;
        if (day < 1 || day > Month.of(month).maxLength()) return null;
        String names = unescape(f[4]);
        if (names.trim().isEmpty()) return null;
        return new PersonalDay(kind, month, day, startYear, names);
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                String code = s.substring(i + 1, i + 3).toUpperCase(Locale.ROOT);
                if (code.equals("25")) { out.append('%'); i += 2; continue; }
                if (code.equals("3B")) { out.append(';'); i += 2; continue; }
                if (code.equals("0A")) { out.append('\n'); i += 2; continue; }
            }
            out.append(c);
        }
        return out.toString();
    }
}
