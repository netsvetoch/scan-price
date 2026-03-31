package ru.ainetico.honestprice.model

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Downloads GGUF model files using Android's system DownloadManager.
 * Survives app background, force stop, and process death.
 */
class ModelDownloader(
  private val context: Context,
  private val onModelsReady: (suspend () -> Unit)? = null
) {

  companion object {
    private const val TAG = "ModelDownloader"

    var MODEL_URL = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf"
    var MMPROJ_URL = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/mmproj-BF16.gguf"

    const val MODEL_FILENAME = "Qwen3.5-0.8B-Q4_K_M.gguf"
    const val MMPROJ_FILENAME = "mmproj-BF16.gguf"

    private const val PREFS_NAME = "model_download_prefs"
    private const val KEY_MODEL_DL_ID = "model_download_id"
    private const val KEY_MMPROJ_DL_ID = "mmproj_download_id"
  }

  sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val filename: String, val progress: Int) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
  }

  private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
  val state: StateFlow<DownloadState> = _state

  private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  fun isModelDownloaded(): Boolean {
    val modelsDir = File(context.filesDir, "models")
    return File(modelsDir, MODEL_FILENAME).exists() && File(modelsDir, MMPROJ_FILENAME).exists()
  }

  fun startDownloadIfNeeded() {
    if (isModelDownloaded()) {
      _state.value = DownloadState.Completed
      return
    }

    scope.launch {
      try {
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

        // Download model file
        if (!File(modelsDir, MODEL_FILENAME).exists()) {
          downloadFileWithManager(MODEL_URL, MODEL_FILENAME, "Файл 1 из 2")
        }

        // Download mmproj file
        if (!File(modelsDir, MMPROJ_FILENAME).exists()) {
          downloadFileWithManager(MMPROJ_URL, MMPROJ_FILENAME, "Файл 2 из 2")
        }

        if (isModelDownloaded()) {
          _state.value = DownloadState.Completed
          Log.i(TAG, "All models downloaded, initializing engine...")
          onModelsReady?.invoke()
        }
      } catch (e: Exception) {
        Log.e(TAG, "Download failed", e)
        _state.value = DownloadState.Error("Ошибка загрузки: ${e.message}")
      }
    }
  }

  /**
   * Download file using system DownloadManager and poll progress.
   * Blocks until download completes or fails.
   */
  private suspend fun downloadFileWithManager(url: String, filename: String, displayName: String) {
    val modelsDir = File(context.filesDir, "models")
    val destFile = File(modelsDir, filename)

    Log.i(TAG, "Starting download: $displayName → $url")
    _state.value = DownloadState.Downloading(displayName, 0)

    // Enqueue download — downloads to public Downloads dir first (DownloadManager limitation)
    val request = DownloadManager.Request(Uri.parse(url))
      .setTitle("ЧестнаяЦена — $displayName")
      .setDescription("Загрузка модели для распознавания ценников")
      .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
      .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "honestprice_$filename")
      .setAllowedOverMetered(true)
      .setAllowedOverRoaming(false)

    val downloadId = downloadManager.enqueue(request)
    Log.i(TAG, "Download enqueued: id=$downloadId")

    // Poll progress
    var completed = false
    while (!completed) {
      val query = DownloadManager.Query().setFilterById(downloadId)
      val cursor = downloadManager.query(query)

      if (cursor != null && cursor.moveToFirst()) {
        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

        val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
        val bytesDownloaded = if (bytesIdx >= 0) cursor.getLong(bytesIdx) else 0
        val totalBytes = if (totalIdx >= 0) cursor.getLong(totalIdx) else -1

        when (status) {
          DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
            val progress = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
            _state.value = DownloadState.Downloading(displayName, progress)
          }

          DownloadManager.STATUS_SUCCESSFUL -> {
            completed = true
            // Move from Downloads to app's filesDir
            val downloadedFile = File(
              Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
              "honestprice_$filename"
            )
            if (downloadedFile.exists()) {
              downloadedFile.copyTo(destFile, overwrite = true)
              downloadedFile.delete()
              Log.i(TAG, "$displayName downloaded: ${destFile.length() / 1024 / 1024}MB")
            }
          }

          DownloadManager.STATUS_FAILED -> {
            val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
            val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
            completed = true
            throw RuntimeException("Download failed: reason=$reason")
          }
        }
        cursor.close()
      }

      if (!completed) {
        delay(500) // Poll every 500ms
      }
    }
  }
}
