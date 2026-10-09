package com.keralatechreach.mobigpt.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableStringBuilder;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.keralatechreach.mobigpt.R;
import com.keralatechreach.mobigpt.database.StarredMessageItem;
import com.keralatechreach.mobigpt.utils.MarkdownFormatter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StarredMessagesAdapter extends RecyclerView.Adapter<StarredMessagesAdapter.StarredViewHolder> {

    public interface OnStarredItemActionListener {
        void onUnstarClick(StarredMessageItem item, int position);
        void onOpenChatClick(StarredMessageItem item);
    }

    private final Context context;
    private final MarkdownFormatter markdownFormatter;
    private final List<StarredMessageItem> items = new ArrayList<>();
    private OnStarredItemActionListener actionListener;

    public StarredMessagesAdapter(Context context) {
        this.context = context;
        this.markdownFormatter = new MarkdownFormatter(context);
    }

    public void setActionListener(OnStarredItemActionListener listener) {
        this.actionListener = listener;
    }

    public void setItems(List<StarredMessageItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, items.size() - position);
        }
    }

    public List<StarredMessageItem> getItems() {
        return items;
    }

    @NonNull
    @Override
    public StarredViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_starred_qa_card, parent, false);
        return new StarredViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StarredViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class StarredViewHolder extends RecyclerView.ViewHolder {
        final TextView textChatTitle;
        final TextView textStarredTime;
        final ImageButton btnUnstar;

        final View layoutQuestionContainer;
        final TextView textQuestionContent;

        final View layoutResponseContainer;
        final TextView textResponseContent;
        final TextView textTokenStats;

        // Thinking accordion
        final View containerThinkingProcess;
        final View layoutThinkingHeader;
        final TextView textThinkingTitle;
        final ImageView iconThinkingChevron;
        final View layoutThinkingBody;
        final TextView textThinkingContent;

        // Actions
        final MaterialButton btnCopy;
        final MaterialButton btnShare;
        final MaterialButton btnOpenChat;

        boolean isThinkingExpanded = false;

        StarredViewHolder(@NonNull View itemView) {
            super(itemView);
            textChatTitle = itemView.findViewById(R.id.text_chat_title);
            textStarredTime = itemView.findViewById(R.id.text_starred_time);
            btnUnstar = itemView.findViewById(R.id.btn_unstar);

            layoutQuestionContainer = itemView.findViewById(R.id.layout_question_container);
            textQuestionContent = itemView.findViewById(R.id.text_question_content);

            layoutResponseContainer = itemView.findViewById(R.id.layout_response_container);
            textResponseContent = itemView.findViewById(R.id.text_response_content);
            textTokenStats = itemView.findViewById(R.id.text_token_stats);

            containerThinkingProcess = itemView.findViewById(R.id.container_thinking_process);
            layoutThinkingHeader = itemView.findViewById(R.id.layout_thinking_header);
            textThinkingTitle = itemView.findViewById(R.id.text_thinking_title);
            iconThinkingChevron = itemView.findViewById(R.id.icon_thinking_chevron);
            layoutThinkingBody = itemView.findViewById(R.id.layout_thinking_body);
            textThinkingContent = itemView.findViewById(R.id.text_thinking_content);

            btnCopy = itemView.findViewById(R.id.btn_copy);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnOpenChat = itemView.findViewById(R.id.btn_open_chat);
        }

        void bind(StarredMessageItem item) {
            textChatTitle.setText(item.chatTitle);
            textStarredTime.setText(formatTime(item.getDisplayTimestamp()));

            // Question
            String question = item.getQuestionText();
            if (question != null && !question.trim().isEmpty()) {
                layoutQuestionContainer.setVisibility(View.VISIBLE);
                textQuestionContent.setText(question);
            } else {
                layoutQuestionContainer.setVisibility(View.GONE);
            }

            // Response
            String rawResponse = item.getResponseText();
            MarkdownFormatter.ParsedMessage parsed = markdownFormatter.parseMessage(rawResponse, false);

            // Thinking process accordion
            if (parsed.isThinking && parsed.thinkingText != null && !parsed.thinkingText.trim().isEmpty()) {
                containerThinkingProcess.setVisibility(View.VISIBLE);
                textThinkingContent.setText(parsed.thinkingText.trim());
                textThinkingTitle.setText("Thought Process (Complete)");

                isThinkingExpanded = false;
                layoutThinkingBody.setVisibility(View.GONE);
                iconThinkingChevron.setRotation(0f);

                layoutThinkingHeader.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    isThinkingExpanded = !isThinkingExpanded;
                    layoutThinkingBody.setVisibility(isThinkingExpanded ? View.VISIBLE : View.GONE);
                    iconThinkingChevron.animate().rotation(isThinkingExpanded ? 180f : 0f).setDuration(150).start();
                });
            } else {
                containerThinkingProcess.setVisibility(View.GONE);
                layoutThinkingHeader.setOnClickListener(null);
            }

            // Formatted Response Markdown
            String cleanText = (parsed.mainText != null && !parsed.mainText.trim().isEmpty())
                    ? parsed.mainText
                    : rawResponse;
            SpannableStringBuilder formattedSpannable = markdownFormatter.format(cleanText);
            textResponseContent.setText(formattedSpannable);

            // Token stats
            String stats = item.getTokenStats();
            if (stats != null && !stats.isEmpty()) {
                textTokenStats.setText(stats);
                textTokenStats.setVisibility(View.VISIBLE);
            } else {
                textTokenStats.setVisibility(View.GONE);
            }

            // Unstar click
            btnUnstar.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && actionListener != null) {
                    actionListener.onUnstarClick(item, pos);
                }
            });

            // Copy action
            btnCopy.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                copyToClipboard(cleanText);
            });

            // Long click copy to copy full Q&A
            btnCopy.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                String fullQa = "Q: " + question + "\n\nA: " + cleanText;
                copyToClipboard(fullQa);
                Toast.makeText(context, "Copied full Q&A to clipboard", Toast.LENGTH_SHORT).show();
                return true;
            });

            // Share action
            btnShare.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                shareQa(question, cleanText);
            });

            // Open Chat action
            btnOpenChat.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (actionListener != null) {
                    actionListener.onOpenChatClick(item);
                }
            });
        }

        private void copyToClipboard(String text) {
            try {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("Starred Message", text);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(context, "Failed to copy: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        private void shareQa(String question, String answer) {
            try {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                String shareBody = "Question:\n" + question + "\n\nAnswer:\n" + answer + "\n\n—\nShared from MobiGPT";
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "MobiGPT Starred Message");
                Intent chooser = Intent.createChooser(shareIntent, "Share via");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooser);
            } catch (Exception e) {
                Toast.makeText(context, "Could not share message", Toast.LENGTH_SHORT).show();
            }
        }

        private String formatTime(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;
            if (diff < 60_000) {
                return "Just now";
            } else if (diff < 3600_000) {
                return (diff / 60_000) + "m ago";
            } else if (diff < 86400_000) {
                return (diff / 3600_000) + "h ago";
            } else if (diff < 172800_000) {
                return "Yesterday";
            } else {
                SimpleDateFormat fmt = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                return fmt.format(new Date(timestamp));
            }
        }
    }
}
