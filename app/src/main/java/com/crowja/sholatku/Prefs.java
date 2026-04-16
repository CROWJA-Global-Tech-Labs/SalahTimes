package com.crowja.sholatku;

import android.content.Context;
import android.content.SharedPreferences;

/** Centralized SharedPreferences wrapper for location + adzan settings. */
public final class Prefs {

    private static final String FILE = "salahtimes_prefs";

    public static final String KEY_LAT         = "user_lat";
    public static final String KEY_LON         = "user_lon";
    public static final String KEY_LOC_NAME    = "user_loc_name";
    public static final String KEY_ADZAN_URI   = "adzan_uri";

    // Per-prayer toggles (0=Fajr, 1=Dhuhr, 2=Asr, 3=Maghrib, 4=Isha)
    public static final String[] KEY_ADZAN_ON = {
            "adzan_on_0", "adzan_on_1", "adzan_on_2", "adzan_on_3", "adzan_on_4"
    };

    // Defaults: Jakarta (matches previous hardcoded values)
    public static final double DEFAULT_LAT  = -6.2088;
    public static final double DEFAULT_LON  = 106.8456;
    public static final String DEFAULT_NAME = "Jakarta";

    private Prefs() {}

    public static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static double getLat(Context ctx) {
        String s = get(ctx).getString(KEY_LAT, null);
        try { return s == null ? DEFAULT_LAT : Double.parseDouble(s); }
        catch (Exception e) { return DEFAULT_LAT; }
    }
    public static double getLon(Context ctx) {
        String s = get(ctx).getString(KEY_LON, null);
        try { return s == null ? DEFAULT_LON : Double.parseDouble(s); }
        catch (Exception e) { return DEFAULT_LON; }
    }
    public static String getLocationName(Context ctx) {
        return get(ctx).getString(KEY_LOC_NAME, DEFAULT_NAME);
    }
    public static void setLocation(Context ctx, double lat, double lon, String name) {
        get(ctx).edit()
                .putString(KEY_LAT, String.valueOf(lat))
                .putString(KEY_LON, String.valueOf(lon))
                .putString(KEY_LOC_NAME, name == null ? "" : name)
                .apply();
    }

    public static boolean isAdzanEnabled(Context ctx, int prayerIdx) {
        return get(ctx).getBoolean(KEY_ADZAN_ON[prayerIdx], false);
    }
    public static void setAdzanEnabled(Context ctx, int prayerIdx, boolean on) {
        get(ctx).edit().putBoolean(KEY_ADZAN_ON[prayerIdx], on).apply();
    }

    public static String getAdzanUri(Context ctx) {
        return get(ctx).getString(KEY_ADZAN_URI, null);
    }
    public static void setAdzanUri(Context ctx, String uri) {
        get(ctx).edit().putString(KEY_ADZAN_URI, uri).apply();
    }
}
