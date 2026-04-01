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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ainetico.honestprice.model.ModelDownloader
import ru.ainetico.honestprice.analyzer.ImageAnalyzer
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.AppDatabase
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.data.ScanRepositoryImpl
import ru.ainetico.honestprice.location.LocationProvider
import ru.ainetico.honestprice.navigation.AppNavigationViewModel
import ru.ainetico.honestprice.navigation.Screen
import ru.ainetico.honestprice.ocr.LocalVisionEngine
import ru.ainetico.honestprice.ui.camera.CameraViewModel
import ru.ainetico.honestprice.ui.common.SwipeBackOverlay
import ru.ainetico.honestprice.ui.history.HistoryScreen
import ru.ainetico.honestprice.ui.history.HistoryViewModel
import ru.ainetico.honestprice.ui.onboarding.OnboardingScreen
import ru.ainetico.honestprice.ui.result.ResultScreen
import ru.ainetico.honestprice.ui.result.ResultViewModel
import ru.ainetico.honestprice.ui.theme.ScanPriceTheme
import ru.ainetico.honestprice.widget.updateLastScanWidget

private const val TRANSITION_DURATION = 300
private const val ACTION_SCAN = "ru.ainetico.honestprice.ACTION_SCAN"
private const val ACTION_GALLERY = "ru.ainetico.honestprice.ACTION_GALLERY"
private const val ACTION_MANUAL = "ru.ainetico.honestprice.ACTION_MANUAL"

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
        val initialOnboardingCompleted = runBlocking { appSettings.onboardingCompleted.first() }

        setContent {
            ScanPriceTheme {
                HonestPriceApp(localVisionEngine, modelDownloader, appSettings, launchAction, initialOnboardingCompleted)
            }
        }
    }
}

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

    // Shared instances
    val repository = remember { ScanRepositoryImpl(db.scanDao()) }
    val analyzer = remember { ImageAnalyzer(localVisionEngine, PriceCalculator(), appSettings) }
    val cameraViewModel = remember { CameraViewModel(analyzer, repository, context.applicationContext) }
    val navViewModel = remember { AppNavigationViewModel(initialShowCamera = launchAction == ACTION_SCAN) }
    val navState by navViewModel.state.collectAsState()

    // Gallery launcher for ACTION_GALLERY shortcut
    val shortcutGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            cameraViewModel.importFromGallery(it, context)
            navViewModel.setShowCameraSheet(true)
        }
    }

    // Handle shortcut actions after NavHost is ready
    LaunchedEffect(launchAction) {
        if (!initialOnboardingCompleted) return@LaunchedEffect
        when (launchAction) {
            ACTION_MANUAL -> navController.navigate(Screen.ResultManual.route)
            ACTION_GALLERY -> shortcutGalleryLauncher.launch("image/*")
        }
    }

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
            HistoryDestination(
                db = db,
                repository = repository,
                appSettings = appSettings,
                modelDownloader = modelDownloader,
                cameraViewModel = cameraViewModel,
                navViewModel = navViewModel,
                showCameraSheet = navState.showCameraSheet,
                context = context,
                onNavigateToResult = { scanId ->
                    navController.navigate(Screen.Result.createRoute(scanId))
                },
                onNavigateToManualEntry = { navController.navigate(Screen.ResultManual.route) }
            )
        }
        composable(
            route = Screen.Result.route,
            arguments = listOf(navArgument("scanId") { type = NavType.LongType })
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getLong("scanId") ?: 0L
            ResultDestination(
                scanId = scanId,
                navState = navState,
                repository = repository,
                db = db,
                context = context,
                navViewModel = navViewModel,
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route) {
                        popUpTo(Screen.History.route) { inclusive = true }
                    }
                },
                onPopBack = { navController.popBackStack() }
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
    }
}

@Composable
private fun HistoryDestination(
    db: AppDatabase,
    repository: ScanRepositoryImpl,
    appSettings: AppSettings,
    modelDownloader: ModelDownloader,
    cameraViewModel: CameraViewModel,
    navViewModel: AppNavigationViewModel,
    showCameraSheet: Boolean,
    context: Context,
    onNavigateToResult: (Long) -> Unit,
    onNavigateToManualEntry: () -> Unit
) {
    val historyViewModel = remember { HistoryViewModel(repository) }
    var overlayScan by remember { mutableStateOf<ru.ainetico.honestprice.data.Scan?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        HistoryScreen(
            viewModel = historyViewModel,
            cameraViewModel = cameraViewModel,
            showSheet = showCameraSheet,
            onShowSheetChange = { navViewModel.setShowCameraSheet(it) },
            onScanClick = { scan -> overlayScan = scan },
            onNavigateToResult = { scanId, result ->
                navViewModel.setPendingResult(scanId, result)
                onNavigateToResult(scanId)
            },
            onNavigateToManualEntry = onNavigateToManualEntry,
            onNavigateToSettings = { showSettings = true }
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
                        MainScope().launch { updateLastScanWidget(context) }
                    },
                    onCancel = { overlayScan = null },
                    onDelete = {
                        MainScope().launch {
                            repository.delete(scanId)
                            updateLastScanWidget(context)
                        }
                        overlayScan = null
                    }
                )
            }
        }

        if (showSettings) {
            SwipeBackOverlay(onDismiss = { showSettings = false }) {
                ru.ainetico.honestprice.ui.settings.SettingsScreen(
                    appSettings = appSettings,
                    scanRepository = repository,
                    modelDownloader = modelDownloader,
                    onBack = { showSettings = false }
                )
            }
        }
    }
}

@Composable
private fun ResultDestination(
    scanId: Long,
    navState: ru.ainetico.honestprice.navigation.AppNavigationState,
    repository: ScanRepositoryImpl,
    db: AppDatabase,
    context: Context,
    navViewModel: AppNavigationViewModel,
    onNavigateToHistory: () -> Unit,
    onPopBack: () -> Unit
) {
    val pending = navState.pendingResult
    val scan = navState.pendingScan
    val isFreshScan = pending?.first == scanId
    val viewModel = remember(scanId) {
        ResultViewModel(repository, db.storeDao(), LocationProvider(context), PriceCalculator()).also { vm ->
            if (isFreshScan && pending != null) {
                vm.loadFromAnalysis(scanId, pending.second, null)
            } else if (scan != null && scan.id == scanId) {
                vm.loadScan(scan)
            }
        }
    }
    // Load imagePath asynchronously for fresh scans
    if (isFreshScan) {
        LaunchedEffect(scanId) {
            val imagePath = withContext(Dispatchers.IO) {
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
            navViewModel.clearPendingResult()
            navViewModel.setShowCameraSheet(false)
            onNavigateToHistory()
            MainScope().launch { updateLastScanWidget(context) }
        },
        onCancel = {
            if (isFreshScan) {
                MainScope().launch { repository.delete(scanId) }
                navViewModel.clearPendingResult()
            }
            navViewModel.setShowCameraSheet(false)
            onPopBack()
        },
        onDelete = if (!isFreshScan) { {
            MainScope().launch {
                repository.delete(scanId)
                updateLastScanWidget(context)
            }
            navViewModel.setShowCameraSheet(false)
            onPopBack()
        } } else null
    )
}
