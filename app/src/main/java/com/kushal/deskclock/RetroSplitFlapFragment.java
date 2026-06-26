package com.kushal.deskclock;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class RetroSplitFlapFragment extends Fragment implements ClockFaceContract {

    private SplitFlapView digitH1, digitH0, digitM1, digitM0;
    private TextView colonSeparator, retroDate, retroBattery;
    private View retroThemePickerOverlay;
    private LinearLayout retroPickerSheet, retroThemeCardsContainer;
    private LinearLayout retroInfoRow;

    private int currentTheme = RetroThemeManager.THEME_CLASSIC_NOIR;
    private boolean pickerVisible = false;

    // Time tracking
    private Handler timeHandler;
    private Runnable timeRunnable;
    private char lastH1 = ' ', lastH0 = ' ', lastM1 = ' ', lastM0 = ' ';

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_retro_split_flap, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Cache view references
        digitH1 = view.findViewById(R.id.digitH1);
        digitH0 = view.findViewById(R.id.digitH0);
        digitM1 = view.findViewById(R.id.digitM1);
        digitM0 = view.findViewById(R.id.digitM0);
        colonSeparator = view.findViewById(R.id.colonSeparator);
        retroDate = view.findViewById(R.id.retroDate);
        retroBattery = view.findViewById(R.id.retroBattery);
        retroThemePickerOverlay = view.findViewById(R.id.retroThemePickerOverlay);
        retroPickerSheet = view.findViewById(R.id.retroPickerSheet);
        retroThemeCardsContainer = view.findViewById(R.id.retroThemeCardsContainer);
        retroInfoRow = view.findViewById(R.id.retroInfoRow);

        // Load and apply saved retro theme
        currentTheme = RetroThemeManager.load(requireContext());
        buildThemeCards();
        applyTheme(currentTheme, false);

        // Long-press → show retro theme picker
        View root = view.findViewById(R.id.retroRoot);
        root.setOnLongClickListener(v -> {
            showThemePicker();
            return true;
        });

        // Tap overlay → dismiss
        retroThemePickerOverlay.setOnClickListener(v -> hideThemePicker());

        // Set up time handler
        timeHandler = new Handler(Looper.getMainLooper());
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                updateTime();
                updateDate();
                timeHandler.postDelayed(this, 1000);
            }
        };

        // Initialize time immediately (no animation for first display)
        initializeTime();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start time updates
        timeHandler.post(timeRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        timeHandler.removeCallbacks(timeRunnable);
    }

    // ════════════════════════════════════════════════
    // Time Management
    // ════════════════════════════════════════════════

    private void initializeTime() {
        Calendar cal = Calendar.getInstance();
        boolean is24 = android.text.format.DateFormat.is24HourFormat(requireContext());
        int hour = is24 ? cal.get(Calendar.HOUR_OF_DAY) : cal.get(Calendar.HOUR);
        if (!is24 && hour == 0) hour = 12;
        int minute = cal.get(Calendar.MINUTE);

        String hStr = String.format(Locale.US, "%02d", hour);
        String mStr = String.format(Locale.US, "%02d", minute);

        lastH1 = hStr.charAt(0);
        lastH0 = hStr.charAt(1);
        lastM1 = mStr.charAt(0);
        lastM0 = mStr.charAt(1);

        digitH1.setDigitImmediate(lastH1);
        digitH0.setDigitImmediate(lastH0);
        digitM1.setDigitImmediate(lastM1);
        digitM0.setDigitImmediate(lastM0);

        updateDate();
    }

    private void updateTime() {
        Calendar cal = Calendar.getInstance();
        boolean is24 = android.text.format.DateFormat.is24HourFormat(requireContext());
        int hour = is24 ? cal.get(Calendar.HOUR_OF_DAY) : cal.get(Calendar.HOUR);
        if (!is24 && hour == 0) hour = 12;
        int minute = cal.get(Calendar.MINUTE);

        String hStr = String.format(Locale.US, "%02d", hour);
        String mStr = String.format(Locale.US, "%02d", minute);

        char h1 = hStr.charAt(0);
        char h0 = hStr.charAt(1);
        char m1 = mStr.charAt(0);
        char m0 = mStr.charAt(1);

        // Only animate digits that actually changed
        if (h1 != lastH1) { digitH1.setDigit(h1); lastH1 = h1; }
        if (h0 != lastH0) { digitH0.setDigit(h0); lastH0 = h0; }
        if (m1 != lastM1) { digitM1.setDigit(m1); lastM1 = m1; }
        if (m0 != lastM0) { digitM0.setDigit(m0); lastM0 = m0; }
    }

    private void updateDate() {
        if (retroDate == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("EEE • MMM d", Locale.US);
        retroDate.setText(sdf.format(Calendar.getInstance().getTime()).toUpperCase(Locale.US));
    }

    // ════════════════════════════════════════════════
    // ClockFaceContract
    // ════════════════════════════════════════════════

    @Override
    public void onBatteryUpdate(int percent, boolean isCharging) {
        if (retroBattery == null) return;
        String text = percent + "%";
        if (isCharging) {
            text += "  ⚡";
            retroBattery.setTextColor(RetroThemeManager.ACCENT[currentTheme]);
        } else {
            retroBattery.setTextColor(RetroThemeManager.TEXT_COLOR[currentTheme]);
        }
        retroBattery.setText(text);
    }

    @Override
    public void onWeatherUpdate(String tempText) {
        // Retro face is minimalist — no weather display
        // Data is still received so it's ready if we switch to Modern face
    }

    // ════════════════════════════════════════════════
    // Theme System
    // ════════════════════════════════════════════════

    private void applyTheme(int themeId, boolean save) {
        currentTheme = themeId;
        if (save) RetroThemeManager.save(requireContext(), themeId);

        int cardBg    = RetroThemeManager.CARD_BG[themeId];
        int textColor = RetroThemeManager.TEXT_COLOR[themeId];
        int accent    = RetroThemeManager.ACCENT[themeId];
        int divider   = RetroThemeManager.DIVIDER[themeId];

        // Apply to all split-flap views
        if (digitH1 != null) digitH1.setColors(cardBg, textColor, divider);
        if (digitH0 != null) digitH0.setColors(cardBg, textColor, divider);
        if (digitM1 != null) digitM1.setColors(cardBg, textColor, divider);
        if (digitM0 != null) digitM0.setColors(cardBg, textColor, divider);

        // Colon color
        if (colonSeparator != null) colonSeparator.setTextColor(textColor);

        // Info row
        if (retroDate != null) retroDate.setTextColor(accent);
        if (retroBattery != null) retroBattery.setTextColor(accent);

        refreshThemeCardSelections();
    }

    private void buildThemeCards() {
        if (retroThemeCardsContainer == null) return;
        retroThemeCardsContainer.removeAllViews();

        for (int i = 0; i < RetroThemeManager.THEME_COUNT; i++) {
            final int idx = i;

            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    dpToPx(88), ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, 0, dpToPx(12), 0);
            card.setLayoutParams(cardLp);
            card.setPadding(dpToPx(10), dpToPx(12), dpToPx(10), dpToPx(12));

            GradientDrawable cardDrawable = new GradientDrawable();
            cardDrawable.setShape(GradientDrawable.RECTANGLE);
            cardDrawable.setColor(0xFF1C1C1C);
            cardDrawable.setCornerRadius(dpToPx(16));
            card.setBackground(cardDrawable);
            card.setTag(idx);

            // Gradient preview strip showing card bg → text color
            View previewStrip = new View(requireContext());
            LinearLayout.LayoutParams stripLp = new LinearLayout.LayoutParams(
                    dpToPx(60), dpToPx(28));
            stripLp.bottomMargin = dpToPx(10);
            previewStrip.setLayoutParams(stripLp);
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{ RetroThemeManager.CARD_BG[i], RetroThemeManager.TEXT_COLOR[i] });
            gradient.setCornerRadius(dpToPx(8));
            previewStrip.setBackground(gradient);
            card.addView(previewStrip);

            // Color dots
            LinearLayout dotsRow = new LinearLayout(requireContext());
            dotsRow.setOrientation(LinearLayout.HORIZONTAL);
            dotsRow.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dotsLp.bottomMargin = dpToPx(8);
            dotsRow.setLayoutParams(dotsLp);

            addDot(dotsRow, RetroThemeManager.TEXT_COLOR[i], dpToPx(10));
            addDot(dotsRow, RetroThemeManager.ACCENT[i],     dpToPx(8));
            addDot(dotsRow, RetroThemeManager.CARD_BG[i],    dpToPx(6));
            card.addView(dotsRow);

            // Theme name
            TextView nameView = new TextView(requireContext());
            nameView.setText(RetroThemeManager.NAMES[i]);
            nameView.setTextColor(0xFF999999);
            nameView.setTextSize(8.5f);
            nameView.setTypeface(nameView.getTypeface(), Typeface.BOLD);
            nameView.setGravity(Gravity.CENTER);
            nameView.setLetterSpacing(0.05f);
            card.addView(nameView);

            card.setOnClickListener(v -> {
                applyTheme(idx, true);
                hideThemePicker();
            });

            retroThemeCardsContainer.addView(card);
        }
    }

    private void addDot(LinearLayout parent, int color, int sizePx) {
        View dot = new View(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        lp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        dot.setLayoutParams(lp);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        dot.setBackground(d);
        parent.addView(dot);
    }

    private void refreshThemeCardSelections() {
        if (retroThemeCardsContainer == null) return;
        for (int i = 0; i < retroThemeCardsContainer.getChildCount(); i++) {
            View child = retroThemeCardsContainer.getChildAt(i);
            if (child.getTag() == null) continue;
            int idx = (int) child.getTag();
            GradientDrawable bg = (GradientDrawable) child.getBackground();
            if (bg == null) continue;
            if (idx == currentTheme) {
                bg.setStroke(dpToPx(2), RetroThemeManager.TEXT_COLOR[idx]);
                bg.setColor(0xFF242424);
            } else {
                bg.setStroke(dpToPx(1), 0x22FFFFFF);
                bg.setColor(0xFF1C1C1C);
            }
        }
    }

    private void showThemePicker() {
        if (pickerVisible || retroThemePickerOverlay == null) return;
        pickerVisible = true;
        retroThemePickerOverlay.setVisibility(View.VISIBLE);
        retroThemePickerOverlay.setAlpha(0f);
        retroThemePickerOverlay.animate()
                .alpha(1f).setDuration(220).start();

        if (retroPickerSheet != null) {
            retroPickerSheet.setTranslationY(dpToPx(400));
            retroPickerSheet.animate()
                    .translationY(0f)
                    .setDuration(320)
                    .setInterpolator(new DecelerateInterpolator(2.2f))
                    .start();
        }

        // Disable ViewPager2 swiping while picker is open
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setSwipeEnabled(false);
        }
    }

    private void hideThemePicker() {
        if (!pickerVisible || retroThemePickerOverlay == null) return;
        pickerVisible = false;

        if (retroPickerSheet != null) {
            retroPickerSheet.animate()
                    .translationY(dpToPx(400))
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        retroThemePickerOverlay.animate()
                .alpha(0f)
                .setDuration(220)
                .withEndAction(() -> {
                    retroThemePickerOverlay.setVisibility(View.GONE);
                    // Re-enable ViewPager2 swiping
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).setSwipeEnabled(true);
                    }
                })
                .start();
    }

    private int dpToPx(float dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
