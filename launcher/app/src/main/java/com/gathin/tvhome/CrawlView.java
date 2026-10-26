package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import java.util.Random;

/**
 * Seasons' anniversary crawl (spec § 4.3, § 4.5): the household's own text in yellow on a dark starry sky, tilted
 * backwards and rolling away into the distance, fading out at the top. Once a day (MainActivity, OncePerDay), at
 * most {@link #MAX_MS}; any key ends it (MainActivity.dispatchKeyEvent), and so does leaving Home.
 *
 * <p>The tilt is one perspective matrix (Matrix.setPolyToPoly from the flat text plane to a trapezoid whose far
 * edge is narrow and high), worked out once per size. The text is drawn in the app's own font (font_medium, Outfit);
 * no film font, logo or opening line. Built like PetalShowerView: no focus, clicks or accessibility, a hardware
 * layer only while playing, nothing allocated per frame, a tick every {@link WorldOverlay#FRAME_MS} (30 fps at most)
 * and no drawing at all once it has ended.
 */
final class CrawlView extends View {

    /** The hard stop: the crawl never runs longer, whatever the text. */
    static final long MAX_MS = 20_000;
    private static final long FADE_IN_MS = 500;
    private static final long FADE_OUT_MS = 700;
    private static final int YELLOW = 0xFFFFD34D;
    private static final int SKY = 0xFF03050B;
    private static final int STARS = 90;
    private static final String[] ROMAN = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
    private static final int[] ROMAN_VALUE = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};

    private final float density;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadePaint = new Paint();
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix plane = new Matrix();
    private final float[] src = new float[8];
    private final float[] dst = new float[8];
    private float[] stars = new float[0];
    /** The text plane's length in its own pixels (it is as wide as the view); its near end is the screen's bottom. */
    private float planeLength;
    private CharSequence text;
    private StaticLayout layout;
    private float layoutLeft;

    private boolean playing;
    private long start;
    private Runnable onDone;
    private final Runnable frame = this::frame;
    private final Runnable hardStop = this::stop;

    CrawlView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        textPaint.setColor(YELLOW);
        textPaint.setTypeface(UiFont.get(context, R.string.font_medium));
        textPaint.setLetterSpacing(0.04f);
        starPaint.setColor(0xFFFFFFFF);
        starPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Rolls {@code crawlText} (already filled in, {@link #fill}) and runs {@code done} on the main thread when it has
     * ended, by itself or by {@link #stop}. Playing again first ends the one before (its callback runs).
     */
    void play(CharSequence crawlText, Runnable done) {
        if (playing) stop();
        text = crawlText == null ? "" : crawlText;
        layout = null;
        if (getWidth() > 0) buildLayout(getWidth(), getHeight());
        onDone = done;
        playing = true;
        start = SystemClock.uptimeMillis();
        setAlpha(0f);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        postDelayed(hardStop, MAX_MS);
        frame();
    }

    boolean isPlaying() {
        return playing;
    }

    /** Ends it now (a key, Home left, the hard stop); nothing more is drawn and the callback runs once. */
    void stop() {
        if (!playing) return;
        end();
        Runnable done = onDone;
        onDone = null;
        if (done != null) done.run();
    }

    private void end() {
        playing = false;
        removeCallbacks(frame);
        removeCallbacks(hardStop);
        setLayerType(LAYER_TYPE_NONE, null);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Torn down with Home: no callback, which would remove this view from a parent that is detaching it.
        if (playing) {
            onDone = null;
            end();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        planeLength = 2.2f * h;
        // The flat plane (as wide as the view, planeLength long) onto a trapezoid: its near end wider than the
        // screen along the bottom edge, its far end narrow and just under the top. A homography of the plane, so
        // the lines get smaller and closer together as they recede.
        src[0] = 0f; src[1] = 0f;
        src[2] = w; src[3] = 0f;
        src[4] = w; src[5] = planeLength;
        src[6] = 0f; src[7] = planeLength;
        dst[0] = w * 0.34f; dst[1] = h * 0.06f;
        dst[2] = w * 0.66f; dst[3] = h * 0.06f;
        dst[4] = w * 1.25f; dst[5] = h;
        dst[6] = -w * 0.25f; dst[7] = h;
        plane.setPolyToPoly(src, 0, dst, 0, 4);
        // The far end fades into the sky: over the plane's first 7 % (about the screen's top quarter).
        fadePaint.setShader(new LinearGradient(0f, 0f, 0f, planeLength * 0.07f, SKY, SKY & 0x00FFFFFF,
                Shader.TileMode.CLAMP));
        Random r = new Random(0x5EA5L);
        stars = new float[STARS * 2];
        for (int i = 0; i < STARS; i++) {
            stars[2 * i] = r.nextFloat() * w;
            stars[2 * i + 1] = r.nextFloat() * h;
        }
        starPaint.setStrokeWidth(Math.max(1f, 1.5f * density));
        if (text != null) buildLayout(w, h);
    }

    private void buildLayout(int w, int h) {
        textPaint.setTextSize(h * 0.055f);
        int width = Math.round(w * 0.6f);
        layoutLeft = (w - width) / 2f;
        layout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .build();
    }

    private void frame() {
        if (!playing) return;
        long t = SystemClock.uptimeMillis() - start;
        float alpha = Math.min(1f, t / (float) FADE_IN_MS);
        if (t > MAX_MS - FADE_OUT_MS) alpha = Math.max(0f, (MAX_MS - t) / (float) FADE_OUT_MS);
        setAlpha(alpha);
        invalidate();
        postDelayed(frame, WorldOverlay.FRAME_MS);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!playing) return;
        c.drawColor(SKY);
        if (stars.length > 0) c.drawPoints(stars, starPaint);
        if (layout == null) return;
        long t = SystemClock.uptimeMillis() - start;
        // From just under the near end until the last line has passed the far end, by the start of the fade-out.
        float travel = planeLength + layout.getHeight();
        float progress = Math.min(1f, t / (float) (MAX_MS - FADE_OUT_MS));
        float top = planeLength - travel * progress;
        c.save();
        c.concat(plane);
        c.clipRect(0f, 0f, getWidth(), planeLength);
        c.save();
        c.translate(layoutLeft, top);
        layout.draw(c);
        c.restore();
        c.drawRect(0f, 0f, getWidth(), planeLength * 0.07f, fadePaint);
        c.restore();
    }

    /**
     * The crawl text with its placeholders filled in, in one pass over the template (what goes in is never read
     * again): {@code {names}}, {@code {years}} and {@code {episode}}, the years as a Roman numeral. Any other
     * {@code {...}} stays as written.
     */
    static String fill(String template, String names, int years) {
        if (template == null) return "";
        String who = names == null ? "" : names;
        StringBuilder out = new StringBuilder(template.length() + who.length() + 8);
        int i = 0;
        while (i < template.length()) {
            char ch = template.charAt(i);
            int close = ch == '{' ? template.indexOf('}', i + 1) : -1;
            if (close < 0) {
                out.append(ch);
                i++;
                continue;
            }
            String key = template.substring(i + 1, close);
            switch (key) {
                case "names": out.append(who); break;
                case "years": out.append(years); break;
                case "episode": out.append(roman(years)); break;
                default:
                    // Not ours: the brace stays and the scan goes on right after it, so a placeholder inside counts.
                    out.append(ch);
                    i++;
                    continue;
            }
            i = close + 1;
        }
        return out.toString();
    }

    /** {@code n} as a Roman numeral for 1 to 3999; anything else (0, negative, larger) as the Arabic number. */
    static String roman(int n) {
        if (n < 1 || n > 3999) return Integer.toString(n);
        StringBuilder out = new StringBuilder();
        int rest = n;
        for (int k = 0; k < ROMAN_VALUE.length; k++) {
            while (rest >= ROMAN_VALUE[k]) {
                out.append(ROMAN[k]);
                rest -= ROMAN_VALUE[k];
            }
        }
        return out.toString();
    }
}
