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

**Gradle additions:**
- `com.google.dagger:hilt-android`
- `com.google.dagger:hilt-android-compiler` (KSP)
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

| Provides | Scope |
|----------|-------|
| `LocalVisionEngine` | `@Singleton` |
| `RemoteVisionClient` | `@Singleton` |
| `ImageAnalyzer` | `@Singleton` |
| `ModelDownloader` | `@Singleton` |

**`AppModule` (`@InstallIn(SingletonComponent::class)`):**

| Provides | Scope |
|----------|-------|
| `PriceCalculator` | — (stateless) |
| `LocationProvider` | — |

Context provided via `@ApplicationContext` — no manual passing.

### 3. ViewModels

All ViewModels get `@HiltViewModel` + `@Inject constructor`:

| ViewModel | Injected Dependencies |
|-----------|----------------------|
| `CameraViewModel` | `ImageAnalyzer`, `ScanRepository`, `@ApplicationContext Context` |
| `ResultViewModel` | `ScanRepository`, `StoreDao`, `LocationProvider`, `PriceCalculator` |
| `HistoryViewModel` | `ScanRepository` |
| `AppNavigationViewModel` | `SavedStateHandle` (for `initialShowCamera`) |

**Compose integration:**
- All `remember { ViewModel(...) }` replaced with `hiltViewModel()`
- Each `hiltViewModel()` call in its own Compose scope creates a separate instance (preserves current ResultViewModel × 3 behavior)

**`HonestPriceApp()` cleanup:**
- Remove entire `remember {}` block with manual wiring
- Composable becomes pure navigation logic

### 4. Testing

- Existing unit tests (MockK + manual construction) **unchanged** — `@Inject constructor` doesn't prevent direct instantiation
- No `hilt-android-testing` added — no UI/integration tests exist
- All current tests continue to pass without modification

## Out of Scope

- Scoped components (ActivityComponent, FragmentComponent) — not needed, all singletons
- Assisted injection — no factory patterns currently used
- `hilt-android-testing` — add when UI tests are introduced
- Refactoring service internals — only wiring changes

## Risks

- **KSP + Hilt compatibility**: Hilt's KSP support is stable since Dagger 2.48+. Need to verify version compatibility with project's Kotlin 2.2.10.
- **Build time**: Minimal impact — Hilt KSP is faster than kapt, and project is small.
- **`AppDatabase.getInstance()` singleton**: Hilt will own the singleton lifecycle. Remove the companion `getInstance()` pattern or keep it as a fallback — Hilt module is the source of truth.
