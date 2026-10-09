package com.keralatechreach.mobigpt.huggingface;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.keralatechreach.mobigpt.R;
import java.util.ArrayList;
import java.util.List;

public class HuggingFaceModelAdapter extends RecyclerView.Adapter<HuggingFaceModelAdapter.ModelViewHolder> {

    public interface OnModelClickListener {
        void onModelClick(HuggingFaceModel model);
    }

    private final List<HuggingFaceModel> models = new ArrayList<>();
    private final OnModelClickListener listener;

    public HuggingFaceModelAdapter(OnModelClickListener listener) {
        this.listener = listener;
    }

    public void setModels(List<HuggingFaceModel> newModels) {
        this.models.clear();
        if (newModels != null) {
            this.models.addAll(newModels);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_huggingface_model, parent, false);
        return new ModelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ModelViewHolder holder, int position) {
        HuggingFaceModel model = models.get(position);
        holder.bind(model, listener);
    }

    @Override
    public int getItemCount() {
        return models.size();
    }

    static class ModelViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvAuthor;
        private final TextView tvTag;
        private final TextView tvName;
        private final TextView tvDownloads;
        private final TextView tvLikes;

        public ModelViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAuthor = itemView.findViewById(R.id.tv_model_author);
            tvTag = itemView.findViewById(R.id.tv_model_tag);
            tvName = itemView.findViewById(R.id.tv_model_name);
            tvDownloads = itemView.findViewById(R.id.tv_model_downloads);
            tvLikes = itemView.findViewById(R.id.tv_model_likes);
        }

        public void bind(HuggingFaceModel model, OnModelClickListener listener) {
            tvAuthor.setText(model.getAuthor().isEmpty() ? "Hugging Face" : model.getAuthor());
            tvName.setText(model.getRepoName());
            tvDownloads.setText(model.getFormattedDownloads());
            tvLikes.setText(model.getFormattedLikes());

            String tag = "GGUF";
            if (model.getPipelineTag() != null && !model.getPipelineTag().isEmpty()) {
                tag = model.getPipelineTag();
            }
            tvTag.setText(tag);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onModelClick(model);
                }
            });
        }
    }
}
