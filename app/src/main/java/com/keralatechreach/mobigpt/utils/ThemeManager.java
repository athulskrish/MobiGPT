package com.keralatechreach.mobigpt.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Central manager for theme configuration
 * Handles theme mode, accent colors, and OLED black theme
 */
public class ThemeManager {
    
    private final Context context;
    private final SettingsManager settingsManager;
    
    public ThemeManager(Context context) {
        this.context = context;
        this.settingsManager = new SettingsManager(context);
    }
    
    /**
     * Static helper to apply theme globally at application startup.
     * Should be called from Application.onCreate() before any Activity is instantiated.
     */
    public static void applyAppTheme(Context context) {
        if (context == null) return;
        new ThemeManager(context).applyThemeMode();
    }
    
    /**
     * Apply the current theme based on settings
     * Should be called in onCreate of activities before setContentView
     */
    public void applyTheme() {
        applyThemeMode();
        applyAccentColor();
    }
    
    /**
     * Apply theme mode (light/dark/system) with support for all Android versions
     */
    public void applyThemeMode() {
        String themeMode = settingsManager.getThemeMode();
        
        switch (themeMode) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case "system":
            default:
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                } else {
                    // For Android 9 (Pie) and older devices without system-wide DayNight API
                    if (isSystemInNightMode(context)) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    } else {
                        // MODE_NIGHT_AUTO_BATTERY enables dark mode when battery saver is active
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
                    }
                }
                break;
        }
    }
    
    /**
     * Apply accent color by setting the appropriate theme
     */
    private void applyAccentColor() {
        if (!(context instanceof Activity)) {
            return;
        }
        
        Activity activity = (Activity) context;
        String accentColor = settingsManager.getAccentColor();
        boolean isOLED = settingsManager.isOLEDBlackTheme();
        boolean isDarkMode = isDarkMode();
        
        // Determine which theme to apply
        int themeResId = getThemeResourceId(accentColor, isOLED, isDarkMode);
        activity.setTheme(themeResId);
    }
    
    /**
     * Get the appropriate theme resource ID based on settings
     */
    public int getThemeResourceId(String accentColor, boolean isOLED, boolean isDarkMode) {
        // If dark mode and OLED is enabled
        if (isDarkMode && isOLED) {
            switch (accentColor) {
                case "blue":
                    return com.keralatechreach.mobigpt.R.style.Theme_MobiGPT_OLED_Blue;
                case "purple":
                    return com.keralatechreach.mobigpt.R.style.Theme_MobiGPT_OLED_Purple;
                case "orange":
                    return com.keralatechreach.mobigpt.R.style.Theme_MobiGPT_OLED_Orange;
                case "pink":
                    return com.keralatechreach.mobigpt.R.style.Theme_MobiGPT_OLED_Pink;
                case "green":
                default:
                    return com.keralatechreach.mobigpt.R.style.Theme_MobiGPT_OLED_Green;
            }
        }
        
        // Regular themes (light or dark based on system/setting)
        switch (accentColor) {
            case "blue":
                return com.keralatechreach.mobigpt.R.style.Theme_MobiGPT_Blue;
            case "purple":
                return com.keralatechreach.mobigpt.R.style.Theme_MobiGPT_Purple;
            case "orange":
                return com.keralatechreach.mobigpt.R.style.Theme_MobiGPT_Orange;
            case "pink":
                return com.keralatechreach.mobigpt.R.style.Theme_MobiGPT_Pink;
            case "green":
            default:
                return com.keralatechreach.mobigpt.R.style.Theme_MobiGPT;
        }
    }
    
    /**
     * Check if the current theme is dark mode.
     * Prioritizes the user's explicit preference over system state,
     * and accurately detects system/OEM dark mode on older Android versions.
     */
    public boolean isDarkMode() {
        String themeMode = settingsManager.getThemeMode();
        if ("dark".equals(themeMode)) {
            return true;
        } else if ("light".equals(themeMode)) {
            return false;
        } else {
            // "system"
            return isSystemInNightMode(context);
        }
    }
    
    /**
     * Check if the device/system is in dark mode, supporting standard AOSP,
     * battery saver, and OEM dark modes (Samsung One UI, MIUI) on older Android versions.
     */
    public static boolean isSystemInNightMode(Context context) {
        if (context == null) return false;
        
        // 1. Standard configuration check
        int nightModeFlags = context.getResources().getConfiguration().uiMode & 
                           Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            return true;
        }
        
        // 2. Battery saver check (especially on Android 9 and below)
        try {
            android.os.PowerManager powerManager = (android.os.PowerManager) 
                context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && powerManager.isPowerSaveMode()) {
                return true;
            }
        } catch (Exception ignored) {}
        
        // 3. OEM Dark Mode detection for older Android versions
        try {
            // Samsung One UI (Android 9 Pie)
            int samsungNightMode = android.provider.Settings.System.getInt(
                context.getContentResolver(), "display_night_mode", 0);
            if (samsungNightMode == 1) {
                return true;
            }
        } catch (Exception ignored) {}
        
        try {
            // Xiaomi MIUI (Android 8/9)
            int miuiNightMode = android.provider.Settings.Secure.getInt(
                context.getContentResolver(), "ui_night_mode", 1);
            if (miuiNightMode == 2) {
                return true;
            }
        } catch (Exception ignored) {}
        
        return false;
    }
    
    /**
     * Generate a unique theme signature representing the current effective styling.
     * Activities compare this on resume to detect if theme settings changed while in the background.
     */
    public String getThemeSignature() {
        String themeMode = settingsManager.getThemeMode();
        String accent = settingsManager.getAccentColor();
        boolean oled = settingsManager.isOLEDBlackTheme();
        boolean dark = isDarkMode();
        return themeMode + ":" + accent + ":" + oled + ":" + dark;
    }
    
    /**
     * Get the current accent color
     */
    public String getAccentColor() {
        return settingsManager.getAccentColor();
    }
    
    /**
     * Check if OLED black theme is enabled
     */
    public boolean isOLEDThemeEnabled() {
        return settingsManager.isOLEDBlackTheme();
    }
    
    /**
     * Get the primary color resource ID for current theme
     */
    public int getPrimaryColorResId() {
        String accentColor = settingsManager.getAccentColor();
        
        switch (accentColor) {
            case "blue":
                return com.keralatechreach.mobigpt.R.color.accent_blue;
            case "purple":
                return com.keralatechreach.mobigpt.R.color.accent_purple;
            case "orange":
                return com.keralatechreach.mobigpt.R.color.accent_orange_primary;
            case "pink":
                return com.keralatechreach.mobigpt.R.color.accent_pink_primary;
            case "green":
            default:
                return com.keralatechreach.mobigpt.R.color.accent_green;
        }
    }
    
    /**
     * Restart activity to apply new theme
     * Call this when theme settings change
     */
    public static void restartActivity(Activity activity) {
        activity.recreate();
    }
}
