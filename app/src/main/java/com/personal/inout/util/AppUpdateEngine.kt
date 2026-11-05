package com.personal.inout.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.personal.inout.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * downloadUrl = the GitHub *API* url of the APK asset (works for private repos).
 * latestVersion = release name, shown to the user.
 */
data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

object AppUpdateEngine {

    private const val RELEASE_API = "https://api.github.com/repos/snj147/InOut/releases/tags/rolling-alpha"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_REDIRECTS = 6

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    // =====================================================================
    // 1. DETECT
    // =====================================================================

    suspend fun checkForUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val token = BuildConfig.REPO_READ_TOKEN
            if (token.isBlank()) {
                return@withContext Result.failure(
                    Exception("DETECT: This build has no REPO_READ_TOKEN. Add the secret and pass it as an env var to the build step.")
                )
            }

            val conn = open("$RELEASE_API?t=${System.currentTimeMillis()}", token, "application/vnd.github+json")
            try {
                val code = conn.responseCode
                if (code != 200) {
                    return@withContext Result.failure(Exception("DETECT: " + describeHttpError(code)))
                }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })

                val releaseName = json.optString("name", "")
                val releaseNotes = json.optString("body", "").ifBlank { "Routine maintenance and optimizations." }

                val assets = json.optJSONArray("assets")
                var assetApiUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk", ignoreCase = true) &&
                            a.optString("state", "uploaded") == "uploaded"
                        ) {
                            assetApiUrl = a.getString("url")
                            break
                        }
                    }
                }
                if (assetApiUrl.isBlank()) {
                    return@withContext Result.failure(
                        Exception("DETECT: Release found but it has no finished .apk asset (build may still be uploading).")
                    )
                }

                val installedCode = installedVersionCode(context)
                // Preferred: release name contains "#<run number>", and CI passes run number as versionCode.
                val runNumber = Regex("#(\\d+)").find(releaseName)?.groupValues?.get(1)?.toLongOrNull()
                val hasUpdate = if (runNumber != null) {
                    runNumber > installedCode
                } else {
                    // Legacy release names: "InOut Alpha Rolling (<full sha>)"
                    val sha = BuildConfig.GIT_SHA
                    sha != "localdev" && sha.isNotBlank() && !releaseName.contains(sha)
                }

                Result.success(UpdateInfo(hasUpdate, releaseName, assetApiUrl, releaseNotes))
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception("DETECT: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }

    private fun describeHttpError(code: Int): String = when (code) {
        401 -> "GitHub rejected the token (401). It is invalid or expired."
        403 -> "GitHub refused (403). Rate-limited, or the token lacks 'Contents: Read-only' on this repo."
        404 -> "Release 'rolling-alpha' not found (404). Either it is being rebuilt right now, or the token has no access to the private repo."
        else -> "GitHub API returned HTTP $code."
    }

    // =====================================================================
    // 2. DOWNLOAD (+ 3. VERIFY before offering install)
    // =====================================================================

    suspend fun startStreamDownload(context: Context, urlString: String, version: String) =
        withContext(Dispatchers.IO) {
            try {
                _downloadState.value = UpdateDownloadState.Downloading(0f)

                val dir = File(context.cacheDir, "updates").apply {
                    mkdirs()
                    listFiles()?.forEach { it.delete() } // clear stale partial/old files
                }
                val part = File(dir, "update.apk.part")
                val target = File(dir, "update.apk")

                val conn = openFollowingRedirects(urlString, BuildConfig.REPO_READ_TOKEN, "application/octet-stream")
                try {
                    val total = conn.contentLengthLong
                    var downloaded = 0L
                    var lastPercent = -1
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n == -1) break
                                out.write(buf, 0, n)
                                downloaded += n
                                if (total > 0) {
                                    val pct = ((downloaded * 100) / total).toInt()
                                    if (pct != lastPercent) {
                                        lastPercent = pct
                                        _downloadState.value = UpdateDownloadState.Downloading(pct / 100f)
                                    }
                                }
                            }
                        }
                    }
                    if (total > 0 && part.length() != total) {
                        throw IOException("Incomplete download (${part.length()} of $total bytes). Check your connection and retry.")
                    }
                } finally {
                    conn.disconnect()
                }

                if (!part.renameTo(target)) throw IOException("Could not finalize the downloaded file.")

                val problem = verifyApk(context, target)
                if (problem != null) {
                    target.delete()
                    _downloadState.value = UpdateDownloadState.Error(problem)
                    return@withContext
                }
                _downloadState.value = UpdateDownloadState.ReadyToInstall(target)
            } catch (e: CancellationException) {
                _downloadState.value = UpdateDownloadState.Idle
                throw e
            } catch (e: Exception) {
                _downloadState.value =
                    UpdateDownloadState.Error("DOWNLOAD: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }

    /** Returns null if the APK is safe to hand to the installer, otherwise a human-readable reason. */
    @Suppress("DEPRECATION")
    private fun verifyApk(context: Context, apk: File): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: return "VERIFY: The downloaded file is not a valid APK (corrupted or incomplete)."

        if (archive.packageName != context.packageName) {
            return "VERIFY: Wrong package. Update is '${archive.packageName}' but this app is '${context.packageName}'."
        }

        val installed = pm.getPackageInfo(context.packageName, flags)
        val newCode = PackageInfoCompat.getLongVersionCode(archive)
        val oldCode = PackageInfoCompat.getLongVersionCode(installed)
        if (newCode <= oldCode) {
            return "VERIFY: Downloaded build ($newCode) is not newer than the installed one ($oldCode)."
        }

        val newSigners = signerHashes(archive)
        val oldSigners = signerHashes(installed)
        if (newSigners.isNotEmpty() && oldSigners.isNotEmpty() && newSigners != oldSigners) {
            return "SIGNATURE: This update is signed with a different key than the installed app, so Android will refuse it. " +
                "Export a backup, uninstall once, install the new build, and restore. After that, updates will work."
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun signerHashes(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        if (sigs == null) return emptySet()
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.map { s -> md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    // =====================================================================
    // 4. INSTALL
    // =====================================================================

    /** Call from the UI thread (button click). */
    fun promptInstall(context: Context, apkFile: File) {
        try {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(
                    context,
                    "Allow \"Install unknown apps\" for InOut, then come back and tap Install again.",
                    Toast.LENGTH_LONG
                ).show()
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
            if (!apkFile.exists()) {
                _downloadState.value = UpdateDownloadState.Error("INSTALL: Downloaded file is gone. Tap Update to download again.")
                return
            }

            // Authority must match AndroidManifest: ${applicationId}.fileprovider
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _downloadState.value = UpdateDownloadState.Error("INSTALL: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    // =====================================================================
    // HTTP helpers
    // =====================================================================

    private fun installedVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    private fun open(url: String, token: String?, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = CONNECT_TIMEOUT_MS
        c.readTimeout = READ_TIMEOUT_MS
        c.instanceFollowRedirects = false // we follow manually so the token never leaks to the CDN
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "InOut-Updater")
        if (!token.isNullOrBlank()) {
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        return c
    }

    /**
     * GitHub answers an asset request with a 302 to a pre-signed storage URL. That second request
     * must NOT carry the Authorization header (storage rejects it), so the token is dropped after hop 1.
     */
    private fun openFollowingRedirects(startUrl: String, token: String?, accept: String): HttpURLConnection {
        var url = startUrl
        var useToken = token
        for (hop in 0..MAX_REDIRECTS) {
            val c = open(url, useToken, accept)
            val code = c.responseCode
            when {
                code == 200 -> return c
                code in 300..399 -> {
                    val loc = c.getHeaderField("Location")
                    c.disconnect()
                    if (loc.isNullOrBlank()) throw IOException("Redirect without a Location header.")
                    url = URL(URL(url), loc).toString()
                    useToken = null
                }
                else -> {
                    c.disconnect()
                    throw IOException(describeHttpError(code))
                }
            }
        }
        throw IOException("Too many redirects while downloading.")
    }
}
