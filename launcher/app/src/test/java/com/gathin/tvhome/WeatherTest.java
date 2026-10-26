package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class WeatherTest {
    @Test public void urlUsesTwoDecimalsAndCurrentFields() { assertEquals("https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,weather_code&timezone=auto", Weather.url("52.52", "13.41")); }
    @Test public void parseReadsTemperatureAndCode() { Weather.Reading r = Weather.parse("{\"current\":{\"time\":\"2026-10-25T19:15\",\"temperature_2m\":11.6,\"weather_code\":3}}"); assertEquals(12, r.temperature); assertEquals(3, r.code); }
    @Test public void parseRejectsGarbage() { assertNull(Weather.parse("<html>")); assertNull(Weather.parse("{\"current\":{}}")); }
    @Test public void freshnessIsTwoHours() { assertTrue(Weather.fresh(0, 2 * 3600_000L - 1)); assertFalse(Weather.fresh(0, 2 * 3600_000L)); }
    @Test public void retryIsFiveMinutesAfterFailure() { assertEquals(30 * 60_000L, Weather.nextDelayMs(false)); assertEquals(5 * 60_000L, Weather.nextDelayMs(true)); }
    @Test public void wmoWords() { assertEquals("wmo_clear", WeatherWords.key(0)); assertEquals("wmo_rain", WeatherWords.key(63)); assertEquals("wmo_thunder", WeatherWords.key(99)); assertEquals("wmo_unknown", WeatherWords.key(42)); }

    // Beyond the brief: the parser's edges and the first fetch after a start.
    @Test public void parseReadsTheCurrentBlockNotTheUnits() {
        // Open-Meteo sends current_units first, with the same field names as strings.
        Weather.Reading r = Weather.parse("{\"latitude\":52.52,\"current_units\":{\"temperature_2m\":\"°C\",\"weather_code\":\"wmo code\"},"
                + "\"current\":{\"time\":\"2026-10-25T19:15\",\"interval\":900,\"temperature_2m\":-2.6,\"weather_code\":71}}");
        assertEquals(-3, r.temperature);
        assertEquals(71, r.code);
    }
    @Test public void parseRejectsMissingOrBrokenFields() {
        assertNull(Weather.parse(null));
        assertNull(Weather.parse(""));
        assertNull(Weather.parse("{\"current\":{\"temperature_2m\":11.6}}"));
        assertNull(Weather.parse("{\"current\":{\"weather_code\":3}}"));
        assertNull(Weather.parse("{\"current\":{\"temperature_2m\":null,\"weather_code\":3}}"));
        assertNull(Weather.parse("{\"current\":{\"temperature_2m\":11.6,\"weather_code\":2.5}}"));
        assertNull(Weather.parse("{\"error\":true,\"reason\":\"Latitude must be in range of -90 to 90°.\"}"));
    }
    @Test public void coordinatesAreRoundedToTwoDecimalsOrRefused() {
        assertEquals("52.52", Weather.coordinate("52.52", 90));
        assertEquals("52.52", Weather.coordinate(" 52.5212 ", 90));
        assertEquals("13.41", Weather.coordinate("13.4149", 180));
        assertEquals("-33.87", Weather.coordinate("-33.8688", 90));
        assertNull(Weather.coordinate("", 90));
        assertNull(Weather.coordinate(null, 90));
        assertNull(Weather.coordinate("north", 90));
        assertNull(Weather.coordinate("91", 90));
    }
    @Test public void aClockSetBackIsNotFresh() { assertFalse(Weather.fresh(10_000, 5_000)); }
    @Test public void firstFetchFollowsThePlan() {
        long min = 60_000L;
        assertEquals(0, Weather.firstDelayMs(0, false, 100 * min));          // nothing yet: now
        assertEquals(20 * min, Weather.firstDelayMs(10 * min, false, 20 * min));   // fetched 10 min ago
        assertEquals(0, Weather.firstDelayMs(10 * min, false, 50 * min));    // overdue
        assertEquals(3 * min, Weather.firstDelayMs(10 * min, true, 12 * min));     // failed 2 min ago
        assertEquals(30 * min, Weather.firstDelayMs(90 * min, false, 10 * min));   // clock set back: one period
    }
    @Test public void wmoWordsCoverEveryRange() {
        int[][] ranges = {{1, 2}, {3, 3}, {45, 45}, {48, 48}, {51, 57}, {61, 67}, {71, 77}, {80, 82}, {85, 86}, {95, 99}};
        String[] keys = {"wmo_fair", "wmo_cloudy", "wmo_fog", "wmo_fog", "wmo_drizzle", "wmo_rain", "wmo_snow",
                "wmo_showers", "wmo_snow_showers", "wmo_thunder"};
        for (int i = 0; i < ranges.length; i++) {
            for (int code = ranges[i][0]; code <= ranges[i][1]; code++) assertEquals(keys[i], WeatherWords.key(code));
        }
        assertEquals("wmo_unknown", WeatherWords.key(-1));
        assertEquals("wmo_unknown", WeatherWords.key(46));
        assertEquals("wmo_unknown", WeatherWords.key(100));
    }
}
