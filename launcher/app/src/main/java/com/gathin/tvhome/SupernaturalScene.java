package com.gathin.tvhome;

/**
 * Where Supernatural's two pictures (world_supernatural_day/night, launcher/worlds/supernatural/make_scene.py) paint
 * the things that move or light up over them, so its overlay, its launch motion and the logo picture (spec § 4.4) meet
 * them exactly. All in the scenes' 192 x 108 units (multiply by width / 192 and height / 108), the same numbers
 * make_scene.py names in capitals; tests/test_supernatural_scene.py keeps the two equal. Rectangles are
 * {x, y, width, height}, points {x, y}.
 */
final class SupernaturalScene {
    /** The units across and down (common.py). */
    static final float UNITS_W = 192f, UNITS_H = 108f;
    /** The horizon (make_scene.py: HORIZON). */
    static final float HORIZON = 56.2f;
    /**
     * The lettering "SUPERNATURAL" over the motel (LETTERING): its height is 6 % of the screen, the logo picture's
     * height in its place; it lies below the pills ({@link PersonalMotions#PILLS}) and above the motel's roof.
     */
    static final float[] LETTERING = {129.0f, 35.6f, 47.0f, 6.48f};
    /**
     * The sky behind the lettering at its corners (top left, top right, bottom left, bottom right) as painted, by day
     * and by night (LETTERING_SKY_DAY/NIGHT): drawn over the lettering when the logo picture takes its place.
     */
    static final int[] LETTERING_SKY_DAY = {0xFFB77141, 0xFFA7673C, 0xFFCD864A, 0xFFC38047};
    static final int[] LETTERING_SKY_NIGHT = {0xFF0E131B, 0xFF0D1219, 0xFF10151E, 0xFF0F141D};

    /** The car, facing left (CAR): its front's x, the ground its tyres stand on, its length and its height. */
    static final float[] CAR = {111.4f, 75.9f, 57.6f, 9.6f};
    /** The lamp in the car's front (CAR_HEADLIGHT): the headlights flare up from here at an app start. */
    static final float[] CAR_HEADLIGHT = {111.9f, 71.0f};
    /** The tail light at the back (CAR_TAIL_LIGHT), glowing red at night. */
    static final float[] CAR_TAIL_LIGHT = {167.9f, 70.3f, 1.0f, 1.9f};
    /** The sun's glint on the roof by day (CAR_ROOF_GLINT). */
    static final float[] CAR_ROOF_GLINT = {142.5f, 66.6f};

    /** The star on the road (STAR): centre and half width and height of its circle in perspective. */
    static final float[] STAR = {90.2f, 73.0f, 10.6f, 2.4f};
    /** How much further than the circle its short rays reach (STAR_RAYS). */
    static final float STAR_RAYS = 1.22f;

    /** The warding circle on the motel's end wall (WARD): centre and radius. */
    static final float[] WARD = {179.6f, 51.4f, 4.4f};
    /** The small marks over the doors (DOOR_MARKS), {x, y} each, and their radius (DOOR_MARK_R). */
    static final float[][] DOOR_MARKS = {
            {128.1f, 47.6f}, {136.7f, 47.6f}, {145.3f, 47.6f}, {153.9f, 47.6f}, {162.5f, 47.6f}, {171.1f, 47.6f}};
    static final float DOOR_MARK_R = 0.95f;

    /** The motel's sign board (SIGN) and its neon: "MOTEL" (NEON_MOTEL) and "VACANCY" (NEON_VACANCY). */
    static final float[] SIGN = {107.0f, 10.4f, 24.0f, 11.2f};
    static final float[] NEON_MOTEL = {109.0f, 11.6f, 20.0f, 5.4f};
    static final float[] NEON_VACANCY = {111.0f, 17.6f, 16.0f, 2.8f};
    /**
     * The centres of MOTEL's five letters across (NEON_MOTEL_X) and their ink's half widths (NEON_MOTEL_HALF), as
     * painted (make_scene.py motel_letter_spans): the overlay lets one of them fail. The ink runs down NEON_MOTEL.
     */
    static final float[] NEON_MOTEL_X = {112.8f, 116.975f, 120.275f, 123.275f, 126.175f};
    static final float[] NEON_MOTEL_HALF = {1.9f, 1.575f, 1.375f, 1.175f, 1.175f};
    /** The sign's pole (SIGN_POLE): its x and its foot, and its half width (SIGN_POLE_HALF). */
    static final float[] SIGN_POLE = {121.0f, 59.6f};
    static final float SIGN_POLE_HALF = 0.45f;
    /**
     * The motel (MOTEL): left, right, the roof's top, the eaves, the ground before the doors; the roof overhangs both
     * sides by ROOF_OVERHANG.
     */
    static final float[] MOTEL = {119.0f, 186.0f, 43.4f, 44.8f, 58.6f};
    static final float ROOF_OVERHANG = 1.6f;
    /**
     * The hills' ridge (HILLS): HORIZON - 108 (base + a1 sin(i f1) + a2 sin(i f2)) with i = x / 192 * cols; see
     * {@link #hillTop}.
     */
    static final float[] HILLS = {0.02f, 0.02f, 0.7f, 0.015f, 2.3f, 40.0f};

    /** The sun on the horizon by day (SUN): centre and radius; the rays fan up from its centre. */
    static final float[] SUN = {67.2f, 54.0f, 6.5f};
    /** The rays' angles in radians from straight up, negative to the left (RAYS); half width and length. */
    static final float[] RAYS = {-0.84f, -0.56f, -0.28f, 0.0f, 0.28f, 0.56f, 0.84f};
    static final float RAY_HALF = 0.05f, RAY_LEN = 75.6f;

    /** The cracks glowing in the ground on the left at night (CRACKS), each a polyline {x0, y0, x1, y1, …}. */
    static final float[][] CRACKS = {
            {6.0f, 57.4f, 9.2f, 57.9f, 11.0f, 57.5f, 14.6f, 58.6f, 17.1f, 58.2f, 19.4f, 59.3f, 23.8f, 59.0f,
                    26.0f, 60.2f, 30.5f, 60.6f, 33.2f, 60.1f, 36.8f, 61.4f},
            {26.0f, 60.2f, 27.4f, 61.6f, 26.6f, 62.5f, 28.3f, 64.0f},
            {33.0f, 57.2f, 36.4f, 57.7f, 38.1f, 57.3f, 41.9f, 58.4f, 44.7f, 58.0f, 47.0f, 59.0f, 51.6f, 58.8f,
                    54.2f, 59.9f, 58.9f, 60.0f, 62.0f, 61.2f},
            {44.7f, 58.0f, 45.5f, 56.9f, 47.9f, 56.7f},
            {51.6f, 58.8f, 52.8f, 60.4f, 51.9f, 61.6f, 53.6f, 62.8f, 52.9f, 64.1f},
            {58.9f, 60.0f, 61.8f, 59.4f, 64.4f, 59.8f, 67.6f, 59.1f, 70.8f, 59.6f},
    };
    /** The cracks' width in their middle (CRACK_W) and the area round them with their glow (CRACK_AREA). */
    static final float CRACK_W = 0.26f;
    static final float[] CRACK_AREA = {4.0f, 54.0f, 70.0f, 12.0f};

    private SupernaturalScene() {
    }

    /**
     * Where the land begins at x (units): the hills' ridge as make_scene.py hills_line paints it, or the horizon where
     * the ridge dips below it.
     */
    static float hillTop(float x) {
        double i = x / UNITS_W * HILLS[5];
        double ridge = HORIZON
                - UNITS_H * (HILLS[0] + HILLS[1] * Math.sin(i * HILLS[2]) + HILLS[3] * Math.sin(i * HILLS[4]));
        return (float) Math.min(HORIZON, ridge);
    }

    /** A rectangle {x, y, width, height} as a box {left, top, right, bottom}, as PersonalMotions.underPills takes. */
    static float[] box(float[] rect) {
        return new float[]{rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3]};
    }
}
