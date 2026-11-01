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
        // 1. AGGRESSIVE SANITIZATION
        // Destroys mobile numbers, GSTINs, bank accounts, dates, and times to prevent false positive amounts.
        var sanitizedText = rawText
            .replace(Regex("\\b[6-9]\\d{9}\\b"), "") // 10-digit Mobile Numbers
            .replace(Regex("\\b\\d{11,18}\\b"), "") // Bank Accounts / Aadhar / PNR
            .replace(Regex("\\b\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]\\b"), "") // GSTIN
            .replace(Regex("\\b\\d{1,2}[\\/\\-]\\d{1,2}[\\/\\-]\\d{2,4}\\b"), "") // Dates
            .replace(Regex("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b"), "") // Times
            .replace(Regex("(?i)(phone|ph|mob|mobile|tel|pin|zip|pincode|hsn|sac|order|invoice|bill no|table|token)[\\s:-]*\\d+"), "")

        val lines = sanitizedText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val lowerCaseDocument = sanitizedText.lowercase()

        // Balance Sheet Detection
        val statementKeywords = listOf("balance sheet", "assets", "liabilities", "statement of affairs", "sundry debtors", "sundry creditors", "net worth", "equity")
        val balanceSheetHits = statementKeywords.count { lowerCaseDocument.contains(it) }
        val stagedItems = extractStagedBalanceSheetItems(lines)

        if (balanceSheetHits >= 2 || stagedItems.size >= 3) {
            return ParsedReceipt("Financial Statement Extract", (stagedItems.sumOf { it.extractedAmount } / 100.0), System.currentTimeMillis(), rawText, DocumentIntent.FINANCIAL_BALANCE_SHEET_STATEMENT, stagedItems)
        }

        // 2. MERCHANT EXTRACTION
        val blacklistedHeaderTokens = listOf("original", "duplicate", "invoice", "cash memo", "gstin", "phone", "email", "bill to", "ship to", "tax", "retail", "journey", "railway")
        var merchant = "Store / Merchant"
        for (line in lines.take(15)) {
            val lower = line.lowercase()
            if (!blacklistedHeaderTokens.any { lower.contains(it) } && line.length in 3..40 && !line.any { it.isDigit() }) {
                merchant = line
                break
            }
        }

        // Helper to extract all valid currency-like numbers from a string
        fun extractNumbers(text: String): List<Double> {
            val numRegex = Regex("(?:₹|rs\\.?\\s*)?([\\d,]+\\.?\\d*)[/-]?")
            return numRegex.findAll(text.lowercase())
                .map { it.groupValues[1].replace(",", "") }
                .mapNotNull { it.toDoubleOrNull() }
                .toList()
        }

        var detectedTotal: Double? = null

        // 3. TIER 1: Strict Total Keywords (Bottom-Up Scan)
        val strictKeywords = listOf("grand total", "total amount", "final amount", "net payable", "net amount", "balance")
        for (line in lines.reversed()) {
            val lowerLine = line.lowercase()
            if (strictKeywords.any { lowerLine.contains(it) }) {
                val nums = extractNumbers(lowerLine)
                if (nums.isNotEmpty()) {
                    detectedTotal = nums.maxOrNull()
                    break
                }
            }
        }

        // 4. TIER 2: Loose Keywords (Ignores Tax lines)
        if (detectedTotal == null) {
            val looseKeywords = listOf("total", "amount", "sub total", "subtotal", "pay")
            for (line in lines.reversed()) {
                val lowerLine = line.lowercase()
                if (looseKeywords.any { lowerLine.contains(it) } && !lowerLine.contains("tax") && !lowerLine.contains("cgst") && !lowerLine.contains("sgst")) {
                    val nums = extractNumbers(lowerLine)
                    if (nums.isNotEmpty()) {
                        detectedTotal = nums.maxOrNull()
                        break
                    }
                }
            }
        }

        // 5. TIER 3: Indian Railway UTS Format (Rs. 150.00 or Rs. 16/-)
        if (detectedTotal == null) {
            val railwayRegex = Regex("(?i)rs\\.?\\s*([\\d,]+\\.?\\d*)[/-]?")
            for (line in lines) {
                val match = railwayRegex.find(line)
                if (match != null) {
                    val num = match.groupValues[1].replace(",", "").toDoubleOrNull()
                    if (num != null && num > 0.0) {
                        detectedTotal = num
                    }
                }
            }
        }

        // 6. FALLBACK: Largest valid number in the bottom half of the receipt
        if (detectedTotal == null) {
            val bottomHalf = lines.takeLast(lines.size / 2)
            val allBottomNums = bottomHalf.flatMap { extractNumbers(it) }.filter { it > 0.0 }
            if (allBottomNums.isNotEmpty()) {
                detectedTotal = allBottomNums.maxOrNull()
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
