package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * The walking company's silhouettes (spec § 4.2), drawn once per size as the mockup's figure() draws them, each kind
 * in {@link #FRAMES} step pictures side by side on one alpha sheet: the tall wanderer with a broad pointed hat, a long
 * coat and a gnarled staff; the small ones; the slender one with bow and quiver; the stocky dwarf with beard and
 * double axe over his shoulder; the warrior with a sword; the horse with its packs; the warrior with a round shield
 * on his back. Hooded coats stream back, legs stride. Generic wanderers, known only by how they go together.
 *
 * <p>The sheets are ALPHA_8: drawn with a paint, they take its colour (the scene's darkest tone) and its alpha (the
 * dimming behind the app's name), so a mood or a name changes nothing here. The soft edge (the mockup's shadow blur
 * of 0.06 of a figure in its own colour) is a blurred pass under the sharp one, drawn here once, not per frame.
 */
final class CompanySilhouettes {
    /** Step pictures per stride (CompanyMotion.STEP_MS). */
    static final int FRAMES = 16;
    /** What a cell holds round the feet, in figure heights: left, top, right, bottom (y up is negative). */
    private static final float U0 = -0.45f, V0 = -1.25f, U1 = 0.5f, V1 = 0.05f;

    private final Bitmap[] sheets = new Bitmap[CompanyMotion.KINDS];
    /** One cell's size and where the feet stand in it, in pixels; the figure height they were drawn for. */
    private int cellW, cellH;
    private float anchorX, anchorY, drawnFor;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint softFill = new Paint(Paint.ANTI_ALIAS_FLAG), softStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), part = new Path();
    private final RectF oval = new RectF();
    private final Matrix turn = new Matrix();
    /** The figure being drawn: its feet in pixels and its height; and its stride (sin) and the coat's sway. */
    private float x0, y0, s, sw, sway;
    private Paint f, k;

    CompanySilhouettes() {
        for (Paint p : new Paint[]{fill, softFill}) p.setStyle(Paint.Style.FILL);
        for (Paint p : new Paint[]{stroke, softStroke}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
    }

    /** Draws the sheets for figures {@code height} pixels high, unless they are drawn for that already. */
    void build(float height) {
        if (height <= 0f || (sheets[0] != null && height == drawnFor)) return;
        recycle();
        drawnFor = height;
        float blur = Math.max(1f, 0.045f * height), pad = 2f * blur;
        BlurMaskFilter soft = new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL);
        softFill.setMaskFilter(soft);
        softStroke.setMaskFilter(soft);
        cellW = (int) Math.ceil((U1 - U0) * height + 2 * pad);
        cellH = (int) Math.ceil((V1 - V0) * height + 2 * pad);
        anchorX = pad - U0 * height;
        anchorY = pad - V0 * height;
        for (int kind = 0; kind < CompanyMotion.KINDS; kind++) {
            Bitmap sheet = Bitmap.createBitmap(cellW * FRAMES, cellH, Bitmap.Config.ALPHA_8);
            Canvas c = new Canvas(sheet);
            for (int frame = 0; frame < FRAMES; frame++) {
                float ph = (float) (WorldOverlay.TAU * frame / FRAMES);
                // Soft first, then sharp on top: the mockup's shadow in the figure's own colour.
                figure(c, frame * cellW + anchorX, anchorY, height, kind, ph, softFill, softStroke);
                figure(c, frame * cellW + anchorX, anchorY, height, kind, ph, fill, stroke);
            }
            sheets[kind] = sheet;
        }
    }

    boolean isBuilt() {
        return sheets[0] != null;
    }

    Bitmap sheet(int kind) {
        return sheets[kind];
    }

    int cellW() {
        return cellW;
    }

    int cellH() {
        return cellH;
    }

    float anchorX() {
        return anchorX;
    }

    float anchorY() {
        return anchorY;
    }

    /** Step picture {@code frame}'s place on its sheet into {@code out}. */
    void cell(int frame, Rect out) {
        out.set(frame * cellW, 0, (frame + 1) * cellW, cellH);
    }

    void recycle() {
        for (int i = 0; i < sheets.length; i++) {
            if (sheets[i] != null) sheets[i].recycle();
            sheets[i] = null;
        }
        drawnFor = 0f;
    }

    // ---- the mockup's figure(): figure units, 1 = a tall figure's height, y up is negative, walking right --------

    private void figure(Canvas c, float x, float y, float height, int kind, float ph, Paint fillWith,
            Paint strokeWith) {
        x0 = x;
        y0 = y;
        s = height;
        sw = (float) Math.sin(ph);
        // The mockup sways the coats at half the stride; here at the stride, so the step pictures repeat.
        sway = 0.015f * (float) Math.sin(ph);
        f = fillWith;
        k = strokeWith;
        switch (kind) {
            case CompanyMotion.MAN:
                cloaked(c, 1f, 0.13f, 0.12f, 0.26f, 0.065f);
                begin();
                mv(-0.08f, -0.55f);
                ln(-0.2f, -0.75f);
                stroke(c, 0.02f);
                break;
            case CompanyMotion.SHIELD:
                cloaked(c, 1.02f, 0.14f, 0.1f, 0.22f, 0.066f);
                begin();
                circle(-0.1f, -0.66f, 0.12f);
                fill(c);
                break;
            case CompanyMotion.SMALL:
                cloaked(c, 0.56f, 0.15f, 0.1f, 0.2f, 0.085f);
                break;
            case CompanyMotion.ELF:
                cloaked(c, 1.04f, 0.1f, 0.14f, 0.14f, 0.06f);
                begin();
                // The bow: an arc round (-0.06, -0.62) from -1.25 to 1.25 radians.
                oval.set(px(-0.06f - 0.3f), py(-0.62f - 0.3f), px(-0.06f + 0.3f), py(-0.62f + 0.3f));
                path.addArc(oval, (float) Math.toDegrees(-1.25), (float) Math.toDegrees(2.5));
                stroke(c, 0.014f);
                begin();
                mv(-0.05f, -0.95f);
                ln(-0.12f, -0.62f);
                stroke(c, 0.03f);
                break;
            case CompanyMotion.DWARF:
                legs(c, -0.2f, 0.07f);
                begin();
                mv(-0.13f, -0.12f);
                qc(-0.18f, -0.45f, -0.12f, -0.56f);
                qc(0f, -0.66f, 0.12f, -0.56f);
                qc(0.17f, -0.4f, 0.13f, -0.12f);
                path.close();
                fill(c);
                begin();
                circle(0.02f, -0.62f, 0.07f);
                fill(c);
                begin();
                mv(0.04f, -0.6f);
                qc(0.12f, -0.5f, 0.05f, -0.4f);
                ln(0f, -0.55f);
                fill(c);
                begin();
                mv(-0.12f, -0.4f);
                ln(0.08f, -0.92f);
                stroke(c, 0.025f);
                // The double axe's two blades round the haft's top.
                begin();
                mv(0.08f, -0.92f);
                qc(0.2f, -0.98f, 0.2f, -0.84f);
                qc(0.13f, -0.86f, 0.08f, -0.92f);
                qc(-0.04f, -0.98f, -0.04f, -0.84f);
                qc(0.03f, -0.86f, 0.08f, -0.92f);
                fill(c);
                break;
            case CompanyMotion.WIZARD:
                legs(c, -0.25f, 0.04f);
                begin();
                mv(0.04f, -0.86f);
                qc(0.12f, -0.6f, 0.1f, -0.04f);
                ln(-0.3f + sway, -0.08f);
                qc(-0.2f, -0.5f, -0.15f, -0.82f);
                qc(-0.05f, -0.9f, 0.04f, -0.86f);
                fill(c);
                begin();
                circle(0f, -0.9f, 0.062f);
                fill(c);
                // The hat's broad brim, tilted a little, and its crown bending back to a point.
                begin();
                part.reset();
                oval.set(px(-0.01f - 0.17f), py(-0.93f - 0.028f), px(-0.01f + 0.17f), py(-0.93f + 0.028f));
                part.addOval(oval, Path.Direction.CW);
                turn.setRotate((float) Math.toDegrees(-0.08), px(-0.01f), py(-0.93f));
                part.transform(turn);
                path.addPath(part);
                fill(c);
                begin();
                mv(-0.08f, -0.94f);
                qc(-0.02f, -1.08f, 0.1f, -1.22f);
                qc(0.03f, -1.08f, 0.07f, -0.95f);
                path.close();
                fill(c);
                begin();
                mv(0.04f, -0.86f);
                qc(0.1f, -0.76f, 0.05f, -0.66f);
                ln(0.02f, -0.84f);
                fill(c);
                begin();
                mv(0.2f, 0f);
                ln(0.24f, -1.02f);
                stroke(c, 0.02f);
                begin();
                mv(0.24f, -1.02f);
                qc(0.3f, -1.08f, 0.27f, -1.12f);
                mv(0.24f, -1.02f);
                qc(0.19f, -1.08f, 0.22f, -1.13f);
                stroke(c, 0.012f);
                break;
            case CompanyMotion.HORSE: {
                float b = -0.42f;
                // Four legs, the diagonal pairs together.
                float[][] hooves = {{-0.24f, sw}, {-0.16f, -sw}, {0.16f, -sw}, {0.24f, sw}};
                for (float[] leg : hooves) {
                    begin();
                    mv(leg[0], b + 0.05f);
                    qc(leg[0] + leg[1] * 0.04f, b + 0.25f, leg[0] + leg[1] * 0.06f, 0f);
                    stroke(c, 0.045f);
                }
                begin();
                mv(-0.28f, b);
                qc(-0.3f, b - 0.14f, -0.1f, b - 0.15f);
                ln(0.2f, b - 0.16f);
                qc(0.27f, b - 0.2f, 0.32f, b - 0.38f);
                qc(0.36f, b - 0.44f, 0.44f, b - 0.4f);
                ln(0.45f, b - 0.35f);
                qc(0.38f, b - 0.3f, 0.34f, b - 0.14f);
                qc(0.32f, b + 0.02f, 0.2f, b + 0.07f);
                ln(-0.22f, b + 0.07f);
                qc(-0.32f, b + 0.04f, -0.28f, b);
                fill(c);
                begin();
                mv(-0.29f, b - 0.05f);
                qc(-0.4f, b + 0.05f, -0.38f, b + 0.22f);
                stroke(c, 0.03f);
                // The packs on its back, and an ear.
                begin();
                ellipse(-0.02f, b - 0.2f, 0.14f, 0.07f);
                ellipse(0.1f, b - 0.19f, 0.06f, 0.05f);
                fill(c);
                begin();
                mv(0.4f, b - 0.36f);
                ln(0.43f, b - 0.45f);
                ln(0.38f, b - 0.4f);
                fill(c);
                break;
            }
            default:
                break;
        }
    }

    /** Two striding legs from the hip down to the ground, {@code width} figure heights thick. */
    private void legs(Canvas c, float hip, float width) {
        begin();
        mv(0f, hip);
        ln(sw * 0.09f, 0f);
        mv(0f, hip);
        ln(-sw * 0.09f, 0f);
        stroke(c, width);
    }

    /** A hooded figure {@code h} high in a coat that streams back to the left (the mockup's cloaked()). */
    private void cloaked(Canvas c, float h, float shoulder, float hem, float trail, float head) {
        legs(c, -h * 0.3f, 0.045f * h);
        begin();
        mv(0.03f * h, -h * (1 - head * 2.2f));
        qc(0.09f * h, -h * 0.78f, 0.06f * h, -h * hem);
        ln(-trail * h + sway, -h * (hem - 0.05f));
        qc(-trail * 0.7f * h, -h * 0.55f, -shoulder * h, -h * 0.8f);
        qc(-shoulder * 0.4f * h, -h * (1 - head * 2.4f), 0.03f * h, -h * (1 - head * 2.2f));
        fill(c);
        // The head under the hood (the mockup meant head * h of a figure; it left the figure's scale out).
        begin();
        circle(0.02f * h, -h * (1 - head), head * h);
        fill(c);
        begin();
        mv(-0.03f * h, -h * (1 - head * 1.8f));
        qc(-0.07f * h, -h * (1 - head * 0.6f), -0.01f * h, -h * (1 + head * 0.1f));
        ln(0.03f * h, -h * (1 - head * 0.2f));
        fill(c);
    }

    private float px(float u) {
        return x0 + u * s;
    }

    private float py(float v) {
        return y0 + v * s;
    }

    private void begin() {
        path.reset();
    }

    private void mv(float u, float v) {
        path.moveTo(px(u), py(v));
    }

    private void ln(float u, float v) {
        path.lineTo(px(u), py(v));
    }

    private void qc(float cu, float cv, float u, float v) {
        path.quadTo(px(cu), py(cv), px(u), py(v));
    }

    private void circle(float u, float v, float r) {
        path.addCircle(px(u), py(v), r * s, Path.Direction.CW);
    }

    private void ellipse(float u, float v, float ru, float rv) {
        oval.set(px(u - ru), py(v - rv), px(u + ru), py(v + rv));
        path.addOval(oval, Path.Direction.CW);
    }

    private void fill(Canvas c) {
        c.drawPath(path, f);
    }

    private void stroke(Canvas c, float width) {
        k.setStrokeWidth(width * s);
        c.drawPath(path, k);
    }
}
