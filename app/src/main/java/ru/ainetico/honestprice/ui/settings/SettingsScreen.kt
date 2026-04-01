package ru.ainetico.honestprice.ui.settings

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.data.DataExporter
import ru.ainetico.honestprice.data.ScanRepository
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    scanRepository: ScanRepository,
    onBack: () -> Unit
) {
    var apiUrl by remember { mutableStateOf(appSettings.apiUrl.value) }
    var apiModel by remember { mutableStateOf(appSettings.apiModel.value) }
    var apiKey by remember { mutableStateOf(appSettings.apiKey.value) }

    var modelList by remember { mutableStateOf<List<String>>(emptyList()) }
    var connectionStatus by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var modelsExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val modelsLoaded = modelList.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_remote_model_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_remote_model_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // URL + Check button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = apiUrl,
                    onValueChange = {
                        apiUrl = it
                        appSettings.setApiUrl(it)
                        modelList = emptyList()
                        connectionStatus = ""
                    },
                    label = { Text(stringResource(R.string.settings_api_url)) },
                    placeholder = { Text("https://api.example.com/v1") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            isChecking = true
                            connectionStatus = ""
                            modelList = emptyList()
                            try {
                                val models = fetchModels(apiUrl.trimEnd('/'), apiKey)
                                modelList = models
                                connectionStatus = "✓ ${models.size} моделей"
                            } catch (e: Exception) {
                                connectionStatus = "✗ ${e.message?.take(50)}"
                                Log.e("Settings", "Connection check failed", e)
                            }
                            isChecking = false
                        }
                    },
                    enabled = apiUrl.isNotBlank() && !isChecking,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.settings_check_connection))
                    }
                }
            }

            // Connection status
            if (connectionStatus.isNotBlank()) {
                Text(
                    text = connectionStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connectionStatus.startsWith("✓")) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error
                )
            }

            // API key
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    appSettings.setApiKey(it)
                    modelList = emptyList()
                    connectionStatus = ""
                },
                label = { Text(stringResource(R.string.settings_api_key)) },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            // Model selector
            ExposedDropdownMenuBox(
                expanded = modelsExpanded && modelsLoaded,
                onExpandedChange = { if (modelsLoaded) modelsExpanded = it }
            ) {
                OutlinedTextField(
                    value = apiModel,
                    onValueChange = {
                        apiModel = it
                        appSettings.setApiModel(it)
                    },
                    label = { Text(stringResource(R.string.settings_api_model)) },
                    placeholder = {
                        Text(if (modelsLoaded) "Выберите модель" else "Сначала проверьте соединение")
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true,
                    enabled = modelsLoaded,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelsExpanded && modelsLoaded) }
                )
                if (modelsLoaded) {
                    ExposedDropdownMenu(
                        expanded = modelsExpanded,
                        onDismissRequest = { modelsExpanded = false }
                    ) {
                        modelList.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    apiModel = model
                                    appSettings.setApiModel(model)
                                    modelsExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Active status
            if (apiUrl.isNotBlank() && apiModel.isNotBlank()) {
                Text(
                    text = stringResource(R.string.settings_remote_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Export section
            Text(
                text = stringResource(R.string.settings_export_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_export_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            var isExporting by remember { mutableStateOf(false) }
            var exportStatus by remember { mutableStateOf("") }
            val exportContext = context

            Button(
                onClick = {
                    scope.launch {
                        isExporting = true
                        exportStatus = ""
                        try {
                            val allScans = withContext(Dispatchers.IO) {
                                scanRepository.getAllScans()
                            }
                            if (allScans.isEmpty()) {
                                exportStatus = "Нет данных для экспорта"
                            } else {
                                val exporter = DataExporter(exportContext)
                                val result = exporter.export(allScans)
                                val files = listOfNotNull(result.csvFile, result.zipFile)
                                exporter.shareFiles(files)
                                exportStatus = "Экспортировано: ${allScans.size} записей"
                            }
                        } catch (e: Exception) {
                            exportStatus = "Ошибка: ${e.message}"
                            Log.e("Settings", "Export failed", e)
                        }
                        isExporting = false
                    }
                },
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.settings_export_button))
            }

            if (exportStatus.isNotBlank()) {
                Text(
                    text = exportStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (exportStatus.startsWith("Ошибка")) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> =
    withContext(Dispatchers.IO) {
        val url = URL("$baseUrl/models")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }

        try {
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val data = json.getJSONArray("data")
            (0 until data.length()).map { data.getJSONObject(it).getString("id") }
        } finally {
            conn.disconnect()
        }
    }
