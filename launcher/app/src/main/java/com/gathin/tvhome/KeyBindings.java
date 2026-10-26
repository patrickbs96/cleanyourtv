package com.gathin.tvhome;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Remote-key bindings made on the TV (Launcher settings, Key bindings; fork, spec § 4.10d), in SharedPreferences
 * NAME. Each learned button is "key.<n>" = "name|keycode|scancode"; its actions are "tap.<n>", "double.<n>" and
 * "hold.<n>", each a target as app_keys has them (a package, "inputs", an action word, or close_apps); an empty slot
 * is not stored. The Home key has only "home.double". toAppKeys turns them into app_keys entries, which the key
 * services read after the config's own (overlay), so a binding wins over config.xml and the theme.
 *
 * Learn mode: KeyBindingsActivity starts it for LEARN_MS; while it runs, the key services hand the next learnable
 * press to the activity (learn) instead of acting on it. The services and the activity share this app's one process,
 * so the hand-over is a call on the main thread: nothing another app could send.
 */
final class KeyBindings {

    /** The SharedPreferences file; the key services listen to it and reload their maps on any change. */
    static final String NAME = "key_bindings";
    static final String TAP = "tap";
    static final String DOUBLE = "double";
    static final String HOLD = "hold";
    /** The target that ends every app in the background (CloseApps, spec § 4.10f). */
    static final String CLOSE_APPS = "close_apps";
    /** Stored for Home's double tap when Nothing is picked, so the config's default (close_apps) no longer applies. */
    static final String NONE = "none";
    /** How long learn mode waits for a press. */
    static final long LEARN_MS = 15_000;

    private static final String NEXT = "next";
    private static final String HOME_DOUBLE = "home.double";

    private KeyBindings() { }

    /** A learned button. keycode is 0 when Android has none for it (KEYCODE_UNKNOWN): it is then told by scancode. */
    static final class Key {
        final int n;
        final String name;
        final int keycode;
        final int scancode;

        Key(int n, String name, int keycode, int scancode) {
            this.n = n;
            this.name = name;
            this.keycode = keycode;
            this.scancode = scancode;
        }

        /** The button as app_keys names it: its keycode when Android has one, otherwise "scan:<code>". */
        String code() {
            return keycode > 0 ? String.valueOf(keycode) : "scan:" + scancode;
        }
    }

    /** Learns a button; returns its number. Numbers only grow, so a removed one is never reused. */
    static int add(SharedPreferences prefs, String name, int keycode, int scancode) {
        int n = Math.max(1, prefs.getInt(NEXT, 1));
        prefs.edit().putString("key." + n, clean(name) + "|" + keycode + "|" + scancode)
                .putInt(NEXT, n + 1).apply();
        return n;
    }

    static void rename(SharedPreferences prefs, int n, String name) {
        Key key = key(prefs, n);
        if (key == null) return;
        prefs.edit().putString("key." + n, clean(name) + "|" + key.keycode + "|" + key.scancode).apply();
    }

    /** Sets one of TAP, DOUBLE or HOLD; an empty or null target clears it. */
    static void set(SharedPreferences prefs, int n, String slot, String target) {
        String key = slot + "." + n;
        if (target == null || target.trim().isEmpty()) prefs.edit().remove(key).apply();
        else prefs.edit().putString(key, target.trim()).apply();
    }

    /** One slot's target, or "" when it is empty. */
    static String get(SharedPreferences prefs, int n, String slot) {
        return prefs.getString(slot + "." + n, "");
    }

    /** Forgets a button and all three of its slots. */
    static void remove(SharedPreferences prefs, int n) {
        prefs.edit().remove("key." + n).remove(TAP + "." + n).remove(DOUBLE + "." + n).remove(HOLD + "." + n).apply();
    }

    /**
     * Sets Home's double tap; an empty or null target clears it, so the config's own (config.xml has
     * double:KEYCODE_HOME:close_apps) applies again. NONE is Nothing chosen on the page: no double tap, over the config.
     */
    static void setHomeDouble(SharedPreferences prefs, String target) {
        if (target == null || target.trim().isEmpty()) prefs.edit().remove(HOME_DOUBLE).apply();
        else prefs.edit().putString(HOME_DOUBLE, target.trim()).apply();
    }

    /** What is stored for Home's double tap: "" when nothing (the config's applies), NONE, or a target. */
    static String homeDouble(SharedPreferences prefs) {
        return prefs.getString(HOME_DOUBLE, "");
    }

    /** Home's double tap as it works: the stored one, "" for NONE, else the config's (its last entry for Home). */
    static String homeDouble(SharedPreferences prefs, List<String> config) {
        String stored = homeDouble(prefs);
        if (NONE.equals(stored)) return "";
        if (!stored.isEmpty()) return stored;
        String target = "";
        for (String entry : config) {
            if (!isHomeDouble(entry)) continue;
            String text = entry.trim();
            target = text.substring(text.indexOf(':', "double:".length()) + 1).trim();
        }
        return target;
    }

    /** Whether an app_keys entry is a "double:" entry for Home, by keycode (3) or by name (KEYCODE_HOME). */
    static boolean isHomeDouble(String entry) {
        if (entry == null) return false;
        String text = entry.trim();
        if (!text.startsWith("double:")) return false;
        String[] parts = text.substring("double:".length()).split(":", 2);
        if (parts.length != 2) return false;
        String key = parts[0].trim();
        return key.equals(String.valueOf(KeyEvent.KEYCODE_HOME)) || key.equals("KEYCODE_HOME");
    }

    /** The learned buttons, in the order they were learned. */
    static List<Key> keys(SharedPreferences prefs) {
        List<Key> keys = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith("key.") || !(entry.getValue() instanceof String)) continue;
            int n;
            try {
                n = Integer.parseInt(entry.getKey().substring(4));
            } catch (NumberFormatException e) {
                continue;
            }
            Key key = parse(n, (String) entry.getValue());
            if (key != null) keys.add(key);
        }
        keys.sort((a, b) -> Integer.compare(a.n, b.n));
        return keys;
    }

    /** The number of the learned button that is this press, or -1: the same keycode, or the same scancode without one. */
    static int find(SharedPreferences prefs, int keycode, int scancode) {
        for (Key key : keys(prefs)) {
            if (keycode > 0 ? key.keycode == keycode : key.keycode <= 0 && key.scancode == scancode) return key.n;
        }
        return -1;
    }

    /** The bindings as app_keys entries: tap, "double:" and "hold:" per button, then Home's double tap. */
    static List<String> toAppKeys(SharedPreferences prefs) {
        List<String> entries = new ArrayList<>();
        for (Key key : keys(prefs)) {
            String code = key.code();
            String tap = get(prefs, key.n, TAP);
            String twice = get(prefs, key.n, DOUBLE);
            String hold = get(prefs, key.n, HOLD);
            if (!tap.isEmpty()) entries.add(code + ":" + tap);
            if (!twice.isEmpty()) entries.add("double:" + code + ":" + twice);
            if (!hold.isEmpty()) entries.add("hold:" + code + ":" + hold);
        }
        String home = homeDouble(prefs);
        if (!home.isEmpty() && !NONE.equals(home)) entries.add("double:" + KeyEvent.KEYCODE_HOME + ":" + home);
        return entries;
    }

    /**
     * The config's app_keys followed by the bindings; the parsers keep the last entry for a key, so these win. Once
     * the Home row is set, the config's Home double tap goes: Nothing (NONE) there must leave Home with none.
     */
    static List<String> overlay(List<String> config, SharedPreferences prefs) {
        List<String> entries = new ArrayList<>();
        boolean homeSet = !homeDouble(prefs).isEmpty();
        for (String entry : config) {
            if (!homeSet || !isHomeDouble(entry)) entries.add(entry);
        }
        entries.addAll(toAppKeys(prefs));
        return entries;
    }

    /** Whether a learned button's tap does nothing although it has a double tap or a hold: the key is then silent. */
    static boolean silentTap(SharedPreferences prefs, int n) {
        return get(prefs, n, TAP).isEmpty() && !(get(prefs, n, DOUBLE).isEmpty() && get(prefs, n, HOLD).isEmpty());
    }

    /**
     * Buttons Home already gives a job, so a binding would cover it: the colour buttons and 1 to 4 switch the worlds
     * (World.forKeyCode), and Settings opens the settings in HomeKeyService before any keycode binding is asked.
     */
    static boolean taken(int keycode) {
        return keycode == KeyEvent.KEYCODE_SETTINGS || World.forKeyCode(keycode) != null;
    }

    /**
     * Whether a press can be learned. The arrows, OK, Back, Home, Power and volume never are (the remote must still
     * work, and the volume keep its repeat), nor the taken ones (colour buttons, Settings), nor a press that has
     * neither keycode nor scancode to tell it by.
     */
    static boolean learnable(int keycode, int scancode) {
        if (taken(keycode)) return false;
        switch (keycode) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_POWER: case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_SLEEP: case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return false;
            default:
                return keycode > 0 || scancode > 0;
        }
    }

    /** Whether learn mode started at {@code startMs} (0: not started) still runs at {@code nowMs}. */
    static boolean learningAt(long startMs, long nowMs) {
        return startMs > 0 && nowMs - startMs < LEARN_MS;
    }

    /** Gets the press learn mode caught; on the main thread, after the key service has returned. */
    interface Learner {
        void learned(int keycode, int scancode);

        /** A taken button (colour button or Settings) was pressed; learn mode keeps waiting. */
        void taken();
    }

    /** Learn mode's start (uptime), 0 when off; the activity that waits; the press being caught, whose release is swallowed too. */
    private static long learnStart;
    private static Learner learner;
    private static int caughtKeycode = -1;
    private static int caughtScancode = -1;
    private static long caughtDownTime;
    private static Handler main;

    /** Learn mode caught this press (button and downTime); its other events are swallowed. */
    static void catchPress(int keycode, int scancode, long downTime) {
        caughtKeycode = keycode;
        caughtScancode = scancode;
        caughtDownTime = downTime;
    }

    /** Whether an event belongs to the caught press: the same button and the same downTime. */
    static boolean caughtPress(int keycode, int scancode, long downTime) {
        return caughtKeycode >= 0 && keycode == caughtKeycode && scancode == caughtScancode
                && downTime == caughtDownTime;
    }

    /** Learn mode for LEARN_MS, until a press is caught or stopLearning. Main thread. */
    static void startLearning(Learner waiting) {
        learner = waiting;
        learnStart = SystemClock.uptimeMillis();
    }

    static void stopLearning() {
        learner = null;
        learnStart = 0;
    }

    /**
     * For the key services, before anything else: in learn mode the next learnable press goes to the activity and
     * is swallowed, its release too; a taken button (taken()) is swallowed the same way and only reported. Other keys
     * pass, so the arrows and Back still work on the page. True: consumed.
     */
    static boolean learn(KeyEvent event) {
        if (caughtPress(event.getKeyCode(), event.getScanCode(), event.getDownTime())) {
            // Every event of the caught press is swallowed, repeated DOWNs and the release too, and by every key
            // service: Android hands each event to each filtering service, so the release is not cleared here (the
            // second service would then act on it). The catch stays until the next one; a later press of the same
            // button has another downTime and is not swallowed.
            return true;
        }
        Learner waiting = learner;
        if (waiting == null || !learningAt(learnStart, SystemClock.uptimeMillis())) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int keycode = event.getKeyCode();
        int scancode = event.getScanCode();
        if (main == null) main = new Handler(Looper.getMainLooper());
        if (taken(keycode)) {
            // A colour button or Settings: swallowed with its release, so it neither switches a world nor opens the
            // settings over the page; the page says why, and learn mode keeps waiting.
            catchPress(keycode, scancode, event.getDownTime());
            main.post(waiting::taken);
            return true;
        }
        if (!learnable(keycode, scancode)) return false;
        catchPress(keycode, scancode, event.getDownTime());
        stopLearning();
        main.post(() -> waiting.learned(keycode, scancode));
        return true;
    }

    /** "name|keycode|scancode", read from the right so an old name with "|" in it still parses. */
    private static Key parse(int n, String value) {
        int second = value.lastIndexOf('|');
        int first = second > 0 ? value.lastIndexOf('|', second - 1) : -1;
        if (first < 0) return null;
        try {
            return new Key(n, value.substring(0, first), Integer.parseInt(value.substring(first + 1, second).trim()),
                    Integer.parseInt(value.substring(second + 1).trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Key key(SharedPreferences prefs, int n) {
        String value = prefs.getString("key." + n, null);
        return value == null ? null : parse(n, value);
    }

    private static String clean(String name) {
        return name == null ? "" : name.replace('|', '/').replace('\n', ' ').trim();
    }
}
