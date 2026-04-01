package ru.ainetico.honestprice.model

import ru.ainetico.honestprice.R
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
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
  private val context: Context
) {

  companion object {
    private const val TAG = "ModelDownloader"
    private const val CHANNEL_SILENT = "model_download"
    private const val CHANNEL_ALERT = "model_ready"
    private const val NOTIF_SILENT = 1001
    private const val NOTIF_ALERT = 1002

    var MODEL_URL = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf"
    var MMPROJ_URL = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/mmproj-BF16.gguf"

    const val MODEL_FILENAME = "Qwen3.5-0.8B-Q4_K_M.gguf"
    const val MMPROJ_FILENAME = "mmproj-BF16.gguf"

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

  private val file1Progress = MutableStateFlow(FileProgress(context.getString(R.string.download_file1_label), 0))
  private val file2Progress = MutableStateFlow(FileProgress(context.getString(R.string.download_file2_label), 0))

  private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
  private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  init {
    createNotificationChannels()
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      notificationManager.createNotificationChannel(
        NotificationChannel(CHANNEL_SILENT, context.getString(R.string.download_channel_silent), NotificationManager.IMPORTANCE_LOW)
      )
      notificationManager.createNotificationChannel(
        NotificationChannel(CHANNEL_ALERT, context.getString(R.string.download_channel_alert), NotificationManager.IMPORTANCE_HIGH)
      )
    }
  }

  private fun showSilentNotification(title: String, text: String, progress: Int) {
    val builder = NotificationCompat.Builder(context, CHANNEL_SILENT)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(title)
      .setContentText(text)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(true)
      .setProgress(100, progress.coerceIn(0, 100), false)
    notificationManager.notify(NOTIF_SILENT, builder.build())
  }

  private fun cancelSilentNotification() {
    notificationManager.cancel(NOTIF_SILENT)
  }

  fun showReadyNotification() {
    showHeadsUpNotification(context.getString(R.string.download_ready_title), context.getString(R.string.download_ready_text))
  }

  private fun showHeadsUpNotification(title: String, text: String) {
    notificationManager.notify(NOTIF_ALERT,
      NotificationCompat.Builder(context, CHANNEL_ALERT)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    )
  }

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

        if (!needModel) file1Progress.value = FileProgress(context.getString(R.string.download_file1_label), 100, done = true)
        if (!needMmproj) file2Progress.value = FileProgress(context.getString(R.string.download_file2_label), 100, done = true)

        _state.value = DownloadState.Downloading(file1Progress.value, file2Progress.value)

        // Launch both downloads in parallel
        val job1 = if (needModel) {
          scope.launch { downloadFileWithManager(MODEL_URL, MODEL_FILENAME, file1Progress) }
        } else null

        val job2 = if (needMmproj) {
          scope.launch { downloadFileWithManager(MMPROJ_URL, MMPROJ_FILENAME, file2Progress) }
        } else null

        // Poll, update UI state and silent notification
        while (job1?.isActive == true || job2?.isActive == true) {
          val f1 = file1Progress.value
          val f2 = file2Progress.value
          _state.value = DownloadState.Downloading(f1, f2)

          val totalProgress = (f1.progress + f2.progress) / 2
          showSilentNotification(context.getString(R.string.download_progress_title), "$totalProgress%", totalProgress)

          delay(2000)
        }

        job1?.join()
        job2?.join()

        if (isModelDownloaded()) {
          cancelSilentNotification()
          _state.value = DownloadState.Completed
          Log.i(TAG, "All models downloaded")
        } else {
          _state.value = DownloadState.Error(context.getString(R.string.download_error_incomplete))
        }
      } catch (e: Exception) {
        Log.e(TAG, "Download failed", e)
        _state.value = DownloadState.Error(context.getString(R.string.download_error_format, e.message))
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
      .setTitle("${context.getString(R.string.app_name)} — $label")
      .setDescription(context.getString(R.string.download_description))
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
            // Only update if progress increased — prevents jitter
            if (progress > progressFlow.value.progress) {
              progressFlow.value = progressFlow.value.copy(progress = progress)
            }
          }

          DownloadManager.STATUS_SUCCESSFUL -> {
            completed = true
            progressFlow.value = progressFlow.value.copy(progress = 100)
            // Copy from DownloadManager's URI to app's filesDir (done=true only after copy)
            try {
              val uri = downloadManager.getUriForDownloadedFile(downloadId)
              if (uri != null) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                  java.io.FileOutputStream(destFile).use { output ->
                    val buf = ByteArray(65536)
                    var len: Int
                    while (input.read(buf).also { len = it } > 0) {
                      output.write(buf, 0, len)
                    }
                  }
                }
                // Remove from Downloads
                downloadManager.remove(downloadId)
                progressFlow.value = progressFlow.value.copy(progress = 100, done = true)
                Log.i(TAG, "$label downloaded: ${destFile.length() / 1024 / 1024}MB")
              } else {
                throw RuntimeException("Download URI is null")
              }
            } catch (e: Exception) {
              Log.e(TAG, "Failed to copy downloaded file", e)
              throw e
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
        delay(2000) // Poll every 500ms
      }
    }
  }
}
