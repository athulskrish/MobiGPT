package com.keralatechreach.mobigpt.ai

import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * MobiGPT LLaMA Android Integration
 * Based on the proven llama.cpp Android example implementation
 */
class LlamaEngine {
    private val tag: String? = this::class.simpleName

    private val threadLocalState: ThreadLocal<State> = ThreadLocal.withInitial { State.Idle }
    
    @Volatile
    private var isNativeInitialized = false
    private val initializationLock = Any()

    private val runLoop: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor {
            thread(start = false, name = "MobiGPT-LLaMA-Thread") {
                Log.d(tag, "Dedicated thread for native code: ${Thread.currentThread().name}")

                // Initialize native library only when actually needed
                initializeNativeLibrary()

                it.run()
            }.apply {
                uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, exception: Throwable ->
                    Log.e(tag, "Unhandled exception", exception)
                }
            }
        }.asCoroutineDispatcher()
    }

    private val nlen: Int = 1024  // Max output tokens - leaving room for prompt in 4096 context

    // Native method declarations - matching the proven llama-android.cpp implementation
    private external fun log_to_android()
    private external fun load_model(filename: String): Long
    private external fun free_model(model: Long)
    private external fun new_context(model: Long, nThreads: Int, nThreadsBatch: Int): Long
    private external fun free_context(context: Long)
    private external fun backend_init(numa: Boolean)
    private external fun new_batch(nTokens: Int, embd: Int, nSeqMax: Int): Long
    private external fun free_batch(batch: Long)
    private external fun new_sampler(): Long
    private external fun free_sampler(sampler: Long)

    private external fun system_info(): String

    private external fun completion_init(
        context: Long,
        batch: Long,
        text: String,
        formatChat: Boolean,
        nLen: Int
    ): Int

    private external fun completion_loop(
        context: Long,
        batch: Long,
        sampler: Long,
        nLen: Int,
        ncur: IntVar
    ): String?

    private external fun kv_cache_clear(context: Long)

    /**
     * Initialize native library with proper error handling
     */
    private fun initializeNativeLibrary() {
        synchronized(initializationLock) {
            if (isNativeInitialized) {
                return
            }
            
            try {
                Log.d(tag, "Loading native library: mobigpt-llama")
                System.loadLibrary("mobigpt-llama")
                
                Log.d(tag, "Setting up llama log handler")
                log_to_android()
                
                Log.d(tag, "Initializing backend")
                backend_init(false)
                
                Log.d(tag, system_info())
                
                isNativeInitialized = true
                Log.i(tag, "Native library initialized successfully")
                
            } catch (e: UnsatisfiedLinkError) {
                Log.e(tag, "Failed to load native library", e)
                throw RuntimeException("Failed to load native library: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize native library", e)
                throw RuntimeException("Failed to initialize native library: ${e.message}", e)
            }
        }
    }

    /**
     * Load a GGUF model from the specified path with threading configuration
     * @param config Model configuration including path and threading parameters
     */
    suspend fun loadModel(config: LlamaInferenceConfig) {
        withContext(runLoop) {
            when (threadLocalState.get()) {
                is State.Idle -> {
                    Log.i(tag, "Loading model: ${config.path}")
                    Log.i(tag, "Thread config - Inference: ${config.getResolvedInferenceThreads()}, " +
                             "Batch: ${config.getResolvedBatchThreads()}, Context: ${config.contextSize}")
                    
                    val model = load_model(config.path)
                    if (model == 0L) throw IllegalStateException("load_model() failed")

                    val context = new_context(
                        model, 
                        config.getResolvedInferenceThreads(),
                        config.getResolvedBatchThreads()
                    )
                    if (context == 0L) throw IllegalStateException("new_context() failed")

                    val batch = new_batch(512, 0, 1)
                    if (batch == 0L) throw IllegalStateException("new_batch() failed")

                    val sampler = new_sampler()
                    if (sampler == 0L) throw IllegalStateException("new_sampler() failed")

                    Log.i(tag, "Model loaded successfully: ${config.path}")
                    threadLocalState.set(State.Loaded(model, context, batch, sampler))
                }
                else -> throw IllegalStateException("Model already loaded")
            }
        }
    }

    /**
     * Load a GGUF model from the specified path (legacy method - uses auto-threading)
     * @param pathToModel Path to the GGUF model file
     */
    suspend fun loadModel(pathToModel: String) {
        loadModel(LlamaInferenceConfig.default(pathToModel))
    }

    /**
     * Generate streaming response for a message
     * @param message Input prompt/message
     * @param formatChat Whether to format as chat (true) or completion (false)
     * @return Flow of generated tokens
     */
    fun sendMessage(message: String, formatChat: Boolean = true): Flow<String> = flow {
        when (val state = threadLocalState.get()) {
            is State.Loaded -> {
                Log.d(tag, "Generating response (prompt length: ${message.length})")
                
                val ncur = IntVar(completion_init(state.context, state.batch, message, formatChat, nlen))
                while (ncur.value <= nlen) {
                    val token = completion_loop(state.context, state.batch, state.sampler, nlen, ncur)
                    if (token == null) {
                        break
                    }
                    if (token.isNotEmpty()) {
                        emit(token)
                    }
                }
                kv_cache_clear(state.context)
                
                Log.d(tag, "Response generation completed")
            }
            else -> {
                Log.e(tag, "No model loaded for message generation")
                emit("Error: No model loaded")
            }
        }
    }.flowOn(runLoop)

    /**
     * Check if a model is currently loaded
     * This method ensures thread-safe access to the model state
     */
    suspend fun isModelLoaded(): Boolean {
        return withContext(runLoop) {
            when (threadLocalState.get()) {
                is State.Loaded -> true
                else -> false
            }
        }
    }

    /**
     * Clear the KV cache to reset model state
     * This should be called after cancelling generation or before starting new inference
     */
    suspend fun clearCache() {
        withContext(runLoop) {
            when (val state = threadLocalState.get()) {
                is State.Loaded -> {
                    Log.d(tag, "Clearing KV cache")
                    kv_cache_clear(state.context)
                }
                else -> {
                    Log.w(tag, "Cannot clear cache - no model loaded")
                }
            }
        }
    }

    /**
     * Unload the current model and free resources
     */
    suspend fun unloadModel() {
        withContext(runLoop) {
            when (val state = threadLocalState.get()) {
                is State.Loaded -> {
                    Log.i(tag, "Unloading model...")
                    
                    free_context(state.context)
                    free_model(state.model)
                    free_batch(state.batch)
                    free_sampler(state.sampler)

                    threadLocalState.set(State.Idle)
                    Log.i(tag, "Model unloaded successfully")
                }
                else -> {
                    Log.d(tag, "No model to unload")
                }
            }
        }
    }

    companion object {
        /**
         * Thread-safe integer variable for native code
         */
        @Keep
        class IntVar(@Volatile @get:Keep var value: Int) {
            @Keep
            fun inc() {
                synchronized(this) {
                    value += 1
                }
            }
        }

        /**
         * State management for the LLaMA engine
         */
        private sealed interface State {
            data object Idle: State
            data class Loaded(val model: Long, val context: Long, val batch: Long, val sampler: Long): State
        }

        // Singleton instance for the engine
        private val _instance: LlamaEngine = LlamaEngine()

        /**
         * Get the singleton instance of LlamaEngine
         */
        fun getInstance(): LlamaEngine = _instance
    }
}