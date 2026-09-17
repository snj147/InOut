package com.personal.inout.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.personal.inout.data.AccountBalanceResult
import com.personal.inout.data.LedgerTransaction
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfDossierGenerator {

    fun generateAndShare(
        context: Context,
        accounts: List<AccountBalanceResult>,
        transactions: List<LedgerTransaction>,
        isProUser: Boolean
    ) {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (points)
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val headerPaint = Paint().apply {
                color = Color.BLACK
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val bodyPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 10f
            }

            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            var y = 45f

            // Document Header
            val titleTag = if (isProUser) "InOut Pro - Financial Statement Dossier" else "InOut - Financial Statement Dossier"
            canvas.drawText(titleTag, 40f, y, titlePaint)
            y += 18f

            val dateStr = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            canvas.drawText("Generated on: $dateStr • Balanced Double-Entry Audit", 40f, y, bodyPaint)
            y += 24f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 20f

            // Accounts Summary Section
            canvas.drawText("ACCOUNT BALANCES (ASSETS & LIABILITIES)", 40f, y, headerPaint)
            y += 16f

            accounts.take(12).forEach { acc ->
                val balText = "INR ${String.format("%,.2f", acc.netBalance)}"
                canvas.drawText("${acc.accountName} (${acc.subType})", 40f, y, bodyPaint)
                canvas.drawText(balText, 440f, y, bodyPaint)
                y += 14f
            }

            y += 12f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 20f

            // Recent Postings Section
            canvas.drawText("AUDIT TRANSACTIONS LOG (MOST RECENT)", 40f, y, headerPaint)
            y += 18f

            canvas.drawText("DATE", 40f, y, headerPaint)
            canvas.drawText("DESCRIPTION", 130f, y, headerPaint)
            canvas.drawText("TAX TAG", 420f, y, headerPaint)
            y += 14f

            transactions.take(28).forEach { tx ->
                val txDate = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(tx.timestamp))
                val taxTag = if (tx.isTaxDeductible) "Deductible" else "-"

                canvas.drawText(txDate, 40f, y, bodyPaint)
                canvas.drawText(tx.description.take(35), 130f, y, bodyPaint)
                canvas.drawText(taxTag, 420f, y, bodyPaint)
                y += 14f
            }

            // Watermark footer if Free Tier
            if (!isProUser) {
                val watermarkPaint = Paint().apply {
                    color = Color.GRAY
                    textSize = 9f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                }
                canvas.drawText("Created with InOut Free Tier • Upgrade to Pro to remove branding", 140f, 810f, watermarkPaint)
            }

            pdfDocument.finishPage(page)

            // Save to internal cache & dispatch Android share intent
            val outputDir = File(context.cacheDir, "dossiers")
            if (!outputDir.exists()) outputDir.mkdirs()
            val file = File(outputDir, "InOut_Dossier_${System.currentTimeMillis()}.pdf")

            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Financial Dossier"))

        } catch (e: Exception) {
            Toast.makeText(context, "PDF generation failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
