package ru.ainetico.honestprice.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.filterNotNull
import ru.ainetico.honestprice.R

@Composable
fun CameraScreen(
  viewModel: CameraViewModel,
  openGallery: Boolean = false
) {
  val context = LocalContext.current
  val state by viewModel.state.collectAsState()
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
              .clip(RoundedCornerShape(16.dp))
              .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp))
          ) {
            Image(
              bitmap = error.previewBitmap.asImageBitmap(),
              contentDescription = null,
              modifier = Modifier
                .fillMaxWidth()
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
            Text("Попробовать снова")
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
          Box(
            modifier = Modifier
              .padding(24.dp)
              .clip(RoundedCornerShape(16.dp))
              .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
              .background(Color.DarkGray)
          ) {
            if (scanning.displayBitmap != null) {
              Image(
                bitmap = scanning.displayBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth
              )
              ScanningOverlay(modifier = Modifier.matchParentSize())
            } else {
              // Skeleton 3:2
              Spacer(
                modifier = Modifier
                  .fillMaxWidth()
                  .aspectRatio(3f / 2f)
              )
            }
          }
        }

        // Scanning label
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .padding(top = 64.dp),
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = Color(0xFF00E676),
              strokeWidth = 2.dp
            )
            Text(
              text = scanning.status,
              color = Color.White,
              fontSize = 16.sp
            )
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
            color = Color.White,
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
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.size(56.dp),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
          ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White)
          }
          // Invisible placeholder matching capture button size
          Spacer(modifier = Modifier.size(72.dp))
          Button(
            onClick = { viewModel.onManualEntry() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.size(56.dp),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
          ) {
            Icon(Icons.Filled.EditNote, contentDescription = null, tint = Color.White)
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
        FrameOverlay(modifier = Modifier.fillMaxSize())

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
private fun FrameOverlay(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val frameWidth = size.width * ru.ainetico.honestprice.FrameConfig.WIDTH_FRACTION
    val frameHeight = frameWidth / ru.ainetico.honestprice.FrameConfig.ASPECT_RATIO
    val left = (size.width - frameWidth) / 2f
    val top = (size.height - frameHeight) / 2f - size.height * ru.ainetico.honestprice.FrameConfig.VERTICAL_OFFSET_FRACTION
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
  val context = LocalContext.current
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
