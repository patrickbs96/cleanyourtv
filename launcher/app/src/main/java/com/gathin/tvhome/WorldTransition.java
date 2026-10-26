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
 * <li>Clean: 250 ms to black and 250 ms back (the only full black).</li>
 * <li>Hogwarts: the old scene darkens in 300 ms; then the candles light one by one from the middle, 60 ms apart, and
 * the new scene comes up under them in 600 ms.</li>
 * <li>Marvel: the old scene darkens in 250 ms; then the HUD boots, the new Home drawn in line by line from the top in
 * 500 ms behind a blue scan edge (corners and pills first, the dock bar last), the reactor kicking once on the way.</li>
 * <li>Star Wars: a hyperspace jump in, like the app start but 700 ms and without the white: streaks from the middle,
 * the old scene zooms in and darkens (400 ms), the new one settles back to its size as the streaks pass (300 ms).</li>
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
    /** How dark the cover gets at the cut: Clean goes to black, the worlds only dim (no hard black between them). */
    static final float CLEAN_PEAK = 1f, HOGWARTS_PEAK = 0.85f, MARVEL_PEAK = 0.9f, STARWARS_PEAK = 0.6f;
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
            case HOGWARTS: return 300;
            case STARWARS: return 400;
            default: return 250;   // Clean and Marvel
        }
    }

    /** The second half, played by the new Home in place of the tiles' entrance. Hogwarts: 10 candles × 60 ms. */
    static long enterMs(World target) {
        switch (target) {
            case HOGWARTS: return 600;
            case MARVEL: return 500;
            case STARWARS: return 300;
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
            default: return CLEAN_PEAK;
        }
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
        fx.prepare(w, from, overlay instanceof HogwartsOverlay ? (HogwartsOverlay) overlay : null);
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

    /** Draws the cover, Marvel's scan edge, Star Wars' streaks and Hogwarts' candle flares at the time it is given. */
    static final class Fx extends View {
        private final WorldTransition owner;
        private final Paint cover = new Paint();
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint();
        private final Paint scanline = new Paint();
        private final Paint streak = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint flare = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] cos = new float[STREAKS], sin = new float[STREAKS];
        private final long[] delay = new long[STREAKS];
        private final float[] candle = new float[3];
        private final float density;
        private World world = World.CLEAN;
        @Nullable private HogwartsOverlay candles;
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
            // The same streaks in both halves: the old Home's run into the new Home's, across recreate().
            Random seeded = new Random(0x5717L);
            for (int i = 0; i < STREAKS; i++) {
                double a = seeded.nextDouble() * 2 * Math.PI;
                cos[i] = (float) Math.cos(a);
                sin[i] = (float) Math.sin(a);
                delay[i] = (long) (seeded.nextFloat() * STREAK_SPREAD_MS);
            }
        }

        void prepare(World w, long t, @Nullable HogwartsOverlay candles) {
            world = w;
            this.candles = candles;
            // A world covers with its own dark colour, Clean with black.
            cover.setColor(w.isClean() ? 0xFF000000 : w.backgroundColor);
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
