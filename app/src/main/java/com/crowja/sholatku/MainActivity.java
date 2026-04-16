package com.crowja.sholatku;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.crowja.sholatku.databinding.ActivityMainBinding;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * SalahTimes — prayer-time display with live countdown, Hijri date,
 * per-prayer adzan toggles, and location-aware MWL calculation.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private static final int REQ_SETTINGS  = 101;
    private static final int REQ_POST_NOTIF = 102;

    private static final int[] PRAYER_NAMES = {
            R.string.prayer_fajr, R.string.prayer_dhuhr, R.string.prayer_asr,
            R.string.prayer_maghrib, R.string.prayer_isha
    };

    private String[] times = new String[5];

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            renderCountdown();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        MobileAds.setRequestConfiguration(new RequestConfiguration.Builder()
                .setTestDeviceIds(Collections.singletonList(AdRequest.DEVICE_ID_EMULATOR))
                .build());
        MobileAds.initialize(this, s -> {});
        binding.adView.loadAd(new AdRequest.Builder().build());

        binding.qiblaCard.setOnClickListener(v ->
                startActivity(new Intent(this, QiblaActivity.class)));

        recomputeTimes();
        renderDate();
        buildPrayerList();
        renderQibla();
        renderLocationNote();

        maybeRequestNotificationPermission();
        AdzanScheduler.rescheduleAll(this);
    }

    @Override protected void onResume() {
        super.onResume();
        // If user changed settings elsewhere, refresh on return.
        recomputeTimes();
        buildPrayerList();
        renderQibla();
        renderLocationNote();
        handler.post(tick);
    }

    @Override protected void onPause()  { super.onPause();  handler.removeCallbacks(tick); }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivityForResult(new Intent(this, SettingsActivity.class), REQ_SETTINGS);
            return true;
        }
        if (id == R.id.action_privacy) {
            startActivity(new Intent(this, PrivacyActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SETTINGS && resultCode == RESULT_OK) {
            recomputeTimes();
            buildPrayerList();
            renderQibla();
            renderLocationNote();
        }
    }

    // ---- Rendering ----

    private void recomputeTimes() {
        double lat = Prefs.getLat(this);
        double lon = Prefs.getLon(this);
        PrayerCalculator.Times t = PrayerCalculator.compute(
                lat, lon, TimeZone.getDefault(), Calendar.getInstance());
        times = t.asArray();
    }

    private void renderDate() {
        binding.tvDate.setText(new SimpleDateFormat("EEEE, dd MMM yyyy",
                Locale.getDefault()).format(new Date()));
        binding.tvHijri.setText(approximateHijri());
    }

    private void renderLocationNote() {
        String name = Prefs.getLocationName(this);
        double lat = Prefs.getLat(this);
        double lon = Prefs.getLon(this);
        String text = getString(R.string.location_fmt, name, lat, lon);
        binding.tvLocationNote.setText(text);
    }

    private void renderQibla() {
        double lat = Prefs.getLat(this);
        double lon = Prefs.getLon(this);
        double bearing = PrayerCalculator.qiblaBearing(lat, lon);
        String label = PrayerCalculator.compassLabel(bearing);
        binding.tvQibla.setText(String.format(Locale.getDefault(),
                "%.0f° %s", bearing, label));
    }

    private void buildPrayerList() {
        binding.prayerList.removeAllViews();
        int nextIdx = nextPrayerIndex();
        for (int i = 0; i < times.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);
            row.setBackground(ContextCompat.getDrawable(this,
                    i == nextIdx ? R.drawable.prayer_row_next : R.drawable.prayer_row_bg));

            TextView name = new TextView(this);
            name.setText(getString(PRAYER_NAMES[i]));
            name.setTextSize(16);
            name.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(name, np);

            TextView time = new TextView(this);
            time.setText(times[i]);
            time.setTextSize(18);
            time.setTypeface(time.getTypeface(), android.graphics.Typeface.BOLD);
            time.setTextColor(ContextCompat.getColor(this,
                    i == nextIdx ? R.color.brand_primary_dark : R.color.text_primary));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.rightMargin = dp(12);
            row.addView(time, tp);

            SwitchCompat sw = new SwitchCompat(this);
            sw.setChecked(Prefs.isAdzanEnabled(this, i));
            sw.setContentDescription(getString(R.string.adzan_switch_desc,
                    getString(PRAYER_NAMES[i])));
            sw.setOnCheckedChangeListener((CompoundButton b, boolean on) -> {
                Prefs.setAdzanEnabled(this, idx, on);
                AdzanScheduler.rescheduleAll(this);
                Toast.makeText(this,
                        getString(on ? R.string.adzan_on_toast : R.string.adzan_off_toast,
                                getString(PRAYER_NAMES[idx])),
                        Toast.LENGTH_SHORT).show();
            });
            row.addView(sw);

            binding.prayerList.addView(row);
        }
    }

    private void renderCountdown() {
        int idx = nextPrayerIndex();
        Calendar now = Calendar.getInstance();
        long target;
        String name, time;
        if (idx >= 0) {
            name = getString(PRAYER_NAMES[idx]);
            time = times[idx];
            target = minutesOf(time) * 60L * 1000L;
            long nowMs = (now.get(Calendar.HOUR_OF_DAY) * 3600L
                    + now.get(Calendar.MINUTE) * 60L
                    + now.get(Calendar.SECOND)) * 1000L;
            long diffMs = target - nowMs;
            setCountdown(name, time, diffMs);
        } else {
            // After Isha → show Fajr tomorrow (recomputed for the next day's coords)
            Calendar tomorrow = (Calendar) now.clone();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);
            PrayerCalculator.Times t2 = PrayerCalculator.compute(
                    Prefs.getLat(this), Prefs.getLon(this), TimeZone.getDefault(), tomorrow);
            String fajr = t2.asArray()[0];
            long nowMs = (now.get(Calendar.HOUR_OF_DAY) * 3600L
                    + now.get(Calendar.MINUTE) * 60L
                    + now.get(Calendar.SECOND)) * 1000L;
            long diffMs = (24L * 3600 * 1000 - nowMs) + minutesOf(fajr) * 60L * 1000L;
            setCountdown(getString(R.string.fajr_tomorrow), fajr, diffMs);
        }
    }

    private void setCountdown(String name, String time, long diffMs) {
        binding.tvNextName.setText(name);
        binding.tvNextTime.setText(time);
        long s = Math.max(0, diffMs / 1000);
        long hh = s / 3600, mm = (s % 3600) / 60, ss = s % 60;
        binding.tvCountdown.setText(getString(R.string.countdown_fmt, hh, mm, ss));
    }

    private int nextPrayerIndex() {
        Calendar now = Calendar.getInstance();
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        for (int i = 0; i < times.length; i++) {
            if (minutesOf(times[i]) > nowMin) return i;
        }
        return -1;
    }

    private static int minutesOf(String hhmm) {
        String[] hm = hhmm.split(":");
        return Integer.parseInt(hm[0]) * 60 + Integer.parseInt(hm[1]);
    }

    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }

    private void maybeRequestNotificationPermission() {
        // POST_NOTIFICATIONS is required on API 33+ for adzan notifications to surface.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIF);
            }
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Pre-create channel so the user sees it in system settings even before first alarm.
            new AdzanReceiver(); // noop; channel is created lazily in onReceive, but we mirror here:
        }
    }

    // ---- Hijri (unchanged Kuwaiti tabular) ----

    private String approximateHijri() {
        Calendar g = Calendar.getInstance();
        int y = g.get(Calendar.YEAR);
        int m = g.get(Calendar.MONTH) + 1;
        int d = g.get(Calendar.DAY_OF_MONTH);
        long jd = gregorianToJd(y, m, d);
        long islamicDay = jd - 1948440 + 10632;
        long n = (islamicDay - 1) / 10631;
        islamicDay = islamicDay - 10631 * n + 354;
        long j = ((10985 - islamicDay) / 5316) * ((50 * islamicDay) / 17719)
                + (islamicDay / 5670) * ((43 * islamicDay) / 15238);
        islamicDay = islamicDay - ((30 - j) / 15) * ((17719 * j) / 50)
                - (j / 16) * ((15238 * j) / 43) + 29;
        int iMonth = (int) ((24 * islamicDay) / 709);
        int iDay = (int) (islamicDay - (709 * iMonth) / 24);
        int iYear = (int) (30 * n + j - 30);
        String[] months = {"Muharram","Safar","Rabi' I","Rabi' II","Jumada I","Jumada II",
                "Rajab","Sha'ban","Ramadan","Shawwal","Dhul Qa'dah","Dhul Hijjah"};
        int mi = Math.max(1, Math.min(12, iMonth)) - 1;
        return iDay + " " + months[mi] + " " + iYear + " AH";
    }

    private static long gregorianToJd(int y, int m, int d) {
        if (m <= 2) { y--; m += 12; }
        long a = y / 100;
        long b = 2 - a + a / 4;
        return (long) Math.floor(365.25 * (y + 4716))
                + (long) Math.floor(30.6001 * (m + 1)) + d + b - 1524;
    }
}
