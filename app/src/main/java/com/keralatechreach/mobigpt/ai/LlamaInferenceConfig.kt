package com.keralatechreach.mobigpt.ai

/**
 * Configuration for loading and running a GGUF model with multi-threading support
 */
data class LlamaInferenceConfig(
    /**
     * Path to the GGUF model file
     */
    val path: String,
    
    /**
     * Number of threads for inference operations
     * - 0: Auto-detect optimal thread count (CPU_COUNT - 2)
     * - 1: Single-threaded (slowest but lowest CPU usage)
     * - 2-8: Multi-threaded (faster inference)
     * 
     * Recommended: 0 for auto-detection, which adapts to device capabilities
     */
    val inferenceThreads: Int = 0,
    
    /**
     * Number of threads for batch processing operations
     * - 0: Same as inferenceThreads
     * - 1-8: Custom thread count for batch operations
     * 
     * Recommended: 0 to match inference threads
     */
    val batchThreads: Int = 0,
    
    /**
     * Context size (number of tokens in context window)
     * Larger context = more memory usage but can handle longer conversations
     * 
     * Recommended values:
     * - 2048: Low memory devices
     * - 4096: Standard (default)
     * - 8192: High memory devices with large models
     */
    val contextSize: Int = 4096
) {
    
    /**
     * Calculate the actual thread count to use for inference
     * @return The resolved thread count (never 0)
     */
    fun getResolvedInferenceThreads(): Int {
        return if (inferenceThreads <= 0) {
            getOptimalThreadCount()
        } else {
            inferenceThreads.coerceIn(1, 8)
        }
    }
    
    /**
     * Calculate the actual thread count to use for batch processing
     * @return The resolved thread count (never 0)
     */
    fun getResolvedBatchThreads(): Int {
        return if (batchThreads <= 0) {
            getResolvedInferenceThreads()
        } else {
            batchThreads.coerceIn(1, 8)
        }
    }
    
    companion object {
        /**
         * Get optimal thread count based on available CPU cores
         * Formula: max(1, min(8, CPU_COUNT - 2))
         * 
         * This reserves 2 cores for Android system and UI, preventing lag
         */
        fun getOptimalThreadCount(): Int {
            val cpuCount = Runtime.getRuntime().availableProcessors()
            return maxOf(1, minOf(8, cpuCount - 2))
        }
        
        /**
         * Create a default configuration with auto-detected threading
         */
        fun default(modelPath: String): LlamaInferenceConfig {
            return LlamaInferenceConfig(
                path = modelPath,
                inferenceThreads = 0,  // Auto-detect
                batchThreads = 0,      // Match inference threads
                contextSize = 4096
            )
        }
        
    }
    
    override fun toString(): String {
        return "LlamaInferenceConfig(path='$path', inferenceThreads=${getResolvedInferenceThreads()}, " +
               "batchThreads=${getResolvedBatchThreads()}, contextSize=$contextSize)"
    }
}
