package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;

import java.util.Random;

/**
 * Supernatural's motion (spec § 4.2) over its two pictures (SupernaturalScene holds where they paint what). By day
 * dust drifting in the low sun, the heat shimmering over the road and the sun's rays breathing; by night slanting
 * rain, the neon flickering (one letter of MOTEL fails now and then, VACANCY stutters), the star and the cracks
 * glowing like embers, the tail light glimmering, a faint sheen on the wet road, and about every 9 s (7 to 11 s
 * apart, at most 400 ms) a flash far away: the sky brightened to 40 % at most and only the sky (never the screen
 * white, spec § 2), a bolt in the distance, the shadow of two huge wings in the same moment, and the sigils on the
 * motel lighting up.
 *
 * <p>The household's own logo picture (WorldLogo, spec § 4.4) takes the painted lettering's place when it is on the
 * TV: the lettering is covered with the sky as painted round it, and the picture drawn there, 6 % of the screen high,
 * at 85 % brightness. That is still, so it is drawn whether or not the motion runs: with reduced motion the overlay
 * never ticks, and its one drawing (at layout, or when day and night swap) is the logo and nothing that moves.
 *
 * <p>The wing shadow is the sky the flash leaves out: the brightening is one path, the sky minus the wings, so the
 * wings stay as dark as the night and nothing under them (the neon) is darkened.
 */
final class SupernaturalOverlay extends WorldOverlay {
    /** The lightning's seed: each flash's place in its 9 s slot follows from it, so tests can pin it. */
    static final long SEED = 0x5E7A7L;
    static final long LIGHTNING_EVERY_MS = 9000, LIGHTNING_JITTER_MS = 1000;
    /** One flash: up, a dip, a second stroke, out; within the spec's 400 ms. */
    static final long LIGHTNING_MS = 360;
    /** The sky's brightening at the flash's peak (spec § 2: at most 40 %). */
    static final float SKY_FLASH = 0.40f;
    /** MOTEL's failing letter and VACANCY's stutter come round once a cycle. */
    static final long MOTEL_CYCLE_MS = 11000, VACANCY_CYCLE_MS = 5300;
    /** Where the two wings meet (no body between them, no figure) and the widest the logo picture may get. */
    static final float WING_X = 96f;
    static final float LOGO_MAX_W = 2f * (SupernaturalScene.UNITS_W - 2f
            - (SupernaturalScene.LETTERING[0] + SupernaturalScene.LETTERING[2] / 2f));

    /** The covers that put a letter out: half width and height round its centre, and VACANCY's round its middle. */
    private static final float LETTER_RX = 2.3f, LETTER_RY = 3.9f, VACANCY_RX = 8.6f, VACANCY_RY = 1.9f;
    /** The neon off: the dark tube on the board; VACANCY dims towards the board's blue-grey. */
    private static final int MOTEL_OFF = 0xFF3A1C22, VACANCY_OFF = 0xFF10181E;
    /** The rain: drops, the dock's top edge they fall to, their slant (x per y) and how long they are (units). */
    private static final int DROPS = 120;
    private static final float RAIN_SLANT = 0.22f, DROP_LEN = 2.8f;
    /** The bolts (three, one per flash in turn), each up to BOLT_SEGS segments, all between the header and the sign. */
    private static final int BOLTS = 3, BOLT_SEGS = 14;
    private static final float[] BOLT_X = {84f, 92f, 100f};
    /** The day's dust in the low sun (a box in units) and the heat bands make_scene.py paints over the road. */
    private static final int MOTES = 28;
    private static final float DUST_L = 10f, DUST_T = 28f, DUST_W = 130f, DUST_H = 34f;
    private static final float HEAT_X = 96f, HEAT_W = 30f, HEAT_Y = SupernaturalScene.HORIZON + 0.6f, HEAT_STEP = 0.65f,
            HEAT_H = 0.15f;
    private static final int HEAT_BANDS = 5;
    /** The neon's reflection on the wet road (make_scene.py wet_reflections) and the motel's roof (MOTEL). */
    private static final float SHEEN_X = 114.5f, SHEEN_Y = 65f, ROOF_Y = 43.4f;

    private final float[] dropX = new float[DROPS], dropY = new float[DROPS], dropSpeed = new float[DROPS];
    private final float[] rain = new float[DROPS * 4];
    private final float[] moteX = new float[MOTES], moteY = new float[MOTES], moteS = new float[MOTES],
            moteP = new float[MOTES];
    /** The bolts in units and in view pixels, as segments {x0, y0, x1, y1}; boltSegs[i] how many each has. */
    private final float[][] boltUnits = new float[BOLTS][BOLT_SEGS * 4], boltPx = new float[BOLTS][BOLT_SEGS * 4];
    private final int[] boltSegs = new int[BOLTS];

    private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Radial glows of radius 1, each scaled to its ellipse: the cracks' red light, the star, tail light, sheen. */
    private final Paint hellGlow = glowPaint(0xFFFF461E), starGlow = glowPaint(0xFFFF6A20),
            tailGlow = glowPaint(0xFFFF2828), sheenGlow = glowPaint(0xFFFF3B4A);
    private final Paint sigilGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint motelOff = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vacancyOff = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crackWide = new Paint(Paint.ANTI_ALIAS_FLAG), crackCore = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint skyFlash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boltGlow = new Paint(Paint.ANTI_ALIAS_FLAG), boltCore = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ray = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint heat = new Paint();
    private final Paint dust = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint skyPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path cracks = new Path(), wings = new Path(), litSky = new Path(), rayPath = new Path();
    private final Matrix skyMatrix = new Matrix();
    private final RectF cover = new RectF(), logoRect = new RectF();
    private final float[] box = new float[4];

    /** The logo picture, or null: then the painted lettering stays. Read once, at the first layout. */
    private Bitmap logo;
    private boolean logoRead;
    /** The sky at the lettering's four corners as a 2 x 2 picture, drawn stretched over the lettering. */
    private Bitmap sky;
    private long startedAt;
    /** 1080-line pixels to view pixels. */
    private float px;

    SupernaturalOverlay(Context context, boolean day) {
        super(context, day);
        Random seeded = new Random(0x5241L);
        for (int i = 0; i < DROPS; i++) {
            dropX[i] = seeded.nextFloat();
            dropY[i] = seeded.nextFloat();
            dropSpeed[i] = 70f + 45f * seeded.nextFloat();
        }
        for (int i = 0; i < MOTES; i++) {
            moteX[i] = seeded.nextFloat();
            moteY[i] = seeded.nextFloat();
            moteS[i] = seeded.nextFloat();
            moteP[i] = seeded.nextFloat();
        }
        for (int b = 0; b < BOLTS; b++) boltSegs[b] = bolt(BOLT_X[b], seeded, boltUnits[b]);
        rainPaint.setColor(0x48AABEDC);
        rainPaint.setStyle(Paint.Style.STROKE);
        sigilGlow.setShader(new RadialGradient(0f, 0f, 1f, new int[]{0xE6FF3020, 0x66FF3020, 0x00FF3020},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        motelOff.setShader(new RadialGradient(0f, 0f, 1f, new int[]{MOTEL_OFF, MOTEL_OFF, MOTEL_OFF & 0x00FFFFFF},
                new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        vacancyOff.setShader(new RadialGradient(0f, 0f, 1f,
                new int[]{VACANCY_OFF, VACANCY_OFF, VACANCY_OFF & 0x00FFFFFF}, new float[]{0f, 0.75f, 1f},
                Shader.TileMode.CLAMP));
        crackWide.setStyle(Paint.Style.STROKE);
        crackWide.setStrokeCap(Paint.Cap.ROUND);
        crackWide.setStrokeJoin(Paint.Join.ROUND);
        crackWide.setColor(0xFFFF5A10);
        crackCore.setStyle(Paint.Style.STROKE);
        crackCore.setStrokeCap(Paint.Cap.ROUND);
        crackCore.setStrokeJoin(Paint.Join.ROUND);
        crackCore.setColor(0xFFFFA048);
        boltGlow.setStyle(Paint.Style.STROKE);
        boltGlow.setStrokeCap(Paint.Cap.ROUND);
        boltGlow.setColor(0xFFB4C8FF);
        boltCore.setStyle(Paint.Style.STROKE);
        boltCore.setStrokeCap(Paint.Cap.ROUND);
        boltCore.setColor(0xFFE6F0FF);
        heat.setColor(0xFFFFDCA0);
        dust.setColor(0xFFFFDCAA);
        // At most 85 % brightness (spec § 4.4): 216 / 255 is just under.
        logoPaint.setColorFilter(new LightingColorFilter(0xFFD8D8D8, 0));
    }

    @Override
    protected void onStart(long now) {
        startedAt = now;
    }

    /** Stopped (onPause, reduced motion, a screensaver): what moves goes, the logo stays. */
    @Override
    void stop() {
        super.stop();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (logo != null) logo.recycle();
        logo = null;
        if (sky != null) sky.recycle();
        sky = null;
    }

    @Override
    protected void layout(int w, int h) {
        px = h / 1080f;
        if (!logoRead) {
            logoRead = true;
            // Decoded here, once, like the scene's first picture: Home's first frame already shows it. Twice the
            // place's size at most, so it is drawn sharp, a little scaled down.
            logo = WorldLogo.load(getContext(), World.SUPERNATURAL, (int) Math.ceil(2 * LOGO_MAX_W * unit),
                    (int) Math.ceil(2 * SupernaturalScene.LETTERING[3] * unit));
        }
        layoutLogo();
        for (int i = 0; i < 4; i++) regions[i].setEmpty();
        if (day) {
            ray.setShader(new RadialGradient(ux(SupernaturalScene.SUN[0]), uy(SupernaturalScene.SUN[1]),
                    SupernaturalScene.RAY_LEN * unit, 0xFFFFE6B4, 0x00FFE6B4, Shader.TileMode.CLAMP));
            // The rays and the dust in the sun; the heat over the road.
            regions[0].set((int) Math.floor(ux(7f)), 0, (int) Math.ceil(ux(DUST_L + DUST_W + 1f)),
                    (int) Math.ceil(uy(DUST_T + DUST_H + 1f)));
            regions[1].set((int) Math.floor(ux(HEAT_X - 1f)), (int) Math.floor(uy(HEAT_Y - 0.4f)),
                    (int) Math.ceil(ux(HEAT_X + HEAT_W + 1.5f)),
                    (int) Math.ceil(uy(HEAT_Y + HEAT_BANDS * HEAT_STEP + 0.4f)));
            return;
        }
        // At night the rain falls over all of the scene above the dock, and everything else that moves lies in it.
        regions[0].set(0, 0, w, (int) Math.ceil(uy(PersonalMotions.DOCK_Y)));
        rainPaint.setStrokeWidth(Math.max(1f, 1.2f * px));
        crackWide.setStrokeWidth(SupernaturalScene.CRACK_W * 6f * unit);
        crackCore.setStrokeWidth(Math.max(1f, SupernaturalScene.CRACK_W * 1.2f * unit));
        boltGlow.setStrokeWidth(Math.max(2f, 5f * px));
        boltCore.setStrokeWidth(Math.max(1f, 1.6f * px));
        cracks.reset();
        for (float[] c : SupernaturalScene.CRACKS) {
            cracks.moveTo(ux(c[0]), uy(c[1]));
            for (int i = 2; i < c.length; i += 2) cracks.lineTo(ux(c[i]), uy(c[i + 1]));
        }
        float horizon = uy(SupernaturalScene.HORIZON);
        // The flash's light: full at the top of the sky, a third of it down at the horizon.
        skyFlash.setShader(new LinearGradient(0f, 0f, 0f, horizon, 0xFFB4C8FF, 0x59B4C8FF, Shader.TileMode.CLAMP));
        float[] wing = wing();
        wings.reset();
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < wing.length; i += 2) {
                float x = ux(WING_X + side * (wing[i] - WING_X)), y = uy(wing[i + 1]);
                if (i == 0) wings.moveTo(x, y);
                else wings.lineTo(x, y);
            }
            wings.close();
        }
        litSky.reset();
        litSky.addRect(0f, Math.min(0f, uy(0f)), w, horizon, Path.Direction.CW);
        litSky.op(wings, Path.Op.DIFFERENCE);
        for (int b = 0; b < BOLTS; b++) {
            float[] u = boltUnits[b], p = boltPx[b];
            for (int i = 0; i < boltSegs[b] * 4; i += 2) {
                p[i] = ux(u[i]);
                p[i + 1] = uy(u[i + 1]);
            }
        }
    }

    /** Where the cover and the logo go, and the sky's 2 x 2 picture for this day or night. */
    private void layoutLogo() {
        if (logo == null) return;
        float[] c = letteringCover();
        cover.set(ux(c[0]), uy(c[1]), ux(c[2]), uy(c[3]));
        logoBox(logo.getWidth(), logo.getHeight(), box);
        logoRect.set(ux(box[0]), uy(box[1]), ux(box[2]), uy(box[3]));
        if (sky == null) {
            sky = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
            skyPaint.setShader(new BitmapShader(sky, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        }
        int[] corners = day ? SupernaturalScene.LETTERING_SKY_DAY : SupernaturalScene.LETTERING_SKY_NIGHT;
        sky.setPixel(0, 0, corners[0]);
        sky.setPixel(1, 0, corners[1]);
        sky.setPixel(0, 1, corners[2]);
        sky.setPixel(1, 1, corners[3]);
        // make_scene.py read the corners 0.3 units inside the lettering: the pixels' centres go there, so the
        // filtered picture blends them as the painted sky does, and holds the edge colours beyond.
        float[] l = SupernaturalScene.LETTERING;
        float x0 = ux(l[0] + 0.3f), y0 = uy(l[1] + 0.3f), x1 = ux(l[0] + l[2] - 0.3f), y1 = uy(l[1] + l[3] - 0.3f);
        skyMatrix.setScale(x1 - x0, y1 - y0);
        skyMatrix.preTranslate(-0.5f, -0.5f);
        skyMatrix.postTranslate(x0, y0);
        skyPaint.getShader().setLocalMatrix(skyMatrix);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (logo != null) {
            c.drawRect(cover, skyPaint);
            c.drawBitmap(logo, null, logoRect, logoPaint);
        }
        if (!isRunning()) return;
        long now = SystemClock.uptimeMillis();
        if (day) drawDay(c, now);
        else drawNight(c, now);
    }

    private void drawDay(Canvas c, long now) {
        double t = now / 1000.0;
        float sx = ux(SupernaturalScene.SUN[0]), sy = uy(SupernaturalScene.SUN[1]);
        float len = SupernaturalScene.RAY_LEN * unit, half = SupernaturalScene.RAY_HALF;
        for (int i = 0; i < SupernaturalScene.RAYS.length; i++) {
            // The painted rays stay; over them each breathes a little and sways by a hundredth of a radian or two.
            float a = SupernaturalScene.RAYS[i] + 0.02f * (float) Math.sin(t * 0.2 + i);
            float breath = 0.5f + 0.5f * (float) Math.sin(t * 0.7 + i * 1.3);
            ray.setAlpha((int) (255 * 0.05f * breath));
            rayPath.reset();
            rayPath.moveTo(sx, sy);
            rayPath.lineTo(sx + (float) Math.sin(a - half) * len, sy - (float) Math.cos(a - half) * len);
            rayPath.lineTo(sx + (float) Math.sin(a + half) * len, sy - (float) Math.cos(a + half) * len);
            rayPath.close();
            c.drawPath(rayPath, ray);
        }
        for (int k = 0; k < HEAT_BANDS; k++) {
            float shift = 0.35f * (float) Math.sin(t * 3 + k);
            float a = (0.06f - k * 0.01f) * (0.7f + 0.3f * (float) Math.sin(t * 2.1 + k * 1.7));
            heat.setAlpha((int) (255 * a));
            float y = uy(HEAT_Y + k * HEAT_STEP);
            c.drawRect(ux(HEAT_X + shift), y, ux(HEAT_X + HEAT_W + shift), y + Math.max(1f, HEAT_H * unit), heat);
        }
        for (int i = 0; i < MOTES; i++) {
            float s = moteS[i];
            // Drifting right with the light, slowly, each at its own pace, swaying a little.
            float x = DUST_L + (float) ((moteX[i] * DUST_W + t * (0.6 + s)) % DUST_W);
            float y = DUST_T + moteY[i] * DUST_H + 0.5f * (float) Math.sin(t * 0.9 + moteP[i] * TAU);
            float a = (0.15f + 0.25f * s) * (0.6f + 0.4f * (float) Math.sin(t * 1.3 + moteP[i] * 7));
            dust.setAlpha((int) (255 * a));
            c.drawCircle(ux(x), uy(y), (0.14f + 0.18f * s) * unit, dust);
        }
    }

    private void drawNight(Canvas c, long now) {
        long since = now - startedAt;
        // The cracks and the glow over them, slowly, like embers.
        float ember = wave(phase(now, 3300, 0));
        oval(c, hellGlow, 0.03f + 0.05f * ember, 39f, 60f, 32f, 7f);
        crackWide.setAlpha((int) (255 * (0.10f + 0.20f * ember)));
        c.drawPath(cracks, crackWide);
        crackCore.setAlpha((int) (255 * 0.35f * ember));
        c.drawPath(cracks, crackCore);
        float[] st = SupernaturalScene.STAR;
        oval(c, starGlow, 0.06f + 0.14f * wave(phase(now, 4200, 900)), st[0], st[1], st[2] * 1.25f,
                st[3] * 1.6f);
        float[] tail = SupernaturalScene.CAR_TAIL_LIGHT;
        oval(c, tailGlow, 0.25f + 0.2f * wave(phase(now, 3600, 400)), tail[0] + tail[2] / 2f,
                tail[1] + tail[3] / 2f, 2.6f, 2.6f);
        // The neon's sheen on the wet road, trembling with the rain.
        float tremble = 0.5f + 0.25f * (float) Math.sin(now / 1300.0 * TAU) + 0.25f * (float) Math.sin(now / 700.0 * TAU);
        oval(c, sheenGlow, 0.04f + 0.05f * tremble, SHEEN_X, SHEEN_Y, 4f, 2.5f);
        float m = motelDim(now, SEED, true);
        if (m > 0f) {
            int letter = motelLetter(now, SEED);
            motelOff.setAlpha((int) (255 * 0.85f * m));
            oval(c, motelOff, SupernaturalScene.NEON_MOTEL_X[letter],
                    SupernaturalScene.NEON_MOTEL[1] + SupernaturalScene.NEON_MOTEL[3] / 2f, LETTER_RX, LETTER_RY);
        }
        float v = vacancyDim(now, SEED, true);
        if (v > 0f) {
            float[] r = SupernaturalScene.NEON_VACANCY;
            vacancyOff.setAlpha((int) (255 * 0.75f * v));
            oval(c, vacancyOff, r[0] + r[2] / 2f, r[1] + r[3] / 2f, VACANCY_RX, VACANCY_RY);
        }
        float flash = lightning(since, SEED);
        if (flash > 0f) {
            skyFlash.setAlpha((int) (255 * skyFlashAlpha(since, SEED, true)));
            c.drawPath(litSky, skyFlash);
            int b = Math.floorMod(lightningSlot(since), BOLTS);
            // Far behind the wings: the bolt shows only where the sky is lit.
            c.save();
            c.clipOutPath(wings);
            boltGlow.setAlpha((int) (255 * 0.3f * flash));
            c.drawLines(boltPx[b], 0, boltSegs[b] * 4, boltGlow);
            boltCore.setAlpha((int) (255 * flash));
            c.drawLines(boltPx[b], 0, boltSegs[b] * 4, boltCore);
            c.restore();
            sigilGlow.setAlpha((int) (255 * 0.7f * flash));
            float[] ward = SupernaturalScene.WARD;
            oval(c, sigilGlow, ward[0], ward[1], ward[2] * 1.7f, ward[2] * 1.7f);
            sigilGlow.setAlpha((int) (255 * 0.6f * flash));
            for (float[] mark : SupernaturalScene.DOOR_MARKS) {
                oval(c, sigilGlow, mark[0], mark[1], SupernaturalScene.DOOR_MARK_R * 2.8f,
                        SupernaturalScene.DOOR_MARK_R * 2.8f);
            }
        }
        float bottom = PersonalMotions.DOCK_Y, span = bottom + DROP_LEN;
        for (int i = 0; i < DROPS; i++) {
            // Falling at its own speed, from the clock itself so it never jumps; drifting left as it falls.
            float y = (float) ((dropY[i] * span + now * (double) dropSpeed[i] / 1000.0) % span) - DROP_LEN;
            float x = dropX[i] * 200f - 4f - RAIN_SLANT * y;
            x -= 200f * (float) Math.floor((x + 4f) / 200f);
            float y1 = Math.min(bottom, y + DROP_LEN);
            int k = i * 4;
            rain[k] = ux(x);
            rain[k + 1] = uy(y);
            rain[k + 2] = ux(x - RAIN_SLANT * (y1 - y));
            rain[k + 3] = uy(y1);
        }
        c.drawLines(rain, rainPaint);
    }

    /** A paint with a radial shader of radius 1 drawn as an ellipse round (x, y) with these half axes, in units. */
    private void oval(Canvas c, Paint p, float x, float y, float rx, float ry) {
        c.save();
        c.translate(ux(x), uy(y));
        c.scale(rx * unit, ry * unit);
        c.drawCircle(0f, 0f, 1f, p);
        c.restore();
    }

    /** The same at this alpha (0 to 1). */
    private void oval(Canvas c, Paint p, float alpha, float x, float y, float rx, float ry) {
        p.setAlpha((int) (255 * Math.max(0f, Math.min(1f, alpha))));
        oval(c, p, x, y, rx, ry);
    }

    /** A glow in this colour, full in the middle and gone at radius 1. */
    private static Paint glowPaint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(0f, 0f, 1f, new int[]{color, color & 0x00FFFFFF}, null,
                Shader.TileMode.CLAMP));
        return p;
    }

    // ---- pure helpers (SupernaturalOverlayTest) ------------------------------------------------------------------

    /** SplitMix64's finaliser: a well mixed 64-bit value for each input, nothing allocated. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long hash(long seed, long k, long salt) {
        return mix(seed + k * 0x9E3779B97F4A7C15L + salt);
    }

    /**
     * When flash {@code k} (1, 2, …) starts, in ms after the motion started: in the middle of its 9 s slot, give or
     * take a second, so two flashes stand 7 to 11 s apart. The slot before the first one stays dark (the transition
     * into the world has its own flash).
     */
    static long lightningStart(int k, long seed) {
        return k * LIGHTNING_EVERY_MS + Math.floorMod(hash(seed, k, 0x4C49L), 2 * LIGHTNING_JITTER_MS + 1)
                - LIGHTNING_JITTER_MS;
    }

    /** The slot whose flash may be lit {@code since} ms after the start: none starts later, none before ends later. */
    static int lightningSlot(long since) {
        return (int) ((since + LIGHTNING_JITTER_MS) / LIGHTNING_EVERY_MS);
    }

    /** How bright the lightning is {@code since} ms after the motion started: 0 between flashes, up to 1. */
    static float lightning(long since, long seed) {
        if (since < 0) return 0f;
        int k = lightningSlot(since);
        return k < 1 ? 0f : flash(since - lightningStart(k, seed));
    }

    /** One flash {@code d} ms after it began: a stroke up to 1 in 40 ms, a dip to 0.3, a second stroke, the fade. */
    static float flash(long d) {
        if (d < 0 || d >= LIGHTNING_MS) return 0f;
        if (d < 40) return d / 40f;
        if (d < 90) return 1f - 0.7f * (d - 40) / 50f;
        if (d < 130) return 0.3f + 0.55f * (d - 90) / 40f;
        float u = 1f - (d - 130) / (float) (LIGHTNING_MS - 130);
        return 0.85f * u * u;
    }

    /** The sky's brightening (its alpha, at most {@link #SKY_FLASH}); none while nothing moves. */
    static float skyFlashAlpha(long since, long seed, boolean moving) {
        return moving ? SKY_FLASH * lightning(since, seed) : 0f;
    }

    /** Which of MOTEL's letters fails in the cycle {@code now} falls in. */
    static int motelLetter(long now, long seed) {
        long cycle = Math.floorDiv(now, MOTEL_CYCLE_MS);
        return (int) Math.floorMod(hash(seed, cycle, 0x4D4FL), (long) SupernaturalScene.NEON_MOTEL_X.length);
    }

    /**
     * How far the failing letter is out (1) or on (0): once a cycle it stutters off and on twice, then stays out for
     * a second or two and comes back; steady (0) while nothing moves.
     */
    static float motelDim(long now, long seed, boolean moving) {
        if (!moving) return 0f;
        long cycle = Math.floorDiv(now, MOTEL_CYCLE_MS);
        long d = Math.floorMod(now, MOTEL_CYCLE_MS) - Math.floorMod(hash(seed, cycle, 0x4154L), 4000L);
        if (d < 0) return 0f;
        if (d < 90) return 1f;
        if (d < 170) return 0f;
        if (d < 240) return 1f;
        if (d < 300) return 0f;
        return d < 300 + 900 + Math.floorMod(hash(seed, cycle, 0x4C45L), 1500L) ? 1f : 0f;
    }

    /** VACANCY's stutter, how far it dims (0 to 1): a few quick drops once a cycle; steady while nothing moves. */
    static float vacancyDim(long now, long seed, boolean moving) {
        if (!moving) return 0f;
        long cycle = Math.floorDiv(now, VACANCY_CYCLE_MS);
        long d = Math.floorMod(now, VACANCY_CYCLE_MS) - Math.floorMod(hash(seed, cycle, 0x5641L), 3500L);
        if (d < 0) return 0f;
        if (d < 60) return 1f;
        if (d < 120) return 0f;
        if (d < 160) return 0.6f;
        if (d < 260) return 0f;
        if (d < 300) return 1f;
        if (d < 340) return 0.4f;
        return 0f;
    }

    /** The box {left, top, right, bottom} in units that putting MOTEL's letter {@code i} out covers. */
    static float[] motelLetterBox(int i) {
        float x = SupernaturalScene.NEON_MOTEL_X[i];
        float y = SupernaturalScene.NEON_MOTEL[1] + SupernaturalScene.NEON_MOTEL[3] / 2f;
        return new float[]{x - LETTER_RX, y - LETTER_RY, x + LETTER_RX, y + LETTER_RY};
    }

    /** The box in units that dimming VACANCY covers. */
    static float[] vacancyBox() {
        float[] r = SupernaturalScene.NEON_VACANCY;
        float x = r[0] + r[2] / 2f, y = r[1] + r[3] / 2f;
        return new float[]{x - VACANCY_RX, y - VACANCY_RY, x + VACANCY_RX, y + VACANCY_RY};
    }

    /**
     * The right wing's outline in units, {x0, y0, x1, y1, …}; the left one is its mirror image round
     * {@link #WING_X}. Its leading edge arches from the root up and out to the tip; the trailing edge comes back in
     * feathers, longer towards the tip, with notches between them. Two wings, no body: an own, general shape.
     */
    static float[] wing() {
        final int lead = 14, feathers = 9;
        float[] out = new float[(lead + 1 + 2 * feathers + 1) * 2];
        int n = 0;
        float root = WING_X + 2f, span = 80f;
        for (int i = 0; i <= lead; i++) {
            float u = i / (float) lead;
            out[n++] = root + u * span;
            out[n++] = 25f - 15f * (float) Math.sin(u * Math.PI * 0.85);
        }
        for (int j = 0; j <= feathers; j++) {
            float v = 1f - j / (float) feathers;
            float base = 22f + (1f - v) * 12f + 7f * (float) Math.sin(v * Math.PI);
            out[n++] = root + v * (span - 2f);
            out[n++] = base + 3.5f * (0.5f + v) * (j == 0 ? 0f : 1f);
            if (j < feathers) {
                float m = v - 0.5f / feathers;
                out[n++] = root + m * (span - 2f);
                out[n++] = 22f + (1f - m) * 12f + 7f * (float) Math.sin(m * Math.PI) - 1.2f;
            }
        }
        return out;
    }

    /**
     * Where the logo picture of {@code w} x {@code h} pixels goes, {left, top, right, bottom} in units into
     * {@code out}: the lettering's height (6 % of the screen), its aspect kept, centred on the lettering; one wider
     * than {@link #LOGO_MAX_W} is narrowed to it, aspect still kept. Nothing for a picture without area.
     */
    static void logoBox(int w, int h, float[] out) {
        float[] l = SupernaturalScene.LETTERING;
        float cx = l[0] + l[2] / 2f, cy = l[1] + l[3] / 2f;
        float bh = 0f, bw = 0f;
        if (w > 0 && h > 0) {
            bh = l[3];
            bw = bh * w / h;
            if (bw > LOGO_MAX_W) {
                bw = LOGO_MAX_W;
                bh = bw * h / w;
            }
        }
        out[0] = cx - bw / 2f;
        out[1] = cy - bh / 2f;
        out[2] = cx + bw / 2f;
        out[3] = cy + bh / 2f;
    }

    /**
     * What covers the painted lettering under a logo picture, {left, top, right, bottom} in units: the lettering
     * and a unit round it (its glow at night), but clear of the pills above, the motel's roof below and the sign's
     * pole on the left.
     */
    static float[] letteringCover() {
        float[] l = SupernaturalScene.LETTERING;
        return new float[]{l[0] - 1f, Math.max(PersonalMotions.PILLS[3] + 0.2f, l[1] - 1f), l[0] + l[2] + 1f,
                Math.min(ROOF_Y - 0.3f, l[1] + l[3] + 1f)};
    }

    /**
     * A bolt from the clouds down towards the hills round {@code x}, as segments into {@code out}: a jagged line in
     * steps of about 4.8 units, kept between the header and the sign, and one short branch. Returns the segments.
     */
    private static int bolt(float x, Random r, float[] out) {
        int n = 0;
        float y = 1f;
        float branchX = 0f, branchY = 0f;
        int steps = BOLT_SEGS - 3;
        for (int i = 0; i < steps; i++) {
            float nx = Math.max(78f, Math.min(105f, x + (r.nextFloat() - 0.5f) * 4.4f));
            float ny = y + 4.0f + r.nextFloat() * 1.6f;
            out[n * 4] = x;
            out[n * 4 + 1] = y;
            out[n * 4 + 2] = nx;
            out[n * 4 + 3] = ny;
            n++;
            if (i == 4) {
                branchX = nx;
                branchY = ny;
            }
            x = nx;
            y = ny;
            if (y > SupernaturalScene.HORIZON - 6f) break;
        }
        float dir = r.nextBoolean() ? 1f : -1f;
        for (int i = 0; i < 3 && n < BOLT_SEGS; i++) {
            float nx = branchX + dir * (1.6f + r.nextFloat() * 1.4f), ny = branchY + 2.4f + r.nextFloat() * 1.2f;
            out[n * 4] = branchX;
            out[n * 4 + 1] = branchY;
            out[n * 4 + 2] = nx;
            out[n * 4 + 3] = ny;
            n++;
            branchX = nx;
            branchY = ny;
        }
        return n;
    }
}
