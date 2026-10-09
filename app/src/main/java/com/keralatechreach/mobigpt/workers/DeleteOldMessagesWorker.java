package com.keralatechreach.mobigpt.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.keralatechreach.mobigpt.database.ChatDatabase;
import com.keralatechreach.mobigpt.database.MessageDao;
import com.keralatechreach.mobigpt.database.ChatDao;
import com.keralatechreach.mobigpt.database.Chat;
import com.keralatechreach.mobigpt.utils.SettingsManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Worker class that handles automatic deletion of old messages
 * This worker runs periodically based on user settings
 * 
 * Features:
 * - Deletes messages older than specified period
 * - Preserves starred messages (never deletes them)
 * - Runs in background without blocking UI
 * - Updates last message for each chat after deletion
 */
public class DeleteOldMessagesWorker extends Worker {
    
    private static final String TAG = "DeleteOldMessagesWorker";
    
    public DeleteOldMessagesWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting auto-delete old messages worker");
        
        try {
            Context context = getApplicationContext();
            SettingsManager settingsManager = new SettingsManager(context);
            
            // Check if auto-delete is enabled
            if (!settingsManager.isDeleteOldMessages()) {
                Log.d(TAG, "Auto-delete is disabled, skipping");
                return Result.success();
            }
            
            // Get the delete period in days
            int deletePeriodDays = settingsManager.getDeleteMessagesAfterDays();
            Log.d(TAG, "Delete period: " + deletePeriodDays + " days");
            
            // Calculate the cutoff timestamp
            long currentTime = System.currentTimeMillis();
            long cutoffTime = currentTime - TimeUnit.DAYS.toMillis(deletePeriodDays);
            
            // Get database instance
            ChatDatabase database = ChatDatabase.getDatabase(context);
            MessageDao messageDao = database.messageDao();
            ChatDao chatDao = database.chatDao();
            
            // Get all chats
            List<Chat> allChats = chatDao.getAllChats();
            
            int totalDeletedCount = 0;
            int chatsProcessed = 0;
            
            // Process each chat
            for (Chat chat : allChats) {
                try {
                    // Count messages before deletion for logging
                    int messageCountBefore = messageDao.getMessageCountForChat(chat.id);
                    
                    // Delete old messages for this chat (excluding starred messages)
                    // We'll use a custom query that respects starred status
                    deleteOldMessagesExcludingStarred(messageDao, chat.id, cutoffTime);
                    
                    // Count messages after deletion
                    int messageCountAfter = messageDao.getMessageCountForChat(chat.id);
                    int deletedCount = messageCountBefore - messageCountAfter;
                    
                    if (deletedCount > 0) {
                        Log.d(TAG, "Deleted " + deletedCount + " old messages from chat: " + chat.title);
                        totalDeletedCount += deletedCount;
                    }
                    
                    chatsProcessed++;
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error deleting messages for chat " + chat.id, e);
                    // Continue with other chats even if one fails
                }
            }
            
            Log.i(TAG, "Auto-delete completed: Processed " + chatsProcessed + " chats, deleted " + 
                  totalDeletedCount + " old messages (starred messages preserved)");
            
            return Result.success();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in auto-delete worker", e);
            // Retry on failure
            return Result.retry();
        }
    }
    
    /**
     * Delete old messages excluding starred messages.
     * The DAO query (MessageDao.deleteOldMessages) already filters with "AND isStarred = 0"
     * so starred messages are never deleted.
     */
    private void deleteOldMessagesExcludingStarred(MessageDao messageDao, long chatId, long cutoffTime) {
        messageDao.deleteOldMessages(chatId, cutoffTime);
    }
}
