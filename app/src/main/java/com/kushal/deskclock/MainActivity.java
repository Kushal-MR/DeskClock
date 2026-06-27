package com.kushal.deskclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Multi-Face Smart Display — host activity.
 *
 * This activity owns all global background services:
 *   • Open-Meteo weather API polling
 *   • Battery state BroadcastReceiver
 *   • Ambient light sensor → adaptive brightness
 *
 * The UI is a ViewPager2 that hosts two Clock Face fragments.
 * Each fragment implements {@link ClockFaceContract} to receive
 * battery and weather data downstream from these services.
 *
 * Face selection persists in SharedPreferences so the app
 * always launches into the user's preferred aesthetic.
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // ─── Weather ──────────────────────────────────────────────────────────────
    private static final String WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=12.9716&longitude=77.5946"
                    + "&current_weather=true"
                    + "&temperature_unit=celsius";
    private static final long WEATHER_REFRESH_MS = 30 * 60_000L; // 30 minutes

    private Handler weatherHandler;
    private Runnable weatherRunnable;
    private ExecutorService weatherExecutor;
    private String lastWeatherText;    // cached for pushing to new fragments

    // ─── Battery ──────────────────────────────────────────────────────────────
    private BroadcastReceiver batteryReceiver;
    private int lastBatteryPercent = -1;
    private boolean lastBatteryCharging = false;

    // ─── Ambient light sensor ─────────────────────────────────────────────────
    private static final float LUX_DIM_THRESHOLD    = 5f;
    private static final float LUX_BRIGHT_THRESHOLD = 10f;
    private static final float DIM_BRIGHTNESS       = 0.04f;
    private static final float NORMAL_BRIGHTNESS    = 1.0f;
    private boolean isDimmed = false;

    private SensorManager sensorManager;
    private Sensor lightSensor;

    // ─── ViewPager2 / Multi-Face ──────────────────────────────────────────────
    private static final String PREFS_NAME = "deskclock_prefs";
    private static final String KEY_FACE   = "selected_face";

    private ViewPager2 faceViewPager;
    private ClockFacePagerAdapter pagerAdapter;

    private View dot0;
    private View dot1;
    private View dot2;
    private BroadcastReceiver spotifyReceiver;
    private String lastSpotifyTrack = "";
    private String lastSpotifyArtist = "";

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

        // ── ViewPager2 setup ──────────────────────────────────────────────────
        faceViewPager = findViewById(R.id.faceViewPager);
        pagerAdapter  = new ClockFacePagerAdapter(this);
        faceViewPager.setAdapter(pagerAdapter);

        // Keep all 3 fragments alive at all times so data can be pushed to them
        faceViewPager.setOffscreenPageLimit(2);

        // Restore last-selected face
        int savedFace = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_FACE, 0);
        faceViewPager.setCurrentItem(savedFace, false);

        // Track page changes → persist + update dots
        faceViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                saveFace(position);
                updateDots(position);
            }
        });

        // ── Dot indicators ────────────────────────────────────────────────────
        dot0 = findViewById(R.id.dot0);
        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        setupDots();
        updateDots(savedFace);

        // ── Battery & Spotify ──────────────────────────────────────────────────
        setupBatteryReceiver();
        setupSpotifyReceiver();

        // ── Light sensor ──────────────────────────────────────────────────────
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        // ── Weather ───────────────────────────────────────────────────────────
        weatherExecutor = Executors.newSingleThreadExecutor();
        weatherHandler  = new Handler(Looper.getMainLooper());
        setupWeatherRunnable();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    protected void onResume() {
        super.onResume();

        // Battery — sticky broadcast, fires immediately with current state
        if (batteryReceiver != null) {
            registerReceiver(
                    batteryReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    Context.RECEIVER_NOT_EXPORTED);
        }
        
        if (spotifyReceiver != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.spotify.music.metadatachanged");
            filter.addAction("com.spotify.music.playbackstatechanged");
            registerReceiver(spotifyReceiver, filter, Context.RECEIVER_EXPORTED);
        }

        // Register the NotificationListener callback for MediaSession-based detection
        SpotifyNotificationListener.setMetadataCallback((track, artist, album, isPlaying, albumArt) -> {
            if (track != null && artist != null) {
                if (!track.equals(lastSpotifyTrack) || !artist.equals(lastSpotifyArtist)) {
                    lastSpotifyTrack = track;
                    lastSpotifyArtist = artist;

                    if (isPlaying && faceViewPager != null && faceViewPager.getCurrentItem() != 2) {
                        faceViewPager.setCurrentItem(2, true);
                    }
                }

                List<Fragment> fragments = getSupportFragmentManager().getFragments();
                for (Fragment f : fragments) {
                    if (f instanceof ClockFaceContract && f.isAdded()) {
                        ((ClockFaceContract) f).onSpotifyUpdate(track, artist, album, isPlaying, albumArt);
                    }
                }
            }
        });

        // Check if notification listener permission is granted
        checkNotificationListenerPermission();

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
        
        if (spotifyReceiver != null) {
            try {
                unregisterReceiver(spotifyReceiver);
            } catch (IllegalArgumentException ignored) {}
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
    // Multi-Face: ViewPager2 Controls
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Called by fragments to disable/re-enable swipe navigation
     * (e.g. when the theme picker bottom sheet is open).
     */
    public void setSwipeEnabled(boolean enabled) {
        if (faceViewPager != null) {
            faceViewPager.setUserInputEnabled(enabled);
        }
    }

    private void saveFace(int position) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putInt(KEY_FACE, position).apply();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Dot Indicators
    // ═════════════════════════════════════════════════════════════════════════

    private void setupDots() {
        setDotDrawable(dot0, 0x88FFFFFF);
        setDotDrawable(dot1, 0x44FFFFFF);
        setDotDrawable(dot2, 0x44FFFFFF);
    }

    private void updateDots(int activePosition) {
        setDotDrawable(dot0, activePosition == 0 ? 0xBBFFFFFF : 0x44FFFFFF);
        setDotDrawable(dot1, activePosition == 1 ? 0xBBFFFFFF : 0x44FFFFFF);
        setDotDrawable(dot2, activePosition == 2 ? 0xBBFFFFFF : 0x44FFFFFF);
    }

    private void setDotDrawable(View dot, int color) {
        if (dot == null) return;
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        dot.setBackground(d);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Data Forwarding to Clock Face Fragments
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Push data to all Clock Face fragments that implement ClockFaceContract.
     * Called from battery receiver and weather callback.
     */
    private void forwardBatteryToFaces(int percent, boolean isCharging) {
        lastBatteryPercent   = percent;
        lastBatteryCharging  = isCharging;

        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment f : fragments) {
            if (f instanceof ClockFaceContract && f.isAdded()) {
                ((ClockFaceContract) f).onBatteryUpdate(percent, isCharging);
            }
        }
    }

    private void forwardWeatherToFaces(String tempText) {
        lastWeatherText = tempText;

        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment f : fragments) {
            if (f instanceof ClockFaceContract && f.isAdded()) {
                ((ClockFaceContract) f).onWeatherUpdate(tempText);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Battery
    // ═════════════════════════════════════════════════════════════════════════

    private void setupBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale  = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

                int percent = (scale > 0) ? Math.round(level * 100f / scale) : level;

                boolean isCharging =
                        status == BatteryManager.BATTERY_STATUS_CHARGING
                                || status == BatteryManager.BATTERY_STATUS_FULL;

                forwardBatteryToFaces(percent, isCharging);
            }
        };
    }
    
    private void setupSpotifyReceiver() {
        spotifyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;
                
                String track = intent.getStringExtra("track");
                String artist = intent.getStringExtra("artist");
                String album = intent.getStringExtra("album");
                boolean isPlaying = intent.getBooleanExtra("playing", false);
                
                if (track != null && artist != null) {
                    if (!track.equals(lastSpotifyTrack) || !artist.equals(lastSpotifyArtist)) {
                        lastSpotifyTrack = track;
                        lastSpotifyArtist = artist;
                        
                        // If a new track starts playing, automatically switch to the Spotify face
                        if (isPlaying && faceViewPager != null && faceViewPager.getCurrentItem() != 2) {
                            faceViewPager.setCurrentItem(2, true);
                        }
                    }
                    
                    List<Fragment> fragments = getSupportFragmentManager().getFragments();
                    for (Fragment f : fragments) {
                        if (f instanceof ClockFaceContract && f.isAdded()) {
                            ((ClockFaceContract) f).onSpotifyUpdate(track, artist, album, isPlaying, null);
                        }
                    }
                }
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

                    int rounded = (int) Math.round(tempC);
                    displayText = rounded + "°C";
                }
                conn.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
            }

            if (displayText != null) {
                final String finalText = displayText;
                weatherHandler.post(() -> forwardWeatherToFaces(finalText));
            }
        });
    }

    /**
     * Checks if the app has Notification Listener permission.
     * If not, prompts the user to enable it in Settings (required for
     * automatic Spotify track detection via MediaSession).
     */
    private void checkNotificationListenerPermission() {
        String enabledListeners = android.provider.Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        if (enabledListeners == null || !enabledListeners.contains(getPackageName())) {
            // Only prompt once per app install — check a preference flag
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            if (!prefs.getBoolean("notification_permission_asked", false)) {
                prefs.edit().putBoolean("notification_permission_asked", true).apply();
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Enable Auto Spotify Detection")
                        .setMessage("To automatically detect songs playing on your other devices, " +
                                "please grant Notification Access to DeskClock.\n\n" +
                                "This lets the app read Spotify's media session.")
                        .setPositiveButton("Open Settings", (d, w) -> {
                            startActivity(new Intent(
                                    "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                        })
                        .setNegativeButton("Later", null)
                        .show();
            }
        }
    }
}