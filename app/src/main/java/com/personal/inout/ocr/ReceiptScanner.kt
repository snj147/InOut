package com.personal.inout.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.personal.inout.data.PocketType
import com.personal.inout.data.StagedStatementLineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class DocumentIntent {
    SINGLE_EXPENSE_RECEIPT,
    FINANCIAL_BALANCE_SHEET_STATEMENT
}

data class ParsedReceipt(
    val merchant: String,
    val total: Double?,
    val dateEpoch: Long?,
    val rawText: String,
    val documentIntent: DocumentIntent = DocumentIntent.SINGLE_EXPENSE_RECEIPT,
    val stagedLineItems: List<StagedStatementLineItem> = emptyList()
)

object ReceiptScanner {

    suspend fun processReceipt(context: Context, imageUri: Uri): ParsedReceipt = processDocumentUri(context, imageUri)

    suspend fun processDocumentUri(context: Context, uri: Uri): ParsedReceipt = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val filename = getFileName(context, uri)

        if (mimeType.contains("text") || mimeType.contains("csv") || filename.endsWith(".csv", ignoreCase = true) || filename.endsWith(".txt", ignoreCase = true)) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            parseDocumentText(text)
        } else if (mimeType.contains("pdf", ignoreCase = true) || filename.endsWith(".pdf", ignoreCase = true)) {
            processPdfUri(context, uri)
        } else {
            val bitmap = loadBitmapFromUri(context, uri)
            processReceiptBitmap(bitmap)
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result = ""
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result.isEmpty()) result = uri.path ?: ""
        return result
    }

    suspend fun processReceiptBitmap(bitmap: Bitmap): ParsedReceipt = withContext(Dispatchers.IO) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val visionText = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        parseDocumentText(visionText.text)
    }

    private suspend fun processPdfUri(context: Context, pdfUri: Uri): ParsedReceipt = withContext(Dispatchers.IO) {
        val fileDescriptor: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(pdfUri, "r")
        if (fileDescriptor == null) return@withContext parseDocumentText("")

        val fullTextBuilder = StringBuilder()
        fileDescriptor.use { pfd ->
            val pdfRenderer = PdfRenderer(pfd)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            val pagesToScan = minOf(pdfRenderer.pageCount, 3)
            for (i in 0 until pagesToScan) {
                val page = pdfRenderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val image = InputImage.fromBitmap(bitmap, 0)
                val pageResult = suspendCancellableCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
                fullTextBuilder.append(pageResult.text).append("\n")
                bitmap.recycle()
            }
            pdfRenderer.close()
        }
        parseDocumentText(fullTextBuilder.toString())
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

    private fun parseDocumentText(rawText: String): ParsedReceipt {
        // AGGRESSIVE PRE-FILTER: Annihilate 10-digit phone numbers, GSTINs, and dates before the scanner even looks at them.
        var sanitizedText = rawText
            .replace(Regex("\\b\\d{10}\\b"), "") // Kills exactly 10-digit numbers (Phones)
            .replace(Regex("\\b\\d{2}[A-Z]{5}\\d{4}[A-Z][A-Z\\d]Z[A-Z\\d]\\b"), "") // Kills GSTINs
            .replace(Regex("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b"), "") // Kills Dates
            .replace(Regex("(?i)phone[\\s:]*\\d+"), "")
            .replace(Regex("(?i)mob[\\s:]*\\d+"), "")

        val lines = sanitizedText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val lowerCaseDocument = sanitizedText.lowercase()

        val statementKeywords = listOf("balance sheet", "assets", "liabilities", "statement of affairs", "sundry debtors", "sundry creditors", "net worth", "equity")
        val balanceSheetHits = statementKeywords.count { lowerCaseDocument.contains(it) }
        val stagedItems = extractStagedBalanceSheetItems(lines)

        if (balanceSheetHits >= 2 || stagedItems.size >= 3) {
            return ParsedReceipt("Financial Statement Extract", (stagedItems.sumOf { it.extractedAmount } / 100.0), System.currentTimeMillis(), rawText, DocumentIntent.FINANCIAL_BALANCE_SHEET_STATEMENT, stagedItems)
        }

        val blacklistedHeaderTokens = listOf("original for recipient", "duplicate for recipient", "tax invoice", "retail invoice", "invoice no", "cash memo", "gstin", "phone:", "email:")
        var merchant = "Store / Merchant"
        for (line in lines.take(10)) {
            val lower = line.lowercase()
            if (!blacklistedHeaderTokens.any { lower.contains(it) } && line.length in 3..40 && !line.any { it.isDigit() }) {
                merchant = line
                break
            }
        }

        // STRICT HEURISTIC: Find words meaning "Total" and grab the number next to them.
        val strictTotalPattern = Pattern.compile("(?i)(?:total amount|grand total|net amount|amount payable|final amount|net payable|sub total|total|net|amount)\\s*[:\\-]?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+\\.\\d{2})")
        var detectedTotal: Double? = null
        
        for (line in lines.reversed()) {
            val matcher = strictTotalPattern.matcher(line)
            if (matcher.find()) {
                val numStr = matcher.group(1)?.replace(",", "")
                val parsed = numStr?.toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    detectedTotal = parsed
                    break
                }
            }
        }

        // FALLBACK: If no keyword found, grab the lowest exact currency format (must have decimal .00)
        if (detectedTotal == null) {
            val currencyPattern = Pattern.compile("(?i)(?:₹|rs\\.?)\\s*([\\d,]+\\.\\d{2})")
            for (line in lines.reversed()) {
                val matcher = currencyPattern.matcher(line)
                if (matcher.find()) {
                    val numStr = matcher.group(1)?.replace(",", "")
                    val parsed = numStr?.toDoubleOrNull()
                    if (parsed != null && parsed > 0.0) {
                        detectedTotal = parsed
                        break
                    }
                }
            }
        }

        return ParsedReceipt(merchant, detectedTotal, System.currentTimeMillis(), rawText, DocumentIntent.SINGLE_EXPENSE_RECEIPT, emptyList())
    }

    private fun extractStagedBalanceSheetItems(lines: List<String>): List<StagedStatementLineItem> {
        val staged = mutableListOf<StagedStatementLineItem>()
        val lineAmountPattern = Pattern.compile("""(.*?)[\s:₹rs\.]*([\d,]+\.?\d{0,2})$""")

        for (line in lines) {
            var label = ""
            var amt = 0.0

            if (line.contains(",")) {
                val tokens = line.split(",")
                for (i in tokens.indices.reversed()) {
                    val potentialNum = tokens[i].replace("\"", "").trim().toDoubleOrNull()
                    if (potentialNum != null && potentialNum > 0) {
                        amt = potentialNum
                        label = if (i > 0) tokens[i-1].replace("\"", "").trim() else "Extracted Account"
                        break
                    }
                }
            } else {
                val matcher = lineAmountPattern.matcher(line)
                if (matcher.find()) {
                    label = matcher.group(1)?.trim() ?: ""
                    amt = matcher.group(2)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                }
            }

            if (label.length in 3..60 && amt > 0.0) {
                val lowerLabel = label.lowercase()
                val inferredType = when {
                    lowerLabel.contains("payable") || lowerLabel.contains("creditor") -> PocketType.PEER_PAYABLE
                    lowerLabel.contains("receivable") || lowerLabel.contains("debtor") || lowerLabel.contains("advance") -> PocketType.PEER_RECEIVABLE
                    lowerLabel.contains("loan") || lowerLabel.contains("borrow") || lowerLabel.contains("mortgage") -> PocketType.LIABILITY_LOAN
                    lowerLabel.contains("card") -> PocketType.CREDIT_CARD
                    lowerLabel.contains("gold") || lowerLabel.contains("property") || lowerLabel.contains("vehicle") || lowerLabel.contains("computer") || lowerLabel.contains("equipment") || lowerLabel.contains("asset") -> PocketType.FIXED_ASSET
                    lowerLabel.contains("fund") || lowerLabel.contains("share") || lowerLabel.contains("equity") || lowerLabel.contains("investment") -> PocketType.INVESTMENT
                    lowerLabel.contains("capital") || lowerLabel.contains("retained") || lowerLabel.contains("profit") -> PocketType.GOAL_POT
                    lowerLabel.contains("wallet") || lowerLabel.contains("prepaid") -> PocketType.PREPAID_WALLET
                    else -> PocketType.LIQUID
                }
                staged.add(StagedStatementLineItem(label, inferredType, (amt * 100).toLong(), true))
            }
        }
        return staged
    }
}
