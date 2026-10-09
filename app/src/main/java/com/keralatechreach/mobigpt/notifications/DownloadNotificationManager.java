package com.keralatechreach.mobigpt.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import com.keralatechreach.mobigpt.MainActivity;
import com.keralatechreach.mobigpt.R;

import java.util.concurrent.ConcurrentHashMap;

/**
 * YouTube-style download notification manager
 * Features:
 * - Persistent download progress notifications
 * - Completion notifications with sound
 * - Error notifications
 * - Integration with notification settings
 * - Channel management for different notification types
 */
public class DownloadNotificationManager {
    private static final String TAG = "DownloadNotificationManager";
    
    // Notification Channels
    private static final String CHANNEL_DOWNLOAD_PROGRESS = "download_progress";
    private static final String CHANNEL_DOWNLOAD_COMPLETE = "download_complete";
    private static final String CHANNEL_DOWNLOAD_ERROR = "download_error";
    
    // Notification IDs
    private static final int BASE_NOTIFICATION_ID = 1000;
    private static final int COMPLETE_NOTIFICATION_ID = 2000;
    private static final int ERROR_NOTIFICATION_ID = 3000;
    
    private final Context context;
    private final NotificationManagerCompat notificationManager;
    private final SharedPreferences preferences;
    
    // Track active download notifications
    private final ConcurrentHashMap<String, Integer> activeDownloadNotifications = new ConcurrentHashMap<>();
    
    public DownloadNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = NotificationManagerCompat.from(context);
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        
        createNotificationChannels();
    }
    
    /**
     * Create notification channels for different types of download notifications
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            
            // Download Progress Channel
            NotificationChannel progressChannel = new NotificationChannel(
                CHANNEL_DOWNLOAD_PROGRESS,
                "Download Progress",
                NotificationManager.IMPORTANCE_LOW
            );
            progressChannel.setDescription("Shows progress of model downloads");
            progressChannel.setShowBadge(false);
            progressChannel.enableLights(false);
            progressChannel.enableVibration(false);
            progressChannel.setSound(null, null);
            manager.createNotificationChannel(progressChannel);
            
            // Download Complete Channel
            NotificationChannel completeChannel = new NotificationChannel(
                CHANNEL_DOWNLOAD_COMPLETE,
                "Download Complete",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            completeChannel.setDescription("Notifies when model downloads complete");
            completeChannel.setShowBadge(true);
            completeChannel.enableLights(true);
            completeChannel.setLightColor(android.graphics.Color.GREEN);
            
            // Set sound based on user preference
            if (isDownloadCompleteNotificationEnabled() && isDownloadCompleteSoundEnabled()) {
                Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                completeChannel.setSound(defaultSoundUri, null);
            } else {
                completeChannel.setSound(null, null);
            }
            manager.createNotificationChannel(completeChannel);
            
            // Download Error Channel
            NotificationChannel errorChannel = new NotificationChannel(
                CHANNEL_DOWNLOAD_ERROR,
                "Download Errors",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            errorChannel.setDescription("Notifies when download errors occur");
            errorChannel.setShowBadge(true);
            errorChannel.enableLights(true);
            errorChannel.setLightColor(android.graphics.Color.RED);
            errorChannel.enableVibration(true);
            manager.createNotificationChannel(errorChannel);
            
            Log.d(TAG, "Notification channels created");
        }
    }
    
    /**
     * Show download started notification
     */
    public void showDownloadStarted(String modelName, String displayName) {
        if (!isDownloadNotificationEnabled()) return;
        
        int notificationId = getNotificationId(modelName);
        activeDownloadNotifications.put(modelName, notificationId);
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            notificationId, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_PROGRESS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Downloading " + displayName)
            .setContentText("Starting download...")
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS);
        
        // Add action buttons for user control
        addDownloadControlActions(builder, modelName, false);
        
        try {
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "Download started notification shown for " + modelName);
        } catch (SecurityException e) {
            Log.w(TAG, "Permission denied for notification: " + e.getMessage());
        }
    }
    
    /**
     * Update download progress notification
     */
    public void updateDownloadProgress(String modelName, String displayName, int progress, 
                                     long downloadedBytes, long totalBytes, long speed) {
        if (!isDownloadNotificationEnabled()) return;
        
        Integer notificationId = activeDownloadNotifications.get(modelName);
        if (notificationId == null) {
            // Notification was dismissed or not created, recreate it
            showDownloadStarted(modelName, displayName);
            notificationId = activeDownloadNotifications.get(modelName);
            if (notificationId == null) return;
        }
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            notificationId, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String contentText;
        if (isShowProgressPercentageEnabled()) {
            contentText = String.format("%d%% • %s/%s • %s/s", 
                progress, 
                formatBytes(downloadedBytes), 
                formatBytes(totalBytes), 
                formatBytes(speed)
            );
        } else {
            contentText = String.format("%s/%s • %s/s", 
                formatBytes(downloadedBytes), 
                formatBytes(totalBytes), 
                formatBytes(speed)
            );
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_PROGRESS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Downloading " + displayName)
            .setContentText(contentText)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS);
        
        // Add action buttons for user control
        addDownloadControlActions(builder, modelName, false);
        
        try {
            notificationManager.notify(notificationId, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Permission denied for notification update: " + e.getMessage());
        }
    }
    
    /**
     * Show download paused notification
     */
    public void showDownloadPaused(String modelName, String displayName, int progress) {
        if (!isDownloadNotificationEnabled()) return;
        
        Integer notificationId = activeDownloadNotifications.get(modelName);
        if (notificationId == null) return;
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            notificationId, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String contentText = String.format("Paused at %d%%", progress);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_PROGRESS)
            .setSmallIcon(R.drawable.ic_pause)
            .setContentTitle(displayName + " - Download Paused")
            .setContentText(contentText)
            .setProgress(100, progress, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS);
        
        // Add action buttons for user control
        addDownloadControlActions(builder, modelName, true);
        
        try {
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "Download paused notification shown for " + modelName);
        } catch (SecurityException e) {
            Log.w(TAG, "Permission denied for notification: " + e.getMessage());
        }
    }
    
    /**
     * Show download resumed notification
     */
    public void showDownloadResumed(String modelName, String displayName) {
        if (!isDownloadNotificationEnabled()) return;
        
        // Update to active download notification
        Integer notificationId = activeDownloadNotifications.get(modelName);
        if (notificationId == null) {
            showDownloadStarted(modelName, displayName);
            return;
        }
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            notificationId, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_PROGRESS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Downloading " + displayName)
            .setContentText("Resuming download...")
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS);
        
        // Add action buttons for user control
        addDownloadControlActions(builder, modelName, false);
        
        try {
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "Download resumed notification shown for " + modelName);
        } catch (SecurityException e) {
            Log.w(TAG, "Permission denied for notification: " + e.getMessage());
        }
    }
    
    /**
     * Show download completed notification
     */
    public void showDownloadCompleted(String modelName, String displayName) {
        if (!isDownloadNotificationEnabled()) return;
        
        // Remove from active downloads
        Integer downloadNotificationId = activeDownloadNotifications.remove(modelName);
        if (downloadNotificationId != null) {
            notificationManager.cancel(downloadNotificationId);
        }
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            COMPLETE_NOTIFICATION_ID + modelName.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_COMPLETE)
            .setSmallIcon(R.drawable.ic_download_done)
            .setContentTitle("Download Complete")
            .setContentText(displayName + " is ready to use")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS);
        
        // Add sound and vibration for completion if enabled
        if (isDownloadCompleteNotificationEnabled()) {
            if (isDownloadCompleteSoundEnabled()) {
                // Set both sound and vibration
                Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                builder.setSound(defaultSoundUri)
                       .setDefaults(NotificationCompat.DEFAULT_VIBRATE);
            } else {
                // Only vibration, no sound
                builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE);
            }
        }
        
        try {
            notificationManager.notify(COMPLETE_NOTIFICATION_ID + modelName.hashCode(), builder.build());
            Log.d(TAG, "Download completed notification shown for " + modelName);
        } catch (SecurityException e) {
            Log.w(TAG, "Permission denied for notification: " + e.getMessage());
        }
    }
    
    /**
     * Show download failed notification
     */
    public void showDownloadFailed(String modelName, String displayName, String errorMessage) {
        if (!isDownloadNotificationEnabled()) return;
        
        // Remove from active downloads
        Integer downloadNotificationId = activeDownloadNotifications.remove(modelName);
        if (downloadNotificationId != null) {
            notificationManager.cancel(downloadNotificationId);
        }
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            ERROR_NOTIFICATION_ID + modelName.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_ERROR)
            .setSmallIcon(R.drawable.ic_error)
            .setContentTitle("Download Failed")
            .setContentText(displayName + " - " + errorMessage)
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(displayName + " download failed: " + errorMessage))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR);
        
        // Add retry action
        Intent retryIntent = new Intent(context, DownloadNotificationReceiver.class);
        retryIntent.setAction(DownloadNotificationReceiver.ACTION_RETRY_DOWNLOAD);
        retryIntent.putExtra("model_name", modelName);
        
        PendingIntent retryPendingIntent = PendingIntent.getBroadcast(
            context,
            (ERROR_NOTIFICATION_ID + modelName.hashCode()) * 10,
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        builder.addAction(R.drawable.ic_refresh, "Retry", retryPendingIntent);
        
        try {
            notificationManager.notify(ERROR_NOTIFICATION_ID + modelName.hashCode(), builder.build());
            Log.d(TAG, "Download failed notification shown for " + modelName);
        } catch (SecurityException e) {
            Log.w(TAG, "Permission denied for notification: " + e.getMessage());
        }
    }
    
    /**
     * Show download cancelled notification
     */
    public void showDownloadCancelled(String modelName, String displayName) {
        // Remove from active downloads
        Integer downloadNotificationId = activeDownloadNotifications.remove(modelName);
        if (downloadNotificationId != null) {
            notificationManager.cancel(downloadNotificationId);
            Log.d(TAG, "Download cancelled notification removed for " + modelName);
        }
    }
    
    /**
     * Add action buttons to download notifications for user control
     */
    private void addDownloadControlActions(NotificationCompat.Builder builder, String modelName, boolean isPaused) {
        if (isPaused) {
            // Add Resume action
            Intent resumeIntent = new Intent(context, DownloadNotificationReceiver.class);
            resumeIntent.setAction(DownloadNotificationReceiver.ACTION_RESUME_DOWNLOAD);
            resumeIntent.putExtra("model_name", modelName);
            
            PendingIntent resumePendingIntent = PendingIntent.getBroadcast(
                context,
                (getNotificationId(modelName)) * 10 + 1,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            builder.addAction(R.drawable.ic_play_arrow, "Resume", resumePendingIntent);
        } else {
            // Add Pause action
            Intent pauseIntent = new Intent(context, DownloadNotificationReceiver.class);
            pauseIntent.setAction(DownloadNotificationReceiver.ACTION_PAUSE_DOWNLOAD);
            pauseIntent.putExtra("model_name", modelName);
            
            PendingIntent pausePendingIntent = PendingIntent.getBroadcast(
                context,
                (getNotificationId(modelName)) * 10 + 2,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            builder.addAction(R.drawable.ic_pause, "Pause", pausePendingIntent);
        }
        
        // Add Cancel action
        Intent cancelIntent = new Intent(context, DownloadNotificationReceiver.class);
        cancelIntent.setAction(DownloadNotificationReceiver.ACTION_CANCEL_DOWNLOAD);
        cancelIntent.putExtra("model_name", modelName);
        
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            (getNotificationId(modelName)) * 10 + 3,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        builder.addAction(R.drawable.ic_close, "Cancel", cancelPendingIntent);
    }
    
    /**
     * Get unique notification ID for a model
     */
    private int getNotificationId(String modelName) {
        return BASE_NOTIFICATION_ID + Math.abs(modelName.hashCode() % 1000);
    }
    
    /**
     * Clear all download notifications
     */
    public void clearAllNotifications() {
        for (Integer notificationId : activeDownloadNotifications.values()) {
            notificationManager.cancel(notificationId);
        }
        activeDownloadNotifications.clear();
        Log.d(TAG, "All download notifications cleared");
    }
    
    /**
     * Clear notification for specific model
     */
    public void clearNotification(String modelName) {
        Integer notificationId = activeDownloadNotifications.remove(modelName);
        if (notificationId != null) {
            notificationManager.cancel(notificationId);
            Log.d(TAG, "Notification cleared for " + modelName);
        }
    }
    
    /**
     * Check if download notifications are enabled in user settings
     */
    private boolean isDownloadNotificationEnabled() {
        return preferences.getBoolean("download_notifications", true);
    }
    
    /**
     * Check if download complete notifications are enabled
     */
    private boolean isDownloadCompleteNotificationEnabled() {
        return isDownloadNotificationEnabled() && 
               preferences.getBoolean("download_complete_sound", true);
    }
    
    /**
     * Check if download complete sound is enabled
     */
    private boolean isDownloadCompleteSoundEnabled() {
        return preferences.getBoolean("download_complete_sound", true);
    }
    
    /**
     * Check if progress percentage should be shown in notifications
     */
    private boolean isShowProgressPercentageEnabled() {
        return preferences.getBoolean("download_progress_notification", true);
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
     * Update notification sound settings based on user preferences
     */
    public void updateNotificationSettings() {
        // For Android O and above, we need to delete and recreate channels to update sound settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                // Delete existing complete channel to update sound settings
                manager.deleteNotificationChannel(CHANNEL_DOWNLOAD_COMPLETE);
                Log.d(TAG, "Deleted existing download complete notification channel");
            }
        }
        
        createNotificationChannels(); // Recreate channels with updated settings
        Log.d(TAG, "Notification settings updated - channels recreated with new sound settings");
    }
}