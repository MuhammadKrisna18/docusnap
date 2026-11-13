package com.krisna.an1adz.core.pdf

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

interface ScanFilterProcessor {
    fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap
}

class DefaultScanFilterProcessor : ScanFilterProcessor {

    private val bwLut = IntArray(256) { v ->
        val low = 85f
        val high = 200f
        when {
            v <= low -> 0
            v >= high -> 255
            else -> {
                val t = (v - low) / (high - low)
                val smooth = t * t * (3f - 2f * t)
                (smooth * 255f).toInt().coerceIn(0, 255)
            }
        }
    }

    override fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap {
        return when (filterType) {
            FilterType.ORIGINAL -> bitmap
            FilterType.SCAN_BLACK_WHITE -> processBlackAndWhite(bitmap)
            FilterType.SCAN_ENHANCED -> processColorEnhanced(bitmap)
        }
    }

    private fun processBlackAndWhite(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height

        val bgMapFull = createIlluminationMap(src, width, height)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val stripHeight = min(256, height)
        val srcPixels = IntArray(width * stripHeight)
        val bgPixels = IntArray(width * stripHeight)
        val outPixels = IntArray(width * stripHeight)

        var y = 0
        while (y < height) {
            val currentStripH = min(stripHeight, height - y)
            val count = width * currentStripH

            src.getPixels(srcPixels, 0, width, 0, y, width, currentStripH)
            bgMapFull.getPixels(bgPixels, 0, width, 0, y, width, currentStripH)

            for (i in 0 until count) {
                val p = srcPixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = (77 * r + 150 * g + 29 * b) shr 8

                val bgVal = bgPixels[i] and 0xFF
                val bgLum = if (bgVal < 30) 30 else bgVal

                val norm = min(255, (lum * 255) / bgLum)
                val finalGray = bwLut[norm]

                outPixels[i] = (0xFF shl 24) or (finalGray shl 16) or (finalGray shl 8) or finalGray
            }

            output.setPixels(outPixels, 0, width, 0, y, width, currentStripH)
            y += currentStripH
        }

        bgMapFull.recycle()
        return output
    }

    private fun processColorEnhanced(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height

        val bgMapFull = createIlluminationMap(src, width, height)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val stripHeight = min(256, height)
        val srcPixels = IntArray(width * stripHeight)
        val bgPixels = IntArray(width * stripHeight)
        val outPixels = IntArray(width * stripHeight)

        var y = 0
        while (y < height) {
            val currentStripH = min(stripHeight, height - y)
            val count = width * currentStripH

            src.getPixels(srcPixels, 0, width, 0, y, width, currentStripH)
            bgMapFull.getPixels(bgPixels, 0, width, 0, y, width, currentStripH)

            for (i in 0 until count) {
                val p = srcPixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val bgVal = bgPixels[i] and 0xFF
                val bgLum = if (bgVal < 30) 30 else bgVal

                val normR = min(255, (r * 255) / bgLum)
                val normG = min(255, (g * 255) / bgLum)
                val normB = min(255, (b * 255) / bgLum)

                val outR = enhanceChannel(normR)
                val outG = enhanceChannel(normG)
                val outB = enhanceChannel(normB)

                outPixels[i] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
            }

            output.setPixels(outPixels, 0, width, 0, y, width, currentStripH)
            y += currentStripH
        }

        bgMapFull.recycle()
        return output
    }

    private fun enhanceChannel(v: Int): Int {
        val low = 60f
        val high = 220f
        return when {
            v <= low -> (v * 0.7f).toInt().coerceIn(0, 255)
            v >= high -> 255
            else -> {
                val t = (v - low) / (high - low)
                val s = t * t * (3f - 2f * t)
                (s * 255f).toInt().coerceIn(0, 255)
            }
        }
    }

    private fun createIlluminationMap(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val thumbW = 160
        val thumbH = max(1, (160f * targetH / targetW).toInt())
        val thumb = Bitmap.createScaledBitmap(src, thumbW, thumbH, true)

        val thumbPixels = IntArray(thumbW * thumbH)
        thumb.getPixels(thumbPixels, 0, thumbW, 0, 0, thumbW, thumbH)
        thumb.recycle()

        val lum = IntArray(thumbW * thumbH)
        for (i in thumbPixels.indices) {
            val p = thumbPixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            lum[i] = (77 * r + 150 * g + 29 * b) shr 8
        }

        val maxH = IntArray(thumbW * thumbH)
        val radius = 5
        for (row in 0 until thumbH) {
            val offset = row * thumbW
            for (col in 0 until thumbW) {
                var maxVal = 0
                val startX = max(0, col - radius)
                val endX = min(thumbW - 1, col + radius)
                for (kx in startX..endX) {
                    val v = lum[offset + kx]
                    if (v > maxVal) maxVal = v
                }
                maxH[offset + col] = maxVal
            }
        }

        val maxHV = IntArray(thumbW * thumbH)
        for (col in 0 until thumbW) {
            for (row in 0 until thumbH) {
                var maxVal = 0
                val startY = max(0, row - radius)
                val endY = min(thumbH - 1, row + radius)
                for (ky in startY..endY) {
                    val v = maxH[ky * thumbW + col]
                    if (v > maxVal) maxVal = v
                }
                maxHV[row * thumbW + col] = maxVal
            }
        }

        val blurRadius = 4
        val blurH = IntArray(thumbW * thumbH)
        for (row in 0 until thumbH) {
            val offset = row * thumbW
            for (col in 0 until thumbW) {
                var sum = 0
                val startX = max(0, col - blurRadius)
                val endX = min(thumbW - 1, col + blurRadius)
                val count = endX - startX + 1
                for (kx in startX..endX) {
                    sum += maxHV[offset + kx]
                }
                blurH[offset + col] = sum / count
            }
        }

        val bgSmallPixels = IntArray(thumbW * thumbH)
        for (col in 0 until thumbW) {
            for (row in 0 until thumbH) {
                var sum = 0
                val startY = max(0, row - blurRadius)
                val endY = min(thumbH - 1, row + blurRadius)
                val count = endY - startY + 1
                for (ky in startY..endY) {
                    sum += blurH[ky * thumbW + col]
                }
                val gray = (sum / count).coerceIn(30, 255)
                bgSmallPixels[row * thumbW + col] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
        }

        val bgSmallBitmap = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
        bgSmallBitmap.setPixels(bgSmallPixels, 0, thumbW, 0, 0, thumbW, thumbH)

        val bgFullBitmap = Bitmap.createScaledBitmap(bgSmallBitmap, targetW, targetH, true)
        bgSmallBitmap.recycle()

        return bgFullBitmap
    }
}
