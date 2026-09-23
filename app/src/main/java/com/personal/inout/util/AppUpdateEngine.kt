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
import org.json.JSONArray
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
    private const val PREFS_NAME = "inout_update_prefs"
    private const val KEY_INSTALLED_SHA = "installed_git_sha"

    suspend fun checkForUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentSha = prefs.getString(KEY_INSTALLED_SHA, "") ?: ""

            // Query the top-level releases endpoint (always ordered by latest created first)
            val url = URL("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "InOut-App")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                error("GitHub API status $code: $err")
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            val releasesArray = JSONArray(responseBody)
            if (releasesArray.length() == 0) {
                return@runCatching UpdateInfo(
                    latestVersion = "InOut",
                    downloadUrl = "",
                    releaseNotes = "No releases found.",
                    hasUpdate = false
                )
            }

            val latestRelease = releasesArray.getJSONObject(0)
            val releaseName = latestRelease.optString("name", "InOut Alpha")
            val assets = latestRelease.optJSONArray("assets")

            var downloadUrl = ""
            var remoteSha = ""

            if (assets != null) {
                val shaPattern = Pattern.compile("InOut-alpha-([a-f0-9]+)\\.apk")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val matcher = shaPattern.matcher(name)
                    if (matcher.find()) {
                        remoteSha = matcher.group(1) ?: ""
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    } else if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                    }
                }
            }

            if (downloadUrl.isBlank()) {
                return@runCatching UpdateInfo(
                    latestVersion = releaseName,
                    downloadUrl = "",
                    releaseNotes = "No APK package attached to release.",
                    hasUpdate = false
                )
            }

            val hasUpdate = remoteSha.isNotBlank() && !remoteSha.equals(currentSha, ignoreCase = true)

            UpdateInfo(
                latestVersion = if (remoteSha.isNotBlank()) "Alpha ($remoteSha)" else releaseName,
                downloadUrl = downloadUrl,
                releaseNotes = if (hasUpdate) "New build available ($remoteSha)" else "You are on the latest build",
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

                    val prefs = (c ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val matcher = Pattern.compile("\\(([a-f0-9]+)\\)").matcher(versionLabel)
                    if (matcher.find()) {
                        val sha = matcher.group(1) ?: ""
                        prefs.edit().putString(KEY_INSTALLED_SHA, sha).apply()
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
