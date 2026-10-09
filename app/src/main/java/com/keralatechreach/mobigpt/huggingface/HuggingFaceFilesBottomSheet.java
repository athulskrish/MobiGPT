package com.keralatechreach.mobigpt.huggingface;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.keralatechreach.mobigpt.R;
import com.keralatechreach.mobigpt.utils.DeviceHardwareHelper;
import java.util.List;
import java.util.Locale;

public class HuggingFaceFilesBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_MODEL = "arg_model";

    public interface OnFileSelectedListener {
        void onFileSelected(HuggingFaceModel model, HuggingFaceFile file);
    }

    private HuggingFaceModel model;
    private OnFileSelectedListener listener;
    private HuggingFaceApiClient apiClient;

    private TextView tvModelTitle;
    private TextView tvModelAuthor;
    private TextView tvDeviceRam;
    private LinearLayout layoutLoading;
    private LinearLayout layoutError;
    private TextView tvErrorMessage;
    private MaterialButton btnRetry;
    private RecyclerView recyclerFiles;
    private HuggingFaceFilesAdapter adapter;

    public static HuggingFaceFilesBottomSheet newInstance(HuggingFaceModel model) {
        HuggingFaceFilesBottomSheet fragment = new HuggingFaceFilesBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_MODEL, model);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnFileSelectedListener) {
            listener = (OnFileSelectedListener) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            model = (HuggingFaceModel) getArguments().getSerializable(ARG_MODEL);
        }
        apiClient = new HuggingFaceApiClient();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_huggingface_files, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvModelTitle = view.findViewById(R.id.tv_sheet_model_title);
        tvModelAuthor = view.findViewById(R.id.tv_sheet_model_author);
        tvDeviceRam = view.findViewById(R.id.tv_sheet_device_ram);
        layoutLoading = view.findViewById(R.id.layout_files_loading);
        layoutError = view.findViewById(R.id.layout_files_error);
        tvErrorMessage = view.findViewById(R.id.tv_files_error_message);
        btnRetry = view.findViewById(R.id.btn_retry_files);
        recyclerFiles = view.findViewById(R.id.recycler_files);
        ImageButton btnClose = view.findViewById(R.id.btn_close_files_sheet);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        if (model != null) {
            tvModelTitle.setText(model.getRepoName());
            tvModelAuthor.setText("by " + (model.getAuthor().isEmpty() ? "Hugging Face" : model.getAuthor()) + " • Select a quantization file");
        }

        // Display hardware RAM info
        Context ctx = getContext();
        long availRam = 0;
        if (ctx != null) {
            DeviceHardwareHelper.SystemHardwareStats stats = DeviceHardwareHelper.getSystemHardwareStats(ctx);
            availRam = stats.availableRamBytes;
            double totalGb = stats.totalRamBytes / (1024.0 * 1024.0 * 1024.0);
            double availGb = stats.availableRamBytes / (1024.0 * 1024.0 * 1024.0);
            tvDeviceRam.setText(String.format(Locale.US, "Device RAM: %.1f GB (%.1f GB Free) • Q4_K_M recommended", totalGb, availGb));
        }

        recyclerFiles.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HuggingFaceFilesAdapter(availRam, file -> {
            if (listener != null && model != null) {
                listener.onFileSelected(model, file);
                dismiss();
            }
        });
        recyclerFiles.setAdapter(adapter);

        btnRetry.setOnClickListener(v -> loadFiles());

        loadFiles();
    }

    private void loadFiles() {
        if (model == null) return;

        layoutLoading.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        recyclerFiles.setVisibility(View.GONE);

        apiClient.fetchModelFiles(model.getId(), new HuggingFaceApiClient.ApiCallback<List<HuggingFaceFile>>() {
            @Override
            public void onSuccess(List<HuggingFaceFile> files) {
                if (!isAdded()) return;
                layoutLoading.setVisibility(View.GONE);
                if (files.isEmpty()) {
                    layoutError.setVisibility(View.VISIBLE);
                    tvErrorMessage.setText("No GGUF files found in this repository");
                    btnRetry.setVisibility(View.GONE);
                } else {
                    recyclerFiles.setVisibility(View.VISIBLE);
                    adapter.setFiles(files);
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;
                layoutLoading.setVisibility(View.GONE);
                layoutError.setVisibility(View.VISIBLE);
                tvErrorMessage.setText(errorMessage);
                btnRetry.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (apiClient != null) {
            apiClient.shutdown();
        }
    }
}
