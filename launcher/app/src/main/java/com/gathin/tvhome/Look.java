package com.gathin.tvhome;

/**
 * The looks of the Jahreskreis world: the four seasons as the ground, the occasions of the year on top and the
 * personal days above all (spec § 4). Plain values only, so the class loads in JVM tests; {@link SeasonsToday} picks
 * the one that applies today.
 */
enum Look {
    WINTER("winter", true, false, 0xFFCFE3FF),
    SPRING("spring", true, true, 0xFFFFC8DD),
    SUMMER("summer", true, false, 0xFFFFD28A),
    AUTUMN("autumn", true, false, 0xFFFFB066),
    NEW_YEAR("new_year", false, false, 0xFFD7B8FF),
    CARNIVAL("carnival", false, false, 0xFFFF5C7A),
    VALENTINE("valentine", false, true, 0xFFFF6B8A),
    EASTER("easter", false, true, 0xFFB8F2C9),
    MOTHERS_DAY("mothers_day", false, true, 0xFFFFB3C7),
    FATHERS_DAY("fathers_day", false, true, 0xFF8FC7FF),
    HALLOWEEN("halloween", false, false, 0xFFFF9A2E),
    ST_MARTIN("st_martin", false, false, 0xFFFFCF5A),
    NIKOLAUS_EVE("nikolaus_eve", false, false, 0xFFFF6B6B),
    NIKOLAUS("nikolaus", false, false, 0xFFFF6B6B),
    ADVENT("advent", false, false, 0xFFFFD27A),
    CHRISTMAS("christmas", false, false, 0xFFFFE7A3),
    BIRTHDAY("birthday", false, false, 0xFFFF8FB1),
    ANNIVERSARY("anniversary", false, false, 0xFFCFE0FF),
    WEDDING("wedding", false, false, 0xFFFFC2D1);

    /** snake_case name, e.g. "st_martin". */
    final String id;
    /** Whether the look has a day and a night version; only the four seasons do. */
    final boolean hasNight;
    /** A bright look: the scene dims it at night (OLED rule) unless it has its own night picture (hasNight). */
    final boolean bright;
    /** Accent colour (0xAARRGGBB) for focus and highlights. */
    final int accent;

    Look(String id, boolean hasNight, boolean bright, int accent) {
        this.id = id;
        this.hasNight = hasNight;
        this.bright = bright;
        this.accent = accent;
    }
}
