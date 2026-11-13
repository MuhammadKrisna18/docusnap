package com.krisna.an1adz.ui.main

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.krisna.an1adz.PdfPreviewNavKey
import com.krisna.an1adz.SignatureNavKey
import com.krisna.an1adz.core.pdf.FilterType
import com.krisna.an1adz.ui.main.components.DeleteConfirmationDialog
import com.krisna.an1adz.ui.main.components.EmptyDocumentsCard
import com.krisna.an1adz.ui.main.components.ErrorDialog
import com.krisna.an1adz.ui.main.components.FilterSelectionDialog
import com.krisna.an1adz.ui.main.components.HeroScannerCard
import com.krisna.an1adz.ui.main.components.ProcessingDialog
import com.krisna.an1adz.ui.main.components.RecentDocumentItem
import com.krisna.an1adz.ui.main.components.SecondaryActionCard
import com.krisna.an1adz.ui.main.components.UpdateDialogs
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainScreenViewModel,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()
    val recentDocs by viewModel.recentDocuments.collectAsStateWithLifecycle()

    val currentVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
    }

    var pendingImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingPdfUri by remember { mutableStateOf<Uri?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(FilterType.SCAN_BLACK_WHITE) }
    var documentToDelete by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(currentVersion) {
        viewModel.checkForUpdates(currentVersion, isManual = false)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MainNavigationEvent.OpenPreview -> {
                    onNavigate(PdfPreviewNavKey(event.filePath))
                }
                is MainNavigationEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pageUris = scanningResult?.pages?.map { it.imageUri } ?: emptyList()
            pendingPdfUri = scanningResult?.pdf?.uri
            if (pageUris.isNotEmpty()) {
                pendingImageUris = pageUris
                selectedFilter = FilterType.SCAN_BLACK_WHITE
                showFilterDialog = true
            } else if (pendingPdfUri != null) {
                viewModel.processScannedPdf(pendingPdfUri!!)
            } else {
                Toast.makeText(context, "Tidak ada hasil dari scanner", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingPdfUri = null
            pendingImageUris = uris
            showFilterDialog = true
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.processDocuments(uris)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "DocuSnap",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(
                        onClick = { viewModel.checkForUpdates(currentVersion, isManual = true) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Periksa Pembaruan",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                HeroScannerCard(
                    onClick = {
                        if (activity != null) {
                            val options = GmsDocumentScannerOptions.Builder()
                                .setGalleryImportAllowed(true)
                                .setPageLimit(50)
                                .setResultFormats(
                                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                                )
                                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                                .build()

                            val scanner = GmsDocumentScanning.getClient(options)
                            scanner.getStartScanIntent(activity)
                                .addOnSuccessListener { intentSender ->
                                    cameraScannerLauncher.launch(
                                        IntentSenderRequest.Builder(intentSender).build()
                                    )
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SecondaryActionCard(
                        icon = Icons.Default.PhotoLibrary,
                        title = "Impor Gambar",
                        subtitle = "JPG, PNG, WEBP, HEIC",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    SecondaryActionCard(
                        icon = Icons.Default.Description,
                        title = "Impor Dokumen",
                        subtitle = "TXT, MD, CSV, JSON, LOG",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            documentPickerLauncher.launch(
                                arrayOf(
                                    "text/*",
                                    "application/json",
                                    "application/xml",
                                    "text/plain",
                                    "text/csv",
                                    "text/html"
                                )
                            )
                        }
                    )
                }
            }

            item {
                SecondaryActionCard(
                    icon = Icons.Default.Draw,
                    title = "Tanda Tangan Digital",
                    subtitle = "Buat & simpan tanda tangan PNG transparan tanpa background",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNavigate(SignatureNavKey) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dokumen Terbaru",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (recentDocs.isNotEmpty()) {
                        Text(
                            text = "${recentDocs.size} Dokumen",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (recentDocs.isEmpty()) {
                item {
                    EmptyDocumentsCard()
                }
            } else {
                items(
                    items = recentDocs,
                    key = { it.absolutePath }
                ) { file ->
                    RecentDocumentItem(
                        file = file,
                        onClick = { onNavigate(PdfPreviewNavKey(file.absolutePath)) },
                        onDelete = { documentToDelete = file }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showFilterDialog) {
        FilterSelectionDialog(
            pageCount = pendingImageUris.size,
            selectedFilter = selectedFilter,
            onFilterChange = { selectedFilter = it },
            onConfirm = {
                showFilterDialog = false
                val pdfUri = pendingPdfUri
                if (selectedFilter == FilterType.ORIGINAL && pdfUri != null) {
                    viewModel.processScannedPdf(pdfUri)
                } else {
                    viewModel.processImages(pendingImageUris, selectedFilter)
                }
            },
            onDismiss = { showFilterDialog = false }
        )
    }

    documentToDelete?.let { file ->
        DeleteConfirmationDialog(
            file = file,
            onConfirm = {
                viewModel.deleteDocument(file)
                documentToDelete = null
            },
            onDismiss = { documentToDelete = null }
        )
    }

    when (val state = uiState) {
        is MainUiState.Processing -> ProcessingDialog(message = state.message)
        is MainUiState.Error -> ErrorDialog(
            message = state.message,
            onDismiss = { viewModel.resetState() }
        )
        is MainUiState.Idle -> Unit
    }

    UpdateDialogs(
        updateState = updateUiState,
        onDismiss = { viewModel.dismissUpdate() },
        onUpdateClick = { info -> viewModel.startDownloadUpdate(context, info) },
        onInstallClick = { apkFile -> viewModel.installApk(context, apkFile) }
    )
}
