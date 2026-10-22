package com.dominikgold.tonka.platform

import kotlin.math.roundToInt

data class CropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

fun centerCrop(width: Int, height: Int, aspectRatio: Float?): CropRect {
    if (aspectRatio == null) return CropRect(0, 0, width, height)
    val currentRatio = width.toFloat() / height
    return if (currentRatio > aspectRatio) {
        val croppedWidth = (height * aspectRatio).roundToInt().coerceAtMost(width)
        CropRect(left = (width - croppedWidth) / 2, top = 0, width = croppedWidth, height = height)
    } else {
        val croppedHeight = (width / aspectRatio).roundToInt().coerceAtMost(height)
        CropRect(left = 0, top = (height - croppedHeight) / 2, width = width, height = croppedHeight)
    }
}

fun scaledSize(width: Int, height: Int, maxLongEdge: Int): Pair<Int, Int> {
    val longEdge = maxOf(width, height)
    if (longEdge <= maxLongEdge) return width to height
    val factor = maxLongEdge.toFloat() / longEdge
    return (width * factor).roundToInt().coerceAtLeast(1) to (height * factor).roundToInt().coerceAtLeast(1)
}
