package com.keralatechreach.mobigpt.huggingface;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.keralatechreach.mobigpt.utils.SecureNetworkManager;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Client for interacting with the public Hugging Face Hub REST API.
 * Uses secure HTTPS connections, background execution, and delivers results
 * back to the Android main thread.
 */
public class HuggingFaceApiClient {
    private static final String TAG = "HuggingFaceApiClient";
    private static final String BASE_URL = "https://huggingface.co/api/models";

    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public HuggingFaceApiClient() {
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "HF-ApiClient");
            t.setDaemon(true);
            return t;
        });
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Search models or fetch trending models from Hugging Face Hub.
     *
     * @param query Search keywords (e.g. "llama-3.2", "qwen2.5"), or empty/null for trending.
     * @param sort Sort order: "trending", "downloads", or "likes".
     * @param limit Maximum results (e.g. 30).
     * @param callback Result callback on main thread.
     */
    public void searchModels(String query, String sort, int limit, ApiCallback<List<HuggingFaceModel>> callback) {
        executor.execute(() -> {
            try {
                StringBuilder urlBuilder = new StringBuilder(BASE_URL);
                if (query != null && !query.trim().isEmpty()) {
                    urlBuilder.append("?search=").append(URLEncoder.encode(query.trim(), "UTF-8"));
                    urlBuilder.append("&filter=gguf");
                    if ("downloads".equalsIgnoreCase(sort)) {
                        urlBuilder.append("&sort=downloads&direction=-1");
                    } else if ("likes".equalsIgnoreCase(sort)) {
                        urlBuilder.append("&sort=likes&direction=-1");
                    } else {
                        urlBuilder.append("&sort=trendingScore&direction=-1");
                    }
                } else {
                    urlBuilder.append("?pipeline_tag=text-generation&filter=gguf");
                    if ("downloads".equalsIgnoreCase(sort)) {
                        urlBuilder.append("&sort=downloads&direction=-1");
                    } else if ("likes".equalsIgnoreCase(sort)) {
                        urlBuilder.append("&sort=likes&direction=-1");
                    } else {
                        urlBuilder.append("&sort=trendingScore&direction=-1");
                    }
                }
                urlBuilder.append("&limit=").append(limit > 0 ? limit : 30);

                String urlString = urlBuilder.toString();
                Log.d(TAG, "Fetching models from: " + urlString);

                String jsonResponse = performGet(urlString);
                JSONArray array = new JSONArray(jsonResponse);
                List<HuggingFaceModel> models = new ArrayList<>();

                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.optJSONObject(i);
                    if (obj != null) {
                        HuggingFaceModel model = HuggingFaceModel.fromJson(obj);
                        if (model != null) {
                            models.add(model);
                        }
                    }
                }

                mainHandler.post(() -> callback.onSuccess(models));

            } catch (Exception e) {
                Log.e(TAG, "Error fetching models from Hugging Face", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to load models"));
            }
        });
    }

    /**
     * Fetch all GGUF quantization files for a specific repository.
     *
     * @param repoId Model repository ID (e.g., "bartowski/Llama-3.2-3B-Instruct-GGUF").
     * @param callback Result callback on main thread.
     */
    public void fetchModelFiles(String repoId, ApiCallback<List<HuggingFaceFile>> callback) {
        if (repoId == null || repoId.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("Invalid repository ID"));
            return;
        }

        executor.execute(() -> {
            try {
                String urlString = BASE_URL + "/" + repoId.trim() + "/tree/main";
                Log.d(TAG, "Fetching files from: " + urlString);

                String jsonResponse = performGet(urlString);
                JSONArray array = new JSONArray(jsonResponse);
                List<HuggingFaceFile> files = new ArrayList<>();

                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.optJSONObject(i);
                    if (obj != null) {
                        HuggingFaceFile file = HuggingFaceFile.fromJson(repoId, obj);
                        if (file != null) {
                            files.add(file);
                        }
                    }
                }

                // Sort: recommended Q4_K_M first, then by size ascending
                Collections.sort(files, (a, b) -> {
                    if (a.isRecommended() && !b.isRecommended()) return -1;
                    if (!a.isRecommended() && b.isRecommended()) return 1;
                    return Long.compare(a.getSizeBytes(), b.getSizeBytes());
                });

                mainHandler.post(() -> callback.onSuccess(files));

            } catch (Exception e) {
                Log.e(TAG, "Error fetching repo files for: " + repoId, e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to load files"));
            }
        });
    }

    private String performGet(String urlString) throws Exception {
        HttpsURLConnection connection = null;
        BufferedReader reader = null;
        try {
            connection = SecureNetworkManager.createSecureConnection(urlString);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "MobiGPT-Android");
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            try {
                SecureNetworkManager.validateCertificatePinning(connection);
            } catch (Exception pinEx) {
                Log.w(TAG, "Pinning validation note: " + pinEx.getMessage());
            }

            if (responseCode >= 200 && responseCode < 300) {
                InputStream is = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            } else {
                StringBuilder errorBody = new StringBuilder();
                InputStream es = connection.getErrorStream();
                if (es != null) {
                    try (BufferedReader errReader = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8))) {
                        String errLine;
                        while ((errLine = errReader.readLine()) != null) {
                            errorBody.append(errLine);
                        }
                    } catch (Exception ignored) {}
                }
                String errDetail = errorBody.length() > 0 ? ": " + errorBody.toString() : "";
                throw new Exception("Hugging Face API returned HTTP " + responseCode + errDetail);
            }
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
