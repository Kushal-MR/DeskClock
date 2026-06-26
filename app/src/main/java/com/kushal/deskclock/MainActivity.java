package com.kushal.deskclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // ─── Weather ──────────────────────────────────────────────────────────────
    // Open-Meteo free API — no API key required.
    // Bengaluru coordinates: 12.9716 N, 77.5946 E
    private static final String WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=12.9716&longitude=77.5946"
                    + "&current_weather=true"
                    + "&temperature_unit=celsius";      // always fetch °C, display as-is
    private static final long WEATHER_REFRESH_MS = 30 * 60_000L; // 30 minutes

    private TextView tempView;
    private Handler weatherHandler;
    private Runnable weatherRunnable;
    private ExecutorService weatherExecutor;

    // ─── Battery ──────────────────────────────────────────────────────────────
    private TextView batteryIndicator;
    private BroadcastReceiver batteryReceiver;

    // ─── Ambient light sensor ─────────────────────────────────────────────────
    // Below LUX_DIM_THRESHOLD → screen dims to DIM_BRIGHTNESS.
    // We add a small hysteresis gap so the screen doesn't flicker at the boundary.
    private static final float LUX_DIM_THRESHOLD   = 5f;
    private static final float LUX_BRIGHT_THRESHOLD = 10f;
    private static final float DIM_BRIGHTNESS      = 0.04f;
    private static final float NORMAL_BRIGHTNESS   = 1.0f;
    private boolean isDimmed = false;

    private SensorManager sensorManager;
    private Sensor lightSensor;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Immersive fullscreen ──────────────────────────────────────────────
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsCtrl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsCtrl != null) {
            insetsCtrl.hide(WindowInsetsCompat.Type.systemBars());
            insetsCtrl.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        // ── Keep screen on permanently ────────────────────────────────────────
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        // Suppress insets padding — we want edge-to-edge black
        View rootView = findViewById(R.id.root_container);
        rootView.setOnApplyWindowInsetsListener((v, insets) -> insets);

        // ── Battery ───────────────────────────────────────────────────────────
        batteryIndicator = findViewById(R.id.batteryIndicator);
        setupBatteryReceiver();

        // ── Light sensor ──────────────────────────────────────────────────────
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        // ── Weather ───────────────────────────────────────────────────────────
        tempView = findViewById(R.id.tempView);
        weatherExecutor = Executors.newSingleThreadExecutor();
        weatherHandler = new Handler(Looper.getMainLooper());
        setupWeatherRunnable();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    protected void onResume() {
        super.onResume();

        // Battery — sticky broadcast, fire immediately
        if (batteryReceiver != null) {
            registerReceiver(
                    batteryReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    Context.RECEIVER_NOT_EXPORTED);
        }

        // Light sensor
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }

        // Weather — fetch now, then every 30 minutes
        fetchWeatherAsync();
        weatherHandler.postDelayed(weatherRunnable, WEATHER_REFRESH_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException ignored) {
                // Was not registered (edge case on quick onCreate→onPause)
            }
        }

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        weatherHandler.removeCallbacks(weatherRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (weatherExecutor != null && !weatherExecutor.isShutdown()) {
            weatherExecutor.shutdownNow();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Battery
    // ═════════════════════════════════════════════════════════════════════════

    private void setupBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (batteryIndicator == null) return;

                int level  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale  = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

                // EXTRA_LEVEL is raw; divide by scale for the real percentage
                int percent = (scale > 0) ? Math.round(level * 100f / scale) : level;

                boolean isCharging =
                        status == BatteryManager.BATTERY_STATUS_CHARGING
                                || status == BatteryManager.BATTERY_STATUS_FULL;

                String text = percent + "%";
                if (isCharging) {
                    text += "  ⚡";
                    // Subtle colour shift when plugged in — warm amber glow
                    batteryIndicator.setTextColor(0xFFFF9D6B);
                } else {
                    batteryIndicator.setTextColor(0xFF5C3010);
                }
                batteryIndicator.setText(text);
            }
        };
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Ambient light sensor  →  adaptive brightness
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_LIGHT) return;

        float lux = event.values[0];
        WindowManager.LayoutParams lp = getWindow().getAttributes();

        if (!isDimmed && lux < LUX_DIM_THRESHOLD) {
            lp.screenBrightness = DIM_BRIGHTNESS;
            getWindow().setAttributes(lp);
            isDimmed = true;
        } else if (isDimmed && lux > LUX_BRIGHT_THRESHOLD) {
            lp.screenBrightness = NORMAL_BRIGHTNESS;
            getWindow().setAttributes(lp);
            isDimmed = false;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Weather  —  Open-Meteo, no API key
    // ═════════════════════════════════════════════════════════════════════════

    private void setupWeatherRunnable() {
        weatherRunnable = new Runnable() {
            @Override
            public void run() {
                fetchWeatherAsync();
                weatherHandler.postDelayed(this, WEATHER_REFRESH_MS);
            }
        };
    }

    /**
     * Dispatches an HTTP GET to Open-Meteo on a background thread,
     * parses current_weather.temperature (°C), and posts the result
     * back to the main thread. Never touches the UI from the background.
     */
    private void fetchWeatherAsync() {
        if (weatherExecutor == null || weatherExecutor.isShutdown()) return;

        weatherExecutor.execute(() -> {
            String displayText = null;

            try {
                URL url = new URL(WEATHER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8_000);
                conn.setReadTimeout(8_000);
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader =
                            new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject root = new JSONObject(sb.toString());
                    JSONObject cw   = root.getJSONObject("current_weather");
                    double tempC    = cw.getDouble("temperature");

                    // Round to nearest integer for clean display
                    int rounded = (int) Math.round(tempC);
                    displayText = rounded + "°C";
                }
                conn.disconnect();

            } catch (Exception e) {
                // Network unavailable or parse error — keep previous value
                e.printStackTrace();
            }

            // Only post UI update if we actually got a value
            if (displayText != null) {
                final String finalText = displayText;
                weatherHandler.post(() -> {
                    if (tempView != null) {
                        tempView.setText(finalText);
                    }
                });
            }
        });
    }
}