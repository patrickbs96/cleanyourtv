package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * The transition into a world (spec § 4.8), in two halves around recreate() (§ 4.2): the old Home plays the first
 * ({@link #leave}) and then saves the world, sets {@link #PENDING_KEY} and recreates; the new Home finds the key in
 * onCreate ({@link #consumePending}) and plays the second ({@link #enter}) instead of the tiles' entrance. Both halves
 * draw one timeline of the target world, 0 to leaveMs + enterMs, the leave half up to the cut and the enter half from
 * it, so the cover the old Home ends on is the one the new Home starts from:
 * <ul>
 * <li>Clean: 250 ms to black and 250 ms back.</li>
 * <li>Seasons: a plain cross-fade through black, 250 ms down and 350 ms up (Jahreskreis spec § 4.8, § 6).</li>
 * <li>Hogwarts: the old scene darkens in 300 ms; then the candles light one by one from the middle, 60 ms apart, and
 * the new scene comes up under them in 600 ms.</li>
 * <li>Marvel: the old scene darkens in 250 ms; then the HUD boots, the new Home drawn in line by line from the top in
 * 500 ms behind a blue scan edge (corners and pills first, the dock bar last), the reactor kicking once on the way.</li>
 * <li>Star Wars: a hyperspace jump in, like the app start but 700 ms and without the white: streaks from the middle,
 * the old scene zooms in and darkens (400 ms), the new one settles back to its size as the streaks pass (300 ms).</li>
 * <li>Supernatural (its own spec § 4.3, § 4.5): the old scene darkens in 300 ms; then, as the new scene comes up in
 * 500 ms, at night one flash lights its sky (at most 40 %, only the sky, never the screen white) with a bolt and the
 * shadow of the wings, by day the sun's rays flare up and fade into the overlay's own. Day or night is the new Home's
 * overlay's (SupernaturalOverlay draws both, so they are its own flash and rays).</li>
 * <li>Middle-earth (its own spec § 4.3): a veil of mist drifts in from the left over the old scene in 400 ms, the
 * scene dimming only a little under it, and stands dense at the cut; in the new Home's 450 ms it drifts on to the
 * right and the landscape of the mood appears behind it, from the left. Soft billows ride its edge.</li>
 * </ul>
 *
 * <p>With reduced motion or animations off it is a hard cut: {@link #leave} runs the switch at once and the new Home
 * plays nothing. A colour key while either half plays is ignored; leaving Home during the leave half switches right
 * after the pause ({@link #onPause}); an enter half whose first frame never comes ends after {@link #enterTimeoutMs}.
 * The timeline is pure and runs in JVM tests; the overlay ({@link Fx}) is built like
 * LaunchFxView: no focus, nothing allocated per frame, 30 fps, whole view invalidated for the under 0.9 s it plays.
 */
final class WorldTransition {
    /** Set to the target world's id right before recreate(); the new Home plays the enter half when it matches. */
    static final String PENDING_KEY = "world_transition_pending";
    /** Hogwarts' candles light this far apart, from the middle of the cluster out. */
    static final long IGNITE_STEP_MS = 60;
    static final long FRAME_MS = 33;
    /**
     * How dark the cover gets at the cut: Clean and Seasons go to black, the themed worlds only dim (no hard black
     * between them).
     */
    static final float CLEAN_PEAK = 1f, HOGWARTS_PEAK = 0.85f, MARVEL_PEAK = 0.9f, STARWARS_PEAK = 0.6f,
            SUPERNATURAL_PEAK = 0.85f, MIDDLE_EARTH_PEAK = 0.3f;
    /** Middle-earth: the mist's opacity where it is dense, its edge's width as a share of the screen's, its colour. */
    static final float FOG_PEAK = 0.9f, FOG_EDGE = 0.4f;
    static final int FOG_COLOR = 0xFFC9CCC3;
    /** The billows along the mist's edge: their height on screen and their place in the edge, as shares. */
    private static final float[] BILLOW_Y = {0.08f, 0.27f, 0.45f, 0.61f, 0.78f, 0.95f};
    private static final float[] BILLOW_IN_EDGE = {0.35f, 0.7f, 0.25f, 0.6f, 0.4f, 0.75f};
    /** Supernatural at night: the flash (SupernaturalOverlay.flash, 360 ms) starts this far into the enter half. */
    static final long SN_FLASH_DELAY_MS = 30;
    /** Supernatural by day: the rays flare up to this alpha in SN_RAYS_UP_MS, then fade by the half's end. */
    static final float SN_RAYS_PEAK = 0.45f;
    static final long SN_RAYS_UP_MS = 180;
    /** Star Wars: 70 streaks, each 400 ms long, starting within the first 300 ms of the 700. */
    static final int STREAKS = 70;
    static final long STREAK_MS = 400, STREAK_SPREAD_MS = 300;
    /** Marvel's reactor kicks this far into the enter half, as the scan passes the middle of the screen. */
    static final long REACTOR_KICK_MS = 250;
    /** A lit candle's flare over the cover lasts this long at most. */
    static final long FLARE_MS = 240;
    /** The enter half's grace past its length (enterTimeoutMs), as WorldLaunchMotion's timeout has one. */
    static final long ENTER_GRACE_MS = 300;

    // ---- timeline (pure) -----------------------------------------------------------------------------

    /** The first half, played by the old Home before recreate(). */
    static long leaveMs(World target) {
        switch (target) {
            case HOGWARTS:
            case SUPERNATURAL: return 300;
            case STARWARS:
            case MIDDLE_EARTH: return 400;
            default: return 250;   // Clean, Seasons and Marvel
        }
    }

    /** The second half, played by the new Home in place of the tiles' entrance. Hogwarts: 10 candles × 60 ms. */
    static long enterMs(World target) {
        switch (target) {
            case HOGWARTS: return 600;
            case MIDDLE_EARTH: return 450;
            case MARVEL:
            case SUPERNATURAL: return 500;
            case STARWARS: return 300;
            case SEASONS: return 350;
            default: return 250;
        }
    }

    /** Whether the transition plays at all; otherwise a hard cut (review focus 4). */
    static boolean shouldPlay(boolean animations, boolean reducedMotion) {
        return animations && !reducedMotion;
    }

    /** True exactly when the pending transition is the one into {@code current}. */
    static boolean consumePending(@Nullable String pending, World current) {
        return current.id.equals(pending);
    }

    /** The same from the launcher's preferences; a match is removed, so it plays once. */
    static boolean consumePending(SharedPreferences prefs, World current) {
        if (!consumePending(prefs.getString(PENDING_KEY, null), current)) return false;
        prefs.edit().remove(PENDING_KEY).commit();
        return true;
    }

    static float peak(World target) {
        switch (target) {
            case HOGWARTS: return HOGWARTS_PEAK;
            case MARVEL: return MARVEL_PEAK;
            case STARWARS: return STARWARS_PEAK;
            case SUPERNATURAL: return SUPERNATURAL_PEAK;
            case MIDDLE_EARTH: return MIDDLE_EARTH_PEAK;
            default: return CLEAN_PEAK;
        }
    }

    /** The mist's edge at {@code s} across it, 0 on its dense side to 1 on its clear side: a smoothstep from 1 to 0. */
    static float fogEdge(float s) {
        s = WorldLaunchMotion.clamp(s);
        return 1f - s * s * (3f - 2f * s);
    }

    /**
     * Where the mist's edge stands at {@code t} ms of the whole timeline, as a share of the width. In the leave half
     * the mist is dense left of it and clears over {@link #FOG_EDGE} to its right, running in from off screen until
     * all is dense at the cut; in the enter half it is clear left of the edge's band and dense right of it, and the
     * edge runs on until the mist is gone.
     */
    static float fogFront(World target, long t, boolean enterHalf) {
        long cut = leaveMs(target);
        if (!enterHalf) return -FOG_EDGE + (1f + FOG_EDGE) * WorldLaunchMotion.ease(WorldLaunchMotion.clamp(t / (float) cut));
        float p = WorldLaunchMotion.clamp((t - cut) / (float) enterMs(target));
        return (1f + FOG_EDGE) * WorldLaunchMotion.ease(p);
    }

    /**
     * Middle-earth's mist at {@code x} (a share of the width) at {@code t} ms of the whole timeline, its opacity, as the
     * half that draws it ({@code enterHalf}) has it; up to {@link #FOG_PEAK}. 0 for the others.
     */
    static float fogAlpha(World target, float x, long t, boolean enterHalf) {
        if (target != World.MIDDLE_EARTH) return 0f;
        float front = fogFront(target, t, enterHalf);
        return FOG_PEAK * (enterHalf ? fogEdge((front - x) / FOG_EDGE) : fogEdge((x - front) / FOG_EDGE));
    }

    /**
     * How strong the billows along the mist's edge are at {@code t}: they swell and settle within each half, so they
     * are gone at the start, at the cut (the mist stands dense) and at the end.
     */
    static float billowStrength(World target, long t, boolean enterHalf) {
        if (target != World.MIDDLE_EARTH) return 0f;
        long cut = leaveMs(target);
        float p = enterHalf ? WorldLaunchMotion.clamp((t - cut) / (float) enterMs(target))
                : WorldLaunchMotion.clamp(t / (float) cut);
        return 4f * p * (1f - p);
    }

    /**
     * Supernatural's flash at night at {@code t} ms of the whole timeline, 0 to 1 (the overlay's flash, which lights
     * only the sky, at most {@link SupernaturalOverlay#SKY_FLASH}): one, early in the enter half. 0 for the others.
     */
    static float flashFor(World target, long t) {
        if (target != World.SUPERNATURAL) return 0f;
        return SupernaturalOverlay.flash(t - leaveMs(target) - SN_FLASH_DELAY_MS);
    }

    /**
     * Supernatural's rays by day at {@code t} ms of the whole timeline, their alpha: up to {@link #SN_RAYS_PEAK} early
     * in the enter half, gone by its end, where the overlay's own faint rays go on. 0 for the others.
     */
    static float raysFor(World target, long t) {
        if (target != World.SUPERNATURAL) return 0f;
        long d = t - leaveMs(target), len = enterMs(target);
        if (d <= 0 || d >= len) return 0f;
        if (d < SN_RAYS_UP_MS) return SN_RAYS_PEAK * WorldLaunchMotion.ease(d / (float) SN_RAYS_UP_MS);
        return SN_RAYS_PEAK * (1f - WorldLaunchMotion.ease((d - SN_RAYS_UP_MS) / (float) (len - SN_RAYS_UP_MS)));
    }

    /** The cover's colour: black for Clean and Seasons (a fade through black), the world's own dark one otherwise. */
    static int coverColor(World target) {
        return target.isClean() || target == World.SEASONS ? 0xFF000000 : target.backgroundColor;
    }

    /** The dark cover's opacity at {@code t} ms of the whole timeline: up to the peak at the cut, down to 0 at the end. */
    static float coverAlpha(World target, long t) {
        long cut = leaveMs(target);
        if (t <= cut) return peak(target) * WorldLaunchMotion.ease(WorldLaunchMotion.clamp(t / (float) cut));
        float p = WorldLaunchMotion.clamp((t - cut) / (float) enterMs(target));
        return peak(target) * (1f - WorldLaunchMotion.ease(p));
    }

    /** Marvel's scan edge as a share of the height: 0 up to the cut, 1 at the end; the cover lies only below it. */
    static float revealLine(World target, long t) {
        if (target != World.MARVEL) return 0f;
        long cut = leaveMs(target);
        if (t <= cut) return 0f;
        return WorldLaunchMotion.ease(WorldLaunchMotion.clamp((t - cut) / (float) enterMs(target)));
    }

    /**
     * Star Wars' scene: the old one zooms in to 1.25 (ease-in), the new one settles from 1.12 to 1 (ease-out). The cut
     * belongs to the half that draws it: the old Home ends on 1.25, the new one starts on 1.12 (it used to start one
     * frame on 1.25 and jump).
     */
    static float sceneZoom(World target, long t, boolean enterHalf) {
        if (target != World.STARWARS) return 1f;
        long cut = leaveMs(target);
        if (!enterHalf) return 1f + 0.25f * WorldLaunchMotion.easeIn(WorldLaunchMotion.clamp(t / (float) cut));
        float p = WorldLaunchMotion.clamp((t - cut) / (float) enterMs(target));
        return 1f + 0.12f * (1f - WorldLaunchMotion.easeOut(p));
    }

    /** The enter half ends at the latest this long after it began, should its overlay never draw a first frame. */
    static long enterTimeoutMs(World target) {
        return enterMs(target) + ENTER_GRACE_MS;
    }

    /** When the candle of this rank lights, in ms into the enter half. */
    static long igniteAtMs(int rank) {
        return rank * IGNITE_STEP_MS;
    }

    /**
     * Each candle's place in the lighting order: by distance from the middle of all of them, nearest first, equally far
     * ones in their given order.
     */
    static int[] igniteRanks(float[] xs, float[] ys) {
        int n = xs.length;
        float mx = 0f, my = 0f;
        for (int i = 0; i < n; i++) {
            mx += xs[i];
            my += ys[i];
        }
        mx /= n;
        my /= n;
        float[] d = new float[n];
        for (int i = 0; i < n; i++) d[i] = (xs[i] - mx) * (xs[i] - mx) + (ys[i] - my) * (ys[i] - my);
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            int r = 0;
            for (int j = 0; j < n; j++) {
                if (d[j] < d[i] || (d[j] == d[i] && j < i)) r++;
            }
            rank[i] = r;
        }
        return rank;
    }

    // ---- playing -------------------------------------------------------------------------------------

    private final FrameLayout root;
    private final WorldScene scene;
    @Nullable private final WorldOverlay overlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tick;
    /** Ends an enter half whose clock never started (no first frame drawn), so isBusy() does not stick. */
    private final Runnable enterTimeout = this::enterTimedOut;
    /** The leave half's switch when Home is left: posted from onPause, so recreate() never runs inside it. */
    private final Runnable deferredEnd = this::switchAfterPause;
    @Nullable private Fx fx;
    private World target = World.CLEAN;
    private boolean leaving, entering, kicked;
    /** The part of the timeline this half plays, and when its first frame was drawn (-1 before). */
    private long from, to, startedAt = -1;
    @Nullable private Runnable then;

    /** {@code scene} zooms for Star Wars; {@code overlay} lights Hogwarts' candles and kicks Marvel's reactor. */
    WorldTransition(FrameLayout root, WorldScene scene, @Nullable WorldOverlay overlay) {
        this.root = root;
        this.scene = scene;
        this.overlay = overlay;
    }

    /** A half plays: a colour key now is ignored. */
    boolean isBusy() {
        return leaving || entering;
    }

    /** The leave half plays: keys but the volume wait, so nothing opens on a Home about to be rebuilt. */
    boolean isLeaving() {
        return leaving;
    }

    /**
     * The first half towards {@code target} over this Home, then {@code switchWorld} (save, set the pending key,
     * recreate). With reduced motion or animations off, at once. Ignored while a half plays.
     */
    void leave(World target, boolean animations, boolean reducedMotion, Runnable switchWorld) {
        if (isBusy()) return;
        if (!shouldPlay(animations, reducedMotion)) {
            switchWorld.run();
            return;
        }
        leaving = true;
        then = switchWorld;
        play(target, 0, leaveMs(target));
        // Over a Home already on screen: from now, not from the overlay's first frame.
        startedAt = SystemClock.uptimeMillis();
        tick();
    }

    /**
     * The second half into {@code current}, from onCreate once Home is built: the cover stands from the first frame,
     * and the clock starts when that frame is drawn.
     */
    void enter(World current) {
        if (isBusy()) return;
        entering = true;
        kicked = false;
        if (overlay != null) overlay.holdLights();
        play(current, leaveMs(current), leaveMs(current) + enterMs(current));
        scene.setZoom(sceneZoom(current, from, true));
        handler.postDelayed(enterTimeout, enterTimeoutMs(current));
    }

    private void play(World w, long from, long to) {
        target = w;
        this.from = from;
        this.to = to;
        startedAt = -1;
        if (fx == null) fx = new Fx(root.getContext(), this);
        fx.prepare(w, from, overlay instanceof HogwartsOverlay ? (HogwartsOverlay) overlay : null,
                overlay instanceof SupernaturalOverlay ? (SupernaturalOverlay) overlay : null);
        if (fx.getParent() == null) {
            root.addView(fx, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    /** From the overlay's first frame in the enter half: the clock starts, and the candles with it. */
    private void firstFrame() {
        if (!entering || startedAt >= 0) return;
        startedAt = SystemClock.uptimeMillis();
        // The clock runs now, so the half ends on its own; a slow first frame must not cut it short.
        handler.removeCallbacks(enterTimeout);
        if (overlay != null) overlay.ignite(startedAt);
        handler.postDelayed(tick, FRAME_MS);
    }

    private void tick() {
        if (!isBusy() || startedAt < 0) return;
        long t = Math.min(to, from + SystemClock.uptimeMillis() - startedAt);
        if (fx != null) fx.setTime(t);
        if (target == World.STARWARS) scene.setZoom(sceneZoom(target, t, entering));
        if (entering && !kicked && target == World.MARVEL && t - from >= REACTOR_KICK_MS) {
            kicked = true;
            if (overlay != null) overlay.pulse(WorldOverlay.REACTOR_KICK);
        }
        if (t >= to) end();
        else handler.postDelayed(tick, FRAME_MS);
    }

    private void end() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(enterTimeout);
        root.removeCallbacks(deferredEnd);
        if (leaving) {
            // The cover stays as it is until the old Home is gone: the new one starts from the same cover.
            leaving = false;
            Runnable run = then;
            then = null;
            if (run != null) run.run();
            return;
        }
        if (!entering) return;
        entering = false;
        if (overlay != null) overlay.ignite(0);
        scene.setZoom(1f);
        if (fx != null) {
            fx.reset();
            if (fx.getParent() == root) root.removeView(fx);
        }
    }

    /**
     * Home left while a half plays: the leave half switches right after this pause (the key was taken, the world
     * changes; recreate() from inside onPause is posted to root instead), the enter half ends at once where it would
     * have, the new skin in place.
     */
    void onPause() {
        if (leaving) {
            handler.removeCallbacks(tick);
            root.removeCallbacks(deferredEnd);
            root.post(deferredEnd);
        } else if (entering) {
            end();
        }
    }

    private void enterTimedOut() {
        if (entering) end();
    }

    private void switchAfterPause() {
        if (leaving) end();
    }

    /** The activity goes: nothing more runs, not even a waiting switch. */
    void cancel() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(enterTimeout);
        root.removeCallbacks(deferredEnd);
        leaving = false;
        entering = false;
        then = null;
    }

    // ---- overlay -------------------------------------------------------------------------------------

    /**
     * Draws the cover, Marvel's scan edge, Star Wars' streaks, Hogwarts' candle flares and Supernatural's flash or rays
     * at the time it is given.
     */
    static final class Fx extends View {
        private final WorldTransition owner;
        private final Paint cover = new Paint();
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint();
        private final Paint scanline = new Paint();
        private final Paint streak = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint flare = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** Middle-earth's mist over a unit edge: dense left of it as it comes in, dense right of it as it goes. */
        private final Paint mistIn = new Paint(), mistOut = new Paint();
        private final Paint billow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] cos = new float[STREAKS], sin = new float[STREAKS];
        private final long[] delay = new long[STREAKS];
        private final float[] candle = new float[3];
        private final float density;
        private World world = World.CLEAN;
        @Nullable private HogwartsOverlay candles;
        /** Supernatural's overlay in the enter half: it draws its own flash or rays over the cover, as the day is. */
        @Nullable private SupernaturalOverlay sky;
        private long time = -1;

        Fx(Context context, WorldTransition owner) {
            super(context);
            this.owner = owner;
            setFocusable(false);
            setClickable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setWillNotDraw(false);
            density = context.getResources().getDisplayMetrics().density;
            edge.setColor(0xFF9FDCFF);
            // The scan edge's glow above it: transparent up to Marvel blue at 55 %, over a unit height.
            glow.setShader(new LinearGradient(0f, 0f, 0f, 1f, 0x009FDCFF, 0x8C9FDCFF, Shader.TileMode.CLAMP));
            scanline.setColor(0xFF9FDCFF);
            streak.setColor(0xFFFFFFFF);
            streak.setStrokeCap(Paint.Cap.BUTT);
            // A candle's flare: Hogwarts' candle glow (#ffd27a .9 to #ffb347 0) around the origin, radius 1.
            flare.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xE6FFD27A, 0x00FFB347}, null,
                    Shader.TileMode.CLAMP));
            core.setColor(0xFFFFF6DC);
            // The mist's edge, sampled once from fogEdge: CLAMP carries the dense end on and the clear end off.
            int stops = 9;
            int[] in = new int[stops], out = new int[stops];
            float[] at = new float[stops];
            int rgb = FOG_COLOR & 0x00FFFFFF;
            for (int i = 0; i < stops; i++) {
                at[i] = i / (float) (stops - 1);
                in[i] = Math.round(255 * fogEdge(at[i])) << 24 | rgb;
                out[i] = Math.round(255 * fogEdge(1f - at[i])) << 24 | rgb;
            }
            mistIn.setShader(new LinearGradient(0f, 0f, 1f, 0f, in, at, Shader.TileMode.CLAMP));
            mistOut.setShader(new LinearGradient(0f, 0f, 1f, 0f, out, at, Shader.TileMode.CLAMP));
            // A billow: a soft round of the same mist around the origin, radius 1.
            billow.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xB3000000 | rgb, 0x00000000 | rgb}, null,
                    Shader.TileMode.CLAMP));
            // The same streaks in both halves: the old Home's run into the new Home's, across recreate().
            Random seeded = new Random(0x5717L);
            for (int i = 0; i < STREAKS; i++) {
                double a = seeded.nextDouble() * 2 * Math.PI;
                cos[i] = (float) Math.cos(a);
                sin[i] = (float) Math.sin(a);
                delay[i] = (long) (seeded.nextFloat() * STREAK_SPREAD_MS);
            }
        }

        void prepare(World w, long t, @Nullable HogwartsOverlay candles, @Nullable SupernaturalOverlay sky) {
            world = w;
            this.candles = candles;
            this.sky = sky;
            // A themed world covers with its own dark colour, Clean and Seasons with black.
            cover.setColor(coverColor(w));
            time = t;
            invalidate();
        }

        void setTime(long t) {
            time = t;
            invalidate();
        }

        void reset() {
            time = -1;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (time < 0) return;
            owner.firstFrame();
            long t = time;
            int w = getWidth(), h = getHeight();
            float a = coverAlpha(world, t);
            float line = revealLine(world, t) * h;
            if (a > 0f && line < h) {
                cover.setAlpha(Math.round(255 * a));
                c.drawRect(0f, line, w, h, cover);
            }
            if (line > 0f && line < h) drawScanEdge(c, w, line, 1f - revealLine(world, t));
            if (world == World.STARWARS) drawStreaks(c, t, w, h);
            if (world == World.HOGWARTS && candles != null) drawFlares(c, t - leaveMs(world));
            if (world == World.MIDDLE_EARTH) drawMist(c, t, w, h, owner.entering);
            if (world == World.SUPERNATURAL && sky != null) {
                // Over the cover, so the flash (or the rays) is the first thing of the new scene that shows.
                if (sky.isDay()) {
                    float r = raysFor(world, t);
                    if (r > 0f) sky.drawRays(c, r);
                } else {
                    float f = flashFor(world, t);
                    if (f > 0f) sky.drawFlash(c, f, 0);
                }
            }
        }

        /** Marvel's boot: the bright edge, a glow above it and fading scanlines in the band just drawn. */
        private void drawScanEdge(Canvas c, int w, float line, float strength) {
            float band = 48f * density;
            glow.setAlpha(Math.round(255 * strength));
            c.save();
            c.translate(0f, line - band);
            c.scale(1f, band);
            c.drawRect(0f, 0f, w, 1f, glow);
            c.restore();
            float step = 3f * density;
            for (float y = line - step; y > line - band; y -= step) {
                float f = 1f - (line - y) / band;
                scanline.setAlpha(Math.round(90 * f * strength));
                c.drawRect(0f, y, w, y + Math.max(1f, density), scanline);
            }
            edge.setAlpha(Math.round(255 * Math.min(1f, strength * 1.5f)));
            c.drawRect(0f, line - density, w, line + density, edge);
        }

        /** Star Wars: the app start's streaks (from the middle, 10 to 900 mockup px out), 400 ms each. */
        private void drawStreaks(Canvas c, long t, int w, int h) {
            float k = w / 1100f, mx = w / 2f, my = h / 2f;
            streak.setStrokeWidth(2f * k);
            for (int i = 0; i < STREAKS; i++) {
                long st = t - delay[i];
                if (st < 0 || st >= STREAK_MS) continue;
                float p = st / (float) STREAK_MS;
                float e = WorldLaunchMotion.bezier(0.6f, 0f, 1f, 1f, p);
                float alpha = p < 0.3f ? WorldLaunchMotion.bezier(0.6f, 0f, 1f, 1f, p / 0.3f)
                        : 1f - WorldLaunchMotion.bezier(0.6f, 0f, 1f, 1f, (p - 0.3f) / 0.7f);
                if (alpha <= 0f) continue;
                float d = (10f + 890f * e) * k, len = 2f * (1f + 119f * e) * k;
                streak.setAlpha(Math.round(255 * alpha));
                c.drawLine(mx + cos[i] * d, my + sin[i] * d, mx + cos[i] * (d + len), my + sin[i] * (d + len), streak);
            }
        }

        /**
         * Middle-earth: the veil of mist as fogAlpha has it, one gradient rect drawn through the canvas transform (no
         * shader matrix, no buffer), and the billows riding its edge.
         */
        private void drawMist(Canvas c, long t, int w, int h, boolean enterHalf) {
            float front = fogFront(world, t, enterHalf);
            float edge = FOG_EDGE * w;
            // The unit gradient's 0 lies at the edge's dense side coming in, at its clear side going out.
            float origin = (enterHalf ? front - FOG_EDGE : front) * w;
            Paint veil = enterHalf ? mistOut : mistIn;
            veil.setAlpha(Math.round(255 * FOG_PEAK));
            c.save();
            c.translate(origin, 0f);
            c.scale(edge, 1f);
            c.drawRect(-origin / edge, 0f, (w - origin) / edge, h, veil);
            c.restore();
            float s = billowStrength(world, t, enterHalf);
            if (s <= 0f) return;
            float ry = 0.2f * h, rx = 1.6f * ry, band = front - (enterHalf ? FOG_EDGE : 0f);
            billow.setAlpha(Math.round(255 * FOG_PEAK * s));
            for (int i = 0; i < BILLOW_Y.length; i++) {
                float x = (band + FOG_EDGE * BILLOW_IN_EDGE[i]) * w;
                if (x + rx < 0f || x - rx > w) continue;
                c.save();
                c.translate(x, BILLOW_Y[i] * h);
                c.scale(rx, ry);
                c.drawCircle(0f, 0f, 1f, billow);
                c.restore();
            }
        }

        /** Hogwarts: each candle flares over the cover as it lights, then leaves the light to the overlay's candle. */
        private void drawFlares(Canvas c, long t) {
            if (t < 0) return;
            long total = enterMs(World.HOGWARTS);
            HogwartsOverlay o = candles;
            for (int i = 0; i < o.candleCount(); i++) {
                long lit = igniteAtMs(o.igniteRank(i));
                long since = t - lit;
                if (since < 0) continue;
                long life = Math.min(FLARE_MS, total - lit);
                if (life <= 0 || since >= life) continue;
                float f = 1f - since / (float) life;
                o.candle(i, candle);
                float r = candle[2] * 1.8f;
                flare.setAlpha(Math.round(255 * f));
                c.save();
                c.translate(candle[0], candle[1]);
                c.scale(r, r);
                c.drawCircle(0f, 0f, 1f, flare);
                c.restore();
                core.setAlpha(Math.round(235 * f));
                c.drawCircle(candle[0], candle[1], candle[2] * 0.15f, core);
            }
        }
    }
}
