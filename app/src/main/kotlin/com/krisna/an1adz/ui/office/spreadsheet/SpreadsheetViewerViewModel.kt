package com.krisna.an1adz.ui.office.spreadsheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisna.an1adz.core.office.spreadsheet.SpreadsheetCell
import com.krisna.an1adz.core.office.spreadsheet.SpreadsheetDocument
import com.krisna.an1adz.data.OfficeRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface SpreadsheetViewerUiState {
    data object Loading : SpreadsheetViewerUiState
    data class Success(
        val document: SpreadsheetDocument,
        val currentSheetIndex: Int = 0,
        val fontScale: Float = 1.0f,
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val isExportingPdf: Boolean = false,
        val selectedCell: SpreadsheetCell? = null
    ) : SpreadsheetViewerUiState
    data class Error(val message: String) : SpreadsheetViewerUiState
}

sealed interface SpreadsheetNavigationEvent {
    data class OpenPdfPreview(val filePath: String) : SpreadsheetNavigationEvent
    data class ShowToast(val message: String) : SpreadsheetNavigationEvent
}

class SpreadsheetViewerViewModel(
    private val filePath: String,
    private val officeRepository: OfficeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SpreadsheetViewerUiState>(SpreadsheetViewerUiState.Loading)
    val uiState: StateFlow<SpreadsheetViewerUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<SpreadsheetNavigationEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    init {
        loadSpreadsheet()
    }

    fun loadSpreadsheet() {
        viewModelScope.launch {
            _uiState.value = SpreadsheetViewerUiState.Loading
            val file = File(filePath)
            if (!file.exists()) {
                _uiState.value = SpreadsheetViewerUiState.Error("Berkas spreadsheet tidak ditemukan di $filePath")
                return@launch
            }

            officeRepository.parseSpreadsheet(file)
                .onSuccess { doc ->
                    _uiState.value = SpreadsheetViewerUiState.Success(document = doc)
                }
                .onFailure { throwable ->
                    _uiState.value = SpreadsheetViewerUiState.Error(
                        throwable.localizedMessage ?: "Gagal memproses berkas spreadsheet"
                    )
                }
        }
    }

    fun selectSheet(index: Int) {
        _uiState.update { current ->
            if (current is SpreadsheetViewerUiState.Success) {
                val validIndex = index.coerceIn(0, current.document.sheets.size - 1)
                current.copy(currentSheetIndex = validIndex, selectedCell = null)
            } else current
        }
    }

    fun selectCell(cell: SpreadsheetCell?) {
        _uiState.update { current ->
            if (current is SpreadsheetViewerUiState.Success) {
                current.copy(selectedCell = cell)
            } else current
        }
    }

    fun increaseFontScale() {
        _uiState.update { current ->
            if (current is SpreadsheetViewerUiState.Success) {
                current.copy(fontScale = (current.fontScale + 0.15f).coerceAtMost(1.8f))
            } else current
        }
    }

    fun decreaseFontScale() {
        _uiState.update { current ->
            if (current is SpreadsheetViewerUiState.Success) {
                current.copy(fontScale = (current.fontScale - 0.15f).coerceAtLeast(0.75f))
            } else current
        }
    }

    fun toggleSearch() {
        _uiState.update { current ->
            if (current is SpreadsheetViewerUiState.Success) {
                val newSearching = !current.isSearching
                current.copy(
                    isSearching = newSearching,
                    searchQuery = if (!newSearching) "" else current.searchQuery
                )
            } else current
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            if (current is SpreadsheetViewerUiState.Success) {
                current.copy(searchQuery = query)
            } else current
        }
    }

    fun exportToPdf() {
        val current = _uiState.value
        if (current !is SpreadsheetViewerUiState.Success || current.isExportingPdf) return

        viewModelScope.launch {
            _uiState.update {
                if (it is SpreadsheetViewerUiState.Success) it.copy(isExportingPdf = true) else it
            }

            val sheet = current.document.sheets.getOrNull(current.currentSheetIndex)
            val sheetTitle = sheet?.name ?: "Sheet"

            officeRepository.exportSpreadsheetToPdf(current.document, current.currentSheetIndex, "${current.document.title}_$sheetTitle")
                .onSuccess { pdfFile ->
                    _uiState.update {
                        if (it is SpreadsheetViewerUiState.Success) it.copy(isExportingPdf = false) else it
                    }
                    _eventChannel.send(SpreadsheetNavigationEvent.ShowToast("Berhasil mengekspor spreadsheet ke PDF!"))
                    _eventChannel.send(SpreadsheetNavigationEvent.OpenPdfPreview(pdfFile.absolutePath))
                }
                .onFailure { throwable ->
                    _uiState.update {
                        if (it is SpreadsheetViewerUiState.Success) it.copy(isExportingPdf = false) else it
                    }
                    _eventChannel.send(
                        SpreadsheetNavigationEvent.ShowToast(
                            "Gagal mengekspor PDF: ${throwable.localizedMessage ?: "Terjadi kesalahan"}"
                        )
                    )
                }
        }
    }
}
