package com.keralatechreach.mobigpt.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.tabs.TabLayout;
import com.keralatechreach.mobigpt.R;

/**
 * Unified theme color updater utility that handles all theme-related UI updates
 * for any activity or fragment in the app. This ensures consistent theming
 * across all UI components without leaving any element un-updated.
 */
public class ThemeColorUpdater {
    
    private static final String TAG = "ThemeColorUpdater";
    
    private final Context context;
    private final ThemeManager themeManager;
    private final ThemeColors themeColors;
    
    public ThemeColorUpdater(Context context) {
        this.context = context;
        this.themeManager = new ThemeManager(context);
        this.themeColors = new ThemeColors(context);
    }
    
    /**
     * Apply all theme colors to an activity
     * This is the main method that should be called to update all theme aspects
     */
    public void applyAllThemeColors(Activity activity) {
        if (activity == null) return;
        
        android.util.Log.d(TAG, "Applying all theme colors to " + activity.getClass().getSimpleName());
        
        // Apply theme to the activity itself
        themeManager.applyTheme();
        
        // Update status bar
        updateStatusBar(activity);
        
        // Update navigation bar
        updateNavigationBar(activity);
        
        // Update toolbar
        updateToolbar(activity);
        
        // Update all views in the activity
        View rootView = activity.findViewById(android.R.id.content);
        if (rootView != null) {
            updateViewHierarchy(rootView);
        }
        
        // Update action bar menu if available
        if (activity instanceof androidx.appcompat.app.AppCompatActivity) {
            androidx.appcompat.app.AppCompatActivity appCompatActivity = 
                (androidx.appcompat.app.AppCompatActivity) activity;
            updateActionBarMenu(appCompatActivity);
        }
        
        android.util.Log.d(TAG, "Theme colors applied successfully");
    }
    
    /**
     * Update status bar appearance (light/dark icons for contrast)
     */
    public void updateStatusBar(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        try {
            boolean isLightTheme = !themeManager.isDarkMode();
            int surfaceColor = themeColors.getSurfaceColor();
            
            // Light status bars (dark icons on light background) are supported on API 23+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.getWindow().setStatusBarColor(surfaceColor);
                androidx.core.view.WindowInsetsControllerCompat insetsController = 
                    androidx.core.view.WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
                if (insetsController != null) {
                    insetsController.setAppearanceLightStatusBars(isLightTheme);
                }
            } else {
                // On API < 23, keep status bar dark to preserve white icon legibility
                activity.getWindow().setStatusBarColor(isLightTheme ? 0xFF000000 : surfaceColor);
            }
            android.util.Log.d(TAG, "Status bar appearance updated (isLightTheme=" + isLightTheme + ")");
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error updating status bar appearance", e);
        }
    }
    
    /**
     * Update navigation bar appearance (light/dark icons for contrast)
     */
    public void updateNavigationBar(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        try {
            boolean isLightTheme = !themeManager.isDarkMode();
            int surfaceColor = themeColors.getSurfaceColor();
            
            // Light navigation bars (dark buttons on light background) are supported on API 27+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.getWindow().setNavigationBarColor(surfaceColor);
                androidx.core.view.WindowInsetsControllerCompat insetsController = 
                    androidx.core.view.WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
                if (insetsController != null) {
                    insetsController.setAppearanceLightNavigationBars(isLightTheme);
                }
            } else {
                // On API < 27, keep navigation bar dark so system buttons remain visible
                activity.getWindow().setNavigationBarColor(isLightTheme ? 0xFF000000 : surfaceColor);
            }
            android.util.Log.d(TAG, "Navigation bar appearance updated (isLightTheme=" + isLightTheme + ")");
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error updating navigation bar appearance", e);
        }
    }
    
    /**
     * Update toolbar colors and gradients
     */
    public void updateToolbar(Activity activity) {
        // Find toolbar in various possible locations
        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar == null) {
            toolbar = activity.findViewById(androidx.appcompat.R.id.action_bar);
        }
        
        if (toolbar != null) {
            applyToolbarGradient(toolbar);
        }
        
        // Also check for action bar
        if (activity instanceof androidx.appcompat.app.AppCompatActivity) {
            androidx.appcompat.app.AppCompatActivity appCompatActivity = 
                (androidx.appcompat.app.AppCompatActivity) activity;
            androidx.appcompat.app.ActionBar actionBar = appCompatActivity.getSupportActionBar();
            if (actionBar != null) {
                int surfaceColor = themeColors.getSurfaceColor();
                actionBar.setBackgroundDrawable(new ColorDrawable(surfaceColor));
            }
        }
    }
    
    /**
     * Apply surface background to toolbar
     */
    private void applyToolbarGradient(Toolbar toolbar) {
        if (toolbar == null) return;
        int surfaceColor = themeColors.getSurfaceColor();
        toolbar.setBackgroundColor(surfaceColor);
        int textColor = themeColors.getTextPrimaryColor();
        toolbar.setTitleTextColor(textColor);
        if (toolbar.getOverflowIcon() != null) {
            toolbar.getOverflowIcon().setTint(textColor);
        }
        android.util.Log.d(TAG, "Toolbar surface applied");
    }
    
    /**
     * Create theme gradient drawable
     */
    private GradientDrawable createThemeGradient() {
        int colorPrimary = themeColors.getPrimaryColor();
        int colorPrimaryVariant = themeColors.getPrimaryVariantColor();
        
        return new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{colorPrimary, colorPrimaryVariant}
        );
    }
    
    /**
     * Update action bar menu colors
     */
    public void updateActionBarMenu(androidx.appcompat.app.AppCompatActivity activity) {
        // This will be called when menu is created/prepared
        try {
            // We need to wait for the menu to be created
            // The actual menu update should be called from onPrepareOptionsMenu
            android.util.Log.d(TAG, "Action bar menu update prepared");
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error preparing menu update", e);
        }
    }
    
    /**
     * Update menu item colors - call from onPrepareOptionsMenu
     */
    public void updateMenuColors(Menu menu) {
        if (menu == null) return;
        
        try {
            // Use textColorPrimary for popup menu items (not textOnPrimaryColor)
            // This ensures black text on light theme and white text on dark theme
            int textColor = themeColors.getTextPrimaryColor();
            
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item != null && item.getTitle() != null) {
                    android.text.SpannableString spanString = 
                        new android.text.SpannableString(item.getTitle());
                    spanString.setSpan(
                        new android.text.style.ForegroundColorSpan(textColor), 
                        0, spanString.length(), 0
                    );
                    item.setTitle(spanString);
                    
                    // Update icon tint if present using the same color
                    if (item.getIcon() != null) {
                        item.getIcon().setTint(textColor);
                    }
                }
            }
            
            android.util.Log.d(TAG, "Menu colors updated with textColorPrimary: #" + Integer.toHexString(textColor & 0xFFFFFF));
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error updating menu colors", e);
        }
    }
    
    /**
     * Update all views in a view hierarchy recursively
     */
    public void updateViewHierarchy(View rootView) {
        if (rootView == null) return;
        
        // Skip the main content area and general containers to prevent unwanted theming
        int id = rootView.getId();
        String className = rootView.getClass().getSimpleName();
        
        // Skip android.R.id.content and other main container views
        if (id == android.R.id.content || 
            "ContentFrameLayout".equals(className) ||
            "DecorView".equals(className) ||
            "ActionBarOverlayLayout".equals(className)) {
            
            // For main containers, only traverse children but don't theme the container itself
            if (rootView instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) rootView;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    View child = viewGroup.getChildAt(i);
                    updateViewHierarchy(child);
                }
            }
            return;
        }
        
        // Update the view only if it's not a main container
        updateSingleView(rootView);
        
        // Recursively update child views
        if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                updateViewHierarchy(child);
            }
        }
    }
    
    /**
     * Update a single view based on its type
     */
    private void updateSingleView(View view) {
        if (view == null) return;
        
        try {
            // Only update specific view types that we know should be themed
            // Skip general container views to avoid unwanted background changes
            
            if (view instanceof ImageView) {
                updateImageView((ImageView) view);
            } else if (view instanceof TextView) {
                updateTextView((TextView) view);
            } else if (view instanceof SwipeRefreshLayout) {
                updateSwipeRefreshLayout((SwipeRefreshLayout) view);
            } else if (view instanceof RecyclerView) {
                updateRecyclerView((RecyclerView) view);
            } else if (view instanceof TabLayout) {
                updateTabLayout((TabLayout) view);
            } else if (view instanceof Toolbar) {
                applyToolbarGradient((Toolbar) view);
            }
            
            // Handle chat icon frame specifically
            int id = view.getId();
            if (id == R.id.chat_icon_frame) {
                updateChatIconBackground(view);
            }
            
            // Handle message containers specifically to apply theme colors
            if (id == R.id.message_container) {
                // Check if this is a user message container by looking at its parent layout
                android.view.ViewParent parent = view.getParent();
                if (parent instanceof android.view.View) {
                    android.view.View parentView = (android.view.View) parent;
                    // Check if this is in a user message layout
                    String resourceName = "";
                    try {
                        resourceName = context.getResources().getResourceEntryName(parentView.getId());
                    } catch (Exception e) {
                        // Resource name not available, ignore
                    }
                    
                    // If this appears to be a user message, apply theme colors
                    if (resourceName.contains("user") || 
                        parentView.getClass().getSimpleName().contains("UserMessage")) {
                        updateUserMessageBackground(view);
                    }
                }
            }
            
            // Only update backgrounds/tints for specific views, not all views
            
            // Only apply background/tint updates to specific UI elements
            if (view instanceof ImageView || 
                view instanceof TextView ||
                view instanceof androidx.appcompat.widget.AppCompatButton ||
                view instanceof com.google.android.material.button.MaterialButton ||
                view instanceof Toolbar ||
                id == R.id.toolbar ||
                id == R.id.nav_view ||
                id == R.id.drawer_header ||
                id == androidx.appcompat.R.id.action_bar) {
                
                updateViewBackground(view);
                updateViewTint(view);
            }
            
            // Don't apply theme changes to general container views like LinearLayout,
            // FrameLayout, etc. to prevent unwanted background color changes
            
        } catch (Exception e) {
            android.util.Log.w(TAG, "Error updating view: " + view.getClass().getSimpleName(), e);
        }
    }
    
    /**
     * Check if a view is inside a user message container to ensure proper contrast
     */
    private boolean isInsideUserMessage(View view) {
        if (view == null) return false;
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            View parentView = (View) parent;
            if ("user_message".equals(parentView.getTag()) || parentView.getId() == R.id.item_message_user_root) {
                return true;
            }
            if (parentView.getId() == R.id.message_container) {
                ViewGroup.LayoutParams lp = parentView.getLayoutParams();
                if (lp instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams clp = 
                        (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) lp;
                    if (clp.endToEnd == androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID) {
                        return true;
                    }
                }
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * Update ImageView tints and colors
     */
    private void updateImageView(ImageView imageView) {
        // Check if this ImageView should be tinted based on common IDs
        int id = imageView.getId();
        
        // Hamburger menu should always be white to show clearly on gradient toolbar
        if (id == R.id.btn_hamburger_menu) {
            ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(0xFFFFFFFF));
        }
        // Other common icon IDs that should be tinted with primary color
        else if (id == R.id.main_chat_icon ||
            id == R.id.btn_star_toggle ||
            id == R.id.star_indicator ||
            id == android.R.id.icon) {
            
            if (isInsideUserMessage(imageView)) {
                // Keep white/gold tint for user message icons on colored background
                ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(0xD9FFFFFF));
            } else {
                int tintColor = themeColors.getPrimaryColor();
                ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(tintColor));
            }
        }
        // Send button should use accent color background
        else if (id == R.id.btn_send) {
            updateSendButtonColors(imageView);
        }
        
        // Check if this ImageView is the chat history icon - look for parent with chat_icon_frame ID
        View parent = (View) imageView.getParent();
        if (parent != null && parent.getId() == R.id.chat_icon_frame) {
            updateChatIconBackground(parent);
        }
    }
    
    /**
     * Update download button colors with state lists
     */
    private void updateDownloadButtonColors(View button) {
        int colorPrimary = themeColors.getPrimaryColor();
        int colorPrimaryVariant = themeColors.getPrimaryVariantColor();
        
        // Create state list drawable
        StateListDrawable stateDrawable = new StateListDrawable();
        
        // Pressed state
        GradientDrawable pressedShape = new GradientDrawable();
        pressedShape.setColor(colorPrimaryVariant);
        pressedShape.setCornerRadius(8 * context.getResources().getDisplayMetrics().density);
        stateDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedShape);
        
        // Normal state
        GradientDrawable normalShape = new GradientDrawable();
        normalShape.setColor(colorPrimary);
        normalShape.setCornerRadius(8 * context.getResources().getDisplayMetrics().density);
        stateDrawable.addState(new int[]{}, normalShape);
        
        button.setBackground(stateDrawable);
    }
    
    /**
     * Update send button with accent color gradient
     */
    private void updateSendButtonColors(View sendButton) {
        try {
            int accentColor = themeColors.getAccentColor();
            int accentColorDark = themeColors.getAccentColorDark();
            
            // Create ripple effect with white overlay
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Create accent color gradient drawable
                GradientDrawable gradient = new GradientDrawable();
                gradient.setShape(GradientDrawable.OVAL);
                gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT);
                gradient.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                gradient.setColors(new int[]{accentColor, accentColorDark});
                
                // Create ripple drawable with white ripple effect
                android.graphics.drawable.RippleDrawable rippleDrawable = 
                    new android.graphics.drawable.RippleDrawable(
                        ColorStateList.valueOf(0x40FFFFFF), // White ripple overlay
                        gradient,
                        null
                    );
                
                sendButton.setBackground(rippleDrawable);
                
                // Ensure the icon is white for good contrast
                if (sendButton instanceof ImageView) {
                    ImageViewCompat.setImageTintList((ImageView) sendButton, 
                        ColorStateList.valueOf(0xFFFFFFFF));
                }
            } else {
                // Fallback for older Android versions
                GradientDrawable gradient = new GradientDrawable();
                gradient.setShape(GradientDrawable.OVAL);
                gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT);
                gradient.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                gradient.setColors(new int[]{accentColor, accentColorDark});
                
                sendButton.setBackground(gradient);
                
                if (sendButton instanceof ImageView) {
                    ImageViewCompat.setImageTintList((ImageView) sendButton, 
                        ColorStateList.valueOf(0xFFFFFFFF));
                }
            }
            
            android.util.Log.d(TAG, "Send button background updated with accent color: #" + 
                Integer.toHexString(accentColor & 0xFFFFFF));
                
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error updating send button background", e);
        }
    }
    
    /**
     * Update chat icon background with accent color gradient
     */
    private void updateChatIconBackground(View chatIconFrame) {
        try {
            int accentColor = themeColors.getAccentColor();
            int accentColorDark = themeColors.getAccentColorDark();
            
            // Create a circular gradient drawable with user's accent color
            GradientDrawable gradient = new GradientDrawable();
            gradient.setShape(GradientDrawable.OVAL);
            gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            gradient.setOrientation(GradientDrawable.Orientation.TL_BR); // Top-left to bottom-right (135 degree)
            gradient.setColors(new int[]{accentColor, accentColorDark});
            
            // Set size
            float density = context.getResources().getDisplayMetrics().density;
            int sizeInPx = (int) (48 * density);
            gradient.setSize(sizeInPx, sizeInPx);
            
            // Apply the gradient to the chat icon frame
            chatIconFrame.setBackground(gradient);
            
            android.util.Log.d(TAG, "Chat icon background updated with accent color: #" + 
                Integer.toHexString(accentColor & 0xFFFFFF));
                
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error updating chat icon background", e);
        }
    }
    
    /**
     * Update user message background with accent color gradient
     */
    private void updateUserMessageBackground(View messageContainer) {
        try {
            int accentColor = themeColors.getAccentColor();
            int accentColorDark = themeColors.getAccentColorDark();
            
            // Create a gradient drawable with user's accent color
            GradientDrawable gradient = new GradientDrawable();
            gradient.setShape(GradientDrawable.RECTANGLE);
            gradient.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            gradient.setOrientation(GradientDrawable.Orientation.TL_BR); // Top-left to bottom-right (135 degree)
            gradient.setColors(new int[]{accentColor, accentColorDark});
            
            // Set corner radius to match the original design
            float density = context.getResources().getDisplayMetrics().density;
            gradient.setCornerRadii(new float[]{
                18 * density, 18 * density,  // top-left
                18 * density, 18 * density,  // top-right
                18 * density, 18 * density,  // bottom-right
                4 * density, 4 * density     // bottom-left (like WhatsApp style)
            });
            
            // Create shadow drawable
            GradientDrawable shadow = new GradientDrawable();
            shadow.setShape(GradientDrawable.RECTANGLE);
            shadow.setColor(0x15000000); // Semi-transparent shadow
            shadow.setCornerRadius(18 * density);
            
            // Create the layer list to include shadow effect
            android.graphics.drawable.LayerDrawable layerDrawable = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                shadow,
                gradient
            });
            
            // Apply shadow positioning
            layerDrawable.setLayerInset(0, 0, 0, (int)(1 * density), (int)(1 * density)); // shadow
            layerDrawable.setLayerInset(1, (int)(1 * density), (int)(1 * density), 0, 0); // main bubble
            
            // Apply the themed background
            messageContainer.setBackground(layerDrawable);
            
            android.util.Log.d(TAG, "User message background updated with accent color: #" + 
                Integer.toHexString(accentColor & 0xFFFFFF));
                
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error updating user message background", e);
        }
    }
    
    /**
     * Update TextView colors
     */
    private void updateTextView(TextView textView) {
        // Update text color based on context and theme
        // This is handled by the theme system, but we can apply specific updates here
        
        // Check for specific TextViews that need custom colors
        int id = textView.getId();
        
        if (id == R.id.text_timestamp || id == R.id.text_token_stats) {
            if (isInsideUserMessage(textView)) {
                // High-contrast 80% white for timestamp on accent bubble
                textView.setTextColor(0xCCFFFFFF);
            } else {
                // These should use secondary text color with alpha
                int textColor = themeColors.getTextSecondaryColor();
                textView.setTextColor(textColor);
            }
        }
    }
    
    /**
     * Update SwipeRefreshLayout colors
     */
    private void updateSwipeRefreshLayout(SwipeRefreshLayout swipeRefresh) {
        int primaryColor = themeColors.getPrimaryColor();
        int secondaryColor = themeColors.getSecondaryColor();
        int accentColor = themeColors.getAccentColor();
        
        swipeRefresh.setColorSchemeColors(primaryColor, secondaryColor, accentColor);
        swipeRefresh.setProgressBackgroundColorSchemeColor(themeColors.getSurfaceColor());
    }
    
    /**
     * Update RecyclerView
     */
    private void updateRecyclerView(RecyclerView recyclerView) {
        // Notify adapter of theme change if it supports it
        if (recyclerView.getAdapter() instanceof ThemeAware) {
            ((ThemeAware) recyclerView.getAdapter()).onThemeChanged();
        } else if (recyclerView.getAdapter() instanceof com.keralatechreach.mobigpt.adapter.MessageAdapter) {
            // Special handling for MessageAdapter even if it doesn't implement ThemeAware
            com.keralatechreach.mobigpt.adapter.MessageAdapter messageAdapter = 
                (com.keralatechreach.mobigpt.adapter.MessageAdapter) recyclerView.getAdapter();
            messageAdapter.onThemeChanged();
        } else {
            // Force refresh to pick up theme changes
            if (recyclerView.getAdapter() != null) {
                recyclerView.getAdapter().notifyDataSetChanged();
            }
        }
    }
    
    /**
     * Update TabLayout colors
     */
    private void updateTabLayout(TabLayout tabLayout) {
        int primaryColor = themeColors.getPrimaryColor();
        int textSecondaryColor = themeColors.getTextSecondaryColor();
        
        tabLayout.setSelectedTabIndicatorColor(primaryColor);
        tabLayout.setTabTextColors(textSecondaryColor, primaryColor);
    }
    
    /**
     * Update view background if it's theme-related
     */
    private void updateViewBackground(View view) {
        // Only update specific views that we know should have themed backgrounds
        // Don't modify general container backgrounds to avoid turning everything dark
        
        int id = view.getId();
        
        // Only update backgrounds for specific theme-related views
        if (id == R.id.toolbar || 
            id == androidx.appcompat.R.id.action_bar ||
            id == R.id.nav_view ||
            id == R.id.drawer_header ||
            view instanceof Toolbar) {
            
            // Update these specific components
            if (view.getBackground() instanceof GradientDrawable) {
                // This is a theme-related gradient, update it
                GradientDrawable drawable = (GradientDrawable) view.getBackground();
                // Apply theme colors to the gradient if needed
            } else if (view.getBackground() instanceof ColorDrawable) {
                // Check if this is a theme color that should be updated
                ColorDrawable colorDrawable = (ColorDrawable) view.getBackground();
                // Update color if it matches theme colors
            }
        }
        
        // Don't modify backgrounds of general containers like LinearLayout, FrameLayout, etc.
        // to prevent turning the entire screen dark
    }
    
    /**
     * Update view tint
     */
    private void updateViewTint(View view) {
        // Only apply tints to specific views that should be tinted
        // Don't apply background tints to general containers
        
        int id = view.getId();
        
        // Skip send button, hamburger menu, and download action buttons as they have special handling/distinct drawables
        if (id == R.id.btn_send || id == R.id.btn_hamburger_menu || 
            id == R.id.btn_pause_resume_download || id == R.id.btn_cancel_download) {
            return;
        }
        
        // Only apply tints to specific UI elements that need theming
        if (view instanceof ImageView ||
            view instanceof androidx.appcompat.widget.AppCompatButton ||
            id == R.id.btn_new_chat ||
            id == R.id.action_settings ||
            id == R.id.fab_scroll_to_bottom) {
            
            // Apply tint to specific interactive elements only
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorControlNormal, typedValue, true);
                if (typedValue.resourceId != 0) {
                    int tintColor = context.getColor(typedValue.resourceId);
                    
                    // Only apply to ImageViews and buttons, not to general containers
                    if (view instanceof ImageView || 
                        view instanceof androidx.appcompat.widget.AppCompatButton) {
                        view.setBackgroundTintList(ColorStateList.valueOf(tintColor));
                    }
                }
            }
        }
        
        // Don't apply background tints to general layout containers
    }
    
    /**
     * Interface for adapters that can respond to theme changes
     */
    public interface ThemeAware {
        void onThemeChanged();
    }
    
    /**
     * Helper class to get theme colors
     */
    private static class ThemeColors {
        private final Context context;
        
        public ThemeColors(Context context) {
            this.context = context;
        }
        
        public int getPrimaryColor() {
            return getThemeColor(com.google.android.material.R.attr.colorPrimary);
        }
        
        public int getPrimaryVariantColor() {
            return getThemeColor(com.google.android.material.R.attr.colorPrimaryVariant);
        }
        
        public int getSecondaryColor() {
            return getThemeColor(com.google.android.material.R.attr.colorSecondary);
        }
        
        public int getAccentColor() {
            // Get the actual accent color selected by user, not just primary color
            try {
                SettingsManager settingsManager = new SettingsManager(context);
                String accentColor = settingsManager.getAccentColor();
                
                switch (accentColor) {
                    case "blue":
                        return context.getColor(R.color.accent_blue);
                    case "purple":
                        return context.getColor(R.color.accent_purple);
                    case "orange":
                        return context.getColor(R.color.accent_orange_primary);
                    case "pink":
                        return context.getColor(R.color.accent_pink_primary);
                    case "green":
                    default:
                        return context.getColor(R.color.accent_green);
                }
            } catch (Exception e) {
                // Fallback to primary if something goes wrong
                return getThemeColor(com.google.android.material.R.attr.colorPrimary);
            }
        }
        
        public int getAccentColorDark() {
            // Get the dark variant of the accent color selected by user
            try {
                SettingsManager settingsManager = new SettingsManager(context);
                String accentColor = settingsManager.getAccentColor();
                
                switch (accentColor) {
                    case "blue":
                        return context.getColor(R.color.accent_blue_dark);
                    case "purple":
                        return context.getColor(R.color.accent_purple_dark);
                    case "orange":
                        return context.getColor(R.color.accent_orange_dark);
                    case "pink":
                        return context.getColor(R.color.accent_pink_dark);
                    case "green":
                    default:
                        return context.getColor(R.color.accent_green_dark);
                }
            } catch (Exception e) {
                // Fallback to primary variant if something goes wrong
                return getThemeColor(com.google.android.material.R.attr.colorPrimaryVariant);
            }
        }
        
        public int getSurfaceColor() {
            return getThemeColor(com.google.android.material.R.attr.colorSurface);
        }
        
        public int getBackgroundColor() {
            return getThemeColor(android.R.attr.colorBackground);
        }
        
        public int getTextPrimaryColor() {
            return getThemeColor(android.R.attr.textColorPrimary);
        }
        
        public int getTextSecondaryColor() {
            return getThemeColor(android.R.attr.textColorSecondary);
        }
        
        public int getTextOnPrimaryColor() {
            return getThemeColor(com.google.android.material.R.attr.colorOnPrimary);
        }
        
        private int getThemeColor(int attr) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
    }
}