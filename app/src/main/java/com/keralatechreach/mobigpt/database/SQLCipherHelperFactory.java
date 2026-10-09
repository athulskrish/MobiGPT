package com.keralatechreach.mobigpt.database;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

/**
 * SQLCipher support helper factory for Room.
 * Uses official Zetetic SupportOpenHelperFactory to ensure proper SQLite and Room lifecycle management.
 */
public class SQLCipherHelperFactory implements SupportSQLiteOpenHelper.Factory {
    
    private final SupportOpenHelperFactory delegate;
    
    public SQLCipherHelperFactory(byte[] passphrase) {
        // Ensure SQLCipher native library is loaded
        System.loadLibrary("sqlcipher");
        this.delegate = new SupportOpenHelperFactory(passphrase);
    }
    
    @NonNull
    @Override
    public SupportSQLiteOpenHelper create(@NonNull SupportSQLiteOpenHelper.Configuration configuration) {
        return delegate.create(configuration);
    }
}
