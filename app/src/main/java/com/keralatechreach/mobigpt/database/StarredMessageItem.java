package com.keralatechreach.mobigpt.database;

import androidx.annotation.Nullable;

/**
 * Model class representing a Starred Q&A item.
 * Pairs a starred message with its complementary counterpart (Question + Response)
 * so users can review the full contextual dialogue in Starred Messages.
 */
public class StarredMessageItem {

    public final Message starredMessage;
    @Nullable public final Message questionMessage;
    @Nullable public final Message responseMessage;
    public final String chatTitle;
    public final long chatId;

    public StarredMessageItem(
            Message starredMessage,
            @Nullable Message questionMessage,
            @Nullable Message responseMessage,
            String chatTitle,
            long chatId
    ) {
        this.starredMessage = starredMessage;
        this.questionMessage = questionMessage;
        this.responseMessage = responseMessage;
        this.chatTitle = (chatTitle != null && !chatTitle.trim().isEmpty()) ? chatTitle : "Conversation";
        this.chatId = chatId;
    }

    public String getQuestionText() {
        if (questionMessage != null && questionMessage.content != null) {
            return questionMessage.content.trim();
        }
        if (starredMessage != null && starredMessage.isUser && starredMessage.content != null) {
            return starredMessage.content.trim();
        }
        return "Prompt unavailable";
    }

    public String getResponseText() {
        if (responseMessage != null && responseMessage.content != null) {
            return responseMessage.content.trim();
        }
        if (starredMessage != null && !starredMessage.isUser && starredMessage.content != null) {
            return starredMessage.content.trim();
        }
        return "No response recorded";
    }

    public long getDisplayTimestamp() {
        if (starredMessage != null) {
            return starredMessage.timestamp;
        }
        if (responseMessage != null) {
            return responseMessage.timestamp;
        }
        if (questionMessage != null) {
            return questionMessage.timestamp;
        }
        return System.currentTimeMillis();
    }

    public String getTokenStats() {
        if (responseMessage != null) {
            return responseMessage.getTokenStats();
        }
        if (starredMessage != null && !starredMessage.isUser) {
            return starredMessage.getTokenStats();
        }
        return null;
    }

    public boolean hasQuestion() {
        return questionMessage != null || (starredMessage != null && starredMessage.isUser);
    }

    public boolean hasResponse() {
        return responseMessage != null || (starredMessage != null && !starredMessage.isUser);
    }
}
