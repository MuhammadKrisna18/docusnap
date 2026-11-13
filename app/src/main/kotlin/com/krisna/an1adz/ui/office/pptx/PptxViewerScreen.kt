package com.krisna.an1adz.ui.office.pptx

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.krisna.an1adz.core.office.docx.TextRun
import com.krisna.an1adz.core.office.pptx.PptxElement
import com.krisna.an1adz.core.office.pptx.PptxSlide

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PptxViewerScreen(
    viewModel: PptxViewerViewModel,
    onBackClick: () -> Unit,
    onNavigateToPdf: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PptxNavigationEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is PptxNavigationEvent.OpenPdfPreview -> {
                    onNavigateToPdf(event.filePath)
                }
            }
        }
    }

    when (val state = uiState) {
        is PptxViewerUiState.Loading -> {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Membaca dan memproses slide presentasi...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        is PptxViewerUiState.Error -> {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Gagal Membuka Presentasi",
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
                        FilledTonalButton(onClick = { viewModel.loadPresentation() }) {
                            Text("Coba Lagi")
                        }
                    }
                }
            }
        }

        is PptxViewerUiState.Success -> {
            if (state.isPresenterMode) {
                PresenterModeView(
                    slide = state.presentation.slides.getOrNull(state.currentSlideIndex) ?: state.presentation.slides.first(),
                    slideIndex = state.currentSlideIndex,
                    totalSlides = state.presentation.totalSlides,
                    onNext = { viewModel.nextSlide() },
                    onPrev = { viewModel.previousSlide() },
                    onExit = { viewModel.togglePresenterMode() }
                )
            } else {
                NormalModeView(
                    state = state,
                    onBackClick = onBackClick,
                    onNext = { viewModel.nextSlide() },
                    onPrev = { viewModel.previousSlide() },
                    onGoToSlide = { viewModel.goToSlide(it) },
                    onTogglePresenter = { viewModel.togglePresenterMode() },
                    onToggleOverview = { viewModel.toggleOverview() },
                    onExportPdf = { viewModel.exportToPdf() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalModeView(
    state: PptxViewerUiState.Success,
    onBackClick: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onGoToSlide: (Int) -> Unit,
    onTogglePresenter: () -> Unit,
    onToggleOverview: () -> Unit,
    onExportPdf: () -> Unit
) {
    val currentSlide = state.presentation.slides.getOrNull(state.currentSlideIndex) ?: state.presentation.slides.first()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.presentation.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Slide ${state.currentSlideIndex + 1} dari ${state.presentation.totalSlides}",
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
                actions = {
                    IconButton(onClick = onToggleOverview) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Daftar Slide"
                        )
                    }
                    IconButton(onClick = onTogglePresenter) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Mode Presentasi",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onExportPdf,
                        enabled = !state.isExportingPdf
                    ) {
                        if (state.isExportingPdf) {
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                SlideCard(
                    slide = currentSlide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SlideControllerBar(
                currentIndex = state.currentSlideIndex,
                totalSlides = state.presentation.totalSlides,
                onPrev = onPrev,
                onNext = onNext,
                onSlideSeek = onGoToSlide
            )
        }

        if (state.isOverviewVisible) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = onToggleOverview,
                sheetState = sheetState
            ) {
                SlideOverviewGrid(
                    slides = state.presentation.slides,
                    currentIndex = state.currentSlideIndex,
                    onSelectSlide = onGoToSlide
                )
            }
        }
    }
}

@Composable
private fun SlideCard(
    slide: PptxSlide,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false
) {
    val cardBg = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val borderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)

    Card(
        modifier = modifier
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(20.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = slide.title ?: "Slide ${slide.slideNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "${slide.slideNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(slide.elements) { element ->
                    when (element) {
                        is PptxElement.Title -> Unit
                        is PptxElement.Subtitle -> {
                            Text(
                                text = element.text,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor.copy(alpha = 0.8f)
                            )
                        }
                        is PptxElement.Paragraph -> {
                            val annotated = buildAnnotatedString {
                                if (element.isBullet) {
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        append("•  ")
                                    }
                                }
                                element.runs.forEach { run ->
                                    appendRun(run = run, defaultColor = textColor)
                                }
                            }
                            Text(
                                text = annotated,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor
                            )
                        }
                        is PptxElement.Image -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = element.bytes,
                                    contentDescription = element.description,
                                    modifier = Modifier
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendRun(
    run: TextRun,
    defaultColor: Color
) {
    val weight = if (run.isBold) FontWeight.Bold else FontWeight.Normal
    val style = if (run.isItalic) FontStyle.Italic else FontStyle.Normal
    val decoration = when {
        run.isUnderline && run.isStrike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
        run.isUnderline -> TextDecoration.Underline
        run.isStrike -> TextDecoration.LineThrough
        else -> TextDecoration.None
    }
    val customColor = run.colorHex?.let {
        try {
            Color(android.graphics.Color.parseColor("#${it.removePrefix("#")}"))
        } catch (_: Exception) {
            defaultColor
        }
    } ?: defaultColor

    withStyle(
        SpanStyle(
            fontWeight = weight,
            fontStyle = style,
            textDecoration = decoration,
            color = customColor
        )
    ) {
        append(run.text)
    }
}

@Composable
private fun SlideControllerBar(
    currentIndex: Int,
    totalSlides: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSlideSeek: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onPrev,
                    enabled = currentIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Slide Sebelumnya"
                    )
                }

                Text(
                    text = "${currentIndex + 1} / $totalSlides",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FilledTonalIconButton(
                    onClick = onNext,
                    enabled = currentIndex < totalSlides - 1
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Slide Selanjutnya"
                    )
                }
            }

            if (totalSlides > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { onSlideSeek(it.toInt()) },
                    valueRange = 0f..(totalSlides - 1).toFloat(),
                    steps = (totalSlides - 2).coerceAtLeast(0)
                )
            }
        }
    }
}

@Composable
private fun PresenterModeView(
    slide: PptxSlide,
    slideIndex: Int,
    totalSlides: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onExit: () -> Unit
) {
    var dragOffsetX = 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffsetX < -50f) {
                            onNext()
                        } else if (dragOffsetX > 50f) {
                            onPrev()
                        }
                        dragOffsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffsetX += dragAmount
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            SlideCard(
                slide = slide,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                isDarkTheme = true
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onPrev() }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onNext() }
            )
        }

        IconButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Keluar Presentasi",
                tint = Color.White
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "Slide ${slideIndex + 1} / $totalSlides",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SlideOverviewGrid(
    slides: List<PptxSlide>,
    currentIndex: Int,
    onSelectSlide: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Daftar Slide Presentasi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
        ) {
            itemsIndexed(slides) { index, slide ->
                val isSelected = index == currentIndex
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectSlide(index) },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = slide.title ?: "Slide ${slide.slideNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Slide ${slide.slideNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
