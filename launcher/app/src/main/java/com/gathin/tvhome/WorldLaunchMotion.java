package com.gathin.tvhome;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import java.util.function.Consumer;

/**
 * The worlds' app start (spec § 4.7), as the motion mockup (vier-welten-v4-motion.html) plays it, before the app's
 * startActivity. Hogwarts: the tile grows 1.07 to 1.25 in 400 ms and glows white-gold, a conic spark swirl turns out
 * of it for 1.1 s, from 60 % on the screen fades to black ("Nox"), the app starts at 1.2 s. Marvel: the tile grows to
 * 1.35 in 900 ms (cubic-out), three rings run out from its middle (0/150/300 ms, 900 ms each), from 400 ms an
 * anamorphic light streak wipes across in 800 ms, at 1.0 s a cut to white (300 ms), then the app. Star Wars: 70
 * streaks shoot out of the screen's middle (150 to 500 ms apart, 900 ms, 900 mockup px), the scene zooms to 1.4×
 * in 1.1 s, the tile stretches to 6×1.6 from 100 ms for 1 s and fades, white at 1.0 s (250 ms), then the app.
 *
 * <p>Clean, reduced motion and animations off never get here: MainActivity starts the app at once
 * ({@link #shouldPlay}), so Clean adds no view. While an effect plays a second OK is swallowed ({@link #begin} and
 * the overlay still up); the app starts exactly once ({@link #finish}), at the effect's end or at the latest
 * {@link #timeoutMs} after it began. The overlay (LaunchFxView) stays {@link #REMOVE_AFTER_MS} after the start, so
 * the black or white holds until the app is in front, then the tile (at the end of its focus zoom, never a half-run
 * one), frame and scene get their looks back. Home leaving before the start (onPause), or Back pressed then, cancels
 * the effect: the app does not start and the overlay goes at once.
 *
 * <p>The guard ({@link #begin}, {@link #finish}, {@link #cancel}) touches no Android class, so it runs in JVM tests.
 */
final class WorldLaunchMotion {

    static final long HOGWARTS_MS = 1200, MARVEL_MS = 1000 + 300, STARWARS_MS = 1250;
    /** The app starts at the latest this long after the effect should have ended (a stalled frame, a lost tick). */
    static final long TIMEOUT_GRACE_MS = 200;
    /** The blend stays this long after the start: the app's window is in front by then. */
    static final long REMOVE_AFTER_MS = 400;
    /** 30 fps at most, as the worlds' other overlays. */
    static final long FRAME_MS = 33;
    /** The focus frame fades out over the tile's first growth: it would otherwise stand round the old size. */
    static final long GLASS_FADE_MS = 120;

    // ---- timeline (pure, spec § 4.7) -----------------------------------------------------------------

    /** The whole effect up to the app's start; 0 for Clean, which plays none. */
    static long durationMs(World w) {
        switch (w) {
            case HOGWARTS: return HOGWARTS_MS;
            case MARVEL: return MARVEL_MS;
            case STARWARS: return STARWARS_MS;
            default: return 0L;
        }
    }

    static long timeoutMs(World w) {
        return durationMs(w) + TIMEOUT_GRACE_MS;
    }

    /** Whether a launch plays the world's effect: never in Clean, with animations off or with reduced motion. */
    static boolean shouldPlay(World w, boolean animations, boolean reducedMotion) {
        return !w.isClean() && animations && !reducedMotion && durationMs(w) > 0;
    }

    /** The black (Hogwarts) or white blend over everything at {@code t} ms, 0 to 1. */
    static float blendAlpha(World w, long t) {
        switch (w) {
            case HOGWARTS: {
                // snuff 1.2s: nothing until 60 %, then to black by the end.
                float p = clamp(t / (float) HOGWARTS_MS);
                return p < 0.6f ? 0f : ease((p - 0.6f) / 0.4f);
            }
            case MARVEL: return t < 1000 ? 0f : ease(clamp((t - 1000) / 300f));
            case STARWARS: return t < 1000 ? 0f : ease(clamp((t - 1000) / 250f));
            default: return 0f;
        }
    }

    /** The blend's colour: Hogwarts goes dark (Nox), the others cut to white. */
    static int blendColor(World w) {
        return w == World.HOGWARTS ? 0xFF000000 : 0xFFFFFFFF;
    }

    /** The tile's width scale at {@code t} ms, from {@code s0} (its focus zoom when OK was pressed). */
    static float tileScaleX(World w, long t, float s0) {
        switch (w) {
            case HOGWARTS: {
                // hogLaunchTile 1.1s: to 1.25 at 40 %, then held.
                float p = clamp(t / 1100f);
                return p < 0.4f ? s0 + (1.25f - s0) * ease(p / 0.4f) : 1.25f;
            }
            case MARVEL: return s0 + (1.35f - s0) * bezier(0.2f, 0.8f, 0.2f, 1f, clamp(t / 900f));
            case STARWARS: return starWarsTile(t, s0, 6f);
            default: return s0;
        }
    }

    static float tileScaleY(World w, long t, float s0) {
        return w == World.STARWARS ? starWarsTile(t, s0, 1.6f) : tileScaleX(w, t, s0);
    }

    /** swTile 1s from 100 ms: to 1.1 at 60 %, then stretched to {@code end}, each leg cubic-bezier(.7,0,1,1). */
    private static float starWarsTile(long t, float s0, float end) {
        if (t < 100) return s0;
        float p = clamp((t - 100) / 1000f);
        if (p < 0.6f) return s0 + (1.1f - s0) * bezier(0.7f, 0f, 1f, 1f, p / 0.6f);
        return 1.1f + (end - 1.1f) * bezier(0.7f, 0f, 1f, 1f, (p - 0.6f) / 0.4f);
    }

    /** The tile's opacity: Star Wars' fades as it stretches, the others stay. */
    static float tileAlpha(World w, long t) {
        if (w != World.STARWARS || t < 100) return 1f;
        return 1f - bezier(0.7f, 0f, 1f, 1f, clamp((t - 100) / 1000f));
    }

    /** Star Wars' scene zoom (zoomsky 1.1s ease-in to 1.4), 1 for the others. */
    static float sceneZoom(World w, long t) {
        if (w != World.STARWARS) return 1f;
        return 1f + 0.4f * easeIn(clamp(t / 1100f));
    }

    static float clamp(float p) {
        return p < 0f ? 0f : p > 1f ? 1f : p;
    }

    /** CSS ease. */
    static float ease(float p) {
        return bezier(0.25f, 0.1f, 0.25f, 1f, p);
    }

    /** CSS ease-in. */
    static float easeIn(float p) {
        return bezier(0.42f, 0f, 1f, 1f, p);
    }

    /** CSS ease-out. */
    static float easeOut(float p) {
        return bezier(0f, 0f, 0.58f, 1f, p);
    }

    /** CSS cubic-bezier(x1,y1,x2,y2) at time {@code t} (0 to 1): bisection on x, nothing allocated. */
    static float bezier(float x1, float y1, float x2, float y2, float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float lo = 0f, hi = 1f, s = t;
        for (int i = 0; i < 24; i++) {
            s = (lo + hi) * 0.5f;
            float x = cubic(x1, x2, s);
            if (Math.abs(x - t) < 1e-5f) break;
            if (x < t) lo = s;
            else hi = s;
        }
        return cubic(y1, y2, s);
    }

    /** One coordinate of the curve from 0 to 1 with control points a and b. */
    private static float cubic(float a, float b, float s) {
        float u = 1f - s;
        return 3f * u * u * s * a + 3f * u * s * s * b + s * s * s;
    }

    // ---- guard ---------------------------------------------------------------------------------------

    private boolean playing;
    @Nullable private Runnable pending;
    private World world = World.CLEAN;

    /**
     * Takes the launch when none is playing: true, and {@code start} waits for {@link #finish}. False while one plays
     * (the second OK is swallowed).
     */
    boolean begin(World w, Runnable start) {
        if (playing) return false;
        playing = true;
        world = w;
        pending = start;
        return true;
    }

    /** Runs the waiting start exactly once and frees the guard, even when the start throws. */
    void finish() {
        if (!playing) return;
        Runnable start = pending;
        pending = null;
        playing = false;
        if (handler != null) {
            handler.removeCallbacks(tick);
            handler.removeCallbacks(timeout);
            if (fx != null && fx.getParent() != null) {
                // The full blend stands while the app comes up, then the overlay goes.
                fx.setElapsed(durationMs(world));
                handler.postDelayed(remove, REMOVE_AFTER_MS);
            }
        }
        if (start != null) start.run();
    }

    /** Drops the waiting start without running it (Home left before the start) and takes the overlay away. */
    void cancel() {
        pending = null;
        playing = false;
        if (handler != null) {
            handler.removeCallbacks(tick);
            handler.removeCallbacks(timeout);
        }
        removeOverlay();
    }

    /** Keys that still work while an effect plays: the volume. Everything else waits (MainActivity.dispatchKeyEvent). */
    static boolean passesThrough(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    /** Back while the effect plays, before the app started: the effect is dropped as on onPause (ruling R13). */
    static boolean cancelsEffect(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE;
    }

    boolean isPlaying() {
        return playing;
    }

    /** An effect plays or its blend still stands: keys (a second OK, a held OK's menu, the D-pad) wait. */
    boolean isBusy() {
        return playing || (fx != null && fx.getParent() != null);
    }

    // ---- views ---------------------------------------------------------------------------------------

    @Nullable private final View glass;
    @Nullable private final WorldScene scene;
    /** Ends the tile's focus zoom where it was heading (AppAdapter.settleFocus); null in tests. */
    @Nullable private final Consumer<View> settle;
    @Nullable private Handler handler;
    @Nullable private LaunchFxView fx;
    @Nullable private FrameLayout root;
    @Nullable private View card;
    private float cardScaleX0 = 1f, cardScaleY0 = 1f, cardAlpha0 = 1f, glassAlpha0 = 1f;
    private long startedAt;
    /** Made on the first play: the guard alone must not touch an Android class (JVM tests). */
    @Nullable private Rect cardRect;
    private final Runnable tick = this::tick;
    private final Runnable timeout = this::finish;
    private final Runnable remove = this::removeOverlay;

    /** The guard alone, for tests. */
    WorldLaunchMotion() {
        this(null, null, null);
    }

    /**
     * {@code glass} fades out while the tile grows; {@code scene} zooms in Star Wars; {@code settle} puts a tile at the
     * end of its focus zoom, before the effect reads it and when the effect gives it back.
     */
    WorldLaunchMotion(@Nullable View glass, @Nullable WorldScene scene, @Nullable Consumer<View> settle) {
        this.glass = glass;
        this.scene = scene;
        this.settle = settle;
    }

    /**
     * Plays the world's effect over {@code root} from {@code card}, then runs {@code start}. A call while one plays
     * (or its blend still stands) is swallowed.
     */
    void play(World w, View card, FrameLayout root, Runnable start) {
        if (isBusy()) return;
        if (!begin(w, start)) return;
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        handler.removeCallbacks(remove);
        this.root = root;
        this.card = card;
        // A focus zoom still running (OK right after a move) would fight the effect's scale, and cancelled halfway it
        // would leave the tile half zoomed and half lifted: it ends where it was heading instead.
        card.animate().cancel();
        if (settle != null) settle.accept(card);
        cardScaleX0 = card.getScaleX();
        cardScaleY0 = card.getScaleY();
        cardAlpha0 = card.getAlpha();
        if (glass != null) {
            // Its fade in (first focus) cancelled halfway: the frame was on its way to full, so it comes back full.
            glass.animate().cancel();
            glassAlpha0 = glass.getAlpha() > 0f ? 1f : 0f;
        }
        // The tile in root coordinates from its layout, plus the focus lift (translation); the scale is drawn apart.
        if (cardRect == null) cardRect = new Rect();
        Rect cardRect = this.cardRect;
        cardRect.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, cardRect);
        cardRect.offset(Math.round(card.getTranslationX()), Math.round(card.getTranslationY()));
        if (fx == null) fx = new LaunchFxView(root.getContext());
        float density = root.getResources().getDisplayMetrics().density;
        fx.prepare(w, cardRect, AppAdapter.cornerRadius(card.getWidth(), density), cardScaleX0, cardScaleY0);
        if (fx.getParent() == null) {
            root.addView(fx, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        startedAt = SystemClock.uptimeMillis();
        handler.postDelayed(timeout, timeoutMs(w));
        tick();
    }

    private void tick() {
        if (!playing) return;
        long t = SystemClock.uptimeMillis() - startedAt;
        long end = durationMs(world);
        apply(Math.min(t, end));
        if (t >= end) finish();
        else if (handler != null) handler.postDelayed(tick, FRAME_MS);
    }

    private void apply(long t) {
        View c = card;
        if (c != null) {
            c.setScaleX(tileScaleX(world, t, cardScaleX0));
            c.setScaleY(tileScaleY(world, t, cardScaleY0));
            c.setAlpha(cardAlpha0 * tileAlpha(world, t));
        }
        if (glass != null) glass.setAlpha(glassAlpha0 * (1f - clamp(t / (float) GLASS_FADE_MS)));
        if (scene != null && world == World.STARWARS) scene.setZoom(sceneZoom(world, t));
        if (fx != null) fx.setElapsed(t);
    }

    /** Back on Home: nothing of the effect is left (also when the app came up within the blend's 400 ms). */
    void onResume() {
        if (!playing) removeOverlay();
    }

    /** Home left: an effect whose app has not started yet is cancelled; a started one's blend goes on schedule. */
    void onPause() {
        if (playing) cancel();
    }

    /** Takes the overlay off and gives the tile, the frame and the scene their looks back. */
    private void removeOverlay() {
        if (handler != null) handler.removeCallbacks(remove);
        View c = card;
        card = null;
        if (c != null) {
            if (settle != null) {
                settle.accept(c);   // the focus zoom of the tile as it is now, focused or not
            } else {
                c.setScaleX(cardScaleX0);
                c.setScaleY(cardScaleY0);
            }
            c.setAlpha(cardAlpha0);
        }
        if (glass != null && c != null) glass.setAlpha(glassAlpha0);
        if (scene != null && c != null) scene.setZoom(1f);
        if (fx != null) {
            fx.reset();
            if (root != null && fx.getParent() == root) root.removeView(fx);
        }
    }
}
