# Hilt DI Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate ScanPrice from manual DI to Hilt with compile-time graph validation.

**Architecture:** Big-bang migration. Create Application class, Hilt modules for all dependency groups, convert ViewModels to `@HiltViewModel`, replace manual `remember {}` wiring in Compose with `hiltViewModel()` where possible. `ResultViewModel` stays manually created (per-scan keying with `.also {}` init logic doesn't fit `hiltViewModel()` scoping). `AppNavigationViewModel` stays manual (intent-derived state).

**Tech Stack:** Dagger Hilt 2.56.2, Hilt Navigation Compose, KSP

**Spec:** `docs/superpowers/specs/2026-04-02-hilt-migration-design.md`

---

### Task 1: Add Hilt dependencies to Gradle

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add Hilt versions and libraries to version catalog**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
hilt = "2.56.2"
hiltNavigationCompose = "1.2.0"
```

Add to `[libraries]`:

```toml
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
```

Add to `[plugins]`:

```toml
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 2: Add Hilt plugin to root build.gradle.kts**

In `build.gradle.kts` (root), add to plugins block:

```kotlin
alias(libs.plugins.hilt) apply false
```

- [ ] **Step 3: Add Hilt plugin and dependencies to app build.gradle.kts**

In `app/build.gradle.kts`, add to plugins block:

```kotlin
alias(libs.plugins.hilt)
```

Add to dependencies block:

```kotlin
// Hilt
implementation(libs.hilt.android)
ksp(libs.hilt.compiler)
implementation(libs.hilt.navigation.compose)
```

- [ ] **Step 4: Verify project syncs**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>&1 | grep dagger`
Expected: Dagger/Hilt dependencies resolve successfully.

**Troubleshooting:** If Hilt plugin fails with "kotlin-android plugin required", add `alias(libs.plugins.kotlin.android)` to `app/build.gradle.kts` plugins (AGP 9.x with `kotlin-compose` should handle Kotlin compilation, but Hilt may require the explicit plugin). Add `kotlin-android` plugin entry to `libs.versions.toml` if needed: `kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }`.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
git commit -m "build: add Hilt dependencies to Gradle"
```

---

### Task 2: Create Application class and annotate MainActivity

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/ScanPriceApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/ru/ainetico/honestprice/MainActivity.kt`

- [ ] **Step 1: Create ScanPriceApplication**

```kotlin
package ru.ainetico.honestprice

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ScanPriceApplication : Application()
```

- [ ] **Step 2: Register in AndroidManifest.xml**

In the `<application>` tag, add `android:name=".ScanPriceApplication"`:

```xml
<application
    android:name=".ScanPriceApplication"
    android:allowBackup="true"
```

- [ ] **Step 3: Add @AndroidEntryPoint to MainActivity**

Add import and annotation:

```kotlin
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
```

- [ ] **Step 4: Verify build compiles**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ScanPriceApplication.kt app/src/main/AndroidManifest.xml app/src/main/java/ru/ainetico/honestprice/MainActivity.kt
git commit -m "build: add Hilt Application class and annotate MainActivity"
```

---

### Task 3: Create DatabaseModule

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/di/DatabaseModule.kt`

- [ ] **Step 1: Create DatabaseModule**

```kotlin
package ru.ainetico.honestprice.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ainetico.honestprice.data.AppDatabase
import ru.ainetico.honestprice.data.ScanDao
import ru.ainetico.honestprice.data.StoreDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideScanDao(database: AppDatabase): ScanDao {
        return database.scanDao()
    }

    @Provides
    fun provideStoreDao(database: AppDatabase): StoreDao {
        return database.storeDao()
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/di/DatabaseModule.kt
git commit -m "refactor: add Hilt DatabaseModule for Room DAOs"
```

---

### Task 4: Create DataModule

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/di/DataModule.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/data/ScanRepository.kt`

- [ ] **Step 1: Create DataModule**

```kotlin
package ru.ainetico.honestprice.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.data.ScanRepository
import ru.ainetico.honestprice.data.ScanRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindScanRepository(impl: ScanRepositoryImpl): ScanRepository

    companion object {
        @Provides
        @Singleton
        fun provideAppSettings(@ApplicationContext context: Context): AppSettings {
            return AppSettings(context)
        }
    }
}
```

- [ ] **Step 2: Add @Inject constructor to ScanRepositoryImpl**

In `app/src/main/java/ru/ainetico/honestprice/data/ScanRepository.kt`, change:

```kotlin
class ScanRepositoryImpl(private val scanDao: ScanDao) : ScanRepository {
```

to:

```kotlin
import javax.inject.Inject

class ScanRepositoryImpl @Inject constructor(private val scanDao: ScanDao) : ScanRepository {
```

- [ ] **Step 3: Verify build compiles**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/di/DataModule.kt app/src/main/java/ru/ainetico/honestprice/data/ScanRepository.kt
git commit -m "refactor: add Hilt DataModule with ScanRepository binding"
```

---

### Task 5: Create VisionModule

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/di/VisionModule.kt`

- [ ] **Step 1: Create VisionModule**

```kotlin
package ru.ainetico.honestprice.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ainetico.honestprice.analyzer.ImageAnalyzer
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.model.ModelDownloader
import ru.ainetico.honestprice.ocr.LocalVisionEngine
import ru.ainetico.honestprice.ocr.RemoteVisionClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VisionModule {

    @Provides
    @Singleton
    fun provideLocalVisionEngine(@ApplicationContext context: Context): LocalVisionEngine {
        return LocalVisionEngine(context)
    }

    @Provides
    @Singleton
    fun provideRemoteVisionClient(): RemoteVisionClient {
        return RemoteVisionClient()
    }

    @Provides
    @Singleton
    fun provideImageAnalyzer(
        localEngine: LocalVisionEngine,
        calculator: PriceCalculator,
        appSettings: AppSettings,
        remoteClient: RemoteVisionClient
    ): ImageAnalyzer {
        return ImageAnalyzer(localEngine, calculator, appSettings, remoteClient)
    }

    @Provides
    @Singleton
    fun provideModelDownloader(@ApplicationContext context: Context): ModelDownloader {
        return ModelDownloader(context)
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/di/VisionModule.kt
git commit -m "refactor: add Hilt VisionModule for engine and analyzer"
```

---

### Task 6: Create AppModule

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/di/AppModule.kt`

- [ ] **Step 1: Create AppModule**

```kotlin
package ru.ainetico.honestprice.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.location.LocationProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun providePriceCalculator(): PriceCalculator {
        return PriceCalculator()
    }

    @Provides
    @Singleton
    fun provideLocationProvider(@ApplicationContext context: Context): LocationProvider {
        return LocationProvider(context)
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/di/AppModule.kt
git commit -m "refactor: add Hilt AppModule for PriceCalculator and LocationProvider"
```

---

### Task 7: Convert ViewModels to @HiltViewModel

**Files:**
- Modify: `app/src/main/java/ru/ainetico/honestprice/ui/camera/CameraViewModel.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/ui/result/ResultViewModel.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/ui/history/HistoryViewModel.kt`

Note: `AppNavigationViewModel` stays manual — it takes `initialShowCamera` derived from `intent?.action` at Activity level, which doesn't fit `@HiltViewModel`/`SavedStateHandle`.

Note: `ResultViewModel` gets `@HiltViewModel` + `@Inject constructor` for compile-time graph validation, but is still manually instantiated in Compose (per-scan keying with `.also {}` init). The `@Inject constructor` is backward-compatible with manual creation.

- [ ] **Step 1: Convert CameraViewModel**

In `app/src/main/java/ru/ainetico/honestprice/ui/camera/CameraViewModel.kt:48-52`, change:

```kotlin
class CameraViewModel(
  private val imageAnalyzer: ImageAnalyzer,
  private val scanRepository: ScanRepository,
  private val appContext: Context
) : ViewModel() {
```

to:

```kotlin
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
  private val imageAnalyzer: ImageAnalyzer,
  private val scanRepository: ScanRepository,
  @ApplicationContext private val appContext: Context
) : ViewModel() {
```

- [ ] **Step 2: Convert ResultViewModel**

In `app/src/main/java/ru/ainetico/honestprice/ui/result/ResultViewModel.kt:51-56`, change:

```kotlin
class ResultViewModel(
  private val scanRepository: ScanRepository,
  private val storeDao: StoreDao,
  private val locationProvider: LocationProvider,
  private val calculator: PriceCalculator
) : ViewModel() {
```

to:

```kotlin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
  private val scanRepository: ScanRepository,
  private val storeDao: StoreDao,
  private val locationProvider: LocationProvider,
  private val calculator: PriceCalculator
) : ViewModel() {
```

- [ ] **Step 3: Convert HistoryViewModel**

In `app/src/main/java/ru/ainetico/honestprice/ui/history/HistoryViewModel.kt:12-14`, change:

```kotlin
class HistoryViewModel(
  scanRepository: ScanRepository
) : ViewModel() {
```

to:

```kotlin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
  scanRepository: ScanRepository
) : ViewModel() {
```

- [ ] **Step 4: Verify build compiles**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ui/camera/CameraViewModel.kt app/src/main/java/ru/ainetico/honestprice/ui/result/ResultViewModel.kt app/src/main/java/ru/ainetico/honestprice/ui/history/HistoryViewModel.kt
git commit -m "refactor: convert ViewModels to @HiltViewModel with @Inject constructor"
```

---

### Task 8: Migrate eager init to ScanPriceApplication

**Files:**
- Modify: `app/src/main/java/ru/ainetico/honestprice/ScanPriceApplication.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/MainActivity.kt`

- [ ] **Step 1: Move engine init logic to ScanPriceApplication**

Replace `ScanPriceApplication` with:

```kotlin
package ru.ainetico.honestprice

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.ainetico.honestprice.model.ModelDownloader
import ru.ainetico.honestprice.ocr.LocalVisionEngine
import javax.inject.Inject

@HiltAndroidApp
class ScanPriceApplication : Application() {

    @Inject lateinit var localVisionEngine: LocalVisionEngine
    @Inject lateinit var modelDownloader: ModelDownloader

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            if (modelDownloader.isModelDownloaded()) {
                Log.i("ScanPriceApplication", "Models already present, initializing vision engine...")
                localVisionEngine.initialize()
                Log.i("ScanPriceApplication", "Vision engine ready: ${localVisionEngine.isAvailable()}")
            } else {
                modelDownloader.state.first { it is ModelDownloader.DownloadState.Completed }
                Log.i("ScanPriceApplication", "Download completed, initializing vision engine...")
                localVisionEngine.initialize()
                Log.i("ScanPriceApplication", "Vision engine ready: ${localVisionEngine.isAvailable()}")
            }
        }
    }
}
```

- [ ] **Step 2: Remove engine init from MainActivity**

In `MainActivity.kt`, remove the class-level properties:

```kotlin
private lateinit var localVisionEngine: LocalVisionEngine
private lateinit var modelDownloader: ModelDownloader
```

And remove the entire engine init block from `onCreate()` (lines 84-96):

```kotlin
localVisionEngine = LocalVisionEngine(applicationContext)
modelDownloader = ModelDownloader(applicationContext)

// Init engine if models already downloaded, otherwise wait for user to trigger download
lifecycleScope.launch {
    if (modelDownloader.isModelDownloaded()) {
        Log.i("MainActivity", "Models already present, initializing vision engine...")
        localVisionEngine.initialize()
        Log.i("MainActivity", "Vision engine ready: ${localVisionEngine.isAvailable()}")
    } else {
        // Wait for user-initiated download to complete
        modelDownloader.state.first { it is ModelDownloader.DownloadState.Completed }
        Log.i("MainActivity", "Download completed, initializing vision engine...")
        localVisionEngine.initialize()
        Log.i("MainActivity", "Vision engine ready: ${localVisionEngine.isAvailable()}")
    }
}
```

Clean up now-unused imports: `LocalVisionEngine`, `ModelDownloader` (from `ru.ainetico.honestprice.model` and `ru.ainetico.honestprice.ocr`), `lifecycleScope`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.flow.first` (check if still used elsewhere in file).

- [ ] **Step 3: Verify build compiles**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ScanPriceApplication.kt app/src/main/java/ru/ainetico/honestprice/MainActivity.kt
git commit -m "refactor: move LocalVisionEngine eager init to Application"
```

---

### Task 9: Rewire MainActivity and composables to use Hilt

This is the main wiring task. The final state of `MainActivity` after this task:
- `@Inject lateinit var` for `appSettings`, `modelDownloader`, `database`, `scanRepository`
- `HonestPriceApp` receives these + `launchAction` + `initialOnboardingCompleted`
- `CameraViewModel` and `HistoryViewModel` use `hiltViewModel()`
- `ResultViewModel` stays manual (per-scan keying)
- `AppNavigationViewModel` stays manual (intent-based)

**Files:**
- Modify: `app/src/main/java/ru/ainetico/honestprice/MainActivity.kt`

- [ ] **Step 1: Add Hilt field injection to MainActivity**

Add after the class declaration:

```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var modelDownloader: ModelDownloader
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var scanRepository: ScanRepository
```

Add import:

```kotlin
import javax.inject.Inject
import ru.ainetico.honestprice.data.AppDatabase
import ru.ainetico.honestprice.data.ScanRepository
```

- [ ] **Step 2: Update onCreate to use injected fields**

Replace:

```kotlin
val appSettings = AppSettings(applicationContext)
val launchAction = intent?.action
val initialOnboardingCompleted = runBlocking { appSettings.onboardingCompleted.first() }

setContent {
    ScanPriceTheme {
        HonestPriceApp(localVisionEngine, modelDownloader, appSettings, launchAction, initialOnboardingCompleted)
    }
}
```

With:

```kotlin
val launchAction = intent?.action
val initialOnboardingCompleted = runBlocking { appSettings.onboardingCompleted.first() }

setContent {
    ScanPriceTheme {
        HonestPriceApp(
            appSettings = appSettings,
            modelDownloader = modelDownloader,
            database = database,
            scanRepository = scanRepository,
            launchAction = launchAction,
            initialOnboardingCompleted = initialOnboardingCompleted
        )
    }
}
```

- [ ] **Step 3: Rewrite HonestPriceApp signature and body**

Change signature to:

```kotlin
@Composable
fun HonestPriceApp(
    appSettings: AppSettings,
    modelDownloader: ModelDownloader,
    database: AppDatabase,
    scanRepository: ScanRepository,
    launchAction: String? = null,
    initialOnboardingCompleted: Boolean = false
) {
```

Replace the manual wiring block:

```kotlin
val db = remember { AppDatabase.getInstance(context) }
// ...
val repository = remember { ScanRepositoryImpl(db.scanDao()) }
val analyzer = remember { ImageAnalyzer(localVisionEngine, PriceCalculator(), appSettings) }
val cameraViewModel = remember { CameraViewModel(analyzer, repository, context.applicationContext) }
```

With:

```kotlin
import androidx.hilt.navigation.compose.hiltViewModel

val cameraViewModel: CameraViewModel = hiltViewModel()
```

Keep `navViewModel` manual:

```kotlin
val navViewModel = remember { AppNavigationViewModel(initialShowCamera = launchAction == ACTION_SCAN) }
```

Update all call sites in the NavHost:

- `OnboardingScreen`: already receives `appSettings` and `modelDownloader` — no change needed
- `HistoryDestination`: pass `db = database, repository = scanRepository, appSettings = appSettings, modelDownloader = modelDownloader`
- `ResultDestination`: pass `repository = scanRepository, db = database`
- `ResultManual` composable: use `scanRepository` and `database`

- [ ] **Step 4: Update HistoryDestination**

Change signature — the type of `repository` changes from `ScanRepositoryImpl` to `ScanRepository`:

```kotlin
@Composable
private fun HistoryDestination(
    db: AppDatabase,
    repository: ScanRepository,
    appSettings: AppSettings,
    modelDownloader: ModelDownloader,
    cameraViewModel: CameraViewModel,
    navViewModel: AppNavigationViewModel,
    showCameraSheet: Boolean,
    context: Context,
    onNavigateToResult: (Long) -> Unit,
    onNavigateToManualEntry: () -> Unit
) {
```

Replace manual `HistoryViewModel` creation:

```kotlin
val historyViewModel = remember { HistoryViewModel(repository) }
```

With:

```kotlin
val historyViewModel: HistoryViewModel = hiltViewModel()
```

The overlay `ResultViewModel` stays manual with `remember(scanId)` — it needs per-scan isolation. The `SettingsScreen` receives `appSettings`, `scanRepository = repository`, and `modelDownloader` from this composable's params.

- [ ] **Step 5: Update ResultDestination**

Change signature — type of `repository` changes from `ScanRepositoryImpl` to `ScanRepository`:

```kotlin
@Composable
private fun ResultDestination(
    scanId: Long,
    navState: ru.ainetico.honestprice.navigation.AppNavigationState,
    repository: ScanRepository,
    db: AppDatabase,
    context: Context,
    navViewModel: AppNavigationViewModel,
    onNavigateToHistory: () -> Unit,
    onPopBack: () -> Unit
) {
```

The `ResultViewModel` manual creation inside stays unchanged — `remember(scanId) { ResultViewModel(...).also { ... } }`.

- [ ] **Step 6: Update ResultManual composable**

In the `composable(Screen.ResultManual.route)` block, replace `repository` and `db` references to use the params threaded from `HonestPriceApp`:

```kotlin
composable(Screen.ResultManual.route) {
    val viewModel = remember {
        ResultViewModel(scanRepository, database.storeDao(), LocationProvider(context), PriceCalculator())
    }
```

- [ ] **Step 7: Clean up imports**

Remove unused imports from `MainActivity.kt`:
- `ru.ainetico.honestprice.analyzer.ImageAnalyzer`
- `ru.ainetico.honestprice.calculator.PriceCalculator` (if no longer referenced directly — check ResultManual)
- `ru.ainetico.honestprice.data.ScanRepositoryImpl`
- `ru.ainetico.honestprice.ocr.LocalVisionEngine` (already removed in Task 8)

Keep imports still needed:
- `ru.ainetico.honestprice.data.AppSettings` (param to OnboardingScreen/SettingsScreen)
- `ru.ainetico.honestprice.model.ModelDownloader` (param to OnboardingScreen/SettingsScreen)
- `ru.ainetico.honestprice.calculator.PriceCalculator` (still used in manual ResultViewModel creation)
- `ru.ainetico.honestprice.location.LocationProvider` (still used in manual ResultViewModel creation)

- [ ] **Step 8: Verify build compiles**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/MainActivity.kt
git commit -m "refactor: rewire HonestPriceApp to use Hilt-injected dependencies"
```

---

### Task 10: Run tests and verify

**Files:** (no changes expected)

- [ ] **Step 1: Run all unit tests**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test 2>&1 | tail -20`
Expected: All tests PASS. Existing tests use manual construction which still works with `@Inject constructor`.

- [ ] **Step 2: Verify debug build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew :app:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Final import cleanup**

Review all modified files for unused imports. Remove any leftover references to manual wiring patterns.

- [ ] **Step 4: Commit cleanup if needed**

```bash
git add -u
git commit -m "chore: clean unused imports after Hilt migration"
```

---

### Task 11: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Update Architecture section**

Changes to make:
- Replace "No DI framework — dependencies manually wired in `MainActivity`" with "Hilt for dependency injection. Modules in `di/` package."
- Update Key Technical Decisions: replace "No DI framework by design" with description of Hilt usage
- Update "Constructor injection for all dependencies" to mention `@Inject constructor` and `@HiltViewModel`
- Add Hilt to SDK & Build section: "Dagger Hilt 2.56.2, Hilt Navigation Compose"
- Note that `ResultViewModel` is still manually created (per-scan keying) and `AppNavigationViewModel` is manual (intent-based)

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md to reflect Hilt DI migration"
```
