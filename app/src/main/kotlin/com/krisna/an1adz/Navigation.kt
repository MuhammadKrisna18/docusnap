package com.krisna.an1adz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.krisna.an1adz.data.DefaultOfficeRepository
import com.krisna.an1adz.data.DefaultPdfRepository
import com.krisna.an1adz.data.DefaultSignatureRepository
import com.krisna.an1adz.ui.main.MainScreen
import com.krisna.an1adz.ui.main.MainScreenViewModel
import com.krisna.an1adz.ui.office.docx.DocxViewerScreen
import com.krisna.an1adz.ui.office.docx.DocxViewerViewModel
import com.krisna.an1adz.ui.preview.PdfPreviewScreen
import com.krisna.an1adz.ui.preview.PdfPreviewViewModel
import com.krisna.an1adz.ui.signature.SignatureScreen
import com.krisna.an1adz.ui.signature.SignatureViewModel

@Composable
fun MainNavigation(initialNavKey: NavKey = Main) {
    val backStack = rememberNavBackStack(initialNavKey)
    val context = LocalContext.current
    val pdfRepository = remember(context) { DefaultPdfRepository(context) }
    val signatureRepository = remember(context) { DefaultSignatureRepository(context) }
    val officeRepository = remember(context) { DefaultOfficeRepository(context) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                val mainViewModel: MainScreenViewModel = viewModel {
                    MainScreenViewModel(
                        pdfRepository = pdfRepository,
                        officeRepository = officeRepository
                    )
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
                    PdfPreviewViewModel(key.filePath, pdfRepository, signatureRepository)
                }
                PdfPreviewScreen(
                    viewModel = previewViewModel,
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<SignatureNavKey> {
                val signatureViewModel: SignatureViewModel = viewModel {
                    SignatureViewModel(signatureRepository)
                }
                SignatureScreen(
                    viewModel = signatureViewModel,
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<DocxViewerNavKey> { key ->
                val docxViewModel: DocxViewerViewModel = viewModel(
                    key = key.filePath
                ) {
                    DocxViewerViewModel(key.filePath, officeRepository)
                }
                DocxViewerScreen(
                    viewModel = docxViewModel,
                    onBackClick = { backStack.removeLastOrNull() },
                    onNavigateToPdf = { pdfPath ->
                        backStack.add(PdfPreviewNavKey(pdfPath))
                    }
                )
            }
        }
    )
}
