package com.gathin.tvhome;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * Enter ends a one-line text field in the settings' dialogs (Jahreskreis' names and greeting, a key's name): the
 * on-screen keyboard closes and the focus goes on to the next control, typically the dialog's Save or OK, so a second
 * OK confirms. Without it the keyboard stayed open after Enter and only Back closed it.
 */
final class ImeDone {
    private ImeDone() {
    }

    /**
     * Whether an editor action ends the input: the keyboard's Done (or Go, Next) key, or an Enter key pressed down on a
     * remote or hardware keyboard ({@code keyCode} -1 when the action came without a key event).
     */
    static boolean finishes(int actionId, int keyCode, int keyAction) {
        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO
                || actionId == EditorInfo.IME_ACTION_NEXT) return true;
        return (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) && keyAction == KeyEvent.ACTION_DOWN;
    }

    /** Makes Enter in {@code field} close the keyboard and move the focus to {@code next} (none when null). */
    static void finishInto(EditText field, View next) {
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setOnEditorActionListener((v, actionId, event) -> {
            int keyCode = event != null ? event.getKeyCode() : -1;
            int keyAction = event != null ? event.getAction() : -1;
            if (actionId == EditorInfo.IME_NULL && keyAction == KeyEvent.ACTION_UP
                    && finishes(actionId, keyCode, KeyEvent.ACTION_DOWN)) {
                return true;   // the up of an Enter already handled on its down
            }
            if (!finishes(actionId, keyCode, keyAction)) return false;
            hide(field);
            if (next != null) next.requestFocus();
            return true;
        });
    }

    /** Closes the on-screen keyboard for this field's window. */
    static void hide(View field) {
        InputMethodManager imm = (InputMethodManager) field.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }
}
