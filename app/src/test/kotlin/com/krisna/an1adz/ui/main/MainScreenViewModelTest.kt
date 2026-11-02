package com.krisna.an1adz.ui.main

import android.graphics.Bitmap
import android.net.Uri
import com.krisna.an1adz.core.pdf.FilterType
import com.krisna.an1adz.data.PdfRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class MainScreenViewModelTest {

    @Test
    fun uiState_initiallyIdle() = runTest {
        val viewModel = MainScreenViewModel(FakePdfRepository())
        assertEquals(MainUiState.Idle, viewModel.uiState.first())
    }

    @Test
    fun resetState_returnsToIdle() = runTest {
        val viewModel = MainScreenViewModel(FakePdfRepository())
        viewModel.resetState()
        assertEquals(MainUiState.Idle, viewModel.uiState.first())
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

    override suspend fun renderPdfPages(pdfFile: File): Result<List<Bitmap>> =
        Result.success(emptyList())

    override suspend fun deleteDocument(file: File): Boolean = true

    override suspend fun renameDocument(file: File, newName: String): Result<File> =
        Result.success(File(file.parentFile, newName))
}
