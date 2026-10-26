package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

/** Plain JVM tests for Jahreskreis' scene: which picture a look shows, when it reloads, its dimming, motion and accent. */
public class SeasonsSceneTest {
    @Test public void lookChangesAtMidnight() {
        LocalDate d1 = LocalDate.of(2026, 12, 5), d2 = d1.plusDays(1);
        assertTrue(WorldScene.needsReload(d1, Look.NIKOLAUS_EVE, d2, Look.NIKOLAUS, true, true));
        assertFalse(WorldScene.needsReload(d1, Look.ADVENT, d1, Look.ADVENT, false, false));
    }

    @Test public void onlySeasonsHaveDayAndNightPictures() {
        assertEquals("world_seasons_winter_night", WorldScene.drawableName(Look.WINTER, false));
        assertEquals("world_seasons_winter_day", WorldScene.drawableName(Look.WINTER, true));
        assertEquals("world_seasons_advent", WorldScene.drawableName(Look.ADVENT, false));
        assertEquals("world_seasons_advent", WorldScene.drawableName(Look.ADVENT, true));
        assertEquals("world_seasons_st_martin", WorldScene.drawableName(Look.ST_MARTIN, true));
    }

    /** Nothing on screen yet: the first look always loads. */
    @Test public void theFirstLookLoads() {
        assertTrue(WorldScene.needsReload(null, null, LocalDate.of(2026, 12, 7), Look.ADVENT, null, true));
    }

    /** A mood change reloads only where it shows: a season's other picture, a bright look's dimming. */
    @Test public void moodChangeReloadsOnlyWhereItShows() {
        LocalDate d = LocalDate.of(2026, 4, 5);
        assertTrue(WorldScene.needsReload(d, Look.WINTER, d, Look.WINTER, true, false));
        assertTrue(WorldScene.needsReload(d, Look.EASTER, d, Look.EASTER, true, false));
        assertFalse(WorldScene.needsReload(d, Look.HALLOWEEN, d, Look.HALLOWEEN, true, false));
        assertFalse(WorldScene.needsReload(d, Look.WINTER, d, Look.WINTER, false, false));
    }

    /** Advent on its next day keeps its picture (the candles are the overlay's); no decode at midnight. */
    @Test public void theSameLookOnANewDayKeepsItsPicture() {
        LocalDate d1 = LocalDate.of(2026, 12, 7);
        assertFalse(WorldScene.needsReload(d1, Look.ADVENT, d1.plusDays(1), Look.ADVENT, true, true));
    }

    /** OLED rule: bright looks are dimmed by 55 % at night, on the scene only; dark looks and the day are not. */
    @Test public void brightLooksDimAtNight() {
        assertEquals(0.55f, WorldScene.dimFor(Look.EASTER, false), 1e-6f);
        assertEquals(0.55f, WorldScene.dimFor(Look.SPRING, false), 1e-6f);
        assertEquals(0.55f, WorldScene.dimFor(Look.VALENTINE, false), 1e-6f);
        assertEquals(0f, WorldScene.dimFor(Look.EASTER, true), 1e-6f);
        assertEquals(0f, WorldScene.dimFor(Look.WINTER, false), 1e-6f);
        assertEquals(0f, WorldScene.dimFor(Look.HALLOWEEN, false), 1e-6f);
        assertEquals(0f, WorldScene.dimFor(null, false), 1e-6f);
    }

    /** By name, so later pictures need no code; a missing one is 0 and the background colour stays. */
    @Test public void picturesAreFoundByName() {
        assertEquals(42, WorldScene.resFor(World.SEASONS, true, Look.WINTER,
                name -> "world_seasons_winter_day".equals(name) ? 42 : 0));
        assertEquals(0, WorldScene.resFor(World.SEASONS, false, Look.WINTER,
                name -> "world_seasons_winter_day".equals(name) ? 42 : 0));
        assertEquals(0, WorldScene.resFor(World.SEASONS, true, null, name -> 42));
        // The other worlds keep their fixed pictures and never look anything up.
        assertEquals(R.drawable.world_hogwarts_day, WorldScene.resFor(World.HOGWARTS, true, Look.WINTER, name -> 7));
        assertEquals(0, WorldScene.resFor(World.CLEAN, true, Look.WINTER, name -> 7));
    }

    @Test public void everyLookHasAMotionAndTheEmptyOneDoesNotTick() {
        SeasonsToday today = SeasonsToday.resolve(LocalDate.of(2026, 12, 7), List.of(), false, false);
        for (Look look : Look.values()) assertNotNull(look.id, LookMotion.forLook(look, today));
        assertFalse(SeasonsOverlay.ticks(LookMotion.NONE));
        assertFalse(SeasonsOverlay.ticks(null));
        assertTrue(SeasonsOverlay.ticks(new LookMotion() {
            @Override public void layout(SeasonsOverlay o, int w, int h) { }
            @Override public void frame(long now) { }
            @Override public void draw(Canvas c) { }
        }));
    }

    /** Seasons takes the day's accent; every other world keeps its own, so Clean stays as it is. */
    @Test public void accentFollowsTheLookInSeasonsOnly() {
        assertEquals(Look.HALLOWEEN.accent, World.SEASONS.accentFor(Look.HALLOWEEN));
        assertEquals(Look.BIRTHDAY.accent, World.SEASONS.accentFor(Look.BIRTHDAY));
        assertEquals(World.SEASONS.accent, World.SEASONS.accentFor(null));
        for (World w : World.values()) {
            if (w == World.SEASONS) continue;
            assertEquals(w.id, w.accent, w.accentFor(Look.HALLOWEEN));
            assertEquals(w.id, w.accent, w.accentFor(null));
        }
        assertEquals(0, World.CLEAN.accentFor(Look.CHRISTMAS));
    }
}
