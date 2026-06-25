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

import java.util.Random;

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
    }
}