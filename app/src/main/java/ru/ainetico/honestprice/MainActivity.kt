package ru.ainetico.honestprice

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import ru.ainetico.honestprice.model.ModelDownloader

import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.data.ScanRepository

import ru.ainetico.honestprice.navigation.AppNavigationViewModel
import ru.ainetico.honestprice.navigation.Screen
import ru.ainetico.honestprice.ui.camera.CameraViewModel
import ru.ainetico.honestprice.ui.navigation.AppNavGraph
import ru.ainetico.honestprice.ui.theme.ScanPriceTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val ACTION_SCAN = "ru.ainetico.honestprice.ACTION_SCAN"
private const val ACTION_GALLERY = "ru.ainetico.honestprice.ACTION_GALLERY"
private const val ACTION_MANUAL = "ru.ainetico.honestprice.ACTION_MANUAL"

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var modelDownloader: ModelDownloader

    @Inject lateinit var scanRepository: ScanRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request max display refresh rate (120Hz+)
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = display?.supportedModes
                ?.maxByOrNull { it.refreshRate }?.modeId ?: 0
        }

        val launchAction = intent?.action

        setContent {
            ScanPriceTheme {
                HonestPriceApp(
                    appSettings = appSettings,
                    modelDownloader = modelDownloader,
                    scanRepository = scanRepository,
                    launchAction = launchAction
                )
            }
        }
    }
}

@Composable
fun HonestPriceApp(
    appSettings: AppSettings,
    modelDownloader: ModelDownloader,
    scanRepository: ScanRepository,
    launchAction: String? = null
) {
    // Load onboarding state asynchronously to avoid blocking the main thread
    var onboardingCompleted by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        onboardingCompleted = appSettings.onboardingCompleted.first()
    }

    val initialOnboardingCompleted = onboardingCompleted ?: return

    val navController = rememberNavController()
    val context = LocalContext.current
    val startDestination = if (initialOnboardingCompleted) Screen.History.route else Screen.Onboarding.route

    // Shared instances
    val cameraViewModel: CameraViewModel = hiltViewModel()
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

    AppNavGraph(
        navController = navController,
        startDestination = startDestination,
        appSettings = appSettings,
        modelDownloader = modelDownloader,
        scanRepository = scanRepository,
        cameraViewModel = cameraViewModel,
        navViewModel = navViewModel,
        navState = navState,
        context = context
    )
}
