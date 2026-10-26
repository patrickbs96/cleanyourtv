package com.gathin.tvhome;

/**
 * Where Middle-earth's three pictures (world_middleearth_morning/day/night, launcher/worlds/middleearth/make_scene.py)
 * paint the things that move or light up over them, so the overlay (smoke, mist, embers, the eye and its cone of
 * light), the walking company and the logo picture (spec § 4.4) meet them exactly. All in the scenes' 192 x 108 units
 * (multiply by width / 192 and height / 108), the same numbers make_scene.py names in capitals;
 * tests/test_middleearth_scene.py keeps the two equal. Rectangles are {x, y, width, height}, points {x, y}.
 */
final class MiddleEarthScene {
    /** The units across and down (common.py). */
    static final float UNITS_W = 192f, UNITS_H = 108f;
    /** The dock's top edge (DOCK, y 766 of 1080): the company walks on it. */
    static final float DOCK = 76.6f;
    /**
     * The logo picture's largest box (LOGO): centred at x 57 %, y 11 % of the screen, at most 18 % wide and 8 % high;
     * and the area round it that all three scenes keep free, sky and clouds only (LOGO_FREE: x 48 to 66 %, y 4 to
     * 18 %).
     */
    static final float[] LOGO = {92.16f, 7.56f, 34.56f, 8.64f};
    static final float[] LOGO_FREE = {92.16f, 4.32f, 34.56f, 15.12f};

    /**
     * The header on the top left (greeting, clock, date, weather) as {left, top, right, bottom} (HEADER): all three
     * scenes show only sky there, and the crater's sparks stop below its bottom.
     */
    static final float[] HEADER = {0.0f, 0.0f, 76.0f, 34.0f};

    /** Morning: the top of the chimney the smoke rises from (CHIMNEY_TOP). */
    static final float[] CHIMNEY_TOP = {171.0f, 45.6f};
    /** Morning and day: the band the mist drifts through, {top, bottom} (MIST_MORNING, MIST_DAY). */
    static final float[] MIST_MORNING = {56.0f, 71.0f};
    static final float[] MIST_DAY = {57.0f, 71.0f};

    /** Night: the crater's rim the embers and sparks rise from, {centre x, y, half width} (CRATER). */
    static final float[] CRATER = {46.0f, 40.0f, 5.5f};
    /** Night: the three lava channels down the volcano's flanks (LAVA), each {x, y, x, y, …} from the rim down. */
    static final float[][] LAVA = {
            {44.0f, 40.6f, 42.6f, 45.0f, 40.4f, 50.0f, 37.0f, 55.5f, 33.6f, 60.5f, 29.4f, 66.0f},
            {48.2f, 40.6f, 50.0f, 45.4f, 53.4f, 50.6f, 56.4f, 55.8f, 60.6f, 61.0f, 64.4f, 66.4f},
            {46.0f, 40.8f, 45.6f, 46.0f, 46.8f, 51.4f, 45.8f, 57.0f, 47.0f, 62.4f}};
    /** The channels' width in their middle (LAVA_W); they taper to their ends. */
    static final float LAVA_W = 0.34f;

    /** Night: the dark tower's top, its crown's highest merlon (TOWER_TOP). */
    static final float[] TOWER_TOP = {146.0f, 35.0f};
    /** Night: the eye set into the crown's wall (EYE), {centre x, y, half width, half height}, dim at rest. */
    static final float[] EYE = {146.0f, 39.0f, 2.9f, 1.35f};
    /** Night: where the sweeping cone of light starts (CONE_ORIGIN), the eye's centre. */
    static final float[] CONE_ORIGIN = {146.0f, 39.0f};

    /** Each scene's darkest tone, the ground in front (DARKEST_*): the company walks in it. */
    static final int DARKEST_MORNING = 0xFF141C10;
    static final int DARKEST_DAY = 0xFF0E161C;
    static final int DARKEST_NIGHT = 0xFF050101;

    private MiddleEarthScene() {}

    /** The darkest tone for a mood: the company's colour. */
    static int darkest(WorldScene.Mood mood) {
        switch (mood) {
            case MORNING: return DARKEST_MORNING;
            case DAY: return DARKEST_DAY;
            default: return DARKEST_NIGHT;
        }
    }
}
