package com.crowja.sholatku;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Schedules per-prayer adzan alarms using AlarmManager. Tries the most
 * precise primitive first and silently degrades if the OS denies it —
 * we NEVER let an alarm-scheduling failure propagate, because this
 * code runs inside BroadcastReceivers (boot / package-replaced) where
 * an uncaught exception would crash the whole app on launch.
 *
 * Fallback chain:
 *   1. setAlarmClock                          — exact, Doze-exempt, visible in alarm-clock UI
 *   2. setExactAndAllowWhileIdle  (API 23+)   — exact, requires USE_EXACT_ALARM or SCHEDULE_EXACT_ALARM
 *   3. set                                    — inexact, always allowed; may fire ±few minutes late
 */
public final class AdzanScheduler {

    private static final String TAG = "AdzanScheduler";

    public static final String ACTION_ADZAN = "com.crowja.sholatku.ACTION_ADZAN";
    public static final String EXTRA_PRAYER_IDX = "prayer_idx";
    private static final int REQUEST_BASE = 1000;

    private AdzanScheduler() {}

    /** Recomputes prayer times for today/tomorrow and schedules all enabled alarms. */
    public static void rescheduleAll(Context ctx) {
        try {
            double lat = Prefs.getLat(ctx);
            double lon = Prefs.getLon(ctx);
            TimeZone tz = TimeZone.getDefault();

            // Always cancel first so disabled prayers don't linger.
            for (int i = 0; i < 5; i++) safeCancel(ctx, i);

            Calendar today = Calendar.getInstance();
            PrayerCalculator.Times t = PrayerCalculator.compute(lat, lon, tz, today);
            String[] arr = t.asArray();
            long now = System.currentTimeMillis();

            for (int i = 0; i < 5; i++) {
                if (!Prefs.isAdzanEnabled(ctx, i)) continue;
                long trigger = triggerMillis(arr[i], today);
                if (trigger <= now) {
                    Calendar tomorrow = (Calendar) today.clone();
                    tomorrow.add(Calendar.DAY_OF_YEAR, 1);
                    PrayerCalculator.Times t2 = PrayerCalculator.compute(lat, lon, tz, tomorrow);
                    trigger = triggerMillis(t2.asArray()[i], tomorrow);
                }
                safeSchedule(ctx, i, trigger);
            }
        } catch (Throwable t) {
            // Any unexpected failure (prefs, calculator edge-case, etc.) must never crash a broadcast.
            Log.w(TAG, "rescheduleAll failed: " + t.getMessage(), t);
        }
    }

    private static void safeCancel(Context ctx, int prayerIdx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            am.cancel(pendingIntent(ctx, prayerIdx));
        } catch (Throwable t) { Log.w(TAG, "cancel " + prayerIdx + " failed", t); }
    }

    private static void safeSchedule(Context ctx, int prayerIdx, long triggerAtMillis) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pendingIntent(ctx, prayerIdx);

        // Try 1: setAlarmClock — most reliable, usually doesn't need exact-alarm perm.
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAtMillis, pi), pi);
            return;
        } catch (Throwable t) {
            Log.w(TAG, "setAlarmClock denied for " + prayerIdx + ": " + t.getMessage());
        }

        // Try 2: exact + allow-while-idle. On API 31+ requires canScheduleExactAlarms().
        try {
            boolean canExact = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                canExact = am.canScheduleExactAlarms();
            }
            if (canExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
                }
                return;
            }
        } catch (Throwable t) {
            Log.w(TAG, "setExact denied for " + prayerIdx + ": " + t.getMessage());
        }

        // Try 3: inexact — always allowed. May fire a few minutes late, but better than nothing.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
        } catch (Throwable t) {
            Log.w(TAG, "inexact set failed for " + prayerIdx + ": " + t.getMessage());
        }
    }

    private static PendingIntent pendingIntent(Context ctx, int prayerIdx) {
        Intent i = new Intent(ctx, AdzanReceiver.class)
                .setAction(ACTION_ADZAN)
                .putExtra(EXTRA_PRAYER_IDX, prayerIdx);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, REQUEST_BASE + prayerIdx, i, flags);
    }

    private static long triggerMillis(String hhmm, Calendar baseDay) {
        String[] p = hhmm.split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        Calendar c = (Calendar) baseDay.clone();
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, m);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Public cancel for the Settings/toggle call sites (unchanged API). */
    public static void cancel(Context ctx, int prayerIdx) { safeCancel(ctx, prayerIdx); }
}
