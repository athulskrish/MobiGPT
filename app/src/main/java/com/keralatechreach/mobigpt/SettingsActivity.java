package com.keralatechreach.mobigpt;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.keralatechreach.mobigpt.database.ChatDatabase;
import com.keralatechreach.mobigpt.database.MessageDao;
import com.keralatechreach.mobigpt.database.ChatDao;
import com.keralatechreach.mobigpt.database.Chat;
import com.keralatechreach.mobigpt.utils.SettingsManager;
import com.keralatechreach.mobigpt.utils.TypographyManager;
import com.keralatechreach.mobigpt.utils.ExportHelper;

import java.util.List;
import java.util.Locale;

/**
 * Professional Settings Activity with organized categories
 * Categories:
 * 1. Appearance
 * 2. Chat
 * 3. Downloads & Storage
 * 4. Security
 * 5. Data & Privacy
 * 6. About
 */
public class SettingsActivity extends AppCompatActivity {

    private String currentThemeSignature = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);

        // Apply theme before calling super.onCreate
        com.keralatechreach.mobigpt.utils.ThemeManager themeManager = 
            new com.keralatechreach.mobigpt.utils.ThemeManager(this);
        themeManager.applyTheme();
        currentThemeSignature = themeManager.getThemeSignature();
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupEdgeToEdgeInsets();

        // Migrate integer preferences to string (fix for ClassCastException)
        migrateIntegerPreferencesToString();

        // Apply all theme colors using unified updater
        applyAllThemeColors();

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            android.widget.TextView titleView = new android.widget.TextView(this);
            titleView.setText(R.string.settings);
            androidx.appcompat.widget.Toolbar.LayoutParams lp = new androidx.appcompat.widget.Toolbar.LayoutParams(
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT,
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL;
            titleView.setLayoutParams(lp);
            toolbar.addView(titleView);
            new TypographyManager(this).applyCompleteTypography(titleView, TypographyManager.TextType.LARGE, false);
        }

        // Load settings fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing() || isDestroyed()) return;
        com.keralatechreach.mobigpt.utils.ThemeManager tm = 
            new com.keralatechreach.mobigpt.utils.ThemeManager(this);
        String latestSig = tm.getThemeSignature();
        if (currentThemeSignature != null && !currentThemeSignature.equals(latestSig)) {
            currentThemeSignature = latestSig;
            recreate();
        }
    }

    private void setupEdgeToEdgeInsets() {
        View appBarLayout = findViewById(R.id.app_bar_layout);
        if (appBarLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                );
                v.setPadding(insets.left, insets.top, insets.right, 0);
                return windowInsets;
            });
        }
        View settingsContainer = findViewById(R.id.settings_container);
        if (settingsContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(settingsContainer, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                );
                v.setPadding(insets.left, 0, insets.right, insets.bottom);
                return windowInsets;
            });
        }
    }

    /**
     * Apply all theme colors using unified theme updater
     */
    private void applyAllThemeColors() {
        com.keralatechreach.mobigpt.utils.ThemeColorUpdater themeUpdater = 
            new com.keralatechreach.mobigpt.utils.ThemeColorUpdater(this);
        themeUpdater.applyAllThemeColors(this);
    }

    /**
     * Migrate integer preferences to string to fix ClassCastException
     * This is needed when preferences were previously stored as integers
     * but are now defined as ListPreference (which requires strings)
     */
    private void migrateIntegerPreferencesToString() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        boolean needsCommit = false;

        // List of preference keys that should be strings (ListPreference)
        String[] keysToMigrate = {
            "max_response_length",
            "inference_threads"
        };

        for (String key : keysToMigrate) {
            try {
                // Try to read as integer
                int intValue = prefs.getInt(key, -1);
                if (intValue != -1) {
                    // Remove the integer value
                    editor.remove(key);
                    // Store as string
                    editor.putString(key, String.valueOf(intValue));
                    needsCommit = true;
                }
            } catch (ClassCastException e) {
                // Already a string, no migration needed
            }
        }
        
        // List of preference keys that should be integers (SeekBarPreference)
        String[][] seekBarKeysToMigrate = {
            // AI Response settings
            {"ai_temperature", "70"},
            {"top_p", "90"},
            {"top_k", "40"},
            {"context_length", "10"},
            // Accessibility settings
            {"brightness_boost", "0"},
            {"animation_speed", "100"},
            {"touch_target_size", "48"},
            // Appearance settings
            {"corner_radius", "8"},
            // Downloads settings
            {"concurrent_downloads", "2"},
            // Chat settings
            {"cache_size", "90"},
            // Security settings
            {"auto_lock_timeout", "300"}
        };
        
        for (String[] pref : seekBarKeysToMigrate) {
            String key = pref[0];
            int defaultValue = Integer.parseInt(pref[1]);
            
            try {
                // Try to get as int - if this succeeds, no migration needed
                prefs.getInt(key, defaultValue);
            } catch (ClassCastException e) {
                // Value is stored as string, migrate to int
                try {
                    String stringValue = prefs.getString(key, pref[1]);
                    int intValue = Integer.parseInt(stringValue);
                    editor.remove(key);
                    editor.putInt(key, intValue);
                    needsCommit = true;
                } catch (Exception ex) {
                    // If parsing fails, just set default
                    editor.remove(key);
                    editor.putInt(key, defaultValue);
                    needsCommit = true;
                }
            }
        }

        if (needsCommit) {
            editor.apply();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends BaseSettingsFragment {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            
            // Ensure toolbar title is set correctly for main settings
            if (getActivity() != null) {
                androidx.appcompat.app.ActionBar actionBar = ((SettingsActivity) getActivity()).getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle("Settings");
                    applyToolbarTypography();
                }
            }
            
            // Setup click listeners for navigation items
            setupNavigationPreferences();

            // Setup restore defaults action
            setupRestoreDefaultsPreference();
        }
        
        @Override
        public void onResume() {
            super.onResume();
            
            // Ensure the correct title when returning from sub-settings
            if (getActivity() != null) {
                androidx.appcompat.app.ActionBar actionBar = ((SettingsActivity) getActivity()).getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle("Settings");
                    applyToolbarTypography();
                }
            }
        }

        private void setupNavigationPreferences() {
            // Appearance
            Preference appearanceSettings = findPreference("appearance_settings");
            if (appearanceSettings != null) {
                appearanceSettings.setOnPreferenceClickListener(preference -> {
                    navigateToFragment(new AppearanceSettingsFragment());
                    return true;
                });
            }

            // Chat Settings
            Preference chatSettings = findPreference("chat_settings");
            if (chatSettings != null) {
                chatSettings.setOnPreferenceClickListener(preference -> {
                    navigateToFragment(new ChatSettingsFragment());
                    return true;
                });
            }

            // Downloads & Storage
            Preference downloadsSettings = findPreference("downloads_settings");
            if (downloadsSettings != null) {
                downloadsSettings.setOnPreferenceClickListener(preference -> {
                    navigateToFragment(new DownloadsSettingsFragment());
                    return true;
                });
            }

            // Security
            Preference securitySettings = findPreference("security_settings");
            if (securitySettings != null) {
                securitySettings.setOnPreferenceClickListener(preference -> {
                    navigateToFragment(new SecuritySettingsFragment());
                    return true;
                });
            }

            // Data & Privacy
            Preference dataSettings = findPreference("data_privacy_settings");
            if (dataSettings != null) {
                dataSettings.setOnPreferenceClickListener(preference -> {
                    navigateToFragment(new DataPrivacySettingsFragment());
                    return true;
                });
            }

            // About
            Preference aboutSettings = findPreference("about_settings");
            if (aboutSettings != null) {
                aboutSettings.setOnPreferenceClickListener(preference -> {
                    navigateToFragment(new AboutSettingsFragment());
                    return true;
                });
            }
        }

        private void navigateToFragment(PreferenceFragmentCompat fragment) {
            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, fragment)
                    .addToBackStack(null)
                    .commit();
        }

        private void setupRestoreDefaultsPreference() {
            Preference restorePref = findPreference("restore_defaults");
            if (restorePref == null || getContext() == null) return;

            restorePref.setOnPreferenceClickListener(pref -> {
                new AlertDialog.Builder(requireContext())
                    .setTitle("Restore default settings")
                    .setMessage("This will reset all settings to their original default values. This action cannot be undone.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore", (d, w) -> performRestoreDefaults())
                    .show();
                return true;
            });
        }

        private void performRestoreDefaults() {
            Context ctx = getContext();
            if (ctx == null) return;

            // Clear all preferences
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
            prefs.edit().clear().apply();

            // Re-apply default values from all preference XMLs
            try {
                PreferenceManager.setDefaultValues(ctx, R.xml.preferences_downloads, true);
            } catch (Exception ignored) {}
            try {
                PreferenceManager.setDefaultValues(ctx, R.xml.preferences_chat, true);
            } catch (Exception ignored) {}
            try {
                PreferenceManager.setDefaultValues(ctx, R.xml.preferences_appearance, true);
            } catch (Exception ignored) {}
            try {
                PreferenceManager.setDefaultValues(ctx, R.xml.preferences_data_privacy, true);
            } catch (Exception ignored) {}
            try {
                PreferenceManager.setDefaultValues(ctx, R.xml.preferences_security, true);
            } catch (Exception ignored) {}

            // Feedback
            android.widget.Toast.makeText(ctx, "Settings restored to defaults", android.widget.Toast.LENGTH_SHORT).show();

            // Recreate activity to apply theme/typography and refresh fragments
            if (getActivity() != null) {
                getActivity().recreate();
            }
        }
    }
    
    /**
     * Base class for all settings fragments with typography support
     */
    public static abstract class BaseSettingsFragment extends PreferenceFragmentCompat 
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // This is implemented by subclasses
        }
        
        @Override
        public void onViewCreated(@androidx.annotation.NonNull android.view.View view, 
                                 @androidx.annotation.Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            // Apply typography immediately after view is created to prevent jumping
            applyTypographyToPreferences();
        }
        
        @Override
        public void onResume() {
            super.onResume();
            // Ensure typography is applied and register for preference changes
            applyTypographyToPreferences();
            
            // Register for preference changes to detect font changes
            if (getPreferenceScreen() != null && getPreferenceScreen().getSharedPreferences() != null) {
                getPreferenceScreen().getSharedPreferences()
                        .registerOnSharedPreferenceChangeListener(this);
            }
        }
        
        @Override
        public void onPause() {
            super.onPause();
            // Unregister preference change listener
            if (getPreferenceScreen() != null && getPreferenceScreen().getSharedPreferences() != null) {
                getPreferenceScreen().getSharedPreferences()
                        .unregisterOnSharedPreferenceChangeListener(this);
            }
        }
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // Re-apply typography when font settings change (no delay to prevent jumping)
            if ("font_size".equals(key) || "font_family".equals(key) || "monospace_code".equals(key)) {
                // Immediately re-apply typography to this fragment
                applyTypographyToPreferences();
            }
        }
        
        /**
         * Apply typography to all preference text views
         */
        protected void applyTypographyToPreferences() {
            if (getView() == null || getContext() == null) return;
            
            try {
                TypographyManager typographyManager = new TypographyManager(getContext());
                
                // Apply to the entire view hierarchy
                applyTypographyToViewGroup((android.view.ViewGroup) getView(), typographyManager);
                
                // Also ensure RecyclerView items get typography applied as they're bound
                androidx.recyclerview.widget.RecyclerView rv = getListView();
                if (rv != null) {
                    // Remove any existing listeners to prevent duplicates
                    rv.clearOnChildAttachStateChangeListeners();
                    
                    // Add listener for new items
                    rv.addOnChildAttachStateChangeListener(new androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener() {
                        @Override
                        public void onChildViewAttachedToWindow(@androidx.annotation.NonNull android.view.View view) {
                            if (view instanceof android.view.ViewGroup && getContext() != null) {
                                TypographyManager tm = new TypographyManager(getContext());
                                applyTypographyToViewGroup((android.view.ViewGroup) view, tm);
                            }
                        }
                        
                        @Override
                        public void onChildViewDetachedFromWindow(@androidx.annotation.NonNull android.view.View view) {
                            // No action needed
                        }
                    });
                    
                    // Gently refresh visible items without causing jumps
                    if (rv.getAdapter() != null && isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (rv.getAdapter() != null) {
                                for (int i = 0; i < rv.getChildCount(); i++) {
                                    android.view.View child = rv.getChildAt(i);
                                    if (child instanceof android.view.ViewGroup) {
                                        applyTypographyToViewGroup((android.view.ViewGroup) child, typographyManager);
                                    }
                                }
                            }
                        });
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("SettingsActivity", "Error applying typography to preferences", e);
            }
        }
        
        /**
         * Recursively apply typography to all TextViews in a ViewGroup
         */
        protected void applyTypographyToViewGroup(android.view.ViewGroup viewGroup, TypographyManager typographyManager) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                android.view.View child = viewGroup.getChildAt(i);
                
                if (child instanceof android.widget.TextView) {
                    android.widget.TextView textView = (android.widget.TextView) child;
                    
                    // Determine text type based on text appearance
                    TypographyManager.TextType textType = TypographyManager.TextType.NORMAL;
                    
                    // Preference titles are usually larger
                    if (child.getId() == android.R.id.title) {
                        textType = TypographyManager.TextType.MEDIUM;
                    }
                    // Preference summaries are usually smaller
                    else if (child.getId() == android.R.id.summary) {
                        textType = TypographyManager.TextType.SMALL;
                    }
                    
                    typographyManager.applyCompleteTypography(textView, textType, false);
                } else if (child instanceof android.view.ViewGroup) {
                    // Recursively apply to nested ViewGroups
                    applyTypographyToViewGroup((android.view.ViewGroup) child, typographyManager);
                }
            }
        }
        
        /**
         * Apply typography to the toolbar to prevent jumping
         */
        protected void applyToolbarTypography() {
            if (getActivity() == null) return;
            
            try {
                android.view.View toolbarView = getActivity().findViewById(R.id.toolbar);
                if (toolbarView instanceof android.view.ViewGroup && getContext() != null) {
                    TypographyManager typographyManager = new TypographyManager(getContext());
                    applyTypographyToViewGroup((android.view.ViewGroup) toolbarView, typographyManager);
                }
            } catch (Exception e) {
                android.util.Log.w("SettingsActivity", "Error applying toolbar typography", e);
            }
        }
    }

    // Downloads & Storage Settings Fragment
    public static class DownloadsSettingsFragment extends BaseSettingsFragment {
        
        private Preference storageUsagePref;
        private Preference clearCachePref;
        
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_downloads, rootKey);
            
            if (getActivity() != null) {
                androidx.appcompat.app.ActionBar actionBar = ((SettingsActivity) getActivity()).getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle("Downloads & Storage");
                    // Apply typography to toolbar immediately
                    applyToolbarTypography();
                }
            }
            
            setupStoragePreferences();
        }
        
        private void setupStoragePreferences() {
            storageUsagePref = findPreference("storage_usage");
            clearCachePref = findPreference("clear_cache");
            
            if (storageUsagePref != null) {
                storageUsagePref.setOnPreferenceClickListener(preference -> {
                    showStorageUsageDetails();
                    return true;
                });
                updateStorageUsageSummary();
            }
            
            if (clearCachePref != null) {
                clearCachePref.setOnPreferenceClickListener(preference -> {
                    showClearCacheDialog();
                    return true;
                });
                updateClearCacheSummary();
            }
            
            // Force refresh storage info when fragment is created
            refreshStorageInfo();
        }
        
        private void refreshStorageInfo() {
            // Delay the refresh slightly to ensure the fragment is fully initialized
            if (getActivity() != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    updateStorageUsageSummary();
                    updateClearCacheSummary();
                }, 500);
            }
        }
        
        @Override
        public void onResume() {
            super.onResume();
            
            // Refresh storage summaries when returning to this screen
            updateStorageUsageSummary();
            updateClearCacheSummary();
        }
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            super.onSharedPreferenceChanged(sharedPreferences, key);
            
            // Handle notification setting changes
            if ("download_notifications".equals(key) || 
                "download_complete_sound".equals(key) || 
                "download_progress_notification".equals(key)) {
                
                // Notify the app to update notification settings
                if (getActivity() instanceof SettingsActivity) {
                    updateNotificationSettings();
                }
            }
        }
        
        private void updateNotificationSettings() {
            if (getActivity() != null) {
                // Create broadcast to MainActivity to update download notification settings
                android.content.Intent intent = new android.content.Intent("UPDATE_NOTIFICATION_SETTINGS");
                intent.setPackage(getActivity().getPackageName());
                getActivity().sendBroadcast(intent);
            }
        }
        
        private void showStorageUsageDetails() {
            if (getActivity() == null) return;
            
            // Calculate storage usage asynchronously
            new Thread(() -> {
                try {
                    StorageInfo storageInfo = calculateStorageInfo();
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> showStorageUsageDialog(storageInfo));
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> android.widget.Toast.makeText(getActivity(), 
                            "Error calculating storage: " + e.getMessage(), 
                            android.widget.Toast.LENGTH_LONG).show());
                    }
                }
            }).start();
        }
        
        private void showStorageUsageDialog(StorageInfo info) {
            if (getActivity() == null) return;
            
            String message = String.format(
                Locale.US,
                "📱 Total App Storage: %s\n\n" +
                "🤖 Models: %s (%d files)\n" +
                "📂 Cache: %s\n" +
                "🗄️ Database: %s\n" +
                "📄 Temp Files: %s\n\n" +
                "💾 Available Space: %s\n" +
                "📊 Storage Usage: %.1f%%",
                info.getFormattedTotalUsed(),
                info.getFormattedModelsSize(), info.modelCount,
                info.getFormattedCacheSize(),
                info.getFormattedDatabaseSize(),
                info.getFormattedTempSize(),
                info.getFormattedAvailableSpace(),
                info.getUsagePercentage()
            );
            
            new android.app.AlertDialog.Builder(getActivity())
                .setTitle("Storage Usage Details")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .setNeutralButton("Refresh", (dialog, which) -> {
                    updateStorageUsageSummary();
                    showStorageUsageDetails();
                })
                .show();
        }
        
        private void showClearCacheDialog() {
            if (getActivity() == null) return;
            
            new Thread(() -> {
                try {
                    long cacheSize = calculateCacheSize();
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (cacheSize > 0) {
                                String message = String.format(
                                    "This will clear %s of cached data including:\n\n" +
                                    "• Temporary files\n" +
                                    "• Image cache\n" +
                                    "• Download cache\n" +
                                    "• System cache\n\n" +
                                    "This action cannot be undone but will free up storage space.",
                                    formatBytes(cacheSize)
                                );
                                
                                new android.app.AlertDialog.Builder(getActivity())
                                    .setTitle("Clear Cache")
                                    .setMessage(message)
                                    .setPositiveButton("Clear Cache", (dialog, which) -> clearCache())
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            } else {
                                android.widget.Toast.makeText(getActivity(), 
                                    "No cache files found to clear", 
                                    android.widget.Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> android.widget.Toast.makeText(getActivity(), 
                            "Error checking cache: " + e.getMessage(), 
                            android.widget.Toast.LENGTH_LONG).show());
                    }
                }
            }).start();
        }
        
        private void clearCache() {
            if (getActivity() == null) return;
            
            // Show initial feedback
            android.widget.Toast.makeText(getActivity(), "Starting cache clear...", 
                android.widget.Toast.LENGTH_SHORT).show();
            
            // Disable preference and show progress
            if (clearCachePref != null) {
                clearCachePref.setEnabled(false);
                clearCachePref.setSummary("Clearing cache...");
            }
            
            new Thread(() -> {
                try {
                    // Calculate current cache size before clearing
                    long initialCacheSize = calculateCacheSize();
                    android.util.Log.i("CacheClear", "Initial cache size: " + formatBytes(initialCacheSize));
                    
                    // Perform the actual cache clear
                    long clearedSize = performCacheClear();
                    
                    // Calculate remaining cache size after clearing
                    long remainingCacheSize = calculateCacheSize();
                    android.util.Log.i("CacheClear", "Remaining cache size: " + formatBytes(remainingCacheSize));
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            String message;
                            if (clearedSize > 0) {
                                message = String.format("Cache cleared successfully!\n\n" +
                                    "• Freed: %s\n" +
                                    "• Before: %s\n" +
                                    "• After: %s",
                                    formatBytes(clearedSize),
                                    formatBytes(initialCacheSize),
                                    formatBytes(remainingCacheSize));
                            } else if (initialCacheSize == 0) {
                                message = "No cache files found to clear.\nThe app cache is already empty.";
                            } else {
                                message = "Cache clearing completed.\nSome files may be in use and couldn't be cleared.";
                            }
                            
                            // Show detailed result dialog
                            new android.app.AlertDialog.Builder(getActivity())
                                .setTitle("Cache Cleared")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show();
                            
                            // Re-enable preference and update summaries
                            if (clearCachePref != null) {
                                clearCachePref.setEnabled(true);
                            }
                            updateStorageUsageSummary();
                            updateClearCacheSummary();
                        });
                    }
                } catch (Exception e) {
                    android.util.Log.e("CacheClear", "Cache clear failed", e);
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            String errorMessage = "Failed to clear cache:\n" + e.getMessage() + 
                                "\n\nThis may happen if:\n" +
                                "• Files are in use by the system\n" +
                                "• Permission restrictions\n" +
                                "• Storage is read-only";
                            
                            new android.app.AlertDialog.Builder(getActivity())
                                .setTitle("Cache Clear Failed")
                                .setMessage(errorMessage)
                                .setPositiveButton("OK", null)
                                .setNeutralButton("Retry", (dialog, which) -> clearCache())
                                .show();
                            
                            // Re-enable preference
                            if (clearCachePref != null) {
                                clearCachePref.setEnabled(true);
                                updateClearCacheSummary();
                            }
                        });
                    }
                }
            }).start();
        }
        
        private void updateStorageUsageSummary() {
            if (storageUsagePref == null || getActivity() == null) return;
            
            new Thread(() -> {
                try {
                    StorageInfo info = calculateStorageInfo();
                    
                    if (getActivity() != null && storageUsagePref != null) {
                        getActivity().runOnUiThread(() -> {
                            String summary = String.format(Locale.US, "Used: %s • Available: %s • %.1f%% full",
                                info.getFormattedTotalUsed(),
                                info.getFormattedAvailableSpace(),
                                info.getUsagePercentage());
                            storageUsagePref.setSummary(summary);
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null && storageUsagePref != null) {
                        getActivity().runOnUiThread(() -> storageUsagePref.setSummary("Error calculating storage usage"));
                    }
                }
            }).start();
        }
        
        private void updateClearCacheSummary() {
            if (clearCachePref == null || getActivity() == null) return;
            
            new Thread(() -> {
                try {
                    long cacheSize = calculateCacheSize();
                    
                    if (getActivity() != null && clearCachePref != null) {
                        getActivity().runOnUiThread(() -> {
                            String summary = cacheSize > 0 ? 
                                "Cache size: " + formatBytes(cacheSize) :
                                "No cache files found";
                            clearCachePref.setSummary(summary);
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null && clearCachePref != null) {
                        getActivity().runOnUiThread(() -> clearCachePref.setSummary("Error checking cache size"));
                    }
                }
            }).start();
        }
        
        private StorageInfo calculateStorageInfo() {
            if (getActivity() == null) return new StorageInfo();
            
            StorageInfo info = new StorageInfo();
            
            try {
                // Get device storage info
                java.io.File dataDir = getActivity().getFilesDir();
                android.os.StatFs stat = new android.os.StatFs(dataDir.getPath());
                long blockSize = stat.getBlockSizeLong();
                long totalBlocks = stat.getBlockCountLong();
                long availableBlocks = stat.getAvailableBlocksLong();
                
                info.totalDeviceSpace = totalBlocks * blockSize;
                info.availableSpace = availableBlocks * blockSize;
                
                // Calculate models storage
                info.modelsSize = calculateModelsSize();
                info.modelCount = getModelFileCount();
                
                // Calculate other storage
                info.cacheSize = calculateCacheSize();
                info.databaseSize = calculateDatabaseSize();
                info.tempSize = calculateTempFilesSize();
                
                info.totalUsed = info.modelsSize + info.cacheSize + info.databaseSize + info.tempSize;
                
            } catch (Exception e) {
                android.util.Log.e("StorageCalc", "Error calculating storage", e);
            }
            
            return info;
        }
        
        private long calculateModelsSize() {
            if (getActivity() == null) return 0;
            
            try {
                java.io.File modelsDir = ModelConfig.getModelsDirectory(getActivity());
                
                if (modelsDir.exists()) {
                    java.io.File[] modelFiles = modelsDir.listFiles((dir, name) -> name.endsWith(".gguf"));
                    if (modelFiles != null) {
                        long totalSize = 0;
                        for (java.io.File file : modelFiles) {
                            totalSize += file.length();
                        }
                        return totalSize;
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("StorageCalc", "Error calculating models size", e);
            }
            
            return 0;
        }
        
        private int getModelFileCount() {
            if (getActivity() == null) return 0;
            
            try {
                java.io.File modelsDir = ModelConfig.getModelsDirectory(getActivity());
                
                if (modelsDir.exists()) {
                    java.io.File[] modelFiles = modelsDir.listFiles((dir, name) -> name.endsWith(".gguf"));
                    return modelFiles != null ? modelFiles.length : 0;
                }
            } catch (Exception e) {
                android.util.Log.e("StorageCalc", "Error counting model files", e);
            }
            
            return 0;
        }
        
        private long calculateCacheSize() {
            if (getActivity() == null) return 0;
            
            long totalSize = 0;
            
            try {
                android.util.Log.d("CacheCalc", "Starting cache size calculation");
                
                // App cache directory
                java.io.File cacheDir = getActivity().getCacheDir();
                if (cacheDir.exists()) {
                    long cacheDirSize = calculateDirectorySize(cacheDir);
                    totalSize += cacheDirSize;
                    android.util.Log.d("CacheCalc", "App cache size: " + formatBytes(cacheDirSize));
                }
                
                // External cache directory
                java.io.File externalCacheDir = getActivity().getExternalCacheDir();
                if (externalCacheDir != null && externalCacheDir.exists()) {
                    long externalCacheSize = calculateDirectorySize(externalCacheDir);
                    totalSize += externalCacheSize;
                    android.util.Log.d("CacheCalc", "External cache size: " + formatBytes(externalCacheSize));
                }
                
                // Temporary files in app files directory
                java.io.File filesDir = getActivity().getFilesDir();
                if (filesDir.exists()) {
                    java.io.File[] files = filesDir.listFiles();
                    if (files != null) {
                        long tempFilesSize = 0;
                        for (java.io.File file : files) {
                            String name = file.getName();
                            if (name.contains("temp") || name.contains("tmp") || 
                                name.endsWith(".tmp") || name.endsWith(".temp") ||
                                name.endsWith(".cache") || name.startsWith("cache_") ||
                                name.contains("download_temp") || name.contains("partial_")) {
                                
                                if (file.isDirectory()) {
                                    tempFilesSize += calculateDirectorySize(file);
                                } else {
                                    tempFilesSize += file.length();
                                }
                            }
                        }
                        totalSize += tempFilesSize;
                        android.util.Log.d("CacheCalc", "Temp files size: " + formatBytes(tempFilesSize));
                    }
                }
                
                // Check for additional cache directories that might exist
                try {
                    java.io.File codeCacheDir = getActivity().getCodeCacheDir();
                    if (codeCacheDir != null && codeCacheDir.exists()) {
                        long codeCacheSize = calculateDirectorySize(codeCacheDir);
                        totalSize += codeCacheSize;
                        android.util.Log.d("CacheCalc", "Code cache size: " + formatBytes(codeCacheSize));
                    }
                } catch (Exception e) {
                    android.util.Log.w("CacheCalc", "Could not check code cache: " + e.getMessage());
                }
                
                android.util.Log.d("CacheCalc", "Total cache size: " + formatBytes(totalSize));
                
            } catch (Exception e) {
                android.util.Log.e("CacheCalc", "Error calculating cache size", e);
            }
            
            return totalSize;
        }
        
        private long calculateDatabaseSize() {
            if (getActivity() == null) return 0;
            
            long totalSize = 0;
            
            try {
                // Database files
                String[] dbNames = {"mobigpt_encrypted.db", "mobigpt.db", "chat_database.db"};
                
                for (String dbName : dbNames) {
                    java.io.File dbFile = getActivity().getDatabasePath(dbName);
                    if (dbFile.exists()) {
                        totalSize += dbFile.length();
                        
                        // WAL and SHM files
                        java.io.File walFile = new java.io.File(dbFile.getPath() + "-wal");
                        java.io.File shmFile = new java.io.File(dbFile.getPath() + "-shm");
                        java.io.File journalFile = new java.io.File(dbFile.getPath() + "-journal");
                        
                        if (walFile.exists()) totalSize += walFile.length();
                        if (shmFile.exists()) totalSize += shmFile.length();
                        if (journalFile.exists()) totalSize += journalFile.length();
                    }
                }
                
            } catch (Exception e) {
                android.util.Log.e("StorageCalc", "Error calculating database size", e);
            }
            
            return totalSize;
        }
        
        private long calculateTempFilesSize() {
            if (getActivity() == null) return 0;
            
            long totalSize = 0;
            
            try {
                java.io.File filesDir = getActivity().getFilesDir();
                if (filesDir.exists()) {
                    java.io.File[] files = filesDir.listFiles();
                    if (files != null) {
                        for (java.io.File file : files) {
                            String name = file.getName();
                            // Count temporary files and directories
                            if (name.contains("temp") || name.contains("tmp") || 
                                name.endsWith(".tmp") || name.endsWith(".temp")) {
                                totalSize += file.isDirectory() ? 
                                    calculateDirectorySize(file) : file.length();
                            }
                        }
                    }
                }
                
            } catch (Exception e) {
                android.util.Log.e("StorageCalc", "Error calculating temp files size", e);
            }
            
            return totalSize;
        }
        
        private long calculateDirectorySize(java.io.File directory) {
            long size = 0;
            if (directory.exists() && directory.isDirectory()) {
                java.io.File[] files = directory.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        if (file.isDirectory()) {
                            size += calculateDirectorySize(file);
                        } else {
                            size += file.length();
                        }
                    }
                }
            }
            return size;
        }
        
        private long performCacheClear() {
            if (getActivity() == null) return 0;
            
            long clearedSize = 0;
            int filesCleared = 0;
            
            try {
                android.util.Log.d("CacheClear", "Starting cache clear operation");
                
                // Clear app cache directory contents (but preserve the directory)
                java.io.File cacheDir = getActivity().getCacheDir();
                if (cacheDir.exists()) {
                    long cacheDirSize = clearDirectoryContents(cacheDir);
                    clearedSize += cacheDirSize;
                    android.util.Log.d("CacheClear", "Cleared app cache: " + formatBytes(cacheDirSize));
                }
                
                // Clear external cache directory contents
                java.io.File externalCacheDir = getActivity().getExternalCacheDir();
                if (externalCacheDir != null && externalCacheDir.exists()) {
                    long externalCacheSize = clearDirectoryContents(externalCacheDir);
                    clearedSize += externalCacheSize;
                    android.util.Log.d("CacheClear", "Cleared external cache: " + formatBytes(externalCacheSize));
                }
                
                // Clear temporary files from app files directory
                java.io.File filesDir = getActivity().getFilesDir();
                if (filesDir.exists()) {
                    java.io.File[] files = filesDir.listFiles();
                    if (files != null) {
                        for (java.io.File file : files) {
                            String name = file.getName();
                            // More comprehensive temp file patterns
                            if (name.contains("temp") || name.contains("tmp") || 
                                name.endsWith(".tmp") || name.endsWith(".temp") ||
                                name.endsWith(".cache") || name.startsWith("cache_") ||
                                name.contains("download_temp") || name.contains("partial_")) {
                                
                                long fileSize;
                                if (file.isDirectory()) {
                                    fileSize = clearDirectoryContents(file);
                                    if (file.delete()) {
                                        filesCleared++;
                                    }
                                } else {
                                    fileSize = file.length();
                                    if (file.delete()) {
                                        filesCleared++;
                                    }
                                }
                                clearedSize += fileSize;
                                android.util.Log.d("CacheClear", "Cleared temp file: " + name + " (" + formatBytes(fileSize) + ")");
                            }
                        }
                    }
                }
                
                // Also try to clear system-level cache using reflection (if possible)
                try {
                    // Trim memory to encourage garbage collection
                    System.gc();
                    System.runFinalization();
                    
                    // Clear WebView cache if available
                    clearWebViewCache();
                } catch (Exception e) {
                    android.util.Log.w("CacheClear", "Could not clear system cache: " + e.getMessage());
                }
                
                android.util.Log.i("CacheClear", "Cache clear completed: " + formatBytes(clearedSize) + 
                    " freed from " + filesCleared + " files/directories");
                
            } catch (Exception e) {
                android.util.Log.e("CacheClear", "Error clearing cache", e);
                throw e;
            }
            
            return clearedSize;
        }
        
        private void clearWebViewCache() {
            try {
                // Clear WebView cache if WebView is initialized
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        try {
                            android.webkit.WebView webView = new android.webkit.WebView(getActivity());
                            webView.clearCache(true);
                            webView.clearHistory();
                            webView.clearFormData();
                            webView.destroy();
                        } catch (Exception e) {
                            android.util.Log.w("CacheClear", "Could not clear WebView cache: " + e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                android.util.Log.w("CacheClear", "WebView cache clear error: " + e.getMessage());
            }
        }
        
        private long clearDirectoryContents(java.io.File directory) {
            long clearedSize = 0;
            if (directory.exists() && directory.isDirectory()) {
                java.io.File[] files = directory.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        try {
                            if (file.isDirectory()) {
                                clearedSize += clearDirectoryContents(file);
                                // Try to delete the directory after clearing its contents
                                if (file.delete()) {
                                    android.util.Log.d("CacheClear", "Deleted directory: " + file.getName());
                                }
                            } else {
                                long fileSize = file.length();
                                if (file.delete()) {
                                    clearedSize += fileSize;
                                    android.util.Log.v("CacheClear", "Deleted file: " + file.getName() + " (" + formatBytes(fileSize) + ")");
                                } else {
                                    android.util.Log.w("CacheClear", "Failed to delete file: " + file.getName());
                                }
                            }
                        } catch (SecurityException e) {
                            android.util.Log.w("CacheClear", "No permission to delete: " + file.getName());
                        } catch (Exception e) {
                            android.util.Log.w("CacheClear", "Error deleting file: " + file.getName() + " - " + e.getMessage());
                        }
                    }
                }
            }
            return clearedSize;
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
        
        // Storage information class
        private static class StorageInfo {
            long totalDeviceSpace = 0;
            long availableSpace = 0;
            long totalUsed = 0;
            long modelsSize = 0;
            long cacheSize = 0;
            long databaseSize = 0;
            long tempSize = 0;
            int modelCount = 0;
            
            String getFormattedTotalUsed() {
                return formatBytesStatic(totalUsed);
            }
            
            String getFormattedAvailableSpace() {
                return formatBytesStatic(availableSpace);
            }
            
            String getFormattedModelsSize() {
                return formatBytesStatic(modelsSize);
            }
            
            String getFormattedCacheSize() {
                return formatBytesStatic(cacheSize);
            }
            
            String getFormattedDatabaseSize() {
                return formatBytesStatic(databaseSize);
            }
            
            String getFormattedTempSize() {
                return formatBytesStatic(tempSize);
            }
            
            double getUsagePercentage() {
                if (totalDeviceSpace == 0) return 0.0;
                long usedSpace = totalDeviceSpace - availableSpace;
                return (double) usedSpace / totalDeviceSpace * 100.0;
            }
            
            private static String formatBytesStatic(long bytes) {
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
        }
    }

    // Chat Settings Fragment
    public static class ChatSettingsFragment extends BaseSettingsFragment {
        
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_chat, rootKey);
            
            if (getActivity() != null) {
                androidx.appcompat.app.ActionBar actionBar = ((SettingsActivity) getActivity()).getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle("Chat Settings");
                    applyToolbarTypography();
                }
            }
            
            // Setup manual delete trigger
            setupManualDeleteTrigger();
            
            // Setup clear search history
            setupClearSearchHistory();
            
            // Schedule or cancel auto-delete based on current settings
            scheduleAutoDeleteIfNeeded();
        }
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            super.onSharedPreferenceChanged(sharedPreferences, key);
            
            // Handle auto-delete settings changes
            if ("delete_old_messages".equals(key) || "delete_messages_after".equals(key)) {
                scheduleAutoDeleteIfNeeded();
                
                // Show feedback to user
                if ("delete_old_messages".equals(key)) {
                    boolean enabled = sharedPreferences.getBoolean(key, false);
                    if (getActivity() != null) {
                        String message = enabled ? 
                            "Auto-delete enabled. Old messages will be removed periodically (starred messages are preserved)." :
                            "Auto-delete disabled. Messages will not be automatically deleted.";
                        android.widget.Toast.makeText(getActivity(), message, 
                            android.widget.Toast.LENGTH_LONG).show();
                    }
                } else {
                    if (getActivity() != null) {
                        String period = sharedPreferences.getString(key, "90");
                        android.widget.Toast.makeText(getActivity(), 
                            "Messages older than " + period + " days will be deleted (starred messages preserved)", 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            }
            // Handle search history settings changes
            else if ("message_search_history".equals(key)) {
                boolean enabled = sharedPreferences.getBoolean(key, true);
                if (getActivity() != null) {
                    if (!enabled) {
                        // If search history is disabled, offer to clear existing history
                        showClearSearchHistoryOnDisableDialog();
                    } else {
                        android.widget.Toast.makeText(getActivity(), 
                            "Search history enabled. Your searches will be saved for suggestions.", 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
                // Update the clear search history preference summary
                updateClearSearchHistorySummary();
            }
        }
        
        /**
         * Schedule or cancel auto-delete work based on current settings
         */
        private void scheduleAutoDeleteIfNeeded() {
            if (getActivity() == null) return;
            
            SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
            boolean autoDeleteEnabled = prefs.getBoolean("delete_old_messages", false);
            
            androidx.work.WorkManager workManager = androidx.work.WorkManager.getInstance(getActivity());
            
            if (autoDeleteEnabled) {
                // Schedule periodic work to delete old messages
                // Run daily to check for old messages
                androidx.work.PeriodicWorkRequest deleteWorkRequest = 
                    new androidx.work.PeriodicWorkRequest.Builder(
                        com.keralatechreach.mobigpt.workers.DeleteOldMessagesWorker.class,
                        1, // repeat every 1 day
                        java.util.concurrent.TimeUnit.DAYS,
                        12, // with 12 hours flex period
                        java.util.concurrent.TimeUnit.HOURS)
                    .setConstraints(new androidx.work.Constraints.Builder()
                        .setRequiresBatteryNotLow(true) // Only run when battery is not low
                        .build())
                    .addTag("auto_delete_messages")
                    .build();
                
                // Use unique work to avoid duplicate scheduling
                workManager.enqueueUniquePeriodicWork(
                    "auto_delete_old_messages",
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    deleteWorkRequest
                );
                
                android.util.Log.i("ChatSettings", "Auto-delete work scheduled");
            } else {
                // Cancel the scheduled work
                workManager.cancelUniqueWork("auto_delete_old_messages");
                android.util.Log.i("ChatSettings", "Auto-delete work cancelled");
            }
        }
        
        /**
         * Setup the manual delete trigger preference
         */
        private void setupManualDeleteTrigger() {
            androidx.preference.Preference deleteNowPref = findPreference("delete_old_messages_now");
            if (deleteNowPref != null) {
                deleteNowPref.setOnPreferenceClickListener(preference -> {
                    showDeleteConfirmationDialog();
                    return true;
                });
            }
        }
        
        /**
         * Setup the clear search history preference
         */
        private void setupClearSearchHistory() {
            androidx.preference.Preference clearSearchHistoryPref = findPreference("clear_search_history");
            if (clearSearchHistoryPref != null) {
                // Update summary with current history count
                updateClearSearchHistorySummary();
                
                clearSearchHistoryPref.setOnPreferenceClickListener(preference -> {
                    showClearSearchHistoryDialog();
                    return true;
                });
            }
        }
        
        /**
         * Update the clear search history preference summary with current count
         */
        private void updateClearSearchHistorySummary() {
            androidx.preference.Preference clearSearchHistoryPref = findPreference("clear_search_history");
            if (clearSearchHistoryPref != null && getActivity() != null) {
                com.keralatechreach.mobigpt.utils.SearchHistoryManager searchHistoryManager = 
                    new com.keralatechreach.mobigpt.utils.SearchHistoryManager(getActivity());
                
                int historySize = searchHistoryManager.getHistorySize();
                if (historySize > 0) {
                    clearSearchHistoryPref.setSummary(historySize + " search " + 
                        (historySize == 1 ? "query" : "queries") + " saved");
                } else {
                    clearSearchHistoryPref.setSummary("No search history");
                }
            }
        }
        
        /**
         * Show dialog to clear search history
         */
        private void showClearSearchHistoryDialog() {
            if (getActivity() == null) return;
            
            com.keralatechreach.mobigpt.utils.SearchHistoryManager searchHistoryManager = 
                new com.keralatechreach.mobigpt.utils.SearchHistoryManager(getActivity());
            
            int historySize = searchHistoryManager.getHistorySize();
            
            if (historySize == 0) {
                android.widget.Toast.makeText(getActivity(), 
                    "No search history to clear", 
                    android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            String message = String.format(
                Locale.getDefault(),
                "This will delete all %d saved search %s.\n\n" +
                "This action cannot be undone. Continue?",
                historySize,
                historySize == 1 ? "query" : "queries"
            );
            
            new android.app.AlertDialog.Builder(getActivity())
                .setTitle("Clear Search History")
                .setMessage(message)
                .setPositiveButton("Clear", (dialog, which) -> {
                    searchHistoryManager.clearSearchHistory();
                    updateClearSearchHistorySummary();
                    android.widget.Toast.makeText(getActivity(), 
                        "Search history cleared", 
                        android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
        }
        
        /**
         * Show dialog when search history is disabled, offering to clear existing data
         */
        private void showClearSearchHistoryOnDisableDialog() {
            if (getActivity() == null) return;
            
            com.keralatechreach.mobigpt.utils.SearchHistoryManager searchHistoryManager = 
                new com.keralatechreach.mobigpt.utils.SearchHistoryManager(getActivity());
            
            // Note: We check using direct prefs since the setting was just changed
            int historySize = searchHistoryManager.getSearchHistory().size(); // Use direct method
            
            if (historySize == 0) {
                android.widget.Toast.makeText(getActivity(), 
                    "Search history disabled. No searches will be saved.", 
                    android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            String message = String.format(
                Locale.getDefault(),
                "Search history has been disabled.\n\n" +
                "You have %d saved search %s. Would you like to clear %s?",
                historySize,
                historySize == 1 ? "query" : "queries",
                historySize == 1 ? "it" : "them"
            );
            
            new android.app.AlertDialog.Builder(getActivity())
                .setTitle("Clear Existing History?")
                .setMessage(message)
                .setPositiveButton("Clear History", (dialog, which) -> {
                    searchHistoryManager.clearSearchHistory();
                    updateClearSearchHistorySummary();
                    android.widget.Toast.makeText(getActivity(), 
                        "Search history disabled and cleared", 
                        android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Keep History", (dialog, which) -> 
                    android.widget.Toast.makeText(getActivity(), 
                        "Search history disabled. Existing history kept but won't be shown.", 
                        android.widget.Toast.LENGTH_SHORT).show())
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
        }
        
        /**
         * Show confirmation dialog before manually deleting old messages
         */
        private void showDeleteConfirmationDialog() {
            if (getActivity() == null) return;
            
            SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
            String deletePeriod = prefs.getString("delete_messages_after", "90");
            
            String message = String.format(
                "This will delete all messages older than %s days across all chats.\n\n" +
                "⭐ Starred messages will NOT be deleted.\n\n" +
                "This action cannot be undone. Continue?",
                deletePeriod
            );
            
            new android.app.AlertDialog.Builder(getActivity())
                .setTitle("Delete Old Messages")
                .setMessage(message)
                .setPositiveButton("Delete", (dialog, which) -> triggerManualDelete())
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
        }
        
        /**
         * Trigger manual deletion of old messages
         */
        private void triggerManualDelete() {
            if (getActivity() == null) return;
            
            // Show progress dialog
            android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(getActivity());
            progressDialog.setMessage("Deleting old messages...");
            progressDialog.setCancelable(false);
            progressDialog.show();
            
            // Run deletion in background thread
            new Thread(() -> {
                try {
                    Context context = getActivity();
                    if (context == null) return;
                    
                    SettingsManager settingsManager = new SettingsManager(context);
                    int deletePeriodDays = settingsManager.getDeleteMessagesAfterDays();
                    
                    // Calculate the cutoff timestamp
                    long currentTime = System.currentTimeMillis();
                    long cutoffTime = currentTime - java.util.concurrent.TimeUnit.DAYS.toMillis(deletePeriodDays);
                    
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
                            // Count messages before deletion
                            int messageCountBefore = messageDao.getMessageCountForChat(chat.id);
                            
                            // Delete old messages (excluding starred)
                            messageDao.deleteOldMessages(chat.id, cutoffTime);
                            
                            // Count messages after deletion
                            int messageCountAfter = messageDao.getMessageCountForChat(chat.id);
                            int deletedCount = messageCountBefore - messageCountAfter;
                            
                            totalDeletedCount += deletedCount;
                            chatsProcessed++;
                            
                        } catch (Exception e) {
                            android.util.Log.e("ChatSettings", "Error deleting messages for chat " + chat.id, e);
                        }
                    }
                    
                    final int finalDeletedCount = totalDeletedCount;
                    final int finalChatsProcessed = chatsProcessed;
                    
                    // Show result on UI thread
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            
                            String resultMessage = String.format(
                                Locale.getDefault(),
                                "Deletion complete!\n\n" +
                                "📊 Chats processed: %d\n" +
                                "🗑️ Messages deleted: %d\n" +
                                "⭐ Starred messages: Preserved",
                                finalChatsProcessed,
                                finalDeletedCount
                            );
                            
                            new android.app.AlertDialog.Builder(getActivity())
                                .setTitle("Deletion Complete")
                                .setMessage(resultMessage)
                                .setPositiveButton("OK", null)
                                .show();
                        });
                    }
                    
                } catch (Exception e) {
                    android.util.Log.e("ChatSettings", "Error in manual delete", e);
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            
                            android.widget.Toast.makeText(getActivity(),
                                "Error deleting messages: " + e.getMessage(),
                                android.widget.Toast.LENGTH_LONG).show();
                        });
                    }
                }
            }).start();
        }
    }

    // Appearance Settings Fragment
    public static class AppearanceSettingsFragment extends BaseSettingsFragment {
        
        private boolean isUpdatingPreferences = false;
        
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_appearance, rootKey);
            
            if (getActivity() != null) {
                androidx.appcompat.app.ActionBar actionBar = ((SettingsActivity) getActivity()).getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle("Appearance");
                    applyToolbarTypography();
                }
            }
        }
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            super.onSharedPreferenceChanged(sharedPreferences, key);
            
            // Prevent recursive updates
            if (isUpdatingPreferences) {
                return;
            }
            
            // Handle OLED black theme toggle
            if ("oled_black_theme".equals(key)) {
                boolean oledEnabled = sharedPreferences.getBoolean(key, false);
                
                if (oledEnabled) {
                    // OLED theme enabled - automatically switch to dark theme
                    String currentTheme = sharedPreferences.getString("theme_mode", "system");
                    if (!"dark".equals(currentTheme)) {
                        isUpdatingPreferences = true;
                        sharedPreferences.edit().putString("theme_mode", "dark").apply();
                        isUpdatingPreferences = false;
                        
                        if (getActivity() != null) {
                            android.widget.Toast.makeText(getActivity(), 
                                "OLED black theme enabled. Dark theme activated automatically.", 
                                android.widget.Toast.LENGTH_LONG).show();
                        }
                    } else {
                        if (getActivity() != null) {
                            android.widget.Toast.makeText(getActivity(), 
                                "OLED black theme enabled", 
                                android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    // OLED theme disabled
                    if (getActivity() != null) {
                        android.widget.Toast.makeText(getActivity(), 
                            "OLED black theme disabled", 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            }
            // Handle theme mode changes
            else if ("theme_mode".equals(key)) {
                String themeMode = sharedPreferences.getString(key, "system");
                boolean oledEnabled = sharedPreferences.getBoolean("oled_black_theme", false);
                
                // If OLED is enabled but theme is switched to light or system, disable OLED
                if (oledEnabled && !"dark".equals(themeMode)) {
                    isUpdatingPreferences = true;
                    sharedPreferences.edit().putBoolean("oled_black_theme", false).apply();
                    isUpdatingPreferences = false;
                    
                    if (getActivity() != null) {
                        android.widget.Toast.makeText(getActivity(), 
                            "Theme changed to " + themeMode + ". OLED black theme disabled automatically.", 
                            android.widget.Toast.LENGTH_LONG).show();
                    }
                } else {
                    if (getActivity() != null) {
                        android.widget.Toast.makeText(getActivity(), 
                            "Theme changed to " + themeMode, 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            }
            
            // Handle theme-related preference changes
            if ("theme_mode".equals(key) || "accent_color".equals(key) || "oled_black_theme".equals(key) ||
                "font_size".equals(key) || "font_family".equals(key) || "monospace_code".equals(key)) {
                if (getActivity() != null) {
                    // Apply new theme
                    com.keralatechreach.mobigpt.utils.ThemeManager themeManager = 
                        new com.keralatechreach.mobigpt.utils.ThemeManager(getActivity());
                    themeManager.applyTheme();
                    
                    // Show feedback for theme color
                    if ("accent_color".equals(key)) {
                        String accentColor = sharedPreferences.getString(key, "green");
                        android.widget.Toast.makeText(getActivity(), 
                            "Theme color changed to " + accentColor, 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                    
                    // Show feedback for typography changes
                    if ("font_size".equals(key)) {
                        String fontSize = sharedPreferences.getString(key, "medium");
                        android.widget.Toast.makeText(getActivity(), 
                            "Font size changed to " + fontSize, 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                    
                    if ("font_family".equals(key)) {
                        String fontFamily = sharedPreferences.getString(key, "system");
                        android.widget.Toast.makeText(getActivity(), 
                            "Font family changed to " + fontFamily, 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                    
                    if ("monospace_code".equals(key)) {
                        boolean enabled = sharedPreferences.getBoolean(key, true);
                        android.widget.Toast.makeText(getActivity(), 
                            "Monospace code " + (enabled ? "enabled" : "disabled"), 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                    
                    // Immediately apply typography to current Settings UI without waiting for recreate
                    try {
                        if (getView() != null && getContext() != null) {
                            TypographyManager typographyManager = new TypographyManager(getContext());
                            // Apply to the preferences hierarchy
                            applyTypographyToViewGroup((android.view.ViewGroup) getView(), typographyManager);
                        }
                        // Apply to the toolbar title as well
                        applyToolbarTypography();
                    } catch (Exception ignored) {}

                    // Recreate activity to apply theme immediately
                    new android.os.Handler().postDelayed(() -> {
                        if (getActivity() != null) {
                            getActivity().recreate();
                        }
                    }, 100); // Reduced delay
                }
            }
        }
    }

    // Data & Privacy Settings Fragment
    public static class DataPrivacySettingsFragment extends BaseSettingsFragment {
        private static final int REQUEST_CODE_AUTH_FOR_EXPORT_ALL = 9003;
        private boolean pendingExportAll = false;
        
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_data_privacy, rootKey);
            
            if (getActivity() != null) {
                androidx.appcompat.app.ActionBar actionBar = ((SettingsActivity) getActivity()).getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle("Data & Privacy");
                    applyToolbarTypography();
                }
            }
            
            // Setup click listeners
            setupExportAllChats();
            setupClearChatHistory();
            setupClearStarredMessages();
            setupClearAllData();
        }
        
        /**
         * Setup export all chats preference
         */
        private void setupExportAllChats() {
            Preference exportAllChats = findPreference("export_all_chats");
            if (exportAllChats != null) {
                exportAllChats.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        showExportAllChatsDialog();
                    }
                    return true;
                });
            }
        }
        
        /**
         * Show dialog to export all chats
         */
        private void showExportAllChatsDialog() {
            if (getContext() == null) return;
            
            // Check if authentication is required for export
            com.keralatechreach.mobigpt.security.SecurityManager securityManager = 
                com.keralatechreach.mobigpt.security.SecurityManager.getInstance(getContext());
            
            if (securityManager != null && securityManager.isAuthRequiredForExport() && securityManager.isLockSetupComplete()) {
                // Authentication required, request it first
                pendingExportAll = true;
                requestAuthenticationForExportAll();
                return;
            }
            
            // No authentication required, show the export dialog
            showExportAllChatsConfirmDialog();
        }
        
        /**
         * Show confirmation dialog to export all chats
         */
        private void showExportAllChatsConfirmDialog() {
            if (getContext() == null) return;
            
            new android.app.AlertDialog.Builder(getContext())
                .setTitle("Export All Chats")
                .setMessage("Export all your conversations as a single PDF file?\n\nThis may take a moment depending on the number of conversations.")
                .setPositiveButton("Export", (dialog, which) -> exportAllChatsToPDF())
                .setNegativeButton("Cancel", null)
                .show();
        }
        
        /**
         * Request authentication for export all chats
         */
        private void requestAuthenticationForExportAll() {
            if (getActivity() == null || getContext() == null) return;
            
            com.keralatechreach.mobigpt.security.SecurityManager securityManager = 
                com.keralatechreach.mobigpt.security.SecurityManager.getInstance(getContext());
            if (securityManager == null) {
                showExportAllChatsConfirmDialog();
                return;
            }
            
            String lockType = securityManager.getLockType();
            if ("biometric".equals(lockType) && securityManager.isBiometricAvailable(getContext())) {
                // Show biometric prompt
                securityManager.showBiometricPrompt(
                    getActivity(),
                    "Export Authentication",
                    "Authenticate to export all conversations",
                    new com.keralatechreach.mobigpt.security.SecurityManager.BiometricAuthCallback() {
                        @Override
                        public void onAuthenticationSucceeded() {
                            pendingExportAll = false;
                            showExportAllChatsConfirmDialog();
                        }
                        
                        @Override
                        public void onAuthenticationFailed() {
                            android.widget.Toast.makeText(getContext(), 
                                "Authentication failed", 
                                android.widget.Toast.LENGTH_SHORT).show();
                            pendingExportAll = false;
                        }
                        
                        @Override
                        public void onAuthenticationError(int errorCode, String errString) {
                            if (errorCode == androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                // Show PIN screen
                                showPinAuthenticationForExportAll();
                            } else {
                                android.widget.Toast.makeText(getContext(), 
                                    "Authentication error: " + errString, 
                                    android.widget.Toast.LENGTH_SHORT).show();
                                pendingExportAll = false;
                            }
                        }
                    }
                );
            } else {
                // Use PIN authentication
                showPinAuthenticationForExportAll();
            }
        }
        
        /**
         * Show PIN authentication for export all
         */
        private void showPinAuthenticationForExportAll() {
            if (getActivity() == null) return;
            
            android.content.Intent intent = new android.content.Intent(getActivity(), 
                com.keralatechreach.mobigpt.security.LockScreenActivity.class);
            intent.putExtra(com.keralatechreach.mobigpt.security.LockScreenActivity.EXTRA_MODE, 
                com.keralatechreach.mobigpt.security.LockScreenActivity.MODE_UNLOCK);
            startActivityForResult(intent, REQUEST_CODE_AUTH_FOR_EXPORT_ALL);
        }
        
        @Override
        public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            
            if (requestCode == REQUEST_CODE_AUTH_FOR_EXPORT_ALL && pendingExportAll) {
                if (resultCode == android.app.Activity.RESULT_OK) {
                    // Authentication successful
                    pendingExportAll = false;
                    showExportAllChatsConfirmDialog();
                } else {
                    // Authentication failed or cancelled
                    android.widget.Toast.makeText(getContext(), 
                        "Authentication cancelled", 
                        android.widget.Toast.LENGTH_SHORT).show();
                    pendingExportAll = false;
                }
            }
        }
        
        /**
         * Export all chats to PDF
         */
        private void exportAllChatsToPDF() {
            if (getContext() == null) return;
            
            // Show progress dialog
            android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(getContext());
            progressDialog.setMessage("Exporting all chats...");
            progressDialog.setCancelable(false);
            progressDialog.show();
            
            // Export in background thread
            new Thread(() -> {
                try {
                    android.net.Uri fileUri = ExportHelper.exportAllChatsToPDF(getContext());
                    
                    // Update UI on main thread
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            
                            if (fileUri != null) {
                                showExportSuccessDialog(fileUri);
                            } else {
                                showExportFailureDialog();
                            }
                        });
                    }
                } catch (Exception e) {
                    android.util.Log.e("DataPrivacy", "Error exporting all chats", e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            showExportFailureDialog();
                        });
                    }
                }
            }).start();
        }
        
        /**
         * Show success dialog after export
         */
        private void showExportSuccessDialog(android.net.Uri fileUri) {
            if (getContext() == null) return;
            
            new android.app.AlertDialog.Builder(getContext())
                .setTitle("Export Complete")
                .setMessage("All conversations have been exported successfully!\n\nThe file has been saved to your Downloads folder.")
                .setPositiveButton("Open", (dialog, which) -> {
                    // Open the file
                    android.content.Intent openIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                    openIntent.setDataAndType(fileUri, "application/pdf");
                    openIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    openIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    try {
                        getContext().startActivity(openIntent);
                    } catch (android.content.ActivityNotFoundException e) {
                        android.widget.Toast.makeText(getContext(), 
                            "No PDF viewer found", 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Share", (dialog, which) -> {
                    // Share the file
                    ExportHelper.shareFile(getContext(), fileUri, "application/pdf");
                })
                .setNeutralButton("Done", null)
                .show();
        }
        
        /**
         * Show failure dialog if export fails
         */
        private void showExportFailureDialog() {
            if (getContext() == null) return;
            
            new android.app.AlertDialog.Builder(getContext())
                .setTitle("Export Failed")
                .setMessage("Failed to export conversations. Please make sure you have storage permissions and enough space.")
                .setPositiveButton("OK", null)
                .show();
        }
        
        /**
         * Setup clear chat history preference
         */
        private void setupClearChatHistory() {
            Preference clearChatHistory = findPreference("clear_chat_history");
            if (clearChatHistory != null) {
                clearChatHistory.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        showClearChatHistoryDialog();
                    }
                    return true;
                });
            }
        }
        
        /**
         * Show dialog to clear chat history
         */
        private void showClearChatHistoryDialog() {
            if (getContext() == null) return;
            
            new android.app.AlertDialog.Builder(getContext())
                .setTitle("Clear Chat History")
                .setMessage("This will permanently delete all your conversations. This action cannot be undone.\n\nAre you sure?")
                .setPositiveButton("Delete All", (dialog, which) -> clearChatHistory())
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
        }
        
        /**
         * Clear all chat history
         */
        private void clearChatHistory() {
            if (getContext() == null) return;
            
            new Thread(() -> {
                try {
                    ChatDatabase database = ChatDatabase.getDatabase(getContext());
                    database.chatDao().deleteAllChats();
                    database.messageDao().deleteAllMessages();
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> android.widget.Toast.makeText(getContext(), 
                            "Chat history cleared", 
                            android.widget.Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {
                    android.util.Log.e("DataPrivacy", "Error clearing chat history", e);
                }
            }).start();
        }
        
        /**
         * Setup clear starred messages preference
         */
        private void setupClearStarredMessages() {
            Preference clearStarredMessages = findPreference("clear_starred_messages");
            if (clearStarredMessages != null) {
                clearStarredMessages.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        showClearStarredMessagesDialog();
                    }
                    return true;
                });
            }
        }
        
        /**
         * Show dialog to clear starred messages
         */
        private void showClearStarredMessagesDialog() {
            if (getContext() == null) return;
            
            new android.app.AlertDialog.Builder(getContext())
                .setTitle("Clear Starred Messages")
                .setMessage("This will remove all bookmarked messages. This action cannot be undone.\n\nAre you sure?")
                .setPositiveButton("Clear", (dialog, which) -> clearStarredMessages())
                .setNegativeButton("Cancel", null)
                .show();
        }
        
        /**
         * Clear all starred messages
         */
        private void clearStarredMessages() {
            if (getContext() == null) return;
            
            new Thread(() -> {
                try {
                    ChatDatabase database = ChatDatabase.getDatabase(getContext());
                    MessageDao messageDao = database.messageDao();
                    List<com.keralatechreach.mobigpt.database.Message> starredMessages = 
                        messageDao.getStarredMessages();
                    
                    for (com.keralatechreach.mobigpt.database.Message message : starredMessages) {
                        message.isStarred = false;
                        messageDao.updateMessage(message);
                    }
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> android.widget.Toast.makeText(getContext(), 
                            "Starred messages cleared", 
                            android.widget.Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {
                    android.util.Log.e("DataPrivacy", "Error clearing starred messages", e);
                }
            }).start();
        }
        
        /**
         * Setup clear all data preference
         */
        private void setupClearAllData() {
            Preference clearAllData = findPreference("clear_all_data");
            if (clearAllData != null) {
                clearAllData.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null) {
                        showClearAllDataDialog();
                    }
                    return true;
                });
            }
        }
        
        /**
         * Show dialog to clear all data
         */
        private void showClearAllDataDialog() {
            if (getContext() == null) return;
            
            new android.app.AlertDialog.Builder(getContext())
                .setTitle("Clear All Data")
                .setMessage("This will delete ALL app data including:\n• All conversations\n• Starred messages\n• Search history\n• Settings\n• Cache\n\nThis action cannot be undone. Are you sure?")
                .setPositiveButton("Delete Everything", (dialog, which) -> clearAllData())
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
        }
        
        /**
         * Clear all app data
         */
        private void clearAllData() {
            if (getContext() == null) return;
            
            new Thread(() -> {
                try {
                    // Clear database
                    ChatDatabase database = ChatDatabase.getDatabase(getContext());
                    database.chatDao().deleteAllChats();
                    database.messageDao().deleteAllMessages();
                    
                    // Clear preferences
                    android.content.SharedPreferences prefs = 
                        androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
                    prefs.edit().clear().apply();
                    
                    // Clear cache
                    clearCache(getContext());
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            android.widget.Toast.makeText(getContext(), 
                                "All data cleared. Please restart the app.", 
                                android.widget.Toast.LENGTH_LONG).show();
                            
                            // Exit app after a delay
                            new android.os.Handler().postDelayed(() -> {
                                if (getActivity() != null) {
                                    getActivity().finishAffinity();
                                }
                            }, 2000);
                        });
                    }
                } catch (Exception e) {
                    android.util.Log.e("DataPrivacy", "Error clearing all data", e);
                }
            }).start();
        }
        
        /**
         * Clear app cache
         */
        private void clearCache(Context context) {
            try {
                java.io.File cacheDir = context.getCacheDir();
                if (cacheDir != null && cacheDir.isDirectory()) {
                    deleteDir(cacheDir);
                }
            } catch (Exception e) {
                android.util.Log.e("DataPrivacy", "Error clearing cache", e);
            }
        }
        
        /**
         * Recursively delete directory contents
         */
        private boolean deleteDir(java.io.File dir) {
            if (dir != null && dir.isDirectory()) {
                String[] children = dir.list();
                if (children != null) {
                    for (String child : children) {
                        boolean success = deleteDir(new java.io.File(dir, child));
                        if (!success) {
                            return false;
                        }
                    }
                }
                return dir.delete();
            } else if (dir != null && dir.isFile()) {
                return dir.delete();
            } else {
                return false;
            }
        }
    }

    // Security Settings Fragment
    public static class SecuritySettingsFragment extends BaseSettingsFragment {
        
        private com.keralatechreach.mobigpt.security.SecurityManager securityManager;
        
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            try {
                setPreferencesFromResource(R.xml.preferences_security, rootKey);
                
                if (getActivity() != null) {
                    androidx.appcompat.app.ActionBar actionBar = ((SettingsActivity) getActivity()).getSupportActionBar();
                    if (actionBar != null) {
                        actionBar.setTitle("Security");
                        applyToolbarTypography();
                    }
                    
                    // Initialize security manager with proper error handling
                    try {
                        securityManager = com.keralatechreach.mobigpt.security.SecurityManager.getInstance(getActivity());
                    } catch (Exception e) {
                        android.util.Log.e("SecuritySettings", "Failed to initialize SecurityManager", e);
                        securityManager = null;
                    }
                }
                
                // Delay setup to ensure fragment is fully initialized
                if (getView() != null) {
                    setupSecurityPreferences();
                } else {
                    // Post setup to run after view is created
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(this::setupSecurityPreferences);
                }
            } catch (Exception e) {
                android.util.Log.e("SecuritySettings", "Error in onCreatePreferences", e);
                // Show error to user
                if (getContext() != null) {
                    android.widget.Toast.makeText(getContext(), 
                        "Error loading security settings", 
                        android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        }
        
        @Override
        public void onViewCreated(@androidx.annotation.NonNull android.view.View view, @androidx.annotation.Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            
            // Ensure preferences are set up after view is created
            if (securityManager == null && getActivity() != null) {
                try {
                    securityManager = com.keralatechreach.mobigpt.security.SecurityManager.getInstance(getActivity());
                } catch (Exception e) {
                    android.util.Log.e("SecuritySettings", "Failed to initialize SecurityManager in onViewCreated", e);
                }
            }
            
            // Setup preferences now that view is ready
            setupSecurityPreferences();
        }
        
        private void setupSecurityPreferences() {
            // Ensure we have a valid context and security manager
            if (getContext() == null || securityManager == null) {
                android.util.Log.w("SecuritySettings", "Context or SecurityManager is null, skipping setup");
                return;
            }
            
            // App Lock toggle
            androidx.preference.SwitchPreferenceCompat appLockPref = findPreference("app_lock_enabled");
            if (appLockPref != null) {
                appLockPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enabled = (Boolean) newValue;
                    if (enabled && securityManager != null && !securityManager.isLockSetupComplete()) {
                        // Need to set up PIN first
                        showPinSetupDialog();
                        return false; // Don't enable yet
                    }
                    return true;
                });
            }
            
            // Lock Type preference
            androidx.preference.ListPreference lockTypePref = findPreference("lock_type");
            if (lockTypePref != null && securityManager != null) {
                try {
                    // Disable biometric option if not available
                    if (!securityManager.isBiometricAvailable(getContext())) {
                        lockTypePref.setEntries(new CharSequence[]{"PIN"});
                        lockTypePref.setEntryValues(new CharSequence[]{"pin"});
                        lockTypePref.setValue("pin");
                        lockTypePref.setSummary("PIN (Biometric not available)");
                    }
                } catch (Exception e) {
                    android.util.Log.e("SecuritySettings", "Error setting up lock type preference", e);
                    // Fallback to PIN only
                    lockTypePref.setEntries(new CharSequence[]{"PIN"});
                    lockTypePref.setEntryValues(new CharSequence[]{"pin"});
                    lockTypePref.setValue("pin");
                    lockTypePref.setSummary("PIN");
                }
            }
            
            // Reset Security button
            Preference resetSecurityPref = findPreference("reset_security");
            if (resetSecurityPref != null) {
                resetSecurityPref.setOnPreferenceClickListener(preference -> {
                    showResetSecurityDialog();
                    return true;
                });
            }
        }
        
        private void showPinSetupDialog() {
            if (getActivity() == null) return;
            
            new AlertDialog.Builder(getActivity())
                .setTitle("Set Up PIN")
                .setMessage("You need to set up a PIN before enabling app lock. Would you like to set it up now?")
                .setPositiveButton("Set Up PIN", (dialog, which) -> startPinSetup())
                .setNegativeButton("Cancel", null)
                .show();
        }
        
        private void startPinSetup() {
            if (getActivity() == null) return;
            
            android.content.Intent intent = new android.content.Intent(getActivity(), 
                com.keralatechreach.mobigpt.security.LockScreenActivity.class);
            intent.putExtra(com.keralatechreach.mobigpt.security.LockScreenActivity.EXTRA_MODE, 
                com.keralatechreach.mobigpt.security.LockScreenActivity.MODE_SETUP_PIN);
            startActivityForResult(intent, 1001);
        }
        
        private void showResetSecurityDialog() {
            if (getActivity() == null) return;
            
            new AlertDialog.Builder(getActivity())
                .setTitle("Reset Security Settings")
                .setMessage("This will clear all security configurations including your PIN. You will need to set up security again if you want to use app lock.\n\nAre you sure?")
                .setPositiveButton("Reset", (dialog, which) -> resetSecurity())
                .setNegativeButton("Cancel", null)
                .show();
        }
        
        private void resetSecurity() {
            if (getActivity() == null) return;
            
            securityManager.resetSecuritySettings();
            
            // Disable app lock in preferences
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            prefs.edit().putBoolean("app_lock_enabled", false).apply();
            
            // Refresh preferences screen
            setPreferencesFromResource(R.xml.preferences_security, null);
            setupSecurityPreferences();
            
            android.widget.Toast.makeText(getActivity(), 
                "Security settings reset successfully", 
                android.widget.Toast.LENGTH_SHORT).show();
        }
        
        @Override
        public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            
            if (requestCode == 1001 && resultCode == android.app.Activity.RESULT_OK) {
                if (getActivity() == null) return;
                // PIN setup successful, now enable app lock
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                prefs.edit().putBoolean("app_lock_enabled", true).apply();
                
                // Refresh preferences
                setPreferencesFromResource(R.xml.preferences_security, null);
                setupSecurityPreferences();
                
                android.widget.Toast.makeText(getActivity(), 
                    "App lock enabled successfully", 
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        }
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            super.onSharedPreferenceChanged(sharedPreferences, key);
            
            // Handle preference changes if needed
            if ("lock_type".equals(key) || "lock_timeout".equals(key) || "lock_on_app_switch".equals(key)) {
                // Settings changed, security manager will pick them up automatically
                android.util.Log.d("SecuritySettings", "Security preference changed: " + key);
            }
        }
    }

    // About Settings Fragment
    public static class AboutSettingsFragment extends BaseSettingsFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_about, rootKey);
            
            if (getActivity() != null) {
                androidx.appcompat.app.ActionBar actionBar = ((SettingsActivity) getActivity()).getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle("About");
                    applyToolbarTypography();
                }
            }
            
            setupAboutPreferences();
        }
        
        private void setupAboutPreferences() {
            // Contact Support
            Preference contactSupport = findPreference("contact_support");
            if (contactSupport != null) {
                contactSupport.setOnPreferenceClickListener(preference -> {
                    openContactSupport();
                    return true;
                });
            }
            
            // Send Feedback
            Preference sendFeedback = findPreference("send_feedback");
            if (sendFeedback != null) {
                sendFeedback.setOnPreferenceClickListener(preference -> {
                    openSendFeedback();
                    return true;
                });
            }
            
            // Rate App
            Preference rateApp = findPreference("rate_app");
            if (rateApp != null) {
                rateApp.setOnPreferenceClickListener(preference -> {
                    openPlayStore();
                    return true;
                });
            }
            
            // Share App
            Preference shareApp = findPreference("share_app");
            if (shareApp != null) {
                shareApp.setOnPreferenceClickListener(preference -> {
                    shareAppWithFriends();
                    return true;
                });
            }
            
            // Website
            Preference website = findPreference("website");
            if (website != null) {
                website.setOnPreferenceClickListener(preference -> {
                    openWebsite();
                    return true;
                });
            }
        }
        
        private void openContactSupport() {
            if (getActivity() == null) return;
            
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SENDTO);
            intent.setData(android.net.Uri.parse("mailto:"));
            intent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{
                com.keralatechreach.mobigpt.utils.SettingsConstants.SUPPORT_EMAIL
            });
            intent.putExtra(android.content.Intent.EXTRA_SUBJECT, 
                com.keralatechreach.mobigpt.utils.SettingsConstants.getSupportEmailSubject());
            intent.putExtra(android.content.Intent.EXTRA_TEXT, 
                "\n\n---\nPlease describe your issue or question above.\n");
            
            try {
                startActivity(android.content.Intent.createChooser(intent, "Send Support Email"));
            } catch (android.content.ActivityNotFoundException e) {
                android.widget.Toast.makeText(getActivity(), 
                    "No email app found. Please email us at: " + 
                    com.keralatechreach.mobigpt.utils.SettingsConstants.SUPPORT_EMAIL, 
                    android.widget.Toast.LENGTH_LONG).show();
            }
        }
        
        private void openSendFeedback() {
            if (getActivity() == null) return;
            
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SENDTO);
            intent.setData(android.net.Uri.parse("mailto:"));
            intent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{
                com.keralatechreach.mobigpt.utils.SettingsConstants.FEEDBACK_EMAIL
            });
            intent.putExtra(android.content.Intent.EXTRA_SUBJECT, 
                com.keralatechreach.mobigpt.utils.SettingsConstants.getFeedbackEmailSubject());
            intent.putExtra(android.content.Intent.EXTRA_TEXT, 
                "\n\n---\nPlease share your thoughts and suggestions above.\n");
            
            try {
                startActivity(android.content.Intent.createChooser(intent, "Send Feedback"));
            } catch (android.content.ActivityNotFoundException e) {
                android.widget.Toast.makeText(getActivity(), 
                    "No email app found. Please email us at: " + 
                    com.keralatechreach.mobigpt.utils.SettingsConstants.FEEDBACK_EMAIL, 
                    android.widget.Toast.LENGTH_LONG).show();
            }
        }
        
        private void openPlayStore() {
            if (getActivity() == null) return;
            
            String packageName = com.keralatechreach.mobigpt.utils.SettingsConstants.PLAY_STORE_PACKAGE;
            
            try {
                // Try to open in Play Store app
                android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW, 
                    android.net.Uri.parse("market://details?id=" + packageName)
                );
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                // If Play Store app is not available, open in browser
                try {
                    android.content.Intent intent = new android.content.Intent(
                        android.content.Intent.ACTION_VIEW, 
                        android.net.Uri.parse(com.keralatechreach.mobigpt.utils.SettingsConstants.PLAY_STORE_URL)
                    );
                    startActivity(intent);
                } catch (Exception ex) {
                    android.widget.Toast.makeText(getActivity(), 
                        "Unable to open Play Store", 
                        android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        }
        
        private void shareAppWithFriends() {
            if (getActivity() == null) return;
            
            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Check out MobiGPT!");
            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, 
                com.keralatechreach.mobigpt.utils.SettingsConstants.SHARE_TEXT);
            
            try {
                startActivity(android.content.Intent.createChooser(shareIntent, "Share MobiGPT"));
            } catch (android.content.ActivityNotFoundException e) {
                android.widget.Toast.makeText(getActivity(), 
                    "Unable to share", 
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        }
        
        private void openWebsite() {
            if (getActivity() == null) return;
            
            String websiteUrl = com.keralatechreach.mobigpt.utils.SettingsConstants.WEBSITE_URL;
            
            try {
                android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW, 
                    android.net.Uri.parse(websiteUrl)
                );
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                android.widget.Toast.makeText(getActivity(), 
                    "No browser app found. Visit: " + websiteUrl, 
                    android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }
}
