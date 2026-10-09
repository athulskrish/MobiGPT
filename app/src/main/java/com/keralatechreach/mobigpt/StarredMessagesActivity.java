package com.keralatechreach.mobigpt;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.keralatechreach.mobigpt.adapter.StarredMessagesAdapter;
import com.keralatechreach.mobigpt.base.BaseActivity;
import com.keralatechreach.mobigpt.database.Chat;
import com.keralatechreach.mobigpt.database.ChatDatabase;
import com.keralatechreach.mobigpt.database.Message;
import com.keralatechreach.mobigpt.database.StarredMessageItem;
import com.keralatechreach.mobigpt.utils.ThemeManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StarredMessagesActivity extends BaseActivity implements StarredMessagesAdapter.OnStarredItemActionListener {

    private RecyclerView recyclerStarredMessages;
    private StarredMessagesAdapter starredAdapter;
    private LinearLayout emptyStateLayout;
    private TextView emptyStateText;
    private ExecutorService backgroundExecutor;
    private ThemeManager themeManager;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize theme manager and apply theme before setting content view
        themeManager = new ThemeManager(this);
        themeManager.applyTheme();

        setContentView(R.layout.activity_starred_messages);

        // Apply all theme colors using unified updater
        applyAllThemeColors();

        backgroundExecutor = Executors.newSingleThreadExecutor();

        initViews();
        setupToolbar();
        setupRecyclerView();
        loadStarredMessages();
    }

    private void applyAllThemeColors() {
        com.keralatechreach.mobigpt.utils.ThemeColorUpdater themeUpdater =
                new com.keralatechreach.mobigpt.utils.ThemeColorUpdater(this);
        themeUpdater.applyAllThemeColors(this);
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        recyclerStarredMessages = findViewById(R.id.recycler_starred_messages);
        emptyStateLayout = findViewById(R.id.empty_state_layout);
        emptyStateText = findViewById(R.id.empty_state_text);
        setupEdgeToEdgeInsets();
    }

    private void setupEdgeToEdgeInsets() {
        if (toolbar != null) {
            int initialToolbarPaddingTop = toolbar.getPaddingTop();
            ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                );
                v.setPadding(v.getPaddingLeft(), initialToolbarPaddingTop + insets.top, v.getPaddingRight(), v.getPaddingBottom());
                android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (lp != null) {
                    android.util.TypedValue tv = new android.util.TypedValue();
                    int defaultHeight = 0;
                    if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                        defaultHeight = android.util.TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
                    }
                    if (defaultHeight > 0) {
                        lp.height = defaultHeight + insets.top;
                        v.setLayoutParams(lp);
                    }
                }
                return windowInsets;
            });
        }
        if (recyclerStarredMessages != null) {
            int initialRecyclerPaddingBottom = recyclerStarredMessages.getPaddingBottom();
            int initialRecyclerPaddingLeft = recyclerStarredMessages.getPaddingLeft();
            int initialRecyclerPaddingRight = recyclerStarredMessages.getPaddingRight();
            ViewCompat.setOnApplyWindowInsetsListener(recyclerStarredMessages, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                );
                v.setPadding(
                        initialRecyclerPaddingLeft + insets.left,
                        v.getPaddingTop(),
                        initialRecyclerPaddingRight + insets.right,
                        initialRecyclerPaddingBottom + insets.bottom
                );
                return windowInsets;
            });
        }
    }

    private void setupToolbar() {
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(true);
                getSupportActionBar().setTitle("Starred Messages");
            }
        }
    }

    private void setupRecyclerView() {
        starredAdapter = new StarredMessagesAdapter(this);
        starredAdapter.setActionListener(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerStarredMessages.setLayoutManager(layoutManager);
        recyclerStarredMessages.setAdapter(starredAdapter);
        recyclerStarredMessages.setHasFixedSize(false);
    }

    private void loadStarredMessages() {
        if (backgroundExecutor == null || backgroundExecutor.isShutdown()) return;

        backgroundExecutor.execute(() -> {
            try {
                ChatDatabase database = ChatDatabase.getDatabase(this);
                List<Message> starredRaw = database.messageDao().getStarredMessages();

                List<StarredMessageItem> pairedItems = new ArrayList<>();
                Set<String> seenInteractionKeys = new HashSet<>();

                for (Message msg : starredRaw) {
                    Message questionMessage = null;
                    Message responseMessage = null;

                    if (msg.isUser) {
                        // User prompt itself was starred
                        questionMessage = msg;
                        // Fetch corresponding subsequent AI response in this chat
                        responseMessage = database.messageDao().getSubsequentAiMessage(msg.chatId, msg.id);
                        if (responseMessage == null) {
                            responseMessage = database.messageDao().getSubsequentAiMessageByTimestamp(msg.chatId, msg.id, msg.timestamp);
                        }
                    } else {
                        // AI response was starred -> fetch preceding user question
                        responseMessage = msg;
                        questionMessage = database.messageDao().getPrecedingUserMessage(msg.chatId, msg.id);
                        if (questionMessage == null) {
                            questionMessage = database.messageDao().getPrecedingUserMessageByTimestamp(msg.chatId, msg.id, msg.timestamp);
                        }
                    }

                    // Key to deduplicate if both prompt and response of the same exchange were starred
                    long qId = questionMessage != null ? questionMessage.id : -1L;
                    long rId = responseMessage != null ? responseMessage.id : -1L;
                    String interactionKey = msg.chatId + "_" + qId + "_" + rId;

                    if (!seenInteractionKeys.contains(interactionKey)) {
                        seenInteractionKeys.add(interactionKey);

                        // Fetch chat title
                        Chat chat = database.chatDao().getChatById(msg.chatId);
                        String title = chat != null ? chat.title : "Conversation";

                        pairedItems.add(new StarredMessageItem(msg, questionMessage, responseMessage, title, msg.chatId));
                    }
                }

                runOnUiThread(() -> {
                    if (pairedItems.isEmpty()) {
                        showEmptyState();
                        updateToolbarSubtitle(0);
                    } else {
                        hideEmptyState();
                        starredAdapter.setItems(pairedItems);
                        updateToolbarSubtitle(pairedItems.size());
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("StarredMessagesActivity", "Error loading starred messages", e);
                runOnUiThread(() -> {
                    if (emptyStateText != null) {
                        emptyStateText.setText("Failed to load starred messages");
                    }
                    showEmptyState();
                });
            }
        });
    }

    private void updateToolbarSubtitle(int count) {
        if (getSupportActionBar() != null) {
            if (count > 0) {
                getSupportActionBar().setSubtitle(count == 1 ? "1 saved Q&A" : count + " saved Q&As");
            } else {
                getSupportActionBar().setSubtitle(null);
            }
        }
    }

    private void showEmptyState() {
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.VISIBLE);
        if (recyclerStarredMessages != null) recyclerStarredMessages.setVisibility(View.GONE);
    }

    private void hideEmptyState() {
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.GONE);
        if (recyclerStarredMessages != null) recyclerStarredMessages.setVisibility(View.VISIBLE);
    }

    @Override
    public void onUnstarClick(StarredMessageItem item, int position) {
        if (item == null) return;

        // Immediately update UI
        starredAdapter.removeItem(position);
        updateToolbarSubtitle(starredAdapter.getItemCount());
        if (starredAdapter.getItemCount() == 0) {
            showEmptyState();
        }
        Toast.makeText(this, "Message unstarred", Toast.LENGTH_SHORT).show();

        // Update database in background
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.execute(() -> {
                try {
                    ChatDatabase database = ChatDatabase.getDatabase(this);
                    if (item.starredMessage != null) {
                        database.messageDao().updateMessageStarredStatus(item.starredMessage.id, false);
                    }
                    if (item.questionMessage != null && item.questionMessage.isStarred) {
                        database.messageDao().updateMessageStarredStatus(item.questionMessage.id, false);
                    }
                    if (item.responseMessage != null && item.responseMessage.isStarred) {
                        database.messageDao().updateMessageStarredStatus(item.responseMessage.id, false);
                    }
                } catch (Exception e) {
                    android.util.Log.e("StarredMessagesActivity", "Error unstarring message", e);
                }
            });
        }
    }

    @Override
    public void onOpenChatClick(StarredMessageItem item) {
        if (item == null) return;

        // Open chat in MainActivity and finish current activity
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_CHAT_ID, item.chatId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_starred_messages, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.action_unstar_all) {
            showUnstarAllDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showUnstarAllDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unstar_all_dialog_title)
                .setMessage(R.string.unstar_all_dialog_message)
                .setPositiveButton(R.string.action_unstar_all, (dialog, which) -> {
                    if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
                        backgroundExecutor.execute(() -> {
                            try {
                                ChatDatabase database = ChatDatabase.getDatabase(this);
                                database.messageDao().unstarAllMessages();
                                runOnUiThread(this::loadStarredMessages);
                            } catch (Exception e) {
                                android.util.Log.e("StarredMessagesActivity", "Error unstarring all messages", e);
                            }
                        });
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStarredMessages();
    }
}