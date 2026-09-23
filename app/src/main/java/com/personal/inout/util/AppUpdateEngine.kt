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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val hasUpdate: Boolean
)

object AppUpdateEngine {

    private const val GITHUB_OWNER = "snj147"
    private const val GITHUB_REPO = "InOut"
    private const val ROLLING_TAG = "alpha-latest"

    suspend fun checkForUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val installedAppLastUpdate = pkgInfo.lastUpdateTime

            val url = URL("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/tags/$ROLLING_TAG")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "InOut-App")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val code = conn.responseCode
            if (code == 404) {
                return@runCatching UpdateInfo(
                    latestVersion = ROLLING_TAG,
                    downloadUrl = "",
                    releaseNotes = "No release tagged '$ROLLING_TAG' found on GitHub.",
                    hasUpdate = false
                )
            }

            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                error("GitHub API status $code: $err")
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)
            val body = json.optString("body", "Automated alpha build") ?: "Automated alpha build"
            val assets = json.optJSONArray("assets")

            var downloadUrl = ""
            var remoteAssetTimestamp = 0L

            if (assets != null) {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "") ?: ""
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "") ?: ""
                        val updatedAtStr = asset.optString("updated_at", "")
                        remoteAssetTimestamp = runCatching {
                            isoFormat.parse(updatedAtStr)?.time ?: 0L
                        }.getOrDefault(0L)
                        break
                    }
                }
            }

            if (downloadUrl.isBlank()) {
                return@runCatching UpdateInfo(
                    latestVersion = ROLLING_TAG,
                    downloadUrl = "",
                    releaseNotes = "Release exists, but no APK is attached yet.",
                    hasUpdate = false
                )
            }

            // Compares the remote APK upload timestamp against the local installation time
            val isNewer = remoteAssetTimestamp > (installedAppLastUpdate + 10000L)

            UpdateInfo(
                latestVersion = ROLLING_TAG,
                downloadUrl = downloadUrl,
                releaseNotes = body,
                hasUpdate = isNewer
            )
        }
    }

    fun startDownloadAndInstall(context: Context, downloadUrl: String, versionLabel: String) {
        val fileName = "InOut-$versionLabel.apk"
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("Downloading InOut Update")
            setDescription("Alpha build $versionLabel")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (id == downloadId) {
                    try {
                        c?.unregisterReceiver(this)
                    } catch (_: Exception) {}

                    val file = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        fileName
                    )
                    if (file.exists()) {
                        installApk(c ?: context, file)
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }
}
