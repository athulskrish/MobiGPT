package com.keralatechreach.mobigpt.huggingface;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.keralatechreach.mobigpt.R;
import com.keralatechreach.mobigpt.base.BaseActivity;
import java.util.List;

public class HuggingFaceHubActivity extends BaseActivity implements HuggingFaceFilesBottomSheet.OnFileSelectedListener {

    public static final String EXTRA_MODEL_NAME = "extra_model_name";
    public static final String EXTRA_DOWNLOAD_URL = "extra_download_url";
    public static final String EXTRA_FILE_NAME = "extra_file_name";
    public static final String EXTRA_FILE_SIZE = "extra_file_size";

    private HuggingFaceApiClient apiClient;
    private HuggingFaceModelAdapter adapter;

    private EditText etSearchQuery;
    private ImageButton btnClearSearch;
    private ChipGroup chipGroupFilters;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerModels;
    private LinearLayout layoutLoading;
    private LinearLayout layoutEmpty;
    private LinearLayout layoutError;
    private TextView tvErrorMessage;
    private MaterialButton btnRetry;

    private String currentQuery = "";
    private String currentSort = "trending";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_huggingface_hub);

        apiClient = new HuggingFaceApiClient();

        initViews();
        setupToolbar();
        setupSearch();
        setupFilterChips();
        setupRecyclerView();

        loadModels();
    }

    private void initViews() {
        etSearchQuery = findViewById(R.id.et_search_query);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        chipGroupFilters = findViewById(R.id.chip_group_filters);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        recyclerModels = findViewById(R.id.recycler_models);
        layoutLoading = findViewById(R.id.layout_loading);
        layoutEmpty = findViewById(R.id.layout_empty);
        layoutError = findViewById(R.id.layout_error);
        tvErrorMessage = findViewById(R.id.tv_error_message);
        btnRetry = findViewById(R.id.btn_retry_models);

        swipeRefresh.setOnRefreshListener(this::loadModels);
        btnRetry.setOnClickListener(v -> loadModels());
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_hf_hub);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSearch() {
        etSearchQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                currentQuery = etSearchQuery.getText() != null ? etSearchQuery.getText().toString().trim() : "";
                loadModels();
                return true;
            }
            return false;
        });

        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                btnClearSearch.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                if (s == null || s.length() == 0) {
                    if (!currentQuery.isEmpty()) {
                        currentQuery = "";
                        loadModels();
                    }
                }
            }
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearchQuery.setText("");
            currentQuery = "";
            loadModels();
        });
    }

    private void setupFilterChips() {
        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                currentSort = "trending";
                currentQuery = "";
                etSearchQuery.setText("");
                loadModels();
                return;
            }

            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chip_trending) {
                currentSort = "trending";
                currentQuery = "";
                etSearchQuery.setText("");
            } else if (checkedId == R.id.chip_downloads) {
                currentSort = "downloads";
                currentQuery = "";
                etSearchQuery.setText("");
            } else if (checkedId == R.id.chip_likes) {
                currentSort = "likes";
                currentQuery = "";
                etSearchQuery.setText("");
            } else if (checkedId == R.id.chip_qwen) {
                currentQuery = "qwen2.5";
                etSearchQuery.setText("qwen2.5");
            } else if (checkedId == R.id.chip_llama) {
                currentQuery = "llama-3.2";
                etSearchQuery.setText("llama-3.2");
            } else if (checkedId == R.id.chip_deepseek) {
                currentQuery = "deepseek";
                etSearchQuery.setText("deepseek");
            } else if (checkedId == R.id.chip_gemma) {
                currentQuery = "gemma";
                etSearchQuery.setText("gemma");
            } else if (checkedId == R.id.chip_phi) {
                currentQuery = "phi-3";
                etSearchQuery.setText("phi-3");
            }
            loadModels();
        });
    }

    private void setupRecyclerView() {
        recyclerModels.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HuggingFaceModelAdapter(model -> {
            HuggingFaceFilesBottomSheet sheet = HuggingFaceFilesBottomSheet.newInstance(model);
            sheet.show(getSupportFragmentManager(), "hf_files_sheet");
        });
        recyclerModels.setAdapter(adapter);
    }

    private void loadModels() {
        layoutLoading.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);

        apiClient.searchModels(currentQuery, currentSort, 35, new HuggingFaceApiClient.ApiCallback<List<HuggingFaceModel>>() {
            @Override
            public void onSuccess(List<HuggingFaceModel> models) {
                swipeRefresh.setRefreshing(false);
                layoutLoading.setVisibility(View.GONE);

                if (models.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    adapter.setModels(null);
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                    adapter.setModels(models);
                }
            }

            @Override
            public void onError(String errorMessage) {
                swipeRefresh.setRefreshing(false);
                layoutLoading.setVisibility(View.GONE);
                layoutError.setVisibility(View.VISIBLE);
                tvErrorMessage.setText(errorMessage != null ? errorMessage : "Unable to load models");
            }
        });
    }

    @Override
    public void onFileSelected(HuggingFaceModel model, HuggingFaceFile file) {
        Intent resultIntent = new Intent();
        String displayName = file.getFileName().replace(".gguf", "").replace("-", " ").replace("_", " ");
        displayName = com.keralatechreach.mobigpt.ModelConfig.capitalizeWords(displayName);
        resultIntent.putExtra(EXTRA_MODEL_NAME, displayName);
        resultIntent.putExtra(EXTRA_DOWNLOAD_URL, file.getDownloadUrl());
        resultIntent.putExtra(EXTRA_FILE_NAME, file.getFileName());
        resultIntent.putExtra(EXTRA_FILE_SIZE, file.getSizeBytes());
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (apiClient != null) {
            apiClient.shutdown();
        }
    }
}
