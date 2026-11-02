package com.krisna.an1adz.core.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

interface ScanFilterProcessor {
    fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap
}

class DefaultScanFilterProcessor : ScanFilterProcessor {

    override fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap {
        if (filterType == FilterType.ORIGINAL) {
            return bitmap
        }

        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colorMatrix = ColorMatrix()

        when (filterType) {
            FilterType.SCAN_BLACK_WHITE -> {
                colorMatrix.setSaturation(0f)
                val contrast = 1.8f
                val brightness = -30f
                val scale = contrast
                val translate = (1f - contrast) * 128f + brightness
                val contrastMatrix = ColorMatrix(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                colorMatrix.postConcat(contrastMatrix)
            }
            FilterType.SCAN_ENHANCED -> {
                val contrast = 1.35f
                val brightness = 15f
                val scale = contrast
                val translate = (1f - contrast) * 128f + brightness
                val contrastMatrix = ColorMatrix(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                colorMatrix.postConcat(contrastMatrix)
            }
            FilterType.ORIGINAL -> Unit
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }
}

