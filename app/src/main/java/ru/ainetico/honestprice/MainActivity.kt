package ru.ainetico.honestprice

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import ru.ainetico.honestprice.analyzer.ImageAnalyzer
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.AppDatabase
import ru.ainetico.honestprice.data.ScanRepositoryImpl
import ru.ainetico.honestprice.navigation.Screen
import ru.ainetico.honestprice.ocr.BarcodeEngine
import ru.ainetico.honestprice.ocr.LocalVisionEngine
import ru.ainetico.honestprice.location.LocationProvider
import ru.ainetico.honestprice.ui.camera.CameraScreen
import ru.ainetico.honestprice.ui.camera.CameraViewModel
import ru.ainetico.honestprice.ui.history.HistoryScreen
import ru.ainetico.honestprice.ui.history.HistoryViewModel
import ru.ainetico.honestprice.ui.onboarding.OnboardingScreen
import ru.ainetico.honestprice.ui.result.ResultScreen
import ru.ainetico.honestprice.ui.result.ResultViewModel
import ru.ainetico.honestprice.ui.theme.ЧестнаяЦенаTheme

class MainActivity : ComponentActivity() {

    private lateinit var localVisionEngine: LocalVisionEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize local vision engine in background
        localVisionEngine = LocalVisionEngine(applicationContext)
        lifecycleScope.launch {
            Log.i("MainActivity", "Initializing local vision engine...")
            localVisionEngine.initialize()
            Log.i("MainActivity", "Vision engine ready: ${localVisionEngine.isAvailable()}")
        }

        setContent {
            ЧестнаяЦенаTheme {
                HonestPriceApp(localVisionEngine)
            }
        }
    }
}

@Composable
fun HonestPriceApp(localVisionEngine: LocalVisionEngine) {
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
            OnboardingScreen(onComplete = {
                navController.navigate(Screen.Camera.createRoute()) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
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
            val repository = remember { ScanRepositoryImpl(db.scanDao()) }
            val analyzer = remember {
                ImageAnalyzer(localVisionEngine, BarcodeEngine(), PriceCalculator())
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
        composable(
            route = Screen.Result.route,
            arguments = listOf(navArgument("scanId") { type = NavType.LongType })
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getLong("scanId") ?: 0L
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
        composable(Screen.History.route) {
            val repository = remember { ScanRepositoryImpl(db.scanDao()) }
            val viewModel = remember { HistoryViewModel(repository) }

            HistoryScreen(
                viewModel = viewModel,
                onScanClick = { scanId -> navController.navigate(Screen.Result.createRoute(scanId)) },
                onCameraClick = { navController.navigate(Screen.Camera.createRoute(openGallery = false)) },
                onGalleryClick = { navController.navigate(Screen.Camera.createRoute(openGallery = true)) },
                onManualClick = { navController.navigate(Screen.ResultManual.route) }
            )
        }
    }
}
