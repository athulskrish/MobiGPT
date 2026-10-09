package com.keralatechreach.mobigpt;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import com.keralatechreach.mobigpt.database.Chat;
import com.keralatechreach.mobigpt.database.ChatDatabase;
import com.keralatechreach.mobigpt.database.Message;
import com.keralatechreach.mobigpt.ai.MobiGPTAI;
import com.keralatechreach.mobigpt.ModelConfig;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ChatManager handles all chat and database operations for the MobiGPT application.
 * This class provides a clean separation between the UI (MainActivity) and the data layer,
 * managing chat creation, message handling, and database operations.
 */
public class ChatManager {
    
    private static final String TAG = "ChatManager";
    private static final int THREAD_POOL_SIZE = 3; // Limit thread pool size
    
    private Context context;
    private ChatDatabase database;
    private ExecutorService databaseExecutor;
    private Handler mainHandler; // Handler for main thread operations
    private MobiGPTAI mobiGPTAI; // AI integration
    private long currentChatId = -1;
    private boolean isNewChat = true;
    private WeakReference<ChatManagerListener> listenerRef;
    private final Object stateLock = new Object();
    
    // Token generation tracking
    private int currentTokenCount = 0;
    private long generationStartTime = 0;
    
    /**
     * Interface for communicating chat events back to the UI layer
     */
    public interface ChatManagerListener {
        void onChatCreated(Chat chat);
        void onChatDeleted(long chatId);
        void onMessagesLoaded(List<Message> messages);
        void onChatHistoryLoaded(List<Chat> chats);
        void onNewChatStarted();
        void onChatSelected(Chat chat);
        void onMessageInputClear();
        void onEmptyStateShow();
        void onEmptyStateHide();
        void onDrawerClose();
        void onError(String message);
        void onChatSwitchBlocked(String message); // Notify when chat switch is blocked during streaming
        // AI response callbacks
        void onAIResponseStarted();
        void onAITokenReceived(String token);
        void onAIResponseComplete(String fullResponse);
        void onAIResponseError(String error);
        void onAIResponseStopped();
        // AI status callbacks
        void onModelLoadingStarted();
        void onModelLoadingProgress(int progress, String status);
        void onModelLoaded(long durationMs);
        void onModelLoadingError(String error);
        void onThinking();
    }
    
    public ChatManager(Context context) {
        this.context = context;
        database = ChatDatabase.getDatabase(context);
        databaseExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        mainHandler = new Handler(Looper.getMainLooper()); // Create Handler for main thread
        // Initialize AI lazily - don't create instance until actually needed
        mobiGPTAI = null;
    }
    
    public void setListener(ChatManagerListener listener) {
        this.listenerRef = new WeakReference<>(listener);
    }
    
    private ChatManagerListener getListener() {
        return listenerRef != null ? listenerRef.get() : null;
    }
    
    public long getCurrentChatId() {
        synchronized (stateLock) {
            return currentChatId;
        }
    }
    
    public boolean isNewChat() {
        synchronized (stateLock) {
            return isNewChat;
        }
    }
    
    public void handleSendMessage(String messageText, ModelConfig.Model selectedModel) {
        if (messageText == null || messageText.trim().isEmpty()) {
            return;
        }
        
        // Send the message with selected model
        sendMessage(messageText.trim(), selectedModel);
        
        // Clear input and hide empty state on main thread
        if (getListener() != null) {
            runOnMainThread(() -> {
                ChatManagerListener listener = getListener();
                if (listener != null) {
                    listener.onMessageInputClear();
                    listener.onEmptyStateHide();
                }
            });
        }
    }
    

    
    public void sendMessage(String messageText, ModelConfig.Model selectedModel) {
        if (messageText == null || messageText.trim().isEmpty()) {
            return;
        }
        
        final String finalMessageText = messageText.trim();
        
        databaseExecutor.execute(() -> {
            try {
                synchronized (stateLock) {
                    // Create or get current chat
                    if (isNewChat) {
                        createNewChatInternal(finalMessageText);
                    }
                    
                    // Add user message and update chat in a transaction
                    long timestamp = System.currentTimeMillis();
                    final long finalCurrentChatId = currentChatId;
                    Message userMessage = new Message(finalCurrentChatId, finalMessageText, true, timestamp);
                    
                    // Get current chat for update
                    Chat currentChat = database.chatDao().getChatById(finalCurrentChatId);
                    if (currentChat != null) {
                        currentChat.lastMessageTime = timestamp;
                        
                        // Use transaction to ensure consistency
                        database.addMessageAndUpdateChat(userMessage, currentChat);
                        
                        // Load updated data on main thread
                        if (getListener() != null) {
                            runOnMainThread(() -> {
                                loadMessages(finalCurrentChatId);
                            });
                        }
                        
                        // Generate AI response after user message is saved
                        if (selectedModel != null) {
                            generateAIResponse(finalCurrentChatId, finalMessageText, selectedModel);
                        }
                        
                    } else {
                        Log.e(TAG, "Failed to update chat after adding message");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending message", e);
                if (getListener() != null) {
                    runOnMainThread(() -> {
                        ChatManagerListener listener = getListener();
                        if (listener != null) {
                            listener.onError("Failed to send message: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }
    

    
    public void createNewChat(String firstMessage) {
        databaseExecutor.execute(() -> {
            try {
                synchronized (stateLock) {
                    createNewChatInternal(firstMessage);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating new chat", e);
                if (getListener() != null) {
                    runOnMainThread(() -> {
                        ChatManagerListener listener = getListener();
                        if (listener != null) {
                            listener.onError("Failed to create new chat: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }
    
    private void createNewChatInternal(String firstMessage) {
        String chatTitle = generateChatTitle(firstMessage);
        long timestamp = System.currentTimeMillis();
        
        Chat newChat = new Chat(chatTitle, timestamp, timestamp);
        currentChatId = database.chatDao().insertChat(newChat);
        isNewChat = false;
        
        if (getListener() != null) {
            // Update chat reference with the generated ID
            newChat.id = currentChatId;
            runOnMainThread(() -> {
                ChatManagerListener listener = getListener();
                if (listener != null) {
                    listener.onChatCreated(newChat);
                }
            });
        }
    }
    
    public void handleStartNewChat() {
        startNewChat();
        
        // Ensure drawer close happens on main thread
        if (getListener() != null) {
            runOnMainThread(() -> {
                ChatManagerListener listener = getListener();
                if (listener != null) {
                    listener.onDrawerClose();
                }
            });
        }
    }
    
    public void startNewChat() {
        synchronized (stateLock) {
            currentChatId = -1;
            isNewChat = true;
        }
        
        // Ensure UI updates happen on main thread
        if (getListener() != null) {
            runOnMainThread(() -> {
                ChatManagerListener listener = getListener();
                if (listener != null) {
                    listener.onNewChatStarted();
                }
            });
        }
    }
    
    public void handleChatClick(Chat chat) {
        Log.d(TAG, "handleChatClick: Selecting chat: " + chat.title + " (ID: " + chat.id + ")");
        
        // Don't allow switching chats while AI is generating a response
        if (isGenerating()) {
            Log.w(TAG, "handleChatClick: Cannot switch chats while AI is generating response");
            if (getListener() != null) {
                runOnMainThread(() -> {
                    ChatManagerListener listener = getListener();
                    if (listener != null) {
                        // Show a message to the user
                        listener.onChatSwitchBlocked("Cannot switch chats while AI is responding. Please wait or stop the current response.");
                    }
                });
            }
            return;
        }
        
        selectChat(chat);
        
        // Ensure UI updates happen on main thread
        if (getListener() != null) {
            runOnMainThread(() -> {
                ChatManagerListener listener = getListener();
                if (listener != null) {
                    Log.d(TAG, "handleChatClick: Notifying UI to select chat and close drawer");
                    listener.onChatSelected(chat);
                    listener.onEmptyStateHide();
                    listener.onDrawerClose();
                }
            });
        }
    }
    
    public void selectChat(Chat chat) {
        Log.d(TAG, "selectChat: Setting current chat to: " + chat.title + " (ID: " + chat.id + ")");
        synchronized (stateLock) {
            currentChatId = chat.id;
            isNewChat = false;
        }
        loadMessages(chat.id);
    }
    
    public void handleDeleteChatClick(Chat chat) {
        Log.d(TAG, "handleDeleteChatClick: Requesting deletion for chat: " + chat.title + " (ID: " + chat.id + ")");
        // Show confirmation dialog
        new AlertDialog.Builder(context)
                .setTitle("Delete Chat")
                .setMessage("Are you sure you want to delete this chat? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Log.d(TAG, "handleDeleteChatClick: User confirmed deletion");
                    deleteChat(chat);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Log.d(TAG, "handleDeleteChatClick: User cancelled deletion");
                })
                .show();
    }
    
    public void deleteChat(Chat chat) {
        Log.d(TAG, "deleteChat: Starting deletion for chat: " + chat.title + " (ID: " + chat.id + ")");
        databaseExecutor.execute(() -> {
            try {
                boolean isDeletingCurrentChat;
                synchronized (stateLock) {
                    isDeletingCurrentChat = (currentChatId == chat.id);
                }
                
                Log.d(TAG, "deleteChat: Deleting from database... isDeletingCurrentChat=" + isDeletingCurrentChat);
                
                // Delete the chat using transaction
                database.deleteChatAndMessages(chat);
                
                Log.d(TAG, "deleteChat: Successfully deleted from database");
                
                // If we deleted the current chat, reset to new chat state
                if (isDeletingCurrentChat) {
                    // Update state on current thread, but notify UI on main thread
                    synchronized (stateLock) {
                        currentChatId = -1;
                        isNewChat = true;
                    }
                    
                    Log.d(TAG, "deleteChat: Reset to new chat state");
                    
                    // Notify UI on main thread
                    if (getListener() != null) {
                        runOnMainThread(() -> {
                            ChatManagerListener listener = getListener();
                            if (listener != null) {
                                listener.onNewChatStarted();
                            }
                        });
                    }
                }
                
                // Refresh chat history on main thread
                runOnMainThread(() -> {
                    Log.d(TAG, "deleteChat: Refreshing chat history");
                    loadChatHistory();
                    ChatManagerListener listener = getListener();
                    if (listener != null) {
                        listener.onChatDeleted(chat.id);
                    }
                });
                
                Log.d(TAG, "deleteChat: Deletion complete");
                
            } catch (Exception e) {
                Log.e(TAG, "Error deleting chat: " + chat.title, e);
                if (getListener() != null) {
                    runOnMainThread(() -> {
                        ChatManagerListener listener = getListener();
                        if (listener != null) {
                            listener.onError("Failed to delete chat: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }
    
    public void loadMessages(long chatId) {
        databaseExecutor.execute(() -> {
            try {
                List<Message> messages = database.messageDao().getMessagesByChatId(chatId);
                
                if (getListener() != null) {
                    runOnMainThread(() -> {
                        ChatManagerListener listener = getListener();
                        if (listener != null) {
                            listener.onMessagesLoaded(messages);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading messages", e);
                if (getListener() != null) {
                    runOnMainThread(() -> {
                        ChatManagerListener listener = getListener();
                        if (listener != null) {
                            listener.onError("Failed to load messages: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }
    
    public void loadChatHistory() {
        databaseExecutor.execute(() -> {
            try {
                List<Chat> chats = database.chatDao().getAllChats();
                
                if (getListener() != null) {
                    runOnMainThread(() -> {
                        ChatManagerListener listener = getListener();
                        if (listener != null) {
                            listener.onChatHistoryLoaded(chats);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading chat history", e);
                if (getListener() != null) {
                    runOnMainThread(() -> {
                        ChatManagerListener listener = getListener();
                        if (listener != null) {
                            listener.onError("Failed to load chat history: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }
    
    /**
     * Search for messages matching the given query
     * @param query Search query string
     * @return List of messages matching the query
     */
    public List<Message> searchMessages(String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return null;
            }
            
            // Search in database (synchronous call - should be called from background thread)
            return database.messageDao().searchMessages(query);
        } catch (Exception e) {
            Log.e(TAG, "Error searching messages", e);
            return null;
        }
    }
    
    /**
     * Search for messages in current chat
     * @param chatId Chat ID to search in
     * @param query Search query string
     * @return List of messages matching the query in the specified chat
     */
    public List<Message> searchMessagesInChat(long chatId, String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return null;
            }
            
            // Search in database (synchronous call - should be called from background thread)
            return database.messageDao().searchMessagesInChat(chatId, query);
        } catch (Exception e) {
            Log.e(TAG, "Error searching messages in chat", e);
            return null;
        }
    }
    
    /**
     * Get the current active chat
     * @return Current chat or null
     */
    public Chat getCurrentChat() {
        try {
            synchronized (stateLock) {
                if (currentChatId <= 0) {
                    return null;
                }
                return database.chatDao().getChatById(currentChatId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting current chat", e);
            return null;
        }
    }
    
    /**
     * Generate AI response for user message
     * Model must be loaded from dropdown selection before calling this method
     * @param userMessage The user's message to respond to
     * @param chatId The current chat ID to save the response to
     */
    private void generateAIResponse(long chatId, String userMessage, ModelConfig.Model selectedModel) {
        // Check if model is provided
        if (selectedModel == null) {
            Log.w(TAG, "No model selected for AI response");
            if (getListener() != null) {
                runOnMainThread(() -> {
                    ChatManagerListener listener = getListener();
                    if (listener != null) {
                        listener.onAIResponseError("No AI model selected. Please select a model first.");
                    }
                });
            }
            return;
        }
        
        // Verify model is loaded (should be loaded from dropdown selection)
        if (!getAI().isModelLoaded()) {
            Log.e(TAG, "Model not loaded! User must select and load a model from dropdown first.");
            if (getListener() != null) {
                runOnMainThread(() -> {
                    ChatManagerListener listener = getListener();
                    if (listener != null) {
                        listener.onAIResponseError("Please select a model from the dropdown first.");
                    }
                });
            }
            return;
        }
        
        Log.d(TAG, "Model is ready, generating response (prompt length: " + userMessage.length() + ")");

        // Notify UI that thinking started
        if (getListener() != null) {
            runOnMainThread(() -> {
                ChatManagerListener listener = getListener();
                if (listener != null) {
                    listener.onThinking();
                }
            });
        }

        // Generate AI response
        generateAIResponseInternal(chatId, userMessage, selectedModel);
    }
    
    /**
     * Internal method to generate AI response using actual AI model
     */
    private void generateAIResponseInternal(long chatId, String userMessage, ModelConfig.Model selectedModel) {
        // Reset token tracking
        currentTokenCount = 0;
        generationStartTime = System.currentTimeMillis();
        
        // Notify UI that AI response started
        if (getListener() != null) {
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    ChatManagerListener listener = getListener();
                    if (listener != null) {
                        listener.onAIResponseStarted();
                    }
                }
            });
        }
        
        // Generate AI response using actual AI model with streaming
        Log.d(TAG, "Generating AI response using model: " + selectedModel.displayName + " (prompt length: " + userMessage.length() + ")");
        
        // Use the Java-compatible method with proper callback interface
        getAI().generateResponseJava(userMessage, new MobiGPTAI.ResponseCallback() {
            @Override
            public void onTokenReceived(String token) {
                // Track token count
                currentTokenCount++;
                
                // Handle each streaming token
                if (getListener() != null) {
                    runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            ChatManagerListener listener = getListener();
                            if (listener != null) {
                                listener.onAITokenReceived(token);
                            }
                        }
                    });
                }
            }

            @Override
            public void onComplete(String finalResponse) {
                // Calculate generation time
                long generationTimeMs = System.currentTimeMillis() - generationStartTime;
                
                // Handle completed response
                Log.d(TAG, "AI response generation completed. Length: " + finalResponse.length() + 
                      ", Tokens: " + currentTokenCount + ", Time: " + generationTimeMs + "ms");
                
                // Save AI response to database on background thread
                databaseExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // Save AI response to database with token statistics
                            long timestamp = System.currentTimeMillis();
                            Message aiMessage = new Message(chatId, finalResponse, false, timestamp);
                            aiMessage.tokenCount = currentTokenCount;
                            aiMessage.generationTimeMs = generationTimeMs;
                            
                            // Update chat last message time
                            Chat currentChat = database.chatDao().getChatById(chatId);
                            if (currentChat != null) {
                                currentChat.lastMessageTime = timestamp;
                                database.addMessageAndUpdateChat(aiMessage, currentChat);
                                
                                // Reload messages and chat history on main thread
                                if (getListener() != null) {
                                    runOnMainThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            loadMessages(chatId);
                                            loadChatHistory();
                                            
                                            ChatManagerListener listener = getListener();
                                            if (listener != null) {
                                                listener.onAIResponseComplete(finalResponse);
                                            }
                                        }
                                    });
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error saving AI response to database", e);
                            if (getListener() != null) {
                                runOnMainThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ChatManagerListener listener = getListener();
                                        if (listener != null) {
                                            listener.onAIResponseError("Failed to save AI response: " + e.getMessage());
                                        }
                                    }
                                });
                            }
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                // Handle generation errors
                Log.e(TAG, "AI response generation failed: " + error);
                if (getListener() != null) {
                    runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            ChatManagerListener listener = getListener();
                            if (listener != null) {
                                listener.onAIResponseError("AI generation failed: " + error);
                            }
                        }
                    });
                }
            }
            
            @Override
            public void onStopped(String partialResponse) {
                // Calculate generation time
                long generationTimeMs = System.currentTimeMillis() - generationStartTime;
                
                // Handle stopped generation with partial response
                Log.d(TAG, "AI response generation stopped. Partial length: " + partialResponse.length() + 
                      ", Tokens: " + currentTokenCount + ", Time: " + generationTimeMs + "ms");
                
                // Save partial AI response to database on background thread
                databaseExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // Save partial AI response to database with token statistics
                            long timestamp = System.currentTimeMillis();
                            Message aiMessage = new Message(chatId, partialResponse, false, timestamp);
                            aiMessage.tokenCount = currentTokenCount;
                            aiMessage.generationTimeMs = generationTimeMs;
                            
                            // Update chat last message time
                            Chat currentChat = database.chatDao().getChatById(chatId);
                            if (currentChat != null) {
                                currentChat.lastMessageTime = timestamp;
                                database.addMessageAndUpdateChat(aiMessage, currentChat);
                                
                                // Reload messages and chat history on main thread
                                if (getListener() != null) {
                                    runOnMainThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            loadMessages(chatId);
                                            loadChatHistory();
                                            
                                            ChatManagerListener listener = getListener();
                                            if (listener != null) {
                                                listener.onAIResponseStopped();
                                            }
                                        }
                                    });
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error saving partial AI response to database", e);
                            if (getListener() != null) {
                                runOnMainThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ChatManagerListener listener = getListener();
                                        if (listener != null) {
                                            listener.onAIResponseError("Failed to save partial response: " + e.getMessage());
                                        }
                                    }
                                });
                            }
                        }
                    }
                });
            }
        });
    }
    
    /**
     * Initialize the AI engine
     * @param callback Callback with initialization result
     */
    public void initializeAI(Runnable onSuccess, Runnable onError) {
        databaseExecutor.execute(() -> {
            try {
                Log.d(TAG, "AI initialization called");
                if (onSuccess != null) {
                    runOnMainThread(onSuccess);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize AI", e);
                if (onError != null) {
                    runOnMainThread(onError);
                }
            }
        });
    }
    
    /**
     * Check if AI model is loaded
     */
    public boolean isAIModelLoaded() {
        return getAI().isModelLoaded();
    }
    
    /**
     * Stop the current inference/generation
     */
    public void stopInference() {
        Log.d(TAG, "Stopping inference");
        getAI().stopGeneration();
        
        // Notify UI that inference was stopped
        if (getListener() != null) {
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    ChatManagerListener listener = getListener();
                    if (listener != null) {
                        listener.onAIResponseStopped();
                    }
                }
            });
        }
    }
    
    /**
     * Check if AI is currently generating a response
     */
    public boolean isGenerating() {
        return getAI().isGenerating();
    }
    
    private String generateChatTitle(String firstMessage) {
        // Generate a title from the first message (max 30 characters)
        if (firstMessage.length() <= 30) {
            return firstMessage;
        } else {
            return firstMessage.substring(0, 27) + "...";
        }
    }
    
    // Getters for accessing database directly if needed
    public ChatDatabase getDatabase() {
        return database;
    }
    
    // Helper method to ensure UI updates happen on main thread
    private void runOnMainThread(Runnable action) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            // Already on main thread
            action.run();
        } else {
            // Switch to main thread using Handler (more reliable than getMainExecutor)
            mainHandler.post(action);
        }
    }
    
    // Cleanup method to shut down executor and AI
    /**
     * Callback interface for eager model loading
     */
    public interface ModelLoadCallback {
        void onModelLoaded(long loadDuration);
        void onModelLoadFailed(String error);
    }
    
    /**
     * Loads a model eagerly (immediately) when selected from dropdown
     * This is the ONLY place where model loading should happen
     */
    public void loadModelEagerly(ModelConfig.Model model, ModelLoadCallback callback) {
        if (model == null) {
            if (callback != null) {
                callback.onModelLoadFailed("No model specified");
            }
            return;
        }
        
        // Build model path
        File externalFilesDir = context.getExternalFilesDir(null);
        String modelPath;
        if (externalFilesDir != null) {
            modelPath = new File(externalFilesDir, "models/" + model.fileName).getAbsolutePath();
        } else {
            modelPath = context.getFilesDir() + "/models/" + model.fileName;
        }
        
        final String finalModelPath = modelPath;
        final long startTime = System.currentTimeMillis();
        
        // Notify listener that model loading has started
        ChatManagerListener listener = getListener();
        if (listener != null) {
            runOnMainThread(() -> listener.onModelLoadingStarted());
        }
        
        // Use the new progress-enabled loading method
        getAI().loadModelWithProgress(finalModelPath, new MobiGPTAI.ModelLoadProgressCallback() {
            @Override
            public void onProgress(int progress, @org.jetbrains.annotations.NotNull String status) {
                Log.d(TAG, "Model loading progress: " + progress + "% - " + status);
                ChatManagerListener listener = getListener();
                if (listener != null) {
                    listener.onModelLoadingProgress(progress, status);
                }
            }
            
            @Override
            public void onComplete(boolean success) {
                long duration = System.currentTimeMillis() - startTime;
                
                if (success) {
                    Log.d(TAG, "Model loaded successfully in " + duration + "ms: " + model.displayName);
                    
                    // Notify the ChatManagerListener (MainActivity) that model is loaded
                    ChatManagerListener listener = getListener();
                    if (listener != null) {
                        runOnMainThread(() -> listener.onModelLoaded(duration));
                    }
                    
                    // Also notify the ModelLoadCallback
                    if (callback != null) {
                        callback.onModelLoaded(duration);
                    }
                } else {
                    Log.e(TAG, "Failed to load model: " + model.displayName);
                    
                    // Notify listener of failure (hide progress bar, re-enable button)
                    ChatManagerListener listener = getListener();
                    if (listener != null) {
                        runOnMainThread(() -> listener.onModelLoadingError("Failed to load model"));
                    }
                    
                    // Also notify the ModelLoadCallback
                    if (callback != null) {
                        callback.onModelLoadFailed("Failed to load model: " + model.displayName);
                    }
                }
            }
        });
    }
    
    /**
     * Update a message in the database (e.g., for starring/unstarring)
     * @param message The message to update
     */
    public void updateMessageInDatabase(Message message) {
        if (message == null) {
            Log.w(TAG, "updateMessageInDatabase: message is null");
            return;
        }
        
        databaseExecutor.execute(() -> {
            try {
                database.messageDao().updateMessage(message);
                Log.d(TAG, "Message updated in database: id=" + message.id + ", starred=" + message.isStarred);
            } catch (Exception e) {
                Log.e(TAG, "Error updating message in database", e);
                runOnMainThread(() -> {
                    ChatManagerListener listener = getListener();
                    if (listener != null) {
                        listener.onError("Failed to update message");
                    }
                });
            }
        });
    }
    
    public void cleanup() {
        // Clear listener to prevent callbacks after cleanup
        listenerRef = null;
        
        if (databaseExecutor != null && !databaseExecutor.isShutdown()) {
            databaseExecutor.shutdown();
        }
        // Cleanup AI resources
        if (mobiGPTAI != null) {
            getAI().cleanup();
        }
    }
    
    /**
     * Get AI instance with lazy initialization to prevent early native library loading
     */
    private MobiGPTAI getAI() {
        if (mobiGPTAI == null) {
            synchronized (this) {
                if (mobiGPTAI == null) {
                    Log.d(TAG, "Initializing MobiGPTAI instance");
                    mobiGPTAI = MobiGPTAI.Companion.getInstance();
                }
            }
        }
        return mobiGPTAI;
    }
}