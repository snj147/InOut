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
import com.personal.inout.data.VaultLedgerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfDossierExporter {

    /**
     * BRD Rule 28 & 34: Generates an ICAI-Compliant Indian Financial Statement Dossier (PDF)
     * including T-Format Balance Sheet, P&L Statement, and Section 80C / 80D Tax Schedules.
     */
    suspend fun generateAndShareDossier(
        context: Context,
        pocketBalances: Map<Long, Double>,
        transactions: List<LedgerTransaction>
    ) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val prefs = context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE)
            val engine = VaultLedgerEngine(db.ledgerDao(), prefs)

            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - (30L * 24 * 3600 * 1000L)
            val (balanceSheet, pnl) = engine.generateIndianStatements(thirtyDaysAgo, now)

            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply {
                textSize = 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.rgb(20, 24, 30)
            }
            val subtitlePaint = Paint().apply {
                textSize = 9f
                color = Color.rgb(90, 100, 110)
            }
            val sectionPaint = Paint().apply {
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.rgb(35, 45, 55)
            }
            val boldTextPaint = Paint().apply {
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.rgb(20, 24, 30)
            }
            val regularTextPaint = Paint().apply {
                textSize = 8.5f
                color = Color.rgb(45, 55, 65)
            }
            val linePaint = Paint().apply {
                strokeWidth = 0.8f
                color = Color.rgb(210, 215, 220)
            }
            val dividerPaint = Paint().apply {
                strokeWidth = 1.2f
                color = Color.rgb(35, 45, 55)
            }

            var y = 38f

            // Document Header
            canvas.drawText("INOUT — STATUTORY FINANCIAL DOSSIER", 40f, y, titlePaint)
            y += 14f
            val dateStr = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(now))
            canvas.drawText("Format: ICAI Indian Accounting Standard & ITR Schedule AL • Generated: $dateStr", 40f, y, subtitlePaint)
            y += 16f
            canvas.drawLine(40f, y, 555f, y, dividerPaint)
            y += 18f

            // 1. STATUTORY BALANCE SHEET (T-FORMAT: LIABILITIES ON LEFT, ASSETS ON RIGHT)
            canvas.drawText("1. BALANCE SHEET AS OF ${SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(now))}", 40f, y, sectionPaint)
            y += 14f

            // Table Header Bar
            canvas.drawText("CAPITAL & LIABILITIES (INR)", 40f, y, boldTextPaint)
            canvas.drawText("AMOUNT", 220f, y, boldTextPaint)
            canvas.drawText("PROPERTY & ASSETS (INR)", 300f, y, boldTextPaint)
            canvas.drawText("AMOUNT", 490f, y, boldTextPaint)
            y += 6f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 13f

            val leftItems = listOf(
                "Proprietor Capital A/c" to balanceSheet.proprietorCapitalAccount,
                "Secured Term Loans" to balanceSheet.securedLoans,
                "Unsecured Borrowings" to balanceSheet.unsecuredLoans,
                "Sundry Creditors & Card Dues" to balanceSheet.sundryCreditorsAndCardDues
            )

            val rightItems = listOf(
                "Fixed Assets (at WDV)" to balanceSheet.fixedCapitalAssetsWDV,
                "Investments & Securities" to balanceSheet.investmentsPortfolio,
                "Sundry Debtors (Advances)" to balanceSheet.sundryDebtorsReceivable,
                "Bank & Prepaid Balances" to balanceSheet.bankAndPrepaidBalances,
                "Cash in Hand" to balanceSheet.cashInHand
            )

            val maxRows = maxOf(leftItems.size, rightItems.size)
            for (i in 0 until maxRows) {
                if (i < leftItems.size) {
                    val (label, amt) = leftItems[i]
                    canvas.drawText(label, 40f, y, regularTextPaint)
                    canvas.drawText(String.format("%,.2f", amt), 210f, y, regularTextPaint)
                }
                if (i < rightItems.size) {
                    val (label, amt) = rightItems[i]
                    canvas.drawText(label, 300f, y, regularTextPaint)
                    canvas.drawText(String.format("%,.2f", amt), 485f, y, regularTextPaint)
                }
                y += 12f
            }

            y += 4f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 12f
            canvas.drawText("TOTAL LIABILITIES", 40f, y, boldTextPaint)
            canvas.drawText("INR ${String.format("%,.2f", balanceSheet.totalLiabilitiesAndCapital)}", 190f, y, boldTextPaint)
            canvas.drawText("TOTAL ASSETS", 300f, y, boldTextPaint)
            canvas.drawText("INR ${String.format("%,.2f", balanceSheet.totalAssets)}", 470f, y, boldTextPaint)
            y += 6f
            canvas.drawLine(40f, y, 555f, y, dividerPaint)
            y += 18f

            // 2. PROFIT & LOSS / INCOME & EXPENDITURE STATEMENT
            canvas.drawText("2. STATEMENT OF INCOME & EXPENDITURE (P&L)", 40f, y, sectionPaint)
            y += 14f

            val pnlItems = listOf(
                "Gross Operational Inflows (Salary, Inflow, Revenue)" to pnl.grossInflows,
                "Operational Living & Consumable Expenses" to -pnl.operationalLivingExpenses,
                "Finance Charges, Loan Interest & Card Fees" to -pnl.financeAndLoanCharges,
                "Depreciation & Wear-and-Tear Amortization" to -pnl.depreciationWrittenOff
            )

            for ((label, amt) in pnlItems) {
                canvas.drawText(label, 40f, y, regularTextPaint)
                canvas.drawText("INR ${String.format("%,.2f", amt)}", 470f, y, regularTextPaint)
                y += 12f
            }

            y += 4f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 12f
            canvas.drawText("NET SAVINGS SURPLUS CARRIED TO CAPITAL", 40f, y, boldTextPaint)
            canvas.drawText("INR ${String.format("%,.2f", pnl.netSurplusSavings)}", 470f, y, boldTextPaint)
            y += 6f
            canvas.drawLine(40f, y, 555f, y, dividerPaint)
            y += 18f

            // 3. STATUTORY TAX DEDUCTION ANNEXURE (SECTION 80C / 80D / ITR DEDUCTIONS)
            canvas.drawText("3. STATUTORY TAX DEDUCTION SCHEDULE (ITR ANNEXURE)", 40f, y, sectionPaint)
            y += 14f

            val taxSummary = pnl.taxDeductibleSummary
            if (taxSummary.isEmpty()) {
                canvas.drawText("No transactions tagged under Section 80C or medical deduction heads during this period.", 40f, y, regularTextPaint)
                y += 14f
            } else {
                for ((cat, amt) in taxSummary) {
                    canvas.drawText("Tax Flagged Category: $cat", 40f, y, regularTextPaint)
                    canvas.drawText("INR ${String.format("%,.2f", amt)}", 470f, y, regularTextPaint)
                    y += 12f
                }
            }

            y += 16f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 14f

            // Footer Signoff
            canvas.drawText("Verified On-Device • Encrypted Double-Entry Engine • Private & Offline-First", 40f, y, subtitlePaint)

            document.finishPage(page)

            val exportDir = File(context.cacheDir, "dossiers").apply { mkdirs() }
            val file = File(exportDir, "InOut_Statutory_Dossier_${System.currentTimeMillis()}.pdf")
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
                context.startActivity(Intent.createChooser(intent, "Share Statutory Dossier (PDF)").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Dossier generation failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
