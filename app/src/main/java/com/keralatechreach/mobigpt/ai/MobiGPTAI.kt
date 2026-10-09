package com.keralatechreach.mobigpt.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

/**
 * Helper class to integrate LlamaEngine with MobiGPT's existing chat system
 */
class MobiGPTAI private constructor() {
    private val tag = "MobiGPTAI"
    private val llamaEngine = LlamaEngine.getInstance()
    private var coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentModelPath: String? = null
    private var currentGenerationJob: Job? = null
    private var currentPartialResponse: StringBuilder? = null
    
    companion object {
        @Volatile
        private var INSTANCE: MobiGPTAI? = null

        fun getInstance(): MobiGPTAI {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MobiGPTAI().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Ensure coroutineScope is active, recreate if cancelled
     * This is crucial for handling activity recreations (e.g., theme changes)
     */
    @Synchronized
    private fun ensureCoroutineScopeActive() {
        if (!coroutineScope.isActive) {
            Log.w(tag, "CoroutineScope was cancelled, recreating it")
            coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }
    }
    
    /**
     * Progress callback for model loading
     */
    interface ModelLoadProgressCallback {
        fun onProgress(progress: Int, status: String)
        fun onComplete(success: Boolean)
    }
    
    /**
     * Load a model from a direct file path with progress reporting (asynchronous)
     * @param modelPath Full path to the model file
     * @param progressCallback Optional callback for progress updates
     * @param callback Completion callback with success status
     */
    fun loadModelWithProgress(modelPath: String, progressCallback: ModelLoadProgressCallback? = null) {
        ensureCoroutineScopeActive()
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    Log.e(tag, "Model file not found: $modelPath")
                    withContext(Dispatchers.Main) {
                        progressCallback?.onComplete(false)
                    }
                    return@launch
                }
                
                // Check if the same model is already loaded
                val currentlyLoaded = llamaEngine.isModelLoaded()
                Log.d(tag, "loadModelWithProgress check - currentPath: $currentModelPath, newPath: $modelPath, isLoaded: $currentlyLoaded")
                
                if (currentModelPath == modelPath && currentlyLoaded) {
                    Log.i(tag, "Model already loaded, skipping reload: $modelPath")
                    withContext(Dispatchers.Main) {
                        progressCallback?.onProgress(100, "Model already loaded")
                        progressCallback?.onComplete(true)
                    }
                    return@launch
                }
                
                // If a different model is loaded, unload it first
                if (currentlyLoaded && currentModelPath != modelPath) {
                    Log.i(tag, "Unloading current model ($currentModelPath) before loading new one ($modelPath)")
                    withContext(Dispatchers.Main) {
                        progressCallback?.onProgress(5, "Unloading previous model...")
                    }
                    llamaEngine.unloadModel()
                    currentModelPath = null
                }
                
                // Simulate realistic loading progress
                withContext(Dispatchers.Main) {
                    progressCallback?.onProgress(10, "Initializing model loading...")
                }
                delay(200)
                
                withContext(Dispatchers.Main) {
                    progressCallback?.onProgress(20, "Reading model file...")
                }
                delay(300)
                
                withContext(Dispatchers.Main) {
                    progressCallback?.onProgress(35, "Loading model weights...")
                }
                
                Log.i(tag, "Loading model: $modelPath")
                // Load the model
                try {
                    llamaEngine.loadModel(modelPath)
                    Log.i(tag, "Model loading call completed: $modelPath")
                    
                    withContext(Dispatchers.Main) {
                        progressCallback?.onProgress(70, "Initializing context...")
                    }
                    delay(200)
                    
                    // Verify the model is actually loaded
                    val isLoaded = llamaEngine.isModelLoaded()
                    Log.i(tag, "Model loading verification - isModelLoaded: $isLoaded")
                    
                    withContext(Dispatchers.Main) {
                        progressCallback?.onProgress(85, "Preparing inference engine...")
                    }
                    delay(200)
                    
                    if (isLoaded) {
                        currentModelPath = modelPath
                        Log.i(tag, "Model loaded successfully: $modelPath")
                        withContext(Dispatchers.Main) {
                            progressCallback?.onProgress(100, "Model ready!")
                            progressCallback?.onComplete(true)
                        }
                    } else {
                        Log.e(tag, "Model loading failed - engine reports not loaded: $modelPath")
                        currentModelPath = null
                        withContext(Dispatchers.Main) {
                            progressCallback?.onComplete(false)
                        }
                    }
                } catch (e: IllegalStateException) {
                    // Handle the case where model is already loaded in engine but we lost track
                    if (e.message?.contains("Model already loaded") == true) {
                        Log.w(tag, "Engine reports model already loaded - syncing state")
                        currentModelPath = modelPath
                        val isLoaded = llamaEngine.isModelLoaded()
                        if (isLoaded) {
                            Log.i(tag, "Model state recovered: $modelPath")
                            withContext(Dispatchers.Main) {
                                progressCallback?.onProgress(100, "Model ready!")
                                progressCallback?.onComplete(true)
                            }
                        } else {
                            Log.e(tag, "Model state inconsistent after recovery attempt")
                            currentModelPath = null
                            withContext(Dispatchers.Main) {
                                progressCallback?.onComplete(false)
                            }
                        }
                    } else {
                        throw e
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to load model: $modelPath (${e.message})", e)
                currentModelPath = null
                withContext(Dispatchers.Main) {
                    progressCallback?.onComplete(false)
                }
            }
        }
    }
    
    /**
     * Generate AI response for user message
     * @param userMessage User's input message
     * @param onTokenReceived Callback for each token received (for streaming)
     * @param onComplete Callback when generation is complete
     * @param onError Callback for errors
     * @param onStopped Callback when generation is stopped with partial response
     */
    fun generateResponse(
        userMessage: String,
        onTokenReceived: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
        onStopped: ((String) -> Unit)? = null
    ) {
        // Cancel any existing generation
        currentGenerationJob?.cancel()
        
        ensureCoroutineScopeActive()
        currentGenerationJob = coroutineScope.launch {
            try {
                // Check both the engine state and our internal state with proper verification
                val engineLoaded = llamaEngine.isModelLoaded()
                val hasCurrentPath = currentModelPath != null
                
                Log.d(tag, "Model state check - engineLoaded: $engineLoaded, hasCurrentPath: $hasCurrentPath, currentPath: $currentModelPath")
                
                if (!engineLoaded || !hasCurrentPath) {
                    val errorMsg = "No model loaded. Engine state: $engineLoaded, Current path: $currentModelPath"
                    Log.e(tag, errorMsg)
                    onError(errorMsg)
                    return@launch
                }
                
                val fullResponse = StringBuilder()
                currentPartialResponse = fullResponse  // Track partial response
                
                // Format the message as a chat prompt
                val chatPrompt = formatChatPrompt(userMessage)
                
                Log.d(tag, "Generating response (message length: ${userMessage.length})")
                
                llamaEngine.sendMessage(chatPrompt, formatChat = true).collect { token ->
                    fullResponse.append(token)
                    onTokenReceived(token)
                }
                
                val finalResponse = fullResponse.toString().trim()
                currentPartialResponse = null  // Clear tracking
                onComplete(finalResponse)
                
                Log.d(tag, "Response generated successfully")
                currentGenerationJob = null
                
            } catch (e: Exception) {
                currentGenerationJob = null
                if (e is CancellationException) {
                    Log.d(tag, "Response generation cancelled")
                    // Get partial response before clearing
                    val partialText = currentPartialResponse?.toString()?.trim() ?: ""
                    currentPartialResponse = null
                    
                    if (partialText.isNotEmpty()) {
                        // Call stopped callback if provided, otherwise treat as complete
                        if (onStopped != null) {
                            onStopped(partialText)
                        } else {
                            onComplete(partialText)
                        }
                    } else {
                        onError("Generation stopped by user")
                    }
                } else {
                    currentPartialResponse = null
                    Log.e(tag, "Error generating response", e)
                    onError("Error generating response: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Stop the current inference/generation
     */
    fun stopGeneration() {
        Log.d(tag, "Stopping current generation")
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        
        // Clear the KV cache to prevent gibberish on next prompt
        ensureCoroutineScopeActive()
        coroutineScope.launch {
            try {
                llamaEngine.clearCache()
                Log.d(tag, "KV cache cleared after stopping generation")
            } catch (e: Exception) {
                Log.e(tag, "Error clearing cache after stop", e)
            }
        }
    }
    
    /**
     * Check if generation is currently in progress
     */
    fun isGenerating(): Boolean {
        return currentGenerationJob?.isActive == true
    }
    
    /**
     * Java-compatible interface for response callbacks
     */
    interface ResponseCallback {
        fun onTokenReceived(token: String)
        fun onComplete(response: String)
        fun onError(error: String)
        fun onStopped(partialResponse: String) {
            // Default implementation treats stopped as complete
            onComplete(partialResponse)
        }
    }
    
    /**
     * Generate AI response with Java-compatible callback interface
     * This method is designed for easy calling from Java code
     * @param userMessage User's input message
     * @param callback Response callback interface
     */
    fun generateResponseJava(userMessage: String, callback: ResponseCallback) {
        generateResponse(
            userMessage,
            { token -> callback.onTokenReceived(token) },
            { response -> callback.onComplete(response) },
            { error -> callback.onError(error) }
        )
    }
    
    /**
     * Format user message as a chat prompt based on model type
     */
    private fun formatChatPrompt(userMessage: String): String {
        // Detect model type from current model path
        val modelName = currentModelPath?.lowercase() ?: ""
        
        return when {
            // Sarvam AI models - use simple format without special tokens
            "sarvam" in modelName -> {
                """System: You are MobiGPT, a helpful AI assistant running locally on an Android device. Always provide brief, direct answers in the user's language. Keep responses under 3 sentences when possible.

User: $userMessage""".trimIndent()
            }
            // Default ChatML format for other models (Qwen, Llama, Phi, etc.)
            else -> {
                """
            <|im_start|>system
            You are MobiGPT, a helpful AI assistant running locally on an Android device. Always provide brief, direct answers. Keep responses under 3 sentences when possible. Avoid unnecessary explanations, elaborations, or repetition.
            <|im_end|>
            <|im_start|>user
            $userMessage
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
            }
        }
    }
    
    /**
     * Check if a model is currently loaded
     */
    fun isModelLoaded(): Boolean {
        return try {
            // Check both our internal state and the engine state
            val hasCurrentPath = currentModelPath != null
            // Use runBlocking to properly access the suspend isModelLoaded method
            val engineLoaded = runBlocking { llamaEngine.isModelLoaded() }
            
            val result = hasCurrentPath && engineLoaded
            Log.d(tag, "isModelLoaded check - hasCurrentPath: $hasCurrentPath, engineLoaded: $engineLoaded, result: $result")
            
            result
        } catch (e: Exception) {
            Log.e(tag, "Error checking model loading status", e)
            false
        }
    }
    
    /**
     * Unload the current model
     */
    fun unloadModel(callback: () -> Unit) {
        ensureCoroutineScopeActive()
        coroutineScope.launch {
            try {
                llamaEngine.unloadModel()
                currentModelPath = null
                callback()
                Log.i(tag, "Model unloaded")
            } catch (e: Exception) {
                Log.e(tag, "Failed to unload model", e)
                currentModelPath = null
                callback() // Still call callback even if unload fails
            }
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        currentPartialResponse = null
        // Unload model synchronously to prevent native memory leak
        try {
            runBlocking {
                llamaEngine.unloadModel()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during cleanup", e)
        }
        currentModelPath = null
        coroutineScope.cancel()
    }
}
