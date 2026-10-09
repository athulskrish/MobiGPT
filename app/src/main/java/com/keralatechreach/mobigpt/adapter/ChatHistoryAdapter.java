package com.keralatechreach.mobigpt.adapter;

import android.content.Context;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import com.keralatechreach.mobigpt.R;
import com.keralatechreach.mobigpt.database.Chat;
import com.keralatechreach.mobigpt.utils.TypographyManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements com.keralatechreach.mobigpt.utils.ThemeColorUpdater.ThemeAware {
    
    private static final int MAX_RECYCLED_VIEWS = 15;
    private static final String TAG = "ChatHistoryAdapter";
    
    public static class HistoryListItem {
        public static final int TYPE_HEADER = 0;
        public static final int TYPE_CHAT = 1;
        
        public final int type;
        public final String headerTitle;
        public final Chat chat;
        
        public HistoryListItem(String headerTitle) {
            this.type = TYPE_HEADER;
            this.headerTitle = headerTitle;
            this.chat = null;
        }
        
        public HistoryListItem(Chat chat) {
            this.type = TYPE_CHAT;
            this.headerTitle = null;
            this.chat = chat;
        }
    }
    
    private final Context context;
    private final TypographyManager typographyManager;
    private final List<Chat> allChats = new ArrayList<>();
    private final List<HistoryListItem> displayedItems = new ArrayList<>();
    private OnChatClickListener onChatClickListener;
    private FilterResultListener filterResultListener;
    private String currentFilterQuery = "";
    private long activeChatId = -1;
    
    public interface FilterResultListener {
        void onFilterResult(int totalCount, int filteredCount, boolean isQueryActive);
    }
    
    public ChatHistoryAdapter(Context context) {
        this.context = context;
        this.typographyManager = context != null ? new TypographyManager(context) : null;
    }
    
    public void setFilterResultListener(FilterResultListener listener) {
        this.filterResultListener = listener;
    }
    
    public void setActiveChatId(long activeChatId) {
        if (this.activeChatId != activeChatId) {
            this.activeChatId = activeChatId;
            notifyDataSetChanged();
        }
    }
    
    public long getActiveChatId() {
        return activeChatId;
    }
    
    public int getTotalChatCount() {
        return allChats.size();
    }
    
    public interface OnChatClickListener {
        void onChatClick(Chat chat);
        void onDeleteChatClick(Chat chat);
        default void onRenameChatClick(Chat chat) {}
        default void onChatLongClick(Chat chat, View view) {}
    }
    
    // Memory leak prevention methods
    public void cleanup() {
        allChats.clear();
        displayedItems.clear();
        onChatClickListener = null;
        filterResultListener = null;
        notifyDataSetChanged();
    }
    
    public void addChat(Chat chat) {
        allChats.add(0, chat); // Add to beginning for newest first
        applyFilter();
    }
    
    public void removeChat(Chat chat) {
        int index = -1;
        for (int i = 0; i < allChats.size(); i++) {
            if (allChats.get(i).id == chat.id) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            allChats.remove(index);
            applyFilter();
        }
    }
    
    public void updateChatTitle(long chatId, String newTitle) {
        for (Chat chat : allChats) {
            if (chat.id == chatId) {
                chat.title = newTitle;
                break;
            }
        }
        applyFilter();
    }
    
    public void filter(String query) {
        this.currentFilterQuery = (query != null) ? query.trim().toLowerCase(Locale.getDefault()) : "";
        applyFilter();
    }
    
    private void applyFilter() {
        List<Chat> filteredChats = new ArrayList<>();
        if (currentFilterQuery.isEmpty()) {
            filteredChats.addAll(allChats);
        } else {
            for (Chat chat : allChats) {
                if (chat != null && chat.title != null && chat.title.toLowerCase(Locale.getDefault()).contains(currentFilterQuery)) {
                    filteredChats.add(chat);
                }
            }
        }
        
        List<HistoryListItem> groupedItems = buildGroupedItems(filteredChats);
        displayedItems.clear();
        displayedItems.addAll(groupedItems);
        notifyDataSetChanged();
        
        if (filterResultListener != null) {
            filterResultListener.onFilterResult(allChats.size(), filteredChats.size(), !currentFilterQuery.isEmpty());
        }
    }
    
    /**
     * Groups chats chronologically into Today, Yesterday, Previous 7 Days, Previous 30 Days, Older.
     */
    private List<HistoryListItem> buildGroupedItems(List<Chat> sourceChats) {
        List<HistoryListItem> items = new ArrayList<>();
        if (sourceChats == null || sourceChats.isEmpty()) {
            return items;
        }
        
        // Compute calendar boundaries
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayStart = cal.getTimeInMillis();
        
        cal.add(Calendar.DAY_OF_YEAR, -1);
        long yesterdayStart = cal.getTimeInMillis();
        
        cal.add(Calendar.DAY_OF_YEAR, -6);
        long sevenDaysStart = cal.getTimeInMillis();
        
        cal.add(Calendar.DAY_OF_YEAR, -23);
        long thirtyDaysStart = cal.getTimeInMillis();
        
        String currentHeader = null;
        
        for (Chat chat : sourceChats) {
            if (chat == null) continue;
            long time = chat.lastMessageTime > 0 ? chat.lastMessageTime : chat.createdAt;
            String header;
            if (time >= todayStart) {
                header = "Today";
            } else if (time >= yesterdayStart) {
                header = "Yesterday";
            } else if (time >= sevenDaysStart) {
                header = "Previous 7 Days";
            } else if (time >= thirtyDaysStart) {
                header = "Previous 30 Days";
            } else {
                header = "Older";
            }
            
            if (!header.equals(currentHeader)) {
                currentHeader = header;
                items.add(new HistoryListItem(header));
            }
            items.add(new HistoryListItem(chat));
        }
        
        return items;
    }
    
    /**
     * Force refresh all chat history items to update accent colors
     */
    public void refreshAccentColors() {
        notifyDataSetChanged();
    }
    
    @Override
    public void onThemeChanged() {
        refreshAccentColors();
    }
    
    public void setChats(List<Chat> chats) {
        allChats.clear();
        if (chats != null) {
            allChats.addAll(chats);
        }
        applyFilter();
    }
    
    public void setOnChatClickListener(OnChatClickListener listener) {
        this.onChatClickListener = listener;
    }
    
    @Override
    public int getItemViewType(int position) {
        if (position >= 0 && position < displayedItems.size()) {
            return displayedItems.get(position).type;
        }
        return HistoryListItem.TYPE_CHAT;
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (parent instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) parent;
            recyclerView.getRecycledViewPool().setMaxRecycledViews(viewType, MAX_RECYCLED_VIEWS);
        }
        
        if (viewType == HistoryListItem.TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_section_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_history, parent, false);
            return new ChatHistoryViewHolder(view);
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position < 0 || position >= displayedItems.size()) return;
        
        HistoryListItem item = displayedItems.get(position);
        if (holder instanceof HeaderViewHolder && item.type == HistoryListItem.TYPE_HEADER) {
            ((HeaderViewHolder) holder).bind(item.headerTitle);
        } else if (holder instanceof ChatHistoryViewHolder && item.type == HistoryListItem.TYPE_CHAT) {
            Chat chat = item.chat;
            boolean isActive = (chat != null && chat.id == activeChatId);
            ((ChatHistoryViewHolder) holder).bind(chat, onChatClickListener, typographyManager, isActive);
        }
    }
    
    @Override
    public int getItemCount() {
        return displayedItems.size();
    }
    
    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof ChatHistoryViewHolder) {
            ChatHistoryViewHolder chatHolder = (ChatHistoryViewHolder) holder;
            chatHolder.layoutChatItemContainer.setOnClickListener(null);
            chatHolder.layoutChatItemContainer.setOnLongClickListener(null);
            if (chatHolder.btnChatMore != null) {
                chatHolder.btnChatMore.setOnClickListener(null);
            }
            chatHolder.textChatTitle.setText(null);
            chatHolder.textLastMessageTime.setText(null);
        }
    }
    
    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        public final TextView textSectionHeader;
        
        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            textSectionHeader = itemView.findViewById(R.id.text_section_header);
        }
        
        public void bind(String title) {
            if (textSectionHeader != null) {
                textSectionHeader.setText(title != null ? title.toUpperCase(Locale.getDefault()) : "");
            }
        }
    }
    
    public static class ChatHistoryViewHolder extends RecyclerView.ViewHolder {
        public TextView textChatTitle;
        public TextView textLastMessageTime;
        public LinearLayout layoutChatInfo;
        public LinearLayout layoutChatItemContainer;
        public ImageButton btnChatMore;
        public android.widget.FrameLayout chatIconFrame;
        
        public ChatHistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            textChatTitle = itemView.findViewById(R.id.text_chat_title);
            textLastMessageTime = itemView.findViewById(R.id.text_last_message_time);
            layoutChatInfo = itemView.findViewById(R.id.layout_chat_info);
            layoutChatItemContainer = (LinearLayout) itemView;
            btnChatMore = itemView.findViewById(R.id.btn_chat_more);
            chatIconFrame = itemView.findViewById(R.id.chat_icon_frame);
            
            applyChatIconGradient();
        }
        
        private void applyChatIconGradient() {
            if (chatIconFrame == null) return;
            
            try {
                com.keralatechreach.mobigpt.utils.SettingsManager settingsManager = 
                    new com.keralatechreach.mobigpt.utils.SettingsManager(itemView.getContext());
                
                String accentColor = settingsManager.getAccentColor();
                int primaryColor;
                int darkColor;
                
                switch (accentColor) {
                    case "blue":
                        primaryColor = itemView.getContext().getColor(R.color.accent_blue);
                        darkColor = itemView.getContext().getColor(R.color.accent_blue_dark);
                        break;
                    case "purple":
                        primaryColor = itemView.getContext().getColor(R.color.accent_purple);
                        darkColor = itemView.getContext().getColor(R.color.accent_purple_dark);
                        break;
                    case "orange":
                        primaryColor = itemView.getContext().getColor(R.color.accent_orange_primary);
                        darkColor = itemView.getContext().getColor(R.color.accent_orange_dark);
                        break;
                    case "pink":
                        primaryColor = itemView.getContext().getColor(R.color.accent_pink_primary);
                        darkColor = itemView.getContext().getColor(R.color.accent_pink_dark);
                        break;
                    case "green":
                    default:
                        primaryColor = itemView.getContext().getColor(R.color.accent_green);
                        darkColor = itemView.getContext().getColor(R.color.accent_green_dark);
                        break;
                }
                
                android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    new int[]{primaryColor, darkColor}
                );
                gradient.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                chatIconFrame.setBackground(gradient);
                
            } catch (Exception e) {
                Log.e(TAG, "Error applying chat icon gradient", e);
                android.util.TypedValue typedValue = new android.util.TypedValue();
                android.content.res.Resources.Theme theme = itemView.getContext().getTheme();
                theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
                int colorPrimary = typedValue.data;
                theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryVariant, typedValue, true);
                int colorPrimaryVariant = typedValue.data;
                
                android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    new int[]{colorPrimary, colorPrimaryVariant}
                );
                gradient.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                chatIconFrame.setBackground(gradient);
            }
        }
        
        public void bind(Chat chat, OnChatClickListener listener, TypographyManager typographyManager, boolean isActive) {
            layoutChatItemContainer.setOnClickListener(null);
            layoutChatItemContainer.setOnLongClickListener(null);
            if (btnChatMore != null) {
                btnChatMore.setOnClickListener(null);
            }
            
            applyChatIconGradient();
            
            if (isActive) {
                layoutChatItemContainer.setBackgroundResource(R.drawable.m3_chat_item_active_background);
                android.util.TypedValue typedValue = new android.util.TypedValue();
                itemView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
                textChatTitle.setTextColor(typedValue.data);
            } else {
                layoutChatItemContainer.setBackgroundResource(R.drawable.m3_chat_item_background);
                android.util.TypedValue typedValue = new android.util.TypedValue();
                itemView.getContext().getTheme().resolveAttribute(R.attr.textColorPrimary, typedValue, true);
                textChatTitle.setTextColor(typedValue.data);
            }
            
            if (typographyManager != null) {
                typographyManager.applyCompleteTypography(textChatTitle, TypographyManager.TextType.MEDIUM, false);
                typographyManager.applyCompleteTypography(textLastMessageTime, TypographyManager.TextType.TINY, false);
            }
            
            if (chat != null) {
                textChatTitle.setText(chat.title);
                long time = chat.lastMessageTime > 0 ? chat.lastMessageTime : chat.createdAt;
                textLastMessageTime.setText(getRelativeTime(time));
                
                if (listener != null) {
                    // Item click - open conversation
                    layoutChatItemContainer.setOnClickListener(v -> {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        listener.onChatClick(chat);
                    });
                    
                    // Item long click - show context actions
                    layoutChatItemContainer.setOnLongClickListener(v -> {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        listener.onChatLongClick(chat, v);
                        return true;
                    });
                    
                    // 3-dot overflow menu
                    if (btnChatMore != null) {
                        btnChatMore.setOnClickListener(v -> {
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            showChatOptionsMenu(v, chat, listener);
                        });
                    }
                }
            } else {
                textChatTitle.setText("");
                textLastMessageTime.setText("");
            }
        }
        
        private void showChatOptionsMenu(View anchorView, Chat chat, OnChatClickListener listener) {
            if (anchorView == null || chat == null || listener == null) return;
            PopupMenu popup = new PopupMenu(anchorView.getContext(), anchorView);
            popup.getMenuInflater().inflate(R.menu.menu_chat_item, popup.getMenu());
            
            // Try to show icons in popup menu
            try {
                java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
                field.setAccessible(true);
                Object menuPopupHelper = field.get(popup);
                java.lang.reflect.Method setForceIcons = menuPopupHelper.getClass().getDeclaredMethod("setForceShowIcon", boolean.class);
                setForceIcons.invoke(menuPopupHelper, true);
            } catch (Exception ignored) {}
            
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_chat_rename) {
                    listener.onRenameChatClick(chat);
                    return true;
                } else if (id == R.id.action_chat_delete) {
                    listener.onDeleteChatClick(chat);
                    return true;
                }
                return false;
            });
            popup.show();
        }
        
        private String getRelativeTime(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;
            
            if (diff < 60000) {
                return "Just now";
            } else if (diff < 3600000) {
                int minutes = (int) (diff / 60000);
                return minutes + "m ago";
            } else if (diff < 86400000) {
                int hours = (int) (diff / 3600000);
                return hours + "h ago";
            } else {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
                return dateFormat.format(new Date(timestamp));
            }
        }
    }
}

