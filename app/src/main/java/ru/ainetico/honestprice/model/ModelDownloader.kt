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

  data class FileProgress(val label: String, val progress: Int, val done: Boolean = false)

  sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val file1: FileProgress, val file2: FileProgress) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
  }

  private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
  val state: StateFlow<DownloadState> = _state

  private val file1Progress = MutableStateFlow(FileProgress("Файл 1 из 2", 0))
  private val file2Progress = MutableStateFlow(FileProgress("Файл 2 из 2", 0))

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
        val needModel = !File(modelsDir, MODEL_FILENAME).exists()
        val needMmproj = !File(modelsDir, MMPROJ_FILENAME).exists()

        if (!needModel) file1Progress.value = FileProgress("Файл 1 из 2", 100, done = true)
        if (!needMmproj) file2Progress.value = FileProgress("Файл 2 из 2", 100, done = true)

        _state.value = DownloadState.Downloading(file1Progress.value, file2Progress.value)

        // Launch both downloads in parallel
        val job1 = if (needModel) {
          scope.launch { downloadFileWithManager(MODEL_URL, MODEL_FILENAME, file1Progress) }
        } else null

        val job2 = if (needMmproj) {
          scope.launch { downloadFileWithManager(MMPROJ_URL, MMPROJ_FILENAME, file2Progress) }
        } else null

        // Poll and update combined state
        while (job1?.isActive == true || job2?.isActive == true) {
          _state.value = DownloadState.Downloading(file1Progress.value, file2Progress.value)
          delay(500)
        }

        job1?.join()
        job2?.join()

        if (isModelDownloaded()) {
          _state.value = DownloadState.Completed
          Log.i(TAG, "All models downloaded, initializing engine...")
          onModelsReady?.invoke()
        } else {
          _state.value = DownloadState.Error("Загрузка не завершена")
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
  private suspend fun downloadFileWithManager(url: String, filename: String, progressFlow: MutableStateFlow<FileProgress>) {
    val modelsDir = File(context.filesDir, "models")
    val destFile = File(modelsDir, filename)

    Log.i(TAG, "Starting download: ${progressFlow.value.label} → $url")

    val label = progressFlow.value.label

    // Enqueue download — downloads to public Downloads dir first (DownloadManager limitation)
    val request = DownloadManager.Request(Uri.parse(url))
      .setTitle("ЧестнаяЦена — $label")
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
            progressFlow.value = progressFlow.value.copy(progress = progress)
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
              progressFlow.value = progressFlow.value.copy(progress = 100, done = true)
              Log.i(TAG, "$label downloaded: ${destFile.length() / 1024 / 1024}MB")
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
