package com.personal.inout.util

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object TelegramFeedbackSender {

    private const val BOT_TOKEN = "8717362977:AAGqxY84CsUuMnxLPC6lHu2LLtb0DivQN7M"
    private const val CHAT_ID = "7679651355"

    suspend fun sendFeedback(
        context: Context,
        userMessage: String,
        imageUri: Uri?,
        extraContext: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val appVersion = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrDefault("1.0")

            val caption = buildString {
                append("📢 *InOut Feedback Report*\n\n")
                append("💬 *Message:* $userMessage\n\n")
                append("📱 *Device:* ${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n")
                append("🏷️ *App Version:* v$appVersion\n")
                if (extraContext.isNotBlank()) {
                    append("⚙️ *Context:* $extraContext\n")
                }
            }

            if (imageUri != null) {
                sendPhoto(context, imageUri, caption)
            } else {
                sendMessage(caption)
            }
        }
    }

    private fun sendMessage(text: String) {
        val url = URL("https://api.telegram.org/bot$BOT_TOKEN/sendMessage")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val json = org.json.JSONObject().apply {
            put("chat_id", CHAT_ID)
            put("text", text)
            put("parse_mode", "Markdown")
        }

        conn.outputStream.use { it.write(json.toString().toByteArray()) }
        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            error("Telegram API error: ${conn.responseCode} $err")
        }
    }

    private fun sendPhoto(context: Context, imageUri: Uri, caption: String) {
        val boundary = "==Boundary-${UUID.randomUUID()}=="
        val url = URL("https://api.telegram.org/bot$BOT_TOKEN/sendPhoto")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        val imageBytes = context.contentResolver.openInputStream(imageUri)?.use { input ->
            val byteBuffer = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var len: Int
            while (input.read(buffer).also { len = it } != -1) {
                byteBuffer.write(buffer, 0, len)
            }
            byteBuffer.toByteArray()
        } ?: error("Failed to read image data")

        val dos = DataOutputStream(conn.outputStream)

        // chat_id param
        dos.writeBytes("--$boundary\r\n")
        dos.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n")
        dos.writeBytes("$CHAT_ID\r\n")

        // caption param
        dos.writeBytes("--$boundary\r\n")
        dos.writeBytes("Content-Disposition: form-data; name=\"caption\"\r\n\r\n")
        dos.write(caption.toByteArray(Charsets.UTF_8))
        dos.writeBytes("\r\n")

        // parse_mode param
        dos.writeBytes("--$boundary\r\n")
        dos.writeBytes("Content-Disposition: form-data; name=\"parse_mode\"\r\n\r\n")
        dos.writeBytes("Markdown\r\n")

        // photo file binary
        dos.writeBytes("--$boundary\r\n")
        dos.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"screenshot.jpg\"\r\n")
        dos.writeBytes("Content-Type: image/jpeg\r\n\r\n")
        dos.write(imageBytes)
        dos.writeBytes("\r\n")

        // closing boundary
        dos.writeBytes("--$boundary--\r\n")
        dos.flush()
        dos.close()

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            error("Telegram API error: ${conn.responseCode} $err")
        }
    }
}
