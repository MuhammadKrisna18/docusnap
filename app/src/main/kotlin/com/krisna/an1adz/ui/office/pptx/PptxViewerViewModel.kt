package com.krisna.an1adz.ui.office.pptx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisna.an1adz.core.office.pptx.PptxPresentation
import com.krisna.an1adz.data.OfficeRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface PptxViewerUiState {
    data object Loading : PptxViewerUiState
    data class Success(
        val presentation: PptxPresentation,
        val currentSlideIndex: Int = 0,
        val fontScale: Float = 1.0f,
        val isPresenterMode: Boolean = false,
        val isOverviewVisible: Boolean = false,
        val isExportingPdf: Boolean = false
    ) : PptxViewerUiState
    data class Error(val message: String) : PptxViewerUiState
}

sealed interface PptxNavigationEvent {
    data class OpenPdfPreview(val filePath: String) : PptxNavigationEvent
    data class ShowToast(val message: String) : PptxNavigationEvent
}

class PptxViewerViewModel(
    private val filePath: String,
    private val officeRepository: OfficeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PptxViewerUiState>(PptxViewerUiState.Loading)
    val uiState: StateFlow<PptxViewerUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<PptxNavigationEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    init {
        loadPresentation()
    }

    fun loadPresentation() {
        viewModelScope.launch {
            _uiState.value = PptxViewerUiState.Loading
            val file = File(filePath)
            if (!file.exists()) {
                _uiState.value = PptxViewerUiState.Error("Berkas presentasi tidak ditemukan di $filePath")
                return@launch
            }

            officeRepository.parsePptx(file)
                .onSuccess { pres ->
                    _uiState.value = PptxViewerUiState.Success(presentation = pres)
                }
                .onFailure { throwable ->
                    _uiState.value = PptxViewerUiState.Error(
                        throwable.localizedMessage ?: "Gagal memproses berkas presentasi PPT"
                    )
                }
        }
    }

    fun nextSlide() {
        _uiState.update { current ->
            if (current is PptxViewerUiState.Success) {
                val nextIndex = (current.currentSlideIndex + 1).coerceAtMost(current.presentation.slides.size - 1)
                current.copy(currentSlideIndex = nextIndex)
            } else current
        }
    }

    fun previousSlide() {
        _uiState.update { current ->
            if (current is PptxViewerUiState.Success) {
                val prevIndex = (current.currentSlideIndex - 1).coerceAtLeast(0)
                current.copy(currentSlideIndex = prevIndex)
            } else current
        }
    }

    fun goToSlide(index: Int) {
        _uiState.update { current ->
            if (current is PptxViewerUiState.Success) {
                val validIndex = index.coerceIn(0, current.presentation.slides.size - 1)
                current.copy(currentSlideIndex = validIndex, isOverviewVisible = false)
            } else current
        }
    }

    fun increaseFontScale() {
        _uiState.update { current ->
            if (current is PptxViewerUiState.Success) {
                current.copy(fontScale = (current.fontScale + 0.15f).coerceAtMost(1.8f))
            } else current
        }
    }

    fun decreaseFontScale() {
        _uiState.update { current ->
            if (current is PptxViewerUiState.Success) {
                current.copy(fontScale = (current.fontScale - 0.15f).coerceAtLeast(0.75f))
            } else current
        }
    }

    fun resetFontScale() {
        _uiState.update { current ->
            if (current is PptxViewerUiState.Success) {
                current.copy(fontScale = 1.0f)
            } else current
        }
    }

    fun togglePresenterMode() {
        _uiState.update { current ->
            if (current is PptxViewerUiState.Success) {
                current.copy(isPresenterMode = !current.isPresenterMode)
            } else current
        }
    }

    fun toggleOverview() {
        _uiState.update { current ->
            if (current is PptxViewerUiState.Success) {
                current.copy(isOverviewVisible = !current.isOverviewVisible)
            } else current
        }
    }

    fun exportToPdf() {
        val current = _uiState.value
        if (current !is PptxViewerUiState.Success || current.isExportingPdf) return

        viewModelScope.launch {
            _uiState.update {
                if (it is PptxViewerUiState.Success) it.copy(isExportingPdf = true) else it
            }

            officeRepository.exportPptxToPdf(current.presentation, current.presentation.title)
                .onSuccess { pdfFile ->
                    _uiState.update {
                        if (it is PptxViewerUiState.Success) it.copy(isExportingPdf = false) else it
                    }
                    _eventChannel.send(PptxNavigationEvent.ShowToast("Berhasil mengekspor slide ke PDF!"))
                    _eventChannel.send(PptxNavigationEvent.OpenPdfPreview(pdfFile.absolutePath))
                }
                .onFailure { throwable ->
                    _uiState.update {
                        if (it is PptxViewerUiState.Success) it.copy(isExportingPdf = false) else it
                    }
                    _eventChannel.send(
                        PptxNavigationEvent.ShowToast(
                            "Gagal mengekspor PDF: ${throwable.localizedMessage ?: "Terjadi kesalahan"}"
                        )
                    )
                }
        }
    }
}
