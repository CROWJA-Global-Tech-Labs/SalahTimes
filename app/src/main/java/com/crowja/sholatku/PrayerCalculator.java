package com.crowja.sholatku;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Minimal astronomical prayer-time calculator using the
 * Muslim World League (MWL) convention:
 *   Fajr angle  = 18°
 *   Isha angle  = 17°
 *   Asr factor  = 1 (Shafi school — shadow length = object height + noon shadow)
 *
 * All math runs locally — no network, no external deps.
 * Results are Strings in "HH:mm" 24-hour format, localized-timezone.
 */
public final class PrayerCalculator {

    public static final class Times {
        public final String fajr, dhuhr, asr, maghrib, isha;
        Times(String f, String d, String a, String m, String i) {
            this.fajr = f; this.dhuhr = d; this.asr = a; this.maghrib = m; this.isha = i;
        }
        public String[] asArray() { return new String[]{fajr, dhuhr, asr, maghrib, isha}; }
    }

    private PrayerCalculator() {}

    public static Times compute(double latDeg, double lonDeg, TimeZone tz, Calendar date) {
        int y = date.get(Calendar.YEAR);
        int m = date.get(Calendar.MONTH) + 1;
        int d = date.get(Calendar.DAY_OF_MONTH);
        double jd = julianDay(y, m, d) - lonDeg / (15.0 * 24.0);

        double[] sp = sunPosition(jd);
        double decl = sp[0];   // solar declination (degrees)
        double eqt  = sp[1];   // equation of time (minutes)

        double tzHours = tz.getOffset(date.getTimeInMillis()) / 3600000.0;
        double dhuhr   = 12.0 + tzHours - lonDeg / 15.0 - eqt / 60.0;

        double fajr    = dhuhr - timeForAngle(18.0, latDeg, decl);
        double asr     = dhuhr + asrTime(1, latDeg, decl);
        double maghrib = dhuhr + timeForAngle(0.833, latDeg, decl);
        double isha    = dhuhr + timeForAngle(17.0, latDeg, decl);

        return new Times(
                fmt(fajr), fmt(dhuhr), fmt(asr), fmt(maghrib), fmt(isha));
    }

    // ---- astronomical helpers ----

    private static double julianDay(int y, int m, int d) {
        if (m <= 2) { y--; m += 12; }
        double A = Math.floor(y / 100.0);
        double B = 2 - A + Math.floor(A / 4.0);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + B - 1524.5;
    }

    /** Returns [declination°, equationOfTime_minutes] at Julian Day jd. */
    private static double[] sunPosition(double jd) {
        double D = jd - 2451545.0;
        double g = norm360(357.529 + 0.98560028 * D);
        double q = norm360(280.459 + 0.98564736 * D);
        double L = norm360(q + 1.915 * sin(g) + 0.020 * sin(2 * g));
        double e = 23.439 - 0.00000036 * D;
        double RA = Math.toDegrees(Math.atan2(cos(e) * sin(L), cos(L))) / 15.0;
        RA = fixHour(RA);
        double decl = Math.toDegrees(Math.asin(sin(e) * sin(L)));
        double eqt  = (q / 15.0 - RA) * 60.0;
        return new double[]{decl, eqt};
    }

    private static double timeForAngle(double angleDeg, double latDeg, double declDeg) {
        double a = -sin(angleDeg) - sin(latDeg) * sin(declDeg);
        double b = cos(latDeg) * cos(declDeg);
        double x = a / b;
        if (x < -1) x = -1; else if (x > 1) x = 1;   // high-latitude safety clamp
        return Math.toDegrees(Math.acos(x)) / 15.0;
    }

    private static double asrTime(int factor, double latDeg, double declDeg) {
        double t = Math.atan(1.0 / (factor + Math.tan(Math.toRadians(Math.abs(latDeg - declDeg)))));
        double a = -Math.sin(t) - sin(latDeg) * sin(declDeg);
        double b = cos(latDeg) * cos(declDeg);
        double x = a / b;
        if (x < -1) x = -1; else if (x > 1) x = 1;
        return Math.toDegrees(Math.acos(x)) / 15.0;
    }

    /** Great-circle bearing from (lat, lon) to the Kaaba (21.4225°N, 39.8262°E). */
    public static double qiblaBearing(double latDeg, double lonDeg) {
        double kLat = 21.4225, kLon = 39.8262;
        double dLon = Math.toRadians(kLon - lonDeg);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(kLat));
        double x = Math.cos(Math.toRadians(latDeg)) * Math.sin(Math.toRadians(kLat))
                - Math.sin(Math.toRadians(latDeg)) * Math.cos(Math.toRadians(kLat)) * Math.cos(dLon);
        double b = Math.toDegrees(Math.atan2(y, x));
        return (b + 360.0) % 360.0;
    }

    public static String compassLabel(double bearing) {
        String[] dirs = {"N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW"};
        return dirs[(int) Math.round(bearing / 22.5) % 16];
    }

    // ---- formatting / math sugar ----

    private static double norm360(double x) { x = x - 360.0 * Math.floor(x / 360.0); return x < 0 ? x + 360.0 : x; }
    private static double fixHour(double x)  { x = x - 24.0 * Math.floor(x / 24.0); return x < 0 ? x + 24.0 : x; }
    private static double sin(double deg)   { return Math.sin(Math.toRadians(deg)); }
    private static double cos(double deg)   { return Math.cos(Math.toRadians(deg)); }

    private static String fmt(double hours) {
        double h = fixHour(hours + 0.5 / 60.0);              // round to nearest minute
        int hh = (int) Math.floor(h);
        int mm = (int) Math.floor((h - hh) * 60.0);
        if (mm == 60) { mm = 0; hh = (hh + 1) % 24; }
        return String.format(java.util.Locale.US, "%02d:%02d", hh, mm);
    }
}
