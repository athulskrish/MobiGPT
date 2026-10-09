package com.keralatechreach.mobigpt.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Secure wrapper for SharedPreferences using AndroidX Security library
 * Provides encrypted storage for sensitive app preferences and metadata
 */
public class SecurePreferences {
    
    private static final String TAG = "SecurePreferences";
    private static final String ENCRYPTED_PREFS_SUFFIX = "_encrypted";
    
    private final SharedPreferences encryptedPrefs;
    private final Context context;
    private final String prefsName;
    
    /**
     * Create secure encrypted SharedPreferences
     * 
     * @param context Application context
     * @param prefsName Name of the preferences file
     * @throws SecurityException if encryption setup fails
     */
    public SecurePreferences(@NonNull Context context, @NonNull String prefsName) {
        this.context = context.getApplicationContext();
        this.prefsName = prefsName;
        this.encryptedPrefs = createEncryptedPreferences(this.context, prefsName);
    }
    
    /**
     * Create or retrieve encrypted SharedPreferences instance
     */
    private SharedPreferences createEncryptedPreferences(Context context, String fileName) {
        try {
            // Create or retrieve the MasterKey for encryption
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            // Create encrypted SharedPreferences
            return EncryptedSharedPreferences.create(
                    context,
                    fileName + ENCRYPTED_PREFS_SUFFIX,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error creating encrypted preferences: " + fileName, e);
            throw new SecurityException("Failed to create encrypted preferences", e);
        }
    }
    
    /**
     * Get the underlying SharedPreferences instance (encrypted)
     */
    public SharedPreferences getPreferences() {
        return encryptedPrefs;
    }
    
    // String operations
    
    public void putString(@NonNull String key, String value) {
        encryptedPrefs.edit().putString(key, value).apply();
    }
    
    public String getString(@NonNull String key, String defaultValue) {
        return encryptedPrefs.getString(key, defaultValue);
    }
    
    // Integer operations
    
    public void putInt(@NonNull String key, int value) {
        encryptedPrefs.edit().putInt(key, value).apply();
    }
    
    public int getInt(@NonNull String key, int defaultValue) {
        return encryptedPrefs.getInt(key, defaultValue);
    }
    
    // Long operations
    
    public void putLong(@NonNull String key, long value) {
        encryptedPrefs.edit().putLong(key, value).apply();
    }
    
    public long getLong(@NonNull String key, long defaultValue) {
        return encryptedPrefs.getLong(key, defaultValue);
    }
    
    // Boolean operations
    
    public void putBoolean(@NonNull String key, boolean value) {
        encryptedPrefs.edit().putBoolean(key, value).apply();
    }
    
    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        return encryptedPrefs.getBoolean(key, defaultValue);
    }
    
    // Float operations
    
    public void putFloat(@NonNull String key, float value) {
        encryptedPrefs.edit().putFloat(key, value).apply();
    }
    
    public float getFloat(@NonNull String key, float defaultValue) {
        return encryptedPrefs.getFloat(key, defaultValue);
    }
    
    // Utility operations
    
    /**
     * Check if a key exists in preferences
     */
    public boolean contains(@NonNull String key) {
        return encryptedPrefs.contains(key);
    }
    
    /**
     * Remove a specific key from preferences
     */
    public void remove(@NonNull String key) {
        encryptedPrefs.edit().remove(key).apply();
    }
    
    /**
     * Clear all preferences
     */
    public void clear() {
        encryptedPrefs.edit().clear().apply();
    }
    
    /**
     * Migrate data from unencrypted SharedPreferences to encrypted
     * Call this once during app upgrade to preserve existing data
     * 
     * @param oldPrefsName Name of the old unencrypted preferences file
     * @return true if migration was successful, false if no migration needed
     */
    public boolean migrateFromUnencrypted(@NonNull String oldPrefsName) {
        try {
            SharedPreferences oldPrefs = context.getSharedPreferences(oldPrefsName, Context.MODE_PRIVATE);
            
            if (oldPrefs.getAll().isEmpty()) {
                Log.d(TAG, "No data to migrate from: " + oldPrefsName);
                return false;
            }
            
            Log.d(TAG, "Migrating " + oldPrefs.getAll().size() + " items from unencrypted to encrypted preferences");
            
            SharedPreferences.Editor editor = encryptedPrefs.edit();
            
            // Copy all data from old preferences to encrypted preferences
            for (java.util.Map.Entry<String, ?> entry : oldPrefs.getAll().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                }
            }
            
            editor.apply();
            
            // Clear old unencrypted data for security
            oldPrefs.edit().clear().apply();
            
            Log.d(TAG, "Migration completed successfully from: " + oldPrefsName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error during migration from: " + oldPrefsName, e);
            return false;
        }
    }
    
    /**
     * Factory method to create SecurePreferences with automatic migration
     * 
     * @param context Application context
     * @param prefsName Name for both old and new preferences
     * @return SecurePreferences instance with migrated data
     */
    public static SecurePreferences createWithMigration(@NonNull Context context, @NonNull String prefsName) {
        SecurePreferences securePrefs = new SecurePreferences(context, prefsName);
        
        // Check if migration is needed (encrypted prefs are empty)
        if (securePrefs.getPreferences().getAll().isEmpty()) {
            securePrefs.migrateFromUnencrypted(prefsName);
        }
        
        return securePrefs;
    }
}
