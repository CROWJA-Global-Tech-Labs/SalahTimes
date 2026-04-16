package com.crowja.sholatku;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * Live Qibla compass. Uses the rotation-vector sensor (fused magnetometer +
 * gyroscope + accelerometer) when available — otherwise falls back to the
 * classic accelerometer + magnetometer pair.
 *
 * The compass dial rotates so that the cardinal 'N' marker always points to
 * true north-ish (magnetic, not corrected for declination — good enough for
 * prayer-facing purposes). A green triangular indicator is drawn at the
 * Qibla bearing from the user's saved location (defaults to Jakarta).
 */
public class QiblaActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sm;
    private Sensor rotVec, accel, magnet;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation    = new float[3];
    private float[] lastAcc, lastMag;

    private CompassView compass;
    private TextView tvBearing, tvLocation;

    private double qiblaBearing = 295.0;  // default until coords loaded

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qibla);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.qibla_label);
        }
        compass   = findViewById(R.id.compassView);
        tvBearing = findViewById(R.id.tvBearing);
        tvLocation = findViewById(R.id.tvLocation);

        double lat = Prefs.getLat(this);
        double lon = Prefs.getLon(this);
        qiblaBearing = PrayerCalculator.qiblaBearing(lat, lon);
        compass.setQibla((float) qiblaBearing);
        tvBearing.setText(String.format(Locale.getDefault(),
                "%.0f° %s", qiblaBearing, PrayerCalculator.compassLabel(qiblaBearing)));
        tvLocation.setText(getString(R.string.qibla_from_fmt, Prefs.getLocationName(this)));

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm != null) {
            rotVec = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotVec == null) {
                accel  = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sm == null) return;
        if (rotVec != null) {
            sm.registerListener(this, rotVec, SensorManager.SENSOR_DELAY_UI);
        } else {
            if (accel  != null) sm.registerListener(this, accel,  SensorManager.SENSOR_DELAY_UI);
            if (magnet != null) sm.registerListener(this, magnet, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sm != null) sm.unregisterListener(this);
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float azimuthDeg = Float.NaN;

        if (e.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, e.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
            azimuthDeg = (float) Math.toDegrees(orientation[0]);
        } else {
            if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER)      lastAcc = e.values.clone();
            else if (e.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) lastMag = e.values.clone();
            if (lastAcc != null && lastMag != null) {
                float[] R = new float[9], I = new float[9];
                if (SensorManager.getRotationMatrix(R, I, lastAcc, lastMag)) {
                    SensorManager.getOrientation(R, orientation);
                    azimuthDeg = (float) Math.toDegrees(orientation[0]);
                }
            }
        }

        if (Float.isNaN(azimuthDeg)) return;

        // Adjust for device rotation (landscape, reverse-portrait, etc.)
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm != null && wm.getDefaultDisplay() != null) {
            int rot = wm.getDefaultDisplay().getRotation();
            int offset = 0;
            switch (rot) {
                case Surface.ROTATION_90:  offset =  90; break;
                case Surface.ROTATION_180: offset = 180; break;
                case Surface.ROTATION_270: offset = 270; break;
            }
            azimuthDeg += offset;
        }
        azimuthDeg = (azimuthDeg + 360f) % 360f;
        compass.setAzimuth(azimuthDeg);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
