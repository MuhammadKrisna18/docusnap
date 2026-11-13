package com.krisna.an1adz.ui.preview

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisna.an1adz.core.signature.SignatureAlignment
import com.krisna.an1adz.data.PdfRepository
import com.krisna.an1adz.data.SignatureRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface PdfPreviewUiState {
    data object Loading : PdfPreviewUiState
    data class Success(val file: File, val pages: List<Bitmap>) : PdfPreviewUiState
    data class Error(val message: String) : PdfPreviewUiState
}

sealed interface PdfPreviewEvent {
    data class ShowToast(val message: String) : PdfPreviewEvent
}

class PdfPreviewViewModel(
    private var filePath: String,
    private val pdfRepository: PdfRepository,
    private val signatureRepository: SignatureRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<PdfPreviewUiState>(PdfPreviewUiState.Loading)
    val uiState: StateFlow<PdfPreviewUiState> = _uiState.asStateFlow()

    private val _readingMode = MutableStateFlow(PdfReadingMode.NORMAL)
    val readingMode: StateFlow<PdfReadingMode> = _readingMode.asStateFlow()

    private val _events = Channel<PdfPreviewEvent>(Channel.BUFFERED)
    val events: Flow<PdfPreviewEvent> = _events.receiveAsFlow()

    val savedSignatures: StateFlow<List<File>> = signatureRepository?.savedSignatures
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        ?: MutableStateFlow(emptyList())

    init {
        loadPdf()
    }

    fun setReadingMode(mode: PdfReadingMode) {
        _readingMode.value = mode
    }

    fun loadPdf() {
        viewModelScope.launch {
            _uiState.value = PdfPreviewUiState.Loading
            val file = File(filePath)
            if (!file.exists()) {
                _uiState.value = PdfPreviewUiState.Error("File tidak ditemukan")
                return@launch
            }
            pdfRepository.renderPdfPages(file)
                .onSuccess { bitmaps ->
                    _uiState.value = PdfPreviewUiState.Success(file = file, pages = bitmaps)
                }
                .onFailure { throwable ->
                    _uiState.value = PdfPreviewUiState.Error(throwable.localizedMessage ?: "Gagal merender PDF")
                }
        }
    }

    fun stampSignature(signatureFile: File, pageIndex: Int, alignment: SignatureAlignment) {
        val currentState = _uiState.value
        if (currentState !is PdfPreviewUiState.Success) return

        viewModelScope.launch {
            _uiState.value = PdfPreviewUiState.Loading
            pdfRepository.stampSignatureToPdf(currentState.file, signatureFile, pageIndex, alignment)
                .onSuccess { newFile ->
                    filePath = newFile.absolutePath
                    _events.send(PdfPreviewEvent.ShowToast("Tanda tangan berhasil dibubuhkan"))
                    loadPdf()
                }
                .onFailure { error ->
                    _events.send(PdfPreviewEvent.ShowToast("Gagal membubuhkan tanda tangan: ${error.localizedMessage}"))
                    loadPdf()
                }
        }
    }

    fun renameFile(newName: String, onResult: (Boolean, String?) -> Unit) {
        val currentState = _uiState.value
        if (currentState !is PdfPreviewUiState.Success) return

        viewModelScope.launch {
            pdfRepository.renameDocument(currentState.file, newName)
                .onSuccess { newFile ->
                    filePath = newFile.absolutePath
                    _uiState.value = currentState.copy(file = newFile)
                    onResult(true, null)
                }
                .onFailure { error ->
                    onResult(false, error.localizedMessage)
                }
        }
    }
}
