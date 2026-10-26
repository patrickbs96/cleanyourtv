package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.LightingColorFilter;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.ToIntFunction;

/**
 * A world's scene behind Home: its day or its night picture (launcher/worlds/<id>/make_scene.py), by the sun at
 * config.xml's latitude and longitude, as wallpaper_moods has it (morning and evening are day, night is night). When
 * the mood changes the new picture fades in over the old one in 300 ms, never a hard cut, and only the picture on
 * screen stays in memory after that. Clean has no scene and adds no views at all.
 *
 * <p>Jahreskreis (Seasons) shows the day's {@link Look} instead (setLook): its picture is found by name
 * ({@link #drawableName}), a day and a night one for the seasons, one for every other look, and a look without a
 * picture keeps the world's plain colour. A new look (midnight, a test date) fades in like a mood change; bright looks
 * without a night picture of their own (Easter, Mother's Day …) are dimmed by {@link #NIGHT_DIM} at night with a colour
 * filter on the scene's views only (OLED rule, spec § 4.8); a season's night picture is painted dark already.
 */
final class WorldScene {
    /** forceMood's choices: the sun decides, or always day, or always night (the hidden test switch, Task 11). */
    static final String MOOD_AUTO = "auto", MOOD_DAY = "day", MOOD_NIGHT = "night";
    /**
     * The test switch's choice in Config.SETTINGS_NAME (one of the moods). It holds while this process lives, so
     * Home's recreate (a world or setting change) keeps it, and a new process (force-stop, reboot) is back on auto.
     */
    static final String FORCE_KEY = "world_scene_force";
    /** Whether this process has already dropped an old process's FORCE_KEY: once per process, not in the prefs. */
    private static boolean processChecked;
    /** Day lasts this long after sunset, while the sky still glows: wallpaper_moods' evening. */
    private static final int DUSK_MINUTES = 30;
    private static final long FADE_MS = 300;
    /** How much darker a bright look's picture is at night (Jahreskreis, OLED rule). */
    static final float NIGHT_DIM = 0.55f;

    private final FrameLayout root;
    private final World world;
    /** Seasons: the day and look to show (setLook), and the ones on screen; null in the other worlds. */
    private LocalDate date, shownDate;
    private Look look, shownLook;
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

    /** {@code date} and {@code look}: Seasons' day and its look (SeasonsToday); ignored, and best null, elsewhere. */
    WorldScene(Context context, FrameLayout root, World w, LocalDate date, Look look) {
        this.root = root;
        this.world = w;
        if (w == World.SEASONS) {
            this.date = date;
            this.look = look;
        }
        String[] place = Config.location(context);
        latitude = place[0];
        longitude = place[1];
        forced = forcedMood(context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE));
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
        int res = resFor(context, w, day, this.look);
        Bitmap first = decode(context, res);
        if (first != null) {
            bottom.setImageBitmap(first);
            dim(bottom, this.look, day);
            shown = first;
            shownDay = day;
            shownDate = this.date;
            shownLook = this.look;
            // The picture covers the whole screen: the world's plain colour under it is overdraw.
            root.setBackground(null);
        } else if (res == 0 && w == World.SEASONS) {
            // A look without a picture yet: its plain colour is what it shows, so the minute tick has nothing to load.
            shownDay = day;
            shownDate = this.date;
            shownLook = this.look;
        }
    }

    /**
     * The test switch's mood for this process: the first scene of a new process drops what an earlier one left
     * (the switch holds only until the launcher restarts, spec § 4.5); after that the saved choice.
     */
    static String forcedMood(SharedPreferences prefs) {
        if (!processChecked) {
            processChecked = true;
            if (prefs.contains(FORCE_KEY)) prefs.edit().remove(FORCE_KEY).apply();
            return MOOD_AUTO;
        }
        String mood = prefs.getString(FORCE_KEY, MOOD_AUTO);
        return MOOD_DAY.equals(mood) || MOOD_NIGHT.equals(mood) ? mood : MOOD_AUTO;
    }

    /** The test switch: saves the mood for this process's next Home; the caller makes Home rebuild. */
    static void saveForcedMood(SharedPreferences prefs, String mood) {
        // Chosen in this process, so its next scene must keep it rather than take it for an old process's.
        processChecked = true;
        if (MOOD_DAY.equals(mood) || MOOD_NIGHT.equals(mood)) prefs.edit().putString(FORCE_KEY, mood).commit();
        else prefs.edit().remove(FORCE_KEY).commit();
    }

    /** For tests: as in a new process. */
    static void resetProcessForTest() {
        processChecked = false;
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

    /**
     * The themed world's picture for day or night, R.drawable.world_&lt;id&gt;_&lt;day|night&gt;; 0 for Clean, which has
     * none, and for Seasons, whose pictures go by look ({@link #resFor(Context, World, boolean, Look)}).
     */
    static int resFor(World w, boolean day) {
        switch (w) {
            case HOGWARTS: return day ? R.drawable.world_hogwarts_day : R.drawable.world_hogwarts_night;
            case MARVEL: return day ? R.drawable.world_marvel_day : R.drawable.world_marvel_night;
            case STARWARS: return day ? R.drawable.world_starwars_day : R.drawable.world_starwars_night;
            default: return 0;
        }
    }

    /** Seasons' picture name for a look: world_seasons_&lt;id&gt;, with _day or _night for the looks that have both. */
    static String drawableName(Look look, boolean day) {
        String name = "world_seasons_" + look.id;
        return look.hasNight ? name + (day ? "_day" : "_night") : name;
    }

    /**
     * The picture for this world, mood and look: Seasons' found by name (the looks add theirs without code), 0 when
     * it has none (its plain colour stays); the other worlds' as {@link #resFor(World, boolean)}.
     */
    static int resFor(Context context, World w, boolean day, Look look) {
        return resFor(w, day, look,
                name -> context.getResources().getIdentifier(name, "drawable", context.getPackageName()));
    }

    /** The same with the lookup by name given (getIdentifier in the app, a stand-in in tests). */
    static int resFor(World w, boolean day, Look look, ToIntFunction<String> byName) {
        if (w != World.SEASONS) return resFor(w, day);
        return look != null ? byName.applyAsInt(drawableName(look, day)) : 0;
    }

    /**
     * Whether Seasons' scene must load another picture: nothing is on screen yet, the look has changed (Nikolaus' eve
     * to Nikolaus at midnight, a test date), or the mood has and that shows in this look (a season's other picture, a
     * bright look's dimming). The same look on a new day keeps its picture: what changes with the day (Advent's
     * candles) is the overlay's.
     */
    static boolean needsReload(LocalDate shownDate, Look shownLook, LocalDate now, Look nowLook, Boolean shownDay,
                               boolean day) {
        if (shownDate == null || shownLook == null || shownDay == null) return true;
        if (nowLook != shownLook) return true;
        if (shownDay == day) return false;
        return nowLook != null && (nowLook.hasNight || dimFor(nowLook, true) != dimFor(nowLook, false));
    }

    /** {@link #seasonsChange}: nothing to do, only the mood moves on (the overlay follows at once), or a new picture. */
    static final int KEEP = 0, MOOD = 1, RELOAD = 2;

    /**
     * What Seasons' scene does on an update: {@link #RELOAD} as {@link #needsReload} (the overlay then follows the mood
     * when the new picture shows, not before its decode and fade); {@link #MOOD} when only the mood has changed and the
     * look shows it nowhere (the same picture by day and night, or a plain colour), so the overlay follows at once;
     * otherwise {@link #KEEP}.
     */
    static int seasonsChange(LocalDate shownDate, Look shownLook, LocalDate now, Look nowLook, Boolean shownDay,
                             boolean day) {
        if (needsReload(shownDate, shownLook, now, nowLook, shownDay, day)) return RELOAD;
        return shownDay != day ? MOOD : KEEP;
    }

    /**
     * How much a look's picture is darkened: {@link #NIGHT_DIM} at night for a bright look without its own night
     * picture, otherwise none. Spring is bright but has a painted night picture, which is not darkened again.
     */
    static float dimFor(Look look, boolean day) {
        return look != null && look.bright && !look.hasNight && !day ? NIGHT_DIM : 0f;
    }

    /** Seasons only: darkens the scene view for this look and mood, or clears it; the other worlds stay untouched. */
    private void dim(ImageView view, Look l, boolean day) {
        if (world != World.SEASONS) return;
        float d = dimFor(l, day);
        if (d <= 0f) {
            view.clearColorFilter();
            return;
        }
        int k = Math.round(255 * (1f - d));
        view.setColorFilter(new LightingColorFilter(0xFF000000 | (k << 16) | (k << 8) | k, 0));
    }

    /** Seasons' day and look (SeasonsToday, on resume and the minute tick); a change fades the new picture in. */
    void setLook(LocalDate date, Look look) {
        if (world != World.SEASONS) return;
        this.date = date;
        this.look = look;
        update();
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

    /**
     * Day or night as the scene shows it now: the mood of the picture on screen (or of Seasons' plain colour), which
     * lags {@link #isDay} by a picture's decode and fade; isDay() before anything shows. The overlay follows this.
     */
    boolean showsDay() {
        return shownDay != null ? shownDay : isDay();
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

    /** auto, day or night (anything else is auto), for this Home only; the scene follows at once. */
    void forceMood(String mood) {
        forced = MOOD_DAY.equals(mood) || MOOD_NIGHT.equals(mood) ? mood : MOOD_AUTO;
        update();
    }

    /** From the minute tick: when the mood (or Seasons' look) has changed, fades the other picture in. */
    void update() {
        if (bottom == null || !active || fading) return;
        boolean day = isDay();
        if (world == World.SEASONS) {
            int change = seasonsChange(shownDate, shownLook, date, look, shownDay, day);
            if (change == MOOD) {
                shownDay = day;
                if (listener != null) listener.run();
            }
            if (change != RELOAD) return;
        } else if (shownDay != null && shownDay == day) {
            return;
        }
        Context context = root.getContext();
        int res = resFor(context, world, day, look);
        if (res == 0) {
            // A look without a picture keeps the plain colour, and is marked shown: no lookup or decode a minute.
            if (world == World.SEASONS) showPlainColour(day);
            return;
        }
        fading = true;
        Look nextLook = look;
        LocalDate nextDate = date;
        // Decoded off the UI thread; it happens twice a day (and when Seasons' look changes).
        new Thread(() -> {
            Bitmap next = decode(context, res);
            root.post(() -> show(next, day, nextLook, nextDate));
        }, "WorldScene").start();
    }

    /** Seasons on a look without a picture: the world's plain colour, the last picture (if any) released. */
    private void showPlainColour(boolean day) {
        shownDay = day;
        shownLook = look;
        shownDate = date;
        if (shown != null) {
            Bitmap old = shown;
            shown = null;
            bottom.setImageDrawable(null);
            bottom.clearColorFilter();
            root.setBackgroundColor(world.backgroundColor);
            bottom.postDelayed(old::recycle, 500);
        }
        // The mood may have changed with the look (a bright look's dimming): the overlay follows the scene.
        if (listener != null) listener.run();
    }

    private void show(Bitmap next, boolean day, Look nextLook, LocalDate nextDate) {
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
            // Nothing to fade from (the first picture could not be read, or Seasons came from a plain colour): put
            // it on screen.
            bottom.setImageBitmap(next);
            dim(bottom, nextLook, day);
            shown = next;
            shownDay = day;
            shownLook = nextLook;
            shownDate = nextDate;
            root.setBackground(null);
            fading = false;
            if (changed != null) changed.run();
            if (world == World.SEASONS) update();   // the look may have changed meanwhile
            return;
        }
        shownDay = day;
        shownLook = nextLook;
        shownDate = nextDate;
        top.setImageBitmap(next);
        dim(top, nextLook, day);
        top.setAlpha(0f);
        top.setVisibility(View.VISIBLE);
        if (changed != null) changed.run();
        top.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> {
            // The new picture moves down in the same frame the top one hides, so nothing flashes; the old one is
            // released after a frame has drawn without it.
            Bitmap old = shown;
            bottom.setImageBitmap(next);
            dim(bottom, nextLook, day);
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
