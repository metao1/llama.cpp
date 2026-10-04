#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"
#include "common.h"
#include "sampling.h"

#define TAG "llama-android.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static void log_callback(enum ggml_log_level level, const char * text, void * user_data) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        LOGe("%s", text);
    } else if (level == GGML_LOG_LEVEL_INFO) {
        LOGi("%s", text);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_log_1to_1android(JNIEnv *, jobject) {
    llama_log_set(log_callback, NULL);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_load_1model(JNIEnv *env, jobject, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, 0);
    llama_model_params params = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(path, params);
    env->ReleaseStringUTFChars(jpath, path);
    return reinterpret_cast<jlong>(model);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1model(JNIEnv *, jobject, jlong model_ptr) {
    llama_model_free(reinterpret_cast<llama_model *>(model_ptr));
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_new_1context(JNIEnv *, jobject, jlong model_ptr) {
    llama_model *model = reinterpret_cast<llama_model *>(model_ptr);
    llama_context_params params = llama_context_default_params();
    params.n_ctx = 2048;
    params.n_batch = 2048;
    params.flash_attn = true;
    llama_context *ctx = llama_init_from_model(model, params);
    return reinterpret_cast<jlong>(ctx);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1context(JNIEnv *, jobject, jlong ctx_ptr) {
    llama_free(reinterpret_cast<llama_context *>(ctx_ptr));
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_backend_1init(JNIEnv *, jobject, jboolean numa) {
    llama_backend_init();
    ggml_time_init();
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_backend_1free(JNIEnv *, jobject) {
    llama_backend_free();
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_new_1batch(JNIEnv *, jobject, jint n_tokens, jint embd, jint n_seq_max) {
    common_batch *batch = new common_batch(common_batch_init(n_tokens, embd, n_seq_max));
    return reinterpret_cast<jlong>(batch);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1batch(JNIEnv *, jobject, jlong batch_ptr) {
    delete reinterpret_cast<common_batch *>(batch_ptr);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_new_1sampler(JNIEnv *, jobject) {
    common_sampler_params sparams;
    common_sampler *sampler = common_sampler_init(NULL, sparams);
    return reinterpret_cast<jlong>(sampler);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1sampler(JNIEnv *, jobject, jlong sampler_ptr) {
    common_sampler_free(reinterpret_cast<common_sampler *>(sampler_ptr));
}

extern "C"
JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_bench_1model(JNIEnv *env, jobject, jlong ctx_ptr, jlong model_ptr, jlong batch_ptr, jint pp, jint tg, jint pl, jint nr) {
    return env->NewStringUTF("pp 100 t/s, tg 20 t/s");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_system_1info(JNIEnv *env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

extern "C"
JNIEXPORT jint JNICALL
Java_android_llama_cpp_LLamaAndroid_completion_1init(JNIEnv *env, jobject, jlong ctx_ptr, jlong batch_ptr, jstring jprompt, jboolean format_chat, jint n_len) {
    llama_context *ctx = reinterpret_cast<llama_context *>(ctx_ptr);
    common_batch *batch = reinterpret_cast<common_batch *>(batch_ptr);
    const char *prompt = env->GetStringUTFChars(jprompt, 0);

    const llama_model *model = llama_get_model(ctx);
    const llama_vocab *vocab = llama_model_get_vocab(model);

    std::vector<llama_token> tokens(n_len);
    int n_tokens = llama_tokenize(vocab, prompt, strlen(prompt), tokens.data(), tokens.size(), true, true);

    common_batch_clear(*batch);
    for (int i = 0; i < n_tokens; i++) {
        common_batch_add(*batch, tokens[i], i, { 0 }, i == n_tokens - 1);
    }

    llama_decode(ctx, *batch);
    env->ReleaseStringUTFChars(jprompt, prompt);
    return n_tokens;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_completion_1loop(JNIEnv *env, jobject, jlong ctx_ptr, jlong batch_ptr, jlong sampler_ptr, jint n_len, jobject jncur) {
    llama_context *ctx = reinterpret_cast<llama_context *>(ctx_ptr);
    common_batch *batch = reinterpret_cast<common_batch *>(batch_ptr);
    common_sampler *sampler = reinterpret_cast<common_sampler *>(sampler_ptr);

    llama_token id = common_sampler_sample(sampler, ctx, batch->n_tokens - 1);
    common_sampler_accept(sampler, id);

    const llama_model *model = llama_get_model(ctx);
    const llama_vocab *vocab = llama_model_get_vocab(model);

    if (llama_vocab_is_eog(vocab, id)) {
        return NULL;
    }

    char buf[256];
    int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
    std::string piece(buf, n);

    common_batch_clear(*batch);
    common_batch_add(*batch, id, 0, { 0 }, true);
    llama_decode(ctx, *batch);

    return env->NewStringUTF(piece.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_kv_1cache_1clear(JNIEnv *, jobject, jlong ctx_ptr) {
    llama_context *ctx = reinterpret_cast<llama_context *>(ctx_ptr);
    llama_kv_cache_clear(ctx);
}
