package ru.ainetico.honestprice.model

import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.ainetico.honestprice.R
import java.io.File

/**
 * Downloads GGUF model files using Android's system DownloadManager.
 * Survives app background, force stop, and process death.
 */
class ModelDownloader(
  private val context: Context,
  private val scope: CoroutineScope
) {

  companion object {
    private const val TAG = "ModelDownloader"

    const val MODEL_URL =
      "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf"
    const val MMPROJ_URL =
      "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/mmproj-BF16.gguf"

    const val MODEL_FILENAME = "Qwen3.5-0.8B-Q4_K_M.gguf"
    const val MMPROJ_FILENAME = "mmproj-BF16.gguf"

    private const val MODEL_SHA256 =
      "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"
    private const val MMPROJ_SHA256 =
      "d312c4d02fd46eea7a16e4f3bbb58840e6222209322ca1e33ca03247ad8935d6"

    private val EXPECTED_HASHES = mapOf(
      MODEL_FILENAME to MODEL_SHA256,
      MMPROJ_FILENAME to MMPROJ_SHA256
    )

  }

  @androidx.compose.runtime.Stable
  data class FileProgress(val label: String, val progress: Int, val done: Boolean = false)

  @androidx.compose.runtime.Stable
  sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val file1: FileProgress, val file2: FileProgress) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
  }

  private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
  val state: StateFlow<DownloadState> = _state

  private val file1Progress =
    MutableStateFlow(FileProgress(context.getString(R.string.download_file1_label), 0))
  private val file2Progress =
    MutableStateFlow(FileProgress(context.getString(R.string.download_file2_label), 0))

  private val downloadManager =
    context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
  private val notificationHelper = DownloadNotificationHelper(context)

  init {
    notificationHelper.createChannels()
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

        if (!needModel) file1Progress.value =
          FileProgress(context.getString(R.string.download_file1_label), 100, done = true)
        if (!needMmproj) file2Progress.value =
          FileProgress(context.getString(R.string.download_file2_label), 100, done = true)

        _state.value = DownloadState.Downloading(file1Progress.value, file2Progress.value)

        // Launch both downloads in parallel (async so exceptions propagate on await)
        val deferred1 = if (needModel) {
          scope.async {
            downloadFileWithManager(
              MODEL_URL,
              MODEL_FILENAME,
              file1Progress
            )
          }
        } else null

        val deferred2 = if (needMmproj) {
          scope.async {
            downloadFileWithManager(
              MMPROJ_URL,
              MMPROJ_FILENAME,
              file2Progress
            )
          }
        } else null

        // Poll, update UI state and silent notification
        while (deferred1?.isActive == true || deferred2?.isActive == true) {
          val f1 = file1Progress.value
          val f2 = file2Progress.value
          _state.value = DownloadState.Downloading(f1, f2)

          val totalProgress = (f1.progress + f2.progress) / 2
          notificationHelper.showProgress(
            context.getString(R.string.download_progress_title),
            "$totalProgress%",
            totalProgress
          )

          delay(2000)
        }

        // await() rethrows exceptions from child coroutines into this try/catch
        deferred1?.await()
        deferred2?.await()

        if (isModelDownloaded()) {
          notificationHelper.cancelProgress()
          _state.value = DownloadState.Completed
          Log.i(TAG, "All models downloaded")
        } else {
          _state.value =
            DownloadState.Error(context.getString(R.string.download_error_incomplete))
        }
      } catch (e: Exception) {
        Log.e(TAG, "Download failed", e)
        _state.value = DownloadState.Error(
          context.getString(
            R.string.download_error_format,
            e.message
          )
        )
      }
    }
  }

  /**
   * Download file using system DownloadManager and poll progress.
   * Blocks until download completes or fails.
   */
  private suspend fun downloadFileWithManager(
    url: String,
    filename: String,
    progressFlow: MutableStateFlow<FileProgress>
  ) {
    val modelsDir = File(context.filesDir, "models")
    val destFile = File(modelsDir, filename)

    Log.i(TAG, "Starting download: ${progressFlow.value.label} → $url")

    val label = progressFlow.value.label

    // Enqueue download — downloads to public Downloads dir first (DownloadManager limitation)
    val request = DownloadManager.Request(url.toUri())
      .setTitle("${context.getString(R.string.app_name)} — $label")
      .setDescription(context.getString(R.string.download_description))
      .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
      .setDestinationInExternalPublicDir(
        Environment.DIRECTORY_DOWNLOADS,
        "honestprice_$filename"
      )
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
            val progress =
              if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
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
                // Verify SHA256 before accepting
                val expectedHash = EXPECTED_HASHES[filename]
                if (expectedHash != null) {
                  val actualHash = FileHashVerifier.sha256(destFile)
                  if (actualHash != expectedHash) {
                    destFile.delete()
                    downloadManager.remove(downloadId)
                    throw SecurityException(
                      "SHA256 mismatch for $filename: expected $expectedHash, got $actualHash"
                    )
                  }
                  Log.i(TAG, "$label SHA256 verified")
                }
                // Remove from Downloads
                downloadManager.remove(downloadId)
                progressFlow.value =
                  progressFlow.value.copy(progress = 100, done = true)
                Log.i(
                  TAG,
                  "$label downloaded: ${destFile.length() / 1024 / 1024}MB"
                )
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
