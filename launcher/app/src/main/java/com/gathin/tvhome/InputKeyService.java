package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.Collections;
import java.util.List;

/**
 * Maps the remote's Input button to the system input switcher, from any app. Remotes such as the Chromecast voice
 * remote send KEYCODE_TV for it, others (KTC and Kalley Realtek Google TV remotes) KEYCODE_TV_INPUT; on Google TV
 * the stock launcher handles both, so with the stock launcher disabled the button does nothing. Buttons listed in
 * input_key_keycodes (config.xml or a theme) open the input switcher the same way, and app_keys start an app (a
 * YouTube button that only the stock launcher answered). Every other key passes through untouched.
 * Opt-in: INPUT_KEY_SERVICE=1 build-and-install.sh (or -InputKeyService on Windows) enables it.
 */
public class InputKeyService extends AccessibilityService {

    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    private int[] inputKeycodes = new int[0];
    /** app_keys with the on-TV bindings (KeyBindings) over them. */
    private final RemoteKeys.Keys keys = new RemoteKeys.Keys();
    private List<String> configAppKeys = Collections.emptyList();
    /** KeyBindings.NAME, watched while bound; the listener is held here, SharedPreferences keeps it only weakly. */
    private SharedPreferences bindings;
    private final SharedPreferences.OnSharedPreferenceChangeListener bindingsChanged = (prefs, key) -> loadKeys();
    /** restore_key_services was on when the config was read: this helper is noted for ServiceRepair. */
    private boolean repairRecord;
    /** consume_volume_keys: Volume Up/Down go to VolumeKeys instead of on to the firmware. */
    private boolean consumeVolumeKeys;
    /** repeat_volume_keys, unless consume_volume_keys is on: Volume Up/Down go on as usual, VolumeKeys repeats a held one. */
    private boolean repeatVolumeKeys;
    /** Stops key filtering while a game controller is connected; null unless gamepad_passthrough and key_filter. */
    private GamepadPassthrough gamepads;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // From config.xml or a theme's config.json; theme.sh rebinds this service after a push.
        Config config = Config.load(this);
        // restore_key_services: note that this helper runs, so Home can put it back after a force stop.
        repairRecord = config.restoreKeyServices;
        if (repairRecord) ServiceRepair.connected(this, getClass());
        inputKeycodes = config.inputKeycodes;
        consumeVolumeKeys = config.consumeVolumeKeys;
        repeatVolumeKeys = config.repeatVolumeKeys && !consumeVolumeKeys;
        configAppKeys = config.appKeys;
        if (bindings == null) {
            bindings = getSharedPreferences(KeyBindings.NAME, MODE_PRIVATE);
            bindings.registerOnSharedPreferenceChangeListener(bindingsChanged);
        }
        loadKeys();
        RemoteKeys.applyKeyFilter(this, config.keyFilter);
        // gamepad_passthrough, off by default. Only with key_filter on: off, the service never filters keys.
        if (config.gamepadPassthrough && config.keyFilter && gamepads == null) {
            gamepads = new GamepadPassthrough(this);
            gamepads.start();
        }
    }

    /** app_keys, then the on-TV bindings over them; again whenever those change. */
    private void loadKeys() {
        keys.load(bindings == null ? configAppKeys : KeyBindings.overlay(configAppKeys, bindings));
    }

    private boolean isInputKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_TV || keyCode == KeyEvent.KEYCODE_TV_INPUT) return true;
        for (int code : inputKeycodes) if (keyCode == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Learn mode (Key bindings page): the next learnable press goes to the page instead. Never volume or Home.
        if (KeyBindings.learn(event)) return true;
        if (consumeVolumeKeys && VolumeKeys.handle(this, event, true)) return true;
        if (repeatVolumeKeys) VolumeKeys.handle(this, event, false);
        keys.note(event);
        // A scancode entry is more specific than any keycode, so it goes first.
        if (keys.handleScancode(this, event)) return true;
        if (keys.handleKeycode(this, event)) return true;
        if (!isInputKey(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            try {
                startActivity(new Intent(ACTION_VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
            }
        }
        return true;
    }

    /** Unbound by Android: the user turned the helper off, so Home doesn't put it back (ServiceRepair). */
    @Override
    public boolean onUnbind(Intent intent) {
        if (repairRecord) ServiceRepair.disconnected(this, getClass());
        keys.drop();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (gamepads != null) gamepads.stop();
        if (consumeVolumeKeys || repeatVolumeKeys) VolumeKeys.stop();
        keys.drop();
        if (bindings != null) bindings.unregisterOnSharedPreferenceChangeListener(bindingsChanged);
        bindings = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
