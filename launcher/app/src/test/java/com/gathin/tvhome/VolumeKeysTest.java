package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VolumeKeysTest {
    // Android's values, written out: android.view.KeyEvent and AudioManager aren't available in JVM tests.
    private static final int KEYCODE_VOLUME_UP = 24;
    private static final int KEYCODE_VOLUME_DOWN = 25;
    private static final int KEYCODE_VOLUME_MUTE = 164;
    private static final int KEYCODE_DPAD_UP = 19;
    private static final int ADJUST_RAISE = 1;
    private static final int ADJUST_LOWER = -1;
    private static final int ACTION_DOWN = 0;
    private static final int ACTION_UP = 1;

    @Test
    public void volumeUpAndDownAreHandled() {
        assertEquals(ADJUST_RAISE, VolumeKeys.direction(KEYCODE_VOLUME_UP));
        assertEquals(ADJUST_LOWER, VolumeKeys.direction(KEYCODE_VOLUME_DOWN));
    }

    @Test
    public void otherKeysPassThrough() {
        // Mute is one press with nothing to repeat, and the firmware did not double it.
        assertEquals(0, VolumeKeys.direction(KEYCODE_VOLUME_MUTE));
        assertEquals(0, VolumeKeys.direction(KEYCODE_DPAD_UP));
    }

    @Test
    public void onlyTheFirstDownAndTheUpChangeThePress() {
        assertTrue(VolumeKeys.changesPress(ACTION_DOWN, 0));
        assertTrue(VolumeKeys.changesPress(ACTION_UP, 0));
        // A remote that sends its own repeats must not restart the 500 ms delay on every one.
        assertFalse(VolumeKeys.changesPress(ACTION_DOWN, 1));
        assertFalse(VolumeKeys.changesPress(ACTION_DOWN, 12));
    }

    @Test
    public void consumeModeIsUnchanged() {
        // consume_volume_keys: a tap starts the repeat, a repeated down changes nothing, the release stops it.
        assertEquals(VolumeKeys.START, VolumeKeys.plan(ACTION_DOWN, 0, true, false));
        assertEquals(VolumeKeys.START, VolumeKeys.plan(ACTION_DOWN, 0, true, true));
        assertEquals(VolumeKeys.KEEP, VolumeKeys.plan(ACTION_DOWN, 3, true, true));
        assertEquals(VolumeKeys.KEEP, VolumeKeys.plan(ACTION_DOWN, 3, true, false));
        assertEquals(VolumeKeys.STOP, VolumeKeys.plan(ACTION_UP, 0, true, true));
    }

    @Test
    public void repeatModeStartsOnTheTapAndStopsOnTheRelease() {
        // repeat_volume_keys (Philips 65OLED934): the tap is handed back and our repeat is scheduled.
        assertEquals(VolumeKeys.START, VolumeKeys.plan(ACTION_DOWN, 0, false, false));
        // HomeKeyService and InputKeyService both see the press: the second one restarts the same repeat.
        assertEquals(VolumeKeys.START, VolumeKeys.plan(ACTION_DOWN, 0, false, true));
        assertEquals(VolumeKeys.STOP, VolumeKeys.plan(ACTION_UP, 0, false, true));
        assertEquals(VolumeKeys.STOP, VolumeKeys.plan(ACTION_UP, 0, false, false));
    }

    @Test
    public void aRealRepeatStopsOurOwn() {
        // Firmware that does repeat: Android applies its repeats, so ours would step twice.
        assertEquals(VolumeKeys.STOP, VolumeKeys.plan(ACTION_DOWN, 1, false, true));
        // Once ours has stopped, the following real repeats just go through.
        assertEquals(VolumeKeys.KEEP, VolumeKeys.plan(ACTION_DOWN, 2, false, false));
    }
}
