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
import java.util.regex.Pattern

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
    private const val PREFS_NAME = "inout_update_prefs"
    private const val KEY_INSTALLED_SHA = "installed_git_sha"

    suspend fun checkForUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentSha = prefs.getString(KEY_INSTALLED_SHA, "") ?: ""

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
                    releaseNotes = "No release found on GitHub.",
                    hasUpdate = false
                )
            }

            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                error("GitHub API status $code: $err")
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)
            val releaseName = json.optString("name", "InOut Alpha")
            val body = json.optString("body", "")
            val assets = json.optJSONArray("assets")

            // Extract the BUILD_SHA:xxxx from release notes
            val matcher = Pattern.compile("BUILD_SHA:([a-f0-9]+)").matcher(body)
            val remoteSha = if (matcher.find()) matcher.group(1) ?: "" else ""

            var downloadUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            if (downloadUrl.isBlank()) {
                return@runCatching UpdateInfo(
                    latestVersion = releaseName,
                    downloadUrl = "",
                    releaseNotes = "No APK package found.",
                    hasUpdate = false
                )
            }

            // If currentSha is empty (first time running), or remoteSha doesn't match currentSha:
            val hasUpdate = remoteSha.isNotBlank() && !remoteSha.equals(currentSha, ignoreCase = true)

            UpdateInfo(
                latestVersion = releaseName,
                downloadUrl = downloadUrl,
                releaseNotes = if (hasUpdate) "New build available ($releaseName)" else "You are on the latest build",
                hasUpdate = hasUpdate
            )
        }
    }

    fun startDownloadAndInstall(context: Context, downloadUrl: String, versionLabel: String) {
        val fileName = "InOut-latest.apk"
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("Downloading InOut Update")
            setDescription(versionLabel)
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

                    // Save the updated marker so it knows it is installed
                    val prefs = (c ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val shaMatcher = Pattern.compile("\\(([a-f0-9]+)\\)").matcher(versionLabel)
                    if (shaMatcher.find()) {
                        val shortSha = shaMatcher.group(1) ?: ""
                        prefs.edit().putString(KEY_INSTALLED_SHA, shortSha).apply()
                    }

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
