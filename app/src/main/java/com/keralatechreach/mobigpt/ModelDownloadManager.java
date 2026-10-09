package com.keralatechreach.mobigpt;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.keralatechreach.mobigpt.security.SecurePreferences;
import com.keralatechreach.mobigpt.utils.NetworkUtils;
import com.keralatechreach.mobigpt.utils.SecureNetworkManager;
import com.keralatechreach.mobigpt.notifications.DownloadNotificationManager;
import com.keralatechreach.mobigpt.notifications.DownloadNotificationReceiver;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.HttpsURLConnection;

/**
 * Enhanced Download Manager for LLM models with robust pause/resume functionality
 * Features:
 * - Seamless pause/resume from exact download position
 * - Persistent download state across app restarts using encrypted preferences
 * - File integrity verification
 * - Network error recovery
 * - Progress tracking with speed calculation
 * - Lifecycle-aware resource management
 */
public class ModelDownloadManager {
    
    private static final String TAG = "ModelDownloadManager";
    
    // Download states
    public enum DownloadState {
        IDLE,           // No download in progress
        STARTING,       // Download initialization
        DOWNLOADING,    // Active download
        PAUSED,         // Download paused by user
        COMPLETED,      // Download completed successfully
        FAILED,         // Download failed
        CANCELLED       // Download cancelled by user
    }
    
    public interface DownloadListener {
        void onDownloadStarted(String modelName);
        void onDownloadProgress(String modelName, int progress, long downloadedBytes, long totalBytes, long speed);
        void onDownloadCompleted(String modelName, File file);
        void onDownloadFailed(String modelName, String error);
        void onDownloadCancelled(String modelName);
        void onDownloadPaused(String modelName);
        void onDownloadResumed(String modelName);
        void onCustomModelSizeUpdated(String modelName, long fileSizeBytes); // New callback for size updates
    }
    
    // Enhanced download session tracking
    private static class DownloadSession {
        final ModelConfig.Model model;
        final File targetFile;
        final File tempFile;
        volatile DownloadState state;
        volatile long totalBytes;
        volatile long downloadedBytes;
        volatile long startTime;
        volatile long lastProgressTime;
        volatile long lastDownloadedBytes;
        volatile boolean shouldStop;
        volatile String error;
        
        // Retry mechanism
        volatile int retryCount = 0;
        volatile int maxRetries = 8;
        volatile long lastRetryTime = 0;
        volatile long retryDelayMs = 1000; // Start with 1 second
        
        // Network health tracking
        volatile long lastSuccessfulReadTime = 0;
        volatile int consecutiveTimeouts = 0;
        volatile int bufferSize = 8192; // Adaptive buffer size
        
        // Progress tracking
        private final AtomicLong speedBytesPerSecond = new AtomicLong(0);
        private final AtomicBoolean isProgressMonitoringActive = new AtomicBoolean(false);
        
        DownloadSession(ModelConfig.Model model, File targetFile, File tempFile) {
            this.model = model;
            this.targetFile = targetFile;
            this.tempFile = tempFile;
            this.state = DownloadState.IDLE;
            this.totalBytes = model.fileSizeBytes;
            this.downloadedBytes = 0;
            this.startTime = System.currentTimeMillis();
            this.lastProgressTime = startTime;
            this.lastDownloadedBytes = 0;
            this.shouldStop = false;
        }
        
        void updateProgress(long downloaded) {
            long currentTime = System.currentTimeMillis();
            long timeDiff = currentTime - lastProgressTime;
            
            if (timeDiff > 1000) { // Update speed every second
                long bytesDiff = downloaded - lastDownloadedBytes;
                speedBytesPerSecond.set(bytesDiff * 1000 / timeDiff);
                lastProgressTime = currentTime;
                lastDownloadedBytes = downloaded;
            }
            
            downloadedBytes = downloaded;
        }
        
        long getSpeed() {
            return speedBytesPerSecond.get();
        }
        
        int getProgressPercentage() {
            if (totalBytes <= 0) return 0;
            return (int) Math.min(100, (downloadedBytes * 100L) / totalBytes);
        }
        
        void saveState(SharedPreferences prefs) {
            SharedPreferences.Editor editor = prefs.edit();
            String prefix = "download_" + model.name + "_";
            
            editor.putString(prefix + "state", state.name());
            editor.putLong(prefix + "total_bytes", totalBytes);
            editor.putLong(prefix + "downloaded_bytes", downloadedBytes);
            editor.putLong(prefix + "start_time", startTime);
            editor.putString(prefix + "target_file", targetFile.getAbsolutePath());
            editor.putString(prefix + "temp_file", tempFile.getAbsolutePath());
            editor.putString(prefix + "download_url", model.downloadUrl);
            editor.putInt(prefix + "retry_count", retryCount);
            editor.putLong(prefix + "last_retry_time", lastRetryTime);
            
            if (error != null) {
                editor.putString(prefix + "error", error);
            } else {
                editor.remove(prefix + "error");
            }
            
            editor.apply();
        }
        
        static DownloadSession loadState(SharedPreferences prefs, ModelConfig.Model model) {
            String prefix = "download_" + model.name + "_";
            
            String stateStr = prefs.getString(prefix + "state", null);
            if (stateStr == null) return null;
            
            try {
                DownloadState state = DownloadState.valueOf(stateStr);
                String targetPath = prefs.getString(prefix + "target_file", null);
                String tempPath = prefs.getString(prefix + "temp_file", null);
                
                if (targetPath == null || tempPath == null) return null;
                
                File targetFile = new File(targetPath);
                File tempFile = new File(tempPath);
                
                DownloadSession session = new DownloadSession(model, targetFile, tempFile);
                session.state = state;
                session.totalBytes = prefs.getLong(prefix + "total_bytes", model.fileSizeBytes);
                session.downloadedBytes = prefs.getLong(prefix + "downloaded_bytes", 0);
                session.startTime = prefs.getLong(prefix + "start_time", System.currentTimeMillis());
                session.error = prefs.getString(prefix + "error", null);
                session.retryCount = prefs.getInt(prefix + "retry_count", 0);
                session.lastRetryTime = prefs.getLong(prefix + "last_retry_time", 0);
                
                return session;
            } catch (Exception e) {
                Log.e(TAG, "Error loading download state for " + model.name, e);
                return null;
            }
        }
        
        void clearState(SharedPreferences prefs) {
            SharedPreferences.Editor editor = prefs.edit();
            String prefix = "download_" + model.name + "_";
            
            editor.remove(prefix + "state");
            editor.remove(prefix + "total_bytes");
            editor.remove(prefix + "downloaded_bytes");
            editor.remove(prefix + "start_time");
            editor.remove(prefix + "target_file");
            editor.remove(prefix + "temp_file");
            editor.remove(prefix + "download_url");
            editor.remove(prefix + "error");
            editor.remove(prefix + "retry_count");
            editor.remove(prefix + "last_retry_time");
            
            editor.apply();
        }
    }
    
    private static volatile ModelDownloadManager instance;
    
    public static ModelDownloadManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ModelDownloadManager.class) {
                if (instance == null) {
                    instance = new ModelDownloadManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }
    
    private final Context context;
    private ExecutorService downloadExecutor;
    private ExecutorService progressExecutor;
    private final Handler mainHandler;
    private final SecurePreferences securePreferences;
    private final SharedPreferences downloadPrefs; // For compatibility
    private final ConcurrentHashMap<String, DownloadSession> activeSessions;
    private final DownloadNotificationManager notificationManager;
    
    private DownloadListener listener;
    private volatile DownloadSession currentSession;
    
    // Lifecycle management
    private volatile boolean isActive = true;
    private volatile boolean isOptimized = false;
    
    // WiFi requirement override - when user explicitly chooses to use mobile data
    private volatile boolean wiFiRequirementOverridden = false;
    
    private synchronized ExecutorService getDownloadExecutor() {
        if (downloadExecutor == null || downloadExecutor.isShutdown() || downloadExecutor.isTerminated()) {
            downloadExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, Constants.THREAD_NAME_DOWNLOAD);
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            });
        }
        return downloadExecutor;
    }
    
    private synchronized ExecutorService getProgressExecutor() {
        if (progressExecutor == null || progressExecutor.isShutdown() || progressExecutor.isTerminated()) {
            progressExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, Constants.THREAD_NAME_DOWNLOAD + "-Progress");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });
        }
        return progressExecutor;
    }
    
    public ModelDownloadManager(Context context) {
        this.context = context.getApplicationContext();
        // Use encrypted SharedPreferences with automatic migration
        this.securePreferences = SecurePreferences.createWithMigration(this.context, "model_downloads_v2");
        this.downloadPrefs = this.securePreferences.getPreferences();
        this.activeSessions = new ConcurrentHashMap<>();
        
        // Ensure thread pools are initialized
        getDownloadExecutor();
        getProgressExecutor();
        
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize notification manager
        this.notificationManager = new DownloadNotificationManager(this.context);
        
        // Set up notification receiver to work with this download manager
        DownloadNotificationReceiver.setDownloadManager(this);
        
        // Restore any previous download sessions
        restoreDownloadSessions();
    }
    
    public void setDownloadListener(DownloadListener listener) {
        this.listener = listener;
    }
    
    /**
     * Set WiFi requirement override - allows downloading on mobile data when user explicitly chooses
     * @param override true to bypass WiFi requirements, false to enforce them
     */
    public void setWiFiRequirementOverride(boolean override) {
        this.wiFiRequirementOverridden = override;
        Log.d(TAG, "WiFi requirement override set to: " + override);
    }
    
    /**
     * Clear WiFi requirement override - resets to default WiFi checking behavior
     */
    public void clearWiFiRequirementOverride() {
        this.wiFiRequirementOverridden = false;
        Log.d(TAG, "WiFi requirement override cleared");
    }
    
    /**
     * Check WiFi requirement for downloads based on user settings
     * @return true if download can proceed, false if WiFi is required but not available
     */
    private boolean checkWiFiRequirementForDownload() {
        try {
            // If user has explicitly overridden WiFi requirement, allow download
            if (wiFiRequirementOverridden) {
                Log.d(TAG, "WiFi requirement overridden by user - allowing download on any network");
                return true;
            }
            
            // Get user preferences
            SharedPreferences userPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean wifiOnlyEnabled = userPrefs.getBoolean("wifi_only_downloads", true);
            
            Log.d(TAG, "WiFi-only downloads enabled: " + wifiOnlyEnabled);
            
            // If WiFi-only is disabled, allow download on any network
            if (!wifiOnlyEnabled) {
                Log.d(TAG, "WiFi-only disabled, allowing download");
                return true;
            }
            
            // Check if WiFi is connected
            boolean isWiFiConnected = NetworkUtils.isWiFiConnected(context);
            boolean isNetworkAvailable = NetworkUtils.isNetworkAvailable(context);
            
            Log.d(TAG, "WiFi connected: " + isWiFiConnected + ", Network available: " + isNetworkAvailable);
            
            // If WiFi is connected, proceed with download
            if (isWiFiConnected) {
                Log.d(TAG, "WiFi connected, download can proceed");
                return true;
            }
            
            // WiFi is required but not connected
            Log.w(TAG, "WiFi required but not connected. Network available: " + isNetworkAvailable);
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking WiFi requirement", e);
            // On error, be conservative and allow the download
            return true;
        }
    }
    
    /**
     * Get an appropriate error message when WiFi requirement is not met
     * @return User-friendly error message explaining the WiFi requirement
     */
    private String getWiFiRequirementErrorMessage() {
        try {
            boolean isNetworkAvailable = NetworkUtils.isNetworkAvailable(context);
            boolean isWiFiConnected = NetworkUtils.isWiFiConnected(context);
            boolean isMobileConnected = NetworkUtils.isMobileDataConnected(context);
            
            if (!isNetworkAvailable) {
                return "No internet connection available. Please check your network settings.";
            } else if (isMobileConnected && !isWiFiConnected) {
                return "WiFi-only downloads is enabled. Please connect to WiFi or change download settings.";
            } else {
                return "WiFi connection required for downloads. Please check your connection.";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating WiFi error message", e);
            return "WiFi connection required for downloads. Please check your settings and connection.";
        }
    }
    
    /**
     * Restore download sessions from persistent storage
     */
    private void restoreDownloadSessions() {
        try {
            getDownloadExecutor().execute(() -> {
                try {
                    for (ModelConfig.Model model : ModelConfig.getAvailableModels()) {
                        DownloadSession session = DownloadSession.loadState(downloadPrefs, model);
                        if (session != null) {
                            Log.d(TAG, "Restored session for " + model.name + " in state: " + session.state);
                            activeSessions.put(model.name, session);
                            
                            // Check if download is actually complete despite the saved state
                            if (session.downloadedBytes >= session.totalBytes && session.totalBytes > 0) {
                                File targetFile = getModelFile(session.model);
                                if (targetFile.exists() && targetFile.length() >= session.totalBytes * 0.95) {
                                    Log.d(TAG, "Session shows complete download, cleaning up state for " + model.name);
                                    session.clearState(downloadPrefs);
                                    activeSessions.remove(model.name);
                                    continue;
                                } else if (session.tempFile.exists() && session.tempFile.length() >= session.totalBytes * 0.95) {
                                    Log.d(TAG, "Temp file complete, finishing download for " + model.name);
                                    // Move temp file to final location in background
                                    try {
                                        if (targetFile.exists()) targetFile.delete();
                                        if (session.tempFile.renameTo(targetFile)) {
                                            session.clearState(downloadPrefs);
                                            activeSessions.remove(model.name);
                                            continue;
                                        }
                                    } catch (Exception e) {
                                        Log.w(TAG, "Failed to complete download for " + model.name, e);
                                    }
                                }
                            }
                            
                            // If session was downloading when app was closed, mark as paused
                            if (session.state == DownloadState.DOWNLOADING || session.state == DownloadState.STARTING) {
                                session.state = DownloadState.PAUSED;
                                session.saveState(downloadPrefs);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error restoring download sessions", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule restoreDownloadSessions", e);
        }
    }
    
    /**
     * Start downloading a model
     */
    public void downloadModel(ModelConfig.Model model) {
        if (model == null) {
            Log.e(TAG, "downloadModel called with null model");
            notifyError("Model cannot be null");
            return;
        }
        
        Log.d(TAG, "=== DOWNLOAD START === Model: " + model.displayName + " (" + model.name + ")");
        Log.d(TAG, "Download URL: " + model.downloadUrl);
        Log.d(TAG, "Expected size: " + (model.fileSizeBytes > 0 ? formatBytes(model.fileSizeBytes) : "Unknown (custom URL)"));
        Log.d(TAG, "Is custom URL: " + model.isCustomUrl);
        
        // Check WiFi requirement before starting download
        if (!checkWiFiRequirementForDownload()) {
            Log.w(TAG, "WiFi requirement not met, cannot start download for " + model.name);
            
            // Provide more detailed error message based on network status
            String errorMessage = getWiFiRequirementErrorMessage();
            notifyError(errorMessage);
            return;
        }
        
        // Check if this model is already downloading
        DownloadSession existingSession = activeSessions.get(model.name);
        if (existingSession != null && existingSession.state == DownloadState.DOWNLOADING) {
            Log.w(TAG, "Download already in progress for " + model.name + 
                  " - downloaded: " + formatBytes(existingSession.downloadedBytes) + 
                  "/" + formatBytes(existingSession.totalBytes));
            notifyError("Download already in progress for " + model.displayName);
            return;
        }
        
        // Cancel any existing session for this model (PAUSED, FAILED, etc.)
        if (existingSession != null) {
            Log.d(TAG, "Cancelling existing session in state: " + existingSession.state + 
                  ", downloaded: " + formatBytes(existingSession.downloadedBytes));
            cancelDownload(model.name);
            // Wait a bit for cleanup
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Thread interrupted during cleanup wait");
            }
        }
        
        // Check storage space - skip for custom URLs where size is unknown
        if (!model.isCustomUrl && model.fileSizeBytes > 0) {
            long availableSpace = getAvailableSpace();
            long requiredSpace = model.fileSizeBytes + (100 * 1024 * 1024);
            Log.d(TAG, "Storage check - Available: " + formatBytes(availableSpace) + 
                  ", Required: " + formatBytes(requiredSpace));
            if (!hasEnoughSpace(model)) {
                Log.e(TAG, "Insufficient storage space for " + model.displayName);
                notifyError("Insufficient storage space for " + model.displayName);
                return;
            }
        } else {
            Log.d(TAG, "Skipping storage check for custom URL - size will be determined from server");
        }
        
        try {
            getDownloadExecutor().execute(() -> {
                try {
                    Log.d(TAG, "Download executor started for " + model.name);
                    
                    // Prepare download session
                    File modelsDir = getModelsDirectory();
                    Log.d(TAG, "Models directory: " + modelsDir.getAbsolutePath());
                    
                    if (!ensureDirectoryExists(modelsDir)) {
                        Log.e(TAG, "Failed to create models directory: " + modelsDir.getAbsolutePath());
                        notifyError("Cannot create models directory");
                        return;
                    }
                    
                    File targetFile = new File(modelsDir, model.fileName);
                    File tempFile = new File(modelsDir, model.fileName + ".tmp");
                    
                    Log.d(TAG, "Target file: " + targetFile.getAbsolutePath());
                    Log.d(TAG, "Temp file: " + tempFile.getAbsolutePath());
                    
                    DownloadSession session = new DownloadSession(model, targetFile, tempFile);
                    activeSessions.put(model.name, session);
                    currentSession = session;
                    
                    Log.d(TAG, "Download session created and set as current session");
                    
                    // Check if partially downloaded file exists
                    if (tempFile.exists()) {
                        session.downloadedBytes = tempFile.length();
                        Log.d(TAG, "Found partial download: " + formatBytes(session.downloadedBytes) + 
                              " (" + session.getProgressPercentage() + "%)");
                    } else {
                        Log.d(TAG, "No partial download found, starting fresh");
                    }
                    
                    session.state = DownloadState.STARTING;
                    session.saveState(downloadPrefs);
                    
                    Log.d(TAG, "Session state saved as STARTING");
                    
                    // Notify download started
                    mainHandler.post(() -> {
                        if (listener != null) {
                            Log.d(TAG, "Notifying listener: onDownloadStarted");
                            listener.onDownloadStarted(model.name);
                        } else {
                            Log.w(TAG, "No listener to notify for download started");
                        }
                        
                        // Show notification
                        notificationManager.showDownloadStarted(model.name, model.displayName);
                    });
                    
                    // Start the actual download
                    Log.d(TAG, "Calling startDownload...");
                    startDownload(session);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error starting download for " + model.name, e);
                    String exceptionName = "Unknown";
                    try {
                        if (e != null && e.getClass() != null) {
                            exceptionName = e.getClass().getName();
                        }
                    } catch (Exception ex) {
                        // Ignore
                    }
                    Log.e(TAG, "Exception details: " + exceptionName + " - " + e.getMessage());
                    notifyError("Failed to start download: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to submit download task to executor for " + model.name, e);
            notifyError("Failed to start download: " + e.getMessage());
        }
    }
    
    /**
     * Perform the actual download with resume capability and retry logic
     */
    private void startDownload(DownloadSession session) {
        Log.d(TAG, "=== START DOWNLOAD === " + session.model.name);
        Log.d(TAG, "Starting from: " + formatBytes(session.downloadedBytes) + 
              "/" + formatBytes(session.totalBytes) + " (" + session.getProgressPercentage() + "%)");
        
        session.state = DownloadState.DOWNLOADING;
        session.lastSuccessfulReadTime = System.currentTimeMillis();
        session.saveState(downloadPrefs);
        
        Log.d(TAG, "State saved as DOWNLOADING, starting progress monitoring");
        startProgressMonitoring(session);
        
        HttpURLConnection connection = null;
        try {
            // Validate download URL is HTTPS for security
            if (!SecureNetworkManager.isSecureUrl(session.model.downloadUrl)) {
                Log.w(TAG, "WARNING: Download URL is not HTTPS. Security may be compromised.");
            }

            // Create secure connection using SecureNetworkManager
            // NOTE: This does NOT call connect() - we need to set all properties first
            connection = SecureNetworkManager.createConnection(session.model.downloadUrl);

            // Adaptive timeouts based on network quality
            int connectTimeout = 30000 + (session.consecutiveTimeouts * 5000); // Increase timeout after failures
            int readTimeout = 45000 + (session.consecutiveTimeouts * 10000);
            SecureNetworkManager.setTimeouts(connection,
                Math.min(connectTimeout, 60000),  // Max 60 seconds
                Math.min(readTimeout, 120000));   // Max 2 minutes

            // Set request headers for resume capability (MUST be BEFORE connecting)
            if (session.downloadedBytes > 0) {
                SecureNetworkManager.configureResume(connection, session.downloadedBytes);
                Log.d(TAG, "Resuming download from byte: " + session.downloadedBytes);
            }

            // Set request method (MUST be BEFORE connecting)
            connection.setRequestMethod("GET");
            
            // NOW connect and get response code
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode + " for " + session.model.name);
            
            // Validate certificate pinning after connection is established
            if (connection instanceof HttpsURLConnection) {
                SecureNetworkManager.validateCertificatePinning((HttpsURLConnection) connection);
            }
            
            // Check for valid response codes
            if (responseCode == 416) { // Range Not Satisfiable - download already complete
                Log.d(TAG, "HTTP 416 received - download already complete for " + session.model.name);
                
                // Check if file is actually complete
                if (session.tempFile.exists() && session.tempFile.length() >= session.totalBytes * 0.95) {
                    Log.d(TAG, "Temp file exists and is complete, finishing download");
                    handleDownloadComplete(session);
                    return;
                } else {
                    Log.w(TAG, "HTTP 416 but file incomplete, resetting download");
                    session.downloadedBytes = 0;
                    if (session.tempFile.exists()) {
                        session.tempFile.delete();
                    }
                    
                    // Disconnect and cleanup the current connection FIRST
                    connection.disconnect();
                    connection = null;
                    
                    // Retry without range header - create completely new connection
                    // IMPORTANT: Must set all properties BEFORE calling getResponseCode()
                    connection = SecureNetworkManager.createConnection(session.model.downloadUrl);
                    
                    // Set timeouts BEFORE connecting
                    SecureNetworkManager.setTimeouts(connection,
                        Math.min(connectTimeout, 60000),
                        Math.min(readTimeout, 120000));
                    
                    // Set request method BEFORE connecting
                    connection.setRequestMethod("GET");
                    
                    // NOW connect and get response code
                    responseCode = connection.getResponseCode();
                    Log.d(TAG, "Retry response code after 416: " + responseCode);
                    
                    // Validate certificate pinning after connection is established
                    if (connection instanceof HttpsURLConnection) {
                        SecureNetworkManager.validateCertificatePinning((HttpsURLConnection) connection);
                    }
                }
            }
            
            if (responseCode != HttpURLConnection.HTTP_OK && 
                responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("Server returned HTTP " + responseCode);
            }
            
            // Get content length
            String contentLengthStr = connection.getHeaderField("Content-Length");
            if (contentLengthStr != null) {
                long contentLength = Long.parseLong(contentLengthStr);
                if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    session.totalBytes = session.downloadedBytes + contentLength;
                } else {
                    session.totalBytes = contentLength;
                    // If not resuming but we have data, server doesn't support resume - start over
                    if (session.downloadedBytes > 0) {
                        Log.w(TAG, "Server doesn't support resume, starting download from beginning");
                        session.downloadedBytes = 0;
                        if (session.tempFile.exists()) {
                            session.tempFile.delete();
                        }
                    }
                }
                
                // Update custom model with file size if it's a custom URL
                if (session.model.isCustomUrl && session.model.fileSizeBytes <= 0) {
                    Log.d(TAG, "Updating custom model size: " + session.model.name + " -> " + session.totalBytes + " bytes");
                    ModelConfig.updateCustomModelSize(context, session.model.name, session.totalBytes);
                    ModelConfig.updateCustomModelInList(session.model.name, session.totalBytes);
                    
                    // Notify listener to refresh UI
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onCustomModelSizeUpdated(session.model.name, session.totalBytes);
                        }
                    });
                }
            }
            
            Log.d(TAG, "Total bytes: " + session.totalBytes + ", already downloaded: " + session.downloadedBytes);
            
            // Perform the download
            Log.d(TAG, "Calling performDownload...");
            performDownload(session, connection);
            
        } catch (Exception e) {
            Log.e(TAG, "Download error for " + session.model.name + ": " + e.getMessage(), e);
            String exceptionName = "Unknown";
            try {
                if (e != null && e.getClass() != null) {
                    exceptionName = e.getClass().getName();
                }
            } catch (Exception ex) {
                // Ignore
            }
            Log.e(TAG, "Exception type: " + exceptionName);
            if (e.getCause() != null) {
                String causeName = "Unknown";
                try {
                    if (e.getCause().getClass() != null) {
                        causeName = e.getCause().getClass().getName();
                    }
                } catch (Exception ex) {
                    // Ignore
                }
                Log.e(TAG, "Caused by: " + causeName + " - " + e.getCause().getMessage());
            }
            
            // Clean up connection on error
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception ex) {
                    Log.w(TAG, "Error disconnecting after failure", ex);
                }
            }
            
            // Determine if error is retryable
            boolean isNetworkError = isNetworkError(e);
            boolean isConnectionPropertyError = e.getMessage() != null && 
                e.getMessage().contains("Cannot set request property");
            
            // Don't retry connection property errors - they indicate a programming bug
            if (isConnectionPropertyError) {
                Log.e(TAG, "Connection property error - not retrying. This is a code bug that needs fixing.");
                handleDownloadError(session, "Internal error: Connection misconfigured. Please restart the download.");
            } else if (isNetworkError && session.retryCount < session.maxRetries && !session.shouldStop) {
                // Attempt automatic retry for network errors
                handleRetry(session, e.getMessage());
            } else {
                // Final failure - no more retries
                String errorMsg = e.getMessage();
                if (session.retryCount >= session.maxRetries) {
                    errorMsg = "Maximum retries exceeded. " + 
                              (errorMsg != null ? errorMsg : "Network error");
                }
                handleDownloadError(session, errorMsg);
            }
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception e) {
                    Log.w(TAG, "Error disconnecting in finally block", e);
                }
            }
        }
    }
    
    /**
     * Check if exception is a network-related error that can be retried
     */
    private boolean isNetworkError(Exception e) {
        if (e == null) return false;
        
        if (e instanceof java.net.SocketTimeoutException ||
            e instanceof java.net.UnknownHostException ||
            e instanceof java.net.SocketException ||
            e instanceof java.io.InterruptedIOException ||
            e instanceof javax.net.ssl.SSLException) {
            return true;
        }
        
        Throwable cause = e.getCause();
        if (cause instanceof Exception && isNetworkError((Exception) cause)) {
            return true;
        }
        
        String message = e.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("timeout") ||
               lower.contains("reset") ||
               lower.contains("connection") ||
               lower.contains("abort") ||
               lower.contains("network") ||
               lower.contains("hostname") ||
               lower.contains("resolve") ||
               lower.contains("eai_nodata") ||
               lower.contains("software caused");
    }
    
    /**
     * Handle automatic retry with exponential backoff
     */
    private void handleRetry(DownloadSession session, String errorMessage) {
        session.retryCount++;
        session.lastRetryTime = System.currentTimeMillis();
        
        boolean networkAvailable = NetworkUtils.isNetworkAvailable(context);
        long baseDelay = networkAvailable ? 2000L : 5000L;
        // Exponential backoff: 2s/5s, 4s/10s, 8s/20s, 16s/30s, capped at 30s
        session.retryDelayMs = Math.min(baseDelay * (1L << Math.min(session.retryCount - 1, 4)), 30000L);
        
        Log.w(TAG, "Network error for " + session.model.name + ": " + errorMessage + 
                   ". Retry " + session.retryCount + "/" + session.maxRetries + 
                   " in " + session.retryDelayMs + "ms. Network available: " + networkAvailable);
        
        session.error = "Connection lost. Reconnecting (" + session.retryCount + "/" + session.maxRetries + ")...";
        session.saveState(downloadPrefs);
        
        // Schedule retry
        try {
            getDownloadExecutor().execute(() -> {
                try {
                    Thread.sleep(session.retryDelayMs);
                    
                    if (!session.shouldStop && (session.state == DownloadState.DOWNLOADING || session.state == DownloadState.STARTING)) {
                        // Check if network is available before retrying
                        if (!NetworkUtils.isNetworkAvailable(context)) {
                            Log.w(TAG, "Network still offline during retry attempt " + session.retryCount + 
                                       " for " + session.model.name);
                            if (session.retryCount < session.maxRetries) {
                                handleRetry(session, "Waiting for network connection...");
                                return;
                            }
                        }
                        Log.d(TAG, "Retrying download for " + session.model.name + " (attempt " + session.retryCount + ")");
                        startDownload(session);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    handleDownloadError(session, "Retry interrupted");
                } catch (Exception e) {
                    Log.e(TAG, "Error executing retry for " + session.model.name, e);
                    handleDownloadError(session, "Retry failed: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule retry on executor", e);
            handleDownloadError(session, "Failed to schedule retry: " + e.getMessage());
        }
    }
    
    /**
     * Perform the actual file download with robust error handling
     */
    private void performDownload(DownloadSession session, HttpURLConnection connection) throws IOException {
        Log.d(TAG, "=== PERFORM DOWNLOAD === " + session.model.name);
        Log.d(TAG, "Temp file: " + session.tempFile.getAbsolutePath());
        
        RandomAccessFile file = null;
        InputStream input = null;
        
        try {
            Log.d(TAG, "Opening RandomAccessFile for write");
            file = new RandomAccessFile(session.tempFile, "rw");
            
            Log.d(TAG, "Getting input stream from connection");
            input = connection.getInputStream();
            
            // Seek to resume position if resuming
            if (session.downloadedBytes > 0) {
                Log.d(TAG, "Resuming - seeking to position: " + formatBytes(session.downloadedBytes));
                file.seek(session.downloadedBytes);
            } else {
                Log.d(TAG, "Starting fresh download from beginning");
            }
            
            // Adaptive buffer size based on network quality
            byte[] buffer = new byte[session.bufferSize];
            int bytesRead;
            long totalRead = session.downloadedBytes;
            long lastSaveTime = System.currentTimeMillis();
            long bytesReadSinceLastSave = 0;
            int readAttempts = 0;
            
            Log.d(TAG, "Starting download loop - buffer size: " + session.bufferSize + " bytes");
            Log.d(TAG, "Initial position: " + formatBytes(totalRead));
            
            while (!session.shouldStop) {
                try {
                    bytesRead = input.read(buffer);
                    
                    if (bytesRead == -1) {
                        // End of stream
                        break;
                    }
                    
                    // Write to file
                    file.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    bytesReadSinceLastSave += bytesRead;
                    session.updateProgress(totalRead);
                    
                    // Track successful read
                    session.lastSuccessfulReadTime = System.currentTimeMillis();
                    session.consecutiveTimeouts = 0;
                    readAttempts = 0;
                    
                    // Check if paused
                    if (session.state == DownloadState.PAUSED) {
                        Log.d(TAG, "Download loop detected PAUSED state");
                        Log.d(TAG, "Paused at " + formatBytes(totalRead) + "/" + 
                              formatBytes(session.totalBytes) + " (" + 
                              session.getProgressPercentage() + "%)");
                        file.getFD().sync(); // Force flush to disk
                        Log.d(TAG, "Data flushed to disk");
                        session.saveState(downloadPrefs);
                        Log.d(TAG, "State saved, exiting download loop");
                        return;
                    }
                    
                    // Check WiFi requirement during download (every 1MB to avoid too frequent checks)
                    // Skip check if user has overridden WiFi requirement
                    if (totalRead % (1024 * 1024) == 0) {
                        if (wiFiRequirementOverridden) {
                            Log.d(TAG, "Continuing download on mobile data (user override active)");
                        } else if (!checkWiFiRequirementForDownload()) {
                            Log.w(TAG, "WiFi requirement no longer met during download, pausing");
                            session.state = DownloadState.PAUSED;
                            session.error = "WiFi connection lost. Download paused - will resume automatically when WiFi is available.";
                            file.getFD().sync(); // Force flush to disk
                            session.saveState(downloadPrefs);
                            
                            // Notify about automatic pause due to WiFi loss
                            mainHandler.post(() -> {
                                if (listener != null) {
                                    listener.onDownloadPaused(session.model.name);
                                }
                            });
                            return;
                        }
                    }
                    
                    // Periodically save state (every 5MB or 30 seconds)
                    long currentTime = System.currentTimeMillis();
                    if (bytesReadSinceLastSave >= 5 * 1024 * 1024 || 
                        (currentTime - lastSaveTime) >= 30000) {
                        
                        file.getFD().sync(); // Force flush to disk
                        session.saveState(downloadPrefs);
                        lastSaveTime = currentTime;
                        bytesReadSinceLastSave = 0;
                        
                        // Reset transient retry count when active data is flowing successfully
                        if (session.retryCount > 0) {
                            session.retryCount = 0;
                        }
                        
                        Log.d(TAG, "Progress saved: " + totalRead + "/" + session.totalBytes + 
                                   " (" + session.getProgressPercentage() + "%) Speed: " + 
                                   formatBytes(session.getSpeed()) + "/s");
                    }
                    
                    // Adaptive buffer sizing based on speed
                    if (totalRead % (10 * 1024 * 1024) == 0) { // Every 10MB
                        adjustBufferSize(session);
                        if (buffer.length != session.bufferSize) {
                            buffer = new byte[session.bufferSize];
                        }
                    }
                    
                    // Yield to other threads occasionally
                    if (totalRead % (1024 * 1024) == 0) { // Every MB
                        Thread.yield();
                    }
                    
                } catch (java.net.SocketTimeoutException e) {
                    readAttempts++;
                    session.consecutiveTimeouts++;
                    
                    Log.w(TAG, "Read timeout (attempt " + readAttempts + "), consecutive timeouts: " + 
                               session.consecutiveTimeouts);
                    
                    if (readAttempts >= 3) {
                        throw new IOException("Multiple consecutive read timeouts", e);
                    }
                    
                    // Brief pause before retry
                    Thread.sleep(1000);
                    continue;
                }
            }
            
            // Final sync before completion check
            if (file != null) {
                file.getFD().sync();
            }
            
            // Check if download was cancelled
            if (session.shouldStop) {
                Log.d(TAG, "Download loop detected shouldStop flag - download cancelled");
                Log.d(TAG, "Downloaded: " + formatBytes(totalRead) + " before cancellation");
                session.saveState(downloadPrefs);
                return;
            }
            
            Log.d(TAG, "Download loop completed - total read: " + formatBytes(totalRead) + 
                  "/" + formatBytes(session.totalBytes));
            
            // Download completed successfully
            if (totalRead >= session.totalBytes * 0.95) { // Allow 5% tolerance
                Log.d(TAG, "Download complete (95% threshold met)");
                // Reset retry counter on successful download
                session.retryCount = 0;
                handleDownloadComplete(session);
            } else {
                String errorMsg = "Incomplete download: " + formatBytes(totalRead) + 
                                "/" + formatBytes(session.totalBytes);
                Log.e(TAG, errorMsg);
                throw new IOException(errorMsg);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        } catch (IOException e) {
            // Save current progress before throwing
            if (file != null) {
                try {
                    file.getFD().sync();
                } catch (Exception ignored) {}
            }
            session.saveState(downloadPrefs);
            throw e;
        } finally {
            // Close resources
            if (input != null) {
                try { input.close(); } catch (Exception ignored) {}
            }
            if (file != null) {
                try { file.close(); } catch (Exception ignored) {}
            }
        }
    }
    
    /**
     * Adjust buffer size based on download speed
     */
    private void adjustBufferSize(DownloadSession session) {
        long speed = session.getSpeed();
        
        // Adjust buffer size based on speed
        if (speed > 10 * 1024 * 1024) { // > 10 MB/s
            session.bufferSize = 64 * 1024; // 64KB buffer for fast connections
        } else if (speed > 1 * 1024 * 1024) { // > 1 MB/s
            session.bufferSize = 32 * 1024; // 32KB buffer for medium connections
        } else if (speed > 100 * 1024) { // > 100 KB/s
            session.bufferSize = 16 * 1024; // 16KB buffer for slow connections
        } else {
            session.bufferSize = 8 * 1024; // 8KB buffer for very slow connections
        }
        
        Log.d(TAG, "Buffer size adjusted to " + session.bufferSize + " bytes (speed: " + 
                   formatBytes(speed) + "/s)");
    }
    
    /**
     * Handle successful download completion
     */
    private void handleDownloadComplete(DownloadSession session) {
        try {
            stopProgressMonitoring(session);
            
            // Verify file integrity
            if (!verifyFileIntegrity(session.tempFile, session.model)) {
                handleDownloadError(session, "File integrity check failed");
                return;
            }
            
            // Move temp file to final location
            if (session.targetFile.exists()) {
                session.targetFile.delete();
            }
            
            if (!session.tempFile.renameTo(session.targetFile)) {
                handleDownloadError(session, "Failed to move downloaded file");
                return;
            }
            
            // Update session state
            session.state = DownloadState.COMPLETED;
            session.downloadedBytes = session.targetFile.length();
            session.clearState(downloadPrefs);
            
            // Remove from active sessions
            activeSessions.remove(session.model.name);
            if (currentSession == session) {
                currentSession = null;
            }
            
            Log.d(TAG, "Download completed successfully for " + session.model.name);
            
            // Notify completion
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onDownloadCompleted(session.model.name, session.targetFile);
                }
                
                // Show completion notification
                notificationManager.showDownloadCompleted(session.model.name, session.model.displayName);
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error completing download for " + session.model.name, e);
            handleDownloadError(session, "Error completing download: " + e.getMessage());
        }
    }
    
    /**
     * Handle download errors
     */
    private void handleDownloadError(DownloadSession session, String errorMessage) {
        stopProgressMonitoring(session);
        
        session.state = DownloadState.FAILED;
        session.error = errorMessage;
        session.saveState(downloadPrefs);
        
        // Clean, user-friendly error message
        String userError = errorMessage;
        if (errorMessage != null) {
            // Simplify technical error messages
            if (errorMessage.contains("Cannot set request property")) {
                userError = "Connection error. Please try again.";
            } else if (errorMessage.contains("Maximum retries exceeded") || 
                       session.retryCount >= session.maxRetries) {
                userError = "Network unstable. Download paused - tap Resume to continue.";
            } else if (errorMessage.length() > 100) {
                // Truncate very long error messages
                userError = errorMessage.substring(0, 97) + "...";
            }
        } else {
            userError = "Download failed. Please try again.";
        }
        
        Log.e(TAG, "Download failed for " + session.model.name + ": " + userError);
        
        final String finalError = userError;
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onDownloadFailed(session.model.name, finalError);
            }
            
            // Show error notification
            notificationManager.showDownloadFailed(session.model.name, session.model.displayName, finalError);
        });
    }
    
    /**
     * Pause current download
     */
    public void pauseDownload() {
        Log.d(TAG, "=== PAUSE DOWNLOAD (current) ===");
        if (currentSession != null && currentSession.state == DownloadState.DOWNLOADING) {
            Log.d(TAG, "Pausing current session: " + currentSession.model.name);
            pauseDownload(currentSession.model.name);
        } else if (currentSession != null) {
            Log.w(TAG, "Current session exists but state is: " + currentSession.state);
        } else {
            Log.w(TAG, "No current session to pause");
        }
    }
    
    /**
     * Pause download for specific model
     */
    public void pauseDownload(String modelName) {
        Log.d(TAG, "=== PAUSE DOWNLOAD === " + modelName);
        
        DownloadSession session = activeSessions.get(modelName);
        if (session == null) {
            Log.w(TAG, "No session found for " + modelName);
            return;
        }
        
        Log.d(TAG, "Session state: " + session.state);
        Log.d(TAG, "Downloaded: " + formatBytes(session.downloadedBytes) + 
              "/" + formatBytes(session.totalBytes) + " (" + session.getProgressPercentage() + "%)");
        
        if (session.state == DownloadState.DOWNLOADING) {
            Log.d(TAG, "Pausing active download for " + modelName);
            
            // Signal the download to stop
            session.shouldStop = false; // Don't set to true, we want pause not cancel
            session.state = DownloadState.PAUSED;
            session.saveState(downloadPrefs);
            
            Log.d(TAG, "State saved as PAUSED, stopping progress monitoring");
            stopProgressMonitoring(session);
            
            // Clear current session if this is it
            if (currentSession == session) {
                Log.d(TAG, "Clearing current session reference");
                currentSession = null;
            } else {
                Log.d(TAG, "Session is not the current session");
            }
            
            // Send a final progress update before pausing so UI can show current progress
            final long downloaded = session.downloadedBytes;
            final long total = session.totalBytes;
            final int progress = session.getProgressPercentage();
            final long speed = session.getSpeed();
            
            Log.d(TAG, "Posting final progress and pause notification to UI");
            mainHandler.post(() -> {
                if (listener != null) {
                    // Send final progress update
                    listener.onDownloadProgress(modelName, progress, downloaded, total, speed);
                    // Then notify about pause
                    listener.onDownloadPaused(modelName);
                    Log.d(TAG, "UI notified of pause");
                } else {
                    Log.w(TAG, "No listener to notify for pause");
                }
                
                // Show paused notification
                notificationManager.showDownloadPaused(modelName, session.model.displayName, progress);
            });
            
            Log.d(TAG, "Download paused successfully for " + modelName);
        } else {
            Log.w(TAG, "Cannot pause - session state is " + session.state + ", not DOWNLOADING");
        }
    }
    
    /**
     * Resume paused or failed download (auto-pauses current download if needed)
     */
    public void resumeDownload() {
        Log.d(TAG, "=== RESUME DOWNLOAD (first paused/failed) ===");
        Log.d(TAG, "Active sessions count: " + activeSessions.size());
        
        // Find first paused or failed download
        for (DownloadSession session : activeSessions.values()) {
            Log.d(TAG, "Session " + session.model.name + " state: " + session.state);
            if (session.state == DownloadState.PAUSED || session.state == DownloadState.FAILED) {
                Log.d(TAG, "Found resumable download: " + session.model.name);
                resumeDownload(session.model.name);
                return;
            }
        }
        
        Log.w(TAG, "No paused or failed downloads found to resume");
    }
    
    /**
     * Resume download with auto-pause of current download
     */
    public void resumeDownloadWithAutoPause(String modelName) {
        Log.d(TAG, "=== RESUME WITH AUTO-PAUSE === " + modelName);
        
        DownloadSession session = activeSessions.get(modelName);
        if (session == null) {
            Log.w(TAG, "No session found for " + modelName);
            return;
        }
        
        Log.d(TAG, "Session state: " + session.state);
        
        if (session.state == DownloadState.PAUSED || session.state == DownloadState.FAILED) {
            
            // Auto-pause any currently downloading session
            if (currentSession != null && currentSession != session && 
                currentSession.state == DownloadState.DOWNLOADING) {
                Log.d(TAG, "Auto-pausing current download: " + currentSession.model.name);
                pauseDownload(currentSession.model.name);
                
				
                // Wait a bit for pause to complete
                try {
                    getDownloadExecutor().execute(() -> {
                        try {
                            Log.d(TAG, "Waiting 500ms for pause to complete...");
                            Thread.sleep(500); // Wait for pause to complete
                            Log.d(TAG, "Proceeding with resume");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            Log.w(TAG, "Thread interrupted during pause wait");
                        }
                        // Now resume the requested download
                        resumeDownload(modelName);
                    });
                } catch (Exception e) {
                    Log.w(TAG, "Failed to schedule auto-pause wait on executor, resuming directly", e);
                    resumeDownload(modelName);
                }
            } else {
                Log.d(TAG, "No conflict, resuming directly");
                // No conflict, resume directly
                resumeDownload(modelName);
            }
        } else {
            Log.w(TAG, "Cannot resume - session state is " + session.state);
        }
    }
    
    /**
     * Resume download for specific model
     */
    public void resumeDownload(String modelName) {
        Log.d(TAG, "=== RESUME DOWNLOAD === " + modelName);
        
        DownloadSession session = activeSessions.get(modelName);
        if (session == null) {
            Log.w(TAG, "Cannot resume " + modelName + ": no session found");
            Log.d(TAG, "Active sessions: " + activeSessions.keySet());
            return;
        }
        
        Log.d(TAG, "Session state: " + session.state);
        Log.d(TAG, "Downloaded: " + formatBytes(session.downloadedBytes) + 
              "/" + formatBytes(session.totalBytes) + " (" + session.getProgressPercentage() + "%)");
        
        if (session.state == DownloadState.PAUSED || session.state == DownloadState.FAILED) {
            
            // Check WiFi requirement before resuming download
            if (!checkWiFiRequirementForDownload()) {
                Log.w(TAG, "WiFi requirement not met, cannot resume download for " + modelName);
                
                String errorMessage = getWiFiRequirementErrorMessage();
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onDownloadFailed(modelName, errorMessage);
                    }
                });
                return;
            }
            
            // Check if download is already complete (downloaded bytes >= total bytes)
            if (session.downloadedBytes >= session.totalBytes && session.totalBytes > 0) {
                Log.d(TAG, "Download already complete for " + modelName + " (" + 
                           session.downloadedBytes + "/" + session.totalBytes + " bytes)");
                
                // Check if the file exists and is complete
                File targetFile = getModelFile(session.model);
                File tempFile = session.tempFile;
                
                Log.d(TAG, "Target file exists: " + targetFile.exists() + 
                      ", size: " + (targetFile.exists() ? formatBytes(targetFile.length()) : "N/A"));
                Log.d(TAG, "Temp file exists: " + tempFile.exists() + 
                      ", size: " + (tempFile.exists() ? formatBytes(tempFile.length()) : "N/A"));
                
                if (targetFile.exists() && targetFile.length() >= session.totalBytes * 0.95) {
                    Log.d(TAG, "Target file complete, marking download as completed");
                    handleDownloadComplete(session);
                    return;
                } else if (tempFile.exists() && tempFile.length() >= session.totalBytes * 0.95) {
                    Log.d(TAG, "Temp file complete, completing download");
                    handleDownloadComplete(session);
                    return;
                } else {
                    // File doesn't exist or is incomplete, reset download state
                    Log.w(TAG, "File missing or incomplete despite download progress, resetting download");
                    session.downloadedBytes = 0;
                    if (tempFile.exists()) {
                        boolean deleted = tempFile.delete();
                        Log.d(TAG, "Temp file deletion: " + deleted);
                    }
                }
            }
            
            // Check if another download is already active
            if (currentSession != null && currentSession != session && 
                currentSession.state == DownloadState.DOWNLOADING) {
                Log.w(TAG, "Cannot resume " + modelName + ": another download (" + 
                           currentSession.model.name + ") is already in progress (state: " + 
                           currentSession.state + ")");
                
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onDownloadFailed(modelName, 
                            "Another download is in progress. Please wait or pause it first.");
                        Log.d(TAG, "Notified listener of conflict");
                    }
                });
                return;
            }
            
            Log.d(TAG, "Resuming download from " + formatBytes(session.downloadedBytes) + 
                  " (previous state: " + session.state + ")");
            
            // Reset retry counter and error state when manually resuming
            session.retryCount = 0;
            session.consecutiveTimeouts = 0;
            session.error = null;
            session.shouldStop = false;
            
            Log.d(TAG, "Retry counters reset, setting as current session");
            currentSession = session;
            
            Log.d(TAG, "Notifying listener of resume");
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onDownloadResumed(modelName);
                    Log.d(TAG, "Listener notified of resume");
                } else {
                    Log.w(TAG, "No listener to notify for resume");
                }
                
                // Show resumed notification
                DownloadSession resumedSession = activeSessions.get(modelName);
                if (resumedSession != null) {
                    notificationManager.showDownloadResumed(modelName, resumedSession.model.displayName);
                }
            });
            
            Log.d(TAG, "Submitting download task to executor");
            try {
                getDownloadExecutor().execute(() -> {
                    Log.d(TAG, "Download task started in executor");
                    startDownload(session);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to submit resume download task to executor", e);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onDownloadFailed(modelName, "Failed to resume download: " + e.getMessage());
                    }
                });
            }
        } else {
            Log.w(TAG, "Cannot resume " + modelName + ": current state is " + session.state + 
                  ", expected PAUSED or FAILED");
        }
    }
    
    /**
     * Cancel current download
     */
    public void cancelDownload() {
        if (currentSession != null) {
            cancelDownload(currentSession.model.name);
        }
    }
    
    /**
     * Cancel download for specific model
     */
    public void cancelDownload(String modelName) {
        DownloadSession session = activeSessions.get(modelName);
        if (session != null) {
            Log.d(TAG, "Cancelling download for " + modelName + " (current state: " + session.state + ")");
            
            session.shouldStop = true;
            session.state = DownloadState.CANCELLED;
            
            stopProgressMonitoring(session);
            
            // Clean up files only if download was in progress
            if (session.tempFile != null && session.tempFile.exists()) {
                boolean deleted = session.tempFile.delete();
                Log.d(TAG, "Temp file deletion for " + modelName + ": " + deleted);
            }
            
            session.clearState(downloadPrefs);
            activeSessions.remove(modelName);
            
            if (currentSession == session) {
                currentSession = null;
            }
            
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onDownloadCancelled(modelName);
                }
                
                // Clear notification
                notificationManager.showDownloadCancelled(modelName, session.model.displayName);
            });
            
            Log.d(TAG, "Download cancelled successfully for " + modelName);
        } else {
            Log.w(TAG, "Cannot cancel " + modelName + ": no active session found");
        }
    }
    
    /**
     * Start progress monitoring for a download session
     */
    private void startProgressMonitoring(DownloadSession session) {
        if (session.isProgressMonitoringActive.compareAndSet(false, true)) {
            try {
                getProgressExecutor().execute(new ProgressMonitorTask(session));
            } catch (Exception e) {
                Log.w(TAG, "Failed to start progress monitoring executor", e);
                session.isProgressMonitoringActive.set(false);
            }
        }
    }
    
    /**
     * Stop progress monitoring for a download session
     */
    private void stopProgressMonitoring(DownloadSession session) {
        session.isProgressMonitoringActive.set(false);
    }
    
    /**
     * Progress monitoring task that runs in background
     */
    private class ProgressMonitorTask implements Runnable {
        private final DownloadSession session;
        
        ProgressMonitorTask(DownloadSession session) {
            this.session = session;
        }
        
        @Override
        public void run() {
            try {
                while (session.isProgressMonitoringActive.get() && 
                       session.state == DownloadState.DOWNLOADING && 
                       !session.shouldStop) {
                    
                    try {
                        long downloaded = session.downloadedBytes;
                        long total = session.totalBytes;
                        int progress = session.getProgressPercentage();
                        long speed = session.getSpeed();
                        
                        // Update UI on main thread
                        mainHandler.post(() -> {
                            if (listener != null) {
                                listener.onDownloadProgress(session.model.name, progress, downloaded, total, speed);
                            }
                            
                            // Update notification progress
                            notificationManager.updateDownloadProgress(session.model.name, session.model.displayName, 
                                                                     progress, downloaded, total, speed);
                        });
                        
                        // Wait before next update
                        Thread.sleep(Constants.PROGRESS_UPDATE_INTERVAL);
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Error in progress monitoring", e);
                        break;
                    }
                }
            } finally {
                session.isProgressMonitoringActive.set(false);
            }
        }
    }
    
    /**
     * Check if a model is currently downloading
     */
    public boolean isModelDownloading(ModelConfig.Model model) {
        DownloadSession session = activeSessions.get(model.name);
        boolean isDownloading = session != null && session.state == DownloadState.DOWNLOADING;
        Log.d("ModelDownloadManager", "isModelDownloading for " + model.name + ": session exists=" + (session != null) + 
              ", state=" + (session != null ? session.state : "null") + ", result=" + isDownloading);
        return isDownloading;
    }
    
    /**
     * Check if a model download is paused
     */
    public boolean isModelDownloadPaused(ModelConfig.Model model) {
        DownloadSession session = activeSessions.get(model.name);
        if (session != null && session.state == DownloadState.PAUSED) {
            // Check if download is actually complete
            if (session.downloadedBytes >= session.totalBytes && session.totalBytes > 0) {
                File targetFile = getModelFile(model);
                if (targetFile.exists() && targetFile.length() >= session.totalBytes * 0.95) {
                    return false; // Not actually paused - it's complete
                }
            }
            return true;
        }
        return false;
    }
    
    /**
     * Check if a model is already downloaded
     */
    public boolean isModelDownloaded(ModelConfig.Model model) {
        // Check if there's an active download first
        DownloadSession session = activeSessions.get(model.name);
        if (session != null && session.state != DownloadState.COMPLETED) {
            return false;
        }
        
        File modelFile = getModelFile(model);
        if (!modelFile.exists() || modelFile.length() == 0) {
            return false;
        }
        
        // For custom URL models with unknown size, just check if file exists and has content
        if (model.isCustomUrl || model.fileSizeBytes <= 0) {
            Log.d(TAG, "Model " + model.name + " (custom/unknown size) download status: exists=" + 
                       modelFile.exists() + ", size=" + modelFile.length());
            return true; // File exists and has content
        }
        
        // Verify file size (allow 5% tolerance)
        long expectedSize = model.fileSizeBytes;
        long actualSize = modelFile.length();
        boolean isCorrectSize = actualSize >= (expectedSize * 0.95) && actualSize <= (expectedSize * 1.05);
        
        Log.d(TAG, "Model " + model.name + " download status: exists=" + modelFile.exists() + 
                   ", size=" + actualSize + "/" + expectedSize + ", correct=" + isCorrectSize);
        
        return isCorrectSize;
    }
    
    /**
     * Get download progress for a model
     */
    public int getDownloadProgress(ModelConfig.Model model) {
        DownloadSession session = activeSessions.get(model.name);
        if (session != null) {
            return session.getProgressPercentage();
        }
        return -1;
    }
    
    /**
     * Check if any download is currently active
     */
    public boolean hasActiveDownload() {
        for (DownloadSession session : activeSessions.values()) {
            if (session.state == DownloadState.DOWNLOADING) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the name of the currently downloading model
     */
    public String getActiveDownloadModelName() {
        for (DownloadSession session : activeSessions.values()) {
            if (session.state == DownloadState.DOWNLOADING) {
                return session.model.name;
            }
        }
        return null;
    }
    
    /**
     * Check if there are any paused or failed downloads that can be resumed
     */
    public boolean hasPausedDownloads() {
        Log.d("ModelDownloadManager", "Checking for resumable downloads, active sessions: " + activeSessions.size());
        for (DownloadSession session : activeSessions.values()) {
            Log.d("ModelDownloadManager", "Session " + session.model.name + " state: " + session.state);
            if (session.state == DownloadState.PAUSED || session.state == DownloadState.FAILED) {
                // Check if download is actually complete
                if (session.downloadedBytes >= session.totalBytes && session.totalBytes > 0) {
                    File targetFile = getModelFile(session.model);
                    if (targetFile.exists() && targetFile.length() >= session.totalBytes * 0.95) {
                        Log.d("ModelDownloadManager", "Session " + session.model.name + " is actually complete, skipping");
                        continue;
                    }
                }
                
                Log.d("ModelDownloadManager", "Found resumable download: " + session.model.name + " (" + session.state + ")");
                return true;
            }
        }
        Log.d("ModelDownloadManager", "No resumable downloads found");
        return false;
    }
    
    /**
     * Get the name of the first paused or failed download that can be resumed
     */
    public String getPausedDownloadModelName() {
        for (DownloadSession session : activeSessions.values()) {
            if (session.state == DownloadState.PAUSED || session.state == DownloadState.FAILED) {
                // Check if download is actually complete
                if (session.downloadedBytes >= session.totalBytes && session.totalBytes > 0) {
                    File targetFile = getModelFile(session.model);
                    if (targetFile.exists() && targetFile.length() >= session.totalBytes * 0.95) {
                        // Skip this one - it's actually complete
                        continue;
                    }
                }
                return session.model.name;
            }
        }
        return null;
    }
    
    /**
     * Delete a downloaded model
     */
    public boolean deleteModel(ModelConfig.Model model) {
        // Cancel any active download first
        cancelDownload(model.name);
        
        File modelFile = getModelFile(model);
        boolean deleted = false;
        
        if (modelFile.exists()) {
            deleted = modelFile.delete();
            Log.d(TAG, "Model file " + modelFile.getName() + " deletion: " + deleted);
        }
        
        // Also delete any temp files
        File tempFile = new File(modelFile.getParentFile(), model.fileName + ".tmp");
        if (tempFile.exists()) {
            tempFile.delete();
        }
        
        return deleted;
    }
    
    /**
     * Verify file integrity after download
     */
    private boolean verifyFileIntegrity(File file, ModelConfig.Model model) {
        if (!file.exists()) {
            Log.e(TAG, "File does not exist for integrity check: " + file.getAbsolutePath());
            return false;
        }
        
        long actualSize = file.length();
        
        // For custom URLs where size is unknown, just check that file exists and has content
        if (model.isCustomUrl || model.fileSizeBytes <= 0) {
            boolean valid = actualSize > 0;
            if (valid) {
                Log.d(TAG, "File integrity check passed for custom URL - file size: " + formatBytes(actualSize));
            } else {
                Log.e(TAG, "File integrity check failed for custom URL - empty file");
            }
            return valid;
        }
        
        long expectedSize = model.fileSizeBytes;
        
        // Check file size (allow 5% tolerance)
        boolean sizeValid = actualSize >= (expectedSize * 0.95) && actualSize <= (expectedSize * 1.05);
        
        if (!sizeValid) {
            Log.e(TAG, "File size mismatch for " + model.name + 
                       ": expected=" + expectedSize + ", actual=" + actualSize);
            return false;
        }
        
        // Additional integrity checks could be added here (checksums, etc.)
        // For now, file size check is sufficient
        
        Log.d(TAG, "File integrity verified for " + model.name);
        return true;
    }
    
    /**
     * Get the models directory
     */
    public File getModelsDirectory() {
        File modelsDir;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use app-specific external storage for Android 10+
            File externalFilesDir = context.getExternalFilesDir(null);
            if (externalFilesDir != null) {
                modelsDir = new File(externalFilesDir, "models");
            } else {
                // Fallback to internal storage
                modelsDir = new File(context.getFilesDir(), "models");
            }
        } else {
            // Use public Downloads directory for older Android versions
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            modelsDir = new File(downloadsDir, "MobiGPT");
        }
        
        Log.d(TAG, "Models directory: " + modelsDir.getAbsolutePath());
        return modelsDir;
    }
    
    /**
     * Get model file path
     */
    public File getModelFile(ModelConfig.Model model) {
        return new File(getModelsDirectory(), model.fileName);
    }
    
    /**
     * Ensure directory exists
     */
    private boolean ensureDirectoryExists(File directory) {
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create directory: " + directory.getAbsolutePath());
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if there's enough storage space
     */
    public boolean hasEnoughSpace(ModelConfig.Model model) {
        // For custom URLs where size is unknown, skip the check
        if (model.isCustomUrl || model.fileSizeBytes <= 0) {
            Log.d(TAG, "Skipping storage check for custom URL model: " + model.name);
            return true;
        }
        
        File modelsDir = getModelsDirectory();
        if (!ensureDirectoryExists(modelsDir)) {
            return false;
        }
        
        long availableSpace = modelsDir.getFreeSpace();
        long requiredSpace = model.fileSizeBytes + (100 * 1024 * 1024); // 100MB buffer
        
        Log.d(TAG, "Storage check for " + model.name + 
                   ": available=" + formatBytes(availableSpace) + 
                   ", required=" + formatBytes(requiredSpace));
        
        return availableSpace > requiredSpace;
    }
    
    /**
     * Get available storage space
     */
    public long getAvailableSpace() {
        File modelsDir = getModelsDirectory();
        if (!ensureDirectoryExists(modelsDir)) {
            return 0;
        }
        return modelsDir.getFreeSpace();
    }
    
    /**
     * Format bytes to human readable string
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * Notify error on main thread
     */
    private void notifyError(String message) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onDownloadFailed("unknown", message);
            }
        });
    }
    
    /**
     * Clean up old models to free space - for compatibility
     */
    public void cleanupOldModels() {
        try {
            getDownloadExecutor().execute(() -> {
                try {
                    File modelsDir = getModelsDirectory();
                    if (!modelsDir.exists()) return;
                    
                    File[] modelFiles = modelsDir.listFiles((dir, name) -> name.endsWith(".gguf"));
                    if (modelFiles == null || modelFiles.length <= Constants.MAX_MODEL_FILES) {
                        return; // No cleanup needed
                    }
                    
                    // Sort by last modified time (oldest first)
                    java.util.Arrays.sort(modelFiles, (f1, f2) -> 
                        Long.compare(f1.lastModified(), f2.lastModified()));
                    
                    // Delete oldest files beyond the limit
                    int filesToDelete = modelFiles.length - Constants.MAX_MODEL_FILES;
                    for (int i = 0; i < filesToDelete; i++) {
                        if (modelFiles[i].delete()) {
                            Log.d(TAG, "Deleted old model file: " + modelFiles[i].getName());
                        }
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error cleaning up old models", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule cleanupOldModels on executor", e);
        }
    }
    
    /**
     * Clean up completed downloads that are still showing as paused/failed
     */
    public void cleanupCompletedDownloads() {
        Log.d(TAG, "Cleaning up completed downloads");
        
        // Create a list to store sessions to remove (to avoid concurrent modification)
        java.util.List<String> sessionsToRemove = new java.util.ArrayList<>();
        
        for (DownloadSession session : activeSessions.values()) {
            // Check if the download is actually complete
            if (session.downloadedBytes >= session.totalBytes && session.totalBytes > 0) {
                File targetFile = getModelFile(session.model);
                
                if (targetFile.exists() && targetFile.length() >= session.totalBytes * 0.95) {
                    Log.d(TAG, "Removing completed download session for " + session.model.name);
                    session.clearState(downloadPrefs);
                    sessionsToRemove.add(session.model.name);
                } else if (session.tempFile.exists() && session.tempFile.length() >= session.totalBytes * 0.95) {
                    Log.d(TAG, "Completing download for " + session.model.name);
                    try {
                        if (targetFile.exists()) targetFile.delete();
                        if (session.tempFile.renameTo(targetFile)) {
                            session.clearState(downloadPrefs);
                            sessionsToRemove.add(session.model.name);
                            
                            // Notify completion on main thread
                            mainHandler.post(() -> {
                                if (listener != null) {
                                    listener.onDownloadCompleted(session.model.name, targetFile);
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to complete download for " + session.model.name, e);
                    }
                }
            }
        }
        
        // Remove completed sessions
        for (String modelName : sessionsToRemove) {
            activeSessions.remove(modelName);
        }
    }

    // Lifecycle Management Implementation - commented out for now
    
    // @Override
    public void onRestore() {
        if (isOptimized) {
            isOptimized = false;
            resumeProgressUpdates();
            Log.d(TAG, "ModelDownloadManager restored from optimization");
        }
    }
    
    // @Override
    public void onOptimize() {
        if (!isOptimized) {
            isOptimized = true;
            pauseProgressUpdates();
            
            // Pause non-essential downloads
            for (DownloadSession session : activeSessions.values()) {
                if (session.state == DownloadState.DOWNLOADING && 
                    session.model.name.contains("optional")) { // Example criteria for non-essential
                    pauseDownload(session.model.name);
                }
            }
            
            Log.d(TAG, "ModelDownloadManager optimized for background");
        }
    }
    
    // @Override
    public void onAggressiveCleanup() {
        // Pause all downloads except essential ones
        for (DownloadSession session : activeSessions.values()) {
            if (session.state == DownloadState.DOWNLOADING && 
                !session.model.name.contains("essential")) { // Example criteria for essential
                pauseDownload(session.model.name);
            }
        }
        
        // Clean up old models more aggressively
        cleanupOldModels();
        
        Log.d(TAG, "ModelDownloadManager performing aggressive cleanup");
    }
    
    // @Override
    public void onDestroy() {
        cleanup();
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        this.listener = null;
        if (isActive) {
            Log.d(TAG, "Cleaning up ModelDownloadManager listener and resources");
            
            // If any download is currently active in the background, don't brutally kill it
            if (hasActiveDownload()) {
                Log.d(TAG, "Active download in progress, keeping background download executors alive");
                return;
            }
            
            isActive = false;
            Log.d(TAG, "Shutting down ModelDownloadManager executors");
            
            // Stop progress monitoring on all sessions
            for (DownloadSession session : activeSessions.values()) {
                stopProgressMonitoring(session);
            }
            
            // Shutdown executors safely
            if (downloadExecutor != null && !downloadExecutor.isShutdown()) {
                downloadExecutor.shutdown();
                downloadExecutor = null;
            }
            
            if (progressExecutor != null && !progressExecutor.isShutdown()) {
                progressExecutor.shutdown();
                progressExecutor = null;
            }
            
            activeSessions.clear();
            currentSession = null;
        }
    }
    
    /**
     * Pause progress updates for memory optimization
     */
    public void pauseProgressUpdates() {
        if (currentSession != null) {
            stopProgressMonitoring(currentSession);
        }
    }
    
    /**
     * Resume progress updates
     */
    public void resumeProgressUpdates() {
        if (currentSession != null && currentSession.state == DownloadState.DOWNLOADING) {
            startProgressMonitoring(currentSession);
        }
    }
    
    /**
     * Clear all download notifications
     */
    public void clearAllNotifications() {
        if (notificationManager != null) {
            notificationManager.clearAllNotifications();
        }
    }

    /**
     * Update notification settings when user preferences change
     */
    public void updateNotificationSettings() {
        if (notificationManager != null) {
            notificationManager.updateNotificationSettings();
        }
    }
}