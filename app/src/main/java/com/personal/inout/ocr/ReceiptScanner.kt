package com.personal.inout.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ParsedReceipt(
    val merchant: String,
    val total: Double? = null,
    val lineItems: List<String> = emptyList()
)

object ReceiptScanner {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun processReceipt(context: Context, imageUri: Uri): ParsedReceipt {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, imageUri))
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
        }
        return processReceiptBitmap(bitmap)
    }

    suspend fun processReceiptBitmap(bitmap: Bitmap): ParsedReceipt = suspendCancellableCoroutine { continuation ->
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

        val totalKeywords = listOf("GRAND TOTAL", "TOTAL AMOUNT", "NET PAYABLE", "TOTAL", "AMOUNT DUE", "SUBTOTAL", "BALANCE DUE")
        var detectedTotal: Double? = null

        for (line in lines.reversed()) {
            val upper = line.uppercase()
            for (kw in totalKeywords) {
                if (upper.contains(kw)) {
                    val priceMatch = Regex("""(?:₹|INR|RS\.?|TOTAL\s*[:\-])?\s*(\d{1,6}(?:,\d{3})*(?:\.\d{1,2})?)""").find(upper)
                    val candidate = priceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
                    if (candidate != null && candidate > 0 && candidate < 500000) {
                        detectedTotal = candidate
                        break
                    }
                }
            }
            if (detectedTotal != null) break
        }

        if (detectedTotal == null) {
            for (i in lines.indices) {
                val upper = lines[i].uppercase()
                if (totalKeywords.any { upper.contains(it) }) {
                    if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1].replace(",", "")
                        val match = Regex("""(\d{1,6}(?:\.\d{1,2})?)""").find(nextLine)
                        val candidate = match?.groupValues?.get(1)?.toDoubleOrNull()
                        if (candidate != null && candidate > 0 && candidate < 500000) {
                            detectedTotal = candidate
                            break
                        }
                    }
                }
            }
        }

        val ignoreList = listOf("EXIT", "POS", "ESC", "BILL", "NO", "DATE", "TIME", "WELCOME", "TAX", "INVOICE", "CASH", "CARD")
        var detectedMerchant = "Receipt"

        for (line in lines.take(5)) {
            val clean = line.replace(Regex("""[^a-zA-Z0-9\s&]"""), "").trim()
            val upper = clean.uppercase()
            val isIgnored = ignoreList.any { upper.startsWith(it) || upper == it }
            val hasDigitsOnly = clean.all { it.isDigit() || it.isWhitespace() }

            if (!isIgnored && !hasDigitsOnly && clean.length in 3..35) {
                detectedMerchant = clean
                break
            }
        }

        return ParsedReceipt(
            merchant = detectedMerchant,
            total = detectedTotal,
            lineItems = lines.take(10)
        )
    }
}
