package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.Consumer;

/**
 * Key bindings (Launcher settings; fork, spec § 4.10d), which replace a button-mapper app: Learn a button catches the
 * next press through the key services (KeyBindings.learn) and asks for a name; each learned button then has a tap, a
 * double tap and a hold, each picked with AppPickerDialog, and the Home button has a double tap. Long OK (or OK) on
 * a button's name renames or removes it, the removal only after a confirmation. Saved at once in KeyBindings.NAME,
 * which the key services watch, so a change works on the next press.
 */
public final class KeyBindingsActivity extends Activity implements KeyBindings.Learner {

    private SharedPreferences prefs;
    private TextView learnChoice;
    private LinearLayout list;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable learnTimeout = this::learnTimedOut;
    private boolean learning;
    /** The keycode whose release this page swallows, after it caught the press itself; -1 when none. */
    private int swallowUp = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_key_bindings);
        prefs = getSharedPreferences(KeyBindings.NAME, MODE_PRIVATE);
        learnChoice = findViewById(R.id.learnChoice);
        learnChoice.setOnClickListener(v -> {
            if (learning) stopLearning();
            else startLearning();
        });
        list = findViewById(R.id.keyList);
        rebuild(null);
        learnChoice.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Read-only: whether either key service is on, without which the bindings do nothing.
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean on = enabled != null && enabled.contains(getPackageName() + "/");
        findViewById(R.id.serviceOff).setVisibility(on ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onPause() {
        if (learning) stopLearning();
        super.onPause();
    }

    private void startLearning() {
        learning = true;
        KeyBindings.startLearning(this);
        learnChoice.setText(R.string.key_learn_waiting);
        handler.postDelayed(learnTimeout, KeyBindings.LEARN_MS);
    }

    private void stopLearning() {
        learning = false;
        KeyBindings.stopLearning();
        handler.removeCallbacks(learnTimeout);
        learnChoice.setText(R.string.key_learn);
    }

    private void learnTimedOut() {
        if (!learning) return;
        stopLearning();
        Toast.makeText(this, R.string.key_learn_timeout, Toast.LENGTH_SHORT).show();
    }

    /** A press caught in learn mode, by a key service or, when none runs, by this page. */
    @Override
    public void learned(int keycode, int scancode) {
        if (!learning) return;
        stopLearning();
        int known = KeyBindings.find(prefs, keycode, scancode);
        if (known >= 0) {
            for (KeyBindings.Key key : KeyBindings.keys(prefs)) {
                if (key.n != known) continue;
                Toast.makeText(this, getString(R.string.key_learn_known, key.name), Toast.LENGTH_SHORT).show();
            }
            focus("key." + known);
            return;
        }
        String suggestion = getString(R.string.key_name_default, scancode > 0 ? scancode : keycode);
        askName(suggestion, name -> {
            int n = KeyBindings.add(prefs, name.isEmpty() ? suggestion : name, keycode, scancode);
            changed();
            rebuild(TAP_TAG + n);
        });
    }

    /**
     * Learn mode without a key service: the press reaches this page instead, so it is caught here (bindings need the
     * service to work, the hint says so). With a service on, the services take the learnable presses first.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (swallowUp >= 0 && event.getKeyCode() == swallowUp) {
            if (event.getAction() == KeyEvent.ACTION_UP) swallowUp = -1;
            return true;
        }
        if (learning && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK) {
                stopLearning();
                swallowUp = code;
                return true;
            }
            if (KeyBindings.learnable(code, event.getScanCode())) {
                swallowUp = code;
                learned(code, event.getScanCode());
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                // OK can't be learned, and it must not click the row and end learn mode either: it keeps waiting.
                Toast.makeText(this, R.string.key_learn_never, Toast.LENGTH_SHORT).show();
                swallowUp = code;
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static final String TAP_TAG = KeyBindings.TAP + ".";
    private static final String HOME_TAG = "home";

    /** The rows under Learn a button, again; focus goes to the row tagged {@code focusTag} when there is one. */
    private void rebuild(String focusTag) {
        list.removeAllViews();
        for (KeyBindings.Key key : KeyBindings.keys(prefs)) {
            String code = key.keycode > 0 ? "Keycode " + key.keycode : "Scancode " + key.scancode;
            SpannableString title = new SpannableString(key.name + "   " + code);
            title.setSpan(new ForegroundColorSpan(0xFFACB8C1), key.name.length(), title.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextView header = row("key." + key.n, title, 28, false);
            header.setOnClickListener(v -> keyMenu(key));
            header.setOnLongClickListener(v -> {
                keyMenu(key);
                return true;
            });
            slotRow(key, KeyBindings.TAP, R.string.key_tap, R.string.key_slot_tap);
            slotRow(key, KeyBindings.DOUBLE, R.string.key_double, R.string.key_slot_double);
            slotRow(key, KeyBindings.HOLD, R.string.key_hold, R.string.key_slot_hold);
        }
        // The Home button: only a double tap, since its press always goes to the system first.
        TextView caption = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(28);
        caption.setLayoutParams(params);
        caption.setText(R.string.key_home);
        caption.setTextColor(Color.WHITE);
        caption.setTextSize(20);
        list.addView(caption);
        TextView home = row(HOME_TAG, getString(R.string.key_double,
                AppPickerDialog.label(this, KeyBindings.homeDouble(prefs))), 10, true);
        home.setOnClickListener(v -> AppPickerDialog.show(this,
                getString(R.string.key_home) + " · " + getString(R.string.key_slot_double),
                KeyBindings.homeDouble(prefs), target -> {
                    KeyBindings.setHomeDouble(prefs, target);
                    changed();
                    rebuild(HOME_TAG);
                }));
        if (focusTag != null) focus(focusTag);
    }

    private void slotRow(KeyBindings.Key key, String slot, int rowText, int slotName) {
        String tag = slot + "." + key.n;
        TextView row = row(tag, getString(rowText, AppPickerDialog.label(this, KeyBindings.get(prefs, key.n, slot))),
                10, true);
        row.setOnClickListener(v -> AppPickerDialog.show(this, key.name + " · " + getString(slotName),
                KeyBindings.get(prefs, key.n, slot), target -> {
                    KeyBindings.set(prefs, key.n, slot, target);
                    changed();
                    rebuild(tag);
                }));
    }

    /** A settings row under the list; a slot row is set in under its button's name. */
    private TextView row(String tag, CharSequence text, int marginTopDp, boolean indented) {
        TextView row = new TextView(this, null, 0, R.style.SettingsRow);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        params.topMargin = dp(marginTopDp);
        row.setLayoutParams(params);
        if (indented) row.setPaddingRelative(dp(42), 0, dp(18), 0);
        row.setTag(tag);
        row.setText(text);
        list.addView(row);
        return row;
    }

    private void focus(String tag) {
        View row = list.findViewWithTag(tag);
        if (row != null) row.requestFocus();
    }

    private void keyMenu(KeyBindings.Key key) {
        String[] items = {getString(R.string.key_rename), getString(R.string.key_remove)};
        new AlertDialog.Builder(this)
                .setTitle(key.name)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        askName(key.name, name -> {
                            if (name.isEmpty()) return;
                            KeyBindings.rename(prefs, key.n, name);
                            rebuild("key." + key.n);
                        });
                    } else {
                        confirmRemove(key);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Nothing is removed without asking: the name, what goes, and the focus on Cancel. */
    private void confirmRemove(KeyBindings.Key key) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.key_remove)
                .setMessage(getString(R.string.key_remove_confirm, key.name))
                .setPositiveButton(R.string.key_remove, (d, which) -> {
                    KeyBindings.remove(prefs, key.n);
                    changed();
                    rebuild(null);
                    learnChoice.requestFocus();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            Button remove = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (remove != null) remove.setTextColor(Color.rgb(0xFF, 0x6B, 0x6B));
            Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (cancel != null) cancel.requestFocus();
        });
        dialog.show();
    }

    private void askName(String suggestion, Consumer<String> named) {
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setSingleLine(true);
        field.setText(suggestion);
        field.selectAll();
        LinearLayout frame = new LinearLayout(this);
        frame.setPaddingRelative(dp(24), dp(8), dp(24), 0);
        frame.addView(field, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(R.string.key_name)
                .setView(frame)
                .setPositiveButton(android.R.string.ok, (d, which) -> named.accept(field.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        field.requestFocus();
    }

    /** Home rebuilds on its next resume when the launcher settings' change count moved, as for every other choice. */
    private void changed() {
        SharedPreferences choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1)
                .apply();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
