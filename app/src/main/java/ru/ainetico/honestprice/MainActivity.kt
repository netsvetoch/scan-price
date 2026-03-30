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
import ru.ainetico.honestprice.analyzer.ImageAnalyzer
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.AppDatabase
import ru.ainetico.honestprice.data.ScanRepositoryImpl
import ru.ainetico.honestprice.image.ImagePreprocessor
import ru.ainetico.honestprice.navigation.Screen
import ru.ainetico.honestprice.ocr.BarcodeEngine
import ru.ainetico.honestprice.ocr.OcrEngine
import ru.ainetico.honestprice.parser.PriceTagParser
import ru.ainetico.honestprice.ui.camera.CameraScreen
import ru.ainetico.honestprice.ui.camera.CameraViewModel
import ru.ainetico.honestprice.ui.onboarding.OnboardingScreen
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
        composable(
            route = Screen.Result.route,
            arguments = listOf(navArgument("scanId") { type = NavType.LongType })
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getLong("scanId") ?: 0L
            // TODO: ResultScreen - Task 8
            androidx.compose.material3.Text("Result (scanId=$scanId)")
        }
        composable(Screen.ResultManual.route) {
            // TODO: ResultScreen manual - Task 8
            androidx.compose.material3.Text("Result Manual")
        }
        composable(Screen.History.route) {
            // TODO: HistoryScreen - Task 9
            androidx.compose.material3.Text("History")
        }
    }
}
