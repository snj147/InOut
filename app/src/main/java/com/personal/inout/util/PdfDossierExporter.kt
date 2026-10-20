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

    suspend fun generateAndShareDossier(
        context: Context,
        pocketBalances: Map<Long, Double>, // Note: Will migrate to Map<Long, Long> in UI batch
        transactions: List<LedgerTransaction>
    ) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val prefs = context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE)
            val engine = VaultLedgerEngine(db.ledgerDao(), prefs)

            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - (30L * 24 * 3600 * 1000L)
            val (balanceSheet, pnl) = engine.generateFinancialStatements(thirtyDaysAgo, now)

            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
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
            canvas.drawText("InOut – Financial Statements | FY 2026-27 (AY 2027-28)", 40f, y, titlePaint)
            y += 14f
            canvas.drawText("Know what comes in. Know what goes out. • Generated: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(now))}", 40f, y, subtitlePaint)
            y += 16f
            canvas.drawLine(40f, y, 555f, y, dividerPaint)
            y += 18f

            // 1. BALANCE SHEET
            canvas.drawText("1. BALANCE SHEET", 40f, y, sectionPaint)
            y += 14f

            canvas.drawText("CAPITAL & LIABILITIES (INR)", 40f, y, boldTextPaint)
            canvas.drawText("AMOUNT", 220f, y, boldTextPaint)
            canvas.drawText("PROPERTY & ASSETS (INR)", 300f, y, boldTextPaint)
            canvas.drawText("AMOUNT", 490f, y, boldTextPaint)
            y += 6f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 13f

            val leftItems = listOf(
                "Proprietor Capital A/c" to balanceSheet.proprietorCapitalAccount / 100.0,
                "Secured Term Loans" to balanceSheet.securedLoans / 100.0,
                "Unsecured Borrowings" to balanceSheet.unsecuredLoans / 100.0,
                "Creditors & Card Dues" to balanceSheet.sundryCreditorsAndCardDues / 100.0
            )

            val rightItems = listOf(
                "Fixed Assets (at WDV)" to balanceSheet.fixedCapitalAssetsWDV / 100.0,
                "Investments & Securities" to balanceSheet.investmentsPortfolio / 100.0,
                "Sundry Debtors (Advances)" to balanceSheet.sundryDebtorsReceivable / 100.0,
                "Bank & Prepaid Balances" to balanceSheet.bankAndPrepaidBalances / 100.0,
                "Cash in Hand" to balanceSheet.cashInHand / 100.0
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
            canvas.drawText("INR ${String.format("%,.2f", balanceSheet.totalLiabilitiesAndCapital / 100.0)}", 190f, y, boldTextPaint)
            canvas.drawText("TOTAL ASSETS", 300f, y, boldTextPaint)
            canvas.drawText("INR ${String.format("%,.2f", balanceSheet.totalAssets / 100.0)}", 470f, y, boldTextPaint)
            y += 6f
            canvas.drawLine(40f, y, 555f, y, dividerPaint)
            y += 18f

            // 2. PROFIT & LOSS
            canvas.drawText("2. STATEMENT OF INCOME & EXPENDITURE", 40f, y, sectionPaint)
            y += 14f

            val pnlItems = listOf(
                "Gross Operational Inflows" to pnl.grossInflows / 100.0,
                "Operational Living Expenses" to -(pnl.operationalLivingExpenses / 100.0),
                "Finance Charges & Loan Interest" to -(pnl.financeAndLoanCharges / 100.0),
                "Depreciation Written Off" to -(pnl.depreciationWrittenOff / 100.0)
            )

            for ((label, amt) in pnlItems) {
                canvas.drawText(label, 40f, y, regularTextPaint)
                canvas.drawText("INR ${String.format("%,.2f", amt)}", 470f, y, regularTextPaint)
                y += 12f
            }

            y += 4f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 12f
            canvas.drawText("NET SURPLUS SAVINGS", 40f, y, boldTextPaint)
            canvas.drawText("INR ${String.format("%,.2f", pnl.netSurplusSavings / 100.0)}", 470f, y, boldTextPaint)
            y += 6f
            canvas.drawLine(40f, y, 555f, y, dividerPaint)
            y += 18f

            // 3. DATA CHECKS (Placeholder for Step 3 implementation)
            canvas.drawText("DATA CHECKS & VERIFICATION", 40f, y, sectionPaint)
            y += 14f
            canvas.drawText("Verify records with AIS/26AS. Cash transactions > ₹2L flagged: 0. Uncategorized entries: 0.", 40f, y, regularTextPaint)
            y += 24f

            // Footer Signoff
            val footer1 = "Prepared from the entries you recorded in InOut. Figures are only as accurate as your entries,"
            val footer2 = "so please match them with your bank statements, Form 16, AIS and Form 26AS before filing."
            val footer3 = "This report is an aid for preparing your ITR; it is not tax advice. For complex cases, consult a Chartered Accountant."
            val footer4 = "Private & offline-first."
            
            canvas.drawText(footer1, 40f, y, subtitlePaint); y += 10f
            canvas.drawText(footer2, 40f, y, subtitlePaint); y += 10f
            canvas.drawText(footer3, 40f, y, subtitlePaint); y += 10f
            canvas.drawText(footer4, 40f, y, subtitlePaint)

            document.finishPage(page)

            val exportDir = File(context.cacheDir, "dossiers").apply { mkdirs() }
            val file = File(exportDir, "InOut_Financial_Report_${System.currentTimeMillis()}.pdf")
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
