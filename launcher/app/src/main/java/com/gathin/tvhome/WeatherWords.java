package com.gathin.tvhome;

import android.content.res.Resources;

/**
 * The weather line's word for a WMO weather code (Open-Meteo's weather_code, spec § 4.10c): one of the launcher's own
 * strings, wmo_clear to wmo_thunder, and wmo_unknown ("—") for a code outside the table.
 */
final class WeatherWords {
    private WeatherWords() {}

    /** The string name for this code: 0 clear, 1–2 fair, 3 cloudy, 45/48 fog, 51–57 drizzle, and so on. */
    static String key(int wmoCode) {
        int c = wmoCode;
        if (c == 0) return "wmo_clear";
        if (c == 1 || c == 2) return "wmo_fair";
        if (c == 3) return "wmo_cloudy";
        if (c == 45 || c == 48) return "wmo_fog";
        if (c >= 51 && c <= 57) return "wmo_drizzle";
        if (c >= 61 && c <= 67) return "wmo_rain";
        if (c >= 71 && c <= 77) return "wmo_snow";
        if (c >= 80 && c <= 82) return "wmo_showers";
        if (c >= 85 && c <= 86) return "wmo_snow_showers";
        if (c >= 95 && c <= 99) return "wmo_thunder";
        return "wmo_unknown";
    }

    /**
     * The string resource for a key from {@link #key}. Looked up by name; the names are literals in this class, which
     * keeps them through resource shrinking. Falls back to wmo_unknown.
     */
    static int stringRes(Resources res, String key) {
        int id = res.getIdentifier(key, "string", res.getResourcePackageName(R.string.wmo_unknown));
        return id != 0 ? id : R.string.wmo_unknown;
    }
}
