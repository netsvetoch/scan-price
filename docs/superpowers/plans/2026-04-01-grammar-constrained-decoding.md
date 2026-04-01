# Grammar-Constrained Decoding for Local Vision Engine

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add JSON Schema-based grammar-constrained decoding to `analyzeImage()` so the local LLM always produces valid JSON matching `ParsedPriceTag`.

**Architecture:** Pass JSON Schema string from Kotlin through JNI to C++. In C++, convert it to GBNF grammar via `json_schema_to_grammar()`, create a temporary grammar-constrained sampler, use it for generation, then free it. The existing sampler (`g_sampler`) is untouched for non-schema calls.

**Trade-off: `<think>` reasoning vs grammar.** Grammar-constrained decoding forces the model to produce valid JSON from the first token, which means the model cannot use its `<think>...</think>` chain-of-thought reasoning. For the 0.8B model, guaranteed valid JSON output is more valuable than sometimes-useful-sometimes-garbled thinking — invalid JSON responses are a total failure, while slightly less accurate content is still usable. The `<think>` stripping code is kept for backward compatibility when grammar is not used.

**Tech Stack:** llama.cpp `common` library (json-schema-to-grammar, sampling), nlohmann/json (vendored in llama.cpp), JNI, Kotlin

---

## File Map

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `llama-lib/src/main/cpp/ai_chat.cpp` | Add `jsonSchema` JNI param, grammar sampler creation |
| Modify | `llama-lib/src/main/java/com/arm/aichat/InferenceEngine.kt` | Add `jsonSchema` to interface |
| Modify | `llama-lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt` | Update JNI declaration + wrapper |
| Modify | `app/src/main/java/ru/ainetico/honestprice/ocr/LocalVisionEngine.kt` | Define schema, pass to engine, simplify prompt & parser |

---

### Task 1: C++ — Grammar-constrained sampler in analyzeImage

**Files:**
- Modify: `llama-lib/src/main/cpp/ai_chat.cpp:1-255`

- [ ] **Step 1: Add includes**

Add at the top of `ai_chat.cpp`, after the existing includes:

```cpp
#include "json-schema-to-grammar.h"
#include <nlohmann/json.hpp>
```

- [ ] **Step 2: Update `analyzeImage` JNI signature**

Change the function signature to accept an additional `jstring jsonSchema` parameter:

```cpp
extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_analyzeImage(
        JNIEnv *env, jobject,
        jbyteArray jimageData, jint dataLen, jstring jprompt, jstring jsonSchema)
```

- [ ] **Step 3: Parse JSON schema and create grammar sampler**

After the prompt formatting block (after line 179), before tokenization, add grammar sampler creation. Insert this code right before the `// Tokenize with image` comment:

```cpp
    // Create grammar-constrained sampler if schema provided
    common_sampler *local_sampler = nullptr;
    if (jsonSchema) {
        const auto *schema_cstr = env->GetStringUTFChars(jsonSchema, nullptr);
        std::string schema_str(schema_cstr);
        env->ReleaseStringUTFChars(jsonSchema, schema_cstr);

        if (!schema_str.empty()) {
            try {
                auto schema_json = nlohmann::ordered_json::parse(schema_str);
                std::string gbnf = json_schema_to_grammar(schema_json);

                if (!gbnf.empty()) {
                    LOGi("Grammar (%d chars): %.200s", (int)gbnf.size(), gbnf.c_str());

                    common_params_sampling sparams;
                    sparams.temp = DEFAULT_SAMPLER_TEMP;
                    sparams.grammar = common_grammar(COMMON_GRAMMAR_TYPE_OUTPUT_FORMAT, gbnf);
                    local_sampler = common_sampler_init(g_model, sparams);
                } else {
                    LOGw("json_schema_to_grammar returned empty GBNF, falling back to unconstrained");
                }
            } catch (const std::exception &e) {
                LOGe("Failed to create grammar sampler: %s", e.what());
            }
        }
    }
    common_sampler *sampler = local_sampler ? local_sampler : g_sampler;
```

- [ ] **Step 4: Replace `g_sampler` with `sampler` in generation loop**

In the generation loop (lines 213-232), replace all 3 occurrences of `g_sampler` with `sampler`:

```cpp
    // Generate response
    common_sampler_reset(sampler);
    std::ostringstream response;

    const int max_tokens = 2048;
    for (int i = 0; i < max_tokens; i++) {
        auto tok = common_sampler_sample(sampler, g_context, -1);
        common_sampler_accept(sampler, tok, true);
        // ... rest unchanged
    }
```

- [ ] **Step 5: Free local sampler after generation**

After the generation loop, before the `<think>` stripping logic, add cleanup:

```cpp
    if (local_sampler) {
        common_sampler_free(local_sampler);
    }
```

- [ ] **Step 6: Build to verify C++ compiles**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew :llama-lib:build`
Expected: BUILD SUCCESSFUL (JNI signature change will cause runtime mismatch but build should pass)

- [ ] **Step 7: Commit**

```bash
git add llama-lib/src/main/cpp/ai_chat.cpp
git commit -m "feat: grammar-constrained decoding in analyzeImage JNI"
```

---

### Task 2: Kotlin — Update JNI interface and wrapper

**Files:**
- Modify: `llama-lib/src/main/java/com/arm/aichat/InferenceEngine.kt:42`
- Modify: `llama-lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt:111,290-304`

- [ ] **Step 1: Update InferenceEngine interface**

In `InferenceEngine.kt`, change the `analyzeImage` signature (line 42):

```kotlin
suspend fun analyzeImage(imageData: ByteArray, prompt: String, jsonSchema: String? = null): String
```

- [ ] **Step 2: Update JNI external declaration**

In `InferenceEngineImpl.kt`, change the private external declaration (line 111):

```kotlin
private external fun analyzeImage(imageData: ByteArray, dataLen: Int, prompt: String, jsonSchema: String?): String
```

- [ ] **Step 3: Update public analyzeImage wrapper**

In `InferenceEngineImpl.kt`, update the override method (lines 290-304):

```kotlin
override suspend fun analyzeImage(imageData: ByteArray, prompt: String, jsonSchema: String?): String =
    withContext(llamaDispatcher) {
        check(_state.value is InferenceEngine.State.ModelReady) {
            "Cannot analyze image in ${_state.value.javaClass.simpleName}!"
        }
        Log.i(TAG, "Analyzing image (${imageData.size} bytes, schema=${jsonSchema != null})...")
        _state.value = InferenceEngine.State.Generating
        try {
            val result = analyzeImage(imageData, imageData.size, prompt, jsonSchema)
            Log.i(TAG, "Image analysis complete: ${result.length} chars")
            result
        } finally {
            _state.value = InferenceEngine.State.ModelReady
        }
    }
```

- [ ] **Step 4: Commit**

```bash
git add llama-lib/src/main/java/com/arm/aichat/InferenceEngine.kt \
        llama-lib/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt
git commit -m "feat: add jsonSchema param to analyzeImage Kotlin API"
```

---

### Task 3: LocalVisionEngine — Schema + simplified prompt

**Files:**
- Modify: `app/src/main/java/ru/ainetico/honestprice/ocr/LocalVisionEngine.kt`

- [ ] **Step 1: Add JSON_SCHEMA constant**

In `LocalVisionEngine.kt` companion object, add the schema after `MMPROJ_FILENAME`:

```kotlin
private const val JSON_SCHEMA = """
{
  "type": "object",
  "properties": {
    "product_name": { "type": ["string", "null"], "description": "Short product name from the tag" },
    "product_description": { "type": ["string", "null"], "description": "Additional details: brand, composition, variety" },
    "price_regular": { "type": ["number", "null"], "description": "Regular price" },
    "price_discount": { "type": ["number", "null"], "description": "Discounted/card price, null if no discount" },
    "weight_value": { "type": ["number", "null"], "description": "Weight or volume number as on the tag" },
    "weight_unit": { "enum": ["г", "кг", "мл", "л", "шт", null] }
  },
  "required": ["product_name", "price_regular"],
  "additionalProperties": false
}
"""
```

- [ ] **Step 2: Simplify PROMPT**

Replace the existing `PROMPT` constant. Since the grammar guarantees JSON structure, the prompt only needs to describe what to extract, not how to format:

```kotlin
private const val PROMPT = "This is a photo of a price tag from a Russian store. " +
    "Extract the product name, description, prices, weight/volume from the tag. " +
    "If there is a discounted or card price, include it separately from the regular price. " +
    "Use null for any fields you cannot read."
```

- [ ] **Step 3: Pass schema to analyzeImage**

In the `analyze()` method, update the `analyzeImage` call (line 125):

```kotlin
val response = eng.analyzeImage(imageBytes, PROMPT, JSON_SCHEMA)
```

- [ ] **Step 4: Simplify parseResponse**

Since grammar guarantees valid JSON, simplify `parseResponse()`:

```kotlin
private fun parseResponse(content: String): ParsedPriceTag {
    return try {
        Log.d(TAG, "Parsing JSON: $content")
        val json = JSONObject(content)

        val unit = json.optStringOrNull("weight_unit")?.lowercase()?.let { raw ->
            when {
                raw.contains("кг") -> WeightUnit.KG
                raw.contains("г") -> WeightUnit.G
                raw.contains("мл") -> WeightUnit.ML
                raw.contains("л") -> WeightUnit.L
                raw.contains("шт") -> WeightUnit.PCS
                else -> null
            }
        }

        val discount = json.optStringOrNull("price_discount")?.toBigDecimalSafe()

        ParsedPriceTag(
            productName = json.optStringOrNull("product_name"),
            productDescription = json.optStringOrNull("product_description"),
            priceRegular = json.optStringOrNull("price_regular")?.toBigDecimalSafe(),
            priceDiscount = if (discount != null && discount.compareTo(java.math.BigDecimal.ZERO) == 0) null else discount,
            weightValue = json.optStringOrNull("weight_value")?.toBigDecimalSafe(),
            weightUnit = unit
        ).also {
            Log.i(
                TAG, "Parsed: name=${it.productName}, regular=${it.priceRegular}, " +
                    "discount=${it.priceDiscount}, weight=${it.weightValue} ${it.weightUnit}"
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse: $content", e)
        ParsedPriceTag()
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ocr/LocalVisionEngine.kt
git commit -m "feat: use JSON schema grammar for local vision engine"
```

---

### Task 4: Build verification

- [ ] **Step 1: Full debug build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run unit tests**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew :app:testDebugUnitTest`
Expected: All tests pass (existing tests don't touch LocalVisionEngine)

- [ ] **Step 3: Final commit if any fixups needed**
