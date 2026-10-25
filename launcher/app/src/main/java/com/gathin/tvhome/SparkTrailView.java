package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Hogwarts' spark trail on a dock move (spec § 4.6): ten golden sparks (5 dp, a soft glow) start round the tile focus
 * left, fly 20 to 70 dp up and outwards and fade out in 700 ms. A transparent full-screen view over the dock, built
 * like PetalShowerView: no focus, clicks or accessibility, nothing allocated per frame. It ticks every 33 ms (30 fps at
 * most) only while sparks fly and invalidates only the area they fly in. A new burst joins the ones still in the air
 * (up to {@link #BURSTS}), so a fast D-pad never cuts a trail off mid-flight.
 */
final class SparkTrailView extends View {

    static final int BURSTS = 3;
    private static final int MAX = BURSTS * 10;
    private static final long FRAME_MS = 33;
    /** The mockup's spark: #ffe9a8, box-shadow 0 0 8px 2px rgba(255,210,120,.9). */
    private static final int CORE = 0xFFFFE9A8;

    private final float density;
    private final Random rnd = new Random();
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] x0 = new float[MAX], y0 = new float[MAX], dx = new float[MAX], dy = new float[MAX];
    private final long[] born = new long[MAX];
    private long life = WorldFocusMotion.SPARK_MS;
    private int next;
    /** Each burst's flight area and start, for the partial invalidation. */
    private final Rect[] areas = {new Rect(), new Rect(), new Rect()};
    private final long[] areaBorn = new long[BURSTS];
    private int nextArea;
    private final Rect dirty = new Rect();
    private boolean playing;
    private final Runnable frame = this::frame;

    SparkTrailView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        core.setColor(CORE);
        // Unit circle, scaled per spark: warm gold at the core fading to nothing at the glow's edge.
        glow.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xE6FFD278, 0x73FFD278, 0x00FFD278},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        for (int i = 0; i < MAX; i++) born[i] = Long.MIN_VALUE / 2;
        for (int i = 0; i < BURSTS; i++) areaBorn[i] = Long.MIN_VALUE / 2;
    }

    /** {@code count} sparks from round {@code from} (root coordinates; this view fills the root) for {@code ms}. */
    void burst(Rect from, int count, long ms) {
        long now = SystemClock.uptimeMillis();
        life = Math.max(1, ms);
        float cx = from.exactCenterX(), cy = from.exactCenterY();
        float w = from.width(), h = from.height();
        int n = Math.min(count, MAX / BURSTS);
        for (int k = 0; k < n; k++) {
            int i = next;
            next = (next + 1) % MAX;
            // Round the middle of the tile, as the mockup scatters them over 60 % of it.
            float ox = (rnd.nextFloat() - 0.5f) * w * 0.6f;
            x0[i] = cx + ox;
            y0[i] = cy + (rnd.nextFloat() - 0.5f) * h * 0.6f;
            // Up 20 to 70 dp, and outwards: away from the tile's middle, up to 30 dp.
            float side = ox == 0f ? (rnd.nextBoolean() ? 1f : -1f) : Math.signum(ox);
            dx[i] = side * rnd.nextFloat() * 30f * density;
            dy[i] = -(20f + rnd.nextFloat() * 50f) * density;
            born[i] = now;
        }
        float reach = 30f * density + glowRadius();
        Rect a = areas[nextArea];
        a.set((int) Math.floor(cx - w * 0.3f - reach), (int) Math.floor(cy - h * 0.3f - 70f * density - glowRadius()),
                (int) Math.ceil(cx + w * 0.3f + reach), (int) Math.ceil(cy + h * 0.3f + glowRadius()));
        areaBorn[nextArea] = now;
        nextArea = (nextArea + 1) % BURSTS;
        if (!playing) {
            playing = true;
            frame();
        }
    }

    /** All sparks gone at once. */
    void stop() {
        if (!playing) return;
        playing = false;
        removeCallbacks(frame);
        for (int i = 0; i < MAX; i++) born[i] = Long.MIN_VALUE / 2;
        for (int i = 0; i < BURSTS; i++) areaBorn[i] = Long.MIN_VALUE / 2;
        invalidate();
    }

    private float glowRadius() {
        return 12f * density;
    }

    private void frame() {
        if (!playing) return;
        long now = SystemClock.uptimeMillis();
        dirty.setEmpty();
        boolean any = false;
        for (int i = 0; i < BURSTS; i++) {
            // One frame past the end too, so the last faded spark is cleared away.
            if (now - areaBorn[i] <= life + FRAME_MS) {
                dirty.union(areas[i]);
                any = true;
            }
        }
        if (!dirty.isEmpty()) invalidate(dirty);
        if (any) {
            postDelayed(frame, FRAME_MS);
        } else {
            playing = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!playing) return;
        long now = SystemClock.uptimeMillis();
        float coreR = 2.5f * density, glowR = glowRadius();
        for (int i = 0; i < MAX; i++) {
            float p = (now - born[i]) / (float) life;
            if (p < 0f || p >= 1f) continue;
            float e = 1f - (1f - p) * (1f - p);          // ease-out
            float s = 1f - 0.8f * e;                      // scale 1 to 0.2
            int a = (int) (255 * (1f - e));
            float x = x0[i] + dx[i] * e, y = y0[i] + dy[i] * e;
            glow.setAlpha(a);
            c.save();
            c.translate(x, y);
            c.scale(glowR * s, glowR * s);
            c.drawCircle(0f, 0f, 1f, glow);
            c.restore();
            core.setAlpha(a);
            c.drawCircle(x, y, coreR * s, core);
        }
    }
}
