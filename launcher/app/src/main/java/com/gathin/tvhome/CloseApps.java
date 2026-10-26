package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Close all apps (fork, spec § 4.10f; the close_apps target, Home's double tap by default, and a row in the Settings
 * menu): ends the background processes of every installed app with ActivityManager.killBackgroundProcesses
 * (KILL_BACKGROUND_PROCESSES, a normal permission). Left running: this launcher, Live TV, Play Services / Store,
 * the Assistant, Chromecast, Android's own packages and Philips' system services (org.droidtv.*).
 *
 * The limit without root: Android ends only processes it counts as background. The app on screen, and any app with a
 * foreground service (Spotify while it plays, a Plex server), keep running, and the call says nothing about what it
 * ended (it returns void), so the toast gives no count. A hung app is no exception: Home's first press sends it to
 * the background, the second closes it, which is the case this is for.
 */
final class CloseApps {

    /** Two runs this close together are one: Android hands each key to every filtering service, so both see it. */
    static final long ONCE_MS = 1500;
    /** How long the work waits, so the app that Home's first press just sent away has stopped (no longer visible). */
    static final long SETTLE_MS = 500;

    /** Never closed: Android, Play Services and Store, text-to-speech, the Assistant and Chromecast. */
    private static final String[] SYSTEM = {
            "com.google.android.gms", "com.google.android.gsf", "com.android.vending", "com.google.android.tts",
            "com.google.android.katniss", "com.google.android.apps.mediashell"};

    /** The last run (uptime), for ONCE_MS; main thread only. */
    private static long lastRun;

    private CloseApps() { }

    /**
     * The packages to close: the TV apps, then the phone apps, each once and in that order, without this launcher,
     * {@code keep} (Live TV), Android's packages, Play Services, Store, the Assistant, Chromecast and org.droidtv.*.
     */
    static List<String> candidates(List<String> leanbackPackages, List<String> libraryPackages, String self,
                                   Set<String> keep) {
        Set<String> found = new LinkedHashSet<>();
        for (List<String> list : List.of(leanbackPackages, libraryPackages)) {
            for (String pkg : list) {
                if (pkg == null || pkg.isEmpty() || pkg.equals(self) || keep.contains(pkg) || system(pkg)) continue;
                found.add(pkg);
            }
        }
        return new ArrayList<>(found);
    }

    private static boolean system(String pkg) {
        if (pkg.startsWith("com.android.") || pkg.startsWith("org.droidtv.")) return true;
        for (String name : SYSTEM) {
            if (pkg.equals(name) || pkg.startsWith(name + ".")) return true;
        }
        return false;
    }

    /** Whether a run at {@code nowMs} is a new one, {@code lastMs} being the last (0: none yet). */
    static boolean due(long nowMs, long lastMs) {
        return lastMs == 0 || nowMs - lastMs >= ONCE_MS;
    }

    /**
     * Closes the apps and shows the toast; from a key service or Home, on the main thread. The package lists and the
     * calls run off it, so the key that started this is not held up; a second call within ONCE_MS does nothing.
     */
    static void start(Context ctx) {
        long now = SystemClock.uptimeMillis();
        if (!due(now, lastRun)) return;
        lastRun = now;
        Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> new Thread(() -> {
            run(app, candidates(app));
            main.post(() -> Toast.makeText(app, R.string.close_apps_done, Toast.LENGTH_LONG).show());
        }, "close-apps").start(), SETTLE_MS);
    }

    /** The installed apps with a TV or a phone entry, as candidates; Live TV is live_tv_package. */
    static List<String> candidates(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        String liveTv = ctx.getString(R.string.live_tv_package).trim();
        return candidates(launchable(pm, Intent.CATEGORY_LEANBACK_LAUNCHER), launchable(pm, Intent.CATEGORY_LAUNCHER),
                ctx.getPackageName(), liveTv.isEmpty() ? Set.of() : Set.of(liveTv));
    }

    private static List<String> launchable(PackageManager pm, String category) {
        List<String> packages = new ArrayList<>();
        for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
            packages.add(r.activityInfo.packageName);
        }
        return packages;
    }

    /**
     * Asks Android to end each candidate's background processes; returns how many it was asked for. Not how many
     * ran: killBackgroundProcesses returns nothing, and since Android 5.1 getRunningAppProcesses lists only this app.
     */
    static int run(Context ctx, List<String> candidates) {
        ActivityManager am = ctx.getSystemService(ActivityManager.class);
        if (am == null) return 0;
        int asked = 0;
        for (String pkg : candidates) {
            try {
                am.killBackgroundProcesses(pkg);
                asked++;
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        return asked;
    }
}
