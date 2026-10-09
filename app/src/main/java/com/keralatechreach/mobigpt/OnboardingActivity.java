package com.keralatechreach.mobigpt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;
import com.keralatechreach.mobigpt.adapter.OnboardingPagerAdapter;
import com.keralatechreach.mobigpt.security.SecurePreferences;
import com.keralatechreach.mobigpt.widget.PageIndicator;

/**
 * Onboarding Activity to guide first-time users through the app features.
 * Shows tutorial screens explaining:
 * - How to download models
 * - Model selection and performance expectations
 * - Chat interface basics
 * Uses encrypted SharedPreferences for secure storage
 */
public class OnboardingActivity extends AppCompatActivity {
    
    private static final String TAG = "OnboardingActivity";
    private static final String PREF_NAME = "MobiGPT_Prefs";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String KEY_CURRENT_PAGE = "current_page";
    
    private ViewPager2 viewPager;
    private PageIndicator pageIndicator;
    private Button btnNext;
    private Button btnSkip;
    private Button btnGetStarted;
    private ImageButton btnBack;
    
    private OnboardingPagerAdapter pagerAdapter;
    private int currentPage = 0;
    private SecurePreferences securePreferences;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Enable edge-to-edge display
        EdgeToEdge.enable(this);

        // Apply theme before calling super.onCreate
        com.keralatechreach.mobigpt.utils.ThemeManager themeManager = 
            new com.keralatechreach.mobigpt.utils.ThemeManager(this);
        themeManager.applyTheme();
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        // Restore saved page position across orientation changes
        if (savedInstanceState != null) {
            currentPage = savedInstanceState.getInt(KEY_CURRENT_PAGE, 0);
        }

        setupEdgeToEdgeInsets();
        setupBackNavigation();
        
        // Apply all theme colors using unified updater
        applyAllThemeColors();
        
        // Initialize encrypted preferences with migration
        securePreferences = SecurePreferences.createWithMigration(this, PREF_NAME);
        
        initViews();
        setupViewPager();
        setupListeners();
        
        // Apply button colors after everything is set up
        applyOnboardingButtonColors();
    }

    private void setupEdgeToEdgeInsets() {
        View rootView = findViewById(R.id.onboarding_root);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                );
                v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return windowInsets;
            });
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentPage > 0) {
                    viewPager.setCurrentItem(currentPage - 1, true);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CURRENT_PAGE, currentPage);
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
     * Initialize all views
     */
    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        pageIndicator = findViewById(R.id.pageIndicator);
        btnNext = findViewById(R.id.btn_next);
        btnSkip = findViewById(R.id.btn_skip);
        btnGetStarted = findViewById(R.id.btn_get_started);
        btnBack = findViewById(R.id.btn_back);
        
        // Ensure onboarding buttons have green background and white text
        applyOnboardingButtonColors();
    }
    
    /**
     * Apply green background and white text to onboarding buttons
     */
    private void applyOnboardingButtonColors() {
        // Get the primary color (green) from the theme
        int primaryColor = getThemeColor(android.R.attr.colorPrimary);
        int whiteColor = getResources().getColor(android.R.color.white, getTheme());
        
        // Apply colors to all buttons
        if (btnNext != null) {
            btnNext.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
            btnNext.setTextColor(whiteColor);
        }
        
        if (btnSkip != null) {
            btnSkip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
            btnSkip.setTextColor(whiteColor);
        }
        
        if (btnGetStarted != null) {
            btnGetStarted.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primaryColor));
            btnGetStarted.setTextColor(whiteColor);
        }
    }
    
    /**
     * Helper method to get theme color
     */
    private int getThemeColor(int colorAttr) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(colorAttr, typedValue, true);
        return typedValue.data;
    }
    
    /**
     * Setup the ViewPager with onboarding screens
     */
    private void setupViewPager() {
        pagerAdapter = new OnboardingPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        
        // Set up the page indicator
        pageIndicator.setDotCount(pagerAdapter.getItemCount());
        pageIndicator.setCurrentPosition(currentPage);
        viewPager.setCurrentItem(currentPage, false);
        
        // Get primary color from theme for indicator
        int primaryColor = getThemeColor(android.R.attr.colorPrimary);
        int inactiveColor = 0x4D000000; // Semi-transparent black
        pageIndicator.setColors(primaryColor, inactiveColor);
        
        // Add smooth page transformer for professional transitions
        viewPager.setPageTransformer(new ViewPager2.PageTransformer() {
            @Override
            public void transformPage(android.view.View page, float position) {
                // Apply fade and scale transformation
                if (position < -1 || position > 1) {
                    page.setAlpha(0f);
                } else {
                    // Fade effect
                    page.setAlpha(1f - Math.abs(position) * 0.3f);
                    
                    // Subtle scale effect
                    float scaleFactor = Math.max(0.85f, 1f - Math.abs(position) * 0.15f);
                    page.setScaleX(scaleFactor);
                    page.setScaleY(scaleFactor);
                }
            }
        });
        
        // Listen to page changes
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPage = position;
                pageIndicator.setCurrentPosition(position);
                updateNavigationButtons();
            }
        });
        
        updateNavigationButtons();
    }
    
    /**
     * Setup button click listeners
     */
    private void setupListeners() {
        btnNext.setOnClickListener(v -> {
            if (currentPage < pagerAdapter.getItemCount() - 1) {
                viewPager.setCurrentItem(currentPage + 1, true);
            }
        });
        
        btnBack.setOnClickListener(v -> {
            if (currentPage > 0) {
                viewPager.setCurrentItem(currentPage - 1, true);
            }
        });
        
        btnSkip.setOnClickListener(v -> finishOnboarding());
        
        btnGetStarted.setOnClickListener(v -> finishOnboarding());
    }
    
    /**
     * Update navigation button visibility based on current page
     */
    private void updateNavigationButtons() {
        int lastPage = pagerAdapter.getItemCount() - 1;
        
        // Show/hide back button
        btnBack.setVisibility(currentPage > 0 ? View.VISIBLE : View.INVISIBLE);
        
        // Show/hide next button and skip button
        if (currentPage == lastPage) {
            btnNext.setVisibility(View.GONE);
            btnSkip.setVisibility(View.GONE);
            btnGetStarted.setVisibility(View.VISIBLE);
        } else {
            btnNext.setVisibility(View.VISIBLE);
            btnSkip.setVisibility(View.VISIBLE);
            btnGetStarted.setVisibility(View.GONE);
        }
    }
    
    /**
     * Complete onboarding and navigate to MainActivity
     */
    private void finishOnboarding() {
        // Mark onboarding as completed using encrypted preferences
        securePreferences.putBoolean(KEY_ONBOARDING_COMPLETED, true);
        
        // Navigate to MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    /**
     * Check if onboarding has been completed
     */
    public static boolean isOnboardingCompleted(android.content.Context context) {
        SecurePreferences securePrefs = SecurePreferences.createWithMigration(context, PREF_NAME);
        return securePrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }
    
    /**
     * Reset onboarding status (useful for testing or settings)
     */
    public static void resetOnboarding(android.content.Context context) {
        SecurePreferences securePrefs = SecurePreferences.createWithMigration(context, PREF_NAME);
        securePrefs.putBoolean(KEY_ONBOARDING_COMPLETED, false);
    }
}
