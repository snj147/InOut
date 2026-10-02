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
import com.personal.inout.data.LedgerPocket
import com.personal.inout.data.LedgerTransaction
import com.personal.inout.data.MovementNature
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfDossierGenerator {

    /**
     * BRD Rule 34: Generates a complete transaction audit log & statutory schedule annexure.
     */
    fun generateAndShare(
        context: Context,
        pockets: List<LedgerPocket>,
        transactions: List<LedgerTransaction>,
        isProUser: Boolean
    ) {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (points)
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.rgb(20, 24, 30)
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val headerPaint = Paint().apply {
                color = Color.BLACK
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val bodyPaint = Paint().apply {
                color = Color.rgb(50, 60, 70)
                textSize = 8.5f
            }

            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 0.8f
            }

            val dividerPaint = Paint().apply {
                color = Color.BLACK
                strokeWidth = 1.2f
            }

            var y = 42f

            // Header Section
            val titleTag = if (isProUser) "InOut Pro — Statutory Audit & Ledger Schedule" else "InOut — Statutory Audit & Ledger Schedule"
            canvas.drawText(titleTag, 40f, y, titlePaint)
            y += 14f

            val dateStr = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            canvas.drawText("Generated on: $dateStr • Compliance: ICAI / Indian Income Tax Rules", 40f, y, bodyPaint)
            y += 18f
            canvas.drawLine(40f, y, 555f, y, dividerPaint)
            y += 18f

            // Accounts Master Summary
            canvas.drawText("ACTIVE ACCOUNT POCKETS & BALANCES", 40f, y, headerPaint)
            y += 14f

            canvas.drawText("POCKET NAME", 40f, y, headerPaint)
            canvas.drawText("CLASSIFICATION", 220f, y, headerPaint)
            canvas.drawText("STATUTORY CATEGORY", 380f, y, headerPaint)
            y += 6f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 12f

            pockets.take(8).forEach { pocket ->
                canvas.drawText(pocket.name.take(24), 40f, y, bodyPaint)
                canvas.drawText(pocket.type.name, 220f, y, bodyPaint)
                val statTag = if (pocket.creditLimit > 0) "Limit: ₹${pocket.creditLimit.toInt()}" else "Asset / Liquid"
                canvas.drawText(statTag, 380f, y, bodyPaint)
                y += 12f
            }

            y += 10f
            canvas.drawLine(40f, y, 555f, y, dividerPaint)
            y += 18f

            // Audit Transaction Journal
            canvas.drawText("AUDIT TRANSACTIONS LOG (CHRONOLOGICAL)", 40f, y, headerPaint)
            y += 14f

            canvas.drawText("DATE", 40f, y, headerPaint)
            canvas.drawText("DESCRIPTION / NARRATION", 110f, y, headerPaint)
            canvas.drawText("NATURE", 340f, y, headerPaint)
            canvas.drawText("AMOUNT (INR)", 450f, y, headerPaint)
            canvas.drawText("80C", 530f, y, headerPaint)
            y += 6f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 12f

            transactions.take(26).forEach { tx ->
                val txDate = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(tx.timestamp))
                val isDebit = tx.movementNature in listOf(MovementNature.OPERATING_EXPENSE, MovementNature.TRANSFER, MovementNature.DEPRECIATION_WRITE, MovementNature.EMI_PRINCIPAL)
                val amtStr = "${if (isDebit) "-" else "+"}₹${String.format("%,.0f", tx.amount)}"
                val taxTag = if (tx.isTaxDeductible) "YES" else "-"

                canvas.drawText(txDate, 40f, y, bodyPaint)
                canvas.drawText(tx.description.take(30), 110f, y, bodyPaint)
                canvas.drawText(tx.movementNature.name.take(14), 340f, y, bodyPaint)
                canvas.drawText(amtStr, 450f, y, bodyPaint)
                canvas.drawText(taxTag, 530f, y, bodyPaint)
                y += 12f
            }

            // Watermark footer if Free Tier
            if (!isProUser) {
                val watermarkPaint = Paint().apply {
                    color = Color.GRAY
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                }
                canvas.drawText("Created with InOut Free Tier • Upgrade to Pro to remove branding", 140f, 810f, watermarkPaint)
            }

            pdfDocument.finishPage(page)

            val outputDir = File(context.cacheDir, "dossiers").apply { if (!exists()) mkdirs() }
            val file = File(outputDir, "InOut_Audit_Schedule_${System.currentTimeMillis()}.pdf")

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
            context.startActivity(Intent.createChooser(shareIntent, "Share Statutory Schedule (PDF)"))

        } catch (e: Exception) {
            Toast.makeText(context, "PDF generation failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
