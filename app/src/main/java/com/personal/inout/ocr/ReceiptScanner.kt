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

        // Primary anchors for invoice and bill totals
        val strictTotalAnchors = listOf(
            "GRAND TOTAL",
            "TOTAL AMOUNT",
            "NET PAYABLE",
            "TOTAL PAYABLE",
            "BALANCE DUE",
            "FINAL TOTAL",
            "AMOUNT PAID"
        )
        val secondaryAnchors = listOf("TOTAL", "SUBTOTAL", "DUE")

        var detectedTotal: Double? = null

        // PASS 1: Strict reverse scan for specific Grand Total anchors (Prevents picking line items like Masala Dosa 149.00)
        for (line in lines.reversed()) {
            val upper = line.uppercase()
            if (strictTotalAnchors.any { upper.contains(it) }) {
                val candidate = extractTrailingNumber(upper)
                if (candidate != null && candidate in 1.0..499999.0) {
                    detectedTotal = candidate
                    break
                }
            }
        }

        // PASS 2: If no strict match, scan the line following a strict anchor
        if (detectedTotal == null) {
            for (i in lines.indices.reversed()) {
                val upper = lines[i].uppercase()
                if (strictTotalAnchors.any { upper.contains(it) }) {
                    if (i + 1 < lines.size) {
                        val candidate = extractTrailingNumber(lines[i + 1].uppercase())
                        if (candidate != null && candidate in 1.0..499999.0) {
                            detectedTotal = candidate
                            break
                        }
                    }
                }
            }
        }

        // PASS 3: Fallback to secondary anchors ("TOTAL")
        if (detectedTotal == null) {
            for (line in lines.reversed()) {
                val upper = line.uppercase()
                // Avoid barcode and date lines
                if (upper.contains("TOTAL") && !upper.contains("SUB") && !upper.contains("QTY")) {
                    val candidate = extractTrailingNumber(upper)
                    if (candidate != null && candidate in 1.0..499999.0) {
                        detectedTotal = candidate
                        break
                    }
                }
            }
        }

        // MERCHANT EXTRACTION: Skip POS UI control text, exit codes, and bill headers
        val uiBlacklist = listOf(
            "EXIT", "POS", "ESC", "SETTINGS", "BILLING", "SCREEN", "WATCH", "HOW TO",
            "BILL NO", "DATE", "TIME", "GSTIN", "TAX", "WELCOME", "CUSTOMER", "TABLE"
        )
        var detectedMerchant = "Receipt"

        for (line in lines.take(8)) {
            val clean = line.replace(Regex("""[^a-zA-Z0-9\s&'-]"""), "").trim()
            val upper = clean.uppercase()
            val isBlacklisted = uiBlacklist.any { upper.contains(it) }
            val isPureNumbers = clean.all { it.isDigit() || it.isWhitespace() }

            if (!isBlacklisted && !isPureNumbers && clean.length in 3..32) {
                detectedMerchant = clean
                break
            }
        }

        return ParsedReceipt(
            merchant = detectedMerchant,
            total = detectedTotal,
            lineItems = lines.take(12)
        )
    }

    private fun extractTrailingNumber(str: String): Double? {
        // Regex extracts currency numbers with optional decimals, rejecting 10-12 digit barcodes & 6 digit PIN codes
        val matches = Regex("""(?:₹|INR|RS\.?)?\s*([0-9]{1,5}(?:\.[0-9]{1,2})?)""").findAll(str)
        for (m in matches.toList().reversed()) {
            val num = m.groupValues[1].toDoubleOrNull()
            if (num != null && num > 0.0) {
                return num
            }
        }
        return null
    }
}
