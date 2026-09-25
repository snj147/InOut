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
import java.text.SimpleDateFormat
import java.util.Locale

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object AppUpdateEngine {

    private const val GITHUB_REPO_OWNER = "snj147"
    private const val GITHUB_REPO_NAME = "InOut"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases"

    suspend fun checkForUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "InOut-Android-Updater")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (connection.responseCode != 200) {
                return@withContext Result.failure(Exception("GitHub API HTTP ${connection.responseCode}"))
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val releasesArray = JSONArray(responseBody)
            if (releasesArray.length() == 0) {
                return@withContext Result.success(UpdateInfo(false, "", "", "No releases found"))
            }

            val installedVersionName = try {
                val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pkgInfo.versionName?.trim() ?: ""
            } catch (e: Exception) {
                ""
            }

            data class CandidateAsset(
                val name: String,
                val downloadUrl: String,
                val sha: String,
                val timestamp: Long
            )

            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }

            val candidateList = mutableListOf<CandidateAsset>()

            for (r in 0 until releasesArray.length()) {
                val releaseObj = releasesArray.getJSONObject(r)
                val assets = releaseObj.optJSONArray("assets") ?: continue

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")

                    if (name.endsWith(".apk", ignoreCase = true)) {
                        val downloadUrl = asset.getString("browser_download_url")
                        val updatedStr = asset.optString("updated_at", asset.optString("created_at", ""))
                        val timestamp = try {
                            isoFormat.parse(updatedStr)?.time ?: 0L
                        } catch (e: Exception) {
                            0L
                        }

                        val extractedSha = if (name.startsWith("InOut-alpha-") && name.endsWith(".apk")) {
                            name.removePrefix("InOut-alpha-").removeSuffix(".apk")
                        } else {
                            name
                        }

                        candidateList.add(CandidateAsset(name, downloadUrl, extractedSha, timestamp))
                    }
                }
            }

            if (candidateList.isEmpty()) {
                return@withContext Result.success(UpdateInfo(false, "", "", "No APK assets found"))
            }

            val newestAsset = candidateList.maxByOrNull { it.timestamp } ?: candidateList.first()
            val targetSha = newestAsset.sha.trim()

            val isNewer = if (installedVersionName.isNotBlank() && targetSha.isNotBlank()) {
                val cleanInstalled = installedVersionName.lowercase()
                val cleanTarget = targetSha.lowercase()
                cleanInstalled != cleanTarget && !cleanInstalled.contains(cleanTarget) && !cleanTarget.contains(cleanInstalled)
            } else {
                false
            }

            Result.success(
                UpdateInfo(
                    hasUpdate = isNewer,
                    latestVersion = targetSha.take(7),
                    downloadUrl = newestAsset.downloadUrl,
                    releaseNotes = "New Alpha Build: ${targetSha.take(7)}"
                )
            )
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

        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("Downloading InOut Update")
            setDescription("Fetching build $versionTag")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destinationFile))
            setMimeType("application/vnd.android.package-archive")
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    try {
                        context.unregisterReceiver(this)
                    } catch (ignored: Exception) {}
                    launchInstaller(context, destinationFile)
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private fun launchInstaller(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }
}
