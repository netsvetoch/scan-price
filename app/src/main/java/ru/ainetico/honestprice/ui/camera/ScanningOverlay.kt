package ru.ainetico.honestprice.ui.camera

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ru.ainetico.honestprice.ui.theme.ScanLineGreen
import ru.ainetico.honestprice.ui.theme.ScanLineGreenAlpha

@Composable
fun ScanningOverlay(modifier: Modifier = Modifier) {
  val infiniteTransition = rememberInfiniteTransition(label = "scanning")
  val scanLineY by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1500, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "scanLine"
  )

  Canvas(modifier = modifier.fillMaxSize()) {
    val lineHeight = 4f
    val gradientHeight = size.height * 0.15f
    val y = scanLineY * size.height

    drawRect(
      brush = Brush.verticalGradient(
        colors = listOf(Color.Transparent, ScanLineGreenAlpha),
        startY = (y - gradientHeight).coerceAtLeast(0f),
        endY = y
      ),
      topLeft = Offset(0f, (y - gradientHeight).coerceAtLeast(0f)),
      size = Size(size.width, gradientHeight.coerceAtMost(y))
    )

    drawRect(
      color = ScanLineGreen,
      topLeft = Offset(0f, y),
      size = Size(size.width, lineHeight)
    )
  }
}
