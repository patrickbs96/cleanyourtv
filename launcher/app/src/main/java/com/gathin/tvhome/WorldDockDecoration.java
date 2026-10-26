package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * The worlds' dock (spec § 4.4): Hogwarts' gold frame with the house stripe, Marvel's metal bar with rivets and six
 * dots, Star Wars' targeting scale. It extends {@link DockDecoration} and keeps its getItemOffsets, so the dock row's
 * geometry is the same as Clean's in every world; only onDraw differs. MainActivity uses it only where
 * {@link World#dockStyleSupported(Config)} holds (plain dock row, no single row), so the row is never a single row.
 * Every Paint, Path and shader is made once here: onDraw allocates nothing.
 */
public class WorldDockDecoration extends DockDecoration {

    /** Hogwarts' house stripe, in quarters from left to right. */
    private static final int[] HOUSE_COLORS = {0xFF7A1B1B, 0xFFC9A84C, 0xFF1B4D2E, 0xFF1E3A6B};
    /** Marvel's six dots above the bar (stone colours, ornament only). */
    private static final int[] STONE_COLORS = {0xFFA855F7, 0xFF3B82F6, 0xFFEF4444, 0xFFF59E0B, 0xFF22C55E, 0xFFF97316};
    /** Star Wars' scale: a tick every 3.2 % of the dock's width. */
    private static final float TICK_STEP = 0.032f;

    private final World world;
    private final int dockSize;
    private final float density;
    private final RectF rect = new RectF();
    private final RectF frame = new RectF();
    /** The band's vertical gradient, made for 0..1 and stretched over the band by a local matrix. */
    private final Matrix bandMatrix = new Matrix();
    private float matrixTop = Float.NaN, matrixBottom = Float.NaN;

    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerGold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ornament = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ornamentBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rivet = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint();
    private final Paint lineLit = new Paint();
    /** Hogwarts' "❖": four small diamonds round a centre, in coordinates round (0,0). */
    private final Path diamond = new Path();
    private final float glyph;

    /** The dock slot whose section of the Star Wars scale is lit, -1 for none. */
    private int focusedSlot = -1;

    /**
     * @param opacity the theme's dock_opacity; handed to the Clean band underneath, which this class never draws:
     *                a world's band wins over the theme's (spec § 4.1).
     */
    WorldDockDecoration(World world, int dockSize, float density, int opacity) {
        super(dockSize, density, false, false, opacity, false, false, true, false, false);
        this.world = world;
        this.dockSize = dockSize;
        this.density = density;
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(density);
        if (world == World.HOGWARTS) {
            band.setShader(new LinearGradient(0, 0, 0, 1, 0xD91C162C, 0xE60E0B18, Shader.TileMode.CLAMP));
            border.setColor(0xFFC9A84C);
            outer.setStyle(Paint.Style.STROKE);
            outer.setStrokeWidth(4 * density);
            outer.setColor(0xCC0A0814);
            outerGold.setStyle(Paint.Style.STROKE);
            outerGold.setStrokeWidth(density);
            outerGold.setColor(0x73C9A84C);
            ornament.setColor(0xFFC9A84C);
            ornamentBg.setColor(0xFF0D0A1A);
        } else if (world == World.MARVEL) {
            band.setShader(new LinearGradient(0, 0, 0, 1, 0xFF2A2A2E, 0xFF121214, Shader.TileMode.CLAMP));
            border.setColor(0xFF4A4A50);
            fill.setColor(0xFFE6B85A);
            // Drawn round (0,0) and moved there with the canvas, so the shader never changes.
            rivet.setShader(new RadialGradient(0, 0, 2.5f * density, 0xFF777777, 0xFF222222, Shader.TileMode.CLAMP));
        } else if (world == World.STARWARS) {
            line.setStrokeWidth(density);
            line.setColor(0x998FB3FF);
            lineLit.setStrokeWidth(density);
            lineLit.setColor(0xFFFFFFFF);
        }
        glyph = 12 * density;
        float a = glyph * 0.2f, d = glyph * 0.28f;
        addDiamond(0, -d, a);
        addDiamond(d, 0, a);
        addDiamond(0, d, a);
        addDiamond(-d, 0, a);
    }

    private void addDiamond(float cx, float cy, float a) {
        diamond.moveTo(cx, cy - a);
        diamond.lineTo(cx + a, cy);
        diamond.lineTo(cx, cy + a);
        diamond.lineTo(cx - a, cy);
        diamond.close();
    }

    /** Hogwarts' house colour at this fraction of the stripe's width (quarters: red, gold, green, blue). */
    static int stripeColorAt(float fraction) {
        int i = (int) (fraction * HOUSE_COLORS.length);
        return HOUSE_COLORS[Math.max(0, Math.min(HOUSE_COLORS.length - 1, i))];
    }

    /** Ticks on the Star Wars scale: one every 3.2 % of the dock's width, at least 3 dp apart. */
    static int tickCount(float dockWidthPx, float density) {
        float step = Math.max(TICK_STEP * dockWidthPx, 3f * density);
        return Math.max(0, (int) (dockWidthPx / step));
    }

    /** Lights the Star Wars scale under this dock slot; -1 (or a tile below the dock) lights none. */
    void setFocusedSlot(int slot) {
        focusedSlot = slot >= 0 && slot < dockSize ? slot : -1;
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        int cardWidth = 0;
        float focusL = 0, focusR = 0;
        boolean focusFound = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            cardWidth = card.getWidth();
            if (pos == focusedSlot) {
                focusL = l;
                focusR = r;
                focusFound = true;
            }
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        // As DockDecoration's band: 14 dp above and below, up to 22 dp at the sides, kept inside the grid together
        // with whatever the world draws outside the band (Hogwarts' 5 dp frame).
        float outside = world == World.HOGWARTS ? 5 * density : 0f;
        float room = Math.min(rect.left, parent.getWidth() - rect.right) - density - outside;
        float side = Math.max(0f, Math.min(22 * density, room));
        rect.inset(-side, -14 * density);
        float tileRadius = AppAdapter.cornerRadius(cardWidth, density);
        if (world == World.HOGWARTS) drawHogwarts(c, tileRadius);
        else if (world == World.MARVEL) drawMarvel(c, tileRadius);
        else if (world == World.STARWARS) drawStarWars(c, focusFound, focusL, focusR);
    }

    /** Stretches the band's 0..1 gradient over the band; the shader only changes when the band moves. */
    private void fitBandShader() {
        if (rect.top == matrixTop && rect.bottom == matrixBottom) return;
        bandMatrix.setScale(1f, Math.max(1f, rect.height()));
        bandMatrix.postTranslate(0f, rect.top);
        band.getShader().setLocalMatrix(bandMatrix);
        matrixTop = rect.top;
        matrixBottom = rect.bottom;
    }

    private void drawHogwarts(Canvas c, float tileRadius) {
        // The mockup's corners: 0.4em against the tiles' 0.5em.
        float radius = 0.8f * tileRadius;
        // Outer frame: 4 dp dark, then 1 dp gold at 45 %, right outside the band.
        float o = 2 * density;
        frame.set(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o);
        c.drawRoundRect(frame, radius + o, radius + o, outer);
        o = 4.5f * density;
        frame.set(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o);
        c.drawRoundRect(frame, radius + o, radius + o, outerGold);
        fitBandShader();
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, border);
        // "❖" on the top edge, 1em (22 dp) in from either end, on a dark patch that cuts the frame behind it.
        float inset = 22 * density + glyph / 2f;
        drawOrnament(c, rect.left + inset, rect.top);
        drawOrnament(c, rect.right - inset, rect.top);
        // The house stripe below the frame: 3 dp in four quarters at 90 %.
        float top = rect.bottom + 7 * density;
        float quarter = rect.width() / HOUSE_COLORS.length;
        for (int i = 0; i < HOUSE_COLORS.length; i++) {
            fill.setColor(HOUSE_COLORS[i]);
            fill.setAlpha(0xE6);
            c.drawRect(rect.left + i * quarter, top, rect.left + (i + 1) * quarter, top + 3 * density, fill);
        }
    }

    private void drawOrnament(Canvas c, float cx, float cy) {
        float half = glyph / 2f, pad = 4 * density;
        c.drawRect(cx - half - pad, cy - half, cx + half + pad, cy + half, ornamentBg);
        c.save();
        c.translate(cx, cy);
        c.drawPath(diamond, ornament);
        c.restore();
    }

    private void drawMarvel(Canvas c, float tileRadius) {
        // The mockup's corners: 0.3em against the tiles' 0.5em.
        float radius = 0.6f * tileRadius;
        fitBandShader();
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, border);
        // The 2 dp gold top edge follows the rounded corners: the band's shape, clipped to its top 2 dp.
        c.save();
        c.clipRect(rect.left, rect.top, rect.right, rect.top + 2 * density);
        c.drawRoundRect(rect, radius, radius, fill);
        c.restore();
        // Two rivets, 5 dp across, in the outer bottom corners.
        float cy = rect.bottom - 8 * density;
        drawRivet(c, rect.left + 9 * density, cy);
        drawRivet(c, rect.right - 9 * density, cy);
        // Six dots, 6 dp across, left above the bar, each with a soft halo of its own colour.
        float r = 3 * density, step = 11 * density;
        float y = rect.top - 10 * density;
        for (int i = 0; i < STONE_COLORS.length; i++) {
            float x = rect.left + r + i * step;
            dotGlow.setColor(STONE_COLORS[i]);
            dotGlow.setAlpha(0x40);
            c.drawCircle(x, y, r + 2.5f * density, dotGlow);
            dotGlow.setAlpha(0xFF);
            c.drawCircle(x, y, r, dotGlow);
        }
    }

    private void drawRivet(Canvas c, float cx, float cy) {
        c.save();
        c.translate(cx, cy);
        c.drawCircle(0, 0, 2.5f * density, rivet);
        c.restore();
    }

    private void drawStarWars(Canvas c, boolean focusFound, float focusL, float focusR) {
        // No band: a 1 dp line where the band's bottom would be, the scale under it, both white under the focus.
        float y = rect.bottom;
        c.drawLine(rect.left, y, rect.right, y, line);
        if (focusFound) c.drawLine(focusL, y, focusR, y, lineLit);
        int n = tickCount(rect.width(), density);
        if (n == 0) return;
        float step = rect.width() / n;
        float top = y + 2 * density, bottom = top + 6 * density;
        for (int i = 0; i < n; i++) {
            float x = rect.left + (i + 0.5f) * step;
            boolean lit = focusFound && x >= focusL && x <= focusR;
            c.drawLine(x, top, x, bottom, lit ? lineLit : line);
        }
    }
}
