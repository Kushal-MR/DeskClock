package com.kushal.deskclock;

import android.content.Context;

/**
 * Colour palette engine for Face 2 — the Retro Split-Flap clock.
 *
 * Each theme defines four colour roles:
 *   CARD_BG    — the matte background fill of each digit card.
 *   TEXT_COLOR  — the stark digit colour (and static colon).
 *   ACCENT     — subtle info-row text (date, battery idle).
 *   DIVIDER    — the thin horizontal line across the card centre.
 *
 * Persistence is kept separate from ThemeManager (Face 1) using its
 * own SharedPreferences key so both faces remember their theme
 * independently.
 */
public class RetroThemeManager {

    private static final String PREFS_NAME = "deskclock_prefs";
    private static final String KEY_THEME  = "selected_retro_theme";

    // ─── Theme IDs ────────────────────────────────────────────────────────────
    public static final int THEME_CLASSIC_NOIR     = 0;  // warm white on dark gray
    public static final int THEME_CRIMSON_TERMINAL = 1;  // bright red on maroon
    public static final int THEME_NEON_MINT        = 2;  // electric mint on teal
    public static final int THEME_AMBER_GLOW       = 3;  // warm amber on dark amber
    public static final int THEME_ELECTRIC_VIOLET  = 4;  // soft violet on deep purple
    public static final int THEME_ARCTIC_BLUE      = 5;  // ice blue on navy
    public static final int THEME_COUNT            = 6;

    /** Display names shown in the retro picker carousel. */
    public static final String[] NAMES = {
        "Classic Noir",
        "Crimson",
        "Neon Mint",
        "Amber Glow",
        "Violet",
        "Arctic Blue",
    };

    // ─── Colour Palettes ─────────────────────────────────────────────────────

    /** Matte card background — the dark body of each split-flap tile. */
    public static final int[] CARD_BG = {
        0xFF2A2A2A,   // Classic Noir   — neutral dark gray
        0xFF3D0A0A,   // Crimson        — deep maroon
        0xFF0A2A1F,   // Neon Mint      — dark teal
        0xFF2A1A00,   // Amber Glow     — dark amber
        0xFF1A0A2E,   // Electric Violet — deep purple
        0xFF0A1A2E,   // Arctic Blue    — navy
    };

    /** Digit colour — the large, dominant character on each tile. */
    public static final int[] TEXT_COLOR = {
        0xFFF5F5F0,   // Classic Noir   — warm white
        0xFFFF4444,   // Crimson        — bright red
        0xFF00FFAA,   // Neon Mint      — electric mint
        0xFFFFB300,   // Amber Glow     — warm amber
        0xFFBB86FC,   // Electric Violet — soft violet
        0xFF64B5F6,   // Arctic Blue    — ice blue
    };

    /** Accent colour — info-row text (date, battery when charging). */
    public static final int[] ACCENT = {
        0xFF666666,   // Classic Noir   — medium gray
        0xFFFF8888,   // Crimson        — soft red
        0xFF66FFCC,   // Neon Mint      — pastel mint
        0xFFFFD54F,   // Amber Glow     — pale amber
        0xFFE0B0FF,   // Electric Violet — light violet
        0xFF90CAF9,   // Arctic Blue    — pale blue
    };

    /** Divider line across the card centre — slightly darker than card body. */
    public static final int[] DIVIDER = {
        0xFF1A1A1A,   // Classic Noir
        0xFF2A0505,   // Crimson
        0xFF051A13,   // Neon Mint
        0xFF1A1000,   // Amber Glow
        0xFF0F0520,   // Electric Violet
        0xFF050F1A,   // Arctic Blue
    };

    // ─── Persistence ─────────────────────────────────────────────────────────

    public static void save(Context ctx, int themeId) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
           .edit().putInt(KEY_THEME, themeId).apply();
    }

    public static int load(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                  .getInt(KEY_THEME, THEME_CLASSIC_NOIR);
    }
}
