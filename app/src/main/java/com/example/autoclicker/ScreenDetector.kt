package com.example.autoclicker

import android.graphics.Bitmap

/**
 * Analiza un Bitmap de la pantalla capturada y ubica los elementos
 * del minijuego de pesca. Las regiones están en % del ancho/alto
 * para funcionar sin importar la resolución de captura.
 *
 * Calibrado con capturas 2400x1080 usando la caña que vas a usar.
 * Si cambias de caña y los colores del minijuego cambian, hay que
 * reajustar isBright()/isNearBlack() o las regiones.
 */
object ScreenDetector {

    private const val BAR_LEFT = 0.313f
    private const val BAR_RIGHT = 0.683f
    private const val BAR_TOP = 0.804f
    private const val BAR_BOTTOM = 0.868f
    private const val BAR_MID_Y = (BAR_TOP + BAR_BOTTOM) / 2f

    private const val LINE_TOP = 0.755f
    private const val LINE_BOTTOM = 0.801f

    data class ReelState(
        val barFillX: Float?,
        val lineX: Float?
    )

    fun analyzeReelBar(bitmap: Bitmap): ReelState {
        val w = bitmap.width
        val h = bitmap.height
        return ReelState(findFillEdge(bitmap, w, h), findLineX(bitmap, w, h))
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
