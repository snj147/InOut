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

        val strictTotalAnchors = listOf(
            "GRAND TOTAL",
            "TOTAL AMOUNT",
            "NET PAYABLE",
            "TOTAL PAYABLE",
            "BALANCE DUE",
            "FINAL TOTAL",
            "TOTAL"
        )

        var detectedTotal: Double? = null

        // Pass 1: Parse candidate lines bottom-up containing total anchor words
        for (line in lines.reversed()) {
            val upper = line.uppercase()
            // Ignore list item lines like "1. Masala Dosa 149.00"
            if (upper.matches(Regex("""^\s*\d+\..*"""))) continue

            if (strictTotalAnchors.any { upper.contains(it) } && !upper.contains("SUB") && !upper.contains("QTY")) {
                val candidate = extractTrailingAmount(upper)
                if (candidate != null && candidate in 1.0..499999.0) {
                    detectedTotal = candidate
                    break
                }
            }
        }

        // Pass 2: Check lines immediately following anchor line
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

        // Extract Merchant Name
        val blacklist = listOf("EXIT", "POS", "ESC", "BILL", "NO", "DATE", "TIME", "GSTIN", "TAX", "WELCOME", "TABLE", "SETTINGS")
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
            lineItems = lines.take(10)
        )
    }

    private fun extractTrailingAmount(str: String): Double? {
        // Strip line indices if present at the start
        val sanitized = str.replace(Regex("""^\s*\d+\.\s*"""), " ")
        // Match numbers, favoring decimal currency tokens
        val matches = Regex("""(?:₹|INR|RS\.?)?\s*([0-9]{1,5}(?:\.[0-9]{1,2})?)""").findAll(sanitized)
        for (m in matches.toList().reversed()) {
            val token = m.groupValues[1]
            // Skip 6-digit postal codes and 10+ digit barcodes
            if (token.length in 6..12 && !token.contains(".")) continue
            val num = token.toDoubleOrNull()
            // Ignore isolated integers of value 1.0 or 2.0 unless formatted as a decimal
            if (num != null && (token.contains(".") || num > 9.0)) {
                return num
            }
        }
        return null
    }
}
