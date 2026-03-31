#include <android/log.h>
#include <jni.h>
#include <iomanip>
#include <cmath>
#include <string>
#include <unistd.h>
#include <sampling.h>

#include "logging.h"
#include "chat.h"
#include "common.h"
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

template<class T>
static std::string join(const std::vector<T> &values, const std::string &delim) {
    std::ostringstream str;
    for (size_t i = 0; i < values.size(); i++) {
        str << values[i];
        if (i < values.size() - 1) { str << delim; }
    }
    return str.str();
}

constexpr int   N_THREADS_MIN           = 2;
constexpr int   N_THREADS_MAX           = 4;
constexpr int   N_THREADS_HEADROOM      = 2;
constexpr int   DEFAULT_CONTEXT_SIZE    = 8192;
constexpr int   BATCH_SIZE              = 512;
constexpr float DEFAULT_SAMPLER_TEMP    = 0.1f;

static llama_model                      * g_model     = nullptr;
static llama_context                    * g_context    = nullptr;
static llama_batch                        g_batch;
static common_chat_templates_ptr          g_chat_templates;
static common_sampler                   * g_sampler    = nullptr;
static mtmd_context                     * g_mtmd_ctx   = nullptr;

static int get_n_threads() {
    return std::max(N_THREADS_MIN, std::min(N_THREADS_MAX,
        (int) sysconf(_SC_NPROCESSORS_ONLN) - N_THREADS_HEADROOM));
}

// ============================================================
// Init / Load / Prepare (same as original)
// ============================================================

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_init(JNIEnv *env, jobject, jstring nativeLibDir) {
    llama_log_set(aichat_android_log_callback, nullptr);
    mtmd_helper_log_set(aichat_android_log_callback, nullptr);

    const auto *path = env->GetStringUTFChars(nativeLibDir, 0);
    LOGi("Loading backends from %s", path);
    ggml_backend_load_all_from_path(path);
    env->ReleaseStringUTFChars(nativeLibDir, path);

    llama_backend_init();
    LOGi("Backend initiated; Log handler set.");
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_load(JNIEnv *env, jobject, jstring jmodel_path) {
    llama_model_params model_params = llama_model_default_params();
    const auto *model_path = env->GetStringUTFChars(jmodel_path, 0);
    LOGi("Loading model from: %s", model_path);

    auto *model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);
    if (!model) { LOGe("Failed to load model!"); return 1; }
    g_model = model;
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_prepare(JNIEnv *, jobject) {
    if (!g_model) { LOGe("Model not loaded!"); return 1; }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = DEFAULT_CONTEXT_SIZE;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = get_n_threads();
    ctx_params.n_threads_batch = get_n_threads();

    g_context = llama_init_from_model(g_model, ctx_params);
    if (!g_context) { LOGe("Failed to create context!"); return 1; }

    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");
    common_params_sampling sparams;
    sparams.temp = DEFAULT_SAMPLER_TEMP;
    g_sampler = common_sampler_init(g_model, sparams);
    LOGi("Model prepared. Threads: %d", get_n_threads());
    return 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

// ============================================================
// Vision: loadMmproj + analyzeImage
// ============================================================

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_loadMmproj(JNIEnv *env, jobject, jstring jpath) {
    if (g_mtmd_ctx) { mtmd_free(g_mtmd_ctx); g_mtmd_ctx = nullptr; }

    const auto *path = env->GetStringUTFChars(jpath, 0);
    LOGi("Loading mmproj from: %s", path);

    mtmd_context_params p = mtmd_context_params_default();
    p.use_gpu = false;
    p.print_timings = true;
    p.n_threads = get_n_threads();

    g_mtmd_ctx = mtmd_init_from_file(path, g_model, p);
    env->ReleaseStringUTFChars(jpath, path);

    if (!g_mtmd_ctx) { LOGe("Failed to init mtmd!"); return 1; }
    LOGi("Vision support: %s", mtmd_support_vision(g_mtmd_ctx) ? "yes" : "no");
    return 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_analyzeImage(
        JNIEnv *env, jobject,
        jbyteArray jimageData, jint dataLen, jstring jprompt)
{
    if (!g_mtmd_ctx || !g_model || !g_context) {
        LOGe("analyzeImage: not initialized!");
        return env->NewStringUTF("");
    }

    // Get image bytes
    auto *img = env->GetByteArrayElements(jimageData, nullptr);
    auto len = static_cast<size_t>(dataLen);
    LOGi("analyzeImage: %zu bytes", len);

    // Create bitmap from JPEG/PNG buffer
    auto *bitmap = mtmd_helper_bitmap_init_from_buf(
        g_mtmd_ctx, reinterpret_cast<const unsigned char *>(img), len);
    env->ReleaseByteArrayElements(jimageData, img, JNI_ABORT);

    if (!bitmap) { LOGe("Failed to create bitmap!"); return env->NewStringUTF(""); }
    LOGi("Bitmap: %dx%d", mtmd_bitmap_get_nx(bitmap), mtmd_bitmap_get_ny(bitmap));

    // Build prompt: <media_marker>\n<user_prompt>
    const auto *prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string full_prompt = std::string(mtmd_default_marker()) + "\n" + prompt;
    env->ReleaseStringUTFChars(jprompt, prompt);

    // Tokenize with image
    mtmd_input_text input_text;
    input_text.text = full_prompt.c_str();
    input_text.add_special = true;
    input_text.parse_special = true;

    const mtmd_bitmap *bitmaps[] = { bitmap };
    auto *chunks = mtmd_input_chunks_init();
    int32_t rc = mtmd_tokenize(g_mtmd_ctx, chunks, &input_text, bitmaps, 1);
    mtmd_bitmap_free(bitmap);

    if (rc != 0) {
        LOGe("Tokenize failed: %d", rc);
        mtmd_input_chunks_free(chunks);
        return env->NewStringUTF("");
    }

    LOGi("Tokenized: %zu tokens, %zu chunks",
         mtmd_helper_get_n_tokens(chunks), mtmd_input_chunks_size(chunks));

    // Clear KV cache
    llama_memory_clear(llama_get_memory(g_context), false);

    // Eval all chunks
    llama_pos n_past = 0;
    rc = mtmd_helper_eval_chunks(g_mtmd_ctx, g_context, chunks, 0, 0, BATCH_SIZE, true, &n_past);
    mtmd_input_chunks_free(chunks);

    if (rc != 0) { LOGe("Eval failed: %d", rc); return env->NewStringUTF(""); }
    LOGi("Eval done, n_past=%d. Generating...", n_past);

    // Generate response
    common_sampler_reset(g_sampler);
    std::ostringstream response;

    const int max_tokens = 2048;  // enough for thinking + JSON response
    for (int i = 0; i < max_tokens; i++) {
        auto tok = common_sampler_sample(g_sampler, g_context, -1);
        common_sampler_accept(g_sampler, tok, true);

        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), tok)) {
            LOGi("EOS at token %d", i);
            break;
        }

        response << common_token_to_piece(g_context, tok);

        common_batch_clear(g_batch);
        common_batch_add(g_batch, tok, n_past, {0}, true);
        if (llama_decode(g_context, g_batch) != 0) { LOGe("Decode failed at %d", i); break; }
        n_past++;
    }

    std::string result = response.str();
    LOGi("Raw response %d chars: %.300s", (int)result.size(), result.c_str());

    // Strip <think>...</think> block — keep only the actual answer
    auto think_start = result.find("<think>");
    auto think_end = result.find("</think>");
    if (think_start != std::string::npos && think_end != std::string::npos) {
        std::string thinking = result.substr(think_start + 7, think_end - think_start - 7);
        LOGi("Thinking (%d chars): %.200s", (int)thinking.size(), thinking.c_str());
        result = result.substr(0, think_start) + result.substr(think_end + 8);
    }

    // Trim whitespace
    auto first = result.find_first_not_of(" \n\r\t");
    auto last = result.find_last_not_of(" \n\r\t");
    if (first != std::string::npos) {
        result = result.substr(first, last - first + 1);
    }

    LOGi("Final response %d chars: %.200s", (int)result.size(), result.c_str());
    return env->NewStringUTF(result.c_str());
}

// ============================================================
// Text chat methods (kept for compatibility)
// ============================================================

static std::vector<common_chat_msg> chat_msgs;
static llama_pos system_prompt_position = 0;
static llama_pos current_position = 0;
static llama_pos stop_generation_position = 0;
static std::string cached_token_chars;
static std::ostringstream assistant_ss;

static void reset_long_term_states(bool clear_kv = true) {
    chat_msgs.clear();
    system_prompt_position = 0;
    current_position = 0;
    if (clear_kv && g_context) llama_memory_clear(llama_get_memory(g_context), false);
}

static void reset_short_term_states() {
    stop_generation_position = 0;
    cached_token_chars.clear();
    assistant_ss.str("");
}

static std::string chat_add_and_format(const std::string &role, const std::string &content) {
    common_chat_msg msg;
    msg.role = role;
    msg.content = content;
    auto formatted = common_chat_format_single(g_chat_templates.get(), chat_msgs, msg, role == "user", false);
    chat_msgs.push_back(msg);
    return formatted;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processSystemPrompt(JNIEnv *env, jobject, jstring jprompt) {
    reset_long_term_states();
    reset_short_term_states();
    const auto *p = env->GetStringUTFChars(jprompt, nullptr);
    std::string formatted(p);
    if (common_chat_templates_was_explicit(g_chat_templates.get()))
        formatted = chat_add_and_format("system", p);
    env->ReleaseStringUTFChars(jprompt, p);
    auto tokens = common_tokenize(g_context, formatted, true, true);
    common_batch_clear(g_batch);
    for (int i = 0; i < (int)tokens.size(); i++)
        common_batch_add(g_batch, tokens[i], i, {0}, i == (int)tokens.size()-1);
    if (llama_decode(g_context, g_batch)) return 2;
    system_prompt_position = current_position = (int)tokens.size();
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processUserPrompt(JNIEnv *env, jobject, jstring jprompt, jint n_predict) {
    reset_short_term_states();
    const auto *p = env->GetStringUTFChars(jprompt, nullptr);
    std::string formatted(p);
    if (common_chat_templates_was_explicit(g_chat_templates.get()))
        formatted = chat_add_and_format("user", p);
    env->ReleaseStringUTFChars(jprompt, p);
    auto tokens = common_tokenize(g_context, formatted, true, true);
    common_batch_clear(g_batch);
    for (int i = 0; i < (int)tokens.size(); i++)
        common_batch_add(g_batch, tokens[i], current_position+i, {0}, i == (int)tokens.size()-1);
    if (llama_decode(g_context, g_batch)) return 2;
    current_position += (int)tokens.size();
    stop_generation_position = current_position + n_predict;
    return 0;
}

static bool is_valid_utf8(const char *s) {
    if (!s) return true;
    const auto *b = (const unsigned char *)s;
    while (*b) {
        int n;
        if ((*b & 0x80) == 0x00) n = 1;
        else if ((*b & 0xE0) == 0xC0) n = 2;
        else if ((*b & 0xF0) == 0xE0) n = 3;
        else if ((*b & 0xF8) == 0xF0) n = 4;
        else return false;
        b++;
        for (int i = 1; i < n; i++) { if ((*b & 0xC0) != 0x80) return false; b++; }
    }
    return true;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_generateNextToken(JNIEnv *env, jobject) {
    if (current_position >= DEFAULT_CONTEXT_SIZE - 4 || current_position >= stop_generation_position)
        return nullptr;
    auto tok = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, tok, true);
    common_batch_clear(g_batch);
    common_batch_add(g_batch, tok, current_position, {0}, true);
    if (llama_decode(g_context, g_batch)) return nullptr;
    current_position++;
    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), tok)) {
        chat_add_and_format("assistant", assistant_ss.str());
        return nullptr;
    }
    cached_token_chars += common_token_to_piece(g_context, tok);
    if (is_valid_utf8(cached_token_chars.c_str())) {
        auto *r = env->NewStringUTF(cached_token_chars.c_str());
        assistant_ss << cached_token_chars;
        cached_token_chars.clear();
        return r;
    }
    return env->NewStringUTF("");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_unload(JNIEnv *, jobject) {
    reset_long_term_states();
    reset_short_term_states();
    if (g_mtmd_ctx) { mtmd_free(g_mtmd_ctx); g_mtmd_ctx = nullptr; }
    if (g_sampler) { common_sampler_free(g_sampler); g_sampler = nullptr; }
    g_chat_templates.reset();
    llama_batch_free(g_batch);
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_shutdown(JNIEnv *, jobject) {
    llama_backend_free();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_benchModel(JNIEnv *env, jobject, jint, jint, jint, jint) {
    return env->NewStringUTF("N/A");
}
