package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The weather line under the date (spec § 4.10c): "12° · Bewölkt" from Open-Meteo for latitude and longitude in
 * config.xml. The launcher's only network access: one HTTPS GET to api.open-meteo.com, every 30 minutes while Home is in
 * front, 5 minutes after a failure, never in the background. The last reading is kept in SharedPreferences "weather" and
 * shown at once on a start within 2 hours of it. A failure leaves the line as it was (the last value, or gone when there
 * is none). Off with weather=false, or without coordinates, and then nothing is fetched at all.
 */
final class Weather {
    private static final String TAG = "Weather";
    static final long PERIOD_MS = 30 * 60_000L;
    static final long RETRY_MS = 5 * 60_000L;
    static final long MAX_AGE_MS = 2 * 3600_000L;
    private static final int TIMEOUT_MS = 5000;
    /** Open-Meteo's answer is about 400 bytes; anything far longer is not it. */
    private static final int MAX_BODY = 16 * 1024;
    private static final String PREFS = "weather";
    private static final String KEY_TEMP = "temp";
    private static final String KEY_CODE = "code";
    private static final String KEY_AT = "at";

    /** One reading: the temperature in whole degrees and the WMO weather code. */
    static final class Reading {
        final int temperature;
        final int code;

        Reading(int temperature, int code) {
            this.temperature = temperature;
            this.code = code;
        }
    }

    private final Context context;
    private final TextView line;
    /** The request, or null when the line is off (weather false, no or broken coordinates). */
    private final String url;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable fetch = this::fetch;
    private ExecutorService executor;
    private boolean running;
    /** Counts starts and stops, so an answer that arrives after stop() changes nothing on screen. */
    private int generation;
    /** When the last fetch failed, 0 when the last one worked; kept for this process only. */
    private long failedAt;

    Weather(Context context, TextView line, String latitude, String longitude, boolean enabled) {
        this.context = context.getApplicationContext();
        this.line = line;
        String lat = coordinate(latitude, 90);
        String lon = coordinate(longitude, 180);
        this.url = enabled && lat != null && lon != null ? url(lat, lon) : null;
    }

    /** Shows the saved reading while it is fresh and plans the next fetch; nothing when the line is off. */
    void start() {
        if (url == null || running) return;
        running = true;
        generation++;
        SharedPreferences prefs = prefs();
        long now = System.currentTimeMillis();
        long at = prefs.getLong(KEY_AT, 0);
        if (at > 0 && fresh(at, now)) {
            show(prefs.getInt(KEY_TEMP, 0), prefs.getInt(KEY_CODE, -1));
        } else {
            // Older than 2 hours (Home was away that long): no stale value; the fetch below brings a new one.
            line.setVisibility(View.GONE);
        }
        boolean lastFailed = failedAt > at;
        handler.postDelayed(fetch, firstDelayMs(Math.max(at, failedAt), lastFailed, now));
    }

    /** No more fetches until the next start; an answer still on its way is dropped. */
    void stop() {
        if (!running) return;
        running = false;
        generation++;
        handler.removeCallbacks(fetch);
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void fetch() {
        if (!running) return;
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "weather");
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
        }
        final int gen = generation;
        final String request = url;
        executor.execute(() -> {
            long started = SystemClock.elapsedRealtime();
            int status = -1;
            String error = null;
            Reading reading = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(request).openConnection();
                c.setConnectTimeout(TIMEOUT_MS);
                c.setReadTimeout(TIMEOUT_MS);
                c.setUseCaches(false);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("Accept", "application/json");
                status = c.getResponseCode();
                if (status == HttpURLConnection.HTTP_OK) {
                    try (InputStream in = c.getInputStream()) {
                        reading = parse(read(in));
                    }
                    if (reading == null) error = "unreadable answer";
                }
            } catch (IOException | RuntimeException e) {
                // The class only: some exception messages carry the URL, and with it the coordinates.
                error = e.getClass().getSimpleName();
            } finally {
                if (c != null) c.disconnect();
            }
            long ms = SystemClock.elapsedRealtime() - started;
            Log.i(TAG, "fetch " + (reading != null ? "ok" : "failed") + ", HTTP " + status + ", " + ms + " ms"
                    + (error != null ? ", " + error : ""));
            final Reading result = reading;
            handler.post(() -> done(gen, result));
        });
    }

    private void done(int gen, Reading reading) {
        long now = System.currentTimeMillis();
        if (reading != null) {
            prefs().edit().putInt(KEY_TEMP, reading.temperature).putInt(KEY_CODE, reading.code)
                    .putLong(KEY_AT, now).apply();
            failedAt = 0;
        } else {
            failedAt = now;
        }
        if (gen != generation || !running) return;
        // A failure leaves the line alone: the last value stays, or it stays gone.
        if (reading != null) show(reading.temperature, reading.code);
        handler.postDelayed(fetch, nextDelayMs(reading == null));
    }

    private void show(int temperature, int code) {
        Resources res = line.getResources();
        String word = res.getString(WeatherWords.stringRes(res, WeatherWords.key(code)));
        line.setText(res.getString(R.string.weather_line, String.valueOf(temperature), word));
        line.setVisibility(View.VISIBLE);
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        for (int n; (n = in.read(buf)) != -1; ) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BODY) return null;
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** The one request this launcher makes. Coordinates as given; the constructor rounds them to two decimals. */
    static String url(String lat, String lon) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code&timezone=auto";
    }

    /** A coordinate in decimal degrees rounded to two decimals (about 1 km), or null when empty, unreadable or out of range. */
    static String coordinate(String value, double limit) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            double d = Double.parseDouble(value.trim());
            if (Double.isNaN(d) || Math.abs(d) > limit) return null;
            return String.format(Locale.ROOT, "%.2f", d);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final Pattern CURRENT = Pattern.compile("\"current\"\\s*:\\s*\\{([^{}]*)\\}");
    /** Each number up to the next comma or the block's end, so 2.5 is no code and "11.6x" no temperature. */
    private static final Pattern TEMPERATURE =
            Pattern.compile("\"temperature_2m\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)(?=\\s*(?:,|$))");
    private static final Pattern CODE = Pattern.compile("\"weather_code\"\\s*:\\s*(\\d{1,3})(?=\\s*(?:,|$))");

    /**
     * temperature_2m (rounded) and weather_code from the "current" block of Open-Meteo's answer, null for anything else.
     * A small scanner for exactly these two numbers: org.json is only a stub in the JVM tests.
     */
    static Reading parse(String json) {
        if (json == null) return null;
        Matcher block = CURRENT.matcher(json);
        if (!block.find()) return null;
        String current = block.group(1);
        Matcher t = TEMPERATURE.matcher(current);
        Matcher c = CODE.matcher(current);
        if (!t.find() || !c.find()) return null;
        try {
            double temperature = Double.parseDouble(t.group(1));
            int code = Integer.parseInt(c.group(1));
            if (Double.isNaN(temperature) || Math.abs(temperature) > 100) return null;
            return new Reading((int) Math.round(temperature), code);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Whether a reading saved at savedAtMs may still be shown: younger than 2 hours (and not from the future). */
    static boolean fresh(long savedAtMs, long nowMs) {
        return nowMs >= savedAtMs && nowMs - savedAtMs < MAX_AGE_MS;
    }

    /** The wait before the next fetch: 30 minutes after a good one, 5 after a failure. */
    static long nextDelayMs(boolean lastFailed) {
        return lastFailed ? RETRY_MS : PERIOD_MS;
    }

    /**
     * The wait before the first fetch after a start, so coming back to Home does not fetch again early: what is left of
     * the plan since the last attempt (0 for none, or when overdue). A last attempt in the future (the clock was set
     * back) waits one full period.
     */
    static long firstDelayMs(long lastAttemptMs, boolean lastFailed, long nowMs) {
        if (lastAttemptMs <= 0) return 0;
        long period = nextDelayMs(lastFailed);
        long elapsed = nowMs - lastAttemptMs;
        if (elapsed < 0) return period;
        return Math.max(0, period - elapsed);
    }
}
