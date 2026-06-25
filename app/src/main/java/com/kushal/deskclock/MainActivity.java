package com.kushal.deskclock;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private Handler burnInHandler;
    private Runnable burnInRunnable;
    private static final long BURN_IN_INTERVAL = 60000; // 60 seconds
    private static final float MAX_OFFSET = 50f;
    private Random random;
    
    private BroadcastReceiver batteryReceiver;
    private TextView batteryIndicator;
    
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private static final float LUX_THRESHOLD = 5f;
    private static final float DIM_BRIGHTNESS = 0.05f;
    private static final float NORMAL_BRIGHTNESS = 1.0f;
    
    private TextView tempView;
    private Handler weatherHandler;
    private Runnable weatherRunnable;
    private ExecutorService executorService;
    private static final long WEATHER_REFRESH_INTERVAL = 30 * 60 * 1000; // 30 minutes
    private static final String WEATHER_API_URL = "https://api.open-meteo.com/v1/forecast?latitude=12.9716&longitude=77.5946&current_weather=true";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Keep screen on indefinitely
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Enable immersive fullscreen mode
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Hide both status bar and navigation bar
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars());
            insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        // Handle insets properly - don't apply padding for fullscreen
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            // For fullscreen, we don't apply padding; let content extend to edges
            return WindowInsetsCompat.CONSUMED;
        });

        // Initialize burn-in prevention
        random = new Random();
        burnInHandler = new Handler(Looper.getMainLooper());
        setupBurnInPrevention();

        // Get battery indicator TextView
        batteryIndicator = findViewById(R.id.batteryIndicator);

        // Setup battery receiver
        setupBatteryReceiver();

        // Initialize light sensor for adaptive brightness
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        // Get temperature view and setup weather fetching
        tempView = findViewById(R.id.tempView);
        executorService = Executors.newSingleThreadExecutor();
        weatherHandler = new Handler(Looper.getMainLooper());
        setupWeatherRefresh();

        // Fetch weather immediately on startup
        fetchWeather();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();

            if (lux < LUX_THRESHOLD) {
                layoutParams.screenBrightness = DIM_BRIGHTNESS;
            } else {
                layoutParams.screenBrightness = NORMAL_BRIGHTNESS;
            }
            getWindow().setAttributes(layoutParams);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No action needed
    }

    private void setupWeatherRefresh() {
        weatherRunnable = new Runnable() {
            @Override
            public void run() {
                fetchWeather();
                weatherHandler.postDelayed(this, WEATHER_REFRESH_INTERVAL);
            }
        };
    }

    private void fetchWeather() {
        executorService.execute(() -> {
            try {
                URL url = new URL(WEATHER_API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                connection.disconnect();

                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONObject currentWeather = jsonResponse.getJSONObject("current_weather");
                double temperatureCelsius = currentWeather.getDouble("temperature");
                
                // Update UI on main thread
                weatherHandler.post(() -> {
                    if (tempView != null) {
                        int tempFahrenheit = (int) (temperatureCelsius * 9 / 5 + 32);
                        tempView.setText(tempFahrenheit + "°");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setupBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;

                String batteryText = "Battery: " + level + "%";
                if (isCharging) {
                    batteryText += " ⚡";
                }
                if (batteryIndicator != null) {
                    batteryIndicator.setText(batteryText);
                }
            }
        };
    }

    private void setupBurnInPrevention() {
        burnInRunnable = new Runnable() {
            @Override
            public void run() {
                LinearLayout clockContainer = findViewById(R.id.clockContainer);
                if (clockContainer != null) {
                    float randomX = (random.nextFloat() * 2 * MAX_OFFSET) - MAX_OFFSET;
                    float randomY = (random.nextFloat() * 2 * MAX_OFFSET) - MAX_OFFSET;
                    clockContainer.setTranslationX(randomX);
                    clockContainer.setTranslationY(randomY);
                }
                burnInHandler.postDelayed(this, BURN_IN_INTERVAL);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start burn-in prevention
        burnInHandler.post(burnInRunnable);

        // Register battery receiver
        if (batteryReceiver != null) {
            IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_NOT_EXPORTED);
        }

        // Register light sensor listener
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // Start weather refresh
        if (weatherHandler != null && weatherRunnable != null) {
            weatherHandler.post(weatherRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop burn-in prevention and prevent memory leaks
        burnInHandler.removeCallbacks(burnInRunnable);

        // Unregister battery receiver
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException e) {
                // Receiver was not registered
            }
        }

        // Unregister light sensor listener
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        // Stop weather refresh
        if (weatherHandler != null && weatherRunnable != null) {
            weatherHandler.removeCallbacks(weatherRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}