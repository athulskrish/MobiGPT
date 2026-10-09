package com.keralatechreach.mobigpt.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.keralatechreach.mobigpt.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom adapter for search suggestions with history icon
 * Displays search history in a dropdown similar to Google Search
 */
public class SearchSuggestionAdapter extends ArrayAdapter<String> {
    
    private static final String TAG = "SearchSuggestionAdapter";
    
    private final Context context;
    private List<String> suggestions;
    private final LayoutInflater inflater;
    
    public SearchSuggestionAdapter(@NonNull Context context, @NonNull List<String> suggestions) {
        super(context, R.layout.item_search_suggestion, suggestions);
        this.context = context;
        this.suggestions = new ArrayList<>(suggestions);
        this.inflater = LayoutInflater.from(context);
        Log.d(TAG, "Adapter created with " + suggestions.size() + " suggestions");
    }
    
    @Override
    public int getCount() {
        return suggestions.size();
    }
    
    @Override
    public String getItem(int position) {
        if (position >= 0 && position < suggestions.size()) {
            return suggestions.get(position);
        }
        return null;
    }
    
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_search_suggestion, parent, false);
            holder = new ViewHolder();
            holder.iconHistory = convertView.findViewById(R.id.icon_history);
            holder.textSuggestion = convertView.findViewById(R.id.text_suggestion);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        String suggestion = getItem(position);
        if (suggestion != null) {
            holder.textSuggestion.setText(suggestion);
        }
        
        return convertView;
    }
    
    /**
     * Update suggestions list
     */
    public void updateSuggestions(List<String> newSuggestions) {
        Log.d(TAG, "Updating suggestions: " + newSuggestions.size() + " items");
        this.suggestions = new ArrayList<>(newSuggestions);
        clear();
        addAll(newSuggestions);
        notifyDataSetChanged();
        Log.d(TAG, "Adapter count after update: " + getCount());
    }
    
    private static class ViewHolder {
        ImageView iconHistory;
        TextView textSuggestion;
    }
}
