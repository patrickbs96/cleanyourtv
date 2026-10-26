package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The personal days' confetti in Clean, Hogwarts, Marvel and Star Wars (Jahreskreis spec § 4.6): once a day, at the
 * first return to Home with an entry ({@link #due}), {@link #DURATION_MS} of confetti in the world's accent and two
 * neighbouring tones ({@link #colours}) falls from above the top edge and is gone. Seasons has its own looks for the
 * day and never shows it. Like the worlds' overlays it draws at most every {@link WorldOverlay#FRAME_MS}, invalidates
 * only the band the pieces are in, allocates nothing per frame and takes no focus; the caller stops it in onPause
 * and removes it when {@code done} runs.
 */
final class ConfettiBurstView extends View {

    static final long DURATION_MS = 3000;
    private static final int PIECES = 80;
    /** The hue turn of the two neighbouring tones, in degrees. */
    private static final float NEIGHBOUR_HUE = 30f;

    private final float density;
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF piece = new RectF();
    private final Rect dirty = new Rect();
    private final Rect lastDirty = new Rect();

    private final float[] xFrac = new float[PIECES], delay = new float[PIECES], fallFrac = new float[PIECES],
            drift = new float[PIECES], swayAmp = new float[PIECES], swayFreq = new float[PIECES],
            phase = new float[PIECES], rot0 = new float[PIECES], spin = new float[PIECES],
            flipFreq = new float[PIECES], halfW = new float[PIECES], halfH = new float[PIECES];
    private final int[] colour = new int[PIECES];

    private int[] colours = new int[0];
    private boolean playing;
    private long start;
    private Runnable onDone;
    private final Runnable frame = this::frame;

    ConfettiBurstView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    /**
     * Whether the confetti is to start: outside Seasons, with an entry today, not yet today
     * ({@link PersonalDays#CONFETTI_LAST}, OncePerDay) and with a clock that can be trusted ({@code clockValid}).
     * Being due does not use the day up; {@link #take} does as the burst starts, so Home left in the wait before it
     * still plays it on the next return. Animations off or reduced motion skip it and count the day at once: it
     * does not come back later the same day.
     */
    static boolean due(SharedPreferences prefs, World w, SeasonsToday today, boolean animations,
                       boolean reducedMotion, boolean clockValid) {
        if (!clockValid || w == World.SEASONS || today == null || today.personal.isEmpty()) return false;
        if (!OncePerDay.due(prefs.getString(PersonalDays.CONFETTI_LAST, null), today.date)) return false;
        if (animations && !reducedMotion) return true;
        OncePerDay.take(prefs, PersonalDays.CONFETTI_LAST, today.date);
        return false;
    }

    /** Marks {@code today} as played as the burst starts; false when it already was (or there is no day). */
    static boolean take(SharedPreferences prefs, SeasonsToday today, boolean clockValid) {
        return today != null && OncePerDay.take(prefs, PersonalDays.CONFETTI_LAST, today.date, clockValid);
    }

    /**
     * The accent and its two neighbours, turned {@link #NEIGHBOUR_HUE} degrees either way (a grey: lighter and
     * darker); 0, Clean's missing accent, takes Clean's key colour.
     */
    static int[] colours(int accent) {
        int argb = accent != 0 ? accent : World.CLEAN.keyColor;
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float v = max, d = max - min;
        float s = max == 0f ? 0f : d / max;
        int base = 0xFF000000 | (argb & 0xFFFFFF);
        if (s < 0.08f) {
            return new int[] {base, WorldTypography.liftTowardsWhite(base, 0.35f), scale(base, 0.65f)};
        }
        float h;
        if (max == r) h = 60f * (((g - b) / d) % 6f);
        else if (max == g) h = 60f * ((b - r) / d + 2f);
        else h = 60f * ((r - g) / d + 4f);
        if (h < 0f) h += 360f;
        return new int[] {base, hsv(h + NEIGHBOUR_HUE, s, v), hsv(h - NEIGHBOUR_HUE, s, v)};
    }

    private static int hsv(float h, float s, float v) {
        h = ((h % 360f) + 360f) % 360f;
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float r, g, b;
        if (h < 60f) { r = c; g = x; b = 0f; }
        else if (h < 120f) { r = x; g = c; b = 0f; }
        else if (h < 180f) { r = 0f; g = c; b = x; }
        else if (h < 240f) { r = 0f; g = x; b = c; }
        else if (h < 300f) { r = x; g = 0f; b = c; }
        else { r = c; g = 0f; b = x; }
        return 0xFF000000 | (Math.round((r + m) * 255f) << 16) | (Math.round((g + m) * 255f) << 8)
                | Math.round((b + m) * 255f);
    }

    private static int scale(int argb, float f) {
        int r = Math.round(((argb >> 16) & 0xFF) * f), g = Math.round(((argb >> 8) & 0xFF) * f);
        int b = Math.round((argb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Plays the burst in {@code tones} ({@link #colours}); {@code done} runs when it has ended or been stopped. */
    void play(int[] tones, Runnable done) {
        stop();
        colours = tones;
        onDone = done;
        for (int i = 0; i < PIECES; i++) {
            xFrac[i] = (i + rnd.nextFloat()) / PIECES;          // spread evenly across the width
            delay[i] = rnd.nextFloat() * 0.3f;                   // fractions of the duration
            fallFrac[i] = 0.75f + rnd.nextFloat() * 0.45f;       // the screen heights fallen in what is left
            drift[i] = (rnd.nextFloat() - 0.5f) * 0.08f;         // fraction of the width
            swayAmp[i] = 6f + rnd.nextFloat() * 14f;             // dp
            swayFreq[i] = 0.6f + rnd.nextFloat() * 0.8f;         // Hz
            phase[i] = rnd.nextFloat() * (float) WorldOverlay.TAU;
            rot0[i] = rnd.nextFloat() * 360f;
            spin[i] = (90f + rnd.nextFloat() * 240f) * (rnd.nextBoolean() ? 1f : -1f);   // degrees per second
            flipFreq[i] = 0.8f + rnd.nextFloat() * 1.4f;         // Hz
            halfW[i] = 3f + rnd.nextFloat() * 2.5f;              // dp
            halfH[i] = 1.6f + rnd.nextFloat() * 1.4f;            // dp
            colour[i] = rnd.nextInt(Math.max(1, tones.length));
        }
        playing = true;
        start = SystemClock.uptimeMillis();
        lastDirty.setEmpty();
        frame();
    }

    /** Ends the burst at once (Home paused, the activity going); its {@code done} runs. */
    void stop() {
        removeCallbacks(frame);
        if (!playing) return;
        playing = false;
        invalidate();
        Runnable done = onDone;
        onDone = null;
        if (done != null) done.run();
    }

    boolean isPlaying() {
        return playing;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    /** One step: the band the pieces are in now, joined with the last one (to clear it), is redrawn. */
    private void frame() {
        if (!playing) return;
        long now = SystemClock.uptimeMillis();
        if (now - start >= DURATION_MS) {
            stop();
            return;
        }
        float p = (now - start) / (float) DURATION_MS;
        int h = getHeight();
        int top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
        float margin = 12f * density;
        for (int i = 0; i < PIECES; i++) {
            float y = pieceY(i, p, h);
            if (Float.isNaN(y)) continue;
            top = Math.min(top, (int) (y - margin));
            bottom = Math.max(bottom, (int) (y + margin));
        }
        if (top <= bottom) dirty.set(0, Math.max(0, top), getWidth(), Math.min(h, bottom));
        else dirty.setEmpty();
        if (!lastDirty.isEmpty()) invalidate(lastDirty);
        if (!dirty.isEmpty()) invalidate(dirty);
        lastDirty.set(dirty);
        postDelayed(frame, WorldOverlay.FRAME_MS);
    }

    /** The piece's y at progress {@code p} of the burst, or NaN while it waits or once it is gone. */
    private float pieceY(int i, float p, int h) {
        float q = (p - delay[i]) / (1f - delay[i]);
        if (q <= 0f || q >= 1f) return Float.NaN;
        float lead = 12f * density;
        // Gathers speed at first, then falls steadily, as paper against the air.
        float fall = q < 0.25f ? q * q * 2f : 0.125f + (q - 0.25f);
        return -lead + (h * fallFrac[i] + lead) * fall / 0.875f;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!playing || colours.length == 0) return;
        long now = SystemClock.uptimeMillis();
        float p = Math.min(1f, (now - start) / (float) DURATION_MS);
        float secs = (now - start) / 1000f;
        int w = getWidth(), h = getHeight();
        for (int i = 0; i < PIECES; i++) {
            float y = pieceY(i, p, h);
            if (Float.isNaN(y)) continue;
            float q = (p - delay[i]) / (1f - delay[i]);
            double sway = WorldOverlay.TAU * swayFreq[i] * secs + phase[i];
            float x = (xFrac[i] + drift[i] * q) * w + swayAmp[i] * density * (float) Math.sin(sway);
            float flip = (float) Math.cos(WorldOverlay.TAU * flipFreq[i] * secs + phase[i]);
            // Fades out over the last fifth of its fall.
            int alpha = q > 0.8f ? (int) (255 * (1f - (q - 0.8f) / 0.2f)) : 255;
            paint.setColor(colours[colour[i]]);
            paint.setAlpha(alpha);
            float hw = halfW[i] * density * Math.max(0.15f, Math.abs(flip)), hh = halfH[i] * density;
            piece.set(-hw, -hh, hw, hh);
            c.save();
            c.translate(x, y);
            c.rotate(rot0[i] + spin[i] * secs);
            c.drawRect(piece, paint);
            c.restore();
        }
    }
}
