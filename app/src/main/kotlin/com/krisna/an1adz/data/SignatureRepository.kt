package com.krisna.an1adz.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.krisna.an1adz.core.signature.DefaultSignatureExporter
import com.krisna.an1adz.core.signature.SignatureExporter
import com.krisna.an1adz.core.signature.SignatureStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface SignatureRepository {
    val savedSignatures: Flow<List<File>>
    suspend fun refreshSignatures()
    fun renderSignature(strokes: List<SignatureStroke>, width: Int, height: Int): Bitmap?
    suspend fun saveSignature(bitmap: Bitmap, customName: String? = null): Result<File>
    suspend fun exportSignatureToUri(sourceFile: File, targetUri: Uri): Result<Unit>
    fun shareSignature(file: File)
    suspend fun deleteSignature(file: File): Boolean
}

class DefaultSignatureRepository(
    private val context: Context,
    private val signatureExporter: SignatureExporter = DefaultSignatureExporter(context)
) : SignatureRepository {

    private val signaturesDir: File
        get() = File(context.filesDir, "signatures").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    private val _savedSignatures = MutableStateFlow<List<File>>(emptyList())
    override val savedSignatures: Flow<List<File>> = _savedSignatures.asStateFlow()

    init {
        _savedSignatures.value = getSignaturesOnDisk()
    }

    private fun getSignaturesOnDisk(): List<File> {
        return signaturesDir.listFiles { file ->
            file.isFile && file.extension.lowercase() == "png"
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    override suspend fun refreshSignatures() {
        withContext(Dispatchers.IO) {
            _savedSignatures.value = getSignaturesOnDisk()
        }
    }

    override fun renderSignature(
        strokes: List<SignatureStroke>,
        width: Int,
        height: Int
    ): Bitmap? = signatureExporter.renderToBitmap(strokes, width, height)

    override suspend fun saveSignature(bitmap: Bitmap, customName: String?): Result<File> {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val name = customName?.takeIf { it.isNotBlank() } ?: "ttd_$timeStamp"
        val targetFile = File(signaturesDir, "$name.png")
        return signatureExporter.saveSignaturePng(bitmap, targetFile).also {
            refreshSignatures()
        }
    }

    override suspend fun exportSignatureToUri(sourceFile: File, targetUri: Uri): Result<Unit> =
        signatureExporter.saveSignatureToUri(sourceFile, targetUri)

    override fun shareSignature(file: File) = signatureExporter.shareSignature(file)

    override suspend fun deleteSignature(file: File): Boolean =
        withContext(Dispatchers.IO) {
            val deleted = file.delete()
            if (deleted) {
                refreshSignatures()
            }
            deleted
        }
}

