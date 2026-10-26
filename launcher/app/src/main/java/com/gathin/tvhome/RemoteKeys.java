package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/**
 * Remote buttons that only the stock launcher answered, for InputKeyService and HomeKeyService: app_keys
 * (config.xml or a theme) maps a keycode to the app it opens, as "190:com.google.android.youtube.tv" or
 * "KEYCODE_BUTTON_3:com.google.android.youtube.tv", or a scancode as "scan:490:com.wbd.hbomax". A "hold:"
 * prefix makes an entry the long-press action instead: "hold:KEYCODE_BUTTON_8:org.xbmc.kodi" opens Kodi when
 * that button is held for HOLD_MS, while the unprefixed entry still opens on a tap; "hold:scan:490:..." does
 * the same for a scancode. A "double:" prefix (fork, spec § 4.10d) makes it the double-tap action: such a key's tap
 * waits DOUBLE_MS for a second press (Keys). One set of maps for both services; whichever filters the key first
 * consumes it. The on-TV bindings (KeyBindings) come after the config's entries, so they win.
 * A target of "input:<input id>" switches to that TV input (an HDMI port) instead of opening an app, a target
 * of "inputs" opens the input switcher, for a remote whose source button sends a key Android does not map, and
 * one of the action words (back, home, recents, mute, sleep, screensaver, settings, wifi, close_apps) does that
 * action; close_apps ends the background apps (CloseApps), and config.xml gives it to Home's double tap.
 */
final class RemoteKeys {

    /** How long a button is held before its "hold:" entry wins (Android's long-press timeout). */
    static final long HOLD_MS = 500;

    /** The app_keys target that opens the input switcher, as "scan:365:inputs". */
    static final String INPUTS = "inputs";

    /** Pre-compiled regex patterns to avoid re-compiling Patterns on every entry during key parsing. */
    private static final Pattern NUMERIC_KEY = Pattern.compile("[0-9]{1,6}");
    private static final Pattern KEYCODE_KEY = Pattern.compile("KEYCODE_[A-Z0-9_]+");

    private RemoteKeys() { }

    /** keycode -> package, with key names resolved by Android. */
    static Map<Integer, String> parseAppKeys(String[] entries) {
        return parseAppKeys(Arrays.asList(entries), KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseAppKeys(Iterable<String> entries) {
        return parseAppKeys(entries, KeyEvent::keyCodeFromString);
    }

    /**
     * keycode -> package. The key is a number or a KEYCODE_ name (resolved by {@code names}; 0, KEYCODE_UNKNOWN,
     * means it isn't one). Entries without a colon, with an unknown key or with no package are skipped; for a
     * keycode listed twice the last entry wins.
     */
    static Map<Integer, String> parseAppKeys(Iterable<String> entries, ToIntFunction<String> names) {
        Map<Integer, String> keys = new HashMap<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String[] parts = entry.split(":", 2);
            if (parts.length != 2 || parts[1].trim().isEmpty()) continue;
            String key = parts[0].trim();
            int code;
            if (NUMERIC_KEY.matcher(key).matches()) {
                code = Integer.parseInt(key);
            } else if (KEYCODE_KEY.matcher(key).matches()) {
                code = names.applyAsInt(key);   // keyCodeFromString takes digits only from API 29, names always
            } else {
                continue;
            }
            if (code > 0) keys.put(code, parts[1].trim());
        }
        return keys;
    }

    /** keycode -> package from the "hold:" entries; the rest of each entry reads as it does in parseAppKeys. */
    static Map<Integer, String> parseHoldAppKeys(String[] entries) {
        return parseHoldAppKeys(Arrays.asList(entries), KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseHoldAppKeys(Iterable<String> entries) {
        return parseHoldAppKeys(entries, KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseHoldAppKeys(Iterable<String> entries, ToIntFunction<String> names) {
        return parseAppKeys(holdEntries(entries), names);
    }

    /**
     * Applies the key_filter choice (config.xml or a theme): when false the service drops
     * FLAG_REQUEST_FILTER_KEY_EVENTS at runtime, so it stays enabled for nothing but stops filtering.
     * Some firmware (TCL Google TV on Android 12) drops key auto-repeat once ANY service filters keys:
     * long-pressing volume then steps once per press. Filtering can only be skipped while the service
     * runs, since the flag is also declared in the manifest metadata.
     */
    static void applyKeyFilter(AccessibilityService service, boolean keyFilter) {
        if (keyFilter) return;
        AccessibilityServiceInfo info = service.getServiceInfo();
        if (info == null) return;
        info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        service.setServiceInfo(info);
    }

    /**
     * scancode -> package, from the "scan:490:com.wbd.hbomax" entries of the same list. For remotes that send
     * several app buttons as one keycode and tell them apart by scancode (Sony BRAVIA: YouTube, Prime Video and
     * Disney+ are all keycode 196, scancodes 485, 490 and 491). Find it with `getevent -lt` while pressing the
     * button: the EV_KEY code, in hex. Other entries are skipped; for a scancode listed twice the last entry wins.
     */
    static Map<Integer, String> parseAppScancodes(Iterable<String> entries) {
        Map<Integer, String> keys = new HashMap<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String[] parts = entry.split(":", 3);
            if (parts.length != 3 || !parts[0].trim().equals("scan") || parts[2].trim().isEmpty()) continue;
            String code = parts[1].trim();
            if (!NUMERIC_KEY.matcher(code).matches()) continue;
            int scan = Integer.parseInt(code);
            if (scan > 0) keys.put(scan, parts[2].trim());
        }
        return keys;
    }

    /** scancode -> package from the "hold:scan:..." entries; the rest reads as it does in parseAppScancodes. */
    static Map<Integer, String> parseHoldAppScancodes(Iterable<String> entries) {
        return parseAppScancodes(holdEntries(entries));
    }

    /** The entries with the "hold:" prefix removed, so the normal parsers can read them. */
    private static Iterable<String> holdEntries(Iterable<String> entries) {
        return prefixed(entries, "hold:");
    }

    /** keycode -> target from the "double:" entries (the double-tap action); the rest reads as in parseAppKeys. */
    static Map<Integer, String> parseDoubleKeys(List<String> entries) {
        return parseDoubleKeys(entries, KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseDoubleKeys(Iterable<String> entries, ToIntFunction<String> names) {
        return parseAppKeys(prefixed(entries, "double:"), names);
    }

    /** scancode -> target from the "double:scan:..." entries; the rest reads as it does in parseAppScancodes. */
    static Map<Integer, String> parseDoubleScancodes(Iterable<String> entries) {
        return parseAppScancodes(prefixed(entries, "double:"));
    }

    /** The entries without the "double:" ones, for a reader that knows only taps and holds. */
    static List<String> withoutDouble(List<String> entries) {
        List<String> rest = new ArrayList<>();
        for (String entry : entries) {
            if (entry != null && !entry.trim().startsWith("double:")) rest.add(entry);
        }
        return rest;
    }

    /** The entries that start with {@code prefix}, with it removed. */
    private static List<String> prefixed(Iterable<String> entries, String prefix) {
        List<String> found = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String text = entry.trim();
            if (text.startsWith(prefix)) found.add(text.substring(prefix.length()).trim());
        }
        return found;
    }

    /** How long after a tap's release a second press still makes it a double tap ("double:" entries). */
    static final long DOUBLE_MS = 400;

    /** DoubleTap's id for a scancode entry: negative, so it never equals a keycode (KEYCODE_HOME above all). */
    static int scanId(int scancode) {
        return -scancode;
    }

    /**
     * Tells a double tap from a tap, for keys with a "double:" entry. It does not know the bindings: the service asks
     * only about such keys (or passes the set of them), and runs every other key at once. onTap on a key's press
     * says FIRST (hold the tap back until the window has passed), SECOND (the double tap: drop the held tap) or, for
     * a key without a "double:" entry, NONE. KEYCODE_HOME is never held back: its first press is PASS and goes on to
     * the system, a second one inside the window is SECOND. The window runs from the first press, or from its
     * release once the service reports that with release().
     */
    static final class DoubleTap {
        static final int NONE = 0;
        static final int FIRST = 1;
        static final int SECOND = 2;
        static final int PASS = 3;

        private final long windowMs;
        private boolean armed;
        private int lastKey;
        private long lastMs;

        DoubleTap(long windowMs) {
            this.windowMs = windowMs;
        }

        int onTap(int key, long nowMs) {
            if (armed && key == lastKey && nowMs - lastMs < windowMs) {
                armed = false;
                return SECOND;
            }
            armed = true;
            lastKey = key;
            lastMs = nowMs;
            return key == KeyEvent.KEYCODE_HOME ? PASS : FIRST;
        }

        /** onTap for any key: NONE, and the end of a pending pair, for a key that is not in {@code doubles}. */
        int onTap(int key, long nowMs, Set<Integer> doubles) {
            if (!doubles.contains(key)) {
                armed = false;
                return NONE;
            }
            return onTap(key, nowMs);
        }

        /** The first press of {@code key} was released: the window now runs from here. */
        void release(int key, long nowMs) {
            if (armed && key == lastKey) lastMs = nowMs;
        }

        /** The held tap of {@code key} has run (or was dropped): a late second press starts over. */
        void forget(int key) {
            if (key == lastKey) armed = false;
        }

        void reset() {
            armed = false;
        }
    }

    /** What Presses decides for one event of a key with a "double:" entry; Keys carries it out. */
    static final class Step {
        /** Swallow the event (true) or pass it on to the system (false: Home's first press, a foreign release). */
        final boolean consume;
        /** The target to run now, or null. */
        final String run;
        /** Drop the held tap of this key: the second press came in time. */
        final boolean cancel;
        /** Run another key's held tap now, before this one is held: this press ended that pair. */
        final boolean flush;
        /** Hold this press's tap back for DOUBLE_MS (then Presses.ran). */
        final boolean holdTap;

        Step(boolean consume, String run, boolean cancel, boolean flush, boolean holdTap) {
            this.consume = consume;
            this.run = run;
            this.cancel = cancel;
            this.flush = flush;
            this.holdTap = holdTap;
        }

        static final Step PASS = new Step(false, null, false, false, false);
        static final Step SWALLOW = new Step(true, null, false, false, false);
    }

    /**
     * The decisions for keys with a "double:" entry, without Android: Keys feeds it each event's action, times and
     * repeat count and does what the Step says. A press is known by its downTime: some IR and Bluetooth drivers send
     * a held button as several identical DOWNs with the same downTime, which the accessibility filter sees before
     * Android's repeat detection, so a DOWN with the running press's downTime (or a repeat count) is that same press,
     * never a second tap. Home is never swallowed except for the press that is its double tap.
     */
    static final class Presses {
        private final DoubleTap taps = new DoubleTap(DOUBLE_MS);
        /** The press that is down: its id and downTime; pressId 0 when none. */
        private int pressId;
        private long pressDownTime;
        /** The press that is a double tap, so its repeats and its release are its own; secondId 0 when none. */
        private int secondId;
        private long secondDownTime;
        /** The id whose tap is held back; 0 when none. */
        private int pendingId;

        Step onEvent(int id, int action, long downTime, long eventTime, int repeatCount, boolean canceled,
                     String app, String holdApp, String doubleApp) {
            boolean home = id == KeyEvent.KEYCODE_HOME;
            boolean samePress = pressId == id && pressDownTime == downTime;
            boolean secondPress = secondId == id && secondDownTime == downTime;
            if (action == KeyEvent.ACTION_DOWN) {
                if (repeatCount > 0 || samePress) {
                    // The button is held: no new tap. Home's first press belongs to the system, its repeats too.
                    return home && !secondPress ? Step.PASS : Step.SWALLOW;
                }
                pressId = id;
                pressDownTime = downTime;
                secondId = 0;
                int tap = taps.onTap(id, eventTime);
                if (tap == DoubleTap.SECOND) {
                    secondId = id;
                    secondDownTime = downTime;
                    boolean cancel = pendingId == id;
                    if (cancel) pendingId = 0;
                    return new Step(true, null, cancel, false, false);
                }
                return tap == DoubleTap.PASS ? Step.PASS : Step.SWALLOW;
            }
            if (action != KeyEvent.ACTION_UP) return home && !secondPress ? Step.PASS : Step.SWALLOW;
            if (!samePress && !secondPress) {
                // The release of a press this never saw go down (it went on to the system): it goes on too.
                return Step.PASS;
            }
            pressId = 0;
            if (secondPress) {
                secondId = 0;
                return new Step(true, canceled ? null : doubleApp, false, false, false);
            }
            if (home) {
                taps.release(id, eventTime);
                return Step.PASS;
            }
            if (canceled) {
                taps.forget(id);
                return Step.SWALLOW;
            }
            if (eventTime - downTime >= HOLD_MS && holdApp != null) {
                taps.forget(id);
                return new Step(true, holdApp, false, false, false);
            }
            taps.release(id, eventTime);
            // No tap entry: the tap does nothing (the key is still swallowed, it has a double tap).
            return new Step(true, null, false, pendingId != 0, true);
        }

        /** Keys held this id's tap back. */
        void held(int id) {
            pendingId = id;
        }

        /** The held tap of this id ran (its window passed, or a flush): a late second press starts over. */
        void ran(int id) {
            if (pendingId == id) pendingId = 0;
            taps.forget(id);
        }

        /** The id whose tap is held back, 0 when none. */
        int pending() {
            return pendingId;
        }

        /** A press of a key without a "double:" entry: a pair in progress ends (its held tap still runs). */
        void otherKey() {
            taps.reset();
        }

        void reset() {
            taps.reset();
            pressId = 0;
            secondId = 0;
            pendingId = 0;
        }
    }

    /**
     * One service's app_keys (config.xml or a theme, then the on-TV bindings of KeyBindings), with the double-tap
     * state. A key with a "double:" entry holds its tap back for DOUBLE_MS after the release and runs it then, unless
     * a second press comes first, which runs the "double:" entry instead; its "hold:" entry still runs on the
     * release of a long press, without waiting. Every other key runs as before. The decisions are Presses'; this
     * only runs targets and the timer. Main thread only.
     */
    static final class Keys {
        private Map<Integer, String> apps = Collections.emptyMap();
        private Map<Integer, String> scans = Collections.emptyMap();
        private Map<Integer, String> holdApps = Collections.emptyMap();
        private Map<Integer, String> holdScans = Collections.emptyMap();
        private Map<Integer, String> doubleApps = Collections.emptyMap();
        private Map<Integer, String> doubleScans = Collections.emptyMap();
        private final Presses presses = new Presses();
        private final Handler handler = new Handler(Looper.getMainLooper());
        /** The held tap, waiting out the window; null when none. */
        private Runnable pending;

        void load(List<String> entries) {
            drop();
            apps = parseAppKeys(entries);
            scans = parseAppScancodes(entries);
            holdApps = parseHoldAppKeys(entries);
            holdScans = parseHoldAppScancodes(entries);
            doubleApps = parseDoubleKeys(entries);
            doubleScans = parseDoubleScancodes(entries);
        }

        /** Drops a held tap and a pair in progress; on unbind and before a reload. */
        void drop() {
            if (pending != null) handler.removeCallbacks(pending);
            pending = null;
            presses.reset();
        }

        /** Every key goes past here first: a press of a key without a "double:" entry ends a pair in progress. */
        void note(KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return;
            if (doubleScans.containsKey(event.getScanCode()) || doubleApps.containsKey(event.getKeyCode())) return;
            presses.otherKey();
        }

        /** A scancode entry; it is more specific than any keycode, so the services ask this first. */
        boolean handleScancode(Context ctx, KeyEvent event) {
            int scan = event.getScanCode();
            return handle(ctx, event, scanId(scan), scans.get(scan), holdScans.get(scan), doubleScans.get(scan));
        }

        boolean handleKeycode(Context ctx, KeyEvent event) {
            int code = event.getKeyCode();
            return handle(ctx, event, code, apps.get(code), holdApps.get(code), doubleApps.get(code));
        }

        private boolean handle(Context ctx, KeyEvent event, int id, String app, String holdApp, String doubleApp) {
            if (doubleApp == null) return RemoteKeys.handle(ctx, event, app, holdApp);
            Step step = presses.onEvent(id, event.getAction(), event.getDownTime(), event.getEventTime(),
                    event.getRepeatCount(), event.isCanceled(), app, holdApp, doubleApp);
            if (step.cancel || step.flush) {
                Runnable held = pending;
                if (held != null) handler.removeCallbacks(held);
                pending = null;
                if (step.flush && held != null) held.run();
            }
            if (step.run != null) launch(ctx, step.run);
            if (step.holdTap) {
                pending = () -> {
                    pending = null;
                    presses.ran(id);
                    launch(ctx, app);
                };
                presses.held(id);
                handler.postDelayed(pending, DOUBLE_MS);
            }
            return step.consume;
        }
    }


    /**
     * Consumes a key mapped by either list. On release it opens the hold app after HOLD_MS, otherwise the normal
     * one; false means neither list has this key, so the caller can pass it on.
     */
    static boolean handle(Context ctx, KeyEvent event, String app, String holdApp) {
        if (app == null && holdApp == null) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            launch(ctx, target(app, holdApp, event.getEventTime() - event.getDownTime()));
        }
        return true;
    }

    /** The app a mapped key opens: its hold entry after HOLD_MS, otherwise its normal one. */
    static String target(String app, String holdApp, long heldMs) {
        return heldMs >= HOLD_MS && holdApp != null ? holdApp : app;
    }

    /** The TV input id of an "input:<id>" target, e.g. "input:com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413743104"; null for a package. */
    static String inputId(String target) {
        if (target == null || !target.startsWith("input:")) return null;
        String id = target.substring("input:".length()).trim();
        return id.isEmpty() ? null : id;
    }

    /**
     * The accessibility global action for an action word, or 0 when the target is something else. "sleep" is
     * GLOBAL_ACTION_LOCK_SCREEN, which puts a TV in standby (as the Sleep Timer add-on does); Android has it from 28.
     */
    static int globalAction(String target, int sdk) {
        switch (target) {
            case "back": return AccessibilityService.GLOBAL_ACTION_BACK;
            case "home": return AccessibilityService.GLOBAL_ACTION_HOME;
            case "recents": return AccessibilityService.GLOBAL_ACTION_RECENTS;
            case "sleep": return sdk >= 28 ? AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN : 0;
            default: return 0;
        }
    }

    /** Whether a target is an action word rather than an app or an input. A package name always has a dot. */
    static boolean isAction(String target) {
        switch (target) {
            case "back": case "home": case "recents": case "mute": case "sleep":
            case "screensaver": case "settings": case "wifi": case KeyBindings.CLOSE_APPS:
                return true;
            default:
                return false;
        }
    }

    /** Runs an action word; false for an app or an input. Needs no permission the launcher does not already have. */
    private static boolean runAction(Context ctx, String target) {
        if (!isAction(target)) return false;
        int global = globalAction(target, Build.VERSION.SDK_INT);
        if (global != 0) {
            if (ctx instanceof AccessibilityService) ((AccessibilityService) ctx).performGlobalAction(global);
            return true;
        }
        switch (target) {
            case "mute":
                // The media stream a TV plays everything on, with its own volume bar, as a remote's mute button would.
                AudioManager audio = ctx.getSystemService(AudioManager.class);
                if (audio != null) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                break;
            case "screensaver":
                // The screensaver chosen in the TV's settings, through the system's "start now" shortcut; the
                // launcher's own clock where the firmware has no such shortcut.
                Intent dream = new Intent(Intent.ACTION_MAIN)
                        .setClassName("com.android.systemui", "com.android.systemui.Somnambulator");
                if (ctx.getPackageManager().resolveActivity(dream, 0) == null) dream = new Intent(ctx, ClockActivity.class);
                start(ctx, dream);
                break;
            case "settings":
                start(ctx, SystemSettings.intent(ctx, Settings.ACTION_SETTINGS));
                break;
            case "wifi":
                start(ctx, SystemSettings.intent(ctx, Settings.ACTION_WIFI_SETTINGS));
                break;
            case KeyBindings.CLOSE_APPS:
                // KILL_BACKGROUND_PROCESSES is the app's, so a key service's context may call it too (spec § 4.10f).
                CloseApps.start(ctx);
                break;
            default:
                break;   // "sleep" below Android 9: consumed, nothing to do
        }
        return true;
    }

    private static void start(Context ctx, Intent intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    /** Opens an app from a remote button: its TV entry if it has one, else its phone entry. An "input:<id>" target
     *  switches to that TV input (an HDMI port) instead, and an action word runs that action. */
    static void launch(Context ctx, String pkg) {
        if (pkg == null || runAction(ctx, pkg)) return;
        if (INPUTS.equals(pkg)) {
            // This launcher's own Inputs menu, as InputsRequestActivity opens it: addressed to Home directly, so a
            // vendor input picker that also answers VIEW_INPUTS never turns it into a chooser.
            try {
                ctx.startActivity(new Intent(ctx, MainActivity.class).setAction(MainActivity.ACTION_VIEW_INPUTS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
            }
            return;
        }
        String inputId = inputId(pkg);
        if (inputId != null) {
            // The same passthrough URI the Inputs menu opens, which the Live TV app handles.
            Intent view = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(inputId),
                            "vnd.android.cursor.item/channel")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(view);
            } catch (ActivityNotFoundException ignored) {
            }
            return;
        }
        Intent open = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (open == null) open = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (open == null) return;
        try {
            ctx.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }
}
