package com.kushal.deskclock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Custom View that renders multiple soft, overlapping color blobs
 * that drift around the screen in a lava-lamp / aurora borealis style.
 * Each blob is a radial gradient from a vibrant color to transparent,
 * painted with additive-like blending for a soft, organic feel.
 *
 * The colors are derived from the current Spotify album art palette.
 */
public class AmbientGlowView extends View {

    private static final int NUM_BLOBS = 4;

    // Each blob has: centerX ratio, centerY ratio, radius, color, phase offset
    private final float[] blobPhaseOffsets = {0f, 1.5f, 3.0f, 4.5f};
    private final float[] blobSpeedX = {0.7f, -0.5f, 0.9f, -0.6f};
    private final float[] blobSpeedY = {0.5f, 0.8f, -0.6f, 0.4f};
    private final float[] blobRadiusMultiplier = {0.7f, 0.55f, 0.65f, 0.5f};

    private int[] blobColors = {0x80E91E63, 0x809C27B0, 0x80673AB7, 0x803F51B5};
    private int[] targetBlobColors = null;
    private int[] startBlobColors = null;
    private float colorTransitionFraction = 1f;

    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator driftAnimator;
    private ValueAnimator colorTransitionAnimator;
    private float driftPhase = 0f;

    private boolean isAnimating = false;
    private long lastDrawTime = 0;

    public AmbientGlowView(Context context) {
        super(context);
        init();
    }

    public AmbientGlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AmbientGlowView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null); // Required for RadialGradient on some devices
        isAnimating = true;
    }

    /**
     * Smoothly transition the blob colors to new album-derived colors.
     */
    public void setColors(int[] newColors) {
        if (newColors == null || newColors.length < 4) return;

        // Make soft, semi-transparent versions of the colors
        int[] softColors = new int[4];
        for (int i = 0; i < 4; i++) {
            float[] hsv = new float[3];
            Color.colorToHSV(newColors[i], hsv);
            hsv[1] = Math.max(0.4f, Math.min(0.8f, hsv[1])); // Keep moderate saturation
            hsv[2] = Math.max(0.5f, Math.min(0.85f, hsv[2])); // Keep visible but not blinding
            int rgb = Color.HSVToColor(hsv);
            softColors[i] = Color.argb(120, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
        }

        startBlobColors = blobColors.clone();
        targetBlobColors = softColors;
        colorTransitionFraction = 0f;

        if (colorTransitionAnimator != null) colorTransitionAnimator.cancel();
        colorTransitionAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorTransitionAnimator.setDuration(2000);
        colorTransitionAnimator.addUpdateListener(anim -> {
            colorTransitionFraction = (float) anim.getAnimatedValue();
            for (int i = 0; i < 4; i++) {
                blobColors[i] = blendColor(startBlobColors[i], targetBlobColors[i], colorTransitionFraction);
            }
            if (!isAnimating) {
                postInvalidateOnAnimation();
            }
        });
        colorTransitionAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        long now = android.os.SystemClock.uptimeMillis();
        if (lastDrawTime == 0) lastDrawTime = now;
        long dt = now - lastDrawTime;
        lastDrawTime = now;

        if (isAnimating) {
            // 20000 ms per full orbit (2 * PI)
            driftPhase += (dt / 20000f) * (float) (Math.PI * 2);
            if (driftPhase > Math.PI * 2) {
                driftPhase -= (float) (Math.PI * 2);
            }
        }

        // Draw black base
        canvas.drawColor(0xFF0A0A0A);

        float maxDim = Math.max(w, h);

        for (int i = 0; i < NUM_BLOBS; i++) {
            float phase = driftPhase + blobPhaseOffsets[i];

            // Lissajous-like drift pattern for each blob
            float cx = w * (0.5f + 0.35f * (float) Math.sin(phase * blobSpeedX[i]));
            float cy = h * (0.5f + 0.35f * (float) Math.cos(phase * blobSpeedY[i]));

            // Gently breathing radius
            float baseRadius = maxDim * blobRadiusMultiplier[i];
            float radius = baseRadius + baseRadius * 0.15f * (float) Math.sin(phase * 1.3f + i);

            // Create radial gradient: album color → transparent
            int color = blobColors[i];
            RadialGradient gradient = new RadialGradient(
                    cx, cy, radius,
                    new int[]{color, adjustAlpha(color, 0.4f), 0x00000000},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP);

            blobPaint.setShader(gradient);
            canvas.drawCircle(cx, cy, radius, blobPaint);
        }

        if (isAnimating) {
            postInvalidateOnAnimation();
        }
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = (int) (Color.alpha(color) * factor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int blendColor(int from, int to, float fraction) {
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * fraction);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * fraction);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * fraction);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction);
        return Color.argb(a, r, g, b);
    }

    public void resumeAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            lastDrawTime = 0; // Reset time so we don't jump
            postInvalidateOnAnimation();
        }
    }

    public void pauseAnimation() {
        isAnimating = false;
    }
}
