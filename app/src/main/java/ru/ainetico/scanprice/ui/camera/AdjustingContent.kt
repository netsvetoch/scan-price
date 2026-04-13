package ru.ainetico.scanprice.ui.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.ainetico.scanprice.R
import ru.ainetico.scanprice.ui.theme.Dimens

/**
 * Gallery image adjustment screen — user drags/zooms to align the price tag within the frame overlay.
 */
@Composable
fun AdjustingContent(
  bitmap: Bitmap,
  frameAspectRatio: Float,
  onToggleOrientation: () -> Unit,
  onConfirm: (viewWidth: Float, viewHeight: Float, offsetX: Float, offsetY: Float, zoom: Float) -> Unit
) {
  var offsetX by remember { mutableFloatStateOf(0f) }
  var offsetY by remember { mutableFloatStateOf(0f) }
  var zoom by remember { mutableFloatStateOf(1f) }

  var viewWidth by remember { mutableFloatStateOf(0f) }
  var viewHeight by remember { mutableFloatStateOf(0f) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .onGloballyPositioned { coords ->
        viewWidth = coords.size.width.toFloat()
        viewHeight = coords.size.height.toFloat()
      }
  ) {
    Image(
      bitmap = bitmap.asImageBitmap(),
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
        fontSize = Dimens.FontSizeSubtitle,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .weight(1f)
          .background(
            Color.Black.copy(alpha = 0.5f),
            RoundedCornerShape(Dimens.CornerRadiusSmall)
          )
          .padding(horizontal = 16.dp, vertical = 8.dp)
      )
      Button(
        onClick = onToggleOrientation,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.4f)),
        modifier = Modifier.size(Dimens.ToolbarButtonSize),
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
        onConfirm(viewWidth, viewHeight, offsetX, offsetY, zoom)
      }) {
        Text(stringResource(R.string.camera_scan_button))
      }
    }
  }
}
