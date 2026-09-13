package com.personal.inout.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.util.regex.Pattern

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
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val visionText = recognizer.process(image).await()

            val lines = visionText.textBlocks.flatMap { it.lines }.map { it.text.trim() }
            val merchant = lines.firstOrNull { it.isNotBlank() } ?: "Scanned Receipt"

            val priceRegex = Pattern.compile("(?i)(?:₹|rs\\.?|inr)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)")
            val detectedItems = mutableListOf<ParsedLineItem>()
            var maxAmount = 0.0

            for (line in lines) {
                val matcher = priceRegex.matcher(line)
                if (matcher.find()) {
                    val amt = matcher.group(1)?.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        val desc = line.replace(matcher.group(0) ?: "", "").trim()
                        if (desc.isNotBlank()) {
                            detectedItems.add(ParsedLineItem(description = desc, amount = amt))
                        }
                        if (amt > maxAmount) {
                            maxAmount = amt
                        }
                    }
                }
            }

            ParsedReceipt(
                merchant = merchant,
                total = maxAmount,
                lineItems = detectedItems
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ParsedReceipt(merchant = "Manual Receipt", total = 0.0, lineItems = emptyList())
        }
    }
}
