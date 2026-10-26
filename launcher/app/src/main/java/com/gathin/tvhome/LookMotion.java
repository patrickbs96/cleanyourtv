package com.gathin.tvhome;

import android.graphics.Canvas;

/**
 * One look's motion over Jahreskreis' scene (spec § 4.5): snow, petals, fireworks, confetti … drawn by
 * {@link SeasonsOverlay}, which holds exactly one for today's look. Built like the other worlds' overlays: shapes in the
 * mockup's 192 x 108 units mapped by the overlay ({@code o.ux}, {@code o.uy}, {@code o.unit}), the moving regions set
 * in {@link #layout} into {@code o.regions}, nothing allocated per frame.
 */
interface LookMotion {
    /** Lays the shapes out for this size and the overlay's day or night, and sets the overlay's regions. */
    void layout(SeasonsOverlay o, int w, int h);

    /** Advances the motion to {@code now} (SystemClock.uptimeMillis()), once per tick before the regions redraw. */
    void frame(long now);

    void draw(Canvas c);

    /** No motion: the overlay draws nothing and does not tick (SeasonsOverlay.ticks). */
    LookMotion NONE = new LookMotion() {
        @Override public void layout(SeasonsOverlay o, int w, int h) {
        }

        @Override public void frame(long now) {
        }

        @Override public void draw(Canvas c) {
        }
    };

    /** The motion for this look on this day (Advent's candles, the personal day's names); never null. */
    static LookMotion forLook(Look look, SeasonsToday today) {
        // The four seasons' (SeasonMotions); the occasions' and personal days' arrive with their pictures (Jahreskreis
        // Tasks 6 to 8), until then those looks are still.
        LookMotion season = SeasonMotions.of(look);
        return season != null ? season : NONE;
    }
}
