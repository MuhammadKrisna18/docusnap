package com.krisna.an1adz.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.krisna.an1adz.core.pdf.FilterType
import com.krisna.an1adz.core.update.AppUpdateInfo
import com.krisna.an1adz.core.update.AppUpdateService
import com.krisna.an1adz.data.PdfRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class MainScreenViewModelTest {

    @Test
    fun uiState_initiallyIdle() = runTest {
        val viewModel = MainScreenViewModel(FakePdfRepository(), FakeAppUpdateService())
        assertEquals(MainUiState.Idle, viewModel.uiState.first())
    }

    @Test
    fun resetState_returnsToIdle() = runTest {
        val viewModel = MainScreenViewModel(FakePdfRepository(), FakeAppUpdateService())
        viewModel.resetState()
        assertEquals(MainUiState.Idle, viewModel.uiState.first())
    }

    @Test
    fun updateUiState_initiallyIdle() = runTest {
        val viewModel = MainScreenViewModel(FakePdfRepository(), FakeAppUpdateService())
        assertEquals(UpdateUiState.Idle, viewModel.updateUiState.first())
    }

    @Test
    fun checkForUpdates_whenNewerAvailable_emitsUpdateAvailable() = runTest {
        val updateInfo = AppUpdateInfo(
            currentVersion = "1.0",
            latestVersion = "v1.1",
            releaseName = "Release v1.1",
            releaseNotes = "Perbaikan fitur",
            downloadUrl = "https://example.com/app.apk",
            isUpdateAvailable = true
        )
        val fakeService = FakeAppUpdateService(updateInfoResult = Result.success(updateInfo))
        val viewModel = MainScreenViewModel(FakePdfRepository(), fakeService)

        viewModel.checkForUpdates("1.0", isManual = true)

        val state = viewModel.updateUiState.first { it is UpdateUiState.UpdateAvailable }
        assertTrue(state is UpdateUiState.UpdateAvailable)
        assertEquals("v1.1", (state as UpdateUiState.UpdateAvailable).info.latestVersion)
    }

    @Test
    fun dismissUpdate_resetsToIdle() = runTest {
        val updateInfo = AppUpdateInfo(
            currentVersion = "1.0",
            latestVersion = "v1.1",
            releaseName = "Release v1.1",
            releaseNotes = "Perbaikan fitur",
            downloadUrl = "https://example.com/app.apk",
            isUpdateAvailable = true
        )
        val fakeService = FakeAppUpdateService(updateInfoResult = Result.success(updateInfo))
        val viewModel = MainScreenViewModel(FakePdfRepository(), fakeService)

        viewModel.checkForUpdates("1.0", isManual = true)
        viewModel.dismissUpdate()

        assertEquals(UpdateUiState.Idle, viewModel.updateUiState.first())
    }
}

private class FakeAppUpdateService(
    private val updateInfoResult: Result<AppUpdateInfo> = Result.success(
        AppUpdateInfo("1.0", "1.0", "Release 1.0", "", "", false)
    )
) : AppUpdateService {
    override suspend fun checkForUpdate(currentVersion: String): Result<AppUpdateInfo> = updateInfoResult

    override suspend fun downloadApk(
        downloadUrl: String,
        destinationFile: File,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = Result.success(destinationFile)

    override fun installApk(context: Context, apkFile: File): Result<Unit> = Result.success(Unit)
}

private class FakePdfRepository : PdfRepository {
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
