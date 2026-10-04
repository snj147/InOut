package com.personal.inout.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class UpdateInfo(val hasUpdate: Boolean, val latestVersion: String, val downloadUrl: String, val releaseNotes: String)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

object AppUpdateEngine {
    private const val GITHUB_REPO_API = "https://api.github.com/repos/snj147/InOut/releases/latest"

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    suspend fun checkForUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_REPO_API)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val tagName = json.getString("tag_name")
                val releaseNotes = json.optString("body", "Routine maintenance and optimizations.")
                
                val assets = json.getJSONArray("assets")
                var downloadUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".zip") || asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
                
                val hasUpdate = compareVersions(tagName.replace("v", ""), currentVersion.replace("v", "")) > 0
                Result.success(UpdateInfo(hasUpdate, tagName, downloadUrl, releaseNotes))
            } else {
                Result.failure(Exception("GitHub API returned ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startStreamDownload(context: Context, urlString: String, version: String) = withContext(Dispatchers.IO) {
        try {
            _downloadState.value = UpdateDownloadState.Downloading(0f)
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connect()

            val fileLength = conn.contentLength
            val isZip = urlString.endsWith(".zip")
            val targetFile = File(context.cacheDir, if (isZip) "update_$version.zip" else "update_$version.apk")

            val input = conn.inputStream
            val output = FileOutputStream(targetFile)
            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int

            while (input.read(data).also { count = it } != -1) {
                total += count.toLong()
                output.write(data, 0, count)
                if (fileLength > 0) {
                    _downloadState.value = UpdateDownloadState.Downloading(total.toFloat() / fileLength)
                }
            }
            output.flush()
            output.close()
            input.close()

            if (isZip) {
                val extractedApk = extractLargestApkFromZip(context, targetFile)
                if (extractedApk != null) {
                    targetFile.delete()
                    _downloadState.value = UpdateDownloadState.ReadyToInstall(extractedApk)
                } else {
                    _downloadState.value = UpdateDownloadState.Error("No valid APK found in downloaded archive.")
                }
            } else {
                _downloadState.value = UpdateDownloadState.ReadyToInstall(targetFile)
            }
        } catch (e: Exception) {
            _downloadState.value = UpdateDownloadState.Error(e.localizedMessage ?: "Download stream failed")
        }
    }

    private fun extractLargestApkFromZip(context: Context, zipFile: File): File? {
        var bestApk: File? = null
        var bestApkSize = 0L

        try {
            val zis = ZipInputStream(zipFile.inputStream())
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                    val tempOutFile = File(context.cacheDir, "extracted_${System.currentTimeMillis()}.apk")
                    FileOutputStream(tempOutFile).use { fos ->
                        zis.copyTo(fos)
                    }
                    if (tempOutFile.length() > bestApkSize) {
                        bestApk?.delete() // Delete the previous smaller apk
                        bestApk = tempOutFile
                        bestApkSize = tempOutFile.length()
                    } else {
                        tempOutFile.delete() // Discard this one
                    }
                }
                entry = zis.nextEntry
            }
            zis.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bestApk
    }

    fun promptInstall(context: Context, apkFile: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val p1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val p2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(p1.size, p2.size)
        for (i in 0 until len) {
            val num1 = p1.getOrElse(i) { 0 }
            val num2 = p2.getOrElse(i) { 0 }
            if (num1 != num2) return num1.compareTo(num2)
        }
        return 0
    }
}
