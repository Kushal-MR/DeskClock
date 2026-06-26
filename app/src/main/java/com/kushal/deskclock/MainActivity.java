package com.kushal.deskclock;

import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
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
                    + "&temperature_unit=celsius";
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
    // Hysteresis gap prevents flickering at the boundary.
    private static final float LUX_DIM_THRESHOLD    = 5f;
    private static final float LUX_BRIGHT_THRESHOLD = 10f;
    private static final float DIM_BRIGHTNESS       = 0.04f;
    private static final float NORMAL_BRIGHTNESS    = 1.0f;
    private boolean isDimmed = false;

    private SensorManager sensorManager;
    private Sensor lightSensor;

    // ─── Theme ────────────────────────────────────────────────────────────────
    private int currentTheme = ThemeManager.THEME_NEON_BLAZE;
    private boolean pickerVisible = false;

    // Cached view references — bound once in onCreate for fast theme switching
    private TextView textClockTime;
    private TextView textClockAmPm;
    private TextView textClockDayName;
    private TextView textClockDate;
    private TextView textClockMonth;
    private TextView locationLabel;
    private LinearLayout weatherCard;
    private LinearLayout dateCard;
    private View       themePickerOverlay;
    private LinearLayout pickerSheet;
    private LinearLayout themeCardsContainer;

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

        // ── Cache view references ─────────────────────────────────────────────
        textClockTime       = findViewById(R.id.textClockTime);
        textClockAmPm       = findViewById(R.id.textClockAmPm);
        textClockDayName    = findViewById(R.id.textClockDayName);
        textClockDate       = findViewById(R.id.textClockDate);
        textClockMonth      = findViewById(R.id.textClockMonth);
        tempView            = findViewById(R.id.tempView);
        locationLabel       = findViewById(R.id.locationLabel);
        batteryIndicator    = findViewById(R.id.batteryIndicator);
        weatherCard         = findViewById(R.id.weatherCard);
        dateCard            = findViewById(R.id.dateCard);
        themePickerOverlay  = findViewById(R.id.themePickerOverlay);
        pickerSheet         = findViewById(R.id.pickerSheet);
        themeCardsContainer = findViewById(R.id.themeCardsContainer);

        // ── Load & apply saved theme ──────────────────────────────────────────
        currentTheme = ThemeManager.load(this);
        buildThemeCards();
        applyTheme(currentTheme, false);

        // ── Long-press → show theme picker ────────────────────────────────────
        rootView.setOnLongClickListener(v -> {
            showThemePicker();
            return true;
        });

        // Tap on dim backdrop → dismiss picker
        themePickerOverlay.setOnClickListener(v -> hideThemePicker());

        // ── Battery ───────────────────────────────────────────────────────────
        setupBatteryReceiver();

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
    // Theme System
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Applies a complete color theme to every themed view.
     *
     * @param themeId one of ThemeManager.THEME_*
     * @param save    whether to persist the choice to SharedPreferences
     */
    private void applyTheme(int themeId, boolean save) {
        currentTheme = themeId;
        if (save) ThemeManager.save(this, themeId);

        int primary   = ThemeManager.PRIMARY[themeId];
        int secondary = ThemeManager.SECONDARY[themeId];
        int dim       = ThemeManager.DIM[themeId];
        int cardBg    = ThemeManager.CARD_BG[themeId];
        int border    = ThemeManager.BORDER[themeId];

        // ── Clock digits ──────────────────────────────────────────────────────
        if (textClockTime != null) {
            textClockTime.setTextColor(primary);
            // Tight same-colour glow — makes strokes appear thicker and vibrant
            textClockTime.setShadowLayer(8f, 0f, 0f, primary);
        }

        // ── Accent / secondary labels ─────────────────────────────────────────
        if (textClockAmPm    != null) textClockAmPm.setTextColor(secondary);
        if (textClockDayName != null) textClockDayName.setTextColor(secondary);
        if (textClockMonth   != null) textClockMonth.setTextColor(secondary);
        if (tempView         != null) tempView.setTextColor(primary);

        // ── Date number stays near-white for maximum readability ──────────────
        if (textClockDate    != null) textClockDate.setTextColor(0xFFFFF0E6);

        // ── Dim labels ────────────────────────────────────────────────────────
        if (locationLabel    != null) locationLabel.setTextColor(dim);
        if (batteryIndicator != null) batteryIndicator.setTextColor(dim);

        // ── Card + pill backgrounds (programmatic GradientDrawable) ───────────
        setRoundedBackground(weatherCard,     cardBg, border, 14f);
        setRoundedBackground(dateCard,        cardBg, border, 14f);
        setRoundedBackground(batteryIndicator, cardBg, border, 32f); // pill = large radius

        // ── Refresh theme card selection highlight ────────────────────────────
        refreshThemeCardSelections();
    }

    /** Builds and sets a GradientDrawable background on any View. */
    private void setRoundedBackground(View v, int fill, int stroke, float radiusDp) {
        if (v == null) return;
        int pL = v.getPaddingLeft();
        int pT = v.getPaddingTop();
        int pR = v.getPaddingRight();
        int pB = v.getPaddingBottom();
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setStroke(dpToPx(1), stroke);
        d.setCornerRadius(dpToPx(radiusDp));
        v.setBackground(d);
        v.setPadding(pL, pT, pR, pB);
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ─── Theme Picker ─────────────────────────────────────────────────────────

    /**
     * Builds one card per theme inside themeCardsContainer.
     * Each card shows a gradient preview strip and the theme name.
     * Called once in onCreate; selection highlights updated in applyTheme().
     */
    private void buildThemeCards() {
        if (themeCardsContainer == null) return;
        themeCardsContainer.removeAllViews();

        for (int i = 0; i < ThemeManager.THEME_COUNT; i++) {
            final int idx = i;

            // ── Card container ────────────────────────────────────────────────
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    dpToPx(88), ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, 0, dpToPx(12), 0);
            card.setLayoutParams(cardLp);
            card.setPadding(dpToPx(10), dpToPx(12), dpToPx(10), dpToPx(12));

            // Dark card background — will gain a border when selected
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setShape(GradientDrawable.RECTANGLE);
            cardBg.setColor(0xFF1C1C1C);
            cardBg.setCornerRadius(dpToPx(16));
            card.setBackground(cardBg);
            card.setTag(idx); // used by refreshThemeCardSelections()

            // ── Gradient colour preview strip ─────────────────────────────────
            View previewStrip = new View(this);
            LinearLayout.LayoutParams stripLp = new LinearLayout.LayoutParams(
                    dpToPx(60), dpToPx(28));
            stripLp.bottomMargin = dpToPx(10);
            previewStrip.setLayoutParams(stripLp);
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{ ThemeManager.PRIMARY[i], ThemeManager.SECONDARY[i] });
            gradient.setCornerRadius(dpToPx(8));
            previewStrip.setBackground(gradient);
            card.addView(previewStrip);

            // ── Three colour dots below the strip ─────────────────────────────
            LinearLayout dotsRow = new LinearLayout(this);
            dotsRow.setOrientation(LinearLayout.HORIZONTAL);
            dotsRow.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dotsLp.bottomMargin = dpToPx(8);
            dotsRow.setLayoutParams(dotsLp);

            addDot(dotsRow, ThemeManager.PRIMARY[i],   dpToPx(10));
            addDot(dotsRow, ThemeManager.SECONDARY[i], dpToPx(8));
            addDot(dotsRow, ThemeManager.DIM[i],       dpToPx(6));
            card.addView(dotsRow);

            // ── Theme name ────────────────────────────────────────────────────
            TextView nameView = new TextView(this);
            nameView.setText(ThemeManager.NAMES[i]);
            nameView.setTextColor(0xFF999999);
            nameView.setTextSize(8.5f);
            nameView.setTypeface(nameView.getTypeface(), Typeface.BOLD);
            nameView.setGravity(Gravity.CENTER);
            nameView.setLetterSpacing(0.05f);
            card.addView(nameView);

            // ── Click → apply theme and close picker ──────────────────────────
            card.setOnClickListener(v -> {
                applyTheme(idx, true);
                hideThemePicker();
            });

            themeCardsContainer.addView(card);
        }
    }

    /** Adds a small circle dot of the given color to a parent LinearLayout. */
    private void addDot(LinearLayout parent, int color, int sizePx) {
        View dot = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        lp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        dot.setLayoutParams(lp);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        dot.setBackground(d);
        parent.addView(dot);
    }

    /** Updates border highlight on each card to reflect current selection. */
    private void refreshThemeCardSelections() {
        if (themeCardsContainer == null) return;
        for (int i = 0; i < themeCardsContainer.getChildCount(); i++) {
            View child = themeCardsContainer.getChildAt(i);
            if (child.getTag() == null) continue;
            int idx = (int) child.getTag();
            GradientDrawable bg = (GradientDrawable) child.getBackground();
            if (bg == null) continue;
            if (idx == currentTheme) {
                bg.setStroke(dpToPx(2), ThemeManager.PRIMARY[idx]);
                bg.setColor(0xFF242424);
            } else {
                bg.setStroke(dpToPx(1), 0x22FFFFFF);
                bg.setColor(0xFF1C1C1C);
            }
        }
    }

    private void showThemePicker() {
        if (pickerVisible || themePickerOverlay == null) return;
        pickerVisible = true;
        themePickerOverlay.setVisibility(View.VISIBLE);
        themePickerOverlay.setAlpha(0f);
        themePickerOverlay.animate()
                .alpha(1f).setDuration(220).start();

        if (pickerSheet != null) {
            pickerSheet.setTranslationY(dpToPx(400));
            pickerSheet.animate()
                    .translationY(0f)
                    .setDuration(320)
                    .setInterpolator(new DecelerateInterpolator(2.2f))
                    .start();
        }
    }

    private void hideThemePicker() {
        if (!pickerVisible || themePickerOverlay == null) return;
        pickerVisible = false;

        if (pickerSheet != null) {
            pickerSheet.animate()
                    .translationY(dpToPx(400))
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        themePickerOverlay.animate()
                .alpha(0f)
                .setDuration(220)
                .withEndAction(() -> themePickerOverlay.setVisibility(View.GONE))
                .start();
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
                    // Glowing secondary accent when plugged in
                    batteryIndicator.setTextColor(ThemeManager.SECONDARY[currentTheme]);
                } else {
                    batteryIndicator.setTextColor(ThemeManager.DIM[currentTheme]);
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