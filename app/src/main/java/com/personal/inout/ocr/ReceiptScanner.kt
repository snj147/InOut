package com.personal.inout.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.regex.Pattern
import kotlin.coroutines.resume

data class ParsedReceipt(
    val totalAmount: Double?,
    val merchantName: String?,
    val rawText: String
)

object ReceiptScanner {
    // Bundled latin model runs completely on-device without internet or play services downloads
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processReceipt(context: Context, imageUri: Uri): ParsedReceipt {
        return suspendCancellableCoroutine { continuation ->
            val image = try {
                InputImage.fromFilePath(context, imageUri)
            } catch (e: Exception) {
                continuation.resume(ParsedReceipt(null, null, ""))
                return@suspendCancellableCoroutine
            }

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text
                    val lines = visionText.textBlocks.flatMap { it.lines.map { line -> line.text } }

                    val detectedAmount = extractTotalAmount(lines)
                    val detectedMerchant = extractMerchant(lines)

                    continuation.resume(
                        ParsedReceipt(
                            totalAmount = detectedAmount,
                            merchantName = detectedMerchant,
                            rawText = fullText
                        )
                    )
                }
                .addOnFailureListener {
                    continuation.resume(ParsedReceipt(null, null, ""))
                }
        }
    }

    private fun extractTotalAmount(lines: List<String>): Double? {
        // Look for typical bill total keywords
        val totalPattern = Pattern.compile("(?i)(?:TOTAL|NET|AMOUNT|PAYABLE|DUE|PAID|GRAND\\s*TOTAL)\\s*[:=]?\\s*(?:INR|RS\\.?|₹)?\\s*([\\d,]+\\.?\\d*)")
        
        // Scan lines in reverse since totals are almost always at the bottom
        for (i in lines.indices.reversed()) {
            val line = lines[i]
            val matcher = totalPattern.matcher(line)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
                if (candidate != null && candidate > 0.0) {
                    return candidate
                }
            }
        }

        // Fallback: look for the highest standalone currency number near the bottom
        val generalNumber = Pattern.compile("(?:INR|RS\\.?|₹)?\\s*([\\d,]+\\.\\d{2})")
        for (i in lines.indices.reversed()) {
            val matcher = generalNumber.matcher(lines[i])
            if (matcher.find()) {
                val candidate = matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
                if (candidate != null && candidate > 0.0) {
                    return candidate
                }
            }
        }

        return null
    }

    private fun extractMerchant(lines: List<String>): String? {
        // Merchant names almost always reside on line 1, 2, or 3 of a printed receipt
        for (i in 0 until minOf(3, lines.size)) {
            val line = lines[i].trim()
            if (line.length >= 3 && !line.matches(Regex(".*[0-9]{5,}.*"))) {
                return line
            }
        }
        return "Scanned Store"
    }
}
