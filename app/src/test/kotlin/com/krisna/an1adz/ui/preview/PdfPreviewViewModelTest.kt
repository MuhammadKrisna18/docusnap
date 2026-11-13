package com.krisna.an1adz.ui.preview

import android.graphics.Bitmap
import android.net.Uri
import com.krisna.an1adz.core.pdf.FilterType
import com.krisna.an1adz.core.signature.SignatureAlignment
import com.krisna.an1adz.core.signature.SignatureStroke
import com.krisna.an1adz.data.PdfRepository
import com.krisna.an1adz.data.SignatureRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class PdfPreviewViewModelTest {

    @Test
    fun readingMode_initiallyNormal() = runTest {
        val file = File.createTempFile("test", ".pdf")
        val viewModel = PdfPreviewViewModel(file.absolutePath, FakePdfRepository())

        assertEquals(PdfReadingMode.NORMAL, viewModel.readingMode.first())
        file.delete()
    }

    @Test
    fun setReadingMode_updatesState() = runTest {
        val file = File.createTempFile("test", ".pdf")
        val viewModel = PdfPreviewViewModel(file.absolutePath, FakePdfRepository())

        viewModel.setReadingMode(PdfReadingMode.SEPIA)
        assertEquals(PdfReadingMode.SEPIA, viewModel.readingMode.first())

        viewModel.setReadingMode(PdfReadingMode.NIGHT)
        assertEquals(PdfReadingMode.NIGHT, viewModel.readingMode.first())

        file.delete()
    }

    @Test
    fun loadPdf_whenFileExists_emitsSuccess() = runTest {
        val file = File.createTempFile("sample", ".pdf")
        val viewModel = PdfPreviewViewModel(file.absolutePath, FakePdfRepository())

        val state = viewModel.uiState.first { it is PdfPreviewUiState.Success }
        assertTrue(state is PdfPreviewUiState.Success)
        assertEquals(file.name, (state as PdfPreviewUiState.Success).file.name)

        file.delete()
    }

    @Test
    fun stampSignature_triggersStamperAndReloads() = runTest {
        val pdfFile = File.createTempFile("doc", ".pdf")
        val sigFile = File.createTempFile("sig", ".png")
        val repo = FakePdfRepository()
        val viewModel = PdfPreviewViewModel(pdfFile.absolutePath, repo)

        viewModel.uiState.first { it is PdfPreviewUiState.Success }
        viewModel.stampSignature(sigFile, 0, SignatureAlignment.BOTTOM_RIGHT)

        val event = viewModel.events.first()
        assertTrue(event is PdfPreviewEvent.ShowToast)

        pdfFile.delete()
        sigFile.delete()
    }
}

private class FakePdfRepository : PdfRepository {
    override val recentDocuments: Flow<List<File>> = MutableStateFlow(emptyList())

    override suspend fun refreshRecentDocuments() {}

    override suspend fun convertImagesToPdf(uris: List<Uri>, filterType: FilterType): Result<File> =
        Result.success(File("test.pdf"))

    override suspend fun convertDocumentsToPdf(uris: List<Uri>): Result<File> =
        Result.success(File("test.pdf"))

    override suspend fun copyScannedPdf(sourceUri: Uri): Result<File> =
        Result.success(File("test.pdf"))

    override suspend fun importExternalPdf(sourceUri: Uri): Result<File> =
        Result.success(File("test.pdf"))

    override suspend fun stampSignatureToPdf(
        pdfFile: File,
        signatureFile: File,
        pageIndex: Int,
        alignment: SignatureAlignment
    ): Result<File> = Result.success(pdfFile)

    override suspend fun renderPdfPages(pdfFile: File): Result<List<Bitmap>> =
        Result.success(emptyList())

    override suspend fun deleteDocument(file: File): Boolean = true

    override suspend fun renameDocument(file: File, newName: String): Result<File> =
        Result.success(File(file.parentFile, newName))
}
