package com.keralatechreach.mobigpt.database;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.Transaction;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import androidx.annotation.NonNull;
import com.keralatechreach.mobigpt.Constants;
import com.keralatechreach.mobigpt.security.DatabaseKeyManager;
import java.util.List;
import java.util.concurrent.Executors;

@Database(entities = {Chat.class, Message.class}, version = 4, exportSchema = true)
public abstract class ChatDatabase extends RoomDatabase {
    
    private static volatile ChatDatabase INSTANCE;
    private static final String TAG = "ChatDatabase";
    
    // Migration from version 1 to 2 (adding composite indexes)
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create the new indexes manually
            try {
                // Individual indexes (these might already exist, so use IF NOT EXISTS)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_timestamp` ON `messages` (`timestamp`)");
                
                // Composite indexes (these are new)
                database.execSQL("CREATE INDEX IF NOT EXISTS `idx_chat_timestamp` ON `messages` (`chatId`, `timestamp`)");
                database.execSQL("CREATE INDEX IF NOT EXISTS `idx_chat_user` ON `messages` (`chatId`, `isUser`)");
                database.execSQL("CREATE INDEX IF NOT EXISTS `idx_user_timestamp` ON `messages` (`isUser`, `timestamp`)");
                
                Log.d(TAG, "Database migrated from version 1 to 2 - all indexes created successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error creating indexes during migration", e);
                throw e;
            }
        }
    };
    
    // Migration from version 2 to 3 (adding isStarred field and related indexes)
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                // Add the isStarred column to the messages table
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `isStarred` INTEGER NOT NULL DEFAULT 0");
                
                // Create new indexes for starred functionality
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_isStarred` ON `messages` (`isStarred`)");
                database.execSQL("CREATE INDEX IF NOT EXISTS `idx_starred_timestamp` ON `messages` (`isStarred`, `timestamp`)");
                database.execSQL("CREATE INDEX IF NOT EXISTS `idx_chat_starred` ON `messages` (`chatId`, `isStarred`)");
                
                Log.d(TAG, "Database migrated from version 2 to 3 - isStarred field and indexes added successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error adding isStarred field during migration", e);
                throw e;
            }
        }
    };
    
    // Migration from version 3 to 4 (adding token count fields)
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                // Add token count and generation time columns to the messages table
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `tokenCount` INTEGER NOT NULL DEFAULT 0");
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `generationTimeMs` INTEGER NOT NULL DEFAULT 0");
                
                Log.d(TAG, "Database migrated from version 3 to 4 - token statistics fields added successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error adding token statistics fields during migration", e);
                throw e;
            }
        }
    };
    
    public abstract ChatDao chatDao();
    public abstract MessageDao messageDao();
    
    // Transaction methods for ensuring database consistency
    @Transaction
    public long createChatWithMessage(Chat chat, Message message) {
        long chatId = chatDao().insertChat(chat);
        message.chatId = chatId;
        messageDao().insertMessage(message);
        return chatId;
    }
    
    @Transaction
    public void addMessageAndUpdateChat(Message message, Chat chat) {
        messageDao().insertMessage(message);
        chatDao().updateChat(chat);
    }
    
    @Transaction
    public void deleteChatAndMessages(Chat chat) {
        // Messages will be deleted automatically due to CASCADE foreign key
        chatDao().deleteChat(chat);
    }
    
    public static ChatDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (ChatDatabase.class) {
                if (INSTANCE == null) {
                    try {
                        // Get encryption key from secure key manager
                        DatabaseKeyManager keyManager = new DatabaseKeyManager(context);
                        byte[] passphrase = keyManager.getDatabasePassphrase();
                        
                        Log.d(TAG, "Initializing encrypted database with SQLCipher");
                        
                        // Create encrypted database using our custom SQLCipher factory
                        SQLCipherHelperFactory factory = new SQLCipherHelperFactory(passphrase);
                        
                        RoomDatabase.Builder<ChatDatabase> builder = Room.databaseBuilder(context.getApplicationContext(),
                                ChatDatabase.class, "chat_database")
                            .openHelperFactory(factory) // Enable SQLCipher encryption
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            // REMOVED: .fallbackToDestructiveMigration() - prevents data loss in production
                            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                            .enableMultiInstanceInvalidation(); // For multi-process support
                        
                        boolean isDebug = (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                        if (isDebug) {
                            builder.setQueryCallback(new RoomDatabase.QueryCallback() {
                                @Override
                                public void onQuery(@NonNull String sqlQuery, @NonNull List<? extends Object> bindArgs) {
                                    Log.d(TAG, "SQL Query: " + sqlQuery);
                                }
                            }, Executors.newSingleThreadExecutor());
                        }

                        INSTANCE = builder
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    Log.d(TAG, "Database created successfully");
                                }
                                
                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    Log.d(TAG, "Database opened successfully");
                                    
                                    // Apply PRAGMA optimizations
                                    execPragma(db, "PRAGMA synchronous=NORMAL");
                                    execPragma(db, "PRAGMA temp_store=MEMORY");
                                    execPragma(db, "PRAGMA cache_size=" + Constants.DB_CACHE_SIZE);

                                    // PRAGMA mmap_size returns a result row in SQLite; query() must be used instead of execSQL()
                                    queryPragma(db, "PRAGMA mmap_size=" + Constants.DB_MMAP_SIZE);
                                    
                                    // SQLCipher memory security
                                    execPragma(db, "PRAGMA cipher_memory_security=ON");
                                    
                                    Log.d(TAG, "Encrypted database optimizations applied");
                                }
                            })
                            .build();
                    
                        Log.d(TAG, "Database initialized with SQLCipher encryption");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to initialize encrypted database", e);
                        throw new RuntimeException("Cannot initialize encrypted database. Please check your device security settings.", e);
                    }
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Executes a PRAGMA statement that does not return rows (e.g., synchronous, temp_store).
     * Falls back to query() if the statement returns a result row.
     */
    private static void execPragma(SupportSQLiteDatabase db, String pragma) {
        try {
            db.execSQL(pragma);
        } catch (Exception e) {
            try (Cursor cursor = db.query(pragma)) {
                if (cursor != null) {
                    cursor.moveToFirst();
                }
            } catch (Exception ex) {
                Log.w(TAG, "Failed to apply pragma: " + pragma, ex);
            }
        }
    }

    /**
     * Executes a PRAGMA statement that returns a result row (e.g., mmap_size) using query().
     */
    private static void queryPragma(SupportSQLiteDatabase db, String pragma) {
        try (Cursor cursor = db.query(pragma)) {
            if (cursor != null) {
                cursor.moveToFirst();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to execute query pragma: " + pragma, e);
        }
    }

    /**
     * Close database instance - useful for testing or memory cleanup
     */
    public static void closeDatabase() {
        if (INSTANCE != null) {
            INSTANCE.close();
            INSTANCE = null;
        }
    }
}