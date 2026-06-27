package com.kushal.deskclock;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class SpotifyStandByFragment extends Fragment implements ClockFaceContract {

    private View rootView;
    private android.widget.ImageView albumCover;
    private TextView trackName;
    private TextView artistName;
    private TextView albumName;
    private TextView factTextView;
    private TextView tempView;
    private TextView batteryIndicator;
    private com.kushal.deskclock.CustomFontTextClock textClockTime;
    private com.kushal.deskclock.CustomFontTextClock textClockAmPm;
    private com.kushal.deskclock.CustomFontTextClock dateTopView;
    private AmbientGlowView ambientGlowView;

    private String currentArtist = "";
    private String currentTrack = "";

    // Facts rotation
    private List<String> currentFacts = new ArrayList<>();
    private int factIndex = 0;
    private final Handler factHandler = new Handler(Looper.getMainLooper());
    private final Runnable factRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentFacts.size() > 1) {
                factIndex = (factIndex + 1) % currentFacts.size();
                crossfadeText(factTextView, currentFacts.get(factIndex));
            }
            factHandler.postDelayed(this, 15000); // Rotate every 15 seconds
        }
    };

    // Background pulsing
    private GradientDrawable backgroundGradient;
    private int[] currentColors = new int[]{0xFF121212, 0xFF121212, 0xFF121212, 0xFF121212};
    private int currentTextColor = 0xFFFFFFFF;
    private ValueAnimator colorAnimator;
    private ValueAnimator pulseAnimator;
    private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();

    public SpotifyStandByFragment() {
        // Required empty constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_spotify_standby, container, false);

        rootView = root.findViewById(R.id.spotifyRootView);
        albumCover = root.findViewById(R.id.albumCover);
        trackName = root.findViewById(R.id.trackName);
        artistName = root.findViewById(R.id.artistName);
        albumName = root.findViewById(R.id.albumName);
        factTextView = root.findViewById(R.id.factTextView);
        tempView = root.findViewById(R.id.tempView);
        batteryIndicator = root.findViewById(R.id.batteryIndicator);
        textClockTime = root.findViewById(R.id.textClockTime);
        textClockAmPm = root.findViewById(R.id.textClockAmPm);
        dateTopView = root.findViewById(R.id.dateTopView);
        ambientGlowView = root.findViewById(R.id.ambientGlowView);

        setupBackground();

        try {
            android.graphics.Typeface outfitBold = android.graphics.Typeface.createFromAsset(requireContext().getAssets(), "fonts/outfit_bold.ttf");
            android.graphics.Typeface outfitMedium = android.graphics.Typeface.createFromAsset(requireContext().getAssets(), "fonts/outfit_medium.ttf");
            
            if (outfitBold != null) {
                textClockTime.setTypeface(outfitBold);
                trackName.setTypeface(outfitBold);
            }
            if (outfitMedium != null) {
                textClockAmPm.setTypeface(outfitMedium);
                if (dateTopView != null) dateTopView.setTypeface(outfitMedium);
                artistName.setTypeface(outfitMedium);
                albumName.setTypeface(outfitMedium);
                factTextView.setTypeface(outfitMedium);
                tempView.setTypeface(outfitMedium);
                batteryIndicator.setTypeface(outfitMedium);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (currentFacts.size() > 1) {
            factHandler.postDelayed(factRunnable, 15000);
        }
        if (ambientGlowView != null) {
            ambientGlowView.resumeAnimation();
        }
        if (pulseAnimator != null && !pulseAnimator.isRunning()) {
            pulseAnimator.start();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        factHandler.removeCallbacks(factRunnable);
        if (ambientGlowView != null) {
            ambientGlowView.pauseAnimation();
        }
        if (pulseAnimator != null) {
            pulseAnimator.pause();
        }
    }

    private void setupBackground() {
        backgroundGradient = new GradientDrawable();
        backgroundGradient.setShape(GradientDrawable.RECTANGLE);
        backgroundGradient.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        backgroundGradient.setColors(currentColors);
        backgroundGradient.setGradientRadius(1500f);
        backgroundGradient.setGradientCenter(0.5f, 0.5f);

        // Keep the old gradient as a subtle overlay on rootView
        rootView.setBackground(backgroundGradient);

        // Gentle center-drift animation (the AmbientGlowView does the heavy lifting now)
        pulseAnimator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        pulseAnimator.setDuration(15000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new LinearInterpolator());
        pulseAnimator.addUpdateListener(anim -> {
            float phase = (float) anim.getAnimatedValue();
            float radius = 1200f + 500f * (float) Math.sin(phase);
            backgroundGradient.setGradientRadius(radius);
            float cx = 0.5f + 0.25f * (float) Math.sin(phase * 0.8f);
            float cy = 0.5f + 0.25f * (float) Math.cos(phase * 0.6f);
            backgroundGradient.setGradientCenter(cx, cy);
            rootView.invalidate();
        });
        pulseAnimator.start();
    }

    @Override
    public void onBatteryUpdate(int percent, boolean isCharging) {
        if (batteryIndicator == null) return;
        String text = percent + "%";
        if (isCharging) {
            text += "  ⚡";
        }
        batteryIndicator.setText(text);
    }

    @Override
    public void onWeatherUpdate(String tempText) {
        if (tempView != null) {
            tempView.setText(tempText);
        }
    }

    private android.graphics.Bitmap currentAlbumArtBitmap = null;

    @Override
    public void onSpotifyUpdate(String track, String artist, String album, boolean isPlaying, android.graphics.Bitmap albumArt) {
        if (!isAdded()) return;

        boolean textChanged = !track.equals(currentTrack) || !artist.equals(currentArtist);

        if (textChanged) {
            currentTrack = track;
            currentArtist = artist;

            crossfadeText(trackName, track);
            crossfadeText(artistName, artist);
            crossfadeText(albumName, album);

            // Fetch new artist facts
            factHandler.removeCallbacks(factRunnable);
            crossfadeText(factTextView, "Finding interesting facts...");
            
            SpotifyDataFetcher.fetchArtistFacts(artist, facts -> {
                if (!isAdded()) return;
                currentFacts = facts;
                factIndex = 0;
                if (!currentFacts.isEmpty()) {
                    crossfadeText(factTextView, currentFacts.get(0));
                    if (currentFacts.size() > 1) {
                        factHandler.postDelayed(factRunnable, 15000);
                    }
                }
            });
        }

        // Always check if the album art has changed, even if the track text is the same.
        // This fixes the bug where Spotify downloads the album art a few milliseconds after the track changes!
        if (albumArt != currentAlbumArtBitmap) {
            currentAlbumArtBitmap = albumArt;
            if (albumArt != null) {
                SpotifyDataFetcher.extractColorsFromBitmap(albumArt, this::animateToNewColors);
            } else {
                SpotifyDataFetcher.fetchAlbumArtAndColors(track, artist, this::animateToNewColors);
            }
        }
    }

    private void animateToNewColors(int[] newColors, android.graphics.Bitmap bitmap) {
        if (bitmap != null && albumCover != null) {
            albumCover.animate().cancel();
            if (!albumCover.isAttachedToWindow() || albumCover.getVisibility() != View.VISIBLE) {
                albumCover.setImageBitmap(bitmap);
                albumCover.setAlpha(1f);
            } else {
                albumCover.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                    albumCover.setImageBitmap(bitmap);
                    albumCover.animate().alpha(1f).setDuration(250).start();
                }).start();
            }
        }

        if (colorAnimator != null) {
            colorAnimator.cancel();
        }

        // Feed colors to the ambient glow background blobs
        if (ambientGlowView != null) {
            ambientGlowView.setColors(newColors);
        }

        int[] startColors = currentColors.clone();
        int startTextColor = currentTextColor;
        
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(newColors[0], hsv);
        hsv[1] = Math.min(0.2f, hsv[1]); // Very low saturation (almost white, just a tiny tint)
        hsv[2] = 0.95f; // Very bright, elegant off-white
        int targetTextColor = android.graphics.Color.HSVToColor(hsv);
        
        // Ensure stark contrast to make the pulsing radial gradient visible, but keep it elegant
        // Index 0 and 1: Center of gradient (Vibrant but not neon)
        // Index 2 and 3: Outer edge (Dark/Muted)
        float[] hsvTemp = new float[3];
        android.graphics.Color.colorToHSV(newColors[0], hsvTemp);
        hsvTemp[1] = Math.max(0.4f, Math.min(0.85f, hsvTemp[1] * 1.2f)); // Moderate saturation
        hsvTemp[2] = Math.max(0.4f, Math.min(0.85f, hsvTemp[2] * 1.2f)); // Moderate brightness
        newColors[0] = android.graphics.Color.HSVToColor(hsvTemp);
        
        android.graphics.Color.colorToHSV(newColors[1], hsvTemp);
        hsvTemp[1] = Math.max(0.4f, Math.min(0.8f, hsvTemp[1] * 1.1f));
        hsvTemp[2] = Math.max(0.4f, Math.min(0.8f, hsvTemp[2] * 1.1f));
        newColors[1] = android.graphics.Color.HSVToColor(hsvTemp);
        
        android.graphics.Color.colorToHSV(newColors[2], hsvTemp);
        hsvTemp[2] = Math.min(0.2f, hsvTemp[2] * 0.5f); // Very dark
        newColors[2] = android.graphics.Color.HSVToColor(hsvTemp);
        
        android.graphics.Color.colorToHSV(newColors[3], hsvTemp);
        hsvTemp[2] = Math.min(0.1f, hsvTemp[2] * 0.3f); // Even darker
        newColors[3] = android.graphics.Color.HSVToColor(hsvTemp);

        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.setDuration(1500); // Smooth 1.5s transition
        colorAnimator.addUpdateListener(anim -> {
            float fraction = anim.getAnimatedFraction();
            int[] tempColors = new int[4];
            for (int i = 0; i < 4; i++) {
                tempColors[i] = (Integer) argbEvaluator.evaluate(fraction, startColors[i], newColors[i]);
            }
            currentColors = tempColors.clone();
            backgroundGradient.setColors(currentColors);
            rootView.setBackground(backgroundGradient);
            
            // Animate and apply text colors with appropriate alphas
            currentTextColor = (Integer) argbEvaluator.evaluate(fraction, startTextColor, targetTextColor);
            int baseColor = currentTextColor & 0x00FFFFFF;
            
            if (textClockTime != null) textClockTime.setTextColor(0xFF000000 | baseColor);
            if (textClockAmPm != null) textClockAmPm.setTextColor(0xCC000000 | baseColor);
            if (dateTopView != null) dateTopView.setTextColor(0xCC000000 | baseColor);
            if (trackName != null) trackName.setTextColor(0xFF000000 | baseColor);
            if (artistName != null) artistName.setTextColor(0xE6000000 | baseColor);
            if (albumName != null) albumName.setTextColor(0xB3000000 | baseColor);
            if (factTextView != null) factTextView.setTextColor(0xCC000000 | baseColor);
            if (tempView != null) tempView.setTextColor(0xFF000000 | baseColor);
            if (batteryIndicator != null) batteryIndicator.setTextColor(0xCC000000 | baseColor);
            
            rootView.invalidate();
        });
        colorAnimator.start();
    }

    private void crossfadeText(TextView tv, String newText) {
        if (tv == null || newText == null) return;
        if (tv.getText().toString().equals(newText)) return;

        tv.animate().cancel();
        
        if (!tv.isAttachedToWindow() || tv.getVisibility() != View.VISIBLE) {
            tv.setText(newText);
            tv.setAlpha(1f);
            return;
        }

        tv.animate()
                .alpha(0f)
                .setDuration(250)
                .withEndAction(() -> {
                    tv.setText(newText);
                    tv.animate().alpha(1f).setDuration(250).start();
                })
                .start();
    }
}
