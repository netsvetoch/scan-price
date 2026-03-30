package ru.ainetico.honestprice.model

import android.graphics.Rect

data class OcrBlock(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float
)
