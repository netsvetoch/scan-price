# UI + Интеграция: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the complete end-to-end flow: onboarding → camera → OCR result → history, connecting the already-built OCR+Parser data layer to a Jetpack Compose UI.

**Architecture:** Single Activity with Compose Navigation (NavHost). MVVM per screen — each screen has a composable + ViewModel. Data layer (ImageAnalyzer, ScanRepository, LocationProvider) is already implemented.

**Tech Stack:** Kotlin, Jetpack Compose, Compose Navigation, CameraX (AndroidView), Material 3, Room (Flow), Kotlin Coroutines

**Spec:** `docs/superpowers/specs/2026-03-30-ui-screens-design.md`

**Base package:** `ru.ainetico.honestprice`

---

## File Structure

```
app/src/main/java/ru/ainetico/honestprice/
├── MainActivity.kt                          — MODIFY: replace template with NavHost
├── navigation/
│   └── Screen.kt                            — CREATE: sealed class with routes
├── ui/
│   ├── onboarding/
│   │   └── OnboardingScreen.kt              — CREATE: HorizontalPager, 2 pages
│   ├── camera/
│   │   ├── CameraScreen.kt                  — CREATE: CameraX preview + controls
│   │   ├── CameraViewModel.kt               — CREATE: capture, gallery, scanning state
│   │   └── ScanningOverlay.kt               — CREATE: gradient animation
│   ├── result/
│   │   ├── ResultScreen.kt                  — CREATE: edit form + price card
│   │   ├── ResultViewModel.kt               — CREATE: fields, recalc, save
│   │   ├── PriceCard.kt                     — CREATE: green honest price card
│   │   └── StoreComboBox.kt                 — CREATE: ExposedDropdownMenuBox
│   ├── history/
│   │   ├── HistoryScreen.kt                 — CREATE: LazyColumn + FABs
│   │   ├── HistoryViewModel.kt              — CREATE: Flow subscription
│   │   └── ScanCard.kt                      — CREATE: single scan card
│   └── theme/                               — EXISTS (no changes)
├── data/
│   ├── ScanDao.kt                           — MODIFY: add getAllScansFlow()
│   └── ScanRepository.kt                    — MODIFY: add updateUserFields(), createManual()
├── ...                                      — all other packages unchanged

app/src/main/res/values/
└── strings.xml                              — MODIFY: add all UI strings

app/src/main/AndroidManifest.xml             — MODIFY: add CAMERA permission
```

---

## Task 1: Add dependencies and permissions

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add Compose Navigation dependency to libs.versions.toml**

Add to `[versions]`:
```toml
navigationCompose = "2.8.6"
lifecycleViewmodelCompose = "2.8.7"
```

Add to `[libraries]`:
```toml
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
```

- [ ] **Step 2: Add dependencies to app/build.gradle.kts**

Add to dependencies block:
```kotlin
    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
```

- [ ] **Step 3: Add CAMERA permission to AndroidManifest.xml**

Add before `<application>`, after location permissions:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

- [ ] **Step 4: Verify build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "feat: add Compose Navigation, ViewModel Compose, and CAMERA permission"
```

---

## Task 2: Navigation routes and updated MainActivity

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/navigation/Screen.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/MainActivity.kt`

- [ ] **Step 1: Create Screen.kt**

```kotlin
package ru.ainetico.honestprice.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Camera : Screen("camera?openGallery={openGallery}") {
        fun createRoute(openGallery: Boolean = false) = "camera?openGallery=$openGallery"
    }
    object Result : Screen("result/{scanId}") {
        fun createRoute(scanId: Long) = "result/$scanId"
    }
    object ResultManual : Screen("result_manual")
    object History : Screen("history")
}
```

- [ ] **Step 2: Replace MainActivity.kt with NavHost shell**

Replace the entire file content:

```kotlin
package ru.ainetico.honestprice

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.ainetico.honestprice.navigation.Screen
import ru.ainetico.honestprice.ui.theme.ЧестнаяЦенаTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ЧестнаяЦенаTheme {
                HonestPriceApp()
            }
        }
    }
}

@Composable
fun HonestPriceApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("honest_price_prefs", Context.MODE_PRIVATE)
    }
    val onboardingCompleted = prefs.getBoolean("onboarding_completed", false)
    val db = remember { AppDatabase.getInstance(context) }
    val hasScans = remember {
        kotlinx.coroutines.runBlocking {
            db.scanDao().getAllScans().isNotEmpty()
        }
    }
    val startDestination = when {
        !onboardingCompleted -> Screen.Onboarding.route
        hasScans -> Screen.History.route
        else -> Screen.Camera.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Onboarding.route) {
            // TODO: OnboardingScreen - Task 4
            androidx.compose.material3.Text("Onboarding")
        }
        composable(
            route = Screen.Camera.route,
            arguments = listOf(
                navArgument("openGallery") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val openGallery = backStackEntry.arguments?.getBoolean("openGallery") ?: false
            // TODO: CameraScreen - Task 5
            androidx.compose.material3.Text("Camera (openGallery=$openGallery)")
        }
        composable(
            route = Screen.Result.route,
            arguments = listOf(navArgument("scanId") { type = NavType.LongType })
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getLong("scanId") ?: 0L
            // TODO: ResultScreen - Task 7
            androidx.compose.material3.Text("Result (scanId=$scanId)")
        }
        composable(Screen.ResultManual.route) {
            // TODO: ResultScreen manual - Task 7
            androidx.compose.material3.Text("Result Manual")
        }
        composable(Screen.History.route) {
            // TODO: HistoryScreen - Task 9
            androidx.compose.material3.Text("History")
        }
    }
}
```

- [ ] **Step 3: Verify build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/navigation/ app/src/main/java/ru/ainetico/honestprice/MainActivity.kt
git commit -m "feat: add navigation routes and NavHost shell in MainActivity"
```

---

## Task 3: Update data layer (ScanDao + ScanRepository)

**Files:**
- Modify: `app/src/main/java/ru/ainetico/honestprice/data/ScanDao.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/data/ScanRepository.kt`

- [ ] **Step 1: Add getAllScansFlow() to ScanDao**

Add import and new method to `ScanDao.kt`:

```kotlin
import kotlinx.coroutines.flow.Flow
```

Add after `getAllScans()`:
```kotlin
    @Query("SELECT * FROM scans WHERE status != 'PROCESSING' ORDER BY createdAt DESC")
    fun getAllScansFlow(): Flow<List<Scan>>
```

- [ ] **Step 2: Add new methods to ScanRepository interface and impl**

Add to the `ScanRepository` interface:
```kotlin
    suspend fun createManual(): Long
    suspend fun updateUserFields(
        scanId: Long,
        tag: ParsedPriceTag,
        price: PriceResult?,
        storeName: String?,
        latitude: Double?,
        longitude: Double?
    )
    fun getAllScansFlow(): Flow<List<Scan>>
    suspend fun getById(scanId: Long): Scan?
```

Add import to `ScanRepository.kt`:
```kotlin
import kotlinx.coroutines.flow.Flow
```

Add implementations to `ScanRepositoryImpl`:
```kotlin
    override suspend fun createManual(): Long {
        return scanDao.insert(Scan(status = ScanStatus.PROCESSING))
    }

    override suspend fun updateUserFields(
        scanId: Long,
        tag: ParsedPriceTag,
        price: PriceResult?,
        storeName: String?,
        latitude: Double?,
        longitude: Double?
    ) {
        val existing = scanDao.getById(scanId) ?: return
        val newStatus = if (existing.status == ScanStatus.COMPLETED || existing.status == ScanStatus.EDITED) {
            ScanStatus.EDITED
        } else {
            ScanStatus.COMPLETED
        }
        scanDao.update(
            existing.copy(
                status = newStatus,
                productName = tag.productName,
                priceRegular = tag.priceRegular?.toPlainString(),
                priceDiscount = tag.priceDiscount?.toPlainString(),
                weightValue = tag.weightValue?.toPlainString(),
                weightUnit = tag.weightUnit?.name,
                barcode = tag.barcode,
                pricePerUnit = price?.pricePerUnit?.toPlainString(),
                pricePerUnitDiscount = price?.pricePerUnitDiscount?.toPlainString(),
                displayUnit = price?.displayUnit?.name,
                storeName = storeName,
                latitude = latitude,
                longitude = longitude
            )
        )
    }

    override fun getAllScansFlow(): Flow<List<Scan>> {
        return scanDao.getAllScansFlow()
    }

    override suspend fun getById(scanId: Long): Scan? {
        return scanDao.getById(scanId)
    }
```

- [ ] **Step 3: Verify existing tests still pass**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/data/ScanDao.kt app/src/main/java/ru/ainetico/honestprice/data/ScanRepository.kt
git commit -m "feat: add getAllScansFlow, updateUserFields, createManual to data layer"
```

---

## Task 4: OnboardingScreen

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/ui/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add onboarding strings to strings.xml**

Add to `app/src/main/res/values/strings.xml` inside `<resources>`:
```xml
    <string name="onboarding_camera_title">Сканируйте ценник</string>
    <string name="onboarding_camera_text">Наведите камеру на ценник — мы рассчитаем честную цену за кг или литр</string>
    <string name="onboarding_camera_hint">Также можно загрузить фото из галереи или ввести данные вручную</string>
    <string name="onboarding_location_title">Сохраняйте местоположение</string>
    <string name="onboarding_location_text">Это поможет запоминать магазин и добавить новые функции в будущем</string>
    <string name="onboarding_next">Далее</string>
    <string name="onboarding_allow">Разрешить</string>
    <string name="onboarding_skip">Пропустить</string>
```

- [ ] **Step 2: Create OnboardingScreen.kt**

```kotlin
package ru.ainetico.honestprice.ui.onboarding

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ainetico.honestprice.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        completeOnboarding(context, onComplete)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> OnboardingPage(
                        icon = { Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary) },
                        title = stringResource(R.string.onboarding_camera_title),
                        text = stringResource(R.string.onboarding_camera_text),
                        hint = stringResource(R.string.onboarding_camera_hint),
                        primaryButtonText = stringResource(R.string.onboarding_next),
                        onPrimaryClick = { scope.launch { pagerState.animateScrollToPage(1) } }
                    )
                    1 -> OnboardingPage(
                        icon = { Icon(Icons.Filled.LocationOn, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary) },
                        title = stringResource(R.string.onboarding_location_title),
                        text = stringResource(R.string.onboarding_location_text),
                        hint = null,
                        primaryButtonText = stringResource(R.string.onboarding_allow),
                        onPrimaryClick = {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        secondaryButtonText = stringResource(R.string.onboarding_skip),
                        onSecondaryClick = { completeOnboarding(context, onComplete) }
                    )
                }
            }

            // Page indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(2) { index ->
                    val color = if (pagerState.currentPage == index)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(8.dp)
                            .background(color, androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    icon: @Composable () -> Unit,
    title: String,
    text: String,
    hint: String?,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onPrimaryClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(primaryButtonText)
        }
        if (secondaryButtonText != null && onSecondaryClick != null) {
            TextButton(onClick = onSecondaryClick) {
                Text(secondaryButtonText)
            }
        }
    }
}

private fun completeOnboarding(context: Context, onComplete: () -> Unit) {
    context.getSharedPreferences("honest_price_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("onboarding_completed", true)
        .apply()
    onComplete()
}
```

- [ ] **Step 3: Wire OnboardingScreen into NavHost in MainActivity.kt**

Replace the onboarding composable block in `HonestPriceApp()`:
```kotlin
        composable(Screen.Onboarding.route) {
            OnboardingScreen(onComplete = {
                navController.navigate(Screen.Camera.createRoute()) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }
```

Add import:
```kotlin
import ru.ainetico.honestprice.ui.onboarding.OnboardingScreen
```

- [ ] **Step 4: Verify build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ui/onboarding/ app/src/main/java/ru/ainetico/honestprice/MainActivity.kt app/src/main/res/values/strings.xml
git commit -m "feat: add OnboardingScreen with camera intro and location permission"
```

---

## Task 5: CameraViewModel

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/ui/camera/CameraViewModel.kt`

- [ ] **Step 1: Create CameraViewModel.kt**

```kotlin
package ru.ainetico.honestprice.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ainetico.honestprice.analyzer.ImageAnalyzer
import ru.ainetico.honestprice.data.ScanRepository
import java.io.File
import java.io.FileOutputStream

sealed class CameraState {
    object Preview : CameraState()
    data class Scanning(val previewBitmap: Bitmap) : CameraState()
}

sealed class CameraEvent {
    data class NavigateToResult(val scanId: Long) : CameraEvent()
    object NavigateToManualEntry : CameraEvent()
}

class CameraViewModel(
    private val imageAnalyzer: ImageAnalyzer,
    private val scanRepository: ScanRepository,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow<CameraState>(CameraState.Preview)
    val state: StateFlow<CameraState> = _state

    private val _event = MutableStateFlow<CameraEvent?>(null)
    val event: StateFlow<CameraEvent?> = _event

    private var scanningJob: Job? = null

    fun capture(bitmap: Bitmap, cropRect: Rect?) {
        _state.value = CameraState.Scanning(bitmap)
        scanningJob = viewModelScope.launch {
            try {
                val result = processImage(bitmap, cropRect)
                _event.value = CameraEvent.NavigateToResult(result)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Processing failed", e)
                _state.value = CameraState.Preview
            }
        }
    }

    fun importFromGallery(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                _state.value = CameraState.Scanning(bitmap)
                val scanId = processImage(bitmap, cropRect = null)
                _event.value = CameraEvent.NavigateToResult(scanId)
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Gallery import failed", e)
                _state.value = CameraState.Preview
            }
        }
    }

    fun retake() {
        scanningJob?.cancel()
        _state.value = CameraState.Preview
    }

    fun onManualEntry() {
        _event.value = CameraEvent.NavigateToManualEntry
    }

    fun eventConsumed() {
        _event.value = null
    }

    private suspend fun processImage(bitmap: Bitmap, cropRect: Rect?): Long {
        return withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val imagesDir = File(appContext.filesDir, "images/originals").apply { mkdirs() }
            val imagePath = File(imagesDir, "scan_${timestamp}.jpg").absolutePath

            FileOutputStream(imagePath).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val scanId = scanRepository.createProcessing(imagePath)
            val analysisResult = imageAnalyzer.analyze(bitmap, cropRect)
            scanRepository.markCompleted(scanId, analysisResult.tag, analysisResult.price)

            // Generate thumbnail
            val thumbDir = File(appContext.filesDir, "images/thumbnails").apply { mkdirs() }
            val thumbPath = File(thumbDir, "thumb_${scanId}_${timestamp}.jpg").absolutePath
            val thumbWidth = 200
            val scale = thumbWidth.toFloat() / bitmap.width
            val thumb = Bitmap.createScaledBitmap(
                bitmap,
                thumbWidth,
                (bitmap.height * scale).toInt(),
                true
            )
            FileOutputStream(thumbPath).use { out ->
                thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            scanId
        }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ui/camera/CameraViewModel.kt
git commit -m "feat: add CameraViewModel with capture, gallery import, and scanning state"
```

---

## Task 6: CameraScreen + ScanningOverlay

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/ui/camera/ScanningOverlay.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/ui/camera/CameraScreen.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add camera strings to strings.xml**

```xml
    <string name="camera_gallery">Галерея</string>
    <string name="camera_capture">Снять</string>
    <string name="camera_manual">Вручную</string>
    <string name="camera_retake">Переснять</string>
    <string name="camera_scanning">Сканирование…</string>
    <string name="camera_permission_needed">Для сканирования нужен доступ к камере</string>
    <string name="camera_open_settings">Открыть настройки</string>
```

- [ ] **Step 2: Create ScanningOverlay.kt**

```kotlin
package ru.ainetico.honestprice.ui.camera

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun ScanningOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val lineHeight = 4f
        val gradientHeight = size.height * 0.15f
        val y = scanLineY * size.height

        // Gradient band above the scan line
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0x4400E676)),
                startY = (y - gradientHeight).coerceAtLeast(0f),
                endY = y
            ),
            topLeft = Offset(0f, (y - gradientHeight).coerceAtLeast(0f)),
            size = Size(size.width, gradientHeight.coerceAtMost(y))
        )

        // Scan line
        drawRect(
            color = Color(0xFF00E676),
            topLeft = Offset(0f, y),
            size = Size(size.width, lineHeight)
        )
    }
}
```

- [ ] **Step 3: Create CameraScreen.kt**

```kotlin
package ru.ainetico.honestprice.ui.camera

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import ru.ainetico.honestprice.R

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    openGallery: Boolean = false,
    onNavigateToResult: (Long) -> Unit,
    onNavigateToManualEntry: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val event by viewModel.event.collectAsState()
    val context = LocalContext.current

    // Handle navigation events
    LaunchedEffect(event) {
        when (val e = event) {
            is CameraEvent.NavigateToResult -> {
                onNavigateToResult(e.scanId)
                viewModel.eventConsumed()
            }
            is CameraEvent.NavigateToManualEntry -> {
                onNavigateToManualEntry()
                viewModel.eventConsumed()
            }
            null -> {}
        }
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importFromGallery(it, context) }
    }

    // Open gallery immediately if requested from History FAB
    LaunchedEffect(openGallery) {
        if (openGallery) {
            galleryLauncher.launch("image/*")
        }
    }

    // Camera permission
    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        val result = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Shared reference to PreviewView for capture
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            is CameraState.Preview -> {
                if (hasCameraPermission) {
                    CameraPreview(
                        onPreviewViewReady = { previewViewRef = it }
                    )
                } else {
                    CameraPermissionDenied(onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        })
                    })
                }

                // Bottom controls
                CameraControls(
                    onGalleryClick = { galleryLauncher.launch("image/*") },
                    onCaptureClick = { previewViewRef?.bitmap?.let { viewModel.capture(it, cropRect = null) } },
                    onManualClick = { viewModel.onManualEntry() }
                )
            }
            is CameraState.Scanning -> {
                // Show frozen preview with scanning animation
                Image(
                    bitmap = s.previewBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                ScanningOverlay()

                // Retake button
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 48.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    FilledTonalButton(onClick = { viewModel.retake() }) {
                        Text(stringResource(R.string.camera_retake))
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onPreviewViewReady: (PreviewView) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }.also {
                previewView = it
                onPreviewViewReady(it)
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    LaunchedEffect(previewView) {
        val pv = previewView ?: return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = pv.surfaceProvider
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )
            } catch (e: Exception) {
                // Camera initialization failed
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Overlay: darkened area with cutout frame
    Canvas(modifier = Modifier.fillMaxSize()) {
        val frameWidth = size.width * 0.85f
        val frameHeight = frameWidth * 0.5f
        val frameLeft = (size.width - frameWidth) / 2
        val frameTop = (size.height - frameHeight) / 2

        // Darken outside frame
        // Top
        drawRect(Color(0x80000000), Offset.Zero, Size(size.width, frameTop))
        // Bottom
        drawRect(Color(0x80000000), Offset(0f, frameTop + frameHeight), Size(size.width, size.height - frameTop - frameHeight))
        // Left
        drawRect(Color(0x80000000), Offset(0f, frameTop), Size(frameLeft, frameHeight))
        // Right
        drawRect(Color(0x80000000), Offset(frameLeft + frameWidth, frameTop), Size(frameLeft, frameHeight))

        // Frame border
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(frameLeft, frameTop),
            size = Size(frameWidth, frameHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )
    }
}

@Composable
private fun CameraControls(
    onGalleryClick: () -> Unit,
    onCaptureClick: () -> Unit,
    onManualClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 32.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery button
            IconButton(
                onClick = onGalleryClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0x40FFFFFF), CircleShape)
            ) {
                Icon(Icons.Filled.Collections, stringResource(R.string.camera_gallery), tint = Color.White)
            }

            // Capture button
            IconButton(
                onClick = onCaptureClick,
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White, CircleShape)
                    .padding(4.dp)
                    .background(Color(0xFFEEEEEE), CircleShape)
            ) {
                // Empty — the circle IS the button
            }

            // Manual button
            IconButton(
                onClick = onManualClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0x40FFFFFF), CircleShape)
            ) {
                Icon(Icons.Filled.Edit, stringResource(R.string.camera_manual), tint = Color.White)
            }
        }
    }
}

@Composable
private fun CameraPermissionDenied(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.camera_permission_needed),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.camera_open_settings))
        }
    }
}

```

NOTE: The imports for `CornerRadius`, `Offset`, `Size`, `Stroke` must be placed at the top of the file alongside the other imports, NOT at the bottom. Also remove the unused `com.google.accompanist.permissions.ExperimentalPermissionsApi` import — the accompanist library is not used.

- [ ] **Step 4: Wire CameraScreen into NavHost**

Replace the camera composable block in `HonestPriceApp()`:
```kotlin
        composable(
            route = Screen.Camera.route,
            arguments = listOf(
                navArgument("openGallery") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val openGallery = backStackEntry.arguments?.getBoolean("openGallery") ?: false
            val db = remember { AppDatabase.getInstance(context) }
            val repository = remember { ScanRepositoryImpl(db.scanDao()) }
            val analyzer = remember {
                ImageAnalyzer(
                    ImagePreprocessor(),
                    OcrEngine(),
                    BarcodeEngine(),
                    PriceTagParser(),
                    PriceCalculator()
                )
            }
            val viewModel = remember { CameraViewModel(analyzer, repository, context.applicationContext) }

            CameraScreen(
                viewModel = viewModel,
                openGallery = openGallery,
                onNavigateToResult = { scanId ->
                    navController.navigate(Screen.Result.createRoute(scanId))
                },
                onNavigateToManualEntry = {
                    navController.navigate(Screen.ResultManual.route)
                }
            )
        }
```

Add needed imports to `MainActivity.kt`:
```kotlin
import ru.ainetico.honestprice.ui.camera.CameraScreen
import ru.ainetico.honestprice.ui.camera.CameraViewModel
import ru.ainetico.honestprice.data.AppDatabase
import ru.ainetico.honestprice.data.ScanRepositoryImpl
import ru.ainetico.honestprice.analyzer.ImageAnalyzer
import ru.ainetico.honestprice.image.ImagePreprocessor
import ru.ainetico.honestprice.ocr.OcrEngine
import ru.ainetico.honestprice.ocr.BarcodeEngine
import ru.ainetico.honestprice.parser.PriceTagParser
import ru.ainetico.honestprice.calculator.PriceCalculator
```

- [ ] **Step 5: Verify build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ui/camera/ app/src/main/java/ru/ainetico/honestprice/MainActivity.kt app/src/main/res/values/strings.xml
git commit -m "feat: add CameraScreen with CameraX preview, scanning overlay, and controls"
```

---

## Task 7: ResultViewModel

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/ui/result/ResultViewModel.kt`

- [ ] **Step 1: Create ResultViewModel.kt**

```kotlin
package ru.ainetico.honestprice.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.ScanRepository
import ru.ainetico.honestprice.data.Store
import ru.ainetico.honestprice.data.StoreDao
import ru.ainetico.honestprice.location.LocationProvider
import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.WeightUnit
import java.math.BigDecimal

data class ResultState(
    val scanId: Long? = null,
    val productName: String = "",
    val priceRegular: String = "",
    val priceDiscount: String = "",
    val weightValue: String = "",
    val weightUnit: WeightUnit = WeightUnit.PCS,
    val availableUnits: List<WeightUnit> = listOf(WeightUnit.G, WeightUnit.KG, WeightUnit.ML, WeightUnit.L, WeightUnit.PCS),
    val storeName: String = "",
    val barcode: String = "",
    val imagePath: String? = null,
    val pricePerUnit: String = "",
    val pricePerUnitDiscount: String = "",
    val displayUnit: WeightUnit = WeightUnit.PCS,
    val isManualEntry: Boolean = false,
    val isSaving: Boolean = false
)

sealed class ResultEvent {
    object Saved : ResultEvent()
}

class ResultViewModel(
    private val scanRepository: ScanRepository,
    private val storeDao: StoreDao,
    private val locationProvider: LocationProvider,
    private val calculator: PriceCalculator
) : ViewModel() {

    private val _state = MutableStateFlow(ResultState())
    val state: StateFlow<ResultState> = _state

    private val _storeSuggestions = MutableStateFlow<List<Store>>(emptyList())
    val storeSuggestions: StateFlow<List<Store>> = _storeSuggestions

    private val _event = MutableStateFlow<ResultEvent?>(null)
    val event: StateFlow<ResultEvent?> = _event

    fun loadScan(scanId: Long) {
        viewModelScope.launch {
            val scan = scanRepository.getById(scanId) ?: return@launch
            val unit = scan.weightUnit?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }
            val availableUnits = when (unit) {
                WeightUnit.G, WeightUnit.KG -> listOf(WeightUnit.G, WeightUnit.KG)
                WeightUnit.ML, WeightUnit.L -> listOf(WeightUnit.ML, WeightUnit.L)
                else -> listOf(WeightUnit.G, WeightUnit.KG, WeightUnit.ML, WeightUnit.L, WeightUnit.PCS)
            }
            _state.value = ResultState(
                scanId = scanId,
                productName = scan.productName ?: "",
                priceRegular = scan.priceRegular ?: "",
                priceDiscount = scan.priceDiscount ?: "",
                weightValue = scan.weightValue ?: "",
                weightUnit = unit ?: WeightUnit.PCS,
                availableUnits = availableUnits,
                storeName = scan.storeName ?: "",
                barcode = scan.barcode ?: "",
                imagePath = scan.imagePath,
                isManualEntry = false
            )
            recalculatePrice()
        }
    }

    fun loadManual() {
        _state.value = ResultState(isManualEntry = true)
    }

    fun updateProductName(value: String) {
        _state.update { it.copy(productName = value) }
    }

    fun updatePriceRegular(value: String) {
        _state.update { it.copy(priceRegular = value) }
        recalculatePrice()
    }

    fun updatePriceDiscount(value: String) {
        _state.update { it.copy(priceDiscount = value) }
        recalculatePrice()
    }

    fun updateWeightValue(value: String) {
        _state.update { it.copy(weightValue = value) }
        recalculatePrice()
    }

    fun selectUnit(unit: WeightUnit) {
        _state.update { it.copy(weightUnit = unit) }
        recalculatePrice()
    }

    fun updateStoreName(value: String) {
        _state.update { it.copy(storeName = value) }
        searchStores(value)
    }

    fun updateBarcode(value: String) {
        _state.update { it.copy(barcode = value) }
    }

    fun eventConsumed() {
        _event.value = null
    }

    private fun searchStores(query: String) {
        viewModelScope.launch {
            _storeSuggestions.value = if (query.isBlank()) {
                storeDao.getAllStores()
            } else {
                storeDao.search(query)
            }
        }
    }

    private fun recalculatePrice() {
        val s = _state.value
        val regular = s.priceRegular.toBigDecimalOrNull()
        val discount = s.priceDiscount.toBigDecimalOrNull()
        val weight = s.weightValue.toBigDecimalOrNull()

        val tag = ParsedPriceTag(
            priceRegular = regular,
            priceDiscount = discount,
            weightValue = weight,
            weightUnit = s.weightUnit
        )
        val result = calculator.calculate(tag)
        _state.update {
            it.copy(
                pricePerUnit = result?.pricePerUnit?.setScale(2, java.math.RoundingMode.HALF_UP)?.toPlainString() ?: "",
                pricePerUnitDiscount = result?.pricePerUnitDiscount?.setScale(2, java.math.RoundingMode.HALF_UP)?.toPlainString() ?: "",
                displayUnit = result?.displayUnit ?: s.weightUnit
            )
        }
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? {
        return try {
            val cleaned = this.replace(',', '.')
            if (cleaned.isBlank()) null else BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun save() {
        val s = _state.value
        if (s.isSaving) return
        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val scanId = s.scanId ?: withContext(Dispatchers.IO) {
                    scanRepository.createManual()
                }

                val location = withContext(Dispatchers.IO) {
                    locationProvider.getCurrentLocation()
                }

                if (s.storeName.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        storeDao.insert(Store(name = s.storeName))
                    }
                }

                val tag = ParsedPriceTag(
                    productName = s.productName.ifBlank { null },
                    priceRegular = s.priceRegular.toBigDecimalOrNull(),
                    priceDiscount = s.priceDiscount.toBigDecimalOrNull(),
                    weightValue = s.weightValue.toBigDecimalOrNull(),
                    weightUnit = s.weightUnit,
                    barcode = s.barcode.ifBlank { null }
                )
                val price = calculator.calculate(tag)

                withContext(Dispatchers.IO) {
                    scanRepository.updateUserFields(
                        scanId = scanId,
                        tag = tag,
                        price = price,
                        storeName = s.storeName.ifBlank { null },
                        latitude = location?.latitude,
                        longitude = location?.longitude
                    )
                }

                _event.value = ResultEvent.Saved
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ui/result/ResultViewModel.kt
git commit -m "feat: add ResultViewModel with field editing, price recalculation, and save"
```

---

## Task 8: ResultScreen + PriceCard + StoreComboBox

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/ui/result/PriceCard.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/ui/result/StoreComboBox.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/ui/result/ResultScreen.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add result strings to strings.xml**

```xml
    <string name="result_product_name">Название товара</string>
    <string name="result_price_regular">Цена</string>
    <string name="result_price_discount">По скидке</string>
    <string name="result_weight">Вес / Объём</string>
    <string name="result_unit">Единица</string>
    <string name="result_store">Магазин</string>
    <string name="result_barcode">Штрихкод</string>
    <string name="result_honest_price">Честная цена</string>
    <string name="result_regular_per_unit">обычная: %s ₽/%s</string>
    <string name="result_save">Сохранить</string>
    <string name="result_saving">Сохранение…</string>
```

- [ ] **Step 2: Create PriceCard.kt**

```kotlin
package ru.ainetico.honestprice.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.model.WeightUnit

@Composable
fun PriceCard(
    pricePerUnit: String,
    pricePerUnitDiscount: String,
    displayUnit: WeightUnit,
    modifier: Modifier = Modifier
) {
    if (pricePerUnit.isBlank() && pricePerUnitDiscount.isBlank()) return

    val mainPrice = pricePerUnitDiscount.ifBlank { pricePerUnit }
    val showRegular = pricePerUnitDiscount.isNotBlank() && pricePerUnit.isNotBlank()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF4CAF50), Color(0xFF2E7D32))
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.result_honest_price),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$mainPrice ₽/${displayUnit.displayName}",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            if (showRegular) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.result_regular_per_unit, pricePerUnit, displayUnit.displayName),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }
    }
}
```

- [ ] **Step 3: Create StoreComboBox.kt**

```kotlin
package ru.ainetico.honestprice.ui.result

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.data.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreComboBox(
    value: String,
    suggestions: List<Store>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.result_store)) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )

        if (suggestions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                suggestions.forEach { store ->
                    DropdownMenuItem(
                        text = { Text(store.name) },
                        onClick = {
                            onValueChange(store.name)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Create ResultScreen.kt**

```kotlin
package ru.ainetico.honestprice.ui.result

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.data.Store
import ru.ainetico.honestprice.model.WeightUnit

@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    onSaved: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val suggestions by viewModel.storeSuggestions.collectAsState()
    val event by viewModel.event.collectAsState()

    LaunchedEffect(event) {
        if (event is ResultEvent.Saved) {
            onSaved()
            viewModel.eventConsumed()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Photo preview
            if (state.imagePath != null) {
                val bitmap = remember(state.imagePath) {
                    BitmapFactory.decodeFile(state.imagePath)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Product name
            OutlinedTextField(
                value = state.productName,
                onValueChange = { viewModel.updateProductName(it) },
                label = { Text(stringResource(R.string.result_product_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Prices row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.priceRegular,
                    onValueChange = { viewModel.updatePriceRegular(it) },
                    label = { Text(stringResource(R.string.result_price_regular)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    suffix = { Text("₽") }
                )
                OutlinedTextField(
                    value = state.priceDiscount,
                    onValueChange = { viewModel.updatePriceDiscount(it) },
                    label = { Text(stringResource(R.string.result_price_discount)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    suffix = { Text("₽") }
                )
            }

            // Weight + unit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.weightValue,
                    onValueChange = { viewModel.updateWeightValue(it) },
                    label = { Text(stringResource(R.string.result_weight)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            // Unit selector - Segmented Button
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                state.availableUnits.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = state.weightUnit == unit,
                        onClick = { viewModel.selectUnit(unit) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = state.availableUnits.size
                        )
                    ) {
                        Text(unit.displayName)
                    }
                }
            }

            // Store
            StoreComboBox(
                value = state.storeName,
                suggestions = suggestions,
                onValueChange = { viewModel.updateStoreName(it) }
            )

            // Barcode
            OutlinedTextField(
                value = state.barcode,
                onValueChange = { viewModel.updateBarcode(it) },
                label = { Text(stringResource(R.string.result_barcode)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = state.barcode.isNotBlank() && !state.isManualEntry
            )

            // Price card
            PriceCard(
                pricePerUnit = state.pricePerUnit,
                pricePerUnitDiscount = state.pricePerUnitDiscount,
                displayUnit = state.displayUnit
            )

            // Save button
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                enabled = !state.isSaving
            ) {
                Text(
                    if (state.isSaving) stringResource(R.string.result_saving)
                    else stringResource(R.string.result_save)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
```

- [ ] **Step 5: Wire ResultScreen into NavHost in MainActivity.kt**

Replace the result composable blocks:
```kotlin
        composable(
            route = Screen.Result.route,
            arguments = listOf(navArgument("scanId") { type = NavType.LongType })
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getLong("scanId") ?: 0L
            val db = remember { AppDatabase.getInstance(context) }
            val repository = remember { ScanRepositoryImpl(db.scanDao()) }
            val viewModel = remember {
                ResultViewModel(repository, db.storeDao(), LocationProvider(context), PriceCalculator())
            }
            LaunchedEffect(scanId) { viewModel.loadScan(scanId) }

            ResultScreen(
                viewModel = viewModel,
                onSaved = {
                    navController.navigate(Screen.History.route) {
                        popUpTo(Screen.Camera.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.ResultManual.route) {
            val db = remember { AppDatabase.getInstance(context) }
            val repository = remember { ScanRepositoryImpl(db.scanDao()) }
            val viewModel = remember {
                ResultViewModel(repository, db.storeDao(), LocationProvider(context), PriceCalculator())
            }
            LaunchedEffect(Unit) { viewModel.loadManual() }

            ResultScreen(
                viewModel = viewModel,
                onSaved = {
                    navController.navigate(Screen.History.route) {
                        popUpTo(Screen.Camera.route) { inclusive = true }
                    }
                }
            )
        }
```

Add imports:
```kotlin
import ru.ainetico.honestprice.ui.result.ResultScreen
import ru.ainetico.honestprice.ui.result.ResultViewModel
import ru.ainetico.honestprice.location.LocationProvider
```

- [ ] **Step 6: Verify build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ui/result/ app/src/main/java/ru/ainetico/honestprice/MainActivity.kt app/src/main/res/values/strings.xml
git commit -m "feat: add ResultScreen with editable fields, price card, and store combobox"
```

---

## Task 9: HistoryViewModel + HistoryScreen + ScanCard

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/ui/history/HistoryViewModel.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/ui/history/ScanCard.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/ui/history/HistoryScreen.kt`
- Modify: `app/src/main/java/ru/ainetico/honestprice/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add history strings to strings.xml**

```xml
    <string name="history_empty">Нет сканирований</string>
    <string name="history_empty_hint">Нажмите 📷 чтобы начать</string>
    <string name="history_fab_camera">Камера</string>
    <string name="history_fab_gallery">Галерея</string>
    <string name="history_fab_manual">Вручную</string>
```

- [ ] **Step 2: Create HistoryViewModel.kt**

```kotlin
package ru.ainetico.honestprice.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.ainetico.honestprice.data.Scan
import ru.ainetico.honestprice.data.ScanRepository

class HistoryViewModel(
    scanRepository: ScanRepository
) : ViewModel() {
    val scans: StateFlow<List<Scan>> = scanRepository.getAllScansFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

- [ ] **Step 3: Create ScanCard.kt**

```kotlin
package ru.ainetico.honestprice.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ainetico.honestprice.data.Scan
import ru.ainetico.honestprice.model.WeightUnit

@Composable
fun ScanCard(scan: Scan, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scan.productName ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (!scan.storeName.isNullOrBlank()) {
                    Text(
                        text = scan.storeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                val priceText = buildString {
                    scan.priceRegular?.let { append("$it ₽") }
                    scan.priceDiscount?.let { append(" → $it ₽") }
                    scan.weightValue?.let { w ->
                        val unitName = scan.weightUnit?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }?.displayName ?: ""
                        append(" / $w $unitName")
                    }
                }
                if (priceText.isNotBlank()) {
                    Text(
                        text = priceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Honest price
            val honestPrice = scan.pricePerUnitDiscount ?: scan.pricePerUnit
            val unitName = scan.displayUnit?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }?.displayName ?: ""
            if (honestPrice != null) {
                Text(
                    text = "$honestPrice ₽/$unitName",
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    }
}
```

- [ ] **Step 4: Create HistoryScreen.kt**

```kotlin
package ru.ainetico.honestprice.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.ainetico.honestprice.R

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onScanClick: (Long) -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onManualClick: () -> Unit
) {
    val scans by viewModel.scans.collectAsState()

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(onClick = onManualClick) {
                    Icon(Icons.Filled.Edit, stringResource(R.string.history_fab_manual))
                }
                SmallFloatingActionButton(onClick = onGalleryClick) {
                    Icon(Icons.Filled.Collections, stringResource(R.string.history_fab_gallery))
                }
                LargeFloatingActionButton(onClick = onCameraClick) {
                    Icon(Icons.Filled.CameraAlt, stringResource(R.string.history_fab_camera))
                }
            }
        }
    ) { padding ->
        if (scans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.history_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scans, key = { it.id }) { scan ->
                    ScanCard(
                        scan = scan,
                        onClick = { onScanClick(scan.id) }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: Wire HistoryScreen into NavHost**

Replace the history composable block:
```kotlin
        composable(Screen.History.route) {
            val db = remember { AppDatabase.getInstance(context) }
            val repository = remember { ScanRepositoryImpl(db.scanDao()) }
            val viewModel = remember { HistoryViewModel(repository) }

            HistoryScreen(
                viewModel = viewModel,
                onScanClick = { scanId ->
                    navController.navigate(Screen.Result.createRoute(scanId))
                },
                onCameraClick = {
                    navController.navigate(Screen.Camera.createRoute(openGallery = false))
                },
                onGalleryClick = {
                    navController.navigate(Screen.Camera.createRoute(openGallery = true))
                },
                onManualClick = {
                    navController.navigate(Screen.ResultManual.route)
                }
            )
        }
```

Add imports:
```kotlin
import ru.ainetico.honestprice.ui.history.HistoryScreen
import ru.ainetico.honestprice.ui.history.HistoryViewModel
```

- [ ] **Step 6: Verify build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ui/history/ app/src/main/java/ru/ainetico/honestprice/MainActivity.kt app/src/main/res/values/strings.xml
git commit -m "feat: add HistoryScreen with LazyColumn, ScanCard, and FAB navigation"
```

---

## Task 10: Final build verification and all tests

- [ ] **Step 1: Run all unit tests**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, all 44 existing tests pass

- [ ] **Step 2: Verify full build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Final commit if any fixes needed**

Only if tests/build revealed issues.
