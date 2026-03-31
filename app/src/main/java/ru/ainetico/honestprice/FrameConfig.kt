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

    /** Vertical offset as fraction of height (positive = up from center) */
    const val VERTICAL_OFFSET_FRACTION = 0.15f
}
