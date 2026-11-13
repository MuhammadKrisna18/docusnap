package com.krisna.an1adz.core.signature

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

interface SignatureExporter {
    fun renderToBitmap(strokes: List<SignatureStroke>, canvasWidth: Int, canvasHeight: Int): Bitmap?
    suspend fun saveSignaturePng(bitmap: Bitmap, targetFile: File): Result<File>
    suspend fun saveSignatureToUri(sourceFile: File, targetUri: Uri): Result<Unit>
    fun shareSignature(file: File)
}

class DefaultSignatureExporter(
    private val context: Context
) : SignatureExporter {

    override fun renderToBitmap(
        strokes: List<SignatureStroke>,
        canvasWidth: Int,
        canvasHeight: Int
    ): Bitmap? {
        if (strokes.isEmpty() || canvasWidth <= 0 || canvasHeight <= 0) return null

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        strokes.forEach { stroke ->
            val halfWidth = stroke.strokeWidth / 2f
            stroke.points.forEach { pt ->
                if (pt.x - halfWidth < minX) minX = pt.x - halfWidth
                if (pt.y - halfWidth < minY) minY = pt.y - halfWidth
                if (pt.x + halfWidth > maxX) maxX = pt.x + halfWidth
                if (pt.y + halfWidth > maxY) maxY = pt.y + halfWidth
            }
        }

        if (minX >= maxX || minY >= maxY) return null

        val padding = 24f
        val cropLeft = max(0, (minX - padding).toInt())
        val cropTop = max(0, (minY - padding).toInt())
        val cropRight = min(canvasWidth, (maxX + padding).toInt())
        val cropBottom = min(canvasHeight, (maxY + padding).toInt())

        val cropWidth = max(1, cropRight - cropLeft)
        val cropHeight = max(1, cropBottom - cropTop)

        val bitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        strokes.forEach { stroke ->
            paint.color = stroke.color.toArgb()
            paint.strokeWidth = stroke.strokeWidth
            val path = Path()
            if (stroke.points.isNotEmpty()) {
                path.moveTo(stroke.points[0].x - cropLeft, stroke.points[0].y - cropTop)
                for (i in 1 until stroke.points.size) {
                    val prev = stroke.points[i - 1]
                    val curr = stroke.points[i]
                    val midX = (prev.x + curr.x) / 2f - cropLeft
                    val midY = (prev.y + curr.y) / 2f - cropTop
                    path.quadTo(prev.x - cropLeft, prev.y - cropTop, midX, midY)
                }
                val last = stroke.points.last()
                path.lineTo(last.x - cropLeft, last.y - cropTop)
            }
            canvas.drawPath(path, paint)
        }

        return bitmap
    }

    override suspend fun saveSignaturePng(bitmap: Bitmap, targetFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (targetFile.parentFile?.exists() == false) {
                    targetFile.parentFile?.mkdirs()
                }
                FileOutputStream(targetFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                targetFile
            }
        }

    override suspend fun saveSignatureToUri(sourceFile: File, targetUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IllegalStateException("Cannot open destination stream")
                Unit
            }
        }

    override fun shareSignature(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(sendIntent, "Bagikan Tanda Tangan PNG").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
    }
}

