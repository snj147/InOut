package com.personal.inout.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.personal.inout.BuildConfig
import com.personal.inout.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File, val versionTag: String) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

object AppUpdateEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

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

    suspend fun startStreamDownload(context: Context, downloadUrl: String, versionTag: String) = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(context.cacheDir, "InOut-alpha-$versionTag.apk")
            if (targetFile.exists()) targetFile.delete()

            val request = Request.Builder().url(downloadUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _downloadState.value = UpdateDownloadState.Error("HTTP Error: ${response.code}")
                    return@withContext
                }

                val responseBody = response.body ?: run {
                    _downloadState.value = UpdateDownloadState.Error("Empty download stream")
                    return@withContext
                }

                val totalBytes = responseBody.contentLength()
                var bytesCopied = 0L
                val buffer = ByteArray(8 * 1024)

                responseBody.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        var read = input.read(buffer)
                        while (read != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            val progress = if (totalBytes > 0) bytesCopied.toFloat() / totalBytes.toFloat() else 0f
                            _downloadState.value = UpdateDownloadState.Downloading(progress, bytesCopied, totalBytes)
                            read = input.read(buffer)
                        }
                    }
                }

                _downloadState.value = UpdateDownloadState.ReadyToInstall(targetFile, versionTag)
            }
        } catch (e: Exception) {
            _downloadState.value = UpdateDownloadState.Error("Download failed: ${e.localizedMessage}")
        }
    }

    fun triggerPackageInstaller(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val apkUri = FileProvider.getUriForFile(
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

    fun resetState() {
        _downloadState.value = UpdateDownloadState.Idle
    }
}

class UpdateRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)
        }
    }
}
