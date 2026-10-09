package com.keralatechreach.mobigpt.security;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.Executor;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Central security manager for app-wide security features
 * Handles app lock, authentication, timeout, and security state
 */
public class SecurityManager implements Application.ActivityLifecycleCallbacks {
    
    private static final String TAG = "SecurityManager";
    
    // Secure preferences keys
    private static final String PREF_PIN_HASH = "pin_hash";
    private static final String PREF_PIN_SALT = "pin_salt";
    private static final String PREF_LAST_AUTH_TIME = "last_auth_time";
    private static final String PREF_IS_LOCKED = "is_locked";
    private static final String PREF_LOCK_SETUP_COMPLETE = "lock_setup_complete";
    private static final String PREF_FAILED_ATTEMPTS = "auth_failed_attempts";
    private static final String PREF_LOCKOUT_TIME = "auth_lockout_time";
    
    // PBKDF2 key derivation parameters
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 10000;
    private static final int PBKDF2_KEY_LENGTH = 256; // bits
    private static final String PBKDF2_PREFIX = "pbkdf2$";
    
    // Rate limiting & lockout policy
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCKOUT_DURATION_MS = 30000L; // 30 seconds
    
    private static SecurityManager instance;
    private final Context context;
    private final SecurePreferences securePrefs;
    private final SharedPreferences appPrefs;
    
    private boolean isAuthenticated = false;
    private long lastInteractionTime = 0;
    private boolean isAppInBackground = false;
    private int activeActivities = 0;
    
    private SecurityManager(Context context) throws SecurityException {
        this.context = context.getApplicationContext();
        try {
            this.securePrefs = new SecurePreferences(this.context, "security_prefs");
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to initialize secure preferences", e);
            throw e;
        }
        this.appPrefs = PreferenceManager.getDefaultSharedPreferences(this.context);
    }
    
    public static synchronized SecurityManager getInstance(Context context) {
        if (instance == null) {
            try {
                instance = new SecurityManager(context);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to create SecurityManager instance", e);
                return null;
            }
        }
        return instance;
    }
    
    /**
     * Initialize security manager and register lifecycle callbacks
     */
    public void initialize(Application application) {
        application.registerActivityLifecycleCallbacks(this);
        updateLastInteractionTime();
        Log.d(TAG, "SecurityManager initialized");
    }
    
    // ==================== App Lock State ====================
    
    /**
     * Check if app lock is enabled in settings
     */
    public boolean isAppLockEnabled() {
        return appPrefs.getBoolean("app_lock_enabled", false);
    }
    
    /**
     * Check if lock has been set up (PIN or biometric configured)
     */
    public boolean isLockSetupComplete() {
        if (securePrefs == null) {
            Log.w(TAG, "SecurePreferences not available, returning false for lock setup");
            return false;
        }
        return securePrefs.getBoolean(PREF_LOCK_SETUP_COMPLETE, false);
    }
    
    /**
     * Mark lock setup as complete
     */
    public void setLockSetupComplete(boolean complete) {
        if (securePrefs == null) {
            Log.w(TAG, "SecurePreferences not available, cannot set lock setup complete");
            return;
        }
        securePrefs.putBoolean(PREF_LOCK_SETUP_COMPLETE, complete);
    }
    
    /**
     * Check if app should be locked
     */
    public boolean shouldLockApp() {
        if (!isAppLockEnabled() || !isLockSetupComplete()) {
            return false;
        }
        
        // If already authenticated and within timeout, don't lock
        if (isAuthenticated && !isLockTimeoutExceeded()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if the lock timeout has been exceeded
     */
    public boolean isLockTimeoutExceeded() {
        if (!isAppLockEnabled()) {
            return false;
        }
        
        int timeoutSeconds = getLockTimeout();
        
        // Special case: 0 means lock immediately (always exceeded)
        if (timeoutSeconds == 0) {
            return true;
        }
        
        // Special case: very large value means never timeout
        if (timeoutSeconds >= Integer.MAX_VALUE - 1000) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        long lastAuthTime = securePrefs != null ? securePrefs.getLong(PREF_LAST_AUTH_TIME, 0) : 0;
        
        // If never authenticated before, timeout is exceeded
        if (lastAuthTime == 0) {
            return true;
        }
        
        long elapsedSeconds = (currentTime - lastAuthTime) / 1000;
        
        return elapsedSeconds > timeoutSeconds;
    }
    
    /**
     * Get lock timeout in seconds from settings
     */
    public int getLockTimeout() {
        String timeoutStr = appPrefs.getString("lock_timeout", "300");
        try {
            return Integer.parseInt(timeoutStr);
        } catch (NumberFormatException e) {
            return 300; // Default 5 minutes
        }
    }
    
    /**
     * Check if lock on app switch is enabled
     */
    public boolean isLockOnAppSwitchEnabled() {
        return appPrefs.getBoolean("lock_on_app_switch", true);
    }
    
    /**
     * Get configured lock type (pin or biometric)
     */
    public String getLockType() {
        return appPrefs.getString("lock_type", "biometric");
    }
    
    // ==================== Authentication ====================
    
    /**
     * Mark the app as authenticated
     */
    public void setAuthenticated(boolean authenticated) {
        this.isAuthenticated = authenticated;
        if (securePrefs != null) {
            if (authenticated) {
                securePrefs.putLong(PREF_LAST_AUTH_TIME, System.currentTimeMillis());
                securePrefs.putBoolean(PREF_IS_LOCKED, false);
            } else {
                securePrefs.putBoolean(PREF_IS_LOCKED, true);
            }
        }
        updateLastInteractionTime();
    }
    
    /**
     * Check if currently authenticated
     */
    public boolean isAuthenticated() {
        return isAuthenticated && !isLockTimeoutExceeded();
    }
    
    /**
     * Lock the app immediately
     */
    public void lockApp() {
        isAuthenticated = false;
        if (securePrefs != null) {
            securePrefs.putBoolean(PREF_IS_LOCKED, true);
        }
        Log.d(TAG, "App locked");
    }
    
    /**
     * Update last interaction time (for timeout tracking)
     */
    public void updateLastInteractionTime() {
        lastInteractionTime = System.currentTimeMillis();
        if (isAuthenticated && securePrefs != null) {
            securePrefs.putLong(PREF_LAST_AUTH_TIME, lastInteractionTime);
        }
    }
    
    // ==================== PIN Management ====================
    
    /**
     * Check if PIN is set
     */
    public boolean isPinSet() {
        if (securePrefs == null) {
            Log.w(TAG, "SecurePreferences not available, returning false for PIN check");
            return false;
        }
        return securePrefs.contains(PREF_PIN_HASH);
    }
    
    /**
     * Set PIN (hashed and salted using PBKDF2WithHmacSHA256)
     */
    public boolean setPin(String pin) {
        if (securePrefs == null) {
            Log.w(TAG, "SecurePreferences not available, cannot set PIN");
            return false;
        }
        
        try {
            String salt = generateSalt();
            String hash = hashPinPBKDF2(pin, salt);
            
            securePrefs.putString(PREF_PIN_HASH, hash);
            securePrefs.putString(PREF_PIN_SALT, salt);
            resetFailedAttempts();
            setLockSetupComplete(true);
            
            Log.d(TAG, "PIN set successfully using PBKDF2");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error setting PIN", e);
            return false;
        }
    }
    
    /**
     * Verify PIN against stored hash.
     * Supports PBKDF2 and provides transparent backward-compatible migration
     * for any legacy SHA-256 hashes.
     */
    public boolean verifyPin(String pin) {
        if (securePrefs == null) {
            Log.w(TAG, "SecurePreferences not available, cannot verify PIN");
            return false;
        }
        
        try {
            String storedHash = securePrefs.getString(PREF_PIN_HASH, null);
            String salt = securePrefs.getString(PREF_PIN_SALT, null);
            
            if (storedHash == null || salt == null) {
                return false;
            }
            
            if (storedHash.startsWith(PBKDF2_PREFIX)) {
                // Modern PBKDF2 verification with constant-time equality
                String inputHash = hashPinPBKDF2(pin, salt);
                return MessageDigest.isEqual(
                    storedHash.getBytes(StandardCharsets.UTF_8),
                    inputHash.getBytes(StandardCharsets.UTF_8)
                );
            } else {
                // Legacy SHA-256 fallback verification
                String legacyInputHash = legacyHashPin(pin, salt);
                boolean matches = MessageDigest.isEqual(
                    storedHash.getBytes(StandardCharsets.UTF_8),
                    legacyInputHash.getBytes(StandardCharsets.UTF_8)
                );
                
                if (matches) {
                    // Transparently upgrade legacy hash to PBKDF2 on successful verification
                    try {
                        String newSalt = generateSalt();
                        String newHash = hashPinPBKDF2(pin, newSalt);
                        securePrefs.putString(PREF_PIN_HASH, newHash);
                        securePrefs.putString(PREF_PIN_SALT, newSalt);
                        Log.d(TAG, "Transparently upgraded legacy PIN hash to PBKDF2");
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to upgrade legacy PIN hash", e);
                    }
                }
                return matches;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error verifying PIN", e);
            return false;
        }
    }
    
    /**
     * Clear PIN and reset lockout states
     */
    public void clearPin() {
        if (securePrefs == null) {
            Log.w(TAG, "SecurePreferences not available, cannot clear PIN");
            return;
        }
        
        securePrefs.remove(PREF_PIN_HASH);
        securePrefs.remove(PREF_PIN_SALT);
        resetFailedAttempts();
        setLockSetupComplete(false);
        Log.d(TAG, "PIN cleared");
    }
    
    /**
     * Hash PIN using PBKDF2WithHmacSHA256
     */
    private String hashPinPBKDF2(String pin, String salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
            pin.toCharArray(),
            salt.getBytes(StandardCharsets.UTF_8),
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH
        );
        SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        byte[] hash = skf.generateSecret(spec).getEncoded();
        return PBKDF2_PREFIX + bytesToHex(hash);
    }
    
    /**
     * Legacy SHA-256 hashing method for backward compatibility
     */
    private String legacyHashPin(String pin, String salt) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String saltedPin = pin + salt;
        byte[] hash = digest.digest(saltedPin.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }
    
    /**
     * Generate cryptographically secure random salt for PIN hashing
     */
    private String generateSalt() {
        byte[] saltBytes = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(saltBytes);
        return bytesToHex(saltBytes);
    }
    
    /**
     * Convert bytes to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    // ==================== Rate Limiting & Lockout ====================
    
    /**
     * Get the number of recorded failed authentication attempts
     */
    public int getFailedAttempts() {
        if (securePrefs == null) {
            return 0;
        }
        return securePrefs.getInt(PREF_FAILED_ATTEMPTS, 0);
    }
    
    /**
     * Record a failed authentication attempt and update lockout time if maximum attempts reached
     * @return Current count of failed attempts
     */
    public int recordFailedAttempt() {
        if (securePrefs == null) {
            return 0;
        }
        
        int currentAttempts = getFailedAttempts() + 1;
        securePrefs.putInt(PREF_FAILED_ATTEMPTS, currentAttempts);
        
        if (currentAttempts >= MAX_FAILED_ATTEMPTS) {
            securePrefs.putLong(PREF_LOCKOUT_TIME, System.currentTimeMillis());
            Log.w(TAG, "Max failed authentication attempts reached (" + currentAttempts + "). App locked out.");
        }
        return currentAttempts;
    }
    
    /**
     * Reset failed attempts counter and lockout timestamp upon successful authentication
     */
    public void resetFailedAttempts() {
        if (securePrefs == null) {
            return;
        }
        securePrefs.remove(PREF_FAILED_ATTEMPTS);
        securePrefs.remove(PREF_LOCKOUT_TIME);
    }
    
    /**
     * Check if authentication is currently locked out
     */
    public boolean isLockedOut() {
        if (securePrefs == null) {
            return false;
        }
        
        int attempts = getFailedAttempts();
        if (attempts < MAX_FAILED_ATTEMPTS) {
            return false;
        }
        
        long lockoutTime = securePrefs.getLong(PREF_LOCKOUT_TIME, 0);
        long elapsed = System.currentTimeMillis() - lockoutTime;
        
        if (elapsed >= LOCKOUT_DURATION_MS) {
            // Lockout period has elapsed, reset lockout state
            resetFailedAttempts();
            return false;
        }
        return true;
    }
    
    /**
     * Get remaining lockout time in seconds
     */
    public int getRemainingLockoutSeconds() {
        if (securePrefs == null) {
            return 0;
        }
        
        long lockoutTime = securePrefs.getLong(PREF_LOCKOUT_TIME, 0);
        long elapsed = System.currentTimeMillis() - lockoutTime;
        long remainingMs = LOCKOUT_DURATION_MS - elapsed;
        
        return remainingMs > 0 ? (int) Math.ceil(remainingMs / 1000.0) : 0;
    }
    
    // ==================== Biometric Authentication ====================
    
    /**
     * Check if biometric authentication is available on device
     */
    public boolean isBiometricAvailable(Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        int result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }
    
    /**
     * Check if biometric is enrolled
     */
    public boolean isBiometricEnrolled(Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        int result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }
    
    /**
     * Show biometric authentication prompt
     */
    public void showBiometricPrompt(FragmentActivity activity, BiometricAuthCallback callback) {
        showBiometricPrompt(activity, "Unlock MobiGPT", "Authenticate to access the app", callback);
    }
    
    /**
     * Show biometric authentication prompt with custom title and subtitle
     */
    public void showBiometricPrompt(FragmentActivity activity, String title, String subtitle, BiometricAuthCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(activity);
        
        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor,
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    callback.onAuthenticationError(errorCode, errString.toString());
                }
                
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    setAuthenticated(true);
                    callback.onAuthenticationSucceeded();
                }
                
                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    callback.onAuthenticationFailed();
                }
            });
        
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use PIN")
            .build();
        
        biometricPrompt.authenticate(promptInfo);
    }
    
    /**
     * Check if authentication is required for export operations
     */
    public boolean isAuthRequiredForExport() {
        return appPrefs.getBoolean("require_auth_export", true);
    }
    
    /**
     * Callback interface for biometric authentication
     */
    public interface BiometricAuthCallback {
        void onAuthenticationSucceeded();
        void onAuthenticationFailed();
        void onAuthenticationError(int errorCode, String errString);
    }
    
    // ==================== Activity Lifecycle Callbacks ====================
    
    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        // Not needed
    }
    
    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        activeActivities++;
        if (activeActivities == 1 && isAppInBackground) {
            // App is coming to foreground
            isAppInBackground = false;
            
            if (isAppLockEnabled() && isLockOnAppSwitchEnabled()) {
                // Lock app when returning from background
                if (!isAuthenticated() || isLockTimeoutExceeded()) {
                    lockApp();
                }
            }
        }
    }
    
    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        updateLastInteractionTime();
    }
    
    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        updateLastInteractionTime();
    }
    
    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        activeActivities--;
        if (activeActivities == 0) {
            // App is going to background
            isAppInBackground = true;
            updateLastInteractionTime();
        }
    }
    
    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        // Not needed
    }
    
    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        // Not needed
    }
    
    // ==================== Security Actions ====================
    
    /**
     * Reset all security settings
     */
    public void resetSecuritySettings() {
        clearPin();
        isAuthenticated = false;
        if (securePrefs != null) {
            securePrefs.clear();
        }
        Log.d(TAG, "Security settings reset");
    }
    
    /**
     * Get time until auto-lock in seconds
     */
    public long getTimeUntilLock() {
        if (!isAppLockEnabled() || !isAuthenticated()) {
            return 0;
        }
        
        int timeoutSeconds = getLockTimeout();
        if (timeoutSeconds == 0) {
            return 0; // Lock immediately
        }
        
        if (timeoutSeconds >= Integer.MAX_VALUE - 1000) {
            return Long.MAX_VALUE; // Never lock
        }
        
        long currentTime = System.currentTimeMillis();
        long lastAuthTime = securePrefs != null ? securePrefs.getLong(PREF_LAST_AUTH_TIME, 0) : 0;
        long elapsedSeconds = (currentTime - lastAuthTime) / 1000;
        
        return Math.max(0, timeoutSeconds - elapsedSeconds);
    }
}
