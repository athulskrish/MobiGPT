package com.keralatechreach.mobigpt.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Manages secure database encryption keys using Android Keystore System
 * Provides a passphrase for SQLCipher database encryption
 * Uses encrypted SharedPreferences to store encrypted keys
 */
public class DatabaseKeyManager {
    
    private static final String TAG = "DatabaseKeyManager";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "MobiGPT_DB_Key";
    private static final String PREFS_NAME = "secure_db_prefs";
    private static final String ENCRYPTED_KEY_PREF = "encrypted_db_key";
    private static final String IV_PREF = "encryption_iv";
    
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_SIZE = 256; // 32 bytes
    
    private final Context context;
    private final SecurePreferences securePreferences;
    private final SharedPreferences prefs; // For compatibility
    
    public DatabaseKeyManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        // Use encrypted SharedPreferences with automatic migration
        this.securePreferences = SecurePreferences.createWithMigration(this.context, PREFS_NAME);
        this.prefs = this.securePreferences.getPreferences();
    }
    
    /**
     * Get or create the database passphrase
     * @return Byte array passphrase for SQLCipher
     */
    @NonNull
    public byte[] getDatabasePassphrase() {
        try {
            // Check if we have an encrypted key stored
            String encryptedKey = prefs.getString(ENCRYPTED_KEY_PREF, null);
            String ivString = prefs.getString(IV_PREF, null);
            
            if (encryptedKey != null && ivString != null) {
                // Decrypt existing key
                return decryptDatabaseKey(encryptedKey, ivString);
            } else {
                // Generate new key
                return generateAndStoreNewKey();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting database passphrase", e);
            // Fallback: generate new key (this will cause data loss if upgrading)
            // In production, you should handle this more gracefully
            try {
                return generateAndStoreNewKey();
            } catch (Exception ex) {
                Log.e(TAG, "Fatal error generating database key", ex);
                throw new RuntimeException("Cannot secure database", ex);
            }
        }
    }
    
    /**
     * Generate a new random database key and store it encrypted
     */
    @NonNull
    private byte[] generateAndStoreNewKey() throws Exception {
        // Generate random 256-bit key
        byte[] databaseKey = new byte[KEY_SIZE / 8]; // 32 bytes
        SecureRandom random = new SecureRandom();
        random.nextBytes(databaseKey);
        
        // Ensure we have a key in Android Keystore
        ensureKeystoreKey();
        
        // Encrypt the database key
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        
        byte[] iv = cipher.getIV();
        byte[] encryptedKey = cipher.doFinal(databaseKey);
        
        // Store encrypted key and IV
        prefs.edit()
                .putString(ENCRYPTED_KEY_PREF, Base64.encodeToString(encryptedKey, Base64.NO_WRAP))
                .putString(IV_PREF, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply();
        
        Log.d(TAG, "Generated and stored new database key");
        return databaseKey;
    }
    
    /**
     * Decrypt the stored database key
     */
    @NonNull
    private byte[] decryptDatabaseKey(@NonNull String encryptedKeyString, @NonNull String ivString) throws Exception {
        byte[] encryptedKey = Base64.decode(encryptedKeyString, Base64.NO_WRAP);
        byte[] iv = Base64.decode(ivString, Base64.NO_WRAP);
        
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        
        SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (secretKey == null) {
            throw new Exception("Keystore key not found");
        }
        
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        
        return cipher.doFinal(encryptedKey);
    }
    
    /**
     * Ensure we have a key in the Android Keystore
     */
    private void ensureKeystoreKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            // Generate new key in keystore
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, 
                    KEYSTORE_PROVIDER
            );
            
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256);
            
            // For Android M and above, require user authentication for enhanced security
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder.setRandomizedEncryptionRequired(true);
            }
            
            // Note: Don't require user authentication for database access
            // as it would make the app unusable. The keystore protection is sufficient.
            
            keyGenerator.init(builder.build());
            keyGenerator.generateKey();
            
            Log.d(TAG, "Generated new keystore key");
        }
    }
    
    /**
     * Clear all stored keys (for security reset or user logout)
     * WARNING: This will make existing encrypted database inaccessible
     */
    public void clearKeys() {
        try {
            // Clear encrypted SharedPreferences
            securePreferences.clear();
            
            // Remove key from keystore
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
            }
            
            Log.d(TAG, "Cleared all database keys");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing keys", e);
        }
    }
    
    /**
     * Get passphrase as char array (required by some SQLCipher APIs)
     */
    @NonNull
    public char[] getPassphraseAsChars() {
        byte[] passphrase = getDatabasePassphrase();
        // Convert bytes to hex string to chars
        String hexString = bytesToHex(passphrase);
        return hexString.toCharArray();
    }
    
    /**
     * Convert byte array to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
