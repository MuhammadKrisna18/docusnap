package com.krisna.an1adz.ui.preview

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisna.an1adz.data.PdfRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface PdfPreviewUiState {
    data object Loading : PdfPreviewUiState
    data class Success(val file: File, val pages: List<Bitmap>) : PdfPreviewUiState
    data class Error(val message: String) : PdfPreviewUiState
}

class PdfPreviewViewModel(
    private var filePath: String,
    private val pdfRepository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PdfPreviewUiState>(PdfPreviewUiState.Loading)
    val uiState: StateFlow<PdfPreviewUiState> = _uiState.asStateFlow()

    init {
        loadPdf()
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
