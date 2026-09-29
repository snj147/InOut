package com.personal.inout.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.personal.inout.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object AppUpdateEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
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

    fun startDownloadAndInstall(context: Context, downloadUrl: String, versionTag: String) {
        val fileName = "InOut-update-$versionTag.apk"
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Downloading InOut $versionTag")
            .setDescription("Preparing Alpha build installer...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destinationFile))
            .setMimeType("application/vnd.android.package-archive")

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {}

                    triggerPackageInstaller(context, destinationFile)
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(onComplete, filter)
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
}
