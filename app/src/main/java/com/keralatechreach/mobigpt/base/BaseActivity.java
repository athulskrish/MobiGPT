package com.keralatechreach.mobigpt.base;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.keralatechreach.mobigpt.security.LockScreenActivity;
import com.keralatechreach.mobigpt.security.SecurityManager;
import com.keralatechreach.mobigpt.utils.NetworkUtils;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Base Activity class that provides proper lifecycle management,
 * resource cleanup, and lazy loading capabilities for all activities
 */
public abstract class BaseActivity extends AppCompatActivity {
    
    private static final String TAG = "BaseActivity";
    private static final int REQUEST_CODE_UNLOCK = 9001;
    
    // Security Management
    protected SecurityManager securityManager;
    private boolean isWaitingForAuth = false;
    
    // Lifecycle Management
    protected Handler mainHandler;
    
    // Network Management
    private ConnectivityManager connectivityManager;
    private NetworkCallback networkCallback;
    private boolean isNetworkCallbackRegistered = false;
    
    // Background Tasks Management
    private ExecutorService backgroundExecutor;
    private Future<?> currentBackgroundTask;
    
    // Lazy Loading State
    private boolean isViewsInitialized = false;
    private boolean isDataLoaded = false;
    private boolean isUiReady = false;
    
    // Memory Management
    private boolean isMemoryOptimized = false;
    
    // Theme Management
    protected String currentThemeSignature = null;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        
        // Apply theme before super.onCreate to ensure layout inflates with correct styling
        if (shouldApplyThemeColors()) {
            try {
                com.keralatechreach.mobigpt.utils.ThemeManager themeManager = 
                    new com.keralatechreach.mobigpt.utils.ThemeManager(this);
                themeManager.applyTheme();
                currentThemeSignature = themeManager.getThemeSignature();
            } catch (Exception e) {
                Log.w(TAG, "Error applying initial theme in onCreate", e);
            }
        }
        
        super.onCreate(savedInstanceState);
        
        // Initialize security manager
        securityManager = SecurityManager.getInstance(this);
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize background executor for lazy loading
        backgroundExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, getClass().getSimpleName() + "-Background");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        
        Log.d(TAG, "BaseActivity created: " + getClass().getSimpleName());
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        
        // Register network callback if needed
        if (needsNetworkMonitoring() && !isNetworkCallbackRegistered) {
            registerNetworkCallback();
        }
        
        Log.d(TAG, "BaseActivity started: " + getClass().getSimpleName());
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Check app lock - must be first to prevent unauthorized access
        if (shouldEnforceAppLock() && checkAndShowLockScreen()) {
            // Lock screen will be shown, pause further initialization
            return;
        }
        
        // Check if theme or night mode changed while the activity was paused/backgrounded
        if (shouldApplyThemeColors()) {
            try {
                com.keralatechreach.mobigpt.utils.ThemeManager themeManager = 
                    new com.keralatechreach.mobigpt.utils.ThemeManager(this);
                String latestThemeSignature = themeManager.getThemeSignature();
                if (currentThemeSignature != null && !currentThemeSignature.equals(latestThemeSignature)) {
                    Log.i(TAG, "Theme changed (" + currentThemeSignature + " -> " + latestThemeSignature + "). Recreating " + getClass().getSimpleName());
                    currentThemeSignature = latestThemeSignature;
                    recreate();
                    return;
                }
                currentThemeSignature = latestThemeSignature;
            } catch (Exception e) {
                Log.w(TAG, "Error verifying theme signature in onResume", e);
            }
        }
        
        // Update security interaction time
        if (securityManager != null) {
            securityManager.updateLastInteractionTime();
        }
        
        // Apply theme colors to ensure consistency
        applyUnifiedThemeColors();
        
        // Lazy load UI components if not already done
        if (!isViewsInitialized) {
            lazyInitializeViews();
        }
        
        // Restore from memory optimization
        if (isMemoryOptimized) {
            restoreFromMemoryOptimization();
        }
        
        // Load data when UI is ready
        if (isUiReady && !isDataLoaded) {
            lazyLoadData();
        }
        
        Log.d(TAG, "BaseActivity resumed: " + getClass().getSimpleName());
    }
    
    /**
     * Apply unified theme colors to ensure all UI elements are properly themed
     * This is called automatically in onResume to handle theme changes
     */
    protected void applyUnifiedThemeColors() {
        try {
            // Only apply if activity supports theming (not for LockScreenActivity etc.)
            if (shouldApplyThemeColors()) {
                com.keralatechreach.mobigpt.utils.ThemeColorUpdater themeUpdater = 
                    new com.keralatechreach.mobigpt.utils.ThemeColorUpdater(this);
                themeUpdater.applyAllThemeColors(this);
                
                Log.d(TAG, "Unified theme colors applied to " + getClass().getSimpleName());
            }
        } catch (Exception e) {
            Log.w(TAG, "Error applying unified theme colors", e);
        }
    }
    
    /**
     * Override this method to control whether automatic theme colors should be applied
     * Default is true for most activities
     */
    protected boolean shouldApplyThemeColors() {
        return true; // Apply theme colors by default
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Cancel non-essential background tasks
        cancelBackgroundTask();
        
        // Optimize memory usage
        optimizeMemoryUsage();
        
        Log.d(TAG, "BaseActivity paused: " + getClass().getSimpleName());
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        
        // Unregister network callback to save battery
        if (isNetworkCallbackRegistered) {
            unregisterNetworkCallback();
        }
        
        // More aggressive cleanup for background state
        performBackgroundCleanup();
        
        Log.d(TAG, "BaseActivity stopped: " + getClass().getSimpleName());
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cancel all background tasks
        cancelBackgroundTask();
        
        // Shutdown executor
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
        
        // Final cleanup
        performFinalCleanup();
        
        // Clear references to prevent memory leaks
        mainHandler = null;
        connectivityManager = null;
        
        Log.d(TAG, "BaseActivity destroyed: " + getClass().getSimpleName());
    }
    
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        
        switch (level) {
            case TRIM_MEMORY_RUNNING_MODERATE:
            case TRIM_MEMORY_RUNNING_LOW:
                // Light memory cleanup
                onMemoryPressure(MemoryPressureLevel.LIGHT);
                break;
                
            case TRIM_MEMORY_RUNNING_CRITICAL:
            case TRIM_MEMORY_UI_HIDDEN:
                // Moderate memory cleanup
                onMemoryPressure(MemoryPressureLevel.MODERATE);
                break;
                
            case TRIM_MEMORY_MODERATE:
            case TRIM_MEMORY_COMPLETE:
                // Aggressive memory cleanup
                onMemoryPressure(MemoryPressureLevel.CRITICAL);
                break;
        }
    }
    
    // Lazy Loading Methods
    
    /**
     * Lazy initialization of views - called only when needed
     */
    private void lazyInitializeViews() {
        if (!isViewsInitialized) {
            executeBackgroundTask(() -> {
                // Initialize heavy views in background
                onLazyInitializeViews();
                
                runOnUiThread(() -> {
                    isViewsInitialized = true;
                    isUiReady = true;
                    
                    // Load data now that UI is ready
                    if (!isDataLoaded) {
                        lazyLoadData();
                    }
                });
            });
        }
    }
    
    /**
     * Lazy loading of data - called when UI is ready
     */
    private void lazyLoadData() {
        if (isUiReady && !isDataLoaded) {
            executeBackgroundTask(() -> {
                try {
                    // Load data in background
                    onLazyLoadData();
                    
                    runOnUiThread(() -> {
                        isDataLoaded = true;
                        onDataLoaded();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error loading data", e);
                    runOnUiThread(() -> onDataLoadError(e));
                }
            });
        }
    }
    
    // Network Management
    
    /**
     * Register network callback for monitoring connectivity
     */
    private void registerNetworkCallback() {
        try {
            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                networkCallback = new NetworkCallback(this);
                
                NetworkRequest.Builder builder = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                
                connectivityManager.registerNetworkCallback(builder.build(), networkCallback);
                isNetworkCallbackRegistered = true;
                
                Log.d(TAG, "Network callback registered");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register network callback", e);
        }
    }
    
    /**
     * Unregister network callback
     */
    private void unregisterNetworkCallback() {
        try {
            if (connectivityManager != null && networkCallback != null && isNetworkCallbackRegistered) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                isNetworkCallbackRegistered = false;
                Log.d(TAG, "Network callback unregistered");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister network callback", e);
        }
    }
    
    // Background Task Management
    
    /**
     * Execute a task in background thread with proper lifecycle handling
     */
    protected void executeBackgroundTask(Runnable task) {
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            currentBackgroundTask = backgroundExecutor.submit(() -> {
                if (!isDestroyed() && !isFinishing()) {
                    task.run();
                }
            });
        }
    }
    
    /**
     * Cancel current background task
     */
    private void cancelBackgroundTask() {
        if (currentBackgroundTask != null && !currentBackgroundTask.isDone()) {
            currentBackgroundTask.cancel(true);
            Log.d(TAG, "Background task cancelled");
        }
    }
    
    // Memory Management
    
    /**
     * Optimize memory usage when activity goes to background
     */
    private void optimizeMemoryUsage() {
        if (!isMemoryOptimized) {
            onOptimizeMemory();
            isMemoryOptimized = true;
        }
    }
    
    /**
     * Restore from memory optimization when activity becomes visible
     */
    private void restoreFromMemoryOptimization() {
        if (isMemoryOptimized) {
            onRestoreFromOptimization();
            isMemoryOptimized = false;
        }
    }
    
    /**
     * Perform cleanup when going to background
     */
    private void performBackgroundCleanup() {
        onBackgroundCleanup();
    }
    
    /**
     * Perform final cleanup on destroy
     */
    private void performFinalCleanup() {
        onFinalCleanup();
    }
    
    // Memory Pressure Levels
    public enum MemoryPressureLevel {
        LIGHT,      // Clear caches, reduce memory usage slightly
        MODERATE,   // Clear more data, pause non-essential operations
        CRITICAL    // Aggressive cleanup, save only essential state
    }
    
    // Network Callback Implementation
    private static class NetworkCallback extends ConnectivityManager.NetworkCallback {
        private final WeakReference<BaseActivity> activityRef;
        
        NetworkCallback(BaseActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }
        
        @Override
        public void onAvailable(@NonNull Network network) {
            BaseActivity activity = activityRef.get();
            if (activity != null && !activity.isDestroyed()) {
                activity.runOnUiThread(() -> activity.onNetworkAvailable());
            }
        }
        
        @Override
        public void onLost(@NonNull Network network) {
            BaseActivity activity = activityRef.get();
            if (activity != null && !activity.isDestroyed()) {
                activity.runOnUiThread(() -> activity.onNetworkLost());
            }
        }
        
        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
            BaseActivity activity = activityRef.get();
            if (activity != null && !activity.isDestroyed()) {
                boolean isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                boolean isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                activity.runOnUiThread(() -> activity.onNetworkCapabilitiesChanged(isWifi, isCellular));
            }
        }
    }
    
    // Abstract and Override Methods for Subclasses
    
    /**
     * Override to provide lazy initialization of heavy views
     */
    protected void onLazyInitializeViews() {
        // Default implementation - override in subclasses
    }
    
    /**
     * Override to provide lazy loading of data
     */
    protected void onLazyLoadData() {
        // Default implementation - override in subclasses
    }
    
    /**
     * Called when data loading is complete
     */
    protected void onDataLoaded() {
        // Default implementation - override in subclasses
    }
    
    /**
     * Called when data loading fails
     */
    protected void onDataLoadError(Exception error) {
        Log.e(TAG, "Data load error", error);
    }
    
    /**
     * Override to specify if activity needs network monitoring
     */
    protected boolean needsNetworkMonitoring() {
        return false; // Default: no network monitoring
    }
    
    /**
     * Called when network becomes available
     */
    protected void onNetworkAvailable() {
        Log.d(TAG, "Network available");
    }
    
    /**
     * Called when network is lost
     */
    protected void onNetworkLost() {
        Log.d(TAG, "Network lost");
    }
    
    /**
     * Called when network capabilities change (WiFi vs Cellular)
     */
    protected void onNetworkCapabilitiesChanged(boolean isWifi, boolean isCellular) {
        Log.d(TAG, "Network capabilities changed: WiFi=" + isWifi + ", Cellular=" + isCellular);
    }
    
    /**
     * Called when memory pressure occurs
     */
    protected void onMemoryPressure(MemoryPressureLevel level) {
        Log.d(TAG, "Memory pressure: " + level);
        
        switch (level) {
            case LIGHT:
                // Clear caches, reduce memory footprint
                System.gc();
                break;
            case MODERATE:
                // More aggressive cleanup
                onOptimizeMemory();
                System.gc();
                break;
            case CRITICAL:
                // Save state and clear everything non-essential
                onOptimizeMemory();
                performBackgroundCleanup();
                System.gc();
                break;
        }
    }
    
    /**
     * Override to provide memory optimization
     */
    protected void onOptimizeMemory() {
        // Default implementation - override in subclasses
    }
    
    /**
     * Override to restore from memory optimization
     */
    protected void onRestoreFromOptimization() {
        // Default implementation - override in subclasses
    }
    
    /**
     * Override to provide background cleanup
     */
    protected void onBackgroundCleanup() {
        // Default implementation - override in subclasses
    }
    
    /**
     * Override to provide final cleanup
     */
    protected void onFinalCleanup() {
        // Default implementation - override in subclasses
    }

    // Security Methods

    /**
     * Check if this activity should enforce app lock
     * Override to disable lock for specific activities (like LockScreenActivity itself)
     */
    protected boolean shouldEnforceAppLock() {
        return true; // Default: enforce lock on all activities
    }

    /**
     * Check if lock screen should be shown and show it
     * @return true if lock screen is shown, false otherwise
     */
    private boolean checkAndShowLockScreen() {
        if (isWaitingForAuth) {
            return true; // Already waiting for authentication
        }

        if (securityManager == null) {
            return false;
        }

        if (securityManager.shouldLockApp()) {
            showLockScreen();
            return true;
        }

        return false;
    }

    /**
     * Show lock screen activity
     */
    private void showLockScreen() {
        isWaitingForAuth = true;
        Intent intent = new Intent(this, LockScreenActivity.class);
        intent.putExtra(LockScreenActivity.EXTRA_MODE, LockScreenActivity.MODE_UNLOCK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivityForResult(intent, REQUEST_CODE_UNLOCK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_UNLOCK) {
            isWaitingForAuth = false;

            if (resultCode == RESULT_OK) {
                // Authentication successful, continue with activity initialization
                Log.d(TAG, "Authentication successful");

                // Resume initialization that was paused
                if (!isViewsInitialized) {
                    lazyInitializeViews();
                }
            } else {
                // Authentication failed or cancelled, finish activity
                Log.d(TAG, "Authentication failed or cancelled");
                finish();
            }
        }
    }

    /**
     * Call this method on user interaction to update timeout
     */
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (securityManager != null) {
            securityManager.updateLastInteractionTime();
        }
    }
    
    /**
     * Helper method to update menu colors using unified theme updater
     * Call this from onPrepareOptionsMenu in activities that have menus
     */
    protected void updateMenuThemeColors(android.view.Menu menu) {
        try {
            com.keralatechreach.mobigpt.utils.ThemeColorUpdater themeUpdater = 
                new com.keralatechreach.mobigpt.utils.ThemeColorUpdater(this);
            themeUpdater.updateMenuColors(menu);
        } catch (Exception e) {
            Log.w(TAG, "Error updating menu theme colors", e);
        }
    }

    // Utility Methods

    /**
     * Check if activity is in a valid state for operations
     */
    protected boolean isActivityValid() {
        return !isDestroyed() && !isFinishing();
    }
    
    /**
     * Run on UI thread if activity is valid
     */
    protected void runOnUiThreadIfValid(Runnable action) {
        if (isActivityValid()) {
            runOnUiThread(action);
        }
    }
}