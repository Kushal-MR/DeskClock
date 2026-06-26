package com.kushal.deskclock;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

/**
 * ModernStandByFragment — the iOS 17 StandBy clock face.
 *
 * Extracts the UI and theme logic that previously lived in MainActivity
 * into a self-contained Fragment. The host Activity pushes battery and
 * weather data through the {@link ClockFaceContract} interface.
 */
public class ModernStandByFragment extends Fragment implements ClockFaceContract {

    // ─── Theme ────────────────────────────────────────────────────────────────
    private int currentTheme = ThemeManager.THEME_NEON_BLAZE;
    private boolean pickerVisible = false;

    // ─── Cached view references ───────────────────────────────────────────────
    private TextView textClockTime;
    private TextView textClockAmPm;
    private TextView textClockDayName;
    private TextView textClockDate;
    private TextView textClockMonth;
    private TextView tempView;
    private TextView locationLabel;
    private TextView batteryIndicator;
    private LinearLayout weatherCard;
    private LinearLayout dateCard;
    private View themePickerOverlay;
    private LinearLayout pickerSheet;
    private LinearLayout themeCardsContainer;

    // ─────────────────────────────────────────────────────────────────────────

    public ModernStandByFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_modern_standby, container, false);

        // ── Cache view references ─────────────────────────────────────────────
        textClockTime       = root.findViewById(R.id.textClockTime);
        textClockAmPm       = root.findViewById(R.id.textClockAmPm);
        textClockDayName    = root.findViewById(R.id.textClockDayName);
        textClockDate       = root.findViewById(R.id.textClockDate);
        textClockMonth      = root.findViewById(R.id.textClockMonth);
        tempView            = root.findViewById(R.id.tempView);
        locationLabel       = root.findViewById(R.id.locationLabel);
        batteryIndicator    = root.findViewById(R.id.batteryIndicator);
        weatherCard         = root.findViewById(R.id.weatherCard);
        dateCard            = root.findViewById(R.id.dateCard);
        themePickerOverlay  = root.findViewById(R.id.themePickerOverlay);
        pickerSheet         = root.findViewById(R.id.pickerSheet);
        themeCardsContainer = root.findViewById(R.id.themeCardsContainer);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Load & apply saved theme ──────────────────────────────────────────
        currentTheme = ThemeManager.load(requireContext());
        buildThemeCards();
        applyTheme(currentTheme, false);

        // ── Long-press → show theme picker ────────────────────────────────────
        view.setOnLongClickListener(v -> {
            showThemePicker();
            return true;
        });

        // Tap on dim backdrop → dismiss picker
        themePickerOverlay.setOnClickListener(v -> hideThemePicker());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ClockFaceContract
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void onBatteryUpdate(int percent, boolean isCharging) {
        if (batteryIndicator == null) return;
        String text = percent + "%";
        if (isCharging) {
            text += "  ⚡";
            batteryIndicator.setTextColor(ThemeManager.SECONDARY[currentTheme]);
        } else {
            batteryIndicator.setTextColor(ThemeManager.DIM[currentTheme]);
        }
        batteryIndicator.setText(text);
    }

    @Override
    public void onWeatherUpdate(String tempText) {
        if (tempView != null) {
            tempView.setText(tempText);
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
        if (save) ThemeManager.save(requireContext(), themeId);

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
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    // ─── Theme Picker ─────────────────────────────────────────────────────────

    /**
     * Builds one card per theme inside themeCardsContainer.
     * Each card shows a gradient preview strip and the theme name.
     * Called once in onViewCreated; selection highlights updated in applyTheme().
     */
    private void buildThemeCards() {
        if (themeCardsContainer == null) return;
        themeCardsContainer.removeAllViews();

        for (int i = 0; i < ThemeManager.THEME_COUNT; i++) {
            final int idx = i;

            // ── Card container ────────────────────────────────────────────────
            LinearLayout card = new LinearLayout(requireContext());
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
            View previewStrip = new View(requireContext());
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
            LinearLayout dotsRow = new LinearLayout(requireContext());
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
            TextView nameView = new TextView(requireContext());
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

        // Disable ViewPager2 swiping while picker is open
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setSwipeEnabled(false);
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
                .withEndAction(() -> {
                    themePickerOverlay.setVisibility(View.GONE);
                    // Re-enable ViewPager2 swiping after picker closes
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) requireActivity()).setSwipeEnabled(true);
                    }
                })
                .start();
    }
}
