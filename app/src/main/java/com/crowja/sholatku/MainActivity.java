package com.crowja.sholatku;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.crowja.sholatku.databinding.ActivityMainBinding;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * SholatKu — prayer-times display.
 *
 * MVP uses fixed reference times (Jakarta / WIB). v1.1 swaps in an astronomical
 * calculation library (batoulapps/adhan-java).
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    // Jakarta (WIB) reference — MVP placeholder.
    private static final List<String[]> TIMES = Arrays.asList(
            new String[]{"Fajr",    "04:35"},
            new String[]{"Dhuhr",   "12:02"},
            new String[]{"Asr",     "15:20"},
            new String[]{"Maghrib", "18:05"},
            new String[]{"Isha",    "19:15"}
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        MobileAds.setRequestConfiguration(
                new RequestConfiguration.Builder()
                        .setTestDeviceIds(Collections.singletonList(AdRequest.DEVICE_ID_EMULATOR))
                        .build());
        MobileAds.initialize(this, initStatus -> {});
        binding.adView.loadAd(new AdRequest.Builder().build());

        renderPrayerTimes();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_privacy) {
            startActivity(new Intent(this, PrivacyActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void renderPrayerTimes() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault());
        binding.tvDate.setText(sdf.format(new Date()));

        StringBuilder sb = new StringBuilder();
        for (String[] row : TIMES) {
            sb.append(row[0]).append("   ").append(row[1]).append('\n');
        }
        binding.tvPrayerTimes.setText(sb.toString().trim());
        binding.tvNext.setText(getString(R.string.next_prayer_fmt, nextPrayer()));
    }

    private String nextPrayer() {
        Calendar now = Calendar.getInstance();
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        for (String[] row : TIMES) {
            String[] hm = row[1].split(":");
            int t = Integer.parseInt(hm[0]) * 60 + Integer.parseInt(hm[1]);
            if (t > nowMin) return row[0] + " (" + row[1] + ")";
        }
        return getString(R.string.fajr_tomorrow);
    }
}
