package com.krisna.an1adz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.krisna.an1adz.ui.theme.AN1AdzTheme
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialNavKey: NavKey = extractNavKeyFromIntent(intent) ?: Main

        enableEdgeToEdge()
        setContent {
            AN1AdzTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(initialNavKey = initialNavKey)
                }
            }
        }
    }

    private fun extractNavKeyFromIntent(intent: Intent?): NavKey? {
        if (intent == null) return null
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }

        if (uri != null) {
            val type = intent.type ?: contentResolver.getType(uri) ?: ""
            val uriString = uri.toString().lowercase()
            val isDocx = type.contains("word") || type.contains("msword") || uriString.endsWith(".docx") || uriString.endsWith(".doc")

            val openedDir = File(cacheDir, "opened_files").apply {
                if (!exists()) {
                    mkdirs()
                }
            }
            val extension = if (isDocx) "docx" else "pdf"
            val targetFile = File(openedDir, "dokumen_${System.currentTimeMillis()}.$extension")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    return if (isDocx) {
                        DocxViewerNavKey(targetFile.absolutePath)
                    } else {
                        PdfPreviewNavKey(targetFile.absolutePath)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }
}
