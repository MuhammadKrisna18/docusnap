package com.krisna.an1adz.ui.office.pptx

import android.net.Uri
import com.krisna.an1adz.core.office.docx.DocxDocument
import com.krisna.an1adz.core.office.docx.TextRun
import com.krisna.an1adz.core.office.pptx.PptxElement
import com.krisna.an1adz.core.office.pptx.PptxPresentation
import com.krisna.an1adz.core.office.pptx.PptxSlide
import com.krisna.an1adz.data.OfficeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PptxViewerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val fakePresentation = PptxPresentation(
        fileName = "presentasi.pptx",
        title = "Pengenalan DocuSnap",
        slides = listOf(
            PptxSlide(
                slideNumber = 1,
                title = "Pengenalan DocuSnap",
                elements = listOf(
                    PptxElement.Title("Pengenalan DocuSnap"),
                    PptxElement.Subtitle("Aplikasi Scanner dan Reader Canggih")
                )
            ),
            PptxSlide(
                slideNumber = 2,
                title = "Fitur Unggulan",
                elements = listOf(
                    PptxElement.Title("Fitur Unggulan"),
                    PptxElement.Paragraph(
                        runs = listOf(TextRun("Scanner HD")),
                        isBullet = true
                    ),
                    PptxElement.Paragraph(
                        runs = listOf(TextRun("PDF & Office Reader")),
                        isBullet = true
                    )
                )
            ),
            PptxSlide(
                slideNumber = 3,
                title = "Kesimpulan",
                elements = listOf(
                    PptxElement.Title("Kesimpulan"),
                    PptxElement.Paragraph(
                        runs = listOf(TextRun("DocuSnap mempermudah produktivitas Anda."))
                    )
                )
            )
        ),
        totalSlides = 3,
        totalWords = 15
    )

    private class FakeOfficeRepository(
        private val presentationToReturn: PptxPresentation,
        var shouldFailParse: Boolean = false,
        var shouldFailExport: Boolean = false
    ) : OfficeRepository {
        override suspend fun parseDocx(file: File): Result<DocxDocument> {
            return Result.failure(UnsupportedOperationException())
        }

        override suspend fun parsePptx(file: File): Result<PptxPresentation> {
            return if (shouldFailParse) {
                Result.failure(RuntimeException("Gagal membaca presentasi"))
            } else {
                Result.success(presentationToReturn)
            }
        }

        override suspend fun importOfficeDocument(uri: Uri, defaultExtension: String): Result<File> {
            return Result.success(File.createTempFile("imported", ".$defaultExtension"))
        }

        override suspend fun exportDocxToPdf(document: DocxDocument, outputTitle: String): Result<File> {
            return Result.failure(UnsupportedOperationException())
        }

        override suspend fun exportPptxToPdf(presentation: PptxPresentation, outputTitle: String): Result<File> {
            return if (shouldFailExport) {
                Result.failure(RuntimeException("Gagal membuat PDF"))
            } else {
                Result.success(File.createTempFile("exported_pptx", ".pdf"))
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
    fun loadPresentation_success_emitsSuccessState() = testScope.runTest {
        val tempFile = File.createTempFile("test_pres", ".pptx")
        val repo = FakeOfficeRepository(fakePresentation)
        val viewModel = PptxViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PptxViewerUiState.Success)
        val success = state as PptxViewerUiState.Success
        assertEquals("Pengenalan DocuSnap", success.presentation.title)
        assertEquals(3, success.presentation.totalSlides)
        assertEquals(0, success.currentSlideIndex)

        tempFile.delete()
    }

    @Test
    fun loadPresentation_fileNotFound_emitsErrorState() = testScope.runTest {
        val repo = FakeOfficeRepository(fakePresentation)
        val viewModel = PptxViewerViewModel("/path/yang/salah/file.pptx", repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PptxViewerUiState.Error)
    }

    @Test
    fun slideNavigation_nextAndPrevious_boundedProperly() = testScope.runTest {
        val tempFile = File.createTempFile("test_pres", ".pptx")
        val repo = FakeOfficeRepository(fakePresentation)
        val viewModel = PptxViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        viewModel.nextSlide()
        var success = viewModel.uiState.value as PptxViewerUiState.Success
        assertEquals(1, success.currentSlideIndex)

        viewModel.nextSlide()
        success = viewModel.uiState.value as PptxViewerUiState.Success
        assertEquals(2, success.currentSlideIndex)

        viewModel.nextSlide()
        success = viewModel.uiState.value as PptxViewerUiState.Success
        assertEquals(2, success.currentSlideIndex)

        viewModel.previousSlide()
        success = viewModel.uiState.value as PptxViewerUiState.Success
        assertEquals(1, success.currentSlideIndex)

        viewModel.previousSlide()
        success = viewModel.uiState.value as PptxViewerUiState.Success
        assertEquals(0, success.currentSlideIndex)

        viewModel.previousSlide()
        success = viewModel.uiState.value as PptxViewerUiState.Success
        assertEquals(0, success.currentSlideIndex)

        tempFile.delete()
    }

    @Test
    fun goToSlide_updatesIndexAndClosesOverview() = testScope.runTest {
        val tempFile = File.createTempFile("test_pres", ".pptx")
        val repo = FakeOfficeRepository(fakePresentation)
        val viewModel = PptxViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        viewModel.toggleOverview()
        var success = viewModel.uiState.value as PptxViewerUiState.Success
        assertTrue(success.isOverviewVisible)

        viewModel.goToSlide(2)
        success = viewModel.uiState.value as PptxViewerUiState.Success
        assertEquals(2, success.currentSlideIndex)
        assertFalse(success.isOverviewVisible)

        tempFile.delete()
    }

    @Test
    fun togglePresenterMode_and_toggleOverview_updatesState() = testScope.runTest {
        val tempFile = File.createTempFile("test_pres", ".pptx")
        val repo = FakeOfficeRepository(fakePresentation)
        val viewModel = PptxViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        viewModel.togglePresenterMode()
        var success = viewModel.uiState.value as PptxViewerUiState.Success
        assertTrue(success.isPresenterMode)

        viewModel.togglePresenterMode()
        success = viewModel.uiState.value as PptxViewerUiState.Success
        assertFalse(success.isPresenterMode)

        tempFile.delete()
    }

    @Test
    fun exportToPdf_success_emitsNavigationEvents() = testScope.runTest {
        val tempFile = File.createTempFile("test_pres", ".pptx")
        val repo = FakeOfficeRepository(fakePresentation)
        val viewModel = PptxViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        val emittedEvents = mutableListOf<PptxNavigationEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.exportToPdf()
        advanceUntilIdle()

        assertTrue(emittedEvents.any { it is PptxNavigationEvent.ShowToast })
        assertTrue(emittedEvents.any { it is PptxNavigationEvent.OpenPdfPreview })

        job.cancel()
        tempFile.delete()
    }
}
