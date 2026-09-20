package com.personal.inout.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ParsedReceipt(
    val merchant: String,
    val total: Double? = null,
    val candidateAmounts: List<Double> = emptyList(),
    val lineItems: List<String> = emptyList()
)

object ReceiptScanner {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun processReceipt(
        context: Context,
        imageUri: Uri,
        useCloudVision: Boolean = false,
        cloudApiKey: String = ""
    ): ParsedReceipt = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, imageUri))
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
        }
        processReceiptBitmap(bitmap, useCloudVision, cloudApiKey)
    }

    suspend fun processReceiptBitmap(
        bitmap: Bitmap,
        useCloudVision: Boolean = false,
        cloudApiKey: String = ""
    ): ParsedReceipt {
        if (useCloudVision && cloudApiKey.isNotBlank()) {
            try {
                return processViaGoogleCloudVision(bitmap, cloudApiKey)
            } catch (_: Exception) {
                // Seamless fallback to on-device ML Kit if cloud fails or offline
            }
        }
        return processViaOnDeviceMLKit(bitmap)
    }

    private suspend fun processViaOnDeviceMLKit(bitmap: Bitmap): ParsedReceipt = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                continuation.resume(extractReceiptData(visionText))
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }

    private fun extractReceiptData(visionText: Text): ParsedReceipt {
        val lines = visionText.textBlocks.flatMap { it.lines }.map { it.text.trim() }.filter { it.isNotBlank() }

        val strictTotalAnchors = listOf(
            "GRAND TOTAL",
            "TOTAL AMOUNT",
            "NET PAYABLE",
            "TOTAL PAYABLE",
            "BALANCE DUE",
            "FINAL TOTAL",
            "AMOUNT PAID",
            "TOTAL"
        )

        val candidateAmounts = mutableListOf<Double>()
        var detectedTotal: Double? = null

        // Collect all valid decimal amounts, strictly filtering out list indices ("1.", "2.")
        for (line in lines) {
            val sanitized = line.replace(Regex("""^\s*\d+\.\s*"""), " ")
            val matches = Regex("""(?:₹|INR|RS\.?)?\s*([0-9]{1,6}(?:\.[0-9]{1,2})?)""").findAll(sanitized)
            for (m in matches) {
                val token = m.groupValues[1]
                if (token.length in 6..12 && !token.contains(".")) continue
                val num = token.toDoubleOrNull()
                if (num != null && num in 1.0..499999.0 && !candidateAmounts.contains(num)) {
                    candidateAmounts.add(num)
                }
            }
        }

        // Pass 1: Parse bottom-up looking for strict total anchors
        for (line in lines.reversed()) {
            val upper = line.uppercase()
            if (upper.matches(Regex("""^\s*\d+\..*"""))) continue

            if (strictTotalAnchors.any { upper.contains(it) } && !upper.contains("SUB") && !upper.contains("QTY")) {
                val candidate = extractTrailingAmount(upper)
                if (candidate != null && candidate in 1.0..499999.0) {
                    detectedTotal = candidate
                    break
                }
            }
        }

        // Pass 2: Check lines directly beneath anchor line
        if (detectedTotal == null) {
            for (i in lines.indices.reversed()) {
                val upper = lines[i].uppercase()
                if (strictTotalAnchors.any { upper.contains(it) } && !upper.contains("SUB")) {
                    if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1].trim()
                        if (!nextLine.matches(Regex("""^\s*\d+\..*"""))) {
                            val candidate = extractTrailingAmount(nextLine.uppercase())
                            if (candidate != null && candidate in 1.0..499999.0) {
                                detectedTotal = candidate
                                break
                            }
                        }
                    }
                }
            }
        }

        // Fallback: If still null, pick the largest decimal number discovered
        if (detectedTotal == null && candidateAmounts.isNotEmpty()) {
            detectedTotal = candidateAmounts.maxOrNull()
        }

        // Extract Merchant Name
        val blacklist = listOf("EXIT", "POS", "ESC", "BILL", "NO", "DATE", "TIME", "GSTIN", "TAX", "WELCOME", "TABLE", "SETTINGS", "SCREEN")
        var merchant = "Receipt"

        for (line in lines.take(6)) {
            val clean = line.replace(Regex("""[^a-zA-Z0-9\s&'-]"""), "").trim()
            val upper = clean.uppercase()
            val isIgnored = blacklist.any { upper.contains(it) }
            val isPureDigits = clean.all { it.isDigit() || it.isWhitespace() }

            if (!isIgnored && !isPureDigits && clean.length in 3..32) {
                merchant = clean
                break
            }
        }

        return ParsedReceipt(
            merchant = merchant,
            total = detectedTotal,
            candidateAmounts = candidateAmounts.sortedDescending().take(6),
            lineItems = lines.take(10)
        )
    }

    private fun extractTrailingAmount(str: String): Double? {
        val sanitized = str.replace(Regex("""^\s*\d+\.\s*"""), " ")
        val matches = Regex("""(?:₹|INR|RS\.?)?\s*([0-9]{1,5}(?:\.[0-9]{1,2})?)""").findAll(sanitized)
        for (m in matches.toList().reversed()) {
            val token = m.groupValues[1]
            if (token.length in 6..12 && !token.contains(".")) continue
            val num = token.toDoubleOrNull()
            if (num != null && (token.contains(".") || num > 9.0)) {
                return num
            }
        }
        return null
    }

    private suspend fun processViaGoogleCloudVision(bitmap: Bitmap, apiKey: String): ParsedReceipt = withContext(Dispatchers.IO) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        val url = URL("https://vision.googleapis.com/v1/images:annotate?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 8000
            readTimeout = 8000
        }

        val jsonRequest = JSONObject().apply {
            put("requests", JSONArray().apply {
                put(JSONObject().apply {
                    put("image", JSONObject().apply { put("content", base64) })
                    put("features", JSONArray().apply {
                        put(JSONObject().apply { put("type", "DOCUMENT_TEXT_DETECTION") })
                    })
                })
            })
        }

        conn.outputStream.use { it.write(jsonRequest.toString().toByteArray()) }

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(responseText)
        val responses = root.optJSONArray("responses") ?: JSONArray()
        val fullTextAnnotation = responses.optJSONObject(0)?.optJSONObject("fullTextAnnotation")
        val rawText = fullTextAnnotation?.optString("text") ?: ""

        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val candidateAmounts = mutableListOf<Double>()
        var detectedTotal: Double? = null

        for (line in lines) {
            val sanitized = line.replace(Regex("""^\s*\d+\.\s*"""), " ")
            val matches = Regex("""(?:₹|INR|RS\.?)?\s*([0-9]{1,6}(?:\.[0-9]{1,2})?)""").findAll(sanitized)
            for (m in matches) {
                val num = m.groupValues[1].toDoubleOrNull()
                if (num != null && num in 1.0..499999.0 && !candidateAmounts.contains(num)) {
                    candidateAmounts.add(num)
                }
            }
        }

        val strictAnchors = listOf("GRAND TOTAL", "TOTAL AMOUNT", "NET PAYABLE", "TOTAL PAYABLE", "TOTAL")
        for (line in lines.reversed()) {
            val upper = line.uppercase()
            if (upper.matches(Regex("""^\s*\d+\..*"""))) continue
            if (strictAnchors.any { upper.contains(it) } && !upper.contains("SUB")) {
                detectedTotal = extractTrailingAmount(upper)
                if (detectedTotal != null) break
            }
        }

        if (detectedTotal == null && candidateAmounts.isNotEmpty()) {
            detectedTotal = candidateAmounts.maxOrNull()
        }

        ParsedReceipt(
            merchant = lines.firstOrNull { it.length in 3..30 } ?: "Cloud Receipt",
            total = detectedTotal,
            candidateAmounts = candidateAmounts.sortedDescending().take(6),
            lineItems = lines.take(10)
        )
    }
}
