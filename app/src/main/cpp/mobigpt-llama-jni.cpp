#include <android/log.h>
#include <jni.h>
#include <string>
#include "llama.h"
#include "common.h"

// Forward declarations for the original llama-android.cpp functions
extern "C" {
    // Original functions from llama-android.cpp with android.llama.cpp.LLamaAndroid package
    JNIEXPORT jlong JNICALL Java_android_llama_cpp_LLamaAndroid_load_1model(JNIEnv *env, jobject thiz, jstring filename);
    JNIEXPORT void JNICALL Java_android_llama_cpp_LLamaAndroid_free_1model(JNIEnv *env, jobject thiz, jlong model);
    JNIEXPORT jlong JNICALL Java_android_llama_cpp_LLamaAndroid_new_1context(JNIEnv *env, jobject thiz, jlong model, jint n_threads, jint n_threads_batch);
    JNIEXPORT void JNICALL Java_android_llama_cpp_LLamaAndroid_free_1context(JNIEnv *env, jobject thiz, jlong context);
    JNIEXPORT void JNICALL Java_android_llama_cpp_LLamaAndroid_backend_1init(JNIEnv *env, jobject thiz, jboolean numa);
    JNIEXPORT void JNICALL Java_android_llama_cpp_LLamaAndroid_backend_1free(JNIEnv *env, jobject thiz);
    JNIEXPORT jstring JNICALL Java_android_llama_cpp_LLamaAndroid_system_1info(JNIEnv *env, jobject thiz);
    JNIEXPORT jlong JNICALL Java_android_llama_cpp_LLamaAndroid_new_1batch(JNIEnv *env, jobject thiz, jint n_tokens, jint embd, jint n_seq_max);
    JNIEXPORT void JNICALL Java_android_llama_cpp_LLamaAndroid_free_1batch(JNIEnv *env, jobject thiz, jlong batch);
    JNIEXPORT jlong JNICALL Java_android_llama_cpp_LLamaAndroid_new_1sampler(JNIEnv *env, jobject thiz);
    JNIEXPORT void JNICALL Java_android_llama_cpp_LLamaAndroid_free_1sampler(JNIEnv *env, jobject thiz, jlong sampler);
    JNIEXPORT jstring JNICALL Java_android_llama_cpp_LLamaAndroid_bench_1model(JNIEnv *env, jobject thiz, jlong context, jlong model, jlong batch, jint pp, jint tg, jint pl, jint nr);
    JNIEXPORT jint JNICALL Java_android_llama_cpp_LLamaAndroid_completion_1init(JNIEnv *env, jobject thiz, jlong context, jlong batch, jstring text, jboolean format_chat, jint n_len);
    JNIEXPORT jstring JNICALL Java_android_llama_cpp_LLamaAndroid_completion_1loop(JNIEnv *env, jobject thiz, jlong context, jlong batch, jlong sampler, jint n_len, jobject intvar_ncur);
    JNIEXPORT void JNICALL Java_android_llama_cpp_LLamaAndroid_kv_1cache_1clear(JNIEnv *env, jobject thiz, jlong context);
}

#define TAG "mobigpt-llama-jni.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Log callback for GGML
static void log_callback(ggml_log_level level, const char * fmt, void * data) {
    if (level == GGML_LOG_LEVEL_ERROR)     __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, data);
    else if (level == GGML_LOG_LEVEL_INFO) __android_log_print(ANDROID_LOG_INFO, TAG, fmt, data);
    else if (level == GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, TAG, fmt, data);
    else __android_log_print(ANDROID_LOG_DEFAULT, TAG, fmt, data);
}

// Wrapper functions for our package: com.keralatechreach.mobigpt.ai.LlamaEngine

extern "C"
JNIEXPORT void JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_log_1to_1android(JNIEnv *env, jobject thiz) {
    LOGi("Setting up GGML log callback for Android");
    llama_log_set(log_callback, nullptr);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_load_1model(JNIEnv *env, jobject thiz, jstring filename) {
    return Java_android_llama_cpp_LLamaAndroid_load_1model(env, thiz, filename);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_free_1model(JNIEnv *env, jobject thiz, jlong model) {
    Java_android_llama_cpp_LLamaAndroid_free_1model(env, thiz, model);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_new_1context(JNIEnv *env, jobject thiz, jlong model, jint n_threads, jint n_threads_batch) {
    return Java_android_llama_cpp_LLamaAndroid_new_1context(env, thiz, model, n_threads, n_threads_batch);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_free_1context(JNIEnv *env, jobject thiz, jlong context) {
    Java_android_llama_cpp_LLamaAndroid_free_1context(env, thiz, context);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_backend_1init(JNIEnv *env, jobject thiz, jboolean numa) {
    Java_android_llama_cpp_LLamaAndroid_backend_1init(env, thiz, numa);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_system_1info(JNIEnv *env, jobject thiz) {
    return Java_android_llama_cpp_LLamaAndroid_system_1info(env, thiz);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_new_1batch(JNIEnv *env, jobject thiz, jint n_tokens, jint embd, jint n_seq_max) {
    return Java_android_llama_cpp_LLamaAndroid_new_1batch(env, thiz, n_tokens, embd, n_seq_max);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_free_1batch(JNIEnv *env, jobject thiz, jlong batch) {
    Java_android_llama_cpp_LLamaAndroid_free_1batch(env, thiz, batch);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_new_1sampler(JNIEnv *env, jobject thiz) {
    return Java_android_llama_cpp_LLamaAndroid_new_1sampler(env, thiz);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_free_1sampler(JNIEnv *env, jobject thiz, jlong sampler) {
    Java_android_llama_cpp_LLamaAndroid_free_1sampler(env, thiz, sampler);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_completion_1init(JNIEnv *env, jobject thiz, jlong context, jlong batch, jstring text, jboolean format_chat, jint n_len) {
    return Java_android_llama_cpp_LLamaAndroid_completion_1init(env, thiz, context, batch, text, format_chat, n_len);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_completion_1loop(JNIEnv *env, jobject thiz, jlong context, jlong batch, jlong sampler, jint n_len, jobject intvar_ncur) {
    return Java_android_llama_cpp_LLamaAndroid_completion_1loop(env, thiz, context, batch, sampler, n_len, intvar_ncur);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_keralatechreach_mobigpt_ai_LlamaEngine_kv_1cache_1clear(JNIEnv *env, jobject thiz, jlong context) {
    Java_android_llama_cpp_LLamaAndroid_kv_1cache_1clear(env, thiz, context);
}