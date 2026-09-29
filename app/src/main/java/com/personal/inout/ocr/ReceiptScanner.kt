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

    suspend fun processReceipt(context: Context, imageUri: Uri): ParsedReceipt = withContext(Dispatchers.IO) {
        val bitmap = loadBitmapFromUri(context, imageUri)
        processReceiptBitmap(bitmap)
    }

    suspend fun processReceiptBitmap(bitmap: Bitmap): ParsedReceipt = withContext(Dispatchers.IO) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val visionText = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        parseReceiptText(visionText.text)
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

    private fun parseReceiptText(rawText: String): ParsedReceipt {
        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        val ignoredHeaderPhrases = listOf(
            "original for recipient",
            "tax invoice",
            "invoice",
            "cash memo",
            "retail invoice",
            "bill to",
            "ship to",
            "gstin",
            "phone:",
            "email:"
        )

        var merchant = "Store / Merchant"
        for (line in lines.take(8)) {
            val lower = line.lowercase()
            val isIgnored = ignoredHeaderPhrases.any { lower.contains(it) }
            if (!isIgnored && line.length in 3..40 && !line.any { it.isDigit() }) {
                merchant = line
                break
            }
        }

        // Target settlement summaries directly to avoid grabbing individual item rows
        val primaryAmountPattern = Pattern.compile(
            """(?i)(?:total\s*amount|grand\s*total|net\s*amount|total|amount\s*paid|received\s*amount)[\s:₹rs\.]*([\d,]+\.?\d{0,2})"""
        )

        var detectedTotal: Double? = null

        for (line in lines.reversed()) {
            val matcher = primaryAmountPattern.matcher(line)
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
