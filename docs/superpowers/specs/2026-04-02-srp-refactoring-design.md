# SRP Refactoring Design

## Goals

- **Testability**: extract pure logic that is hard to unit-test when embedded in large classes
- **Readability**: reduce cognitive load by giving each file a single clear responsibility

## Scope

4 extractions + 1 consolidation. `AppSettings` (93 LOC) stays as-is.

---

## 1. FileHashVerifier

**Source:** `ModelDownloader.kt` (~20 LOC)

**What:** Pure `object` with no Android dependencies.

```kotlin
// model/FileHashVerifier.kt
object FileHashVerifier {
    fun sha256(file: File): String
    fun verify(file: File, expectedHash: String): Boolean
}
```

**Rationale:** SHA256 computation and comparison is a self-contained concern. Extracting it enables unit testing without Robolectric or mocking DownloadManager.

**Impact on ModelDownloader:** Replace inline hash logic with `FileHashVerifier.verify(file, hash)`. ModelDownloader drops ~20 LOC.

---

## 2. DownloadNotificationHelper

**Source:** `ModelDownloader.kt` (~50 LOC)

**What:** Class that encapsulates notification channel creation and notification building.

```kotlin
// model/DownloadNotificationHelper.kt
class DownloadNotificationHelper(private val context: Context) {
    fun createChannel()
    fun buildProgressNotification(label: String, progress: Int, max: Int): Notification
    fun buildCompletionNotification(): Notification
    fun showReadyNotification()  // public API used by ScanPriceApplication
    fun notify(id: Int, notification: Notification)
    fun cancel(id: Int)
}
```

**Rationale:** Notification logic is purely presentational and orthogonal to download orchestration. Separating it lets ModelDownloader focus on download lifecycle.

**Impact on ModelDownloader:** Inject `DownloadNotificationHelper` and call its methods instead of inline `NotificationCompat.Builder` chains. ModelDownloader drops to ~200 LOC.

---

## 3. ImageCropper

**Source:** `CameraViewModel.kt` (~60 LOC)

**What:** `object` with stateless geometry functions. All Android-derived values (density, aspect ratio) are passed as parameters, making the functions testable without context.

```kotlin
// ui/camera/ImageCropper.kt
object ImageCropper {
    fun cropToFrame(
        bitmap: Bitmap,
        density: Float,
        isVerticalFrame: Boolean,
        viewWidth: Int, viewHeight: Int
    ): Bitmap

    fun cropAligned(
        bitmap: Bitmap,
        panX: Float, panY: Float,
        zoom: Float,
        density: Float,
        isVerticalFrame: Boolean,
        displayWidth: Int, displayHeight: Int
    ): Bitmap
}
```

Both functions compute frame geometry internally using `FrameConfig` + the provided `density` and `isVerticalFrame`. The caller (CameraViewModel) passes these values from its state and `appContext.resources.displayMetrics.density`.

**Rationale:** Crop coordinate math is the most complex logic in CameraViewModel and the hardest to test in-place. By surfacing `density` and `isVerticalFrame` as parameters instead of reading them from Context/StateFlow, the functions become testable with arbitrary inputs.

**Impact on CameraViewModel:** Replace inline crop logic with `ImageCropper.cropToFrame(...)` / `ImageCropper.cropAligned(...)`. ViewModel drops to ~240 LOC and focuses on state management.

---

## 4. AppNavGraph

**Source:** `MainActivity.kt` (~130 LOC)

**What:** Composable function in a dedicated file.

```kotlin
// ui/navigation/AppNavGraph.kt
@Composable
fun AppNavGraph(
    navController: NavHostController,
    appNavViewModel: AppNavigationViewModel,
    // other shared state parameters
)
```

Contains the `NavHost { ... }` block with all `composable()` destination declarations.

**Rationale:** Navigation graph assembly is the single largest block in MainActivity. Extracting it makes MainActivity a thin shell: `setContent` + DI + shortcut routing. Each concern is readable independently.

**Impact on MainActivity:** Drops to ~120 LOC. Calls `AppNavGraph(navController, ...)` inside `setContent`.

---

## 5. ImagePreprocessor Consolidation

**Source:** `LocalVisionEngine.kt` (duplicate methods)

**What:** Add `toGrayscale()` and `bitmapToJpeg()` to the existing `ImagePreprocessor` class (`image/ImagePreprocessor.kt`). No new classes created. `ImagePreprocessor` is a Hilt-injected `class` (not `object`), and stays that way.

```kotlin
// Added to existing ImagePreprocessor class
class ImagePreprocessor @Inject constructor() {
    // existing: cropBitmap, processBitmap, processFile, calculateInSampleSize
    fun toGrayscale(bitmap: Bitmap): Bitmap   // new
    fun bitmapToJpeg(bitmap: Bitmap, quality: Int): ByteArray  // new
}
```

**Rationale:** Eliminates duplication. LocalVisionEngine delegates all image manipulation to the shared utility.

**Impact on LocalVisionEngine:** Remove ~25 LOC of duplicated methods. Import `ImagePreprocessor.toGrayscale` / `.bitmapToJpeg` instead.

---

## What We Do NOT Touch

- **AppSettings.kt** (93 LOC) — acceptable size for dual-backend settings
- **RemoteVisionClient.kt** — already focused on one concern
- **PriceTagParser, PriceCalculator, ScanRepository** — already well-extracted
- DI module structure, navigation routes, database layer

## Testing Strategy

| Extraction | Test Type | Framework |
|---|---|---|
| FileHashVerifier | Pure JUnit 5 | Create temp file, verify hash |
| DownloadNotificationHelper | Robolectric JUnit 4 | Verify notification content |
| ImageCropper | JUnit 4 + Robolectric | Bitmap assertions on known inputs |
| AppNavGraph | No new tests | Compose Navigation; existing manual QA covers it |
| ImagePreprocessor additions | JUnit 4 + Robolectric | Bitmap grayscale/JPEG assertions |

## Risk Assessment

- **Low risk:** All extractions are move-refactors (cut code, paste into new file, replace with call). No behavioral changes.
- **Compile-time safety:** Kotlin compiler catches any missed references immediately.
- **Incremental:** Each extraction is independent — can be done and verified one at a time.
