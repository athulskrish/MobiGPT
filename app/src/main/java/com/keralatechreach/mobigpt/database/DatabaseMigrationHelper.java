package com.keralatechreach.mobigpt.database;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.keralatechreach.mobigpt.security.DatabaseKeyManager;
import com.keralatechreach.mobigpt.security.SecurePreferences;

import java.io.File;

/**
 * Utility class to migrate from unencrypted database to encrypted database
 * This should be called once when updating the app to the encrypted version
 * Uses encrypted SharedPreferences for migration status tracking
 */
public class DatabaseMigrationHelper {
    
    private static final String TAG = "DBMigration";
    private static final String DB_NAME = "chat_database";
    private static final String UNENCRYPTED_DB_NAME = DB_NAME;
    private static final String ENCRYPTED_DB_NAME = DB_NAME + "_encrypted";
    private static final String MIGRATION_FLAG = "db_encrypted";
    private static final String PREFS_NAME = "db_migration";
    
    /**
     * Check if migration is needed and perform it
     * @param context Application context
     * @return true if migration was successful or not needed, false on error
     */
    public static boolean migrateIfNeeded(@NonNull Context context) {
        try {
            // Check if already migrated
            if (isMigrationComplete(context)) {
                Log.d(TAG, "Database already encrypted, no migration needed");
                return true;
            }
            
            // Check if unencrypted database exists
            File unencryptedDb = context.getDatabasePath(UNENCRYPTED_DB_NAME);
            if (!unencryptedDb.exists()) {
                // No database exists yet, mark as migrated
                Log.d(TAG, "No existing database found, marking as encrypted");
                markMigrationComplete(context);
                return true;
            }
            
            Log.d(TAG, "Starting database encryption migration...");
            
            // Perform migration
            boolean success = migrateDatabase(context);
            
            if (success) {
                markMigrationComplete(context);
                Log.d(TAG, "Database migration successful");
            } else {
                Log.e(TAG, "Database migration failed");
            }
            
            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during database migration", e);
            return false;
        }
    }
    
    /**
     * Perform the actual migration from unencrypted to encrypted
     */
    private static boolean migrateDatabase(@NonNull Context context) {
        try {
            // Get encryption key
            DatabaseKeyManager keyManager = new DatabaseKeyManager(context);
            byte[] passphrase = keyManager.getDatabasePassphrase();
            
            // Open unencrypted database
            File unencryptedDbFile = context.getDatabasePath(UNENCRYPTED_DB_NAME);
            
            // Create a temporary encrypted database
            File encryptedDbFile = context.getDatabasePath(ENCRYPTED_DB_NAME);
            
            // Use our custom SQLCipher factory
            SQLCipherHelperFactory factory = new SQLCipherHelperFactory(passphrase);
            
            // Build temporary Room database to access unencrypted data
            ChatDatabase unencryptedDb = Room.databaseBuilder(
                    context.getApplicationContext(),
                    ChatDatabase.class,
                    UNENCRYPTED_DB_NAME)
                    .allowMainThreadQueries() // Only for migration
                    .build();
            
            // Build encrypted database
            ChatDatabase encryptedDb = Room.databaseBuilder(
                    context.getApplicationContext(),
                    ChatDatabase.class,
                    ENCRYPTED_DB_NAME)
                    .openHelperFactory(factory)
                    .allowMainThreadQueries() // Only for migration
                    .build();
            
            // Copy data from unencrypted to encrypted
            copyDatabaseData(unencryptedDb, encryptedDb);
            
            // Close databases
            unencryptedDb.close();
            encryptedDb.close();
            
            // Delete old unencrypted database files
            deleteDatabase(context, UNENCRYPTED_DB_NAME);
            
            // Rename encrypted database to original name
            File finalDbFile = context.getDatabasePath(DB_NAME);
            if (finalDbFile.exists()) {
                finalDbFile.delete();
            }
            encryptedDbFile.renameTo(finalDbFile);
            
            // Also rename WAL and SHM files if they exist
            renameWalFiles(context, ENCRYPTED_DB_NAME, DB_NAME);
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error migrating database", e);
            return false;
        }
    }
    
    /**
     * Copy all data from unencrypted to encrypted database
     */
    private static void copyDatabaseData(ChatDatabase source, ChatDatabase destination) {
        try {
            // Copy all chats
            for (Chat chat : source.chatDao().getAllChats()) {
                destination.chatDao().insertChat(chat);
            }
            
            // Copy all messages
            for (Message message : source.messageDao().getAllMessages()) {
                destination.messageDao().insertMessage(message);
            }
            
            Log.d(TAG, "Database data copied successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error copying database data", e);
            throw e;
        }
    }
    
    /**
     * Delete database and all associated files
     */
    private static void deleteDatabase(Context context, String dbName) {
        File dbFile = context.getDatabasePath(dbName);
        File walFile = new File(dbFile.getPath() + "-wal");
        File shmFile = new File(dbFile.getPath() + "-shm");
        File journalFile = new File(dbFile.getPath() + "-journal");
        
        if (dbFile.exists()) dbFile.delete();
        if (walFile.exists()) walFile.delete();
        if (shmFile.exists()) shmFile.delete();
        if (journalFile.exists()) journalFile.delete();
    }
    
    /**
     * Rename WAL and SHM files
     */
    private static void renameWalFiles(Context context, String oldName, String newName) {
        File oldWal = new File(context.getDatabasePath(oldName).getPath() + "-wal");
        File newWal = new File(context.getDatabasePath(newName).getPath() + "-wal");
        File oldShm = new File(context.getDatabasePath(oldName).getPath() + "-shm");
        File newShm = new File(context.getDatabasePath(newName).getPath() + "-shm");
        
        if (oldWal.exists()) oldWal.renameTo(newWal);
        if (oldShm.exists()) oldShm.renameTo(newShm);
    }
    
    /**
     * Check if migration has been completed
     */
    private static boolean isMigrationComplete(Context context) {
        SecurePreferences securePrefs = SecurePreferences.createWithMigration(context, PREFS_NAME);
        return securePrefs.getBoolean(MIGRATION_FLAG, false);
    }
    
    /**
     * Mark migration as complete
     */
    private static void markMigrationComplete(Context context) {
        SecurePreferences securePrefs = SecurePreferences.createWithMigration(context, PREFS_NAME);
        securePrefs.putBoolean(MIGRATION_FLAG, true);
    }
}
