package ru.ainetico.honestprice.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.ui.theme.Dimens

@Composable
fun CameraScreen(
  viewModel: CameraViewModel,
  openGallery: Boolean = false
) {
  val context = LocalContext.current
  val state by viewModel.state.collectAsState()
  val isVertical by viewModel.isVerticalFrame.collectAsState()
  var cameraRef by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
  var torchEnabled by remember { mutableStateOf(false) }

  // Keep screen on while camera is active
  val view = LocalView.current
  DisposableEffect(Unit) {
    view.keepScreenOn = true
    onDispose { view.keepScreenOn = false }
  }
  val frameAspectRatio =
    ru.ainetico.honestprice.FrameConfig.ratio(isVertical)

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

  // PreviewView reference shared between CameraPreview composable and capture button
  var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    when {
      state is CameraState.RemoteError -> {
        val remoteError = state as CameraState.RemoteError
        ErrorContent(
          message = remoteError.message,
          previewBitmap = remoteError.croppedBitmap,
          frameAspectRatio = frameAspectRatio,
          primaryAction = { viewModel.retryWithLocal() },
          primaryLabel = stringResource(R.string.camera_process_local),
          secondaryAction = { viewModel.dismissError() },
          secondaryLabel = stringResource(R.string.action_cancel)
        )
      }

      state is CameraState.Error -> {
        val error = state as CameraState.Error
        ErrorContent(
          message = error.message,
          previewBitmap = error.previewBitmap,
          frameAspectRatio = frameAspectRatio,
          primaryAction = { viewModel.dismissError() },
          primaryLabel = stringResource(R.string.camera_retry)
        )
      }

      state is CameraState.Adjusting -> {
        val adjusting = state as CameraState.Adjusting
        AdjustingContent(
          bitmap = adjusting.bitmap,
          frameAspectRatio = frameAspectRatio,
          onToggleOrientation = { viewModel.toggleFrameOrientation() },
          onConfirm = { viewWidth, viewHeight, offsetX, offsetY, zoom ->
            viewModel.confirmAdjustment(adjusting.bitmap, viewWidth, viewHeight, offsetX, offsetY, zoom)
          }
        )
      }

      state is CameraState.Scanning -> {
        val scanning = state as CameraState.Scanning
        ScanningContent(
          displayBitmap = scanning.displayBitmap,
          status = scanning.status,
          frameAspectRatio = frameAspectRatio
        )
      }

      !cameraPermissionGranted -> {
        CameraPermissionContent(
          permissionDeniedPermanently = permissionDeniedPermanently,
          onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
          onOpenSettings = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
              data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
          },
          onGallery = { galleryLauncher.launch("image/*") },
          onManualEntry = { viewModel.onManualEntry() }
        )

        // Bottom controls
        BottomGalleryControls(
          onGallery = { galleryLauncher.launch("image/*") },
          onManualEntry = { viewModel.onManualEntry() },
          captureButton = { Spacer(modifier = Modifier.size(Dimens.CaptureButtonSize)) },
          modifier = Modifier.align(Alignment.BottomCenter)
        )
      }

      else -> {
        // Live camera preview with controls
        CameraPreview(
          modifier = Modifier.fillMaxSize(),
          onPreviewViewReady = { previewViewRef = it },
          onCameraReady = { cameraRef = it },
          onError = { e -> Log.e("CameraScreen", "Camera binding failed", e) }
        )

        // Darkened frame overlay
        FrameOverlay(modifier = Modifier.fillMaxSize(), aspectRatio = frameAspectRatio)

        // Top buttons: torch + rotate
        Row(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 16.dp, end = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Torch button
          if (cameraRef?.cameraInfo?.hasFlashUnit() == true) {
            Button(
              onClick = {
                torchEnabled = !torchEnabled
                cameraRef?.cameraControl?.enableTorch(torchEnabled)
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.4f)),
              modifier = Modifier.size(Dimens.ToolbarButtonSize),
              contentPadding = PaddingValues(0.dp),
              shape = CircleShape
            ) {
              Icon(
                if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = stringResource(if (torchEnabled) R.string.camera_torch_on else R.string.camera_torch_off),
                tint = Color.White
              )
            }
          }
          // Rotate frame button
          Button(
            onClick = { viewModel.toggleFrameOrientation() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.4f)),
            modifier = Modifier.size(Dimens.ToolbarButtonSize),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
          ) {
            Icon(Icons.Filled.CropRotate, contentDescription = stringResource(R.string.camera_rotate_frame), tint = Color.White)
          }
        }

        // Zoom toggle — centered above bottom controls
        if (cameraRef != null) {
          var isZoomed by remember { mutableStateOf(false) }
          Button(
            onClick = {
              isZoomed = !isZoomed
              cameraRef?.cameraControl?.setZoomRatio(if (isZoomed) 2f else 1f)
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isZoomed) Color.White else Color.White.copy(alpha = 0.4f)
            ),
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = Dimens.ZoomToggleBottomOffset)
              .size(Dimens.ZoomToggleSize),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
          ) {
            Text(
              if (isZoomed) "x2" else "x1",
              color = if (isZoomed) Color.Black else Color.White,
              fontSize = Dimens.FontSizeCaption
            )
          }
        }

        // Bottom controls
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(bottom = Dimens.BottomControlsBottomPadding, start = Dimens.BottomControlsSidePadding, end = Dimens.BottomControlsSidePadding),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Gallery button
          Button(
            onClick = { galleryLauncher.launch("image/*") },
            colors = ButtonDefaults.buttonColors(
              containerColor = Color.White.copy(alpha = 0.4f)
            ),
            modifier = Modifier.size(Dimens.ActionButtonSize),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
          ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.camera_gallery), tint = Color.White)
          }

          // Capture button
          Button(
            onClick = {
              previewViewRef?.bitmap?.let { bmp ->
                viewModel.capture(bmp, cropRect = null)
              }
            },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color.White
              ),
            modifier = Modifier.size(Dimens.CaptureButtonSize),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
          ) {
            Box(
              modifier = Modifier
                .size(Dimens.CaptureButtonInner)
                .clip(CircleShape)
                .border(2.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
            )
          }

          // Manual entry button
          Button(
            onClick = { viewModel.onManualEntry() },
            colors = ButtonDefaults.buttonColors(
              containerColor = Color.White.copy(alpha = 0.4f)
            ),
            modifier = Modifier.size(Dimens.ActionButtonSize),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
          ) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.camera_manual), tint = Color.White)
          }
        }
      }
    }
  }
}

@Composable
private fun ErrorContent(
  message: String,
  previewBitmap: android.graphics.Bitmap,
  frameAspectRatio: Float,
  primaryAction: () -> Unit,
  primaryLabel: String,
  secondaryAction: (() -> Unit)? = null,
  secondaryLabel: String? = null
) {
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
        .clip(RoundedCornerShape(Dimens.CornerRadiusLarge))
        .border(Dimens.FrameBorderInner, MaterialTheme.colorScheme.error, RoundedCornerShape(Dimens.CornerRadiusLarge))
        .background(Color.DarkGray),
      contentAlignment = Alignment.Center
    ) {
      Image(
        bitmap = previewBitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier
          .fillMaxSize()
          .clip(RoundedCornerShape(Dimens.CornerRadiusLarge)),
        contentScale = ContentScale.Fit,
        alpha = 0.5f
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = message,
      color = MaterialTheme.colorScheme.error,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 32.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(onClick = primaryAction) {
      Text(primaryLabel)
    }

    if (secondaryAction != null && secondaryLabel != null) {
      Spacer(modifier = Modifier.height(8.dp))
      TextButton(onClick = secondaryAction) {
        Text(secondaryLabel)
      }
    }
  }
}

@Composable
private fun ScanningContent(
  displayBitmap: android.graphics.Bitmap?,
  status: String,
  frameAspectRatio: Float
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(bottom = 12.dp)
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(Dimens.ScanningIndicatorSize),
        color = MaterialTheme.colorScheme.primary,
        strokeWidth = Dimens.ScanningStrokeWidth
      )
      Text(
        text = status,
        style = MaterialTheme.typography.bodyMedium
      )
    }

    Box(
      modifier = Modifier
        .padding(horizontal = 24.dp)
        .fillMaxWidth()
        .aspectRatio(frameAspectRatio)
        .clip(RoundedCornerShape(Dimens.CornerRadiusLarge))
        .border(Dimens.FrameBorderInner, Color.White.copy(alpha = 0.3f), RoundedCornerShape(Dimens.CornerRadiusLarge))
        .background(Color.DarkGray),
      contentAlignment = Alignment.Center
    ) {
      if (displayBitmap != null) {
        Image(
          bitmap = displayBitmap.asImageBitmap(),
          contentDescription = null,
          modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(Dimens.CornerRadiusLarge)),
          contentScale = ContentScale.Fit
        )
        ScanningOverlay(modifier = Modifier.matchParentSize())
      }
    }
  }
}
