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
```

## Architecture

**MVVM with Jetpack Compose.** No DI framework — dependencies manually wired in `MainActivity`. Navigation state managed by `AppNavigationViewModel`.

### Layers

- **UI** (`ui/`): 100% Compose screens. Each feature folder has a Screen + ViewModel. State via `StateFlow`.
- **Service** (`ocr/`, `analyzer/`): Vision engine abstraction. `ImageAnalyzer` routes to local or remote engine based on user settings.
- **Data** (`data/`): Room database (`honest_price.db`), `ScanRepository` interface, `AppSettings` (EncryptedSharedPreferences for API credentials, plain SharedPreferences for non-sensitive settings).
- **Model** (`model/`): `ParsedPriceTag` is the core data class output from vision engines.
- **Calculator** (`calculator/`): `PriceCalculator` converts prices to per-unit for comparison.

### Navigation Flow

`Camera → Result → History` (plus Settings and Onboarding). Routes defined in `navigation/Screen.kt`.
Settings and scan detail views render as swipe-back overlays (`ui/common/SwipeBackOverlay`), not NavHost routes.

### Dual Vision Pipeline

`ImageAnalyzer` orchestrates two engines:

1. **`LocalVisionEngine`** — On-device llama.cpp inference with a GGUF model (Qwen3.5-0.8B). Preprocesses images to 640px max, JPEG quality 60. Slow (30-120s) but fully offline.
2. **`RemoteVisionClient`** — OpenAI-compatible API. Base64 JPEG + JSON Schema for structured output. Configurable endpoint/model/key in settings.

The remote engine is preferred when configured; local is the offline fallback.

### Native Layer

`llama-lib/` is a CMake-based C++ module bridging llama.cpp (git submodule) via JNI. ARM64-v8a only.

## Key Technical Decisions

- **No on-device OCR** (Tesseract/ML Kit). They fail on Russian Cyrillic price tags. The app uses multimodal vision LLMs instead — image in, structured JSON out.
- **Local engine uses grammar-constrained decoding.** JSON Schema is converted to GBNF grammar via llama.cpp, guaranteeing valid JSON output from the local model.
- **`BarcodeEngine`** (ML Kit) is integrated but not wired to UI yet.
- **No DI framework by design** — the app is small enough for manual wiring.

## Database

Room with 2 entities: `Scan` (price tag data + image path + GPS) and `Store` (autocomplete). One migration (v1→v2: added `productDescription`).

## SDK & Build

- Kotlin 2.2.10, Compose BOM 2025.12.00, Room 2.7.1, CameraX 1.4.1
- Min SDK 24, Target/Compile SDK 36
- KSP for annotation processing (Room)
- ProGuard enabled for release builds

## Testing

JUnit 4 + MockK + Robolectric. Tests cover `PriceCalculator`, `WeightUnit`, `ImagePreprocessor`, `ImageAnalyzer`, `RemoteVisionClient`, `ScanRepository`, `DataExporter`. No UI tests.

### Testing Gotchas

- Classes using `android.util.Log` need `@RunWith(RobolectricTestRunner::class)`
- `BigDecimal` equality: use `compareTo() == 0`, not `assertEquals` (scale differs: `89.90` ≠ `89.9`)
- `PriceResult` requires `source: ParsedPriceTag` parameter — easy to miss in test constructors

## Security

- API key/URL/model stored in `EncryptedSharedPreferences` (`secure_settings`), backed by Android Keystore AES-256-GCM
- `AppSettings` uses `commit()` (synchronous) for credential setters and migration; `apply()` (async) for non-critical settings — prevents data loss on crash
- CSV export sanitizes formula-triggering characters (`=`, `+`, `-`, `@`, `\t`) with tab prefix to prevent injection
- `RemoteVisionClient` HTTP connections are closed in `finally` blocks
