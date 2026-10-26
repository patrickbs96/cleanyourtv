package com.gathin.tvhome;

import java.util.Random;

/**
 * Middle-earth's walking company (spec § 4.2) without the drawing: ten figures on the dock's top edge, walking left to
 * right once across in about 6 minutes, then a pause of 2 to 5 minutes (random, its seed fixed for the day), then in
 * from the left again. Behind the focused app's name (its extent plus 4 % of the width) the company shows at a
 * 60 %, softened over 3 % of the width, and at full strength everywhere else; MiddleEarthOverlay draws it with the
 * silhouettes of CompanySilhouettes.
 *
 * <p>Its clock is its own: it walks only while the overlay runs (start, advance on every tick, stop), so after
 * onPause, a screensaver or reduced motion it goes on where it stood. One late frame moves it on by
 * {@link #MAX_FRAME_MS} at most, it never jumps. Times are SystemClock.uptimeMillis().
 */
final class CompanyMotion {
    /** Once across, and the pause after it (spec § 4.2). */
    static final long CROSS_MS = 6 * 60_000L;
    static final long PAUSE_MIN_MS = 2 * 60_000L, PAUSE_MAX_MS = 5 * 60_000L;
    /** One stride of the legs, the walked time a step picture lasts times CompanySilhouettes.FRAMES. */
    static final long STEP_MS = 2400;
    /** The most one tick moves the company on, however late it comes. */
    static final long MAX_FRAME_MS = 200;
    /** Behind the name: 60 % (spec 25 %, too faint on the TV: Patrick, TV-Abnahme 27.10.); the name's margin and the soft edge, as shares of the width. */
    static final float DIM = 0.6f, NAME_MARGIN = 0.04f, NAME_FADE = 0.03f;
    /** A new name's dimmed area (or none) comes in over this long (blendAlpha). */
    static final long NAME_BLEND_MS = 200;

    /** The figures' kinds (the mockup's figure()). */
    static final int SHIELD = 0, HORSE = 1, SMALL = 2, MAN = 3, DWARF = 4, ELF = 5, WIZARD = 6, KINDS = 7;
    /**
     * The company from the back to the front (the mockup's COMPANY2): the warrior with the round shield on his back,
     * the horse with its packs led by a small one, the warrior with a sword, two small ones, the dwarf with his axe,
     * the slender one with bow and quiver, a small one and ahead of all the tall wanderer with hat and staff.
     */
    static final int[] COMPANY = {SHIELD, HORSE, SMALL, MAN, SMALL, SMALL, DWARF, ELF, SMALL, WIZARD};
    /** The room behind a figure of each kind, in figure heights (the mockup's GAPS). */
    static final float[] GAP = {0.34f, 0.55f, 0.26f, 0.32f, 0.30f, 0.30f, 0.36f};
    /** How far each figure walks behind the wizard at the head, in figure heights (COMPANY's order). */
    static final float[] OFFSETS = offsets();
    /** What a figure reaches in front of its feet and behind them, in figure heights, with its soft edge. */
    static final float FRONT = 0.6f, BACK = 0.6f;

    /** The day this company walks (its seed), as LocalDate.toEpochDay(). */
    final long day;
    private final Random random;
    /** Time into this cycle (across, then the pause), this cycle's pause, and the last tick; -1: none since a stop. */
    private long elapsed, pause, last = -1;
    /** Whether its clock ever ran: until then (reduced motion, animations off) it stands in the middle. */
    private boolean walked;
    /** Who started the clock last (an overlay): only that one stops it again; null for anyone. */
    private Object owner;

    CompanyMotion(long day) {
        this.day = day;
        random = new Random(daySeed(day));
        pause = nextPause();
    }

    /** The motion starts (the overlay's onStart): it walks on from here. */
    void start(long now) {
        start(now, null);
    }

    /** The motion starts for {@code who}: only its {@link #stop(long, Object)} stops it, a late one of another not. */
    void start(long now, Object who) {
        last = now;
        walked = true;
        owner = who;
    }

    /** Every tick: walks the time since the last one, {@link #MAX_FRAME_MS} at most; the first after a stop starts. */
    void advance(long now) {
        if (last < 0) {
            last = now;
            walked = true;
            return;
        }
        long dt = Math.max(0, Math.min(MAX_FRAME_MS, now - last));
        last = now;
        elapsed += dt;
        long cycle;
        while (elapsed >= (cycle = cycleMs(CROSS_MS, pause))) {
            elapsed -= cycle;
            pause = nextPause();
        }
    }

    /** The motion stops (onPause, reduced motion, a screensaver): walked up to now, then it stands. */
    void stop(long now) {
        stop(now, owner);
    }

    /** Stopped by {@code who}: walked up to now and standing, unless another started it since (then nothing). */
    void stop(long now, Object who) {
        if (who != owner) return;
        if (last >= 0) advance(now);
        last = -1;
        owner = null;
    }

    /** Whether the clock runs (started or picked up by a tick, not stopped since). */
    boolean isRunning() {
        return last >= 0;
    }

    /** Whether the clock ever ran; until then the company stands in the middle (headX). */
    boolean hasWalked() {
        return walked;
    }

    /** Whether the company shows: standing before it ever walked, then while crossing, not in the pause. */
    boolean isVisible() {
        return !walked || progress() >= 0f;
    }

    /**
     * The wizard's x at the head on a screen {@code width} wide, figures {@code s} high (pixels): along its crossing
     * ({@link #headX(float, float, float)}), or before it ever walked with the group's middle at half the width.
     */
    float headX(float width, float s) {
        if (!walked) return width / 2f + OFFSETS[0] * s / 2f;
        return headX(Math.max(0f, progress()), width, s);
    }

    /** How far across, 0 (just coming in) up to 1 (just gone); -1 during the pause. */
    float progress() {
        return elapsed < CROSS_MS ? elapsed / (float) CROSS_MS : -1f;
    }

    /** The time walked in this crossing, for the steps. */
    long walkedMs() {
        return Math.min(elapsed, CROSS_MS);
    }

    /** This cycle's pause after the crossing. */
    long pauseMs() {
        return pause;
    }

    private long nextPause() {
        return PAUSE_MIN_MS + (long) (random.nextDouble() * (PAUSE_MAX_MS - PAUSE_MIN_MS));
    }

    // ---- pure helpers (CompanyMotionTest) -------------------------------------------------------------------------

    /** One cycle: across, then the pause. */
    static long cycleMs(long crossMs, long pauseMs) {
        return crossMs + pauseMs;
    }

    /** The pauses' seed for a day (LocalDate.toEpochDay()): the same all day, another the next. */
    static long daySeed(long epochDay) {
        return (epochDay * 0x9E3779B97F4A7C15L) ^ 0x4D45_4752_4F55_5045L;
    }

    /**
     * The company's strength at {@code x}: {@link #DIM} behind the app's name from {@code nameLeft} to
     * {@code nameRight} plus {@link #NAME_MARGIN} of {@code width} on either side, rising softly (smoothstep) to 1
     * over {@link #NAME_FADE} of the width, 1 beyond; 1 everywhere without a name (nameLeft = nameRight = -1). All in
     * the same pixels.
     */
    static float alphaAt(float x, float nameLeft, float nameRight, float width) {
        if (nameRight < 0f || nameRight <= nameLeft) return 1f;
        float margin = NAME_MARGIN * width, fade = NAME_FADE * width;
        float out = Math.max(Math.max(nameLeft - margin - x, x - nameRight - margin), 0f);
        if (out <= 0f) return DIM;
        float t = Math.min(1f, out / fade);
        return DIM + (1f - DIM) * t * t * (3f - 2f * t);
    }

    /**
     * The strength at {@code x} while the dimmed area goes over from the name {@code fromLeft}..{@code fromRight} to
     * {@code toLeft}..{@code toRight} (either -1, -1 for none): {@code t} 0 the old area, 1 the new, eased between.
     */
    static float blendAlpha(float x, float fromLeft, float fromRight, float toLeft, float toRight, float width,
            float t) {
        float to = alphaAt(x, toLeft, toRight, width);
        if (t >= 1f) return to;
        float from = alphaAt(x, fromLeft, fromRight, width);
        if (t <= 0f) return from;
        float e = t * t * (3f - 2f * t);
        return from + (to - from) * e;
    }

    /**
     * The wizard's x at the head at {@code progress} on a screen {@code width} wide, figures {@code s} high (pixels):
     * at 0 the whole company stands left of the screen, at 1 its last figure has left it on the right.
     */
    static float headX(float progress, float width, float s) {
        return -FRONT * s + progress * (width + (FRONT + OFFSETS[0] + BACK) * s);
    }

    /**
     * The step picture (0 to {@code frames} - 1) for figure {@code i} after {@code walkedMs} of walking: one stride
     * every {@link #STEP_MS}, each figure on its own beat (the mockup's i * 1.3 radians).
     */
    static int stepFrame(long walkedMs, int i, int frames) {
        int base = (int) ((walkedMs % STEP_MS) * frames / STEP_MS);
        int beat = Math.round((float) (i * 1.3 / WorldOverlay.TAU * frames));
        return (base + beat) % frames;
    }

    private static float[] offsets() {
        float[] off = new float[COMPANY.length];
        for (int i = COMPANY.length - 2; i >= 0; i--) off[i] = off[i + 1] + GAP[COMPANY[i + 1]];
        return off;
    }
}
