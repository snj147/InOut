package com.personal.inout.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
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
import kotlin.math.abs

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
                continuation.resume(extractReceiptData(visionText, bitmap.height))
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }

    private fun extractReceiptData(visionText: Text, imageHeight: Int): ParsedReceipt {
        val allLines = visionText.textBlocks.flatMap { it.lines }
        val strictAnchors = listOf("GRAND TOTAL", "TOTAL AMOUNT", "NET PAYABLE", "TOTAL PAYABLE", "BALANCE DUE", "FINAL TOTAL", "AMOUNT PAID", "TOTAL")

        var detectedTotal: Double? = null
        var bestAnchorBox: Rect? = null

        // 1. SPATIAL PASS: Find bounding box of the total keyword
        for (line in allLines.reversed()) {
            val upper = line.text.uppercase()
            if (strictAnchors.any { upper.contains(it) } && !upper.contains("SUB") && !upper.contains("QTY")) {
                val inlineNum = extractValidCurrency(upper)
                if (inlineNum != null) {
                    detectedTotal = inlineNum
                    break
                }
                bestAnchorBox = line.boundingBox
                if (bestAnchorBox != null) break
            }
        }

        // 2. 2D CLUSTERING: If number was not on same text block, search within horizontal corridor
        if (detectedTotal == null && bestAnchorBox != null) {
            val anchorY = bestAnchorBox.centerY()
            val candidates = mutableListOf<Pair<Double, Int>>() // amount to horizontal distance

            for (other in allLines) {
                val box = other.boundingBox ?: continue
                // Within 32px vertical corridor of anchor (same row)
                if (abs(box.centerY() - anchorY) <= 32 && box.left >= bestAnchorBox.left) {
                    val num = extractValidCurrency(other.text)
                    if (num != null) {
                        candidates.add(num to (box.left - bestAnchorBox.right))
                    }
                }
            }

            // Closest valid number to the right
            detectedTotal = candidates.minByOrNull { it.second }?.first

            // Fallback: Check row immediately beneath anchor box
            if (detectedTotal == null) {
                val nextLineBelow = allLines
                    .filter { (it.boundingBox?.top ?: 0) in bestAnchorBox.bottom..(bestAnchorBox.bottom + 65) }
                    .mapNotNull { extractValidCurrency(it.text) }
                    .firstOrNull()
                detectedTotal = nextLineBelow
            }
        }

        // 3. MERCHANT EXTRACTION (Largest font in upper 25% of image, skipping UI button noise)
        val uiBlacklist = listOf("EXIT", "POS", "ESC", "SETTINGS", "BILLING", "SCREEN", "WATCH", "BILL NO", "DATE", "TIME", "GSTIN", "TAX")
        var bestMerchant = "Receipt"
        var maxFontSize = 0

        val topLines = allLines.filter { (it.boundingBox?.top ?: 0) < (imageHeight * 0.28) }
        for (line in topLines) {
            val clean = line.text.replace(Regex("""[^a-zA-Z0-9\s&'-]"""), "").trim()
            val upper = clean.uppercase()
            val isIgnored = uiBlacklist.any { upper.contains(it) }
            val isPureNum = clean.all { it.isDigit() || it.isWhitespace() }

            if (!isIgnored && !isPureNum && clean.length in 3..35) {
                val h = line.boundingBox?.height() ?: 0
                if (h > maxFontSize) {
                    maxFontSize = h
                    bestMerchant = clean
                }
            }
        }

        return ParsedReceipt(
            merchant = bestMerchant,
            total = detectedTotal,
            lineItems = allLines.take(12).map { it.text }
        )
    }

    private fun extractValidCurrency(str: String): Double? {
        val matches = Regex("""(?:₹|INR|RS\.?)?\s*([0-9]{1,5}(?:\.[0-9]{1,2})?)""").findAll(str)
        for (m in matches.toList().reversed()) {
            val token = m.groupValues[1]
            // Reject 6-digit postal PIN codes, 10-12 digit barcodes
            if (token.length in 6..12 && !token.contains(".")) continue
            val num = token.toDoubleOrNull()
            if (num != null && num in 1.0..499999.0) {
                return num
            }
        }
        return null
    }
}
