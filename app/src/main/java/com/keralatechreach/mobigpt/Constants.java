package com.keralatechreach.mobigpt;

public class Constants {
    // SharedPreferences keys
    public static final String PREF_ONBOARDING_COMPLETED = "onboarding_completed";
    
    // Onboarding constants
    public static final int ONBOARDING_PAGE_COUNT = 5;
    public static final String ONBOARDING_PREF_NAME = "MobiGPT_Prefs";
    
    // View types for RecyclerView
    public static final int VIEW_TYPE_USER_MESSAGE = 1;
    public static final int VIEW_TYPE_AI_MESSAGE = 2;
    
    // Performance optimization constants
    public static final int RECYCLERVIEW_CACHE_SIZE = 30;
    public static final int RECYCLERVIEW_POOL_SIZE = 15;
    public static final int MESSAGE_PAGINATION_SIZE = 50;
    public static final long PROGRESS_UPDATE_INTERVAL = 1500; // 1.5 seconds
    
    // Model storage optimization constants
    public static final int MAX_MODEL_FILES = 5;
    
    // Database optimization constants
    public static final int DB_CACHE_SIZE = 10000;
    public static final long DB_MMAP_SIZE = 268435456; // 256MB
    
    // Thread names
    public static final String THREAD_NAME_DOWNLOAD = "ModelDownload";
    
    private Constants() {
        // Prevent instantiation
    }
}