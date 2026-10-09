package com.keralatechreach.mobigpt;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import androidx.annotation.NonNull;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Configuration class to hold LLM model details including download URLs,
 * file sizes, and other metadata for quantized models.
 */
public class ModelConfig {
    
    public static class Model {
        public final String name;
        public final String displayName;
        public final String downloadUrl;
        public final long fileSizeBytes;
        public final String fileName;
        public final String description;
        public final String quantization;
        public final String parameterCount;
        public final boolean isCustomUrl; // Flag to indicate if this is a custom URL download
        
        public Model(String name, String displayName, String downloadUrl, 
                    long fileSizeBytes, String fileName, String description, String quantization, String parameterCount) {
            this(name, displayName, downloadUrl, fileSizeBytes, fileName, description, quantization, parameterCount, false);
        }

        public Model(String name, String displayName, String downloadUrl, 
                    long fileSizeBytes, String fileName, String description, String quantization, String parameterCount, boolean isCustomUrl) {
            this.name = name;
            this.displayName = displayName;
            this.downloadUrl = downloadUrl;
            this.fileSizeBytes = fileSizeBytes;
            this.fileName = fileName;
            this.description = description;
            this.quantization = quantization;
            this.parameterCount = parameterCount;
            this.isCustomUrl = isCustomUrl;
        }
        
        /**
         * Constructor for custom URL downloads where size is unknown
         * Size will be determined from Content-Length header during download
         */
        private Model(String name, String displayName, String downloadUrl, String fileName) {
            this(name, displayName, downloadUrl, -1, fileName, "Custom model from URL", "Unknown", "Unknown", true);
        }
        
        /**
         * Constructor for custom URL downloads with known size
         * Used when restoring from SharedPreferences or updating after Content-Length is known
         */
        private Model(String name, String displayName, String downloadUrl, String fileName, long fileSizeBytes) {
            this(name, displayName, downloadUrl, fileSizeBytes, fileName, "Custom model from URL", "Unknown", "Unknown", true);
        }
        
        /**
         * Create a custom model from a URL (for user-provided Hugging Face links)
         * @param displayName User-friendly name for the model
         * @param downloadUrl Direct download URL (e.g., from Hugging Face)
         * @param fileName Filename to save as (must end with .gguf)
         * @return Model instance for custom URL download
         */
        public static Model createFromCustomUrl(String displayName, String downloadUrl, String fileName) {
            // Generate a unique name from the filename
            String name = fileName.replace(".gguf", "").replace(".GGUF", "").replaceAll("[^a-zA-Z0-9-_]", "-").toLowerCase();
            
            String searchSource = (fileName + " " + (downloadUrl != null ? downloadUrl : ""));
            // Extract quantization (e.g. Q4_K_M, Q8_0, IQ3_M, FP16, BF16)
            String quantization = "Unknown";
            java.util.regex.Pattern quantPattern = java.util.regex.Pattern.compile("(?i)(I?Q[0-9]+(?:_[A-Za-z0-9_]+)?|FP16|BF16)");
            java.util.regex.Matcher quantMatcher = quantPattern.matcher(searchSource);
            if (quantMatcher.find()) {
                quantization = quantMatcher.group(1).toUpperCase();
            }

            // Extract parameter count (e.g. 0.5B, 1.5B, 3B, 7B, 8B, 14B)
            String parameterCount = "Unknown";
            java.util.regex.Pattern paramPattern = java.util.regex.Pattern.compile("(?i)(?:^|[-_.\\s/])([0-9]+(?:\\.[0-9]+)?)[bB](?:[-_.\\s/]|$)");
            java.util.regex.Matcher paramMatcher = paramPattern.matcher(searchSource);
            if (paramMatcher.find()) {
                parameterCount = paramMatcher.group(1) + "B";
            }

            return new Model(
                name,
                displayName,
                downloadUrl,
                -1,
                fileName,
                "Custom model from URL",
                quantization,
                parameterCount,
                true
            );
        }

        /**
         * Create a model instance for a locally imported or discovered GGUF file.
         * Extracts parameter size and quantization from filename and assigns real disk file size.
         *
         * @param fileName The GGUF filename (e.g. "Llama-3.2-1B-Instruct-Q4_K_M.gguf")
         * @param fileSizeBytes Actual size of the file on disk in bytes
         * @return Initialized Model object
         */
        public static Model createLocalImportedModel(String fileName, long fileSizeBytes) {
            String name = fileName.replace(".gguf", "").replace(".GGUF", "")
                                  .replaceAll("[^a-zA-Z0-9-_]", "-").toLowerCase();

            // Extract quantization (e.g. Q4_K_M, Q8_0, IQ3_M, FP16, BF16)
            String quantization = "Unknown";
            java.util.regex.Pattern quantPattern = java.util.regex.Pattern.compile("(?i)(I?Q[0-9]+(?:_[A-Za-z0-9_]+)?|FP16|BF16)");
            java.util.regex.Matcher quantMatcher = quantPattern.matcher(fileName);
            if (quantMatcher.find()) {
                quantization = quantMatcher.group(1).toUpperCase();
            }

            // Extract parameter count (e.g. 0.5B, 1.5B, 3B, 7B, 8B, 14B)
            String parameterCount = "Unknown";
            java.util.regex.Pattern paramPattern = java.util.regex.Pattern.compile("(?i)(?:^|[-_.\\s])([0-9]+(?:\\.[0-9]+)?)[bB](?:[-_.\\s]|$)");
            java.util.regex.Matcher paramMatcher = paramPattern.matcher(fileName);
            if (paramMatcher.find()) {
                parameterCount = paramMatcher.group(1) + "B";
            } else if (fileSizeBytes > 0) {
                // Heuristic estimation based on quantized file size
                if (fileSizeBytes < 750_000_000L) {
                    parameterCount = "~0.5B";
                } else if (fileSizeBytes < 1_600_000_000L) {
                    parameterCount = "~1.5B";
                } else if (fileSizeBytes < 2_800_000_000L) {
                    parameterCount = "~3B";
                } else if (fileSizeBytes < 5_800_000_000L) {
                    parameterCount = "~7B";
                } else if (fileSizeBytes < 8_000_000_000L) {
                    parameterCount = "~8B";
                }
            }

            // Format clean display name
            String base = fileName.replace(".gguf", "").replace(".GGUF", "")
                                  .replace("-", " ")
                                  .replace("_", " ");
            String displayName = capitalizeWords(base);

            return new Model(
                name,
                displayName,
                "", // No download URL since it's already on device
                fileSizeBytes,
                fileName,
                "Locally imported GGUF model",
                quantization,
                parameterCount,
                true // isCustomUrl = true so it can be managed/deleted as custom
            );
        }
        
        /**
         * Get human-readable file size
         */
        public String getFormattedFileSize() {
            if (fileSizeBytes < 0) {
                return "Unknown"; // For custom URLs where size isn't known yet
            } else if (fileSizeBytes < 1024) {
                return fileSizeBytes + " B";
            } else if (fileSizeBytes < 1024 * 1024) {
                return String.format(Locale.US, "%.1f KB", fileSizeBytes / 1024.0);
            } else if (fileSizeBytes < 1024 * 1024 * 1024) {
                return String.format(Locale.US, "%.1f MB", fileSizeBytes / (1024.0 * 1024.0));
            } else {
                return String.format(Locale.US, "%.1f GB", fileSizeBytes / (1024.0 * 1024.0 * 1024.0));
            }
        }
        
        /**
         * Estimated RAM in MB required to run this model in memory with context KV cache.
         */
        public int getEstimatedRamMb() {
            String p = (parameterCount != null) ? parameterCount.toUpperCase() : "";
            if (p.contains("0.5B")) return 650;
            if (p.contains("1.5B") || p.contains("1B")) return 1450;
            if (p.contains("2B")) return 1950;
            if (p.contains("3B") || p.contains("3.8B") || p.contains("4B")) return 2700;
            if (p.contains("7B") || p.contains("8B")) return 4800;
            if (p.contains("9B")) return 6200;
            if (p.contains("12B")) return 8200;
            
            if (fileSizeBytes > 0) {
                return (int) (fileSizeBytes / (1024 * 1024) + 350);
            }
            return 1500;
        }

        /**
         * Get a human-centered, clutter-free description optimized for mobile cognitive limits.
         */
        public String getCleanDescription() {
            if (description == null || description.trim().isEmpty()) {
                return "Offline AI conversational model";
            }
            String d = description.trim();
            // If description starts with display name or model name, strip it to prevent redundant repetition
            if (displayName != null && d.toLowerCase().startsWith(displayName.toLowerCase())) {
                d = d.substring(displayName.length()).trim();
                if (d.startsWith("-") || d.startsWith(":") || d.startsWith("—") || d.startsWith("•")) {
                    d = d.substring(1).trim();
                }
            }
            // Strip generic technical filler phrases
            d = d.replaceAll("(?i)\\bInstruct model with Q4 quantization\\b", "")
                 .replaceAll("(?i)\\bwith Q4 quantization\\b", "")
                 .replaceAll("(?i)\\bInstruct model\\b", "")
                 .trim();
            if (d.startsWith("-") || d.startsWith(":") || d.startsWith("—") || d.startsWith("•")) {
                d = d.substring(1).trim();
            }
            return d.isEmpty() ? "General reasoning and conversation" : d;
        }

        /**
         * Get visual initial or brand marker for this model
         */
        public String getFamilyInitial() {
            String n = (name != null ? name : displayName != null ? displayName : "").toLowerCase();
            if (n.contains("deepseek")) return "🧠";
            if (n.contains("sarvam")) return "🇮🇳";
            if (n.contains("coder") || n.contains("code")) return "</>";
            if (n.contains("qwen")) return "Q";
            if (n.contains("gemma")) return "G";
            if (n.contains("llama")) return "🦙";
            if (n.contains("phi")) return "Φ";
            if (n.contains("mistral")) return "M";
            if (displayName != null && !displayName.isEmpty()) {
                return displayName.substring(0, 1).toUpperCase();
            }
            return "AI";
        }

        @NonNull
        @Override
        public String toString() {
            return displayName + " (" + parameterCount + ", " + getFormattedFileSize() + ")";
        }
    }

    public enum RamCompatibility {
        OPTIMAL,
        TIGHT,
        INSUFFICIENT
    }

    public static long getAvailableRamMb(Context context) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                android.app.ActivityManager.MemoryInfo memInfo = new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(memInfo);
                return memInfo.availMem / (1024 * 1024);
            }
        } catch (Exception e) {
            Log.e("ModelConfig", "Error getting available RAM", e);
        }
        return 1024;
    }

    public static RamCompatibility getRamCompatibility(Context context, Model model) {
        if (model == null) return RamCompatibility.OPTIMAL;
        long availMb = getAvailableRamMb(context);
        int reqMb = model.getEstimatedRamMb();
        if (availMb >= reqMb + 250) {
            return RamCompatibility.OPTIMAL;
        } else if (availMb >= reqMb - 150) {
            return RamCompatibility.TIGHT;
        } else {
            return RamCompatibility.INSUFFICIENT;
        }
    }
    
    // Available models for download
    private static final List<Model> AVAILABLE_MODELS = new ArrayList<>();
    
    static {
        


        // Qwen 2.5 models - Excellent performance
        AVAILABLE_MODELS.add(new Model(
            "qwen2.5-0.5b-q4",
            "Qwen 2.5 0.5B",
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            491_400_032L, // ~491MB (actual file size)
            "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "Ultra-fast & lightweight assistant for everyday chat",
            "Q4_K_M",
            "0.5B"
        ));
        
        AVAILABLE_MODELS.add(new Model(
            "qwen2.5-1.5b-q4",
            "Qwen 2.5 1.5B",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            1_117_320_736L, // ~1.1GB (actual file size)
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "Balanced daily conversational and reasoning assistant",
            "Q4_K_M",
            "1.5B"
        ));
        
        AVAILABLE_MODELS.add(new Model(
            "qwen2.5-3b-q4",
            "Qwen 2.5 3B",
            "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            2_100_000_000L, // ~2.1GB
            "qwen2.5-3b-instruct-q4_k_m.gguf",
            "Strong instruction following and general intelligence",
            "Q4_K_M",
            "3B"
        ));

        AVAILABLE_MODELS.add(new Model(
            "qwen2.5-7b-q4",
            "Qwen 2.5 7B",
            "https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-GGUF/resolve/main/Qwen2.5-7B-Instruct-Q4_K_M.gguf",
            4_724_620_928L, // ~4.4GB (actual file size)
            "Qwen2.5-7B-Instruct-Q4_K_M.gguf",
            "High-accuracy reasoning and comprehensive knowledge",
            "Q4_K_M",
            "7B"
        ));

        // Qwen 2.5 Coder models - Specialized for coding and reasoning
        AVAILABLE_MODELS.add(new Model(
            "qwen2.5-coder-0.5b-q4",
            "Qwen 2.5 Coder 0.5B",
            "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
            491_400_064L, // ~491MB (actual file size)
            "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
            "Fast on-device code assistance and syntax generation",
            "Q4_K_M",
            "0.5B"
        ));

        AVAILABLE_MODELS.add(new Model(
            "qwen2.5-coder-1.5b-q4",
            "Qwen 2.5 Coder 1.5B",
            "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            1_117_320_768L, // ~1.1GB (actual file size)
            "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            "Balanced coding assistant for algorithms and scripts",
            "Q4_K_M",
            "1.5B"
        ));

        AVAILABLE_MODELS.add(new Model(
            "qwen2.5-coder-3b-q4",
            "Qwen 2.5 Coder 3B",
            "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            2_104_932_800L, // ~2.1GB (actual file size)
            "qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            "High-accuracy code generation, review, and debugging",
            "Q4_K_M",
            "3B"
        ));

        AVAILABLE_MODELS.add(new Model(
            "qwen2.5-coder-7b-q4",
            "Qwen 2.5 Coder 7B",
            "https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct-GGUF/resolve/main/qwen2.5-coder-7b-instruct-q4_k_m.gguf",
            4_683_073_536L, // ~4.7GB (actual file size)
            "qwen2.5-coder-7b-instruct-q4_k_m.gguf",
            "Flagship open model for complex software engineering",
            "Q4_K_M",
            "7B"
        ));

        // DeepSeek-R1 Distill Qwen models - Advanced reasoning with step-by-step thinking
        AVAILABLE_MODELS.add(new Model(
            "deepseek-r1-distill-qwen-1.5b-q4",
            "DeepSeek R1 Qwen 1.5B",
            "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            1_117_320_800L, // ~1.1GB (actual file size)
            "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            "Step-by-step reasoning with explicit thought chains",
            "Q4_K_M",
            "1.5B"
        ));

        AVAILABLE_MODELS.add(new Model(
            "deepseek-r1-distill-qwen-7b-q4",
            "DeepSeek R1 Qwen 7B",
            "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-7B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf",
            4_683_073_504L, // ~4.7GB (actual file size)
            "DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf",
            "Advanced mathematical, algorithmic & logical reasoning",
            "Q4_K_M",
            "7B"
        ));

        // Llama 3.2 models
        AVAILABLE_MODELS.add(new Model(
            "llama-3.2-1b-q4",
            "Llama 3.2 1B",
            "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            800_000_000L, // ~800MB
            "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            "Meta's ultra-compact mobile assistant",
            "Q4_K_M",
            "1B"
        ));
        
        AVAILABLE_MODELS.add(new Model(
            "llama-3.2-3b-q4",
            "Llama 3.2 3B",
            "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            2_000_000_000L, // ~2GB
            "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            "Meta's efficient multilingual conversational model",
            "Q4_K_M",
            "3B"
        ));

        // Llama 3.1 models
        AVAILABLE_MODELS.add(new Model(
            "llama-3.1-8b-q4",
            "Llama 3.1 8B",
            "https://huggingface.co/bartowski/Meta-Llama-3.1-8B-Instruct-GGUF/resolve/main/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
            4_920_000_000L, // ~4.6GB (actual file size)
            "Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
            "Meta's flagship model for deep analysis and writing",
            "Q4_K_M",
            "8B"
        ));

        // Gemma 2 models
        AVAILABLE_MODELS.add(new Model(
            "gemma-2-2b-q4",
            "Gemma 2 2B",
            "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            1_717_986_592L, // ~1.6GB (actual file size)
            "gemma-2-2b-it-Q4_K_M.gguf",
            "Google's lightweight and safe conversational model",
            "Q4_K_M",
            "2B"
        ));
        
        AVAILABLE_MODELS.add(new Model(
            "gemma-2-9b-q4",
            "Gemma 2 9B",
            "https://huggingface.co/bartowski/gemma-2-9b-it-GGUF/resolve/main/gemma-2-9b-it-Q4_K_M.gguf",
            5_500_000_000L, // ~5.5GB
            "gemma-2-9b-it-Q4_K_M.gguf",
            "High-fidelity generation and deep world knowledge",
            "Q4_K_M",
            "9B"
        ));

        // Gemma 3 models - Google's next-generation multimodal open weights
        AVAILABLE_MODELS.add(new Model(
            "gemma-3-1b-q4",
            "Gemma 3 1B",
            "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
            806_058_272L, // ~806MB (actual file size)
            "gemma-3-1b-it-Q4_K_M.gguf",
            "Google's next-generation compact mobile intelligence",
            "Q4_K_M",
            "1B"
        ));

        AVAILABLE_MODELS.add(new Model(
            "gemma-3-4b-q4",
            "Gemma 3 4B",
            "https://huggingface.co/unsloth/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
            2_489_894_016L, // ~2.49GB (actual file size)
            "gemma-3-4b-it-Q4_K_M.gguf",
            "Multilingual efficiency with advanced reasoning",
            "Q4_K_M",
            "4B"
        ));

        AVAILABLE_MODELS.add(new Model(
            "gemma-3-12b-q4",
            "Gemma 3 12B",
            "https://huggingface.co/unsloth/gemma-3-12b-it-GGUF/resolve/main/gemma-3-12b-it-Q4_K_M.gguf",
            7_300_778_336L, // ~7.3GB (actual file size)
            "gemma-3-12b-it-Q4_K_M.gguf",
            "Flagship Google intelligence for high-RAM devices",
            "Q4_K_M",
            "12B"
        ));

        // CodeGemma models - Google's specialized code intelligence
        AVAILABLE_MODELS.add(new Model(
            "codegemma-2b-q4",
            "CodeGemma 2B",
            "https://huggingface.co/bartowski/codegemma-2b-GGUF/resolve/main/codegemma-2b-Q4_K_M.gguf",
            1_630_262_400L, // ~1.63GB (actual file size)
            "codegemma-2b-Q4_K_M.gguf",
            "Lightweight Google assistant specialized for code",
            "Q4_K_M",
            "2B"
        ));

        AVAILABLE_MODELS.add(new Model(
            "codegemma-7b-q4",
            "CodeGemma 7B",
            "https://huggingface.co/bartowski/codegemma-7b-it-GGUF/resolve/main/codegemma-7b-it-Q4_K_M.gguf",
            5_329_759_232L, // ~5.33GB (actual file size)
            "codegemma-7b-it-Q4_K_M.gguf",
            "Powerful code generation, refactoring, and debugging",
            "Q4_K_M",
            "7B"
        ));

        // Phi models - Microsoft's efficient models
        AVAILABLE_MODELS.add(new Model(
            "phi-3-mini-q4",
            "Phi-3 Mini",
            "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            2_361_982_464L, // ~2.2GB (actual file size)
            "Phi-3-mini-4k-instruct-Q4_K_M.gguf",
            "Microsoft's high-efficiency reasoning model",
            "Q4_K_M",
            "3.8B"
        ));



        // Mistral models
        AVAILABLE_MODELS.add(new Model(
            "mistral-7b-q4",
            "Mistral 7B",
            "https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF/resolve/main/mistral-7b-instruct-v0.2.Q4_K_M.gguf",
            4_400_000_000L, // ~4.4GB
            "mistral-7b-instruct-v0.2.Q4_K_M.gguf",
            "Versatile reasoning and natural open conversation",
            "Q4_K_M",
            "7B"
        ));

        // Code-specific models
        AVAILABLE_MODELS.add(new Model(
            "codellama-7b-q4",
            "Code Llama 7B",
            "https://huggingface.co/TheBloke/CodeLlama-7B-Instruct-GGUF/resolve/main/codellama-7b-instruct.Q4_K_M.gguf",
            4_081_004_320L, // ~3.8GB (actual file size)
            "codellama-7b-instruct.Q4_K_M.gguf",
            "Specialized code completion, translation, and review",
            "Q4_K_M",
            "7B"
        ));



        // Indian AI models - Optimized for Indian languages and cultural context
        AVAILABLE_MODELS.add(new Model(
            "sarvam-2b-q4",
            "Sarvam AI 2B (Indian)",
            "https://huggingface.co/mradermacher/sarvam-2b-v0.5-GGUF/resolve/main/sarvam-2b-v0.5.Q4_K_M.gguf",
            1_540_000_000L, // ~1.54GB
            "sarvam-2b-v0.5.Q4_K_M.gguf",
            "Optimized for 10 Indian languages and English",
            "Q4_K_M",
            "2B"
        ));

        AVAILABLE_MODELS.add(new Model(
            "sarvam-1-q4",
            "Sarvam AI 3B (Indian)",
            "https://huggingface.co/mradermacher/sarvam-1-GGUF/resolve/main/sarvam-1.Q4_K_M.gguf",
            1_550_000_000L, // ~1.55GB
            "sarvam-1.Q4_K_M.gguf",
            "Full model optimized for Indian languages and English",
            "Q4_K_M",
            "3B"
        ));
    }
    
    /**
     * Get all available models (predefined + downloaded custom models)
     */
    public static List<Model> getAvailableModels() {
        return new ArrayList<>(AVAILABLE_MODELS);
    }
    
    /**
     * Get all available models including dynamically discovered downloaded models
     * This scans the models directory for .gguf files and includes them
     * @param context Application context to access file system
     * @return Combined list of predefined and custom downloaded models
     */
    public static List<Model> getAvailableModels(Context context) {
        List<Model> allModels = new ArrayList<>(AVAILABLE_MODELS);
        
        // Get models directory
        File modelsDir = getModelsDirectory(context);
        if (!modelsDir.exists()) {
            Log.d("ModelConfig", "Models directory does not exist");
            return allModels;
        }
        
        // Get list of downloaded .gguf files
        File[] files = modelsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".gguf"));
        if (files == null || files.length == 0) {
            Log.d("ModelConfig", "No GGUF files found in models directory");
            return allModels;
        }
        
        // Keep track of filenames already in predefined models
        Set<String> predefinedFileNames = new HashSet<>();
        for (Model model : AVAILABLE_MODELS) {
            predefinedFileNames.add(model.fileName.toLowerCase());
        }
        
        // Add custom downloaded models that aren't in predefined list
        for (File file : files) {
            String fileName = file.getName();
            if (!predefinedFileNames.contains(fileName.toLowerCase())) {
                // Create a model instance for this downloaded file
                String displayName = fileName.replace(".gguf", "")
                                           .replace("-", " ")
                                           .replace("_", " ");
                // Capitalize first letter of each word
                displayName = capitalizeWords(displayName);
                
                Model customModel = Model.createLocalImportedModel(fileName, file.length());
                allModels.add(customModel);
                Log.d("ModelConfig", "Added custom model: " + customModel.displayName + " (" + fileName + ")");
            }
        }
        
        Log.d("ModelConfig", "Total models available: " + allModels.size() + 
              " (predefined: " + AVAILABLE_MODELS.size() + ", custom: " + (allModels.size() - AVAILABLE_MODELS.size()) + ")");
        
        return allModels;
    }
    
    /**
     * Helper method to capitalize words in a string
     */
    public static String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        String[] words = str.split("\\s+");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }
        
        return result.toString().trim();
    }
    
    /**
     * Get the models directory
     */
    public static File getModelsDirectory(Context context) {
        File modelsDir;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use app-specific external storage for Android 10+
            File externalFilesDir = context.getExternalFilesDir(null);
            if (externalFilesDir != null) {
                modelsDir = new File(externalFilesDir, "models");
            } else {
                // Fallback to internal storage
                modelsDir = new File(context.getFilesDir(), "models");
            }
        } else {
            // Use public Downloads directory for older Android versions
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            modelsDir = new File(downloadsDir, "MobiGPT");
        }
        
        return modelsDir;
    }
    
    /**
     * Get a model by its name (searches both predefined and custom models)
     */
    public static Model getModelByName(String name) {
        for (Model model : AVAILABLE_MODELS) {
            if (model.name.equals(name)) {
                return model;
            }
        }
        return null;
    }
    
    /**
     * Get a model by its name, including custom downloaded models
     * @param name Model name to search for
     * @param context Application context to scan for custom models
     * @return Model if found, null otherwise
     */
    public static Model getModelByName(String name, Context context) {
        // First check predefined models
        Model model = getModelByName(name);
        if (model != null) {
            return model;
        }
        
        // Search custom models
        List<Model> allModels = getAvailableModels(context);
        for (Model m : allModels) {
            if (m.name.equals(name)) {
                return m;
            }
        }
        
        return null;
    }
    
    /**
     * Get a model by its filename
     * @param fileName The filename to search for (e.g., "model.gguf")
     * @param context Application context
     * @return Model if found, null otherwise
     */
    @SuppressWarnings("unused")
    public static Model getModelByFileName(String fileName, Context context) {
        List<Model> allModels = getAvailableModels(context);
        for (Model model : allModels) {
            if (model.fileName.equalsIgnoreCase(fileName)) {
                return model;
            }
        }
        return null;
    }
    
    /**
     * Add a custom model to the available models list (for in-progress downloads)
     * This allows the model to appear in the spinner even while downloading
     * @param model The custom model to add
     */
    public static synchronized void addCustomModel(Model model) {
        if (model == null || !model.isCustomUrl) {
            Log.w("ModelConfig", "Cannot add null or non-custom model");
            return;
        }
        
        // Check if model already exists
        for (Model existing : AVAILABLE_MODELS) {
            if (existing.name.equals(model.name)) {
                Log.d("ModelConfig", "Model already exists in list: " + model.name);
                return;
            }
        }
        
        AVAILABLE_MODELS.add(model);
        Log.d("ModelConfig", "Added custom model to available models: " + model.displayName);
    }
    
    /**
     * Remove a custom model from the available models list
     * @param modelName The name of the model to remove
     */
    public static synchronized void removeCustomModel(String modelName) {
        AVAILABLE_MODELS.removeIf(model -> model.isCustomUrl && model.name.equals(modelName));
        Log.d("ModelConfig", "Removed custom model: " + modelName);
    }
    
    /**
     * Save custom model to persistent storage (SharedPreferences)
     * @param context Application context
     * @param model Custom model to save
     */
    public static void saveCustomModel(Context context, Model model) {
        if (model == null || !model.isCustomUrl) {
            Log.w("ModelConfig", "Cannot save null or non-custom model");
            return;
        }
        
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("custom_models", Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            
            // Save model details with model name as key prefix
            String prefix = "model_" + model.name + "_";
            editor.putString(prefix + "displayName", model.displayName);
            editor.putString(prefix + "downloadUrl", model.downloadUrl != null ? model.downloadUrl : "");
            editor.putString(prefix + "fileName", model.fileName);
            editor.putLong(prefix + "fileSizeBytes", model.fileSizeBytes);
            editor.putString(prefix + "quantization", model.quantization != null ? model.quantization : "Unknown");
            editor.putString(prefix + "parameterCount", model.parameterCount != null ? model.parameterCount : "Unknown");
            editor.putString(prefix + "description", model.description != null ? model.description : "Custom model");
            editor.putLong(prefix + "timestamp", System.currentTimeMillis());
            
            // Add to list of custom model names
            Set<String> customModelNames = prefs.getStringSet("custom_model_names", new HashSet<>());
            Set<String> updatedNames = new HashSet<>(customModelNames);
            updatedNames.add(model.name);
            editor.putStringSet("custom_model_names", updatedNames);
            
            editor.apply();
            Log.d("ModelConfig", "Saved custom model to preferences: " + model.displayName + 
                  " (size: " + (model.fileSizeBytes > 0 ? model.fileSizeBytes : "unknown") + ")");
        } catch (Exception e) {
            Log.e("ModelConfig", "Error saving custom model", e);
        }
    }
    
    /**
     * Load all custom models from persistent storage
     * @param context Application context
     * @return List of custom models
     */
    public static List<Model> loadCustomModels(Context context) {
        List<Model> customModels = new ArrayList<>();
        
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("custom_models", Context.MODE_PRIVATE);
            Set<String> customModelNames = prefs.getStringSet("custom_model_names", new HashSet<>());
            
            for (String modelName : customModelNames) {
                String prefix = "model_" + modelName + "_";
                String displayName = prefs.getString(prefix + "displayName", null);
                String downloadUrl = prefs.getString(prefix + "downloadUrl", "");
                String fileName = prefs.getString(prefix + "fileName", null);
                long fileSizeBytes = prefs.getLong(prefix + "fileSizeBytes", -1L);
                String quantization = prefs.getString(prefix + "quantization", "Unknown");
                String parameterCount = prefs.getString(prefix + "parameterCount", "Unknown");
                String description = prefs.getString(prefix + "description", "Custom model");
                
                if (displayName != null && fileName != null) {
                    Model model = new Model(
                        modelName,
                        displayName,
                        downloadUrl,
                        fileSizeBytes,
                        fileName,
                        description,
                        quantization,
                        parameterCount,
                        true
                    );
                    customModels.add(model);
                    Log.d("ModelConfig", "Loaded custom model from preferences: " + displayName + 
                          " (size: " + (fileSizeBytes > 0 ? fileSizeBytes : "unknown") + ")");
                }
            }
        } catch (Exception e) {
            Log.e("ModelConfig", "Error loading custom models", e);
        }
        
        return customModels;
    }
    
    /**
     * Restore custom models to the available models list on app start
     * @param context Application context
     */
    public static synchronized void restoreCustomModels(Context context) {
        List<Model> customModels = loadCustomModels(context);
        
        for (Model model : customModels) {
            // Only add if not already in list
            boolean exists = false;
            for (Model existing : AVAILABLE_MODELS) {
                if (existing.name.equals(model.name)) {
                    exists = true;
                    break;
                }
            }
            
            if (!exists) {
                AVAILABLE_MODELS.add(model);
                Log.d("ModelConfig", "Restored custom model: " + model.displayName);
            }
        }
    }
    
    /**
     * Delete custom model from persistent storage
     * @param context Application context
     * @param modelName Model name to delete
     */
    public static void deleteCustomModel(Context context, String modelName) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("custom_models", Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            
            // Remove model details
            String prefix = "model_" + modelName + "_";
            editor.remove(prefix + "displayName");
            editor.remove(prefix + "downloadUrl");
            editor.remove(prefix + "fileName");
            editor.remove(prefix + "fileSizeBytes");
            editor.remove(prefix + "timestamp");
            
            // Remove from list of custom model names
            Set<String> customModelNames = prefs.getStringSet("custom_model_names", new HashSet<>());
            Set<String> updatedNames = new HashSet<>(customModelNames);
            updatedNames.remove(modelName);
            editor.putStringSet("custom_model_names", updatedNames);
            
            editor.apply();
            Log.d("ModelConfig", "Deleted custom model from preferences: " + modelName);
        } catch (Exception e) {
            Log.e("ModelConfig", "Error deleting custom model", e);
        }
    }
    
    /**
     * Update the file size of a custom model in persistent storage
     * Called after Content-Length is retrieved from server
     * @param context Application context
     * @param modelName Model name
     * @param fileSizeBytes File size in bytes
     */
    public static void updateCustomModelSize(Context context, String modelName, long fileSizeBytes) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("custom_models", Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            
            String prefix = "model_" + modelName + "_";
            editor.putLong(prefix + "fileSizeBytes", fileSizeBytes);
            editor.apply();
            
            Log.d("ModelConfig", "Updated file size for custom model " + modelName + ": " + fileSizeBytes + " bytes");
        } catch (Exception e) {
            Log.e("ModelConfig", "Error updating custom model size", e);
        }
    }
    
    /**
     * Update a custom model in the available models list with new file size
     * @param modelName Model name
     * @param fileSizeBytes New file size in bytes
     * @return Updated model, or null if not found
     */
    @SuppressWarnings("UnusedReturnValue")
    public static synchronized Model updateCustomModelInList(String modelName, long fileSizeBytes) {
        for (int i = 0; i < AVAILABLE_MODELS.size(); i++) {
            Model model = AVAILABLE_MODELS.get(i);
            if (model.isCustomUrl && model.name.equals(modelName)) {
                // Create new model instance with updated size, preserving extracted metadata
                Model updatedModel = new Model(
                    model.name,
                    model.displayName,
                    model.downloadUrl,
                    fileSizeBytes,
                    model.fileName,
                    model.description,
                    model.quantization,
                    model.parameterCount,
                    true
                );
                
                // Replace in list
                AVAILABLE_MODELS.set(i, updatedModel);
                Log.d("ModelConfig", "Updated model in list: " + modelName + " with size " + fileSizeBytes);
                return updatedModel;
            }
        }
        return null;
    }
}