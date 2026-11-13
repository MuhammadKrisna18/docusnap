package com.krisna.an1adz.ui.signature

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.krisna.an1adz.core.util.FormatUtils
import com.krisna.an1adz.ui.signature.components.SignatureCanvas
import com.krisna.an1adz.ui.signature.components.SignaturePreviewDialog
import com.krisna.an1adz.ui.signature.components.SignatureToolbar
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureScreen(
    viewModel: SignatureViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()

    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    var activeFileForExport by remember { mutableStateOf<File?>(null) }

    val createPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { destinationUri ->
        val fileToSave = activeFileForExport
        if (destinationUri != null && fileToSave != null) {
            viewModel.exportSignatureToUri(fileToSave, destinationUri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SignatureEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Tanda Tangan Digital",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Format PNG Transparan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SignatureCanvas(
                    strokes = uiState.strokes,
                    selectedColor = uiState.selectedColor,
                    selectedStrokeWidth = uiState.selectedStrokeWidth,
                    onStrokeCompleted = { viewModel.addStroke(it) },
                    onCanvasSizeChanged = { canvasSize = it },
                    modifier = Modifier.fillMaxSize()
                )
            }

            SignatureToolbar(
                selectedColor = uiState.selectedColor,
                selectedStrokeWidth = uiState.selectedStrokeWidth,
                canUndo = uiState.strokes.isNotEmpty(),
                hasStrokes = uiState.strokes.isNotEmpty(),
                onColorSelected = { viewModel.setColor(it) },
                onStrokeWidthSelected = { viewModel.setStrokeWidth(it) },
                onUndoClick = { viewModel.undo() },
                onClearClick = { viewModel.clear() },
                onSaveClick = {
                    viewModel.createPreview(canvasSize.width, canvasSize.height)
                }
            )

            if (savedSignatures.isNotEmpty()) {
                Text(
                    text = "Tanda Tangan Tersimpan (${savedSignatures.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(savedSignatures, key = { it.absolutePath }) { file ->
                        SavedSignatureItem(
                            file = file,
                            onDownload = {
                                activeFileForExport = file
                                createPngLauncher.launch(file.name)
                            },
                            onShare = { viewModel.shareSignature(file) },
                            onDelete = { viewModel.deleteSignature(file) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.previewBitmap != null) {
        SignaturePreviewDialog(
            bitmap = uiState.previewBitmap!!,
            isSaving = uiState.isSaving,
            onConfirmSave = { customName ->
                viewModel.saveSignature(uiState.previewBitmap!!, customName) { savedFile ->
                    activeFileForExport = savedFile
                }
            },
            onDismiss = { viewModel.dismissPreview() }
        )
    }
}

@Composable
private fun SavedSignatureItem(
    file: File,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(180.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .drawBehind {
                        val checkSize = 12f
                        val numX = (size.width / checkSize).toInt() + 1
                        val numY = (size.height / checkSize).toInt() + 1
                        for (i in 0 until numX) {
                            for (j in 0 until numY) {
                                val color = if ((i + j) % 2 == 0) Color(0xFFF1F5F9) else Color(0xFFE2E8F0)
                                drawRect(
                                    color = color,
                                    topLeft = Offset(i * checkSize, j * checkSize),
                                    size = Size(checkSize, checkSize)
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = file,
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(6.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = file.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = FormatUtils.formatFileSize(file.length()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onShare, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Bagikan",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDownload, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Unduh",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

