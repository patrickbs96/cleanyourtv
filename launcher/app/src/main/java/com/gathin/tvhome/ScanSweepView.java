package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Marvel's scan on a dock move (spec § 4.6): a band 18 % of the tile wide, pale blue at 55 % in its middle, runs once
 * from left to right over the new tile in 500 ms, fading in over the first fifth and out towards the end (the
 * mockup's sweep). A transparent full-screen view over the dock, built like PetalShowerView: no focus, clicks or
 * accessibility, nothing allocated per frame, ticking every 33 ms (30 fps at most) only while it sweeps and
 * invalidating only the tile.
 */
final class ScanSweepView extends View {

    private static final long FRAME_MS = 33;
    private static final float BAND = 0.18f;

    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearGradient shader;
    private final Matrix matrix = new Matrix();
    private final RectF tile = new RectF();
    private final Rect dirty = new Rect();
    private float radius;
    private long start, duration = WorldFocusMotion.SCAN_MS;
    private boolean playing;
    private final Runnable frame = this::frame;

    ScanSweepView(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        // One unit wide, moved and stretched per frame by its local matrix: transparent, rgba(160,230,255,.55), transparent.
        shader = new LinearGradient(0f, 0f, 1f, 0f, new int[]{0x00A0E6FF, 0x8CA0E6FF, 0x00A0E6FF}, null,
                Shader.TileMode.CLAMP);
        band.setShader(shader);
    }

    /** One sweep over {@code over} (root coordinates; this view fills the root), its corners rounded by {@code r}. */
    void sweep(Rect over, float r, long ms) {
        tile.set(over);
        radius = r;
        duration = Math.max(1, ms);
        dirty.set(over);
        dirty.inset(-2, -2);
        start = SystemClock.uptimeMillis();
        if (!playing) {
            playing = true;
            frame();
        } else {
            invalidate();
        }
    }

    void stop() {
        if (!playing) return;
        playing = false;
        removeCallbacks(frame);
        invalidate(dirty);
    }

    private void frame() {
        if (!playing) return;
        invalidate(dirty);
        // One frame past the end, so the faded band is cleared away.
        if (SystemClock.uptimeMillis() - start <= duration + FRAME_MS) postDelayed(frame, FRAME_MS);
        else playing = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!playing) return;
        float p = (SystemClock.uptimeMillis() - start) / (float) duration;
        if (p < 0f || p >= 1f) return;
        // From 20 % left of the tile to its right edge (left: -20 % to 100 %), linear.
        float w = tile.width();
        float bandW = w * BAND;
        float left = tile.left + w * (-0.2f + 1.2f * p);
        // Opacity 0 to 1 over the first fifth, then down to 0 at the end.
        float a = p < 0.2f ? p / 0.2f : 1f - (p - 0.2f) / 0.8f;
        matrix.setScale(bandW, 1f);
        matrix.postTranslate(left, 0f);
        shader.setLocalMatrix(matrix);
        band.setAlpha((int) (255 * a));
        // The tile's own shape with the band as its fill: nothing outside the band, round corners kept.
        c.drawRoundRect(tile, radius, radius, band);
    }
}
