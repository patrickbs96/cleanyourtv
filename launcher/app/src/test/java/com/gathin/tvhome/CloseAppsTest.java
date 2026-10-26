package com.gathin.tvhome;

import android.view.KeyEvent;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CloseAppsTest {
    @Test public void candidatesSkipSelfSystemAndKeptPackages() {
        List<String> c = CloseApps.candidates(List.of("com.netflix.ninja", "com.gathin.tvhome", "com.android.vending", "com.google.android.katniss", "org.droidtv.playtv", "com.spotify.tv.android"), List.of("flar2.homebutton"), "com.gathin.tvhome", Set.of("org.droidtv.playtv"));
        assertEquals(List.of("com.netflix.ninja", "com.spotify.tv.android", "flar2.homebutton"), c);
    }
    @Test public void homeDoubleTapIsNeverHeldBack() { RemoteKeys.DoubleTap d = new RemoteKeys.DoubleTap(400); assertEquals(RemoteKeys.DoubleTap.PASS, d.onTap(KeyEvent.KEYCODE_HOME, 1000)); assertEquals(RemoteKeys.DoubleTap.SECOND, d.onTap(KeyEvent.KEYCODE_HOME, 1300)); }

    @Test public void candidatesKeepTheirOrderWithoutDuplicates() {
        List<String> c = CloseApps.candidates(List.of("b.app", "a.app"), List.of("a.app", "c.app", "b.app"),
                "com.gathin.tvhome", Set.of());
        assertEquals(List.of("b.app", "a.app", "c.app"), c);
    }

    @Test public void playServicesAssistantCastAndPhilipsServicesRunOn() {
        List<String> c = CloseApps.candidates(List.of("com.google.android.gms", "com.google.android.gsf",
                "com.google.android.tts", "com.google.android.apps.mediashell", "com.android.tv.settings",
                "org.droidtv.settings", "com.google.android.youtube.tv"), List.of(), "com.gathin.tvhome", Set.of());
        assertEquals(List.of("com.google.android.youtube.tv"), c);
    }

    @Test public void closeAppsIsAnActionWord() {
        assertTrue(RemoteKeys.isAction("close_apps"));
        assertEquals(0, RemoteKeys.globalAction("close_apps", 30));
    }

    @Test public void bothKeyServicesCloseOnlyOnce() {
        // Android hands each key to every filtering service, so a double tap reaches both: the second run is skipped.
        assertTrue(CloseApps.due(10_000, 0));
        assertFalse(CloseApps.due(10_000 + CloseApps.ONCE_MS - 1, 10_000));
        assertTrue(CloseApps.due(10_000 + CloseApps.ONCE_MS, 10_000));
    }
}
