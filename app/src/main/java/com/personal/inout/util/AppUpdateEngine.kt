package com.personal.inout.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.personal.inout.BuildConfig
import com.personal.inout.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

object AppUpdateEngine {

    val downloadProgress = MutableStateFlow(-1f) // -1f = Idle, 0.0..1.0 = Downloading, 2.0 = Staging

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

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

    suspend fun startDownloadAndInstall(context: Context, downloadUrl: String, versionTag: String) = withContext(Dispatchers.IO) {
        downloadProgress.value = 0f
        try {
            val request = Request.Builder().url(downloadUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    downloadProgress.value = -1f
                    return@withContext
                }

                val body = response.body ?: run {
                    downloadProgress.value = -1f
                    return@withContext
                }

                val contentLength = body.contentLength()
                val source = body.source()

                val fileName = "InOut-update-$versionTag.apk"
                val file = File(context.cacheDir, fileName)
                val sink = FileOutputStream(file)

                var bytesRead: Long = 0
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = source.read(buffer)
                    if (read == -1) break
                    sink.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        downloadProgress.value = (bytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 0.99f)
                    }
                }
                sink.flush()
                sink.close()

                downloadProgress.value = 2f
                withContext(Dispatchers.Main) {
                    triggerPackageInstaller(context, file)
                    delay(3000)
                    downloadProgress.value = -1f
                }
            }
        } catch (e: Exception) {
            downloadProgress.value = -1f
        }
    }

    private fun triggerPackageInstaller(context: Context, apkFile: File) {
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
