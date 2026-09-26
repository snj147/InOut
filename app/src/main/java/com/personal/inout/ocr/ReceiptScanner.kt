package com.personal.inout.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ParsedReceipt(
    val merchant: String,
    val total: Double?,
    val dateEpoch: Long?,
    val rawText: String
)

object ReceiptScanner {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun processReceipt(
        context: Context,
        imageUri: Uri,
        useCloudVision: Boolean = false,
        apiKey: String = ""
    ): ParsedReceipt = withContext(Dispatchers.IO) {
        val bitmap = loadBitmapFromUri(context, imageUri)
        processReceiptBitmap(bitmap, useCloudVision, apiKey)
    }

    suspend fun processReceiptBitmap(
        bitmap: Bitmap,
        useCloudVision: Boolean = false,
        apiKey: String = ""
    ): ParsedReceipt = withContext(Dispatchers.IO) {
        if (useCloudVision && apiKey.isNotBlank()) {
            try {
                return@withContext processWithCloudVision(bitmap, apiKey)
            } catch (_: Exception) {
                // Fall back cleanly to on-device ML Kit if cloud vision request fails
            }
        }
        processWithOnDeviceMlKit(bitmap)
    }

    @Suppress("DEPRECATION")
    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    private suspend fun processWithOnDeviceMlKit(bitmap: Bitmap): ParsedReceipt {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val visionText = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    cont.resume(text)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }

        return parseReceiptText(visionText.text)
    }

    private fun processWithCloudVision(bitmap: Bitmap, apiKey: String): ParsedReceipt {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
        val imageBytes = byteArrayOutputStream.toByteArray()
        val base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)

        val requestJson = JSONObject().apply {
            put("requests", JSONArray().apply {
                put(JSONObject().apply {
                    put("image", JSONObject().put("content", base64Image))
                    put("features", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "TEXT_DETECTION")
                            put("maxResults", 1)
                        })
                    })
                })
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://vision.googleapis.com/v1/images:annotate?key=$apiKey")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Cloud Vision API call failed: HTTP ${response.code}")
            }
            val responseBody = response.body?.string() ?: ""
            val json = JSONObject(responseBody)
            val responsesArray = json.optJSONArray("responses")
            val firstResponse = responsesArray?.optJSONObject(0)
            val fullTextAnnotation = firstResponse?.optJSONObject("fullTextAnnotation")
            val extractedText = fullTextAnnotation?.optString("text") ?: ""

            return parseReceiptText(extractedText)
        }
    }

    private fun parseReceiptText(rawText: String): ParsedReceipt {
        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        var merchant = "Store / Merchant"
        for (line in lines.take(5)) {
            val lower = line.lowercase()
            if (!lower.contains("tax") &&
                !lower.contains("invoice") &&
                !lower.contains("bill") &&
                !lower.contains("receipt") &&
                !lower.contains("tel") &&
                !lower.contains("phone") &&
                !lower.contains("gst") &&
                line.length in 3..35
            ) {
                merchant = line
                break
            }
        }

        var detectedTotal: Double? = null
        val amountPattern = Pattern.compile("""(?i)(?:total|grand\s*total|net\s*amount|amount\s*paid|subtotal|balance\s*due)[\s:₹rs\.]*([\d,]+\.?\d{0,2})""")

        for (line in lines.reversed()) {
            val matcher = amountPattern.matcher(line)
            if (matcher.find()) {
                val numStr = matcher.group(1)?.replace(",", "")
                val parsed = numStr?.toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    detectedTotal = parsed
                    break
                }
            }
        }

        if (detectedTotal == null) {
            val fallbackPattern = Pattern.compile("""(?i)[₹rs\.\s]+([\d,]+\.\d{2})""")
            val candidates = mutableListOf<Double>()
            for (line in lines) {
                val matcher = fallbackPattern.matcher(line)
                while (matcher.find()) {
                    val numStr = matcher.group(1)?.replace(",", "")
                    numStr?.toDoubleOrNull()?.let { candidates.add(it) }
                }
            }
            detectedTotal = candidates.maxOrNull()
        }

        return ParsedReceipt(
            merchant = merchant,
            total = detectedTotal,
            dateEpoch = System.currentTimeMillis(),
            rawText = rawText
        )
    }
}
