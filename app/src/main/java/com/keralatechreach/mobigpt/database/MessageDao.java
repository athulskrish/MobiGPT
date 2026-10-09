package com.keralatechreach.mobigpt.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MessageDao {
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    List<Message> getMessagesByChatId(long chatId);
    
    @Insert
    long insertMessage(Message message);
    
    @Update
    void updateMessage(Message message);
    
    @Delete
    void deleteMessage(Message message);
    
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    int getMessageCountForChat(long chatId);
    
    @Update
    void updateMessages(List<Message> messages);
    
    // Delete old messages but preserve starred messages
    @Query("DELETE FROM messages WHERE chatId = :chatId AND timestamp < :beforeTimestamp AND isStarred = 0")
    void deleteOldMessages(long chatId, long beforeTimestamp);
    
    // Starred messages
    @Query("SELECT * FROM messages WHERE isStarred = 1 ORDER BY timestamp DESC")
    List<Message> getStarredMessages();

    @Query("SELECT COUNT(*) FROM messages WHERE isStarred = 1")
    int getStarredMessagesCount();

    @Query("UPDATE messages SET isStarred = 0 WHERE isStarred = 1")
    void unstarAllMessages();
    
    @Query("UPDATE messages SET isStarred = :isStarred WHERE id = :messageId")
    void updateMessageStarredStatus(long messageId, boolean isStarred);

    // Get preceding user question for an AI response in the same chat
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isUser = 1 AND id < :aiMessageId ORDER BY id DESC LIMIT 1")
    Message getPrecedingUserMessage(long chatId, long aiMessageId);

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isUser = 1 AND timestamp <= :aiTimestamp AND id != :aiMessageId ORDER BY timestamp DESC, id DESC LIMIT 1")
    Message getPrecedingUserMessageByTimestamp(long chatId, long aiMessageId, long aiTimestamp);

    // Get subsequent AI response for a user question in the same chat
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isUser = 0 AND id > :userMessageId ORDER BY id ASC LIMIT 1")
    Message getSubsequentAiMessage(long chatId, long userMessageId);

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isUser = 0 AND timestamp >= :userTimestamp AND id != :userMessageId ORDER BY timestamp ASC, id ASC LIMIT 1")
    Message getSubsequentAiMessageByTimestamp(long chatId, long userMessageId, long userTimestamp);
    
    // Get all messages (for migration/export purposes)
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    List<Message> getAllMessages();
    
    // Search messages - full-text search across all messages
    @Query("SELECT * FROM messages WHERE content LIKE '%' || :searchQuery || '%' ORDER BY timestamp DESC")
    List<Message> searchMessages(String searchQuery);
    
    // Search messages in a specific chat
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND content LIKE '%' || :searchQuery || '%' ORDER BY timestamp DESC")
    List<Message> searchMessagesInChat(long chatId, String searchQuery);
    
    // Delete all messages
    @Query("DELETE FROM messages")
    void deleteAllMessages();
    
    // Get messages for chat (alias for backwards compatibility)
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    List<Message> getMessagesForChat(long chatId);
}