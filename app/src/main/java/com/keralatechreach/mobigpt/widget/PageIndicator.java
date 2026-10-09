package com.keralatechreach.mobigpt.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;

/**
 * Custom professional page indicator with smooth animations
 * Features:
 * - Smooth animated transitions between pages
 * - Active indicator expansion animation
 * - Material Design style dots
 * - Customizable colors and sizes
 */
public class PageIndicator extends View {
    
    private static final int DEFAULT_DOT_COUNT = 3;
    private static final float DEFAULT_DOT_RADIUS_DP = 4f;
    private static final float DEFAULT_DOT_SPACING_DP = 12f;
    private static final float DEFAULT_ACTIVE_DOT_RADIUS_DP = 5f;
    private static final int DEFAULT_ACTIVE_COLOR = 0xFF4CAF50; // Green
    private static final int DEFAULT_INACTIVE_COLOR = 0x4D000000; // Semi-transparent black
    private static final long ANIMATION_DURATION = 300;
    
    private Paint activePaint;
    private Paint inactivePaint;
    private int dotCount = DEFAULT_DOT_COUNT;
    private float dotRadius;
    private float activeDotRadius;
    private float dotSpacing;
    private int currentPosition = 0;
    private float animatedPosition = 0f;
    
    private ValueAnimator positionAnimator;
    
    public PageIndicator(Context context) {
        this(context, null);
    }
    
    public PageIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public PageIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }
    
    private void init(Context context, AttributeSet attrs) {
        // Convert DP to pixels
        dotRadius = dpToPx(DEFAULT_DOT_RADIUS_DP);
        activeDotRadius = dpToPx(DEFAULT_ACTIVE_DOT_RADIUS_DP);
        dotSpacing = dpToPx(DEFAULT_DOT_SPACING_DP);
        
        // Initialize paints
        activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activePaint.setStyle(Paint.Style.FILL);
        activePaint.setColor(DEFAULT_ACTIVE_COLOR);
        
        inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inactivePaint.setStyle(Paint.Style.FILL);
        inactivePaint.setColor(DEFAULT_INACTIVE_COLOR);
        
        // Handle custom attributes if needed
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, 
                new int[]{android.R.attr.colorPrimary});
            try {
                // Get primary color from theme
                TypedValue typedValue = new TypedValue();
                if (context.getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true)) {
                    activePaint.setColor(typedValue.data);
                }
            } finally {
                a.recycle();
            }
        }
    }
    
    /**
     * Set the number of dots to display
     */
    public void setDotCount(int count) {
        if (count < 0) return;
        this.dotCount = count;
        requestLayout();
        invalidate();
    }
    
    /**
     * Set the current page position with smooth animation
     */
    public void setCurrentPosition(int position) {
        if (position < 0 || position >= dotCount) return;
        
        // Cancel any running animation
        if (positionAnimator != null && positionAnimator.isRunning()) {
            positionAnimator.cancel();
        }
        
        // Animate from current animated position to new position
        positionAnimator = ValueAnimator.ofFloat(animatedPosition, position);
        positionAnimator.setDuration(ANIMATION_DURATION);
        positionAnimator.setInterpolator(new DecelerateInterpolator());
        positionAnimator.addUpdateListener(animation -> {
            animatedPosition = (float) animation.getAnimatedValue();
            invalidate();
        });
        positionAnimator.start();
        
        currentPosition = position;
    }
    
    /**
     * Set the colors for active and inactive dots
     */
    public void setColors(int activeColor, int inactiveColor) {
        activePaint.setColor(activeColor);
        inactivePaint.setColor(inactiveColor);
        invalidate();
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Calculate required width: dots + spacing between them
        int width = (int) ((dotCount * activeDotRadius * 2) + ((dotCount - 1) * dotSpacing));
        width += getPaddingLeft() + getPaddingRight();
        
        // Height is just the diameter of the active dot
        int height = (int) (activeDotRadius * 2) + getPaddingTop() + getPaddingBottom();
        
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        );
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (dotCount <= 0) return;
        
        float totalWidth = (dotCount * activeDotRadius * 2) + ((dotCount - 1) * dotSpacing);
        float startX = (getWidth() - totalWidth) / 2f + activeDotRadius;
        float centerY = getHeight() / 2f;
        
        // Draw all dots
        for (int i = 0; i < dotCount; i++) {
            float dotX = startX + i * (activeDotRadius * 2 + dotSpacing);
            
            // Calculate distance from current animated position
            float distance = Math.abs(i - animatedPosition);
            
            // Interpolate radius and alpha based on distance
            float radius;
            int alpha;
            
            if (distance < 1f) {
                // Active or transitioning dot
                radius = dotRadius + (activeDotRadius - dotRadius) * (1f - distance);
                alpha = (int) (255 * (0.4f + 0.6f * (1f - distance)));
                
                // Use active color with interpolated alpha
                activePaint.setAlpha(alpha);
                canvas.drawCircle(dotX, centerY, radius, activePaint);
            } else {
                // Inactive dot
                canvas.drawCircle(dotX, centerY, dotRadius, inactivePaint);
            }
        }
    }
    
    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            getResources().getDisplayMetrics()
        );
    }
}
