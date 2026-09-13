package com.personal.inout.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.regex.Pattern
import kotlin.coroutines.resume

data class ParsedLineItem(
    val description: String,
    val amount: Double
)

data class ParsedReceipt(
    val merchant: String,
    val total: Double,
    val lineItems: List<ParsedLineItem>
)

object ReceiptScanner {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processReceipt(context: Context, uri: Uri): ParsedReceipt {
        return suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val lines = mutableListOf<String>()
                        for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                val txt = line.text.trim()
                                if (txt.isNotEmpty()) {
                                    lines.add(txt)
                                }
                            }
                        }

                        val merchant = lines.firstOrNull() ?: "Scanned Receipt"
                        val priceRegex = Pattern.compile("(?i)(?:₹|rs\\.?|inr)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)")
                        val detectedItems = mutableListOf<ParsedLineItem>()
                        var maxAmount = 0.0

                        for (l in lines) {
                            val matcher = priceRegex.matcher(l)
                            if (matcher.find()) {
                                val amt = matcher.group(1)?.toDoubleOrNull() ?: 0.0
                                if (amt > 0.0) {
                                    val desc = l.replace(matcher.group(0) ?: "", "").trim()
                                    if (desc.isNotEmpty()) {
                                        detectedItems.add(ParsedLineItem(description = desc, amount = amt))
                                    }
                                    if (amt > maxAmount) {
                                        maxAmount = amt
                                    }
                                }
                            }
                        }

                        continuation.resume(
                            ParsedReceipt(
                                merchant = merchant,
                                total = maxAmount,
                                lineItems = detectedItems
                            )
                        )
                    }
                    .addOnFailureListener { exception ->
                        exception.printStackTrace()
                        continuation.resume(
                            ParsedReceipt(
                                merchant = "Manual Receipt",
                                total = 0.0,
                                lineItems = emptyList()
                            )
                        )
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                continuation.resume(
                    ParsedReceipt(
                        merchant = "Manual Receipt",
                        total = 0.0,
                        lineItems = emptyList()
                    )
                )
            }
        }
    }
}
