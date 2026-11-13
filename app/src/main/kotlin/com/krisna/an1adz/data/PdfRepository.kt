package com.krisna.an1adz.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.krisna.an1adz.core.pdf.DefaultDocumentSourceReader
import com.krisna.an1adz.core.pdf.DefaultPdfGenerator
import com.krisna.an1adz.core.pdf.DefaultPdfPreviewRenderer
import com.krisna.an1adz.core.pdf.DefaultPdfSignatureStamper
import com.krisna.an1adz.core.pdf.DefaultScanFilterProcessor
import com.krisna.an1adz.core.pdf.DocumentSourceReader
import com.krisna.an1adz.core.pdf.FilterType
import com.krisna.an1adz.core.pdf.PdfGenerator
import com.krisna.an1adz.core.pdf.PdfPreviewRenderer
import com.krisna.an1adz.core.pdf.PdfSignatureStamper
import com.krisna.an1adz.core.pdf.ScanFilterProcessor
import com.krisna.an1adz.core.signature.SignatureAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

interface PdfRepository {
    val recentDocuments: Flow<List<File>>
    suspend fun refreshRecentDocuments()
    suspend fun convertImagesToPdf(uris: List<Uri>, filterType: FilterType): Result<File>
    suspend fun convertDocumentsToPdf(uris: List<Uri>): Result<File>
    suspend fun copyScannedPdf(sourceUri: Uri): Result<File>
    suspend fun importExternalPdf(sourceUri: Uri): Result<File>
    suspend fun stampSignatureToPdf(
        pdfFile: File,
        signatureFile: File,
        pageIndex: Int,
        alignment: SignatureAlignment
    ): Result<File>
    suspend fun renderPdfPages(pdfFile: File): Result<List<Bitmap>>
    suspend fun deleteDocument(file: File): Boolean
    suspend fun renameDocument(file: File, newName: String): Result<File>
}

class DefaultPdfRepository(
    private val context: Context,
    private val documentSourceReader: DocumentSourceReader = DefaultDocumentSourceReader(context),
    private val scanFilterProcessor: ScanFilterProcessor = DefaultScanFilterProcessor(),
    private val pdfGenerator: PdfGenerator = DefaultPdfGenerator(),
    private val pdfPreviewRenderer: PdfPreviewRenderer = DefaultPdfPreviewRenderer(),
    private val pdfSignatureStamper: PdfSignatureStamper = DefaultPdfSignatureStamper()
) : PdfRepository {

    private val _recentDocuments = MutableStateFlow<List<File>>(emptyList())
    override val recentDocuments: Flow<List<File>> = _recentDocuments.asStateFlow()

    init {
        _recentDocuments.value = documentSourceReader.getRecentDocuments()
    }

    override suspend fun refreshRecentDocuments() {
        withContext(Dispatchers.IO) {
            _recentDocuments.value = documentSourceReader.getRecentDocuments()
        }
    }

    override suspend fun convertImagesToPdf(uris: List<Uri>, filterType: FilterType): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmaps = uris.mapNotNull { uri ->
                    val rawBitmap = documentSourceReader.decodeBitmapFromUri(uri) ?: return@mapNotNull null
                    scanFilterProcessor.applyFilter(rawBitmap, filterType)
                }
                if (bitmaps.isEmpty()) {
                    throw IllegalArgumentException("Tidak ada gambar yang valid untuk didekode")
                }
                val targetFile = documentSourceReader.createDocumentFile("foto_scan")
                val generated = pdfGenerator.generateFromBitmaps(bitmaps, targetFile).getOrThrow()
                refreshRecentDocuments()
                generated
            }
        }

    override suspend fun convertDocumentsToPdf(uris: List<Uri>): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (uris.isEmpty()) {
                    throw IllegalArgumentException("Tidak ada file yang dipilih")
                }

                val stringBuilder = StringBuilder()
                var isMonospace = false
                val firstFileName = documentSourceReader.getUriFileName(uris.first())

                uris.forEachIndexed { index, uri ->
                    val fileName = documentSourceReader.getUriFileName(uri)
                    val lower = fileName.lowercase()
                    if (lower.endsWith(".csv") || lower.endsWith(".json") || lower.endsWith(".log") || lower.endsWith(".xml")) {
                        isMonospace = true
                    }

                    val content = documentSourceReader.readTextFromUri(uri)
                    if (!content.isNullOrBlank()) {
                        if (index > 0) {
                            stringBuilder.append("\n\n--- Halaman Dokumen Berikutnya: $fileName ---\n\n")
                        }
                        stringBuilder.append(content)
                    }
                }

                val fullText = stringBuilder.toString().trim()
                if (fullText.isEmpty()) {
                    throw IllegalArgumentException("File dokumen kosong atau tidak terbaca")
                }

                val prefix = if (uris.size > 1) "gabungan_dokumen" else firstFileName.substringBeforeLast(".")
                val targetFile = documentSourceReader.createDocumentFile(prefix.take(15))
                val title = if (uris.size > 1) "${uris.size} Dokumen Tergabung" else firstFileName
                val generated = pdfGenerator.generateFromText(fullText, title, isMonospace, targetFile).getOrThrow()
                refreshRecentDocuments()
                generated
            }
        }

    override suspend fun copyScannedPdf(sourceUri: Uri): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targetFile = documentSourceReader.createDocumentFile("kamera_scan")
                val stream = if (sourceUri.scheme == "file") {
                    FileInputStream(File(sourceUri.path ?: sourceUri.schemeSpecificPart))
                } else {
                    context.contentResolver.openInputStream(sourceUri)
                }
                stream?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalArgumentException("Gagal membaca hasil scan kamera")
                refreshRecentDocuments()
                targetFile
            }
        }

    override suspend fun importExternalPdf(sourceUri: Uri): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val originalName = documentSourceReader.getUriFileName(sourceUri)
                val cleanPrefix = originalName.substringBeforeLast(".").ifBlank { "dokumen_buka" }
                val targetFile = documentSourceReader.createDocumentFile(cleanPrefix.take(20))
                val stream = if (sourceUri.scheme == "file") {
                    FileInputStream(File(sourceUri.path ?: sourceUri.schemeSpecificPart))
                } else {
                    context.contentResolver.openInputStream(sourceUri)
                }
                stream?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalArgumentException("Gagal membuka file PDF")
                refreshRecentDocuments()
                targetFile
            }
        }

    override suspend fun stampSignatureToPdf(
        pdfFile: File,
        signatureFile: File,
        pageIndex: Int,
        alignment: SignatureAlignment
    ): Result<File> = withContext(Dispatchers.IO) {
        val originalName = pdfFile.nameWithoutExtension
        val targetFile = documentSourceReader.createDocumentFile("${originalName}_ttd".take(25))
        val result = pdfSignatureStamper.stampSignature(pdfFile, signatureFile, pageIndex, alignment, targetFile)
        if (result.isSuccess) {
            refreshRecentDocuments()
        }
        result
    }

    override suspend fun renderPdfPages(pdfFile: File): Result<List<Bitmap>> =
        pdfPreviewRenderer.renderPages(pdfFile)

    override suspend fun deleteDocument(file: File): Boolean =
        withContext(Dispatchers.IO) {
            val deleted = documentSourceReader.deleteDocument(file)
            if (deleted) {
                refreshRecentDocuments()
            }
            deleted
        }

    override suspend fun renameDocument(file: File, newName: String): Result<File> =
        withContext(Dispatchers.IO) {
            val result = documentSourceReader.renameDocument(file, newName)
            if (result.isSuccess) {
                refreshRecentDocuments()
            }
            result
        }
}
