package com.keralatechreach.mobigpt.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.keralatechreach.mobigpt.ModelConfig;
import com.keralatechreach.mobigpt.ModelDownloadManager;

/**
 * Broadcast receiver to handle notification action buttons
 * Enables pause/resume/cancel actions directly from notifications
 */
public class DownloadNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "DownloadNotificationReceiver";
    
    public static final String ACTION_PAUSE_DOWNLOAD = "com.keralatechreach.mobigpt.ACTION_PAUSE_DOWNLOAD";
    public static final String ACTION_RESUME_DOWNLOAD = "com.keralatechreach.mobigpt.ACTION_RESUME_DOWNLOAD";
    public static final String ACTION_CANCEL_DOWNLOAD = "com.keralatechreach.mobigpt.ACTION_CANCEL_DOWNLOAD";
    public static final String ACTION_RETRY_DOWNLOAD = "com.keralatechreach.mobigpt.ACTION_RETRY_DOWNLOAD";
    
    private static ModelDownloadManager downloadManager;
    
    /**
     * Set the download manager instance to handle actions
     */
    public static void setDownloadManager(ModelDownloadManager manager) {
        downloadManager = manager;
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "Received null intent or action");
            return;
        }
        
        String action = intent.getAction();
        String modelName = intent.getStringExtra("model_name");
        
        if (modelName == null) {
            Log.w(TAG, "No model name provided in intent");
            return;
        }
        
        if (downloadManager == null) {
            downloadManager = ModelDownloadManager.getInstance(context);
        }
        if (downloadManager == null) {
            Log.w(TAG, "Download manager not available, cannot handle action: " + action);
            return;
        }
        
        Log.d(TAG, "Handling action: " + action + " for model: " + modelName);
        
        switch (action) {
            case ACTION_PAUSE_DOWNLOAD:
                handlePauseDownload(modelName);
                break;
                
            case ACTION_RESUME_DOWNLOAD:
                handleResumeDownload(modelName);
                break;
                
            case ACTION_CANCEL_DOWNLOAD:
                handleCancelDownload(modelName);
                break;
                
            case ACTION_RETRY_DOWNLOAD:
                handleRetryDownload(context, modelName);
                break;
                
            default:
                Log.w(TAG, "Unknown action: " + action);
                break;
        }
    }
    
    /**
     * Handle pause download action
     */
    private void handlePauseDownload(String modelName) {
        try {
            Log.d(TAG, "Pausing download for: " + modelName);
            downloadManager.pauseDownload(modelName);
        } catch (Exception e) {
            Log.e(TAG, "Error pausing download for " + modelName, e);
        }
    }
    
    /**
     * Handle resume download action
     */
    private void handleResumeDownload(String modelName) {
        try {
            Log.d(TAG, "Resuming download for: " + modelName);
            downloadManager.resumeDownload(modelName);
        } catch (Exception e) {
            Log.e(TAG, "Error resuming download for " + modelName, e);
        }
    }
    
    /**
     * Handle cancel download action
     */
    private void handleCancelDownload(String modelName) {
        try {
            Log.d(TAG, "Cancelling download for: " + modelName);
            downloadManager.cancelDownload(modelName);
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling download for " + modelName, e);
        }
    }
    
    /**
     * Handle retry download action
     */
    private void handleRetryDownload(Context context, String modelName) {
        try {
            Log.d(TAG, "Retrying download for: " + modelName);
            
            // Find the model by name
            ModelConfig.Model model = findModelByName(modelName);
            if (model != null) {
                downloadManager.downloadModel(model);
            } else {
                Log.e(TAG, "Model not found for retry: " + modelName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrying download for " + modelName, e);
        }
    }
    
    /**
     * Find model by name from available models
     */
    private ModelConfig.Model findModelByName(String modelName) {
        try {
            for (ModelConfig.Model model : ModelConfig.getAvailableModels()) {
                if (model.name.equals(modelName)) {
                    return model;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding model by name: " + modelName, e);
        }
        return null;
    }
}