package ru.ainetico.honestprice.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Semantic dimension constants for the app UI.
 * Standard Material spacing (4/8/12/16/24/32.dp) stays inline.
 */
object Dimens {
  // Corner radii
  val CornerRadiusSmall = 8.dp
  val CornerRadiusMedium = 12.dp
  val CornerRadiusLarge = 16.dp

  // Camera controls
  val CaptureButtonSize = 72.dp
  val CaptureButtonInner = 60.dp
  val ActionButtonSize = 56.dp
  val ToolbarButtonSize = 48.dp
  val ZoomToggleSize = 40.dp
  val ZoomToggleBottomOffset = 132.dp
  val BottomControlsBottomPadding = 48.dp
  val BottomControlsSidePadding = 32.dp
  val ScanningIndicatorSize = 18.dp
  val ScanningStrokeWidth = 2.dp

  // Frame overlay
  val FrameBorderOuter = 3.dp
  val FrameBorderInner = 2.dp
  val FrameBorderOffset = 1.dp

  // History FABs
  val FabSizeLarge = 64.dp
  val FabSizeSmall = 48.dp
  val FabIconSize = 32.dp
  val SkeletonItemHeight = 72.dp
  val AppendIndicatorSize = 24.dp
  val ScanningSheetHeight = 320.dp

  // Onboarding
  val OnboardingIconSize = 96.dp
  val DotIndicatorSize = 8.dp

  // Settings
  val LanguageDropdownWidth = 160.dp
  val ConnectionSpinnerSize = 24.dp
  val ExportSpinnerSize = 20.dp

  // PriceCard
  val PriceCardPadding = 20.dp

  // Font sizes (outside theme Typography)
  val FontSizePrice = 32.sp
  val FontSizeCardPrice = 18.sp
  val FontSizeSubtitle = 14.sp
  val FontSizeCaption = 13.sp
  val FontSizeBody = 16.sp
}
