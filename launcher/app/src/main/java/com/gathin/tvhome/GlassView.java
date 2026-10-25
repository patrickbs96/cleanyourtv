package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A translucent glass slab: soft sheen fill, bright top specular, crisp rim and a faint outer glow.
 * One instance floats over the grid and is moved with spring animations between tiles.
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;
    /** Glow colour as 0xRRGGBB: white normally, a festival's accent (diya gold) while a festive skin is on. */
    private int glowRgb = 0xFFFFFF;
    /** Glow strength: the white glow stays faint, a coloured one needs more alpha to read as warm light. */
    private int glowAlpha = 0x30;
    /** glass_style rim or halo: no sheen and no fill, only the crisp rim (and, for halo, a lighter outer glow). */
    private boolean flat;
    private boolean halo;
    /** Marvel's target brackets: four corner angles round the slab, in the rim's colour (spec § 4.4). */
    private boolean brackets;
    /** How far the brackets stand off the slab: 8 dp while open, 2 dp once locked on. */
    private float bracketOut;
    private final Paint bracket = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The eight bracket legs as drawLines pairs, filled in onDraw without allocating. */
    private final float[] bracketLines = new float[32];
    /** Room round the slab: the glow's 6 dp, more while the open brackets need it. */
    private float inset;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        inset = 6f * density;
        bracketOut = 2f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0xCCFFFFFF);
        bracket.setStyle(Paint.Style.STROKE);
        bracket.setStrokeWidth(2f * density);
        bracket.setStrokeCap(Paint.Cap.SQUARE);
        glow.setStyle(Paint.Style.STROKE);
        // Cache the slab as a texture. The spring only changes translation, so the GPU can
        // move it without replaying clipPath and the glow on every frame. That path is
        // software-rendered on the MT5895 and is what makes the highlight crawl.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** Tints the rim and the outer glow; 0 restores the plain white glass. */
    void setAccent(int argb) {
        setAccent(argb, false);
    }

    /**
     * As {@link #setAccent(int)}; exact (the worlds, spec § 4.4) paints the rim in the accent itself instead of lifting
     * it towards white. The glow is the same either way.
     */
    void setAccent(int argb, boolean exact) {
        if (exact && argb != 0) {
            glowRgb = argb & 0xFFFFFF;
            glowAlpha = 0x70;
            rim.setColor(argb);
        } else if (argb == 0) {
            glowRgb = 0xFFFFFF;
            glowAlpha = 0x30;
            rim.setColor(0xCCFFFFFF);
        } else {
            glowRgb = argb & 0xFFFFFF;
            glowAlpha = 0x70;
            // The rim is the accent lifted towards white, so the edge still reads as glass, not paint.
            int r = (((glowRgb >> 16) & 0xFF) + 255 * 2) / 3, g = (((glowRgb >> 8) & 0xFF) + 255 * 2) / 3,
                    b = ((glowRgb & 0xFF) + 255 * 2) / 3;
            rim.setColor(0xE6000000 | (r << 16) | (g << 8) | b);
        }
        invalidate();
    }

    /** One of Config's GLASS_* styles. */
    void setStyle(String style) {
        flat = Config.GLASS_RIM.equals(style) || Config.GLASS_HALO.equals(style);
        halo = Config.GLASS_HALO.equals(style);
        rim.setStrokeWidth((flat ? 2f : 1.5f) * density);
        invalidate();
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    /**
     * Marvel's target brackets on or off. On, the view keeps 10 dp round the slab instead of 6 dp, so the open
     * brackets (8 dp out, 2 dp thick) are not cut off; MainActivity sizes the view with {@link #insetPx()}.
     */
    void setBrackets(boolean on) {
        brackets = on;
        inset = (on ? 10f : 6f) * density;
        invalidate();
    }

    /** Locked brackets sit 2 dp off the slab, open ones 8 dp. Task 8 animates between the two. */
    void setBracketsLocked(boolean locked) {
        bracketOut = (locked ? 2f : 8f) * density;
        invalidate();
    }

    /** The room this view keeps round the slab on every side: 6 dp, or 10 dp with the brackets on. */
    float insetPx() {
        return inset;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0x3DFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
        specular.setShader(new LinearGradient(0, 0, 0, h * 0.45f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Room for the glow (and, with the brackets on, for the open brackets); the glow itself always spans 6 dp.
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float glowRoom = 6f * density;

        // Outer glow: thin rings across the inset, each a little fainter, so it fades out smoothly. Three wide
        // strokes left visible bands once the glow took an app's colour at a stronger alpha. The hardware layer
        // caches the result, so the rings cost one draw, not one per frame of the spring.
        if (!flat || halo) {
            int rings = 12;
            float step = glowRoom / rings;
            int alpha = flat ? glowAlpha / 2 : glowAlpha;
            glow.setStrokeWidth(step + 0.5f);
            for (int i = 0; i < rings; i++) {
                float o = step * (i + 0.5f);
                float t = 1f - (float) i / rings;
                glow.setColor(Math.round(alpha * t) << 24 | glowRgb);
                canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, glow);
            }
        }
        if (flat) {
            canvas.drawRoundRect(rect, radius, radius, rim);
            if (brackets) drawBrackets(canvas);
            return;
        }

        canvas.drawRoundRect(rect, radius, radius, fill);

        // Sheen across the top 45%: clipRect stays on the GPU (clipPath did not on the MT5895) and
        // the round rect under it keeps the top corners rounded with a flat bottom edge.
        canvas.save();
        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f);
        canvas.drawRoundRect(rect, radius, radius, specular);
        canvas.restore();

        canvas.drawRoundRect(rect, radius, radius, rim);
        if (brackets) drawBrackets(canvas);
    }

    /** Four corner angles bracketOut off the slab, each leg 30 % of its edge, 2 dp thick in the rim's colour. */
    private void drawBrackets(Canvas canvas) {
        float l = rect.left - bracketOut, t = rect.top - bracketOut;
        float r = rect.right + bracketOut, b = rect.bottom + bracketOut;
        float lx = (r - l) * 0.3f, ly = (b - t) * 0.3f;
        float[] p = bracketLines;
        int i = 0;
        // Top left, top right, bottom right, bottom left: a horizontal and a vertical leg each.
        i = leg(p, i, l, t, l + lx, t);
        i = leg(p, i, l, t, l, t + ly);
        i = leg(p, i, r, t, r - lx, t);
        i = leg(p, i, r, t, r, t + ly);
        i = leg(p, i, r, b, r - lx, b);
        i = leg(p, i, r, b, r, b - ly);
        i = leg(p, i, l, b, l + lx, b);
        leg(p, i, l, b, l, b - ly);
        bracket.setColor(rim.getColor());
        canvas.drawLines(p, bracket);
    }

    private static int leg(float[] p, int i, float x0, float y0, float x1, float y1) {
        p[i] = x0;
        p[i + 1] = y0;
        p[i + 2] = x1;
        p[i + 3] = y1;
        return i + 4;
    }
}
