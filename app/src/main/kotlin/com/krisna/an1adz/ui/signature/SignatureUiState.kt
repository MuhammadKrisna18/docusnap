package com.krisna.an1adz.ui.signature

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.krisna.an1adz.core.signature.SignatureStroke
import java.io.File

data class SignatureUiState(
    val strokes: List<SignatureStroke> = emptyList(),
    val selectedColor: Color = Color(0xFF1E293B),
    val selectedStrokeWidth: Float = 6f,
    val previewBitmap: Bitmap? = null,
    val savedFile: File? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

sealed interface SignatureEvent {
    data class ShowToast(val message: String) : SignatureEvent
}

