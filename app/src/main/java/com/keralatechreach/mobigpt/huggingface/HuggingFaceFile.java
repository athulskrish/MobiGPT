package com.keralatechreach.mobigpt.huggingface;

import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * Model class representing a specific GGUF file inside a Hugging Face repository.
 */
public class HuggingFaceFile implements Serializable {
    private final String repoId;
    private final String fileName;
    private final long sizeBytes;
    private final String quantization;
    private final String downloadUrl;
    private final boolean isRecommended;

    public HuggingFaceFile(String repoId, String fileName, long sizeBytes, String quantization, String downloadUrl, boolean isRecommended) {
        this.repoId = repoId;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.quantization = quantization;
        this.downloadUrl = downloadUrl;
        this.isRecommended = isRecommended;
    }

    public static HuggingFaceFile fromJson(String repoId, JSONObject json) {
        if (json == null || repoId == null) return null;

        String path = json.optString("path", "");
        if (path.isEmpty()) return null;

        String lower = path.toLowerCase(Locale.US);
        // Only include .gguf files
        if (!lower.endsWith(".gguf")) {
            return null;
        }

        // Exclude multimodal vision projectors (CLIP architecture) which cannot run as standalone LLMs
        if (lower.contains("mmproj") || lower.contains("-clip") || lower.startsWith("clip")) {
            return null;
        }

        // Avoid split multi-part shards (e.g. model-00001-of-00003.gguf)
        if (lower.matches(".*-\\d{5}-of-\\d{5}\\.gguf")) {
            return null;
        }
        long size = json.optLong("size", -1);
        if (size <= 0 && json.has("lfs")) {
            JSONObject lfs = json.optJSONObject("lfs");
            if (lfs != null) {
                size = lfs.optLong("size", -1);
            }
        }

        // Extract quantization from file name (e.g., Q4_K_M, Q8_0, IQ3_M, FP16, etc.)
        String quant = extractQuantization(path);
        boolean recommended = quant.equalsIgnoreCase("Q4_K_M") || quant.equalsIgnoreCase("Q4_0");

        String downloadUrl = "https://huggingface.co/" + repoId + "/resolve/main/" + path;

        return new HuggingFaceFile(repoId, path, size, quant, downloadUrl, recommended);
    }

    private static String extractQuantization(String fileName) {
        Pattern quantPattern = Pattern.compile("(?i)(I?Q[0-9]+(?:_[A-Za-z0-9_]+)?|FP16|BF16|FP32)");
        Matcher quantMatcher = quantPattern.matcher(fileName);
        if (quantMatcher.find()) {
            return quantMatcher.group(1).toUpperCase(Locale.US);
        }
        return "GGUF";
    }

    public String getRepoId() {
        return repoId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getQuantization() {
        return quantization;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public boolean isRecommended() {
        return isRecommended;
    }

    public String getFormattedSize() {
        if (sizeBytes <= 0) {
            return "Unknown size";
        }
        double gb = sizeBytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) {
            return String.format(Locale.US, "%.2f GB", gb);
        } else {
            double mb = sizeBytes / (1024.0 * 1024.0);
            return String.format(Locale.US, "%.1f MB", mb);
        }
    }
}
