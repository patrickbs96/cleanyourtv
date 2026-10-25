package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * A world's scene behind Home: its day or its night picture (launcher/worlds/<id>/make_scene.py), by the sun at
 * config.xml's latitude and longitude, as wallpaper_moods has it (morning and evening are day, night is night). When
 * the mood changes the new picture fades in over the old one in 300 ms, never a hard cut, and only the picture on
 * screen stays in memory after that. Clean has no scene and adds no views at all.
 */
final class WorldScene {
    /** forceMood's choices: the sun decides, or always day, or always night (the hidden test switch, Task 11). */
    static final String MOOD_AUTO = "auto", MOOD_DAY = "day", MOOD_NIGHT = "night";
    /** Day lasts this long after sunset, while the sky still glows: wallpaper_moods' evening. */
    private static final int DUSK_MINUTES = 30;
    private static final long FADE_MS = 300;

    private final FrameLayout root;
    private final World world;
    /** The picture on screen, and above it the next one while it fades in; null in Clean. */
    private final ImageView bottom, top;
    private final String latitude, longitude;
    private LocalDate sunDay;
    private int[] sun;
    private String forced = MOOD_AUTO;
    /** The mood of the picture on screen; null before the first one (or when it could not be read). */
    private Boolean shownDay;
    private Bitmap shown;
    private boolean active;
    private boolean fading;
    private Runnable listener;

    WorldScene(Context context, FrameLayout root, World w) {
        this.root = root;
        this.world = w;
        latitude = context.getString(R.string.latitude);
        longitude = context.getString(R.string.longitude);
        if (w.isClean()) {
            bottom = null;
            top = null;
            return;
        }
        bottom = makeView(context);
        top = makeView(context);
        top.setVisibility(View.INVISIBLE);
        top.setAlpha(0f);
        // Under everything else of Home (and above a theme's wallpaper, which a world overrides).
        root.addView(bottom, 0, matchParent());
        root.addView(top, 1, matchParent());
        // The first picture is decoded right here, so Home's first frame already has it, as with the wallpaper.
        boolean day = isDay();
        Bitmap first = decode(context, resFor(w, day));
        if (first != null) {
            bottom.setImageBitmap(first);
            shown = first;
            shownDay = day;
            // The picture covers the whole screen: the world's plain colour under it is overdraw.
            root.setBackground(null);
        }
    }

    private static ImageView makeView(Context context) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setFocusable(false);
        return view;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** Day from sunrise until half an hour after sunset (wallpaper_moods' morning and evening), night the rest. */
    static boolean isDay(int minuteOfDay, int sunrise, int sunset) {
        return minuteOfDay >= sunrise && minuteOfDay < sunset + DUSK_MINUTES;
    }

    /** The same with forceMood's choice first: day or night as forced, the sun's for auto (and anything unknown). */
    static boolean isDay(String forced, int minuteOfDay, int sunrise, int sunset) {
        if (MOOD_DAY.equals(forced)) return true;
        if (MOOD_NIGHT.equals(forced)) return false;
        return isDay(minuteOfDay, sunrise, sunset);
    }

    /** The world's picture for day or night, R.drawable.world_<id>_<day|night>; 0 for Clean, which has none. */
    static int resFor(World w, boolean day) {
        switch (w) {
            case HOGWARTS: return day ? R.drawable.world_hogwarts_day : R.drawable.world_hogwarts_night;
            case MARVEL: return day ? R.drawable.world_marvel_day : R.drawable.world_marvel_night;
            case STARWARS: return day ? R.drawable.world_starwars_day : R.drawable.world_starwars_night;
            default: return 0;
        }
    }

    /** Whether it is day now, by the sun (worked out once a day) or by forceMood. Also for Hogwarts' Lumos/Nox. */
    boolean isDay() {
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(sunDay)) {
            sunDay = now.toLocalDate();
            sun = WallpaperMood.sun(sunDay, latitude, longitude, ZoneId.systemDefault());
        }
        return isDay(forced, now.getHour() * 60 + now.getMinute(), sun[0], sun[1]);
    }

    void start() {
        active = true;
        update();
    }

    void stop() {
        active = false;
    }

    /** Called when the mood of the scene changes (the overlay follows it); null for none. */
    void setListener(Runnable onMoodChanged) {
        listener = onMoodChanged;
    }

    /** Zooms the picture round the screen's middle, 1 for none (Star Wars' app start, spec § 4.7). */
    void setZoom(float scale) {
        if (bottom == null) return;
        bottom.setScaleX(scale);
        bottom.setScaleY(scale);
        top.setScaleX(scale);
        top.setScaleY(scale);
    }

    /** auto, day or night (anything else is auto), until the next Home start; the scene follows at once. */
    void forceMood(String mood) {
        forced = MOOD_DAY.equals(mood) || MOOD_NIGHT.equals(mood) ? mood : MOOD_AUTO;
        update();
    }

    /** From the minute tick: when the mood has changed, fades the other picture in. */
    void update() {
        if (bottom == null || !active || fading) return;
        boolean day = isDay();
        if (shownDay != null && shownDay == day) return;
        int res = resFor(world, day);
        Context context = root.getContext();
        fading = true;
        // Decoded off the UI thread; it happens twice a day.
        new Thread(() -> {
            Bitmap next = decode(context, res);
            root.post(() -> show(next, day));
        }, "WorldScene").start();
    }

    private void show(Bitmap next, boolean day) {
        if (next == null) {
            fading = false;
            return;   // unreadable: keep the picture on screen (or the world's colour)
        }
        if (!active) {
            // Home went away meanwhile: drop it; start() decodes again.
            fading = false;
            next.recycle();
            return;
        }
        Runnable changed = listener;
        if (shown == null) {
            // Nothing to fade from (the first picture could not be read): put it on screen.
            bottom.setImageBitmap(next);
            shown = next;
            shownDay = day;
            root.setBackground(null);
            fading = false;
            if (changed != null) changed.run();
            return;
        }
        shownDay = day;
        top.setImageBitmap(next);
        top.setAlpha(0f);
        top.setVisibility(View.VISIBLE);
        if (changed != null) changed.run();
        top.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> {
            // The new picture moves down in the same frame the top one hides, so nothing flashes; the old one is
            // released after a frame has drawn without it.
            Bitmap old = shown;
            bottom.setImageBitmap(next);
            shown = next;
            top.setVisibility(View.INVISIBLE);
            top.setImageDrawable(null);
            top.setAlpha(0f);
            fading = false;
            if (old != null && old != next) bottom.postDelayed(old::recycle, 500);
            update();   // forceMood may have asked for the other mood meanwhile
        }).start();
    }

    /** One picture as RGB_565 (2 bytes a pixel, 4 MB at 1080p), or null when it cannot be read. */
    private static Bitmap decode(Context context, int res) {
        if (res == 0) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inScaled = false;
            return BitmapFactory.decodeResource(context.getResources(), res, options);
        } catch (Exception | OutOfMemoryError failure) {
            Log.w("TvHome.WorldScene", "Scene not readable", failure);
            return null;
        }
    }
}
