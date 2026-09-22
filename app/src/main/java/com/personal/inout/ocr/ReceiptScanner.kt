package com.personal.inout.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

data class ReceiptItem(
    val name: String,
    val price: Double
)

data class ParsedReceipt(
    val merchant: String,
    val total: Double?,
    val dateEpoch: Long? = null,
    val lineItems: List<ReceiptItem> = emptyList(),
    val rawText: String = ""
)

private data class SpatialElement(
    val text: String,
    val box: Rect
)

private data class SpatialLine(
    val elements: MutableList<SpatialElement> = mutableListOf(),
    var top: Int = 0,
    var bottom: Int = 0
) {
    val text: String
        get() = elements.sortedBy { it.box.left }.joinToString(" ") { it.text }
}

object ReceiptScanner {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun processReceipt(
        context: Context,
        imageUri: Uri,
        useCloud: Boolean = false,
        cloudApiKey: String = ""
    ): ParsedReceipt = withContext(Dispatchers.IO) {
        val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
        val bitmap = BitmapFactory.decodeStream(inputStream) ?: error("Failed to decode image from Uri")
        processReceiptBitmap(bitmap, useCloud, cloudApiKey)
    }

    suspend fun processReceiptBitmap(
        bitmap: Bitmap,
        useCloud: Boolean = false,
        cloudApiKey: String = ""
    ): ParsedReceipt = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = runRecognizer(image)
        parseSpatialReceipt(visionText)
    }

    private suspend fun runRecognizer(image: InputImage): Text =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private fun parseSpatialReceipt(visionText: Text): ParsedReceipt {
        val allElements = mutableListOf<SpatialElement>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    allElements.add(SpatialElement(element.text.trim(), box))
                }
            }
        }

        if (allElements.isEmpty()) {
            return ParsedReceipt("General Expense", null, null, emptyList(), visionText.text)
        }

        // 1. Group spatial elements into horizontal rows based on vertical baseline
        val spatialLines = assembleSpatialLines(allElements)

        // 2. Extract Grand Total
        val total = extractGrandTotal(spatialLines)

        // 3. Extract Merchant Name
        val merchant = extractMerchant(spatialLines)

        // 4. Extract Line Items
        val lineItems = extractLineItems(spatialLines)

        return ParsedReceipt(
            merchant = merchant,
            total = total,
            lineItems = lineItems,
            rawText = visionText.text
        )
    }

    private fun assembleSpatialLines(elements: List<SpatialElement>): List<SpatialLine> {
        val sorted = elements.sortedBy { it.box.top }
        val lines = mutableListOf<SpatialLine>()

        for (el in sorted) {
            val elMidY = (el.box.top + el.box.bottom) / 2
            val elHeight = el.box.bottom - el.box.top

            val matchedLine = lines.firstOrNull { line ->
                val lineMidY = (line.top + line.bottom) / 2
                val tolerance = (elHeight * 0.55).toInt().coerceAtLeast(8)
                abs(elMidY - lineMidY) <= tolerance
            }

            if (matchedLine != null) {
                matchedLine.elements.add(el)
                matchedLine.top = minOf(matchedLine.top, el.box.top)
                matchedLine.bottom = maxOf(matchedLine.bottom, el.box.bottom)
            } else {
                lines.add(
                    SpatialLine(
                        elements = mutableListOf(el),
                        top = el.box.top,
                        bottom = el.box.bottom
                    )
                )
            }
        }

        return lines.sortedBy { it.top }
    }

    private fun extractGrandTotal(lines: List<SpatialLine>): Double? {
        val highPriorityAnchors = listOf(
            "grand total", "net payable", "amount payable", "total payable",
            "balance due", "total amount", "bill total", "net amount", "invoice total"
        )
        val standardAnchors = listOf("total", "due", "paid", "amount", "final")
        val negativeFilter = Regex("""\b(?:\d{10,12}|[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1})\b""")

        // Pass 1: High Priority Anchors
        for (line in lines.reversed()) {
            val text = line.text.lowercase()
            if (highPriorityAnchors.any { text.contains(it) }) {
                extractNumericPrice(line.text, negativeFilter)?.let { return it }
            }
        }

        // Pass 2: Standard Anchors
        for (line in lines.reversed()) {
            val text = line.text.lowercase()
            if (standardAnchors.any { text.contains(it) } && !text.contains("sub total") && !text.contains("subtotal")) {
                extractNumericPrice(line.text, negativeFilter)?.let { return it }
            }
        }

        // Pass 3: Fallback to highest valid currency number in lower half
        val lowerHalfLines = lines.takeLast((lines.size * 0.6).toInt().coerceAtLeast(1))
        val candidateAmounts = mutableListOf<Double>()
        for (line in lowerHalfLines) {
            extractNumericPrice(line.text, negativeFilter)?.let { candidateAmounts.add(it) }
        }

        return candidateAmounts.maxOrNull()
    }

    private fun extractNumericPrice(text: String, negativeFilter: Regex): Double? {
        if (negativeFilter.containsMatchIn(text)) return null

        val priceRegex = Regex("""(?:₹|rs\.?|inr)?\s*(\d{1,6}(?:[.,]\d{2})?)""", RegexOption.IGNORE_CASE)
        val matches = priceRegex.findAll(text)

        val validPrices = matches.mapNotNull { m ->
            val clean = m.groupValues[1].replace(",", ".")
            clean.toDoubleOrNull()
        }.filter { it > 0.0 && it < 1_000_000.0 }.toList()

        return validPrices.lastOrNull()
    }

    private fun extractMerchant(lines: List<SpatialLine>): String {
        val ignoredHeaders = listOf(
            "tax invoice", "retail invoice", "bill of supply", "cash memo",
            "welcome", "invoice", "receipt", "gstin", "order", "date", "table"
        )

        for (line in lines.take(6)) {
            val raw = line.text.trim()
            val lower = raw.lowercase()

            if (raw.length < 3) continue
            if (ignoredHeaders.any { lower.contains(it) }) continue
            if (raw.matches(Regex("""^[\d\s\-_:./]+$"""))) continue

            val cleaned = raw.replace(Regex("""[^a-zA-Z0-9\s&'-]"""), "").trim()
            if (cleaned.length >= 3) {
                return cleaned.split(" ").joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { it.uppercase() }
                }
            }
        }

        return "General Expense"
    }

    private fun extractLineItems(lines: List<SpatialLine>): List<ReceiptItem> {
        val items = mutableListOf<ReceiptItem>()
        val skipKeywords = listOf("total", "tax", "subtotal", "gst", "cgst", "sgst", "discount", "change", "cash", "card")
        val priceRegex = Regex("""(?:₹|rs\.?|inr)?\s*(\d{1,5}(?:[.,]\d{2})?)\s*$""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val text = line.text.trim()
            val lower = text.lowercase()

            if (skipKeywords.any { lower.contains(it) }) continue

            val match = priceRegex.find(text) ?: continue
            val price = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: continue
            val name = text.substring(0, match.range.first).trim().replace(Regex("""[^a-zA-Z0-9\s]"""), "")

            if (name.length >= 2 && price > 0.0) {
                items.add(ReceiptItem(name = name, price = price))
            }
        }

        return items
    }
}
