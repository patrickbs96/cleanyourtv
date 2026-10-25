package com.gathin.tvhome;

import android.view.KeyEvent;

/**
 * Which world a key press on Home switches to. A colour key switches on release, never while a popup, the app
 * library or reorder mode has the keys; firmware that sends the colour keys as KEYCODE_UNKNOWN is matched by scan code.
 */
final class WorldSwitch {
    private WorldSwitch() {}

    /** The world to switch to, or null when this event switches nothing. */
    static World target(KeyEvent event, World current, boolean popupOpen) {
        if (event.isCanceled()) return null;
        return target(event.getAction(), event.getKeyCode(), event.getScanCode(), current, popupOpen);
    }

    /** The same from plain values; KeyEvent cannot be built in JVM tests. */
    static World target(int action, int keyCode, int scanCode, World current, boolean popupOpen) {
        if (action != KeyEvent.ACTION_UP || popupOpen) return null;
        World target = keyCode == KeyEvent.KEYCODE_UNKNOWN ? World.forScanCode(scanCode) : World.forKeyCode(keyCode);
        return target == current ? null : target;
    }
}
