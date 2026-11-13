package com.krisna.an1adz.ui.signature

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisna.an1adz.core.signature.SignatureStroke
import com.krisna.an1adz.data.DefaultSignatureRepository
import com.krisna.an1adz.data.SignatureRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class SignatureViewModel(
    private val signatureRepository: SignatureRepository
) : ViewModel() {

    constructor(context: Context) : this(DefaultSignatureRepository(context))

    val savedSignatures: StateFlow<List<File>> = signatureRepository.savedSignatures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(SignatureUiState())
    val uiState: StateFlow<SignatureUiState> = _uiState.asStateFlow()

    private val _events = Channel<SignatureEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setColor(color: Color) {
        _uiState.update { it.copy(selectedColor = color) }
    }

    fun setStrokeWidth(width: Float) {
        _uiState.update { it.copy(selectedStrokeWidth = width) }
    }

    fun addStroke(points: List<Offset>) {
        if (points.isEmpty()) return
        val newStroke = SignatureStroke(
            points = points,
            color = _uiState.value.selectedColor,
            strokeWidth = _uiState.value.selectedStrokeWidth
        )
        _uiState.update { it.copy(strokes = it.strokes + newStroke) }
    }

    fun undo() {
        _uiState.update {
            if (it.strokes.isNotEmpty()) {
                it.copy(strokes = it.strokes.dropLast(1))
            } else {
                it
            }
        }
    }

    fun clear() {
        _uiState.update { it.copy(strokes = emptyList(), previewBitmap = null) }
    }

    fun createPreview(canvasWidth: Int, canvasHeight: Int) {
        val strokes = _uiState.value.strokes
        if (strokes.isEmpty()) {
            viewModelScope.launch {
                _events.send(SignatureEvent.ShowToast("Kanvas masih kosong, buat tanda tangan terlebih dahulu"))
            }
            return
        }

        val bitmap = signatureRepository.renderSignature(strokes, canvasWidth, canvasHeight)
        if (bitmap != null) {
            _uiState.update { it.copy(previewBitmap = bitmap) }
        } else {
            viewModelScope.launch {
                _events.send(SignatureEvent.ShowToast("Gagal memproses tanda tangan"))
            }
        }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(previewBitmap = null) }
    }

    fun saveSignature(bitmap: Bitmap, customName: String? = null, onSaved: (File) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            signatureRepository.saveSignature(bitmap, customName)
                .onSuccess { file ->
                    _uiState.update { it.copy(isSaving = false, savedFile = file, previewBitmap = null) }
                    _events.send(SignatureEvent.ShowToast("Tanda tangan PNG transparan berhasil disimpan"))
                    onSaved(file)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(SignatureEvent.ShowToast("Gagal menyimpan: ${error.localizedMessage}"))
                }
        }
    }

    fun exportSignatureToUri(sourceFile: File, targetUri: Uri) {
        viewModelScope.launch {
            signatureRepository.exportSignatureToUri(sourceFile, targetUri)
                .onSuccess {
                    _events.send(SignatureEvent.ShowToast("File PNG berhasil diunduh"))
                }
                .onFailure { error ->
                    _events.send(SignatureEvent.ShowToast("Gagal mengunduh: ${error.localizedMessage}"))
                }
        }
    }

    fun shareSignature(file: File) {
        signatureRepository.shareSignature(file)
    }

    fun deleteSignature(file: File) {
        viewModelScope.launch {
            val success = signatureRepository.deleteSignature(file)
            if (success) {
                _events.send(SignatureEvent.ShowToast("Tanda tangan dihapus"))
            }
        }
    }
}

