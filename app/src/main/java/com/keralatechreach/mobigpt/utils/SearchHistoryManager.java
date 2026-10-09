package com.keralatechreach.mobigpt.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Manager class for handling search history
 * Stores and retrieves search queries with deduplication and ordering
 */
public class SearchHistoryManager {
    
    private static final String PREF_KEY_SEARCH_HISTORY = "search_history";
    private static final int MAX_HISTORY_SIZE = 50; // Maximum number of search queries to store
    
    private final Context context;
    private final SharedPreferences prefs;
    private final SettingsManager settingsManager;
    
    public SearchHistoryManager(Context context) {
        this.context = context;
        this.prefs = getEncryptedPreferences(context);
        this.settingsManager = new SettingsManager(context);
    }
    
    /**
     * Create or retrieve encrypted SharedPreferences for search history.
     * Falls back to default SharedPreferences if encryption fails.
     */
    private static SharedPreferences getEncryptedPreferences(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                "search_history_encrypted",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            android.util.Log.e("SearchHistory", "Failed to create encrypted prefs, using default", e);
            return context.getSharedPreferences("search_history_fallback", Context.MODE_PRIVATE);
        }
    }
    
    /**
     * Check if search history is enabled in settings
     */
    public boolean isSearchHistoryEnabled() {
        return settingsManager.isMessageSearchHistory();
    }
    
    /**
     * Add a search query to history
     * Automatically deduplicates and moves recent queries to the top
     * 
     * @param query The search query to add
     */
    public void addSearchQuery(String query) {
        if (!isSearchHistoryEnabled()) {
            return; // Don't save if search history is disabled
        }
        
        if (query == null || query.trim().isEmpty()) {
            return; // Don't save empty queries
        }
        
        query = query.trim();
        
        // Get current history
        List<String> history = getSearchHistory();
        
        // Use LinkedHashSet to maintain order and avoid duplicates
        LinkedHashSet<String> historySet = new LinkedHashSet<>();
        
        // Add the new query first (most recent)
        historySet.add(query);
        
        // Add existing queries (this automatically deduplicates)
        historySet.addAll(history);
        
        // Convert back to list and limit size
        List<String> updatedHistory = new ArrayList<>(historySet);
        if (updatedHistory.size() > MAX_HISTORY_SIZE) {
            updatedHistory = updatedHistory.subList(0, MAX_HISTORY_SIZE);
        }
        
        // Save to preferences
        saveSearchHistory(updatedHistory);
    }
    
    /**
     * Get the complete search history
     * Returns queries in order from most recent to oldest
     * 
     * @return List of search queries
     */
    public List<String> getSearchHistory() {
        if (!isSearchHistoryEnabled()) {
            return new ArrayList<>(); // Return empty list if disabled
        }
        
        String json = prefs.getString(PREF_KEY_SEARCH_HISTORY, null);
        
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            JSONArray jsonArray = new JSONArray(json);
            List<String> history = new ArrayList<>();
            
            for (int i = 0; i < jsonArray.length(); i++) {
                history.add(jsonArray.getString(i));
            }
            
            return history;
        } catch (JSONException e) {
            android.util.Log.e("SearchHistory", "Error parsing search history", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get search suggestions based on a query prefix
     * Returns queries that start with the given prefix
     * 
     * @param prefix The search prefix to match
     * @return List of matching search queries
     */
    public List<String> getSuggestions(String prefix) {
        if (!isSearchHistoryEnabled()) {
            return new ArrayList<>();
        }
        
        if (prefix == null || prefix.trim().isEmpty()) {
            return getSearchHistory(); // Return all history if no prefix
        }
        
        String lowerPrefix = prefix.trim().toLowerCase();
        List<String> allHistory = getSearchHistory();
        List<String> suggestions = new ArrayList<>();
        
        for (String query : allHistory) {
            if (query.toLowerCase().startsWith(lowerPrefix)) {
                suggestions.add(query);
            }
        }
        
        return suggestions;
    }
    
    /**
     * Get search suggestions that contain the query anywhere (not just prefix)
     * 
     * @param query The search term to match
     * @return List of matching search queries
     */
    public List<String> getSuggestionsContaining(String query) {
        if (!isSearchHistoryEnabled()) {
            return new ArrayList<>();
        }
        
        if (query == null || query.trim().isEmpty()) {
            return getSearchHistory();
        }
        
        String lowerQuery = query.trim().toLowerCase();
        List<String> allHistory = getSearchHistory();
        List<String> suggestions = new ArrayList<>();
        
        for (String historyQuery : allHistory) {
            if (historyQuery.toLowerCase().contains(lowerQuery)) {
                suggestions.add(historyQuery);
            }
        }
        
        return suggestions;
    }
    
    /**
     * Remove a specific query from search history
     * 
     * @param query The query to remove
     */
    public void removeSearchQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        
        List<String> history = getSearchHistory();
        history.remove(query.trim());
        saveSearchHistory(history);
    }
    
    /**
     * Clear all search history
     */
    public void clearSearchHistory() {
        prefs.edit().remove(PREF_KEY_SEARCH_HISTORY).apply();
    }
    
    /**
     * Get the number of items in search history
     * 
     * @return Number of saved search queries
     */
    public int getHistorySize() {
        return getSearchHistory().size();
    }
    
    /**
     * Check if search history is empty
     * 
     * @return true if history is empty, false otherwise
     */
    public boolean isHistoryEmpty() {
        return getHistorySize() == 0;
    }
    
    /**
     * Get the most recent search query
     * 
     * @return The most recent query, or null if history is empty
     */
    public String getLastSearchQuery() {
        List<String> history = getSearchHistory();
        return history.isEmpty() ? null : history.get(0);
    }
    
    /**
     * Save search history to preferences
     */
    private void saveSearchHistory(List<String> history) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (String query : history) {
                jsonArray.put(query);
            }
            String json = jsonArray.toString();
            prefs.edit().putString(PREF_KEY_SEARCH_HISTORY, json).apply();
        } catch (Exception e) {
            android.util.Log.e("SearchHistory", "Error saving search history", e);
        }
    }
}
