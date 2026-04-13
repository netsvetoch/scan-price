package ru.ainetico.honestprice.ui.settings

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.ocr.ApiHttpClient
import ru.ainetico.honestprice.ui.theme.Dimens

private sealed interface ConnectionStatus {
  data object Idle : ConnectionStatus
  data class Success(val message: String) : ConnectionStatus
  data class Error(val message: String) : ConnectionStatus
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteModelSection(appSettings: AppSettings) {
  val savedUseRemote by appSettings.useRemoteServer.collectAsState(initial = false)
  var useRemote by remember { mutableStateOf(savedUseRemote) }
  LaunchedEffect(savedUseRemote) { useRemote = savedUseRemote }

  var apiUrl by remember {
    mutableStateOf(
      appSettings.apiUrl.value
        .removePrefix("https://")
        .removePrefix("http://")
    )
  }
  var apiModel by remember { mutableStateOf(appSettings.apiModel.value) }
  var apiKey by remember { mutableStateOf(appSettings.apiKey.value) }

  val savedSystemPrompt by appSettings.systemPrompt.collectAsState(initial = "")
  var systemPrompt by remember { mutableStateOf(savedSystemPrompt) }
  LaunchedEffect(savedSystemPrompt) { systemPrompt = savedSystemPrompt }

  var modelList by remember { mutableStateOf<List<String>>(emptyList()) }
  var connectionStatus by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.Idle) }
  var isChecking by remember { mutableStateOf(false) }
  var modelsExpanded by remember { mutableStateOf(false) }

  val scope = rememberCoroutineScope()
  val resources = LocalResources.current
  val modelsLoaded = modelList.isNotEmpty()

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      SettingsSectionHeader(
        titleRes = R.string.settings_remote_model_title,
        descriptionRes = R.string.settings_remote_model_description
      )
    }
    Switch(
      checked = useRemote,
      onCheckedChange = {
        useRemote = it
        scope.launch { appSettings.setUseRemoteServer(it) }
      }
    )
  }

  AnimatedVisibility(visible = useRemote) {
    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // URL + Check button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
      ) {
        OutlinedTextField(
          value = apiUrl,
          onValueChange = { input ->
            val cleaned = input
              .removePrefix("https://")
              .removePrefix("http://")
            apiUrl = cleaned
            appSettings.setApiUrl("https://$cleaned")
            modelList = emptyList()
            connectionStatus = ConnectionStatus.Idle
          },
          label = { Text(stringResource(R.string.settings_api_url)) },
          prefix = { Text("https://") },
          placeholder = { Text("api.example.com/v1") },
          modifier = Modifier.weight(1f),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        IconButton(
          onClick = {
            scope.launch {
              isChecking = true
              connectionStatus = ConnectionStatus.Idle
              modelList = emptyList()
              try {
                val models = fetchModels("https://${apiUrl.trimEnd('/')}", apiKey)
                modelList = models
                connectionStatus = ConnectionStatus.Success(
                  resources.getQuantityString(
                    R.plurals.settings_models_found, models.size, models.size
                  )
                )
              } catch (e: Exception) {
                connectionStatus = ConnectionStatus.Error(
                  e.message?.take(50) ?: "Unknown error"
                )
                Log.e("Settings", "Connection check failed", e)
              }
              isChecking = false
            }
          },
          enabled = apiUrl.isNotBlank() && !isChecking,
          modifier = Modifier.padding(top = 8.dp)
        ) {
          if (isChecking) {
            CircularProgressIndicator(
              modifier = Modifier.size(Dimens.ConnectionSpinnerSize),
              strokeWidth = Dimens.ScanningStrokeWidth
            )
          } else {
            Icon(
              Icons.Filled.Refresh,
              contentDescription = stringResource(R.string.settings_check_connection)
            )
          }
        }
      }

      // Connection status
      when (val status = connectionStatus) {
        is ConnectionStatus.Success -> Text(
          text = "✓ ${status.message}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary
        )

        is ConnectionStatus.Error -> Text(
          text = "✗ ${status.message}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error
        )

        ConnectionStatus.Idle -> {}
      }

      // API key
      OutlinedTextField(
        value = apiKey,
        onValueChange = {
          apiKey = it
          appSettings.setApiKey(it)
          modelList = emptyList()
          connectionStatus = ConnectionStatus.Idle
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
            Text(
              if (modelsLoaded) stringResource(R.string.settings_select_model)
              else stringResource(R.string.settings_check_first)
            )
          },
          modifier = Modifier
              .fillMaxWidth()
              .menuAnchor(),
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

      // System prompt
      OutlinedTextField(
        value = systemPrompt,
        onValueChange = {
          systemPrompt = it
          scope.launch { appSettings.setSystemPrompt(it) }
        },
        label = { Text(stringResource(R.string.settings_system_prompt)) },
        placeholder = { Text(ru.ainetico.honestprice.ocr.RemoteVisionClient.DEFAULT_SYSTEM_PROMPT) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 8
      )

      // Active status
      if (apiUrl.isNotBlank() && apiModel.isNotBlank()) {
        Text(
          text = stringResource(R.string.settings_remote_active),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}

private suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> =
  withContext(Dispatchers.IO) {
    val request = Request.Builder()
      .url("$baseUrl/models")
      .apply {
        if (apiKey.isNotBlank()) {
          addHeader("Authorization", "Bearer $apiKey")
        }
      }
      .build()

    ApiHttpClient.quickClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw RuntimeException(
          "HTTP ${response.code}: ${
            response.body?.string()?.take(100)
          }"
        )
      }
      val json = JSONObject(response.body!!.string())
      val data = json.getJSONArray("data")
      (0 until data.length()).map { data.getJSONObject(it).getString("id") }
    }
  }
