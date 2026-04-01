# Hilt DI Migration Design

## Summary

Migrate ScanPrice from manual dependency injection to Hilt (Dagger). Big-bang approach — all dependencies migrated in one pass. The project has 4 ViewModels, ~10 services, and manual wiring in `MainActivity`/`HonestPriceApp()`.

## Motivation

- 8+ dependencies manually wired via `remember {}` in a single composable
- `ResultViewModel` created 3 times with identical dependency trees
- Compile-time graph validation catches wiring errors before runtime
- Cleaner separation: `HonestPriceApp()` becomes pure navigation

## Approach: Big-bang migration

Project is small enough (56 Kotlin files) for a complete migration without intermediate mixed states.

## Design

### 1. Infrastructure

**New `ScanPriceApplication`:**
- `@HiltAndroidApp` annotated Application class
- Registered in `AndroidManifest.xml`

**`MainActivity` changes:**
- Add `@AndroidEntryPoint`
- Remove `lateinit var localVisionEngine` and `modelDownloader` properties
- Remove all manual instantiation from `onCreate()`
- `AppNavigationViewModel` stays manually created in `MainActivity` (see Section 3 for rationale)

**Gradle additions:**
- `com.google.dagger:hilt-android` (2.52+, required for Kotlin 2.x KSP compatibility)
- `com.google.dagger:hilt-android-compiler` (KSP, matching Dagger version)
- `androidx.hilt:hilt-navigation-compose`
- Hilt Gradle plugin (`com.google.dagger.hilt.android`)

### 2. Hilt Modules

All modules in `ru.ainetico.honestprice.di` package.

**`DatabaseModule` (`@InstallIn(SingletonComponent::class)`):**

| Provides | Scope | Source |
|----------|-------|--------|
| `AppDatabase` | `@Singleton` | `AppDatabase.getDatabase(context)` |
| `ScanDao` | — | `db.scanDao()` |
| `StoreDao` | — | `db.storeDao()` |

**`DataModule` (`@InstallIn(SingletonComponent::class)`):**

| Binding | Type |
|---------|------|
| `ScanRepository` ← `ScanRepositoryImpl` | `@Binds @Singleton` |
| `AppSettings` | `@Provides @Singleton` |

**`VisionModule` (`@InstallIn(SingletonComponent::class)`):**

| Provides | Scope | Dependencies |
|----------|-------|-------------|
| `LocalVisionEngine` | `@Singleton` | `@ApplicationContext Context` |
| `RemoteVisionClient` | `@Singleton` | (no-arg) |
| `ImageAnalyzer` | `@Singleton` | `LocalVisionEngine`, `PriceCalculator`, `AppSettings`, `RemoteVisionClient` |
| `ModelDownloader` | `@Singleton` | `@ApplicationContext Context` |

**`AppModule` (`@InstallIn(SingletonComponent::class)`):**

| Provides | Scope |
|----------|-------|
| `PriceCalculator` | — (stateless) |
| `LocationProvider` | — |

Context provided via `@ApplicationContext` — no manual passing.

### 3. ViewModels

**Hilt-managed ViewModels (`@HiltViewModel` + `@Inject constructor`):**

| ViewModel | Injected Dependencies |
|-----------|----------------------|
| `CameraViewModel` | `ImageAnalyzer`, `ScanRepository`, `@ApplicationContext Context` |
| `ResultViewModel` | `ScanRepository`, `StoreDao`, `LocationProvider`, `PriceCalculator` |
| `HistoryViewModel` | `ScanRepository` |

**Manually managed ViewModels:**

| ViewModel | Reason |
|-----------|--------|
| `AppNavigationViewModel` | Requires `initialShowCamera` derived from `intent?.action` at Activity level. Does not fit `@HiltViewModel` pattern — no `SavedStateHandle` route. Stays as `viewModel { AppNavigationViewModel(showCamera) }` in `MainActivity`. |

**Compose integration:**
- `CameraViewModel`: currently a shared singleton via `remember {}` at `HonestPriceApp` level. Use `hiltViewModel()` at the `HonestPriceApp` composable level (activity-scoped `ViewModelStoreOwner`) to preserve singleton behavior across navigation.
- `HistoryViewModel`: `hiltViewModel()` inside `HistoryScreen` NavBackStackEntry — standard per-screen scoping.
- `ResultViewModel` (×3): Two instances in NavHost destinations use `hiltViewModel()` per NavBackStackEntry. The overlay instance in `HistoryDestination` needs a unique `ViewModelStoreOwner` — use `hiltViewModel(key = scanId)` or create a `remember(scanId) { ... }` wrapper with its own `ViewModelStore` to ensure per-scan isolation.

**`HonestPriceApp()` cleanup:**
- Remove entire `remember {}` block with manual wiring
- Composable becomes pure navigation logic
- `AppSettings` and `ModelDownloader` passed to `OnboardingScreen`/`SettingsScreen` are injected via their parent ViewModel or provided through a dedicated `SettingsViewModel` (preferred — keeps composables dependency-free)

### 4. Eager Initialization

`LocalVisionEngine` currently has startup logic in `MainActivity.onCreate()`: if model is downloaded, engine initializes immediately; otherwise waits for download. This orchestration must be preserved.

**Solution:** `ScanPriceApplication.onCreate()` triggers eager init:
```kotlin
@HiltAndroidApp
class ScanPriceApplication : Application() {
    @Inject lateinit var localVisionEngine: LocalVisionEngine
    @Inject lateinit var modelDownloader: ModelDownloader
    // Hilt injects at Application creation → engine is available from app start
}
```
The engine's internal init logic (check model → initialize or wait) stays unchanged inside `LocalVisionEngine`. Hilt just ensures the singleton is created early.

### 5. Edge Cases

**`DataExporter`:** Currently created locally in `ExportSection.kt` composable with a `Context` param. Stays locally created — it's a short-lived utility, not a shared service. No Hilt involvement.

**`LastScanWidget` (Glance):** Accesses `AppDatabase` directly. Glance widgets run outside Activity scope. Use `AppDatabase.getInstance()` in the widget (keep the companion factory). Hilt entry points for widgets (`@AndroidEntryPoint` for `BroadcastReceiver`) can be added later if needed.

**`runBlocking` for `onboardingCompleted`:** Pre-existing issue (blocks main thread at startup). This migration does not worsen it. Out of scope.

### 6. Testing

- Existing unit tests (MockK + manual construction) **unchanged** — `@Inject constructor` doesn't prevent direct instantiation
- No `hilt-android-testing` added — no UI/integration tests exist
- All current tests continue to pass without modification

## Out of Scope

- Scoped components (ActivityComponent, FragmentComponent) — not needed, all singletons
- `hilt-android-testing` — add when UI tests are introduced
- Refactoring service internals — only wiring changes
- `runBlocking` on main thread for onboarding check

## Risks

- **KSP + Hilt compatibility**: Pin Dagger 2.52+ for Kotlin 2.x KSP support. Verify exact version against Kotlin 2.2.10 + KSP 2.2.10-x.
- **Build time**: Minimal impact — Hilt KSP is faster than kapt, and project is small.
- **`AppDatabase.getInstance()` singleton**: Hilt owns the singleton lifecycle for app code. Keep companion `getInstance()` for widget access only.
- **ResultViewModel overlay scoping**: `hiltViewModel()` scopes to `NavBackStackEntry` by default. Overlay instances need explicit key or custom `ViewModelStoreOwner` to avoid sharing.
