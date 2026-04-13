package ru.ainetico.scanprice.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ainetico.scanprice.R
import ru.ainetico.scanprice.data.AppSettings
import ru.ainetico.scanprice.data.ScanRepository
import ru.ainetico.scanprice.model.ModelDownloader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  appSettings: AppSettings,
  scanRepository: ScanRepository,
  modelDownloader: ModelDownloader,
  onBack: () -> Unit
) {
  val allScans by scanRepository.getAllScansFlow().collectAsState(initial = emptyList())

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.action_back)
            )
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(16.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      LanguageSection()

      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

      RemoteModelSection(appSettings)

      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

      LocalModelSection(appSettings, modelDownloader)

      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

      ExportSection(scanRepository, allScans.size)
    }
  }
}
