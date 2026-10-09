package com.keralatechreach.mobigpt.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.keralatechreach.mobigpt.Constants;
import com.keralatechreach.mobigpt.R;
import com.keralatechreach.mobigpt.database.ChatDatabase;
import com.keralatechreach.mobigpt.database.Message;
import com.keralatechreach.mobigpt.utils.MarkdownFormatter;
import com.keralatechreach.mobigpt.utils.SettingsManager;
import com.keralatechreach.mobigpt.utils.TypographyManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements com.keralatechreach.mobigpt.utils.ThemeColorUpdater.ThemeAware {

    private static final int MAX_RECYCLED_VIEWS = Constants.RECYCLERVIEW_POOL_SIZE;

	private static final String TAG = "MessageAdapter";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Context context;
    private final MarkdownFormatter markdownFormatter;
    private final SettingsManager settingsManager;
    private final TypographyManager typographyManager;
    private ExecutorService backgroundExecutor;
    
    private List<Message> messages = new ArrayList<>();
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    
    // Track animated messages to prevent re-animating on scroll
    private final Set<Long> animatedMessageIds = new HashSet<>();
    
    // Status message handling
    private Message statusMessage = null;
    private boolean isShowingStatus = false;
    
    // Streaming message handling
    private Message streamingMessage = null;
    private boolean isShowingStreaming = false;
    private StringBuilder streamingContent = new StringBuilder();
    
    // Multi-select mode handling
    private boolean isSelectionMode = false;
    private final Set<Long> selectedMessageIds = new HashSet<>();
    
    // Search/highlight functionality
    private String searchQuery = null;
    private boolean isSearchMode = false;
    
    // Context menu interface
    public interface OnMessageActionListener {
        void onMessageCopied(Message message);
        void onMessageStarred(Message message, boolean isStarred);
        void onMessageRegenerated(Message message);
        void onMessageRetried(Message message);
        void onMessageShared(Message message);
        void onMultipleMessagesShared(List<Message> messages);
        void onSelectionModeChanged(boolean isSelectionMode, int selectedCount);
    }
    
    private OnMessageActionListener messageActionListener;
    
    // Batching mechanism to reduce UI updates during streaming
    private static final int BATCH_UPDATE_DELAY_MS = 50; // Update UI every 50ms max
    private final Handler batchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingBatchUpdate = null;
    private boolean hasPendingTokens = false;
    
    // Memory optimization - track view recycling
    private final RecyclerView.RecycledViewPool recycledViewPool = new RecyclerView.RecycledViewPool();
    
    public MessageAdapter(Context context) {
        this.context = context;
        this.markdownFormatter = context != null ? new MarkdownFormatter(context) : null;
        this.settingsManager = context != null ? new SettingsManager(context) : null;
        this.typographyManager = context != null ? new TypographyManager(context) : null;
        this.backgroundExecutor = Executors.newSingleThreadExecutor();
        
        // Configure view pool for memory efficiency
        recycledViewPool.setMaxRecycledViews(Constants.VIEW_TYPE_USER_MESSAGE, MAX_RECYCLED_VIEWS);
        recycledViewPool.setMaxRecycledViews(Constants.VIEW_TYPE_AI_MESSAGE, MAX_RECYCLED_VIEWS);
        setHasStableIds(true); // Enable stable IDs for better performance
    }
    
    // Backward compatibility constructor
    @Deprecated
    public MessageAdapter() {
        this(null);
    }
    
    public void setOnMessageActionListener(OnMessageActionListener listener) {
        this.messageActionListener = listener;
    }
    
    /**
     * Helper method to perform haptic feedback if enabled in settings
     */
    private void performHapticFeedback(View view, int feedbackConstant) {
        if (settingsManager != null && settingsManager.isHapticFeedback() && view != null) {
            view.performHapticFeedback(feedbackConstant);
        }
    }
    
    /**
     * ThemeAware implementation - refresh all views when theme changes
     */
    @Override
    public void onThemeChanged() {
        Log.d(TAG, "Theme changed - refreshing all message views");
        notifyDataSetChanged();
    }
    
    /**
     * Get a valid executor service, creating a new one if necessary
     * This ensures the adapter can recover from cleanup() being called
     */
    private ExecutorService getExecutor() {
        if (backgroundExecutor == null || backgroundExecutor.isShutdown() || backgroundExecutor.isTerminated()) {
            android.util.Log.d("MessageAdapter", "Creating new background executor (old was shutdown)");
            backgroundExecutor = Executors.newSingleThreadExecutor();
        }
        return backgroundExecutor;
    }
    
    // Memory leak prevention methods
    public void cleanup() {
        messages.clear();
        animatedMessageIds.clear();
        clearStatusMessage();
        clearStreamingMessage();
        recycledViewPool.clear();
        messageActionListener = null;
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
        notifyDataSetChanged();
    }
    
    /**
     * Memory optimization - trim old messages if list gets too large
     */
    public void trimMemory() {
        if (messages.size() > Constants.MESSAGE_PAGINATION_SIZE * 2) {
            int itemsToRemove = messages.size() - Constants.MESSAGE_PAGINATION_SIZE;
            
            // Remove animation tracking for trimmed messages
            for (int i = 0; i < itemsToRemove; i++) {
                if (messages.get(i) != null) {
                    animatedMessageIds.remove(messages.get(i).id);
                }
            }
            
            messages.subList(0, itemsToRemove).clear();
            notifyItemRangeRemoved(0, itemsToRemove);
        }
    }
    
    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }
    
    public void updateMessages(List<Message> newMessages) {
        if (newMessages == null) {
            newMessages = new ArrayList<>();
        }
        
        MessageDiffCallback diffCallback = new MessageDiffCallback(messages, newMessages);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        
        messages.clear();
        messages.addAll(newMessages);
        diffResult.dispatchUpdatesTo(this);
    }
    
    public void setMessages(List<Message> messages) {
        if (this.messages.isEmpty()) {
            // First time setting messages
            this.messages.clear();
            this.messages.addAll(messages != null ? messages : new ArrayList<>());
            notifyDataSetChanged();
        } else {
            // Use DiffUtil for better performance
            updateMessages(messages);
        }
    }
    
    /**
     * Show a status message (e.g., "Model loading...", "Thinking...")
     * This appears as a temporary AI message at the bottom of the chat
     */
    public void showStatusMessage(String text) {
        // Remove any existing status message first
        clearStatusMessage();
        
        // Create a temporary status message
        statusMessage = new Message(-1, text, false, System.currentTimeMillis());
        isShowingStatus = true;
        
        // Add it to the end of the list and notify
        notifyItemInserted(getItemCount() - 1);
    }
    
    /**
     * Update the current status message text
     */
    public void updateStatusMessage(String text) {
        if (isShowingStatus && statusMessage != null) {
            statusMessage.content = text;
            notifyItemChanged(getItemCount() - 1);
        } else {
            // If no status message is showing, create one
            showStatusMessage(text);
        }
    }
    
    /**
     * Clear/remove the status message
     */
    public void clearStatusMessage() {
        if (isShowingStatus) {
            isShowingStatus = false;
            statusMessage = null;
            // Notify that the last item (status message) was removed
            notifyItemRemoved(getEffectiveItemCount() - 1);
        }
    }
    
    /**
     * Start a streaming AI response
     * This creates a temporary AI message that will be updated with each token
     */
    public void startStreamingResponse() {
        // Clear any existing streaming message
        clearStreamingMessage();
        
        // Also clear status message since we're starting the actual response
        clearStatusMessage();
        
        // Create streaming message
        streamingContent.setLength(0); // Clear the content buffer
        streamingMessage = new Message(-2, "", false, System.currentTimeMillis());
        isShowingStreaming = true;
        
        // Add it to the end of the list and notify
        notifyItemInserted(getEffectiveItemCount() - 1);
    }
    
    /**
     * Add a token to the streaming response
     * Uses batching to reduce UI update frequency and prevent text realignment
     */
    public void addStreamingToken(String token) {
        if (isShowingStreaming && streamingMessage != null) {
            // Append token to buffer immediately
            streamingContent.append(token);
            streamingMessage.content = streamingContent.toString();
            
            // Schedule batched UI update to reduce layout recalculations
            scheduleBatchUpdate();
        } else {
            // If no streaming message is active, start one
            startStreamingResponse();
            addStreamingToken(token);
        }
    }
    
    /**
     * Get the markdown formatter instance (for external use if needed)
     * @return MarkdownFormatter instance or null if not available
     */
    public MarkdownFormatter getMarkdownFormatter() {
        return markdownFormatter;
    }
    
    /**
     * Check if markdown formatting is available
     * @return true if markdown formatting is supported, false otherwise
     */
    public boolean isMarkdownFormattingEnabled() {
        return markdownFormatter != null;
    }
    
    /**
     * Schedule a batched UI update to reduce layout recalculations
     * This prevents text realignment issues during streaming
     */
    private void scheduleBatchUpdate() {
        // If there's already a pending update, don't schedule another
        if (hasPendingTokens) {
            return;
        }
        
        hasPendingTokens = true;
        
        // Cancel any existing pending update
        if (pendingBatchUpdate != null) {
            batchHandler.removeCallbacks(pendingBatchUpdate);
        }
        
        // Schedule the update
        pendingBatchUpdate = () -> {
            if (isShowingStreaming) {
                int streamingPosition = getEffectiveItemCount() - 1;
                notifyItemChanged(streamingPosition);
            }
            hasPendingTokens = false;
            pendingBatchUpdate = null;
        };
        
        batchHandler.postDelayed(pendingBatchUpdate, BATCH_UPDATE_DELAY_MS);
    }
    
    /**
     * Complete the streaming response (this will be replaced by the actual saved message)
     */
    public void completeStreamingResponse() {
        if (isShowingStreaming) {
            // Force any pending updates immediately before clearing
            if (pendingBatchUpdate != null) {
                batchHandler.removeCallbacks(pendingBatchUpdate);
                if (isShowingStreaming) {
                    int streamingPosition = getEffectiveItemCount() - 1;
                    notifyItemChanged(streamingPosition);
                }
                pendingBatchUpdate = null;
                hasPendingTokens = false;
            }
            clearStreamingMessage();
        }
    }
    
    /**
     * Clear/remove the streaming message
     */
    public void clearStreamingMessage() {
        if (isShowingStreaming) {
            // Cancel any pending batch updates
            if (pendingBatchUpdate != null) {
                batchHandler.removeCallbacks(pendingBatchUpdate);
                pendingBatchUpdate = null;
            }
            hasPendingTokens = false;
            
            int positionToRemove = getEffectiveItemCount() - 1;
            isShowingStreaming = false;
            streamingMessage = null;
            streamingContent.setLength(0);
            // Notify that the streaming message was removed
            notifyItemRemoved(positionToRemove);
        }
    }
    
    /**
     * Check if currently showing a streaming message
     */
    public boolean isStreamingActive() {
        return isShowingStreaming;
    }
    
    /**
     * Get current streaming content (for debugging)
     */
    public String getCurrentStreamingContent() {
        return isShowingStreaming ? streamingContent.toString() : "";
    }
    
    /**
     * Get the list of messages (for regeneration and other operations)
     */
    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }
    
    /**
     * Remove a specific message from the adapter
     */
    public void removeMessage(Message message) {
        if (message == null) return;
        
        int position = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).id == message.id) {
                position = i;
                break;
            }
        }
        
        if (position >= 0) {
            messages.remove(position);
            notifyItemRemoved(position);
            
            // Also delete from database in background
            if (backgroundExecutor != null && !backgroundExecutor.isShutdown() && !backgroundExecutor.isTerminated() && context != null) {
                backgroundExecutor.execute(() -> {
                    try {
                        ChatDatabase database = ChatDatabase.getDatabase(context);
                        database.messageDao().deleteMessage(message);
                    } catch (Exception e) {
                        android.util.Log.e("MessageAdapter", "Failed to delete message from database", e);
                    }
                });
            }
        }
    }
    
    /**
     * Enter selection mode
     */
    public void enterSelectionMode() {
        Log.d(TAG, "enterSelectionMode: Currently in selection mode: " + isSelectionMode);
        
        if (!isSelectionMode) {
            Log.d(TAG, "enterSelectionMode: Entering selection mode");
            isSelectionMode = true;
            selectedMessageIds.clear();
            Log.d(TAG, "enterSelectionMode: Selection mode activated, selectedMessageIds cleared");
            notifySelectionModeChanged();
        } else {
            Log.d(TAG, "enterSelectionMode: Already in selection mode, no change");
        }
    }
    
    /**
     * Exit selection mode
     */
    public void exitSelectionMode() {
        Log.d(TAG, "exitSelectionMode: Currently in selection mode: " + isSelectionMode + ", selectedCount=" + selectedMessageIds.size());
        
        if (isSelectionMode) {
            Log.d(TAG, "exitSelectionMode: Exiting selection mode");
            isSelectionMode = false;
            selectedMessageIds.clear();
            Log.d(TAG, "exitSelectionMode: Selection mode deactivated, selectedMessageIds cleared, refreshing UI");
            notifyDataSetChanged(); // Refresh all items to remove selection overlays
            notifySelectionModeChanged();
        } else {
            Log.d(TAG, "exitSelectionMode: Not in selection mode, no change");
        }
    }
    
    /**
     * Toggle selection for a message
     */
    public void toggleMessageSelection(Message message, int position) {
        Log.d(TAG, "toggleMessageSelection: messageId=" + (message != null ? message.id : "null") + ", position=" + position + ", inSelectionMode=" + isSelectionMode);
        
        if (!isSelectionMode || message == null) {
            if (!isSelectionMode) {
                Log.w(TAG, "toggleMessageSelection: Not in selection mode, cannot toggle");
            }
            if (message == null) {
                Log.w(TAG, "toggleMessageSelection: Message is null, cannot toggle");
            }
            return;
        }
        
        boolean wasSelected = selectedMessageIds.contains(message.id);
        
        if (wasSelected) {
            selectedMessageIds.remove(message.id);
            Log.d(TAG, "toggleMessageSelection: Deselected messageId=" + message.id + ", newCount=" + selectedMessageIds.size());
        } else {
            selectedMessageIds.add(message.id);
            Log.d(TAG, "toggleMessageSelection: Selected messageId=" + message.id + ", newCount=" + selectedMessageIds.size());
        }
        
        notifyItemChanged(position);
        notifySelectionModeChanged();
    }
    
    /**
     * Check if a message is selected
     */
    public boolean isMessageSelected(Message message) {
        return message != null && selectedMessageIds.contains(message.id);
    }
    
    /**
     * Get number of selected messages
     */
    public int getSelectedCount() {
        return selectedMessageIds.size();
    }
    
    /**
     * Check if in selection mode
     */
    public boolean isInSelectionMode() {
        return isSelectionMode;
    }
    
    /**
     * Get all selected messages
     */
    public List<Message> getSelectedMessages() {
        List<Message> selected = new ArrayList<>();
        for (Message message : messages) {
            if (selectedMessageIds.contains(message.id)) {
                selected.add(message);
            }
        }
        return selected;
    }
    
    /**
     * Select all messages
     */
    public void selectAll() {
        Log.d(TAG, "selectAll: inSelectionMode=" + isSelectionMode + ", totalMessages=" + messages.size());
        
        if (!isSelectionMode) {
            Log.w(TAG, "selectAll: Not in selection mode, cannot select all");
            return;
        }
        
        selectedMessageIds.clear();
        int selectableCount = 0;
        
        for (Message message : messages) {
            if (message.id > 0) { // Don't select status or streaming messages
                selectedMessageIds.add(message.id);
                selectableCount++;
            }
        }
        
        Log.d(TAG, "selectAll: Selected " + selectableCount + " messages (skipped " + (messages.size() - selectableCount) + " non-selectable)");
        
        notifyDataSetChanged();
        notifySelectionModeChanged();
    }
    
    /**
     * Deselect all messages
     */
    public void deselectAll() {
        Log.d(TAG, "deselectAll: inSelectionMode=" + isSelectionMode + ", currentlySelected=" + selectedMessageIds.size());
        
        if (!isSelectionMode) {
            Log.w(TAG, "deselectAll: Not in selection mode, cannot deselect");
            return;
        }
        
        int previousCount = selectedMessageIds.size();
        selectedMessageIds.clear();
        
        Log.d(TAG, "deselectAll: Cleared " + previousCount + " selections");
        
        notifyDataSetChanged();
        notifySelectionModeChanged();
    }
    
    /**
     * Notify listener about selection mode changes
     */
    private void notifySelectionModeChanged() {
        Log.d(TAG, "notifySelectionModeChanged: isSelectionMode=" + isSelectionMode + ", selectedCount=" + selectedMessageIds.size());
        if (selectedMessageIds.size() > 0) {
            Log.d(TAG, "notifySelectionModeChanged: Selected message IDs: " + selectedMessageIds);
        }
        
        if (messageActionListener != null) {
            Log.d(TAG, "notifySelectionModeChanged: Calling listener.onSelectionModeChanged");
            messageActionListener.onSelectionModeChanged(isSelectionMode, selectedMessageIds.size());
        } else {
            Log.w(TAG, "notifySelectionModeChanged: messageActionListener is null, cannot notify");
        }
    }
    
    // ===== SEARCH FUNCTIONALITY =====
    
    /**
     * Set search query and enable search mode
     * This will highlight matching text in messages
     */
    public void setSearchQuery(String query) {
        this.searchQuery = query;
        this.isSearchMode = query != null && !query.trim().isEmpty();
        notifyDataSetChanged(); // Refresh all items to apply/remove highlights
    }
    
    /**
     * Clear search query and exit search mode
     */
    public void clearSearch() {
        this.searchQuery = null;
        this.isSearchMode = false;
        notifyDataSetChanged(); // Refresh all items to remove highlights
    }
    
    /**
     * Get current search query
     */
    public String getSearchQuery() {
        return searchQuery;
    }
    
    /**
     * Check if in search mode
     */
    public boolean isInSearchMode() {
        return isSearchMode;
    }
    
    /**
     * Find position of next message matching search query
     * @param startPosition Position to start searching from (exclusive)
     * @return Position of next matching message, or -1 if none found
     */
    public int findNextMatch(int startPosition) {
        if (!isSearchMode || messages == null || messages.isEmpty()) {
            return -1;
        }
        
        String query = searchQuery.toLowerCase(Locale.getDefault());
        for (int i = startPosition + 1; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message != null && message.content != null) {
                if (message.content.toLowerCase(Locale.getDefault()).contains(query)) {
                    return i;
                }
            }
        }
        
        // Wrap around to beginning
        for (int i = 0; i <= startPosition; i++) {
            Message message = messages.get(i);
            if (message != null && message.content != null) {
                if (message.content.toLowerCase(Locale.getDefault()).contains(query)) {
                    return i;
                }
            }
        }
        
        return -1;
    }
    
    /**
     * Find position of previous message matching search query
     * @param startPosition Position to start searching from (exclusive)
     * @return Position of previous matching message, or -1 if none found
     */
    public int findPreviousMatch(int startPosition) {
        if (!isSearchMode || messages == null || messages.isEmpty()) {
            return -1;
        }
        
        String query = searchQuery.toLowerCase(Locale.getDefault());
        for (int i = startPosition - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message != null && message.content != null) {
                if (message.content.toLowerCase(Locale.getDefault()).contains(query)) {
                    return i;
                }
            }
        }
        
        // Wrap around to end
        for (int i = messages.size() - 1; i >= startPosition; i--) {
            Message message = messages.get(i);
            if (message != null && message.content != null) {
                if (message.content.toLowerCase(Locale.getDefault()).contains(query)) {
                    return i;
                }
            }
        }
        
        return -1;
    }
    
    /**
     * Count total matches for current search query
     */
    public int getMatchCount() {
        if (!isSearchMode || messages == null) {
            return 0;
        }
        
        int count = 0;
        String query = searchQuery.toLowerCase(Locale.getDefault());
        for (Message message : messages) {
            if (message != null && message.content != null) {
                if (message.content.toLowerCase(Locale.getDefault()).contains(query)) {
                    count++;
                }
            }
        }
        return count;
    }
    
    // ===== END SEARCH FUNCTIONALITY =====
    
    /**
     * Highlight search query in text
     * @param text Text to highlight
     * @param query Search query to highlight
     * @return CharSequence with highlighted text, or original text if no query
     */
    private CharSequence highlightSearchQuery(String text, String query) {
        if (text == null || query == null || query.trim().isEmpty()) {
            return text;
        }
        
        String lowerText = text.toLowerCase(Locale.getDefault());
        String lowerQuery = query.toLowerCase(Locale.getDefault());
        
        SpannableString spannable = new SpannableString(text);
        
        int startPos = 0;
        while (startPos < lowerText.length()) {
            int index = lowerText.indexOf(lowerQuery, startPos);
            if (index == -1) {
                break;
            }
            
            // Highlight with yellow background and dark text
            spannable.setSpan(
                new BackgroundColorSpan(Color.parseColor("#FFEB3B")), // Yellow highlight
                index,
                index + query.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            spannable.setSpan(
                new ForegroundColorSpan(Color.parseColor("#000000")), // Black text
                index,
                index + query.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            
            startPos = index + query.length();
        }
        
        return spannable;
    }
    
    // DiffUtil callback for efficient updates
    private static class MessageDiffCallback extends DiffUtil.Callback {
        private final List<Message> oldList;
        private final List<Message> newList;
        
        MessageDiffCallback(List<Message> oldList, List<Message> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }
        
        @Override
        public int getOldListSize() {
            return oldList.size();
        }
        
        @Override
        public int getNewListSize() {
            return newList.size();
        }
        
        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).id == newList.get(newItemPosition).id;
        }
        
        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Message oldMessage = oldList.get(oldItemPosition);
            Message newMessage = newList.get(newItemPosition);
            
            return oldMessage.content.equals(newMessage.content) &&
                   oldMessage.timestamp == newMessage.timestamp &&
                   oldMessage.isUser == newMessage.isUser &&
                   oldMessage.isStarred == newMessage.isStarred;
        }
    }
    
    @Override
    public int getItemViewType(int position) {
        // Check if this is the status message position
        if (isShowingStatus && position == messages.size()) {
            return Constants.VIEW_TYPE_AI_MESSAGE; // Status messages appear as AI messages
        }
        // Check if this is the streaming message position
        if (isShowingStreaming && position == getEffectiveItemCount() - 1) {
            return Constants.VIEW_TYPE_AI_MESSAGE; // Streaming messages appear as AI messages
        }
        return messages.get(position).isUser ? Constants.VIEW_TYPE_USER_MESSAGE : Constants.VIEW_TYPE_AI_MESSAGE;
    }
    
    @Override
    public long getItemId(int position) {
        // Return stable ID based on message ID
        if (isShowingStatus && position == messages.size()) {
            return -1; // Special ID for status message
        }
        if (isShowingStreaming && position == getEffectiveItemCount() - 1) {
            return -2; // Special ID for streaming message
        }
        return messages.get(position).id;
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Set recycled view pool size to limit memory usage
        if (parent instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) parent;
            recyclerView.setRecycledViewPool(recycledViewPool);
        }
        
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        
        if (viewType == Constants.VIEW_TYPE_USER_MESSAGE) {
            View view = inflater.inflate(R.layout.item_message_user, parent, false);
            return new UserMessageViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_message_ai, parent, false);
            return new AIMessageViewHolder(view);
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message;
        boolean isStatusOrStreaming = false;
        
        // Check if this is the status message position
        if (isShowingStatus && position == messages.size()) {
            message = statusMessage;
            isStatusOrStreaming = true;
        }
        // Check if this is the streaming message position
        else if (isShowingStreaming && position == getEffectiveItemCount() - 1) {
            message = streamingMessage;
            isStatusOrStreaming = true;
        } else {
            message = messages.get(position);
        }
        
        if (holder instanceof UserMessageViewHolder) {
            // Don't set long click listener for status/streaming messages
            View.OnLongClickListener longClickListener = isStatusOrStreaming ? null : v -> {
                // Haptic feedback on long press
                performHapticFeedback(v, HapticFeedbackConstants.LONG_PRESS);
                
                android.util.Log.d("MessageAdapter", "Long press detected on user message at position: " + position);
                
                // If in selection mode, toggle selection on long press too
                if (isSelectionMode) {
                    toggleMessageSelection(message, position);
                } else {
                    showContextMenu(v, message, position);
                }
                return true;
            };
            
            // Add click listener for selection mode
            View.OnClickListener clickListener = isStatusOrStreaming ? null : v -> {
                if (isSelectionMode) {
                    performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    toggleMessageSelection(message, position);
                }
            };
            
            ((UserMessageViewHolder) holder).bind(message, markdownFormatter, longClickListener, clickListener, isMessageSelected(message), this);
        } else if (holder instanceof AIMessageViewHolder) {
            // Add visual indicator for streaming messages
            boolean isStreaming = (isShowingStreaming && position == getEffectiveItemCount() - 1);
            // Don't set long click listener for status/streaming messages
            View.OnLongClickListener longClickListener = isStatusOrStreaming ? null : v -> {
                // Haptic feedback on long press
                performHapticFeedback(v, HapticFeedbackConstants.LONG_PRESS);
                
                android.util.Log.d("MessageAdapter", "Long press detected on AI message at position: " + position + ", isStreaming: " + isStreaming);
                
                // If in selection mode, toggle selection on long press too
                if (isSelectionMode) {
                    toggleMessageSelection(message, position);
                } else {
                    showContextMenu(v, message, position);
                }
                return true;
            };
            
            // Add click listener for selection mode
            View.OnClickListener clickListener = isStatusOrStreaming ? null : v -> {
                if (isSelectionMode) {
                    performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
                    toggleMessageSelection(message, position);
                }
            };
            
            ((AIMessageViewHolder) holder).bind(message, isStreaming, markdownFormatter, longClickListener, clickListener, isMessageSelected(message), this);
        }
    }

    @Override
    public int getItemCount() {
        return getEffectiveItemCount();
    }
    
    /**
     * Calculate the effective item count including status and streaming messages
     */
    private int getEffectiveItemCount() {
        int baseCount = messages.size();
        if (isShowingStatus) baseCount++;
        if (isShowingStreaming) baseCount++;
        return baseCount;
    }    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        // Clear any heavy resources when view is recycled
        if (holder instanceof UserMessageViewHolder) {
            ((UserMessageViewHolder) holder).clear();
        } else if (holder instanceof AIMessageViewHolder) {
            ((AIMessageViewHolder) holder).clear();
        }
    }
    
    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        // Optimize for performance when view is attached
    }
    
    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        // Clear resources when view is detached
    }
    
    /**
     * Get the recycled view pool for external configuration
     */
    public RecyclerView.RecycledViewPool getRecycledViewPool() {
        return recycledViewPool;
    }
    
    /**
     * Animate message entrance with fade-in and slide-up effect
     * @param view The view to animate
     * @param messageId The ID of the message being animated
     */
    private void animateMessageEntrance(View view, long messageId) {
        // Skip animation for status messages (id = -1) or streaming messages (id = -2)
        if (messageId < 0) {
            return;
        }
        
        // Only animate if we haven't animated this message before
        if (!animatedMessageIds.contains(messageId)) {
            animatedMessageIds.add(messageId);
            
            // Set initial state
            view.setAlpha(0f);
            view.setTranslationY(20f);
            
            // Animate to final state
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        } else {
            // Ensure view is visible if it was already animated
            view.setAlpha(1f);
            view.setTranslationY(0f);
        }
    }
    
    /**
     * Show context menu for a message
     */
    private void showContextMenu(View anchor, Message message, int position) {
        if (context == null || message == null) return;
        
        // Don't show context menu for status or streaming messages
        if (message.id == -1 || message.id == -2) {
            android.util.Log.d("MessageAdapter", "Ignoring long press on status/streaming message");
            return;
        }
        
        android.util.Log.d("MessageAdapter", "Showing context menu for message ID: " + message.id + " at position: " + position);
        
        View contextMenuView = LayoutInflater.from(context).inflate(R.layout.message_context_menu, null);
        
        PopupWindow popupWindow = new PopupWindow(
            contextMenuView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        );
        
        // Set elevation for modern devices and ensure popup displays properly
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            popupWindow.setElevation(8.0f);
        }
        
        // Set a simple transparent background to ensure popup window displays
        popupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        
        // Setup context menu items with null checks
        LinearLayout btnCopy = contextMenuView.findViewById(R.id.btn_copy_message);
        LinearLayout btnStar = contextMenuView.findViewById(R.id.btn_star_message);
        LinearLayout btnRegenerate = contextMenuView.findViewById(R.id.btn_regenerate_message);
        LinearLayout btnRetry = contextMenuView.findViewById(R.id.btn_retry_message);
        LinearLayout btnShare = contextMenuView.findViewById(R.id.btn_share_message);
        LinearLayout btnSelectMultiple = contextMenuView.findViewById(R.id.btn_select_multiple);
        ImageView iconStar = contextMenuView.findViewById(R.id.icon_star);
        TextView textStarAction = contextMenuView.findViewById(R.id.text_star_action);
        
        // Verify all required views are present
        if (btnCopy == null || btnStar == null || btnRegenerate == null || 
            btnRetry == null || btnShare == null || btnSelectMultiple == null) {
            android.util.Log.e("MessageAdapter", "Context menu views not found in layout");
            return;
        }
        
        // Update star button
        if (iconStar != null && textStarAction != null) {
            iconStar.setImageResource(message.isStarred ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
            textStarAction.setText(message.isStarred ? "Unstar" : "Star");
        }
        
        // Show/hide regenerate and retry buttons based on message type
        if (message.isUser) {
            // For user messages: hide regenerate, show retry
            btnRegenerate.setVisibility(View.GONE);
            View dividerAboveRegenerate = contextMenuView.findViewById(R.id.divider_before_regenerate);
            if (dividerAboveRegenerate != null) {
                dividerAboveRegenerate.setVisibility(View.GONE);
            }
            btnRetry.setVisibility(View.VISIBLE);
            View dividerAboveRetry = contextMenuView.findViewById(R.id.divider_before_retry);
            if (dividerAboveRetry != null) {
                dividerAboveRetry.setVisibility(View.VISIBLE);
            }
        } else {
            // For AI messages: show regenerate, hide retry
            btnRegenerate.setVisibility(View.VISIBLE);
            View dividerAboveRegenerate = contextMenuView.findViewById(R.id.divider_before_regenerate);
            if (dividerAboveRegenerate != null) {
                dividerAboveRegenerate.setVisibility(View.VISIBLE);
            }
            btnRetry.setVisibility(View.GONE);
            View dividerAboveRetry = contextMenuView.findViewById(R.id.divider_before_retry);
            if (dividerAboveRetry != null) {
                dividerAboveRetry.setVisibility(View.GONE);
            }
        }
        
        // Copy button click
        btnCopy.setOnClickListener(v -> {
            // Haptic feedback on copy action
            performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
            
            copyMessageToClipboard(message);
            popupWindow.dismiss();
        });
        
        // Star button click
        btnStar.setOnClickListener(v -> {
            // Haptic feedback on star toggle
            performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
            
            toggleMessageStarred(message, position);
            popupWindow.dismiss();
        });
        
        // Regenerate button click (only for AI messages)
        btnRegenerate.setOnClickListener(v -> {
            // Haptic feedback on regenerate action
            performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
            
            regenerateMessage(message);
            popupWindow.dismiss();
        });
        
        // Retry button click (only for user messages)
        btnRetry.setOnClickListener(v -> {
            // Haptic feedback on retry action
            performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
            
            retryMessage(message);
            popupWindow.dismiss();
        });
        
        // Share button click
        btnShare.setOnClickListener(v -> {
            // Haptic feedback on share action
            performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
            
            shareMessage(message);
            popupWindow.dismiss();
        });
        
        // Select Multiple button click
        btnSelectMultiple.setOnClickListener(v -> {
            // Haptic feedback on select action
            performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
            
            Log.d(TAG, "btnSelectMultiple clicked: messageId=" + message.id + ", position=" + position);
            Log.d(TAG, "btnSelectMultiple: Entering selection mode and selecting message");
            
            enterSelectionMode();
            toggleMessageSelection(message, position);
            popupWindow.dismiss();
            
            Log.d(TAG, "btnSelectMultiple: Context menu dismissed");
        });
        
        // Show popup window
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.showAsDropDown(anchor, 0, -anchor.getHeight());
    }
    
    /**
     * Copy message content to clipboard
     */
    void copyMessageToClipboard(Message message) {
        if (context == null || message == null) return;
        
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            
            // Null check for clipboard service - some devices may not have this service
            if (clipboard == null) {
                Toast.makeText(context, "Clipboard service not available on this device", Toast.LENGTH_SHORT).show();
                return;
            }
            
            ClipData clip = ClipData.newPlainText("Message", message.content);
            clipboard.setPrimaryClip(clip);
            
            Toast.makeText(context, "Message copied to clipboard", Toast.LENGTH_SHORT).show();
            
            if (messageActionListener != null) {
                messageActionListener.onMessageCopied(message);
            }
        } catch (Exception e) {
            // Catch any unexpected errors (SecurityException, etc.)
            Toast.makeText(context, "Failed to copy message: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Toggle starred status of a message
     */
    private void toggleMessageStarred(Message message, int position) {
        if (context == null || message == null) return;
        
        boolean newStarredState = !message.isStarred;
        message.isStarred = newStarredState;
        notifyItemChanged(position);
        
        // Update database in background
        getExecutor().execute(() -> {
            try {
                ChatDatabase.getDatabase(context).messageDao().updateMessageStarredStatus(message.id, newStarredState);
                
                handler.post(() -> {
                    Toast.makeText(context, newStarredState ? "Starred" : "Unstarred", Toast.LENGTH_SHORT).show();
                    if (messageActionListener != null) {
                        messageActionListener.onMessageStarred(message, newStarredState);
                    }
                });
            } catch (Exception e) {
                // Revert on error
                message.isStarred = !newStarredState;
                handler.post(() -> {
                    notifyItemChanged(position);
                    Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * Regenerate AI response for a message
     */
    void regenerateMessage(Message message) {
        if (context == null || message == null) return;
        
        // Only allow regenerate for AI messages
        if (message.isUser) {
            Toast.makeText(context, "Can only regenerate AI responses", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(context, "Regenerating response...", Toast.LENGTH_SHORT).show();
        
        if (messageActionListener != null) {
            messageActionListener.onMessageRegenerated(message);
        }
    }
    
    /**
     * Retry sending a user message
     */
    void retryMessage(Message message) {
        if (context == null || message == null) return;
        
        // Only allow retry for user messages
        if (!message.isUser) {
            Toast.makeText(context, "Can only retry user messages", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(context, "Retrying message...", Toast.LENGTH_SHORT).show();
        
        if (messageActionListener != null) {
            messageActionListener.onMessageRetried(message);
        }
    }
    
    /**
     * Share message content via Android share intent
     */
    void shareMessage(Message message) {
        Log.d(TAG, "shareMessage: messageId=" + (message != null ? message.id : "null"));
        
        if (context == null || message == null) {
            if (context == null) {
                Log.e(TAG, "shareMessage: Context is null, cannot share");
            }
            if (message == null) {
                Log.e(TAG, "shareMessage: Message is null, cannot share");
            }
            return;
        }
        
        Log.d(TAG, "shareMessage: isUser=" + message.isUser + ", contentLength=" + message.content.length());
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        
        // Build share text with context
        String shareText = message.content;
        
        // Add header based on message type
        if (message.isUser) {
            shareText = "My message:\n\n" + shareText;
            Log.d(TAG, "shareMessage: Adding 'My message' header");
        } else {
            shareText = "AI Response:\n\n" + shareText;
            Log.d(TAG, "shareMessage: Adding 'AI Response' header");
        }
        
        // Add app attribution
        shareText += "\n\n—\nShared from MobiGPT";
        
        Log.d(TAG, "shareMessage: Final share text length: " + shareText.length() + " characters");
        
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Message from MobiGPT");
        
        // Create chooser to show share options
        Intent chooserIntent = Intent.createChooser(shareIntent, "Share message via");
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        try {
            Log.d(TAG, "shareMessage: Starting share activity");
            context.startActivity(chooserIntent);
            
            if (messageActionListener != null) {
                Log.d(TAG, "shareMessage: Notifying listener");
                messageActionListener.onMessageShared(message);
            } else {
                Log.w(TAG, "shareMessage: messageActionListener is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "shareMessage: Failed to share", e);
            Toast.makeText(context, "Failed to share message", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Share multiple selected messages together
     */
    public void shareSelectedMessages() {
        Log.d(TAG, "shareSelectedMessages: Starting...");
        
        if (context == null) {
            Log.e(TAG, "shareSelectedMessages: Context is null, cannot share");
            return;
        }
        
        List<Message> selectedMessages = getSelectedMessages();
        Log.d(TAG, "shareSelectedMessages: Retrieved " + selectedMessages.size() + " selected messages");
        
        if (selectedMessages.isEmpty()) {
            Log.w(TAG, "shareSelectedMessages: No messages selected");
            Toast.makeText(context, "No messages selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "shareSelectedMessages: Building share intent for " + selectedMessages.size() + " messages");
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        
        // Build share text with all selected messages
        StringBuilder shareText = new StringBuilder();
        shareText.append("Conversation from MobiGPT\n");
        shareText.append("━━━━━━━━━━━━━━━━━━━━\n\n");
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault());
        
        for (int i = 0; i < selectedMessages.size(); i++) {
            Message msg = selectedMessages.get(i);
            
            Log.d(TAG, "shareSelectedMessages: Processing message " + (i + 1) + "/" + selectedMessages.size() + 
                  " (ID=" + msg.id + ", isUser=" + msg.isUser + ", length=" + msg.content.length() + ")");
            
            // Add message header
            if (msg.isUser) {
                shareText.append("👤 You");
            } else {
                shareText.append("🤖 AI");
            }
            shareText.append(" • ").append(dateFormat.format(new Date(msg.timestamp))).append("\n");
            
            // Add message content
            shareText.append(msg.content);
            
            // Add separator between messages (except for last one)
            if (i < selectedMessages.size() - 1) {
                shareText.append("\n\n─────────\n\n");
            }
        }
        
        // Add footer
        shareText.append("\n\n━━━━━━━━━━━━━━━━━━━━\n");
        shareText.append("Shared ").append(selectedMessages.size()).append(" message");
        if (selectedMessages.size() > 1) {
            shareText.append("s");
        }
        shareText.append(" from MobiGPT");
        
        Log.d(TAG, "shareSelectedMessages: Final share text length: " + shareText.length() + " characters");
        
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Conversation from MobiGPT");
        
        // Create chooser to show share options
        Intent chooserIntent = Intent.createChooser(shareIntent, "Share conversation via");
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        try {
            Log.d(TAG, "shareSelectedMessages: Starting share activity");
            context.startActivity(chooserIntent);
            
            if (messageActionListener != null) {
                Log.d(TAG, "shareSelectedMessages: Notifying listener about share");
                messageActionListener.onMultipleMessagesShared(selectedMessages);
            } else {
                Log.w(TAG, "shareSelectedMessages: messageActionListener is null, cannot notify");
            }
            
            // Exit selection mode after sharing
            Log.d(TAG, "shareSelectedMessages: Exiting selection mode");
            exitSelectionMode();
            
            Log.d(TAG, "shareSelectedMessages: Share completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "shareSelectedMessages: Failed to share", e);
            Toast.makeText(context, "Failed to share messages", Toast.LENGTH_SHORT).show();
        }
    }
    
    static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;
        TextView textTimestamp;
        ImageView starIndicator;
        View btnStarToggle;
        View btnQuickCopy;
        ImageView selectionIndicator;
        LinearLayout messageContainer;
        
        UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.text_message);
            if (textMessage != null) {
                textMessage.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            }
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            starIndicator = itemView.findViewById(R.id.star_indicator);
            btnStarToggle = itemView.findViewById(R.id.btn_star_toggle);
            btnQuickCopy = itemView.findViewById(R.id.btn_quick_copy);
            selectionIndicator = itemView.findViewById(R.id.selection_indicator);
            messageContainer = itemView.findViewById(R.id.message_container);
        }
        
        void bind(Message message) {
            bind(message, null, null, null, false, null);
        }
        
        void bind(Message message, MarkdownFormatter markdownFormatter) {
            bind(message, markdownFormatter, null, null, false, null);
        }
        
        void bind(Message message, MarkdownFormatter markdownFormatter, View.OnLongClickListener longClickListener) {
            bind(message, markdownFormatter, longClickListener, null, false, null);
        }
        
        void bind(Message message, MarkdownFormatter markdownFormatter, View.OnLongClickListener longClickListener, MessageAdapter adapter) {
            bind(message, markdownFormatter, longClickListener, null, false, adapter);
        }
        
        void bind(Message message, MarkdownFormatter markdownFormatter, View.OnLongClickListener longClickListener, 
                  View.OnClickListener clickListener, boolean isSelected, MessageAdapter adapter) {
            if (message != null) {
                // Animate message entrance
                if (adapter != null && messageContainer != null) {
                    adapter.animateMessageEntrance(messageContainer, message.id);
                }
                
                // Apply theme-aware background immediately when binding (unless selected)
                if (adapter != null && messageContainer != null && !isSelected) {
                    applyUserMessageThemeBackground(messageContainer, adapter);
                }
                
                // Apply typography to message text
                if (adapter != null && adapter.typographyManager != null && textMessage != null) {
                    adapter.typographyManager.applyCompleteTypography(
                        textMessage, 
                        TypographyManager.TextType.NORMAL, 
                        false
                    );
                }
                
                // Apply typography to timestamp
                if (adapter != null && adapter.typographyManager != null && textTimestamp != null) {
                    adapter.typographyManager.applyCompleteTypography(
                        textTimestamp, 
                        TypographyManager.TextType.TINY, 
                        false
                    );
                }
                
                // Apply markdown formatting and search highlighting to user messages if available
                if (markdownFormatter != null) {
                    CharSequence formattedText = markdownFormatter.format(message.content);
                    // Apply search highlighting if in search mode
                    if (adapter != null && adapter.isInSearchMode()) {
                        formattedText = adapter.highlightSearchQuery(formattedText.toString(), adapter.getSearchQuery());
                    }
                    textMessage.setText(formattedText);
                } else {
                    // Apply search highlighting if in search mode
                    CharSequence displayText = message.content;
                    if (adapter != null && adapter.isInSearchMode()) {
                        displayText = adapter.highlightSearchQuery(message.content, adapter.getSearchQuery());
                    }
                    textMessage.setText(displayText);
                }
                
                // Show/hide timestamp based on settings
                if (textTimestamp != null) {
                    textTimestamp.setTextColor(0xCCFFFFFF); // 80% white for clear readability on accent bubble
                    if (adapter != null && adapter.settingsManager != null && adapter.settingsManager.isTimestampMessages()) {
                        textTimestamp.setVisibility(View.VISIBLE);
                        textTimestamp.setText(formatTime(message.timestamp));
                    } else {
                        textTimestamp.setVisibility(View.GONE);
                    }
                }
                
                // Show selection indicator
                if (selectionIndicator != null) {
                    selectionIndicator.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                }
                
                // Show selection overlay with clear visual feedback
                if (messageContainer != null) {
                    if (isSelected) {
                        // Selected: Show checkmark and highlight
                        messageContainer.setAlpha(1f); // Keep full opacity
                        messageContainer.setScaleX(0.95f);
                        messageContainer.setScaleY(0.95f);
                        // Add a subtle highlight effect by changing background tint
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            messageContainer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                                android.graphics.Color.parseColor("#4CAF50"))); // Green tint
                        }
                    } else {
                        // Not selected: Reset to normal and apply theme colors
                        messageContainer.setAlpha(1f);
                        messageContainer.setScaleX(1f);
                        messageContainer.setScaleY(1f);
                        // Apply theme-aware background for user messages
                        applyUserMessageThemeBackground(messageContainer, adapter);
                    }
                }
                
                // Star indicator - show when starred
                if (starIndicator != null) {
                    starIndicator.setVisibility(message.isStarred ? View.VISIBLE : View.GONE);
                    androidx.core.widget.ImageViewCompat.setImageTintList(starIndicator,
                        android.content.res.ColorStateList.valueOf(0xFFFFD54F)); // Gold star for high visibility
                }
                
                // Star toggle button
                if (btnStarToggle != null && adapter != null) {
                    if (btnStarToggle instanceof ImageView) {
                        ImageView starIv = (ImageView) btnStarToggle;
                        starIv.setImageResource(message.isStarred ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
                        androidx.core.widget.ImageViewCompat.setImageTintList(starIv,
                            android.content.res.ColorStateList.valueOf(message.isStarred ? 0xFFFFD54F : 0xD9FFFFFF));
                    }
                    btnStarToggle.setOnClickListener(v -> {
                        adapter.performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
                        adapter.toggleMessageStarred(message, getAdapterPosition());
                    });
                }
                
                // Quick Copy button
                if (btnQuickCopy != null && adapter != null) {
                    if (btnQuickCopy instanceof ImageView) {
                        androidx.core.widget.ImageViewCompat.setImageTintList((ImageView) btnQuickCopy,
                            android.content.res.ColorStateList.valueOf(0xD9FFFFFF));
                    }
                    btnQuickCopy.setOnClickListener(v -> {
                        adapter.performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
                        adapter.copyMessageToClipboard(message);
                    });
                }
                
                // Set click listener for selection mode with improved touch handling
                if (clickListener != null && messageContainer != null) {
                    messageContainer.setOnClickListener(clickListener);
                    messageContainer.setClickable(true);
                    messageContainer.setFocusable(true);
                    // Ensure the view is not blocking touch events
                    messageContainer.setEnabled(true);
                } else if (messageContainer != null) {
                    messageContainer.setOnClickListener(null);
                    messageContainer.setClickable(false);
                }
                
                // Set long click listener for context menu with improved touch handling
                if (longClickListener != null && messageContainer != null) {
                    messageContainer.setOnLongClickListener(longClickListener);
                    messageContainer.setLongClickable(true);
                } else if (messageContainer != null) {
                    messageContainer.setOnLongClickListener(null);
                    messageContainer.setLongClickable(false);
                }
            }
        }
        
        void clear() {
            textMessage.setText(null);
            textTimestamp.setText(null);
            if (starIndicator != null) {
                starIndicator.setVisibility(View.GONE);
            }
            if (btnStarToggle != null) {
                btnStarToggle.setOnClickListener(null);
            }
            if (selectionIndicator != null) {
                selectionIndicator.setVisibility(View.GONE);
            }
            if (messageContainer != null) {
                messageContainer.setOnLongClickListener(null);
                messageContainer.setOnClickListener(null);
                // Reset animation properties to default state
                messageContainer.setAlpha(1f);
                messageContainer.setTranslationY(0f);
                messageContainer.setScaleX(1f);
                messageContainer.setScaleY(1f);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    messageContainer.setBackgroundTintList(null);
                }
            }
        }
        
        private String formatTime(long timestamp) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));
        }
        
        /**
         * Apply theme-aware background to user message bubble
         * Uses the current accent color selected by the user
         */
        private void applyUserMessageThemeBackground(View messageContainer, MessageAdapter adapter) {
            if (messageContainer == null || adapter == null || adapter.context == null) {
                return;
            }
            
            try {
                // Get the settings manager to read accent color
                com.keralatechreach.mobigpt.utils.SettingsManager settingsManager = 
                    new com.keralatechreach.mobigpt.utils.SettingsManager(adapter.context);
                String accentColor = settingsManager.getAccentColor();
                
                // Get accent colors based on user selection
                int primaryAccentColor;
                int secondaryAccentColor;
                
                switch (accentColor) {
                    case "blue":
                        primaryAccentColor = adapter.context.getColor(com.keralatechreach.mobigpt.R.color.accent_blue);
                        secondaryAccentColor = adapter.context.getColor(com.keralatechreach.mobigpt.R.color.accent_blue_dark);
                        break;
                    case "purple":
                        primaryAccentColor = adapter.context.getColor(com.keralatechreach.mobigpt.R.color.accent_purple);
                        secondaryAccentColor = adapter.context.getColor(com.keralatechreach.mobigpt.R.color.accent_purple_dark);
                        break;
                    case "orange":
                        primaryAccentColor = adapter.context.getColor(com.keralatechreach.mobigpt.R.color.accent_orange_primary);
                        secondaryAccentColor = adapter.context.getColor(com.keralatechreach.mobigpt.R.color.accent_orange_dark);
                        break;
                    case "pink":
                        primaryAccentColor = adapter.context.getColor(com.keralatechreach.mobigpt.R.color.accent_pink_primary);
                        secondaryAccentColor = adapter.context.getColor(com.keralatechreach.mobigpt.R.color.accent_pink_dark);
                        break;
                    case "green":
                    default:
                        primaryAccentColor = adapter.context.getColor(com.keralatechreach.mobigpt.R.color.accent_green);
                        secondaryAccentColor = adapter.context.getColor(com.keralatechreach.mobigpt.R.color.accent_green_dark);
                        break;
                }
                
                // Create a gradient drawable with the current accent colors
                android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable();
                gradient.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                gradient.setGradientType(android.graphics.drawable.GradientDrawable.LINEAR_GRADIENT);
                gradient.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR); // Top-left to bottom-right (135 degree)
                gradient.setColors(new int[]{primaryAccentColor, secondaryAccentColor});
                
                // Set corner radius to match the original design
                float density = adapter.context.getResources().getDisplayMetrics().density;
                gradient.setCornerRadii(new float[]{
                    18 * density, 18 * density,  // top-left
                    18 * density, 18 * density,  // top-right
                    18 * density, 18 * density,  // bottom-right
                    4 * density, 4 * density     // bottom-left (like WhatsApp style)
                });
                
                // Create the layer list to include shadow effect
                android.graphics.drawable.LayerDrawable layerDrawable = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                    createShadowDrawable(density),
                    gradient
                });
                
                // Apply shadow positioning
                layerDrawable.setLayerInset(0, 0, 0, (int)(1 * density), (int)(1 * density)); // shadow
                layerDrawable.setLayerInset(1, (int)(1 * density), (int)(1 * density), 0, 0); // main bubble
                
                // Apply the themed background
                messageContainer.setBackground(layerDrawable);
                
                // Clear any background tint to ensure the gradient shows properly
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    messageContainer.setBackgroundTintList(null);
                }
                
                android.util.Log.d("MessageAdapter", "Applied user message theme background with accent color: " + accentColor);
                
            } catch (Exception e) {
                android.util.Log.e("MessageAdapter", "Error applying user message theme background", e);
                // Fallback to default background if theme application fails
                messageContainer.setBackgroundResource(com.keralatechreach.mobigpt.R.drawable.user_message_background);
            }
        }
        
        /**
         * Create shadow drawable for the message bubble
         */
        private android.graphics.drawable.Drawable createShadowDrawable(float density) {
            android.graphics.drawable.GradientDrawable shadow = new android.graphics.drawable.GradientDrawable();
            shadow.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            shadow.setColor(0x15000000); // Semi-transparent shadow
            shadow.setCornerRadius(18 * density);
            return shadow;
        }
    }
    
    static class AIMessageViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;
        TextView textTimestamp;
        TextView textTokenStats;
        ImageView starIndicator;
        View btnStarToggle;
        View btnQuickCopy;
        View btnQuickRegenerate;
        View btnQuickShare;
        ImageView selectionIndicator;
        LinearLayout messageContainer;
        LinearLayout typingIndicator;
        View typingDot1, typingDot2, typingDot3;
        private ObjectAnimator dot1Animator, dot2Animator, dot3Animator;
        private boolean isCurrentlyStreaming = false;

        // Thinking Accordion & Rich Code Block views
        LinearLayout containerThinkingProcess;
        View layoutThinkingHeader;
        ImageView iconThinkingBrain;
        TextView textThinkingTitle;
        ImageView iconThinkingChevron;
        LinearLayout layoutThinkingBody;
        TextView textThinkingContent;
        LinearLayout containerCodeBlocks;
        
        AIMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.text_message);
            if (textMessage != null) {
                textMessage.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            }
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            textTokenStats = itemView.findViewById(R.id.text_token_stats);
            starIndicator = itemView.findViewById(R.id.star_indicator);
            btnStarToggle = itemView.findViewById(R.id.btn_star_toggle);
            btnQuickCopy = itemView.findViewById(R.id.btn_quick_copy);
            btnQuickRegenerate = itemView.findViewById(R.id.btn_quick_regenerate);
            btnQuickShare = itemView.findViewById(R.id.btn_quick_share);
            selectionIndicator = itemView.findViewById(R.id.selection_indicator);
            messageContainer = itemView.findViewById(R.id.message_container);
            typingIndicator = itemView.findViewById(R.id.typing_indicator);
            typingDot1 = itemView.findViewById(R.id.typing_dot_1);
            typingDot2 = itemView.findViewById(R.id.typing_dot_2);
            typingDot3 = itemView.findViewById(R.id.typing_dot_3);

            // Bind new Thinking Process & Code Block views
            containerThinkingProcess = itemView.findViewById(R.id.container_thinking_process);
            layoutThinkingHeader = itemView.findViewById(R.id.layout_thinking_header);
            iconThinkingBrain = itemView.findViewById(R.id.icon_thinking_brain);
            textThinkingTitle = itemView.findViewById(R.id.text_thinking_title);
            iconThinkingChevron = itemView.findViewById(R.id.icon_thinking_chevron);
            layoutThinkingBody = itemView.findViewById(R.id.layout_thinking_body);
            textThinkingContent = itemView.findViewById(R.id.text_thinking_content);
            containerCodeBlocks = itemView.findViewById(R.id.container_code_blocks);
            
            setupTypingIndicator();
        }
        
        void bind(Message message) {
            bind(message, false, null, null, null, false, null);
        }
        
        void bind(Message message, boolean isStreaming) {
            bind(message, isStreaming, null, null, null, false, null);
        }
        
        void bind(Message message, boolean isStreaming, MarkdownFormatter markdownFormatter) {
            bind(message, isStreaming, markdownFormatter, null, null, false, null);
        }
        
        void bind(Message message, boolean isStreaming, MarkdownFormatter markdownFormatter, View.OnLongClickListener longClickListener) {
            bind(message, isStreaming, markdownFormatter, longClickListener, null, false, null);
        }
        
        void bind(Message message, boolean isStreaming, MarkdownFormatter markdownFormatter, View.OnLongClickListener longClickListener, MessageAdapter adapter) {
            bind(message, isStreaming, markdownFormatter, longClickListener, null, false, adapter);
        }
        
        void bind(Message message, boolean isStreaming, MarkdownFormatter markdownFormatter, View.OnLongClickListener longClickListener,
                  View.OnClickListener clickListener, boolean isSelected, MessageAdapter adapter) {
            if (message != null) {
                // Animate message entrance
                if (adapter != null && messageContainer != null) {
                    adapter.animateMessageEntrance(messageContainer, message.id);
                }
                
                // Apply typography to message text
                if (adapter != null && adapter.typographyManager != null && textMessage != null) {
                    adapter.typographyManager.applyCompleteTypography(
                        textMessage, 
                        TypographyManager.TextType.NORMAL, 
                        false
                    );
                }
                
                // Apply typography to timestamp
                if (adapter != null && adapter.typographyManager != null && textTimestamp != null) {
                    adapter.typographyManager.applyCompleteTypography(
                        textTimestamp, 
                        TypographyManager.TextType.TINY, 
                        false
                    );
                }
                
                String content = message.content;
                isCurrentlyStreaming = isStreaming;

                MarkdownFormatter.ParsedMessage parsed = markdownFormatter != null
                    ? markdownFormatter.parseMessage(content, isStreaming)
                    : new MarkdownFormatter.ParsedMessage(null, false, false, content, null);

                // Bind Thinking Process Accordion (e.g. DeepSeek-R1 <think>)
                if (containerThinkingProcess != null) {
                    if (parsed.isThinking && parsed.thinkingText != null && !parsed.thinkingText.isEmpty()) {
                        containerThinkingProcess.setVisibility(View.VISIBLE);
                        if (textThinkingTitle != null) {
                            textThinkingTitle.setText(parsed.isThinkingComplete ? "Thought Process (Complete)" : "Thinking Process...");
                        }
                        if (textThinkingContent != null) {
                            textThinkingContent.setText(parsed.thinkingText);
                        }
                        // During active thinking, keep expanded; once complete, default to collapsed
                        if (isStreaming && !parsed.isThinkingComplete) {
                            if (layoutThinkingBody != null) layoutThinkingBody.setVisibility(View.VISIBLE);
                            if (iconThinkingChevron != null) iconThinkingChevron.setRotation(180f);
                        } else if (!isStreaming && layoutThinkingBody != null && layoutThinkingBody.getVisibility() != View.VISIBLE) {
                            layoutThinkingBody.setVisibility(View.GONE);
                            if (iconThinkingChevron != null) iconThinkingChevron.setRotation(0f);
                        }

                        if (layoutThinkingHeader != null) {
                            layoutThinkingHeader.setOnClickListener(v -> {
                                if (layoutThinkingBody != null) {
                                    boolean isExpanded = layoutThinkingBody.getVisibility() == View.VISIBLE;
                                    layoutThinkingBody.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                                    if (iconThinkingChevron != null) {
                                        iconThinkingChevron.animate().rotation(isExpanded ? 0f : 180f).setDuration(200).start();
                                    }
                                }
                            });
                        }
                    } else {
                        containerThinkingProcess.setVisibility(View.GONE);
                    }
                }

                String displayContent = parsed.mainText;

                // Handle streaming / content display
                if (isStreaming) {
                    if (parsed.isThinking && !parsed.isThinkingComplete) {
                        hideTypingIndicator();
                        textMessage.setVisibility(View.GONE);
                    } else if (displayContent == null || displayContent.trim().isEmpty()) {
                        showTypingIndicator();
                        textMessage.setVisibility(View.GONE);
                    } else {
                        hideTypingIndicator();
                        textMessage.setVisibility(View.VISIBLE);
                        if (markdownFormatter != null) {
                            textMessage.setText(markdownFormatter.formatStreaming(displayContent + "▋", true));
                        } else {
                            textMessage.setText(displayContent + "▋");
                        }
                    }
                    if (containerCodeBlocks != null) {
                        containerCodeBlocks.setVisibility(View.GONE);
                        containerCodeBlocks.removeAllViews();
                    }
                } else {
                    hideTypingIndicator();

                    // Render rich code blocks if detected and message is completed
                    if (containerCodeBlocks != null && parsed.chunks != null && parsed.chunks.size() > 1) {
                        textMessage.setVisibility(View.GONE);
                        containerCodeBlocks.setVisibility(View.VISIBLE);
                        containerCodeBlocks.removeAllViews();
                        LayoutInflater inflater = LayoutInflater.from(itemView.getContext());

                        for (MarkdownFormatter.MessageChunk chunk : parsed.chunks) {
                            if (chunk.isCode) {
                                View codeView = inflater.inflate(R.layout.layout_code_block, containerCodeBlocks, false);
                                TextView textLang = codeView.findViewById(R.id.text_code_language);
                                TextView textCode = codeView.findViewById(R.id.text_code_content);
                                View btnCopy = codeView.findViewById(R.id.btn_copy_code_block);
                                TextView textCopy = codeView.findViewById(R.id.text_copy_code_block);
                                ImageView iconCopy = codeView.findViewById(R.id.icon_copy_code_block);

                                if (textLang != null) textLang.setText(chunk.language);
                                if (textCode != null) textCode.setText(chunk.text);
                                if (btnCopy != null) {
                                    btnCopy.setOnClickListener(v -> {
                                        ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                        ClipData clip = ClipData.newPlainText("Code", chunk.text);
                                        if (clipboard != null) {
                                            clipboard.setPrimaryClip(clip);
                                            if (textCopy != null) textCopy.setText("Copied!");
                                            if (iconCopy != null) iconCopy.setImageResource(R.drawable.ic_check);
                                            v.postDelayed(() -> {
                                                if (textCopy != null) textCopy.setText("Copy");
                                                if (iconCopy != null) iconCopy.setImageResource(R.drawable.ic_copy);
                                            }, 2000);
                                        }
                                    });
                                }
                                containerCodeBlocks.addView(codeView);
                            } else {
                                TextView tv = new TextView(itemView.getContext());
                                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
                                tv.setLineSpacing(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 4, itemView.getResources().getDisplayMetrics()), 1.0f);
                                tv.setTextColor(textMessage.getTextColors());
                                tv.setPadding(0, 4, 0, 4);
                                if (markdownFormatter != null) {
                                    tv.setText(markdownFormatter.format(chunk.text));
                                } else {
                                    tv.setText(chunk.text);
                                }
                                tv.setTextIsSelectable(true);
                                containerCodeBlocks.addView(tv);
                            }
                        }
                    } else {
                        if (containerCodeBlocks != null) {
                            containerCodeBlocks.setVisibility(View.GONE);
                            containerCodeBlocks.removeAllViews();
                        }
                        textMessage.setVisibility(View.VISIBLE);
                        if (markdownFormatter != null) {
                            CharSequence formattedText = markdownFormatter.format(displayContent);
                            if (adapter != null && adapter.isInSearchMode()) {
                                formattedText = adapter.highlightSearchQuery(formattedText.toString(), adapter.getSearchQuery());
                            }
                            textMessage.setText(formattedText);
                        } else {
                            CharSequence displayText = displayContent;
                            if (adapter != null && adapter.isInSearchMode()) {
                                displayText = adapter.highlightSearchQuery(displayContent, adapter.getSearchQuery());
                            }
                            textMessage.setText(displayText);
                        }
                    }
                }
                
                // Show/hide timestamp based on settings
                if (textTimestamp != null) {
                    if (adapter != null && adapter.settingsManager != null && adapter.settingsManager.isTimestampMessages()) {
                        textTimestamp.setVisibility(View.VISIBLE);
                        textTimestamp.setText(formatTime(message.timestamp));
                    } else {
                        textTimestamp.setVisibility(View.GONE);
                    }
                }
                
                // Show/hide token statistics for completed AI responses (not streaming)
                if (textTokenStats != null && !isStreaming) {
                    String tokenStats = message.getTokenStats();
                    if (tokenStats != null && !tokenStats.isEmpty()) {
                        textTokenStats.setVisibility(View.VISIBLE);
                        textTokenStats.setText(tokenStats);
                        // Apply typography to token stats
                        if (adapter != null && adapter.typographyManager != null) {
                            adapter.typographyManager.applyCompleteTypography(
                                textTokenStats, 
                                TypographyManager.TextType.TINY, 
                                false
                            );
                        }
                    } else {
                        textTokenStats.setVisibility(View.GONE);
                    }
                } else if (textTokenStats != null) {
                    // Hide token stats for streaming messages
                    textTokenStats.setVisibility(View.GONE);
                }
                
                // Show selection indicator
                if (selectionIndicator != null && !isStreaming) {
                    selectionIndicator.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                } else if (selectionIndicator != null) {
                    selectionIndicator.setVisibility(View.GONE);
                }
                
                // Show selection overlay with clear visual feedback
                if (messageContainer != null) {
                    if (isSelected) {
                        // Selected: Show checkmark and highlight
                        messageContainer.setAlpha(1f); // Keep full opacity
                        messageContainer.setScaleX(0.95f);
                        messageContainer.setScaleY(0.95f);
                        // Add a subtle highlight effect by changing background tint
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            messageContainer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                                android.graphics.Color.parseColor("#4CAF50"))); // Green tint
                        }
                    } else {
                        // Not selected: Reset to normal
                        messageContainer.setAlpha(1f);
                        messageContainer.setScaleX(1f);
                        messageContainer.setScaleY(1f);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            messageContainer.setBackgroundTintList(null); // Remove tint
                        }
                    }
                }
                
                // Star indicator - show when starred (not for streaming)
                if (starIndicator != null) {
                    starIndicator.setVisibility(!isStreaming && message.isStarred ? View.VISIBLE : View.GONE);
                }
                
                // Star toggle button (not for streaming)
                if (btnStarToggle != null && !isStreaming && adapter != null) {
                    btnStarToggle.setVisibility(View.VISIBLE);
                    if (btnStarToggle instanceof ImageView) {
                        ((ImageView) btnStarToggle).setImageResource(message.isStarred ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
                    }
                    btnStarToggle.setOnClickListener(v -> {
                        adapter.performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
                        adapter.toggleMessageStarred(message, getAdapterPosition());
                    });
                } else if (btnStarToggle != null) {
                    btnStarToggle.setVisibility(View.GONE);
                }

                // Quick Copy button (not for streaming)
                if (btnQuickCopy != null && !isStreaming && adapter != null) {
                    btnQuickCopy.setVisibility(View.VISIBLE);
                    btnQuickCopy.setOnClickListener(v -> {
                        adapter.performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
                        adapter.copyMessageToClipboard(message);
                    });
                } else if (btnQuickCopy != null) {
                    btnQuickCopy.setVisibility(View.GONE);
                }

                // Quick Regenerate button (not for streaming)
                if (btnQuickRegenerate != null && !isStreaming && adapter != null) {
                    btnQuickRegenerate.setVisibility(View.VISIBLE);
                    btnQuickRegenerate.setOnClickListener(v -> {
                        adapter.performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
                        adapter.regenerateMessage(message);
                    });
                } else if (btnQuickRegenerate != null) {
                    btnQuickRegenerate.setVisibility(View.GONE);
                }

                // Quick Share button (not for streaming)
                if (btnQuickShare != null && !isStreaming && adapter != null) {
                    btnQuickShare.setVisibility(View.VISIBLE);
                    btnQuickShare.setOnClickListener(v -> {
                        adapter.performHapticFeedback(v, HapticFeedbackConstants.VIRTUAL_KEY);
                        adapter.shareMessage(message);
                    });
                } else if (btnQuickShare != null) {
                    btnQuickShare.setVisibility(View.GONE);
                }
                
                // Set click listener for selection mode with improved touch handling
                if (clickListener != null && messageContainer != null && !isStreaming) {
                    messageContainer.setOnClickListener(clickListener);
                    messageContainer.setClickable(true);
                    messageContainer.setFocusable(true);
                    messageContainer.setEnabled(true);
                } else if (messageContainer != null) {
                    messageContainer.setOnClickListener(null);
                    messageContainer.setClickable(false);
                }
                
                // Set long click listener for context menu with improved touch handling (only for non-streaming messages)
                if (longClickListener != null && messageContainer != null && !isStreaming) {
                    messageContainer.setOnLongClickListener(longClickListener);
                    messageContainer.setLongClickable(true);
                } else if (messageContainer != null && !isStreaming) {
                    messageContainer.setOnLongClickListener(null);
                    messageContainer.setLongClickable(false);
                }
            }
        }
        
        /**
         * Setup typing indicator animations
         */
        private void setupTypingIndicator() {
            if (typingDot1 == null || typingDot2 == null || typingDot3 == null) return;
            
            // Create pulsing animation for each dot with staggered delays
            dot1Animator = ObjectAnimator.ofFloat(typingDot1, "alpha", 0.3f, 1.0f);
            dot1Animator.setDuration(600);
            dot1Animator.setRepeatCount(ValueAnimator.INFINITE);
            dot1Animator.setRepeatMode(ValueAnimator.REVERSE);
            dot1Animator.setStartDelay(0);
            
            dot2Animator = ObjectAnimator.ofFloat(typingDot2, "alpha", 0.3f, 1.0f);
            dot2Animator.setDuration(600);
            dot2Animator.setRepeatCount(ValueAnimator.INFINITE);
            dot2Animator.setRepeatMode(ValueAnimator.REVERSE);
            dot2Animator.setStartDelay(200);
            
            dot3Animator = ObjectAnimator.ofFloat(typingDot3, "alpha", 0.3f, 1.0f);
            dot3Animator.setDuration(600);
            dot3Animator.setRepeatCount(ValueAnimator.INFINITE);
            dot3Animator.setRepeatMode(ValueAnimator.REVERSE);
            dot3Animator.setStartDelay(400);
        }
        
        /**
         * Show and start animating the typing indicator
         */
        private void showTypingIndicator() {
            if (typingIndicator == null) return;
            
            typingIndicator.setVisibility(View.VISIBLE);
            
            // Start animations
            if (dot1Animator != null && !dot1Animator.isRunning()) {
                dot1Animator.start();
            }
            if (dot2Animator != null && !dot2Animator.isRunning()) {
                dot2Animator.start();
            }
            if (dot3Animator != null && !dot3Animator.isRunning()) {
                dot3Animator.start();
            }
        }
        
        /**
         * Hide and stop animating the typing indicator
         */
        private void hideTypingIndicator() {
            if (typingIndicator == null) return;
            
            typingIndicator.setVisibility(View.GONE);
            
            // Stop animations
            if (dot1Animator != null && dot1Animator.isRunning()) {
                dot1Animator.cancel();
            }
            if (dot2Animator != null && dot2Animator.isRunning()) {
                dot2Animator.cancel();
            }
            if (dot3Animator != null && dot3Animator.isRunning()) {
                dot3Animator.cancel();
            }
        }
        
        void clear() {
            hideTypingIndicator();
            isCurrentlyStreaming = false;
            textMessage.setText(null);
            textTimestamp.setText(null);
            if (starIndicator != null) {
                starIndicator.setVisibility(View.GONE);
            }
            if (btnStarToggle != null) {
                btnStarToggle.setOnClickListener(null);
            }
            if (selectionIndicator != null) {
                selectionIndicator.setVisibility(View.GONE);
            }
            if (messageContainer != null) {
                messageContainer.setOnLongClickListener(null);
                messageContainer.setOnClickListener(null);
                // Reset animation properties to default state
                messageContainer.setAlpha(1f);
                messageContainer.setTranslationY(0f);
                messageContainer.setScaleX(1f);
                messageContainer.setScaleY(1f);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    messageContainer.setBackgroundTintList(null);
                }
            }
        }
        
        private String formatTime(long timestamp) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));
        }
    }
}