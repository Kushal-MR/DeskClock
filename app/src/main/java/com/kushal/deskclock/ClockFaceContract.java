package com.kushal.deskclock;

/**
 * Contract interface that all Clock Face fragments implement.
 *
 * The hosting Activity (MainActivity) pushes global data — battery state,
 * weather temperature — downstream to whichever face is currently active
 * through these callbacks. This keeps the data-producing services at the
 * Activity level while letting each face decide how (or whether) to render
 * the information.
 */
public interface ClockFaceContract {

    /**
     * Called when a battery status broadcast is received.
     *
     * @param percent    battery level 0–100
     * @param isCharging true when plugged in or full
     */
    void onBatteryUpdate(int percent, boolean isCharging);

    /**
     * Called when a fresh weather reading arrives from Open-Meteo.
     *
     * @param tempText display-ready string, e.g. "28°C"
     */
    void onWeatherUpdate(String tempText);
}
