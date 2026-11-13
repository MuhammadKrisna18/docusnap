package com.krisna.an1adz.ui.preview

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krisna.an1adz.core.pdf.DefaultFileExporter
import com.krisna.an1adz.core.pdf.FileExporter
import com.krisna.an1adz.ui.preview.components.RenameDocumentDialog
import com.krisna.an1adz.ui.preview.components.StampSignatureDialog
import com.krisna.an1adz.ui.preview.components.ZoomablePdfPageCard
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    viewModel: PdfPreviewViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    fileExporter: FileExporter? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val exporter = fileExporter ?: remember { DefaultFileExporter(context) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readingMode by viewModel.readingMode.collectAsStateWithLifecycle()
    val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var activeFileForSaving by remember { mutableStateOf<File?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showStampDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        val fileToSave = activeFileForSaving
        if (destinationUri != null && fileToSave != null) {
            coroutineScope.launch {
                val result = exporter.savePdfToUri(fileToSave, destinationUri)
                if (result.isSuccess) {
                    Toast.makeText(context, "PDF berhasil disimpan!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        "Gagal menyimpan PDF: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PdfPreviewEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = readingMode.backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val displayName = when (val state = uiState) {
                                is PdfPreviewUiState.Success -> state.file.name
                                else -> "Pratinjau PDF"
                            }
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (uiState is PdfPreviewUiState.Success) {
                                val currentFile = (uiState as PdfPreviewUiState.Success).file
                                IconButton(
                                    onClick = {
                                        renameInput = currentFile.nameWithoutExtension
                                        showRenameDialog = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Ubah Nama",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        if (uiState is PdfPreviewUiState.Success) {
                            val count = (uiState as PdfPreviewUiState.Success).pages.size
                            Text(
                                text = "$count Halaman  •  Ketuk dua kali untuk zoom",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val nextMode = when (readingMode) {
                                PdfReadingMode.NORMAL -> PdfReadingMode.SEPIA
                                PdfReadingMode.SEPIA -> PdfReadingMode.NIGHT
                                PdfReadingMode.NIGHT -> PdfReadingMode.NORMAL
                            }
                            viewModel.setReadingMode(nextMode)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = "Mode Membaca",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (uiState is PdfPreviewUiState.Success) {
                val file = (uiState as PdfPreviewUiState.Success).file
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { exporter.sharePdf(file) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Bagikan", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { showStampDialog = true },
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Beri TTD", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = {
                                activeFileForSaving = file
                                createDocumentLauncher.launch(file.name)
                            },
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Unduh", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is PdfPreviewUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is PdfPreviewUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(onClick = { viewModel.loadPdf() }) {
                            Text("Coba Lagi")
                        }
                    }
                }
            }
            is PdfPreviewUiState.Success -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(readingMode.backgroundColor)
                        .padding(innerPadding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(state.pages) { index, bitmap ->
                        ZoomablePdfPageCard(
                            bitmap = bitmap,
                            pageNumber = index + 1,
                            totalPages = state.pages.size
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameDocumentDialog(
            inputName = renameInput,
            onInputChange = { renameInput = it },
            onConfirm = {
                val trimmed = renameInput.trim()
                if (trimmed.isNotEmpty()) {
                    viewModel.renameFile(trimmed) { success, error ->
                        if (success) {
                            showRenameDialog = false
                            Toast.makeText(context, "Nama dokumen diperbarui", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, error ?: "Gagal mengubah nama", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showStampDialog && uiState is PdfPreviewUiState.Success) {
        val total = (uiState as PdfPreviewUiState.Success).pages.size
        StampSignatureDialog(
            totalPages = total,
            signatures = savedSignatures,
            onConfirm = { signatureFile, pageIndex, alignment ->
                showStampDialog = false
                viewModel.stampSignature(signatureFile, pageIndex, alignment)
            },
            onDismiss = { showStampDialog = false }
        )
    }
}
