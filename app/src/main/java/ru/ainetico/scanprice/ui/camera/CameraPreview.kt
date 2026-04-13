package ru.ainetico.scanprice.ui.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Wraps a CameraX PreviewView in an AndroidView composable.
 * Calls [onPreviewViewReady] once the PreviewView is created so the caller
 * can reference it for bitmap capture.
 */
@Composable
fun CameraPreview(
  modifier: Modifier = Modifier,
  onPreviewViewReady: (PreviewView) -> Unit,
  onCameraReady: (androidx.camera.core.Camera) -> Unit = {},
  onError: (Exception) -> Unit = {}
) {
  val lifecycleOwner = LocalLifecycleOwner.current

  var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      val previewView = PreviewView(ctx).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
      }
      onPreviewViewReady(previewView)

      val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
      cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        cameraProviderRef = cameraProvider
        val preview = Preview.Builder().build().also {
          it.surfaceProvider = previewView.surfaceProvider
        }
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
          cameraProvider.unbindAll()
          val camera =
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
          onCameraReady(camera)
        } catch (e: Exception) {
          onError(e)
        }
      }, ContextCompat.getMainExecutor(ctx))

      previewView
    }
  )

  // Release camera when composable leaves composition
  DisposableEffect(Unit) {
    onDispose {
      cameraProviderRef?.unbindAll()
    }
  }
}
