package ru.ainetico.honestprice.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.ainetico.honestprice.R

@Composable
fun CameraScreen(
  viewModel: CameraViewModel,
  openGallery: Boolean = false
) {
  val context = LocalContext.current
  val state by viewModel.state.collectAsState()
  val isVertical by viewModel.isVerticalFrame.collectAsState()
  val frameAspectRatio =
    if (isVertical) 1f / ru.ainetico.honestprice.FrameConfig.ASPECT_RATIO else ru.ainetico.honestprice.FrameConfig.ASPECT_RATIO
  // Navigation events handled by parent (HistoryScreen)

  // Permission state
  var cameraPermissionGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
      ) == PackageManager.PERMISSION_GRANTED
    )
  }
  var permissionDeniedPermanently by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    cameraPermissionGranted = granted
    if (!granted) {
      permissionDeniedPermanently = true
    }
  }

  // Gallery picker
  val galleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    uri?.let { viewModel.importFromGallery(it, context) }
  }

  // Auto-launch gallery if requested
  LaunchedEffect(openGallery) {
    if (openGallery) {
      galleryLauncher.launch("image/*")
    }
  }

  // No auto-request — permission is handled on onboarding or via in-screen button

  // PreviewView reference shared between CameraPreview composable and capture button
  var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    when {
      state is CameraState.RemoteError -> {
        val remoteError = state as CameraState.RemoteError
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Box(
            modifier = Modifier
              .padding(24.dp)
              .fillMaxWidth()
              .aspectRatio(frameAspectRatio)
              .clip(RoundedCornerShape(16.dp))
              .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp))
              .background(Color.DarkGray),
            contentAlignment = Alignment.Center
          ) {
            Image(
              bitmap = remoteError.croppedBitmap.asImageBitmap(),
              contentDescription = null,
              modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp)),
              contentScale = ContentScale.Fit,
              alpha = 0.5f
            )
          }
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = remoteError.message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(onClick = { viewModel.retryWithLocal() }) {
            Text(stringResource(R.string.camera_process_local))
          }
          Spacer(modifier = Modifier.height(8.dp))
          TextButton(onClick = { viewModel.dismissError() }) {
            Text(stringResource(R.string.action_cancel))
          }
        }
      }

      state is CameraState.Error -> {
        val error = state as CameraState.Error

        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Box(
            modifier = Modifier
              .padding(24.dp)
              .fillMaxWidth()
              .aspectRatio(frameAspectRatio)
              .clip(RoundedCornerShape(16.dp))
              .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp))
              .background(Color.DarkGray),
            contentAlignment = Alignment.Center
          ) {
            Image(
              bitmap = error.previewBitmap.asImageBitmap(),
              contentDescription = null,
              modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp)),
              contentScale = ContentScale.Fit,
              alpha = 0.5f
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          Text(
            text = error.message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
          )

          Spacer(modifier = Modifier.height(16.dp))

          Button(onClick = { viewModel.dismissError() }) {
            Text(stringResource(R.string.camera_retry))
          }
        }
      }

      state is CameraState.Adjusting -> {
        // User drags the gallery image to align price tag within frame
        val adjusting = state as CameraState.Adjusting
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }
        var zoom by remember { mutableStateOf(1f) }

        var viewWidth by remember { mutableStateOf(0f) }
        var viewHeight by remember { mutableStateOf(0f) }

        Box(
          modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
              viewWidth = coords.size.width.toFloat()
              viewHeight = coords.size.height.toFloat()
            }
        ) {
          Image(
            bitmap = adjusting.bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer(
                translationX = offsetX,
                translationY = offsetY,
                scaleX = zoom,
                scaleY = zoom
              )
              .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                  offsetX += pan.x
                  offsetY += pan.y
                  zoom = (zoom * gestureZoom).coerceIn(0.5f, 5f)
                }
              },
            contentScale = ContentScale.FillWidth
          )

          // Frame overlay on top
          FrameOverlay(modifier = Modifier.fillMaxSize(), aspectRatio = frameAspectRatio)

          // Hint text + rotate button
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .align(Alignment.TopCenter)
              .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = stringResource(R.string.camera_frame_hint),
              color = Color.White,
              fontSize = 14.sp,
              textAlign = TextAlign.Center,
              modifier = Modifier
                .weight(1f)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Button(
              onClick = { viewModel.toggleFrameOrientation() },
              colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.4f)),
              modifier = Modifier.size(48.dp),
              contentPadding = PaddingValues(0.dp),
              shape = CircleShape
            ) {
              Icon(
                Icons.Filled.CropRotate,
                contentDescription = stringResource(R.string.camera_rotate_frame),
                tint = Color.White
              )
            }
          }

          // Confirm button
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .align(Alignment.BottomCenter)
              .padding(bottom = 32.dp),
            contentAlignment = Alignment.Center
          ) {
            Button(onClick = {
              viewModel.confirmAdjustment(
                adjusting.bitmap,
                viewWidth,
                viewHeight,
                offsetX,
                offsetY,
                zoom
              )
            }) {
              Text(stringResource(R.string.camera_scan_button))
            }
          }
        }
      }

      state is CameraState.Scanning -> {
        val scanning = state as CameraState.Scanning

        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Status above image
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              color = MaterialTheme.colorScheme.primary,
              strokeWidth = 2.dp
            )
            Text(
              text = scanning.status,
              style = MaterialTheme.typography.bodyMedium
            )
          }

          Box(
            modifier = Modifier
              .padding(horizontal = 24.dp)
              .fillMaxWidth()
              .aspectRatio(frameAspectRatio)
              .clip(RoundedCornerShape(16.dp))
              .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
              .background(Color.DarkGray),
            contentAlignment = Alignment.Center
          ) {
            if (scanning.displayBitmap != null) {
              Image(
                bitmap = scanning.displayBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                  .fillMaxSize()
                  .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
              )
              ScanningOverlay(modifier = Modifier.matchParentSize())
            }
          }
        }

      }

      !cameraPermissionGranted -> {
        // No camera permission — show explanation + allow button + gallery/manual controls
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = stringResource(R.string.camera_permission_needed),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = {
              if (permissionDeniedPermanently) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                  data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
              } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
              }
            }
          ) {
            Text(stringResource(R.string.onboarding_allow))
          }
        }

        // Bottom controls — exact same layout as camera mode
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Button(
            onClick = { galleryLauncher.launch("image/*") },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.size(56.dp),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
          ) {
            Icon(
              Icons.Filled.PhotoLibrary,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          // Invisible placeholder matching capture button size
          Spacer(modifier = Modifier.size(72.dp))
          Button(
            onClick = { viewModel.onManualEntry() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.size(56.dp),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
          ) {
            Icon(
              Icons.Filled.EditNote,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      else -> {
        // Live camera preview with controls
        CameraPreview(
          modifier = Modifier.fillMaxSize(),
          onPreviewViewReady = { previewViewRef = it }
        )

        // Darkened frame overlay
        FrameOverlay(modifier = Modifier.fillMaxSize(), aspectRatio = frameAspectRatio)

        // Rotate frame button
        Button(
          onClick = { viewModel.toggleFrameOrientation() },
          colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 16.dp, end = 16.dp)
            .size(48.dp),
          contentPadding = PaddingValues(0.dp),
          shape = CircleShape
        ) {
          Icon(Icons.Filled.CropRotate, contentDescription = stringResource(R.string.camera_rotate_frame), tint = Color.White)
        }

        // Bottom controls
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Gallery button
          Column(
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Button(
              onClick = { galleryLauncher.launch("image/*") },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.2f)
              ),
              modifier = Modifier.size(56.dp),
              contentPadding = PaddingValues(0.dp),
              shape = CircleShape
            ) {
              Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White)
            }
          }

          // Capture button (big white circle)
          Column(
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Button(
              onClick = {
                previewViewRef?.bitmap?.let { bmp ->
                  viewModel.capture(bmp, cropRect = null)
                }
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color.White
              ),
              modifier = Modifier.size(72.dp),
              contentPadding = PaddingValues(0.dp),
              shape = CircleShape
            ) {
              Box(
                modifier = Modifier
                  .size(60.dp)
                  .clip(CircleShape)
                  .border(2.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
              )
            }
          }

          // Manual entry button
          Column(
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Button(
              onClick = { viewModel.onManualEntry() },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.2f)
              ),
              modifier = Modifier.size(56.dp),
              contentPadding = PaddingValues(0.dp),
              shape = CircleShape
            ) {
              Icon(Icons.Filled.EditNote, contentDescription = null, tint = Color.White)
            }
          }
        }
      }
    }
  }
}

/**
 * Draws a darkened overlay with a transparent "frame" cutout — rounded rect,
 * ~85% width, 50% height aspect ratio.
 */
@Composable
private fun FrameOverlay(
  modifier: Modifier = Modifier,
  aspectRatio: Float = ru.ainetico.honestprice.FrameConfig.ASPECT_RATIO
) {
  Canvas(modifier = modifier) {
    val frameWidth = size.width * ru.ainetico.honestprice.FrameConfig.WIDTH_FRACTION
    val frameHeight = frameWidth / aspectRatio
    val left = (size.width - frameWidth) / 2f
    val top =
      (size.height - frameHeight) / 2f - size.height * ru.ainetico.honestprice.FrameConfig.VERTICAL_OFFSET_FRACTION
    val cornerRadius = 16.dp.toPx()

    // Outer border (dark) for contrast on light backgrounds
    drawRoundRect(
      color = Color(0xAA000000),
      topLeft = Offset(left - 1.dp.toPx(), top - 1.dp.toPx()),
      size = Size(frameWidth + 2.dp.toPx(), frameHeight + 2.dp.toPx()),
      cornerRadius = CornerRadius(cornerRadius + 1.dp.toPx()),
      style = Stroke(width = 3.dp.toPx())
    )

    // Inner border (white) for contrast on dark backgrounds
    drawRoundRect(
      color = Color.White,
      topLeft = Offset(left, top),
      size = Size(frameWidth, frameHeight),
      cornerRadius = CornerRadius(cornerRadius),
      style = Stroke(width = 2.dp.toPx())
    )
  }
}

/**
 * Wraps a CameraX PreviewView in an AndroidView composable.
 * Calls [onPreviewViewReady] once the PreviewView is created so the caller
 * can reference it for bitmap capture.
 */
@Composable
private fun CameraPreview(
  modifier: Modifier = Modifier,
  onPreviewViewReady: (PreviewView) -> Unit
) {
  LocalContext.current
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
          cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
        } catch (e: Exception) {
          // Camera binding failed — e.g., no back camera
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
