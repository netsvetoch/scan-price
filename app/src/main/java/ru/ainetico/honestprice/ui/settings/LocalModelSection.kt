package ru.ainetico.honestprice.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.data.AppSettings
import ru.ainetico.honestprice.model.ModelDownloader

@Composable
fun LocalModelSection(
    appSettings: AppSettings,
    modelDownloader: ModelDownloader
) {
    var localPrompt by remember { mutableStateOf(appSettings.localPrompt.value) }
    val downloadState by modelDownloader.state.collectAsState()
    val modelsDownloaded = modelDownloader.isModelDownloaded()

    Text(
        text = stringResource(R.string.settings_local_model_title),
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        text = stringResource(R.string.settings_local_model_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    OutlinedTextField(
        value = localPrompt,
        onValueChange = {
            localPrompt = it
            appSettings.setLocalPrompt(it)
        },
        label = { Text(stringResource(R.string.settings_local_prompt)) },
        placeholder = { Text(ru.ainetico.honestprice.ocr.LocalVisionEngine.DEFAULT_PROMPT) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 8
    )

    if (modelsDownloaded && downloadState !is ModelDownloader.DownloadState.Downloading) {
        Text(
            text = stringResource(R.string.settings_local_model_ready),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        when (downloadState) {
            is ModelDownloader.DownloadState.Downloading -> {
                val dl = downloadState as ModelDownloader.DownloadState.Downloading
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DownloadFileRow(dl.file1)
                    DownloadFileRow(dl.file2)
                }
            }
            is ModelDownloader.DownloadState.Error -> {
                Text(
                    text = (downloadState as ModelDownloader.DownloadState.Error).message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {}
        }
        Button(
            onClick = { modelDownloader.startDownloadIfNeeded() },
            enabled = downloadState !is ModelDownloader.DownloadState.Downloading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_local_model_download))
        }
    }
}

@Composable
internal fun DownloadFileRow(fp: ModelDownloader.FileProgress) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = fp.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (fp.done) "✓" else "${fp.progress}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (fp.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (fp.done) {
            LinearProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            LinearProgressIndicator(
                progress = { fp.progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
