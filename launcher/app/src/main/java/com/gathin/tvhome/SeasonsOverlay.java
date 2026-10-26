package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;

/**
 * Jahreskreis' motion over its scene: hands the overlay's layout, ticks and drawing to the one {@link LookMotion} of
 * today's look (LookMotion.forLook). A look without motion ({@link LookMotion#NONE}) draws nothing and does not tick,
 * so a still look costs no 30 fps loop. When the look (or the day, for Advent's candles) changes, {@link #setToday}
 * swaps the motion in place.
 */
final class SeasonsOverlay extends WorldOverlay {
    private LookMotion motion = LookMotion.NONE;

    SeasonsOverlay(Context context, boolean day, SeasonsToday today) {
        super(context, day);
        setToday(today);
    }

    /** Whether this motion needs the overlay's ticks: anything but none. */
    static boolean ticks(LookMotion motion) {
        return motion != null && motion != LookMotion.NONE;
    }

    /** Today's look and its details; the motion is built anew for it and starts ticking if it moves. */
    void setToday(SeasonsToday today) {
        LookMotion next = today != null ? LookMotion.forLook(today.look, today) : LookMotion.NONE;
        motion = next != null ? next : LookMotion.NONE;
        if (getWidth() > 0) layout(getWidth(), getHeight());
        else clearRegions();
        invalidate();
        wake();
    }

    /** Whether it is day in the scene: the motions draw their day or night set. */
    boolean isDay() {
        return day;
    }

    @Override
    protected boolean idle() {
        return !ticks(motion);
    }

    @Override
    protected void layout(int w, int h) {
        clearRegions();
        motion.layout(this, w, h);
    }

    @Override
    protected void onFrame(long now) {
        motion.frame(now);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        motion.draw(canvas);
    }

    private void clearRegions() {
        for (Rect r : regions) r.setEmpty();
    }
}
