package com.krisna.an1adz.ui.office.docx

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.krisna.an1adz.core.office.docx.DocxAlignment
import com.krisna.an1adz.core.office.docx.DocxDocument
import com.krisna.an1adz.core.office.docx.DocxElement
import com.krisna.an1adz.core.office.docx.TableRow
import com.krisna.an1adz.core.office.docx.TextRun
import com.krisna.an1adz.ui.preview.PdfReadingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocxViewerScreen(
    viewModel: DocxViewerViewModel,
    onBackClick: () -> Unit,
    onNavigateToPdf: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DocxNavigationEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is DocxNavigationEvent.OpenPdfPreview -> {
                    onNavigateToPdf(event.filePath)
                }
            }
        }
    }

    val readingMode = (uiState as? DocxViewerUiState.Success)?.readingMode ?: PdfReadingMode.NORMAL
    val (backgroundColor, textColor, surfaceColor, borderColor) = when (readingMode) {
        PdfReadingMode.NORMAL -> QuadColor(
            background = Color(0xFFF8F9FA),
            text = Color(0xFF1E293B),
            surface = Color.White,
            border = Color(0xFFE2E8F0)
        )
        PdfReadingMode.SEPIA -> QuadColor(
            background = Color(0xFFFBF0D9),
            text = Color(0xFF5F4B32),
            surface = Color(0xFFF4E4C1),
            border = Color(0xFFE0CFA7)
        )
        PdfReadingMode.NIGHT -> QuadColor(
            background = Color(0xFF121212),
            text = Color(0xFFE2E8F0),
            surface = Color(0xFF1E1E1E),
            border = Color(0xFF334155)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when (val state = uiState) {
                        is DocxViewerUiState.Success -> state.document.title
                        else -> "Dokumen Word"
                    }
                    Text(
                        text = titleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
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
                    if (uiState is DocxViewerUiState.Success) {
                        val successState = uiState as DocxViewerUiState.Success
                        IconButton(
                            onClick = { viewModel.decreaseFontScale() },
                            enabled = successState.fontScale > 0.75f
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = "Perkecil Huruf"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.increaseFontScale() },
                            enabled = successState.fontScale < 1.75f
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Perbesar Huruf"
                            )
                        }
                        IconButton(
                            onClick = {
                                val nextMode = when (successState.readingMode) {
                                    PdfReadingMode.NORMAL -> PdfReadingMode.SEPIA
                                    PdfReadingMode.SEPIA -> PdfReadingMode.NIGHT
                                    PdfReadingMode.NIGHT -> PdfReadingMode.NORMAL
                                }
                                viewModel.setReadingMode(nextMode)
                            }
                        ) {
                            val modeIcon = when (successState.readingMode) {
                                PdfReadingMode.NORMAL -> Icons.AutoMirrored.Filled.MenuBook
                                PdfReadingMode.SEPIA -> Icons.Default.LightMode
                                PdfReadingMode.NIGHT -> Icons.Default.DarkMode
                            }
                            Icon(
                                imageVector = modeIcon,
                                contentDescription = "Ganti Tema Baca"
                            )
                        }
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari Teks"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.exportToPdf() },
                            enabled = !successState.isExportingPdf
                        ) {
                            if (successState.isExportingPdf) {
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
                    containerColor = surfaceColor,
                    titleContentColor = textColor,
                    navigationIconContentColor = textColor,
                    actionIconContentColor = textColor
                )
            )
        },
        containerColor = backgroundColor
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is DocxViewerUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Membaca dan memproses dokumen Word...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }
                is DocxViewerUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Gagal Membuka Dokumen",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        FilledTonalButton(onClick = { viewModel.loadDocument() }) {
                            Text("Coba Lagi")
                        }
                    }
                }
                is DocxViewerUiState.Success -> {
                    DocxContent(
                        document = state.document,
                        fontScale = state.fontScale,
                        searchQuery = state.searchQuery,
                        isSearching = state.isSearching,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onCloseSearch = { viewModel.toggleSearch() },
                        textColor = textColor,
                        surfaceColor = surfaceColor,
                        borderColor = borderColor
                    )
                }
            }
        }
    }
}

@Composable
private fun DocxContent(
    document: DocxDocument,
    fontScale: Float,
    searchQuery: String,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    textColor: Color,
    surfaceColor: Color,
    borderColor: Color
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (isSearching) {
            Surface(
                color = surfaceColor,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Cari kata dalam dokumen...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Hapus")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = borderColor
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onCloseSearch) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup Pencarian")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                DocumentStatsHeader(
                    document = document,
                    surfaceColor = surfaceColor,
                    textColor = textColor,
                    borderColor = borderColor
                )
            }

            items(document.elements) { element ->
                when (element) {
                    is DocxElement.Heading -> {
                        HeadingView(
                            element = element,
                            fontScale = fontScale,
                            searchQuery = searchQuery,
                            textColor = textColor
                        )
                    }
                    is DocxElement.Paragraph -> {
                        ParagraphView(
                            element = element,
                            fontScale = fontScale,
                            searchQuery = searchQuery,
                            textColor = textColor
                        )
                    }
                    is DocxElement.Table -> {
                        TableView(
                            rows = element.rows,
                            fontScale = fontScale,
                            textColor = textColor,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor
                        )
                    }
                    is DocxElement.Image -> {
                        ImageView(element = element)
                    }
                    is DocxElement.Divider -> {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(borderColor)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DocumentStatsHeader(
    document: DocxDocument,
    surfaceColor: Color,
    textColor: Color,
    borderColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatBadge(label = "Kata", value = "${document.wordCount}", textColor = textColor)
                StatBadge(label = "Paragraf", value = "${document.paragraphCount}", textColor = textColor)
                StatBadge(label = "Karakter", value = "${document.charCount}", textColor = textColor)
            }
        }
    }
}

@Composable
private fun StatBadge(
    label: String,
    value: String,
    textColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelMedium,
            color = textColor.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun HeadingView(
    element: DocxElement.Heading,
    fontScale: Float,
    searchQuery: String,
    textColor: Color
) {
    val (baseSize, weight) = when (element.level) {
        1 -> Pair(22.sp, FontWeight.Bold)
        2 -> Pair(19.sp, FontWeight.Bold)
        3 -> Pair(17.sp, FontWeight.SemiBold)
        else -> Pair(15.sp, FontWeight.SemiBold)
    }

    val annotated = buildAnnotatedStringWithSearch(
        text = element.text,
        baseColor = textColor,
        searchQuery = searchQuery
    )

    Text(
        text = annotated,
        fontSize = (baseSize.value * fontScale).sp,
        fontWeight = weight,
        color = textColor,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun ParagraphView(
    element: DocxElement.Paragraph,
    fontScale: Float,
    searchQuery: String,
    textColor: Color
) {
    val annotated = buildAnnotatedString {
        if (element.isBullet) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                append("•  ")
            }
        }

        element.runs.forEach { run ->
            appendFormattedRun(run = run, fallbackColor = textColor, searchQuery = searchQuery)
        }
    }

    val textAlign = when (element.alignment) {
        DocxAlignment.CENTER -> TextAlign.Center
        DocxAlignment.RIGHT -> TextAlign.End
        DocxAlignment.JUSTIFY -> TextAlign.Justify
        else -> TextAlign.Start
    }

    Text(
        text = annotated,
        fontSize = (15.sp.value * fontScale).sp,
        lineHeight = (22.sp.value * fontScale).sp,
        textAlign = textAlign,
        color = textColor,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun AnnotatedString.Builder.appendFormattedRun(
    run: TextRun,
    fallbackColor: Color,
    searchQuery: String
) {
    val customColor = run.colorHex?.let {
        try {
            Color(android.graphics.Color.parseColor("#${it.removePrefix("#")}"))
        } catch (_: Exception) {
            fallbackColor
        }
    } ?: fallbackColor

    val weight = if (run.isBold) FontWeight.Bold else FontWeight.Normal
    val style = if (run.isItalic) FontStyle.Italic else FontStyle.Normal
    val decoration = when {
        run.isUnderline && run.isStrike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
        run.isUnderline -> TextDecoration.Underline
        run.isStrike -> TextDecoration.LineThrough
        else -> TextDecoration.None
    }

    val runText = run.text
    if (searchQuery.isNotBlank() && runText.contains(searchQuery, ignoreCase = true)) {
        var startIndex = 0
        val lowerText = runText.lowercase()
        val lowerQuery = searchQuery.lowercase()

        while (startIndex < runText.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
            if (matchIndex == -1) {
                withStyle(SpanStyle(fontWeight = weight, fontStyle = style, textDecoration = decoration, color = customColor)) {
                    append(runText.substring(startIndex))
                }
                break
            }

            if (matchIndex > startIndex) {
                withStyle(SpanStyle(fontWeight = weight, fontStyle = style, textDecoration = decoration, color = customColor)) {
                    append(runText.substring(startIndex, matchIndex))
                }
            }

            val matchEnd = matchIndex + searchQuery.length
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontStyle = style,
                    textDecoration = decoration,
                    color = Color.Black,
                    background = Color(0xFFFFEB3B)
                )
            ) {
                append(runText.substring(matchIndex, matchEnd))
            }
            startIndex = matchEnd
        }
    } else {
        withStyle(
            SpanStyle(
                fontWeight = weight,
                fontStyle = style,
                textDecoration = decoration,
                color = customColor
            )
        ) {
            append(runText)
        }
    }
}

private fun buildAnnotatedStringWithSearch(
    text: String,
    baseColor: Color,
    searchQuery: String
): AnnotatedString {
    return buildAnnotatedString {
        if (searchQuery.isNotBlank() && text.contains(searchQuery, ignoreCase = true)) {
            var startIndex = 0
            val lowerText = text.lowercase()
            val lowerQuery = searchQuery.lowercase()

            while (startIndex < text.length) {
                val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
                if (matchIndex == -1) {
                    withStyle(SpanStyle(color = baseColor)) {
                        append(text.substring(startIndex))
                    }
                    break
                }

                if (matchIndex > startIndex) {
                    withStyle(SpanStyle(color = baseColor)) {
                        append(text.substring(startIndex, matchIndex))
                    }
                }

                val matchEnd = matchIndex + searchQuery.length
                withStyle(
                    SpanStyle(
                        color = Color.Black,
                        background = Color(0xFFFFEB3B),
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(text.substring(matchIndex, matchEnd))
                }
                startIndex = matchEnd
            }
        } else {
            withStyle(SpanStyle(color = baseColor)) {
                append(text)
            }
        }
    }
}

@Composable
private fun TableView(
    rows: List<TableRow>,
    fontScale: Float,
    textColor: Color,
    surfaceColor: Color,
    borderColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(4.dp)
        ) {
            rows.forEachIndexed { rowIndex, row ->
                val isHeader = rowIndex == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isHeader) borderColor.copy(alpha = 0.3f) else Color.Transparent)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.cells.forEach { cell ->
                        Text(
                            text = cell.text,
                            fontSize = (13.sp.value * fontScale).sp,
                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                            color = textColor,
                            modifier = Modifier
                                .width(140.dp)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
                if (rowIndex < rows.size - 1) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(borderColor.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageView(element: DocxElement.Image) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = element.bytes,
            contentDescription = element.description,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}

private data class QuadColor(
    val background: Color,
    val text: Color,
    val surface: Color,
    val border: Color
)
