package ru.ainetico.honestprice.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ru.ainetico.honestprice.FrameConfig

/**
 * Draws a darkened overlay with a transparent "frame" cutout — rounded rect,
 * ~85% width, 50% height aspect ratio.
 */
@Composable
fun FrameOverlay(
  modifier: Modifier = Modifier,
  aspectRatio: Float = FrameConfig.ASPECT_RATIO
) {
  Canvas(modifier = modifier) {
    // Base rectangle from WIDTH_FRACTION, then rotate for vertical
    val baseWidth = size.width * FrameConfig.WIDTH_FRACTION
    val baseHeight = baseWidth / FrameConfig.ASPECT_RATIO
    val frameWidth = if (aspectRatio >= 1f) baseWidth else baseHeight
    val frameHeight = if (aspectRatio >= 1f) baseHeight else baseWidth
    val left = (size.width - frameWidth) / 2f
    val top = FrameConfig.frameTop(size.height, frameHeight, density)
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
