package com.krisna.an1adz.data

import android.content.Context
import android.net.Uri
import com.krisna.an1adz.core.office.docx.DocxDocument
import com.krisna.an1adz.core.office.docx.DocxParser
import com.krisna.an1adz.core.office.docx.DocxPdfExporter
import com.krisna.an1adz.core.office.pptx.PptxParser
import com.krisna.an1adz.core.office.pptx.PptxPdfExporter
import com.krisna.an1adz.core.office.pptx.PptxPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface OfficeRepository {
    suspend fun parseDocx(file: File): Result<DocxDocument>
    suspend fun parsePptx(file: File): Result<PptxPresentation>
    suspend fun importOfficeDocument(uri: Uri, defaultExtension: String = "docx"): Result<File>
    suspend fun exportDocxToPdf(document: DocxDocument, outputTitle: String): Result<File>
    suspend fun exportPptxToPdf(presentation: PptxPresentation, outputTitle: String): Result<File>
}

class DefaultOfficeRepository(
    private val context: Context,
    private val docxParser: DocxParser = DocxParser(),
    private val docxPdfExporter: DocxPdfExporter = DocxPdfExporter(),
    private val pptxParser: PptxParser = PptxParser(),
    private val pptxPdfExporter: PptxPdfExporter = PptxPdfExporter()
) : OfficeRepository {

    override suspend fun parseDocx(file: File): Result<DocxDocument> = withContext(Dispatchers.IO) {
        try {
            val parsed = docxParser.parse(file)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun parsePptx(file: File): Result<PptxPresentation> = withContext(Dispatchers.IO) {
        try {
            val parsed = pptxParser.parse(file)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importOfficeDocument(uri: Uri, defaultExtension: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val officeDir = File(context.filesDir, "office_docs").apply {
                if (!exists()) mkdirs()
            }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val targetFile = File(officeDir, "DOC_$timeStamp.$defaultExtension")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Gagal membuka file dokumen"))

            if (targetFile.exists() && targetFile.length() > 0) {
                Result.success(targetFile)
            } else {
                Result.failure(IllegalStateException("File dokumen kosong atau tidak ditemukan"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportDocxToPdf(document: DocxDocument, outputTitle: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pdfDir = File(context.filesDir, "documents").apply {
                if (!exists()) mkdirs()
            }
            val safeName = outputTitle.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val targetFile = File(pdfDir, "DOC_${safeName}_$timeStamp.pdf")

            docxPdfExporter.exportToPdf(document, targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportPptxToPdf(presentation: PptxPresentation, outputTitle: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pdfDir = File(context.filesDir, "documents").apply {
                if (!exists()) mkdirs()
            }
            val safeName = outputTitle.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val targetFile = File(pdfDir, "PPT_${safeName}_$timeStamp.pdf")

            pptxPdfExporter.exportToPdf(presentation, targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
