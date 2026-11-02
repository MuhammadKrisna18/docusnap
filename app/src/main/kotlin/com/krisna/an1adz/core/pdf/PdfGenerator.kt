package com.krisna.an1adz.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

interface PdfGenerator {
    suspend fun generateFromBitmaps(bitmaps: List<Bitmap>, outputFile: File): Result<File>
    suspend fun generateFromText(
        text: String,
        documentTitle: String,
        isMonospace: Boolean,
        outputFile: File
    ): Result<File>
}

class DefaultPdfGenerator : PdfGenerator {

    companion object {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 40f
    }

    override suspend fun generateFromBitmaps(bitmaps: List<Bitmap>, outputFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val document = PdfDocument()
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                    isDither = true
                }

                bitmaps.forEachIndexed { index, bitmap ->
                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas

                    canvas.drawColor(Color.WHITE)

                    val maxTargetWidth = PAGE_WIDTH - (MARGIN * 2)
                    val maxTargetHeight = PAGE_HEIGHT - (MARGIN * 2)

                    val scale = minOf(
                        maxTargetWidth / bitmap.width.toFloat(),
                        maxTargetHeight / bitmap.height.toFloat()
                    )

                    val scaledWidth = bitmap.width * scale
                    val scaledHeight = bitmap.height * scale

                    val left = (PAGE_WIDTH - scaledWidth) / 2f
                    val top = (PAGE_HEIGHT - scaledHeight) / 2f

                    val destRect = Rect(
                        left.toInt(),
                        top.toInt(),
                        (left + scaledWidth).toInt(),
                        (top + scaledHeight).toInt()
                    )

                    canvas.drawBitmap(bitmap, null, destRect, paint)
                    document.finishPage(page)
                }

                FileOutputStream(outputFile).use { outputStream ->
                    document.writeTo(outputStream)
                }
                document.close()
                outputFile
            }
        }

    override suspend fun generateFromText(
        text: String,
        documentTitle: String,
        isMonospace: Boolean,
        outputFile: File
    ): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val document = PdfDocument()
                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = if (isMonospace) 10f else 11.5f
                    typeface = if (isMonospace) Typeface.MONOSPACE else Typeface.SANS_SERIF
                }

                val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                }

                val printableWidth = (PAGE_WIDTH - (MARGIN * 2)).toInt()
                val contentTopMargin = MARGIN + 20f
                val printableHeight = (PAGE_HEIGHT - (MARGIN * 2) - 30f).toInt()

                val staticLayout = StaticLayout.Builder
                    .obtain(text, 0, text.length, textPaint, printableWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.25f)
                    .setIncludePad(false)
                    .build()

                val totalLines = staticLayout.lineCount
                var currentLine = 0
                var pageNumber = 1

                while (currentLine < totalLines) {
                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    canvas.drawText(documentTitle, MARGIN, MARGIN, headerPaint)
                    canvas.drawText("Halaman $pageNumber", PAGE_WIDTH - MARGIN - 50f, MARGIN, headerPaint)

                    val startY = staticLayout.getLineTop(currentLine)
                    var endLine = currentLine
                    while (endLine < totalLines && (staticLayout.getLineBottom(endLine) - startY) <= printableHeight) {
                        endLine++
                    }

                    canvas.save()
                    canvas.translate(MARGIN, contentTopMargin - startY)
                    canvas.clipRect(0, startY, printableWidth, startY + printableHeight)
                    staticLayout.draw(canvas)
                    canvas.restore()

                    document.finishPage(page)
                    currentLine = maxOf(endLine, currentLine + 1)
                    pageNumber++
                }

                FileOutputStream(outputFile).use { outputStream ->
                    document.writeTo(outputStream)
                }
                document.close()
                outputFile
            }
        }
}
