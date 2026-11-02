package com.krisna.an1adz.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

interface AppUpdateService {
    suspend fun checkForUpdate(currentVersion: String): Result<AppUpdateInfo>
    suspend fun downloadApk(
        downloadUrl: String,
        destinationFile: File,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File>
    fun installApk(context: Context, apkFile: File): Result<Unit>
}

class DefaultAppUpdateService(
    private val owner: String = "MuhammadKrisna18",
    private val repo: String = "docusnap"
) : AppUpdateService {

    override suspend fun checkForUpdate(currentVersion: String): Result<AppUpdateInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
                val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "DocuSnap-App")
                }

                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("HTTP ${connection.responseCode}: ${connection.responseMessage}")
                }

                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseBody)
                val tagName = json.optString("tag_name", "")
                val releaseName = json.optString("name", tagName)
                val releaseNotes = json.optString("body", "")

                val assets = json.optJSONArray("assets")
                var apkUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                val isNewer = isVersionNewer(tagName, currentVersion)
                AppUpdateInfo(
                    currentVersion = currentVersion,
                    latestVersion = tagName,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes,
                    downloadUrl = apkUrl,
                    isUpdateAvailable = isNewer && apkUrl.isNotBlank()
                )
            }
        }

    override suspend fun downloadApk(
        downloadUrl: String,
        destinationFile: File,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirectCount = 0

            while (true) {
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "DocuSnap-App")
                }

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308
                ) {
                    val location = connection.getHeaderField("Location")
                    if (location != null && redirectCount < 5) {
                        currentUrl = location
                        redirectCount++
                        continue
                    }
                }
                break
            }

            destinationFile.parentFile?.mkdirs()
            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytes = input.read(buffer)
                    while (bytes != -1) {
                        output.write(buffer, 0, bytes)
                        downloadedBytes += bytes
                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else -1f
                        onProgress(progress, downloadedBytes, totalBytes)
                        bytes = input.read(buffer)
                    }
                }
            }

            destinationFile
        }
    }

    override fun installApk(context: Context, apkFile: File): Result<Unit> = runCatching {
        if (!apkFile.exists()) {
            throw IllegalStateException("File APK tidak ditemukan")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return@runCatching
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        val cleanLatest = latest.trimStart('v', 'V').trim()
        val cleanCurrent = current.trimStart('v', 'V').trim()
        if (cleanLatest.isBlank() || cleanLatest == cleanCurrent) return false

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(latestParts.size, currentParts.size)

        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
