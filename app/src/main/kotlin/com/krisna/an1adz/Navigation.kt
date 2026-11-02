package com.krisna.an1adz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.krisna.an1adz.data.DefaultPdfRepository
import com.krisna.an1adz.ui.main.MainScreen
import com.krisna.an1adz.ui.main.MainScreenViewModel
import com.krisna.an1adz.ui.preview.PdfPreviewScreen
import com.krisna.an1adz.ui.preview.PdfPreviewViewModel

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)
    val context = LocalContext.current
    val pdfRepository = remember(context) { DefaultPdfRepository(context) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                val mainViewModel: MainScreenViewModel = viewModel {
                    MainScreenViewModel(pdfRepository)
                }
                MainScreen(
                    viewModel = mainViewModel,
                    onNavigate = { navKey -> backStack.add(navKey) }
                )
            }
            entry<PdfPreviewNavKey> { key ->
                val previewViewModel: PdfPreviewViewModel = viewModel(
                    key = key.filePath
                ) {
                    PdfPreviewViewModel(key.filePath, pdfRepository)
                }
                PdfPreviewScreen(
                    viewModel = previewViewModel,
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
