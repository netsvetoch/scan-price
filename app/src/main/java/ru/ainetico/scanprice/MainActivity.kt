package ru.ainetico.scanprice

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import ru.ainetico.scanprice.data.AppSettings
import ru.ainetico.scanprice.data.ScanRepository
import ru.ainetico.scanprice.model.ModelDownloader
import ru.ainetico.scanprice.navigation.AppNavigationViewModel
import ru.ainetico.scanprice.navigation.Screen
import ru.ainetico.scanprice.ui.camera.CameraViewModel
import ru.ainetico.scanprice.ui.navigation.AppNavGraph
import ru.ainetico.scanprice.ui.theme.ScanPriceTheme
import javax.inject.Inject

private const val ACTION_SCAN = "ru.ainetico.scanprice.ACTION_SCAN"
private const val ACTION_GALLERY = "ru.ainetico.scanprice.ACTION_GALLERY"
private const val ACTION_MANUAL = "ru.ainetico.scanprice.ACTION_MANUAL"

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

  @Inject
  lateinit var appSettings: AppSettings

  @Inject
  lateinit var modelDownloader: ModelDownloader

  @Inject
  lateinit var scanRepository: ScanRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Request max display refresh rate (120Hz+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.attributes = window.attributes.apply {
        preferredDisplayModeId = display?.supportedModes
          ?.maxByOrNull { it.refreshRate }?.modeId ?: 0
      }
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
  val startDestination =
    if (initialOnboardingCompleted) Screen.History.route else Screen.Onboarding.route

  // Shared instances
  val cameraViewModel: CameraViewModel = hiltViewModel()
  val navViewModel =
    remember { AppNavigationViewModel(initialShowCamera = launchAction == ACTION_SCAN) }
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
