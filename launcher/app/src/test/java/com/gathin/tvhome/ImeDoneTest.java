package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import org.junit.Test;

public class ImeDoneTest {
    /** The keyboard's Done key and an Enter pressed down end the input; anything else (the Enter's up) does not. */
    @Test public void doneAndEnterFinishTheInput() {
        assertTrue(ImeDone.finishes(EditorInfo.IME_ACTION_DONE, -1, -1));
        assertTrue(ImeDone.finishes(EditorInfo.IME_ACTION_GO, -1, -1));
        assertTrue(ImeDone.finishes(EditorInfo.IME_ACTION_NEXT, -1, -1));
        assertTrue(ImeDone.finishes(EditorInfo.IME_NULL, KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN));
        assertTrue(ImeDone.finishes(EditorInfo.IME_NULL, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.ACTION_DOWN));
        assertTrue(ImeDone.finishes(EditorInfo.IME_NULL, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN));
        assertFalse(ImeDone.finishes(EditorInfo.IME_NULL, KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_UP));
        assertFalse(ImeDone.finishes(EditorInfo.IME_NULL, KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN));
        assertFalse(ImeDone.finishes(EditorInfo.IME_NULL, -1, -1));
    }
}
