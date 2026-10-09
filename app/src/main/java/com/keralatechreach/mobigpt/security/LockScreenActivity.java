package com.keralatechreach.mobigpt.security;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.keralatechreach.mobigpt.R;

/**
 * Lock screen for app authentication
 * Supports both biometric and PIN authentication
 * Note: Does not extend BaseActivity to avoid circular lock enforcement
 */
public class LockScreenActivity extends AppCompatActivity {
    
    private static final String TAG = "LockScreenActivity";
    
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_UNLOCK = "unlock";
    public static final String MODE_SETUP_PIN = "setup_pin";
    public static final String MODE_CHANGE_PIN = "change_pin";
    
    private SecurityManager securityManager;
    private String mode = MODE_UNLOCK;
    
    // UI Components
    private TextView titleText;
    private TextView subtitleText;
    private TextInputLayout pinInputLayout;
    private TextInputEditText pinInput;
    private Button unlockButton;
    private Button useBiometricButton;
    private ImageView lockIcon;
    
    private final Handler lockoutHandler = new Handler(Looper.getMainLooper());
    private Runnable lockoutRunnable;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        
        // Apply theme before setting content view
        com.keralatechreach.mobigpt.utils.ThemeManager themeManager = 
            new com.keralatechreach.mobigpt.utils.ThemeManager(this);
        themeManager.applyTheme();
        
        // Prevent screenshots on lock screen
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );
        
        setContentView(R.layout.activity_lock_screen);
        setupEdgeToEdgeInsets();
        
        // Apply all theme colors using unified updater
        applyAllThemeColors();
        
        securityManager = SecurityManager.getInstance(this);
        
        // Get mode from intent
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_MODE)) {
            mode = intent.getStringExtra(EXTRA_MODE);
        }
        
        initializeViews();
        setupUI();
        
        // Auto-show biometric if in unlock mode, biometric is primary, and not locked out
        if (MODE_UNLOCK.equals(mode) && shouldUseBiometric() && !securityManager.isLockedOut()) {
            new Handler(Looper.getMainLooper()).postDelayed(this::showBiometricPrompt, 500);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (MODE_UNLOCK.equals(mode)) {
            checkAndEnforceLockout();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lockoutRunnable != null) {
            lockoutHandler.removeCallbacks(lockoutRunnable);
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
     * Lock screen should apply limited theming to maintain security focus
     * Note: LockScreenActivity doesn't extend BaseActivity, so this is just a placeholder
     */
    protected boolean shouldApplyThemeColors() {
        return true; // Apply theme colors but let the unified updater handle it appropriately
    }
    
    private void setupEdgeToEdgeInsets() {
        View rootView = findViewById(R.id.lock_screen_root);
        if (rootView != null) {
            int initialPaddingStart = rootView.getPaddingStart();
            int initialPaddingTop = rootView.getPaddingTop();
            int initialPaddingEnd = rootView.getPaddingEnd();
            int initialPaddingBottom = rootView.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                );
                v.setPaddingRelative(
                    initialPaddingStart + insets.left,
                    initialPaddingTop + insets.top,
                    initialPaddingEnd + insets.right,
                    initialPaddingBottom + insets.bottom
                );
                return windowInsets;
            });
        }
    }
    
    private void initializeViews() {
        titleText = findViewById(R.id.lock_title);
        subtitleText = findViewById(R.id.lock_subtitle);
        pinInputLayout = findViewById(R.id.pin_input_layout);
        pinInput = findViewById(R.id.pin_input);
        unlockButton = findViewById(R.id.unlock_button);
        useBiometricButton = findViewById(R.id.use_biometric_button);
        lockIcon = findViewById(R.id.lock_icon);
    }
    
    private void setupUI() {
        switch (mode) {
            case MODE_SETUP_PIN:
                setupPinSetupMode();
                break;
            case MODE_CHANGE_PIN:
                setupChangePinMode();
                break;
            case MODE_UNLOCK:
            default:
                setupUnlockMode();
                break;
        }
        
        unlockButton.setOnClickListener(v -> handleUnlockClick());
        useBiometricButton.setOnClickListener(v -> showBiometricPrompt());
    }
    
    private void setupUnlockMode() {
        titleText.setText("Welcome Back");
        subtitleText.setText("Enter your PIN to unlock");
        unlockButton.setText("Unlock");
        
        // Show biometric button if available
        if (shouldUseBiometric()) {
            useBiometricButton.setVisibility(View.VISIBLE);
        } else {
            useBiometricButton.setVisibility(View.GONE);
        }
    }
    
    private void setupPinSetupMode() {
        titleText.setText("Set Up PIN");
        subtitleText.setText("Create a 4-6 digit PIN");
        unlockButton.setText("Set PIN");
        useBiometricButton.setVisibility(View.GONE);
    }
    
    private void setupChangePinMode() {
        titleText.setText("Change PIN");
        subtitleText.setText("Enter your current PIN");
        unlockButton.setText("Verify");
        useBiometricButton.setVisibility(View.GONE);
    }
    
    private void handleUnlockClick() {
        String pin = pinInput.getText() != null ? pinInput.getText().toString().trim() : "";
        
        if (pin.isEmpty()) {
            pinInputLayout.setError("Please enter PIN");
            return;
        }
        
        switch (mode) {
            case MODE_SETUP_PIN:
                handlePinSetup(pin);
                break;
            case MODE_CHANGE_PIN:
                handlePinChange(pin);
                break;
            case MODE_UNLOCK:
            default:
                handleUnlock(pin);
                break;
        }
    }
    
    private void handleUnlock(String pin) {
        if (!securityManager.isPinSet()) {
            Toast.makeText(this, "PIN not set. Please set up security first.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        if (securityManager.isLockedOut()) {
            checkAndEnforceLockout();
            return;
        }
        
        if (securityManager.verifyPin(pin)) {
            // Authentication successful
            securityManager.setAuthenticated(true);
            securityManager.resetFailedAttempts();
            Toast.makeText(this, "Unlocked successfully", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else {
            // Authentication failed
            int failed = securityManager.recordFailedAttempt();
            pinInput.setText("");
            
            if (securityManager.isLockedOut()) {
                checkAndEnforceLockout();
            } else {
                int remainingAttempts = SecurityManager.MAX_FAILED_ATTEMPTS - failed;
                pinInputLayout.setError("Incorrect PIN");
                Toast.makeText(this, 
                    "Incorrect PIN. " + remainingAttempts + " attempts remaining", 
                    Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void handlePinSetup(String pin) {
        if (pin.length() < 4) {
            pinInputLayout.setError("PIN must be at least 4 digits");
            return;
        }
        
        if (pin.length() > 6) {
            pinInputLayout.setError("PIN must be at most 6 digits");
            return;
        }
        
        if (!pin.matches("\\d+")) {
            pinInputLayout.setError("PIN must contain only numbers");
            return;
        }
        
        // Ask for confirmation
        confirmPin(pin);
    }
    
    private void confirmPin(String pin) {
        titleText.setText("Confirm PIN");
        subtitleText.setText("Re-enter your PIN to confirm");
        pinInput.setText("");
        pinInputLayout.setError(null);
        
        unlockButton.setOnClickListener(v -> {
            String confirmPin = pinInput.getText() != null ? pinInput.getText().toString().trim() : "";
            
            if (confirmPin.equals(pin)) {
                if (securityManager.setPin(pin)) {
                    Toast.makeText(this, "PIN set successfully", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(this, "Failed to set PIN. Please try again.", Toast.LENGTH_LONG).show();
                }
            } else {
                pinInputLayout.setError("PINs do not match");
                pinInput.setText("");
            }
        });
    }
    
    private void handlePinChange(String currentPin) {
        if (securityManager.verifyPin(currentPin)) {
            // Current PIN verified, now set up new PIN
            titleText.setText("New PIN");
            subtitleText.setText("Enter your new PIN");
            pinInput.setText("");
            pinInputLayout.setError(null);
            mode = MODE_SETUP_PIN;
            unlockButton.setOnClickListener(v -> handleUnlockClick());
        } else {
            pinInputLayout.setError("Incorrect current PIN");
            pinInput.setText("");
        }
    }
    
    private void checkAndEnforceLockout() {
        if (!MODE_UNLOCK.equals(mode)) {
            return;
        }
        
        if (securityManager.isLockedOut()) {
            int remaining = securityManager.getRemainingLockoutSeconds();
            pinInput.setEnabled(false);
            unlockButton.setEnabled(false);
            useBiometricButton.setEnabled(false);
            pinInputLayout.setError("Too many failed attempts. Try again in " + remaining + "s");
            
            if (lockoutRunnable != null) {
                lockoutHandler.removeCallbacks(lockoutRunnable);
            }
            
            lockoutRunnable = new Runnable() {
                @Override
                public void run() {
                    if (securityManager.isLockedOut()) {
                        int rem = securityManager.getRemainingLockoutSeconds();
                        pinInputLayout.setError("Too many failed attempts. Try again in " + rem + "s");
                        lockoutHandler.postDelayed(this, 1000);
                    } else {
                        pinInput.setEnabled(true);
                        unlockButton.setEnabled(true);
                        if (shouldUseBiometric()) {
                            useBiometricButton.setVisibility(View.VISIBLE);
                            useBiometricButton.setEnabled(true);
                        }
                        pinInputLayout.setError(null);
                        Toast.makeText(LockScreenActivity.this, "You can try again now", Toast.LENGTH_SHORT).show();
                    }
                }
            };
            lockoutHandler.postDelayed(lockoutRunnable, 1000);
        } else {
            pinInput.setEnabled(true);
            unlockButton.setEnabled(true);
            if (shouldUseBiometric()) {
                useBiometricButton.setVisibility(View.VISIBLE);
                useBiometricButton.setEnabled(true);
            }
            pinInputLayout.setError(null);
        }
    }
    
    private boolean shouldUseBiometric() {
        String lockType = securityManager.getLockType();
        return "biometric".equals(lockType) && securityManager.isBiometricAvailable(this);
    }
    
    private void showBiometricPrompt() {
        if (securityManager.isLockedOut()) {
            Toast.makeText(this, "Too many failed attempts. Please wait.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!securityManager.isBiometricAvailable(this)) {
            Toast.makeText(this, "Biometric authentication not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        securityManager.showBiometricPrompt(this, new SecurityManager.BiometricAuthCallback() {
            @Override
            public void onAuthenticationSucceeded() {
                runOnUiThread(() -> {
                    securityManager.setAuthenticated(true);
                    securityManager.resetFailedAttempts();
                    Toast.makeText(LockScreenActivity.this, "Authentication successful", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }
            
            @Override
            public void onAuthenticationFailed() {
                runOnUiThread(() -> {
                    Toast.makeText(LockScreenActivity.this, "Authentication failed", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onAuthenticationError(int errorCode, String errString) {
                runOnUiThread(() -> {
                    // If user cancels biometric, they can use PIN instead
                    if (errorCode == androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED) {
                        Toast.makeText(LockScreenActivity.this, "Use PIN to unlock", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(LockScreenActivity.this, "Error: " + errString, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    @Override
    public void onBackPressed() {
        // Prevent back button from bypassing lock screen in unlock mode
        if (MODE_UNLOCK.equals(mode)) {
            // Move app to background instead of finishing
            moveTaskToBack(true);
        } else {
            super.onBackPressed();
        }
    }
}
