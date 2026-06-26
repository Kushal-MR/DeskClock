package com.kushal.deskclock;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.palette.graphics.Palette;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpotifyDataFetcher {
    private static final String TAG = "SpotifyDataFetcher";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ColorCallback {
        void onColorsExtracted(int[] colors, Bitmap albumArt);
    }

    public interface FactsCallback {
        void onFactsFetched(List<String> facts);
    }

    public static void fetchAlbumArtAndColors(String track, String artist, ColorCallback callback) {
        executor.execute(() -> {
            try {
                String query = URLEncoder.encode(artist + " " + track, "UTF-8");
                URL url = new URL("https://itunes.apple.com/search?term=" + query + "&limit=1&entity=song");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    JSONArray results = json.optJSONArray("results");
                    if (results != null && results.length() > 0) {
                        JSONObject song = results.getJSONObject(0);
                        String artworkUrl = song.optString("artworkUrl100");
                        if (!artworkUrl.isEmpty()) {
                            // Get high-res artwork
                            artworkUrl = artworkUrl.replace("100x100bb", "600x600bb");
                            URL imageUrl = new URL(artworkUrl);
                            HttpURLConnection imageConn = (HttpURLConnection) imageUrl.openConnection();
                            imageConn.setDoInput(true);
                            imageConn.connect();
                            InputStream input = imageConn.getInputStream();
                            Bitmap bitmap = BitmapFactory.decodeStream(input);

                            if (bitmap != null) {
                                Palette.from(bitmap).resizeBitmapArea(2500).generate(palette -> {
                                    int dominant = palette.getDominantColor(0xFF121212);
                                    int vibrant = palette.getVibrantColor(dominant);
                                    int darkVibrant = palette.getDarkVibrantColor(dominant);
                                    int muted = palette.getMutedColor(darkVibrant);
                                    
                                    // Boost colors to ensure they are never just dull gray
                                    vibrant = boostColor(vibrant);
                                    darkVibrant = boostColor(darkVibrant);
                                    dominant = boostColor(dominant);
                                    muted = boostColor(muted);
                                    
                                    int[] colors = new int[]{vibrant, darkVibrant, dominant, muted};
                                    mainHandler.post(() -> callback.onColorsExtracted(colors, bitmap));
                                });
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching album art", e);
            }
            
            // Fallback default colors if anything fails
            int[] defaultColors = new int[]{0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5};
            mainHandler.post(() -> callback.onColorsExtracted(defaultColors, null));
        });
    }
    public static void extractColorsFromBitmap(android.graphics.Bitmap bitmap, ColorCallback callback) {
        if (bitmap == null) {
            int[] defaultColors = new int[]{0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5};
            mainHandler.post(() -> callback.onColorsExtracted(defaultColors, null));
            return;
        }

        executor.execute(() -> {
            androidx.palette.graphics.Palette.from(bitmap).resizeBitmapArea(2500).generate(palette -> {
                int dominant = palette.getDominantColor(0xFF121212);
                int vibrant = palette.getVibrantColor(dominant);
                int darkVibrant = palette.getDarkVibrantColor(dominant);
                int muted = palette.getMutedColor(darkVibrant);
                
                vibrant = boostColor(vibrant);
                darkVibrant = boostColor(darkVibrant);
                dominant = boostColor(dominant);
                muted = boostColor(muted);
                
                int[] colors = new int[]{vibrant, darkVibrant, dominant, muted};
                mainHandler.post(() -> callback.onColorsExtracted(colors, bitmap));
            });
        });
    }

    private static int boostColor(int color) {
        float[] hsl = new float[3];
        androidx.core.graphics.ColorUtils.colorToHSL(color, hsl);
        // Boost saturation if it's too low
        if (hsl[1] < 0.4f) {
            hsl[1] = 0.4f + (hsl[1] * 0.5f);
        }
        // Ensure it's not too dark to be visible or too bright
        if (hsl[2] < 0.2f) {
            hsl[2] = 0.2f + (hsl[2] * 0.5f);
        }
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl);
    }

    public static void fetchArtistFacts(String artist, FactsCallback callback) {
        executor.execute(() -> {
            try {
                // 1. Clean artist name (take only the primary artist before any commas, &, or "feat.")
                String cleanArtist = artist;
                if (cleanArtist.contains(",")) cleanArtist = cleanArtist.split(",")[0];
                if (cleanArtist.contains("&")) cleanArtist = cleanArtist.split("&")[0];
                if (cleanArtist.toLowerCase().contains(" feat")) cleanArtist = cleanArtist.split("(?i) feat")[0];
                if (cleanArtist.toLowerCase().contains(" ft")) cleanArtist = cleanArtist.split("(?i) ft")[0];
                cleanArtist = cleanArtist.trim();

                // 2. Search Wikipedia to get the exact page title
                String searchUrlStr = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=" 
                        + URLEncoder.encode(cleanArtist, "UTF-8") + "&utf8=&format=json&srlimit=1";
                URL searchUrl = new URL(searchUrlStr);
                HttpURLConnection searchConn = (HttpURLConnection) searchUrl.openConnection();
                searchConn.setRequestMethod("GET");
                searchConn.setRequestProperty("User-Agent", "DeskClockApp/1.0 (kushal@example.com)");
                
                String exactTitle = null;
                if (searchConn.getResponseCode() == 200) {
                    BufferedReader sr = new BufferedReader(new InputStreamReader(searchConn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = sr.readLine()) != null) sb.append(line);
                    sr.close();
                    
                    JSONObject searchJson = new JSONObject(sb.toString());
                    JSONArray searchResults = searchJson.optJSONObject("query").optJSONArray("search");
                    if (searchResults != null && searchResults.length() > 0) {
                        exactTitle = searchResults.getJSONObject(0).optString("title");
                    }
                }
                
                if (exactTitle != null) {
                    // 3. Fetch summary using the exact page title
                    String encodedTitle = URLEncoder.encode(exactTitle.replace(" ", "_"), "UTF-8");
                    URL url = new URL("https://en.wikipedia.org/api/rest_v1/page/summary/" + encodedTitle);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("User-Agent", "DeskClockApp/1.0 (kushal@example.com)");
    
                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
    
                        JSONObject json = new JSONObject(response.toString());
                        String extract = json.optString("extract");
                        
                        if (!extract.isEmpty()) {
                            String[] sentences = extract.split("(?<=[.!?])\\s+");
                            List<String> facts = new ArrayList<>();
                            for (String s : sentences) {
                                if (s.length() > 20) {
                                    facts.add(s);
                                }
                            }
                            if (!facts.isEmpty()) {
                                mainHandler.post(() -> callback.onFactsFetched(facts));
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching artist facts", e);
            }

            // Fallback fact
            List<String> defaultFacts = new ArrayList<>();
            defaultFacts.add("Now Playing: " + artist + " on Spotify.");
            defaultFacts.add("Enjoy the music!");
            mainHandler.post(() -> callback.onFactsFetched(defaultFacts));
        });
    }
}
