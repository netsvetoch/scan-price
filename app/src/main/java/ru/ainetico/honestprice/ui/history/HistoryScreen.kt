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
import kotlinx.coroutines.launch
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.model.AnalysisResult
import ru.ainetico.honestprice.ui.camera.CameraEvent
import ru.ainetico.honestprice.ui.camera.CameraScreen
import ru.ainetico.honestprice.ui.camera.CameraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    cameraViewModel: CameraViewModel,
    showSheet: Boolean,
    onShowSheetChange: (Boolean) -> Unit,
    onScanClick: (ru.ainetico.honestprice.data.Scan) -> Unit,
    onNavigateToResult: (Long, AnalysisResult) -> Unit,
    onNavigateToManualEntry: () -> Unit
) {
    val scans by viewModel.scans.collectAsState()
    val context = LocalContext.current

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            cameraViewModel.importFromGallery(it, context)
            onShowSheetChange(true)
        }
    }

    // Listen for camera events — navigate to Result via NavHost
    val cameraEvent by cameraViewModel.event.collectAsState()
    LaunchedEffect(cameraEvent) {
        when (val e = cameraEvent) {
            is CameraEvent.NavigateToResult -> {
                cameraViewModel.eventConsumed()
                // Close sheet with animation, then navigate
                sheetState.hide()
                onShowSheetChange(false)
                cameraViewModel.resetToPreview()
                onNavigateToResult(e.scanId, e.result)
            }
            is CameraEvent.NavigateToManualEntry -> {
                cameraViewModel.eventConsumed()
                sheetState.hide()
                onShowSheetChange(false)
                cameraViewModel.resetToPreview()
                onNavigateToManualEntry()
            }
            null -> {}
        }
    }

    // Helper to close sheet with animation
    fun closeSheet() {
        scope.launch {
            sheetState.hide()
            onShowSheetChange(false)
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
                LargeFloatingActionButton(onClick = { onShowSheetChange(true) }) {
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
                    ScanCard(scan = scan, onClick = { onScanClick(scan) })
                }
            }
        }
    }

    // Camera BottomSheet — only camera, no result form
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                onShowSheetChange(false)
                cameraViewModel.resetToPreview()
            },
            sheetState = sheetState
        ) {
            Box(modifier = Modifier.height(500.dp)) {
                CameraScreen(
                    viewModel = cameraViewModel,
                    onNavigateToResult = { _, _ -> },
                    onNavigateToManualEntry = { }
                )
            }
        }
    }
}
