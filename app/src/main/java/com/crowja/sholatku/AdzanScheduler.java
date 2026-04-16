package com.crowja.sholatku;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Schedules per-prayer adzan alarms using AlarmManager.setAlarmClock,
 * which is the most reliable exact-alarm primitive on modern Android —
 * it bypasses Doze and does not require SCHEDULE_EXACT_ALARM permission.
 *
 * Each prayer has a stable request code (1000 + prayerIdx) so we can
 * cancel/replace its PendingIntent independently.
 */
public final class AdzanScheduler {

    public static final String ACTION_ADZAN = "com.crowja.sholatku.ACTION_ADZAN";
    public static final String EXTRA_PRAYER_IDX = "prayer_idx";
    private static final int REQUEST_BASE = 1000;

    private AdzanScheduler() {}

    /** Recomputes prayer times for today/tomorrow and schedules all enabled alarms. */
    public static void rescheduleAll(Context ctx) {
        double lat = Prefs.getLat(ctx);
        double lon = Prefs.getLon(ctx);
        TimeZone tz = TimeZone.getDefault();

        // Cancel all existing regardless of state, then re-add enabled ones.
        for (int i = 0; i < 5; i++) cancel(ctx, i);

        Calendar today = Calendar.getInstance();
        PrayerCalculator.Times t = PrayerCalculator.compute(lat, lon, tz, today);
        String[] arr = t.asArray();
        long now = System.currentTimeMillis();

        for (int i = 0; i < 5; i++) {
            if (!Prefs.isAdzanEnabled(ctx, i)) continue;
            long trigger = triggerMillis(arr[i], today);
            if (trigger <= now) {
                // Already passed today → schedule for tomorrow
                Calendar tomorrow = (Calendar) today.clone();
                tomorrow.add(Calendar.DAY_OF_YEAR, 1);
                PrayerCalculator.Times t2 = PrayerCalculator.compute(lat, lon, tz, tomorrow);
                trigger = triggerMillis(t2.asArray()[i], tomorrow);
            }
            schedule(ctx, i, trigger);
        }
    }

    public static void cancel(Context ctx, int prayerIdx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pendingIntent(ctx, prayerIdx));
    }

    private static void schedule(Context ctx, int prayerIdx, long triggerAtMillis) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pendingIntent(ctx, prayerIdx);
        try {
            // setAlarmClock is exact, Doze-exempt, and visible as an upcoming alarm.
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAtMillis, pi), pi);
        } catch (SecurityException se) {
            // Fallback for rare OEM restrictions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
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
}
