package com.kushal.deskclock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * A single split-flap digit card.
 *
 * Draws a matte rounded-rect tile with a bold monospace digit centred inside.
 * A thin horizontal divider runs across the vertical midpoint, replicating the
 * mechanical split-flap boards found in airports and train stations.
 *
 * <h3>Flip animation</h3>
 * When {@link #setDigit(char)} is called with a different character the view
 * executes a two-phase 500 ms transition:
 * <ol>
 *     <li><b>Phase 1 (0–250 ms)</b> — the <em>top half</em> of the old digit
 *         rotates downward around the card's horizontal centre line, revealing
 *         the new digit's top half behind it.</li>
 *     <li><b>Phase 2 (250–500 ms)</b> — the <em>bottom half</em> of the new
 *         digit swings down from above, settling into place over the old
 *         bottom half.</li>
 * </ol>
 * 3-D rotation uses {@link Camera} / {@link Matrix} for realistic perspective
 * without requiring OpenGL.
 */
public class SplitFlapView extends View {

    // ─── Aspect ratio & animation constants ──────────────────────────────────
    private static final float ASPECT_RATIO      = 0.65f;  // width / height
    private static final long  FLIP_DURATION_MS  = 500L;
    private static final float CORNER_RADIUS_DP  = 10f;
    private static final float DIVIDER_HEIGHT_DP = 1.5f;
    private static final float SHADOW_ALPHA_MAX  = 60f;     // 0-255

    // ─── Paints ──────────────────────────────────────────────────────────────
    private final Paint cardPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flapPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ─── 3-D transform objects ───────────────────────────────────────────────
    private final Camera camera = new Camera();
    private final Matrix matrix = new Matrix();

    // ─── Geometry cache ──────────────────────────────────────────────────────
    private final RectF cardRect     = new RectF();
    private final RectF topHalfRect  = new RectF();
    private final RectF botHalfRect  = new RectF();
    private float cornerRadiusPx;
    private float dividerHeightPx;

    // ─── State ───────────────────────────────────────────────────────────────
    private char  currentChar = '0';
    private char  nextChar    = '0';
    private boolean isFlipping  = false;
    private float   flipProgress = 0f;           // 0 → 1

    // ─── Colours (theme-able) ────────────────────────────────────────────────
    private int cardColor    = 0xFF2A2A2A;        // matte dark gray
    private int textColor    = 0xFFF5F5F0;        // warm white
    private int dividerColor = 0xFF1A1A1A;        // darker gray

    private ValueAnimator flipAnimator;

    // ═════════════════════════════════════════════════════════════════════════
    // Constructors
    // ═════════════════════════════════════════════════════════════════════════

    public SplitFlapView(Context context) {
        super(context);
        init();
    }

    public SplitFlapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SplitFlapView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        cornerRadiusPx  = CORNER_RADIUS_DP * density;
        dividerHeightPx = DIVIDER_HEIGHT_DP * density;

        cardPaint.setStyle(Paint.Style.FILL);
        cardPaint.setColor(cardColor);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(textColor);
        textPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        textPaint.setAntiAlias(true);

        dividerPaint.setStyle(Paint.Style.FILL);
        dividerPaint.setColor(dividerColor);

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0x00000000);   // starts fully transparent

        flapPaint.setStyle(Paint.Style.FILL);
        flapPaint.setColor(cardColor);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Public API
    // ═════════════════════════════════════════════════════════════════════════

    /** Animate a flip to the new digit. No-op if already showing {@code c}. */
    public void setDigit(char c) {
        if (c == currentChar && !isFlipping) return;
        if (isFlipping && c == nextChar) return;

        // If already flipping, snap to completion first
        if (isFlipping && flipAnimator != null) {
            flipAnimator.cancel();
            currentChar = nextChar;
            isFlipping = false;
        }

        nextChar = c;
        startFlip();
    }

    /** Set the digit instantly without animation — used on first render. */
    public void setDigitImmediate(char c) {
        if (flipAnimator != null) flipAnimator.cancel();
        currentChar = c;
        nextChar = c;
        isFlipping = false;
        invalidate();
    }

    /** Re-colour the card for the currently active retro theme. */
    public void setColors(int cardBg, int text, int divider) {
        this.cardColor    = cardBg;
        this.textColor    = text;
        this.dividerColor = divider;

        cardPaint.setColor(cardColor);
        flapPaint.setColor(cardColor);
        textPaint.setColor(textColor);
        dividerPaint.setColor(dividerColor);
        invalidate();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Measurement — enforce width = height × ASPECT_RATIO
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int height;
        if (heightMode == MeasureSpec.EXACTLY || heightMode == MeasureSpec.AT_MOST) {
            height = heightSize;
        } else {
            height = dpToPx(200);
        }

        // Apply margins by subtracting padding
        int usableHeight = height - getPaddingTop() - getPaddingBottom();
        int width = (int) (usableHeight * ASPECT_RATIO) + getPaddingLeft() + getPaddingRight();

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cardRect.set(0, 0, w, h);
        float half = h / 2f;
        topHalfRect.set(0, 0, w, half);
        botHalfRect.set(0, half, w, h);

        // Scale text to ~60% of card height for prominence
        textPaint.setTextSize(h * 0.60f);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Drawing
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float halfH = h / 2f;

        if (!isFlipping) {
            // ── Static state ─────────────────────────────────────────────────
            drawStaticCard(canvas, currentChar, w, h, halfH);
        } else {
            drawFlippingCard(canvas, w, h, halfH);
        }
    }

    /** Draws the card at rest — single character, no animation. */
    private void drawStaticCard(Canvas canvas, char c, int w, int h, float halfH) {
        // Card background
        canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, cardPaint);

        // Digit
        drawCentredChar(canvas, c, w, h);

        // Divider line
        canvas.drawRect(0, halfH - dividerHeightPx / 2,
                         w, halfH + dividerHeightPx / 2, dividerPaint);
    }

    /** Draws the two-phase split-flap flip in progress. */
    private void drawFlippingCard(Canvas canvas, int w, int h, float halfH) {
        if (flipProgress <= 0.5f) {
            // ── Phase 1: top half flips down ─────────────────────────────────
            float phase = flipProgress / 0.5f;      // 0 → 1
            float angle = phase * 90f;               // 0° → 90°

            // Layer 1: bottom half of OLD digit (static, still visible)
            canvas.save();
            canvas.clipRect(0, halfH, w, h);
            canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, cardPaint);
            drawCentredChar(canvas, currentChar, w, h);
            canvas.restore();

            // Layer 2: top half of NEW digit (revealed behind the flap)
            canvas.save();
            canvas.clipRect(0, 0, w, halfH);
            canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, cardPaint);
            drawCentredChar(canvas, nextChar, w, h);
            canvas.restore();

            // Layer 3: animated top flap — old digit's top half folding down
            canvas.save();
            camera.save();
            camera.rotateX(angle);
            camera.getMatrix(matrix);
            camera.restore();
            matrix.preTranslate(-w / 2f, -halfH);
            matrix.postTranslate(w / 2f, halfH);
            canvas.concat(matrix);
            canvas.clipRect(0, 0, w, halfH);
            flapPaint.setColor(cardColor);
            canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, flapPaint);
            drawCentredChar(canvas, currentChar, w, h);
            canvas.restore();

            // Shadow beneath the flap for depth
            float shadowAlpha = SHADOW_ALPHA_MAX * phase;
            shadowPaint.setColor(((int) shadowAlpha << 24));
            canvas.drawRect(0, halfH, w, halfH + dpToPx(4) * phase, shadowPaint);

        } else {
            // ── Phase 2: bottom half of new digit settles ────────────────────
            float phase = (flipProgress - 0.5f) / 0.5f;   // 0 → 1
            float angle = 90f * (1f - phase);              // 90° → 0°

            // Layer 1: bottom half of OLD digit (will be covered)
            canvas.save();
            canvas.clipRect(0, halfH, w, h);
            canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, cardPaint);
            drawCentredChar(canvas, currentChar, w, h);
            canvas.restore();

            // Layer 2: top half of NEW digit (static, already revealed)
            canvas.save();
            canvas.clipRect(0, 0, w, halfH);
            canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, cardPaint);
            drawCentredChar(canvas, nextChar, w, h);
            canvas.restore();

            // Layer 3: animated bottom flap — new digit's bottom half dropping
            canvas.save();
            camera.save();
            camera.rotateX(-angle);
            camera.getMatrix(matrix);
            camera.restore();
            matrix.preTranslate(-w / 2f, -halfH);
            matrix.postTranslate(w / 2f, halfH);
            canvas.concat(matrix);
            canvas.clipRect(0, halfH, w, h);
            flapPaint.setColor(cardColor);
            canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, flapPaint);
            drawCentredChar(canvas, nextChar, w, h);
            canvas.restore();

            // Shadow above the settling flap (receding)
            float shadowAlpha = SHADOW_ALPHA_MAX * (1f - phase);
            shadowPaint.setColor(((int) shadowAlpha << 24));
            canvas.drawRect(0, halfH - dpToPx(4) * (1f - phase), w, halfH, shadowPaint);
        }

        // Divider line (always on top)
        canvas.drawRect(0, halfH - dividerHeightPx / 2,
                         w, halfH + dividerHeightPx / 2, dividerPaint);
    }

    /** Draws a single character centred in the full card rect. */
    private void drawCentredChar(Canvas canvas, char c, int w, int h) {
        String text = String.valueOf(c);
        float x = w / 2f;
        float y = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(text, x, y, textPaint);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Animation
    // ═════════════════════════════════════════════════════════════════════════

    private void startFlip() {
        isFlipping = true;
        flipProgress = 0f;

        flipAnimator = ValueAnimator.ofFloat(0f, 1f);
        flipAnimator.setDuration(FLIP_DURATION_MS);
        flipAnimator.setInterpolator(new DecelerateInterpolator(1.5f));

        flipAnimator.addUpdateListener(anim -> {
            flipProgress = (float) anim.getAnimatedValue();
            invalidate();
        });

        flipAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                currentChar = nextChar;
                isFlipping = false;
                invalidate();
            }
        });

        flipAnimator.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Utility
    // ═════════════════════════════════════════════════════════════════════════

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
