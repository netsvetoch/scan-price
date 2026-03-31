package ru.ainetico.honestprice.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads GGUF model files with progress notification.
 * URLs will be configured later.
 */
class ModelDownloader(private val context: Context) {

  companion object {
    private const val TAG = "ModelDownloader"
    private const val CHANNEL_ID = "model_download"
    private const val NOTIFICATION_ID = 1001

    // TODO: replace with actual URLs
    var MODEL_URL = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf"
    var MMPROJ_URL = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/mmproj-BF16.gguf"

    const val MODEL_FILENAME = "Qwen3.5-0.8B-Q4_K_M.gguf"
    const val MMPROJ_FILENAME = "mmproj-BF16.gguf"
  }

  sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val filename: String, val progress: Int) : DownloadState()  // progress 0-100
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
  }

  private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
  val state: StateFlow<DownloadState> = _state

  private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  init {
    createNotificationChannel()
  }

  fun isModelDownloaded(): Boolean {
    val modelsDir = File(context.filesDir, "models")
    return File(modelsDir, MODEL_FILENAME).exists() && File(modelsDir, MMPROJ_FILENAME).exists()
  }

  suspend fun downloadModels() {
    if (isModelDownloaded()) {
      _state.value = DownloadState.Completed
      return
    }

    withContext(Dispatchers.IO) {
      try {
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

        // Download model
        val modelFile = File(modelsDir, MODEL_FILENAME)
        if (!modelFile.exists()) {
          downloadFile(MODEL_URL, modelFile, MODEL_FILENAME)
        }

        // Download mmproj
        val mmprojFile = File(modelsDir, MMPROJ_FILENAME)
        if (!mmprojFile.exists()) {
          downloadFile(MMPROJ_URL, mmprojFile, MMPROJ_FILENAME)
        }

        _state.value = DownloadState.Completed
        showNotification("Модели загружены", "Приложение готово к работе", -1)
        Log.i(TAG, "All models downloaded successfully")
      } catch (e: Exception) {
        Log.e(TAG, "Download failed", e)
        _state.value = DownloadState.Error("Ошибка загрузки: ${e.message}")
        showNotification("Ошибка загрузки", e.message ?: "Неизвестная ошибка", -1)
      }
    }
  }

  private fun downloadFile(urlStr: String, destFile: File, displayName: String) {
    Log.i(TAG, "Downloading $displayName from $urlStr")
    _state.value = DownloadState.Downloading(displayName, 0)

    val tmpFile = File(destFile.parent, "${destFile.name}.tmp")
    val url = URL(urlStr)
    val connection = url.openConnection() as HttpURLConnection
    connection.connectTimeout = 30_000
    connection.readTimeout = 30_000
    connection.instanceFollowRedirects = true

    try {
      connection.connect()

      if (connection.responseCode != HttpURLConnection.HTTP_OK) {
        throw RuntimeException("HTTP ${connection.responseCode}: ${connection.responseMessage}")
      }

      val totalBytes = connection.contentLengthLong
      var downloadedBytes = 0L

      connection.inputStream.use { input ->
        FileOutputStream(tmpFile).use { output ->
          val buffer = ByteArray(8192)
          var bytesRead: Int

          while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            downloadedBytes += bytesRead

            val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else -1
            _state.value = DownloadState.Downloading(displayName, progress)

            if (progress >= 0) {
              showNotification(
                "Загрузка $displayName",
                "${downloadedBytes / 1024 / 1024}MB / ${totalBytes / 1024 / 1024}MB",
                progress
              )
            }
          }
        }
      }

      tmpFile.renameTo(destFile)
      Log.i(TAG, "$displayName downloaded: ${destFile.length() / 1024 / 1024}MB")
    } finally {
      connection.disconnect()
      if (tmpFile.exists()) tmpFile.delete()
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Загрузка моделей",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Прогресс загрузки моделей ИИ"
      }
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun showNotification(title: String, text: String, progress: Int) {
    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(title)
      .setContentText(text)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(progress in 0..99)

    if (progress in 0..99) {
      builder.setProgress(100, progress, false)
    } else if (progress == -1) {
      builder.setProgress(0, 0, false)
      builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
      builder.setOngoing(false)
    }

    notificationManager.notify(NOTIFICATION_ID, builder.build())
  }
}
