package ru.ainetico.honestprice.ui.history

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.ScanRepository
import ru.ainetico.honestprice.data.StoreDao
import ru.ainetico.honestprice.location.LocationProvider
import ru.ainetico.honestprice.model.AnalysisResult
import ru.ainetico.honestprice.ui.camera.CameraEvent
import ru.ainetico.honestprice.ui.camera.CameraScreen
import ru.ainetico.honestprice.ui.camera.CameraViewModel
import ru.ainetico.honestprice.ui.result.ResultScreen
import ru.ainetico.honestprice.ui.result.ResultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    cameraViewModel: CameraViewModel,
    repository: ScanRepository,
    storeDao: StoreDao,
    locationProvider: LocationProvider,
    onScanClick: (Long) -> Unit,
    onNavigateToManualEntry: () -> Unit,
    openCameraOnStart: Boolean = true
) {
    val scans by viewModel.scans.collectAsState()
    val context = LocalContext.current

    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Open camera on first composition only
    LaunchedEffect(Unit) {
        if (openCameraOnStart) showSheet = true
    }

    // After camera scans, holds the result until user saves or cancels
    var pendingResult by remember { mutableStateOf<Triple<Long, AnalysisResult, String?>?>(null) }
    val showingResult = pendingResult != null

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            cameraViewModel.importFromGallery(it, context)
            showSheet = true
        }
    }

    // Listen for camera navigation events
    val cameraEvent by cameraViewModel.event.collectAsState()
    LaunchedEffect(cameraEvent) {
        when (val e = cameraEvent) {
            is CameraEvent.NavigateToResult -> {
                cameraViewModel.eventConsumed()
                val imagePath = repository.getById(e.scanId)?.imagePath
                pendingResult = Triple(e.scanId, e.result, imagePath)
            }
            is CameraEvent.NavigateToManualEntry -> {
                cameraViewModel.eventConsumed()
                showSheet = false
                cameraViewModel.resetToPreview()
                onNavigateToManualEntry()
            }
            null -> {}
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = onNavigateToManualEntry) {
                    Icon(Icons.Filled.Edit, stringResource(R.string.history_fab_manual))
                }
                SmallFloatingActionButton(onClick = { galleryLauncher.launch("image/*") }) {
                    Icon(Icons.Filled.Collections, stringResource(R.string.history_fab_gallery))
                }
                LargeFloatingActionButton(onClick = { showSheet = true }) {
                    Icon(Icons.Filled.CameraAlt, stringResource(R.string.history_fab_camera))
                }
            }
        }
    ) { padding ->
        if (scans.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.history_empty_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scans, key = { it.id }) { scan ->
                    ScanCard(scan = scan, onClick = {
                        showSheet = false
                        cameraViewModel.resetToPreview()
                        onScanClick(scan.id)
                    })
                }
            }
        }
    }

    // BottomSheet — shows Camera or Result seamlessly
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                if (showingResult) {
                    // Cancel the pending scan
                    pendingResult?.let { (scanId, _, _) ->
                        CoroutineScope(Dispatchers.IO).launch {
                            repository.delete(scanId)
                        }
                    }
                    pendingResult = null
                }
                showSheet = false
                cameraViewModel.resetToPreview()
            },
            sheetState = sheetState
        ) {
            if (showingResult) {
                // Show result form inside the same BottomSheet
                val (scanId, result, imagePath) = pendingResult!!
                val resultViewModel = remember(scanId) {
                    ResultViewModel(repository, storeDao, locationProvider, PriceCalculator())
                }
                LaunchedEffect(scanId) {
                    resultViewModel.loadFromAnalysis(scanId, result, imagePath)
                }
                ResultScreen(
                    viewModel = resultViewModel,
                    onSaved = {
                        pendingResult = null
                        showSheet = false
                        cameraViewModel.resetToPreview()
                    },
                    onCancel = {
                        CoroutineScope(Dispatchers.IO).launch {
                            repository.delete(scanId)
                        }
                        pendingResult = null
                        cameraViewModel.resetToPreview()
                    }
                )
            } else {
                // Show camera
                Box(modifier = Modifier.height(500.dp)) {
                    CameraScreen(
                        viewModel = cameraViewModel,
                        onNavigateToResult = { _, _ -> },  // handled via LaunchedEffect above
                        onNavigateToManualEntry = { }       // handled via LaunchedEffect above
                    )
                }
            }
        }
    }
}
