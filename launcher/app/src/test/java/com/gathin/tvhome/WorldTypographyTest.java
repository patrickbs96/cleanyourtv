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

    /** Supernatural's words follow the day's greeting: on the road by day, something out there by night. */
    @Test public void supernaturalWordsFollowTheGreeting() {
        assertEquals("Guten Morgen · Back on the road",
                WorldTypography.greetingText(World.SUPERNATURAL, "Guten Morgen", true));
        assertEquals("Good evening · Something's out there",
                WorldTypography.greetingText(World.SUPERNATURAL, "Good evening", false));
        // Star Wars keeps its words at night too.
        assertEquals("Guten Abend · Äußerer Rand", WorldTypography.greetingText(World.STARWARS, "Guten Abend", false));
    }

    /** Middle-earth's words follow the day's greeting in the app's language: the road by day, the eye by night. */
    @Test public void middleEarthWordsFollowTheGreeting() {
        java.util.function.IntFunction<String> de = res -> res == R.string.world_middleearth_greeting_day
                ? "Weit ist der Weg" : res == R.string.world_middleearth_greeting_night ? "Das Auge wacht" : "?";
        assertEquals("Guten Tag · Weit ist der Weg",
                WorldTypography.greetingText(World.MIDDLE_EARTH, "Guten Tag", true, de));
        assertEquals("Guten Abend · Das Auge wacht",
                WorldTypography.greetingText(World.MIDDLE_EARTH, "Guten Abend", false, de));
        // The others keep their words with the lookup too.
        assertEquals("Nox · Guten Abend", WorldTypography.greetingText(World.HOGWARTS, "Guten Abend", false, de));
        assertEquals("Guten Abend · Äußerer Rand", WorldTypography.greetingText(World.STARWARS, "Guten Abend", false, de));
        assertEquals("Guten Abend", WorldTypography.greetingText(World.CLEAN, "Guten Abend", false, de));
    }

    @Test public void middleEarthGreetsInGold() {
        assertEquals(0xFFD9B56A, WorldTypography.greetingColor(World.MIDDLE_EARTH, 0x12345678));
        assertEquals(0xFFD9B56A, WorldTypography.greetingColor(World.MIDDLE_EARTH, 0x12345678, Look.HALLOWEEN.accent));
    }

    @Test public void supernaturalGreetsInAmber() {
        assertEquals(0xFFE0A24A, WorldTypography.greetingColor(World.SUPERNATURAL, 0x12345678));
        assertEquals(0xFFE0A24A, WorldTypography.greetingColor(World.SUPERNATURAL, 0x12345678, Look.HALLOWEEN.accent));
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

    /** Seasons' greeting is the day's accent lifted towards white, as the festival's; the other worlds keep theirs. */
    @Test public void seasonsGreetingTakesTheLiftedAccent() {
        assertEquals(WorldTypography.liftTowardsWhite(Look.HALLOWEEN.accent, 0.25f),
                WorldTypography.greetingColor(World.SEASONS, 0x12345678, Look.HALLOWEEN.accent));
        assertEquals(0x12345678, WorldTypography.greetingColor(World.CLEAN, 0x12345678, Look.HALLOWEEN.accent));
        assertEquals(0xFFE8C87A, WorldTypography.greetingColor(World.HOGWARTS, 0, Look.HALLOWEEN.accent));
        // Seasons without a look (accent 0) keeps the fallback.
        assertEquals(0x12345678, WorldTypography.greetingColor(World.SEASONS, 0x12345678, 0));
    }

    @Test public void liftTowardsWhiteMovesEachChannelAQuarter() {
        assertEquals(0xFF3F3F3F, WorldTypography.liftTowardsWhite(0xFF000000, 0.25f));
        assertEquals(0xFFFFFFFF, WorldTypography.liftTowardsWhite(0x00FFFFFF, 0.25f));
        assertEquals(0xFFFF573F, WorldTypography.liftTowardsWhite(0xFFFF2000, 0.25f));
    }

    @Test public void dayIsSevenToEighteen() {
        assertEquals(false, WorldTypography.isDayHour(6));
        assertEquals(true, WorldTypography.isDayHour(7));
        assertEquals(true, WorldTypography.isDayHour(18));
        assertEquals(false, WorldTypography.isDayHour(19));
    }
}
