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
}
