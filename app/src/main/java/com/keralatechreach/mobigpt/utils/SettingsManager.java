package com.keralatechreach.mobigpt.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

/**
 * Utility class for managing app settings/preferences
 * Provides centralized access to all settings with type-safe getters
 */
public class SettingsManager {
    
    private final SharedPreferences prefs;
    
    public SettingsManager(Context context) {
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }
    
    // Downloads & Storage
    public boolean isWifiOnlyDownloads() {
        return prefs.getBoolean("wifi_only_downloads", true);
    }
    
    public boolean isAutoDownloadUpdates() {
        return prefs.getBoolean("auto_download_updates", false);
    }
    
    public boolean isDownloadNotificationsEnabled() {
        return prefs.getBoolean("download_notifications", true);
    }
    
    public boolean isDownloadCompleteSound() {
        return prefs.getBoolean("download_complete_sound", true);
    }
    
    public boolean isDownloadProgressNotification() {
        return prefs.getBoolean("download_progress_notification", true);
    }
    
    public boolean isLowStorageWarning() {
        return prefs.getBoolean("low_storage_warning", true);
    }
    
    // Chat Settings
    public boolean isDeleteOldMessages() {
        return prefs.getBoolean("delete_old_messages", false);
    }
    
    public int getDeleteMessagesAfterDays() {
        return Integer.parseInt(prefs.getString("delete_messages_after", "90"));
    }
    
    public boolean isTimestampMessages() {
        return prefs.getBoolean("timestamp_messages", true);
    }
    
    public boolean isMessageSearchHistory() {
        return prefs.getBoolean("message_search_history", true);
    }
    
    public boolean isAutoScrollNewMessages() {
        return prefs.getBoolean("auto_scroll_new_messages", true);
    }
    
    public boolean isHapticFeedback() {
        return prefs.getBoolean("haptic_feedback", true);
    }
    
    // Appearance Settings
    public String getThemeMode() {
        return prefs.getString("theme_mode", "system");
    }
    
    public String getAccentColor() {
        return prefs.getString("accent_color", "green");
    }
    
    public boolean isOLEDBlackTheme() {
        return prefs.getBoolean("oled_black_theme", false);
    }
    
    public String getFontSize() {
        return prefs.getString("font_size", "medium");
    }
    
    public String getFontFamily() {
        return prefs.getString("font_family", "system");
    }
    
    public int getMessageSpacing() {
        return prefs.getInt("message_spacing", 8);
    }
    
    public boolean isMonospaceCode() {
        return prefs.getBoolean("monospace_code", true);
    }
    
    public String getMessageBubbleStyle() {
        return prefs.getString("message_bubble_style", "rounded");
    }
    
    public boolean isShowMessageAvatars() {
        return prefs.getBoolean("show_message_avatars", true);
    }
    
    public boolean isShowTypingIndicator() {
        return prefs.getBoolean("show_typing_indicator", true);
    }
    
    public boolean isAnimateMessages() {
        return prefs.getBoolean("animate_messages", true);
    }
    
    public boolean isShowTokenCount() {
        return prefs.getBoolean("show_token_count", false);
    }
    
    // Security Settings
    public boolean isAppLockEnabled() {
        return prefs.getBoolean("app_lock_enabled", false);
    }
    
    public String getLockType() {
        return prefs.getString("lock_type", "biometric");
    }
    
    public int getLockTimeout() {
        return Integer.parseInt(prefs.getString("lock_timeout", "300"));
    }
    
    public boolean isLockOnAppSwitch() {
        return prefs.getBoolean("lock_on_app_switch", true);
    }
    
    public boolean isBiometricEnabled() {
        return prefs.getBoolean("biometric_enabled", true);
    }
    
    public boolean isFallbackToPin() {
        return prefs.getBoolean("fallback_to_pin", true);
    }
    
    public boolean isBlockScreenshots() {
        return prefs.getBoolean("block_screenshots", false);
    }
    
    public boolean isHideInRecentApps() {
        return prefs.getBoolean("hide_in_recent_apps", false);
    }
    
    public boolean isSecureKeyboard() {
        return prefs.getBoolean("secure_keyboard", false);
    }
    
    public boolean isSecureDelete() {
        return prefs.getBoolean("secure_delete", false);
    }
    
    public boolean isRequireAuthExport() {
        return prefs.getBoolean("require_auth_export", true);
    }
    
    // Accessibility Settings
    public boolean isScreenReaderOptimized() {
        return prefs.getBoolean("screen_reader_optimized", false);
    }
    
    public boolean isHighContrastMode() {
        return prefs.getBoolean("high_contrast_mode", false);
    }
    
    public int getTextScale() {
        return Integer.parseInt(prefs.getString("text_scale", "100"));
    }
    
    public boolean isBoldText() {
        return prefs.getBoolean("bold_text", false);
    }
    
    public boolean isColorBlindMode() {
        return prefs.getBoolean("color_blind_mode", false);
    }
    
    public String getColorBlindType() {
        return prefs.getString("color_blind_type", "none");
    }
    
    public int getBrightnessBoost() {
        return prefs.getInt("brightness_boost", 0);
    }
    
    public boolean isReduceMotion() {
        return prefs.getBoolean("reduce_motion", false);
    }
    
    public boolean isDisableParallax() {
        return prefs.getBoolean("disable_parallax", false);
    }
    
    public int getAnimationSpeed() {
        return prefs.getInt("animation_speed", 100);
    }
    
    public int getTouchTargetSize() {
        return prefs.getInt("touch_target_size", 48);
    }
    
    public boolean isExtraHapticFeedback() {
        return prefs.getBoolean("extra_haptic_feedback", false);
    }
    
    public boolean isAudioFeedback() {
        return prefs.getBoolean("audio_feedback", false);
    }
    
    public int getLongPressDuration() {
        return Integer.parseInt(prefs.getString("long_press_duration", "500"));
    }
    
    // About Settings
    public boolean isAutoUpdateCheck() {
        return prefs.getBoolean("auto_update_check", true);
    }

    // Appearance Setters
    public void setThemeMode(String themeMode) {
        prefs.edit().putString("theme_mode", themeMode).apply();
    }

    public void setOLEDBlackTheme(boolean oledBlackTheme) {
        prefs.edit().putBoolean("oled_black_theme", oledBlackTheme).apply();
    }
}
