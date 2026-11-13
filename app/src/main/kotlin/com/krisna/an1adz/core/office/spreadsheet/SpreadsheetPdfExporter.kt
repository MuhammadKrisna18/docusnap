package com.krisna.an1adz.core.office.spreadsheet

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

class SpreadsheetPdfExporter {

    private val pageWidth = 842
    private val pageHeight = 595
    private val marginLeft = 35f
    private val marginRight = 35f
    private val marginTop = 40f
    private val marginBottom = 40f
    private val contentWidth = pageWidth - marginLeft - marginRight

    fun exportToPdf(
        document: SpreadsheetDocument,
        sheetIndex: Int,
        destinationFile: File
    ): Result<File> {
        return try {
            destinationFile.parentFile?.let {
                if (!it.exists()) it.mkdirs()
            }

            val sheet = document.sheets.getOrNull(sheetIndex) ?: document.sheets.firstOrNull()
            if (sheet == null) {
                return Result.failure(IllegalStateException("Lembar kerja tidak ditemukan"))
            }

            val pdfDocument = PdfDocument()

            val titlePaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.rgb(24, 30, 42)
            }

            val subtitlePaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                color = Color.rgb(71, 85, 105)
            }

            val headerTextPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.rgb(30, 41, 59)
            }

            val cellTextPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 8.5f
                color = Color.rgb(30, 41, 59)
            }

            val footerPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 8f
                color = Color.GRAY
            }

            val headerBgPaint = Paint().apply {
                color = Color.rgb(241, 245, 249)
                style = Paint.Style.FILL
            }

            val borderPaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }

            val altRowBgPaint = Paint().apply {
                color = Color.rgb(248, 250, 252)
                style = Paint.Style.FILL
            }

            val numCols = sheet.maxColumns.coerceIn(1, 12)
            val rowHeaderWidth = 28f
            val cellWidth = (contentWidth - rowHeaderWidth) / numCols.toFloat()
            val rowHeight = 20f

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas = currentPage.canvas
            var currentY = marginTop

            fun drawPageHeaders() {
                val title = "${document.title} - ${sheet.name}"
                canvas.drawText(title, marginLeft, currentY, titlePaint)
                currentY += 16f
                canvas.drawText("Total Baris: ${sheet.rowCount}  |  Total Kolom: ${sheet.maxColumns}", marginLeft, currentY, subtitlePaint)
                currentY += 16f

                val headerRect = RectF(marginLeft, currentY, marginLeft + contentWidth, currentY + rowHeight)
                canvas.drawRect(headerRect, headerBgPaint)
                canvas.drawRect(headerRect, borderPaint)

                canvas.drawRect(marginLeft, currentY, marginLeft + rowHeaderWidth, currentY + rowHeight, borderPaint)
                canvas.drawText("#", marginLeft + 10f, currentY + 14f, headerTextPaint)

                for (c in 0 until numCols) {
                    val colX = marginLeft + rowHeaderWidth + (c * cellWidth)
                    canvas.drawRect(colX, currentY, colX + cellWidth, currentY + rowHeight, borderPaint)
                    val letter = getColumnLetter(c)
                    canvas.drawText(letter, colX + (cellWidth / 2f) - 4f, currentY + 14f, headerTextPaint)
                }
                currentY += rowHeight
            }

            fun checkPageBreak() {
                if (currentY + rowHeight > pageHeight - marginBottom) {
                    canvas.drawText(
                        "- Halaman $pageNumber -",
                        (pageWidth / 2f) - 30f,
                        pageHeight - 20f,
                        footerPaint
                    )
                    pdfDocument.finishPage(currentPage)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    currentY = marginTop
                    drawPageHeaders()
                }
            }

            drawPageHeaders()

            for (row in sheet.rows) {
                checkPageBreak()

                val isAlt = row.rowIndex % 2 == 0
                val rowRect = RectF(marginLeft, currentY, marginLeft + contentWidth, currentY + rowHeight)
                if (isAlt) {
                    canvas.drawRect(rowRect, altRowBgPaint)
                }

                canvas.drawRect(marginLeft, currentY, marginLeft + rowHeaderWidth, currentY + rowHeight, borderPaint)
                val rowNumStr = "${row.rowIndex}"
                canvas.drawText(rowNumStr, marginLeft + 6f, currentY + 14f, subtitlePaint)

                val cellMap = row.cells.associateBy { it.colIndex }
                for (c in 0 until numCols) {
                    val colX = marginLeft + rowHeaderWidth + (c * cellWidth)
                    canvas.drawRect(colX, currentY, colX + cellWidth, currentY + rowHeight, borderPaint)

                    val cell = cellMap[c]
                    if (cell != null && cell.value.isNotEmpty()) {
                        val paint = if (cell.isHeader) headerTextPaint else cellTextPaint
                        val truncatedText = truncateText(cell.value, paint, cellWidth - 8f)
                        val textX = if (cell.isNumeric) {
                            val measured = paint.measureText(truncatedText)
                            colX + cellWidth - measured - 5f
                        } else {
                            colX + 5f
                        }
                        canvas.drawText(truncatedText, textX, currentY + 14f, paint)
                    }
                }
                currentY += rowHeight
            }

            canvas.drawText(
                "- Halaman $pageNumber -",
                (pageWidth / 2f) - 30f,
                pageHeight - 20f,
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

    private fun truncateText(text: String, paint: TextPaint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated...") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return if (truncated.isEmpty()) "" else "$truncated..."
    }

    private fun getColumnLetter(colIndex: Int): String {
        var n = colIndex
        var result = ""
        while (n >= 0) {
            result = ('A'.code + (n % 26)).toChar() + result
            n = (n / 26) - 1
        }
        return result
    }
}
