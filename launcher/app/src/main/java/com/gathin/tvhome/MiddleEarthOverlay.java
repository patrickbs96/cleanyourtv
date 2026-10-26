package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;

import java.time.LocalDate;
import java.util.Random;

/**
 * Middle-earth's motion (spec § 4.1) over its three pictures (MiddleEarthScene holds where they paint what). In the
 * morning smoke rises from the chimney and mist drifts over the fields; by day mist drifts through the valley; by
 * night the crater glows and throws sparks, the lava channels glimmer, ash falls, and on the tower's crown the eye
 * pulses while its cone of light sweeps slowly over the land, about every 18 s from 0.9 radians on one side of
 * straight down to 0.9 on the other.
 *
 * <p>In every mood the company (spec § 4.2) walks on the dock's top edge in the scene's darkest tone, left to right
 * once in about 6 minutes, then pauses for 2 to 5; behind the focused app's name (setHeroName) it shows at a quarter.
 * Its step pictures are drawn once per size (CompanySilhouettes), its clock is its own (CompanyMotion) and is kept for
 * the process, so it goes on where it stood after onPause, a screensaver or Home built anew. Stopped, it stands; with
 * reduced motion or animations off, when it never walked, it stands in the middle of the screen.
 *
 * <p>The household's own logo picture (WorldLogo, spec § 4.4) stands in the free area at the top middle, the same
 * place in all three moods, at 85 % brightness; without one there is no lettering. The logo and, by night, the eye
 * are still, so they are drawn whether or not the motion runs: with reduced motion (or paused) the overlay never
 * ticks, and its one drawing is the logo and the eye at rest with its cone straight down (coneAngle, eyePulse).
 *
 * <p>Everything keeps to its place: the sparks and the crater's glow end below the header, the ash leaves the header
 * out, smoke and the eye's glow end below the pills, the mist stays above the dock and the cone points down.
 */
final class MiddleEarthOverlay extends WorldOverlay {
    /** The cone's sweep: once to and fro in about 18 s (the mockup's sin(t * 0.35)), 0.9 radians either side. */
    static final long CONE_PERIOD_MS = 18000;
    static final float CONE_SWEEP = 0.9f;
    /** The cone's length and its half width at the far end, in units (the mockup's 0.6 and 0.04 of the width). */
    static final float CONE_LEN = 115f, CONE_HALF = 7.7f;
    /** The eye's pulse: its strength between EYE_MIN and 1 over one breath; EYE_REST while nothing moves. */
    static final long EYE_PULSE_MS = 2700;
    static final float EYE_MIN = 0.7f, EYE_REST = 0.85f;
    /**
     * The eye's glow, half axes in units: the mockup's 0.07 of the width round the eye. Its top (y 25.6) stays just
     * below the pills (PersonalMotions.PILLS, bottom y 23), its left (x 132.6) right of the logo's free area.
     */
    static final float EYE_GLOW_RX = 0.07f * MiddleEarthScene.UNITS_W, EYE_GLOW_RY = EYE_GLOW_RX;
    /**
     * Smoke: how far a puff rises and drifts right over its life (units), its radius at the end, its life. The chimney
     * (x 171) stands under the pills (x 128 to 192), so the smoke ends one unit below them, at y 24 (the mockup's
     * 0.2 of the height would reach y 21).
     */
    static final float SMOKE_RISE = 19f, SMOKE_DRIFT = 4f, SMOKE_R_MIN = 1.0f, SMOKE_R_MAX = 2.6f;
    static final long SMOKE_LIFE_MS = 12500;
    private static final int PUFFS = 10;
    /** Mist: the puffs' half axes in units; MORNING and DAY have their bands (MiddleEarthScene.MIST_*). */
    static final float MIST_RX_MORNING = 26f, MIST_RX_DAY = 32f, MIST_RY = 4.5f;
    private static final int MIST_MORNING_PUFFS = 6, MIST_DAY_PUFFS = 8;
    /** The crater's glow: an ellipse round (CRATER x, CRATER_GLOW_Y), its top below the header. */
    static final float CRATER_GLOW_Y = MiddleEarthScene.CRATER[1] + 0.5f, CRATER_GLOW_RX = 12f, CRATER_GLOW_RY = 6f;
    /** Sparks rise from the crater's rim this far (units), no higher than the header's bottom, and fade out. */
    static final float EMBER_RISE = MiddleEarthScene.CRATER[1] - MiddleEarthScene.HEADER[3] - 0.3f;
    static final long EMBER_LIFE_MS = 4200;
    private static final int EMBERS = 24;
    private static final float EMBER_SIZE = 0.28f;
    /** Ash: flakes falling over the scene above the dock, slowly, drifting right. */
    private static final int ASH = 40;
    /** The company's figure height in units: a tenth of the picture's (the mockup's S). */
    static final float COMPANY_S = 0.1f * MiddleEarthScene.UNITS_H;
    /** Today's company, kept over Home's rebuilds (recreate) so it walks on from where it stood. */
    private static CompanyMotion sharedCompany;

    private final CompanyMotion company;
    private final CompanySilhouettes silhouettes = new CompanySilhouettes();
    /** The silhouettes' paint: the mood's darkest tone, its alpha the dimming at the figure. */
    private final Paint companyPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    /**
     * The night's warm back light round the figures (CompanySilhouettes' rim sheets): the crater glow's orange, as if
     * the volcano lit them from behind; its alpha CompanyMotion.rimFor times the figure's.
     */
    static final int COMPANY_RIM = 0xFFFF7828;
    private final Paint rimPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect cell = new Rect();
    private final RectF place = new RectF();
    /** The focused app's name on screen, left and right in view pixels; -1 for none. */
    private float nameLeft = -1f, nameRight = -1f;
    /** The area before the last change and when it changed: the dimming blends from it (CompanyMotion.blendAlpha). */
    private float fromLeft = -1f, fromRight = -1f;
    private long nameAt;

    private final float[] puffX = new float[PUFFS], puffS = new float[PUFFS];
    private final float[] mistX = new float[MIST_DAY_PUFFS], mistY = new float[MIST_DAY_PUFFS],
            mistS = new float[MIST_DAY_PUFFS];
    private final float[] emberX = new float[EMBERS], emberP = new float[EMBERS], emberS = new float[EMBERS];
    private final int[] emberColor = new int[EMBERS];
    private final float[] ashX = new float[ASH], ashY = new float[ASH], ashS = new float[ASH];
    /** The ash's flakes in view pixels this frame, the fainter and the stronger ones apart. */
    private final float[] ashFaint = new float[ASH * 2], ashStrong = new float[ASH * 2];

    /** Radial glows of radius 1, each scaled to its ellipse. */
    private final Paint smoke = glowPaint(0xFFEBEBE1), mistMorning = glowPaint(0xFFF0F0E1),
            mistDay = glowPaint(0xFFE1EBEB), craterGlow = glowPaint(0xFFFF7828), eyeGlow = glowPaint(0xFFFF781E);
    private final Paint lava = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ember = new Paint();
    private final Paint ashFaintPaint = new Paint(), ashStrongPaint = new Paint();
    private final Paint cone = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeRing = new Paint(Paint.ANTI_ALIAS_FLAG), eyeInner = new Paint(Paint.ANTI_ALIAS_FLAG),
            eyeCore = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path lavaPath = new Path(), conePath = new Path();
    private final RectF oval = new RectF(), logoRect = new RectF();
    private final float[] box = new float[4];

    private WorldScene.Mood mood;
    /** The logo picture, or null: then there is none. Read once, at the first layout. */
    private Bitmap logo;
    private boolean logoRead;
    /** 1080-line pixels to view pixels, and the dock's top edge in view pixels. */
    private float px, dockPx;

    MiddleEarthOverlay(Context context, boolean day) {
        super(context, day);
        mood = day ? WorldScene.Mood.DAY : WorldScene.Mood.NIGHT;
        company = companyFor(LocalDate.now().toEpochDay());
        Random seeded = new Random(0x4D45L);
        for (int i = 0; i < PUFFS; i++) {
            puffX[i] = (i + seeded.nextFloat() * 0.5f) / PUFFS;
            puffS[i] = seeded.nextFloat();
        }
        for (int i = 0; i < MIST_DAY_PUFFS; i++) {
            mistX[i] = seeded.nextFloat();
            mistY[i] = seeded.nextFloat();
            mistS[i] = seeded.nextFloat();
        }
        for (int i = 0; i < EMBERS; i++) {
            emberX[i] = seeded.nextFloat();
            emberP[i] = seeded.nextFloat();
            emberS[i] = seeded.nextFloat();
            emberColor[i] = 0xFFFF003C | (150 + Math.round(emberS[i] * 80)) << 8;
        }
        for (int i = 0; i < ASH; i++) {
            ashX[i] = seeded.nextFloat();
            ashY[i] = seeded.nextFloat();
            ashS[i] = seeded.nextFloat();
        }
        lava.setStyle(Paint.Style.STROKE);
        lava.setStrokeCap(Paint.Cap.ROUND);
        lava.setStrokeJoin(Paint.Join.ROUND);
        lava.setColor(0xFFFF6A20);
        ashFaintPaint.setColor(0xFF3C2824);
        ashFaintPaint.setAlpha(120);
        ashStrongPaint.setColor(0xFF3C2824);
        ashStrongPaint.setAlpha(190);
        for (Paint p : new Paint[]{ashFaintPaint, ashStrongPaint}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.SQUARE);
        }
        eyeRing.setColor(0xFFFFB040);
        eyeInner.setColor(0xFFFF6A10);
        eyeCore.setColor(0xFF100404);
        rimPaint.setColor(COMPANY_RIM);
        // At most 85 % brightness (spec § 4.4): 216 / 255 is just under.
        logoPaint.setColorFilter(new LightingColorFilter(0xFFD8D8D8, 0));
    }

    /** The scene's mood as it shows it: morning, day and night each have their own set. */
    @Override
    void setMood(WorldScene.Mood mood) {
        if (mood == null || this.mood == mood) return;
        this.mood = mood;
        day = mood.isDay();
        if (getWidth() > 0) layout(getWidth(), getHeight());
        invalidate();
    }

    /** Day or night alone (the other overlays' way in): a morning stays a morning by day. */
    @Override
    void setDay(boolean day) {
        if (mood.isDay() == day) return;
        setMood(day ? WorldScene.Mood.DAY : WorldScene.Mood.NIGHT);
    }

    /**
     * Stopped (onPause, reduced motion, a screensaver): what moves goes, the logo and the eye at rest stay, and the
     * company stands where it got to.
     */
    @Override
    void stop() {
        // Only if this overlay started the clock: an old one's late stop after a recreate leaves the new one's be.
        company.stop(SystemClock.uptimeMillis(), this);
        super.stop();
        invalidate();
    }

    @Override
    protected void onStart(long now) {
        company.start(now, this);
    }

    @Override
    protected void onFrame(long now) {
        company.advance(now);
    }

    /**
     * The focused app's name above the dock, its left and right edge in this view's pixels (MainActivity, on every
     * focus change and layout of the name); -1, -1 while none shows. The company dims behind it.
     */
    void setHeroName(float left, float right) {
        if (left == nameLeft && right == nameRight) return;
        // From the area as it stands (a blend under way ends where it was heading) over to the new one.
        fromLeft = nameLeft;
        fromRight = nameRight;
        nameLeft = left;
        nameRight = right;
        nameAt = SystemClock.uptimeMillis();
        invalidate();
    }

    /** Today's company: the one already walking, or a new one for a new day. */
    private static CompanyMotion companyFor(long day) {
        if (sharedCompany == null || sharedCompany.day != day) sharedCompany = new CompanyMotion(day);
        return sharedCompany;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (logo != null) logo.recycle();
        logo = null;
        // Attached again, the next layout reads the picture afresh (it may have come or gone meanwhile).
        logoRead = false;
    }

    @Override
    protected void layout(int w, int h) {
        px = h / 1080f;
        dockPx = uy(MiddleEarthScene.DOCK);
        float[] l = MiddleEarthScene.LOGO;
        if (!logoRead) {
            logoRead = true;
            // Decoded here, once: Home's first frame already shows it. Twice the place's size at most, so it is
            // drawn sharp, a little scaled down.
            logo = WorldLogo.load(getContext(), World.MIDDLE_EARTH, (int) Math.ceil(2 * l[2] * unit),
                    (int) Math.ceil(2 * l[3] * unit));
        }
        if (logo != null) {
            logoBox(logo.getWidth(), logo.getHeight(), box);
            logoRect.set(ux(box[0]), uy(box[1]), ux(box[2]), uy(box[3]));
        }
        for (int i = 0; i < 4; i++) regions[i].setEmpty();
        // The company's step pictures, drawn once for this size (a mood changes only the paint's colour), and the
        // band over the dock's top edge it walks in, in every mood.
        silhouettes.build(COMPANY_S * unit);
        companyPaint.setColor(MiddleEarthScene.darkest(mood));
        // By night the warm back light's rim sheets too, drawn once the first night comes (kept for later nights).
        if (CompanyMotion.rimFor(mood) > 0f) silhouettes.buildRim();
        if (silhouettes.isBuilt()) {
            float top = dockPx - silhouettes.anchorY();
            regions[3].set(0, (int) Math.floor(top), w, (int) Math.ceil(top + silhouettes.placeH()));
        }
        switch (mood) {
            case MORNING: {
                float[] c = MiddleEarthScene.CHIMNEY_TOP;
                float[] m = MiddleEarthScene.MIST_MORNING;
                regions[0].set((int) Math.floor(ux(c[0] - 2f - SMOKE_R_MAX)),
                        (int) Math.floor(uy(c[1] - SMOKE_RISE - SMOKE_R_MAX - 0.5f)),
                        (int) Math.ceil(ux(c[0] + 2f + SMOKE_DRIFT + SMOKE_R_MAX)),
                        (int) Math.ceil(uy(c[1] + SMOKE_R_MIN + 0.5f)));
                regions[1].set(0, (int) Math.floor(uy(m[0] - MIST_RY - 0.5f)), w,
                        (int) Math.ceil(uy(m[1] + MIST_RY + 0.5f)));
                return;
            }
            case DAY: {
                float[] m = MiddleEarthScene.MIST_DAY;
                regions[0].set(0, (int) Math.floor(uy(m[0] - MIST_RY - 0.5f)), w,
                        (int) Math.ceil(uy(m[1] + MIST_RY + 0.5f)));
                return;
            }
            default:
                break;
        }
        // At night the ash falls over all of the scene above the dock, and everything else that moves lies in it.
        regions[0].set(0, 0, w, (int) Math.ceil(dockPx));
        float flake = Math.max(1f, 2f * px);
        ashFaintPaint.setStrokeWidth(flake);
        ashStrongPaint.setStrokeWidth(flake);
        lava.setStrokeWidth(Math.max(1f, MiddleEarthScene.LAVA_W * 1.8f * unit));
        lavaPath.reset();
        for (float[] channel : MiddleEarthScene.LAVA) {
            lavaPath.moveTo(ux(channel[0]), uy(channel[1]));
            for (int i = 2; i < channel.length; i += 2) lavaPath.lineTo(ux(channel[i]), uy(channel[i + 1]));
        }
        // The cone along +x from the eye, turned while drawing: full at the eye, gone at its far end.
        float len = CONE_LEN * unit, half = CONE_HALF * unit;
        conePath.reset();
        conePath.moveTo(0f, 0f);
        conePath.lineTo(len, -half);
        conePath.lineTo(len, half);
        conePath.close();
        cone.setShader(new LinearGradient(0f, 0f, len, 0f, 0x4DFFAA3C, 0x00FFAA3C, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        boolean moving = isRunning();
        long now = SystemClock.uptimeMillis();
        if (moving) {
            if (mood == WorldScene.Mood.MORNING) drawMorning(c, now);
            else if (mood == WorldScene.Mood.DAY) drawMist(c, now, MiddleEarthScene.MIST_DAY, mistDay, MIST_DAY_PUFFS,
                    MIST_RX_DAY, 0.22f, 1.54f);
            else drawNight(c, now);
        }
        // The eye at night, pulsing and sweeping while the motion runs, at rest otherwise.
        if (!day && getWidth() > 0) drawEye(c, now, moving);
        drawCompany(c, now);
        if (logo != null) c.drawBitmap(logo, null, logoRect, logoPaint);
    }

    /**
     * The company on the dock's top edge, from the last figure to the wizard ahead, each in its step picture and
     * dimmed behind the app's name at its feet; nothing during the pause.
     */
    private void drawCompany(Canvas c, long now) {
        if (!company.isVisible() || !silhouettes.isBuilt()) return;
        float w = getWidth(), s = COMPANY_S * unit;
        // Walking along its crossing, or standing in the middle while its clock never ran (reduced motion).
        float head = company.headX(w, s);
        long walked = company.walkedMs();
        float cw = silhouettes.placeW(), top = dockPx - silhouettes.anchorY();
        // A new name's dimmed area comes in over NAME_BLEND_MS while the motion runs (its ticks draw it), at once else.
        float blend = isRunning() ? (now - nameAt) / (float) CompanyMotion.NAME_BLEND_MS : 1f;
        // By night: not dimmed behind the name, a little see-through everywhere, and outlined by the warm back light
        // of the rim sheets.
        float dim = CompanyMotion.dimFor(mood), rim = silhouettes.hasRim() ? CompanyMotion.rimFor(mood) : 0f;
        float strength = CompanyMotion.strengthFor(mood);
        for (int i = 0; i < CompanyMotion.COMPANY.length; i++) {
            float x = head - CompanyMotion.OFFSETS[i] * s, left = x - silhouettes.anchorX();
            if (left + cw < 0f || left > w) continue;
            float alpha = strength
                    * CompanyMotion.blendAlpha(x, fromLeft, fromRight, nameLeft, nameRight, w, blend, dim);
            companyPaint.setAlpha(Math.round(255 * alpha));
            silhouettes.cell(CompanyMotion.stepFrame(walked, i, CompanySilhouettes.FRAMES), cell);
            place.set(left, top, left + cw, top + silhouettes.placeH());
            c.drawBitmap(silhouettes.sheet(CompanyMotion.COMPANY[i]), cell, place, companyPaint);
            if (rim > 0f) {
                rimPaint.setAlpha(Math.round(255 * rim * alpha));
                c.drawBitmap(silhouettes.rim(CompanyMotion.COMPANY[i]), cell, place, rimPaint);
            }
        }
    }

    private void drawMorning(Canvas c, long now) {
        float[] top = MiddleEarthScene.CHIMNEY_TOP;
        for (int i = 0; i < PUFFS; i++) {
            // Each puff rises, swells and drifts right with the breeze, swaying a little, and fades as it goes.
            float ph = phase(now, SMOKE_LIFE_MS, (long) (puffX[i] * SMOKE_LIFE_MS));
            float sway = 0.8f * (float) Math.sin(ph * 6 + puffS[i] * TAU);
            float x = top[0] + sway + ph * SMOKE_DRIFT, y = top[1] - ph * SMOKE_RISE;
            float r = SMOKE_R_MIN + ph * (SMOKE_R_MAX - SMOKE_R_MIN);
            oval(c, smoke, 0.4f * (1f - ph) * Math.min(1f, ph * 8f), x, y, r, r);
        }
        drawMist(c, now, MiddleEarthScene.MIST_MORNING, mistMorning, MIST_MORNING_PUFFS, MIST_RX_MORNING, 0.16f,
                1.15f);
    }

    /** Mist puffs drifting right through a band {top, bottom} at about {@code speed} units a second. */
    private void drawMist(Canvas c, long now, float[] band, Paint p, int puffs, float rx, float alpha, float speed) {
        double t = now / 1000.0;
        float span = MiddleEarthScene.UNITS_W + 2 * rx;
        for (int i = 0; i < puffs; i++) {
            float x = (float) ((mistX[i] * span + t * speed * (0.5 + mistS[i])) % span) - rx;
            float y = band[0] + mistY[i] * (band[1] - band[0]);
            float breathe = 0.8f + 0.2f * (float) Math.sin(t * 0.3 + i * 1.7);
            oval(c, p, alpha * breathe, x, y, rx, MIST_RY);
        }
    }

    private void drawNight(Canvas c, long now) {
        float[] crater = MiddleEarthScene.CRATER;
        // The crater's glow, breathing (the mockup's 0.35 + 0.1 sin(1.3 t)), and the lava channels glimmering.
        float breath = (float) Math.sin(now / 1000.0 * 1.3);
        oval(c, craterGlow, 0.35f + 0.1f * breath, crater[0], CRATER_GLOW_Y, CRATER_GLOW_RX, CRATER_GLOW_RY);
        lava.setAlpha((int) (255 * (0.12f + 0.18f * wave(phase(now, 4100, 0)))));
        c.drawPath(lavaPath, lava);
        float size = Math.max(1f, EMBER_SIZE * unit);
        for (int i = 0; i < EMBERS; i++) {
            // Sparks rise from the rim, spreading, and fade out before the header.
            float ph = phase(now, EMBER_LIFE_MS, (long) (emberP[i] * EMBER_LIFE_MS));
            float x = crater[0] + (emberX[i] - 0.5f) * 2f * crater[2] * (1f + 1.5f * ph);
            float y = crater[1] - ph * EMBER_RISE;
            ember.setColor(emberColor[i]);
            ember.setAlpha((int) (255 * (1f - ph)));
            float ex = ux(x), ey = uy(y);
            c.drawRect(ex, ey, ex + size, ey + size, ember);
        }
        double t = now / 1000.0;
        float bottom = MiddleEarthScene.DOCK;
        int faint = 0, strong = 0;
        for (int i = 0; i < ASH; i++) {
            // Falling at its own pace from the clock itself, so it never jumps; drifting right as it falls.
            float y = (float) ((ashY[i] * bottom + t * 2.1 * (0.5 + ashS[i])) % bottom);
            float x = (float) ((ashX[i] * MiddleEarthScene.UNITS_W + t * 0.96) % MiddleEarthScene.UNITS_W);
            if (inHeader(x, y)) continue;
            if (ashS[i] < 0.5f) {
                ashFaint[faint++] = ux(x);
                ashFaint[faint++] = uy(y);
            } else {
                ashStrong[strong++] = ux(x);
                ashStrong[strong++] = uy(y);
            }
        }
        if (faint > 0) c.drawPoints(ashFaint, 0, faint, ashFaintPaint);
        if (strong > 0) c.drawPoints(ashStrong, 0, strong, ashStrongPaint);
    }

    /** The cone from the eye over the land, above the dock, then the eye's glow, ring and dark round core. */
    private void drawEye(Canvas c, long now, boolean moving) {
        float[] o = MiddleEarthScene.CONE_ORIGIN, e = MiddleEarthScene.EYE;
        float pulse = eyePulse(now, moving);
        c.save();
        c.clipRect(0f, 0f, getWidth(), dockPx);
        c.translate(ux(o[0]), uy(o[1]));
        // +x turned a quarter round points straight down; a positive angle turns it further, towards the land left.
        c.rotate((float) Math.toDegrees(Math.PI / 2 + coneAngle(now, moving)));
        cone.setAlpha((int) (255 * (0.75f + 0.25f * pulse)));
        c.drawPath(conePath, cone);
        c.restore();
        oval(c, eyeGlow, 0.7f * pulse, e[0], e[1], EYE_GLOW_RX * (0.8f + 0.2f * pulse), EYE_GLOW_RY);
        float cx = ux(e[0]), cy = uy(e[1]);
        float rx = e[2] * (0.85f + 0.15f * pulse) * unit, ry = e[3] * unit;
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry);
        eyeRing.setAlpha((int) (255 * (0.6f + 0.4f * pulse)));
        c.drawOval(oval, eyeRing);
        oval.set(cx - rx * 0.7f, cy - ry * 0.68f, cx + rx * 0.7f, cy + ry * 0.68f);
        c.drawOval(oval, eyeInner);
        c.drawCircle(cx, cy, ry * 0.42f, eyeCore);
    }

    /** A paint with a radial shader of radius 1 drawn at this alpha as an ellipse round (x, y), in units. */
    private void oval(Canvas c, Paint p, float alpha, float x, float y, float rx, float ry) {
        p.setAlpha((int) (255 * Math.max(0f, Math.min(1f, alpha))));
        c.save();
        c.translate(ux(x), uy(y));
        c.scale(rx * unit, ry * unit);
        c.drawCircle(0f, 0f, 1f, p);
        c.restore();
    }

    /** A glow in this colour, full in the middle and gone at radius 1. */
    private static Paint glowPaint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(0f, 0f, 1f, new int[]{color, color & 0x00FFFFFF}, null,
                Shader.TileMode.CLAMP));
        return p;
    }

    // ---- pure helpers (MiddleEarthOverlayTest) -------------------------------------------------------------------

    /**
     * The cone's turn from straight down at uptime {@code now}, in radians, positive towards the left: a slow sine of
     * {@link #CONE_SWEEP} over {@link #CONE_PERIOD_MS}; straight down (0) while nothing moves.
     */
    static float coneAngle(long now, boolean moving) {
        if (!moving) return 0f;
        return CONE_SWEEP * (float) Math.sin(TAU * phase(now, CONE_PERIOD_MS, 0));
    }

    /** Half the cone's opening, in radians. */
    static float coneHalfAngle() {
        return (float) Math.atan2(CONE_HALF, CONE_LEN);
    }

    /** The eye's strength at uptime {@code now}: between {@link #EYE_MIN} and 1; {@link #EYE_REST} while still. */
    static float eyePulse(long now, boolean moving) {
        if (!moving) return EYE_REST;
        return EYE_MIN + (1f - EYE_MIN) * wave(phase(now, EYE_PULSE_MS, 0));
    }

    /** Whether (x, y) in units lies in the header on the top left (MiddleEarthScene.HEADER): no ash falls there. */
    static boolean inHeader(float x, float y) {
        float[] h = MiddleEarthScene.HEADER;
        return x >= h[0] && x <= h[2] && y >= h[1] && y <= h[3];
    }

    /**
     * Where the logo picture of {@code w} x {@code h} pixels goes, {left, top, right, bottom} in units into
     * {@code out}: as large as fits MiddleEarthScene.LOGO (18 % wide, 8 % high), aspect kept, centred on it (x 57 %,
     * y 11 %). Nothing for a picture without area.
     */
    static void logoBox(int w, int h, float[] out) {
        float[] l = MiddleEarthScene.LOGO;
        float cx = l[0] + l[2] / 2f, cy = l[1] + l[3] / 2f;
        float bw = 0f, bh = 0f;
        if (w > 0 && h > 0) {
            float k = Math.min(l[2] / w, l[3] / h);
            bw = w * k;
            bh = h * k;
        }
        out[0] = cx - bw / 2f;
        out[1] = cy - bh / 2f;
        out[2] = cx + bw / 2f;
        out[3] = cy + bh / 2f;
    }
}
