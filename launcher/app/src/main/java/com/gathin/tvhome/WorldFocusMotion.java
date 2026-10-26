package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * The worlds' dock move (spec § 4.6), as the motion mockup (vier-welten-v4-motion.html) plays it. Hogwarts: a softer
 * spring and ten sparks rising from the tile focus left. Marvel: the target brackets open (60 ms), the frame jumps
 * (360 ms, MainActivity holds its spring back by {@link #frameDelayMs}), the brackets lock on (250 ms), a scan sweeps
 * the new tile (500 ms) and the reactor kicks. Star Wars: the frame moves in three hard steps then blips
 * (MainActivity, with {@link #stepsInterpolate}, {@link #blipScaleY} and {@link #blipAlpha}); the scale under the dock
 * follows on its own (WorldDockDecoration). Supernatural: the frame jumps and flickers like an unsteady bulb, calm on
 * key repeat (MainActivity, with {@link #flickerAlpha} and {@link FlickerGate}). The hero changes too: a fade, a fade
 * with a flash, or typing.
 *
 * <p>Clean never gets here. Every effect restarts cleanly on the next move (fast D-pad, key repeat): what plays is
 * cancelled first, the brackets end locked, typing ends on the whole label. MainActivity calls in only with
 * animations on and reduced motion off.
 */
final class WorldFocusMotion {

    /** Marvel's brackets open before the frame leaves, jump with it, then lock on (spec § 4.6). */
    static final long BRACKET_OPEN_MS = 60, FRAME_JUMP_MS = 360, BRACKET_LOCK_MS = 250;
    static final long SCAN_MS = 500, SPARK_MS = 700;
    static final int SPARKS = 10;
    /** Star Wars: the frame's three hard steps, 60 ms each, then the hologram blip. */
    static final int STEPS = 3;
    static final long STEPS_MS = 180, BLIP_MS = 300;
    /** Supernatural: the frame jumps and flickers like an unsteady bulb, three dips in 150 ms, then steady (§ 4.3). */
    static final long FLICKER_MS = 150;
    static final int FLICKERS = 3;
    /** How dark each dip goes, the first the deepest: the bulb catches, then settles. */
    private static final float[] FLICKER_DEPTH = {0.7f, 0.5f, 0.3f};
    /** A move this soon after the last one (a held key, a fast D-pad) jumps steady, so the frame never strobes. */
    static final long FLICKER_CALM_MS = 300;
    /** The hero's fade out and in (Hogwarts, Marvel), and Star Wars' typing pace. */
    static final long HERO_FADE_MS = 220;
    static final long TYPING_MS = 45;
    /** No effect runs longer than this (common rules, spec § 4.6); a long label types faster to stay inside. */
    static final long EFFECT_MAX_MS = 1200;
    /** Marvel's flash: how much lighter the hero starts its fade in, per channel, in FLASH_STEPS filters. */
    private static final int FLASH_ADD = 0x80;
    private static final int FLASH_STEPS = 8;

    /** The world's spring for the frame, or null: Clean keeps today's, Star Wars steps instead of springing. */
    @Nullable
    static SpringForce springFor(World w, boolean advanced) {
        // advanced_effects tunes Clean's spring only; a world's motion is the same either way.
        switch (w) {
            case HOGWARTS: return new SpringForce().setStiffness(260f).setDampingRatio(0.7f);
            case MARVEL: return new SpringForce().setStiffness(520f).setDampingRatio(1f);
            default: return null;
        }
    }

    /** CSS steps(n) (jump-end): 0 until the first n-th has passed, then one n-th per n-th, 1 only at the end. */
    static float stepsInterpolate(float t, int steps) {
        if (t >= 1f) return 1f;
        if (t <= 0f) return 0f;
        return (float) Math.floor(t * steps) / steps;
    }

    /** When the char at {@code charIndex} appears, after the typing started: 45 ms each. */
    static long typingDelayMs(int charIndex) {
        return TYPING_MS * charIndex;
    }

    /** The pace for a label this long: 45 ms per char, faster when that would run past the effect limit. */
    static long typingStepMs(int length) {
        if (length <= 0) return TYPING_MS;
        return Math.max(1L, Math.min(TYPING_MS, EFFECT_MAX_MS / length));
    }

    /**
     * When char {@code charIndex} of a {@code length}-char label appears: {@link #typingDelayMs} (char 0 at once),
     * unless that would run past the effect limit, then at the shorter {@link #typingStepMs} pace.
     */
    static long typingCharDelayMs(int charIndex, int length) {
        long step = typingStepMs(length);
        return step == TYPING_MS ? typingDelayMs(charIndex) : step * charIndex;
    }

    /** How long the frame waits before it moves: Marvel's brackets open first, on the dock only. */
    static long frameDelayMs(World w, boolean dockMove) {
        return w == World.MARVEL && dockMove ? BRACKET_OPEN_MS : 0L;
    }

    /** Whether the world's frame jumps and flickers instead of springing: Supernatural (MainActivity). */
    static boolean flickers(World w) {
        return w == World.SUPERNATURAL;
    }

    /**
     * The frame's opacity {@code ms} after its jump: three dips of 50 ms each, down to 0.3, 0.5 and 0.7 and straight
     * back up (a triangle, so every frame at 60 fps catches each dip), full from 150 ms on.
     */
    static float flickerAlpha(long ms) {
        if (ms <= 0 || ms >= FLICKER_MS) return 1f;
        float period = (float) FLICKER_MS / FLICKERS;
        int i = Math.min(FLICKERS - 1, (int) (ms / period));
        float phase = (ms - i * period) / period;            // 0..1 within this dip
        float down = 1f - Math.abs(2f * phase - 1f);          // 0 at its ends, 1 halfway
        return 1f - FLICKER_DEPTH[i] * down;
    }

    /**
     * Decides which of Supernatural's moves flicker: the first, and each after a pause of {@link #FLICKER_CALM_MS};
     * every move closer to the last one (key repeat, fast taps) jumps steady and keeps the calm going.
     */
    static final class FlickerGate {
        private boolean moved;
        private long lastMoveMs;

        /** A move now ({@code nowMs}, a monotonic clock): true when the frame flickers on it. */
        boolean onMove(long nowMs) {
            boolean flicker = !moved || nowMs - lastMoveMs >= FLICKER_CALM_MS;
            moved = true;
            lastMoveMs = nowMs;
            return flicker;
        }

        /** Focus left the tiles or Home paused: the next move is a first one. */
        void reset() {
            moved = false;
        }
    }

    /** The blip's height over its 300 ms (holoblip): 0.2, 1.05 at 40 %, 1 at the end. */
    static float blipScaleY(float t) {
        if (t <= 0f) return 0.2f;
        if (t < 0.4f) return 0.2f + (1.05f - 0.2f) * (t / 0.4f);
        if (t < 1f) return 1.05f + (1f - 1.05f) * ((t - 0.4f) / 0.6f);
        return 1f;
    }

    /** The blip's opacity: 0.2, full at 40 %, a flicker to 0.6 at 60 %, full at the end. */
    static float blipAlpha(float t) {
        if (t <= 0f) return 0.2f;
        if (t < 0.4f) return 0.2f + 0.8f * (t / 0.4f);
        if (t < 0.6f) return 1f - 0.4f * ((t - 0.4f) / 0.2f);
        if (t < 1f) return 0.6f + 0.4f * ((t - 0.6f) / 0.4f);
        return 1f;
    }

    private final GlassView glass;
    @Nullable private final WorldOverlay overlay;
    @Nullable private final SparkTrailView sparks;
    @Nullable private final ScanSweepView scan;
    private final ValueAnimator brackets = new ValueAnimator();
    private final Rect scanRect = new Rect();
    private float tileRadius;
    private final Runnable lockOn = this::lockOn;
    /** Marvel's brackets are open (or opening) and waiting to lock on: a move now keeps them open. */
    private boolean lockPending;

    // Marvel's hero flash: one filter per step, built once, so the fade allocates nothing per frame.
    private final LightingColorFilter[] flashFilters = new LightingColorFilter[FLASH_STEPS];
    private final Paint flashPaint = new Paint();
    private final ValueAnimator flash = new ValueAnimator();
    @Nullable private View flashed;

    // Star Wars' typing: the chars not typed yet are drawn invisible, so the line keeps its size (and the tilt its pivot).
    private final HiddenSpan hidden = new HiddenSpan();
    @Nullable private TextView typed;
    @Nullable private Spannable typedText;
    private int typedChars;
    private final Runnable typeNext = this::typeNext;

    WorldFocusMotion(GlassView glass, @Nullable WorldOverlay overlay, @Nullable SparkTrailView sparks,
                     @Nullable ScanSweepView scan) {
        this.glass = glass;
        this.overlay = overlay;
        this.sparks = sparks;
        this.scan = scan;
        brackets.setInterpolator(new LinearInterpolator());
        brackets.addUpdateListener(a -> glass.setBracketsOpen((float) a.getAnimatedValue()));
        for (int i = 0; i < FLASH_STEPS; i++) {
            int add = FLASH_ADD * (FLASH_STEPS - i) / FLASH_STEPS;
            flashFilters[i] = new LightingColorFilter(0xFFFFFF, add << 16 | add << 8 | add);
        }
        flash.setFloatValues(0f, 1f);
        flash.setInterpolator(new LinearInterpolator());
        flash.addUpdateListener(a -> {
            View v = flashed;
            if (v == null) return;
            int i = Math.min(FLASH_STEPS - 1, (int) (a.getAnimatedFraction() * FLASH_STEPS));
            flashPaint.setColorFilter(flashFilters[i]);
            v.setLayerPaint(flashPaint);
        });
        flash.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                endFlash();
            }
        });
    }

    /** The zoomed tile's corner radius, so the scan follows the tile's corners. */
    void setTileRadius(float px) {
        tileRadius = px;
    }

    /**
     * Focus moved from dock tile {@code from} to {@code to} (both rects in the root's coordinates, {@code toRect} the
     * zoomed tile the frame lands on). Plays the world's extra on top of the frame's own move.
     */
    void onDockMove(World w, int from, int to, Rect fromRect, Rect toRect) {
        switch (w) {
            case HOGWARTS:
                if (sparks != null) sparks.burst(fromRect, SPARKS, SPARK_MS);
                break;
            case MARVEL:
                if (scan != null) scan.stop();
                scanRect.set(toRect);
                // A held key or a fast D-pad: the brackets stay open and lock on once the moves stop.
                if (!lockPending) openBrackets();
                glass.removeCallbacks(lockOn);
                lockPending = true;
                glass.postDelayed(lockOn, BRACKET_OPEN_MS + FRAME_JUMP_MS);
                break;
            default:
                // Star Wars: the steps and the blip are the frame's move; the scale lights its new section itself.
                break;
        }
    }

    private void openBrackets() {
        brackets.cancel();
        brackets.setFloatValues(currentOpen(), 1f);
        brackets.setDuration(BRACKET_OPEN_MS);
        brackets.start();
    }

    /** Marvel's brackets are still open from the last move, so the frame need not wait for them (frameDelayMs). */
    boolean bracketsHeldOpen() {
        return lockPending;
    }

    private void lockOn() {
        lockPending = false;
        brackets.cancel();
        brackets.setFloatValues(1f, 0f);
        brackets.setDuration(BRACKET_LOCK_MS);
        brackets.start();
        if (scan != null) scan.sweep(scanRect, tileRadius, SCAN_MS);
        if (overlay != null) overlay.pulse(WorldOverlay.REACTOR_KICK);
    }

    private float currentOpen() {
        Object v = brackets.getAnimatedValue();
        return brackets.isRunning() && v instanceof Float ? (Float) v : 0f;
    }

    /**
     * Stops the move's extras where they are: the brackets lock on at once, the scan ends. Sparks already in the air
     * fly on (a new burst joins them). Called before every move that is no dock move (onDockMove restarts its own).
     */
    void cancelMove() {
        glass.removeCallbacks(lockOn);
        lockPending = false;
        brackets.cancel();
        glass.setBracketsLocked(true);
        if (scan != null) scan.stop();
    }

    /** Everything off at once (onPause, focus leaving the tiles): extras, sparks, the flash, typing finished. */
    void cancel() {
        cancelMove();
        if (sparks != null) sparks.stop();
        flash.cancel();
        endFlash();
        finishTyping();
    }

    // ---- hero -------------------------------------------------------------------------------------

    /**
     * Marvel's flash: {@code hero} fades in from a lighter self back to its own colours over the fade's 220 ms,
     * through a hardware layer for just that time.
     */
    void flash(View hero) {
        flash.cancel();
        endFlash();
        flashed = hero;
        flashPaint.setColorFilter(flashFilters[0]);
        hero.setLayerType(View.LAYER_TYPE_HARDWARE, flashPaint);
        flash.setDuration(HERO_FADE_MS);
        flash.start();
    }

    private void endFlash() {
        View v = flashed;
        if (v == null) return;
        flashed = null;
        v.setLayerType(View.LAYER_TYPE_NONE, null);
    }

    /**
     * Star Wars: {@code view} types its current text anew, char i at {@link #typingCharDelayMs} (the first at once, then
     * one each 45 ms); the whole line takes its room at once.
     */
    void type(TextView view) {
        finishTyping();
        view.setText(view.getText(), TextView.BufferType.SPANNABLE);
        CharSequence text = view.getText();
        if (!(text instanceof Spannable) || text.length() <= 1) return;
        typed = view;
        typedText = (Spannable) text;
        typedChars = 1;   // char 0 at 0 ms
        typedText.setSpan(hidden, 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.postDelayed(typeNext, nextCharDelay());
    }

    /** From the char just shown to the next one. */
    private long nextCharDelay() {
        int len = typedText == null ? 0 : typedText.length();
        return typingCharDelayMs(typedChars, len) - typingCharDelayMs(typedChars - 1, len);
    }

    private void typeNext() {
        TextView view = typed;
        Spannable text = typedText;
        // The hero got new text meanwhile (fillHero): that typing is over.
        if (view == null || text == null || view.getText() != text) {
            typed = null;
            typedText = null;
            return;
        }
        typedChars++;
        if (typedChars >= text.length()) {
            finishTyping();
            return;
        }
        text.setSpan(hidden, typedChars, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.postDelayed(typeNext, nextCharDelay());
    }

    /** Ends any typing on the whole label. */
    void finishTyping() {
        TextView view = typed;
        Spannable text = typedText;
        typed = null;
        typedText = null;
        if (view == null) return;
        view.removeCallbacks(typeNext);
        if (text != null) text.removeSpan(hidden);
    }

    /** Draws its chars invisible, glow included; the layout keeps their room. */
    private static final class HiddenSpan extends CharacterStyle implements UpdateAppearance {
        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setColor(0);
            tp.clearShadowLayer();
        }
    }
}
