package com.krisna.an1adz.ui.main

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.krisna.an1adz.core.pdf.FilterType
import com.krisna.an1adz.data.PdfRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        val viewModel = MainScreenViewModel(TestPdfRepository())
        composeTestRule.setContent {
            MainScreen(
                viewModel = viewModel,
                onNavigate = {}
            )
        }
    }

    @Test
    fun screenTitle_exists() {
        composeTestRule.onNodeWithText("DocuSnap").assertExists()
    }
}

private class TestPdfRepository : PdfRepository {
    override val recentDocuments: Flow<List<File>> = MutableStateFlow(emptyList())

    override suspend fun refreshRecentDocuments() {}

    override suspend fun convertImagesToPdf(uris: List<Uri>, filterType: FilterType): Result<File> =
        Result.success(File("test.pdf"))

    override suspend fun convertDocumentsToPdf(uris: List<Uri>): Result<File> =
        Result.success(File("test.pdf"))

    override suspend fun copyScannedPdf(sourceUri: Uri): Result<File> =
        Result.success(File("test.pdf"))

    override suspend fun renderPdfPages(pdfFile: File): Result<List<Bitmap>> =
        Result.success(emptyList())

    override suspend fun deleteDocument(file: File): Boolean = true

    override suspend fun renameDocument(file: File, newName: String): Result<File> =
        Result.success(File(file.parentFile, newName))
}
