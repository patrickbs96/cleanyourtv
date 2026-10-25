package com.gathin.tvhome;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * SharedPreferences over a HashMap, for JVM tests. An editor collects its changes and applies them on commit or apply
 * as Android does: clear() first, then the removes and puts in the order they were made. Listeners are kept but never
 * called. {@link #put} sets a value directly, without an editor.
 */
final class FakePrefs implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();

    /** Sets a value as a test's starting state; null removes the key. */
    FakePrefs put(String key, Object value) {
        if (value == null) values.remove(key);
        else values.put(key, value);
        return this;
    }

    @Override
    public Map<String, ?> getAll() {
        return new HashMap<>(values);
    }

    @Override
    public String getString(String key, String defValue) {
        Object v = values.get(key);
        return v != null ? (String) v : defValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues) {
        Object v = values.get(key);
        return v != null ? new HashSet<>((Set<String>) v) : defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        Object v = values.get(key);
        return v != null ? (Integer) v : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object v = values.get(key);
        return v != null ? (Long) v : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object v = values.get(key);
        return v != null ? (Float) v : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object v = values.get(key);
        return v != null ? (Boolean) v : defValue;
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new FakeEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }

    private final class FakeEditor implements Editor {
        /** Pending changes in order; a null value removes the key. */
        private final Map<String, Object> changes = new java.util.LinkedHashMap<>();
        private boolean clear;

        private Editor change(String key, Object value) {
            changes.remove(key);   // the last change to a key wins, in its new place
            changes.put(key, value);
            return this;
        }

        @Override public Editor putString(String key, String value) { return change(key, value); }
        @Override public Editor putStringSet(String key, Set<String> values) { return change(key, values == null ? null : new HashSet<>(values)); }
        @Override public Editor putInt(String key, int value) { return change(key, value); }
        @Override public Editor putLong(String key, long value) { return change(key, value); }
        @Override public Editor putFloat(String key, float value) { return change(key, value); }
        @Override public Editor putBoolean(String key, boolean value) { return change(key, value); }
        @Override public Editor remove(String key) { return change(key, null); }

        @Override
        public Editor clear() {
            clear = true;
            return this;
        }

        @Override
        public boolean commit() {
            if (clear) values.clear();
            for (Map.Entry<String, Object> e : changes.entrySet()) put(e.getKey(), e.getValue());
            clear = false;
            changes.clear();
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }
}
