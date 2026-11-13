package com.krisna.an1adz.ui.office.spreadsheet

import android.net.Uri
import com.krisna.an1adz.core.office.docx.DocxDocument
import com.krisna.an1adz.core.office.pptx.PptxPresentation
import com.krisna.an1adz.core.office.spreadsheet.SpreadsheetCell
import com.krisna.an1adz.core.office.spreadsheet.SpreadsheetDocument
import com.krisna.an1adz.core.office.spreadsheet.SpreadsheetRow
import com.krisna.an1adz.core.office.spreadsheet.SpreadsheetSheet
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SpreadsheetViewerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val fakeDocument = SpreadsheetDocument(
        fileName = "keuangan.xlsx",
        title = "Laporan Keuangan",
        sheets = listOf(
            SpreadsheetSheet(
                name = "Januari",
                rows = listOf(
                    SpreadsheetRow(
                        rowIndex = 1,
                        cells = listOf(
                            SpreadsheetCell(colIndex = 0, cellRef = "A1", value = "Item", isHeader = true),
                            SpreadsheetCell(colIndex = 1, cellRef = "B1", value = "Harga", isHeader = true)
                        )
                    ),
                    SpreadsheetRow(
                        rowIndex = 2,
                        cells = listOf(
                            SpreadsheetCell(colIndex = 0, cellRef = "A2", value = "Kertas A4"),
                            SpreadsheetCell(colIndex = 1, cellRef = "B2", value = "50000", isNumeric = true)
                        )
                    )
                ),
                maxColumns = 2,
                rowCount = 2
            ),
            SpreadsheetSheet(
                name = "Februari",
                rows = emptyList(),
                maxColumns = 0,
                rowCount = 0
            )
        ),
        totalSheets = 2,
        totalRows = 2,
        totalColumns = 2
    )

    private class FakeOfficeRepository(
        private val docToReturn: SpreadsheetDocument,
        var shouldFailParse: Boolean = false,
        var shouldFailExport: Boolean = false
    ) : OfficeRepository {
        override suspend fun parseDocx(file: File): Result<DocxDocument> {
            return Result.failure(UnsupportedOperationException())
        }

        override suspend fun parsePptx(file: File): Result<PptxPresentation> {
            return Result.failure(UnsupportedOperationException())
        }

        override suspend fun parseSpreadsheet(file: File): Result<SpreadsheetDocument> {
            return if (shouldFailParse) {
                Result.failure(RuntimeException("Gagal membaca spreadsheet"))
            } else {
                Result.success(docToReturn)
            }
        }

        override suspend fun importOfficeDocument(uri: Uri, defaultExtension: String): Result<File> {
            return Result.success(File.createTempFile("imported", ".$defaultExtension"))
        }

        override suspend fun exportDocxToPdf(document: DocxDocument, outputTitle: String): Result<File> {
            return Result.failure(UnsupportedOperationException())
        }

        override suspend fun exportPptxToPdf(presentation: PptxPresentation, outputTitle: String): Result<File> {
            return Result.failure(UnsupportedOperationException())
        }

        override suspend fun exportSpreadsheetToPdf(document: SpreadsheetDocument, sheetIndex: Int, outputTitle: String): Result<File> {
            return if (shouldFailExport) {
                Result.failure(RuntimeException("Gagal membuat PDF"))
            } else {
                Result.success(File.createTempFile("exported_sheet", ".pdf"))
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
    fun loadSpreadsheet_success_emitsSuccessState() = testScope.runTest {
        val tempFile = File.createTempFile("test_sheet", ".xlsx")
        val repo = FakeOfficeRepository(fakeDocument)
        val viewModel = SpreadsheetViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SpreadsheetViewerUiState.Success)
        val success = state as SpreadsheetViewerUiState.Success
        assertEquals("Laporan Keuangan", success.document.title)
        assertEquals(2, success.document.totalSheets)
        assertEquals(0, success.currentSheetIndex)

        tempFile.delete()
    }

    @Test
    fun loadSpreadsheet_fileNotFound_emitsErrorState() = testScope.runTest {
        val repo = FakeOfficeRepository(fakeDocument)
        val viewModel = SpreadsheetViewerViewModel("/path/yang/salah/file.xlsx", repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SpreadsheetViewerUiState.Error)
    }

    @Test
    fun selectSheet_updatesCurrentSheetIndex() = testScope.runTest {
        val tempFile = File.createTempFile("test_sheet", ".xlsx")
        val repo = FakeOfficeRepository(fakeDocument)
        val viewModel = SpreadsheetViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        viewModel.selectSheet(1)
        val success = viewModel.uiState.value as SpreadsheetViewerUiState.Success
        assertEquals(1, success.currentSheetIndex)

        tempFile.delete()
    }

    @Test
    fun selectCell_updatesSelectedCell() = testScope.runTest {
        val tempFile = File.createTempFile("test_sheet", ".xlsx")
        val repo = FakeOfficeRepository(fakeDocument)
        val viewModel = SpreadsheetViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        val cell = SpreadsheetCell(0, "A2", "Kertas A4")
        viewModel.selectCell(cell)
        var success = viewModel.uiState.value as SpreadsheetViewerUiState.Success
        assertNotNull(success.selectedCell)
        assertEquals("A2", success.selectedCell?.cellRef)

        viewModel.selectCell(null)
        success = viewModel.uiState.value as SpreadsheetViewerUiState.Success
        assertNull(success.selectedCell)

        tempFile.delete()
    }

    @Test
    fun fontScale_increaseAndDecrease_boundedProperly() = testScope.runTest {
        val tempFile = File.createTempFile("test_sheet", ".xlsx")
        val repo = FakeOfficeRepository(fakeDocument)
        val viewModel = SpreadsheetViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        viewModel.increaseFontScale()
        var success = viewModel.uiState.value as SpreadsheetViewerUiState.Success
        assertTrue(success.fontScale > 1.0f)

        viewModel.decreaseFontScale()
        success = viewModel.uiState.value as SpreadsheetViewerUiState.Success
        assertEquals(1.0f, success.fontScale, 0.01f)

        tempFile.delete()
    }

    @Test
    fun toggleSearch_and_setSearchQuery_updatesState() = testScope.runTest {
        val tempFile = File.createTempFile("test_sheet", ".xlsx")
        val repo = FakeOfficeRepository(fakeDocument)
        val viewModel = SpreadsheetViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        viewModel.toggleSearch()
        var success = viewModel.uiState.value as SpreadsheetViewerUiState.Success
        assertTrue(success.isSearching)

        viewModel.setSearchQuery("Kertas")
        success = viewModel.uiState.value as SpreadsheetViewerUiState.Success
        assertEquals("Kertas", success.searchQuery)

        viewModel.toggleSearch()
        success = viewModel.uiState.value as SpreadsheetViewerUiState.Success
        assertFalse(success.isSearching)
        assertEquals("", success.searchQuery)

        tempFile.delete()
    }

    @Test
    fun exportToPdf_success_emitsNavigationEvents() = testScope.runTest {
        val tempFile = File.createTempFile("test_sheet", ".xlsx")
        val repo = FakeOfficeRepository(fakeDocument)
        val viewModel = SpreadsheetViewerViewModel(tempFile.absolutePath, repo)

        advanceUntilIdle()

        val emittedEvents = mutableListOf<SpreadsheetNavigationEvent>()
        val job = launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.exportToPdf()
        advanceUntilIdle()

        assertTrue(emittedEvents.any { it is SpreadsheetNavigationEvent.ShowToast })
        assertTrue(emittedEvents.any { it is SpreadsheetNavigationEvent.OpenPdfPreview })

        job.cancel()
        tempFile.delete()
    }
}
