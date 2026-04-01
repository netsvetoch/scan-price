package ru.ainetico.honestprice

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.ainetico.honestprice.model.ModelDownloader
import ru.ainetico.honestprice.analyzer.ImageAnalyzer
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.AppDatabase
import ru.ainetico.honestprice.data.ScanRepositoryImpl
import ru.ainetico.honestprice.navigation.Screen
// import ru.ainetico.honestprice.ocr.BarcodeEngine  // TODO: add barcode scanner separately
import ru.ainetico.honestprice.ocr.LocalVisionEngine
import ru.ainetico.honestprice.location.LocationProvider
import ru.ainetico.honestprice.ui.camera.CameraViewModel
import ru.ainetico.honestprice.ui.history.HistoryScreen
import ru.ainetico.honestprice.ui.history.HistoryViewModel
import ru.ainetico.honestprice.ui.onboarding.OnboardingScreen
import ru.ainetico.honestprice.ui.result.ResultScreen
import ru.ainetico.honestprice.ui.result.ResultViewModel
import ru.ainetico.honestprice.ui.theme.ScanPriceTheme
import ru.ainetico.honestprice.widget.updateLastScanWidget
import kotlin.math.roundToInt

private const val TRANSITION_DURATION = 300

class MainActivity : AppCompatActivity() {

    private lateinit var localVisionEngine: LocalVisionEngine
  private lateinit var modelDownloader: ModelDownloader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request max display refresh rate (120Hz+)
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = display?.supportedModes
                ?.maxByOrNull { it.refreshRate }?.modeId ?: 0
        }

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

        val appSettings = AppSettings(applicationContext)

        val launchAction = intent?.action

        setContent {
            ScanPriceTheme {
                HonestPriceApp(localVisionEngine, modelDownloader, appSettings, launchAction)
            }
        }
    }
}

private const val ACTION_SCAN = "ru.ainetico.honestprice.ACTION_SCAN"
private const val ACTION_GALLERY = "ru.ainetico.honestprice.ACTION_GALLERY"
private const val ACTION_MANUAL = "ru.ainetico.honestprice.ACTION_MANUAL"

@Composable
fun HonestPriceApp(localVisionEngine: LocalVisionEngine, modelDownloader: ModelDownloader, appSettings: AppSettings, launchAction: String? = null) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("honest_price_prefs", Context.MODE_PRIVATE)
    }
    val onboardingCompleted = prefs.getBoolean("onboarding_completed", false)
    val db = remember { AppDatabase.getInstance(context) }
    val startDestination = if (onboardingCompleted) Screen.History.route else Screen.Onboarding.route

    // Shared instances
    val repository = remember { ScanRepositoryImpl(db.scanDao()) }
    val analyzer = remember { ImageAnalyzer(localVisionEngine, PriceCalculator(), appSettings) }
    val cameraViewModel = remember { CameraViewModel(analyzer, repository, context.applicationContext) }
    var showCameraSheet by remember { mutableStateOf(launchAction == ACTION_SCAN) }

    // Gallery launcher for ACTION_GALLERY shortcut
    val shortcutGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            cameraViewModel.importFromGallery(it, context)
            showCameraSheet = true
        }
    }

    // Handle shortcut actions after NavHost is ready
    LaunchedEffect(launchAction) {
        if (!onboardingCompleted) return@LaunchedEffect
        when (launchAction) {
            ACTION_MANUAL -> navController.navigate(Screen.ResultManual.route)
            ACTION_GALLERY -> shortcutGalleryLauncher.launch("image/*")
        }
    }
    var pendingResult by remember { mutableStateOf<Pair<Long, ru.ainetico.honestprice.model.AnalysisResult>?>(null) }
    var pendingScan by remember { mutableStateOf<ru.ainetico.honestprice.data.Scan?>(null) }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(TRANSITION_DURATION)) + fadeIn(tween(TRANSITION_DURATION)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(TRANSITION_DURATION)) + fadeOut(tween(TRANSITION_DURATION / 2)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(TRANSITION_DURATION)) + fadeIn(tween(TRANSITION_DURATION)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(TRANSITION_DURATION)) + fadeOut(tween(TRANSITION_DURATION / 2)) }
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(modelDownloader = modelDownloader, onComplete = {
                navController.navigate(Screen.History.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }
        composable(Screen.History.route) {
            val historyViewModel = remember { HistoryViewModel(repository) }
            var overlayScan by remember { mutableStateOf<ru.ainetico.honestprice.data.Scan?>(null) }

            Box(modifier = Modifier.fillMaxSize()) {
                HistoryScreen(
                    viewModel = historyViewModel,
                    cameraViewModel = cameraViewModel,
                    showSheet = showCameraSheet,
                    onShowSheetChange = { showCameraSheet = it },
                    onScanClick = { scan ->
                        overlayScan = scan
                    },
                    onNavigateToResult = { scanId, result ->
                        pendingResult = Pair(scanId, result)
                        navController.navigate(Screen.Result.createRoute(scanId))
                    },
                    onNavigateToManualEntry = { navController.navigate(Screen.ResultManual.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )

                overlayScan?.let { scan ->
                    val scanId = scan.id
                    val viewModel = remember(scanId) {
                        ResultViewModel(repository, db.storeDao(), LocationProvider(context), PriceCalculator()).also { vm ->
                            vm.loadScan(scan)
                        }
                    }
                    SwipeBackOverlay(
                        onDismiss = { overlayScan = null }
                    ) {
                        ResultScreen(
                            viewModel = viewModel,
                            onSaved = {
                                overlayScan = null
                                kotlinx.coroutines.MainScope().launch { updateLastScanWidget(context) }
                            },
                            onCancel = { overlayScan = null },
                            onDelete = {
                                kotlinx.coroutines.MainScope().launch {
                                    repository.delete(scanId)
                                    updateLastScanWidget(context)
                                }
                                overlayScan = null
                            }
                        )
                    }
                }
            }
        }
        composable(
            route = Screen.Result.route,
            arguments = listOf(navArgument("scanId") { type = NavType.LongType })
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getLong("scanId") ?: 0L
            val pending = pendingResult
            val scan = pendingScan
            val isFreshScan = pending?.first == scanId
            val viewModel = remember(scanId) {
                ResultViewModel(repository, db.storeDao(), LocationProvider(context), PriceCalculator()).also { vm ->
                    if (isFreshScan && pending != null) {
                        // imagePath loaded async inside ResultScreen
                        vm.loadFromAnalysis(scanId, pending.second, null)
                    } else if (scan != null && scan.id == scanId) {
                        vm.loadScan(scan)
                    }
                }
            }
            // Load imagePath asynchronously for fresh scans
            if (isFreshScan) {
                LaunchedEffect(scanId) {
                    val imagePath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        repository.getById(scanId)?.imagePath
                    }
                    if (imagePath != null) {
                        viewModel.updateImagePath(imagePath)
                    }
                }
            }
            ResultScreen(
                viewModel = viewModel,
                onSaved = {
                    pendingResult = null
                    showCameraSheet = false
                    navController.navigate(Screen.History.route) {
                        popUpTo(Screen.History.route) { inclusive = true }
                    }
                    kotlinx.coroutines.MainScope().launch { updateLastScanWidget(context) }
                },
                onCancel = {
                    if (isFreshScan) {
                        kotlinx.coroutines.MainScope().launch {
                            repository.delete(scanId)
                        }
                        pendingResult = null
                    }
                    showCameraSheet = false
                    navController.popBackStack()
                },
                onDelete = if (!isFreshScan) { {
                    kotlinx.coroutines.MainScope().launch {
                        repository.delete(scanId)
                        updateLastScanWidget(context)
                    }
                    showCameraSheet = false
                    navController.popBackStack()
                } } else null
            )
        }
        composable(Screen.ResultManual.route) {
            val viewModel = remember {
                ResultViewModel(repository, db.storeDao(), LocationProvider(context), PriceCalculator())
            }
            LaunchedEffect(Unit) { viewModel.loadManual() }
            ResultScreen(
                viewModel = viewModel,
                onSaved = {
                    navController.navigate(Screen.History.route) {
                        popUpTo(Screen.History.route) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            ru.ainetico.honestprice.ui.settings.SettingsScreen(
                appSettings = appSettings,
                scanRepository = repository,
                modelDownloader = modelDownloader,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun SwipeBackOverlay(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val offsetX = remember { Animatable(screenWidthPx) }
    val scope = rememberCoroutineScope()

    // Slide in on appear
    LaunchedEffect(Unit) {
        offsetX.animateTo(0f, tween(TRANSITION_DURATION))
    }

    val progress = (offsetX.value / screenWidthPx).coerceIn(0f, 1f)

    // Scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f * (1f - progress)))
    )

    // Content with swipe gesture
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value > screenWidthPx * 0.3f) {
                                offsetX.animateTo(screenWidthPx, tween(200))
                                onDismiss()
                            } else {
                                offsetX.animateTo(0f, tween(200))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, tween(200)) }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + dragAmount).coerceAtLeast(0f))
                        }
                    }
                )
            }
    ) {
        content()
    }
}
