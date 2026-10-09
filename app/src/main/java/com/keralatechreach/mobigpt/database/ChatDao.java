package com.keralatechreach.mobigpt.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ChatDao {
    
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    List<Chat> getAllChats();
    
    @Query("SELECT * FROM chats WHERE id = :chatId")
    Chat getChatById(long chatId);
    
    @Insert
    long insertChat(Chat chat);
    
    @Update
    void updateChat(Chat chat);
    
    @Delete
    void deleteChat(Chat chat);
    
    @Query("DELETE FROM chats")
    void deleteAllChats();
}