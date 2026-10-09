package com.keralatechreach.mobigpt.huggingface;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.keralatechreach.mobigpt.R;
import com.keralatechreach.mobigpt.utils.DeviceHardwareHelper;
import java.util.ArrayList;
import java.util.List;

public class HuggingFaceFilesAdapter extends RecyclerView.Adapter<HuggingFaceFilesAdapter.FileViewHolder> {

    public interface OnFileDownloadClickListener {
        void onDownloadClick(HuggingFaceFile file);
    }

    private final List<HuggingFaceFile> files = new ArrayList<>();
    private final long availableRamBytes;
    private final OnFileDownloadClickListener listener;

    public HuggingFaceFilesAdapter(long availableRamBytes, OnFileDownloadClickListener listener) {
        this.availableRamBytes = availableRamBytes;
        this.listener = listener;
    }

    public void setFiles(List<HuggingFaceFile> newFiles) {
        this.files.clear();
        if (newFiles != null) {
            this.files.addAll(newFiles);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_huggingface_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        HuggingFaceFile file = files.get(position);
        holder.bind(file, availableRamBytes, listener);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvQuant;
        private final TextView tvRecommended;
        private final TextView tvSize;
        private final TextView tvFileName;
        private final TextView tvRamBadge;
        private final MaterialButton btnDownload;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuant = itemView.findViewById(R.id.tv_file_quant);
            tvRecommended = itemView.findViewById(R.id.tv_file_recommended);
            tvSize = itemView.findViewById(R.id.tv_file_size);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvRamBadge = itemView.findViewById(R.id.tv_ram_badge);
            btnDownload = itemView.findViewById(R.id.btn_download_file);
        }

        public void bind(HuggingFaceFile file, long availableRamBytes, OnFileDownloadClickListener listener) {
            tvQuant.setText(file.getQuantization());
            tvRecommended.setVisibility(file.isRecommended() ? View.VISIBLE : View.GONE);
            tvSize.setText(file.getFormattedSize());
            tvFileName.setText(file.getFileName());

            String readinessBadge = DeviceHardwareHelper.getModelReadinessBadge(file.getSizeBytes(), availableRamBytes);
            tvRamBadge.setText(readinessBadge);

            btnDownload.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDownloadClick(file);
                }
            });
        }
    }
}
