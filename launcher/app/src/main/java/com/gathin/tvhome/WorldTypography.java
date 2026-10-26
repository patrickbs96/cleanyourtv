package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

/**
 * The header and the hero name in a world's type (spec § 4.4): Cormorant Garamond in Hogwarts, Oswald in Marvel,
 * Michroma in Star Wars, with each world's colours, spacing and hero style, and the greeting words of § 4.9.
 * Clean returns before touching anything, so its views keep what the layout and Config gave them.
 */
final class WorldTypography {
    private WorldTypography() {}

    static final int HOG_CREAM = 0xFFF5E9C8;
    static final int HOG_GOLD = 0xFFC9A84C;
    static final int MARVEL_GOLD = 0xFFE6B85A;
    static final int STARWARS_HOLO = 0xFF8FB3FF;

    /** The greeting's size and spacing next to Clean's 12sp/0.22, from the mockup (vier-welten-v3). */
    private static final float HOG_GREETING_SCALE = 1.27f;
    private static final float STARWARS_TEXT_SCALE = 0.82f;
    /** Star Wars draws its clock 10 % smaller than Clean, Michroma being much wider than Outfit. */
    private static final float STARWARS_CLOCK_SCALE = 0.9f;
    private static final float STARWARS_HERO_SCALE = 0.75f;
    /** The Hogwarts hero prefix ("ZAUBERSPIEGEL ZEIGT") at 30 % of the name's size, spaced 0.3 em. */
    private static final float HOG_PREFIX_SIZE = 0.3f;
    private static final float HOG_PREFIX_SPACING = 0.3f;

    /** Fonts, colours, spacing and the hero's style for this world; nothing at all for Clean. */
    static void apply(World w, TextView greeting, TextView clock, TextView date, View clockDivider,
                      TextView heroName, TextView heroMeta) {
        if (w.isClean()) return;
        Context c = clock.getContext();
        float dp = c.getResources().getDisplayMetrics().density;
        greeting.setTextColor(greetingColor(w, greeting.getCurrentTextColor()));
        switch (w) {
            case HOGWARTS: {
                Typeface regular = UiFont.get(c, R.string.font_world_hog_regular);
                clock.setTypeface(UiFont.get(c, R.string.font_world_hog_light));
                clock.setLetterSpacing(0f);
                clock.setTextColor(HOG_CREAM);
                greeting.setTypeface(regular);
                greeting.setLetterSpacing(0.3f);
                scaleText(greeting, HOG_GREETING_SCALE);
                date.setTypeface(regular);
                date.setTextColor(HOG_GOLD);
                clockDivider.setBackgroundColor(0x66C9A84C);   // gold, 40 %
                heroName.setTypeface(UiFont.get(c, R.string.font_world_hog_italic));
                heroName.setTextColor(HOG_CREAM);
                heroName.setLetterSpacing(0f);
                // Two lines: the small prefix over the name (heroText). Exactly two, reserved before the first
                // text arrives: showHero places the shelf by its height before fillHero sets the text, so a
                // height that grew with the text would put the first showing a line too low. Bottom gravity
                // keeps the name on the dock side of the reserved room.
                heroName.setSingleLine(false);
                heroName.setLines(2);
                heroName.setEllipsize(TextUtils.TruncateAt.END);
                heroName.setGravity(Gravity.BOTTOM | Gravity.START);
                heroMeta.setTypeface(regular);
                break;
            }
            case MARVEL: {
                clock.setTypeface(UiFont.get(c, R.string.font_world_marvel_bold));
                clock.setLetterSpacing(0.02f);
                clock.setTextColor(0xFFFFFFFF);   // opaque, so the gradient below shows at full strength
                verticalGradient(clock, new int[]{0xFFFFF2D0, MARVEL_GOLD, 0xFFA86D1F}, new float[]{0f, 0.55f, 1f});
                clock.setShadowLayer(10 * dp, 0, 2 * dp, 0x59E6B85A);   // soft gold, 35 %
                greeting.setTypeface(UiFont.get(c, R.string.font_world_marvel_medium));
                greeting.setLetterSpacing(0.35f);
                Typeface light = UiFont.get(c, R.string.font_world_marvel_light);
                date.setTypeface(light);
                date.setTextColor(0xFFF0D9B0);
                clockDivider.setBackgroundColor(0x66E6B85A);
                heroName.setTypeface(UiFont.get(c, R.string.font_world_marvel_bold));
                heroName.setLetterSpacing(0.06f);
                heroName.setTextColor(0xFFFFFFFF);
                verticalGradient(heroName, new int[]{0xFFFFFFFF, MARVEL_GOLD, 0xFFB4201F}, new float[]{0f, 0.6f, 1f});
                heroMeta.setTypeface(light);
                break;
            }
            case STARWARS: {
                Typeface michroma = UiFont.get(c, R.string.font_world_starwars);
                clock.setTypeface(michroma);
                clock.setLetterSpacing(0.05f);
                clock.setTextColor(0xFFBCD3FF);
                scaleText(clock, STARWARS_CLOCK_SCALE);
                clock.setShadowLayer(12 * dp, 0, 0, 0x995B8CFF);   // blue glow, 60 %
                greeting.setTypeface(michroma);
                greeting.setLetterSpacing(0.35f);
                scaleText(greeting, STARWARS_TEXT_SCALE);
                date.setTypeface(michroma);
                date.setTextColor(STARWARS_HOLO);
                scaleText(date, STARWARS_TEXT_SCALE);
                clockDivider.setBackgroundColor(0x668FB3FF);
                heroName.setTypeface(michroma);
                heroName.setLetterSpacing(0.12f);
                heroName.setTextColor(0xFFFFE81F);   // crawl yellow
                scaleText(heroName, STARWARS_HERO_SCALE);
                heroName.setShadowLayer(10 * dp, 0, 0, 0x59FFE81F);
                // Tipped back 18° from its bottom edge, the way the crawl lies in the picture.
                heroName.setRotationX(18f);
                heroName.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                    v.setPivotX(0f);
                    v.setPivotY(b - t);
                });
                heroMeta.setTypeface(michroma);
                break;
            }
            default:
                break;
        }
    }

    /**
     * The weather line (Weather, spec § 4.10c) in the date's type, colour, size and spacing, once the world has styled
     * the date (and shamsi_date has resized it); Clean returns and keeps the layout's stats look.
     */
    static void applyWeather(World w, TextView date, TextView weather) {
        if (w.isClean()) return;
        weather.setTypeface(date.getTypeface());
        weather.setTextColor(date.getCurrentTextColor());
        weather.setTextSize(TypedValue.COMPLEX_UNIT_PX, date.getTextSize());
        weather.setLetterSpacing(date.getLetterSpacing());
        weather.setAllCaps(true);   // as the date, which the layout writes in capitals
    }

    /**
     * The hero line for this app label. Hogwarts puts the small, spaced gold "ZAUBERSPIEGEL ZEIGT" over the name
     * (drawn in the italic the hero view has); Marvel and Star Wars write it in capitals; Clean leaves it as it is.
     */
    static CharSequence heroText(World w, String label, Resources res) {
        String name = heroLabel(w, label);
        if (w.heroPrefixRes == 0) return name;
        String prefix = res.getString(w.heroPrefixRes).toUpperCase(Locale.getDefault());
        SpannableStringBuilder text = new SpannableStringBuilder(prefix).append('\n').append(name);
        int end = prefix.length();
        text.setSpan(new RelativeSizeSpan(HOG_PREFIX_SIZE), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(HOG_GOLD), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new PrefixSpan(UiFont.get(res, R.string.font_world_hog_regular), HOG_PREFIX_SPACING),
                0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    /** The app label in the world's casing: capitals in Marvel and Star Wars, unchanged in Clean and Hogwarts. */
    static String heroLabel(World w, String label) {
        if (w == World.MARVEL || w == World.STARWARS) return label.toUpperCase(Locale.getDefault());
        return label;
    }

    /** "Nox · Guten Abend", "Guten Abend · Äußerer Rand": the world's words around the day's greeting. */
    static String greetingText(World w, String base, boolean day) {
        String prefix = w.greetingPrefix(day);
        String suffix = w.greetingSuffix();
        String text = prefix.isEmpty() ? base : prefix + " · " + base;
        return suffix.isEmpty() ? text : text + " · " + suffix;
    }

    /** The greeting's colour in this world; Clean keeps fallback, the layout's or Config's. */
    static int greetingColor(World w, int fallback) {
        switch (w) {
            case HOGWARTS: return 0xFFE8C87A;
            case MARVEL: return MARVEL_GOLD;
            case STARWARS: return STARWARS_HOLO;
            default: return fallback;
        }
    }

    /** Day for the greeting words until the scene's sun times arrive (Task 4): 7:00 to 18:59. */
    static boolean isDayHour(int hour) {
        return hour >= 7 && hour <= 18;
    }

    private static void scaleText(TextView view, float factor) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.getTextSize() * factor);
    }

    /** A top-to-bottom gradient over the view's text, refitted whenever its height changes. */
    private static void verticalGradient(TextView view, int[] colors, float[] positions) {
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t == ob - ot && view.getPaint().getShader() != null) return;
            view.getPaint().setShader(new LinearGradient(0, 0, 0, b - t, colors, positions, Shader.TileMode.CLAMP));
            view.invalidate();
        });
    }

    /** The Hogwarts prefix's upright face and wide spacing inside the italic hero line. */
    private static final class PrefixSpan extends MetricAffectingSpan {
        private final Typeface face;
        private final float spacing;

        PrefixSpan(Typeface face, float spacing) {
            this.face = face;
            this.spacing = spacing;
        }

        @Override public void updateMeasureState(TextPaint paint) {
            apply(paint);
        }

        @Override public void updateDrawState(TextPaint paint) {
            apply(paint);
        }

        private void apply(TextPaint paint) {
            paint.setTypeface(face);
            paint.setLetterSpacing(spacing);
        }
    }
}
