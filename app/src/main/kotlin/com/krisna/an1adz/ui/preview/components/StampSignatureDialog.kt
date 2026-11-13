package com.krisna.an1adz.ui.preview.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.krisna.an1adz.core.signature.SignatureAlignment
import java.io.File

@Composable
fun StampSignatureDialog(
    totalPages: Int,
    signatures: List<File>,
    onConfirm: (File, Int, SignatureAlignment) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFile by remember { mutableStateOf(signatures.firstOrNull()) }
    var selectedPage by remember { mutableIntStateOf(totalPages - 1) }
    var selectedAlignment by remember { mutableStateOf(SignatureAlignment.BOTTOM_RIGHT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Bubuhkan Tanda Tangan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (signatures.isEmpty()) {
                    Text(
                        text = "Belum ada tanda tangan yang disimpan. Silakan buat tanda tangan digital terlebih dahulu di menu utama.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "Pilih Tanda Tangan:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(signatures) { file ->
                            val isSelected = file == selectedFile
                            Box(
                                modifier = Modifier
                                    .size(width = 90.dp, height = 55.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .drawBehind {
                                        val checkSize = 10f
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
                                    }
                                    .clickable { selectedFile = file },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = file.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Halaman Dokumen:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(totalPages) { pageIdx ->
                            FilterChip(
                                selected = selectedPage == pageIdx,
                                onClick = { selectedPage = pageIdx },
                                label = { Text("Hal ${pageIdx + 1}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    Text(
                        text = "Posisi Tanda Tangan:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            SignatureAlignment.BOTTOM_LEFT to "Kiri",
                            SignatureAlignment.BOTTOM_CENTER to "Tengah",
                            SignatureAlignment.BOTTOM_RIGHT to "Kanan"
                        ).forEach { (align, label) ->
                            FilterChip(
                                selected = selectedAlignment == align,
                                onClick = { selectedAlignment = align },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedFile != null) {
                Button(
                    onClick = {
                        onConfirm(selectedFile!!, selectedPage, selectedAlignment)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Bubuhkan")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

