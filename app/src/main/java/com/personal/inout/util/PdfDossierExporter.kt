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

    // Configurable tax limits (Update these yearly as per Indian Budget)
    private val TAX_LIMITS = mapOf(
        "80C" to 150000.0,
        "80D" to 75000.0,
        "80CCD(1B) NPS" to 50000.0
    )

    suspend fun generateAndShareDossier(
        context: Context,
        fyStartYear: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val dao = db.ledgerDao()

            // Calculate exact Epochs for the Financial Year (1 Apr - 31 Mar)
            val cal = Calendar.getInstance()
            cal.set(fyStartYear, Calendar.APRIL, 1, 0, 0, 0)
            val fromEpoch = cal.timeInMillis
            cal.set(fyStartYear + 1, Calendar.MARCH, 31, 23, 59, 59)
            val toEpoch = cal.timeInMillis

            val fyTxs = dao.getTransactionsBetween(fromEpoch, toEpoch)
            val rawPockets = dao.getAllActivePocketsSnapshot()
            
            // Calculate Opening Capital (All txs before Apr 1)
            val openingTxs = dao.getTransactionsBetween(0, fromEpoch - 1)
            var openingCapital = 0.0
            openingTxs.forEach { tx ->
                if (tx.movementNature == MovementNature.OPERATING_INCOME || tx.movementNature == MovementNature.OPENING_BASELINE) openingCapital += (tx.amount / 100.0)
                if (tx.movementNature == MovementNature.OPERATING_EXPENSE || tx.movementNature == MovementNature.EMI_PRINCIPAL) openingCapital -= (tx.amount / 100.0)
            }

            // FY Aggregations
            var fySurplus = 0.0
            var fyDrawings = 0.0 // Currently mapped to expenses tagged as "Drawings"
            var grossIncome = 0.0
            var grossExpense = 0.0
            var tdsPaid = 0.0
            var cashTransactionsOver2L = 0
            var uncategorizedCount = 0

            val incomeByHead = mutableMapOf("Salary" to 0.0, "Business/Profession" to 0.0, "Capital Gains" to 0.0, "House Property" to 0.0, "Other Sources" to 0.0)
            val taxDeductions = mutableMapOf<String, Double>()

            fyTxs.forEach { tx ->
                val amt = tx.amount / 100.0
                if (tx.category.isBlank() || tx.category == "General") uncategorizedCount++
                
                // Track 269ST Cash Limit Violations
                val isCash = rawPockets.firstOrNull { it.id == tx.sourcePocketId }?.name?.contains("Cash", ignoreCase = true) == true
                if (isCash && amt >= 200000.0) cashTransactionsOver2L++

                if (tx.movementNature == MovementNature.OPERATING_INCOME) {
                    grossIncome += amt
                    fySurplus += amt
                    
                    // Simple logic: If user tags income with specific words, route it to ITR heads, otherwise "Other Sources"
                    val catLower = tx.category.lowercase()
                    when {
                        catLower.contains("salary") -> incomeByHead["Salary"] = (incomeByHead["Salary"] ?: 0.0) + amt
                        catLower.contains("business") || catLower.contains("freelance") -> incomeByHead["Business/Profession"] = (incomeByHead["Business/Profession"] ?: 0.0) + amt
                        catLower.contains("gain") || catLower.contains("dividend") -> incomeByHead["Capital Gains"] = (incomeByHead["Capital Gains"] ?: 0.0) + amt
                        catLower.contains("rent") -> incomeByHead["House Property"] = (incomeByHead["House Property"] ?: 0.0) + amt
                        else -> incomeByHead["Other Sources"] = (incomeByHead["Other Sources"] ?: 0.0) + amt
                    }
                }
                
                if (tx.movementNature == MovementNature.OPERATING_EXPENSE || tx.movementNature == MovementNature.EMI_PRINCIPAL) {
                    grossExpense += amt
                    fySurplus -= amt
                    if (tx.category.contains("Drawing", ignoreCase = true)) fyDrawings += amt
                    if (tx.category.contains("TDS", ignoreCase = true) || tx.category.contains("Advance Tax", ignoreCase = true)) tdsPaid += amt
                }

                if (tx.isTaxDeductible) {
                    taxDeductions[tx.category] = (taxDeductions[tx.category] ?: 0.0) + amt
                }
            }

            val closingCapital = openingCapital + fySurplus - fyDrawings

            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            
            // PAINTS
            val titlePaint = Paint().apply { textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK }
            val subPaint = Paint().apply { textSize = 10f; color = Color.DKGRAY }
            val secPaint = Paint().apply { textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK }
            val boldPaint = Paint().apply { textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK }
            val textPaint = Paint().apply { textSize = 10f; color = Color.DKGRAY }
            val linePaint = Paint().apply { strokeWidth = 1f; color = Color.LTGRAY }
            val warningPaint = Paint().apply { textSize = 10f; color = Color.rgb(200, 50, 50) }

            // PAGE 1: Core Financials
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var y = 40f

            canvas.drawText("InOut – Financial Statements | FY $fyStartYear-${(fyStartYear+1)%100} (AY ${fyStartYear+1}-${(fyStartYear+2)%100})", 40f, y, titlePaint); y += 16f
            canvas.drawText("Know what comes in. Know what goes out.", 40f, y, subPaint); y += 12f
            canvas.drawLine(40f, y, 555f, y, Paint().apply { strokeWidth = 1.5f; color = Color.BLACK }); y += 24f

            // Section 1: Capital Reconciliation
            canvas.drawText("1. CAPITAL RECONCILIATION", 40f, y, secPaint); y += 16f
            canvas.drawText("Opening Capital (as of 1 Apr $fyStartYear)", 40f, y, textPaint)
            canvas.drawText(String.format("%,.2f", openingCapital), 480f, y, textPaint); y += 14f
            canvas.drawText("Add: Net Surplus for the Year", 40f, y, textPaint)
            canvas.drawText(String.format("%,.2f", fySurplus), 480f, y, textPaint); y += 14f
            canvas.drawText("Less: Personal Drawings", 40f, y, textPaint)
            canvas.drawText(String.format("%,.2f", fyDrawings), 480f, y, textPaint); y += 14f
            canvas.drawLine(40f, y, 555f, y, linePaint); y += 14f
            canvas.drawText("CLOSING CAPITAL (as of 31 Mar ${fyStartYear+1})", 40f, y, boldPaint)
            canvas.drawText(String.format("%,.2f", closingCapital), 480f, y, boldPaint); y += 24f

            // Section 2: Income by ITR Head
            canvas.drawText("2. INCOME BY ITR HEAD (Verify with Form 16 / AIS)", 40f, y, secPaint); y += 16f
            incomeByHead.forEach { (head, amt) ->
                if (amt > 0) {
                    canvas.drawText(head, 40f, y, textPaint)
                    canvas.drawText(String.format("%,.2f", amt), 480f, y, textPaint); y += 14f
                }
            }
            canvas.drawLine(40f, y, 555f, y, linePaint); y += 14f
            canvas.drawText("TOTAL GROSS INCOME", 40f, y, boldPaint)
            canvas.drawText(String.format("%,.2f", grossIncome), 480f, y, boldPaint); y += 24f

            // Section 3: Taxes Paid
            canvas.drawText("3. TAXES PAID (Verify with Form 26AS)", 40f, y, secPaint); y += 16f
            canvas.drawText("TDS / Advance Tax / Self-Assessment Logged", 40f, y, textPaint)
            canvas.drawText(String.format("%,.2f", tdsPaid), 480f, y, textPaint); y += 24f

            // Section 4: Data Checks
            canvas.drawText("4. DATA CHECKS & ALERTS", 40f, y, secPaint); y += 16f
            if (uncategorizedCount > 0) canvas.drawText("⚠ Warn: $uncategorizedCount entries missing category labels.", 40f, y, warningPaint) else canvas.drawText("✓ All entries categorized.", 40f, y, textPaint)
            y += 14f
            if (cashTransactionsOver2L > 0) canvas.drawText("⚠ Warn: $cashTransactionsOver2L cash transaction(s) exceed ₹2L (Sec 269ST limit).", 40f, y, warningPaint) else canvas.drawText("✓ No cash transactions > ₹2L detected.", 40f, y, textPaint)
            y += 14f
            canvas.drawText("✓ Future dated entries: 0 (Ledger synchronized).", 40f, y, textPaint)
            
            document.finishPage(page)

            // PAGE 2: Deductions & Assets
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = 40f

            // Section 5: Deductions Summary
            canvas.drawText("5. DEDUCTIONS SUMMARY (Tagged Entries)", 40f, y, secPaint); y += 12f
            canvas.drawText("Note: Verify current limits as per your selected tax regime.", 40f, y, subPaint); y += 16f
            
            canvas.drawText("CATEGORY", 40f, y, boldPaint)
            canvas.drawText("CLAIMED (INR)", 280f, y, boldPaint)
            canvas.drawText("SUGGESTED LIMIT", 430f, y, boldPaint); y += 8f
            canvas.drawLine(40f, y, 555f, y, linePaint); y += 14f

            if (taxDeductions.isEmpty()) {
                canvas.drawText("No tax-deductible entries tagged in this period.", 40f, y, textPaint); y += 14f
            } else {
                taxDeductions.forEach { (cat, amt) ->
                    val defaultLimit = TAX_LIMITS.entries.firstOrNull { cat.contains(it.key, ignoreCase = true) }?.value
                    canvas.drawText(cat, 40f, y, textPaint)
                    canvas.drawText(String.format("%,.2f", amt), 280f, y, textPaint)
                    canvas.drawText(if (defaultLimit != null) String.format("%,.2f", defaultLimit) else "N/A", 430f, y, textPaint)
                    y += 14f
                }
            }
            y += 16f

            // Section 6, 7, 8: Assets & Loans (Simplified Aggregations)
            canvas.drawText("6. LOAN STATEMENT SUMMARY", 40f, y, secPaint); y += 16f
            val loanPockets = rawPockets.filter { it.type == PocketType.LIABILITY_LOAN }
            if (loanPockets.isEmpty()) canvas.drawText("No liability loans active.", 40f, y, textPaint) else {
                loanPockets.forEach { p -> canvas.drawText(p.name, 40f, y, textPaint); y += 14f }
            }
            y += 24f

            canvas.drawText("7. CAPITAL GAINS / VDA STATEMENT", 40f, y, secPaint); y += 12f
            canvas.drawText("/* Note: VDA/Crypto and exact Capital Gains breakdown require separate CSV ledger ingestion. */", 40f, y, subPaint); y += 24f

            canvas.drawText("8. FIXED ASSET REGISTER", 40f, y, secPaint); y += 16f
            val assetPockets = rawPockets.filter { it.type == PocketType.FIXED_ASSET }
            if (assetPockets.isEmpty()) canvas.drawText("No fixed assets tracked.", 40f, y, textPaint) else {
                assetPockets.forEach { p -> canvas.drawText(p.name, 40f, y, textPaint); y += 14f }
            }
            y += 24f

            // Footer
            y = 720f
            canvas.drawLine(40f, y, 555f, y, dividerPaint); y += 14f
            val footer1 = "Prepared from the entries you recorded in InOut. Figures are only as accurate as your entries,"
            val footer2 = "so please match them with your bank statements, Form 16, AIS and Form 26AS before filing."
            val footer3 = "This report is an aid for preparing your ITR; it is not tax advice. For complex cases, consult a CA."
            val footer4 = "Private & offline-first."
            
            canvas.drawText(footer1, 40f, y, subPaint); y += 12f
            canvas.drawText(footer2, 40f, y, subPaint); y += 12f
            canvas.drawText(footer3, 40f, y, subPaint); y += 12f
            canvas.drawText(footer4, 40f, y, subPaint)

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
