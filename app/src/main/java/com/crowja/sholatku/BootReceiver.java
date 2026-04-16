package com.crowja.sholatku;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * AlarmManager alarms are cleared on device reboot / APK replace.
 * Re-arm them automatically so the user doesn't have to open the app.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)) {
            try {
                AdzanScheduler.rescheduleAll(ctx);
            } catch (Throwable ignored) {
                // Never let a scheduling failure crash a receiver — a crash here
                // on MY_PACKAGE_REPLACED would block the app from launching.
            }
        }
    }
}
