package com.keralatechreach.mobigpt.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chats")
public class Chat {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public String title;
    public long createdAt;
    public long lastMessageTime;
    
    public Chat(String title, long createdAt, long lastMessageTime) {
        this.title = title;
        this.createdAt = createdAt;
        this.lastMessageTime = lastMessageTime;
    }
}