package com.crowja.sholatku;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/**
 * Fires when a scheduled adzan alarm goes off.
 *   - Posts a high-importance notification with the adzan audio as its
 *     sound (reliable even while the app is killed or in Doze).
 *   - Immediately re-schedules all alarms so the next day's times are queued.
 */
public class AdzanReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "adzan_channel";
    private static final int NOTIF_BASE = 2000;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            int idx = intent == null ? 0 : intent.getIntExtra(AdzanScheduler.EXTRA_PRAYER_IDX, 0);
            if (idx < 0 || idx > 4) idx = 0;

            createChannel(ctx);
            postNotification(ctx, idx);

            // Re-queue the next occurrence (same prayer, tomorrow) and refresh others.
            AdzanScheduler.rescheduleAll(ctx);
        } catch (Throwable ignored) {
            // Broadcast receivers must never throw — a crash here would block future alarms.
        }
    }

    private void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.adzan_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(ctx.getString(R.string.adzan_channel_desc));

        Uri sound = resolveAdzanUri(ctx);
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        ch.setSound(sound, aa);
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }

    private void postNotification(Context ctx, int prayerIdx) {
        int[] names = {
                R.string.prayer_fajr, R.string.prayer_dhuhr, R.string.prayer_asr,
                R.string.prayer_maghrib, R.string.prayer_isha
        };
        String title   = ctx.getString(R.string.adzan_title_fmt, ctx.getString(names[prayerIdx]));
        String content = ctx.getString(R.string.adzan_body);

        Intent open = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent tapPi = PendingIntent.getActivity(ctx, 3000 + prayerIdx, open, piFlags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(ctx, CHANNEL_ID);
        } else {
            //noinspection deprecation
            b = new Notification.Builder(ctx)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setSound(resolveAdzanUri(ctx), android.media.AudioManager.STREAM_ALARM);
        }
        b.setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(true)
                .setContentIntent(tapPi)
                .setCategory(Notification.CATEGORY_ALARM);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_BASE + prayerIdx, b.build());
    }

    private static Uri resolveAdzanUri(Context ctx) {
        String saved = Prefs.getAdzanUri(ctx);
        if (saved != null) {
            try { return Uri.parse(saved); } catch (Exception ignored) {}
        }
        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarm != null) return alarm;
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }
}
