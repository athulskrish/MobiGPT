package com.keralatechreach.mobigpt.huggingface;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Model class representing a Hugging Face repository entry.
 */
public class HuggingFaceModel implements Serializable {
    private final String id;
    private final String author;
    private final String repoName;
    private final long downloads;
    private final int likes;
    private final String pipelineTag;
    private final List<String> tags;
    private final String lastModified;

    public HuggingFaceModel(String id, String author, String repoName, long downloads, int likes, String pipelineTag, List<String> tags, String lastModified) {
        this.id = id;
        this.author = author;
        this.repoName = repoName;
        this.downloads = downloads;
        this.likes = likes;
        this.pipelineTag = pipelineTag;
        this.tags = tags != null ? tags : new ArrayList<>();
        this.lastModified = lastModified;
    }

    public static HuggingFaceModel fromJson(JSONObject json) {
        if (json == null) return null;
        String id = json.optString("id", "");
        if (id.isEmpty()) {
            id = json.optString("modelId", "");
        }
        if (id.isEmpty()) return null;

        String author = "";
        String repoName = id;
        int slashIdx = id.indexOf('/');
        if (slashIdx != -1) {
            author = id.substring(0, slashIdx);
            repoName = id.substring(slashIdx + 1);
        }

        long downloads = json.optLong("downloads", 0);
        int likes = json.optInt("likes", 0);
        String pipelineTag = json.optString("pipeline_tag", "text-generation");
        String lastModified = json.optString("lastModified", "");

        List<String> tagList = new ArrayList<>();
        JSONArray tagsJson = json.optJSONArray("tags");
        if (tagsJson != null) {
            for (int i = 0; i < tagsJson.length(); i++) {
                String tag = tagsJson.optString(i);
                if (tag != null && !tag.isEmpty()) {
                    tagList.add(tag);
                }
            }
        }

        return new HuggingFaceModel(id, author, repoName, downloads, likes, pipelineTag, tagList, lastModified);
    }

    public String getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }

    public String getRepoName() {
        return repoName;
    }

    public long getDownloads() {
        return downloads;
    }

    public int getLikes() {
        return likes;
    }

    public String getPipelineTag() {
        return pipelineTag;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getLastModified() {
        return lastModified;
    }

    /**
     * Formats downloads number into a human-friendly string (e.g. 1.2k, 450k, 1.5M).
     */
    public String getFormattedDownloads() {
        if (downloads >= 1_000_000) {
            return String.format(java.util.Locale.US, "%.1fM", downloads / 1_000_000.0);
        } else if (downloads >= 1_000) {
            return String.format(java.util.Locale.US, "%.1fk", downloads / 1_000.0);
        }
        return String.valueOf(downloads);
    }

    public String getFormattedLikes() {
        if (likes >= 1_000) {
            return String.format(java.util.Locale.US, "%.1fk", likes / 1_000.0);
        }
        return String.valueOf(likes);
    }
}
