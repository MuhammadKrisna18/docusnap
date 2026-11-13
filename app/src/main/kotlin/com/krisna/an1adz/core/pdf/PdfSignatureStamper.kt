package com.krisna.an1adz.core.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.krisna.an1adz.core.signature.SignatureAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

interface PdfSignatureStamper {
    suspend fun stampSignature(
        pdfFile: File,
        signatureFile: File,
        pageIndex: Int,
        alignment: SignatureAlignment,
        targetFile: File
    ): Result<File>
}

class DefaultPdfSignatureStamper : PdfSignatureStamper {

    override suspend fun stampSignature(
        pdfFile: File,
        signatureFile: File,
        pageIndex: Int,
        alignment: SignatureAlignment,
        targetFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val sigBitmap = BitmapFactory.decodeFile(signatureFile.absolutePath)
                ?: throw IllegalArgumentException("Gagal membaca file tanda tangan")

            val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)
            val pdfDocument = PdfDocument()

            val validTargetPage = pageIndex.coerceIn(0, max(0, pdfRenderer.pageCount - 1))

            for (i in 0 until pdfRenderer.pageCount) {
                val page = pdfRenderer.openPage(i)
                val pageWidth = page.width
                val pageHeight = page.height

                val pageBitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(pageBitmap)
                canvas.drawColor(Color.WHITE)
                page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                if (i == validTargetPage) {
                    val maxSigW = pageWidth * 0.35f
                    val maxSigH = pageHeight * 0.15f
                    val scale = min(maxSigW / sigBitmap.width, maxSigH / sigBitmap.height)
                    val sigW = (sigBitmap.width * scale).toInt()
                    val sigH = (sigBitmap.height * scale).toInt()

                    val scaledSig = Bitmap.createScaledBitmap(sigBitmap, max(1, sigW), max(1, sigH), true)

                    val margin = 32f
                    val x = when (alignment) {
                        SignatureAlignment.BOTTOM_LEFT -> margin
                        SignatureAlignment.BOTTOM_CENTER -> (pageWidth - sigW) / 2f
                        SignatureAlignment.BOTTOM_RIGHT -> pageWidth - sigW - margin
                    }
                    val y = pageHeight - sigH - margin

                    canvas.drawBitmap(scaledSig, x, y, null)
                    if (scaledSig != sigBitmap) {
                        scaledSig.recycle()
                    }
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create()
                val newPage = pdfDocument.startPage(pageInfo)
                newPage.canvas.drawBitmap(pageBitmap, 0f, 0f, null)
                pdfDocument.finishPage(newPage)

                pageBitmap.recycle()
                page.close()
            }

            sigBitmap.recycle()
            pdfRenderer.close()
            fileDescriptor.close()

            if (targetFile.parentFile?.exists() == false) {
                targetFile.parentFile?.mkdirs()
            }

            FileOutputStream(targetFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            targetFile
        }
    }
}

