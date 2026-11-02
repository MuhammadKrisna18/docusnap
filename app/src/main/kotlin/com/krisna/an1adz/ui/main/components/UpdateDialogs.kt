package com.krisna.an1adz.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krisna.an1adz.core.update.AppUpdateInfo
import com.krisna.an1adz.core.util.FormatUtils
import com.krisna.an1adz.ui.main.UpdateUiState
import java.io.File

@Composable
fun UpdateDialogs(
    updateState: UpdateUiState,
    onDismiss: () -> Unit,
    onUpdateClick: (AppUpdateInfo) -> Unit,
    onInstallClick: (File) -> Unit
) {
    when (updateState) {
        is UpdateUiState.Checking -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("Memeriksa Pembaruan", fontWeight = FontWeight.Bold) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Mengecek versi rilis terbaru di GitHub...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            )
        }
        is UpdateUiState.UpdateAvailable -> {
            val info = updateState.info
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text("Pembaruan Tersedia!", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "DocuSnap ${info.latestVersion} telah tersedia (versi Anda saat ini: v${info.currentVersion}).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (info.releaseNotes.isNotBlank()) {
                            Text(
                                text = "Catatan Pembaruan:\n${info.releaseNotes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onUpdateClick(info) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Perbarui Sekarang")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Nanti")
                    }
                }
            )
        }
        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("Mengunduh Pembaruan...", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (updateState.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { updateState.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                            val progressPercent = (updateState.progress * 100).toInt()
                            val downloadedStr = FormatUtils.formatFileSize(updateState.downloadedBytes)
                            val totalStr = FormatUtils.formatFileSize(updateState.totalBytes)
                            Text(
                                text = "$progressPercent% ($downloadedStr / $totalStr)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = FormatUtils.formatFileSize(updateState.downloadedBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
        is UpdateUiState.DownloadCompleted -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Unduhan Selesai", fontWeight = FontWeight.Bold) },
                text = { Text("Pembaruan siap dipasang di perangkat Anda.") },
                confirmButton = {
                    Button(
                        onClick = { onInstallClick(updateState.apkFile) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Pasang Sekarang")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Tutup")
                    }
                }
            )
        }
        is UpdateUiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Pembaruan Gagal", fontWeight = FontWeight.Bold) },
                text = { Text(updateState.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Tutup")
                    }
                }
            )
        }
        is UpdateUiState.Idle -> Unit
    }
}
