package com.gathin.tvhome;

import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.List;
import java.util.Random;

/**
 * OLED burn-in guard (config.xml oled_shift, spec § 4.10a): once a minute Home's header, pills, shelf, grid with its
 * dock band and the focus frame move together by a random −3…+3 in X and Y, a pure translation, 400 ms soft. It
 * starts at 0 and only the first minute tick moves it, so a screenshot right after a cold start shows Home unmoved.
 * The views given here are moved by nothing else, so the translation is theirs alone (MainActivity wraps the moving
 * part of Home in one layer for exactly that).
 */
final class OledShift {
    /** The farthest the views move from their place, per axis, before density. */
    static final int MAX = 3;
    static final long MOVE_MS = 400;

    private final List<View> views;
    private final Random rnd;
    private final boolean enabled;
    private final boolean animated;
    private int[] current = {0, 0};
    /** Made on the first animated move: the JVM tests never touch an Android class. */
    private DecelerateInterpolator interpolator;

    OledShift(List<View> views, Random rnd, boolean enabled) {
        this(views, rnd, enabled, true);
    }

    /** {@code animated} false (reduced motion, animations off): each move is a jump; the panel is still spared. */
    OledShift(List<View> views, Random rnd, boolean enabled, boolean animated) {
        this.views = views;
        this.rnd = rnd;
        this.enabled = enabled;
        this.animated = animated;
    }

    /** A new point within ±MAX on both axes, never the previous one (a "move" that stays put spares nothing). */
    static int[] nextOffset(Random rnd, int[] previous) {
        int x, y;
        do {
            x = rnd.nextInt(2 * MAX + 1) - MAX;
            y = rnd.nextInt(2 * MAX + 1) - MAX;
        } while (x == previous[0] && y == previous[1]);
        return new int[]{x, y};
    }

    /** The minute's move; nothing while oled_shift is off. */
    void next() {
        if (!enabled) return;
        current = nextOffset(rnd, current);
        if (views.isEmpty()) return;
        float density = views.get(0).getResources().getDisplayMetrics().density;
        float x = current[0] * density;
        float y = current[1] * density;
        for (View v : views) {
            if (!animated) {
                v.setTranslationX(x);
                v.setTranslationY(y);
                continue;
            }
            if (interpolator == null) interpolator = new DecelerateInterpolator();
            v.animate().translationX(x).translationY(y).setDuration(MOVE_MS).setInterpolator(interpolator).start();
        }
    }

    /** Back to 0 at once (onPause): Home comes back where it was laid out, the next move a minute after. */
    void reset() {
        current = new int[]{0, 0};
        for (View v : views) {
            v.animate().cancel();
            v.setTranslationX(0f);
            v.setTranslationY(0f);
        }
    }

    /** The offset in steps of one (before density), {0, 0} at rest. */
    int[] current() {
        return current.clone();
    }
}
