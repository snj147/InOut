package com.personal.inout.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.personal.inout.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object AppUpdateEngine {
    private const val GITHUB_REPO = "snj147/InOut"
    private const val RELEASE_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/tags/rolling-alpha"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASE_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "InOut-Vault-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("GitHub API HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
                val json = JSONObject(body)

                val releaseName = json.optString("name", "")
                val releaseBody = json.optString("body", "")
                val assets = json.optJSONArray("assets")

                var apkDownloadUrl = ""
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

                val remoteSha = releaseName.substringAfter("(").substringBefore(")").trim()
                val currentInstalledSha = BuildConfig.GIT_SHA.trim()

                val isNewer = currentInstalledSha.isNotBlank() &&
                        remoteSha.isNotBlank() &&
                        !currentInstalledSha.startsWith(remoteSha) &&
                        !remoteSha.startsWith(currentInstalledSha) &&
                        currentInstalledSha != "localdev"

                val shortRemote = if (remoteSha.length >= 7) remoteSha.take(7) else remoteSha.ifBlank { "Alpha" }

                Result.success(
                    UpdateInfo(
                        hasUpdate = isNewer && apkDownloadUrl.isNotBlank(),
                        latestVersion = shortRemote,
                        downloadUrl = apkDownloadUrl,
                        releaseNotes = releaseBody.ifBlank { "Automated continuous build update." }
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startDownloadAndInstall(context: Context, downloadUrl: String, versionTag: String) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(downloadUrl)

            val request = DownloadManager.Request(uri).apply {
                setTitle("InOut Update ($versionTag)")
                setDescription("Downloading latest alpha build...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "InOut-alpha-$versionTag.apk")
                setMimeType("application/vnd.android.package-archive")
            }

            downloadManager.enqueue(request)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
