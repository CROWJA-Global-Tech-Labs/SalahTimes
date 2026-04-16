package com.crowja.sholatku;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;

/**
 * User-facing settings:
 *   - Update location via GPS (fused → network → gps providers), or manual lat/lon.
 *   - Pick adzan sound via system RingtonePicker (any alarm/notification/music tone).
 * Persists into {@link Prefs} and re-schedules all enabled adzan alarms
 * on save so new coordinates immediately take effect.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_LOC_PERMISSION = 701;
    private static final int REQ_PICK_TONE      = 702;

    private EditText etLat, etLon, etName;
    private TextView tvToneName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings_title);
        }

        etLat     = findViewById(R.id.etLat);
        etLon     = findViewById(R.id.etLon);
        etName    = findViewById(R.id.etLocName);
        tvToneName = findViewById(R.id.tvToneName);

        etLat.setText(String.valueOf(Prefs.getLat(this)));
        etLon.setText(String.valueOf(Prefs.getLon(this)));
        etName.setText(Prefs.getLocationName(this));
        refreshToneLabel();

        findViewById(R.id.btnUseGps).setOnClickListener(v -> onUseGps());
        findViewById(R.id.btnPickTone).setOnClickListener(v -> onPickTone());
        findViewById(R.id.btnDefaultTone).setOnClickListener(v -> {
            Prefs.setAdzanUri(this, null);
            refreshToneLabel();
            Toast.makeText(this, R.string.tone_reset_default, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnSave).setOnClickListener(v -> onSave());
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    // ---- GPS flow ----

    private void onUseGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOC_PERMISSION);
            return;
        }
        requestLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        if (requestCode == REQ_LOC_PERMISSION) {
            for (int r : results) {
                if (r == PackageManager.PERMISSION_GRANTED) { requestLocation(); return; }
            }
            Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("MissingPermission")
    private void requestLocation() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) { Toast.makeText(this, R.string.gps_unavailable, Toast.LENGTH_LONG).show(); return; }

        // Try last-known from all providers first (instant, no wait).
        Location best = null;
        for (String p : lm.getProviders(true)) {
            Location l = lm.getLastKnownLocation(p);
            if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
        }
        if (best != null) { applyLocation(best); return; }

        // No cached fix → request a single update (limited to providers the user has enabled).
        Toast.makeText(this, R.string.locating, Toast.LENGTH_SHORT).show();
        final LocationListener[] holder = new LocationListener[1];
        LocationListener ll = new LocationListener() {
            @Override public void onLocationChanged(@NonNull Location location) {
                try { lm.removeUpdates(holder[0]); } catch (Exception ignored) {}
                applyLocation(location);
            }
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        };
        holder[0] = ll;
        boolean any = false;
        for (String p : lm.getProviders(true)) {
            try { lm.requestSingleUpdate(p, ll, getMainLooper()); any = true; }
            catch (Exception ignored) {}
        }
        if (!any) Toast.makeText(this, R.string.gps_unavailable, Toast.LENGTH_LONG).show();
    }

    private void applyLocation(Location loc) {
        etLat.setText(String.format(Locale.US, "%.6f", loc.getLatitude()));
        etLon.setText(String.format(Locale.US, "%.6f", loc.getLongitude()));
        // Best-effort reverse geocode for a human-readable name.
        try {
            Geocoder g = new Geocoder(this, Locale.getDefault());
            List<Address> addrs = g.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
            if (addrs != null && !addrs.isEmpty()) {
                Address a = addrs.get(0);
                String name = a.getLocality();
                if (TextUtils.isEmpty(name)) name = a.getSubAdminArea();
                if (TextUtils.isEmpty(name)) name = a.getAdminArea();
                if (!TextUtils.isEmpty(name)) etName.setText(name);
            }
        } catch (Exception ignored) { /* Geocoder offline/unavailable — leave name as is */ }
        Toast.makeText(this, R.string.location_updated, Toast.LENGTH_SHORT).show();
    }

    // ---- Tone picker ----

    private void onPickTone() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                        RingtoneManager.TYPE_ALARM | RingtoneManager.TYPE_NOTIFICATION)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.pick_tone_title))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        String saved = Prefs.getAdzanUri(this);
        if (saved != null) {
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(saved));
        }
        try { startActivityForResult(i, REQ_PICK_TONE); }
        catch (Exception e) { Toast.makeText(this, R.string.tone_picker_unavailable, Toast.LENGTH_LONG).show(); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_TONE && resultCode == RESULT_OK && data != null) {
            Uri u = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            Prefs.setAdzanUri(this, u == null ? null : u.toString());
            refreshToneLabel();
        }
    }

    private void refreshToneLabel() {
        String saved = Prefs.getAdzanUri(this);
        if (saved == null) {
            tvToneName.setText(R.string.tone_default);
            return;
        }
        try {
            android.media.Ringtone r = RingtoneManager.getRingtone(this, Uri.parse(saved));
            String title = r == null ? null : r.getTitle(this);
            tvToneName.setText(TextUtils.isEmpty(title) ? getString(R.string.tone_custom) : title);
        } catch (Exception e) { tvToneName.setText(R.string.tone_custom); }
    }

    // ---- Save ----

    private void onSave() {
        double lat, lon;
        try {
            lat = Double.parseDouble(etLat.getText().toString().trim());
            lon = Double.parseDouble(etLon.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.invalid_coords, Toast.LENGTH_LONG).show();
            return;
        }
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            Toast.makeText(this, R.string.invalid_coords_range, Toast.LENGTH_LONG).show();
            return;
        }
        String name = etName.getText().toString().trim();
        Prefs.setLocation(this, lat, lon, name);
        try { AdzanScheduler.rescheduleAll(this); } catch (Throwable ignored) {}
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}
