package com.personal.inout.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.personal.inout.data.PocketType
import com.personal.inout.data.StagedStatementLineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

enum class DocumentIntent {
    SINGLE_EXPENSE_RECEIPT,
    FINANCIAL_BALANCE_SHEET_STATEMENT
}

/** One possible "total" the scanner found. Show 2-3 of these as tappable chips when unsure. */
data class AmountCandidate(
    val amount: Double,
    val label: String,
    val score: Double
)

data class ParsedReceipt(
    val merchant: String,
    val total: Double?,
    val dateEpoch: Long?,
    val rawText: String,
    val documentIntent: DocumentIntent = DocumentIntent.SINGLE_EXPENSE_RECEIPT,
    val stagedLineItems: List<StagedStatementLineItem> = emptyList(),
    /** 0.0 - 1.0. Below ~0.6 you should ask the user to confirm. */
    val confidence: Double = 0.0,
    /** Top distinct amounts, best first (includes the chosen total). */
    val alternativeTotals: List<AmountCandidate> = emptyList(),
    /** True when the scan is ambiguous: show alternativeTotals and let the user pick. */
    val needsConfirmation: Boolean = false,
    /** False when no date was found on the document and dateEpoch is just "now". */
    val dateDetected: Boolean = false
)

object ReceiptScanner {

    private const val MAX_PDF_PAGES = 8
    private const val MAX_BITMAP_SIDE = 3000

    // =====================================================================
    // ENTRY POINTS
    // =====================================================================

    suspend fun processReceipt(context: Context, imageUri: Uri): ParsedReceipt =
        processDocumentUri(context, imageUri)

    suspend fun processDocumentUri(context: Context, uri: Uri): ParsedReceipt = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val filename = getFileName(context, uri)

        when {
            mimeType.contains("text") || mimeType.contains("csv") ||
                filename.endsWith(".csv", true) || filename.endsWith(".txt", true) -> {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                parseRows(rowsFromPlainText(text), text, structured = true)
            }
            mimeType.contains("pdf", true) || filename.endsWith(".pdf", true) -> processPdfUri(context, uri)
            else -> processReceiptBitmap(loadBitmapFromUri(context, uri))
        }
    }

    suspend fun processReceiptBitmap(bitmap: Bitmap): ParsedReceipt = withContext(Dispatchers.IO) {
        val scaled = downscale(bitmap)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val vt = recognizer.recognize(InputImage.fromBitmap(scaled, 0))
            parseRows(rowsFromVision(vt, 0, 1, scaled.height), vt.text, structured = false)
        } finally {
            recognizer.close()
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private suspend fun processPdfUri(context: Context, pdfUri: Uri): ParsedReceipt = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            ?: return@withContext parseRows(emptyList(), "", structured = false)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var renderer: PdfRenderer? = null
        val rows = mutableListOf<RawRow>()
        val fullText = StringBuilder()

        try {
            renderer = PdfRenderer(pfd)
            val total = renderer.pageCount
            // Totals live on the LAST pages. Scan first 2 + last 6 when the PDF is long.
            val pages = if (total <= MAX_PDF_PAGES) (0 until total).toList()
            else (listOf(0, 1) + ((total - (MAX_PDF_PAGES - 2)) until total)).distinct()

            pages.forEachIndexed { order, pageIndex ->
                val bitmap = renderPage(renderer, pageIndex)
                try {
                    val vt = recognizer.recognize(InputImage.fromBitmap(bitmap, 0))
                    rows += rowsFromVision(vt, order, pages.size, bitmap.height)
                    fullText.append(vt.text).append('\n')
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
            recognizer.close()
        }
        parseRows(rows, fullText.toString(), structured = false)
    }

    private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap {
        val page = renderer.openPage(index)
        return try {
            val scale = (1800f / page.width).coerceIn(1f, 3f)
            val bmp = Bitmap.createBitmap(
                (page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888
            )
            bmp.eraseColor(Color.WHITE) // PdfRenderer draws on transparent; OCR needs a white page
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } finally {
            page.close()
        }
    }

    private fun downscale(src: Bitmap): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= MAX_BITMAP_SIDE) return src
        val f = MAX_BITMAP_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * f).toInt(), (src.height * f).toInt(), true)
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result = ""
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index) ?: ""
                }
            }
        }
        if (result.isEmpty()) result = uri.path ?: ""
        return result
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

    private suspend fun TextRecognizer.recognize(image: InputImage): Text =
        suspendCancellableCoroutine { cont ->
            process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    // =====================================================================
    // LAYOUT: turn OCR output into visual ROWS (label + amount on the same row)
    // =====================================================================

    private class RawRow(val text: String, val yFrac: Double, val height: Double)

    private class Row(
        val index: Int,
        val original: String,
        val fixed: String,   // OCR digit confusions + currency normalised
        val masked: String,  // fixed, with phones / GSTIN / dates / ids blanked out
        val yFrac: Double,   // 0 = top of document, 1 = bottom (across pages)
        val heightRatio: Double // row text height vs median row height (big font = grand total)
    )

    private fun rowsFromVision(vt: Text, page: Int, pageCount: Int, pageHeight: Int): List<RawRow> {
        class Item(val text: String, val left: Int, val cy: Double, val h: Double)

        val items = vt.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val b = line.boundingBox ?: return@mapNotNull null
            Item(line.text, b.left, b.exactCenterY().toDouble(), b.height().toDouble())
        }.sortedBy { it.cy }

        val groups = mutableListOf<MutableList<Item>>()
        for (item in items) {
            val g = groups.lastOrNull()
            if (g != null) {
                val gcy = g.map { it.cy }.average()
                val gh = g.map { it.h }.average()
                if (abs(item.cy - gcy) <= 0.55 * min(item.h, gh)) {
                    g.add(item)
                    continue
                }
            }
            groups.add(mutableListOf(item))
        }

        return groups.map { g ->
            val ordered = g.sortedBy { it.left }
            RawRow(
                text = ordered.joinToString(" ") { it.text },
                yFrac = (page + g.map { it.cy }.average() / max(pageHeight, 1)) / max(pageCount, 1),
                height = g.map { it.h }.average()
            )
        }
    }

    private fun rowsFromPlainText(text: String): List<RawRow> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        return lines.mapIndexed { i, l -> RawRow(l, i.toDouble() / max(lines.size, 1), 1.0) }
    }

    // =====================================================================
    // TEXT CLEANING
    // =====================================================================

    private val ocrNumericToken = Regex("[0-9OoIlSB][0-9OoIlSB.,]{2,}")
    private val currencyWord = Regex("(?i)(?<![A-Za-z])(?:rs|inr)\\.?\\s*(?=\\d)")
    private const val CURRENCY_CHARS = "₹\$€£"

    /** 4S0.OO -> 450.00 . Only touches tokens that are mostly digits. */
    private fun fixOcrDigits(s: String): String = ocrNumericToken.replace(s) { m ->
        val t = m.value
        val digits = t.count { it.isDigit() }
        val letters = t.count { it.isLetter() }
        if (digits < 2 || letters > 2) t
        else t.map {
            when (it) {
                'O', 'o' -> '0'
                'I', 'l' -> '1'
                'S' -> '5'
                'B' -> '8'
                else -> it
            }
        }.joinToString("")
    }

    private fun normalizeCurrency(s: String): String = currencyWord.replace(s, "₹ ")

    private val monthNames = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"
    private val dateNumeric = Regex("""(?<!\d)(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})(?!\d)""")
    private val dateIso = Regex("""(?<!\d)(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})(?!\d)""")
    private val dateNamed = Regex(
        """(?i)(?<!\d)(\d{1,2})(?:st|nd|rd|th)?[\s\-]*($monthNames)[a-z]*[\s\-,.]*(\d{4}|\d{2})(?!\d)"""
    )
    private val dateNamedUs = Regex(
        """(?i)\b($monthNames)[a-z]*\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})(?!\d)"""
    )
    private val timeRegex = Regex("""(?i)(?<!\d)\d{1,2}:\d{2}(?::\d{2})?(?:\s?[ap]\.?m\.?)?""")
    private val gstinRegex = Regex("""(?<![A-Za-z0-9])\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z](?![A-Za-z0-9])""")
    private val phoneRegex = Regex("""(?<!\d)(?:\+?91[\s-]?)?[6-9]\d{9}(?!\d)""")
    private val phoneKeywordRegex =
        Regex("""(?i)\b(?:ph|phone|mob|mobile|tel|cell|contact|call|whatsapp)\b\.?[\s:#\-]*\+?\d[\d\s\-]{5,}""")
    private val longDigitsRegex = Regex("""(?<![\d.,])\d{11,}(?!\d)""")
    private val strongIdRegex = Regex(
        """(?i)\b(?:gstin|gst\s*no|table|token|pin(?:code)?|zip|hsn|sac|kot|txn|uid|ref)\b[\s.:#\-]*(?:no\.?)?[\s.:#\-]*[A-Za-z0-9/\-]*\d[A-Za-z0-9/\-]*"""
    )
    // "Bill 450" could be a real amount, so these only count as IDs when followed by No / # / ID.
    private val weakIdRegex = Regex(
        """(?i)\b(?:invoice|inv|bill|receipt|order|ord|trans(?:action)?|reference|cash\s*memo)\s*(?:no\.?|number|num|#|id)\s*[:#.\-]*\s*[A-Za-z0-9/\-]*\d[A-Za-z0-9/\-]*"""
    )

    private fun maskNoise(s: String): String {
        var t = s
        for (r in listOf(
            dateNamed, dateNamedUs, dateIso, dateNumeric, timeRegex,
            gstinRegex, phoneKeywordRegex, phoneRegex, strongIdRegex, weakIdRegex, longDigitsRegex
        )) t = r.replace(t, " ")
        return t
    }

    // =====================================================================
    // MONEY PARSING
    // =====================================================================

    private val amountRegex = Regex(
        """(?<![\w.,])(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:[.,]\d{1,2})?)(?!\d|[.,]\d|\s*%)"""
    )
    private val decimalTail = Regex("""[.,]\d{1,2}$""")

    private class Money(val value: Double, val raw: String, val hasDecimals: Boolean, val hasCurrency: Boolean)

    private fun findMoney(text: String): List<Money> =
        amountRegex.findAll(text).mapNotNull { m ->
            val raw = m.groupValues[1]
            val v = normalizeAmount(raw) ?: return@mapNotNull null
            val before = text.substring(max(0, m.range.first - 2), m.range.first)
            Money(v, raw, decimalTail.containsMatchIn(raw), before.any { it in CURRENCY_CHARS })
        }.toList()

    /** Handles 1,234.50 / 1.234,50 / 450,00 / 1,23,456 / 450.00 */
    private fun normalizeAmount(raw: String): Double? {
        var s = raw.replace(" ", "")
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 -> {
                val after = s.length - lastComma - 1
                if (s.count { it == ',' } == 1 && after in 1..2) s.replace(',', '.') else s.replace(",", "")
            }
            lastDot >= 0 -> if (s.count { it == '.' } > 1) s.replace(".", "") else s
            else -> s
        }
        return s.toDoubleOrNull()
    }

    // =====================================================================
    // MAIN PARSE
    // =====================================================================

    private fun parseRows(rawRows: List<RawRow>, rawText: String, structured: Boolean): ParsedReceipt {
        val heights = rawRows.map { it.height }.sorted()
        val median = (if (heights.isEmpty()) 1.0 else heights[heights.size / 2]).coerceAtLeast(1.0)

        val rows = rawRows.mapIndexed { i, r ->
            val fixed = normalizeCurrency(fixOcrDigits(r.text))
            Row(i, r.text, fixed, maskNoise(fixed), r.yFrac, r.height / median)
        }

        val date = extractDate(rows)
        val dateEpoch = date ?: System.currentTimeMillis()

        // ---- Balance-sheet / statement detection (needs real keywords, not just "3 lines with numbers") ----
        val lowerDoc = rows.joinToString("\n") { it.masked }.lowercase()
        val hits = statementKeywords.count { lowerDoc.contains(it) }
        val staged = extractStagedBalanceSheetItems(rows.map { it.masked })
        val isStatement = (hits >= 2 && staged.size >= 2) || (structured && hits >= 1 && staged.size >= 3)
        if (isStatement) {
            return ParsedReceipt(
                merchant = "Financial Statement Extract",
                total = staged.sumOf { it.extractedAmount } / 100.0,
                dateEpoch = dateEpoch,
                rawText = rawText,
                documentIntent = DocumentIntent.FINANCIAL_BALANCE_SHEET_STATEMENT,
                stagedLineItems = staged,
                confidence = 0.7,
                dateDetected = date != null
            )
        }

        val merchant = extractMerchant(rows)
        val ranked = pickTotal(rows)
        val best = ranked.firstOrNull()

        val alternatives = ranked
            .distinctBy { (it.value * 100).roundToLong() }
            .take(3)
            .map { AmountCandidate(it.value, it.label, it.score) }

        var confidence = 0.0
        var needsConfirmation = true
        if (best != null) {
            val runnerUp = ranked.firstOrNull { (it.value * 100).roundToLong() != (best.value * 100).roundToLong() }
            val margin = if (runnerUp == null) 10.0 else best.score - runnerUp.score
            confidence = 0.6 * (best.score / 16.0).coerceIn(0.0, 1.0) + 0.4 * (margin / 3.0).coerceIn(0.0, 1.0)
            needsConfirmation = best.score < 8.0 || margin < 2.0
        }

        return ParsedReceipt(
            merchant = merchant,
            total = best?.value,
            dateEpoch = dateEpoch,
            rawText = rawText,
            documentIntent = DocumentIntent.SINGLE_EXPENSE_RECEIPT,
            stagedLineItems = emptyList(),
            confidence = confidence,
            alternativeTotals = alternatives,
            needsConfirmation = needsConfirmation,
            dateDetected = date != null
        )
    }

    // =====================================================================
    // TOTAL DETECTION: collect candidates, score them, pick the best
    // =====================================================================

    private class Cand(
        val value: Double,
        val label: String,
        val weight: Double,
        val score: Double,
        val rawToken: String
    ) {
        fun copy(value: Double = this.value, score: Double = this.score) =
            Cand(value, label, weight, score, rawToken)
    }

    private val keywordWeights = linkedMapOf(
        "grand total" to 10.0,
        "total invoice value" to 10.0,
        "total payable" to 10.0,
        "net payable" to 10.0,
        "amount payable" to 10.0,
        "payable amount" to 10.0,
        "total due" to 10.0,
        "amount due" to 10.0,
        "balance due" to 10.0,
        "net amount" to 9.0,
        "final amount" to 9.0,
        "bill amount" to 9.0,
        "invoice amount" to 9.0,
        "invoice total" to 9.0,
        "invoice value" to 9.0,
        "total amount" to 9.0,
        "amount to pay" to 9.0,
        "net total" to 8.5,
        "you pay" to 8.0,
        "to pay" to 8.0,
        "payable" to 6.0,
        "total" to 6.0,
        "amount" to 3.5,
        "amt" to 3.0,
        "sub total" to 2.5,
        "subtotal" to 2.5,
        "amount paid" to 2.5,
        "paid amount" to 2.5,
        "total paid" to 2.5,
        "paid" to 2.0
    )
    private val spacelessWeights = keywordWeights.mapKeys { it.key.replace(" ", "") }
    private val keywordRegex = Regex(
        "(?i)\\b(?:" +
            keywordWeights.keys.sortedByDescending { it.length }.joinToString("|") { it.replace(" ", "\\s*") } +
            ")\\b"
    )

    // Words that make a line NOT a payable total (tax lines, tender lines, counts, etc.)
    private val excludeWords = Regex(
        """(?i)\b(?:tax|gst|cgst|sgst|igst|utgst|vat|cess|discount|disc|saving|savings|round|rounding|change|cash|tender|tendered|received|refund|item|items|qty|tip|gratuity|service|delivery|packing|shipping|points|mrp)\b"""
    )
    private val prefixOverride = Regex("(?i)\\b(?:incl|including|inclusive|after|with|net)\\b")
    private val leadExclusion = Regex(
        """(?i)^[\s:.\-(]*(?:no\.?\s*of\s*)?(?:items?|qty|quantity|pcs|pieces|units|nos?\b|count|savings?|saved|discount|disc|gst|cgst|sgst|igst|vat|tax|cess|points|weight|wt)"""
    )
    private val inclusiveStrip = Regex("""(?i)\(?\b(?:incl(?:uding|usive|\.)?|inc\.?|after|with)\b[^\d)]*\)?""")
    private val stopWords = Regex(
        """(?i)\b(?:paid|cash|change|tender|tendered|received|balance|tax|gst|cgst|sgst|igst|vat|cess|discount|savings?|round|upi|card|visa|mastercard)\b"""
    )
    private val taxRowRegex = Regex("""(?i)\b(?:cgst|sgst|igst|utgst|vat|cess|service\s*charge|tax)\b""")
    private val taxRowExclude = Regex("""(?i)\b(?:total|incl|inclusive|including|invoice)\b""")

    private fun pickTotal(rows: List<Row>): List<Cand> {
        val cands = mutableListOf<Cand>()

        for (row in rows) {
            val text = row.masked
            val matches = keywordRegex.findAll(text).toList()
            if (matches.isEmpty()) continue

            var prevEnd = 0
            matches.forEachIndexed { mi, m ->
                val start = m.range.first
                val end = m.range.last + 1
                val prefix = text.substring(prevEnd, start)
                prevEnd = end

                val key = m.value.lowercase().replace(Regex("\\s+"), "")
                val weight = spacelessWeights[key] ?: return@forEachIndexed

                // Only look at words right before the keyword (after the last number), e.g. "CGST Amount"
                val tail = prefix.substring(prefix.indexOfLast { it.isDigit() } + 1)
                if (excludeWords.containsMatchIn(tail) && !prefixOverride.containsMatchIn(tail)) return@forEachIndexed

                val segEnd = if (mi + 1 < matches.size) matches[mi + 1].range.first else text.length
                val segment = text.substring(end, segEnd)
                if (leadExclusion.containsMatchIn(segment)) return@forEachIndexed // "Total Items 12"

                val stripped = inclusiveStrip.replace(segment, " ")
                val cut = stopWords.find(stripped)?.range?.first ?: stripped.length
                val cleaned = stripped.substring(0, cut)

                var amounts = findMoney(cleaned).filter { it.value > 0.0 }
                var penalty = 0.0
                var scoreRow = row

                // Label and amount landed on different rows -> look at the next row if it is amount-only.
                if (amounts.isEmpty() && mi == matches.lastIndex && stripped.none { it.isLetter() }) {
                    val next = rows.getOrNull(row.index + 1)
                    if (next != null && next.masked.none { it.isLetter() }) {
                        amounts = findMoney(next.masked).filter { it.value > 0.0 }
                        penalty = 1.5
                        scoreRow = next
                    }
                }
                if (amounts.isEmpty()) return@forEachIndexed

                val pick = amounts.lastOrNull { it.hasDecimals } ?: amounts.last()
                val score = weight +
                    scoreRow.yFrac * 2.0 +
                    (if (scoreRow.heightRatio >= 1.25) 1.0 else 0.0) +
                    (if (pick.hasDecimals) 0.5 else 0.0) +
                    (if (pick.hasCurrency) 0.5 else 0.0) -
                    penalty

                cands += Cand(pick.value, m.value.trim().lowercase(), weight, score, pick.raw)
            }
        }

        if (cands.isEmpty()) return fallbackCandidates(rows)

        // ---- cross-candidate evidence ----
        val (itemsSum, itemCount) = sumItems(rows)
        val taxSum = sumTax(rows)

        fun reconcileBonus(v: Double): Double = when {
            itemCount < 2 || itemsSum <= 0.0 -> 0.0
            abs(v - (itemsSum + taxSum)) <= 1.0 -> 2.5
            abs(v - itemsSum) <= 1.0 -> 1.5
            else -> 0.0
        }

        val strongMax = cands.filter { it.weight >= 6.0 }.maxOfOrNull { it.value }
        val freq = cands.filter { it.weight >= 2.0 }.groupingBy { (it.value * 100).roundToLong() }.eachCount()

        val scored = cands.map { c ->
            var cur = c
            // Rupee sign misread as "7": 7450.00 -> 450.00 when only the stripped value matches the item sum
            if (cur.rawToken.trim().startsWith("7") && cur.value in 7000.0..79999.99) {
                val alt = normalizeAmount(cur.rawToken.trim().drop(1))
                if (alt != null && alt > 0 && reconcileBonus(alt) > 0 && reconcileBonus(cur.value) == 0.0) {
                    cur = cur.copy(value = alt)
                }
            }
            var s = cur.score
            if (cur.weight >= 6.0 && strongMax != null && cur.value == strongMax) s += 1.0
            if (cur.weight >= 2.0) {
                val n = freq[(cur.value * 100).roundToLong()] ?: 1
                s += min(2.0, (n - 1).toDouble())
            }
            s += reconcileBonus(cur.value)
            cur.copy(score = s)
        }
        return scored.sortedByDescending { it.score }
    }

    /** No keyword found (UPI screenshots, bare receipts): rank by currency symbol, big font, position, size. */
    private fun fallbackCandidates(rows: List<Row>): List<Cand> {
        val pool = mutableListOf<Cand>()
        for (row in rows) {
            val t = row.masked
            if (excludeWords.containsMatchIn(t)) continue
            for (m in findMoney(t)) {
                if (m.value <= 0.0 || m.value >= 10_000_000.0) continue
                if (!m.hasDecimals && !m.hasCurrency) continue
                val s = 1.0 + row.yFrac * 2.0 +
                    (if (m.hasCurrency) 1.5 else 0.0) +
                    (if (m.hasDecimals) 0.5 else 0.0) +
                    (if (row.heightRatio >= 1.4) 1.5 else 0.0)
                pool += Cand(m.value, "largest amount", 1.0, s, m.raw)
            }
        }
        val mx = pool.maxOfOrNull { it.value }
        return pool.map { if (it.value == mx) it.copy(score = it.score + 1.0) else it }
            .sortedByDescending { it.score }
    }

    /** Sum of the last amount on every "item-looking" row (letters, then a number). Used only as evidence. */
    private fun sumItems(rows: List<Row>): Pair<Double, Int> {
        var sum = 0.0
        var count = 0
        for (row in rows) {
            val t = row.masked
            if (keywordRegex.containsMatchIn(t) || excludeWords.containsMatchIn(t)) continue
            val last = findMoney(t).lastOrNull { it.value > 0.0 } ?: continue
            val label = t.substring(0, max(0, t.lastIndexOf(last.raw)))
            if (label.count { it.isLetter() } < 3) continue
            sum += last.value
            count++
        }
        return sum to count
    }

    private fun sumTax(rows: List<Row>): Double {
        var sum = 0.0
        for (row in rows) {
            val t = row.masked
            if (!taxRowRegex.containsMatchIn(t) || taxRowExclude.containsMatchIn(t)) continue
            val last = findMoney(t).lastOrNull { it.value > 0.0 } ?: continue
            sum += last.value
        }
        return sum
    }

    // =====================================================================
    // MERCHANT + DATE
    // =====================================================================

    private val merchantBlacklist = listOf(
        "original", "duplicate", "invoice", "cash memo", "gstin", "gst", "phone", "email", "bill to", "ship to",
        "tax", "retail", "journey", "railway", "road", "street", "nagar", "colony", "near", "opp", "floor",
        "www", "http", ".com", "@", "welcome", "thank", "customer copy", "receipt"
    )

    private fun extractMerchant(rows: List<Row>): String {
        val candidates = rows.take(8).filter { r ->
            val t = r.original.trim()
            val lower = t.lowercase()
            val letters = t.count { it.isLetter() }
            t.length in 3..40 &&
                letters >= 3 &&
                letters >= t.length * 0.6 &&
                t.count { it.isDigit() } <= 2 &&
                merchantBlacklist.none { lower.contains(it) }
        }
        // Biggest font wins; ties go to whichever is higher up.
        return candidates
            .sortedWith(compareByDescending<Row> { (it.heightRatio * 5).toInt() }.thenBy { it.index })
            .firstOrNull()?.original?.trim() ?: "Store / Merchant"
    }

    private val dateKeyword = Regex("""(?i)\b(?:date|dt|dated|bill\s*date|invoice\s*date)\b""")

    private fun extractDate(rows: List<Row>): Long? {
        val limit = System.currentTimeMillis() + 2 * 86_400_000L
        var firstAny: Long? = null
        for (row in rows) {
            val hasKeyword = dateKeyword.containsMatchIn(row.fixed)
            for (epoch in datesIn(row.fixed)) {
                if (epoch > limit) continue
                if (hasKeyword) return epoch
                if (firstAny == null) firstAny = epoch
            }
        }
        return firstAny
    }

    private fun datesIn(text: String): List<Long> {
        val out = mutableListOf<Long>()

        dateNumeric.findAll(text).forEach { m ->
            var d = m.groupValues[1].toInt()
            var mo = m.groupValues[2].toInt()
            var y = m.groupValues[3].toInt()
            if (y < 100) y += 2000
            if (mo > 12 && d <= 12) { val t = d; d = mo; mo = t } // mm/dd/yyyy
            epochOf(y, mo, d)?.let { out += it }
        }
        dateIso.findAll(text).forEach { m ->
            epochOf(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { out += it }
        }
        dateNamed.findAll(text).forEach { m ->
            var y = m.groupValues[3].toInt()
            if (y < 100) y += 2000
            epochOf(y, monthIndex(m.groupValues[2]), m.groupValues[1].toInt())?.let { out += it }
        }
        dateNamedUs.findAll(text).forEach { m ->
            epochOf(m.groupValues[3].toInt(), monthIndex(m.groupValues[1]), m.groupValues[2].toInt())?.let { out += it }
        }
        return out
    }

    private fun monthIndex(name: String): Int = monthNames.split("|").indexOf(name.lowercase().take(3)) + 1

    private fun epochOf(y: Int, m: Int, d: Int): Long? {
        if (y !in 2000..2100 || m !in 1..12 || d !in 1..31) return null
        return try {
            val cal = Calendar.getInstance()
            cal.isLenient = false
            cal.clear()
            cal.set(y, m - 1, d, 12, 0, 0)
            cal.timeInMillis
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    // =====================================================================
    // BALANCE SHEET / STATEMENT EXTRACTION
    // =====================================================================

    private val statementKeywords = listOf(
        "balance sheet", "assets", "liabilities", "statement of affairs",
        "sundry debtors", "sundry creditors", "net worth", "equity"
    )

    private val statementLine = Regex(
        """^(.*?)[\s:.,\-]*(?:₹)?\s*([\d,]+(?:\.\d{1,2})?)\s*(?:cr|dr)?$""",
        RegexOption.IGNORE_CASE
    )

    private fun extractStagedBalanceSheetItems(lines: List<String>): List<StagedStatementLineItem> {
        val staged = mutableListOf<StagedStatementLineItem>()

        for (rawLine in lines) {
            val line = rawLine.replace("\"", "").trim()
            if (line.isBlank()) continue

            var label = ""
            var amt = 0.0

            val m = statementLine.find(line)
            if (m != null) {
                label = m.groupValues[1].trim().trim(':', '-', '.', ',')
                amt = normalizeAmount(m.groupValues[2]) ?: 0.0
            }
            // Multi-column CSV fallback: Category,Account,Amount
            if ((label.length < 3 || amt <= 0.0) && line.contains(",")) {
                val tokens = line.split(",")
                for (i in tokens.indices.reversed()) {
                    val n = tokens[i].trim().replace(" ", "").toDoubleOrNull()
                    if (n != null && n > 0) {
                        amt = n
                        label = if (i > 0) tokens[i - 1].trim() else "Extracted Account"
                        break
                    }
                }
            }

            if (label.length in 3..60 && label.count { it.isLetter() } >= 3 && amt > 0.0) {
                val l = label.lowercase()
                val type = when {
                    l.contains("payable") || l.contains("creditor") -> PocketType.PEER_PAYABLE
                    l.contains("receivable") || l.contains("debtor") || l.contains("advance") -> PocketType.PEER_RECEIVABLE
                    l.contains("loan") || l.contains("borrow") || l.contains("mortgage") -> PocketType.LIABILITY_LOAN
                    l.contains("card") -> PocketType.CREDIT_CARD
                    l.contains("gold") || l.contains("property") || l.contains("vehicle") ||
                        l.contains("computer") || l.contains("equipment") || l.contains("asset") -> PocketType.FIXED_ASSET
                    l.contains("fund") || l.contains("share") || l.contains("equity") || l.contains("investment") -> PocketType.INVESTMENT
                    l.contains("capital") || l.contains("retained") || l.contains("profit") -> PocketType.GOAL_POT
                    l.contains("wallet") || l.contains("prepaid") -> PocketType.PREPAID_WALLET
                    else -> PocketType.LIQUID
                }
                staged.add(StagedStatementLineItem(label, type, (amt * 100).roundToLong(), true))
            }
        }
        return staged
    }
}
