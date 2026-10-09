package com.keralatechreach.mobigpt;

import android.app.Application;
import android.util.Log;

import com.keralatechreach.mobigpt.database.DatabaseMigrationHelper;
import com.keralatechreach.mobigpt.security.SecurityManager;
import com.keralatechreach.mobigpt.utils.SettingsManager;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Custom Application class for MobiGPT
 * Handles app-wide initialization including database encryption migration
 */
public class MobiGPTApplication extends Application {
    
    private static final String TAG = "MobiGPTApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.d(TAG, "Application starting...");
        
        // Initialize theme configuration globally before any activities are created
        com.keralatechreach.mobigpt.utils.ThemeManager.applyAppTheme(this);
        
        // Log system information for debugging
        logSystemInfo();
        
        // Initialize SQLCipher native library
        try {
            System.loadLibrary("sqlcipher");
            Log.d(TAG, "SQLCipher native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load SQLCipher native library", e);
        }
        
        // Initialize security manager and register lifecycle callbacks
        initializeSecurity();
        
        // Perform database migration from unencrypted to encrypted
        // This is safe to call on every app start - it checks if migration is needed
        performDatabaseMigration();
        
        // Initialize auto-delete scheduling based on user settings
        initializeAutoDeleteScheduling();
    }
    
    /**
     * Log system information for debugging
     */
    private void logSystemInfo() {
        try {
            Log.i(TAG, "=== System Information ===");
            Log.i(TAG, "Android Version: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")");
            Log.i(TAG, "Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            Log.i(TAG, "ABI: " + android.os.Build.SUPPORTED_ABIS[0]);
            
            // Log memory information
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory() / (1024 * 1024);
            long totalMemory = runtime.totalMemory() / (1024 * 1024);
            long freeMemory = runtime.freeMemory() / (1024 * 1024);
            
            Log.i(TAG, "Max Memory: " + maxMemory + " MB");
            Log.i(TAG, "Total Memory: " + totalMemory + " MB");
            Log.i(TAG, "Free Memory: " + freeMemory + " MB");
            
            // Check for 16KB page size and modern Android version support
            if (android.os.Build.VERSION.SDK_INT >= 36) {
                Log.i(TAG, "Android version: Android 16+ (API 36+) - Full 16KB page support");
            } else if (android.os.Build.VERSION.SDK_INT >= 35) {
                Log.i(TAG, "16KB page size support: Available (Android 15+)");
            } else {
                Log.i(TAG, "16KB page size support: Not available (Android < 15)");
            }
            
            Log.i(TAG, "=========================");
        } catch (Exception e) {
            Log.w(TAG, "Error logging system info", e);
        }
    }
    
    /**
     * Initialize security manager with lifecycle tracking
     */
    private void initializeSecurity() {
        try {
            SecurityManager securityManager = SecurityManager.getInstance(this);
            securityManager.initialize(this);
            Log.d(TAG, "Security manager initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing security manager", e);
        }
    }
    
    /**
     * Migrate database from unencrypted to encrypted format if needed
     */
    private void performDatabaseMigration() {
        new Thread(() -> {
            try {
                boolean success = DatabaseMigrationHelper.migrateIfNeeded(this);
                if (success) {
                    Log.d(TAG, "Database is secure and encrypted");
                } else {
                    Log.e(TAG, "Database migration encountered an issue");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during database migration", e);
            }
        }).start();
    }
    
    /**
     * Initialize auto-delete scheduling based on user settings
     * This ensures the auto-delete work is scheduled even if the user hasn't opened settings
     */
    private void initializeAutoDeleteScheduling() {
        try {
            SettingsManager settingsManager = new SettingsManager(this);
            boolean autoDeleteEnabled = settingsManager.isDeleteOldMessages();
            
            WorkManager workManager = WorkManager.getInstance(this);
            
            if (autoDeleteEnabled) {
                // Schedule periodic work to delete old messages
                PeriodicWorkRequest deleteWorkRequest = 
                    new PeriodicWorkRequest.Builder(
                        com.keralatechreach.mobigpt.workers.DeleteOldMessagesWorker.class,
                        1, // repeat every 1 day
                        TimeUnit.DAYS,
                        12, // with 12 hours flex period
                        TimeUnit.HOURS)
                    .setConstraints(new Constraints.Builder()
                        .setRequiresBatteryNotLow(true) // Only run when battery is not low
                        .build())
                    .addTag("auto_delete_messages")
                    .build();
                
                // Use unique work to avoid duplicate scheduling
                workManager.enqueueUniquePeriodicWork(
                    "auto_delete_old_messages",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    deleteWorkRequest
                );
                
                Log.i(TAG, "Auto-delete work initialized and scheduled");
            } else {
                // Cancel the scheduled work if it exists
                workManager.cancelUniqueWork("auto_delete_old_messages");
                Log.i(TAG, "Auto-delete is disabled");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing auto-delete scheduling", e);
        }
    }
}
