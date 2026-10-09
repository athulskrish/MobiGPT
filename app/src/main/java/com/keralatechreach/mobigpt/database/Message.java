package com.keralatechreach.mobigpt.database;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;
import androidx.room.Index;
import androidx.room.Ignore;

@Entity(tableName = "messages",
        foreignKeys = @ForeignKey(entity = Chat.class,
                                parentColumns = "id",
                                childColumns = "chatId",
                                onDelete = ForeignKey.CASCADE),
        indices = {
            @Index("chatId"),
            @Index("timestamp"),
            @Index("isStarred"),
            @Index(value = {"chatId", "timestamp"}, name = "idx_chat_timestamp"),
            @Index(value = {"chatId", "isUser"}, name = "idx_chat_user"),
            @Index(value = {"isUser", "timestamp"}, name = "idx_user_timestamp"),
            @Index(value = {"isStarred", "timestamp"}, name = "idx_starred_timestamp"),
            @Index(value = {"chatId", "isStarred"}, name = "idx_chat_starred")
        })
public class Message {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long chatId;
    public String content;
    public boolean isUser; // true for user messages, false for AI responses
    public boolean isStarred; // true for starred messages
    public long timestamp;
    
    // Token generation statistics (for AI responses)
    public int tokenCount; // Total number of tokens generated
    public long generationTimeMs; // Time taken to generate the response in milliseconds
    
    public Message(long chatId, String content, boolean isUser, long timestamp) {
        this.chatId = chatId;
        this.content = content;
        this.isUser = isUser;
        this.isStarred = false; // default to not starred
        this.timestamp = timestamp;
        this.tokenCount = 0;
        this.generationTimeMs = 0;
    }
    
    // Constructor with starred field
    @Ignore
    public Message(long chatId, String content, boolean isUser, boolean isStarred, long timestamp) {
        this.chatId = chatId;
        this.content = content;
        this.isUser = isUser;
        this.isStarred = isStarred;
        this.timestamp = timestamp;
        this.tokenCount = 0;
        this.generationTimeMs = 0;
    }
    
    // Helper method to calculate tokens per second
    public double getTokensPerSecond() {
        if (generationTimeMs <= 0 || tokenCount <= 0) {
            return 0;
        }
        return (tokenCount * 1000.0) / generationTimeMs;
    }
    
    // Helper method to get formatted token statistics
    public String getTokenStats() {
        if (!isUser && tokenCount > 0 && generationTimeMs > 0) {
            double tokensPerSec = getTokensPerSecond();
            return String.format("%.1f token/s", tokensPerSec);
        }
        return null;
    }
}