package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

/**
 * A world's continuous motion over its scene (WorldScene): Hogwarts' candles and stars, Marvel's reactor, streak and
 * beacon, Star Wars' hyperspace streaks and stars. One transparent full-screen view per world, built like
 * PetalShowerView: no focus, clicks or accessibility, nothing allocated per frame. It ticks every 33 ms (30 fps at
 * most) and invalidates the regions that move. Clean has none; Seasons' follows the day's look (SeasonsOverlay).
 *
 * <p>No hardware layer: the overlay runs the whole time Home shows a world, and a full-screen layer would hold ~8 MB
 * and be redrawn in full on every tick anyway (from API 21 on a hardware-accelerated view ignores the dirty rect of
 * invalidate(Rect), so the regions only say what moves). Without a layer the tick re-records this view's display
 * list, which is a handful of draw calls.
 *
 * <p>Shapes come in the mockup's 192 x 108 units (vier-welten-v3.html, as launcher/worlds/&lt;id&gt;/make_scene.py
 * draws the pictures) and are mapped the way the scene's CENTER_CROP maps the picture, so they sit on it.
 */
abstract class WorldOverlay extends View {
    /** pulse(): Marvel's reactor kicks once (Task 8); the other worlds ignore it. */
    static final int REACTOR_KICK = 1;
    static final long FRAME_MS = 33;
    static final double TAU = 2 * Math.PI;

    /** The regions that move, in view pixels; subclasses set them in layout(). Empty ones are skipped. */
    protected final Rect[] regions = {new Rect(), new Rect(), new Rect(), new Rect()};
    /** Pixels per mockup unit and the picture's top-left on screen, as CENTER_CROP places it. */
    protected float unit = 1f, originX, originY;
    protected boolean day;
    private boolean running;
    private final Runnable frame = this::frame;

    WorldOverlay(Context context, boolean day) {
        super(context);
        this.day = day;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    /**
     * The overlay for this world; null for Clean, which has none. Seasons' follows {@code today}'s look
     * (SeasonsOverlay) and needs it: without one it has none either.
     */
    static WorldOverlay create(Context context, World w, boolean day, SeasonsToday today) {
        if (!hasOverlay(w, today != null)) return null;
        switch (w) {
            case HOGWARTS: return new HogwartsOverlay(context, day);
            case MARVEL: return new MarvelOverlay(context, day);
            case STARWARS: return new StarWarsOverlay(context, day);
            case SUPERNATURAL: return new SupernaturalOverlay(context, day);
            case SEASONS: return new SeasonsOverlay(context, day, today);
            default: return null;
        }
    }

    /** Whether {@link #create} makes one: every world but Clean, Seasons only with its day's look. */
    static boolean hasOverlay(World w, boolean seasonsToday) {
        switch (w) {
            case HOGWARTS:
            case MARVEL:
            case STARWARS:
            case SUPERNATURAL:
                return true;
            case SEASONS:
                return seasonsToday;
            default:
                return false;
        }
    }

    /** Moves only in a world, with animations on and reduced motion off, while Home is resumed and no screensaver shows. */
    static boolean shouldRun(World w, boolean animations, boolean reducedMotion, boolean resumed, boolean screensaver) {
        return !w.isClean() && animations && !reducedMotion && resumed && !screensaver;
    }

    /** Day or night as the scene shows it; the overlay draws its day or night set from the next frame on. */
    void setDay(boolean day) {
        if (this.day == day) return;
        this.day = day;
        if (getWidth() > 0) layout(getWidth(), getHeight());
        invalidate();
    }

    /**
     * The scene's mood as it shows it (WorldScene.showsMood). Only Middle-earth tells its morning from its day and
     * overrides this; every other overlay draws its day set for a morning ({@link #setDay}).
     */
    void setMood(WorldScene.Mood mood) {
        setDay(mood.isDay());
    }

    /** Whether it draws its day set (as the scene shows it), else its night set. */
    boolean isDay() {
        return day;
    }

    void start() {
        if (running) return;
        running = true;
        onStart(SystemClock.uptimeMillis());
        frame();
    }

    void stop() {
        if (!running) return;
        running = false;
        removeCallbacks(frame);
    }

    boolean isRunning() {
        return running;
    }

    /** A one-off accent on top of the motion (REACTOR_KICK); ignored by default. */
    void pulse(int what) {
    }

    /** The world's lights (Hogwarts' candles) stay dark until {@link #ignite}: the transition into it (Task 10). */
    void holdLights() {
    }

    /** The lights come on one by one from this uptime on (WorldTransition); 0 or less: all on at once. */
    void ignite(long at) {
    }

    /**
     * Nothing moves: the ticks stop (the overlay still counts as running) until {@link #wake}. Only Seasons on a still
     * look; the themed worlds always move.
     */
    protected boolean idle() {
        return false;
    }

    /** What moves has changed (Seasons' new look): ticks again if running and not idle, without a second loop. */
    protected final void wake() {
        if (!running) return;
        removeCallbacks(frame);
        frame();
    }

    private void frame() {
        if (!running || idle()) return;
        onFrame(SystemClock.uptimeMillis());
        for (Rect r : regions) {
            if (!r.isEmpty()) invalidate(r);
        }
        postDelayed(frame, FRAME_MS);
    }

    /** Called when the motion starts; the time is SystemClock.uptimeMillis(). */
    protected void onStart(long now) {
    }

    /** Called on every tick before the regions are invalidated. */
    protected void onFrame(long now) {
    }

    /** Lays the shapes out for this size and the current day or night, and sets {@link #regions}. */
    protected abstract void layout(int w, int h);

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        unit = Math.max(w / 192f, h / 108f);
        originX = (w - 192 * unit) / 2f;
        originY = (h - 108 * unit) / 2f;
        layout(w, h);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    /** A mockup x or y in view pixels. */
    protected final float ux(float x) {
        return originX + x * unit;
    }

    protected final float uy(float y) {
        return originY + y * unit;
    }

    /** Where {@code now} is in a cycle of {@code periodMs} shifted by {@code offsetMs}: 0 up to (not) 1. */
    static float phase(long now, long periodMs, long offsetMs) {
        long t = (now + offsetMs) % periodMs;
        if (t < 0) t += periodMs;
        return t / (float) periodMs;
    }

    /** A breath over one cycle: 0 at the start, 1 halfway, 0 again at the end, eased like a sine. */
    static float wave(float phase) {
        return 0.5f - 0.5f * (float) Math.cos(TAU * phase);
    }

    /** Sets {@code r} to the bounds around (cx, cy) with this half width and half height, rounded outwards. */
    protected static void around(Rect r, float cx, float cy, float halfW, float halfH) {
        r.set((int) Math.floor(cx - halfW), (int) Math.floor(cy - halfH), (int) Math.ceil(cx + halfW),
                (int) Math.ceil(cy + halfH));
    }
}
