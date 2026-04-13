package ru.ainetico.honestprice.ui.settings

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.data.DataExporter
import ru.ainetico.honestprice.data.ScanRepository
import ru.ainetico.honestprice.ui.theme.Dimens

@Composable
fun ExportSection(
  scanRepository: ScanRepository,
  scanCount: Int
) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  SettingsSectionHeader(
    titleRes = R.string.settings_export_title,
    descriptionRes = R.string.settings_export_description
  )

  var isExporting by remember { mutableStateOf(false) }
  var exportStatus by remember { mutableStateOf("") }
  var isExportError by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Button(
      onClick = {
        scope.launch {
          isExporting = true
          exportStatus = ""
          isExportError = false
          try {
            val scans = withContext(Dispatchers.IO) {
              scanRepository.getAllScans()
            }
            if (scans.isEmpty()) {
              exportStatus = context.getString(R.string.settings_export_no_data)
              isExportError = false
            } else {
              val exporter = DataExporter(context)
              val result = exporter.export(scans)
              val files = listOfNotNull(result.csvFile, result.zipFile)
              exporter.shareFiles(files)
              exportStatus = context.resources.getQuantityString(
                R.plurals.settings_export_success, scans.size, scans.size
              )
              isExportError = false
            }
          } catch (e: Exception) {
            exportStatus = context.getString(R.string.settings_export_error, e.message)
            isExportError = true
            Log.e("Settings", "Export failed", e)
          }
          isExporting = false
        }
      },
      enabled = !isExporting && scanCount > 0
    ) {
      if (isExporting) {
        CircularProgressIndicator(
          modifier = Modifier.size(Dimens.ExportSpinnerSize),
          strokeWidth = Dimens.ScanningStrokeWidth,
          color = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
      }
      Text(stringResource(R.string.settings_export_button))
    }
    Text(
      text = pluralStringResource(R.plurals.settings_scan_count, scanCount, scanCount),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }

  if (exportStatus.isNotBlank()) {
    Text(
      text = exportStatus,
      style = MaterialTheme.typography.bodySmall,
      color = if (isExportError) MaterialTheme.colorScheme.error
      else MaterialTheme.colorScheme.primary
    )
  }
}
