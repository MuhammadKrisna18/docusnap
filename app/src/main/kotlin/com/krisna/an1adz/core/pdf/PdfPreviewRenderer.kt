package com.krisna.an1adz.core.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface PdfPreviewRenderer {
    suspend fun renderPages(pdfFile: File): Result<List<Bitmap>>
}

class DefaultPdfPreviewRenderer : PdfPreviewRenderer {

    override suspend fun renderPages(pdfFile: File): Result<List<Bitmap>> = withContext(Dispatchers.IO) {
        runCatching {
            val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)
            val bitmaps = mutableListOf<Bitmap>()

            for (i in 0 until pdfRenderer.pageCount) {
                val page = pdfRenderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }

            pdfRenderer.close()
            fileDescriptor.close()
            bitmaps
        }
    }
}

