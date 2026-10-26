package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Seasons (Launcher settings; Jahreskreis spec § 4.7): the household's personal days, the anniversary crawl text and
 * greeting, and the Mother's Day and Father's Day switches, all in PersonalDays.PREFS. Laid out like KeyBindingsActivity: Add an
 * entry on top, then one row per entry (kind icon, names, kind, date, start year); OK on a row changes it, and its
 * Remove asks first with the focus on Cancel. The kind is a choice, the names come from the keyboard, day, month and
 * start year from NumberPickers without it.
 *
 * Over adb, {@code am start -n com.gathin.tvhome/.SeasonsActivity --es force_date 2026-12-06} (or {@code auto}) only
 * saves the test date for this process (PersonalDays.saveForcedDate) and finishes without showing anything; the theme
 * is translucent so not even a starting window shows. The manifest lets only the shell and this app start it.
 */
public final class SeasonsActivity extends Activity {

    private static final String TAG = "SeasonsActivity";
    private static final int FIRST_YEAR = 1900;

    private SharedPreferences prefs;
    private LinearLayout list;
    private TextView addChoice;
    private TextView mothersDayChoice;
    private TextView fathersDayChoice;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent() != null && getIntent().hasExtra(PersonalDays.FORCE_DATE)) {
            forceDate(getIntent().getStringExtra(PersonalDays.FORCE_DATE));
            finish();
            return;
        }
        setContentView(R.layout.activity_seasons);
        prefs = getSharedPreferences(PersonalDays.PREFS, MODE_PRIVATE);
        list = findViewById(R.id.dayList);
        addChoice = findViewById(R.id.addChoice);
        addChoice.setOnClickListener(v -> edit(-1));
        findViewById(R.id.crawlChoice).setOnClickListener(v -> editCrawl());
        findViewById(R.id.anniversaryGreetingChoice).setOnClickListener(v -> editAnniversaryGreeting());
        mothersDayChoice = findViewById(R.id.mothersDayChoice);
        mothersDayChoice.setOnClickListener(v -> toggle(PersonalDays.MOTHERS_DAY));
        fathersDayChoice = findViewById(R.id.fathersDayChoice);
        fathersDayChoice.setOnClickListener(v -> toggle(PersonalDays.FATHERS_DAY));
        rebuild(null);
        addChoice.requestFocus();
    }

    /** The adb test switch: a bad value is logged and changes nothing; Home rebuilds with a good one. */
    private void forceDate(String value) {
        SharedPreferences seasons = getSharedPreferences(PersonalDays.PREFS, MODE_PRIVATE);
        if (PersonalDays.saveForcedDate(seasons, value)) {
            Log.i(TAG, "force_date " + value.trim());
            changed();
        } else {
            Log.w(TAG, "force_date ignored, not yyyy-MM-dd or auto: " + value);
        }
    }

    private static final String DAY_TAG = "day.";

    /** The entry rows and the two switches again; focus goes to the row tagged {@code focusTag} when there is one. */
    private void rebuild(String focusTag) {
        list.removeAllViews();
        List<PersonalDay> days = PersonalDays.load(prefs);
        for (int i = 0; i < days.size(); i++) {
            PersonalDay day = days.get(i);
            String head = icon(day.kind) + "  " + day.names.replace('\n', ' ');
            String detail = "   " + kindName(day.kind) + " · " + dayAndMonth(day.month, day.day) + " · " + day.startYear;
            SpannableString text = new SpannableString(head + detail);
            text.setSpan(new ForegroundColorSpan(0xFFACB8C1), head.length(), text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int index = i;
            TextView row = new TextView(this, null, 0, R.style.SettingsRow);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            params.topMargin = dp(10);
            row.setLayoutParams(params);
            // As the page's own rows: right-aligned and RTL in Hebrew (layout-iw), as written elsewhere.
            row.setGravity(addChoice.getGravity());
            row.setTextDirection(addChoice.getTextDirection());
            row.setSingleLine(true);
            row.setTag(DAY_TAG + i);
            row.setText(text);
            row.setOnClickListener(v -> edit(index));
            list.addView(row);
        }
        mothersDayChoice.setText(getString(R.string.seasons_mothers_day,
                getString(PersonalDays.mothersDay(prefs) ? R.string.on : R.string.off)));
        fathersDayChoice.setText(getString(R.string.seasons_fathers_day,
                getString(PersonalDays.fathersDay(prefs) ? R.string.on : R.string.off)));
        if (focusTag != null) {
            View row = list.findViewWithTag(focusTag);
            if (row != null) row.requestFocus();
        }
    }

    private void toggle(String key) {
        prefs.edit().putBoolean(key, !prefs.getBoolean(key, false)).commit();
        changed();
        rebuild(null);
    }

    /** The entry editor: {@code index} -1 adds a new entry, otherwise it changes or removes that one. */
    private void edit(int index) {
        List<PersonalDay> days = new ArrayList<>(PersonalDays.load(prefs));
        PersonalDay old = index >= 0 && index < days.size() ? days.get(index) : null;
        int thisYear = LocalDate.now().getYear();

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPaddingRelative(dp(24), dp(8), dp(24), 0);

        form.addView(caption(R.string.seasons_kind));
        RadioGroup kinds = new RadioGroup(this);
        kinds.setOrientation(RadioGroup.HORIZONTAL);
        PersonalDay.Kind[] all = PersonalDay.Kind.values();
        for (int k = 0; k < all.length; k++) {
            RadioButton choice = new RadioButton(this);
            choice.setId(View.generateViewId());
            choice.setTag(all[k]);
            choice.setText(icon(all[k]) + " " + kindName(all[k]));
            choice.setPaddingRelative(0, 0, dp(18), 0);
            kinds.addView(choice);
            if (all[k] == (old != null ? old.kind : PersonalDay.Kind.BIRTHDAY)) kinds.check(choice.getId());
        }
        form.addView(kinds);

        form.addView(caption(R.string.seasons_names));
        EditText names = new EditText(this);
        names.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        names.setSingleLine(true);
        names.setHint(R.string.seasons_names_hint);
        if (old != null) names.setText(old.names);
        form.addView(names, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        form.addView(caption(R.string.seasons_date));
        LinearLayout pickers = new LinearLayout(this);
        pickers.setOrientation(LinearLayout.HORIZONTAL);
        pickers.setGravity(Gravity.CENTER_HORIZONTAL);
        NumberPicker day = picker(1, 31, old != null ? old.day : LocalDate.now().getDayOfMonth());
        NumberPicker month = picker(1, 12, old != null ? old.month : LocalDate.now().getMonthValue());
        String[] months = new String[12];
        for (int m = 1; m <= 12; m++) months[m - 1] = Month.of(m).getDisplayName(TextStyle.FULL_STANDALONE, locale());
        month.setDisplayedValues(months);
        int startYear = old != null ? old.startYear : thisYear;
        NumberPicker year = picker(Math.min(FIRST_YEAR, startYear), Math.max(thisYear + 5, startYear), startYear);
        // Only real days: 29 February stays, it falls on the 28th in other years (PersonalDay.isOn).
        day.setMaxValue(Month.of(month.getValue()).maxLength());
        month.setOnValueChangedListener((p, before, after) -> day.setMaxValue(Month.of(after).maxLength()));
        pickers.addView(day);
        pickers.addView(month);
        pickers.addView(year);
        form.addView(pickers);
        TextView yearHint = caption(R.string.seasons_year_hint);
        yearHint.setTextSize(14);
        yearHint.setTextColor(0xFFACB8C1);
        form.addView(yearHint);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(old != null ? R.string.seasons_edit : R.string.seasons_new)
                .setView(form)
                .setPositiveButton(R.string.seasons_save, null)
                .setNegativeButton(android.R.string.cancel, null);
        if (old != null) builder.setNeutralButton(R.string.seasons_remove, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            // Save closes only with a name; the dialog stays open for one otherwise.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String entered = names.getText().toString().trim();
                if (entered.isEmpty()) {
                    Toast.makeText(this, R.string.seasons_names_missing, Toast.LENGTH_SHORT).show();
                    names.requestFocus();
                    return;
                }
                RadioButton picked = kinds.findViewById(kinds.getCheckedRadioButtonId());
                PersonalDay.Kind kind = picked != null ? (PersonalDay.Kind) picked.getTag() : PersonalDay.Kind.BIRTHDAY;
                PersonalDay entry = new PersonalDay(kind, month.getValue(), day.getValue(), year.getValue(), entered);
                int at;
                if (old != null) {
                    days.set(index, entry);
                    at = index;
                } else {
                    days.add(entry);
                    at = days.size() - 1;
                }
                PersonalDays.save(prefs, days);
                changed();
                dialog.dismiss();
                rebuild(DAY_TAG + at);
            });
            Button remove = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (remove != null) {
                remove.setTextColor(Color.rgb(0xFF, 0x6B, 0x6B));
                remove.setOnClickListener(v -> {
                    dialog.dismiss();
                    confirmRemove(index, old);
                });
            }
        });
        dialog.show();
        if (old == null) names.requestFocus();
    }

    /** Nothing is removed without asking: the entry's names, what goes, and the focus on Cancel. */
    private void confirmRemove(int index, PersonalDay old) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.seasons_remove)
                .setMessage(getString(R.string.seasons_remove_confirm, old.names.replace('\n', ' ')))
                .setPositiveButton(R.string.seasons_remove, (d, which) -> {
                    List<PersonalDay> days = new ArrayList<>(PersonalDays.load(prefs));
                    // By its saved line, not only its place: the list is read again, in case it changed meanwhile.
                    if (index < days.size() && days.get(index).encode().equals(old.encode())) {
                        days.remove(index);
                        PersonalDays.save(prefs, days);
                        changed();
                    }
                    rebuild(null);
                    addChoice.requestFocus();
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

    /** The crawl text, several lines from the keyboard; empty brings back the default. */
    private void editCrawl() {
        String defaultText = getString(R.string.seasons_crawl_default);
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setMinLines(4);
        field.setMaxLines(10);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setText(PersonalDays.crawlText(prefs, defaultText));
        LinearLayout frame = new LinearLayout(this);
        frame.setPaddingRelative(dp(24), dp(8), dp(24), 0);
        frame.addView(field, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(R.string.seasons_crawl)
                .setMessage(R.string.seasons_crawl_hint)
                .setView(frame)
                .setPositiveButton(R.string.seasons_save, (d, which) -> {
                    PersonalDays.saveCrawlText(prefs, field.getText().toString(), defaultText);
                    changed();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        field.requestFocus();
    }

    /** The anniversary's greeting, one line from the keyboard; empty or the default itself brings back the default. */
    private void editAnniversaryGreeting() {
        String defaultText = getString(R.string.seasons_anniversary_greeting_default);
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setSingleLine(true);
        field.setText(PersonalDays.anniversaryGreeting(prefs, defaultText));
        LinearLayout frame = new LinearLayout(this);
        frame.setPaddingRelative(dp(24), dp(8), dp(24), 0);
        frame.addView(field, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(R.string.seasons_anniversary_greeting)
                .setMessage(R.string.seasons_anniversary_greeting_hint)
                .setView(frame)
                .setPositiveButton(R.string.seasons_save, (d, which) -> {
                    PersonalDays.saveAnniversaryGreeting(prefs, field.getText().toString(), defaultText);
                    changed();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        field.requestFocus();
    }

    private TextView caption(int text) {
        TextView caption = new TextView(this);
        caption.setText(text);
        caption.setTextColor(Color.WHITE);
        caption.setTextSize(16);
        caption.setPaddingRelative(0, dp(12), 0, dp(4));
        return caption;
    }

    /** A picker the arrows turn; its number field takes no focus, so the keyboard never opens. */
    private NumberPicker picker(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(Math.max(min, Math.min(max, value)));
        picker.setWrapSelectorWheel(true);
        picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(12));
        params.setMarginEnd(dp(12));
        picker.setLayoutParams(params);
        return picker;
    }

    private static String icon(PersonalDay.Kind kind) {
        switch (kind) {
            case ANNIVERSARY: return "♥";          // heart
            case WEDDING: return "💍";        // ring
            default: return "🎂";             // birthday cake
        }
    }

    private String kindName(PersonalDay.Kind kind) {
        switch (kind) {
            case ANNIVERSARY: return getString(R.string.seasons_kind_anniversary);
            case WEDDING: return getString(R.string.seasons_kind_wedding);
            default: return getString(R.string.seasons_kind_birthday);
        }
    }

    /** Day and month in the TV's language, e.g. "24 December"; 2000 is a leap year, so 29 February formats too. */
    private String dayAndMonth(int month, int day) {
        String pattern = DateFormat.getBestDateTimePattern(locale(), "dMMMM");
        return LocalDate.of(2000, month, day).format(DateTimeFormatter.ofPattern(pattern, locale()));
    }

    private Locale locale() {
        return getResources().getConfiguration().getLocales().get(0);
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
