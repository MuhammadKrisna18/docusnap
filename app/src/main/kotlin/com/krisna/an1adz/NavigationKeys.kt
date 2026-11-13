package com.krisna.an1adz

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Main : NavKey

@Serializable
data class PdfPreviewNavKey(val filePath: String) : NavKey

@Serializable
data object SignatureNavKey : NavKey

@Serializable
data class DocxViewerNavKey(val filePath: String) : NavKey
