package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WorldTypographyTest {
    @Test public void greetingJoinsPrefixAndSuffixWithMiddleDot() {
        assertEquals("Nox · Guten Abend", WorldTypography.greetingText(World.HOGWARTS, "Guten Abend", false));
        assertEquals("Suit up · Guten Abend", WorldTypography.greetingText(World.MARVEL, "Guten Abend", true));
        assertEquals("Guten Abend · Äußerer Rand", WorldTypography.greetingText(World.STARWARS, "Guten Abend", true));
        assertEquals("Guten Abend", WorldTypography.greetingText(World.CLEAN, "Guten Abend", true));
    }

    @Test public void lumosByDay() {
        assertEquals("Lumos · Guten Morgen", WorldTypography.greetingText(World.HOGWARTS, "Guten Morgen", true));
    }

    @Test public void cleanKeepsFallbackColour() {
        assertEquals(0x12345678, WorldTypography.greetingColor(World.CLEAN, 0x12345678));
        assertEquals(0xFF8FB3FF, WorldTypography.greetingColor(World.STARWARS, 0));
        assertEquals(0xFFE8C87A, WorldTypography.greetingColor(World.HOGWARTS, 0));
        assertEquals(0xFFE6B85A, WorldTypography.greetingColor(World.MARVEL, 0));
    }

    @Test public void heroLabelCasing() {
        assertEquals("NETFLIX", WorldTypography.heroLabel(World.MARVEL, "Netflix"));
        assertEquals("NETFLIX", WorldTypography.heroLabel(World.STARWARS, "Netflix"));
        assertEquals("Netflix", WorldTypography.heroLabel(World.CLEAN, "Netflix"));
        assertEquals("Netflix", WorldTypography.heroLabel(World.HOGWARTS, "Netflix"));
    }

    /** Seasons writes like Clean: the greeting, its colour and the hero label stay as they are. */
    @Test public void seasonsKeepsCleansType() {
        assertEquals("Guten Abend", WorldTypography.greetingText(World.SEASONS, "Guten Abend", false));
        assertEquals(0x12345678, WorldTypography.greetingColor(World.SEASONS, 0x12345678));
        assertEquals("Netflix", WorldTypography.heroLabel(World.SEASONS, "Netflix"));
    }

    @Test public void dayIsSevenToEighteen() {
        assertEquals(false, WorldTypography.isDayHour(6));
        assertEquals(true, WorldTypography.isDayHour(7));
        assertEquals(true, WorldTypography.isDayHour(18));
        assertEquals(false, WorldTypography.isDayHour(19));
    }
}
