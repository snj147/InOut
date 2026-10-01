package com.personal.inout.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.personal.inout.BuildConfig
import com.personal.inout.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

sealed class UpdateDownloadStatus {
    object Idle : UpdateDownloadStatus()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadStatus()
    object Staging : UpdateDownloadStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadStatus()
    data class Failed(val error: String) : UpdateDownloadStatus()
}

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)
        }
    }
}

object AppUpdateEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _downloadStatus = MutableStateFlow<UpdateDownloadStatus>(UpdateDownloadStatus.Idle)
    val downloadStatus = _downloadStatus.asStateFlow()

    suspend fun checkForUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/snj147/InOut/releases/tags/rolling-alpha")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Release not found (HTTP ${response.code})"))
                }
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val bodyText = json.optString("body", "")

                val shaRegex = """(?:commit|build)\s+[`']?([a-f0-9]{7,40})[`']?""".toRegex(RegexOption.IGNORE_CASE)
                val match = shaRegex.find(bodyText)
                val remoteSha = match?.groupValues?.get(1) ?: json.optString("target_commitish", "")

                val currentSha = BuildConfig.GIT_SHA
                val isNewer = remoteSha.isNotBlank() && !currentSha.startsWith(remoteSha.take(7)) && !remoteSha.startsWith(currentSha.take(7))

                var apkDownloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                Result.success(
                    UpdateInfo(
                        hasUpdate = isNewer && apkDownloadUrl.isNotBlank(),
                        latestVersion = remoteSha.take(7),
                        downloadUrl = apkDownloadUrl,
                        releaseNotes = bodyText.ifBlank { "New Alpha build ready for installation." }
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun streamDownloadApk(context: Context, downloadUrl: String, versionTag: String) = withContext(Dispatchers.IO) {
        val cacheApk = File(context.cacheDir, "InOut-alpha-$versionTag.apk")
        if (cacheApk.exists()) cacheApk.delete()

        try {
            val request = Request.Builder().url(downloadUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _downloadStatus.value = UpdateDownloadStatus.Failed("Download failed: HTTP ${response.code}")
                    return@withContext
                }

                val responseBody = response.body ?: run {
                    _downloadStatus.value = UpdateDownloadStatus.Failed("Empty response body")
                    return@withContext
                }

                val totalLength = responseBody.contentLength()
                var downloaded = 0L

                responseBody.byteStream().use { input ->
                    FileOutputStream(cacheApk).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            val progress = if (totalLength > 0) downloaded.toFloat() / totalLength.toFloat() else 0f
                            _downloadStatus.value = UpdateDownloadStatus.Downloading(progress, downloaded, totalLength)
                        }
                        output.flush()
                    }
                }

                _downloadStatus.value = UpdateDownloadStatus.Staging
                _downloadStatus.value = UpdateDownloadStatus.ReadyToInstall(cacheApk)
            }
        } catch (e: Exception) {
            _downloadStatus.value = UpdateDownloadStatus.Failed(e.localizedMessage ?: "Network error during download")
        }
    }

    fun promptInstaller(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }

    fun resetStatus() {
        _downloadStatus.value = UpdateDownloadStatus.Idle
    }
}
