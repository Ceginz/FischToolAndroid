package com.example.autoclicker

import android.graphics.Bitmap

object ScreenDetector {

    private const val BAR_LEFT = 0.313f
    private const val BAR_RIGHT = 0.683f
    private const val BAR_TOP = 0.804f
    private const val BAR_BOTTOM = 0.868f
    private const val BAR_MID_Y = (BAR_TOP + BAR_BOTTOM) / 2f

    private const val LINE_TOP = 0.755f
    private const val LINE_BOTTOM = 0.801f

    private const val CAST_X = 0.832f
    private const val CAST_Y = 0.675f

    private const val SHAKE_CX = 0.223f
    private const val SHAKE_CY = 0.498f
    private const val SHAKE_RADIUS = 0.045f

    data class ReelState(val barFillX: Float?, val lineX: Float?)

    fun analyzeReelBar(bitmap: Bitmap): ReelState {
        val w = bitmap.width
        val h = bitmap.height
        return ReelState(findFillEdge(bitmap, w, h), findLineX(bitmap, w, h))
    }

    fun holdPoint(w: Int, h: Int): Pair<Int, Int> =
        ((BAR_LEFT + BAR_RIGHT) / 2f * w).toInt() to (BAR_MID_Y * h).toInt()

    fun castButtonPoint(w: Int, h: Int): Pair<Int, Int> =
        (CAST_X * w).toInt() to (CAST_Y * h).toInt()

    fun shakeButtonPoint(w: Int, h: Int): Pair<Int, Int> =
        (SHAKE_CX * w).toInt() to (SHAKE_CY * h).toInt()

    fun isShakeButtonVisible(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val cx = (SHAKE_CX * w).toInt()
        val cy = (SHAKE_CY * h).toInt()
        val r = (SHAKE_RADIUS * w).toInt()

        val left = (cx - r).coerceIn(0, w - 1)
        val top = (cy - r).coerceIn(0, h - 1)
        val size = (r * 2).coerceAtMost(minOf(w - left, h - top))
        if (size <= 0) return false

        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, left, top, size, size)

        var darkCount = 0
        var brightCount = 0
        for (px in pixels) {
            val r8 = (px shr 16) and 0xFF
            val g8 = (px shr 8) and 0xFF
            val b8 = px and 0xFF
            val maxC = maxOf(r8, g8, b8)
            if (maxC < 45) darkCount++
            else if (r8 > 170 && g8 > 170 && b8 > 170) brightCount++
        }

        val total = pixels.size
        return darkCount > total * 0.5 && brightCount > total * 0.02
    }

    private fun isBright(px: Int): Boolean {
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        return b > 150 && g > 120
    }

    private fun isNearBlack(px: Int): Boolean {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val b = px and 0xFF
        return r < 45 && g < 45 && b < 45
    }

    private fun findFillEdge(bitmap: Bitmap, w: Int, h: Int): Float? {
        val xStart = (BAR_LEFT * w).toInt().coerceIn(0, w - 1)
        val xEnd = (BAR_RIGHT * w).toInt().coerceIn(xStart + 1, w)
        val y = (BAR_MID_Y * h).toInt().coerceIn(0, h - 1)

        val rowWidth = xEnd - xStart
        val row = IntArray(rowWidth)
        bitmap.getPixels(row, 0, rowWidth, xStart, y, rowWidth, 1)

        var lastBrightIndex = -1
        var i = 0
        while (i < rowWidth) {
            if (isBright(row[i])) {
                lastBrightIndex = i
                i++
                continue
            }
            val sustained = (0 until 25).all { offset ->
                val idx = i + offset
                idx >= rowWidth || !isBright(row[idx])
            }
            if (sustained) {
                return if (lastBrightIndex >= 0) (xStart + lastBrightIndex).toFloat() / w else null
            }
            i++
        }
        return if (lastBrightIndex >= 0) (xStart + lastBrightIndex).toFloat() / w else null
    }

    private fun findLineX(bitmap: Bitmap, w: Int, h: Int): Float? {
        val margin = (0.02f * w).toInt()
        val xStart = ((BAR_LEFT * w).toInt() - margin).coerceIn(0, w - 1)
        val xEnd = ((BAR_RIGHT * w).toInt() + margin).coerceIn(xStart + 1, w)
        val yStart = (LINE_TOP * h).toInt().coerceIn(0, h - 1)
        val yEnd = (LINE_BOTTOM * h).toInt().coerceIn(yStart + 1, h)

        val regionW = xEnd - xStart
        val regionH = yEnd - yStart
        val pixels = IntArray(regionW * regionH)
        bitmap.getPixels(pixels, 0, regionW, xStart, yStart, regionW, regionH)

        var sumX = 0L
        var count = 0
        for (row in 0 until regionH) {
            val base = row * regionW
            for (col in 0 until regionW) {
                if (isNearBlack(pixels[base + col])) {
                    sumX += (xStart + col)
                    count++
                }
            }
        }

        if (count < 15) return null
        return (sumX / count).toFloat() / w
    }
}
