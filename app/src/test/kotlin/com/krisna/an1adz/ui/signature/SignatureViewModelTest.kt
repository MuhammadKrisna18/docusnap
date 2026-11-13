package com.krisna.an1adz.ui.signature

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.krisna.an1adz.core.signature.SignatureStroke
import com.krisna.an1adz.data.SignatureRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class SignatureViewModelTest {

    @Test
    fun uiState_initiallyEmpty() = runTest {
        val viewModel = SignatureViewModel(FakeSignatureRepository())
        val state = viewModel.uiState.first()

        assertTrue(state.strokes.isEmpty())
        assertEquals(Color(0xFF1E293B), state.selectedColor)
        assertEquals(6f, state.selectedStrokeWidth)
    }

    @Test
    fun setColor_updatesSelectedColor() = runTest {
        val viewModel = SignatureViewModel(FakeSignatureRepository())
        viewModel.setColor(Color.Red)

        val state = viewModel.uiState.first()
        assertEquals(Color.Red, state.selectedColor)
    }

    @Test
    fun setStrokeWidth_updatesSelectedStrokeWidth() = runTest {
        val viewModel = SignatureViewModel(FakeSignatureRepository())
        viewModel.setStrokeWidth(10f)

        val state = viewModel.uiState.first()
        assertEquals(10f, state.selectedStrokeWidth)
    }

    @Test
    fun addStroke_appendsStroke() = runTest {
        val viewModel = SignatureViewModel(FakeSignatureRepository())
        val points = listOf(Offset(10f, 10f), Offset(20f, 20f))

        viewModel.addStroke(points)

        val state = viewModel.uiState.first()
        assertEquals(1, state.strokes.size)
        assertEquals(points, state.strokes[0].points)
    }

    @Test
    fun undo_removesLastStroke() = runTest {
        val viewModel = SignatureViewModel(FakeSignatureRepository())
        viewModel.addStroke(listOf(Offset(10f, 10f)))
        viewModel.addStroke(listOf(Offset(20f, 20f)))

        viewModel.undo()

        val state = viewModel.uiState.first()
        assertEquals(1, state.strokes.size)
    }

    @Test
    fun clear_removesAllStrokes() = runTest {
        val viewModel = SignatureViewModel(FakeSignatureRepository())
        viewModel.addStroke(listOf(Offset(10f, 10f)))
        viewModel.addStroke(listOf(Offset(20f, 20f)))

        viewModel.clear()

        val state = viewModel.uiState.first()
        assertTrue(state.strokes.isEmpty())
    }
}

private class FakeSignatureRepository : SignatureRepository {
    override val savedSignatures: Flow<List<File>> = MutableStateFlow(emptyList())

    override suspend fun refreshSignatures() {}

    override fun renderSignature(
        strokes: List<SignatureStroke>,
        width: Int,
        height: Int
    ): Bitmap? = null

    override suspend fun saveSignature(bitmap: Bitmap, customName: String?): Result<File> =
        Result.success(File("signature.png"))

    override suspend fun exportSignatureToUri(sourceFile: File, targetUri: Uri): Result<Unit> =
        Result.success(Unit)

    override fun shareSignature(file: File) {}

    override suspend fun deleteSignature(file: File): Boolean = true
}

