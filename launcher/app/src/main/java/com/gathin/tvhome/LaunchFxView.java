package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;

import java.util.Random;

/**
 * The worlds' app-start overlay (spec § 4.7), drawn over all of Home: Hogwarts' white-gold glow round the tile, its
 * conic spark swirl and the black of "Nox"; Marvel's three reactor rings, the anamorphic light streak and the cut to
 * white; Star Wars' 70 hyperspace streaks and the white; Supernatural's headlights, the lamp and its two cones growing
 * out of the car's front till their light fills the screen, and the white; Middle-earth's lettering of fire running
 * once round the tile's frame, and the white. The tile's own growth, the frame's fade and the scene's zoom are
 * WorldLaunchMotion's; this view only draws what lies over them, at the time {@link #setElapsed} gives it.
 *
 * <p>Middle-earth's signs are made up for this launcher (Mittelerde spec § 4.3: no font, no real script, no
 * inscription): seven strokes, hooks and rings of its own, built once as paths in a unit box and placed round the
 * frame per launch.
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
    private final Paint beam = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lamp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path unitCone = new Path();
    private final float[] cos = new float[STREAKS], sin = new float[STREAKS];
    private final long[] delay = new long[STREAKS];
    private final RectF box = new RectF();
    /** Middle-earth: the signs (unit box round the origin), their glow and core strokes, the spark at the fire's head. */
    private final Path[] glyphs = new Path[WorldLaunchMotion.GLYPHS];
    private final Paint fireGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fireCore = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spark = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The row round the frame, laid out once per launch (and view width): place, turn and share of the lap per sign. */
    private final float[] gx = new float[WorldLaunchMotion.MAX_GLYPHS], gy = new float[WorldLaunchMotion.MAX_GLYPHS],
            gdeg = new float[WorldLaunchMotion.MAX_GLYPHS], gpos = new float[WorldLaunchMotion.MAX_GLYPHS];
    private final float[] track = new float[3];
    private int glyphN, trackW = -1;
    private float glyphSize, trackLeft, trackTop, trackRight, trackBottom;

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
        // Supernatural's headlights in warm white: a cone from its apex at the origin to x = -1, y = ±1, its light
        // fading along it; the lamp's hot point and the wash its cones throw, radius 1 each.
        unitCone.moveTo(0f, 0f);
        unitCone.lineTo(-1f, -1f);
        unitCone.lineTo(-1f, 1f);
        unitCone.close();
        beam.setShader(new LinearGradient(0f, 0f, -1f, 0f, new int[]{0xF2FFF4DC, 0x8CFFF4DC, 0x00FFF4DC},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        lamp.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xFFFFFFF0, 0x99FFF4DC, 0x00FFF4DC},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        wash.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xFFFFF4DC, 0xB3FFF4DC, 0x00FFF4DC},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        // Middle-earth's fire: a wide soft stroke under a thin hot one, both round; the head's spark radius 1.
        fireGlow.setStyle(Paint.Style.STROKE);
        fireGlow.setStrokeCap(Paint.Cap.ROUND);
        fireGlow.setStrokeJoin(Paint.Join.ROUND);
        fireGlow.setStrokeWidth(0.34f);
        fireCore.setStyle(Paint.Style.STROKE);
        fireCore.setStrokeCap(Paint.Cap.ROUND);
        fireCore.setStrokeJoin(Paint.Join.ROUND);
        fireCore.setStrokeWidth(0.1f);
        spark.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xFFFFF8E0, 0xCCFF9A2A, 0x00FF6A10},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        buildGlyphs();
    }

    /** The seven made-up signs, each in a unit box round the origin (y down): no letter of any real script. */
    private void buildGlyphs() {
        for (int i = 0; i < glyphs.length; i++) glyphs[i] = new Path();
        // 0: a hook rising from the foot and curling over.
        glyphs[0].moveTo(-0.25f, 0.45f);
        glyphs[0].quadTo(-0.35f, -0.2f, 0.05f, -0.4f);
        glyphs[0].quadTo(0.35f, -0.45f, 0.2f, -0.1f);
        // 1: a zigzag of three strokes.
        glyphs[1].moveTo(-0.35f, 0.4f);
        glyphs[1].lineTo(-0.1f, -0.4f);
        glyphs[1].lineTo(0.1f, 0.2f);
        glyphs[1].lineTo(0.35f, -0.4f);
        // 2: a ring above a slanting foot.
        glyphs[2].addCircle(0.05f, -0.22f, 0.18f, Path.Direction.CW);
        glyphs[2].moveTo(0.02f, -0.04f);
        glyphs[2].lineTo(-0.2f, 0.45f);
        // 3: a chevron pointing down under a dot.
        glyphs[3].moveTo(-0.35f, -0.22f);
        glyphs[3].lineTo(0f, 0.4f);
        glyphs[3].lineTo(0.35f, -0.22f);
        glyphs[3].addCircle(0f, -0.38f, 0.05f, Path.Direction.CW);
        // 4: one long wave from top right to bottom left.
        glyphs[4].moveTo(0.3f, -0.45f);
        glyphs[4].cubicTo(-0.5f, -0.25f, 0.5f, 0.2f, -0.3f, 0.45f);
        // 5: a stem forking into a bowl.
        glyphs[5].moveTo(0f, 0.45f);
        glyphs[5].lineTo(0f, -0.05f);
        glyphs[5].moveTo(-0.3f, -0.4f);
        glyphs[5].quadTo(0f, 0.05f, 0.3f, -0.4f);
        // 6: a tick with a short bar beside it.
        glyphs[6].moveTo(-0.32f, -0.1f);
        glyphs[6].lineTo(-0.1f, 0.4f);
        glyphs[6].lineTo(0.05f, -0.45f);
        glyphs[6].moveTo(0.15f, 0.05f);
        glyphs[6].lineTo(0.36f, 0.05f);
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
        trackW = -1;   // the row round this tile is laid out at the first draw, when the view's width is known
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
            case SUPERNATURAL:
                drawSupernatural(c, t);
                break;
            case MIDDLE_EARTH:
                drawMiddleEarth(c, t, k);
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

    private void drawSupernatural(Canvas c, long t) {
        // The headlamp where the scene paints it (SupernaturalScene, 192 x 108 units mapped as the scene's
        // CENTER_CROP maps the picture, as WorldOverlay does).
        int w = getWidth(), h = getHeight();
        float u = Math.max(w / SupernaturalScene.UNITS_W, h / SupernaturalScene.UNITS_H);
        float hx = (w - SupernaturalScene.UNITS_W * u) / 2f + SupernaturalScene.CAR_HEADLIGHT[0] * u;
        float hy = (h - SupernaturalScene.UNITS_H * u) / 2f + SupernaturalScene.CAR_HEADLIGHT[1] * u;
        float reach = WorldLaunchMotion.beamReach(t);
        if (reach > 0f) {
            // The light the cones throw: a warm wash round the car that has grown over the whole screen by the white.
            float r = (12f + 300f * reach) * u;
            glowAt(c, wash, 0.75f * reach, hx, hy, r, r);
            // Two cones out of the front (the car faces left): the low beam down along the road, the high beam a
            // little up, both opening until they reach past the screen's edges.
            float len = (8f + 200f * reach) * u;
            cone(c, hx, hy, -7f, len, 0.16f + 1.3f * reach, reach);
            cone(c, hx, hy, 3f, 1.1f * len, 0.10f + 1.0f * reach, 0.8f * reach);
        }
        glowAt(c, lamp, WorldLaunchMotion.lampGlow(t), hx, hy, 3.2f * u, 2.2f * u);
    }

    /**
     * Middle-earth: the signs stand on a track just outside the tile's frame (at its focus zoom), as many as fit; each
     * burns as the fire's head reaches it and glows on as an ember, a spark rides the head, and the ring flares up
     * before the white. Glow and core are one path each per sign, coloured by its heat; nothing is allocated.
     */
    private void drawMiddleEarth(Canvas c, long t, float k) {
        if (trackW != getWidth()) layoutTrack(k);
        float tw = trackRight - trackLeft, th = trackBottom - trackTop;
        for (int i = 0; i < glyphN; i++) {
            float heat = WorldLaunchMotion.glyphHeat(gpos[i], t);
            if (heat <= 0f) continue;
            // A light flicker per sign, as a flame's, on a fixed phase each.
            float lit = heat * (0.9f + 0.1f * (float) Math.sin(t * 0.031f + i * 1.7f));
            int color = WorldLaunchMotion.fireColor(heat);
            c.save();
            c.translate(gx[i], gy[i]);
            c.rotate(gdeg[i]);
            c.scale(glyphSize, glyphSize);
            fireGlow.setColor(color);
            fireGlow.setAlpha(Math.round(255 * 0.4f * WorldLaunchMotion.clamp(lit)));
            c.drawPath(glyphs[WorldLaunchMotion.glyphAt(i)], fireGlow);
            fireCore.setColor(color);
            fireCore.setAlpha(Math.round(255 * WorldLaunchMotion.clamp(lit)));
            c.drawPath(glyphs[WorldLaunchMotion.glyphAt(i)], fireCore);
            c.restore();
        }
        if (t < WorldLaunchMotion.FIRE_LAP_MS) {
            WorldLaunchMotion.trackAt(tw, th, WorldLaunchMotion.fireHead(t), track);
            float r = 0.9f * glyphSize;
            glowAt(c, spark, 1f, trackLeft + track[0], trackTop + track[1], r, r);
        }
    }

    /** Places the row round this launch's tile: once per launch and view width, into the arrays made at the start. */
    private void layoutTrack(float k) {
        trackW = getWidth();
        glyphSize = 16f * k;
        float hw = tileW * s0x / 2f, hh = tileH * s0y / 2f, gap = 0.75f * glyphSize;
        trackLeft = cx - hw - gap;
        trackTop = cy - hh - gap;
        trackRight = cx + hw + gap;
        trackBottom = cy + hh + gap;
        float tw = trackRight - trackLeft, th = trackBottom - trackTop;
        glyphN = WorldLaunchMotion.glyphCount(2f * (tw + th), 1.3f * glyphSize);
        for (int i = 0; i < glyphN; i++) {
            gpos[i] = (i + 0.5f) / glyphN;
            WorldLaunchMotion.trackAt(tw, th, gpos[i], track);
            gx[i] = trackLeft + track[0];
            gy[i] = trackTop + track[1];
            gdeg[i] = track[2];
        }
    }

    /**
     * One light cone from (x, y), turned {@code deg} from straight left, {@code len} px long and opening to
     * {@code spread} × len on either side at its end; soft round its edge (a wider, fainter cone under it).
     */
    private void cone(Canvas c, float x, float y, float deg, float len, float spread, float alpha) {
        float a = WorldLaunchMotion.clamp(alpha);
        c.save();
        c.translate(x, y);
        c.rotate(deg);
        c.scale(len, len * spread * 1.5f);
        beam.setAlpha(Math.round(255 * 0.35f * a));
        c.drawPath(unitCone, beam);
        c.restore();
        c.save();
        c.translate(x, y);
        c.rotate(deg);
        c.scale(len, len * spread);
        beam.setAlpha(Math.round(255 * a));
        c.drawPath(unitCone, beam);
        c.restore();
    }

    /** A radial glow of radius 1 drawn as an ellipse round (x, y) with these half axes in px, at this alpha. */
    private static void glowAt(Canvas c, Paint p, float alpha, float x, float y, float rx, float ry) {
        float a = WorldLaunchMotion.clamp(alpha);
        if (a <= 0f) return;
        p.setAlpha(Math.round(255 * a));
        c.save();
        c.translate(x, y);
        c.scale(rx, ry);
        c.drawCircle(0f, 0f, 1f, p);
        c.restore();
    }
}
