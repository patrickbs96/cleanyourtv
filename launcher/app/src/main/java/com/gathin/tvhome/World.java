package com.gathin.tvhome;

import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.function.IntFunction;

/**
 * The worlds the colour keys and digits switch between: Clean (green) is today's launcher unchanged, Hogwarts
 * (yellow), Marvel (red), Star Wars (blue), Supernatural (digit 5 only) and Middle-earth (digit 6 only) lay a skin
 * over it. Seasons
 * (Jahreskreis, digit 1 only, no colour key) brings its own scene and overlay but moves like Clean: dock, app start,
 * fonts and hero are Clean's ({@link #hasOwnMotion()}). Only plain values live here, so the class loads in JVM tests; the skins read them. Every
 * caller checks {@link #isClean()} or {@link #hasOwnMotion()} first and then takes exactly the old path.
 */
enum World {
    CLEAN("clean", R.string.world_clean, 0xFF2ECC40, 0, 0, 0, 0, 0),
    SEASONS("seasons", R.string.world_seasons, 0xFFE8A33D, 0, 0, 0, 0xFF0D0F14, 0),
    HOGWARTS("hogwarts", R.string.world_hogwarts, 0xFFF1C40F, 0xFFE8C87A, 0xFFD9A441, 35, 0xFF141020,
            R.string.world_hog_hero_prefix),
    MARVEL("marvel", R.string.world_marvel, 0xFFE03131, 0xFF9FDCFF, 0xFFE6B85A, 30, 0xFF120306, 0),
    STARWARS("starwars", R.string.world_starwars, 0xFF2F6FE0, 0xFFCFE0FF, 0xFF5B8CFF, 25, 0xFF000000, 0),
    SUPERNATURAL("supernatural", R.string.world_supernatural, 0xFFE0A24A, 0xFFE0A24A, 0xFFE0A24A, 30, 0xFF0B0D10, 0),
    MIDDLE_EARTH("middleearth", R.string.world_middleearth, 0xFFD9B56A, 0xFFD9B56A, 0xFFD9B56A, 30, 0xFF0C0D0B, 0);

    /** The chosen world's id in the launcher's preferences. */
    static final String PREF_KEY = "world";

    /** Saved under {@link #PREF_KEY}: clean, seasons, hogwarts, marvel, starwars, supernatural or middleearth. */
    final String id;
    final int labelRes;
    /** The colour of the remote key that picks this world. */
    final int keyColor;
    /** Accent for focus and highlights; 0 for Clean, which keeps the theme's. */
    final int accent;
    /** The ambient glow behind the dock and its strength in percent; 0/0 for Clean. */
    final int ambientColor;
    final int ambientAlphaPercent;
    /** The home background; 0 for Clean, which keeps Config's background_color. */
    final int backgroundColor;
    /** The line in front of the hero's app name, e.g. "Zauberspiegel zeigt"; 0 for none. */
    final int heroPrefixRes;

    World(String id, int labelRes, int keyColor, int accent, int ambientColor, int ambientAlphaPercent,
          int backgroundColor, int heroPrefixRes) {
        this.id = id;
        this.labelRes = labelRes;
        this.keyColor = keyColor;
        this.accent = accent;
        this.ambientColor = ambientColor;
        this.ambientAlphaPercent = ambientAlphaPercent;
        this.backgroundColor = backgroundColor;
        this.heroPrefixRes = heroPrefixRes;
    }

    /** The world with this id; an unknown, empty or missing one is Clean. */
    static World parse(String id) {
        if (id != null) {
            for (World w : values()) {
                if (w.id.equals(id)) return w;
            }
        }
        return CLEAN;
    }

    /** The saved world, Clean when none (or an unknown one) is saved. */
    static World current(SharedPreferences prefs) {
        return parse(prefs.getString(PREF_KEY, null));
    }

    /** Saves this world with commit, not apply: recreate() follows at once and must read it back. */
    void save(SharedPreferences prefs) {
        prefs.edit().putString(PREF_KEY, id).commit();
    }

    /**
     * The world a colour key or a digit picks, or null for any other key. The digits follow this enum's order
     * (0 Clean, 1 Seasons, 2 Hogwarts, 3 Marvel, 4 Star Wars, 5 Supernatural, 6 Middle-earth), so a world added at
     * the end takes the next one, up to 9. They are the clean way to Star Wars on the Philips: its firmware also opens
     * the TV help on blue. Seasons, Supernatural and Middle-earth have no colour key.
     */
    static World forKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_PROG_GREEN: return CLEAN;
            case KeyEvent.KEYCODE_PROG_YELLOW: return HOGWARTS;
            case KeyEvent.KEYCODE_PROG_RED: return MARVEL;
            case KeyEvent.KEYCODE_PROG_BLUE: return STARWARS;
            default:
                int digit = keyCode - KeyEvent.KEYCODE_0;   // KEYCODE_0 to KEYCODE_9 are consecutive
                World[] all = values();
                return digit >= 0 && digit < Math.min(10, all.length) ? all[digit] : null;
        }
    }

    /** The same by scan code, for firmware that sends the colour keys without a key code (398 red to 401 blue). */
    static World forScanCode(int scanCode) {
        switch (scanCode) {
            case 399: return CLEAN;
            case 400: return HOGWARTS;
            case 398: return MARVEL;
            case 401: return STARWARS;
            default: return null;
        }
    }

    boolean isClean() {
        return this == CLEAN;
    }

    /**
     * Whether the world has its own motion and style: dock move, app start, dock skin, hero and fonts (Hogwarts,
     * Marvel, Star Wars, Supernatural, Middle-earth). False for Clean and for Seasons, which takes Clean's for all of
     * them (Jahreskreis spec § 4.8) and keeps only its scene, overlay, accent and icon-only pills.
     */
    boolean hasOwnMotion() {
        return this == HOGWARTS || this == MARVEL || this == STARWARS || this == SUPERNATURAL || this == MIDDLE_EARTH;
    }

    /**
     * Whether the world splits the day in three, a morning before its day and night (Mittelerde spec § 4.1): only
     * Middle-earth. The others know day and night.
     */
    boolean hasMorning() {
        return this == MIDDLE_EARTH;
    }

    /**
     * The accent Home paints with: in Seasons the day's look's (spec § 6, coloured by the occasion), in every other
     * world its own {@link #accent}, whatever the look; also Seasons' own while no look is known.
     */
    int accentFor(Look look) {
        return this == SEASONS && look != null ? look.accent : accent;
    }

    /** The word in front of the greeting: Lumos by day and Nox by night in Hogwarts, Suit up in Marvel. */
    String greetingPrefix(boolean day) {
        if (this == HOGWARTS) return day ? "Lumos" : "Nox";
        if (this == MARVEL) return "Suit up";
        return "";
    }

    /**
     * The words after the greeting: Äußerer Rand in Star Wars, Back on the road by day and Something's out there by
     * night in Supernatural (own lines, the same in every language, Supernatural spec § 4.3). Only the words in the
     * code: Middle-earth's are strings ({@link #greetingSuffixRes}), so "" here; callers that show a greeting take
     * {@link #greetingSuffix(WorldScene.Mood, IntFunction)}.
     */
    String greetingSuffix(boolean day) {
        if (this == STARWARS) return "Äußerer Rand";
        if (this == SUPERNATURAL) return day ? "Back on the road" : "Something's out there";
        return "";
    }

    /**
     * Whether Home draws the glow behind the dock, given config's ambient_glow: as configured in Clean and the themed
     * worlds, never in Seasons. There the scene is the background, and the focused app's colour (Netflix red) washed
     * the whole picture.
     */
    boolean showsAmbientGlow(boolean configured) {
        return configured && this != SEASONS;
    }

    /**
     * The words after the greeting that come in the app's language, as a string: Middle-earth's Ein neuer Tag im
     * Hügelland in the morning, Weit ist der Weg by day and Das Auge wacht by night (Mittelerde spec § 4.3). 0 for
     * every other world, whose words are {@link #greetingSuffix(boolean)}.
     */
    int greetingSuffixRes(WorldScene.Mood mood) {
        if (this != MIDDLE_EARTH) return 0;
        switch (mood) {
            case MORNING: return R.string.world_middleearth_greeting_morning;
            case DAY: return R.string.world_middleearth_greeting_day;
            default: return R.string.world_middleearth_greeting_night;
        }
    }

    /**
     * The words after the greeting in every world for the scene's mood: {@link #greetingSuffixRes} looked up through
     * {@code strings} (Resources::getString on the TV), else the words in the code, a morning being day. Home and the
     * personal greeting take this one.
     */
    String greetingSuffix(WorldScene.Mood mood, IntFunction<String> strings) {
        int res = greetingSuffixRes(mood);
        return res != 0 ? strings.apply(res) : greetingSuffix(mood.isDay());
    }

    /** Every world but Clean shows the top pills as icons only. */
    boolean forcesIconOnlyPills() {
        return this != CLEAN;
    }

    /** Whether the worlds' dock skin fits this layout: a plain dock row, no single row, no cinematic shelf, no rows below. */
    static boolean dockStyleSupported(Config cfg) {
        return dockStyleSupported(cfg.dockRow, cfg.singleRow, cfg.cinematicHome, cfg.rowsBelowDock);
    }

    static boolean dockStyleSupported(boolean dockRow, boolean singleRow, boolean cinematic, int rowsBelowDock) {
        return dockRow && !singleRow && !cinematic && rowsBelowDock == 0;
    }
}
