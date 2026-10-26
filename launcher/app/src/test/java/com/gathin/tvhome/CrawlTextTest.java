package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Plain JVM tests for the anniversary crawl's text: the placeholders and the episode's Roman numeral. */
public class CrawlTextTest {

    @Test
    public void briefCase() {
        assertEquals("Episode IV: Alex & Robin", CrawlView.fill("Episode {episode}: {names}", "Alex & Robin", 4));
    }

    @Test
    public void allThreePlaceholders() {
        assertEquals("Not so long ago …\n\nEpisode XII\nAlex & Robin\n\n12 years later …",
                CrawlView.fill("Not so long ago …\n\nEpisode {episode}\n{names}\n\n{years} years later …",
                        "Alex & Robin", 12));
    }

    @Test
    public void eachPlaceholderAsOftenAsItStands() {
        assertEquals("2 2 II II Alex Alex", CrawlView.fill("{years} {years} {episode} {episode} {names} {names}",
                "Alex", 2));
    }

    @Test
    public void unknownPlaceholdersStay() {
        assertEquals("{day} {Names} {} { names } Alex", CrawlView.fill("{day} {Names} {} { names } {names}", "Alex", 1));
    }

    @Test
    public void anOpenBraceWithoutCloseStays() {
        assertEquals("Episode I {names", CrawlView.fill("Episode {episode} {names", "Alex", 1));
        assertEquals("{", CrawlView.fill("{", "Alex", 1));
    }

    @Test
    public void namesAreNotFilledInASecondTime() {
        // A name that looks like a placeholder is a name: one pass over the template, never over what went in.
        assertEquals("Episode III: {years}", CrawlView.fill("Episode {episode}: {names}", "{years}", 3));
    }

    @Test
    public void nullNamesAndTemplate() {
        assertEquals("Episode V: ", CrawlView.fill("Episode {episode}: {names}", null, 5));
        assertEquals("", CrawlView.fill(null, "Alex", 5));
    }

    @Test
    public void episodeOutsideTheRomanRangeIsArabic() {
        assertEquals("Episode 0", CrawlView.fill("Episode {episode}", "Alex", 0));
        assertEquals("Episode 4000", CrawlView.fill("Episode {episode}", "Alex", 4000));
        assertEquals("Episode -2", CrawlView.fill("Episode {episode}", "Alex", -2));
    }

    @Test
    public void romanNumerals() {
        assertEquals("I", CrawlView.roman(1));
        assertEquals("III", CrawlView.roman(3));
        assertEquals("IV", CrawlView.roman(4));
        assertEquals("V", CrawlView.roman(5));
        assertEquals("IX", CrawlView.roman(9));
        assertEquals("XIV", CrawlView.roman(14));
        assertEquals("XIX", CrawlView.roman(19));
        assertEquals("XL", CrawlView.roman(40));
        assertEquals("XLIX", CrawlView.roman(49));
        assertEquals("XC", CrawlView.roman(90));
        assertEquals("CD", CrawlView.roman(400));
        assertEquals("CM", CrawlView.roman(900));
        assertEquals("MCMXCIV", CrawlView.roman(1994));
        assertEquals("MMXXVI", CrawlView.roman(2026));
        assertEquals("MMMCMXCIX", CrawlView.roman(3999));
    }

    @Test
    public void romanEdgesAreArabic() {
        assertEquals("0", CrawlView.roman(0));
        assertEquals("4000", CrawlView.roman(4000));
        assertEquals("-1", CrawlView.roman(-1));
    }
}
