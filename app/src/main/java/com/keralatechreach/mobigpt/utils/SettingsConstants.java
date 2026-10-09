package com.keralatechreach.mobigpt.utils;

/**
 * Constants for app settings, URLs, and other configuration values.
 * 
 * ⚠️ FORK NOTICE: If you are forking this project, update ALL values below
 * with your own developer/organization information before publishing.
 */
@SuppressWarnings("unused")
public class SettingsConstants {
    
 
    
    // Developer Information
    public static final String DEVELOPER_NAME = "Kerala Tech Reach";
    public static final String DEVELOPER_EMAIL = "info@keralatechreach.in";
    
    // URLs
    public static final String WEBSITE_URL = "https://keralatechreach.com";
    
    // Play Store
    public static final String PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.keralatechreach.mobile_llm5";
    public static final String PLAY_STORE_PACKAGE = "com.keralatechreach.mobile_llm5";
    
    // Contact
    public static final String CONTACT_EMAIL = "info@keralatechreach.in";
    public static final String SUPPORT_EMAIL = "info@keralatechreach.in";
    public static final String FEEDBACK_EMAIL = "info@keralatechreach.in";
    
    // Default Share Text
    public static final String SHARE_TEXT = "Check out MobiGPT - A powerful private AI chat assistant! Runs processing locally on your device.Your chat never leaves your device. Download it from: " + PLAY_STORE_URL;
    
    /**
     * Get email subject for support
     */
    public static String getSupportEmailSubject() {
        return "MobiGPT Support Request";
    }
    
    /**
     * Get email subject for feedback
     */
    public static String getFeedbackEmailSubject() {
        return "MobiGPT Feedback";
    }
}
