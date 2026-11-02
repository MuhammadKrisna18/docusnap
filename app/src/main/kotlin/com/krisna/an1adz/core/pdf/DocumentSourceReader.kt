package com.krisna.an1adz.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface DocumentSourceReader {
    suspend fun decodeBitmapFromUri(uri: Uri, maxWidth: Int = 2480, maxHeight: Int = 3508): Bitmap?
    suspend fun readTextFromUri(uri: Uri): String?
    fun getUriFileName(uri: Uri): String
    fun createDocumentFile(prefix: String = "scan"): File
    fun getRecentDocuments(): List<File>
    fun deleteDocument(file: File): Boolean
    fun renameDocument(file: File, newName: String): Result<File>
}

class DefaultDocumentSourceReader(
    private val context: Context
) : DocumentSourceReader {

    private val documentsDir: File
        get() = File(context.filesDir, "documents").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    private fun openInputStream(uri: Uri): InputStream? {
        return runCatching {
            if (uri.scheme == "file") {
                val filePath = uri.path ?: uri.schemeSpecificPart
                FileInputStream(File(filePath))
            } else {
                context.contentResolver.openInputStream(uri)
            }
        }.onFailure {
            Log.e("DocuSnap", "Failed to open stream for $uri", it)
        }.getOrNull()
    }

    override suspend fun decodeBitmapFromUri(uri: Uri, maxWidth: Int, maxHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val source = if (uri.scheme == "file") {
                            val file = File(uri.path ?: uri.schemeSpecificPart)
                            ImageDecoder.createSource(file)
                        } else {
                            ImageDecoder.createSource(context.contentResolver, uri)
                        }
                        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.isMutableRequired = true

                            var sampleSize = 1
                            while ((info.size.width / sampleSize) > maxWidth || (info.size.height / sampleSize) > maxHeight) {
                                sampleSize *= 2
                            }
                            if (sampleSize > 1) {
                                decoder.setTargetSampleSize(sampleSize)
                            }
                        }
                        return@withContext bitmap
                    } catch (e: Exception) {
                        Log.w("DocuSnap", "ImageDecoder failed, attempting byte array decoding", e)
                    }
                }

                val bytes = openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                var sampleSize = 1
                while ((options.outWidth / sampleSize) > maxWidth || (options.outHeight / sampleSize) > maxHeight) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            } catch (e: Exception) {
                Log.e("DocuSnap", "decodeBitmapFromUri completely failed for $uri", e)
                null
            }
        }

    override suspend fun readTextFromUri(uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
            }.getOrNull()
        }

    override fun getUriFileName(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = cursor.getString(index)
                    }
                }
            }
        }
        return name ?: uri.lastPathSegment ?: "document"
    }

    override fun createDocumentFile(prefix: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${prefix}_$timeStamp.pdf"
        return File(documentsDir, fileName)
    }

    override fun getRecentDocuments(): List<File> {
        return documentsDir.listFiles { f ->
            f.isFile && f.extension.equals("pdf", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    override fun deleteDocument(file: File): Boolean {
        return file.exists() && file.delete()
    }

    override fun renameDocument(file: File, newName: String): Result<File> {
        val sanitizedName = if (newName.endsWith(".pdf", ignoreCase = true)) {
            newName
        } else {
            "$newName.pdf"
        }
        val target = File(file.parentFile, sanitizedName)
        if (target.exists() && target.absolutePath != file.absolutePath) {
            return Result.failure(IllegalArgumentException("File dengan nama tersebut sudah ada"))
        }
        val success = file.renameTo(target)
        return if (success) {
            Result.success(target)
        } else {
            Result.failure(IllegalStateException("Gagal mengubah nama file"))
        }
    }
}
