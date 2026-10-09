	package com.keralatechreach.mobigpt.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.TypefaceSpan;
import android.text.style.RelativeSizeSpan;
import androidx.core.content.ContextCompat;
import com.keralatechreach.mobigpt.R;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing markdown-style text and converting it to formatted Android SpannableString
 * Supports: **bold**, *italic*, `code`, ~~strikethrough~~, __underline__, # headers, and basic formatting
 */
public class MarkdownFormatter {
    
    private final Context context;
    
    // Regex patterns for different markdown elements
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.*?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*(.*?)\\*(?!\\*)"); // Avoid matching ** and avoid overlap
    private static final Pattern CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("~~(.*?)~~");
    private static final Pattern UNDERLINE_PATTERN = Pattern.compile("__(.*?)__");
    private static final Pattern HEADER1_PATTERN = Pattern.compile("^# (.+)$", Pattern.MULTILINE);
    private static final Pattern HEADER2_PATTERN = Pattern.compile("^## (.+)$", Pattern.MULTILINE);
    private static final Pattern HEADER3_PATTERN = Pattern.compile("^### (.+)$", Pattern.MULTILINE);
    
    // Additional patterns for common AI response formatting
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\w]*\\n?([\\s\\S]*?)```");
    private static final Pattern CODE_BLOCK_PATTERN_WITH_LANG = Pattern.compile("```([\\w-]*)\\n?([\\s\\S]*?)```");
    private static final Pattern BULLET_POINT_PATTERN = Pattern.compile("^\\s*[-*+]\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^\\s\\)]+)\\)");
    
    public MarkdownFormatter(Context context) {
        this.context = context;
    }
    
    private boolean isDarkMode() {
        if (context == null) return false;
        return new ThemeManager(context).isDarkMode();
    }
    
    private int getCodeBackgroundColor() {
        return ContextCompat.getColor(context, isDarkMode() ? R.color.code_background_dark : R.color.code_background);
    }
    
    private int getCodeTextColor() {
        return ContextCompat.getColor(context, isDarkMode() ? R.color.code_text_dark : R.color.code_text);
    }
    
    private int getHeaderTextColor() {
        return ContextCompat.getColor(context, isDarkMode() ? R.color.header_text_dark : R.color.header_text);
    }
    
    /**
     * Convert markdown text to SpannableStringBuilder with formatting
     * @param text The input text with markdown formatting
     * @return SpannableStringBuilder with Android text spans applied
     */
    public SpannableStringBuilder format(String text) {
        if (text == null || text.isEmpty()) {
            return new SpannableStringBuilder();
        }
        
        SpannableStringBuilder spannableBuilder = new SpannableStringBuilder(text);
        
        // Apply formatting in order (most specific to least specific)
        applyCodeBlocks(spannableBuilder);  // Apply code blocks first to avoid conflicts
        applyHeaders(spannableBuilder);
        applyLinks(spannableBuilder);
        applyBold(spannableBuilder);
        applyItalic(spannableBuilder);
        applyCode(spannableBuilder);
        applyStrikethrough(spannableBuilder);
        applyUnderline(spannableBuilder);
        applyBulletPoints(spannableBuilder);
        
        return spannableBuilder;
    }
    
    /**
     * Format text optimized for streaming (lighter processing for partial content)
     * This method applies only the most common formatting to reduce processing overhead during streaming
     * @param text The input text with markdown formatting (may be incomplete)
     * @param isStreaming Whether this is streaming content (affects processing)
     * @return SpannableStringBuilder with basic Android text spans applied
     */
    public SpannableStringBuilder formatStreaming(String text, boolean isStreaming) {
        if (text == null || text.isEmpty()) {
            return new SpannableStringBuilder();
        }
        
        SpannableStringBuilder spannableBuilder = new SpannableStringBuilder(text);
        
        if (isStreaming) {
            // During streaming, only apply bold and italic for performance
            // Other formatting will be applied when streaming is complete
            applyBold(spannableBuilder);
            applyItalic(spannableBuilder);
        } else {
            // Apply full formatting for completed content
            return format(text);
        }
        
        return spannableBuilder;
    }
    
    /**
     * Apply link formatting for [label](url)
     */
    private void applyLinks(SpannableStringBuilder builder) {
        String text = builder.toString();
        Matcher matcher = LINK_PATTERN.matcher(text);
        
        class LinkMatch {
            final int start;
            final int end;
            final String label;
            final String url;
            LinkMatch(int start, int end, String label, String url) {
                this.start = start;
                this.end = end;
                this.label = label;
                this.url = url;
            }
        }
        
        List<LinkMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new LinkMatch(matcher.start(), matcher.end(), matcher.group(1), matcher.group(2)));
        }
        
        int linkColor = ContextCompat.getColor(context, R.color.info_blue);
        for (int i = matches.size() - 1; i >= 0; i--) {
            LinkMatch match = matches.get(i);
            builder.replace(match.start, match.end, match.label);
            int newEnd = match.start + match.label.length();
            builder.setSpan(new android.text.style.URLSpan(match.url), match.start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new ForegroundColorSpan(linkColor), match.start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new UnderlineSpan(), match.start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    
    /**
     * Apply bold formatting for **text**
     */
    private void applyBold(SpannableStringBuilder builder) {
        applyPattern(builder, BOLD_PATTERN, text -> new StyleSpan(Typeface.BOLD));
    }
    
    /**
     * Apply italic formatting for *text*
     */
    private void applyItalic(SpannableStringBuilder builder) {
        applyPattern(builder, ITALIC_PATTERN, text -> new StyleSpan(Typeface.ITALIC));
    }
    
    /**
     * Apply code formatting for `text`
     */
    private void applyCode(SpannableStringBuilder builder) {
        applyPattern(builder, CODE_PATTERN, text -> {
            // Return an array of spans for code formatting
            return new Object[] {
                new TypefaceSpan("monospace"),
                new BackgroundColorSpan(getCodeBackgroundColor()),
                new ForegroundColorSpan(getCodeTextColor())
            };
        });
    }
    
    /**
     * Apply strikethrough formatting for ~~text~~
     */
    private void applyStrikethrough(SpannableStringBuilder builder) {
        applyPattern(builder, STRIKETHROUGH_PATTERN, text -> new StrikethroughSpan());
    }
    
    /**
     * Apply underline formatting for __text__
     */
    private void applyUnderline(SpannableStringBuilder builder) {
        applyPattern(builder, UNDERLINE_PATTERN, text -> new UnderlineSpan());
    }
    
    /**
     * Apply header formatting for # text, ## text, ### text
     */
    private void applyHeaders(SpannableStringBuilder builder) {
        int headerColor = getHeaderTextColor();
        // Header 1 (largest)
        applyPattern(builder, HEADER1_PATTERN, text -> new Object[] {
            new StyleSpan(Typeface.BOLD),
            new RelativeSizeSpan(1.35f),
            new ForegroundColorSpan(headerColor)
        });
        
        // Header 2 (medium)
        applyPattern(builder, HEADER2_PATTERN, text -> new Object[] {
            new StyleSpan(Typeface.BOLD),
            new RelativeSizeSpan(1.2f),
            new ForegroundColorSpan(headerColor)
        });
        
        // Header 3 (smaller)
        applyPattern(builder, HEADER3_PATTERN, text -> new Object[] {
            new StyleSpan(Typeface.BOLD),
            new RelativeSizeSpan(1.1f),
            new ForegroundColorSpan(headerColor)
        });
    }
    
    /**
     * Apply code block formatting for ```code```
     */
    private void applyCodeBlocks(SpannableStringBuilder builder) {
        applyPattern(builder, CODE_BLOCK_PATTERN, text -> {
            // Return an array of spans for code block formatting
            return new Object[] {
                new TypefaceSpan("monospace"),
                new BackgroundColorSpan(getCodeBackgroundColor()),
                new ForegroundColorSpan(getCodeTextColor()),
                new RelativeSizeSpan(0.9f) // Slightly smaller text for code blocks
            };
        });
    }
    
    /**
     * Apply bullet point formatting for - text, * text, + text
     */
    private void applyBulletPoints(SpannableStringBuilder builder) {
        String text = builder.toString();
        Matcher matcher = BULLET_POINT_PATTERN.matcher(text);
        
        // Store matches to apply in reverse order
        List<MatchResult> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new MatchResult(matcher.start(), matcher.end(), matcher.group(1)));
        }
        
        int bulletColor = getHeaderTextColor();
        // Apply matches in reverse order to maintain correct indices
        for (int i = matches.size() - 1; i >= 0; i--) {
            MatchResult match = matches.get(i);
            
            // Replace with bullet point character and content
            String bulletText = "• " + match.content;
            builder.replace(match.start, match.end, bulletText);
            
            // Apply formatting to the bullet point
            int bulletEnd = match.start + 1;
            builder.setSpan(new ForegroundColorSpan(bulletColor), 
                           match.start, bulletEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new StyleSpan(Typeface.BOLD),
                           match.start, bulletEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    
    /**
     * Generic method to apply a pattern and span(s) to text
     */
    private void applyPattern(SpannableStringBuilder builder, Pattern pattern, SpanFactory spanFactory) {
        String text = builder.toString();
        Matcher matcher = pattern.matcher(text);
        
        // Store matches to apply in reverse order (to maintain correct indices)
        List<MatchResult> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new MatchResult(matcher.start(), matcher.end(), matcher.group(1)));
        }
        
        // Apply matches in reverse order to maintain correct indices
        for (int i = matches.size() - 1; i >= 0; i--) {
            MatchResult match = matches.get(i);
            
            // Replace the markdown syntax with just the text content
            builder.replace(match.start, match.end, match.content);
            
            // Calculate new end position after replacement
            int newEnd = match.start + match.content.length();
            
            // Apply the span(s)
            Object spanOrSpans = spanFactory.createSpan(match.content);
            if (spanOrSpans instanceof Object[]) {
                // Multiple spans (like for code formatting)
                Object[] spans = (Object[]) spanOrSpans;
                for (Object span : spans) {
                    builder.setSpan(span, match.start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                // Single span
                builder.setSpan(spanOrSpans, match.start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }
    
    /**
     * Interface for creating spans based on matched text
     */
    private interface SpanFactory {
        Object createSpan(String text);
    }
    
    /**
     * Helper class to store match results
     */
    private static class MatchResult {
        final int start;
        final int end;
        final String content;
        
        MatchResult(int start, int end, String content) {
            this.start = start;
            this.end = end;
            this.content = content != null ? content : "";
        }
    }

    /**
     * Represents a chunk of message content (either rich formatted text or a distinct code block)
     */
    public static class MessageChunk {
        public final boolean isCode;
        public final String text;
        public final String language;

        public MessageChunk(boolean isCode, String text, String language) {
            this.isCode = isCode;
            this.text = text != null ? text : "";
            this.language = language != null ? language : "CODE";
        }
    }

    /**
     * Structured result separating reasoning/thinking and rich message chunks
     */
    public static class ParsedMessage {
        public final String thinkingText;
        public final boolean isThinking;
        public final boolean isThinkingComplete;
        public final String mainText;
        public final List<MessageChunk> chunks;

        public ParsedMessage(String thinkingText, boolean isThinking, boolean isThinkingComplete, String mainText, List<MessageChunk> chunks) {
            this.thinkingText = thinkingText;
            this.isThinking = isThinking;
            this.isThinkingComplete = isThinkingComplete;
            this.mainText = mainText != null ? mainText : "";
            this.chunks = chunks != null ? chunks : new ArrayList<>();
        }
    }

    /**
     * Parse message text into thinking process (e.g. DeepSeek-R1 <think>) and rich content chunks
     * @param rawText Raw text received from the model
     * @param isStreaming Whether the message is actively streaming
     * @return ParsedMessage with isolated thinking and content chunks
     */
    public ParsedMessage parseMessage(String rawText, boolean isStreaming) {
        if (rawText == null || rawText.isEmpty()) {
            return new ParsedMessage(null, false, false, "", new ArrayList<>());
        }

        String thinkingText = null;
        boolean isThinking = false;
        boolean isThinkingComplete = false;
        String mainText = rawText;

        int thinkStart = rawText.indexOf("<think>");
        if (thinkStart != -1) {
            isThinking = true;
            int contentStart = thinkStart + 7;
            int thinkEnd = rawText.indexOf("</think>", contentStart);
            if (thinkEnd != -1) {
                thinkingText = rawText.substring(contentStart, thinkEnd).trim();
                isThinkingComplete = true;
                mainText = rawText.substring(thinkEnd + 8).trim();
            } else {
                thinkingText = rawText.substring(contentStart).trim();
                isThinkingComplete = false;
                mainText = "";
            }
        }

        List<MessageChunk> chunks = new ArrayList<>();
        if (!mainText.isEmpty() && !isStreaming) {
            Matcher matcher = CODE_BLOCK_PATTERN_WITH_LANG.matcher(mainText);
            int lastEnd = 0;
            while (matcher.find()) {
                if (matcher.start() > lastEnd) {
                    String textBefore = mainText.substring(lastEnd, matcher.start()).trim();
                    if (!textBefore.isEmpty()) {
                        chunks.add(new MessageChunk(false, textBefore, null));
                    }
                }
                String lang = matcher.group(1);
                if (lang == null || lang.trim().isEmpty()) {
                    lang = "CODE";
                } else {
                    lang = lang.trim().toUpperCase(java.util.Locale.US);
                }
                String code = matcher.group(2);
                chunks.add(new MessageChunk(true, code != null ? code.trim() : "", lang));
                lastEnd = matcher.end();
            }
            if (lastEnd < mainText.length()) {
                String textAfter = mainText.substring(lastEnd).trim();
                if (!textAfter.isEmpty()) {
                    chunks.add(new MessageChunk(false, textAfter, null));
                }
            }
        }

        if (chunks.isEmpty() && !mainText.isEmpty()) {
            chunks.add(new MessageChunk(false, mainText, null));
        }

        return new ParsedMessage(thinkingText, isThinking, isThinkingComplete, mainText, chunks);
    }
}