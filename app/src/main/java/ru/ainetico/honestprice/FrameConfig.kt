package ru.ainetico.honestprice

/**
 * Global frame configuration — shared between UI overlay and image cropping.
 * TODO: move to user settings later.
 */
object FrameConfig {
    /** Frame width as fraction of image/screen width */
    const val WIDTH_FRACTION = 0.85f

    /** Frame aspect ratio (width / height), e.g. 3:2 = 1.5 */
    const val ASPECT_RATIO = 3f / 2f

    /** Top controls reserved area in dp (torch/rotate buttons + padding) */
    const val TOP_INSET_DP = 80f

    /** Bottom controls reserved area in dp (zoom + capture button + padding) */
    const val BOTTOM_INSET_DP = 168f

    /** Effective aspect ratio: inverted when frame is vertical */
    fun ratio(isVertical: Boolean): Float =
        if (isVertical) 1f / ASPECT_RATIO else ASPECT_RATIO

    /** Calculate frame top position in pixels, centered in free area between controls */
    fun frameTop(containerHeight: Float, frameHeight: Float, density: Float): Float {
        val topInset = TOP_INSET_DP * density
        val bottomInset = BOTTOM_INSET_DP * density
        val freeTop = topInset
        val freeHeight = containerHeight - topInset - bottomInset
        return freeTop + (freeHeight - frameHeight) / 2f
    }
}
