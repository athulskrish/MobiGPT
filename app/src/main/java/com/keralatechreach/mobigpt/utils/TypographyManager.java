package com.keralatechreach.mobigpt.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Typography Manager
 * Manages font size, font family, and monospace code formatting throughout the app
 * Provides centralized methods to apply typography settings to views
 */
public class TypographyManager {
    
    private final Context context;
    private final SettingsManager settingsManager;
    
    // Base font sizes in SP (scaled pixels)
    private static final float BASE_SIZE_SMALL = 12f;
    private static final float BASE_SIZE_MEDIUM = 14f;
    private static final float BASE_SIZE_LARGE = 16f;
    private static final float BASE_SIZE_XLARGE = 18f;
    
    // Font scale multipliers for different text types
    private static final float SCALE_TINY = 0.75f;        // For captions, timestamps
    private static final float SCALE_SMALL = 0.85f;       // For secondary text
    private static final float SCALE_NORMAL = 1.0f;       // For body text
    private static final float SCALE_MEDIUM = 1.15f;      // For subtitles
    private static final float SCALE_LARGE = 1.3f;        // For titles
    private static final float SCALE_XLARGE = 1.5f;       // For headers
    private static final float SCALE_XXLARGE = 1.75f;     // For display text
    
    public TypographyManager(Context context) {
        this.context = context;
        this.settingsManager = new SettingsManager(context);
    }
    
    /**
     * Get base font size based on user preference
     */
    public float getBaseFontSize() {
        String fontSize = settingsManager.getFontSize();
        switch (fontSize) {
            case "small":
                return BASE_SIZE_SMALL;
            case "large":
                return BASE_SIZE_LARGE;
            case "xlarge":
                return BASE_SIZE_XLARGE;
            case "medium":
            default:
                return BASE_SIZE_MEDIUM;
        }
    }
    
    /**
     * Get scaled font size for specific text type
     */
    public float getFontSize(TextType textType) {
        float baseSize = getBaseFontSize();
        float scale = getScaleForTextType(textType);
        return baseSize * scale;
    }
    
    /**
     * Get scale multiplier for text type
     */
    private float getScaleForTextType(TextType textType) {
        switch (textType) {
            case TINY:
                return SCALE_TINY;
            case SMALL:
                return SCALE_SMALL;
            case NORMAL:
                return SCALE_NORMAL;
            case MEDIUM:
                return SCALE_MEDIUM;
            case LARGE:
                return SCALE_LARGE;
            case XLARGE:
                return SCALE_XLARGE;
            case XXLARGE:
                return SCALE_XXLARGE;
            default:
                return SCALE_NORMAL;
        }
    }
    
    /**
     * Get Typeface based on user preference
     */
    public Typeface getTypeface() {
        return getTypeface(false);
    }
    
    /**
     * Get Typeface based on user preference
     * @param forceMonospace Force monospace font (for code blocks)
     */
    public Typeface getTypeface(boolean forceMonospace) {
        if (forceMonospace && settingsManager.isMonospaceCode()) {
            return Typeface.MONOSPACE;
        }
        
        String fontFamily = settingsManager.getFontFamily();
        switch (fontFamily) {
            case "roboto":
                return Typeface.DEFAULT;
            case "sans-serif":
                return Typeface.SANS_SERIF;
            case "serif":
                return Typeface.SERIF;
            case "system":
            default:
                return Typeface.DEFAULT;
        }
    }
    
    /**
     * Apply typography to a TextView
     */
    public void applyTypography(TextView textView, TextType textType) {
        applyTypography(textView, textType, false);
    }
    
    /**
     * Apply typography to a TextView
     * @param textView The TextView to apply typography to
     * @param textType The text type (determines size scaling)
     * @param isCode Whether this is code text (applies monospace if enabled)
     */
    public void applyTypography(TextView textView, TextType textType, boolean isCode) {
        if (textView == null) return;
        
        // Apply font size
        float fontSize = getFontSize(textType);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
        
        // Apply font family
        Typeface typeface = getTypeface(isCode);
        textView.setTypeface(typeface);
    }
    
    /**
     * Apply typography to an EditText
     */
    public void applyTypography(EditText editText, TextType textType) {
        if (editText == null) return;
        
        // Apply font size
        float fontSize = getFontSize(textType);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
        
        // Apply font family
        Typeface typeface = getTypeface();
        editText.setTypeface(typeface);
    }
    
    /**
     * Apply typography to a Button
     */
    public void applyTypography(Button button, TextType textType) {
        if (button == null) return;
        
        // Apply font size
        float fontSize = getFontSize(textType);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
        
        // Apply font family
        Typeface typeface = getTypeface();
        button.setTypeface(typeface);
    }
    
    /**
     * Apply typography recursively to all TextViews in a ViewGroup
     * Useful for applying to entire layouts or RecyclerView items
     */
    public void applyTypographyToViewGroup(ViewGroup viewGroup, TextType textType) {
        if (viewGroup == null) return;
        
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            
            if (child instanceof TextView) {
                applyTypography((TextView) child, textType);
            } else if (child instanceof EditText) {
                applyTypography((EditText) child, textType);
            } else if (child instanceof Button) {
                applyTypography((Button) child, textType);
            } else if (child instanceof ViewGroup) {
                // Recursively apply to nested ViewGroups
                applyTypographyToViewGroup((ViewGroup) child, textType);
            }
        }
    }
    
    /**
     * Apply typography to RecyclerView items
     * Call this in RecyclerView.Adapter's onBindViewHolder
     */
    public void applyTypographyToRecyclerView(RecyclerView recyclerView, TextType textType) {
        if (recyclerView == null) return;
        
        // Register an observer to apply typography to new items as they're bound
        recyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
                if (view instanceof ViewGroup) {
                    applyTypographyToViewGroup((ViewGroup) view, textType);
                }
            }
            
            @Override
            public void onChildViewDetachedFromWindow(View view) {
                // No action needed
            }
        });
    }
    
    /**
     * Get line spacing multiplier based on font size
     * Larger fonts need slightly more line spacing for readability
     */
    public float getLineSpacingMultiplier() {
        String fontSize = settingsManager.getFontSize();
        switch (fontSize) {
            case "small":
                return 1.1f;
            case "large":
                return 1.25f;
            case "xlarge":
                return 1.3f;
            case "medium":
            default:
                return 1.2f;
        }
    }
    
    /**
     * Apply line spacing to a TextView
     */
    public void applyLineSpacing(TextView textView) {
        if (textView == null) return;
        
        float multiplier = getLineSpacingMultiplier();
        float extraSpacing = 0; // Additional spacing in pixels
        textView.setLineSpacing(extraSpacing, multiplier);
    }
    
    /**
     * Get letter spacing based on font family
     * Some fonts benefit from slight letter spacing adjustments
     */
    public float getLetterSpacing() {
        String fontFamily = settingsManager.getFontFamily();
        switch (fontFamily) {
            case "serif":
                return 0.02f; // Slightly wider spacing for serif
            case "sans-serif":
            case "roboto":
            case "system":
            default:
                return 0.0f; // Default spacing
        }
    }
    
    /**
     * Apply letter spacing to a TextView
     */
    public void applyLetterSpacing(TextView textView) {
        if (textView == null) return;
        
        float letterSpacing = getLetterSpacing();
        textView.setLetterSpacing(letterSpacing);
    }
    
    /**
     * Apply complete typography with spacing to a TextView
     * This is a convenience method that applies font, line spacing, and letter spacing
     */
    public void applyCompleteTypography(TextView textView, TextType textType, boolean isCode) {
        applyTypography(textView, textType, isCode);
        applyLineSpacing(textView);
        applyLetterSpacing(textView);
    }
    
    /**
     * Text type enumeration for different UI elements
     * Used to determine appropriate font scaling
     */
    public enum TextType {
        TINY,       // Captions, timestamps (0.75x)
        SMALL,      // Secondary text, hints (0.85x)
        NORMAL,     // Body text, messages (1.0x)
        MEDIUM,     // Subtitles, labels (1.15x)
        LARGE,      // Titles, section headers (1.3x)
        XLARGE,     // Page headers, toolbar titles (1.5x)
        XXLARGE     // Display text, splash screens (1.75x)
    }
}
