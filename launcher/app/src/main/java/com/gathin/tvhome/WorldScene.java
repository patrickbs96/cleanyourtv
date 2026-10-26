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
 * <p>Middle-earth has three moods ({@link Mood}): its morning picture from the sunrise to 11:00, then its day one
 * (Mittelerde spec § 4.1). Its pictures are found by name ({@link #drawableName(World, Mood)}); until they are there
 * it keeps its plain colour.
 *
 * <p>Jahreskreis (Seasons) shows the day's {@link Look} instead (setLook): its picture is found by name
 * ({@link #drawableName}), a day and a night one for the seasons, one for every other look, and a look without a
 * picture keeps the world's plain colour. A new look (midnight, a test date) fades in like a mood change; bright looks
 * without a night picture of their own (Easter, Mother's Day …) are dimmed by {@link #NIGHT_DIM} at night with a colour
 * filter on the scene's views only (OLED rule, spec § 4.8); a season's night picture is painted dark already.
 */
final class WorldScene {
    /**
     * The scene's moods. Every world knows day and night; Middle-earth ({@link World#hasMorning}) splits its day into
     * a morning until 11:00 and the day after (Mittelerde spec § 4.1). A morning counts as day wherever only day and
     * night matter (the overlays' setDay, Hogwarts' Lumos, Supernatural's words).
     */
    enum Mood {
        MORNING("morning"), DAY("day"), NIGHT("night");

        /** As forceMood saves it, and the end of Middle-earth's picture name (world_middleearth_&lt;id&gt;). */
        final String id;

        Mood(String id) {
            this.id = id;
        }

        boolean isDay() {
            return this != NIGHT;
        }
    }

    /**
     * forceMood's choices: the sun decides, or always morning, day or night (the hidden test switch, Task 11).
     * Morning is Middle-earth's; a world without one shows its day.
     */
    static final String MOOD_AUTO = "auto", MOOD_MORNING = "morning", MOOD_DAY = "day", MOOD_NIGHT = "night";
    /** Middle-earth's morning ends at 11:00 (spec § 4.1), in minutes of the day. */
    static final int MORNING_END = 11 * 60;
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
    private Mood shownMood;
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
        Mood mood = mood();
        int res = resFor(context, w, mood, this.look);
        Bitmap first = decode(context, res);
        if (first != null) {
            bottom.setImageBitmap(first);
            dim(bottom, this.look, mood.isDay());
            shown = first;
            shownMood = mood;
            shownDate = this.date;
            shownLook = this.look;
            // The picture covers the whole screen: the world's plain colour under it is overdraw.
            root.setBackground(null);
        } else if (res == 0) {
            // No picture (yet) for this look or mood (Seasons' plain looks, Middle-earth before its pictures): its
            // plain colour is what it shows, so the minute tick has nothing to load.
            shownMood = mood;
            shownDate = this.date;
            shownLook = this.look;
        }
    }

    /** forceMood's choice as given, or auto for anything unknown. */
    private static String knownMood(String mood) {
        return MOOD_MORNING.equals(mood) || MOOD_DAY.equals(mood) || MOOD_NIGHT.equals(mood) ? mood : MOOD_AUTO;
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
        return knownMood(prefs.getString(FORCE_KEY, MOOD_AUTO));
    }

    /** The test switch: saves the mood for this process's next Home; the caller makes Home rebuild. */
    static void saveForcedMood(SharedPreferences prefs, String mood) {
        // Chosen in this process, so its next scene must keep it rather than take it for an old process's.
        processChecked = true;
        String known = knownMood(mood);
        if (!MOOD_AUTO.equals(known)) prefs.edit().putString(FORCE_KEY, known).commit();
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

    /**
     * The same with forceMood's choice first: day (a forced morning is day too) or night as forced, the sun's for
     * auto (and anything unknown).
     */
    static boolean isDay(String forced, int minuteOfDay, int sunrise, int sunset) {
        if (MOOD_DAY.equals(forced) || MOOD_MORNING.equals(forced)) return true;
        if (MOOD_NIGHT.equals(forced)) return false;
        return isDay(minuteOfDay, sunrise, sunset);
    }

    /**
     * World {@code w}'s mood at this minute, forceMood's choice first. Night outside {@link #isDay(int, int, int)};
     * in a world with a morning ({@link World#hasMorning}) morning from the sunrise until {@link #MORNING_END}, so
     * none when the sun rises after 11:00, and day from then on; every other world has day and night only. A forced
     * morning is the day of a world without one; anything unknown is auto.
     */
    static Mood mood(World w, String forced, int minuteOfDay, int sunrise, int sunset) {
        if (MOOD_MORNING.equals(forced)) return w.hasMorning() ? Mood.MORNING : Mood.DAY;
        if (MOOD_DAY.equals(forced)) return Mood.DAY;
        if (MOOD_NIGHT.equals(forced)) return Mood.NIGHT;
        if (!isDay(minuteOfDay, sunrise, sunset)) return Mood.NIGHT;
        return w.hasMorning() && minuteOfDay < MORNING_END ? Mood.MORNING : Mood.DAY;
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
            case SUPERNATURAL: return day ? R.drawable.world_supernatural_day : R.drawable.world_supernatural_night;
            default: return 0;
        }
    }

    /** Seasons' picture name for a look: world_seasons_&lt;id&gt;, with _day or _night for the looks that have both. */
    static String drawableName(Look look, boolean day) {
        String name = "world_seasons_" + look.id;
        return look.hasNight ? name + (day ? "_day" : "_night") : name;
    }

    /**
     * The picture name of a world with a morning for this mood: world_middleearth_morning, _day or _night (made by
     * launcher/worlds/middleearth/make_scene.py); null for the worlds without one, whose pictures are fixed.
     */
    static String drawableName(World w, Mood mood) {
        return w.hasMorning() ? "world_" + w.id + "_" + mood.id : null;
    }

    /**
     * The fixed picture for this world and mood: {@link #resFor(World, boolean)}, a morning being day. 0 for Clean,
     * Seasons and Middle-earth, whose pictures are found by name ({@link #resFor(World, Mood, Look, ToIntFunction)}).
     */
    static int resFor(World w, Mood mood) {
        return resFor(w, mood, null, name -> 0);
    }

    /**
     * The picture for this world, mood and look: Seasons' and Middle-earth's found by name (Seasons' looks add theirs
     * without code, Middle-earth's three come with its pictures), 0 when there is none (its plain colour stays); the
     * other worlds' as {@link #resFor(World, boolean)}.
     */
    static int resFor(Context context, World w, Mood mood, Look look) {
        return resFor(w, mood, look,
                name -> context.getResources().getIdentifier(name, "drawable", context.getPackageName()));
    }

    /** The same with the lookup by name given (getIdentifier in the app, a stand-in in tests). */
    static int resFor(World w, Mood mood, Look look, ToIntFunction<String> byName) {
        if (w.hasMorning()) return byName.applyAsInt(drawableName(w, mood));
        if (w != World.SEASONS) return resFor(w, mood.isDay());
        return look != null ? byName.applyAsInt(drawableName(look, mood.isDay())) : 0;
    }

    /** The same by day or night only: Seasons' looks know no morning. */
    static int resFor(World w, boolean day, Look look, ToIntFunction<String> byName) {
        return resFor(w, day ? Mood.DAY : Mood.NIGHT, look, byName);
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

    /**
     * This world's mood now, by the sun (worked out once a day) or by forceMood. Also for the greeting's words
     * (Hogwarts' Lumos/Nox, Middle-earth's three).
     */
    Mood mood() {
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(sunDay)) {
            sunDay = now.toLocalDate();
            sun = WallpaperMood.sun(sunDay, latitude, longitude, ZoneId.systemDefault());
        }
        return mood(world, forced, now.getHour() * 60 + now.getMinute(), sun[0], sun[1]);
    }

    /** Whether it is day now (a morning is day), as {@link #mood()}. */
    boolean isDay() {
        return mood().isDay();
    }

    /**
     * The mood as the scene shows it now: the picture's on screen (or that of a plain colour), which lags
     * {@link #mood()} by a picture's decode and fade; mood() before anything shows. The overlay follows this.
     */
    Mood showsMood() {
        return shownMood != null ? shownMood : mood();
    }

    /** Day or night as the scene shows it now ({@link #showsMood}, a morning being day). */
    boolean showsDay() {
        return showsMood().isDay();
    }

    /** Seasons' shown day or night for {@link #seasonsChange}; null before anything shows. */
    private Boolean shownDay() {
        return shownMood != null ? shownMood.isDay() : null;
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

    /** auto, morning, day or night (anything else is auto), for this Home only; the scene follows at once. */
    void forceMood(String mood) {
        forced = knownMood(mood);
        update();
    }

    /** From the minute tick: when the mood (or Seasons' look) has changed, fades the other picture in. */
    void update() {
        if (bottom == null || !active || fading) return;
        Mood mood = mood();
        if (world == World.SEASONS) {
            int change = seasonsChange(shownDate, shownLook, date, look, shownDay(), mood.isDay());
            if (change == MOOD) {
                shownMood = mood;
                if (listener != null) listener.run();
            }
            if (change != RELOAD) return;
        } else if (shownMood == mood) {
            return;
        }
        Context context = root.getContext();
        int res = resFor(context, world, mood, look);
        if (res == 0) {
            // A look or mood without a picture (Seasons' plain looks, Middle-earth before its pictures) keeps the
            // plain colour, and is marked shown: no lookup or decode a minute. The themed worlds' fixed pictures are
            // never 0.
            showPlainColour(mood);
            return;
        }
        fading = true;
        Look nextLook = look;
        LocalDate nextDate = date;
        // Decoded off the UI thread; it happens two or three times a day (and when Seasons' look changes).
        new Thread(() -> {
            Bitmap next = decode(context, res);
            root.post(() -> show(next, mood, nextLook, nextDate));
        }, "WorldScene").start();
    }

    /** A look or mood without a picture: the world's plain colour, the last picture (if any) released. */
    private void showPlainColour(Mood mood) {
        shownMood = mood;
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

    private void show(Bitmap next, Mood mood, Look nextLook, LocalDate nextDate) {
        boolean day = mood.isDay();
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
            shownMood = mood;
            shownLook = nextLook;
            shownDate = nextDate;
            root.setBackground(null);
            fading = false;
            if (changed != null) changed.run();
            if (world == World.SEASONS) update();   // the look may have changed meanwhile
            return;
        }
        shownMood = mood;
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
