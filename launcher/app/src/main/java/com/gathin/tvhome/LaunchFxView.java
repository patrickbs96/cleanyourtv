package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;

import java.util.Random;

/**
 * The worlds' app-start overlay (spec § 4.7), drawn over all of Home: Hogwarts' white-gold glow round the tile, its
 * conic spark swirl and the black of "Nox"; Marvel's three reactor rings, the anamorphic light streak and the cut to
 * white; Star Wars' 70 hyperspace streaks and the white. The tile's own growth, the frame's fade and the scene's zoom
 * are WorldLaunchMotion's; this view only draws what lies over them, at the time {@link #setElapsed} gives it.
 *
 * <p>Built like PetalShowerView: no focus, clicks or accessibility, every paint, shader and per-streak value made
 * once (the streaks' angles and delays are drawn anew per launch, into the same arrays), nothing allocated per
 * frame. Sizes follow the mockup, whose screen is 1100 px wide. It invalidates the whole view: from the rings on the
 * effect spans the screen, and it ends in a full-screen blend; it plays only for the effect's 1.2 to 1.3 s.
 */
final class LaunchFxView extends View {

    static final int STREAKS = 70;
    /** The mockup's screen width: its px become this share of ours. */
    private static final float MOCKUP_W = 1100f;
    /** Hogwarts' glow: box-shadow 0 0 60px 20px rgba(255,210,120,.9) as rings fading outward. */
    private static final int GLOW_RINGS = 10;
    private static final int GOLD = 0xFFFFD278;

    private final Random rnd = new Random();
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint swirl = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flare = new Paint();
    private final Paint streak = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blend = new Paint();
    private final float[] cos = new float[STREAKS], sin = new float[STREAKS];
    private final long[] delay = new long[STREAKS];
    private final RectF box = new RectF();

    private World world = World.CLEAN;
    private float cx, cy, tileW, tileH, radius, s0x = 1f, s0y = 1f;
    private long elapsed = -1;

    LaunchFxView(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        rim.setStyle(Paint.Style.STROKE);
        rim.setColor(0xFFFFFFFF);
        glow.setStyle(Paint.Style.STROKE);
        glow.setColor(GOLD);
        // conic-gradient(transparent 0 70%, rgba(255,220,140,.9) 85%, transparent 100%) on a unit circle.
        swirl.setShader(new SweepGradient(0f, 0f, new int[]{0x00FFDC8C, 0x00FFDC8C, 0xE6FFDC8C, 0x00FFDC8C},
                new float[]{0f, 0.7f, 0.85f, 1f}));
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0xFFA0E6FF);
        // linear-gradient(90deg, transparent, rgba(255,235,200,.95), transparent) over a unit width.
        flare.setShader(new LinearGradient(0f, 0f, 1f, 0f, new int[]{0x00FFEBC8, 0xF2FFEBC8, 0x00FFEBC8},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        streak.setColor(0xFFFFFFFF);
        streak.setStrokeCap(Paint.Cap.BUTT);
    }

    /**
     * Sets up a launch of {@code w} from the tile at {@code tile} (root coordinates, unscaled, the view fills the root)
     * with corner {@code cornerRadius}, zoomed {@code scaleX}×{@code scaleY} when OK was pressed.
     */
    void prepare(World w, Rect tile, float cornerRadius, float scaleX, float scaleY) {
        world = w;
        cx = tile.exactCenterX();
        cy = tile.exactCenterY();
        tileW = tile.width();
        tileH = tile.height();
        radius = cornerRadius;
        s0x = scaleX;
        s0y = scaleY;
        blend.setColor(WorldLaunchMotion.blendColor(w));
        if (w == World.STARWARS) {
            // Random angles, starts 150 to 500 ms in (the mockup's 0.15 + random × 0.35 s).
            for (int i = 0; i < STREAKS; i++) {
                double a = rnd.nextDouble() * 2 * Math.PI;
                cos[i] = (float) Math.cos(a);
                sin[i] = (float) Math.sin(a);
                delay[i] = 150 + (long) (rnd.nextFloat() * 350);
            }
        }
        elapsed = 0;
        invalidate();
    }

    /** The effect's time in ms; redraws. */
    void setElapsed(long ms) {
        elapsed = ms;
        invalidate();
    }

    /** Nothing drawn until the next prepare. */
    void reset() {
        elapsed = -1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (elapsed < 0 || !world.hasOwnMotion()) return;
        long t = elapsed;
        float k = getWidth() / MOCKUP_W;
        switch (world) {
            case HOGWARTS:
                drawHogwarts(c, t, k);
                break;
            case MARVEL:
                drawMarvel(c, t, k);
                break;
            case STARWARS:
                drawStarWars(c, t, k);
                break;
            default:
                break;
        }
        float a = WorldLaunchMotion.blendAlpha(world, t);
        if (a > 0f) {
            blend.setAlpha(Math.round(255 * a));
            c.drawRect(0f, 0f, getWidth(), getHeight(), blend);
        }
    }

    private void drawHogwarts(Canvas c, long t, float k) {
        // The tile's glow comes up with its growth, the first 40 % of 1.1 s, then holds.
        float p = WorldLaunchMotion.clamp(t / 1100f);
        float g = p < 0.4f ? WorldLaunchMotion.ease(p / 0.4f) : 1f;
        if (g > 0f) {
            float hw = tileW * WorldLaunchMotion.tileScaleX(world, t, s0x) / 2f;
            float hh = tileH * WorldLaunchMotion.tileScaleY(world, t, s0y) / 2f;
            float r = radius * WorldLaunchMotion.tileScaleX(world, t, s0x);
            // The glow: 20 px spread plus half the 60 px blur, in rings fading outward.
            float reach = 50f * k, step = reach / GLOW_RINGS;
            glow.setStrokeWidth(step + 0.5f);
            for (int i = 0; i < GLOW_RINGS; i++) {
                float f = 1f - i / (float) GLOW_RINGS;
                glow.setAlpha(Math.round(230 * g * f * f * 0.5f));
                float o = step * (i + 0.5f);
                box.set(cx - hw - o, cy - hh - o, cx + hw + o, cy + hh + o);
                c.drawRoundRect(box, r + o, r + o, glow);
            }
            // The 3 px white rim on the tile's edge.
            float rimW = 3f * k;
            rim.setStrokeWidth(rimW);
            rim.setAlpha(Math.round(255 * g));
            box.set(cx - hw - rimW / 2f, cy - hh - rimW / 2f, cx + hw + rimW / 2f, cy + hh + rimW / 2f);
            c.drawRoundRect(box, r + rimW / 2f, r + rimW / 2f, rim);
        }
        // The swirl (hogSwirl 1.1s ease-in): 14 em (280 mockup px) wide, scale 0.3 to 2.4, 1.5 turns,
        // fading in to 30 % and out to the end.
        if (t < 1100) {
            float e = WorldLaunchMotion.easeIn(p);
            float alpha = p < 0.3f ? WorldLaunchMotion.easeIn(p / 0.3f)
                    : 1f - WorldLaunchMotion.easeIn((p - 0.3f) / 0.7f);
            if (alpha > 0f) {
                float rad = 140f * k * (0.3f + 2.1f * e);
                swirl.setAlpha(Math.round(255 * alpha));
                c.save();
                c.translate(cx, cy);
                c.rotate(540f * e);
                c.scale(rad, rad);
                c.drawCircle(0f, 0f, 1f, swirl);
                c.restore();
            }
        }
    }

    private void drawMarvel(Canvas c, long t, float k) {
        // Three rings from the tile's middle (ringout .9s ease-out, 0/150/300 ms): to 160 % of the screen's width
        // and 280 % of its height, fading out.
        ring.setStrokeWidth(2f * k);
        for (int i = 0; i < 3; i++) {
            long rt = t - 150L * i;
            if (rt < 0 || rt >= 900) continue;
            float e = WorldLaunchMotion.easeOut(rt / 900f);
            float rx = 0.8f * getWidth() * e, ry = 1.4f * getHeight() * e;
            ring.setAlpha(Math.round(230 * (1f - e)));
            box.set(cx - rx, cy - ry, cx + rx, cy + ry);
            c.drawOval(box, ring);
        }
        // The light streak (wipe .8s from .4s ease-in): 40 % wide, from -45 % to 110 % of the width, in by 30 %.
        long ft = t - 400;
        if (ft >= 0 && ft <= 800) {
            float e = WorldLaunchMotion.easeIn(ft / 800f);
            float alpha = e < 0.3f ? e / 0.3f : 1f;
            float w = 0.4f * getWidth();
            float left = (-0.45f + 1.55f * e) * getWidth();
            flare.setAlpha(Math.round(255 * alpha));
            c.save();
            c.translate(left, 0f);
            c.scale(w, 1f);
            c.drawRect(0f, 0f, 1f, getHeight(), flare);
            c.restore();
        }
    }

    private void drawStarWars(Canvas c, long t, float k) {
        // 70 streaks from the screen's middle (hyp .9s, cubic-bezier(.6,0,1,1)): 10 to 900 mockup px out, from 2 to
        // 240 px long, in by 30 % and out to the end.
        float mx = getWidth() / 2f, my = getHeight() / 2f;
        streak.setStrokeWidth(2f * k);
        for (int i = 0; i < STREAKS; i++) {
            long st = t - delay[i];
            if (st < 0 || st >= 900) continue;
            float p = st / 900f;
            float e = WorldLaunchMotion.bezier(0.6f, 0f, 1f, 1f, p);
            float alpha = p < 0.3f ? WorldLaunchMotion.bezier(0.6f, 0f, 1f, 1f, p / 0.3f)
                    : 1f - WorldLaunchMotion.bezier(0.6f, 0f, 1f, 1f, (p - 0.3f) / 0.7f);
            if (alpha <= 0f) continue;
            float d = (10f + 890f * e) * k, len = 2f * (1f + 119f * e) * k;
            streak.setAlpha(Math.round(255 * alpha));
            c.drawLine(mx + cos[i] * d, my + sin[i] * d, mx + cos[i] * (d + len), my + sin[i] * (d + len), streak);
        }
    }
}
