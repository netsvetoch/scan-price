# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**ScanPrice (Ценоскан)** — Android app that scans Russian price tags using a vision LLM, extracts structured data (product name, prices, weight), and calculates per-unit prices for comparison shopping.

## Build Commands

```bash
./gradlew :app:assembleDebug        # Build debug APK
./gradlew :app:installDebug          # Build & install on connected device
./gradlew test                       # Run all unit tests
./gradlew :app:testDebugUnitTest     # Run debug unit tests only
./gradlew :llama-lib:build           # Rebuild native C++ library (llama.cpp JNI)
./gradlew clean                      # Full clean
adb logcat -s AndroidRuntime         # Read crash logs from connected device
```

## Architecture

**MVVM with Jetpack Compose + Hilt DI.** Package: `ru.ainetico.honestprice`. DI modules in `di/` package. Navigation state managed by `AppNavigationViewModel`.

### Layers

- **UI** (`ui/`): 100% Compose screens. Each feature folder has a Screen + ViewModel. State via `StateFlow`. One-shot events (navigation, snackbar) use `Channel<Event>(Channel.BUFFERED)` + `receiveAsFlow()`, NOT `StateFlow<Event?>` + `eventConsumed()`. Camera screen is decomposed into extracted composables: `CameraPreview`, `FrameOverlay`, `CameraPermissionContent`, `AdjustingContent` (gesture handler), each in its own file. Crop geometry lives in `ImageCropper` object (`ui/camera/`), not in the ViewModel. Navigation graph in `ui/navigation/AppNavGraph.kt`; `MainActivity` is a thin shell.
- **No `runBlocking` on main thread.** For async data needed before first frame (e.g. onboarding check), use nullable state + `LaunchedEffect` + early return: `var x by remember { mutableStateOf<T?>(null) }; LaunchedEffect(Unit) { x = flow.first() }; val value = x ?: return`.
- **Service** (`ocr/`, `analyzer/`): Vision engine abstraction. `ImageAnalyzer` routes to local or remote engine based on user settings.
- **Data** (`data/`): Room database (`honest_price.db`), `ScanRepository` interface, `AppSettings` (Preferences DataStore for non-sensitive settings as `Flow<T>`; EncryptedSharedPreferences for API credentials as `StateFlow<T>`).
- **Model** (`model/`): `ParsedPriceTag` is the core data class output from vision engines. `FileHashVerifier` (SHA256), `DownloadNotificationHelper` (download notifications) extracted from `ModelDownloader`.
- **Calculator** (`calculator/`): `PriceCalculator` converts prices to per-unit for comparison. Validates business values: rejects negative/zero prices and weights, ignores discount ≥ regular price.

### Navigation Flow

`Camera → Result → History` (plus Settings and Onboarding). Routes defined in `navigation/Screen.kt`.
Settings and scan detail views render as swipe-back overlays (`ui/common/SwipeBackOverlay`), not NavHost routes.

### Dual Vision Pipeline

`ImageAnalyzer` orchestrates two engines via the `VisionEngine` interface (`ocr/VisionEngine.kt`). Both return `VisionResult.Success` | `VisionResult.Error` — never throw from `analyze()`. `ImageAnalyzer` converts errors to `RemoteAnalysisException` or `LocalAnalysisException` for the UI layer.

1. **`LocalVisionEngine`** — On-device llama.cpp inference with a GGUF model (Qwen3.5-0.8B). Preprocesses images to 640px max, JPEG quality 60. Slow (30-120s) but fully offline.
2. **`RemoteVisionClient`** — OpenAI-compatible API via OkHttp. Base64 JPEG + JSON Schema for structured output. Reads endpoint/model/key from injected `AppSettings`.

`image/ImagePreprocessor` is the canonical home for bitmap utilities (`bitmapToJpeg`, `downscaleToMaxSide`, `cropBitmap`). Add new bitmap helpers there, not inside engine classes.

The remote engine is preferred when configured; local is the offline fallback.

Both engines delegate JSON→`ParsedPriceTag` conversion to `PriceTagParser` (shared `object` in `ocr/`). Weight-unit mapping, `BigDecimal` safe parsing, and null-coalescing live there — edit once, not twice.

### Native Layer

`llama-lib/` is a CMake-based C++ module bridging llama.cpp (git submodule) via JNI. ARM64-v8a only.

## Key Technical Decisions

- **No on-device OCR** (Tesseract/ML Kit). They fail on Russian Cyrillic price tags. The app uses multimodal vision LLMs instead — image in, structured JSON out.
- **Local engine uses grammar-constrained decoding.** JSON Schema is converted to GBNF grammar via llama.cpp, guaranteeing valid JSON output from the local model.
- **Hilt DI** with modules in `di/`: `DatabaseModule`, `DataModule`, `VisionModule`, `AppModule`. `@HiltViewModel` on `CameraViewModel`, `ResultViewModel`, `HistoryViewModel`. All use `hiltViewModel()` in Compose; overlay instances keyed via `hiltViewModel(key = ...)`. `AppNavigationViewModel` stays manual (intent-derived state).
- **Constructor `@Inject` for all dependencies.** No internal `= ConcreteClass()` creation — keeps classes testable and Hilt-compatible.
- **Eager init in `ScanPriceApplication`**: `LocalVisionEngine` and `ModelDownloader` injected and initialized at app startup via `@Inject lateinit var`.
- **History uses Paging 3** with `insertSeparators()` for date headers. `HistoryViewModel` exposes `Flow<PagingData<ScanListItem>>` (sealed interface: `ScanItem` | `DateHeader`). `formatRelativeDate` needs `Context` so date label formatting stays in Compose, not ViewModel.

## Database

Room with 2 entities: `Scan` (price tag data + image path + GPS, indexed on `createdAt` and `status`) and `Store` (autocomplete). Migrations: v1→v2 (added `productDescription`), v2→v3 (added indexes).

## SDK & Build

- Kotlin 2.2.10, Compose BOM 2025.12.00, Room 2.7.1, CameraX 1.4.1, OkHttp 4.12, Hilt 2.59.2, Paging 3.3.6, security-crypto 1.0.0
- Min SDK 24, Target/Compile SDK 36
- KSP for annotation processing (Room, Hilt)
- ProGuard enabled for release builds
- Release build: R8 minification + resource shrinking enabled. Signing via `keystore.properties` (gitignored) with debug fallback. See `keystore.properties.example` for format.

### Build Gotchas

- **`security-crypto` 1.0.0 uses `MasterKeys` API** (not `MasterKey.Builder` from 1.1.0-alpha). Don't upgrade to alpha — no stable 1.1.0 exists.
- **Gradle KTS imports**: `java.util.Properties` (and similar) must use `import` at top of `.kts` file — inline `java.util.Properties()` fails with "Unresolved reference 'util'"
- **`BuildConfig` requires opt-in** on AGP 9+: `buildFeatures { buildConfig = true }` in `app/build.gradle.kts`. Needed for `BuildConfig.DEBUG` guards on sensitive logging.
- **Hilt requires 2.59+** for AGP 9.x compatibility (AGP 9 dropped `BaseExtension`)
- **Hilt + KSP classloader**: `ksp` plugin must be declared `apply false` in root `build.gradle.kts` alongside `hilt`
- **Don't add `kotlin-android` explicitly** — `kotlin.compose` already registers the kotlin extension
- **`./gradlew test` fails** on `llama-lib` (missing junit dep). Use `:app:testDebugUnitTest` for app tests
- **GGUF model SHA256 hashes** in `ModelDownloader.kt` are hardcoded. If HuggingFace updates a file, the download will fail with `SecurityException`. Update hashes after verifying the new file.
- **`ModelDownloader` coroutine scope** uses `SupervisorJob` — child exceptions from `scope.launch` don't propagate to parent. Use `async`/`await` when exceptions must be caught by the caller.
- **Room `PagingSource` requires `room-paging`** artifact (`androidx.room:room-paging`). Without it, KSP fails with `Cannot find required type element LimitOffsetPagingSource`.
- **JUnit 5 on Android** requires `useJUnitPlatform()` in `testOptions`, plus `junit-platform-launcher` and `junit-vintage-engine` runtime deps (vintage keeps JUnit 4 tests working).

## Testing

JUnit 4 + JUnit 5 + MockK + Robolectric. Tests cover `PriceCalculator`, `WeightUnit`, `ImagePreprocessor`, `ImageCropper`, `FileHashVerifier`, `ImageAnalyzer`, `RemoteVisionClient`, `ScanRepository`, `DataExporter`. No UI tests. `PriceCalculatorTest` uses JUnit 5 `@ParameterizedTest`/`@CsvSource`; all others use JUnit 4.

### Testing Gotchas

- Pure `object`s with only `java.*` deps (e.g. `FileHashVerifier`) use plain JUnit 5 — no Robolectric needed. Classes using `Bitmap` or `android.*` need Robolectric (JUnit 4).
- Classes using `android.util.Log` need `@RunWith(RobolectricTestRunner::class)`
- `BigDecimal` equality: use `compareTo() == 0`, not `assertEquals` (scale differs: `89.90` ≠ `89.9`)
- `PriceResult` requires `source: ParsedPriceTag` parameter — easy to miss in test constructors
- `AppSettings` mocking: non-sensitive fields (`systemPrompt`, `localPrompt`, etc.) use `flowOf("")`; encrypted fields (`apiUrl`, `apiKey`, `apiModel`) use `MutableStateFlow("")`; `isRemoteModelConfigured()` needs `coEvery` (suspend)
- Vision engine mocking: `coEvery { engine.analyze(any(), any()) }` must return `VisionResult.Success(tag)` or `VisionResult.Error(msg)`, not raw `ParsedPriceTag`. `RemoteVisionClient` constructor requires `AppSettings` (use `mockk<AppSettings>()` in tests that only call `parseResponse`).

## Security

- Response/JSON content logging in `ocr/` guarded by `if (BuildConfig.DEBUG)` — prevents leaking API responses in release builds
- API key/URL/model stored in `EncryptedSharedPreferences` (`secure_settings`), backed by Android Keystore AES-256-GCM
- `AppSettings`: credential setters use `commit()` (synchronous, EncryptedSharedPreferences); non-sensitive setters are `suspend fun` via DataStore. `isRemoteModelConfigured()` is also `suspend`.
- CSV export sanitizes formula-triggering characters (`=`, `+`, `-`, `@`, `\t`) with tab prefix to prevent injection
- `RemoteVisionClient` and `RemoteModelSection` use OkHttp via shared `ApiHttpClient` singleton (`ocr/ApiHttpClient.kt`). Response bodies auto-closed via `.use {}`.
