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

    @Test
    public void everyWorldHasItsOwnDayAndNightPicture() {
        assertEquals(R.drawable.world_hogwarts_day, WorldScene.resFor(World.HOGWARTS, true));
        assertEquals(R.drawable.world_hogwarts_night, WorldScene.resFor(World.HOGWARTS, false));
        assertEquals(R.drawable.world_marvel_day, WorldScene.resFor(World.MARVEL, true));
        assertEquals(R.drawable.world_marvel_night, WorldScene.resFor(World.MARVEL, false));
        assertEquals(R.drawable.world_starwars_day, WorldScene.resFor(World.STARWARS, true));
        assertEquals(R.drawable.world_starwars_night, WorldScene.resFor(World.STARWARS, false));
    }

    @Test
    public void forcedMoodWinsOverTheSun() {
        assertTrue(WorldScene.isDay(WorldScene.MOOD_DAY, 2 * 60, 7 * 60, 18 * 60 + 30));
        assertFalse(WorldScene.isDay(WorldScene.MOOD_NIGHT, 12 * 60, 7 * 60, 18 * 60 + 30));
        assertTrue(WorldScene.isDay(WorldScene.MOOD_AUTO, 12 * 60, 7 * 60, 18 * 60 + 30));
        assertFalse(WorldScene.isDay("unknown", 2 * 60, 7 * 60, 18 * 60 + 30));
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
