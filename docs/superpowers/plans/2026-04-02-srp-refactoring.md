# SRP Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decompose 4 large classes into focused units for testability and readability, plus consolidate duplicated image preprocessing.

**Architecture:** Extract pure logic (SHA256, crop math) into stateless objects, notification logic into a helper class, nav graph into a composable function, and move duplicated bitmap methods into the existing `ImagePreprocessor` class.

**Tech Stack:** Kotlin, Android SDK, Hilt, Jetpack Compose Navigation, JUnit 4/5, Robolectric, MockK

**Spec:** `docs/superpowers/specs/2026-04-02-srp-refactoring-design.md`

---

### Task 1: Extract FileHashVerifier from ModelDownloader

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/model/FileHashVerifier.kt`
- Create: `app/src/test/java/ru/ainetico/honestprice/model/FileHashVerifierTest.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/model/ModelDownloader.kt:287-297` (remove `sha256`), `:246-254` (replace inline verify with `FileHashVerifier.verify`)

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/ru/ainetico/honestprice/model/FileHashVerifierTest.kt
package ru.ainetico.honestprice.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class FileHashVerifierTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `sha256 returns correct hash for known content`() {
        val file = File(tempDir.toFile(), "test.txt").apply { writeText("hello") }
        // sha256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            FileHashVerifier.sha256(file)
        )
    }

    @Test
    fun `verify returns true for matching hash`() {
        val file = File(tempDir.toFile(), "test.txt").apply { writeText("hello") }
        assertTrue(FileHashVerifier.verify(file, "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"))
    }

    @Test
    fun `verify returns false for wrong hash`() {
        val file = File(tempDir.toFile(), "test.txt").apply { writeText("hello") }
        assertFalse(FileHashVerifier.verify(file, "0000000000000000000000000000000000000000000000000000000000000000"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.ainetico.honestprice.model.FileHashVerifierTest" --info`
Expected: FAIL — `FileHashVerifier` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// app/src/main/java/ru/ainetico/honestprice/model/FileHashVerifier.kt
package ru.ainetico.honestprice.model

import java.io.File
import java.security.MessageDigest

object FileHashVerifier {

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(65536)
            var len: Int
            while (input.read(buf).also { len = it } > 0) {
                digest.update(buf, 0, len)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File, expectedHash: String): Boolean =
        sha256(file).equals(expectedHash, ignoreCase = true)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.ainetico.honestprice.model.FileHashVerifierTest" --info`
Expected: PASS — all 3 tests green.

- [ ] **Step 5: Wire into ModelDownloader**

In `ModelDownloader.kt`:
1. Replace lines 246-255 (inline SHA verify block) with:
```kotlin
val actualHash = FileHashVerifier.sha256(destFile)
if (actualHash != expectedHash) {
    destFile.delete()
    downloadManager.remove(downloadId)
    throw SecurityException(
        "SHA256 mismatch for $filename: expected $expectedHash, got $actualHash"
    )
}
```
2. Delete the `private fun sha256(file: File): String` method (lines 287-297).
3. Remove `import java.security.MessageDigest` if now unused.

- [ ] **Step 6: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/model/FileHashVerifier.kt \
       app/src/test/java/ru/ainetico/honestprice/model/FileHashVerifierTest.kt \
       app/src/main/java/ru/ainetico/honestprice/model/ModelDownloader.kt
git commit -m "refactor: extract FileHashVerifier from ModelDownloader"
```

---

### Task 2: Extract DownloadNotificationHelper from ModelDownloader

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/model/DownloadNotificationHelper.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/model/ModelDownloader.kt` (remove notification methods, delegate to helper)

No new tests — notification logic requires `NotificationManager` which is best tested via Robolectric integration. The extraction itself is a pure move-refactor verified by compile.

- [ ] **Step 1: Create DownloadNotificationHelper**

```kotlin
// app/src/main/java/ru/ainetico/honestprice/model/DownloadNotificationHelper.kt
package ru.ainetico.honestprice.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import ru.ainetico.honestprice.R

class DownloadNotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_SILENT = "model_download"
        private const val CHANNEL_ALERT = "model_ready"
        private const val NOTIF_SILENT = 1001
        private const val NOTIF_ALERT = 1002
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_SILENT, context.getString(R.string.download_channel_silent), NotificationManager.IMPORTANCE_LOW)
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERT, context.getString(R.string.download_channel_alert), NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    fun showProgress(title: String, text: String, progress: Int) {
        val builder = NotificationCompat.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progress.coerceIn(0, 100), false)
        notificationManager.notify(NOTIF_SILENT, builder.build())
    }

    fun cancelProgress() {
        notificationManager.cancel(NOTIF_SILENT)
    }

    fun showReadyNotification() {
        notificationManager.notify(NOTIF_ALERT,
            NotificationCompat.Builder(context, CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.download_ready_title))
                .setContentText(context.getString(R.string.download_ready_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }
}
```

- [ ] **Step 2: Refactor ModelDownloader to use helper**

In `ModelDownloader.kt`:
1. Add field: `private val notificationHelper = DownloadNotificationHelper(context)`
2. In `init`, replace `createNotificationChannels()` with `notificationHelper.createChannels()`
3. Replace `showSilentNotification(...)` calls with `notificationHelper.showProgress(...)`
4. Replace `cancelSilentNotification()` with `notificationHelper.cancelProgress()`
5. Replace the public `showReadyNotification()` body with `notificationHelper.showReadyNotification()`
6. Delete private methods: `createNotificationChannels`, `showSilentNotification`, `cancelSilentNotification`, `showHeadsUpNotification`
7. Remove notification-related constants (`CHANNEL_SILENT`, `CHANNEL_ALERT`, `NOTIF_SILENT`, `NOTIF_ALERT`) from companion
8. Remove the `notificationManager` field (`private val notificationManager = ...`) — helper owns it now
9. Remove unused imports: `NotificationChannel`, `NotificationCompat`, `NotificationManager`, `Build` (if unused)

Keep `showReadyNotification()` as a public delegate on `ModelDownloader` so external callers don't break.

- [ ] **Step 3: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/model/DownloadNotificationHelper.kt \
       app/src/main/java/ru/ainetico/honestprice/model/ModelDownloader.kt
git commit -m "refactor: extract DownloadNotificationHelper from ModelDownloader"
```

---

### Task 3: Extract ImageCropper from CameraViewModel

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/ui/camera/ImageCropper.kt`
- Create: `app/src/test/java/ru/ainetico/honestprice/ui/camera/ImageCropperTest.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/ui/camera/CameraViewModel.kt:248-304` (remove crop methods, delegate to ImageCropper)

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/ru/ainetico/honestprice/ui/camera/ImageCropperTest.kt
package ru.ainetico.honestprice.ui.camera

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageCropperTest {

    @Test
    fun `cropToFrame crops center of bitmap`() {
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        // density=1 so dp=px, horizontal frame
        val result = ImageCropper.cropToFrame(bitmap, density = 1f, isVerticalFrame = false)
        // frameWidth = 1000 * 0.85 = 850, frameHeight = 850 / 1.5 = 566
        assertEquals(850, result.width)
        assertEquals(566, result.height)
    }

    @Test
    fun `cropToFrame with vertical frame swaps dimensions`() {
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val result = ImageCropper.cropToFrame(bitmap, density = 1f, isVerticalFrame = true)
        // vertical: frameWidth = baseH = 566, frameHeight = baseW = 850
        assertEquals(566, result.width)
        assertEquals(850, result.height)
    }

    @Test
    fun `cropToFrame returns original when frame exceeds bitmap`() {
        val small = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val result = ImageCropper.cropToFrame(small, density = 3f, isVerticalFrame = false)
        // frame would exceed bounds, so returns original
        assertEquals(50, result.width)
    }

    @Test
    fun `cropAligned produces valid crop`() {
        val bitmap = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888)
        val result = ImageCropper.cropAligned(
            bitmap = bitmap,
            viewW = 1080f, viewH = 1920f,
            panX = 0f, panY = 0f,
            zoom = 1f,
            density = 2f,
            isVerticalFrame = false
        )
        assertTrue(result.width > 0)
        assertTrue(result.height > 0)
        assertTrue(result.width <= bitmap.width)
        assertTrue(result.height <= bitmap.height)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.ainetico.honestprice.ui.camera.ImageCropperTest" --info`
Expected: FAIL — `ImageCropper` does not exist.

- [ ] **Step 3: Write ImageCropper implementation**

```kotlin
// app/src/main/java/ru/ainetico/honestprice/ui/camera/ImageCropper.kt
package ru.ainetico.honestprice.ui.camera

import android.graphics.Bitmap
import android.util.Log
import ru.ainetico.honestprice.FrameConfig

object ImageCropper {

    private const val TAG = "ImageCropper"

    fun cropToFrame(bitmap: Bitmap, density: Float, isVerticalFrame: Boolean): Bitmap {
        val aspectRatio = if (isVerticalFrame) 1f / FrameConfig.ASPECT_RATIO else FrameConfig.ASPECT_RATIO
        val baseW = (bitmap.width * FrameConfig.WIDTH_FRACTION).toInt()
        val baseH = (baseW / FrameConfig.ASPECT_RATIO).toInt()
        val frameWidth = if (aspectRatio >= 1f) baseW else baseH
        val frameHeight = if (aspectRatio >= 1f) baseH else baseW
        val left = (bitmap.width - frameWidth) / 2
        val top = FrameConfig.frameTop(bitmap.height.toFloat(), frameHeight.toFloat(), density).toInt().coerceAtLeast(0)

        if (frameWidth <= 0 || frameHeight <= 0 || left + frameWidth > bitmap.width || top + frameHeight > bitmap.height) {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, left, top, frameWidth, frameHeight)
    }

    fun cropAligned(
        bitmap: Bitmap,
        viewW: Float, viewH: Float,
        panX: Float, panY: Float,
        zoom: Float,
        density: Float,
        isVerticalFrame: Boolean
    ): Bitmap {
        val aspectRatio = if (isVerticalFrame) 1f / FrameConfig.ASPECT_RATIO else FrameConfig.ASPECT_RATIO

        val scaleToFill = viewW / bitmap.width
        val totalScale = scaleToFill * zoom

        val imgCenterX = viewW / 2f
        val imgCenterY = viewH / 2f

        val baseW = viewW * FrameConfig.WIDTH_FRACTION
        val baseH = baseW / FrameConfig.ASPECT_RATIO
        val frameW = if (aspectRatio >= 1f) baseW else baseH
        val frameH = if (aspectRatio >= 1f) baseH else baseW
        val frameLeft = (viewW - frameW) / 2f
        val frameTop = FrameConfig.frameTop(viewH, frameH, density)

        fun viewToBmpX(vx: Float) = ((vx - imgCenterX - panX) / totalScale + bitmap.width / 2f)
        fun viewToBmpY(vy: Float) = ((vy - imgCenterY - panY) / totalScale + bitmap.height / 2f)

        val bmpLeft = viewToBmpX(frameLeft).toInt().coerceIn(0, bitmap.width - 1)
        val bmpTop = viewToBmpY(frameTop).toInt().coerceIn(0, bitmap.height - 1)
        val bmpRight = viewToBmpX(frameLeft + frameW).toInt().coerceIn(bmpLeft + 1, bitmap.width)
        val bmpBottom = viewToBmpY(frameTop + frameH).toInt().coerceIn(bmpTop + 1, bitmap.height)

        val cropW = bmpRight - bmpLeft
        val cropH = bmpBottom - bmpTop

        Log.d(TAG, "cropAligned: view=${viewW}x${viewH} pan=$panX,$panY zoom=$zoom → bmp crop ($bmpLeft,$bmpTop ${cropW}x${cropH})")

        return Bitmap.createBitmap(bitmap, bmpLeft, bmpTop, cropW, cropH)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.ainetico.honestprice.ui.camera.ImageCropperTest" --info`
Expected: PASS — all 4 tests green.

- [ ] **Step 5: Wire into CameraViewModel**

In `CameraViewModel.kt`:
1. Delete `private fun cropToFrame(bitmap: Bitmap): Bitmap` (lines 291-304)
2. Delete `private fun cropAligned(...)` (lines 248-289)
3. Delete `private val currentAspectRatio` property (lines 68-69)
4. Replace all `cropToFrame(bitmap)` calls with:
   ```kotlin
   ImageCropper.cropToFrame(bitmap, appContext.resources.displayMetrics.density, _isVerticalFrame.value)
   ```
5. Replace the `cropAligned(bitmap, viewWidth, viewHeight, offsetX, offsetY, zoom)` call with:
   ```kotlin
   ImageCropper.cropAligned(bitmap, viewWidth, viewHeight, offsetX, offsetY, zoom, appContext.resources.displayMetrics.density, _isVerticalFrame.value)
   ```
6. Remove unused imports (`FrameConfig` if no longer referenced).

- [ ] **Step 6: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Run all tests**

Run: `./gradlew :app:testDebugUnitTest --info`
Expected: All existing tests still pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ui/camera/ImageCropper.kt \
       app/src/test/java/ru/ainetico/honestprice/ui/camera/ImageCropperTest.kt \
       app/src/main/java/ru/ainetico/honestprice/ui/camera/CameraViewModel.kt
git commit -m "refactor: extract ImageCropper from CameraViewModel"
```

---

### Task 4: Extract AppNavGraph from MainActivity

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/MainActivity.kt` (move NavHost + destination composables)

No TDD — Compose Navigation composables are verified by build + manual QA.

- [ ] **Step 1: Create AppNavGraph.kt**

Move the `NavHost { ... }` block (lines 138-201) and the two private composables `HistoryDestination` (lines 204-271) and `ResultDestination` (lines 273-330) into a new file.

```kotlin
// app/src/main/java/ru/ainetico/honestprice/ui/navigation/AppNavGraph.kt
package ru.ainetico.honestprice.ui.navigation

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.data.ScanRepository
import ru.ainetico.honestprice.model.ModelDownloader
import ru.ainetico.honestprice.navigation.AppNavigationViewModel
import ru.ainetico.honestprice.navigation.AppNavigationState
import ru.ainetico.honestprice.navigation.Screen
import ru.ainetico.honestprice.ui.camera.CameraViewModel
import ru.ainetico.honestprice.ui.common.SwipeBackOverlay
import ru.ainetico.honestprice.ui.history.HistoryScreen
import ru.ainetico.honestprice.ui.history.HistoryViewModel
import ru.ainetico.honestprice.ui.onboarding.OnboardingScreen
import ru.ainetico.honestprice.ui.result.ResultScreen
import ru.ainetico.honestprice.ui.result.ResultViewModel
import ru.ainetico.honestprice.widget.updateLastScanWidget

private const val TRANSITION_DURATION = 300

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    appSettings: AppSettings,
    modelDownloader: ModelDownloader,
    scanRepository: ScanRepository,
    cameraViewModel: CameraViewModel,
    navViewModel: AppNavigationViewModel,
    navState: AppNavigationState,
    context: Context
) {
    // ... NavHost block + HistoryDestination + ResultDestination
    // (exact copy of current lines 138-330 from MainActivity.kt)
}
```

The `HistoryDestination` and `ResultDestination` composables become `private` functions in the same file (they were already `private` in MainActivity.kt).

- [ ] **Step 2: Simplify MainActivity.kt**

`HonestPriceApp` now calls `AppNavGraph(...)` instead of containing the NavHost inline. Remove:
- `NavHost` block and everything inside it
- `HistoryDestination` composable
- `ResultDestination` composable
- `TRANSITION_DURATION` constant
- All imports that are now only used in `AppNavGraph.kt`

`HonestPriceApp` becomes ~70 LOC: onboarding state, navController setup, shared VMs, shortcut handling, then `AppNavGraph(...)`.

- [ ] **Step 3: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ui/navigation/AppNavGraph.kt \
       app/src/main/java/ru/ainetico/honestprice/MainActivity.kt
git commit -m "refactor: extract AppNavGraph from MainActivity"
```

---

### Task 5: Consolidate ImagePreprocessor (add toGrayscale + bitmapToJpeg)

**Files:**
- Modify: `app/src/main/java/ru/ainetico/honestprice/image/ImagePreprocessor.kt` (add 2 methods)
- Modify: `app/src/test/java/ru/ainetico/honestprice/image/ImagePreprocessorTest.kt` (add tests)
- Modify: `app/src/main/java/ru/ainetico/honestprice/ocr/LocalVisionEngine.kt` (delegate to ImagePreprocessor)

- [ ] **Step 1: Write failing tests for new methods**

Add to `ImagePreprocessorTest.kt`:

```kotlin
@Test
fun `bitmapToJpeg returns non-empty byte array`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    val result = preprocessor.bitmapToJpeg(bitmap, 80)
    assertTrue(result.isNotEmpty())
}

@Test
fun `bitmapToJpeg starts with JPEG magic bytes`() {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    val result = preprocessor.bitmapToJpeg(bitmap, 80)
    // JPEG starts with FF D8
    assertEquals(0xFF.toByte(), result[0])
    assertEquals(0xD8.toByte(), result[1])
}

@Test
fun `downscaleToMaxSide shrinks large bitmap`() {
    val bitmap = Bitmap.createBitmap(1280, 960, Bitmap.Config.ARGB_8888)
    val result = preprocessor.downscaleToMaxSide(bitmap, 640)
    assertEquals(640, result.width)
    assertEquals(480, result.height)
}

@Test
fun `downscaleToMaxSide returns same bitmap when already small`() {
    val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
    val result = preprocessor.downscaleToMaxSide(bitmap, 640)
    assertSame(bitmap, result)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.ainetico.honestprice.image.ImagePreprocessorTest" --info`
Expected: FAIL — methods don't exist.

- [ ] **Step 3: Add methods to ImagePreprocessor**

Add to `ImagePreprocessor.kt`:

```kotlin
fun bitmapToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
    val stream = java.io.ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return stream.toByteArray()
}

fun downscaleToMaxSide(bitmap: Bitmap, maxSide: Int): Bitmap {
    val longer = maxOf(bitmap.width, bitmap.height)
    if (longer <= maxSide) return bitmap
    val scale = maxSide.toFloat() / longer
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt(),
        (bitmap.height * scale).toInt(),
        true
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.ainetico.honestprice.image.ImagePreprocessorTest" --info`
Expected: PASS — all tests green (old + new).

- [ ] **Step 5: Wire into LocalVisionEngine**

In `LocalVisionEngine.kt`:
1. Add constructor parameter or field: inject/create `ImagePreprocessor`.
   Since `LocalVisionEngine` is not Hilt-managed (manual `Context` constructor), create an instance:
   ```kotlin
   private val imagePreprocessor = ImagePreprocessor()
   ```
2. In `analyze()`, replace:
   ```kotlin
   val cropped = cropToPriceTag(bitmap)
   val resized = downscale(cropped, 640)
   val imageBytes = bitmapToJpeg(resized, quality = 60)
   ```
   with:
   ```kotlin
   val cropped = cropToPriceTag(bitmap)
   val resized = imagePreprocessor.downscaleToMaxSide(cropped, 640)
   val imageBytes = imagePreprocessor.bitmapToJpeg(resized, quality = 60)
   ```
3. Delete private methods: `toGrayscale`, `downscale`, `bitmapToJpeg` (lines 185-212).
4. Remove unused import `java.io.ByteArrayOutputStream`.

Note: Keep `cropToPriceTag` in `LocalVisionEngine` — it uses `FrameConfig` constants but with simplified centering logic specific to the local engine (no density/isVerticalFrame). It's not the same crop as `ImageCropper`.

- [ ] **Step 6: Verify build and all tests**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest --info`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/image/ImagePreprocessor.kt \
       app/src/test/java/ru/ainetico/honestprice/image/ImagePreprocessorTest.kt \
       app/src/main/java/ru/ainetico/honestprice/ocr/LocalVisionEngine.kt
git commit -m "refactor: consolidate image preprocessing into ImagePreprocessor"
```

---

### Task 6: Final verification

- [ ] **Step 1: Run full test suite**

Run: `./gradlew :app:testDebugUnitTest --info`
Expected: All tests pass.

- [ ] **Step 2: Build release APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify LOC reduction**

Quick check that the refactored files are smaller:
```bash
wc -l app/src/main/java/ru/ainetico/honestprice/model/ModelDownloader.kt \
      app/src/main/java/ru/ainetico/honestprice/ui/camera/CameraViewModel.kt \
      app/src/main/java/ru/ainetico/honestprice/MainActivity.kt \
      app/src/main/java/ru/ainetico/honestprice/ocr/LocalVisionEngine.kt
```
Expected: ModelDownloader ~230, CameraViewModel ~240, MainActivity ~120, LocalVisionEngine ~180
