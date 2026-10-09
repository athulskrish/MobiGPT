package com.keralatechreach.mobigpt.huggingface;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class HuggingFaceModelTest {

    @Test
    public void testHuggingFaceModelFormatting() {
        HuggingFaceModel model = new HuggingFaceModel(
                "bartowski/Llama-3.2-3B-Instruct-GGUF",
                "bartowski",
                "Llama-3.2-3B-Instruct-GGUF",
                154200,
                450,
                "text-generation",
                Arrays.asList("gguf", "text-generation", "llama"),
                "2024-10-01"
        );

        assertEquals("bartowski/Llama-3.2-3B-Instruct-GGUF", model.getId());
        assertEquals("bartowski", model.getAuthor());
        assertEquals("Llama-3.2-3B-Instruct-GGUF", model.getRepoName());
        assertEquals(154200, model.getDownloads());
        assertEquals(450, model.getLikes());
        assertEquals("154.2k", model.getFormattedDownloads());
        assertEquals("450", model.getFormattedLikes());
        assertEquals(3, model.getTags().size());
    }

    @Test
    public void testHuggingFaceModelHighDownloadsFormatting() {
        HuggingFaceModel model = new HuggingFaceModel(
                "Qwen/Qwen2.5-7B-Instruct-GGUF",
                "Qwen",
                "Qwen2.5-7B-Instruct-GGUF",
                1250000,
                1200,
                "text-generation",
                Collections.emptyList(),
                "2024-10-01"
        );

        assertEquals("1.3M", model.getFormattedDownloads());
        assertEquals("1.2k", model.getFormattedLikes());
    }

    @Test
    public void testHuggingFaceFileFormatting() {
        String repoId = "bartowski/Llama-3.2-3B-Instruct-GGUF";
        HuggingFaceFile file = new HuggingFaceFile(
                repoId,
                "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                2013265920L, // ~1.87 GB
                "Q4_K_M",
                "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                true
        );

        assertEquals(repoId, file.getRepoId());
        assertEquals("Llama-3.2-3B-Instruct-Q4_K_M.gguf", file.getFileName());
        assertEquals("Q4_K_M", file.getQuantization());
        assertTrue(file.isRecommended());
        assertEquals("https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf", file.getDownloadUrl());
        assertEquals("1.88 GB", file.getFormattedSize());
    }

    @Test
    public void testSmallFileFormattingInMb() {
        String repoId = "Qwen/Qwen2.5-0.5B-Instruct-GGUF";
        HuggingFaceFile file = new HuggingFaceFile(
                repoId,
                "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                491400032L, // ~468.6 MB
                "Q4_K_M",
                "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                true
        );

        assertEquals("468.6 MB", file.getFormattedSize());
    }
}
