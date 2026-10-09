package com.keralatechreach.mobigpt.models;

/**
 * Represents a suggestion chip shown in the empty state
 * 
 * Suggestion chips help users get started with the AI chat by providing
 * pre-written prompts they can tap to quickly send a message.
 */
public class SuggestionChip {
    
    private final String text;
    private final Integer iconResId; // Optional icon resource ID
    
    /**
     * Create a suggestion chip with text only
     * @param text The suggestion text to display and send when clicked
     */
    public SuggestionChip(String text) {
        this.text = text;
        this.iconResId = null;
    }
    
    /**
     * Create a suggestion chip with text and icon
     * @param text The suggestion text to display and send when clicked
     * @param iconResId Resource ID of the icon to display (e.g., R.drawable.ic_lightbulb)
     */
    public SuggestionChip(String text, Integer iconResId) {
        this.text = text;
        this.iconResId = iconResId;
    }
    
    public String getText() {
        return text;
    }
    
    public Integer getIconResId() {
        return iconResId;
    }
    
    public boolean hasIcon() {
        return iconResId != null;
    }
}
