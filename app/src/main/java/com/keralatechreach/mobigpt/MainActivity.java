package com.keralatechreach.mobigpt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.card.MaterialCardView;
import com.keralatechreach.mobigpt.adapter.ChatHistoryAdapter;
import com.keralatechreach.mobigpt.adapter.MessageAdapter;
import com.keralatechreach.mobigpt.adapter.SearchSuggestionAdapter;
import com.keralatechreach.mobigpt.base.BaseActivity;
import com.keralatechreach.mobigpt.database.Chat;
import com.keralatechreach.mobigpt.database.ChatDatabase;
import com.keralatechreach.mobigpt.database.Message;
import com.keralatechreach.mobigpt.models.SuggestionChip;
import com.keralatechreach.mobigpt.utils.MessageSwipeCallback;
import com.keralatechreach.mobigpt.utils.NetworkUtils;
import com.keralatechreach.mobigpt.utils.ExportHelper;
import com.keralatechreach.mobigpt.utils.SearchHistoryManager;
import com.keralatechreach.mobigpt.utils.SettingsManager;
import com.keralatechreach.mobigpt.utils.TypographyManager;
import com.keralatechreach.mobigpt.utils.TutorialManager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BaseActivity implements ChatHistoryAdapter.OnChatClickListener, ChatManager.ChatManagerListener, ModelDownloadManager.DownloadListener, MessageAdapter.OnMessageActionListener {
    
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_STORAGE = 100;
    private static final int PERMISSION_REQUEST_NOTIFICATION = 101;
    private static final int REQUEST_CODE_AUTH_FOR_EXPORT = 9002;
    public static final String EXTRA_OPEN_CHAT_ID = "extra_open_chat_id";
    
    // Export pending state
    private String pendingExportFormat = null;
    private boolean pendingExportIsShare = false;
    
    private ExecutorService backgroundExecutor;
    
    // UI Components - will be lazily initialized
    private DrawerLayout drawerLayout;
    private Toolbar toolbar;
    private TextView toolbarTitle;
    private RecyclerView recyclerMessages;
    private RecyclerView recyclerChatHistory;
    private EditText editMessage;
    private ImageButton btnSend;
    private ImageButton btnNewChat;
    private View emptyState;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout drawerHeader;
    private PopupWindow toolbarMenuPopup;
    
    // Model loading progress components (discreet in-line)
    private LinearLayout modelLoadingProgressLayout;
    private ProgressBar progressModelLoading;
    private TextView textModelLoadingStatus;
    private TextView textModelLoadingPercentage;
    
    // Model download components
    private View downloadProgressLayout;
    private ProgressBar progressDownload;
    private TextView textDownloadStatus;
    private TextView textDownloadDetails;
    private Button btnPauseResumeDownload;
    private Button btnCancelDownload;
    
    // Navigation & Hub components (PocketPal AI Style)
    private View chatContentContainer;
    private View modelsHubContainer;
    private com.google.android.material.bottomnavigation.BottomNavigationView bottomNavigationBar;
    public static final int TAB_CHAT = 0;
    public static final int TAB_MODELS = 1;
    private int currentTab = TAB_CHAT;
    private boolean isSwitchingTab = false;

    // Model selection & Hub components
    private View inlineModelSelectionContainer;
    private RecyclerView recyclerModelSelection;
    private EditText editSearchModel;
    private ImageButton btnCloseModelSelection;
    private ImageButton btnClearSearch;
    private TextView chipFilterAll;
    private TextView chipFilterDownloaded;
    private TextView chipFilterCompatible;
    private TextView chipFilterCoding;
    private TextView chipFilterCompact;
    private View layoutModelEmptyState;
    private TextView btnEmptyResetFilter;
    private View btnDownloadFromUrl;
    private LinearLayout selectedModelDisplay;
    private TextView textSelectedModelName;
    private TextView textSelectedModelDetails;
    private TextView textSelectedModelStatus;
    private ImageView iconDropdownArrow;
    private ImageView mainChatIcon;
    private TextView textChatModelAvatar;
    private TextView textModelCountBadge;
    private TextView textRamAdvisorHint;
    private TextView badgeMemoryStatus;
    private ModelListAdapter modelListAdapter;
    private String activeCategoryFilter = "all";
    
    private MessageAdapter messageAdapter;
    private ChatHistoryAdapter chatHistoryAdapter;
    private ChatManager chatManager;
    private ModelDownloadManager downloadManager;
    private ModelConfig.Model selectedModel;
    private Handler mainHandler;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabScrollToBottom;
    
    // Simple model loading state - no complicated flags
    private String loadedModelName = null; // Name of currently loaded model
    private boolean isLoadingModel = false; // True while model is being loaded
    
    // Smart auto-scroll state tracking
    private boolean isUserScrolledUp = false;
    private boolean isAutoScrollEnabled = true;
    private boolean isProgrammaticScroll = false; // Flag to ignore programmatic scrolls
    private int lastKnownItemCount = 0; // Track item count changes
    
    // Scroll position anchoring to prevent content shift while user reads
    private int anchoredPosition = -1; // The position user is currently viewing
    private int anchoredOffset = 0; // Pixel offset from top of that position
    
    // Search functionality
    private SearchView searchView;
    private List<Message> allMessages = new ArrayList<>(); // Original full message list
    private List<Message> searchResults = new ArrayList<>(); // Filtered search results
    @SuppressWarnings("unused")
    private int currentSearchMatchIndex = -1; // Current match being viewed
    private SearchHistoryManager searchHistoryManager; // Manages search query history
    private SearchSuggestionAdapter searchSuggestionAdapter; // Adapter for search suggestions
    
    // Settings manager
    private SettingsManager settingsManager;
    
    // Typography manager
    private TypographyManager typographyManager;
    
    // Tutorial manager
    private TutorialManager tutorialManager;
    
    // Selection mode menu
    private Menu optionsMenu;
    
    // WiFi monitoring for automatic download resumption
    private AlertDialog wiFiWaitingDialog;
    private Handler wiFiMonitorHandler;
    private Runnable wiFiMonitorRunnable;
    private boolean isWiFiMonitoringActive = false;
    private static final int WIFI_CHECK_INTERVAL = 2000; // Check every 2 seconds
    
    // WiFi override flag - when user explicitly chooses to continue with mobile data
    private boolean wiFiRequirementOverridden = false;
    
    // Broadcast receiver for notification settings updates
    private BroadcastReceiver notificationSettingsReceiver;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before calling super.onCreate
        com.keralatechreach.mobigpt.utils.ThemeManager themeManager = 
            new com.keralatechreach.mobigpt.utils.ThemeManager(this);
        themeManager.applyTheme();
        
        super.onCreate(savedInstanceState);
        
        // Check if onboarding has been completed
        if (!OnboardingActivity.isOnboardingCompleted(this)) {
            // Navigate to onboarding
            Intent intent = new Intent(this, OnboardingActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        
        // Initialize search history manager
        searchHistoryManager = new SearchHistoryManager(this);
        
        // Initialize settings manager
        settingsManager = new SettingsManager(this);
        
        // Initialize typography manager
        typographyManager = new TypographyManager(this);
        
        // Initialize tutorial manager
        tutorialManager = new TutorialManager(this);
        
        // Create background executor for non-UI tasks
        backgroundExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "MainActivity-Background");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        
        setContentView(R.layout.activity_main);
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize core components immediately
        initViews();
        setupToolbar();
        
        // Setup modern back navigation compatible with AndroidX Predictive Back
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (toolbarMenuPopup != null && toolbarMenuPopup.isShowing()) {
                    toolbarMenuPopup.dismiss();
                    return;
                }
                if (inlineModelSelectionContainer != null && inlineModelSelectionContainer.getVisibility() == View.VISIBLE) {
                    hideInlineModelSelection();
                    return;
                }
                if (currentTab == TAB_MODELS) {
                    switchToTab(TAB_CHAT);
                    return;
                }
                if (messageAdapter != null && messageAdapter.isInSelectionMode()) {
                    messageAdapter.exitSelectionMode();
                    return;
                }
                if (drawerLayout != null && drawerLayout.isDrawerOpen(findViewById(R.id.nav_view))) {
                    drawerLayout.closeDrawers();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
        
        // Apply typography to main UI elements
        applyTypographyToUI();
        
        // Setup download manager first (needed by model spinner)
        setupDownloadManager();
        
        // Setup broadcast receiver for notification settings
        setupNotificationSettingsReceiver();
        
        // CRITICAL: Setup ChatManager on main thread BEFORE setupComponents()
        // This prevents race condition where spinner's onItemSelected() fires before chatManager is initialized
        setupChatManager();
        
        // Setup essential components (this calls setupModelSpinner which needs chatManager)
        setupComponents();
        
        // Request notification permission for Android 13+ only if tutorial is already completed,
        // preventing interruptions and permission popups while new users are in the tutorial
        if (tutorialManager == null || tutorialManager.isTutorialCompleted()) {
            requestNotificationPermission();
        }
        
        // Initialize non-UI components in background
        safeExecuteBackground(() -> {
            // Initialize AI engine in background
            initializeAI();
            
            // Load data when ready
            runOnUiThread(() -> {
                if (chatManager != null) {
                    chatManager.loadChatHistory();
                }
                showEmptyState();
                
                // Open specific chat if requested by intent
                handleOpenChatIntent(getIntent());

                // Show tutorial for first-time users (after UI is ready)
                showTutorialIfNeeded();
                
                // Auto-open model dropdown if onboarding/tutorial done but no model loaded
                autoOpenModelDropdownIfNeeded();
            });
        });
        
        Log.d(TAG, "MainActivity created with lifecycle management");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOpenChatIntent(intent);
    }

    /**
     * Handle incoming intent to open a specific chat by ID (e.g. from Starred Messages)
     */
    private void handleOpenChatIntent(Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_OPEN_CHAT_ID)) return;

        long openChatId = intent.getLongExtra(EXTRA_OPEN_CHAT_ID, -1);
        intent.removeExtra(EXTRA_OPEN_CHAT_ID);
        if (openChatId == -1) return;

        Log.d(TAG, "handleOpenChatIntent: Opening chat ID " + openChatId);
        safeExecuteBackground(() -> {
            try {
                ChatDatabase database = ChatDatabase.getDatabase(this);
                Chat chat = database.chatDao().getChatById(openChatId);
                if (chat != null) {
                    runOnUiThread(() -> {
                        if (currentTab != TAB_CHAT) {
                            switchToTab(TAB_CHAT);
                        }
                        if (chatManager != null) {
                            chatManager.handleChatClick(chat);
                        }
                    });
                } else {
                    Log.w(TAG, "handleOpenChatIntent: Chat not found for ID " + openChatId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error opening chat from intent ID: " + openChatId, e);
            }
        });
    }
    
    /**
     * Safely execute a task on the background executor
     * Checks if the executor is still valid before executing
     * @param task The runnable task to execute
     * @return true if task was submitted successfully, false otherwise
     */
    private boolean safeExecuteBackground(Runnable task) {
        if (backgroundExecutor == null || backgroundExecutor.isShutdown()) {
            Log.w(TAG, "Background executor not available, task not executed");
            return false;
        }
        
        try {
            backgroundExecutor.execute(task);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException e) {
            Log.e(TAG, "Task rejected by executor", e);
            return false;
        }
    }
    
    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        toolbar = findViewById(R.id.toolbar);
        toolbarTitle = findViewById(R.id.toolbar_title);
        recyclerMessages = findViewById(R.id.recycler_messages);
        recyclerChatHistory = findViewById(R.id.recycler_chat_history);
        editMessage = findViewById(R.id.edit_message);
        btnSend = findViewById(R.id.btn_send);
        btnNewChat = findViewById(R.id.btn_new_chat);
        emptyState = findViewById(R.id.empty_state);
        fabScrollToBottom = findViewById(R.id.fab_scroll_to_bottom);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        drawerHeader = findViewById(R.id.drawer_header);
        
        // Model loading progress components (discreet in-line)
        modelLoadingProgressLayout = findViewById(R.id.model_loading_progress_layout);
        progressModelLoading = findViewById(R.id.progress_model_loading);
        textModelLoadingStatus = findViewById(R.id.text_model_loading_status);
        textModelLoadingPercentage = findViewById(R.id.text_model_loading_percentage);
        
        // Model download components
        downloadProgressLayout = findViewById(R.id.download_progress_layout);
        progressDownload = findViewById(R.id.progress_download);
        textDownloadStatus = findViewById(R.id.text_download_status);
        textDownloadDetails = findViewById(R.id.text_download_details);
        btnPauseResumeDownload = findViewById(R.id.btn_pause_resume_download);
        btnCancelDownload = findViewById(R.id.btn_cancel_download);
        
        // Tab containers & Bottom navigation (PocketPal AI Style)
        chatContentContainer = findViewById(R.id.chat_content_container);
        modelsHubContainer = findViewById(R.id.models_hub_container);
        bottomNavigationBar = findViewById(R.id.bottom_navigation_bar);
        if (bottomNavigationBar != null) {
            bottomNavigationBar.setOnItemSelectedListener(item -> {
                if (isSwitchingTab) return true;
                int itemId = item.getItemId();
                if (itemId == R.id.nav_chat) {
                    switchToTab(TAB_CHAT);
                    return true;
                } else if (itemId == R.id.nav_models) {
                    switchToTab(TAB_MODELS);
                    return true;
                }
                return false;
            });
        }

        // Model selection & Hub components
        inlineModelSelectionContainer = findViewById(R.id.inline_model_selection_container);
        recyclerModelSelection = findViewById(R.id.recycler_model_selection);
        editSearchModel = findViewById(R.id.edit_search_model);
        btnCloseModelSelection = findViewById(R.id.btn_close_model_selection);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        chipFilterAll = findViewById(R.id.chip_filter_all);
        chipFilterDownloaded = findViewById(R.id.chip_filter_downloaded);
        chipFilterCompatible = findViewById(R.id.chip_filter_compatible);
        chipFilterCoding = findViewById(R.id.chip_filter_coding);
        chipFilterCompact = findViewById(R.id.chip_filter_compact);
        layoutModelEmptyState = findViewById(R.id.layout_model_empty_state);
        btnEmptyResetFilter = findViewById(R.id.btn_empty_reset_filter);
        btnDownloadFromUrl = findViewById(R.id.btn_download_from_url);
        selectedModelDisplay = findViewById(R.id.selected_model_display);
        textSelectedModelName = findViewById(R.id.text_selected_model_name);
        textSelectedModelDetails = findViewById(R.id.text_selected_model_details);
        textSelectedModelStatus = findViewById(R.id.text_selected_model_status);
        iconDropdownArrow = findViewById(R.id.icon_dropdown_arrow);
        mainChatIcon = findViewById(R.id.main_chat_icon);
        textChatModelAvatar = findViewById(R.id.text_chat_model_avatar);
        if (iconDropdownArrow != null) {
            iconDropdownArrow.setRotation(0f);
        }

        // Outside touch listeners to collapse dropdown when user interacts with chat or input
        if (recyclerMessages != null) {
            recyclerMessages.setOnTouchListener((v, event) -> {
                if (inlineModelSelectionContainer != null && inlineModelSelectionContainer.getVisibility() == View.VISIBLE) {
                    hideInlineModelSelection();
                }
                return false;
            });
        }
        if (emptyState != null) {
            emptyState.setOnTouchListener((v, event) -> {
                if (inlineModelSelectionContainer != null && inlineModelSelectionContainer.getVisibility() == View.VISIBLE) {
                    hideInlineModelSelection();
                }
                return false;
            });
        }
        if (editMessage != null) {
            editMessage.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && inlineModelSelectionContainer != null && inlineModelSelectionContainer.getVisibility() == View.VISIBLE) {
                    hideInlineModelSelection();
                }
            });
        }
        
        // Setup hamburger menu button (Back arrow on Model Hub, Drawer toggle on Chat)
        ImageButton btnHamburgerMenu = findViewById(R.id.btn_hamburger_menu);
        if (btnHamburgerMenu != null) {
            btnHamburgerMenu.setOnClickListener(v -> {
                // Haptic feedback on button press
                performHapticFeedback(v);
                
                if (currentTab == TAB_MODELS) {
                    switchToTab(TAB_CHAT);
                } else if (drawerLayout != null) {
                    drawerLayout.openDrawer(findViewById(R.id.nav_view));
                }
            });
        }
        
        // Setup "View chat history" button in empty state
        LinearLayout btnViewChatHistory = findViewById(R.id.btn_view_chat_history_enhanced);
        if (btnViewChatHistory != null) {
            btnViewChatHistory.setOnClickListener(v -> {
                // Haptic feedback on button press
                performHapticFeedback(v);
                
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(findViewById(R.id.nav_view));
                }
            });
        }
        
        // Setup suggestion chips in enhanced empty state
        setupSuggestionChips();
        
        // Setup hardware telemetry banner
        setupHardwareTelemetry();

        // Setup contextual slash command chips
        setupSlashCommandChips();

        // Setup edge-to-edge window insets
        setupEdgeToEdgeInsets();
        
        // Debug: Check if views are found
        Log.d("MainActivity", "inlineModelSelectionContainer: " + inlineModelSelectionContainer);
        Log.d("MainActivity", "downloadProgressLayout: " + downloadProgressLayout);
        Log.d("MainActivity", "btnPauseResumeDownload: " + btnPauseResumeDownload);
    }

    public void switchToTab(int tab) {
        if (!isActivityValid()) return;
        if (isSwitchingTab) return;

        try {
            isSwitchingTab = true;
            currentTab = tab;
            if (bottomNavigationBar != null) {
                performHapticFeedback(bottomNavigationBar);
            }

            ImageButton btnHamburger = findViewById(R.id.btn_hamburger_menu);

            if (tab == TAB_CHAT) {
                if (chatContentContainer != null) chatContentContainer.setVisibility(View.VISIBLE);
                if (modelsHubContainer != null) modelsHubContainer.setVisibility(View.GONE);
                if (toolbarTitle != null) toolbarTitle.setText("MobiGPT");
                if (btnNewChat != null) btnNewChat.setVisibility(View.VISIBLE);
                if (btnHamburger != null) {
                    btnHamburger.setImageResource(R.drawable.ic_menu);
                    btnHamburger.setContentDescription("Open Chat History");
                }
                updateDrawerNavActiveState(TAB_CHAT);
                if (bottomNavigationBar != null && bottomNavigationBar.getSelectedItemId() != R.id.nav_chat) {
                    bottomNavigationBar.setSelectedItemId(R.id.nav_chat);
                }
                if (iconDropdownArrow != null) {
                    iconDropdownArrow.setRotation(0f);
                }
                invalidateOptionsMenu();
            } else if (tab == TAB_MODELS) {
                if (chatContentContainer != null) chatContentContainer.setVisibility(View.GONE);
                if (modelsHubContainer != null) modelsHubContainer.setVisibility(View.VISIBLE);
                if (toolbarTitle != null) toolbarTitle.setText("Model Hub");
                if (btnNewChat != null) btnNewChat.setVisibility(View.GONE);
                if (btnHamburger != null) {
                    btnHamburger.setImageResource(R.drawable.ic_back);
                    btnHamburger.setContentDescription("Back to Chat");
                }
                updateDrawerNavActiveState(TAB_MODELS);
                if (bottomNavigationBar != null && bottomNavigationBar.getSelectedItemId() != R.id.nav_models) {
                    bottomNavigationBar.setSelectedItemId(R.id.nav_models);
                }
                setupHardwareTelemetry();
                if (modelListAdapter != null) {
                    modelListAdapter.updateModels(ModelConfig.getAvailableModels(this));
                    if (selectedModel != null) {
                        modelListAdapter.setCurrentModel(selectedModel.name);
                    }
                }
                invalidateOptionsMenu();
            }
        } finally {
            isSwitchingTab = false;
        }
    }

    private void setupHardwareTelemetry() {
        TextView textDeviceTelemetry = findViewById(R.id.text_device_telemetry);
        textRamAdvisorHint = findViewById(R.id.text_ram_advisor_hint);
        badgeMemoryStatus = findViewById(R.id.badge_memory_status);
        textModelCountBadge = findViewById(R.id.text_model_count_badge);

        long availRamMb = ModelConfig.getAvailableRamMb(this);
        int cores = Runtime.getRuntime().availableProcessors();
        String ramStr = String.format(Locale.US, "%.1f GB Free RAM", availRamMb / 1024.0);
        String fitHint = (availRamMb < 1200) ? "Optimal for ≤ 0.5B" : (availRamMb < 3200) ? "Optimal for ≤ 3B" : "All models supported";

        if (textDeviceTelemetry != null) {
            textDeviceTelemetry.setText("⚡ " + ramStr + " · " + fitHint);
        }
        if (badgeMemoryStatus != null) {
            if (availRamMb < 1200) {
                if (textRamAdvisorHint != null) textRamAdvisorHint.setText("Models ≤ 0.5B recommended for your RAM");
                badgeMemoryStatus.setText("Low RAM");
                badgeMemoryStatus.setTextColor(getResources().getColor(R.color.warning_orange, getTheme()));
            } else if (availRamMb < 3200) {
                if (textRamAdvisorHint != null) textRamAdvisorHint.setText("Models ≤ 3B recommended for optimal speed");
                badgeMemoryStatus.setText("Normal RAM");
                badgeMemoryStatus.setTextColor(getResources().getColor(R.color.green_700, getTheme()));
            } else {
                if (textRamAdvisorHint != null) textRamAdvisorHint.setText("All models compatible with your hardware");
                badgeMemoryStatus.setText("Flagship Ready");
                badgeMemoryStatus.setTextColor(getResources().getColor(R.color.green_700, getTheme()));
            }
        }
        List<ModelConfig.Model> models = ModelConfig.getAvailableModels(this);
        if (textModelCountBadge != null) {
            textModelCountBadge.setText(String.valueOf(models.size()));
        }
        updateModelFilterChips(models);
    }

    private void updateModelFilterChips(List<ModelConfig.Model> models) {
        if (models == null) return;
        int total = models.size();
        int downloaded = 0;
        int compatible = 0;
        int coding = 0;
        int compact = 0;

        for (ModelConfig.Model m : models) {
            if (downloadManager != null && downloadManager.isModelDownloaded(m)) {
                downloaded++;
            }
            ModelConfig.RamCompatibility compat = ModelConfig.getRamCompatibility(this, m);
            if (compat != ModelConfig.RamCompatibility.INSUFFICIENT) {
                compatible++;
            }
            if ((m.name != null && m.name.toLowerCase().contains("coder"))
                || (m.displayName != null && m.displayName.toLowerCase().contains("coder"))
                || (m.description != null && m.description.toLowerCase().contains("cod"))) {
                coding++;
            }
            String p = m.parameterCount != null ? m.parameterCount.toUpperCase() : "";
            if (p.contains("0.5B") || p.contains("1.5B") || p.contains("1B")) {
                compact++;
            }
        }

        // Update main Model Hub chips
        if (chipFilterAll != null) chipFilterAll.setText("All (" + total + ")");
        if (chipFilterDownloaded != null) chipFilterDownloaded.setText("Downloaded (" + downloaded + ")");
        if (chipFilterCompatible != null) chipFilterCompatible.setText("⚡ Fits RAM (" + compatible + ")");
        if (chipFilterCoding != null) chipFilterCoding.setText("💻 Coding (" + coding + ")");
        if (chipFilterCompact != null) chipFilterCompact.setText("🪶 Compact (" + compact + ")");

        // Update Bottom Sheet chips if active
        if (sheetChipAll != null) sheetChipAll.setText("All (" + total + ")");
        if (sheetChipDownloaded != null) sheetChipDownloaded.setText("Downloaded (" + downloaded + ")");
        if (sheetChipCompatible != null) sheetChipCompatible.setText("⚡ Fits RAM (" + compatible + ")");
        if (sheetChipCoding != null) sheetChipCoding.setText("💻 Coding (" + coding + ")");
        if (sheetChipCompact != null) sheetChipCompact.setText("🪶 Compact (" + compact + ")");
    }

    private void setupSlashCommandChips() {
        TextView chipCode = findViewById(R.id.chip_cmd_code);
        TextView chipSummarize = findViewById(R.id.chip_cmd_summarize);
        TextView chipExplain = findViewById(R.id.chip_cmd_explain);
        TextView chipFix = findViewById(R.id.chip_cmd_fix);
        TextView chipBrainstorm = findViewById(R.id.chip_cmd_brainstorm);

        if (chipCode != null) {
            chipCode.setOnClickListener(v -> applySlashCommand("You are an expert software engineer. Write clean, efficient code for: "));
        }
        if (chipSummarize != null) {
            chipSummarize.setOnClickListener(v -> applySlashCommand("Summarize the following clearly in concise bullet points: "));
        }
        if (chipExplain != null) {
            chipExplain.setOnClickListener(v -> applySlashCommand("Explain the following concept simply with clear examples: "));
        }
        if (chipFix != null) {
            chipFix.setOnClickListener(v -> applySlashCommand("Fix grammar, spelling, and polish the following text: "));
        }
        if (chipBrainstorm != null) {
            chipBrainstorm.setOnClickListener(v -> applySlashCommand("Brainstorm 5 innovative and practical ideas for: "));
        }
    }

    private void applySlashCommand(String template) {
        if (editMessage != null) {
            String current = editMessage.getText().toString();
            if (current.startsWith("/")) {
                editMessage.setText(template);
            } else if (!current.isEmpty()) {
                editMessage.setText(template + "\n\n" + current);
            } else {
                editMessage.setText(template);
            }
            editMessage.setSelection(editMessage.getText().length());
            editMessage.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editMessage, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }
    
    private void setupEdgeToEdgeInsets() {
        if (toolbar != null) {
            int initialToolbarPaddingTop = toolbar.getPaddingTop();
            ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                );
                v.setPadding(v.getPaddingLeft(), initialToolbarPaddingTop + insets.top, v.getPaddingRight(), v.getPaddingBottom());
                ViewGroup.LayoutParams lp = v.getLayoutParams();
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
        
        // Edge-to-edge and IME keyboard handling for main container & bottom navigation
        View mainContentLinear = findViewById(R.id.main_content_linear);
        View messageInputLayout = findViewById(R.id.message_input_layout);
        int initialMessageInputPaddingStart = messageInputLayout != null ? messageInputLayout.getPaddingStart() : 0;
        int initialMessageInputPaddingTop = messageInputLayout != null ? messageInputLayout.getPaddingTop() : 0;
        int initialMessageInputPaddingEnd = messageInputLayout != null ? messageInputLayout.getPaddingEnd() : 0;
        int initialMessageInputPaddingBottom = messageInputLayout != null ? messageInputLayout.getPaddingBottom() : 0;

        int initialModelRecyclerPaddingLeft = recyclerModelSelection != null ? recyclerModelSelection.getPaddingLeft() : 0;
        int initialModelRecyclerPaddingTop = recyclerModelSelection != null ? recyclerModelSelection.getPaddingTop() : 0;
        int initialModelRecyclerPaddingRight = recyclerModelSelection != null ? recyclerModelSelection.getPaddingRight() : 0;
        int initialModelRecyclerPaddingBottom = recyclerModelSelection != null ? recyclerModelSelection.getPaddingBottom() : 0;

        if (mainContentLinear != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainContentLinear, (v, windowInsets) -> {
                Insets navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
                Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
                Insets cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

                int navBottom = Math.max(navInsets.bottom, systemBars.bottom);
                int imeBottom = imeInsets.bottom;
                boolean isImeVisible = imeBottom > navBottom || windowInsets.isVisible(WindowInsetsCompat.Type.ime());

                // Push container up by keyboard height when typing so input is never covered.
                // When keyboard is closed, keep container bottom padding at 0 so message input's
                // surface background extends cleanly behind the system navigation buttons.
                int containerBottom = isImeVisible ? imeBottom : 0;
                v.setPadding(cutoutInsets.left, 0, cutoutInsets.right, containerBottom);

                // Pad the message input box above navigation buttons when keyboard is closed.
                // When keyboard is open, container is already lifted above the keyboard, so use initial padding.
                if (messageInputLayout != null) {
                    int bottomNavPadding = isImeVisible ? 0 : navBottom;
                    messageInputLayout.setPaddingRelative(
                        initialMessageInputPaddingStart + cutoutInsets.left,
                        initialMessageInputPaddingTop,
                        initialMessageInputPaddingEnd + cutoutInsets.right,
                        initialMessageInputPaddingBottom + bottomNavPadding
                    );
                }

                // In Model Hub tab, ensure recycler content is padded above navigation buttons
                if (recyclerModelSelection != null) {
                    int bottomNavPadding = isImeVisible ? 0 : navBottom;
                    recyclerModelSelection.setPadding(
                        initialModelRecyclerPaddingLeft,
                        initialModelRecyclerPaddingTop,
                        initialModelRecyclerPaddingRight,
                        initialModelRecyclerPaddingBottom + bottomNavPadding
                    );
                }

                return windowInsets;
            });
            ViewCompat.requestApplyInsets(mainContentLinear);
        }
        
        if (drawerHeader != null) {
            int initialHeaderPaddingTop = drawerHeader.getPaddingTop();
            ViewCompat.setOnApplyWindowInsetsListener(drawerHeader, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                );
                v.setPadding(v.getPaddingLeft(), initialHeaderPaddingTop + insets.top, v.getPaddingRight(), v.getPaddingBottom());
                return windowInsets;
            });
        }
    }
    
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        
        // Disable the default action bar title since we use a custom TextView
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        
        // Apply all theme-aware colors (toolbar, drawer header, icons, buttons, status bar)
        applyAllThemeColors();
        
        // Note: The hamburger menu is handled manually via the btn_hamburger_menu button
        Log.d("MainActivity", "Toolbar set up with custom hamburger menu");
    }
    
    /**
     * Apply typography settings to all UI elements
     * This applies font size, font family, and spacing across the entire app
     */
    private void applyTypographyToUI() {
        if (typographyManager == null) return;
        
        // Apply to toolbar title
        if (toolbarTitle != null) {
            typographyManager.applyCompleteTypography(
                toolbarTitle, 
                TypographyManager.TextType.LARGE, 
                false
            );
        }
        
        // Apply to message input field
        if (editMessage != null) {
            typographyManager.applyTypography(
                editMessage, 
                TypographyManager.TextType.NORMAL
            );
        }
        
        // Apply to download status texts
        if (textDownloadStatus != null) {
            typographyManager.applyCompleteTypography(
                textDownloadStatus, 
                TypographyManager.TextType.MEDIUM, 
                false
            );
        }
        
        if (textDownloadDetails != null) {
            typographyManager.applyCompleteTypography(
                textDownloadDetails, 
                TypographyManager.TextType.SMALL, 
                false
            );
        }
        
        // Apply to model loading texts
        if (textModelLoadingStatus != null) {
            typographyManager.applyCompleteTypography(
                textModelLoadingStatus, 
                TypographyManager.TextType.MEDIUM, 
                false
            );
        }
        
        if (textModelLoadingPercentage != null) {
            typographyManager.applyCompleteTypography(
                textModelLoadingPercentage,
                TypographyManager.TextType.MEDIUM,
                false
            );
        }

        // Apply to navigation drawer (nav_view)
        android.view.View nav = findViewById(R.id.nav_view);
        if (nav instanceof android.view.ViewGroup) {
            typographyManager.applyTypographyToViewGroup(
                (android.view.ViewGroup) nav,
                TypographyManager.TextType.NORMAL
            );
        }

        Log.d(TAG, "Typography applied to UI elements - Font: " + 
            settingsManager.getFontFamily() + ", Size: " + settingsManager.getFontSize());
    }
    
    /**
     * Get the current theme resource ID based on settings
     */
    private int getThemeResId() {
        if (settingsManager == null) {
            return R.style.Theme_MobiGPT;
        }
        
        com.keralatechreach.mobigpt.utils.ThemeManager tm = 
            new com.keralatechreach.mobigpt.utils.ThemeManager(this);
        return tm.getThemeResourceId(
            settingsManager.getAccentColor(),
            settingsManager.isOLEDBlackTheme(),
            tm.isDarkMode()
        );
    }
    
    /**
     * Apply theme-aware surface styling to toolbar
     */
    private void applyToolbarGradient() {
        if (toolbar == null) return;
        
        android.util.TypedValue typedValue = new android.util.TypedValue();
        android.content.res.Resources.Theme theme = getTheme();
        
        // Use surface color for modern Material 3 clean look
        theme.resolveAttribute(R.attr.surfaceColor, typedValue, true);
        int surfaceColor = typedValue.data;
        
        theme.resolveAttribute(R.attr.textColorPrimary, typedValue, true);
        int textColor = typedValue.data;
        
        toolbar.setBackgroundColor(surfaceColor);
        if (toolbarTitle != null) {
            toolbarTitle.setTextColor(textColor);
        }
        ImageButton btnHamburger = findViewById(R.id.btn_hamburger_menu);
        if (btnHamburger != null) {
            btnHamburger.setColorFilter(textColor);
        }
        if (btnNewChat != null) {
            btnNewChat.setColorFilter(textColor);
        }
        if (toolbar.getOverflowIcon() != null) {
            toolbar.getOverflowIcon().setTint(textColor);
        }
    }
    
    /**
     * Apply theme-aware styling to the navigation drawer header (clean surface background for ChatGPT style)
     */
    private void applyDrawerHeaderGradient() {
        if (drawerHeader == null) return;
        drawerHeader.setBackground(null);
    }
    
    /**
     * Apply theme-aware colors to download control buttons
     */
    private void applyDownloadButtonColors() {
        if (btnPauseResumeDownload == null || btnCancelDownload == null) return;
        
        // Get theme colors
        android.util.TypedValue typedValue = new android.util.TypedValue();
        android.content.res.Resources.Theme theme = getTheme();
        
        // Get primary color for pause/resume button
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int colorPrimary = typedValue.data;
        
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryVariant, typedValue, true);
        int colorPrimaryVariant = typedValue.data;
        
        float density = getResources().getDisplayMetrics().density;
        float pillRadius = 16 * density;
        
        // Create state list drawable for pause/resume button (tonal pill)
        android.graphics.drawable.StateListDrawable pauseResumeDrawable = new android.graphics.drawable.StateListDrawable();
        
        android.graphics.drawable.GradientDrawable pressedShape = new android.graphics.drawable.GradientDrawable();
        pressedShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        pressedShape.setColor(colorPrimaryVariant);
        pressedShape.setCornerRadius(pillRadius);
        pauseResumeDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedShape);
        
        android.graphics.drawable.GradientDrawable disabledShape = new android.graphics.drawable.GradientDrawable();
        disabledShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        disabledShape.setColor(0xFFCCCCCC);
        disabledShape.setCornerRadius(pillRadius);
        pauseResumeDrawable.addState(new int[]{-android.R.attr.state_enabled}, disabledShape);
        
        android.graphics.drawable.GradientDrawable normalShape = new android.graphics.drawable.GradientDrawable();
        normalShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        normalShape.setColor(colorPrimary);
        normalShape.setCornerRadius(pillRadius);
        pauseResumeDrawable.addState(new int[]{}, normalShape);
        
        btnPauseResumeDownload.setBackground(pauseResumeDrawable);
        btnPauseResumeDownload.setTextColor(android.graphics.Color.WHITE);
        
        // Create state list drawable for cancel button (red pill)
        android.graphics.drawable.StateListDrawable cancelDrawable = new android.graphics.drawable.StateListDrawable();
        
        android.graphics.drawable.GradientDrawable cancelPressedShape = new android.graphics.drawable.GradientDrawable();
        cancelPressedShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        cancelPressedShape.setColor(0xFFB71C1C); // Darker red
        cancelPressedShape.setCornerRadius(pillRadius);
        cancelDrawable.addState(new int[]{android.R.attr.state_pressed}, cancelPressedShape);
        
        android.graphics.drawable.GradientDrawable cancelDisabledShape = new android.graphics.drawable.GradientDrawable();
        cancelDisabledShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        cancelDisabledShape.setColor(0xFFCCCCCC);
        cancelDisabledShape.setCornerRadius(pillRadius);
        cancelDrawable.addState(new int[]{-android.R.attr.state_enabled}, cancelDisabledShape);
        
        android.graphics.drawable.GradientDrawable cancelNormalShape = new android.graphics.drawable.GradientDrawable();
        cancelNormalShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        cancelNormalShape.setColor(0xFFEF4444); // Modern flat red
        cancelNormalShape.setCornerRadius(pillRadius);
        cancelDrawable.addState(new int[]{}, cancelNormalShape);
        
        btnCancelDownload.setBackground(cancelDrawable);
        btnCancelDownload.setTextColor(android.graphics.Color.WHITE);
        
        Log.d(TAG, String.format("Applying download button colors - Primary: #%06X", colorPrimary & 0xFFFFFF));
    }
    
    /**
     * Unified method to apply all theme-aware UI updates
     * Call this method whenever the theme changes to update all UI elements at once
     */
    private void applyAllThemeColors() {
        Log.d(TAG, "Applying all theme colors...");
        
        // Use the unified theme color updater
        com.keralatechreach.mobigpt.utils.ThemeColorUpdater themeUpdater = 
            new com.keralatechreach.mobigpt.utils.ThemeColorUpdater(this);
        themeUpdater.applyAllThemeColors(this);
        
        // Apply activity-specific theme updates that aren't covered by the universal updater
        applyMainActivitySpecificTheme();
        
        Log.d(TAG, "All theme colors applied successfully");
    }
    
    /**
     * Apply MainActivity-specific theme updates
     */
    private void applyMainActivitySpecificTheme() {
        // Apply drawer header and toolbar gradients (specific to MainActivity)
        applyToolbarGradient();
        applyDrawerHeaderGradient();
        
        // Ensure hamburger menu is white for visibility on gradient toolbar
        ImageButton btnHamburgerMenu = findViewById(R.id.btn_hamburger_menu);
        if (btnHamburgerMenu != null) {
            androidx.core.widget.ImageViewCompat.setImageTintList(btnHamburgerMenu, 
                android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        }
        
        // Refresh chat history to update icon colors
        if (chatHistoryAdapter != null) {
            chatHistoryAdapter.refreshAccentColors();
        }
        
        // Refresh message adapter to update accent colors
        if (messageAdapter != null) {
            messageAdapter.notifyDataSetChanged();
        }
        
        // Update pull-to-refresh colors
        if (swipeRefresh != null) {
            com.keralatechreach.mobigpt.utils.ThemeManager themeManager = 
                new com.keralatechreach.mobigpt.utils.ThemeManager(this);
            int accentColorResId = themeManager.getPrimaryColorResId();
            
            swipeRefresh.setColorSchemeResources(
                accentColorResId,
                R.color.secondary_blue,
                R.color.accent_orange
            );
            swipeRefresh.setProgressBackgroundColorSchemeResource(
                themeManager.isDarkMode() ? 
                    (themeManager.isOLEDThemeEnabled() ? R.color.oled_surface : R.color.surface_dark) : 
                    R.color.white
            );
        }
        
        Log.d(TAG, "MainActivity-specific theme applied");
    }
    
    /**
     * Setup suggestion chips in the enhanced empty state
     */
    private void setupSuggestionChips() {
        // Find the suggestions container in the empty state
        View emptyStateView = findViewById(R.id.empty_state);
        if (emptyStateView == null) {
            Log.w(TAG, "Empty state view not found, cannot setup suggestion chips");
            return;
        }
        
        LinearLayout suggestionsContainer = emptyStateView.findViewById(R.id.suggestions_container);
        if (suggestionsContainer == null) {
            Log.w(TAG, "Suggestions container not found in empty state");
            return;
        }
        
        // Create suggestion cards (4 high-quality starters in balanced 2x2 grid)
        ArrayList<SuggestionChip> suggestions = new ArrayList<>();
        suggestions.add(new SuggestionChip("Explain quantum computing simply", R.drawable.ic_chat_bubble));
        suggestions.add(new SuggestionChip("Help with coding & debugging", R.drawable.ic_code));
        suggestions.add(new SuggestionChip("Summarize notes into key points", R.drawable.ic_document));
        suggestions.add(new SuggestionChip("Translate text to another language", R.drawable.ic_chat));
        
        // Get the two rows using IDs for better reliability
        LinearLayout row1 = emptyStateView.findViewById(R.id.suggestion_row_1);
        LinearLayout row2 = emptyStateView.findViewById(R.id.suggestion_row_2);
        
        if (row1 == null || row2 == null) {
            Log.w(TAG, "Suggestion rows not found");
            return;
        }
        
        // Clear any existing chips
        row1.removeAllViews();
        row2.removeAllViews();
        
        // Add chips to rows (2 cards per row, 50% width each)
        for (int i = 0; i < suggestions.size() && i < 4; i++) {
            SuggestionChip chip = suggestions.get(i);
            ViewGroup targetRow = (i < 2) ? row1 : row2;
            View chipView = createChipView(targetRow, chip, i);
            targetRow.addView(chipView);
        }
        
        // Ensure the container is visible
        suggestionsContainer.setVisibility(View.VISIBLE);
        row1.setVisibility(View.VISIBLE);
        row2.setVisibility(View.VISIBLE);
        
        Log.d(TAG, "Suggestion chips setup complete with " + suggestions.size() + " chips");
    }
    
    /**
     * Create a chip view from a SuggestionChip
     */
    private View createChipView(ViewGroup parent, SuggestionChip chip, int index) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View chipView = inflater.inflate(R.layout.item_suggestion_chip, parent, false);
        
        // Set layout parameters with 1.0f weight so each card takes exactly 50% of the row
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        params.setMargins(4, 4, 4, 4); // Add spacing between chips
        chipView.setLayoutParams(params);
        
        MaterialCardView chipContainer = chipView.findViewById(R.id.chip_container);
        TextView chipText = chipView.findViewById(R.id.chip_text);
        ImageView chipIcon = chipView.findViewById(R.id.chip_icon);
        
        // Set chip text
        chipText.setText(chip.getText());
        
        // Set icon if available
        if (chip.hasIcon()) {
            chipIcon.setVisibility(View.VISIBLE);
            chipIcon.setImageResource(chip.getIconResId());
        } else {
            chipIcon.setVisibility(View.GONE);
        }
        
        // Set click listener
        chipContainer.setOnClickListener(v -> {
            // Animate the chip
            animateChipClick(chipContainer);
            
            // Fill the message input with the suggestion without auto-running
            if (editMessage != null) {
                editMessage.setText(chip.getText());
                editMessage.setSelection(editMessage.getText().length());
                editMessage.requestFocus();
                
                // Show keyboard so user can edit or send
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editMessage, InputMethodManager.SHOW_IMPLICIT);
                }
            }
            
            Log.d(TAG, "Suggestion chip clicked: " + chip.getText());
        });
        
        // Add entrance animation with delay based on index
        chipView.setAlpha(0f);
        chipView.setTranslationY(20f);
        chipView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay(index * 50L)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
        
        return chipView;
    }
    
    /**
     * Animate chip click with scale effect
     */
    private void animateChipClick(View chipView) {
        // Haptic feedback on chip tap
        performHapticFeedback(chipView);
        
        chipView.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction(() -> {
                chipView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start();
            })
            .start();
    }
    
    /**
     * Setup essential UI components and listeners
     */
    private void setupComponents() {
        // Setup essential components immediately
        setupRecyclerViews();
        setupListeners();
        setupModelSpinner();
        setupStarredMessagesButton();
        setupSwipeRefresh();
        
        Log.d(TAG, "Components setup completed");
    }
    
    private void setupChatManager() {
        chatManager = new ChatManager(this);
        chatManager.setListener(this);
    }
    
    private void setupDownloadManager() {
        downloadManager = ModelDownloadManager.getInstance(this);
        downloadManager.setDownloadListener(this);
        

    }
    
    /**
     * Setup broadcast receiver for notification settings updates
     */
    private void setupNotificationSettingsReceiver() {
        notificationSettingsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("UPDATE_NOTIFICATION_SETTINGS".equals(intent.getAction())) {
                    Log.d(TAG, "Notification settings update received");
                    if (downloadManager != null) {
                        downloadManager.updateNotificationSettings();
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter("UPDATE_NOTIFICATION_SETTINGS");
        ContextCompat.registerReceiver(this, notificationSettingsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "Notification settings receiver registered");
    }
    
    /**
     * Request notification permission for Android 13+ (API 33+)
     */
    public void requestNotificationPermission() {
        Log.d(TAG, "requestNotificationPermission() called");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int permissionStatus = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS);
            Log.d(TAG, "POST_NOTIFICATIONS checkSelfPermission: " + permissionStatus + 
                       " (PERMISSION_GRANTED=" + PackageManager.PERMISSION_GRANTED + ")");
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission from system...");
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                    PERMISSION_REQUEST_NOTIFICATION);
            } else {
                Log.d(TAG, "Notification permission is already granted");
            }
        } else {
            Log.d(TAG, "Notification permission not required for Android < 13 (SDK " + Build.VERSION.SDK_INT + ")");
        }
    }
    
    private void setupModelSpinner() {
        try {
            // Restore custom models from persistent storage (for in-progress downloads)
            ModelConfig.restoreCustomModels(this);
            
            // Setup inline model selection
            List<ModelConfig.Model> models = ModelConfig.getAvailableModels(this);
            
            // Debug: Log the model names
            Log.d("MainActivity", "Number of models: " + models.size());
            for (int i = 0; i < models.size(); i++) {
                Log.d("MainActivity", "Model " + i + ": " + models.get(i).displayName);
            }
            
            // Setup RecyclerView for model selection & Hub
            if (recyclerModelSelection != null) {
                recyclerModelSelection.setLayoutManager(new LinearLayoutManager(this));
                modelListAdapter = new ModelListAdapter(models);
                recyclerModelSelection.setAdapter(modelListAdapter);
            }
            
            // Setup model indicator pill click listener (opens modern bottom sheet model switcher)
            if (selectedModelDisplay != null) {
                selectedModelDisplay.setOnClickListener(v -> {
                    performHapticFeedback(v);
                    showModelHubBottomSheet();
                });
            }
            
            // Setup close button
            if (btnCloseModelSelection != null) {
                btnCloseModelSelection.setOnClickListener(v -> {
                    performHapticFeedback(v);
                    hideInlineModelSelection();
                });
            }

            // Setup clear search button
            if (btnClearSearch != null) {
                btnClearSearch.setOnClickListener(v -> {
                    performHapticFeedback(v);
                    if (editSearchModel != null) {
                        editSearchModel.setText("");
                    }
                });
            }

            // Setup category filter chips
            if (chipFilterAll != null) {
                chipFilterAll.setSelected(true);
                chipFilterAll.setOnClickListener(v -> setModelCategoryFilter("all"));
            }
            if (chipFilterDownloaded != null) {
                chipFilterDownloaded.setOnClickListener(v -> setModelCategoryFilter("downloaded"));
            }
            if (chipFilterCompatible != null) {
                chipFilterCompatible.setOnClickListener(v -> setModelCategoryFilter("compatible"));
            }
            if (chipFilterCoding != null) {
                chipFilterCoding.setOnClickListener(v -> setModelCategoryFilter("coding"));
            }
            if (chipFilterCompact != null) {
                chipFilterCompact.setOnClickListener(v -> setModelCategoryFilter("compact"));
            }

            // Setup empty state reset button
            if (btnEmptyResetFilter != null) {
                btnEmptyResetFilter.setOnClickListener(v -> {
                    performHapticFeedback(v);
                    if (editSearchModel != null) {
                        editSearchModel.setText("");
                    }
                    setModelCategoryFilter("all");
                });
            }
            
            // Setup download from URL / import custom model button
            if (btnDownloadFromUrl != null) {
                btnDownloadFromUrl.setOnClickListener(v -> {
                    performHapticFeedback(v);
                    showCustomModelImportOptionsDialog();
                });
            }
            
            // Setup search functionality
            if (editSearchModel != null) {
                editSearchModel.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (btnClearSearch != null) {
                            btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                        }
                        applyModelFilters();
                    }
                    
                    @Override
                    public void afterTextChanged(android.text.Editable s) {}
                });
            }
            
            // Set initial state
            selectedModel = null;
            updateSelectedModelDisplay(null);
            Log.d("MainActivity", "Inline model selection setup complete");
            
        } catch (Exception e) {
            Log.e("MainActivity", "Error setting up model selection", e);
            Toast.makeText(this, "Error loading models: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setModelCategoryFilter(String category) {
        if (chipFilterAll != null) performHapticFeedback(chipFilterAll);
        activeCategoryFilter = category;
        if (chipFilterAll != null) chipFilterAll.setSelected("all".equalsIgnoreCase(category));
        if (chipFilterDownloaded != null) chipFilterDownloaded.setSelected("downloaded".equalsIgnoreCase(category));
        if (chipFilterCompatible != null) chipFilterCompatible.setSelected("compatible".equalsIgnoreCase(category));
        if (chipFilterCoding != null) chipFilterCoding.setSelected("coding".equalsIgnoreCase(category));
        if (chipFilterCompact != null) chipFilterCompact.setSelected("compact".equalsIgnoreCase(category));
        applyModelFilters();
    }

    private void applyModelFilters() {
        if (modelListAdapter != null) {
            String query = editSearchModel != null ? editSearchModel.getText().toString() : "";
            modelListAdapter.filter(query, activeCategoryFilter);
        }
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int resolveThemeColor(int attr) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        if (getTheme().resolveAttribute(attr, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return ContextCompat.getColor(this, typedValue.resourceId);
            }
            return typedValue.data;
        }
        return android.graphics.Color.GRAY;
    }

    private android.graphics.drawable.Drawable resolveThemeDrawable(int attr) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        if (getTheme().resolveAttribute(attr, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return ContextCompat.getDrawable(this, typedValue.resourceId);
            }
        }
        return null;
    }
    
    /**
     * Toggle the model selection dropdown visibility
     */
    private void toggleInlineModelSelection() {
        if (modelHubBottomSheetDialog != null && modelHubBottomSheetDialog.isShowing()) {
            hideInlineModelSelection();
        } else {
            showInlineModelSelection();
        }
    }
    
    /**
     * Show the model selection dropdown (opens the streamlined modal sheet)
     */
    private void showInlineModelSelection() {
        showModelHubBottomSheet();
    }
    
    /**
     * Hide the model selection dropdown
     */
    private void hideInlineModelSelection() {
        if (modelHubBottomSheetDialog != null && modelHubBottomSheetDialog.isShowing()) {
            modelHubBottomSheetDialog.dismiss();
        }
        if (editSearchModel != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(editSearchModel.getWindowToken(), 0);
            }
            editSearchModel.clearFocus();
        }
        if (iconDropdownArrow != null) {
            iconDropdownArrow.animate().rotation(0f).setDuration(200).start();
        }
    }
    
    private static final int REQUEST_CODE_PICK_GGUF = 3001;
    private static final int REQUEST_CODE_HUGGINGFACE_HUB = 3002;
    private com.google.android.material.bottomsheet.BottomSheetDialog modelHubBottomSheetDialog;
    private String activeBottomSheetCategoryFilter = "all";
    private EditText sheetSearchEdit;
    private TextView sheetChipAll;
    private TextView sheetChipDownloaded;
    private TextView sheetChipCompatible;
    private TextView sheetChipCoding;
    private TextView sheetChipCompact;

    /**
     * Display the reimagined Model Hub as a smooth Material Modal Bottom Sheet
     */
    private void showModelHubBottomSheet() {
        if (!isActivityValid()) return;

        if (modelHubBottomSheetDialog == null) {
            modelHubBottomSheetDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
            View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_model_hub, null);
            modelHubBottomSheetDialog.setContentView(sheetView);

            // Animate dropdown arrow when sheet opens/closes and expand behavior
            modelHubBottomSheetDialog.setOnShowListener(dialog -> {
                if (iconDropdownArrow != null) {
                    iconDropdownArrow.animate().rotation(180f).setDuration(220).start();
                }
                FrameLayout bottomSheet = modelHubBottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    com.google.android.material.bottomsheet.BottomSheetBehavior<FrameLayout> behavior =
                            com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                    behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                    behavior.setSkipCollapsed(true);
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;
                    int targetHeight = (int) (screenHeight * 0.85);
                    behavior.setPeekHeight(targetHeight);
                    bottomSheet.getLayoutParams().height = targetHeight;
                    bottomSheet.requestLayout();
                }
            });
            modelHubBottomSheetDialog.setOnDismissListener(dialog -> {
                if (iconDropdownArrow != null) {
                    iconDropdownArrow.animate().rotation(0f).setDuration(200).start();
                }
            });

            // Close button
            ImageButton btnClose = sheetView.findViewById(R.id.btn_close_model_selection);
            if (btnClose != null) {
                btnClose.setOnClickListener(v -> {
                    performHapticFeedback(v);
                    modelHubBottomSheetDialog.dismiss();
                });
            }

            // Search input
            sheetSearchEdit = sheetView.findViewById(R.id.edit_search_model);
            ImageButton btnClearSearchSheet = sheetView.findViewById(R.id.btn_clear_search);
            if (btnClearSearchSheet != null) {
                btnClearSearchSheet.setOnClickListener(v -> {
                    performHapticFeedback(v);
                    if (sheetSearchEdit != null) {
                        sheetSearchEdit.setText("");
                    }
                });
            }
            if (sheetSearchEdit != null) {
                sheetSearchEdit.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (btnClearSearchSheet != null) {
                            btnClearSearchSheet.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                        }
                        applySheetModelFilters();
                    }
                    @Override
                    public void afterTextChanged(android.text.Editable s) {}
                });
            }

            // Category filter chips
            sheetChipAll = sheetView.findViewById(R.id.chip_filter_all);
            sheetChipDownloaded = sheetView.findViewById(R.id.chip_filter_downloaded);
            sheetChipCompatible = sheetView.findViewById(R.id.chip_filter_compatible);
            sheetChipCoding = sheetView.findViewById(R.id.chip_filter_coding);
            sheetChipCompact = sheetView.findViewById(R.id.chip_filter_compact);

            if (sheetChipAll != null) {
                sheetChipAll.setOnClickListener(v -> setSheetCategoryFilter("all"));
            }
            if (sheetChipDownloaded != null) {
                sheetChipDownloaded.setOnClickListener(v -> setSheetCategoryFilter("downloaded"));
            }
            if (sheetChipCompatible != null) {
                sheetChipCompatible.setOnClickListener(v -> setSheetCategoryFilter("compatible"));
            }
            if (sheetChipCoding != null) {
                sheetChipCoding.setOnClickListener(v -> setSheetCategoryFilter("coding"));
            }
            if (sheetChipCompact != null) {
                sheetChipCompact.setOnClickListener(v -> setSheetCategoryFilter("compact"));
            }

            // Empty state reset button
            TextView btnReset = sheetView.findViewById(R.id.btn_empty_reset_filter);
            if (btnReset != null) {
                btnReset.setOnClickListener(v -> {
                    performHapticFeedback(v);
                    if (sheetSearchEdit != null) sheetSearchEdit.setText("");
                    setSheetCategoryFilter("all");
                });
            }

            // Pinned action: Import custom model
            View btnCustom = sheetView.findViewById(R.id.btn_download_from_url);
            if (btnCustom != null) {
                btnCustom.setOnClickListener(v -> {
                    performHapticFeedback(v);
                    modelHubBottomSheetDialog.dismiss();
                    showCustomModelImportOptionsDialog();
                });
            }
        }

        View sheetView = modelHubBottomSheetDialog.findViewById(R.id.bottom_sheet_root);
        if (sheetView != null) {
            // Update Hardware Telemetry & RAM Fit Advisor
            long availRamMb = ModelConfig.getAvailableRamMb(this);
            int cores = Runtime.getRuntime().availableProcessors();
            String ramStr = String.format(Locale.US, "%.1f GB Free RAM", availRamMb / 1024.0);

            TextView textTelemetry = sheetView.findViewById(R.id.text_device_telemetry);
            TextView textRamHint = sheetView.findViewById(R.id.text_ram_advisor_hint);
            TextView badgeMemory = sheetView.findViewById(R.id.badge_memory_status);
            TextView textCountBadge = sheetView.findViewById(R.id.text_model_count_badge);

            String fitHint = (availRamMb < 1200) ? "Optimal for ≤ 0.5B" : (availRamMb < 3200) ? "Optimal for ≤ 3B" : "All models supported";

            if (textTelemetry != null) {
                textTelemetry.setText("⚡ " + ramStr + " · " + fitHint);
            }
            if (badgeMemory != null) {
                if (availRamMb < 1200) {
                    if (textRamHint != null) textRamHint.setText("Models ≤ 0.5B recommended for your RAM");
                    badgeMemory.setText("Low RAM");
                    badgeMemory.setTextColor(getResources().getColor(R.color.warning_orange, getTheme()));
                } else if (availRamMb < 3200) {
                    if (textRamHint != null) textRamHint.setText("Models ≤ 3B recommended for optimal speed");
                    badgeMemory.setText("Normal RAM");
                    badgeMemory.setTextColor(getResources().getColor(R.color.green_700, getTheme()));
                } else {
                    if (textRamHint != null) textRamHint.setText("All models compatible with your hardware");
                    badgeMemory.setText("Flagship Ready");
                    badgeMemory.setTextColor(getResources().getColor(R.color.green_700, getTheme()));
                }
            }

            List<ModelConfig.Model> models = ModelConfig.getAvailableModels(this);
            if (textCountBadge != null) {
                textCountBadge.setText(String.valueOf(models.size()));
            }

            RecyclerView sheetRecycler = sheetView.findViewById(R.id.recycler_model_selection);
            if (sheetRecycler != null) {
                sheetRecycler.setLayoutManager(new LinearLayoutManager(this));
                if (modelListAdapter == null) {
                    modelListAdapter = new ModelListAdapter(models);
                } else {
                    modelListAdapter.updateModels(models);
                }
                if (selectedModel != null) {
                    modelListAdapter.setCurrentModel(selectedModel.name);
                }
                sheetRecycler.setAdapter(modelListAdapter);
            }

            setSheetCategoryFilter("all");
        }

        modelHubBottomSheetDialog.show();
    }

    private void setSheetCategoryFilter(String category) {
        if (sheetChipAll != null) performHapticFeedback(sheetChipAll);
        activeBottomSheetCategoryFilter = category;
        if (sheetChipAll != null) sheetChipAll.setSelected("all".equalsIgnoreCase(category));
        if (sheetChipDownloaded != null) sheetChipDownloaded.setSelected("downloaded".equalsIgnoreCase(category));
        if (sheetChipCompatible != null) sheetChipCompatible.setSelected("compatible".equalsIgnoreCase(category));
        if (sheetChipCoding != null) sheetChipCoding.setSelected("coding".equalsIgnoreCase(category));
        if (sheetChipCompact != null) sheetChipCompact.setSelected("compact".equalsIgnoreCase(category));
        applySheetModelFilters();
    }

    private void applySheetModelFilters() {
        if (modelListAdapter != null) {
            String query = sheetSearchEdit != null ? sheetSearchEdit.getText().toString() : "";
            modelListAdapter.filter(query, activeBottomSheetCategoryFilter);
        }
    }

    private void showCustomModelImportOptionsDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_import_model, null);
        bottomSheetDialog.setContentView(sheetView);

        FrameLayout bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundResource(android.R.color.transparent);
        }

        View cardHf = sheetView.findViewById(R.id.card_browse_huggingface);
        View cardStorage = sheetView.findViewById(R.id.card_import_from_storage);
        View cardUrl = sheetView.findViewById(R.id.card_download_from_url);
        View btnClose = sheetView.findViewById(R.id.btn_close_import_sheet);

        if (cardHf != null) {
            cardHf.setOnClickListener(v -> {
                performHapticFeedback(v);
                bottomSheetDialog.dismiss();
                openHuggingFaceHub();
            });
        }

        if (cardStorage != null) {
            cardStorage.setOnClickListener(v -> {
                performHapticFeedback(v);
                bottomSheetDialog.dismiss();
                openGgufFilePicker();
            });
        }

        if (cardUrl != null) {
            cardUrl.setOnClickListener(v -> {
                performHapticFeedback(v);
                bottomSheetDialog.dismiss();
                showCustomUrlDownloadDialog();
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                performHapticFeedback(v);
                bottomSheetDialog.dismiss();
            });
        }

        bottomSheetDialog.show();
    }

    private void openHuggingFaceHub() {
        try {
            Intent intent = new Intent(this, com.keralatechreach.mobigpt.huggingface.HuggingFaceHubActivity.class);
            startActivityForResult(intent, REQUEST_CODE_HUGGINGFACE_HUB);
        } catch (Exception e) {
            Log.e(TAG, "Error opening Hugging Face Hub", e);
            Toast.makeText(this, "Could not open Hugging Face Hub", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGgufFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_CODE_PICK_GGUF);
        } catch (Exception e) {
            Log.e(TAG, "Error opening document picker", e);
            Toast.makeText(this, "Could not open file picker", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleImportGgufFileUri(android.net.Uri uri) {
        if (uri == null) return;

        String tempFileName = "imported_model.gguf";
        long totalBytesTemp = -1;

        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    String name = cursor.getString(nameIndex);
                    if (name != null && !name.trim().isEmpty()) {
                        tempFileName = name.trim();
                    }
                }
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    totalBytesTemp = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not resolve file name/size from cursor", e);
        }

        if (!tempFileName.toLowerCase().endsWith(".gguf")) {
            tempFileName += ".gguf";
        }

        final String fileName = tempFileName;
        final long totalBytes = totalBytesTemp;

        // Inflate modern progress dialog layout
        View progressView = getLayoutInflater().inflate(R.layout.dialog_import_progress, null);
        TextView textFileName = progressView.findViewById(R.id.text_import_filename);
        ProgressBar progressBar = progressView.findViewById(R.id.progress_import);
        TextView textDetail = progressView.findViewById(R.id.text_import_progress_detail);
        com.google.android.material.button.MaterialButton btnCancel = progressView.findViewById(R.id.btn_cancel_import);

        if (textFileName != null) {
            textFileName.setText(fileName);
        }
        if (progressBar != null) {
            if (totalBytes > 0) {
                progressBar.setIndeterminate(false);
                progressBar.setMax(100);
                progressBar.setProgress(0);
            } else {
                progressBar.setIndeterminate(true);
            }
        }
        if (textDetail != null) {
            textDetail.setText(totalBytes > 0 
                ? "0.0 MB / " + String.format(Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0)) + " (0%)"
                : "Transferring file to app storage...");
        }

        AlertDialog progressDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(progressView)
                .setCancelable(false)
                .create();

        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }

        java.util.concurrent.atomic.AtomicBoolean isCancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                performHapticFeedback(v);
                isCancelled.set(true);
                progressDialog.dismiss();
                Toast.makeText(this, "Import cancelled", Toast.LENGTH_SHORT).show();
            });
        }

        progressDialog.show();

        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            File destFile = null;
            try {
                File modelsDir = ModelConfig.getModelsDirectory(this);
                if (!modelsDir.exists()) {
                    modelsDir.mkdirs();
                }

                destFile = new File(modelsDir, fileName);
                try (java.io.InputStream is = getContentResolver().openInputStream(uri);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile)) {

                    if (is == null) {
                        throw new java.io.IOException("Unable to open input stream for selected file");
                    }

                    byte[] buffer = new byte[131072]; // High throughput 128KB buffer
                    int len;
                    long bytesCopied = 0;
                    long lastUpdateTimestamp = 0;

                    while ((len = is.read(buffer)) > 0) {
                        if (isCancelled.get()) {
                            if (destFile.exists()) {
                                destFile.delete();
                            }
                            return;
                        }
                        fos.write(buffer, 0, len);
                        bytesCopied += len;

                        long now = System.currentTimeMillis();
                        if (now - lastUpdateTimestamp > 150) {
                            lastUpdateTimestamp = now;
                            long currentCopied = bytesCopied;
                            runOnUiThread(() -> {
                                if (progressDialog.isShowing()) {
                                    if (totalBytes > 0) {
                                        int percent = (int) Math.min(100, (currentCopied * 100) / totalBytes);
                                        if (progressBar != null) progressBar.setProgress(percent);
                                        if (textDetail != null) {
                                            String copiedMb = String.format(Locale.US, "%.1f MB", currentCopied / (1024.0 * 1024.0));
                                            String totalMb = String.format(Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0));
                                            textDetail.setText(copiedMb + " / " + totalMb + " (" + percent + "%)");
                                        }
                                    } else {
                                        if (textDetail != null) {
                                            String copiedMb = String.format(Locale.US, "%.1f MB transferred", currentCopied / (1024.0 * 1024.0));
                                            textDetail.setText(copiedMb);
                                        }
                                    }
                                }
                            });
                        }
                    }
                    fos.flush();

                    if (isCancelled.get()) {
                        if (destFile.exists()) {
                            destFile.delete();
                        }
                        return;
                    }

                    long finalSize = destFile.length();
                    ModelConfig.Model importedModel = ModelConfig.Model.createLocalImportedModel(fileName, finalSize);
                    ModelConfig.addCustomModel(importedModel);
                    ModelConfig.saveCustomModel(this, importedModel);

                    runOnUiThread(() -> {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        refreshModelSpinner();

                        StringBuilder metaDetails = new StringBuilder();
                        metaDetails.append(importedModel.displayName).append("\n\n");
                        metaDetails.append("• Size: ").append(importedModel.getFormattedFileSize()).append("\n");
                        if (importedModel.quantization != null && !"Unknown".equalsIgnoreCase(importedModel.quantization)) {
                            metaDetails.append("• Quantization: ").append(importedModel.quantization).append("\n");
                        }
                        if (importedModel.parameterCount != null && !"Unknown".equalsIgnoreCase(importedModel.parameterCount)) {
                            metaDetails.append("• Parameters: ").append(importedModel.parameterCount).append("\n");
                        }
                        metaDetails.append("\nWould you like to load this model into chat now?");

                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                .setTitle("Model Imported Successfully")
                                .setMessage(metaDetails.toString())
                                .setPositiveButton("Load Model Now", (d, w) -> {
                                    loadModel(importedModel);
                                    hideInlineModelSelection();
                                    updateSelectedModelDisplay(importedModel);
                                    if (currentTab == TAB_MODELS) {
                                        switchToTab(TAB_CHAT);
                                    }
                                })
                                .setNegativeButton("Later", (d, w) -> {
                                    selectedModel = importedModel;
                                    updateSelectedModelDisplay(importedModel);
                                    showModelHubBottomSheet();
                                })
                                .show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to import GGUF file", e);
                if (destFile != null && destFile.exists() && isCancelled.get()) {
                    destFile.delete();
                }
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    if (!isCancelled.get()) {
                        Toast.makeText(this, "Failed to import file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    /**
     * Update the selected model display in the chat toolbar dropdown trigger
     */
    private void updateSelectedModelDisplay(ModelConfig.Model model) {
        if (textSelectedModelName != null && textSelectedModelDetails != null) {
            if (model != null) {
                textSelectedModelName.setText(model.displayName);

                // Update brand monogram avatar or fallback icon
                if (textChatModelAvatar != null && mainChatIcon != null) {
                    String initial = model.getFamilyInitial();
                    textChatModelAvatar.setText(initial);
                    textChatModelAvatar.setVisibility(View.VISIBLE);
                    mainChatIcon.setVisibility(View.GONE);
                }

                // Avoid repeating parameter size if already part of displayName
                boolean hasParamInTitle = model.parameterCount != null && model.displayName != null
                        && model.displayName.toUpperCase().contains(model.parameterCount.toUpperCase());
                String paramStr = (!hasParamInTitle && model.parameterCount != null 
                        && !model.parameterCount.isEmpty() && !"Unknown".equalsIgnoreCase(model.parameterCount))
                        ? model.parameterCount + " · " : "";

                String quantStr = (model.quantization != null && !model.quantization.isEmpty()) ? model.quantization : "";
                if (quantStr.equalsIgnoreCase("Q4_K_M") || quantStr.equalsIgnoreCase("q4_k_m")) {
                    quantStr = "Q4";
                }

                StringBuilder specs = new StringBuilder();
                if (!paramStr.isEmpty()) specs.append(paramStr);
                specs.append(model.getFormattedFileSize());
                if (!quantStr.isEmpty()) specs.append(" · ").append(quantStr);

                boolean isLoaded = model.name.equals(loadedModelName);
                boolean isDownloaded = downloadManager != null && downloadManager.isModelDownloaded(model);

                if (isLoaded) {
                    specs.append(" · 100% Offline");
                    if (textSelectedModelStatus != null) {
                        textSelectedModelStatus.setText("● Loaded");
                        textSelectedModelStatus.setTextColor(getResources().getColor(R.color.green_700, getTheme()));
                        textSelectedModelStatus.setBackground(null);
                        textSelectedModelStatus.setPadding(0, 0, 0, 0);
                        textSelectedModelStatus.setVisibility(View.VISIBLE);
                    }
                } else if (isDownloaded) {
                    specs.append(" · Ready");
                    if (textSelectedModelStatus != null) {
                        textSelectedModelStatus.setText("✓ Ready");
                        textSelectedModelStatus.setTextColor(getResources().getColor(R.color.green_700, getTheme()));
                        textSelectedModelStatus.setBackground(null);
                        textSelectedModelStatus.setPadding(0, 0, 0, 0);
                        textSelectedModelStatus.setVisibility(View.VISIBLE);
                    }
                } else {
                    specs.append(" · Tap to Download");
                    if (textSelectedModelStatus != null) {
                        textSelectedModelStatus.setText("Available");
                        textSelectedModelStatus.setTextColor(resolveThemeColor(R.attr.textColorSecondary));
                        textSelectedModelStatus.setBackground(null);
                        textSelectedModelStatus.setPadding(0, 0, 0, 0);
                        textSelectedModelStatus.setVisibility(View.VISIBLE);
                    }
                }
                textSelectedModelDetails.setText(specs.toString());
                textSelectedModelDetails.setVisibility(View.VISIBLE);
            } else {
                if (textChatModelAvatar != null && mainChatIcon != null) {
                    textChatModelAvatar.setVisibility(View.GONE);
                    mainChatIcon.setVisibility(View.VISIBLE);
                    mainChatIcon.setImageResource(R.drawable.ic_chat_bubble);
                }
                textSelectedModelName.setText("Select AI Model");
                textSelectedModelDetails.setText("Tap to choose a local LLM");
                textSelectedModelDetails.setVisibility(View.VISIBLE);
                if (textSelectedModelStatus != null) {
                    textSelectedModelStatus.setVisibility(View.GONE);
                }
            }
            updateDrawerModelStatus();
        }
    }
    
    /**
     * Refresh model list to include newly downloaded custom models
     * Call this after a custom model download completes
     */
    private void refreshModelSpinner() {
        try {
            Log.d("MainActivity", "Refreshing model list to include custom models");
            
            // Get updated model list (includes newly downloaded custom models)
            List<ModelConfig.Model> models = ModelConfig.getAvailableModels(this);
            Log.d("MainActivity", "Total models after refresh: " + models.size());
            
            // Update model list adapter
            if (modelListAdapter != null) {
                modelListAdapter.updateModels(models);
                
                // Update current model selection
                if (selectedModel != null) {
                    modelListAdapter.setCurrentModel(selectedModel.name);
                }
            }
            
            // Update selected model display
            updateSelectedModelDisplay(selectedModel);
            
            Log.d("MainActivity", "Model list refreshed successfully");
            
        } catch (Exception e) {
            Log.e("MainActivity", "Error refreshing model list", e);
        }
    }
    
    /**
     * Simple, robust model loader - loads the selected model
     * Only loads if model is downloaded and different from currently loaded
     * Uses isLoadingModel flag to prevent duplicate/concurrent loading
     */
    private void loadModel(ModelConfig.Model model) {
        if (model == null) {
            Log.w(TAG, "Cannot load null model");
            return;
        }
        
        // Defensive null check for chatManager
        if (chatManager == null) {
            Log.e(TAG, "ChatManager is null, cannot load model: " + model.displayName);
            Toast.makeText(this, "App not ready yet. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        selectedModel = model;
        
        // Update UI to show selected model in inline dropdown
        updateSelectedModelDisplay(model);
        
        // Update model list adapter if it exists
        if (modelListAdapter != null) {
            modelListAdapter.setCurrentModel(model.name);
        }
        

        
        // Check if model is downloaded
        if (!downloadManager.isModelDownloaded(model)) {
            if (downloadManager.isModelDownloadPaused(model)) {
                showPausedDownloadIndicator(model.name);
            } else {
                Toast.makeText(this, model.displayName + " is not downloaded. Tap download icon to get it.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        
        // Skip if this exact model is already loaded
        if (model.name.equals(loadedModelName)) {
            Log.d(TAG, "Model already loaded: " + model.displayName);
            Toast.makeText(this, model.displayName + " is ready", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Skip if a model is currently being loaded (prevent duplicate loading)
        if (isLoadingModel) {
            Log.d(TAG, "Model loading already in progress, ignoring duplicate request for: " + model.displayName);
            return;
        }
        
        // Mark that we're loading a model
        isLoadingModel = true;
        
        // Load the model
        Log.d(TAG, "Loading model: " + model.displayName);
        chatManager.loadModelEagerly(model, new ChatManager.ModelLoadCallback() {
            @Override
            public void onModelLoaded(long loadDuration) {
                runOnUiThread(() -> {
                    loadedModelName = model.name;
                    isLoadingModel = false; // Clear loading flag
                    updateSelectedModelDisplay(model);
                    if (modelListAdapter != null) {
                        modelListAdapter.setCurrentModel(model.name);
                    }
                    String durationStr = loadDuration < 1000 ? loadDuration + "ms" : String.format("%.1fs", loadDuration / 1000.0);
                    Toast.makeText(MainActivity.this, 
                        model.displayName + " loaded (" + durationStr + ")", 
                        Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onModelLoadFailed(String error) {
                runOnUiThread(() -> {
                    loadedModelName = null;
                    isLoadingModel = false; // Clear loading flag
                    updateSelectedModelDisplay(model);
                    Toast.makeText(MainActivity.this, 
                        "Failed to load " + model.displayName + ": " + error, 
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    @SuppressLint("InvalidSetHasFixedSize")
    private void setupRecyclerViews() {
        // Messages RecyclerView optimizations
        messageAdapter = new MessageAdapter(this);
        messageAdapter.setOnMessageActionListener(this); // Set the listener for message actions
        LinearLayoutManager messagesLayoutManager = new LinearLayoutManager(this);
        
        // Enable layout optimization
        messagesLayoutManager.setStackFromEnd(true);
        messagesLayoutManager.setReverseLayout(false);
        
        recyclerMessages.setLayoutManager(messagesLayoutManager);
        recyclerMessages.setAdapter(messageAdapter);
        
        // Enhanced performance optimizations
        recyclerMessages.setHasFixedSize(true);
        recyclerMessages.setItemViewCacheSize(Constants.RECYCLERVIEW_CACHE_SIZE);
        recyclerMessages.setItemAnimator(null); // Disable animations for better performance
        
        // Configure recycled view pool
        recyclerMessages.getRecycledViewPool().setMaxRecycledViews(
            Constants.VIEW_TYPE_USER_MESSAGE, Constants.RECYCLERVIEW_POOL_SIZE);
        recyclerMessages.getRecycledViewPool().setMaxRecycledViews(
            Constants.VIEW_TYPE_AI_MESSAGE, Constants.RECYCLERVIEW_POOL_SIZE);
        
        // Setup swipe gestures for messages
        setupMessageSwipeGestures();
        
        // Add scroll listener for smart auto-scroll detection
        // Only triggers on USER-initiated scrolls, not programmatic ones
        recyclerMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                
                // Only check when user finishes dragging (SCROLL_STATE_IDLE)
                // This prevents constant updates during scroll animations
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // Ignore if this was a programmatic scroll
                    if (isProgrammaticScroll) {
                        isProgrammaticScroll = false;
                        return;
                    }
                    
                    // Check if user is at bottom or scrolled up
                    checkAndUpdateScrollState();
                    
                    // If user has scrolled up, capture their current position as anchor
                    if (isUserScrolledUp) {
                        captureScrollAnchor();
                    }
                }
            }
            
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // Ignore programmatic scrolls
                if (isProgrammaticScroll) {
                    return;
                }
                
                // Only check for upward user scrolls (dy < 0 means scrolling up)
                if (dy < 0 && !isUserScrolledUp) {
                    // User is actively scrolling UP - disable auto-scroll immediately
                    isUserScrolledUp = true;
                    showScrollToBottomButton();
                    Log.d(TAG, "User scrolled up - auto-scroll disabled");
                }
                
                // Load more messages when scrolled to top
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && layoutManager.findFirstVisibleItemPosition() == 0 && dy < 0) {
                    loadMoreMessages();
                }
            }
        });
        
        // Chat History RecyclerView
        chatHistoryAdapter = new ChatHistoryAdapter(this);
        chatHistoryAdapter.setOnChatClickListener(this);
        LinearLayoutManager historyLayoutManager = new LinearLayoutManager(this);
        recyclerChatHistory.setLayoutManager(historyLayoutManager);
        recyclerChatHistory.setAdapter(chatHistoryAdapter);
        
        // Optimize Chat History RecyclerView performance inside NestedScrollView
        recyclerChatHistory.setHasFixedSize(false);
        recyclerChatHistory.setNestedScrollingEnabled(false);
        recyclerChatHistory.setItemViewCacheSize(15);
        recyclerChatHistory.setItemAnimator(null); // Disable animations for smoother scrolling

        // Log to verify setup
        Log.d(TAG, "Chat history RecyclerView setup complete with adapter and click listener");
    }
    
    /**
     * Setup swipe gestures for messages
     * - Swipe right: Star/unstar message
     * - Swipe left: Delete message
     */
    private void setupMessageSwipeGestures() {
        MessageSwipeCallback swipeCallback = new MessageSwipeCallback(this, new MessageSwipeCallback.SwipeActionListener() {
            @Override
            public void onSwipeToStar(int position) {
                // Get the message at this position
                if (messageAdapter != null && position >= 0 && position < messageAdapter.getItemCount()) {
                    List<Message> messages = messageAdapter.getMessages();
                    if (position < messages.size()) {
                        Message message = messages.get(position);
                        
                        // Toggle starred status
                        boolean newStarredState = !message.isStarred;
                        message.isStarred = newStarredState;
                        
                        // Update in database
                        if (chatManager != null) {
                            chatManager.updateMessageInDatabase(message);
                        }
                        
                        // Update UI
                        messageAdapter.notifyItemChanged(position);
                        
                        // Show feedback
                        String feedback = newStarredState ? "Message starred ⭐" : "Star removed";
                        Toast.makeText(MainActivity.this, feedback, Toast.LENGTH_SHORT).show();
                        
                        Log.d(TAG, "Swipe to star: position=" + position + ", starred=" + newStarredState);
                    }
                }
            }
            
            @Override
            public void onSwipeToDelete(int position) {
                // Get the message at this position
                if (messageAdapter != null && position >= 0 && position < messageAdapter.getItemCount()) {
                    List<Message> messages = messageAdapter.getMessages();
                    if (position < messages.size()) {
                        Message message = messages.get(position);
                        
                        // Remove message from adapter and database
                        messageAdapter.removeMessage(message);
                        
                        // Show feedback with undo option
                        Toast.makeText(MainActivity.this, "Message deleted", Toast.LENGTH_SHORT).show();
                        
                        Log.d(TAG, "Swipe to delete: position=" + position + ", messageId=" + message.id);
                    }
                }
            }
        });
        
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
        itemTouchHelper.attachToRecyclerView(recyclerMessages);
        
        Log.d(TAG, "Message swipe gestures enabled (right=star, left=delete)");
    }
    
    /**
     * Load more messages for pagination
     */
    private void loadMoreMessages() {
        // Implementation for loading more messages
        if (chatManager != null) {
            long currentChatId = chatManager.getCurrentChatId();
            if (currentChatId != -1) {
                // This would use the new paginated DAO methods
                Log.d("MainActivity", "Loading more messages for chat: " + currentChatId);
            }
        }
    }
    
    /**
     * Check if user is at the bottom of the RecyclerView
     */
    private boolean isAtBottom() {
        if (recyclerMessages == null || messageAdapter == null) return false;
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerMessages.getLayoutManager();
        if (layoutManager == null) return false;
        
        int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
        int lastItemPosition = messageAdapter.getItemCount() - 1;
        
        // Consider "at bottom" if viewing the last item
        // Small tolerance for partial visibility
        return lastVisiblePosition >= lastItemPosition;
    }
    
    /**
     * Check and update scroll state when user stops scrolling
     * Only called when scroll becomes idle (not on every frame)
     */
    private void checkAndUpdateScrollState() {
        boolean atBottom = isAtBottom();
        
        if (atBottom && isUserScrolledUp) {
            // User scrolled back to bottom - re-enable auto-scroll
            isUserScrolledUp = false;
            clearScrollAnchor(); // Clear anchor since we're at bottom
            hideScrollToBottomButton();
            Log.d(TAG, "User returned to bottom - auto-scroll re-enabled");
        }
        // Note: We handle the "scrolled up" case immediately in onScrolled()
        // when dy < 0, so we don't need to check it here
    }
    
    
    /**
     * Capture the current scroll position as an anchor point
     * This prevents content from shifting when new items are added below
     */
    private void captureScrollAnchor() {
        if (recyclerMessages == null) return;
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerMessages.getLayoutManager();
        if (layoutManager == null) return;
        
        // Get the first fully visible item as anchor
        int firstVisiblePos = layoutManager.findFirstVisibleItemPosition();
        if (firstVisiblePos != RecyclerView.NO_POSITION) {
            View firstVisibleView = layoutManager.findViewByPosition(firstVisiblePos);
            if (firstVisibleView != null) {
                anchoredPosition = firstVisiblePos;
                anchoredOffset = firstVisibleView.getTop();
                Log.d(TAG, "Scroll anchor captured: position=" + anchoredPosition + ", offset=" + anchoredOffset);
            }
        }
    }
    
    /**
     * Restore the scroll anchor position after content changes
     * This maintains the user's reading position even when new content is added
     */
    private void restoreScrollAnchor() {
        if (recyclerMessages == null || anchoredPosition == -1) return;
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerMessages.getLayoutManager();
        if (layoutManager == null) return;
        
        // Restore the anchored position with the same offset
        layoutManager.scrollToPositionWithOffset(anchoredPosition, anchoredOffset);
        Log.d(TAG, "Scroll anchor restored: position=" + anchoredPosition + ", offset=" + anchoredOffset);
    }
    
    /**
     * Clear the scroll anchor (called when user returns to bottom)
     */
    private void clearScrollAnchor() {
        anchoredPosition = -1;
        anchoredOffset = 0;
    }
    
    /**
     * Show the scroll-to-bottom button
     */
    private void showScrollToBottomButton() {
        if (fabScrollToBottom != null && !fabScrollToBottom.isShown() && messageAdapter.getItemCount() > 0) {
            fabScrollToBottom.show();
        }
    }
    
    /**
     * Hide the scroll-to-bottom button
     */
    private void hideScrollToBottomButton() {
        if (fabScrollToBottom != null && fabScrollToBottom.isShown()) {
            fabScrollToBottom.hide();
        }
    }
    
    /**
     * Scroll to the bottom of the chat
     * @param smooth Whether to use smooth scrolling
     */
    private void scrollToBottom(boolean smooth) {
        if (recyclerMessages == null || messageAdapter == null) return;
        
        int itemCount = messageAdapter.getItemCount();
        if (itemCount > 0) {
            // Mark this as a programmatic scroll so the listener ignores it
            isProgrammaticScroll = true;
            
            if (smooth) {
                recyclerMessages.smoothScrollToPosition(itemCount - 1);
            } else {
                recyclerMessages.scrollToPosition(itemCount - 1);
            }
            
            // Reset scroll state, clear anchor, and hide FAB
            isUserScrolledUp = false;
            clearScrollAnchor();
            hideScrollToBottomButton();
        }
    }
    
    private void setupListeners() {
        // Initialize send button state
        updateSendButtonState(editMessage != null ? editMessage.getText().toString().trim() : "");
        
        // Add text watcher to reactively update send button appearance
        if (editMessage != null) {
            editMessage.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateSendButtonState(s != null ? s.toString().trim() : "");
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        btnSend.setOnClickListener(v -> {
            // Haptic feedback on button press
            performHapticFeedback(v);
            
            // Null check for chatManager
            if (chatManager == null) {
                Toast.makeText(this, "App not ready. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Check if currently generating - if so, stop inference
            if (chatManager.isGenerating()) {
                chatManager.stopInference();
                return;
            }
            
            // Validate message text
            String messageText = editMessage.getText().toString().trim();
            if (messageText.isEmpty()) {
                return;
            }
            
            // Validate model is selected
            if (selectedModel == null) {
                Toast.makeText(this, "Please select a model first", Toast.LENGTH_SHORT).show();
                showInlineModelSelection();
                return;
            }
            
            // Null check for downloadManager
            if (downloadManager == null) {
                Toast.makeText(this, "Download manager not ready. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Validate model is downloaded
            if (!downloadManager.isModelDownloaded(selectedModel)) {
                Toast.makeText(this, "Model not downloaded. Please download " + selectedModel.displayName + " first.", Toast.LENGTH_SHORT).show();
                showInlineModelSelection();
                return;
            }
            
            // Validate model is loaded
            if (!selectedModel.name.equals(loadedModelName)) {
                Toast.makeText(this, "Model not loaded. Please wait for model to load.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Send message
            chatManager.handleSendMessage(messageText, selectedModel);
        });
        btnNewChat.setOnClickListener(v -> {
            // Haptic feedback on button press
            performHapticFeedback(v);
            
            // Null check for chatManager
            if (chatManager == null) {
                Toast.makeText(this, "App not ready. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            chatManager.handleStartNewChat();
        });
        
        // Scroll to bottom FAB click listener
        fabScrollToBottom.setOnClickListener(v -> {
            // Haptic feedback on button press
            performHapticFeedback(v);
            scrollToBottom(true);
        });
        
        // Add a way to open the drawer if the hamburger menu isn't visible
        TextView toolbarTitle = findViewById(R.id.toolbar_title);
        if (toolbarTitle != null) {
            toolbarTitle.setOnLongClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(findViewById(R.id.nav_view));
                    Toast.makeText(this, "Chat History", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
        }
        
        editMessage.setOnEditorActionListener((v, actionId, event) -> {
            // Null check for chatManager
            if (chatManager == null) {
                Toast.makeText(this, "App not ready. Please try again.", Toast.LENGTH_SHORT).show();
                return true;
            }
            
            String messageText = editMessage.getText().toString().trim();
            if (selectedModel == null) {
                Toast.makeText(this, "Please select a model first", Toast.LENGTH_SHORT).show();
                return true;
            }
            
            // Null check for downloadManager
            if (downloadManager == null) {
                Toast.makeText(this, "Download manager not ready. Please try again.", Toast.LENGTH_SHORT).show();
                return true;
            }
            
            // Check if the selected model is downloaded
            if (!downloadManager.isModelDownloaded(selectedModel)) {
                Toast.makeText(this, "Model not downloaded. Please download " + selectedModel.displayName + " first.", Toast.LENGTH_SHORT).show();
                return true;
            }
            
            chatManager.handleSendMessage(messageText, selectedModel);
            return true;
        });
        
        // Cancel download button
        btnCancelDownload.setOnClickListener(v -> {
            // Haptic feedback on button press
            performHapticFeedback(v);
            
            if (downloadManager != null) {
                downloadManager.cancelDownload();
                hideDownloadProgress();
                Toast.makeText(this, "Download cancelled", Toast.LENGTH_SHORT).show();
                
                if (modelListAdapter != null) {
                    modelListAdapter.notifyDataSetChanged();
                }
            }
        });
        
        // Pause/Resume download button
        btnPauseResumeDownload.setOnClickListener(v -> {
            // Haptic feedback on button press
            performHapticFeedback(v);
            
            if (downloadManager == null) {
                Log.e(TAG, "Download manager is null");
                Toast.makeText(this, "Download manager not initialized", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Log.d(TAG, "Pause/Resume button clicked");
            Log.d(TAG, "Selected model: " + (selectedModel != null ? selectedModel.displayName : "null"));
            
            // First clean up any completed downloads
            downloadManager.cleanupCompletedDownloads();
            
            // Check if there's an active download
            boolean hasActiveDownload = downloadManager.hasActiveDownload();
            boolean hasPausedDownload = downloadManager.hasPausedDownloads();
            
            Log.d(TAG, "Has active download: " + hasActiveDownload);
            Log.d(TAG, "Has paused downloads: " + hasPausedDownload);
            
            if (hasActiveDownload) {
                // There's an active download - pause it
                String activeModelName = downloadManager.getActiveDownloadModelName();
                if (activeModelName != null) {
                    Log.d(TAG, "Pausing active download: " + activeModelName);
                    downloadManager.pauseDownload(activeModelName);
                    
                    // Update button text immediately
                    btnPauseResumeDownload.setText("Resume");
                    btnPauseResumeDownload.setEnabled(true);
                    
                    Toast.makeText(this, "Download paused", Toast.LENGTH_SHORT).show();
                } else {
                    Log.w(TAG, "Active download found but model name is null");
                    Toast.makeText(this, "Error: Cannot identify active download", Toast.LENGTH_SHORT).show();
                }
            } else if (hasPausedDownload) {
                // There's a paused download - check WiFi requirement before resuming
                String pausedModelName = downloadManager.getPausedDownloadModelName();
                if (pausedModelName != null) {
                    
                    // Check WiFi requirement before resuming
                    if (checkWiFiRequirementForDownload()) {
                        Log.d(TAG, "Resuming paused download: " + pausedModelName);
                        downloadManager.resumeDownload(pausedModelName);
                        
                        // Update button text immediately
                        btnPauseResumeDownload.setText("Pause");
                        btnPauseResumeDownload.setEnabled(true);
                        
                        Toast.makeText(this, "Download resumed", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.d(TAG, "Cannot resume download - WiFi requirement not met");
                        // The WiFi check method will show appropriate dialog, so no additional message needed here
                    }
                } else {
                    Log.w(TAG, "Paused download found but model name is null");
                    Toast.makeText(this, "Error: Cannot identify paused download", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "No download to pause or resume");
                Toast.makeText(this, "No download to pause or resume", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void initializeAI() {
        // Initialize AI engine on background thread
        new Thread(() -> {
            try {
                // Initialize the AI engine via ChatManager which handles the complexity
                Log.d(TAG, "AI engine initialization started");
                
                runOnUiThread(() -> {
                    Log.d(TAG, "AI engine ready for use");
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize AI engine", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "AI initialization failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private void showEmptyState() {
        emptyState.setVisibility(View.VISIBLE);
        recyclerMessages.setVisibility(View.GONE);
        
        // Ensure suggestion chips are visible when showing empty state
        View emptyStateView = findViewById(R.id.empty_state);
        if (emptyStateView != null) {
            LinearLayout suggestionsContainer = emptyStateView.findViewById(R.id.suggestions_container);
            if (suggestionsContainer != null && suggestionsContainer.getVisibility() != View.VISIBLE) {
                suggestionsContainer.setVisibility(View.VISIBLE);
            }
            
            // Re-setup chips if they're not present
            LinearLayout row1 = emptyStateView.findViewById(R.id.suggestion_row_1);
            if (row1 != null && row1.getChildCount() == 0) {
                // Chips were removed, re-setup them
                setupSuggestionChips();
            }
        }
    }
    
    private void hideEmptyState() {
        emptyState.setVisibility(View.GONE);
        recyclerMessages.setVisibility(View.VISIBLE);
    }
    
    /**
     * Change send button to stop button during inference
     */
    private void setSendButtonToStop() {
        if (btnSend != null) {
            btnSend.setImageResource(R.drawable.ic_stop);
            
            // Apply theme-aware stop button background (red color)
            android.graphics.drawable.GradientDrawable stopBackground = new android.graphics.drawable.GradientDrawable();
            stopBackground.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            stopBackground.setColor(0xFFE53E3E); // Red color for stop
            btnSend.setBackground(stopBackground);
            
            // Ensure icon is white for good contrast
            androidx.core.widget.ImageViewCompat.setImageTintList(btnSend, 
                android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            
            btnSend.setAlpha(1.0f);
            btnSend.setContentDescription("Stop");
        }
    }
    
    /**
     * Change stop button back to send button after inference
     */
    private void setSendButtonToSend() {
        if (btnSend != null) {
            btnSend.setImageResource(R.drawable.ic_send);
            
            // Apply theme-aware send button background using the ThemeColorUpdater
            com.keralatechreach.mobigpt.utils.ThemeColorUpdater themeUpdater = 
                new com.keralatechreach.mobigpt.utils.ThemeColorUpdater(this);
            themeUpdater.updateViewHierarchy(btnSend);
            
            updateSendButtonState(editMessage != null ? editMessage.getText().toString().trim() : "");
            btnSend.setContentDescription("Send");
        }
    }
    
    /**
     * Update send button opacity and reactivity based on whether text is entered
     */
    private void updateSendButtonState(String text) {
        if (btnSend == null) return;
        if (chatManager != null && chatManager.isGenerating()) {
            btnSend.setAlpha(1.0f);
            return;
        }
        boolean hasText = text != null && !text.isEmpty();
        btnSend.animate().alpha(hasText ? 1.0f : 0.45f).setDuration(150).start();
    }
    
    private void updateDownloadButton() {
        if (modelListAdapter != null) {
            modelListAdapter.notifyDataSetChanged();
        }
    }
    
    // Model action handlers
    public void onDownloadModel(ModelConfig.Model model) {
        selectedModel = model;
        downloadModel();
    }
    
    public void onDeleteModel(ModelConfig.Model model) {
        if (model == null) return;
        
        new AlertDialog.Builder(this)
            .setTitle("Delete Model?")
            .setMessage("Are you sure you want to delete " + model.displayName + " (" + model.getFormattedFileSize() + ") from device storage?\n\nYou will need to download it again to use it.")
            .setIcon(R.drawable.ic_delete)
            .setPositiveButton("Delete", (dialog, which) -> {
                selectedModel = model;
                // Clear loaded model name if deleting the currently loaded model
                if (model.name.equals(loadedModelName)) {
                    loadedModelName = null;
                }
                deleteModel();
                updateSelectedModelDisplay(selectedModel);
            })
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .show();
    }
    

    
    private void downloadModel() {
        if (selectedModel == null) {
            Toast.makeText(this, "Please select a model first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check if model is already downloaded (e.g., custom models discovered from filesystem)
        if (downloadManager.isModelDownloaded(selectedModel)) {
            Toast.makeText(this, selectedModel.displayName + " is already downloaded", Toast.LENGTH_SHORT).show();
            Log.d("MainActivity", "Model already downloaded, skipping: " + selectedModel.displayName);
            return;
        }
        
        // Check if download URL is empty (shouldn't happen for normal downloads)
        if (selectedModel.downloadUrl == null || selectedModel.downloadUrl.trim().isEmpty()) {
            Toast.makeText(this, "Cannot download: No URL available for " + selectedModel.displayName, Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "Cannot download model with empty URL: " + selectedModel.displayName);
            return;
        }
        
        Log.d("MainActivity", "Attempting to download model: " + selectedModel.displayName);
        
        // Request notification permission for Android 13+ when user initiates download
        requestNotificationPermission();
        
        // Check storage permissions for Android 9 and below (API <= 28)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                // Request permission
                Log.d("MainActivity", "Requesting storage permission...");
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 
                    PERMISSION_REQUEST_STORAGE);
                return;
            }
        }
        
        // Reset WiFi requirement override for new downloads (clean state)
        wiFiRequirementOverridden = false;
        if (downloadManager != null) {
            downloadManager.clearWiFiRequirementOverride();
        }
        
        // Check WiFi requirement before download
        if (!checkWiFiRequirementForDownload()) {
            return; // User chose to cancel or wait for WiFi
        }
        
        // Continue with the actual download
        continueDownloadAfterWiFiCheck();
    }
    
    /**
     * Check WiFi requirement for downloads based on user settings
     * @return true if download can proceed, false if user chose to cancel or wait
     */
    private boolean checkWiFiRequirementForDownload() {
        // If user has explicitly overridden WiFi requirement, allow download
        if (wiFiRequirementOverridden) {
            Log.d("MainActivity", "WiFi requirement overridden by user - allowing download on any network");
            return true;
        }
        
        // Check if WiFi-only downloads is enabled in settings
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean wifiOnlyEnabled = prefs.getBoolean("wifi_only_downloads", true);
        
        Log.d("MainActivity", "WiFi-only downloads enabled: " + wifiOnlyEnabled);
        
        // If WiFi-only is disabled, allow download on any network
        if (!wifiOnlyEnabled) {
            Log.d("MainActivity", "WiFi-only disabled, allowing download");
            return true;
        }
        
        // Check if WiFi is connected
        boolean isWiFiConnected = NetworkUtils.isWiFiConnected(this);
        boolean isNetworkAvailable = NetworkUtils.isNetworkAvailable(this);
        
        Log.d("MainActivity", "WiFi connected: " + isWiFiConnected + ", Network available: " + isNetworkAvailable);
        
        // If WiFi is connected, proceed with download
        if (isWiFiConnected) {
            Log.d("MainActivity", "WiFi connected, proceeding with download");
            return true;
        }
        
        // WiFi is required but not connected - show user dialog
        showWiFiRequirementDialog(isNetworkAvailable);
        return false;
    }
    
    /**
     * Show dialog when WiFi is required but not connected
     * @param hasOtherConnection true if mobile data or other connection is available
     */
    private void showWiFiRequirementDialog(boolean hasOtherConnection) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("WiFi Required for Download");
        
        String message;
        if (hasOtherConnection) {
            message = "You have enabled \"WiFi Only Downloads\" but you're not connected to WiFi.\n\n" +
                     "Model download size: " + (selectedModel != null ? selectedModel.getFormattedFileSize() : "Unknown") + "\n\n" +
                     "What would you like to do?";
        } else {
            message = "You have enabled \"WiFi Only Downloads\" but no network connection is available.\n\n" +
                     "Please connect to WiFi to download models.";
        }
        
        builder.setMessage(message);
        builder.setIcon(R.drawable.ic_wifi);
        
        if (hasOtherConnection) {
            // Show three options: Continue anyway, Wait for WiFi, Cancel
            builder.setPositiveButton("Continue Anyway", (dialog, which) -> {
                Log.d("MainActivity", "User chose to continue download without WiFi");
                proceedWithDownloadAnyway();
            });
            
            builder.setNeutralButton("Wait for WiFi", (dialog, which) -> {
                Log.d("MainActivity", "User chose to wait for WiFi");
                showWaitingForWiFiDialog();
            });
            
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                Log.d("MainActivity", "User cancelled download due to WiFi requirement");
                dialog.dismiss();
            });
        } else {
            // Only show option to check settings or cancel
            builder.setPositiveButton("Check Settings", (dialog, which) -> {
                Log.d("MainActivity", "User chose to check network settings");
                openWiFiSettings();
            });
            
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                Log.d("MainActivity", "User cancelled download - no network available");
                dialog.dismiss();
            });
        }
        
        builder.setCancelable(false); // Prevent dismissing by tapping outside
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    /**
     * Proceed with download despite WiFi requirement
     */
    private void proceedWithDownloadAnyway() {
        Log.d("MainActivity", "Proceeding with download without WiFi - user override");
        
        // Set override flag to bypass WiFi checks for this download session
        wiFiRequirementOverridden = true;
        Log.d("MainActivity", "WiFi requirement overridden by user choice");
        
        // Set override in download manager as well
        if (downloadManager != null) {
            downloadManager.setWiFiRequirementOverride(true);
        }
        
        // Show warning about data usage
        Toast.makeText(this, "Downloading using mobile data. Monitor your data usage.", Toast.LENGTH_LONG).show();
        
        // Continue with the actual download
        continueDownloadAfterWiFiCheck();
    }
    
    /**
     * Show dialog to wait for WiFi connection and start automatic monitoring
     */
    private void showWaitingForWiFiDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Waiting for WiFi");
        builder.setMessage("Waiting for WiFi connection to start download...\n\nDownload will start automatically when WiFi is connected.\n\n📶 Monitoring network status...");
        builder.setIcon(R.drawable.ic_wifi);
        
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Log.d("MainActivity", "User cancelled waiting for WiFi");
            stopWiFiMonitoring();
            dialog.dismiss();
        });
        
        // Prevent back button from dismissing - user must explicitly cancel
        builder.setCancelable(false);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Store dialog reference and start WiFi monitoring
        wiFiWaitingDialog = dialog;
        startWiFiMonitoring();
    }
    
    /**
     * Open device WiFi settings
     */
    private void openWiFiSettings() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to open WiFi settings", e);
            Toast.makeText(this, "Unable to open WiFi settings", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Show dialog for downloading model from custom URL
     */
    private void showCustomUrlDownloadDialog() {
        // Inflate custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_custom_download, null);
        
        // Get references to views
        com.google.android.material.textfield.TextInputLayout textInputModelName = dialogView.findViewById(R.id.text_input_model_name);
        com.google.android.material.textfield.TextInputEditText inputModelName = dialogView.findViewById(R.id.input_model_name);
        com.google.android.material.textfield.TextInputLayout textInputUrl = dialogView.findViewById(R.id.text_input_url);
        com.google.android.material.textfield.TextInputEditText inputUrl = dialogView.findViewById(R.id.input_url);
        com.google.android.material.button.MaterialButton btnBrowseHF = dialogView.findViewById(R.id.btn_browse_huggingface);
        
        // Set up browse button
        btnBrowseHF.setOnClickListener(v -> {
            performHapticFeedback(v);
            openHuggingFaceBrowser();
        });

        // Set up quick paste button
        TextView btnPasteUrl = dialogView.findViewById(R.id.btn_paste_url);
        if (btnPasteUrl != null) {
            btnPasteUrl.setOnClickListener(v -> {
                performHapticFeedback(v);
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                    CharSequence clipData = clipboard.getPrimaryClip().getItemAt(0).getText();
                    if (clipData != null) {
                        String pasted = clipData.toString().trim();
                        inputUrl.setText(pasted);
                        inputUrl.setSelection(inputUrl.getText().length());
                        Toast.makeText(this, "Pasted from clipboard", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Auto-fix Hugging Face /blob/ URL to /resolve/ and prefill model name
        inputUrl.addTextChangedListener(new android.text.TextWatcher() {
            private boolean isFormatting = false;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (isFormatting || s == null) return;
                String currentText = s.toString();

                if (currentText.contains("huggingface.co/") && currentText.contains("/blob/")) {
                    isFormatting = true;
                    String sanitized = currentText.replace("/blob/", "/resolve/");
                    int sel = Math.min(inputUrl.getSelectionStart() + 3, sanitized.length());
                    inputUrl.setText(sanitized);
                    inputUrl.setSelection(Math.max(0, sel));
                    isFormatting = false;
                    currentText = sanitized;
                    Toast.makeText(MainActivity.this, "Auto-fixed Hugging Face link to direct download", Toast.LENGTH_SHORT).show();
                }

                String currentName = inputModelName.getText() != null ? inputModelName.getText().toString().trim() : "";
                if (currentName.isEmpty()) {
                    String suggested = suggestModelNameFromUrl(currentText);
                    if (suggested != null && !suggested.isEmpty()) {
                        inputModelName.setText(suggested);
                    }
                }
            }
        });
        
        // Create Material AlertDialog
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setView(dialogView);
        builder.setPositiveButton("Download", null); // Set to null to override later
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        
        // Make dialog corners rounded
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }
        
        dialog.show();
        
        // Override positive button click to validate inputs
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            performHapticFeedback(v);
            String modelName = inputModelName.getText() != null ? inputModelName.getText().toString().trim() : "";
            String url = inputUrl.getText() != null ? inputUrl.getText().toString().trim() : "";
            
            // Reset errors
            textInputModelName.setError(null);
            textInputUrl.setError(null);
            
            // Validate inputs
            boolean isValid = true;
            
            if (modelName.isEmpty()) {
                textInputModelName.setError("Please enter a model name");
                inputModelName.requestFocus();
                isValid = false;
            }
            
            if (url.isEmpty()) {
                textInputUrl.setError("Please enter a download URL");
                if (isValid) inputUrl.requestFocus();
                isValid = false;
            } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                textInputUrl.setError("URL must start with http:// or https://");
                if (isValid) inputUrl.requestFocus();
                isValid = false;
            }
            
            if (isValid) {
                // All validations passed - create model and start download
                dialog.dismiss();
                downloadModelFromCustomUrl(modelName, url);
            }
        });
    }

    /**
     * Suggest a clean user-friendly model name from a direct download URL
     */
    private String suggestModelNameFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        try {
            String clean = url.trim();
            int qIndex = clean.indexOf('?');
            if (qIndex != -1) clean = clean.substring(0, qIndex);
            int lastSlash = clean.lastIndexOf('/');
            if (lastSlash != -1 && lastSlash < clean.length() - 1) {
                String file = clean.substring(lastSlash + 1);
                if (file.toLowerCase().endsWith(".gguf")) {
                    String base = file.substring(0, file.length() - 5)
                                      .replace("-", " ")
                                      .replace("_", " ");
                    return ModelConfig.capitalizeWords(base);
                }
            }
        } catch (Exception ignored) {}
        return "";
    }
    
    /**
     * Download model from custom URL with simplified parameters
     */
    private void downloadModelFromCustomUrl(String modelName, String downloadUrl) {
        downloadModelFromCustomUrl(modelName, downloadUrl, null);
    }

    private void downloadModelFromCustomUrl(String modelName, String downloadUrl, String explicitFileName) {
        if (downloadUrl != null && downloadUrl.contains("huggingface.co/") && downloadUrl.contains("/blob/")) {
            downloadUrl = downloadUrl.replace("/blob/", "/resolve/");
        }
        if (modelName != null && modelName.toLowerCase().endsWith(".gguf")) {
            modelName = modelName.substring(0, modelName.length() - 5).trim();
        }
        Log.d(TAG, "Starting custom URL download: " + modelName + " from " + downloadUrl);
        
        // Generate or use unique file name
        String fileName;
        if (explicitFileName != null && !explicitFileName.trim().isEmpty() && explicitFileName.toLowerCase().endsWith(".gguf")) {
            fileName = explicitFileName.trim();
        } else {
            fileName = generateUniqueFileName(modelName);
        }
        Log.d(TAG, "Selected download file name: " + fileName);
        
        // Create a custom model instance
        ModelConfig.Model customModel = ModelConfig.Model.createFromCustomUrl(modelName, downloadUrl, fileName);
        
        // Add model to available models list (so it appears in spinner immediately)
        ModelConfig.addCustomModel(customModel);
        
        // Save custom model to persistent storage (so it persists across app restarts)
        ModelConfig.saveCustomModel(this, customModel);
        
        // Refresh spinner to show the new model
        refreshModelSpinner();
        
        // Set as selected model
        selectedModel = customModel;
        
        // Check WiFi requirement
        if (!checkWiFiRequirementForDownload()) {
            return; // User chose to cancel or wait for WiFi
        }
        
        // Request notification permission for Android 13+ when user initiates download
        requestNotificationPermission();
        
        // Check storage permissions for Android 9 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 
                    PERMISSION_REQUEST_STORAGE);
                return;
            }
        }
        
        // Show info message
        Toast.makeText(this, "Starting download: " + modelName + "\nSaving as: " + fileName, Toast.LENGTH_LONG).show();
        
        // Start download
        if (downloadManager != null) {
            downloadManager.downloadModel(customModel);
        } else {
            Log.e(TAG, "Download manager is null");
            Toast.makeText(this, "Download manager not initialized", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Generate a unique file name from the model name
     * If a file with the same name exists, append a number (e.g., name (2).gguf)
     */
    private String generateUniqueFileName(String modelName) {
        // Sanitize the model name to create a valid file name
        String baseFileName = modelName
            .replaceAll("[^a-zA-Z0-9-_\\s]", "") // Remove invalid characters
            .replaceAll("\\s+", "-") // Replace spaces with hyphens
            .toLowerCase()
            .trim();
        
        // Ensure base name is not empty
        if (baseFileName.isEmpty()) {
            baseFileName = "custom-model";
        }
        
        // Get the models directory
        java.io.File modelsDir = downloadManager.getModelsDirectory();
        
        // Start with the base file name
        String fileName = baseFileName + ".gguf";
        java.io.File targetFile = new java.io.File(modelsDir, fileName);
        
        // If file doesn't exist, use the base name
        if (!targetFile.exists()) {
            return fileName;
        }
        
        // If file exists, add incrementing number
        int counter = 2;
        while (true) {
            fileName = baseFileName + " (" + counter + ").gguf";
            targetFile = new java.io.File(modelsDir, fileName);
            
            if (!targetFile.exists()) {
                return fileName;
            }
            
            counter++;
            
            // Safety check to prevent infinite loop
            if (counter > 1000) {
                // Use timestamp as fallback
                long timestamp = System.currentTimeMillis();
                return baseFileName + "-" + timestamp + ".gguf";
            }
        }
    }
    
    /**
     * Open Hugging Face hub to search and download models in-app
     */
    private void openHuggingFaceBrowser() {
        openHuggingFaceHub();
    }
    
    /**
     * Start automatic WiFi monitoring for paused downloads (background monitoring without dialog)
     */
    private void startAutomaticWiFiMonitoringForPausedDownload(String modelName) {
        Log.d("MainActivity", "Starting automatic WiFi monitoring for paused download: " + modelName);
        
        // Use a different monitoring approach for background monitoring
        if (isWiFiMonitoringActive) {
            Log.d("MainActivity", "WiFi monitoring already active");
            return;
        }
        
        isWiFiMonitoringActive = true;
        
        if (wiFiMonitorHandler == null) {
            wiFiMonitorHandler = new Handler(Looper.getMainLooper());
        }
        
        wiFiMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isWiFiMonitoringActive) {
                    Log.d("MainActivity", "Automatic WiFi monitoring stopped");
                    return;
                }
                
                // Check if WiFi is now available and download is still paused
                if (NetworkUtils.isWiFiConnected(MainActivity.this)) {
                    // Check if the download is still paused
                    if (downloadManager != null && downloadManager.hasPausedDownloads()) {
                        Log.d("MainActivity", "WiFi detected! Auto-resuming paused download");
                        onWiFiConnectedForPausedDownload(modelName);
                    } else {
                        Log.d("MainActivity", "WiFi detected but no paused downloads found");
                        stopWiFiMonitoring();
                    }
                } else {
                    // Schedule next check (less frequent for background monitoring)
                    if (isWiFiMonitoringActive && wiFiMonitorHandler != null) {
                        wiFiMonitorHandler.postDelayed(this, WIFI_CHECK_INTERVAL * 2); // 4 seconds for background
                    }
                }
            }
        };
        
        // Start monitoring
        wiFiMonitorHandler.post(wiFiMonitorRunnable);
    }
    
    /**
     * Called when WiFi connection is detected for a paused download
     */
    private void onWiFiConnectedForPausedDownload(String modelName) {
        Log.d("MainActivity", "WiFi connected - auto-resuming paused download: " + modelName);
        
        // Stop monitoring
        stopWiFiMonitoring();
        
        // Show success message
        Toast.makeText(this, "WiFi connected! Resuming download automatically...", Toast.LENGTH_SHORT).show();
        
        // Resume the download directly through download manager
        if (downloadManager != null) {
            downloadManager.resumeDownload(modelName);
        }
    }

    /**
     * Start WiFi monitoring to automatically begin download when WiFi becomes available
     */
    private void startWiFiMonitoring() {
        if (isWiFiMonitoringActive) {
            Log.d("MainActivity", "WiFi monitoring already active");
            return;
        }
        
        Log.d("MainActivity", "Starting WiFi monitoring");
        isWiFiMonitoringActive = true;
        
        if (wiFiMonitorHandler == null) {
            wiFiMonitorHandler = new Handler(Looper.getMainLooper());
        }
        
        wiFiMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isWiFiMonitoringActive) {
                    Log.d("MainActivity", "WiFi monitoring stopped");
                    return;
                }
                
                // Check if WiFi is now available
                if (NetworkUtils.isWiFiConnected(MainActivity.this)) {
                    Log.d("MainActivity", "WiFi detected! Auto-starting download");
                    onWiFiConnected();
                } else {
                    // Schedule next check
                    if (isWiFiMonitoringActive && wiFiMonitorHandler != null) {
                        wiFiMonitorHandler.postDelayed(this, WIFI_CHECK_INTERVAL);
                    }
                }
            }
        };
        
        // Start monitoring
        wiFiMonitorHandler.post(wiFiMonitorRunnable);
    }
    
    /**
     * Stop WiFi monitoring
     */
    private void stopWiFiMonitoring() {
        Log.d("MainActivity", "Stopping WiFi monitoring");
        isWiFiMonitoringActive = false;
        
        if (wiFiMonitorHandler != null && wiFiMonitorRunnable != null) {
            wiFiMonitorHandler.removeCallbacks(wiFiMonitorRunnable);
        }
    }
    
    /**
     * Called when WiFi connection is detected during monitoring
     */
    private void onWiFiConnected() {
        Log.d("MainActivity", "WiFi connected - starting download automatically");
        
        // Stop monitoring
        stopWiFiMonitoring();
        
        // Dismiss waiting dialog if it's showing
        if (wiFiWaitingDialog != null && wiFiWaitingDialog.isShowing()) {
            wiFiWaitingDialog.dismiss();
            wiFiWaitingDialog = null;
        }
        
        // Show success message
        Toast.makeText(this, "WiFi connected! Starting download...", Toast.LENGTH_SHORT).show();
        
        // Start the download
        continueDownloadAfterWiFiCheck();
    }

    /**
     * Continue with the actual download after WiFi check is complete
     */
    private void continueDownloadAfterWiFiCheck() {
        Log.d("MainActivity", "Continuing with download after WiFi check");
        
        // Log storage information
        long availableSpace = downloadManager.getAvailableSpace();
        long requiredSpace = selectedModel.fileSizeBytes;
        Log.d("MainActivity", "Available space: " + formatBytes(availableSpace));
        Log.d("MainActivity", "Required space: " + formatBytes(requiredSpace));
        Log.d("MainActivity", "Model size: " + selectedModel.getFormattedFileSize());
        
        // Check storage space
        if (!downloadManager.hasEnoughSpace(selectedModel)) {
            String message = String.format("Not enough storage space.\nRequired: %s\nAvailable: %s", 
                selectedModel.getFormattedFileSize(), 
                formatBytes(availableSpace));
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            Log.w("MainActivity", "Storage check failed: " + message);
            return;
        }
        
        // Start download
        Log.d("MainActivity", "Starting download...");
        downloadManager.downloadModel(selectedModel);
        if (modelListAdapter != null) {
            modelListAdapter.notifyDataSetChanged();
        }
    }

    private void deleteModel() {
        if (selectedModel == null) {
            Log.e("MainActivity", "Cannot delete model: selectedModel is null");
            Toast.makeText(this, "Please select a model first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d("MainActivity", "Attempting to delete model: " + selectedModel.displayName);
        
        if (downloadManager.deleteModel(selectedModel)) {
            Log.d("MainActivity", "Model deleted successfully: " + selectedModel.displayName);
            
            // If it's a custom model, remove from persistent storage and available models list
            if (selectedModel.isCustomUrl) {
                ModelConfig.deleteCustomModel(this, selectedModel.name);
                ModelConfig.removeCustomModel(selectedModel.name);
                
                // Refresh spinner to remove the model
                refreshModelSpinner();
            }
            
            Toast.makeText(this, "Model deleted successfully", Toast.LENGTH_SHORT).show();
            updateDownloadButton();
        } else {
            Log.e("MainActivity", "Failed to delete model: " + selectedModel.displayName);
            Toast.makeText(this, "Failed to delete model", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showDownloadProgress() {
        if (!isActivityValid() || downloadProgressLayout == null) {
            Log.w("MainActivity", "showDownloadProgress: activity invalid or downloadProgressLayout is null");
            return;
        }
        downloadProgressLayout.setVisibility(View.VISIBLE);
        // Ensure progress bar is configured correctly
        if (progressDownload != null) {
            progressDownload.setMax(100);
            progressDownload.setProgress(0);
        }
        // Set initial button state to "Pause"
        if (btnPauseResumeDownload != null) {
            btnPauseResumeDownload.setText("Pause");
            btnPauseResumeDownload.setEnabled(true);
        }
        // Apply current accent colors to download buttons
        applyDownloadButtonColors();
        if (progressDownload != null) {
            Log.d("MainActivity", "Download progress layout shown, progress bar max: " + progressDownload.getMax());
        }
    }
    
    private void hideDownloadProgress() {
        if (downloadProgressLayout != null) {
            downloadProgressLayout.setVisibility(View.GONE);
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    @Override
    public void onChatClick(Chat chat) {
        if (chat == null) {
            Log.e(TAG, "Chat is null in onChatClick");
            Toast.makeText(this, "Error: Chat data is invalid", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "onChatClick called for chat: " + chat.title + " (ID: " + chat.id + ")");
        chatManager.handleChatClick(chat);
    }
    
    @Override
    public void onDeleteChatClick(Chat chat) {
        if (chat == null) {
            Log.e(TAG, "Chat is null in onDeleteChatClick");
            Toast.makeText(this, "Error: Chat data is invalid", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "onDeleteChatClick called for chat: " + chat.title + " (ID: " + chat.id + ")");
        chatManager.handleDeleteChatClick(chat);
    }
    
    @Override
    public void onRenameChatClick(Chat chat) {
        if (chat == null) return;

        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(chat.title);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setTextColor(resolveThemeColor(R.attr.textColorPrimary));
        input.setHintTextColor(resolveThemeColor(R.attr.textColorSecondary));

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        int marginHorizontal = (int) (22 * getResources().getDisplayMetrics().density);
        int marginVertical = (int) (10 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = marginHorizontal;
        params.rightMargin = marginHorizontal;
        params.topMargin = marginVertical;
        params.bottomMargin = marginVertical;
        input.setLayoutParams(params);
        container.addView(input);

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Rename Conversation")
            .setView(container)
            .setPositiveButton("Save", (dialog, which) -> {
                String newTitle = input.getText().toString().trim();
                if (!newTitle.isEmpty() && !newTitle.equals(chat.title)) {
                    chat.title = newTitle;
                    safeExecuteBackground(() -> {
                        try {
                            ChatDatabase database = ChatDatabase.getDatabase(this);
                            database.chatDao().updateChat(chat);
                            runOnUiThread(() -> {
                                if (chatHistoryAdapter != null) {
                                    chatHistoryAdapter.updateChatTitle(chat.id, newTitle);
                                }
                                if (chatManager != null && chatManager.getCurrentChatId() == chat.id) {
                                    if (toolbarTitle != null) {
                                        toolbarTitle.setText(newTitle);
                                    }
                                }
                                Toast.makeText(this, "Conversation renamed", Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error renaming chat", e);
                        }
                    });
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public void onChatLongClick(Chat chat, View view) {
        if (chat == null) return;
        CharSequence[] options = new CharSequence[]{"Open Conversation", "Rename", "Delete"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(chat.title)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    onChatClick(chat);
                } else if (which == 1) {
                    onRenameChatClick(chat);
                } else if (which == 2) {
                    onDeleteChatClick(chat);
                }
            })
            .show();
    }
    
    // ChatManagerListener implementations
    @Override
    public void onChatCreated(Chat chat) {
        toolbarTitle.setText(chat.title);
    }
    
    @Override
    public void onChatDeleted(long chatId) {
        // Chat deletion is handled in ChatManager
        // Reload chat history after deletion
        if (chatManager != null) {
            chatManager.loadChatHistory();
        }
    }
    
    @Override
    public void onMessagesLoaded(List<Message> messages) {
        if (messageAdapter != null) {
            messageAdapter.setMessages(messages);
        }
        
        // Scroll to bottom
        if (!messages.isEmpty() && recyclerMessages != null) {
            recyclerMessages.scrollToPosition(messages.size() - 1);
        }
    }
    
    @Override
    public void onChatHistoryLoaded(List<Chat> chats) {
        Log.d(TAG, "onChatHistoryLoaded: Received " + (chats != null ? chats.size() : 0) + " chats");
        if (chatHistoryAdapter != null) {
            chatHistoryAdapter.setChats(chats);
            if (chatManager != null) {
                chatHistoryAdapter.setActiveChatId(chatManager.getCurrentChatId());
            }
            Log.d(TAG, "onChatHistoryLoaded: Chats set in adapter");
        } else {
            Log.e(TAG, "onChatHistoryLoaded: chatHistoryAdapter is null!");
        }

        View emptyHistory = findViewById(R.id.layout_drawer_empty_history);
        if (emptyHistory != null) {
            boolean isEmpty = (chats == null || chats.isEmpty());
            emptyHistory.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            if (recyclerChatHistory != null) {
                recyclerChatHistory.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            }
        }
    }
    
    @Override
    public void onNewChatStarted() {
        if (toolbarTitle != null) {
            toolbarTitle.setText("MobiGPT");
        }
        if (messageAdapter != null) {
            messageAdapter.setMessages(List.of());
        }
        if (chatHistoryAdapter != null) {
            chatHistoryAdapter.setActiveChatId(-1);
        }
        showEmptyState();
    }
    
    @Override
    public void onChatSelected(Chat chat) {
        if (toolbarTitle != null) {
            toolbarTitle.setText(chat != null ? chat.title : "MobiGPT");
        }
        if (chatHistoryAdapter != null && chat != null) {
            chatHistoryAdapter.setActiveChatId(chat.id);
        }
    }
    
    @Override
    public void onMessageInputClear() {
        if (editMessage != null) {
            editMessage.setText("");
        }
    }
    
    @Override
    public void onEmptyStateShow() {
        showEmptyState();
    }
    
    @Override
    public void onEmptyStateHide() {
        hideEmptyState();
    }
    
    @Override
    public void onDrawerClose() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawers();
        }
    }
    
    @Override
    public void onError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }
    
    @Override
    public void onChatSwitchBlocked(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }
    
    // AI Response ChatManagerListener implementations
    @Override
    public void onAIResponseStarted() {
        // Show typing indicator or loading state and start streaming
        runOnUiThread(() -> {
            // Clear any status messages and start streaming response
            messageAdapter.clearStatusMessage();
            messageAdapter.startStreamingResponse();
            
            // Change send button to stop button
            setSendButtonToStop();
            
            // Reset all scroll state for new response, respecting user settings
            isAutoScrollEnabled = settingsManager.isAutoScrollNewMessages();
            isUserScrolledUp = false;
            lastKnownItemCount = messageAdapter.getItemCount();
            
            // Scroll to bottom when starting a new AI response if auto-scroll is enabled
            if (isAutoScrollEnabled) {
                scrollToBottom(true);
            }
            
            Log.d(TAG, "AI response started - smart auto-scroll enabled");
        });
    }
    
    @Override
    public void onAITokenReceived(String token) {
        // Handle streaming tokens - update UI with each token
        runOnUiThread(() -> {
            // Add token to the streaming message
            messageAdapter.addStreamingToken(token);
            
            // Smart auto-scroll with position anchoring
            if (!isUserScrolledUp && isAutoScrollEnabled) {
                // User is at bottom - auto-scroll normally
                int itemCount = messageAdapter.getItemCount();
                if (itemCount > 0) {
                    // Only scroll if item count changed (new content added)
                    if (itemCount != lastKnownItemCount) {
                        lastKnownItemCount = itemCount;
                        isProgrammaticScroll = true;
                        recyclerMessages.scrollToPosition(itemCount - 1);
                    }
                }
            } else if (isUserScrolledUp) {
                // User has scrolled up - restore their reading position
                // This prevents the content from jumping when new tokens are added
                restoreScrollAnchor();
            }
        });
    }

    @Override
    public void onAIResponseComplete(String fullResponse) {
        // AI response is complete and saved to database
        runOnUiThread(() -> {
            // Complete the streaming (this will remove the temp streaming message)
            messageAdapter.completeStreamingResponse();
            
            // Change stop button back to send button
            setSendButtonToSend();
            
            // Reset scroll state
            lastKnownItemCount = 0;
            
            Log.d(TAG, "AI response complete");
            // Messages will be automatically reloaded by ChatManager which will show the saved message
        });
    }
    
    @Override
    public void onAIResponseError(String error) {
        runOnUiThread(() -> {
            // Clear streaming message on error
            messageAdapter.clearStreamingMessage();
            
            // Change stop button back to send button
            setSendButtonToSend();
            
            Toast.makeText(MainActivity.this, "AI Error: " + error, Toast.LENGTH_LONG).show();
            Log.e(TAG, "AI response error: " + error);
        });
    }
    
    @Override
    public void onAIResponseStopped() {
        runOnUiThread(() -> {
            // Complete the streaming with whatever was generated (saves partial response)
            messageAdapter.completeStreamingResponse();
            
            // Change stop button back to send button
            setSendButtonToSend();
            
            Toast.makeText(MainActivity.this, "Generation stopped - partial response saved", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "AI response stopped by user - partial response saved");
        });
    }
    
    // AI Status ChatManagerListener implementations
    @Override
    public void onModelLoadingStarted() {
        runOnUiThread(() -> {
            Log.d(TAG, "Model loading started");
            
            // Show discreet in-line progress bar
            if (modelLoadingProgressLayout != null) {
                modelLoadingProgressLayout.setVisibility(View.VISIBLE);
            }
            if (textSelectedModelStatus != null) {
                textSelectedModelStatus.setText("● Loading...");
                textSelectedModelStatus.setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorPrimary));
                textSelectedModelStatus.setBackground(null);
                textSelectedModelStatus.setPadding(0, 0, 0, 0);
                textSelectedModelStatus.setVisibility(View.VISIBLE);
            }
            if (progressModelLoading != null) {
                progressModelLoading.setProgress(0);
            }
            if (textModelLoadingPercentage != null) {
                textModelLoadingPercentage.setText("0%");
            }
            if (textModelLoadingStatus != null) {
                textModelLoadingStatus.setText("Initializing...");
            }
            
            // Disable send button during loading
            if (btnSend != null) {
                btnSend.setEnabled(false);
                btnSend.setAlpha(0.5f);
            }
            
            // Keep text input enabled so user can type while loading
            if (editMessage != null) {
                editMessage.setEnabled(true);
                editMessage.setHint("Type your message (send disabled until model loads)");
            }
            
            messageAdapter.showStatusMessage("Loading model...");
            // Scroll to bottom to show status message
            if (messageAdapter.getItemCount() > 0) {
                recyclerMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
            }
        });
    }
    
    @Override
    public void onModelLoadingProgress(int progress, String status) {
        runOnUiThread(() -> {
            Log.d(TAG, "Model loading progress: " + progress + "% - " + status);
            
            if (progressModelLoading != null) {
                progressModelLoading.setProgress(progress);
            }
            if (textModelLoadingPercentage != null) {
                textModelLoadingPercentage.setText(progress + "%");
            }
            if (textModelLoadingStatus != null) {
                textModelLoadingStatus.setText(status);
            }
        });
    }
    
    @Override
    public void onModelLoaded(long durationMs) {
        runOnUiThread(() -> {
            String durationText = durationMs < 1000 ? "(" + durationMs + "ms)" : "(" + String.format("%.1f", durationMs / 1000.0) + "s)";
            Log.d(TAG, "Model loaded in " + durationMs + "ms");
            
            // Hide the progress bar
            if (modelLoadingProgressLayout != null) {
                modelLoadingProgressLayout.setVisibility(View.GONE);
            }
            
            if (selectedModel != null) {
                loadedModelName = selectedModel.name;
                updateSelectedModelDisplay(selectedModel);
            }
            
            // Re-enable send button
            if (btnSend != null) {
                btnSend.setEnabled(true);
                btnSend.setAlpha(1.0f);
            }
            
            // Restore normal hint
            if (editMessage != null) {
                editMessage.setHint(R.string.type_message_hint);
            }
            
            // Show "Model loaded" briefly, then clear it to restore full interactivity
            messageAdapter.updateStatusMessage("Model loaded " + durationText);
            
            // Clear the status message after 2 seconds to restore chat history and context menu functionality
            new Handler(Looper.getMainLooper()).postDelayed(() -> messageAdapter.clearStatusMessage(), 2000);
        });
    }
    
    @Override
    public void onModelLoadingError(String error) {
        runOnUiThread(() -> {
            Log.e(TAG, "Model loading error: " + error);
            loadedModelName = null;
            isLoadingModel = false; // Clear loading flag on error
            
            // Hide the progress bar
            if (modelLoadingProgressLayout != null) {
                modelLoadingProgressLayout.setVisibility(View.GONE);
            }
            if (selectedModel != null) {
                updateSelectedModelDisplay(selectedModel);
            }
            
            // Re-enable send button
            if (btnSend != null) {
                btnSend.setEnabled(true);
                btnSend.setAlpha(1.0f);
            }
            
            // Restore normal hint
            if (editMessage != null) {
                editMessage.setHint(R.string.type_message_hint);
            }
            
            messageAdapter.clearStatusMessage();
            
            // Show error toast
            Toast.makeText(MainActivity.this, "Failed to load model: " + error, Toast.LENGTH_LONG).show();
        });
    }
    
    @Override
    public void onThinking() {
        runOnUiThread(() -> {
            Log.d(TAG, "AI thinking started");
            messageAdapter.updateStatusMessage("Thinking...");
        });
    }
    
    private String formatModelDisplayName(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            return "Model";
        }
        ModelConfig.Model model = ModelConfig.getModelByName(modelName, this);
        if (model != null && model.displayName != null && !model.displayName.trim().isEmpty()) {
            return model.displayName;
        }
        model = ModelConfig.getModelByFileName(modelName, this);
        if (model != null && model.displayName != null && !model.displayName.trim().isEmpty()) {
            return model.displayName;
        }
        String clean = modelName.replace(".gguf", "").replace("-", " ").replace("_", " ");
        if (clean.length() > 1) {
            return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
        }
        return clean;
    }

    // ModelDownloadManager.DownloadListener implementations
    @Override
    public void onDownloadStarted(String modelName) {
        if (!isActivityValid()) {
            return;
        }
        showDownloadProgress();
        if (textDownloadStatus != null) {
            textDownloadStatus.setText("Downloading " + formatModelDisplayName(modelName) + "...");
        }
        if (progressDownload != null) {
            progressDownload.setProgress(0);
        }
        if (textDownloadDetails != null) {
            textDownloadDetails.setText("0 MB / 0 MB (0%)");
        }
        if (btnPauseResumeDownload != null) {
            btnPauseResumeDownload.setText("Pause");
            btnPauseResumeDownload.setEnabled(true);
        }
        
        if (modelListAdapter != null) {
            modelListAdapter.notifyDataSetChanged();
        }
        
        // Ensure suggestion chips remain visible if empty state is showing
        if (emptyState != null && emptyState.getVisibility() == View.VISIBLE) {
            View emptyStateView = findViewById(R.id.empty_state);
            if (emptyStateView != null) {
                LinearLayout suggestionsContainer = emptyStateView.findViewById(R.id.suggestions_container);
                if (suggestionsContainer != null) {
                    suggestionsContainer.setVisibility(View.VISIBLE);
                    suggestionsContainer.requestLayout(); // Force layout refresh
                }
            }
        }
    }
    
    @Override
    public void onDownloadProgress(String modelName, int progress, long downloadedBytes, long totalBytes, long speed) {
        if (!isActivityValid()) {
            return;
        }
        Log.d("MainActivity", "Download progress: " + progress + "% (" + downloadedBytes + "/" + totalBytes + ")");
        
        // Ensure we're on the UI thread
        runOnUiThread(() -> {
            if (!isActivityValid()) {
                return;
            }
            // Clamp progress to valid range
            int clampedProgress = Math.max(0, Math.min(100, progress));
            if (progressDownload != null) {
                progressDownload.setProgress(clampedProgress);
            }
            
            String downloadedStr = formatBytes(downloadedBytes);
            String totalStr = formatBytes(totalBytes);
            String speedStr = formatBytes(speed) + "/s";
            String progressText = downloadedStr + " / " + totalStr + " (" + clampedProgress + "%) • " + speedStr;
            if (textDownloadDetails != null) {
                textDownloadDetails.setText(progressText);
            }
            
            Log.d("MainActivity", "UI updated with progress: " + clampedProgress + "%, text: " + progressText);
        });
    }
    
    @Override
    public void onDownloadCompleted(String modelName, File file) {
        if (!isActivityValid()) {
            return;
        }
        // Clear WiFi requirement override on successful download completion
        wiFiRequirementOverridden = false;
        if (downloadManager != null) {
            downloadManager.clearWiFiRequirementOverride();
        }
        Log.d("MainActivity", "Download completed - WiFi requirement override cleared");
        
        if (modelListAdapter != null) {
            modelListAdapter.notifyDataSetChanged();
        }
        
        // Show 100% completion first
        if (progressDownload != null) {
            progressDownload.setProgress(100);
        }
        if (textDownloadDetails != null && file != null) {
            textDownloadDetails.setText(formatBytes(file.length()) + " / " + formatBytes(file.length()) + " (100%)");
        }
        if (textDownloadStatus != null) {
            textDownloadStatus.setText("Download completed!");
        }
        if (btnPauseResumeDownload != null) {
            btnPauseResumeDownload.setEnabled(false);
        }
        
        // Hide progress after a short delay to show completion
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isActivityValid()) {
                return;
            }
            hideDownloadProgress();
            updateDownloadButton();
            Toast.makeText(this, modelName + " downloaded successfully!", Toast.LENGTH_LONG).show();
            
            // Refresh the model spinner to include newly downloaded model
            refreshModelSpinner();
            
            // Automatically load the model after download completion
            if (selectedModel != null && selectedModel.name.equals(modelName)) {
                Log.d(TAG, "Auto-loading model after download: " + modelName);
                loadModel(selectedModel);
            }
        }, 1500); // Show completion for 1.5 seconds
    }
    
    @Override
    public void onDownloadFailed(String modelName, String error) {
        if (!isActivityValid()) {
            return;
        }
        // Clear WiFi requirement override on download failure
        wiFiRequirementOverridden = false;
        if (downloadManager != null) {
            downloadManager.clearWiFiRequirementOverride();
        }
        Log.d("MainActivity", "Download failed - WiFi requirement override cleared");
        
        if (modelListAdapter != null) {
            modelListAdapter.notifyDataSetChanged();
        }
        
        if (btnPauseResumeDownload != null) {
            btnPauseResumeDownload.setEnabled(false);
        }
        hideDownloadProgress();
        updateDownloadButton();
        Toast.makeText(this, "Download failed: " + error, Toast.LENGTH_LONG).show();
    }
    
    @Override
    public void onDownloadCancelled(String modelName) {
        if (!isActivityValid()) {
            return;
        }
        // Clear WiFi requirement override on download cancellation
        wiFiRequirementOverridden = false;
        if (downloadManager != null) {
            downloadManager.clearWiFiRequirementOverride();
        }
        Log.d("MainActivity", "Download cancelled - WiFi requirement override cleared");
        
        // If this is a custom model that was cancelled before any download progress,
        // remove it from the list and persistent storage
        ModelConfig.Model model = ModelConfig.getModelByName(modelName, this);
        if (model != null && model.isCustomUrl && downloadManager != null) {
            java.io.File modelFile = downloadManager.getModelFile(model);
            // Only remove if file doesn't exist (download was cancelled early)
            if (modelFile != null && !modelFile.exists()) {
                Log.d("MainActivity", "Removing cancelled custom model: " + modelName);
                ModelConfig.deleteCustomModel(this, modelName);
                ModelConfig.removeCustomModel(modelName);
                refreshModelSpinner();
            }
        }
        
        if (modelListAdapter != null) {
            modelListAdapter.notifyDataSetChanged();
        }
        
        if (btnPauseResumeDownload != null) {
            btnPauseResumeDownload.setEnabled(false);
        }
        hideDownloadProgress();
        updateDownloadButton();
        Toast.makeText(this, "Download cancelled: " + modelName, Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onDownloadPaused(String modelName) {
        if (!isActivityValid()) {
            return;
        }
        // Keep the download progress UI visible but update to show paused state
        // Don't hide the progress layout - just update the button and status
        
        // Check if pause was due to WiFi requirement
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean wifiOnlyEnabled = prefs.getBoolean("wifi_only_downloads", true);
        boolean isWiFiConnected = NetworkUtils.isWiFiConnected(this);
        
        String statusMessage;
        String toastMessage;
        
        if (wifiOnlyEnabled && !isWiFiConnected) {
            statusMessage = "Download paused - waiting for WiFi";
            toastMessage = "Download paused: WiFi connection lost. Will resume automatically when WiFi is available.";
            
            // Start automatic WiFi monitoring for paused downloads
            startAutomaticWiFiMonitoringForPausedDownload(modelName);
        } else {
            statusMessage = "Download paused: " + modelName;
            toastMessage = "Download paused: " + modelName + ". Tap Resume to continue.";
        }
        
        if (textDownloadStatus != null) {
            textDownloadStatus.setText(statusMessage);
        }
        
        // Keep the current progress details visible so user can see where they paused
        // The progress bar and details will already be updated from the final progress update
        
        if (btnPauseResumeDownload != null) {
            btnPauseResumeDownload.setText("Resume");
            btnPauseResumeDownload.setEnabled(true);
        }
        
        // Show a toast indicating the download is paused
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
        
        if (modelListAdapter != null) {
            modelListAdapter.notifyDataSetChanged();
        }
    }
    
    @Override
    public void onDownloadResumed(String modelName) {
        if (!isActivityValid()) {
            return;
        }
        // Update UI to show resumed state and restart progress
        showDownloadProgress();
        if (textDownloadStatus != null) {
            textDownloadStatus.setText("Downloading " + formatModelDisplayName(modelName) + "...");
        }
        if (btnPauseResumeDownload != null) {
            btnPauseResumeDownload.setText("Pause");
        }
        Toast.makeText(this, "Download resumed: " + formatModelDisplayName(modelName), Toast.LENGTH_SHORT).show();
        
        if (modelListAdapter != null) {
            modelListAdapter.notifyDataSetChanged();
        }
    }
    
    @Override
    public void onCustomModelSizeUpdated(String modelName, long fileSizeBytes) {
        if (!isActivityValid()) {
            return;
        }
        Log.d(TAG, "Custom model size updated: " + modelName + " -> " + fileSizeBytes + " bytes");
        
        // Refresh the model spinner to show the updated size
        refreshModelSpinner();
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "MainActivity started");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing() || isDestroyed()) {
            return;
        }
        
        // Reapply theme to pick up any theme/accent color changes from Settings
        com.keralatechreach.mobigpt.utils.ThemeManager themeManager = 
            new com.keralatechreach.mobigpt.utils.ThemeManager(this);
        themeManager.applyTheme();
        
        // Apply all theme-aware colors in a single unified call
        applyAllThemeColors();
        
        // Reapply typography to pick up any font changes from Settings
        applyTypographyToUI();
        
        // Refresh message adapter to apply new typography to messages
        if (messageAdapter != null) {
            messageAdapter.notifyDataSetChanged();
        }

        // Refresh drawer live telemetry and theme UI
        updateDrawerModelStatus();
        updateQuickThemeUI();
        updateDrawerNavActiveState(currentTab);

        // Ensure window insets (navigation bar & keyboard) are freshly applied
        View mainContentLinear = findViewById(R.id.main_content_linear);
        if (mainContentLinear != null) {
            ViewCompat.requestApplyInsets(mainContentLinear);
        }
        
        // Resume WiFi monitoring if there's a waiting dialog
        if (wiFiWaitingDialog != null && wiFiWaitingDialog.isShowing()) {
            Log.d("MainActivity", "Resuming WiFi monitoring on activity resume");
            startWiFiMonitoring();
        }
        
        // Check for WiFi-paused downloads that can now be resumed
        checkForWiFiPausedDownloads();
        
        // Re-set listeners after cleanup (cleanup nullifies them to prevent leaks)
        if (messageAdapter != null) {
            messageAdapter.setOnMessageActionListener(this);
        }
        if (chatHistoryAdapter != null) {
            chatHistoryAdapter.setOnChatClickListener(this);
        }
        
        // Resume operations
        if (downloadManager != null) {
            downloadManager.setDownloadListener(this);
            downloadManager.resumeProgressUpdates();
            checkForPausedDownloads();
        }
        
        if (modelListAdapter != null) {
            modelListAdapter.notifyDataSetChanged();
        }
        
        // Reload data when activity becomes visible
        if (chatManager != null) {
            chatManager.loadChatHistory();
            
            // Only reload messages if not actively streaming
            if (messageAdapter == null || !messageAdapter.isStreamingActive()) {
                long currentChatId = chatManager.getCurrentChatId();
                if (currentChatId != -1) {
                    chatManager.loadMessages(currentChatId);
                }
            } else {
                Log.d(TAG, "Skipping message reload - streaming is active");
            }
        }

        updateStarredMessagesCount();
        
        Log.d(TAG, "MainActivity resumed");
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // Stop WiFi monitoring when app is paused
        stopWiFiMonitoring();
        
        // Pause heavy operations for memory optimization
        if (downloadManager != null) {
            downloadManager.pauseProgressUpdates();
        }
        
        // Clear adapters when activity is not visible to save memory
        // BUT preserve streaming state to prevent data loss when notification bar is opened
        if (messageAdapter != null) {
            // Only cleanup if not actively streaming
            if (!messageAdapter.isStreamingActive()) {
                messageAdapter.cleanup();
            }
        }
        if (chatHistoryAdapter != null) {
            chatHistoryAdapter.cleanup();
        }
        if (toolbarMenuPopup != null && toolbarMenuPopup.isShowing()) {
            toolbarMenuPopup.dismiss();
        }
        
        Log.d(TAG, "MainActivity paused");
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        
        Log.d(TAG, "MainActivity stopped");
    }


    
    /**
     * Check for downloads paused due to WiFi loss that can now be resumed
     */
    private void checkForWiFiPausedDownloads() {
        // Only check if WiFi is now available and we have paused downloads
        if (!NetworkUtils.isWiFiConnected(this) || downloadManager == null) {
            return;
        }
        
        if (downloadManager.hasPausedDownloads()) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean wifiOnlyEnabled = prefs.getBoolean("wifi_only_downloads", true);
            
            if (wifiOnlyEnabled) {
                String pausedModelName = downloadManager.getPausedDownloadModelName();
                if (pausedModelName != null) {
                    Log.d("MainActivity", "WiFi available and paused download found: " + pausedModelName);
                    
                    // Auto-resume without notification (seamless experience)
                    downloadManager.resumeDownload(pausedModelName);
                    Toast.makeText(this, "WiFi available - resuming download automatically", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * Check for paused downloads and show appropriate UI
     */
    private void checkForPausedDownloads() {
        if (downloadManager != null) {
            // First, clean up any downloads that are actually complete
            downloadManager.cleanupCompletedDownloads();
            
            // Then check for actual paused downloads
            if (downloadManager.hasPausedDownloads()) {
                String pausedModelName = downloadManager.getPausedDownloadModelName();
                if (pausedModelName != null) {
                    // Show a simple indication that there's a paused download
                    showPausedDownloadIndicator(pausedModelName);
                }
            }
        }
    }
    
    /**
     * Show indicator for paused download
     */
    private void showPausedDownloadIndicator(String modelName) {
        if (!isActivityValid() || downloadProgressLayout == null) {
            return;
        }
        // Show the download progress layout with resume button
        downloadProgressLayout.setVisibility(View.VISIBLE);
        if (textDownloadStatus != null) {
            textDownloadStatus.setText("Download paused: " + formatModelDisplayName(modelName));
        }
        
        // Try to get the actual progress from the download manager
        if (selectedModel != null && selectedModel.name.equals(modelName) && downloadManager != null) {
            int progress = downloadManager.getDownloadProgress(selectedModel);
            if (progress >= 0) {
                if (progressDownload != null) {
                    progressDownload.setProgress(progress);
                }
                if (textDownloadDetails != null) {
                    textDownloadDetails.setText("Paused at " + progress + "% - Tap Resume to continue");
                }
            } else {
                if (progressDownload != null) {
                    progressDownload.setProgress(0);
                }
                if (textDownloadDetails != null) {
                    textDownloadDetails.setText("Tap Resume to continue");
                }
            }
        } else {
            if (progressDownload != null) {
                progressDownload.setProgress(0);
            }
            if (textDownloadDetails != null) {
                textDownloadDetails.setText("Tap Resume to continue");
            }
        }
        
        if (btnPauseResumeDownload != null) {
            btnPauseResumeDownload.setText("Resume");
            btnPauseResumeDownload.setEnabled(true);
        }
        
        Log.d("MainActivity", "Showing paused download indicator for: " + modelName);
    }
    
    /**
     * Setup navigation drawer actions, quick links, search, and footer
     */
    private void setupStarredMessagesButton() {
        View btnStarredMessages = findViewById(R.id.btn_starred_messages);
        if (btnStarredMessages != null) {
            btnStarredMessages.setOnClickListener(v -> {
                // Haptic feedback on button press
                performHapticFeedback(v);
                
                // Close drawer first
                if (drawerLayout != null) {
                    drawerLayout.closeDrawers();
                }
                
                // Open starred messages activity
                Intent intent = new Intent(this, StarredMessagesActivity.class);
                startActivity(intent);
            });
        }

        View btnDrawerSettings = findViewById(R.id.btn_drawer_settings);
        if (btnDrawerSettings != null) {
            btnDrawerSettings.setOnClickListener(v -> {
                performHapticFeedback(v);
                if (drawerLayout != null) {
                    drawerLayout.closeDrawers();
                }
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
            });
        }

        View btnDrawerNavModels = findViewById(R.id.btn_drawer_nav_models);
        if (btnDrawerNavModels != null) {
            btnDrawerNavModels.setOnClickListener(v -> {
                performHapticFeedback(v);
                if (drawerLayout != null) {
                    drawerLayout.closeDrawers();
                }
                if (currentTab != TAB_MODELS) {
                    switchToTab(TAB_MODELS);
                }
            });
        }

        View btnDrawerQuickTheme = findViewById(R.id.btn_drawer_quick_theme);
        if (btnDrawerQuickTheme != null) {
            btnDrawerQuickTheme.setOnClickListener(v -> {
                performHapticFeedback(v);
                toggleQuickTheme();
            });
        }
        updateQuickThemeUI();
        updateDrawerModelStatus();
        updateDrawerNavActiveState(currentTab);

        // Real-time conversation search & toggle
        View btnDrawerSearchToggle = findViewById(R.id.btn_drawer_search_toggle);
        View layoutDrawerSearch = findViewById(R.id.layout_drawer_search);
        android.widget.EditText editDrawerSearchChat = findViewById(R.id.edit_drawer_search_chat);
        View btnDrawerClearSearch = findViewById(R.id.btn_drawer_clear_search);
        View layoutNoSearchResults = findViewById(R.id.layout_drawer_no_search_results);
        View layoutEmptyHistory = findViewById(R.id.layout_drawer_empty_history);

        if (btnDrawerSearchToggle != null && layoutDrawerSearch != null) {
            btnDrawerSearchToggle.setOnClickListener(v -> {
                performHapticFeedback(v);
                if (layoutDrawerSearch.getVisibility() == View.VISIBLE) {
                    layoutDrawerSearch.setVisibility(View.GONE);
                    if (editDrawerSearchChat != null) {
                        editDrawerSearchChat.setText("");
                        android.view.inputmethod.InputMethodManager imm =
                                (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(editDrawerSearchChat.getWindowToken(), 0);
                        }
                    }
                } else {
                    layoutDrawerSearch.setVisibility(View.VISIBLE);
                    if (editDrawerSearchChat != null) {
                        editDrawerSearchChat.requestFocus();
                        android.view.inputmethod.InputMethodManager imm =
                                (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showSoftInput(editDrawerSearchChat, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                        }
                    }
                }
            });
        }

        if (editDrawerSearchChat != null) {
            editDrawerSearchChat.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String query = (s != null) ? s.toString().trim() : "";
                    if (btnDrawerClearSearch != null) {
                        btnDrawerClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    if (chatHistoryAdapter != null) {
                        chatHistoryAdapter.filter(query);
                    }
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (btnDrawerClearSearch != null && editDrawerSearchChat != null) {
            btnDrawerClearSearch.setOnClickListener(v -> {
                performHapticFeedback(v);
                editDrawerSearchChat.setText("");
            });
        }

        if (chatHistoryAdapter != null) {
            chatHistoryAdapter.setFilterResultListener((totalCount, filteredCount, isQueryActive) -> {
                runOnUiThread(() -> {
                    if (layoutNoSearchResults != null) {
                        layoutNoSearchResults.setVisibility((isQueryActive && filteredCount == 0) ? View.VISIBLE : View.GONE);
                    }
                    if (layoutEmptyHistory != null) {
                        layoutEmptyHistory.setVisibility((!isQueryActive && totalCount == 0) ? View.VISIBLE : View.GONE);
                    }
                    if (recyclerChatHistory != null) {
                        recyclerChatHistory.setVisibility(filteredCount > 0 ? View.VISIBLE : View.GONE);
                    }
                });
            });
        }

        // Apply system window insets to drawer footer to avoid system gesture pill overlap
        View navView = findViewById(R.id.nav_view);
        if (navView != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(navView, (v, insets) -> {
                androidx.core.graphics.Insets navBarInsets = insets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars());
                View footer = v.findViewById(R.id.layout_drawer_footer);
                if (footer != null) {
                    footer.setPadding(0, 0, 0, navBarInsets.bottom);
                }
                return insets;
            });
        }

        if (drawerLayout != null) {
            drawerLayout.addDrawerListener(new androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
                @Override
                public void onDrawerOpened(View drawerView) {
                    if (tutorialManager != null && tutorialManager.isTutorialRunning()) {
                        tutorialManager.cancelTutorial();
                    }
                    updateStarredMessagesCount();
                }

                @Override
                public void onDrawerClosed(View drawerView) {
                    if (layoutDrawerSearch != null && layoutDrawerSearch.getVisibility() == View.VISIBLE) {
                        layoutDrawerSearch.setVisibility(View.GONE);
                        if (editDrawerSearchChat != null) {
                            editDrawerSearchChat.setText("");
                        }
                    }
                }
            });
        }
        updateStarredMessagesCount();
    }

    private void updateStarredMessagesCount() {
        TextView textStarredBadge = findViewById(R.id.text_starred_badge);
        if (textStarredBadge == null) return;

        safeExecuteBackground(() -> {
            try {
                ChatDatabase database = ChatDatabase.getDatabase(this);
                int count = database.messageDao().getStarredMessagesCount();
                runOnUiThread(() -> {
                    if (count > 0) {
                        textStarredBadge.setText(String.valueOf(count));
                        textStarredBadge.setVisibility(View.VISIBLE);
                    } else {
                        textStarredBadge.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error fetching starred messages count", e);
            }
        });
    }

    private void updateDrawerNavActiveState(int activeTab) {
        View btnDrawerNavModels = findViewById(R.id.btn_drawer_nav_models);
        if (btnDrawerNavModels != null) {
            btnDrawerNavModels.setBackgroundResource(activeTab == TAB_MODELS ?
                    R.drawable.m3_chat_item_active_background : R.drawable.m3_drawer_item_background);
        }
    }

    private void updateDrawerModelStatus() {
        TextView textDrawerModelsBadge = findViewById(R.id.text_drawer_models_badge);
        if (textDrawerModelsBadge != null) {
            safeExecuteBackground(() -> {
                try {
                    List<ModelConfig.Model> allModels = ModelConfig.getAvailableModels(this);
                    int downloadedCount = 0;
                    if (allModels != null) {
                        for (ModelConfig.Model m : allModels) {
                            if (downloadManager != null && downloadManager.isModelDownloaded(m)) {
                                downloadedCount++;
                            }
                        }
                    }
                    final int count = downloadedCount;
                    runOnUiThread(() -> {
                        if (textDrawerModelsBadge != null) {
                            if (count > 0) {
                                textDrawerModelsBadge.setText(count + " Local");
                            } else {
                                textDrawerModelsBadge.setText("Explore");
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.w(TAG, "Error updating drawer models badge", e);
                }
            });
        }
    }

    private void updateQuickThemeUI() {
        TextView textDrawerQuickTheme = findViewById(R.id.text_drawer_quick_theme);
        TextView textDrawerThemeBadge = findViewById(R.id.text_drawer_theme_badge);
        if (textDrawerQuickTheme == null || textDrawerThemeBadge == null) return;

        SettingsManager settingsManager = new SettingsManager(this);
        String themeMode = settingsManager.getThemeMode();
        boolean isOLED = settingsManager.isOLEDBlackTheme();

        if (isOLED) {
            textDrawerQuickTheme.setText("Theme");
            textDrawerThemeBadge.setText("OLED Black");
        } else if ("dark".equals(themeMode)) {
            textDrawerQuickTheme.setText("Theme");
            textDrawerThemeBadge.setText("Dark");
        } else if ("light".equals(themeMode)) {
            textDrawerQuickTheme.setText("Theme");
            textDrawerThemeBadge.setText("Light");
        } else {
            textDrawerQuickTheme.setText("Theme");
            textDrawerThemeBadge.setText("Auto");
        }
    }

    private void toggleQuickTheme() {
        SettingsManager settingsManager = new SettingsManager(this);
        String currentMode = settingsManager.getThemeMode();
        boolean isOLED = settingsManager.isOLEDBlackTheme();

        String nextMode;
        boolean nextOLED = false;

        // Cycle: System -> Light -> Dark -> OLED Black -> System
        if ("system".equals(currentMode)) {
            nextMode = "light";
            nextOLED = false;
        } else if ("light".equals(currentMode)) {
            nextMode = "dark";
            nextOLED = false;
        } else if ("dark".equals(currentMode) && !isOLED) {
            nextMode = "dark";
            nextOLED = true;
        } else {
            // from OLED Black
            nextMode = "system";
            nextOLED = false;
        }

        settingsManager.setThemeMode(nextMode);
        settingsManager.setOLEDBlackTheme(nextOLED);

        com.keralatechreach.mobigpt.utils.ThemeManager themeManager = 
            new com.keralatechreach.mobigpt.utils.ThemeManager(this);
        themeManager.applyTheme();
        updateQuickThemeUI();
        recreate();
    }
    
    /**
     * Setup Pull-to-Refresh functionality
     */
    private void setupSwipeRefresh() {
        if (swipeRefresh == null) {
            Log.w(TAG, "SwipeRefreshLayout not found");
            return;
        }
        
        // Get accent color from theme
        com.keralatechreach.mobigpt.utils.ThemeManager themeManager = 
            new com.keralatechreach.mobigpt.utils.ThemeManager(this);
        int accentColorResId = themeManager.getPrimaryColorResId();
        
        // Set the Material Design colors for the refresh indicator using accent color
        swipeRefresh.setColorSchemeResources(
            accentColorResId,
            R.color.secondary_blue,
            R.color.accent_orange
        );
        
        // Set background color for the refresh indicator
        swipeRefresh.setProgressBackgroundColorSchemeResource(
            themeManager.isDarkMode() ? 
                (themeManager.isOLEDThemeEnabled() ? R.color.oled_surface : R.color.surface_dark) : 
                R.color.white
        );
        
        // Set the refresh listener
        swipeRefresh.setOnRefreshListener(() -> {
            Log.d(TAG, "Pull-to-refresh triggered");
            refreshChat();
        });
        
        Log.d(TAG, "Pull-to-Refresh setup complete");
    }
    
    /**
     * Refresh the current chat by reloading messages from database
     */
    private void refreshChat() {
        // Haptic feedback on pull-to-refresh trigger
        performHapticFeedback(swipeRefresh);
        
        if (chatManager == null) {
            // Stop the refresh animation
            if (swipeRefresh != null) {
                swipeRefresh.setRefreshing(false);
            }
            return;
        }
        
        long currentChatId = chatManager.getCurrentChatId();
        
        if (currentChatId == -1) {
            // No active chat - just stop the refresh animation
            Log.d(TAG, "No active chat to refresh");
            Toast.makeText(this, "No active chat", Toast.LENGTH_SHORT).show();
            if (swipeRefresh != null) {
                swipeRefresh.setRefreshing(false);
            }
            return;
        }
        
        // Reload messages from database
        Log.d(TAG, "Refreshing chat: " + currentChatId);
        
        // Perform refresh on background thread
        new Thread(() -> {
            try {
                // Reload messages (this will call onMessagesLoaded on main thread)
                chatManager.loadMessages(currentChatId);
                
                // Small delay to show the refresh animation (better UX)
                Thread.sleep(300);
                
                // Stop the refresh animation on main thread
                runOnUiThread(() -> {
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                    Toast.makeText(this, "Chat refreshed", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Chat refresh complete");
                });
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing chat", e);
                runOnUiThread(() -> {
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                    Toast.makeText(this, "Refresh failed", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    // MessageAdapter.OnMessageActionListener implementation
    @Override
    public void onMessageCopied(Message message) {
        // Message copied - no additional action needed
        Log.d(TAG, "Message copied: " + message.id);
    }
    
    @Override
    public void onMessageStarred(Message message, boolean isStarred) {
        Log.d(TAG, "Message " + (isStarred ? "starred" : "unstarred") + ": " + message.id);
        updateStarredMessagesCount();
    }
    
    @Override
    public void onMessageRegenerated(Message message) {
        Log.d(TAG, "Regenerating message: " + message.id);
        
        // Find the user message that prompted this AI response
        // We need to get the previous message in the conversation
        if (messageAdapter != null && message.chatId > 0) {
            // Get all messages from the adapter
            List<Message> messages = messageAdapter.getMessages();
            
            // Find the index of the current message
            int messageIndex = -1;
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).id == message.id) {
                    messageIndex = i;
                    break;
                }
            }
            
            // Find the previous user message
            if (messageIndex > 0) {
                for (int i = messageIndex - 1; i >= 0; i--) {
                    Message msg = messages.get(i);
                    if (msg.isUser) {
                        // Found the user message, regenerate response
                        String userPrompt = msg.content;
                        
                        // Delete the current AI response
                        messageAdapter.removeMessage(message);
                        
                        // Regenerate the response using the chat manager
                        if (chatManager != null) {
                            // Check if model is selected and ready
                            if (selectedModel != null) {
                                chatManager.handleSendMessage(userPrompt, selectedModel);
                            } else {
                                Toast.makeText(this, "Please select a model first", Toast.LENGTH_SHORT).show();
                            }
                        }
                        break;
                    }
                }
            } else {
                Toast.makeText(this, "Cannot find original prompt", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    public void onMessageRetried(Message message) {
        Log.d(TAG, "Retrying message: " + message.id);
        
        // Only retry user messages
        if (!message.isUser) {
            Toast.makeText(this, "Can only retry user messages", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get the user's message content
        String userPrompt = message.content;
        
        // Find and remove the AI response that followed this message (if any)
        if (messageAdapter != null && message.chatId > 0) {
            List<Message> messages = messageAdapter.getMessages();
            
            // Find the index of the current user message
            int messageIndex = -1;
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).id == message.id) {
                    messageIndex = i;
                    break;
                }
            }
            
            // Check if there's an AI response after this user message
            if (messageIndex >= 0 && messageIndex < messages.size() - 1) {
                Message nextMessage = messages.get(messageIndex + 1);
                if (!nextMessage.isUser) {
                    // Remove the AI response
                    messageAdapter.removeMessage(nextMessage);
                }
            }
        }
        
        // Resend the user message
        if (chatManager != null && selectedModel != null) {
            chatManager.handleSendMessage(userPrompt, selectedModel);
        } else if (selectedModel == null) {
            Toast.makeText(this, "Please select a model first", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onMessageShared(Message message) {
        // Message shared - log analytics or show feedback
        Log.d(TAG, "onMessageShared: messageId=" + message.id + ", isUser=" + message.isUser + ", contentLength=" + message.content.length());
        // You could track this event in analytics if needed
    }
    
    @Override
    public void onMultipleMessagesShared(List<Message> messages) {
        // Multiple messages shared
        Log.d(TAG, "onMultipleMessagesShared: count=" + messages.size());
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            Log.d(TAG, "onMultipleMessagesShared: [" + i + "] messageId=" + msg.id + ", isUser=" + msg.isUser + ", contentLength=" + msg.content.length());
        }
        Toast.makeText(this, messages.size() + " messages shared", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "onMultipleMessagesShared: Toast shown to user");
    }
    
    @Override
    public void onSelectionModeChanged(boolean isSelectionMode, int selectedCount) {
        Log.d(TAG, "onSelectionModeChanged: isSelectionMode=" + isSelectionMode + ", selectedCount=" + selectedCount);
        
        if (isSelectionMode) {
            Log.d(TAG, "onSelectionModeChanged: Entering selection mode, showing action bar");
            // Show selection action bar
            showSelectionActionBar(selectedCount);
        } else {
            Log.d(TAG, "onSelectionModeChanged: Exiting selection mode, hiding action bar");
            // Hide selection action bar
            hideSelectionActionBar();
        }
    }
    
    /**
     * Show action bar for selection mode
     */
    private void showSelectionActionBar(int count) {
        Log.d(TAG, "showSelectionActionBar: count=" + count);
        Log.d(TAG, "showSelectionActionBar: Thread=" + Thread.currentThread().getName());
        
        if (getSupportActionBar() != null) {
            String title = count + " selected";
            getSupportActionBar().setTitle(title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            Log.d(TAG, "showSelectionActionBar: ActionBar title set to '" + title + "', home button enabled");
        } else {
            Log.w(TAG, "showSelectionActionBar: ActionBar is null!");
        }
        
        // Invalidate options menu to trigger onPrepareOptionsMenu where we handle all menu visibility
        Log.d(TAG, "showSelectionActionBar: Invalidating options menu to update menu items...");
        invalidateOptionsMenu();
        
        // Disable input during selection mode
        if (editMessage != null) {
            editMessage.setEnabled(false);
            editMessage.setHint("Selection mode active - tap messages to select");
            Log.d(TAG, "showSelectionActionBar: Input field disabled with selection hint");
        } else {
            Log.w(TAG, "showSelectionActionBar: editMessage is null");
        }
        
        Log.d(TAG, "showSelectionActionBar: Selection action bar setup complete");
    }
    
    /**
     * Hide action bar for selection mode
     */
    private void hideSelectionActionBar() {
        Log.d(TAG, "hideSelectionActionBar: Hiding selection action bar");
        
        if (getSupportActionBar() != null && toolbarTitle != null) {
            getSupportActionBar().setTitle(null);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            toolbarTitle.setText("MobiGPT");
            Log.d(TAG, "hideSelectionActionBar: ActionBar title cleared, home button disabled, toolbar title reset to 'MobiGPT'");
        } else {
            if (getSupportActionBar() == null) {
                Log.w(TAG, "hideSelectionActionBar: ActionBar is null!");
            }
            if (toolbarTitle == null) {
                Log.w(TAG, "hideSelectionActionBar: toolbarTitle is null!");
            }
        }
        
        // Invalidate options menu to trigger onPrepareOptionsMenu where we handle all menu visibility
        Log.d(TAG, "hideSelectionActionBar: Invalidating options menu to restore normal menu...");
        invalidateOptionsMenu();
        
        // Re-enable input
        if (editMessage != null) {
            editMessage.setEnabled(true);
            editMessage.setHint(R.string.type_message_hint);
            Log.d(TAG, "hideSelectionActionBar: Input field re-enabled with default hint");
        } else {
            Log.w(TAG, "hideSelectionActionBar: editMessage is null");
        }
        
        Log.d(TAG, "hideSelectionActionBar: Selection action bar hidden, normal UI restored");
    }



    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        
        switch (level) {
            case TRIM_MEMORY_RUNNING_MODERATE:
            case TRIM_MEMORY_RUNNING_LOW:
                // Clear unnecessary caches
                if (messageAdapter != null) {
                    messageAdapter.trimMemory();
                }

                break;
            case TRIM_MEMORY_RUNNING_CRITICAL:
            case TRIM_MEMORY_UI_HIDDEN:
            case TRIM_MEMORY_MODERATE:
            case TRIM_MEMORY_BACKGROUND:
            case TRIM_MEMORY_COMPLETE:
                // More aggressive cleanup, but preserve streaming state
                if (messageAdapter != null) {
                    // Only cleanup if not actively streaming
                    if (!messageAdapter.isStreamingActive()) {
                        messageAdapter.cleanup();
                    }
                }
                if (chatHistoryAdapter != null) {
                    chatHistoryAdapter.cleanup();
                }
                if (downloadManager != null) {
                    downloadManager.cleanupOldModels();
                }
                // Force garbage collection
                System.gc();
                break;
        }
    }

    // ===== MENU AND SEARCH FUNCTIONALITY =====
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu: Inflating menu, current optionsMenu=" + (this.optionsMenu != null));
        getMenuInflater().inflate(R.menu.menu_main, menu);
        
        // Store menu reference for later use
        this.optionsMenu = menu;
        
        Log.d(TAG, "onCreateOptionsMenu: Menu inflated, menu size=" + menu.size());
        
        // Show icons in overflow menu
        if (menu != null && menu.getClass().getSimpleName().equals("MenuBuilder")) {
            try {
                java.lang.reflect.Method m = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
                m.setAccessible(true);
                m.invoke(menu, true);
            } catch (Exception e) {
                Log.e(TAG, "Error displaying icons in menu", e);
            }
        }

        // Apply theme-aware text colors to menu items programmatically for better visibility
        applyMenuTextColors(menu);
        
        // Log all menu items
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            Log.d(TAG, "onCreateOptionsMenu: Item " + i + ": id=" + item.getItemId() + 
                  ", title=" + item.getTitle() + ", visible=" + item.isVisible());
        }
        
        MenuItem searchItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) searchItem.getActionView();
        
        if (searchView != null) {
            searchView.setQueryHint("Search messages...");
            searchView.setMaxWidth(Integer.MAX_VALUE);
            
            // Handle search query submission
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    performSearch(query);
                    return true;
                }
                
                @Override
                public boolean onQueryTextChange(String newText) {
                    if (newText == null || newText.trim().isEmpty()) {
                        clearSearch();
                        // Show all history when query is empty
                        if (searchSuggestionAdapter != null) {
                            List<String> allHistory = searchHistoryManager != null ? 
                                searchHistoryManager.getSearchHistory() : new ArrayList<>();
                            searchSuggestionAdapter.updateSuggestions(allHistory);
                        }
                    } else {
                        performSearch(newText);
                        // Update suggestions as user types
                        updateSearchSuggestions(newText);
                    }
                    return true;
                }
            });
            
            // Handle search view expand/collapse
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(@NonNull MenuItem item) {
                    // Save current messages before entering search mode
                    if (messageAdapter != null) {
                        allMessages = new ArrayList<>(messageAdapter.getMessages());
                    }
                    // Setup suggestions when search is opened
                    setupSearchSuggestions();
                    return true;
                }
                
                @Override
                public boolean onMenuItemActionCollapse(@NonNull MenuItem item) {
                    // Exit search mode
                    clearSearch();
                    return true;
                }
            });
        }
        
        if (toolbar != null) {
            toolbar.post(this::setupToolbarOverflowHook);
        }
        
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu: Called, menu size=" + menu.size());
        
        // Apply theme-aware menu colors using unified updater
        com.keralatechreach.mobigpt.utils.ThemeColorUpdater themeUpdater = 
            new com.keralatechreach.mobigpt.utils.ThemeColorUpdater(this);
        themeUpdater.updateMenuColors(menu);
        
        boolean inSelectionMode = messageAdapter != null && messageAdapter.isInSelectionMode();
        int selectedCount = messageAdapter != null ? messageAdapter.getSelectedCount() : 0;
        
        Log.d(TAG, "onPrepareOptionsMenu: In selection mode=" + inSelectionMode + ", selectedCount=" + selectedCount);
        
        // Handle share button visibility based on selection mode
        MenuItem shareItem = menu.findItem(R.id.action_share_selected);
        if (shareItem != null) {
            if (inSelectionMode) {
                shareItem.setVisible(true);
                shareItem.setEnabled(selectedCount > 0);
                if (shareItem.getIcon() != null) {
                    shareItem.getIcon().setAlpha(selectedCount > 0 ? 255 : 128);
                }
                Log.d(TAG, "onPrepareOptionsMenu: Share button set - visible=true, enabled=" + (selectedCount > 0));
            } else {
                shareItem.setVisible(false);
                Log.d(TAG, "onPrepareOptionsMenu: Share button hidden (not in selection mode)");
            }
        } else {
            Log.e(TAG, "onPrepareOptionsMenu: Share item NOT FOUND!");
        }
        
        // Hide/show other menu items based on selection mode and active tab
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            searchItem.setVisible(!inSelectionMode && currentTab != TAB_MODELS);
        }
        
        MenuItem starredItem = menu.findItem(R.id.action_starred_messages);
        if (starredItem != null) {
            starredItem.setVisible(!inSelectionMode);
        }
        
        MenuItem exportItem = menu.findItem(R.id.action_export);
        if (exportItem != null) {
            exportItem.setVisible(!inSelectionMode);
        }
        
        MenuItem settingsItem = menu.findItem(R.id.action_settings);
        if (settingsItem != null) {
            settingsItem.setVisible(!inSelectionMode);
        }
        
        // Log all menu items state after updates
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            Log.d(TAG, "onPrepareOptionsMenu: Item " + i + ": id=" + item.getItemId() + 
                  ", title=" + item.getTitle() + ", visible=" + item.isVisible() + 
                  ", enabled=" + item.isEnabled());
        }
        
        if (toolbar != null) {
            toolbar.post(this::setupToolbarOverflowHook);
        }

        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Log.d(TAG, "onOptionsItemSelected: itemId=" + id);
        
        if (id == android.R.id.home) {
            Log.d(TAG, "onOptionsItemSelected: Home button clicked");
            // Handle back button in action bar (exit selection mode)
            if (messageAdapter != null && messageAdapter.isInSelectionMode()) {
                Log.d(TAG, "onOptionsItemSelected: Exiting selection mode via home button");
                messageAdapter.exitSelectionMode();
                return true;
            }
        } else if (id == R.id.action_share_selected) {
            Log.d(TAG, "onOptionsItemSelected: Share selected messages clicked");
            // Share selected messages
            if (messageAdapter != null) {
                int selectedCount = messageAdapter.getSelectedCount();
                Log.d(TAG, "onOptionsItemSelected: Sharing " + selectedCount + " selected messages");
                messageAdapter.shareSelectedMessages();
            } else {
                Log.w(TAG, "onOptionsItemSelected: messageAdapter is null, cannot share");
            }
            return true;
        } else if (id == R.id.action_starred_messages) {
            Log.d(TAG, "onOptionsItemSelected: Starred messages clicked");
            // Open starred messages activity
            Intent intent = new Intent(this, StarredMessagesActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_download_from_url) {
            Log.d(TAG, "onOptionsItemSelected: Download from URL clicked");
            // Show custom URL download dialog
            showCustomUrlDownloadDialog();
            return true;
        } else if (id == R.id.action_export) {
            Log.d(TAG, "onOptionsItemSelected: Export clicked");
            // Export conversation
            showExportDialog();
            return true;
        } else if (id == R.id.action_settings) {
            Log.d(TAG, "onOptionsItemSelected: Settings clicked");
            // Open settings activity
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        }
        
        Log.d(TAG, "onOptionsItemSelected: Delegating to super for unhandled itemId=" + id);
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void openOptionsMenu() {
        if (toolbar != null) {
            View overflowBtn = findToolbarOverflowButton();
            if (overflowBtn != null) {
                showModernToolbarMenu(overflowBtn);
                return;
            }
        }
        super.openOptionsMenu();
    }

    private View findToolbarOverflowButton() {
        if (toolbar == null) return null;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof androidx.appcompat.widget.ActionMenuView) {
                androidx.appcompat.widget.ActionMenuView amv = (androidx.appcompat.widget.ActionMenuView) child;
                for (int j = 0; j < amv.getChildCount(); j++) {
                    View actionView = amv.getChildAt(j);
                    ViewGroup.LayoutParams lp = actionView.getLayoutParams();
                    if (lp instanceof androidx.appcompat.widget.ActionMenuView.LayoutParams) {
                        if (((androidx.appcompat.widget.ActionMenuView.LayoutParams) lp).isOverflowButton) {
                            return actionView;
                        }
                    }
                    String className = actionView.getClass().getName();
                    if (className.contains("OverflowMenuButton") || className.contains("ActionMenuPresenter$Overflow")) {
                        return actionView;
                    }
                }
            }
        }
        return null;
    }

    private void setupToolbarOverflowHook() {
        if (toolbar == null || isFinishing() || isDestroyed()) return;
        View overflowBtn = findToolbarOverflowButton();
        if (overflowBtn != null) {
            attachOverflowClickListener(overflowBtn);
        } else {
            toolbar.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                View btn = findToolbarOverflowButton();
                if (btn != null) {
                    attachOverflowClickListener(btn);
                }
            });
        }
    }

    private void attachOverflowClickListener(View overflowBtn) {
        overflowBtn.setOnTouchListener(null);
        overflowBtn.setOnClickListener(v -> {
            performHapticFeedback(v);
            showModernToolbarMenu(v);
        });
        Log.d(TAG, "attachOverflowClickListener: Successfully hooked modern toolbar menu to " + overflowBtn);
    }

    private void showModernToolbarMenu(View anchor) {
        if (isFinishing() || isDestroyed()) return;
        if (toolbarMenuPopup != null && toolbarMenuPopup.isShowing()) {
            toolbarMenuPopup.dismiss();
            return;
        }

        View popupView = LayoutInflater.from(this).inflate(R.layout.toolbar_overflow_menu, null);
        int width = (int) (240 * getResources().getDisplayMetrics().density);
        toolbarMenuPopup = new PopupWindow(
            popupView,
            width,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        );
        toolbarMenuPopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        toolbarMenuPopup.setOutsideTouchable(true);
        toolbarMenuPopup.setFocusable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            toolbarMenuPopup.setElevation(16f);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            toolbarMenuPopup.setIsClippedToScreen(true);
        }

        // Starred Messages count badge
        TextView badgeStarred = popupView.findViewById(R.id.badge_menu_starred);
        if (badgeStarred != null) {
            safeExecuteBackground(() -> {
                try {
                    ChatDatabase database = ChatDatabase.getDatabase(this);
                    int count = database.messageDao().getStarredMessagesCount();
                    runOnUiThread(() -> {
                        if (badgeStarred != null && !isFinishing() && !isDestroyed()) {
                            if (count > 0) {
                                badgeStarred.setText(String.valueOf(count));
                                badgeStarred.setVisibility(View.VISIBLE);
                            } else {
                                badgeStarred.setVisibility(View.GONE);
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.w(TAG, "Error loading starred count for menu", e);
                }
            });
        }

        View btnStarred = popupView.findViewById(R.id.btn_menu_starred);
        if (btnStarred != null) {
            btnStarred.setOnClickListener(v -> {
                performHapticFeedback(v);
                if (toolbarMenuPopup != null) toolbarMenuPopup.dismiss();
                Intent intent = new Intent(this, StarredMessagesActivity.class);
                startActivity(intent);
            });
        }

        View btnExport = popupView.findViewById(R.id.btn_menu_export);
        if (btnExport != null) {
            btnExport.setOnClickListener(v -> {
                performHapticFeedback(v);
                if (toolbarMenuPopup != null) toolbarMenuPopup.dismiss();
                showExportDialog();
            });
        }

        View btnDownloadUrl = popupView.findViewById(R.id.btn_menu_download_from_url);
        if (btnDownloadUrl != null) {
            btnDownloadUrl.setOnClickListener(v -> {
                performHapticFeedback(v);
                if (toolbarMenuPopup != null) toolbarMenuPopup.dismiss();
                showCustomUrlDownloadDialog();
            });
        }

        View btnSettings = popupView.findViewById(R.id.btn_menu_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                performHapticFeedback(v);
                if (toolbarMenuPopup != null) toolbarMenuPopup.dismiss();
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
            });
        }

        View targetAnchor = anchor != null ? anchor : toolbar;
        if (targetAnchor != null) {
            int xOffset = (int) (-8 * getResources().getDisplayMetrics().density);
            int yOffset = (int) (4 * getResources().getDisplayMetrics().density);
            androidx.core.widget.PopupWindowCompat.showAsDropDown(toolbarMenuPopup, targetAnchor, xOffset, yOffset, Gravity.END);
        }
    }
    
    /**
     * Perform search on messages
     */
    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            clearSearch();
            return;
        }
        
        Log.d(TAG, "Performing search for: " + query);
        
        // Save search query to history if enabled
        if (searchHistoryManager != null && searchHistoryManager.isSearchHistoryEnabled()) {
            searchHistoryManager.addSearchQuery(query);
        }
        
        // Set search query in adapter for highlighting
        if (messageAdapter != null) {
            messageAdapter.setSearchQuery(query);
        }
        
        // If we're in the current chat, just highlight messages
        // For global search across all chats, we'd need to query the database
        if (chatManager != null && chatManager.getCurrentChatId() != -1) {
            // Search in current chat
            searchInCurrentChat(query);
        } else {
            // No active chat - search globally
            searchGlobally(query);
        }
    }
    
    /**
     * Search in the currently active chat
     */
    private void searchInCurrentChat(String query) {
        if (messageAdapter == null) return;
        Log.d(TAG, "searchInCurrentChat: " + query);
        
        // The adapter already has the messages, just apply highlighting
        // Count matches
        int matchCount = messageAdapter.getMatchCount();
        
        if (matchCount > 0) {
            // Jump to first match
            int firstMatch = messageAdapter.findNextMatch(-1);
            if (firstMatch != -1) {
                currentSearchMatchIndex = firstMatch;
                scrollToPosition(firstMatch, true);
                
                // Show match count in toolbar
                if (toolbarTitle != null) {
                    toolbarTitle.setText(matchCount + " match" + (matchCount != 1 ? "es" : ""));
                }
            }
        } else {
            Toast.makeText(this, "No matches found", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Search globally across all chats
     */
    private void searchGlobally(String query) {
        if (chatManager == null) return;
        
        // Execute search in background
        safeExecuteBackground(() -> {
            try {
                List<Message> results = chatManager.searchMessages(query);
                
                runOnUiThread(() -> {
                    if (results != null && !results.isEmpty()) {
                        searchResults = results;
                        
                        // Show results in adapter
                        if (messageAdapter != null) {
                            messageAdapter.setMessages(results);
                            messageAdapter.setSearchQuery(query);
                        }
                        
                        // Update toolbar
                        if (toolbarTitle != null) {
                            toolbarTitle.setText(results.size() + " match" + (results.size() != 1 ? "es" : ""));
                        }
                        
                        // Hide empty state if showing
                        hideEmptyState();
                    } else {
                        runOnUiThread(() -> Toast.makeText(this, "No messages found", Toast.LENGTH_SHORT).show());
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error searching messages", e);
                runOnUiThread(() -> Toast.makeText(this, "Search error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }
    
    /**
     * Clear search and restore normal view
     */
    private void clearSearch() {
        Log.d(TAG, "Clearing search");
        
        // Clear search query in adapter
        if (messageAdapter != null) {
            messageAdapter.clearSearch();
            
            // Restore original messages if we were in search mode
            if (!allMessages.isEmpty() && chatManager != null) {
                // Reload current chat messages
                long currentChatId = chatManager.getCurrentChatId();
                if (currentChatId != -1) {
                    chatManager.loadMessages(currentChatId);
                } else {
                    // No active chat, clear messages
                    messageAdapter.setMessages(new ArrayList<>());
                    showEmptyState();
                }
            }
        }
        
        // Reset search state
        searchResults.clear();
        currentSearchMatchIndex = -1;
        allMessages.clear();
        
        // Restore toolbar title
        if (toolbarTitle != null && chatManager != null) {
            Chat currentChat = chatManager.getCurrentChat();
            if (currentChat != null) {
                toolbarTitle.setText(currentChat.title);
            } else {
                toolbarTitle.setText("MobiGPT");
            }
        }
        
        // Collapse search view if expanded
        if (searchView != null && !searchView.isIconified()) {
            searchView.setIconified(true);
        }
    }
    
    /**
     * Setup search suggestions from history
     */
    @SuppressLint("DiscouragedApi")
    private void setupSearchSuggestions() {
        if (searchHistoryManager == null || !searchHistoryManager.isSearchHistoryEnabled() || searchView == null) {
            Log.d(TAG, "Search suggestions setup skipped - manager=" + (searchHistoryManager != null) + 
                  ", enabled=" + (searchHistoryManager != null && searchHistoryManager.isSearchHistoryEnabled()) + 
                  ", searchView=" + (searchView != null));
            return;
        }
        
        // Get search history
        List<String> history = searchHistoryManager.getSearchHistory();
        Log.d(TAG, "Setting up search suggestions with " + history.size() + " history items");
        
        // Create suggestions adapter
        searchSuggestionAdapter = new SearchSuggestionAdapter(this, history);
        
        // Get the AutoCompleteTextView from SearchView
        try {
            // Try multiple methods to find the AutoCompleteTextView
            android.widget.AutoCompleteTextView searchAutoComplete = null;
            
            // Method 1: Use androidx.appcompat.R.id
            try {
                int searchTextId = androidx.appcompat.R.id.search_src_text;
                searchAutoComplete = searchView.findViewById(searchTextId);
                Log.d(TAG, "Method 1 (androidx.appcompat.R.id): searchAutoComplete=" + (searchAutoComplete != null));
            } catch (Exception e) {
                Log.w(TAG, "Method 1 failed: " + e.getMessage());
            }
            
            // Method 2: Use getIdentifier with proper parameters
            if (searchAutoComplete == null) {
                try {
                    int searchTextId = getResources().getIdentifier("search_src_text", "id", "android");
                    if (searchTextId != 0) {
                        searchAutoComplete = searchView.findViewById(searchTextId);
                        Log.d(TAG, "Method 2 (getIdentifier): searchAutoComplete=" + (searchAutoComplete != null));
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Method 2 failed: " + e.getMessage());
                }
            }
            
            // Method 3: Try to find by traversing the view hierarchy
            if (searchAutoComplete == null) {
                try {
                    searchAutoComplete = findAutoCompleteTextView(searchView);
                    Log.d(TAG, "Method 3 (hierarchy search): searchAutoComplete=" + (searchAutoComplete != null));
                } catch (Exception e) {
                    Log.w(TAG, "Method 3 failed: " + e.getMessage());
                }
            }
            
            if (searchAutoComplete != null) {
                // Set the adapter
                searchAutoComplete.setAdapter(searchSuggestionAdapter);
                searchAutoComplete.setThreshold(1); // Show suggestions after 1 character
                searchAutoComplete.setDropDownBackgroundResource(R.drawable.dropdown_list_background);
                
                // Handle suggestion selection
                searchAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
                    String selectedSuggestion = searchSuggestionAdapter.getItem(position);
                    if (selectedSuggestion != null) {
                        Log.d(TAG, "Suggestion selected: " + selectedSuggestion);
                        searchView.setQuery(selectedSuggestion, true); // Submit the query
                    }
                });
                
                Log.d(TAG, "Search suggestions initialized successfully with " + history.size() + " items");
            } else {
                Log.e(TAG, "Could not find AutoCompleteTextView in SearchView using any method");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up search suggestions", e);
        }
    }
    
    /**
     * Helper method to find AutoCompleteTextView by traversing view hierarchy
     */
    private android.widget.AutoCompleteTextView findAutoCompleteTextView(View view) {
        if (view instanceof android.widget.AutoCompleteTextView) {
            return (android.widget.AutoCompleteTextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.AutoCompleteTextView result = findAutoCompleteTextView(group.getChildAt(i));
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
    
    /**
     * Update search suggestions based on current query
     */
    private void updateSearchSuggestions(String query) {
        if (searchHistoryManager == null || !searchHistoryManager.isSearchHistoryEnabled()) {
            Log.d(TAG, "updateSearchSuggestions: Skipped - manager=" + (searchHistoryManager != null) + 
                  ", enabled=" + (searchHistoryManager != null && searchHistoryManager.isSearchHistoryEnabled()));
            return;
        }
        
        if (searchSuggestionAdapter == null) {
            Log.w(TAG, "updateSearchSuggestions: Adapter is null, setting up suggestions");
            setupSearchSuggestions();
            return;
        }
        
        // Get matching suggestions
        List<String> suggestions = searchHistoryManager.getSuggestionsContaining(query);
        
        // Update adapter
        searchSuggestionAdapter.updateSuggestions(suggestions);
        
        // Log for debugging
        Log.d(TAG, "Updated suggestions: " + suggestions.size() + " matches for query: '" + query + "'");
        if (!suggestions.isEmpty()) {
            Log.d(TAG, "First few suggestions: " + suggestions.subList(0, Math.min(3, suggestions.size())));
        }
    }
    
    /**
     * Scroll to a specific position with optional smooth scrolling
     */
    private void scrollToPosition(int position, boolean smooth) {
        if (recyclerMessages == null) return;
        
        isProgrammaticScroll = true;
        
        if (smooth) {
            recyclerMessages.smoothScrollToPosition(position);
        } else {
            recyclerMessages.scrollToPosition(position);
        }
        
        // Reset flag after a short delay
        mainHandler.postDelayed(() -> isProgrammaticScroll = false, 500);
    }
    
    // ===== END MENU AND SEARCH FUNCTIONALITY =====
    // ===== EXPORT FUNCTIONALITY =====
    
    /**
     * Show export dialog with options
     */
    private void showExportDialog() {
        // Check if there's a conversation to export
        if (chatManager == null || chatManager.getCurrentChatId() == -1) {
            Toast.makeText(this, R.string.no_conversation_to_export, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get current messages
        List<Message> messages = messageAdapter != null ? messageAdapter.getMessages() : new ArrayList<>();
        if (messages.isEmpty()) {
            Toast.makeText(this, R.string.no_conversation_to_export, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.choose_export_format);
        
        String[] options = {
            getString(R.string.export_as_markdown),
            getString(R.string.export_as_pdf),
            getString(R.string.share_conversation)
        };
        
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Export as Markdown
                    exportConversation("markdown");
                    break;
                case 1: // Export as PDF
                    exportConversation("pdf");
                    break;
                case 2: // Share
                    showShareDialog();
                    break;
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    /**
     * Show share dialog to choose format for sharing
     */
    private void showShareDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Share as");
        
        String[] options = {"Markdown", "PDF"};
        
        builder.setItems(options, (dialog, which) -> {
            String format = which == 0 ? "markdown" : "pdf";
            exportAndShare(format);
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    /**
     * Request authentication for export operations
     */
    private void requestAuthenticationForExport() {
        if (securityManager == null || !securityManager.isLockSetupComplete()) {
            // No PIN/biometric set up, proceed without authentication
            performPendingExport();
            return;
        }
        
        // Check if biometric is available and preferred
        String lockType = securityManager.getLockType();
        if ("biometric".equals(lockType) && securityManager.isBiometricAvailable(this)) {
            // Show biometric prompt
            securityManager.showBiometricPrompt(this, "Export Authentication", 
                "Authenticate to export conversation", 
                new com.keralatechreach.mobigpt.security.SecurityManager.BiometricAuthCallback() {
                    @Override
                    public void onAuthenticationSucceeded() {
                        performPendingExport();
                    }
                    
                    @Override
                    public void onAuthenticationFailed() {
                        Toast.makeText(MainActivity.this, "Authentication failed", Toast.LENGTH_SHORT).show();
                        clearPendingExport();
                    }
                    
                    @Override
                    public void onAuthenticationError(int errorCode, String errString) {
                        //User cancelled or error - show PIN screen
                        if (errorCode == androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            // Show PIN authentication screen
                            showPinAuthenticationForExport();
                        } else {
                            Toast.makeText(MainActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                            clearPendingExport();
                        }
                    }
                });
        } else {
            // Use PIN authentication
            showPinAuthenticationForExport();
        }
    }
    
    /**
     * Show PIN authentication screen for export
     */
    private void showPinAuthenticationForExport() {
        Intent intent = new Intent(this, com.keralatechreach.mobigpt.security.LockScreenActivity.class);
        intent.putExtra(com.keralatechreach.mobigpt.security.LockScreenActivity.EXTRA_MODE, 
            com.keralatechreach.mobigpt.security.LockScreenActivity.MODE_UNLOCK);
        startActivityForResult(intent, REQUEST_CODE_AUTH_FOR_EXPORT);
    }
    
    /**
     * Perform the pending export operation after authentication
     */
    private void performPendingExport() {
        if (pendingExportFormat == null) {
            return;
        }
        
        String format = pendingExportFormat;
        boolean isShare = pendingExportIsShare;
        
        // Clear pending export
        clearPendingExport();
        
        // Perform the export
        if (isShare) {
            exportAndShareInternal(format);
        } else {
            exportConversationInternal(format);
        }
    }
    
    /**
     * Clear pending export operation
     */
    private void clearPendingExport() {
        pendingExportFormat = null;
        pendingExportIsShare = false;
    }
    
    /**
     * Internal method to export conversation without authentication check
     */
    private void exportConversationInternal(String format) {
        if (chatManager == null || chatManager.getCurrentChatId() == -1) {
            Toast.makeText(this, R.string.no_conversation_to_export, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get messages from adapter (on main thread)
        final List<Message> messages = messageAdapter != null ? new ArrayList<>(messageAdapter.getMessages()) : new ArrayList<>();
        
        if (messages.isEmpty()) {
            Toast.makeText(this, R.string.no_conversation_to_export, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show progress toast
        Toast.makeText(this, R.string.exporting, Toast.LENGTH_SHORT).show();
        
        final long chatId = chatManager.getCurrentChatId();
        
        // Export in background thread
        if (!safeExecuteBackground(() -> {
            android.net.Uri exportedUri = null;
            
            try {
                // Get chat from database (on background thread)
                Chat currentChat = chatManager.getDatabase().chatDao().getChatById(chatId);
                
                if (currentChat == null) {
                    runOnUiThread(() -> 
                        Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
                    );
                    return;
                }
                
                if ("markdown".equals(format)) {
                    exportedUri = ExportHelper.exportToMarkdown(this, currentChat, messages);
                } else if ("pdf".equals(format)) {
                    exportedUri = ExportHelper.exportToPDF(this, currentChat, messages);
                }
                
                final android.net.Uri finalUri = exportedUri;
                runOnUiThread(() -> {
                    if (finalUri != null) {
                        String message = getString(R.string.export_successful);
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show();
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error exporting conversation", e);
                runOnUiThread(() -> 
                    Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
                );
            }
        })) {
            // Task was not submitted successfully
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Internal method to export and share conversation without authentication check
     */
    private void exportAndShareInternal(String format) {
        if (chatManager == null || chatManager.getCurrentChatId() == -1) {
            Toast.makeText(this, R.string.no_conversation_to_export, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Get messages from adapter (on main thread)
        final List<Message> messages = messageAdapter != null ? new ArrayList<>(messageAdapter.getMessages()) : new ArrayList<>();
        
        if (messages.isEmpty()) {
            Toast.makeText(this, R.string.no_conversation_to_export, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show progress toast
        Toast.makeText(this, R.string.exporting, Toast.LENGTH_SHORT).show();
        
        final long chatId = chatManager.getCurrentChatId();
        
        // Export and share in background thread
        if (!safeExecuteBackground(() -> {
            android.net.Uri exportedUri = null;
            String mimeType = null;
            
            try {
                // Get chat from database (on background thread)
                Chat currentChat = chatManager.getDatabase().chatDao().getChatById(chatId);
                
                if (currentChat == null) {
                    runOnUiThread(() -> 
                        Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
                    );
                    return;
                }
                
                if ("markdown".equals(format)) {
                    exportedUri = ExportHelper.exportToMarkdown(this, currentChat, messages);
                    mimeType = "text/markdown";
                } else if ("pdf".equals(format)) {
                    exportedUri = ExportHelper.exportToPDF(this, currentChat, messages);
                    mimeType = "application/pdf";
                }
                
                if (exportedUri != null) {
                    final android.net.Uri finalUri = exportedUri;
                    final String finalMimeType = mimeType;
                    
                    runOnUiThread(() -> ExportHelper.shareFile(this, finalUri, finalMimeType));
                } else {
                    runOnUiThread(() -> 
                        Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
                    );
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error exporting and sharing conversation", e);
                runOnUiThread(() -> 
                    Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
                );
            }
        })) {
            // Task was not submitted successfully
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Export conversation to file
     */
    private void exportConversation(String format) {
        // Check if authentication is required for export
        if (securityManager != null && securityManager.isAuthRequiredForExport() && 
            securityManager.isLockSetupComplete()) {
            // Store pending export and request authentication
            pendingExportFormat = format;
            pendingExportIsShare = false;
            requestAuthenticationForExport();
            return;
        }
        
        // No authentication required, proceed with export
        exportConversationInternal(format);
    }
    
    /**
     * Export and share conversation
     */
    private void exportAndShare(String format) {
        // Check if authentication is required for export
        if (securityManager != null && securityManager.isAuthRequiredForExport() && 
            securityManager.isLockSetupComplete()) {
            // Store pending export and request authentication
            pendingExportFormat = format;
            pendingExportIsShare = true;
            requestAuthenticationForExport();
            return;
        }
        
        // No authentication required, proceed with export
        exportAndShareInternal(format);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_AUTH_FOR_EXPORT) {
            if (resultCode == RESULT_OK) {
                // Authentication successful, perform pending export
                performPendingExport();
            } else {
                // Authentication failed or cancelled
                Toast.makeText(this, "Authentication cancelled", Toast.LENGTH_SHORT).show();
                clearPendingExport();
            }
        } else if (requestCode == REQUEST_CODE_PICK_GGUF) {
            if (resultCode == RESULT_OK && data != null) {
                android.net.Uri uri = data.getData();
                if (uri != null) {
                    handleImportGgufFileUri(uri);
                }
            }
        } else if (requestCode == REQUEST_CODE_HUGGINGFACE_HUB) {
            if (resultCode == RESULT_OK && data != null) {
                String modelName = data.getStringExtra(com.keralatechreach.mobigpt.huggingface.HuggingFaceHubActivity.EXTRA_MODEL_NAME);
                String downloadUrl = data.getStringExtra(com.keralatechreach.mobigpt.huggingface.HuggingFaceHubActivity.EXTRA_DOWNLOAD_URL);
                String explicitFileName = data.getStringExtra(com.keralatechreach.mobigpt.huggingface.HuggingFaceHubActivity.EXTRA_FILE_NAME);
                if (modelName != null && downloadUrl != null) {
                    downloadModelFromCustomUrl(modelName, downloadUrl, explicitFileName);
                }
            }
        }
    }
    
    /**
     * Helper method to perform haptic feedback if enabled in settings
     */
    private void performHapticFeedback(View view) {
        if (settingsManager != null && settingsManager.isHapticFeedback() && view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }
    
    /**
     * Apply theme-aware text colors to menu items for dark theme visibility
     */
    private void applyMenuTextColors(Menu menu) {
        if (menu == null) return;
        
        // Get theme-aware text color using textColorPrimary for popup menus
        // This ensures black text on light theme and white text on dark theme
        int textColor;
        android.content.res.TypedArray ta = obtainStyledAttributes(new int[]{
            android.R.attr.textColorPrimary
        });
        try {
            textColor = ta.getColor(0, ContextCompat.getColor(this, android.R.color.black));
        } finally {
            ta.recycle();
        }
        
        // Apply text color to all menu items
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item != null) {
                // Apply to the title
                android.text.SpannableString spanString = new android.text.SpannableString(item.getTitle());
                spanString.setSpan(new android.text.style.ForegroundColorSpan(textColor), 0, spanString.length(), 0);
                item.setTitle(spanString);
                
                Log.d(TAG, "Applied text color to menu item: " + item.getTitle() + " (color: " + Integer.toHexString(textColor) + ")");
            }
        }
    }

    /**
     * Show tutorial for first-time users
     */
    private void showTutorialIfNeeded() {
        if (tutorialManager == null || tutorialManager.isTutorialCompleted()) {
            return;
        }
        
        // Start the interactive tutorial directly after brief delay for UI to settle
        new Handler(Looper.getMainLooper()).postDelayed(this::startInteractiveTutorial, 500);
    }
    
    /**
     * Automatically open model dropdown if:
     * 1. Onboarding is completed
     * 2. Interactive tutorial is completed
     * 3. No model is currently loaded
     * 4. Has not already auto-opened previously
     * 5. Drawer (sidebar) is not open
     */
    private void autoOpenModelDropdownIfNeeded() {
        // Check if onboarding and tutorial are completed
        boolean onboardingCompleted = OnboardingActivity.isOnboardingCompleted(this);
        boolean tutorialCompleted = tutorialManager != null && tutorialManager.isTutorialCompleted();
        
        // Check if model is loaded
        boolean modelLoaded = chatManager != null && chatManager.isAIModelLoaded();
        
        Log.d(TAG, "Auto-open dropdown check - Onboarding: " + onboardingCompleted + 
                   ", Tutorial: " + tutorialCompleted + ", Model loaded: " + modelLoaded);
        
        // Auto-open dropdown if onboarding/tutorial done but no model loaded
        if (onboardingCompleted && tutorialCompleted && !modelLoaded) {
            // Guard: Only auto-open once across app lifecycle
            if (tutorialManager != null && tutorialManager.hasAutoOpenedModelDropdown()) {
                Log.d(TAG, "Model dropdown already auto-opened previously, skipping");
                return;
            }
            // Guard: Do not auto-open if drawer (sidebar) is open
            if (drawerLayout != null && findViewById(R.id.nav_view) != null && 
                drawerLayout.isDrawerOpen(findViewById(R.id.nav_view))) {
                Log.d(TAG, "Drawer is currently open, skipping auto-open model dropdown");
                return;
            }

            if (tutorialManager != null) {
                tutorialManager.markModelDropdownAutoOpened();
            }

            // Wait a bit for UI to settle, then open dropdown
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                boolean isDrawerOpen = drawerLayout != null && findViewById(R.id.nav_view) != null &&
                        drawerLayout.isDrawerOpen(findViewById(R.id.nav_view));
                // Check if activity is still valid and drawer is NOT open before showing popup
                if (!isFinishing() && !isDestroyed() && !isDrawerOpen && selectedModelDisplay != null) {
                    Log.d(TAG, "Auto-opening model dropdown");
                    showInlineModelSelection();
                } else {
                    Log.w(TAG, "Cannot auto-open dropdown - activity not ready, finishing, or drawer open");
                }
            }, 800);
        }
    }
    
    /**
     * Start the interactive tutorial sequence
     */
    private void startInteractiveTutorial() {
        // Get the hamburger menu button
        ImageButton btnHamburger = findViewById(R.id.btn_hamburger_menu);
        
        // Ensure all views are available
        if (selectedModelDisplay == null || editMessage == null || btnSend == null || 
            btnHamburger == null || toolbar == null) {
            Log.w(TAG, "Tutorial skipped - required views not found");
            tutorialManager.markTutorialCompleted();
            return;
        }
        
        // Start the tutorial with all required views and callback for completion
        tutorialManager.startTutorial(
            selectedModelDisplay,
            downloadProgressLayout,
            editMessage,
            btnSend,
            btnHamburger,
            toolbar,
            // Callback when tutorial completes or ends - ask for notification permission and auto-open model dropdown
            () -> {
                Log.d(TAG, "Tutorial finished/dismissed, requesting notification permission and auto-opening model dropdown");
                // Immediately ask for notification permission after tutorial ends
                requestNotificationPermission();

                // Guard: Only auto-open once across app lifecycle
                if (tutorialManager != null && tutorialManager.hasAutoOpenedModelDropdown()) {
                    Log.d(TAG, "Model dropdown was already auto-opened, skipping tutorial auto-open");
                    return;
                }
                if (tutorialManager != null) {
                    tutorialManager.markModelDropdownAutoOpened();
                }

                // Wait a bit for tutorial UI to clear, then open dropdown
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    boolean isDrawerOpen = drawerLayout != null && findViewById(R.id.nav_view) != null &&
                            drawerLayout.isDrawerOpen(findViewById(R.id.nav_view));
                    // Check if activity is still valid and drawer is NOT open before showing popup
                    if (!isFinishing() && !isDestroyed() && !isDrawerOpen && selectedModelDisplay != null && 
                        chatManager != null && !chatManager.isAIModelLoaded()) {
                        showInlineModelSelection();
                    } else {
                        Log.w(TAG, "Cannot auto-open dropdown after tutorial - activity not ready, drawer open, or model already loaded");
                    }
                }, 1000);
            }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Unregister notification settings receiver
        if (notificationSettingsReceiver != null) {
            try {
                unregisterReceiver(notificationSettingsReceiver);
                Log.d(TAG, "Notification settings receiver unregistered");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver not registered: " + e.getMessage());
            }
            notificationSettingsReceiver = null;
        }
        
        // Stop WiFi monitoring if active
        stopWiFiMonitoring();
        
        // Dismiss WiFi waiting dialog if showing
        if (wiFiWaitingDialog != null && wiFiWaitingDialog.isShowing()) {
            wiFiWaitingDialog.dismiss();
            wiFiWaitingDialog = null;
        }
        
        // Shutdown background executor
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            try {
                // Shutdown gracefully and cancel pending tasks
                backgroundExecutor.shutdownNow();
                // Wait a short time for tasks to terminate
                if (!backgroundExecutor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    Log.w(TAG, "Background executor did not terminate in time");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for executor shutdown", e);
                // Force shutdown if interrupted
                backgroundExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            } finally {
                backgroundExecutor = null;
            }
        }
        
        // Hide progress bar if showing
        if (modelLoadingProgressLayout != null && modelLoadingProgressLayout.getVisibility() == View.VISIBLE) {
            modelLoadingProgressLayout.setVisibility(View.GONE);
        }
        
        // Enhanced cleanup
        if (messageAdapter != null) {
            messageAdapter.cleanup();
            messageAdapter = null;
        }
        

        
        if (chatHistoryAdapter != null) {
            chatHistoryAdapter.cleanup();
            chatHistoryAdapter = null;
        }
        if (chatManager != null) {
            chatManager.cleanup();
            chatManager = null;
        }
        if (downloadManager != null) {
            downloadManager.setDownloadListener(null);
            downloadManager = null;
        }
        
        if (tutorialManager != null) {
            tutorialManager.cancelTutorial();
            tutorialManager = null;
        }

        if (modelHubBottomSheetDialog != null) {
            if (modelHubBottomSheetDialog.isShowing()) {
                modelHubBottomSheetDialog.dismiss();
            }
            modelHubBottomSheetDialog = null;
        }

        if (toolbarMenuPopup != null) {
            if (toolbarMenuPopup.isShowing()) {
                toolbarMenuPopup.dismiss();
            }
            toolbarMenuPopup = null;
        }
        
        // Clear view references to prevent memory leaks
        drawerLayout = null;
        toolbar = null;
        toolbarTitle = null;
        recyclerMessages = null;
        recyclerChatHistory = null;
        editMessage = null;
        btnSend = null;
        btnNewChat = null;
        emptyState = null;
        swipeRefresh = null;
        downloadProgressLayout = null;
        progressDownload = null;
        textDownloadStatus = null;
        textDownloadDetails = null;
        btnPauseResumeDownload = null;
        btnCancelDownload = null;
        inlineModelSelectionContainer = null;
        recyclerModelSelection = null;
        editSearchModel = null;
        btnCloseModelSelection = null;
        btnClearSearch = null;
        chipFilterAll = null;
        chipFilterDownloaded = null;
        chipFilterCompatible = null;
        chipFilterCoding = null;
        chipFilterCompact = null;
        layoutModelEmptyState = null;
        btnEmptyResetFilter = null;
        btnDownloadFromUrl = null;
        selectedModelDisplay = null;
        textSelectedModelName = null;
        textSelectedModelDetails = null;
        textSelectedModelStatus = null;
        iconDropdownArrow = null;
        mainChatIcon = null;
        textChatModelAvatar = null;
        textModelCountBadge = null;
        textRamAdvisorHint = null;
        badgeMemoryStatus = null;
        chatContentContainer = null;
        modelsHubContainer = null;
        bottomNavigationBar = null;
        modelListAdapter = null;
        mainHandler = null;
        
        Log.d(TAG, "MainActivity destroyed with proper cleanup");
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, retry download
                downloadModel();
            } else {
                // Permission denied
                Toast.makeText(this, "Storage permission is required to download models", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == PERMISSION_REQUEST_NOTIFICATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission granted");
            } else {
                Log.d(TAG, "Notification permission denied");
            }
        }
    }
    

    
    /**
     * RecyclerView adapter for model list in the dropdown with Material card design
     */
    private class ModelListAdapter extends RecyclerView.Adapter<ModelListAdapter.ModelViewHolder> {
        private List<ModelConfig.Model> allModels;
        private List<ModelConfig.Model> filteredModels;
        private String currentModelName = null;

        public ModelListAdapter(List<ModelConfig.Model> models) {
            this.allModels = new ArrayList<>(models);
            this.filteredModels = new ArrayList<>(models);
            updateModelFilterChips(this.allModels);
        }

        public void updateModels(List<ModelConfig.Model> models) {
            this.allModels = new ArrayList<>(models);
            updateModelFilterChips(this.allModels);
            filter(editSearchModel != null ? editSearchModel.getText().toString() : "", activeCategoryFilter);
        }

        public void setCurrentModel(String modelName) {
            this.currentModelName = modelName;
            notifyDataSetChanged();
        }

        public void filter(String query) {
            filter(query, activeCategoryFilter);
        }

        public void filter(String query, String category) {
            filteredModels.clear();
            String lowerQuery = (query != null) ? query.trim().toLowerCase() : "";

            for (ModelConfig.Model model : allModels) {
                // Category match
                boolean matchesCategory = true;
                if ("downloaded".equalsIgnoreCase(category)) {
                    matchesCategory = downloadManager != null && downloadManager.isModelDownloaded(model);
                } else if ("coding".equalsIgnoreCase(category)) {
                    matchesCategory = (model.name != null && model.name.toLowerCase().contains("coder"))
                        || (model.displayName != null && model.displayName.toLowerCase().contains("coder"))
                        || (model.description != null && model.description.toLowerCase().contains("cod"));
                } else if ("compact".equalsIgnoreCase(category)) {
                    String p = model.parameterCount != null ? model.parameterCount.toUpperCase() : "";
                    matchesCategory = p.contains("0.5B") || p.contains("1.5B") || p.contains("1B");
                } else if ("compatible".equalsIgnoreCase(category)) {
                    ModelConfig.RamCompatibility compat = ModelConfig.getRamCompatibility(MainActivity.this, model);
                    matchesCategory = (compat != ModelConfig.RamCompatibility.INSUFFICIENT);
                }

                if (!matchesCategory) {
                    continue;
                }

                // Query match
                if (lowerQuery.isEmpty()) {
                    filteredModels.add(model);
                } else {
                    boolean matchesQuery = (model.displayName != null && model.displayName.toLowerCase().contains(lowerQuery))
                        || (model.parameterCount != null && model.parameterCount.toLowerCase().contains(lowerQuery))
                        || (model.description != null && model.description.toLowerCase().contains(lowerQuery))
                        || (model.getFormattedFileSize() != null && model.getFormattedFileSize().toLowerCase().contains(lowerQuery));
                    if (matchesQuery) {
                        filteredModels.add(model);
                    }
                }
            }
            notifyDataSetChanged();

            // Toggle empty state
            if (layoutModelEmptyState != null && recyclerModelSelection != null) {
                if (filteredModels.isEmpty()) {
                    layoutModelEmptyState.setVisibility(View.VISIBLE);
                    recyclerModelSelection.setVisibility(View.GONE);
                } else {
                    layoutModelEmptyState.setVisibility(View.GONE);
                    recyclerModelSelection.setVisibility(View.VISIBLE);
                }
            }
            if (modelHubBottomSheetDialog != null && modelHubBottomSheetDialog.isShowing()) {
                View sheetEmpty = modelHubBottomSheetDialog.findViewById(R.id.layout_model_empty_state);
                View sheetRecycler = modelHubBottomSheetDialog.findViewById(R.id.recycler_model_selection);
                if (sheetEmpty != null && sheetRecycler != null) {
                    if (filteredModels.isEmpty()) {
                        sheetEmpty.setVisibility(View.VISIBLE);
                        sheetRecycler.setVisibility(View.GONE);
                    } else {
                        sheetEmpty.setVisibility(View.GONE);
                        sheetRecycler.setVisibility(View.VISIBLE);
                    }
                }
            }
        }

        @NonNull
        @Override
        public ModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_model_card, parent, false);
            return new ModelViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ModelViewHolder vh, int position) {
            ModelConfig.Model model = filteredModels.get(position);
            boolean isSelected = model.name.equals(currentModelName);
            boolean isDownloaded = downloadManager != null && downloadManager.isModelDownloaded(model);
            boolean isLoaded = model.name.equals(loadedModelName);

            vh.text1.setText(model.displayName);
            vh.text2.setText(model.getFormattedFileSize());

            // Parameter count tag: Hide if parameter size is already clearly in displayName to prevent repetition!
            boolean hasParamInTitle = model.parameterCount != null && model.displayName != null
                    && model.displayName.toUpperCase().contains(model.parameterCount.toUpperCase());
            boolean showParam = !hasParamInTitle && model.parameterCount != null 
                    && !model.parameterCount.isEmpty() && !"Unknown".equalsIgnoreCase(model.parameterCount);

            if (showParam) {
                vh.tagParams.setText(model.parameterCount);
                vh.tagParams.setVisibility(View.VISIBLE);
                if (vh.specDotParam != null) vh.specDotParam.setVisibility(View.VISIBLE);
            } else {
                vh.tagParams.setVisibility(View.GONE);
                if (vh.specDotParam != null) vh.specDotParam.setVisibility(View.GONE);
            }

            // Quantization tag (concise formatting)
            if (model.quantization != null && !model.quantization.isEmpty() && !"Unknown".equalsIgnoreCase(model.quantization)) {
                String quantDisplay = model.quantization;
                if (quantDisplay.equalsIgnoreCase("Q4_K_M") || quantDisplay.equalsIgnoreCase("q4_k_m")) {
                    quantDisplay = "Q4";
                }
                vh.tagQuant.setText(quantDisplay);
                vh.tagQuant.setVisibility(View.VISIBLE);
                if (vh.specDotQuant != null) vh.specDotQuant.setVisibility(View.VISIBLE);
            } else {
                vh.tagQuant.setVisibility(View.GONE);
                if (vh.specDotQuant != null) vh.specDotQuant.setVisibility(View.GONE);
            }

            // RAM Compatibility tag: Calm green text for optimal fit, distinct warning badge only when restricted
            if (vh.tagRam != null) {
                ModelConfig.RamCompatibility compat = ModelConfig.getRamCompatibility(MainActivity.this, model);
                vh.tagRam.setVisibility(View.VISIBLE);
                if (vh.specDotRam != null) vh.specDotRam.setVisibility(View.VISIBLE);
                if (compat == ModelConfig.RamCompatibility.OPTIMAL) {
                    vh.tagRam.setText("● Fits RAM");
                    vh.tagRam.setTextColor(getResources().getColor(R.color.green_700, getTheme()));
                    vh.tagRam.setBackground(null);
                    vh.tagRam.setPadding(0, 0, 0, 0);
                } else if (compat == ModelConfig.RamCompatibility.TIGHT) {
                    vh.tagRam.setText("Tight RAM");
                    vh.tagRam.setTextColor(getResources().getColor(R.color.warning_orange, getTheme()));
                    vh.tagRam.setBackgroundResource(R.drawable.model_status_badge_warning);
                    vh.tagRam.setPadding(dpToPx(6), dpToPx(1.5f), dpToPx(6), dpToPx(1.5f));
                } else {
                    vh.tagRam.setText("⚠️ Low RAM");
                    vh.tagRam.setTextColor(getResources().getColor(R.color.error_red, getTheme()));
                    vh.tagRam.setBackgroundResource(R.drawable.model_status_badge_danger);
                    vh.tagRam.setPadding(dpToPx(6), dpToPx(1.5f), dpToPx(6), dpToPx(1.5f));
                }
            } else if (vh.specDotRam != null) {
                vh.specDotRam.setVisibility(View.GONE);
            }

            // Model description: Human-centered, clutter-free utility description
            vh.textDescription.setText(model.getCleanDescription());
            vh.textDescription.setVisibility(View.VISIBLE);

            // Contextual brand avatar or icon
            if (vh.modelAvatarText != null) {
                String initial = model.getFamilyInitial();
                vh.modelAvatarText.setText(initial);
                vh.modelAvatarText.setVisibility(View.VISIBLE);
                if (vh.modelIcon != null) vh.modelIcon.setVisibility(View.GONE);
            } else if (vh.modelIcon != null) {
                if (model.isCustomUrl) {
                    vh.modelIcon.setImageResource(R.drawable.ic_link);
                } else if (model.name.toLowerCase().contains("coder") || 
                           (model.description != null && model.description.toLowerCase().contains("cod"))) {
                    vh.modelIcon.setImageResource(R.drawable.ic_code);
                } else {
                    vh.modelIcon.setImageResource(R.drawable.ic_chat_bubble);
                }
                vh.modelIcon.setVisibility(View.VISIBLE);
            }

            if (isLoaded) {
                vh.modelStatus.setVisibility(View.VISIBLE);
                vh.modelStatus.setText("● Active");
                vh.modelStatus.setBackgroundResource(R.drawable.model_status_badge_active);
                vh.modelStatus.setTextColor(getResources().getColor(R.color.green_700, getTheme()));
                vh.btnAction.setImageResource(R.drawable.ic_delete);
                vh.btnAction.setContentDescription("Delete " + model.displayName);
                vh.btnAction.setBackground(resolveThemeDrawable(android.R.attr.selectableItemBackgroundBorderless));
                vh.btnAction.setColorFilter(resolveThemeColor(R.attr.textColorSecondary));
                vh.cardView.setStrokeColor(resolveThemeColor(com.google.android.material.R.attr.colorPrimary));
                vh.cardView.setStrokeWidth(dpToPx(1.5f));
            } else if (isDownloaded) {
                vh.modelStatus.setVisibility(View.VISIBLE);
                vh.modelStatus.setText("✓ Ready");
                vh.modelStatus.setBackgroundResource(R.drawable.model_status_badge_ready);
                vh.modelStatus.setTextColor(getResources().getColor(R.color.green_700, getTheme()));
                vh.btnAction.setImageResource(R.drawable.ic_delete);
                vh.btnAction.setContentDescription("Delete " + model.displayName);
                vh.btnAction.setBackground(resolveThemeDrawable(android.R.attr.selectableItemBackgroundBorderless));
                vh.btnAction.setColorFilter(resolveThemeColor(R.attr.textColorSecondary));
                int borderColor = isSelected ? resolveThemeColor(com.google.android.material.R.attr.colorPrimary) : resolveThemeColor(R.attr.borderColor);
                vh.cardView.setStrokeColor(borderColor);
                vh.cardView.setStrokeWidth(isSelected ? dpToPx(1.5f) : dpToPx(1f));
            } else {
                vh.modelStatus.setVisibility(View.GONE);
                vh.btnAction.setImageResource(R.drawable.ic_download);
                vh.btnAction.setContentDescription("Download " + model.displayName);
                vh.btnAction.setBackgroundResource(R.drawable.btn_model_download_background);
                vh.btnAction.setColorFilter(resolveThemeColor(com.google.android.material.R.attr.colorPrimary));
                int borderColor = isSelected ? resolveThemeColor(com.google.android.material.R.attr.colorPrimary) : resolveThemeColor(R.attr.borderColor);
                vh.cardView.setStrokeColor(borderColor);
                vh.cardView.setStrokeWidth(isSelected ? dpToPx(1.5f) : dpToPx(1f));
            }

            vh.btnAction.setOnClickListener(v -> {
                performHapticFeedback(v);
                if (isDownloaded) {
                    onDeleteModel(model);
                } else {
                    if (modelHubBottomSheetDialog != null && modelHubBottomSheetDialog.isShowing()) {
                        modelHubBottomSheetDialog.dismiss();
                    }
                    onDownloadModel(model);
                }
            });

            vh.cardView.setOnClickListener(v -> {
                performHapticFeedback(v);
                if (modelHubBottomSheetDialog != null && modelHubBottomSheetDialog.isShowing()) {
                    modelHubBottomSheetDialog.dismiss();
                }
                if (isDownloaded) {
                    loadModel(model);
                    hideInlineModelSelection();
                    updateSelectedModelDisplay(model);
                    if (currentTab == TAB_MODELS) {
                        switchToTab(TAB_CHAT);
                    }
                } else {
                    onDownloadModel(model);
                    hideInlineModelSelection();
                }
            });
        }

        @Override
        public int getItemCount() {
            return filteredModels.size();
        }

        class ModelViewHolder extends RecyclerView.ViewHolder {
            TextView text1;
            TextView text2;
            TextView textDescription;
            TextView tagParams;
            TextView tagQuant;
            TextView tagRam;
            TextView specDotParam;
            TextView specDotQuant;
            TextView specDotRam;
            ImageView modelIcon;
            TextView modelAvatarText;
            TextView modelStatus;
            ImageButton btnAction;
            com.google.android.material.card.MaterialCardView cardView;

            ModelViewHolder(View view) {
                super(view);
                cardView = (com.google.android.material.card.MaterialCardView) view;
                text1 = view.findViewById(R.id.text1);
                text2 = view.findViewById(R.id.text2);
                textDescription = view.findViewById(R.id.text_model_description);
                tagParams = view.findViewById(R.id.tag_params);
                tagQuant = view.findViewById(R.id.tag_quant);
                tagRam = view.findViewById(R.id.tag_ram);
                specDotParam = view.findViewById(R.id.spec_dot_param);
                specDotQuant = view.findViewById(R.id.spec_dot_quant);
                specDotRam = view.findViewById(R.id.spec_dot_ram);
                modelIcon = view.findViewById(R.id.model_icon);
                modelAvatarText = view.findViewById(R.id.model_avatar_text);
                modelStatus = view.findViewById(R.id.model_status);
                btnAction = view.findViewById(R.id.btn_action);
            }
        }
    }
}