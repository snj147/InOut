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

    /**
     * BRD Rule 29: Heuristic Document Classifier Guardrail.
     * Differentiates a single point-of-sale receipt from a multi-line balance sheet statement.
     */
    private fun parseReceiptText(rawText: String): ParsedReceipt {
        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val lowerCaseDocument = rawText.lowercase()

        // 1. Detect Financial Statement / Balance Sheet Markers (Rule 29.3)
        val statementKeywords = listOf(
            "balance sheet", "assets and liabilities", "statement of affairs",
            "schedule iii", "itr-3", "sundry debtors", "sundry creditors",
            "capital account", "fixed assets", "trial balance"
        )

        val isBalanceSheetStatement = statementKeywords.count { lowerCaseDocument.contains(it) } >= 2

        if (isBalanceSheetStatement) {
            val stagedItems = extractStagedBalanceSheetItems(lines)
            return ParsedReceipt(
                merchant = "Statutory Balance Sheet",
                total = stagedItems.sumOf { it.extractedAmount },
                dateEpoch = System.currentTimeMillis(),
                rawText = rawText,
                documentIntent = DocumentIntent.FINANCIAL_BALANCE_SHEET_STATEMENT,
                stagedLineItems = stagedItems
            )
        }

        // 2. Standard Single-Merchant Point-of-Sale Receipt Parsing
        val blacklistedHeaderTokens = listOf(
            "original for recipient",
            "duplicate for recipient",
            "triplicate for supplier",
            "tax invoice",
            "retail invoice",
            "invoice no",
            "cash memo",
            "bill to",
            "ship to",
            "gstin",
            "phone:",
            "email:"
        )

        var merchant = "Store / Merchant"
        for (line in lines.take(10)) {
            val lower = line.lowercase()
            val isBlacklisted = blacklistedHeaderTokens.any { lower.contains(it) }
            if (!isBlacklisted && line.length in 3..40 && !line.any { it.isDigit() }) {
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

        // Fallback: search highest formatted decimal currency candidate
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

    /**
     * Extracts multi-line assets and liabilities from statement OCR scans (Rule 29.4).
     */
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
                                label.contains("car", ignoreCase = true) -> PocketType.FIXED_ASSET

                        label.contains("fund", ignoreCase = true) ||
                                label.contains("share", ignoreCase = true) ||
                                label.contains("equity", ignoreCase = true) ||
                                label.contains("ppf", ignoreCase = true) ||
                                label.contains("fd", ignoreCase = true) -> PocketType.INVESTMENT

                        label.contains("loan", ignoreCase = true) ||
                                label.contains("borrow", ignoreCase = true) -> PocketType.LIABILITY_LOAN

                        label.contains("card", ignoreCase = true) -> PocketType.CREDIT_CARD

                        label.contains("cash", ignoreCase = true) -> PocketType.LIQUID

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
