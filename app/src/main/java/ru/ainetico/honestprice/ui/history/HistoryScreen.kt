package ru.ainetico.honestprice.ui.history

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import ru.ainetico.honestprice.ui.camera.CameraState
import ru.ainetico.honestprice.ui.camera.CameraViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun formatDateHeader(timestamp: Long): String {
  val scanCal = Calendar.getInstance().apply { timeInMillis = timestamp }
  val todayCal = Calendar.getInstance()

  val sameDay = scanCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
      scanCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
  if (sameDay) return "Сегодня"

  todayCal.add(Calendar.DAY_OF_YEAR, -1)
  val yesterday = scanCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
      scanCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
  if (yesterday) return "Вчера"

  return SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(timestamp))
}

private fun dateKey(timestamp: Long): String {
  val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
  return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
  viewModel: HistoryViewModel,
  cameraViewModel: CameraViewModel,
  showSheet: Boolean,
  onShowSheetChange: (Boolean) -> Unit,
  onScanClick: (ru.ainetico.honestprice.data.Scan) -> Unit,
  onNavigateToResult: (Long, AnalysisResult) -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateToManualEntry: () -> Unit
) {
  val scansList by viewModel.scans.collectAsState()
  val scans = scansList
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

  // Listen for camera events via Flow collect (not collectAsState)
  LaunchedEffect(cameraViewModel) {
    cameraViewModel.event.collect { e ->
      when (e) {
        is CameraEvent.NavigateToResult -> {
          cameraViewModel.eventConsumed()
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
  }

  // Helper to close sheet with animation
  fun closeSheet() {
    scope.launch {
      sheetState.hide()
      onShowSheetChange(false)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.app_title)) },
        actions = {
          IconButton(onClick = onNavigateToSettings) {
            Icon(
              Icons.Filled.Settings,
              contentDescription = stringResource(R.string.settings_title)
            )
          }
        }
      )
    },
    floatingActionButton = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        FloatingActionButton(
          onClick = onNavigateToManualEntry,
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(48.dp)
        ) {
          Icon(Icons.Filled.Edit, stringResource(R.string.history_fab_manual))
        }
        FloatingActionButton(
          onClick = { galleryLauncher.launch("image/*") },
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(48.dp)
        ) {
          Icon(Icons.Filled.Collections, stringResource(R.string.history_fab_gallery))
        }
        FloatingActionButton(
          onClick = { onShowSheetChange(true) },
          shape = CircleShape,
          modifier = Modifier.size(64.dp)
        ) {
          Icon(
            Icons.Filled.CameraAlt,
            stringResource(R.string.history_fab_camera),
            modifier = Modifier.size(32.dp)
          )
        }
      }
    }
  ) { padding ->
    when {
      scans == null -> {
        // Loading skeleton
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          repeat(5) {
            ru.ainetico.honestprice.ui.common.ShimmerBox(
              modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
            )
          }
        }
      }

      scans.isEmpty() -> {
        Box(
          Modifier
            .fillMaxSize()
            .padding(padding), contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              stringResource(R.string.history_empty),
              style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
              stringResource(R.string.history_empty_hint),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
          }
        }
      }

      else -> {
        val todayKey = dateKey(System.currentTimeMillis())
        val grouped = remember(scans) {
          scans.groupBy { dateKey(it.createdAt) }
            .toSortedMap(compareByDescending { it })
        }
        val expandedState = remember { mutableStateMapOf<String, Boolean>() }

        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          grouped.forEach { (key, groupScans) ->
            val isExpanded = expandedState.getOrPut(key) { key == todayKey }
            val header = formatDateHeader(groupScans.first().createdAt)

            item(key = "header_$key") {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { expandedState[key] = !isExpanded }
                  .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "$header (${groupScans.size})",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                  imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            items(groupScans, key = { it.id }) { scan ->
              AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
              ) {
                ScanCard(scan = scan, onClick = { onScanClick(scan) })
              }
            }
          }
        }
      }
    }
  }

  // Camera BottomSheet
  if (showSheet) {
    val cameraState by cameraViewModel.state.collectAsState()
    val isVertical by cameraViewModel.isVerticalFrame.collectAsState()
    val sheetModifier = when {
      cameraState is CameraState.Scanning && isVertical -> Modifier.fillMaxHeight(0.85f)
      cameraState is CameraState.Scanning -> Modifier.height(320.dp)
      else -> Modifier.fillMaxHeight(0.85f)
    }

    ModalBottomSheet(
      onDismissRequest = {
        onShowSheetChange(false)
        cameraViewModel.resetToPreview()
      },
      sheetState = sheetState
    ) {
      Box(modifier = sheetModifier) {
        CameraScreen(
          viewModel = cameraViewModel
        )
      }
    }
  }
}
