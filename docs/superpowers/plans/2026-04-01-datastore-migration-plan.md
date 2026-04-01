# DataStore Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all plain SharedPreferences with Preferences DataStore, consolidating non-sensitive settings into `AppSettings` with `Flow<T>` API.

**Architecture:** `AppSettings` becomes the single entry point for all preferences. Non-sensitive fields backed by Preferences DataStore (`app_preferences`), encrypted fields stay on EncryptedSharedPreferences. Callers adapt to `Flow<T>` via `collectAsState()` or `.first()`.

**Tech Stack:** Preferences DataStore 1.1.7, Kotlin Coroutines, Jetpack Compose

**Spec:** `docs/superpowers/specs/2026-04-01-datastore-migration-design.md`

---

### Task 1: Add DataStore dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version and library to version catalog**

In `gradle/libs.versions.toml`, add:

```toml
# In [versions] section:
datastorePreferences = "1.1.7"

# In [libraries] section:
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }
```

- [ ] **Step 2: Add dependency to app build.gradle.kts**

In `app/build.gradle.kts`, in the `dependencies` block, replace:

```kotlin
// Encrypted SharedPreferences for secure API key storage
implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

with:

```kotlin
// Encrypted SharedPreferences for secure API key storage
implementation("androidx.security:security-crypto:1.1.0-alpha06")

// Preferences DataStore
implementation(libs.datastore.preferences)
```

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Preferences DataStore dependency"
```

---

### Task 2: Rewrite AppSettings with DataStore

**Files:**
- Modify: `app/src/main/java/ru/ainetico/honestprice/data/AppSettings.kt`

- [ ] **Step 1: Rewrite AppSettings**

Replace the entire file with:

```kotlin
package ru.ainetico.honestprice.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppSettings(context: Context) {

    private val dataStore = context.dataStore

    private val securePrefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // DataStore-backed fields (Flow<T>)
    private object Keys {
        val USE_REMOTE_SERVER = booleanPreferencesKey("use_remote_server")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val LOCAL_PROMPT = stringPreferencesKey("local_prompt")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val useRemoteServer: Flow<Boolean> = dataStore.data.map { it[Keys.USE_REMOTE_SERVER] ?: false }
    val systemPrompt: Flow<String> = dataStore.data.map { it[Keys.SYSTEM_PROMPT] ?: "" }
    val localPrompt: Flow<String> = dataStore.data.map { it[Keys.LOCAL_PROMPT] ?: "" }
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }

    suspend fun setUseRemoteServer(enabled: Boolean) {
        dataStore.edit { it[Keys.USE_REMOTE_SERVER] = enabled }
    }

    suspend fun setSystemPrompt(prompt: String) {
        dataStore.edit { it[Keys.SYSTEM_PROMPT] = prompt }
    }

    suspend fun setLocalPrompt(prompt: String) {
        dataStore.edit { it[Keys.LOCAL_PROMPT] = prompt }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    // Encrypted fields (StateFlow<T>, unchanged API)
    private val _apiUrl = MutableStateFlow(securePrefs.getString("api_url", "") ?: "")
    val apiUrl: StateFlow<String> = _apiUrl

    private val _apiKey = MutableStateFlow(securePrefs.getString("api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey

    private val _apiModel = MutableStateFlow(securePrefs.getString("api_model", "") ?: "")
    val apiModel: StateFlow<String> = _apiModel

    fun setApiUrl(url: String) {
        securePrefs.edit().putString("api_url", url).commit()
        _apiUrl.value = url
    }

    fun setApiKey(key: String) {
        securePrefs.edit().putString("api_key", key).commit()
        _apiKey.value = key
    }

    fun setApiModel(model: String) {
        securePrefs.edit().putString("api_model", model).commit()
        _apiModel.value = model
    }

    suspend fun isRemoteModelConfigured(): Boolean {
        return useRemoteServer.first() && _apiUrl.value.isNotBlank() && _apiModel.value.isNotBlank()
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew :app:assembleDebug`
Expected: Build will FAIL because callers still use old API. That's expected — we fix callers in subsequent tasks.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/data/AppSettings.kt
git commit -m "refactor: rewrite AppSettings to use Preferences DataStore"
```

---

### Task 3: Adapt ImageAnalyzer and fix tests

**Files:**
- Modify: `app/src/main/java/ru/ainetico/honestprice/analyzer/ImageAnalyzer.kt`
- Modify: `app/src/test/java/ru/ainetico/honestprice/analyzer/ImageAnalyzerTest.kt`

- [ ] **Step 1: Update ImageAnalyzer to use Flow API**

In `app/src/main/java/ru/ainetico/honestprice/analyzer/ImageAnalyzer.kt`, replace the `analyze` function body. The file becomes:

```kotlin
package ru.ainetico.honestprice.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.flow.first
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.model.AnalysisResult
import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.ocr.LocalVisionEngine
import ru.ainetico.honestprice.ocr.RemoteVisionClient

sealed class AnalysisMode {
    object Local : AnalysisMode()
    object Remote : AnalysisMode()
}

class RemoteAnalysisException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ImageAnalyzer(
    private val localEngine: LocalVisionEngine,
    private val calculator: PriceCalculator,
    private val appSettings: AppSettings
) {
    private val remoteClient = RemoteVisionClient()

    suspend fun analyze(bitmap: Bitmap, cropRect: Rect?, forceLocal: Boolean = false): AnalysisResult {
        val useRemote = !forceLocal && appSettings.isRemoteModelConfigured()

        val tag: ParsedPriceTag = if (useRemote) {
            try {
                Log.d("ImageAnalyzer", "Using remote model...")
                val systemPrompt = appSettings.systemPrompt.first().ifBlank { RemoteVisionClient.DEFAULT_SYSTEM_PROMPT }
                remoteClient.analyze(bitmap, appSettings.apiUrl.value, appSettings.apiKey.value, appSettings.apiModel.value, systemPrompt)
            } catch (e: Exception) {
                Log.e("ImageAnalyzer", "Remote failed: ${e.message}")
                throw RemoteAnalysisException("Ошибка при подключении к серверу", e)
            }
        } else {
            Log.d("ImageAnalyzer", "Using local model...")
            val prompt = appSettings.localPrompt.first().ifBlank { LocalVisionEngine.DEFAULT_PROMPT }
            localEngine.analyze(bitmap, prompt)
        }

        val price = calculator.calculate(tag)
        return AnalysisResult(tag = tag, price = price)
    }
}
```

Key changes: added `import kotlinx.coroutines.flow.first`, replaced `appSettings.systemPrompt.value` with `appSettings.systemPrompt.first()`, same for `localPrompt`. `isRemoteModelConfigured()` is now `suspend` — already called from `suspend fun analyze()`.

- [ ] **Step 2: Update ImageAnalyzerTest mocks**

In `app/src/test/java/ru/ainetico/honestprice/analyzer/ImageAnalyzerTest.kt`, replace the file with:

```kotlin
package ru.ainetico.honestprice.analyzer

import android.graphics.Bitmap
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.WeightUnit
import ru.ainetico.honestprice.ocr.LocalVisionEngine
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class ImageAnalyzerTest {

    private val localEngine = mockk<LocalVisionEngine>()
    private val calculator = PriceCalculator()
    private val appSettings = mockk<AppSettings> {
        coEvery { isRemoteModelConfigured() } returns false
        every { apiUrl } returns MutableStateFlow("")
        every { apiKey } returns MutableStateFlow("")
        every { apiModel } returns MutableStateFlow("")
        every { systemPrompt } returns flowOf("")
        every { localPrompt } returns flowOf("")
    }

    private val analyzer = ImageAnalyzer(localEngine, calculator, appSettings)

    @Test
    fun `analyze returns complete result from local engine`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        coEvery { localEngine.analyze(any(), any()) } returns ParsedPriceTag(
            productName = "Молоко",
            priceRegular = BigDecimal("89.90"),
            weightValue = BigDecimal("1"),
            weightUnit = WeightUnit.L
        )

        val result = analyzer.analyze(bitmap, null)

        assertEquals("Молоко", result.tag.productName)
        assertEquals(BigDecimal("89.90"), result.tag.priceRegular)
        assertEquals(WeightUnit.L, result.tag.weightUnit)
        assertNotNull(result.price)
        assertEquals(WeightUnit.L, result.price!!.displayUnit)
    }

    @Test
    fun `analyze returns null price when no price detected`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        coEvery { localEngine.analyze(any(), any()) } returns ParsedPriceTag()

        val result = analyzer.analyze(bitmap, null)

        assertNull(result.tag.priceRegular)
        assertNull(result.price)
    }

    @Test
    fun `analyze throws RemoteAnalysisException when remote fails`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val remoteSettings = mockk<AppSettings> {
            coEvery { isRemoteModelConfigured() } returns true
            every { apiUrl } returns MutableStateFlow("https://example.com")
            every { apiKey } returns MutableStateFlow("key")
            every { apiModel } returns MutableStateFlow("model")
            every { systemPrompt } returns flowOf("")
            every { localPrompt } returns flowOf("")
        }
        val remoteAnalyzer = ImageAnalyzer(localEngine, calculator, remoteSettings)

        try {
            remoteAnalyzer.analyze(bitmap, null)
            fail("Expected RemoteAnalysisException")
        } catch (e: RemoteAnalysisException) {
            assertEquals("Ошибка при подключении к серверу", e.message)
        }
    }

    @Test
    fun `analyze uses local engine when forceLocal is true`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val remoteSettings = mockk<AppSettings> {
            coEvery { isRemoteModelConfigured() } returns true
            every { apiUrl } returns MutableStateFlow("https://example.com")
            every { apiKey } returns MutableStateFlow("key")
            every { apiModel } returns MutableStateFlow("model")
            every { systemPrompt } returns flowOf("")
            every { localPrompt } returns flowOf("")
        }
        val remoteAnalyzer = ImageAnalyzer(localEngine, calculator, remoteSettings)
        coEvery { localEngine.analyze(any(), any()) } returns ParsedPriceTag(
            productName = "Хлеб",
            priceRegular = BigDecimal("45.00")
        )

        val result = remoteAnalyzer.analyze(bitmap, null, forceLocal = true)

        assertEquals("Хлеб", result.tag.productName)
    }
}
```

Key changes: `every { isRemoteModelConfigured() }` → `coEvery { isRemoteModelConfigured() }` (now suspend), `MutableStateFlow("")` → `flowOf("")` for `systemPrompt` and `localPrompt`.

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.ainetico.honestprice.analyzer.ImageAnalyzerTest"`
Expected: All 4 tests PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/analyzer/ImageAnalyzer.kt app/src/test/java/ru/ainetico/honestprice/analyzer/ImageAnalyzerTest.kt
git commit -m "refactor: adapt ImageAnalyzer to DataStore Flow API"
```

---

### Task 4: Adapt MainActivity (onboarding flow)

**Files:**
- Modify: `app/src/main/java/ru/ainetico/honestprice/MainActivity.kt`

- [ ] **Step 1: Update HonestPriceApp to read onboardingCompleted from AppSettings**

**Important:** DataStore reads are async — `collectAsState(initial = false)` would always show Onboarding on the first frame, even for users who completed it. `NavHost` only uses `startDestination` on initial composition, so the recomposition wouldn't fix the route. To avoid this, read the initial value synchronously in `onCreate()` via `runBlocking`.

In `MainActivity.kt`, in `onCreate()`, change lines 97-102:

```kotlin
        val appSettings = AppSettings(applicationContext)
        val launchAction = intent?.action

        setContent {
            ScanPriceTheme {
                HonestPriceApp(localVisionEngine, modelDownloader, appSettings, launchAction)
```

to:

```kotlin
        val appSettings = AppSettings(applicationContext)
        val launchAction = intent?.action
        val initialOnboardingCompleted = runBlocking { appSettings.onboardingCompleted.first() }

        setContent {
            ScanPriceTheme {
                HonestPriceApp(localVisionEngine, modelDownloader, appSettings, launchAction, initialOnboardingCompleted)
```

Add import: `import kotlinx.coroutines.runBlocking` (the `import kotlinx.coroutines.flow.first` is already present at line 36).

Then update the `HonestPriceApp` signature and body. Change lines 108-122:

```kotlin
@Composable
fun HonestPriceApp(
    localVisionEngine: LocalVisionEngine,
    modelDownloader: ModelDownloader,
    appSettings: AppSettings,
    launchAction: String? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("honest_price_prefs", Context.MODE_PRIVATE)
    }
    val onboardingCompleted = prefs.getBoolean("onboarding_completed", false)
    val db = remember { AppDatabase.getInstance(context) }
    val startDestination = if (onboardingCompleted) Screen.History.route else Screen.Onboarding.route
```

to:

```kotlin
@Composable
fun HonestPriceApp(
    localVisionEngine: LocalVisionEngine,
    modelDownloader: ModelDownloader,
    appSettings: AppSettings,
    launchAction: String? = null,
    initialOnboardingCompleted: Boolean = false
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val startDestination = if (initialOnboardingCompleted) Screen.History.route else Screen.Onboarding.route
```

Remove the unused `import android.content.Context` only if no other usage remains. (Check: `HistoryDestination` uses `context: Context` parameter — it's still needed as a parameter type.)

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/MainActivity.kt
git commit -m "refactor: read onboardingCompleted from AppSettings DataStore"
```

---

### Task 5: Adapt OnboardingScreen

**Files:**
- Modify: `app/src/main/java/ru/ainetico/honestprice/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Replace SharedPreferences write with AppSettings**

Change the `OnboardingScreen` composable signature to accept `appSettings: AppSettings` and replace the `completeOnboarding` helper.

Remove the `completeOnboarding` private function (lines 48-54):

```kotlin
private fun completeOnboarding(context: Context, onComplete: () -> Unit) {
  context.getSharedPreferences("honest_price_prefs", Context.MODE_PRIVATE)
    .edit()
    .putBoolean("onboarding_completed", true)
    .apply()
  onComplete()
}
```

Update the composable signature from:

```kotlin
@Composable
fun OnboardingScreen(modelDownloader: ModelDownloader, onComplete: () -> Unit) {
```

to:

```kotlin
@Composable
fun OnboardingScreen(appSettings: AppSettings, modelDownloader: ModelDownloader, onComplete: () -> Unit) {
```

The composable already has `val coroutineScope = rememberCoroutineScope()` — reuse it.

There are **two** call sites for `completeOnboarding` that both need replacing:

**Call site 1** — location permission callback (line 75):
```kotlin
// Inside rememberLauncherForActivityResult callback:
completeOnboarding(context, onComplete)
```
Replace with:
```kotlin
coroutineScope.launch {
    appSettings.setOnboardingCompleted(true)
    onComplete()
}
```

**Call site 2** — skip button on location page (line 187):
```kotlin
onClick = { completeOnboarding(context, onComplete) },
```
Replace with:
```kotlin
onClick = {
    coroutineScope.launch {
        appSettings.setOnboardingCompleted(true)
        onComplete()
    }
},
```

Remove unused imports: `android.content.Context` (if no longer needed — check other usages in the file).

- [ ] **Step 2: Update caller in MainActivity.kt**

In `MainActivity.kt`, the `OnboardingScreen` call (line 160) changes from:

```kotlin
OnboardingScreen(modelDownloader = modelDownloader, onComplete = {
```

to:

```kotlin
OnboardingScreen(appSettings = appSettings, modelDownloader = modelDownloader, onComplete = {
```

Note: `appSettings` is already available in `HonestPriceApp` — it's a parameter.

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ui/onboarding/OnboardingScreen.kt app/src/main/java/ru/ainetico/honestprice/MainActivity.kt
git commit -m "refactor: OnboardingScreen uses AppSettings instead of SharedPreferences"
```

---

### Task 6: Adapt RemoteModelSection

**Files:**
- Modify: `app/src/main/java/ru/ainetico/honestprice/ui/settings/RemoteModelSection.kt`

- [ ] **Step 1: Update DataStore-backed fields to use collectAsState**

Only `useRemote` and `systemPrompt` change. `apiUrl`, `apiKey`, `apiModel` stay as-is (StateFlow).

Replace lines 50-54:

```kotlin
    var useRemote by remember { mutableStateOf(appSettings.useRemoteServer.value) }
    var apiUrl by remember { mutableStateOf(appSettings.apiUrl.value) }
    var apiModel by remember { mutableStateOf(appSettings.apiModel.value) }
    var apiKey by remember { mutableStateOf(appSettings.apiKey.value) }
    var systemPrompt by remember { mutableStateOf(appSettings.systemPrompt.value) }
```

with:

```kotlin
    val savedUseRemote by appSettings.useRemoteServer.collectAsState(initial = false)
    var useRemote by remember { mutableStateOf(savedUseRemote) }
    LaunchedEffect(savedUseRemote) { useRemote = savedUseRemote }

    var apiUrl by remember { mutableStateOf(appSettings.apiUrl.value) }
    var apiModel by remember { mutableStateOf(appSettings.apiModel.value) }
    var apiKey by remember { mutableStateOf(appSettings.apiKey.value) }

    val savedSystemPrompt by appSettings.systemPrompt.collectAsState(initial = "")
    var systemPrompt by remember { mutableStateOf(savedSystemPrompt) }
    LaunchedEffect(savedSystemPrompt) { systemPrompt = savedSystemPrompt }
```

Add imports at top of file:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
```

- [ ] **Step 2: Wrap suspend setters in scope.launch**

The file already has `val scope = rememberCoroutineScope()` (line 61).

Replace `appSettings.setUseRemoteServer(it)` (line 85) with:

```kotlin
scope.launch { appSettings.setUseRemoteServer(it) }
```

Replace `appSettings.setSystemPrompt(it)` (line 223) with:

```kotlin
scope.launch { appSettings.setSystemPrompt(it) }
```

Leave `appSettings.setApiUrl(it)`, `appSettings.setApiKey(it)`, `appSettings.setApiModel(it)` unchanged — they are still synchronous.

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ui/settings/RemoteModelSection.kt
git commit -m "refactor: RemoteModelSection adapts to DataStore Flow API"
```

---

### Task 7: Adapt LocalModelSection

**Files:**
- Modify: `app/src/main/java/ru/ainetico/honestprice/ui/settings/LocalModelSection.kt`

- [ ] **Step 1: Update localPrompt to use collectAsState**

Replace line 32:

```kotlin
    var localPrompt by remember { mutableStateOf(appSettings.localPrompt.value) }
```

with:

```kotlin
    val scope = rememberCoroutineScope()
    val savedLocalPrompt by appSettings.localPrompt.collectAsState(initial = "")
    var localPrompt by remember { mutableStateOf(savedLocalPrompt) }
    LaunchedEffect(savedLocalPrompt) { localPrompt = savedLocalPrompt }
```

Add imports (note: `collectAsState` is already imported in this file — only add missing ones):

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Wrap setter in scope.launch**

Replace `appSettings.setLocalPrompt(it)` (line 50) with:

```kotlin
scope.launch { appSettings.setLocalPrompt(it) }
```

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ui/settings/LocalModelSection.kt
git commit -m "refactor: LocalModelSection adapts to DataStore Flow API"
```

---

### Task 8: Run all tests and final verification

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All tests PASS

- [ ] **Step 2: Verify no remaining SharedPreferences usage for non-sensitive data**

Run: `grep -r "getSharedPreferences\|honest_price_prefs\|app_settings" app/src/main/java/`
Expected: No matches (only `secure_settings` in `AppSettings.kt` via EncryptedSharedPreferences)

- [ ] **Step 3: Build debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit (if any fixups needed)**

Only if previous steps required fixes.
