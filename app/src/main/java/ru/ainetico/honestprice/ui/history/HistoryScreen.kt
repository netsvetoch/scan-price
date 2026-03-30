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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    ScanCard(scan = scan, onClick = { onScanClick(scan.id) })
                }
            }
        }
    }
}
