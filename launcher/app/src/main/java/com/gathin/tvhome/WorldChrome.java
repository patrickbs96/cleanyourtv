package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * The header's world chrome (spec § 4.4): each world's pill backgrounds (gold medallions, HUD switches, holo
 * switches) on every pill of the top bar, Hogwarts' candle, owl and star icons and its "⟡ ✦ ⟡" under the date,
 * Marvel's HUD corners and Star Wars' scanline field around the greeting, clock and date. The pills' icon-only shape
 * comes from MainActivity (World.forcesIconOnlyPills) and the header's type from WorldTypography. Clean returns before
 * touching anything, so its pills keep pill_bg and the three views stay gone.
 */
final class WorldChrome {
    private WorldChrome() {}

    static final String HOG_ORNAMENT = "⟡ ✦ ⟡";
    /** Marvel's HUD lines: #78C8FF at 35 %. */
    static final int HUD_COLOR = 0x5978C8FF;
    /** Star Wars' scanlines: one line of #5B8CFF at 8 % every 3 px, as the mockup's repeating gradient. */
    static final int SCANLINE_COLOR = 0x145B8CFF;
    static final int SCANLINE_PERIOD_PX = 3;
    /** HUD and scanlines reach this far past the header's text on its right, and (layout margins) 12dp on its left. */
    private static final int FIELD_END_DP = 16;
    private static final int FIELD_START_DP = 12;
    /** The VPN warning dot's diameter. */
    private static final int VPN_DOT_DP = 9;

    static void apply(World w, Activity a, TextView btnInputs, TextView btnWifi, TextView btnSettings,
                      View worldHud, TextView worldOrnament, View scanlines) {
        if (w.isClean()) return;
        float dp = a.getResources().getDisplayMetrics().density;
        stylePills(w, (ViewGroup) btnSettings.getParent());
        switch (w) {
            case HOGWARTS:
                setIcon(btnInputs, R.drawable.ic_world_hog_candle);
                setIcon(btnWifi, R.drawable.ic_world_hog_owl);   // kept by updateWifi (ownsNetworkIcon)
                setIcon(btnSettings, R.drawable.ic_world_hog_star);
                worldOrnament.setText(HOG_ORNAMENT);
                worldOrnament.setTextColor(WorldTypography.HOG_GOLD);
                worldOrnament.setAlpha(0.8f);
                worldOrnament.setVisibility(View.VISIBLE);
                break;
            case MARVEL:
                worldHud.setBackground(new HudCornersDrawable(dp));
                fitToHeader(worldHud, a, dp);
                worldHud.setVisibility(View.VISIBLE);
                break;
            case STARWARS:
                scanlines.setBackground(scanlineField(a));
                fitToHeader(scanlines, a, dp);
                scanlines.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }

    /**
     * Whether the world draws its own icon on the network pill (Hogwarts' owl), which then keeps it instead of the
     * Wi-Fi, Ethernet or offline glyph; the status colours still tint it. False for Clean.
     */
    static boolean ownsNetworkIcon(World w) {
        return w == World.HOGWARTS;
    }

    /**
     * The worlds' stand-in for the red "VPN off" capsule, which icon-only pills have no text for: a red dot on the
     * top-right corner of the network pill's icon, in the capsule's red with a dark rim so it reads on the light
     * focused pill too. Drawn in the pill's overlay, so icon changes and the status tint leave it alone; it starts
     * hidden (alpha 0), and {@link #showVpnWarning} shows it. Callers add it only outside Clean.
     */
    static Drawable addVpnDot(TextView pill) {
        float dp = pill.getResources().getDisplayMetrics().density;
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(VpnBadge.RED);
        dot.setStroke(Math.round(1.5f * dp), 0xCC000000);
        dot.setAlpha(0);
        int size = Math.round(VPN_DOT_DP * dp);
        int inset = Math.round(dp);
        pill.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            Drawable icon = pill.getCompoundDrawablesRelative()[0];
            int iconW = icon != null ? icon.getIntrinsicWidth() : Math.round(22 * dp);
            int iconH = icon != null ? icon.getIntrinsicHeight() : Math.round(22 * dp);
            int w = r - l, h = b - t;
            // Icon-only, the icon sits centred between the paddings; with a label (wifi_label_width) at the start.
            int iconLeft = TextUtils.isEmpty(pill.getText())
                    ? pill.getPaddingLeft() + (w - pill.getPaddingLeft() - pill.getPaddingRight() - iconW) / 2
                    : pill.getPaddingLeft();
            int iconTop = (h - iconH) / 2;
            int right = iconLeft + iconW + inset, top = iconTop - inset;
            dot.setBounds(right - size, top, right, top + size);
        });
        pill.getOverlay().add(dot);
        return dot;
    }

    /** Shows or hides the dot from {@link #addVpnDot}: shown while a configured VPN is down. */
    static void showVpnWarning(TextView pill, Drawable dot, boolean vpnDown) {
        int alpha = vpnDown ? 255 : 0;
        if (dot.getAlpha() == alpha) return;
        dot.setAlpha(alpha);
        pill.invalidate();
    }

    /** The world's pill background; Clean's pill_bg for Clean. */
    static int pillBackground(World w) {
        switch (w) {
            case HOGWARTS: return R.drawable.pill_bg_hogwarts;
            case MARVEL: return R.drawable.pill_bg_marvel;
            case STARWARS: return R.drawable.pill_bg_starwars;
            default: return R.drawable.pill_bg;
        }
    }

    /**
     * The world's background on every button of the top bar: Inputs, network, reorder, library, top_bar_apps and
     * Settings, shown or not, so a pill that appears later (Inputs once the TV lists some) has it too. Each gets its
     * own drawable, as a state list keeps its view's focus state.
     */
    private static void stylePills(World w, ViewGroup bar) {
        int background = pillBackground(w);
        for (int i = 0; i < bar.getChildCount(); i++) bar.getChildAt(i).setBackgroundResource(background);
    }

    /**
     * A world's own icon on a pill. The pill's tint (pill_text, or the network status colours) stays, so focus still
     * turns the icon dark.
     */
    private static void setIcon(TextView pill, int icon) {
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
    }

    /** A 1 x 3 px tile, its top pixel the scanline colour, repeated over the field. */
    private static Drawable scanlineField(Activity a) {
        Bitmap tile = Bitmap.createBitmap(1, SCANLINE_PERIOD_PX, Bitmap.Config.ARGB_8888);
        tile.setPixel(0, 0, SCANLINE_COLOR);
        BitmapDrawable field = new BitmapDrawable(a.getResources(), tile);
        field.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        field.setFilterBitmap(false);
        return field;
    }

    /**
     * Sizes the field (laid out from leftBlock's start, top and bottom) to the header's text: as wide as the widest
     * of greeting, clock and date lines reach, plus a margin, instead of leftBlock's full width up to the pills.
     * Refitted after each layout of leftBlock (the clock's width changes by the minute) and when the date or greeting
     * text changes, which a fixed-width TextView does without a new layout.
     */
    private static void fitToHeader(View field, Activity a, float dp) {
        View block = a.findViewById(R.id.leftBlock);
        TextView[] texts = {
                a.findViewById(R.id.greeting), a.findViewById(R.id.clock), a.findViewById(R.id.date),
                a.findViewById(R.id.shamsiDate), a.findViewById(R.id.lunarDate)};
        int start = Math.round(FIELD_START_DP * dp);
        int end = Math.round(FIELD_END_DP * dp);
        Runnable refit = () -> {
            int right = 0;
            for (TextView text : texts) {
                // isShown, not the own visibility: shamsiDate and lunarDate can sit in a gone parent.
                if (text != null && text.isShown()) right = Math.max(right, textRight(text, block));
            }
            int width = Math.min(right, block.getWidth()) + start + end;
            ViewGroup.LayoutParams lp = field.getLayoutParams();
            if (right > 0 && lp.width != width) {
                lp.width = width;
                field.setLayoutParams(lp);
            }
        };
        // Posted: a layout change listener runs inside the layout pass, where a new layout would be deferred anyway.
        block.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> field.post(refit));
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                field.post(refit);
            }
        };
        for (TextView text : texts) {
            if (text != null) text.addTextChangedListener(watcher);
        }
    }

    /** Where the text's longest line ends, in the ancestor's coordinates (the header texts are gravity left). */
    private static int textRight(TextView text, View ancestor) {
        int left = 0;
        for (View v = text; v != null && v != ancestor; v = (View) v.getParent()) left += v.getLeft();
        Layout layout = text.getLayout();
        if (layout == null) return left + text.getWidth();
        float widest = 0;
        for (int i = 0; i < layout.getLineCount(); i++) widest = Math.max(widest, layout.getLineWidth(i));
        return left + text.getTotalPaddingLeft() + Math.round(widest);
    }

    /**
     * Marvel's HUD frame, as the mockup's: thin lines along the whole top and left edge, meeting in a 4dp round corner,
     * and a short bracket in the bottom right corner (30 % of the width, 40 % of the height). Paths are built when the
     * bounds change, never while drawing.
     */
    static final class HudCornersDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF corner = new RectF();
        private final float radius;

        HudCornersDrawable(float dp) {
            radius = 4 * dp;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, dp));
            paint.setColor(HUD_COLOR);
        }

        @Override protected void onBoundsChange(Rect bounds) {
            float half = paint.getStrokeWidth() / 2f;
            float l = bounds.left + half, t = bounds.top + half, r = bounds.right - half, b = bounds.bottom - half;
            path.reset();
            if (r <= l || b <= t) return;
            // Top left: down the left edge, round the corner, along the top.
            path.moveTo(l, b);
            path.lineTo(l, t + radius);
            corner.set(l, t, l + 2 * radius, t + 2 * radius);
            path.arcTo(corner, 180f, 90f, false);
            path.lineTo(r, t);
            // Bottom right bracket.
            path.moveTo(r - (r - l) * 0.3f, b);
            path.lineTo(r, b);
            path.lineTo(r, b - (b - t) * 0.4f);
        }

        @Override public void draw(Canvas canvas) {
            canvas.drawPath(path, paint);
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(Math.round(Color.alpha(HUD_COLOR) * alpha / 255f));
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
