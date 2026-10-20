package com.personal.inout.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.personal.inout.data.AppDatabase
import com.personal.inout.data.LedgerTransaction
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

object PdfDossierExporter {

    private val TAX_LIMITS = mapOf(
        "80C" to 150000L * 100L,
        "80D" to 75000L * 100L,
        "80CCD(1B) NPS" to 50000L * 100L
    )

    suspend fun generateAndShareDossier(
        context: Context,
        fyStartYear: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val dao = db.ledgerDao()

            val cal = Calendar.getInstance()
            cal.set(fyStartYear, Calendar.APRIL, 1, 0, 0, 0)
            val fromEpoch = cal.timeInMillis
            cal.set(fyStartYear + 1, Calendar.MARCH, 31, 23, 59, 59)
            val toEpoch = cal.timeInMillis

            val fyTxs = dao.getTransactionsBetween(fromEpoch, toEpoch)
            val rawPockets = dao.getAllActivePocketsSnapshot()
            val balances = rawPockets.associate { it.id to dao.computePocketBalance(it.id, toEpoch) }

            val openingTxs = dao.getTransactionsBetween(0, fromEpoch - 1)
            var openingCapital = 0L
            openingTxs.forEach { tx ->
                if (tx.movementNature == MovementNature.OPERATING_INCOME || tx.movementNature == MovementNature.OPENING_BASELINE) openingCapital += tx.amount
                if (tx.movementNature == MovementNature.OPERATING_EXPENSE || tx.movementNature == MovementNature.EMI_PRINCIPAL) openingCapital -= tx.amount
            }

            var fySurplus = 0L
            var fyDrawings = 0L
            var grossIncome = 0L
            var grossExpense = 0L
            var tdsPaid = 0L
            var cashTransactionsOver2L = 0
            var uncategorizedCount = 0
            var futureDatedCount = 0
            var untaggedIncomeCount = 0

            val incomeByHead = mutableMapOf("Salary" to 0L, "Business/Profession" to 0L, "Capital Gains" to 0L, "House Property" to 0L, "Other Sources" to 0L)
            val taxDeductions = mutableMapOf<String, Long>()
            val monthlyFlows = mutableMapOf<String, Pair<Long, Long>>()

            val monthFormatter = SimpleDateFormat("MMM yyyy", Locale.getDefault())

            fyTxs.forEach { tx ->
                if (tx.timestamp > System.currentTimeMillis()) futureDatedCount++
                if (tx.category.isBlank() || tx.category.equals("General", true)) uncategorizedCount++
                
                val srcPocket = rawPockets.firstOrNull { it.id == tx.sourcePocketId }
                val isCash = srcPocket?.name?.contains("Cash", ignoreCase = true) == true
                if (isCash && tx.amount >= 200000_00L) cashTransactionsOver2L++

                val monthKey = monthFormatter.format(Date(tx.timestamp))
                val currentFlow = monthlyFlows[monthKey] ?: Pair(0L, 0L)

                if (tx.movementNature == MovementNature.OPERATING_INCOME) {
                    grossIncome += tx.amount
                    fySurplus += tx.amount
                    monthlyFlows[monthKey] = Pair(currentFlow.first + tx.amount, currentFlow.second)
                    
                    val catLower = tx.category.lowercase()
                    when {
                        catLower.contains("salary") -> incomeByHead["Salary"] = (incomeByHead["Salary"] ?: 0L) + tx.amount
                        catLower.contains("business") || catLower.contains("freelance") -> incomeByHead["Business/Profession"] = (incomeByHead["Business/Profession"] ?: 0L) + tx.amount
                        catLower.contains("gain") || catLower.contains("dividend") -> incomeByHead["Capital Gains"] = (incomeByHead["Capital Gains"] ?: 0L) + tx.amount
                        catLower.contains("rent") -> incomeByHead["House Property"] = (incomeByHead["House Property"] ?: 0L) + tx.amount
                        else -> {
                            incomeByHead["Other Sources"] = (incomeByHead["Other Sources"] ?: 0L) + tx.amount
                            untaggedIncomeCount++
                        }
                    }
                }
                
                if (tx.movementNature == MovementNature.OPERATING_EXPENSE || tx.movementNature == MovementNature.EMI_PRINCIPAL) {
                    grossExpense += tx.amount
                    fySurplus -= tx.amount
                    monthlyFlows[monthKey] = Pair(currentFlow.first, currentFlow.second + tx.amount)
                    
                    if (tx.category.contains("Drawing", ignoreCase = true)) fyDrawings += tx.amount
                    if (tx.category.contains("tds", ignoreCase = true) || tx.category.contains("tax", ignoreCase = true)) tdsPaid += tx.amount
                }

                if (tx.isTaxDeductible) {
                    taxDeductions[tx.category] = (taxDeductions[tx.category] ?: 0L) + tx.amount
                }
            }

            val closingCapital = openingCapital + fySurplus - fyDrawings

            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            
            val titlePaint = Paint().apply { textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK }
            val subPaint = Paint().apply { textSize = 10f; color = Color.DKGRAY }
            val secPaint = Paint().apply { textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK }
            val boldPaint = Paint().apply { textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK }
            val textPaint = Paint().apply { textSize = 10f; color = Color.DKGRAY }
            val linePaint = Paint().apply { strokeWidth = 1f; color = Color.LTGRAY }
            val footerPaint = Paint().apply { textSize = 8f; color = Color.GRAY; textAlign = Paint.Align.CENTER }
            val warningPaint = Paint().apply { textSize = 10f; color = Color.rgb(200, 50, 50) }
            val successPaint = Paint().apply { textSize = 10f; color = Color.rgb(50, 150, 50) }

            fun drawHeaderAndFooter(canvas: android.graphics.Canvas, pageNum: Int, pageTitle: String) {
                canvas.drawText("InOut – Financial Statements | FY $fyStartYear-${(fyStartYear+1)%100} (AY ${fyStartYear+1}-${(fyStartYear+2)%100})", 40f, 40f, titlePaint)
                canvas.drawText("Know what comes in. Know what goes out.", 40f, 54f, textPaint)
                canvas.drawLine(40f, 66f, 555f, 66f, Paint().apply { strokeWidth = 1.5f; color = Color.BLACK })
                
                canvas.drawText(pageTitle, 40f, 90f, secPaint)

                val fY = 780f
                canvas.drawLine(40f, fY - 10f, 555f, fY - 10f, linePaint)
                canvas.drawText("Prepared from entries recorded in InOut. Verify with bank statements, Form 16, AIS, and Form 26AS.", 297f, fY, footerPaint)
                canvas.drawText("This report is an aid for preparing your ITR; it is not tax advice. Private & offline-first. Page $pageNum", 297f, fY + 12f, footerPaint)
            }

            var pageCount = 1
            var y: Float

            // --- PAGE 1: CAPITAL RECONCILIATION ---
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            drawHeaderAndFooter(canvas, pageCount++, "1. CAPITAL RECONCILIATION")
            y = 120f
            
            canvas.drawText("Opening Capital (as of 1 Apr $fyStartYear)", 40f, y, textPaint)
            canvas.drawText(String.format("%,.2f", openingCapital / 100.0), 480f, y, textPaint); y += 18f
            canvas.drawText("Add: Net Surplus for the Year", 40f, y, textPaint)
            canvas.drawText(String.format("%,.2f", fySurplus / 100.0), 480f, y, textPaint); y += 18f
            canvas.drawText("Less: Personal Drawings", 40f, y, textPaint)
            canvas.drawText(String.format("%,.2f", fyDrawings / 100.0), 480f, y, textPaint); y += 18f
            canvas.drawLine(40f, y, 555f, y, linePaint); y += 18f
            canvas.drawText("CLOSING CAPITAL (as of 31 Mar ${fyStartYear+1})", 40f, y, boldPaint)
            canvas.drawText(String.format("%,.2f", closingCapital / 100.0), 480f, y, boldPaint)
            document.finishPage(page)

            // --- PAGE 2: INCOME BY ITR HEAD ---
            page = document.startPage(pageInfo)
            canvas = page.canvas
            drawHeaderAndFooter(canvas, pageCount++, "2. INCOME BY ITR HEAD")
            y = 120f
            
            canvas.drawText("Verify these heads with your Form 16, AIS, and Annual Information Statement.", 40f, y, textPaint); y += 24f
            incomeByHead.forEach { (head, amt) ->
                canvas.drawText(head, 40f, y, textPaint)
                canvas.drawText(String.format("%,.2f", amt / 100.0), 480f, y, textPaint); y += 18f
            }
            canvas.drawLine(40f, y, 555f, y, linePaint); y += 18f
            canvas.drawText("TOTAL GROSS INCOME", 40f, y, boldPaint)
            canvas.drawText(String.format("%,.2f", grossIncome / 100.0), 480f, y, boldPaint)
            document.finishPage(page)

            // --- PAGE 3: TAXES PAID ---
            page = document.startPage(pageInfo)
            canvas = page.canvas
            drawHeaderAndFooter(canvas, pageCount++, "3. TAXES PAID")
            y = 120f
            
            canvas.drawText("TDS / Advance Tax / Self-Assessment Logged", 40f, y, textPaint)
            canvas.drawText(String.format("%,.2f", tdsPaid / 100.0), 480f, y, textPaint); y += 24f
            canvas.drawText("Verify with Form 26AS to ensure tax credits are accurately reflected by the IT Department.", 40f, y, textPaint)
            document.finishPage(page)

            // --- PAGE 4: DATA CHECKS ---
            page = document.startPage(pageInfo)
            canvas = page.canvas
            drawHeaderAndFooter(canvas, pageCount++, "4. DATA CHECKS")
            y = 120f
            
            fun drawCheck(label: String, isWarning: Boolean) {
                canvas.drawText(label, 40f, y, if (isWarning) warningPaint else successPaint)
                y += 20f
            }
            
            val duplicateCount = fyTxs.groupBy { "${it.amount}_${it.timestamp / 86400000}" }.count { it.value.size > 1 }
            
            var totalAssets = 0L; var totalLiabs = 0L
            rawPockets.forEach { p -> 
                val b = balances[p.id] ?: 0L
                if (p.type in listOf(PocketType.CREDIT_CARD, PocketType.LIABILITY_LOAN, PocketType.PEER_PAYABLE)) {
                    if (b >= 0) totalAssets += b else totalLiabs += abs(b)
                } else {
                    if (b < 0 && p.type == PocketType.PEER_RECEIVABLE) totalLiabs += abs(b) else totalAssets += b
                }
            }
            val tallyDiff = totalAssets - (totalLiabs + closingCapital)
            drawCheck(if (tallyDiff == 0L) "✓ Balance Sheet Tallies perfectly." else "⚠ Warn: Balance Sheet mismatch of ₹${tallyDiff / 100.0}.", tallyDiff != 0L)
            drawCheck(if (uncategorizedCount > 0) "⚠ Warn: $uncategorizedCount entries missing specific category labels." else "✓ All entries categorized.", uncategorizedCount > 0)
            drawCheck(if (futureDatedCount > 0) "⚠ Warn: $futureDatedCount entries have future dates." else "✓ No future dated entries detected.", futureDatedCount > 0)
            drawCheck(if (cashTransactionsOver2L > 0) "⚠ Warn: $cashTransactionsOver2L cash transaction(s) exceed ₹2L (Sec 269ST limit)." else "✓ No cash transactions > ₹2L detected.", cashTransactionsOver2L > 0)
            drawCheck(if (duplicateCount > 0) "⚠ Warn: $duplicateCount potential duplicate transaction pairs found." else "✓ No obvious duplicates detected.", duplicateCount > 0)
            drawCheck(if (untaggedIncomeCount > 0) "⚠ Warn: $untaggedIncomeCount income entries mapped to 'Other Sources'. Please review." else "✓ All income specifically tagged.", untaggedIncomeCount > 0)
            document.finishPage(page)

            // --- PAGE 5: DEDUCTIONS SUMMARY ---
            page = document.startPage(pageInfo)
            canvas = page.canvas
            drawHeaderAndFooter(canvas, pageCount++, "5. DEDUCTIONS SUMMARY")
            y = 120f
            
            canvas.drawText("CATEGORY", 40f, y, boldPaint)
            canvas.drawText("CLAIMED (INR)", 280f, y, boldPaint)
            canvas.drawText("SUGGESTED LIMIT", 430f, y, boldPaint); y += 8f
            canvas.drawLine(40f, y, 555f, y, linePaint); y += 18f

            if (taxDeductions.isEmpty()) {
                canvas.drawText("No tax-deductible entries tagged in this period.", 40f, y, textPaint)
            } else {
                taxDeductions.forEach { (cat, amt) ->
                    val defaultLimitPaise = TaxLimitsConfig.DEDUCTION_LIMITS.entries.firstOrNull { cat.contains(it.key, ignoreCase = true) }?.value
                    canvas.drawText(cat, 40f, y, textPaint)
                    canvas.drawText(String.format("%,.2f", amt / 100.0), 280f, y, textPaint)
                    canvas.drawText(if (defaultLimitPaise != null && defaultLimitPaise > 0) String.format("%,.2f", defaultLimitPaise / 100.0) else "Check Act", 430f, y, textPaint)
                    y += 18f
                }
            }
            document.finishPage(page)

            // --- PAGE 6: LOAN STATEMENT ---
            page = document.startPage(pageInfo)
            canvas = page.canvas
            drawHeaderAndFooter(canvas, pageCount++, "6. LOAN STATEMENT")
            y = 120f
            
            val loanPockets = rawPockets.filter { it.type == PocketType.LIABILITY_LOAN }
            if (loanPockets.isEmpty()) {
                canvas.drawText("No liability loans tracked in this period.", 40f, y, textPaint)
            } else {
                loanPockets.forEach { p ->
                    val principalPaid = fyTxs.filter { it.sourcePocketId == p.id && it.movementNature == MovementNature.EMI_PRINCIPAL }.sumOf { it.amount }
                    val interestPaid = fyTxs.filter { it.sourcePocketId == p.id && it.movementNature == MovementNature.OPERATING_EXPENSE && it.category.contains("Interest", true) }.sumOf { it.amount }
                    canvas.drawText("Loan Account: ${p.name}", 40f, y, boldPaint); y += 14f
                    canvas.drawText("Principal Repaid: ₹${String.format("%,.2f", principalPaid / 100.0)}", 40f, y, textPaint); y += 14f
                    canvas.drawText("Interest Paid: ₹${String.format("%,.2f", interestPaid / 100.0)}", 40f, y, textPaint); y += 24f
                }
            }
            document.finishPage(page)

            // --- PAGE 7: CAPITAL GAINS / VDA ---
            page = document.startPage(pageInfo)
            canvas = page.canvas
            drawHeaderAndFooter(canvas, pageCount++, "7. CAPITAL GAINS / VDA STATEMENT")
            y = 120f
            
            val vdaTxs = fyTxs.filter { it.category.contains("Crypto", true) || it.category.contains("VDA", true) }
            val capGainsTxs = fyTxs.filter { it.category.contains("Capital Gain", true) || it.category.contains("Shares", true) }
            
            canvas.drawText("Capital Gains (Shares / Mutual Funds): ₹${String.format("%,.2f", capGainsTxs.sumOf { it.amount } / 100.0)}", 40f, y, textPaint); y += 18f
            canvas.drawText("VDA / Crypto Gains (Taxed at flat 30%): ₹${String.format("%,.2f", vdaTxs.sumOf { it.amount } / 100.0)}", 40f, y, textPaint); y += 24f
            canvas.drawText("Note: Detailed scrip-wise breakdown requires Form 26QB / Broker P&L.", 40f, y, subPaint)
            document.finishPage(page)

            // --- PAGE 8: FIXED ASSET REGISTER ---
            page = document.startPage(pageInfo)
            canvas = page.canvas
            drawHeaderAndFooter(canvas, pageCount++, "8. FIXED ASSET REGISTER")
            y = 120f
            
            val assetPockets = rawPockets.filter { it.type == PocketType.FIXED_ASSET }
            if (assetPockets.isEmpty()) {
                canvas.drawText("No fixed assets tracked in this period.", 40f, y, textPaint)
            } else {
                assetPockets.forEach { p ->
                    val dep = fyTxs.filter { it.sourcePocketId == p.id && it.movementNature == MovementNature.DEPRECIATION_WRITE }.sumOf { it.amount }
                    val currentVal = balances[p.id] ?: 0L
                    canvas.drawText("Asset: ${p.name}", 40f, y, boldPaint); y += 14f
                    canvas.drawText("Depreciation this FY: ₹${String.format("%,.2f", dep / 100.0)}", 40f, y, textPaint); y += 14f
                    canvas.drawText("Current WDV: ₹${String.format("%,.2f", currentVal / 100.0)}", 40f, y, textPaint); y += 24f
                }
            }
            document.finishPage(page)

            // --- PAGE 9: MONTH-WISE SUMMARY & PEERS ---
            page = document.startPage(pageInfo)
            canvas = page.canvas
            drawHeaderAndFooter(canvas, pageCount, "9. MONTH-WISE SUMMARY & RECEIVABLES")
            y = 120f
            
            canvas.drawText("MONTH", 40f, y, boldPaint)
            canvas.drawText("INCOME", 200f, y, boldPaint)
            canvas.drawText("EXPENSE", 360f, y, boldPaint); y += 8f
            canvas.drawLine(40f, y, 555f, y, linePaint); y += 18f

            monthlyFlows.forEach { (month, flows) ->
                canvas.drawText(month, 40f, y, textPaint)
                canvas.drawText(String.format("%,.2f", flows.first / 100.0), 200f, y, textPaint)
                canvas.drawText(String.format("%,.2f", flows.second / 100.0), 360f, y, textPaint)
                y += 18f
            }
            y += 16f
            
            canvas.drawText("SUNDRY DEBTORS (RECEIVABLES)", 40f, y, secPaint); y += 16f
            val debtors = rawPockets.filter { it.type == PocketType.PEER_RECEIVABLE }.filter { (balances[it.id] ?: 0L) > 0L }
            if (debtors.isEmpty()) canvas.drawText("No outstanding receivables.", 40f, y, textPaint)
            else debtors.forEach { d -> canvas.drawText("${d.name}: ₹${String.format("%,.2f", (balances[d.id] ?: 0L) / 100.0)}", 40f, y, textPaint); y += 14f }
            
            document.finishPage(page)

            val exportDir = File(context.cacheDir, "dossiers").apply { mkdirs() }
            val file = File(exportDir, "InOut_Financial_Report_FY${fyStartYear}.pdf")
            FileOutputStream(file).use { out -> document.writeTo(out) }
            document.close()

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Share Financial Report (PDF)").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Report generation failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
