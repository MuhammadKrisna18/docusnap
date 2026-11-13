package com.krisna.an1adz.core.office.docx

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import java.io.File
import java.io.FileOutputStream

class DocxPdfExporter {

    private val pageWidth = 595
    private val pageHeight = 842
    private val marginLeft = 40f
    private val marginRight = 40f
    private val marginTop = 50f
    private val marginBottom = 50f
    private val contentWidth = (pageWidth - marginLeft - marginRight).toInt()

    fun exportToPdf(document: DocxDocument, destinationFile: File): Result<File> {
        return try {
            destinationFile.parentFile?.let {
                if (!it.exists()) it.mkdirs()
            }

            val pdfDocument = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas = currentPage.canvas
            var currentY = marginTop

            val titlePaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.BLACK
            }

            val bodyPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 12f
                color = Color.rgb(33, 33, 33)
            }

            val heading1Paint = TextPaint().apply {
                isAntiAlias = true
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.rgb(20, 60, 120)
            }

            val heading2Paint = TextPaint().apply {
                isAntiAlias = true
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.rgb(40, 40, 40)
            }

            val footerPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 9f
                color = Color.GRAY
            }

            val tableBorderPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(200, 200, 200)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            fun checkPageBreak(requiredHeight: Float) {
                if (currentY + requiredHeight > pageHeight - marginBottom) {
                    canvas.drawText(
                        "- Halaman $pageNumber -",
                        (pageWidth / 2f) - 30f,
                        pageHeight - 25f,
                        footerPaint
                    )
                    pdfDocument.finishPage(currentPage)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    currentY = marginTop
                }
            }

            val titleLayout = createStaticLayout(document.title, titlePaint, contentWidth, Layout.Alignment.ALIGN_NORMAL)
            checkPageBreak(titleLayout.height.toFloat() + 20f)
            canvas.save()
            canvas.translate(marginLeft, currentY)
            titleLayout.draw(canvas)
            canvas.restore()
            currentY += titleLayout.height + 20f

            for (element in document.elements) {
                when (element) {
                    is DocxElement.Heading -> {
                        val paint = if (element.level <= 1) heading1Paint else heading2Paint
                        val layout = createStaticLayout(element.text, paint, contentWidth, Layout.Alignment.ALIGN_NORMAL)
                        checkPageBreak(layout.height.toFloat() + 14f)
                        canvas.save()
                        canvas.translate(marginLeft, currentY)
                        layout.draw(canvas)
                        canvas.restore()
                        currentY += layout.height + 10f
                    }

                    is DocxElement.Paragraph -> {
                        val spannable = SpannableStringBuilder()
                        if (element.isBullet) {
                            spannable.append("•  ")
                        }
                        for (run in element.runs) {
                            val start = spannable.length
                            spannable.append(run.text)
                            val end = spannable.length

                            if (run.isBold && run.isItalic) {
                                spannable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            } else if (run.isBold) {
                                spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            } else if (run.isItalic) {
                                spannable.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }

                            if (run.isUnderline) {
                                spannable.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                            if (run.isStrike) {
                                spannable.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                            if (run.colorHex != null) {
                                try {
                                    val parsedColor = Color.parseColor("#${run.colorHex.removePrefix("#")}")
                                    spannable.setSpan(ForegroundColorSpan(parsedColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                } catch (_: Exception) {
                                }
                            }
                        }

                        val alignment = when (element.alignment) {
                            DocxAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                            DocxAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                            else -> Layout.Alignment.ALIGN_NORMAL
                        }

                        val layout = createStaticLayout(spannable, bodyPaint, contentWidth, alignment)
                        checkPageBreak(layout.height.toFloat() + 8f)
                        canvas.save()
                        canvas.translate(marginLeft, currentY)
                        layout.draw(canvas)
                        canvas.restore()
                        currentY += layout.height + 8f
                    }

                    is DocxElement.Table -> {
                        val maxCols = element.rows.maxOfOrNull { it.cells.size } ?: 1
                        val colWidth = contentWidth / maxCols.toFloat()
                        val cellPadding = 6f

                        for (row in element.rows) {
                            val cellLayouts = row.cells.map { cell ->
                                createStaticLayout(cell.text, bodyPaint, (colWidth - (cellPadding * 2)).toInt().coerceAtLeast(10), Layout.Alignment.ALIGN_NORMAL)
                            }
                            val rowHeight = (cellLayouts.maxOfOrNull { it.height } ?: 20) + (cellPadding * 2)

                            checkPageBreak(rowHeight)

                            var cellX = marginLeft
                            cellLayouts.forEach { layout ->
                                canvas.drawRect(cellX, currentY, cellX + colWidth, currentY + rowHeight, tableBorderPaint)
                                canvas.save()
                                canvas.translate(cellX + cellPadding, currentY + cellPadding)
                                layout.draw(canvas)
                                canvas.restore()
                                cellX += colWidth
                            }
                            currentY += rowHeight
                        }
                        currentY += 12f
                    }

                    is DocxElement.Image -> {
                        try {
                            val bitmap = BitmapFactory.decodeByteArray(element.bytes, 0, element.bytes.size)
                            if (bitmap != null) {
                                val scale = (contentWidth.toFloat() / bitmap.width.toFloat()).coerceAtMost(1f)
                                val destWidth = (bitmap.width * scale).toInt()
                                val destHeight = (bitmap.height * scale).toInt().coerceAtMost(400)

                                checkPageBreak(destHeight.toFloat() + 12f)
                                val leftOffset = marginLeft + ((contentWidth - destWidth) / 2f)
                                val srcRect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                                val destRect = android.graphics.RectF(leftOffset, currentY, leftOffset + destWidth, currentY + destHeight)
                                canvas.drawBitmap(bitmap, srcRect, destRect, null)
                                currentY += destHeight + 12f
                                bitmap.recycle()
                            }
                        } catch (_: Exception) {
                        }
                    }

                    is DocxElement.Divider -> {
                        checkPageBreak(12f)
                        canvas.drawLine(marginLeft, currentY, marginLeft + contentWidth, currentY, tableBorderPaint)
                        currentY += 12f
                    }
                }
            }

            canvas.drawText(
                "- Halaman $pageNumber -",
                (pageWidth / 2f) - 30f,
                pageHeight - 25f,
                footerPaint
            )
            pdfDocument.finishPage(currentPage)

            FileOutputStream(destinationFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment
    ): StaticLayout {
        val safeWidth = width.coerceAtLeast(10)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(true)
            .build()
    }
}
