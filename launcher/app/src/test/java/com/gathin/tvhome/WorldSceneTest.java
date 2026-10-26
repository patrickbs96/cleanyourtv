package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Plain JVM tests for the worlds' scenes: when it is day, and which picture each world shows. */
public class WorldSceneTest {
    @Test
    public void dayRunsFromSunriseToHalfAnHourAfterSunset() {
        assertFalse(WorldScene.isDay(6 * 60 + 59, 7 * 60, 18 * 60 + 30));
        assertTrue(WorldScene.isDay(7 * 60, 7 * 60, 18 * 60 + 30));
        assertTrue(WorldScene.isDay(18 * 60 + 59, 7 * 60, 18 * 60 + 30));
        assertFalse(WorldScene.isDay(19 * 60, 7 * 60, 18 * 60 + 30));
    }

    @Test
    public void cleanHasNoScene() {
        assertEquals(0, WorldScene.resFor(World.CLEAN, true));
        assertNotEquals(0, WorldScene.resFor(World.HOGWARTS, false));
    }

    /** Seasons has no fixed picture: its pictures go by look and are found by name (SeasonsSceneTest). */
    @Test
    public void seasonsHasNoFixedPicture() {
        assertEquals(0, WorldScene.resFor(World.SEASONS, true));
        assertEquals(0, WorldScene.resFor(World.SEASONS, false));
    }

    @Test
    public void everyWorldHasItsOwnDayAndNightPicture() {
        assertEquals(R.drawable.world_hogwarts_day, WorldScene.resFor(World.HOGWARTS, true));
        assertEquals(R.drawable.world_hogwarts_night, WorldScene.resFor(World.HOGWARTS, false));
        assertEquals(R.drawable.world_marvel_day, WorldScene.resFor(World.MARVEL, true));
        assertEquals(R.drawable.world_marvel_night, WorldScene.resFor(World.MARVEL, false));
        assertEquals(R.drawable.world_starwars_day, WorldScene.resFor(World.STARWARS, true));
        assertEquals(R.drawable.world_starwars_night, WorldScene.resFor(World.STARWARS, false));
        assertEquals(R.drawable.world_supernatural_day, WorldScene.resFor(World.SUPERNATURAL, true));
        assertEquals(R.drawable.world_supernatural_night, WorldScene.resFor(World.SUPERNATURAL, false));
    }

    @Test
    public void forcedMoodWinsOverTheSun() {
        assertTrue(WorldScene.isDay(WorldScene.MOOD_DAY, 2 * 60, 7 * 60, 18 * 60 + 30));
        assertFalse(WorldScene.isDay(WorldScene.MOOD_NIGHT, 12 * 60, 7 * 60, 18 * 60 + 30));
        assertTrue(WorldScene.isDay(WorldScene.MOOD_AUTO, 12 * 60, 7 * 60, 18 * 60 + 30));
        assertFalse(WorldScene.isDay("unknown", 2 * 60, 7 * 60, 18 * 60 + 30));
    }

    private static final int SUNRISE = 7 * 60, SUNSET = 18 * 60 + 30;

    /** A sunrise after 11:00 leaves no morning (spec § 4.1, only in theory): day right from the sunrise on. */
    @Test
    public void moodSkipsMorningAfterEleven() {
        int late = 11 * 60 + 30;
        assertEquals(WorldScene.Mood.DAY, WorldScene.mood(World.MIDDLE_EARTH, WorldScene.MOOD_AUTO, 11 * 60 + 40, late,
                20 * 60));
        assertEquals(WorldScene.Mood.DAY, WorldScene.mood(World.MIDDLE_EARTH, WorldScene.MOOD_AUTO, late, late, 20 * 60));
        assertEquals(WorldScene.Mood.NIGHT, WorldScene.mood(World.MIDDLE_EARTH, WorldScene.MOOD_AUTO, 10 * 60 + 50, late,
                20 * 60));
    }

    /** Middle-earth: morning from the sunrise to 11:00, day to half an hour after sunset, night the rest. */
    @Test
    public void morningUntilEleven() {
        World w = World.MIDDLE_EARTH;
        String auto = WorldScene.MOOD_AUTO;
        assertEquals(WorldScene.Mood.NIGHT, WorldScene.mood(w, auto, SUNRISE - 1, SUNRISE, SUNSET));
        assertEquals(WorldScene.Mood.MORNING, WorldScene.mood(w, auto, SUNRISE, SUNRISE, SUNSET));   // sunrise exactly
        assertEquals(WorldScene.Mood.MORNING, WorldScene.mood(w, auto, 10 * 60 + 59, SUNRISE, SUNSET));
        assertEquals(WorldScene.Mood.DAY, WorldScene.mood(w, auto, 11 * 60, SUNRISE, SUNSET));        // 11:00 exactly
        assertEquals(WorldScene.Mood.DAY, WorldScene.mood(w, auto, SUNSET + 29, SUNRISE, SUNSET));
        assertEquals(WorldScene.Mood.NIGHT, WorldScene.mood(w, auto, SUNSET + 30, SUNRISE, SUNSET));  // sunset + 30
        assertEquals(WorldScene.Mood.NIGHT, WorldScene.mood(w, auto, 2 * 60, SUNRISE, SUNSET));
        // The other worlds know no morning: day from the sunrise, exactly as isDay has it.
        for (World other : World.values()) {
            if (other.hasMorning()) continue;
            assertEquals(other.name(), WorldScene.Mood.DAY, WorldScene.mood(other, auto, SUNRISE, SUNRISE, SUNSET));
            assertEquals(other.name(), WorldScene.Mood.DAY, WorldScene.mood(other, auto, 10 * 60, SUNRISE, SUNSET));
            assertEquals(other.name(), WorldScene.Mood.NIGHT,
                    WorldScene.mood(other, auto, SUNSET + 30, SUNRISE, SUNSET));
            for (int minute = 0; minute < 24 * 60; minute += 7) {
                assertEquals(other.name() + " " + minute, WorldScene.isDay(minute, SUNRISE, SUNSET),
                        WorldScene.mood(other, auto, minute, SUNRISE, SUNSET).isDay());
            }
        }
    }

    /** The test switch's Morning is Middle-earth's morning; a world without one shows its day. */
    @Test
    public void forcedMorningIsDayWithoutMorning() {
        for (World w : World.values()) {
            WorldScene.Mood expected = w.hasMorning() ? WorldScene.Mood.MORNING : WorldScene.Mood.DAY;
            assertEquals(w.name(), expected, WorldScene.mood(w, WorldScene.MOOD_MORNING, 2 * 60, SUNRISE, SUNSET));
            assertEquals(w.name(), expected, WorldScene.mood(w, WorldScene.MOOD_MORNING, 15 * 60, SUNRISE, SUNSET));
        }
        assertTrue(WorldScene.isDay(WorldScene.MOOD_MORNING, 2 * 60, SUNRISE, SUNSET));
        // Day and night as forced, also in Middle-earth's morning hours; anything unknown is the sun's.
        World w = World.MIDDLE_EARTH;
        assertEquals(WorldScene.Mood.DAY, WorldScene.mood(w, WorldScene.MOOD_DAY, 8 * 60, SUNRISE, SUNSET));
        assertEquals(WorldScene.Mood.NIGHT, WorldScene.mood(w, WorldScene.MOOD_NIGHT, 8 * 60, SUNRISE, SUNSET));
        assertEquals(WorldScene.Mood.MORNING, WorldScene.mood(w, "unknown", 8 * 60, SUNRISE, SUNSET));
    }

    @Test
    public void testSwitchKeepsMorning() {
        WorldScene.resetProcessForTest();
        FakePrefs prefs = new FakePrefs();
        WorldScene.forcedMood(prefs);
        WorldScene.saveForcedMood(prefs, WorldScene.MOOD_MORNING);
        assertEquals(WorldScene.MOOD_MORNING, WorldScene.forcedMood(prefs));
        assertEquals("morning", prefs.getString(WorldScene.FORCE_KEY, null));
    }

    /**
     * Middle-earth's three pictures are found by name (world_middleearth_&lt;mood&gt;, made in Task 3): until they are
     * there the lookup gives 0 and the world's plain colour stays. The other worlds keep their fixed pictures.
     */
    @Test
    public void middleEarthPicturesGoByMood() {
        assertEquals("world_middleearth_morning", WorldScene.drawableName(World.MIDDLE_EARTH, WorldScene.Mood.MORNING));
        assertEquals("world_middleearth_day", WorldScene.drawableName(World.MIDDLE_EARTH, WorldScene.Mood.DAY));
        assertEquals("world_middleearth_night", WorldScene.drawableName(World.MIDDLE_EARTH, WorldScene.Mood.NIGHT));
        java.util.function.ToIntFunction<String> found = name -> name.hashCode();
        for (WorldScene.Mood m : WorldScene.Mood.values()) {
            assertEquals(("world_middleearth_" + m.id).hashCode(),
                    WorldScene.resFor(World.MIDDLE_EARTH, m, null, found));
            assertEquals(0, WorldScene.resFor(World.MIDDLE_EARTH, m, null, name -> 0));
            assertEquals(0, WorldScene.resFor(World.MIDDLE_EARTH, m));
        }
        java.util.function.ToIntFunction<String> never = name -> { throw new AssertionError(name); };
        assertEquals(R.drawable.world_hogwarts_day, WorldScene.resFor(World.HOGWARTS, WorldScene.Mood.DAY, null, never));
        assertEquals(R.drawable.world_hogwarts_day, WorldScene.resFor(World.HOGWARTS, WorldScene.Mood.MORNING));
        assertEquals(R.drawable.world_supernatural_night, WorldScene.resFor(World.SUPERNATURAL, WorldScene.Mood.NIGHT));
        assertEquals(0, WorldScene.resFor(World.CLEAN, WorldScene.Mood.DAY));
    }

    @Test
    public void testSwitchHoldsThroughRecreatesOfThisProcess() {
        WorldScene.resetProcessForTest();
        FakePrefs prefs = new FakePrefs();
        assertEquals(WorldScene.MOOD_AUTO, WorldScene.forcedMood(prefs));   // Home's first start
        WorldScene.saveForcedMood(prefs, WorldScene.MOOD_DAY);              // Settings: Scene = Day
        assertEquals(WorldScene.MOOD_DAY, WorldScene.forcedMood(prefs));    // Home rebuilds
        assertEquals(WorldScene.MOOD_DAY, WorldScene.forcedMood(prefs));    // colour key: recreate again
        WorldScene.saveForcedMood(prefs, WorldScene.MOOD_AUTO);
        assertEquals(WorldScene.MOOD_AUTO, WorldScene.forcedMood(prefs));
        assertFalse(prefs.contains(WorldScene.FORCE_KEY));
    }

    @Test
    public void aNewProcessIsBackOnAuto() {
        FakePrefs prefs = new FakePrefs().put(WorldScene.FORCE_KEY, WorldScene.MOOD_NIGHT);
        WorldScene.resetProcessForTest();   // force-stop or reboot
        assertEquals(WorldScene.MOOD_AUTO, WorldScene.forcedMood(prefs));
        assertFalse(prefs.contains(WorldScene.FORCE_KEY));
        assertEquals(WorldScene.MOOD_AUTO, WorldScene.forcedMood(prefs));
    }

    @Test
    public void aChoiceMadeBeforeTheFirstSceneOfAProcessHolds() {
        // Settings restored first after the process died: its choice is this process's, not an old one.
        WorldScene.resetProcessForTest();
        FakePrefs prefs = new FakePrefs();
        WorldScene.saveForcedMood(prefs, WorldScene.MOOD_NIGHT);
        assertEquals(WorldScene.MOOD_NIGHT, WorldScene.forcedMood(prefs));
    }

    @Test
    public void unknownSavedMoodIsAuto() {
        WorldScene.resetProcessForTest();
        FakePrefs prefs = new FakePrefs();
        WorldScene.forcedMood(prefs);
        prefs.put(WorldScene.FORCE_KEY, "noon");
        assertEquals(WorldScene.MOOD_AUTO, WorldScene.forcedMood(prefs));
    }
}
