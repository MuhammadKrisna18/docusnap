package com.krisna.an1adz.core.office.pptx

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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

class PptxPdfExporter {

    private val pageWidth = 960
    private val pageHeight = 540
    private val marginLeft = 50f
    private val marginRight = 50f
    private val marginTop = 45f
    private val marginBottom = 45f
    private val contentWidth = (pageWidth - marginLeft - marginRight).toInt()

    fun exportToPdf(presentation: PptxPresentation, destinationFile: File): Result<File> {
        return try {
            destinationFile.parentFile?.let {
                if (!it.exists()) it.mkdirs()
            }

            val pdfDocument = PdfDocument()

            val titlePaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.rgb(24, 30, 42)
            }

            val subtitlePaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.rgb(71, 85, 105)
            }

            val bodyPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 13f
                color = Color.rgb(30, 41, 59)
            }

            val footerPaint = TextPaint().apply {
                isAntiAlias = true
                textSize = 10f
                color = Color.GRAY
            }

            val accentPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(59, 130, 246)
                style = Paint.Style.FILL
            }

            val cardBorderPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(226, 232, 240)
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }

            presentation.slides.forEachIndexed { index, slide ->
                val slideNum = index + 1
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, slideNum).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawColor(Color.rgb(248, 250, 252))

                val cardRect = RectF(20f, 20f, pageWidth - 20f, pageHeight - 20f)
                val cardFillPaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(cardRect, 12f, 12f, cardFillPaint)
                canvas.drawRoundRect(cardRect, 12f, 12f, cardBorderPaint)

                canvas.drawRoundRect(RectF(marginLeft, marginTop - 15f, marginLeft + 6f, marginTop + 25f), 3f, 3f, accentPaint)

                var currentY = marginTop

                val slideTitle = slide.title?.takeIf { !it.startsWith("Slide ", ignoreCase = true) } ?: "Slide $slideNum"
                val titleLayout = createStaticLayout(slideTitle, titlePaint, contentWidth - 20, Layout.Alignment.ALIGN_NORMAL)
                canvas.save()
                canvas.translate(marginLeft + 16f, currentY)
                titleLayout.draw(canvas)
                canvas.restore()
                currentY += titleLayout.height + 25f

                for (element in slide.elements) {
                    if (currentY > pageHeight - marginBottom - 30f) break

                    when (element) {
                        is PptxElement.Title -> {
                            if (!element.text.trim().equals(slideTitle.trim(), ignoreCase = true)) {
                                val layout = createStaticLayout(element.text, titlePaint, contentWidth, Layout.Alignment.ALIGN_NORMAL)
                                if (currentY + layout.height <= pageHeight - marginBottom) {
                                    canvas.save()
                                    canvas.translate(marginLeft, currentY)
                                    layout.draw(canvas)
                                    canvas.restore()
                                    currentY += layout.height + 12f
                                }
                            }
                        }

                        is PptxElement.Subtitle -> {
                            val layout = createStaticLayout(element.text, subtitlePaint, contentWidth, Layout.Alignment.ALIGN_NORMAL)
                            if (currentY + layout.height <= pageHeight - marginBottom) {
                                canvas.save()
                                canvas.translate(marginLeft, currentY)
                                layout.draw(canvas)
                                canvas.restore()
                                currentY += layout.height + 12f
                            }
                        }

                        is PptxElement.Paragraph -> {
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
                                        val c = Color.parseColor("#${run.colorHex.removePrefix("#")}")
                                        spannable.setSpan(ForegroundColorSpan(c), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    } catch (_: Exception) {
                                    }
                                }
                            }

                            val layout = createStaticLayout(spannable, bodyPaint, contentWidth, Layout.Alignment.ALIGN_NORMAL)
                            if (currentY + layout.height <= pageHeight - marginBottom) {
                                canvas.save()
                                canvas.translate(marginLeft, currentY)
                                layout.draw(canvas)
                                canvas.restore()
                                currentY += layout.height + 8f
                            }
                        }

                        is PptxElement.Image -> {
                            try {
                                val bitmap = BitmapFactory.decodeByteArray(element.bytes, 0, element.bytes.size)
                                if (bitmap != null) {
                                    val maxHeight = (pageHeight - currentY - marginBottom).coerceAtMost(180f)
                                    if (maxHeight > 50f) {
                                        val scale = (maxHeight / bitmap.height.toFloat()).coerceAtMost(contentWidth.toFloat() / bitmap.width.toFloat())
                                        val destWidth = (bitmap.width * scale).toInt()
                                        val destHeight = (bitmap.height * scale).toInt()
                                        val left = marginLeft + ((contentWidth - destWidth) / 2f)
                                        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                                        val destRect = RectF(left, currentY, left + destWidth, currentY + destHeight)
                                        canvas.drawBitmap(bitmap, srcRect, destRect, null)
                                        currentY += destHeight + 12f
                                    }
                                    bitmap.recycle()
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }

                val footerText = "DocuSnap  |  ${presentation.title}"
                canvas.drawText(footerText, marginLeft, pageHeight - 32f, footerPaint)

                val slideNumberBadge = "Slide $slideNum / ${presentation.totalSlides}"
                val badgeWidth = footerPaint.measureText(slideNumberBadge)
                canvas.drawText(slideNumberBadge, pageWidth - marginRight - badgeWidth, pageHeight - 32f, footerPaint)

                pdfDocument.finishPage(page)
            }

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
