package com.personal.inout.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
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

    suspend fun processDocumentUri(context: Context, uri: Uri): ParsedReceipt = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        if (mimeType.contains("pdf", ignoreCase = true) || uri.toString().endsWith(".pdf", ignoreCase = true)) {
            processPdfUri(context, uri)
        } else {
            val bitmap = loadBitmapFromUri(context, uri)
            processReceiptBitmap(bitmap)
        }
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
        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val lowerCaseDocument = rawText.lowercase()

        val statementKeywords = listOf(
            "balance sheet", "assets and liabilities", "statement of affairs",
            "schedule iii", "itr-3", "sundry debtors", "sundry creditors",
            "capital account", "fixed assets", "trial balance", "portfolio valuation",
            "account statement", "net worth statement"
        )

        val balanceSheetHits = statementKeywords.count { lowerCaseDocument.contains(it) }
        val stagedItems = extractStagedBalanceSheetItems(lines)

        val isBalanceSheetStatement = balanceSheetHits >= 1 || stagedItems.size >= 3

        if (isBalanceSheetStatement && stagedItems.isNotEmpty()) {
            return ParsedReceipt(
                merchant = "Audited Financial Statement",
                total = stagedItems.sumOf { it.extractedAmount },
                dateEpoch = System.currentTimeMillis(),
                rawText = rawText,
                documentIntent = DocumentIntent.FINANCIAL_BALANCE_SHEET_STATEMENT,
                stagedLineItems = stagedItems
            )
        }

        // Single Point-of-Sale Extraction
        val blacklistedHeaderTokens = listOf(
            "original for recipient", "duplicate for recipient", "tax invoice",
            "retail invoice", "invoice no", "cash memo", "gstin", "phone:", "email:"
        )

        var merchant = "Store / Merchant"
        for (line in lines.take(10)) {
            val lower = line.lowercase()
            if (!blacklistedHeaderTokens.any { lower.contains(it) } && line.length in 3..40 && !line.any { it.isDigit() }) {
                merchant = line
                break
            }
        }

        val settlementLabelPattern = Pattern.compile(
            """(?i)(?:total\s*amount|grand\s*total|net\s*amount|amount\s*payable|sub\s*total)[\s:₹rs\.]*([\d,]+\.?\d{0,2})"""
        )

        var detectedTotal: Double? = null
        for (line in lines.reversed()) {
            val matcher = settlementLabelPattern.matcher(line)
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
            val currencyPattern = Pattern.compile("""(?i)[₹rs\.\s]*([\d,]+\.\d{2})""")
            val candidates = mutableListOf<Double>()
            for (line in lines) {
                val matcher = currencyPattern.matcher(line)
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
            rawText = rawText,
            documentIntent = DocumentIntent.SINGLE_EXPENSE_RECEIPT,
            stagedLineItems = emptyList()
        )
    }

    private fun extractStagedBalanceSheetItems(lines: List<String>): List<StagedStatementLineItem> {
        val staged = mutableListOf<StagedStatementLineItem>()
        val lineAmountPattern = Pattern.compile("""(.*?)[\s:₹rs\.]*([\d,]+\.?\d{0,2})$""")

        for (line in lines) {
            val matcher = lineAmountPattern.matcher(line)
            if (matcher.find()) {
                val label = matcher.group(1)?.trim() ?: continue
                val amountStr = matcher.group(2)?.replace(",", "") ?: continue
                val amt = amountStr.toDoubleOrNull() ?: continue

                if (label.length in 3..50 && amt > 0.0) {
                    val inferredType = when {
                        label.contains("gold", ignoreCase = true) ||
                                label.contains("property", ignoreCase = true) ||
                                label.contains("vehicle", ignoreCase = true) ||
                                label.contains("flat", ignoreCase = true) ||
                                label.contains("land", ignoreCase = true) ||
                                label.contains("car", ignoreCase = true) -> PocketType.FIXED_ASSET

                        label.contains("fund", ignoreCase = true) ||
                                label.contains("share", ignoreCase = true) ||
                                label.contains("equity", ignoreCase = true) ||
                                label.contains("ppf", ignoreCase = true) ||
                                label.contains("nps", ignoreCase = true) ||
                                label.contains("deposit", ignoreCase = true) ||
                                label.contains("fd", ignoreCase = true) -> PocketType.INVESTMENT

                        label.contains("loan", ignoreCase = true) ||
                                label.contains("borrow", ignoreCase = true) ||
                                label.contains("mortgage", ignoreCase = true) ||
                                label.contains("overdraft", ignoreCase = true) -> PocketType.LIABILITY_LOAN

                        label.contains("card", ignoreCase = true) -> PocketType.CREDIT_CARD

                        label.contains("wallet", ignoreCase = true) -> PocketType.PREPAID_WALLET

                        label.contains("receivable", ignoreCase = true) ||
                                label.contains("debtor", ignoreCase = true) -> PocketType.PEER_RECEIVABLE

                        else -> PocketType.LIQUID
                    }

                    staged.add(
                        StagedStatementLineItem(
                            rawExtractedName = label,
                            inferredType = inferredType,
                            extractedAmount = amt,
                            isSelectedForCommit = true
                        )
                    )
                }
            }
        }
        return staged
    }
}
