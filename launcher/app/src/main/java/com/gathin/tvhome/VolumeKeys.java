package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

/**
 * Two ways the key services handle Volume Up/Down, both off by default and both needing key_filter.
 *
 * consume_volume_keys: some firmware (TCL G03, Android 11, #1100) applies a volume key before the key
 * services' filter and again when the service hands it back, so one tap moved two steps. The services swallow
 * Volume Up/Down instead, which leaves the firmware's first step; a swallowed key gets no auto-repeat, so a held
 * one is repeated here, from the same delay and at about the rate Android uses.
 *
 * repeat_volume_keys: on other firmware (Philips 65OLED934, Android 11) nothing applies the key before the
 * filter, and a key the filter hands back is re-injected without auto-repeat, so a held key moved one step only.
 * The key is handed back as usual (Android applies the tap's step) and a held one is repeated here, the same way.
 * Firmware that does deliver real repeats would step twice: the first repeated down that arrives while our own
 * repeat runs stops it, and Android's repeats go through on their own. With both keys on, consume wins.
 *
 * The state is per process, not per service: with HomeKeyService and InputKeyService both bound, each sees the
 * same press, and the second call restarts the same repeat instead of adding a second one.
 */
final class VolumeKeys {
    static final long REPEAT_DELAY_MS = 500;
    static final long REPEAT_INTERVAL_MS = 60;

    /** What an event does to our own repeat. */
    static final int KEEP = 0;
    static final int START = 1;
    static final int STOP = 2;

    /** Created on first use, so the class loads in JVM tests that only call the pure methods. */
    private static Handler handler;
    private static Runnable step;

    private VolumeKeys() {
    }

    /** AudioManager direction for a volume key, or 0 for any other key. */
    static int direction(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) return AudioManager.ADJUST_RAISE;
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return AudioManager.ADJUST_LOWER;
        return 0;
    }

    /** Whether this event starts or ends a press; a repeated down event changes nothing. */
    static boolean changesPress(int action, int repeatCount) {
        return action != KeyEvent.ACTION_DOWN || repeatCount == 0;
    }

    /**
     * KEEP, START or STOP for a volume key event. The first down starts the repeat, anything but a down ends it.
     * A repeated down changes nothing when the key is swallowed (consume_volume_keys: the remote's own repeats
     * must not restart the 500 ms delay); handed back (repeat_volume_keys) it is a real repeat Android will apply,
     * so our own repeat, if it runs, stops.
     */
    static int plan(int action, int repeatCount, boolean swallow, boolean repeating) {
        if (changesPress(action, repeatCount)) return action == KeyEvent.ACTION_DOWN ? START : STOP;
        return !swallow && repeating ? STOP : KEEP;
    }

    /**
     * A volume key: repeats it while held and returns swallow (true swallows it, consume_volume_keys; false hands
     * it back, repeat_volume_keys). Any other key is left alone (false).
     */
    static boolean handle(Context context, KeyEvent event, boolean swallow) {
        int direction = direction(event.getKeyCode());
        if (direction == 0) return false;
        int plan = plan(event.getAction(), event.getRepeatCount(), swallow, step != null);
        if (plan == KEEP) return swallow;
        stop();
        if (plan == STOP) return swallow;
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio == null) return swallow;
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        step = new Runnable() {
            @Override
            public void run() {
                // Handed back (repeat_volume_keys) the stream is named: the Philips firmware ignores the suggested
                // stream from a service (adjustVolume moved nothing there), the media stream it takes.
                if (swallow) audio.adjustVolume(direction, AudioManager.FLAG_SHOW_UI);
                else audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
                handler.postDelayed(this, REPEAT_INTERVAL_MS);
            }
        };
        handler.postDelayed(step, REPEAT_DELAY_MS);
        return swallow;
    }

    /** Ends a repeat in progress, e.g. when the service goes away while a key is held. */
    static void stop() {
        if (handler != null && step != null) handler.removeCallbacks(step);
        step = null;
    }
}
