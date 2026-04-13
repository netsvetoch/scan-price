package ru.ainetico.honestprice.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer

/**
 * Shimmer skeleton placeholder for async loading states.
 */
@Composable
fun ShimmerBox(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 12.dp
) {
  Box(
    modifier = modifier
        .shimmer()
        .clip(RoundedCornerShape(cornerRadius))
        .background(MaterialTheme.colorScheme.surfaceVariant)
  )
}

/**
 * Image skeleton with aspect ratio (default 3:2 for price tags).
 */
@Composable
fun ShimmerImageSkeleton(
  modifier: Modifier = Modifier,
  aspectRatio: Float = 3f / 2f
) {
  ShimmerBox(
    modifier = modifier
        .fillMaxWidth()
        .aspectRatio(aspectRatio)
  )
}

/**
 * Text line skeleton.
 */
@Composable
fun ShimmerLine(
  modifier: Modifier = Modifier,
  height: Dp = 16.dp
) {
  ShimmerBox(
    modifier = modifier
        .fillMaxWidth()
        .height(height),
    cornerRadius = 4.dp
  )
}
