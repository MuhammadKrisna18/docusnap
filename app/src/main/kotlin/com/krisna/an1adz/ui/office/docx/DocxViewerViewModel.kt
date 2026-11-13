package com.krisna.an1adz.ui.office.docx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisna.an1adz.core.office.docx.DocxDocument
import com.krisna.an1adz.data.OfficeRepository
import com.krisna.an1adz.ui.preview.PdfReadingMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface DocxViewerUiState {
    data object Loading : DocxViewerUiState
    data class Success(
        val document: DocxDocument,
        val fontScale: Float = 1.0f,
        val readingMode: PdfReadingMode = PdfReadingMode.NORMAL,
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val isExportingPdf: Boolean = false
    ) : DocxViewerUiState
    data class Error(val message: String) : DocxViewerUiState
}

sealed interface DocxNavigationEvent {
    data class OpenPdfPreview(val filePath: String) : DocxNavigationEvent
    data class ShowToast(val message: String) : DocxNavigationEvent
}

class DocxViewerViewModel(
    private val filePath: String,
    private val officeRepository: OfficeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DocxViewerUiState>(DocxViewerUiState.Loading)
    val uiState: StateFlow<DocxViewerUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<DocxNavigationEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    init {
        loadDocument()
    }

    fun loadDocument() {
        viewModelScope.launch {
            _uiState.value = DocxViewerUiState.Loading
            val file = File(filePath)
            if (!file.exists()) {
                _uiState.value = DocxViewerUiState.Error("Berkas dokumen tidak ditemukan di $filePath")
                return@launch
            }

            officeRepository.parseDocx(file)
                .onSuccess { doc ->
                    _uiState.value = DocxViewerUiState.Success(document = doc)
                }
                .onFailure { throwable ->
                    _uiState.value = DocxViewerUiState.Error(
                        throwable.localizedMessage ?: "Gagal memproses dokumen Word"
                    )
                }
        }
    }

    fun increaseFontScale() {
        _uiState.update { current ->
            if (current is DocxViewerUiState.Success) {
                current.copy(fontScale = (current.fontScale + 0.15f).coerceAtMost(1.8f))
            } else current
        }
    }

    fun decreaseFontScale() {
        _uiState.update { current ->
            if (current is DocxViewerUiState.Success) {
                current.copy(fontScale = (current.fontScale - 0.15f).coerceAtLeast(0.7f))
            } else current
        }
    }

    fun setReadingMode(mode: PdfReadingMode) {
        _uiState.update { current ->
            if (current is DocxViewerUiState.Success) {
                current.copy(readingMode = mode)
            } else current
        }
    }

    fun toggleSearch() {
        _uiState.update { current ->
            if (current is DocxViewerUiState.Success) {
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
            if (current is DocxViewerUiState.Success) {
                current.copy(searchQuery = query)
            } else current
        }
    }

    fun exportToPdf() {
        val current = _uiState.value
        if (current !is DocxViewerUiState.Success || current.isExportingPdf) return

        viewModelScope.launch {
            _uiState.update {
                if (it is DocxViewerUiState.Success) it.copy(isExportingPdf = true) else it
            }

            officeRepository.exportDocxToPdf(current.document, current.document.title)
                .onSuccess { pdfFile ->
                    _uiState.update {
                        if (it is DocxViewerUiState.Success) it.copy(isExportingPdf = false) else it
                    }
                    _eventChannel.send(DocxNavigationEvent.ShowToast("Berhasil diekspor ke PDF!"))
                    _eventChannel.send(DocxNavigationEvent.OpenPdfPreview(pdfFile.absolutePath))
                }
                .onFailure { throwable ->
                    _uiState.update {
                        if (it is DocxViewerUiState.Success) it.copy(isExportingPdf = false) else it
                    }
                    _eventChannel.send(
                        DocxNavigationEvent.ShowToast(
                            "Gagal mengekspor PDF: ${throwable.localizedMessage ?: "Terjadi kesalahan"}"
                        )
                    )
                }
        }
    }
}

