package com.kushal.deskclock;

import android.content.Context;

/**
 * Central source-of-truth for all color themes.
 *
 * Each theme defines five colour roles:
 *   PRIMARY   — clock digits, the dominant hero colour.
 *   SECONDARY — AM/PM, day name, month, temperature, city.
 *   DIM       — very dark tint for city label and battery pill text.
 *   CARD_BG   — semi-transparent fill for the right-panel widget cards (~30% alpha).
 *   BORDER    — hairline stroke on cards and pill (~20% alpha primary).
 */
public class ThemeManager {

    private static final String PREFS_NAME = "deskclock_prefs";
    private static final String KEY_THEME  = "selected_theme";

    // ─── Theme IDs ────────────────────────────────────────────────────────────
    public static final int THEME_NEON_BLAZE     = 0;  // warm neon orange   (default)
    public static final int THEME_CYBERPUNK      = 1;  // electric cyan
    public static final int THEME_PURPLE_HAZE    = 2;  // soft violet
    public static final int THEME_MATCHA         = 3;  // neon green
    public static final int THEME_ROSE_GOLD      = 4;  // blush pink
    public static final int THEME_GOLDEN_HOUR    = 5;  // pure gold
    public static final int THEME_COUNT          = 6;

    /** Display names shown in the picker carousel. */
    public static final String[] NAMES = {
        "Neon Blaze",
        "Cyberpunk",
        "Purple Haze",
        "Matcha",
        "Rose Gold",
        "Golden Hour",
    };

    // ─── Colour Palettes ─────────────────────────────────────────────────────

    /** Dominant clock colour — fills the massive h:mm digits. */
    public static final int[] PRIMARY = {
        0xFFFF6B35,   // Neon Blaze   — vivid orange-red
        0xFF00D4FF,   // Cyberpunk    — electric cyan
        0xFFC77DFF,   // Purple Haze  — soft violet
        0xFF39D353,   // Matcha       — neon green
        0xFFFF85A1,   // Rose Gold    — blush pink
        0xFFFFD700,   // Golden Hour  — pure gold
    };

    /** Accent colour — used for AM/PM, day, month, temperature. */
    public static final int[] SECONDARY = {
        0xFFFF9D6B,   // Neon Blaze   — warm peach
        0xFF7DF9FF,   // Cyberpunk    — ice cyan
        0xFFE0AAFF,   // Purple Haze  — lilac
        0xFF88FF9A,   // Matcha       — mint
        0xFFFFB3C6,   // Rose Gold    — light blush
        0xFFFFF176,   // Golden Hour  — pale lemon
    };

    /** Very dark tint — barely-visible labels (city, battery idle). */
    public static final int[] DIM = {
        0xFF5C2800,   // Neon Blaze
        0xFF003A4D,   // Cyberpunk
        0xFF3D0066,   // Purple Haze
        0xFF0A3D14,   // Matcha
        0xFF4D001C,   // Rose Gold
        0xFF3D2B00,   // Golden Hour
    };

    /** Widget card background — ~30% opacity warm-dark tint. */
    public static final int[] CARD_BG = {
        0x4D110800,   // Neon Blaze
        0x4D001D26,   // Cyberpunk
        0x4D1A0033,   // Purple Haze
        0x4D001A06,   // Matcha
        0x4D260009,   // Rose Gold
        0x4D1A1100,   // Golden Hour
    };

    /** Card / pill border — ~20% opacity primary hue. */
    public static final int[] BORDER = {
        0x33FF6B35,   // Neon Blaze
        0x3300D4FF,   // Cyberpunk
        0x33C77DFF,   // Purple Haze
        0x3339D353,   // Matcha
        0x33FF85A1,   // Rose Gold
        0x33FFD700,   // Golden Hour
    };

    // ─── Persistence ─────────────────────────────────────────────────────────

    public static void save(Context ctx, int themeId) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
           .edit().putInt(KEY_THEME, themeId).apply();
    }

    public static int load(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                  .getInt(KEY_THEME, THEME_NEON_BLAZE);
    }
}
