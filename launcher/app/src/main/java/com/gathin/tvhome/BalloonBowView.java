package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * The bow that ties the birthday balloons' strings to the dock (spec § 4.3): on Jahreskreis' birthday look the
 * strings painted in world_seasons_birthday.jpg run down to the dock's top edge at {@link #KNOT_X}, and this bow sits
 * there, over the dock.
 *
 * <p>Why a view of its own: the scene and its overlay (SeasonsOverlay) lie under the dock band, so a bow painted there
 * would be covered by the band. MainActivity adds this view inside oledLayer right under the focus frame (GlassView):
 * over the dock and its band, under the frame, and it moves with the dock when OledShift nudges the layer. It is still,
 * so it never ticks ({@link #idle}); it is gone on every other look and in every other world.
 *
 * <p>Built on WorldOverlay only for its mapping: the bow is placed in the mockup's 192 x 108 units as CENTER_CROP
 * places the picture, so it meets the painted strings.
 */
final class BalloonBowView extends WorldOverlay {
    /** Where the strings meet the dock's top edge (personal.py: KNOT_X, DOCK), units. */
    static final float KNOT_X = 69.12f, KNOT_Y = SeasonMotions.DOCK * 108;
    /** A loop's centre offset and half axes, the knot's radius and the tails' reach, units. */
    static final float LOOP_DX = 1.55f, LOOP_RX = 1.5f, LOOP_RY = 0.95f, KNOT_R = 0.55f, TAIL = 2.1f;
    /** The bow's colour: the birthday's pink (the mockup's #ff8fb1), its shade and its light. */
    private static final int BOW = 0xFFFF8FB1, SHADE = 0xFFC05A80, LIGHT = 0xFFFFC8DA;

    private Paint fill, shade, light;
    private RectF loop;
    private Path tails;

    BalloonBowView(Context context) {
        super(context, true);
    }

    /** Whether this look shows the bow: the birthday only. */
    static boolean shows(Look look) {
        return look == Look.BIRTHDAY;
    }

    /** Shows the bow on the birthday look, hides it on every other. */
    void setLook(Look look) {
        setVisibility(shows(look) ? VISIBLE : GONE);
    }

    @Override
    protected boolean idle() {
        return true;
    }

    @Override
    protected void layout(int w, int h) {
        if (fill == null) {
            fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(BOW);
            shade = new Paint(Paint.ANTI_ALIAS_FLAG);
            shade.setColor(SHADE);
            light = new Paint(Paint.ANTI_ALIAS_FLAG);
            light.setColor(LIGHT);
            loop = new RectF();
            tails = new Path();
        }
        // Two ribbon ends hanging from the knot over the dock's edge, round the knot at the origin.
        float u = unit;
        tails.rewind();
        tails.moveTo(-0.25f * u, 0f);
        tails.lineTo(-0.9f * u, TAIL * u);
        tails.lineTo(-0.35f * u, TAIL * 0.85f * u);
        tails.lineTo(0.05f * u, 0.2f * u);
        tails.close();
        tails.moveTo(0.25f * u, 0f);
        tails.lineTo(1.05f * u, TAIL * 0.92f * u);
        tails.lineTo(0.5f * u, TAIL * 0.8f * u);
        tails.lineTo(-0.05f * u, 0.2f * u);
        tails.close();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (fill == null) return;
        float u = unit;
        c.save();
        c.translate(ux(KNOT_X), uy(KNOT_Y) - 0.2f * u);
        c.drawPath(tails, shade);
        // The two loops, each tilted up and out, a darker inside and a light edge on top.
        for (int side = -1; side <= 1; side += 2) {
            c.save();
            c.rotate(side * -25f);
            loop.set(side * LOOP_DX * u - LOOP_RX * u, -LOOP_RY * u, side * LOOP_DX * u + LOOP_RX * u, LOOP_RY * u);
            c.drawOval(loop, fill);
            loop.inset(LOOP_RX * u * 0.45f, LOOP_RY * u * 0.45f);
            loop.offset(-side * 0.25f * u, 0f);
            c.drawOval(loop, shade);
            c.restore();
        }
        c.drawCircle(0f, 0f, KNOT_R * u, fill);
        c.drawCircle(-0.15f * u, -0.18f * u, KNOT_R * 0.45f * u, light);
        c.restore();
    }
}
