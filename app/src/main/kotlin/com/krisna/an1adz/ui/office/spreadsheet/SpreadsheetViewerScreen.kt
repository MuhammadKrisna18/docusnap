package com.krisna.an1adz.ui.office.spreadsheet

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krisna.an1adz.core.office.spreadsheet.SpreadsheetCell
import com.krisna.an1adz.core.office.spreadsheet.SpreadsheetRow
import com.krisna.an1adz.core.office.spreadsheet.SpreadsheetSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadsheetViewerScreen(
    viewModel: SpreadsheetViewerViewModel,
    onBackClick: () -> Unit,
    onNavigateToPdf: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SpreadsheetNavigationEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is SpreadsheetNavigationEvent.OpenPdfPreview -> {
                    onNavigateToPdf(event.filePath)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when (val state = uiState) {
                        is SpreadsheetViewerUiState.Success -> state.document.title
                        else -> "Spreadsheet Excel"
                    }
                    val subtitleText = when (val state = uiState) {
                        is SpreadsheetViewerUiState.Success -> {
                            val activeSheet = state.document.sheets.getOrNull(state.currentSheetIndex)
                            if (activeSheet != null) {
                                "${activeSheet.rowCount} Baris • ${activeSheet.maxColumns} Kolom"
                            } else ""
                        }
                        else -> ""
                    }
                    Column {
                        Text(
                            text = titleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (subtitleText.isNotEmpty()) {
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
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
                    if (uiState is SpreadsheetViewerUiState.Success) {
                        val success = uiState as SpreadsheetViewerUiState.Success
                        IconButton(
                            onClick = { viewModel.decreaseFontScale() },
                            enabled = success.fontScale > 0.8f
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = "Perkecil Huruf"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.increaseFontScale() },
                            enabled = success.fontScale < 1.75f
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Perbesar Huruf"
                            )
                        }
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari Sel"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.exportToPdf() },
                            enabled = !success.isExportingPdf
                        ) {
                            if (success.isExportingPdf) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "Ekspor ke PDF",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is SpreadsheetViewerUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Membaca dan memproses lembar spreadsheet...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is SpreadsheetViewerUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Gagal Membuka Spreadsheet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        FilledTonalButton(onClick = { viewModel.loadSpreadsheet() }) {
                            Text("Coba Lagi")
                        }
                    }
                }
                is SpreadsheetViewerUiState.Success -> {
                    SpreadsheetContent(
                        state = state,
                        onSelectSheet = { viewModel.selectSheet(it) },
                        onSelectCell = { viewModel.selectCell(it) },
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onCloseSearch = { viewModel.toggleSearch() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpreadsheetContent(
    state: SpreadsheetViewerUiState.Success,
    onSelectSheet: (Int) -> Unit,
    onSelectCell: (SpreadsheetCell?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit
) {
    val activeSheet = state.document.sheets.getOrNull(state.currentSheetIndex) ?: state.document.sheets.firstOrNull()

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.isSearching) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Cari nilai atau teks dalam sel...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Hapus")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onCloseSearch) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }
            }
        }

        if (state.document.sheets.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = state.currentSheetIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                state.document.sheets.forEachIndexed { index, sheet ->
                    Tab(
                        selected = state.currentSheetIndex == index,
                        onClick = { onSelectSheet(index) },
                        text = {
                            Text(
                                text = sheet.name,
                                fontWeight = if (state.currentSheetIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }

        CellInspectionBar(selectedCell = state.selectedCell)

        if (activeSheet != null) {
            SpreadsheetTableGrid(
                sheet = activeSheet,
                fontScale = state.fontScale,
                searchQuery = state.searchQuery,
                selectedCell = state.selectedCell,
                onCellClick = onSelectCell
            )
        }
    }
}

@Composable
private fun CellInspectionBar(selectedCell: SpreadsheetCell?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = selectedCell?.cellRef ?: "Sel",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = selectedCell?.value ?: "Ketuk salah satu sel untuk melihat isi lengkap",
                style = MaterialTheme.typography.bodySmall,
                color = if (selectedCell != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SpreadsheetTableGrid(
    sheet: SpreadsheetSheet,
    fontScale: Float,
    searchQuery: String,
    selectedCell: SpreadsheetCell?,
    onCellClick: (SpreadsheetCell) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val cellWidth = (110.dp.value * fontScale).dp.coerceIn(80.dp, 200.dp)
    val rowHeight = (36.dp.value * fontScale).dp.coerceIn(30.dp, 60.dp)
    val rowHeaderWidth = 38.dp
    val totalCols = sheet.maxColumns.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScrollState)
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                modifier = Modifier
                    .width(rowHeaderWidth)
                    .height(rowHeight)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            for (c in 0 until totalCols) {
                val letter = getColumnLetter(c)
                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .height(rowHeight)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(sheet.rows) { row ->
                val isAlt = row.rowIndex % 2 == 0
                val rowBg = if (isAlt) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                val cellMap = row.cells.associateBy { it.colIndex }

                Row(
                    modifier = Modifier
                        .background(rowBg)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .width(rowHeaderWidth)
                            .height(rowHeight)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${row.rowIndex}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    for (c in 0 until totalCols) {
                        val cell = cellMap[c]
                        val cellRef = "${getColumnLetter(c)}${row.rowIndex}"
                        val isSelected = selectedCell?.cellRef == cellRef
                        val isSearchMatch = searchQuery.isNotBlank() && cell?.value?.contains(searchQuery, ignoreCase = true) == true

                        val cellBg = when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            isSearchMatch -> Color(0xFFFFF9C4)
                            cell?.isHeader == true -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            else -> Color.Transparent
                        }

                        val cellBorder = if (isSelected) {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }

                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .height(rowHeight)
                                .background(cellBg)
                                .then(cellBorder)
                                .clickable {
                                    val clicked = cell ?: SpreadsheetCell(
                                        colIndex = c,
                                        cellRef = cellRef,
                                        value = ""
                                    )
                                    onCellClick(clicked)
                                }
                                .padding(horizontal = 6.dp),
                            contentAlignment = if (cell?.isNumeric == true) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            if (cell != null && cell.value.isNotEmpty()) {
                                Text(
                                    text = cell.value,
                                    fontSize = (12.sp.value * fontScale).sp,
                                    fontWeight = if (cell.isHeader) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSearchMatch) Color.Black else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = if (cell.isNumeric) TextAlign.End else TextAlign.Start
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getColumnLetter(colIndex: Int): String {
    var n = colIndex
    var result = ""
    while (n >= 0) {
        result = ('A'.code + (n % 26)).toChar() + result
        n = (n / 26) - 1
    }
    return result
}
