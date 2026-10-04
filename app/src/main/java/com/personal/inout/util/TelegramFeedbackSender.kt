package com.personal.inout.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object TelegramFeedbackSender {

    private const val BOT_TOKEN = "8932453873:AAE3ORsJu5gPsjyL5M50dFIG2wQe0gi0nTk" 
    private const val CHAT_ID = "7679651355"

    suspend fun sendFeedback(context: Context, userMessage: String, imageUri: Uri?): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (BOT_TOKEN == "YOUR_BOT_TOKEN_HERE") {
                return@withContext Result.failure(Exception("Bot token not configured. Please add it to TelegramFeedbackSender.kt"))
            }

            val textPayload = "🛡 *InOut Vault Alert*\n\n$userMessage"

            if (imageUri != null) {
                sendPhotoWithCaption(context, imageUri, textPayload)
            } else {
                sendMessage(textPayload)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sendMessage(text: String): Result<Boolean> {
        val urlString = "https://api.telegram.org/bot$BOT_TOKEN/sendMessage?chat_id=$CHAT_ID&text=${android.net.Uri.encode(text)}&parse_mode=Markdown"
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        
        return if (conn.responseCode == 200) Result.success(true) 
        else Result.failure(Exception("Telegram API error: ${conn.responseCode} ${conn.responseMessage}"))
    }

    private fun sendPhotoWithCaption(context: Context, imageUri: Uri, caption: String): Result<Boolean> {
        val urlString = "https://api.telegram.org/bot$BOT_TOKEN/sendPhoto"
        val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        conn.outputStream.use { os ->
            os.write(("$twoHyphens$boundary$lineEnd").toByteArray())
            os.write(("Content-Disposition: form-data; name=\"chat_id\"$lineEnd$lineEnd$CHAT_ID$lineEnd").toByteArray())

            os.write(("$twoHyphens$boundary$lineEnd").toByteArray())
            os.write(("Content-Disposition: form-data; name=\"caption\"$lineEnd$lineEnd$caption$lineEnd").toByteArray())

            os.write(("$twoHyphens$boundary$lineEnd").toByteArray())
            os.write(("Content-Disposition: form-data; name=\"photo\"; filename=\"screenshot.jpg\"$lineEnd").toByteArray())
            os.write(("Content-Type: image/jpeg$lineEnd$lineEnd").toByteArray())

            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            inputStream?.copyTo(os)
            inputStream?.close()

            os.write(("$lineEnd$twoHyphens$boundary$twoHyphens$lineEnd").toByteArray())
            os.flush()
        }

        return if (conn.responseCode == 200) Result.success(true) 
        else Result.failure(Exception("Telegram API error: ${conn.responseCode}"))
    }
}
