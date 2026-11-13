package com.krisna.an1adz.ui.office.docx

import android.net.Uri
import com.krisna.an1adz.core.office.docx.DocxAlignment
import com.krisna.an1adz.core.office.docx.DocxDocument
import com.krisna.an1adz.core.office.docx.DocxElement
import com.krisna.an1adz.core.office.docx.TextRun
import com.krisna.an1adz.data.OfficeRepository
import com.krisna.an1adz.ui.preview.PdfReadingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DocxViewerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val fakeDocument = DocxDocument(
        fileName = "test.docx",
        title = "Judul Dokumen Uji",
        elements = listOf(
            DocxElement.Heading("Judul Dokumen Uji", 1),
            DocxElement.Paragraph(
                runs = listOf(TextRun("Ini adalah isi paragraf dokumen pengujian.")),
                alignment = DocxAlignment.LEFT
            )
        ),
        wordCount = 7,
        paragraphCount = 2,
        charCount = 50
    )

    private class FakeOfficeRepository(
        private val documentToReturn: DocxDocument,
        var shouldFailParse: Boolean = false,
        var shouldFailExport: Boolean = false
    ) : OfficeRepository {
        override suspend fun parseDocx(file: File): Result<DocxDocument> {
            return if (shouldFailParse) {
                Result.failure(RuntimeException("Gagal membaca dokumen"))
            } else {
                Result.success(documentToReturn)
            }
        }

        override suspend fun importOfficeDocument(uri: Uri, defaultExtension: String): Result<File> {
            return Result.success(File.createTempFile("imported", ".$defaultExtension"))
        }

        override suspend fun exportDocxToPdf(document: DocxDocument, outputTitle: String): Result<File> {
            return if (shouldFailExport) {
                Result.failure(RuntimeException("Gagal membuat PDF"))
            } else {
                Result.success(File.createTempFile("exported", ".pdf"))
            }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadDocument_success_emitsSuccessState() = testScope.runTest {
        val tempFile = File.createTempFile("test_doc", ".docx")
        val repo = FakeOfficeRepository(fakeDocument)
        val viewModel = DocxViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DocxViewerUiState.Success)
        val success = state as DocxViewerUiState.Success
        assertEquals("Judul Dokumen Uji", success.document.title)
        assertEquals(7, success.document.wordCount)

        tempFile.delete()
    }

    @Test
    fun loadDocument_fileNotFound_emitsErrorState() = testScope.runTest {
        val repo = FakeOfficeRepository(fakeDocument)
        val viewModel = DocxViewerViewModel("/path/yang/pasti/tidak/ada/file.docx", repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DocxViewerUiState.Error)
    }

    @Test
    fun fontScale_increaseAndDecrease_boundedProperly() = testScope.runTest {
        val tempFile = File.createTempFile("test_doc", ".docx")
        val repo = FakeOfficeRepository(fakeDocument)
        val viewModel = DocxViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        viewModel.increaseFontScale()
        val scaledUp = (viewModel.uiState.value as DocxViewerUiState.Success).fontScale
        assertTrue(scaledUp > 1.0f)

        viewModel.decreaseFontScale()
        val scaledDown = (viewModel.uiState.value as DocxViewerUiState.Success).fontScale
        assertEquals(1.0f, scaledDown, 0.01f)

        tempFile.delete()
    }

    @Test
    fun setReadingMode_updatesReadingMode() = testScope.runTest {
        val tempFile = File.createTempFile("test_doc", ".docx")
        val repo = FakeOfficeRepository(fakeDocument)
        val viewModel = DocxViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        viewModel.setReadingMode(PdfReadingMode.SEPIA)
        val sepiaState = viewModel.uiState.value as DocxViewerUiState.Success
        assertEquals(PdfReadingMode.SEPIA, sepiaState.readingMode)

        viewModel.setReadingMode(PdfReadingMode.NIGHT)
        val nightState = viewModel.uiState.value as DocxViewerUiState.Success
        assertEquals(PdfReadingMode.NIGHT, nightState.readingMode)

        tempFile.delete()
    }

    @Test
    fun toggleSearch_and_setSearchQuery_updatesState() = testScope.runTest {
        val tempFile = File.createTempFile("test_doc", ".docx")
        val repo = FakeOfficeRepository(fakeDocument)
        val viewModel = DocxViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        viewModel.toggleSearch()
        var success = viewModel.uiState.value as DocxViewerUiState.Success
        assertTrue(success.isSearching)

        viewModel.setSearchQuery("pengujian")
        success = viewModel.uiState.value as DocxViewerUiState.Success
        assertEquals("pengujian", success.searchQuery)

        viewModel.toggleSearch()
        success = viewModel.uiState.value as DocxViewerUiState.Success
        assertFalse(success.isSearching)
        assertEquals("", success.searchQuery)

        tempFile.delete()
    }

    @Test
    fun exportToPdf_success_emitsNavigationEvents() = testScope.runTest {
        val tempFile = File.createTempFile("test_doc", ".docx")
        val repo = FakeOfficeRepository(fakeDocument)
        val viewModel = DocxViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        val emittedEvents = mutableListOf<DocxNavigationEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.exportToPdf()
        advanceUntilIdle()

        assertTrue(emittedEvents.any { it is DocxNavigationEvent.ShowToast })
        assertTrue(emittedEvents.any { it is DocxNavigationEvent.OpenPdfPreview })

        job.cancel()
        tempFile.delete()
    }
}
