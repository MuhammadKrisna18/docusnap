package com.krisna.an1adz.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisna.an1adz.core.pdf.FilterType
import com.krisna.an1adz.data.PdfRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface MainUiState {
    data object Idle : MainUiState
    data class Processing(val message: String) : MainUiState
    data class Error(val message: String) : MainUiState
}

sealed interface MainNavigationEvent {
    data class OpenPreview(val filePath: String) : MainNavigationEvent
    data class ShowToast(val message: String) : MainNavigationEvent
}

class MainScreenViewModel(
    private val pdfRepository: PdfRepository
) : ViewModel() {

    val recentDocuments: StateFlow<List<File>> = pdfRepository.recentDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Idle)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<MainNavigationEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    fun processScannedPdf(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = MainUiState.Processing("Menyimpan hasil scan kamera kualitas tinggi...")
            pdfRepository.copyScannedPdf(uri)
                .onSuccess { file ->
                    _uiState.value = MainUiState.Idle
                    _eventChannel.send(MainNavigationEvent.OpenPreview(file.absolutePath))
                }
                .onFailure { throwable ->
                    _uiState.value = MainUiState.Error(throwable.localizedMessage ?: "Gagal memproses hasil scan")
                }
        }
    }

    fun processImages(uris: List<Uri>, filterType: FilterType) {
        viewModelScope.launch {
            _uiState.value = MainUiState.Processing("Memproses ${uris.size} gambar resolusi tinggi ke PDF...")
            pdfRepository.convertImagesToPdf(uris, filterType)
                .onSuccess { file ->
                    _uiState.value = MainUiState.Idle
                    _eventChannel.send(MainNavigationEvent.OpenPreview(file.absolutePath))
                }
                .onFailure { throwable ->
                    _uiState.value = MainUiState.Error(throwable.localizedMessage ?: "Gagal mengonversi gambar")
                }
        }
    }

    fun processDocuments(uris: List<Uri>) {
        viewModelScope.launch {
            val msg = if (uris.size > 1) "Mengonversi ${uris.size} file dokumen ke PDF..." else "Mengonversi dokumen ke PDF..."
            _uiState.value = MainUiState.Processing(msg)
            pdfRepository.convertDocumentsToPdf(uris)
                .onSuccess { file ->
                    _uiState.value = MainUiState.Idle
                    _eventChannel.send(MainNavigationEvent.OpenPreview(file.absolutePath))
                }
                .onFailure { throwable ->
                    _uiState.value = MainUiState.Error(throwable.localizedMessage ?: "Gagal mengonversi dokumen")
                }
        }
    }

    fun deleteDocument(file: File) {
        viewModelScope.launch {
            val deleted = pdfRepository.deleteDocument(file)
            if (deleted) {
                _eventChannel.send(MainNavigationEvent.ShowToast("Dokumen berhasil dihapus"))
            } else {
                _eventChannel.send(MainNavigationEvent.ShowToast("Gagal menghapus dokumen"))
            }
        }
    }

    fun refreshDocuments() {
        viewModelScope.launch {
            pdfRepository.refreshRecentDocuments()
        }
    }

    fun resetState() {
        _uiState.value = MainUiState.Idle
    }
}
