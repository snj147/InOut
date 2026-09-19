package com.personal.inout.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.personal.inout.data.FlowRecord
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketBalanceSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfDossierExporter {
    suspend fun generateAndShareDossier(
        context: Context,
        balances: List<PocketBalanceSummary>,
        records: List<FlowRecord>
    ) = withContext(Dispatchers.IO) {
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply {
                textSize = 18f
                isFakeBoldText = true
                color = android.graphics.Color.BLACK
            }
            val subPaint = Paint().apply {
                textSize = 10f
                color = android.graphics.Color.DKGRAY
            }
            val textPaint = Paint().apply {
                textSize = 9f
                color = android.graphics.Color.BLACK
            }
            val redPaint = Paint().apply {
                textSize = 9f
                color = android.graphics.Color.RED
            }
            val greenPaint = Paint().apply {
                textSize = 9f
                color = android.graphics.Color.rgb(30, 130, 60)
            }

            var y = 40f
            canvas.drawText("THE VAULT — FINANCIAL AUDIT DOSSIER", 40f, y, titlePaint)
            y += 16f
            val dateStr = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            canvas.drawText("Generated locally: $dateStr • The Vault Sovereign Ledger", 40f, y, subPaint)
            y += 24f

            canvas.drawLine(40f, y, 555f, y, titlePaint)
            y += 18f

            canvas.drawText("ACCOUNT BALANCES (CASH, CARDS & PEOPLE)", 40f, y, titlePaint.apply { textSize = 11f })
            y += 16f

            balances.forEach { b ->
                val balText = "INR ${String.format("%,.2f", b.currentBalance)}"
                canvas.drawText("${b.name} (${b.pocketType.name})", 50f, y, textPaint)
                canvas.drawText(balText, 450f, y, if (b.currentBalance < 0) redPaint else textPaint)
                y += 14f
            }

            y += 16f
            canvas.drawLine(40f, y, 555f, y, subPaint)
            y += 18f

            canvas.drawText("RECENT LEDGER FLOWS", 40f, y, titlePaint.apply { textSize = 11f })
            y += 16f

            records.take(35).forEach { r ->
                val isOut = r.nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.PEER_REPAY, MovementNature.CARD_PAYMENT)
                val amtText = "${if (isOut) "-" else "+"} INR ${String.format("%,.2f", r.amount)}"
                val dStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(r.timestamp))
                canvas.drawText("$dStr  ${r.note.ifBlank { r.category }}", 50f, y, textPaint)
                canvas.drawText(amtText, 450f, y, if (isOut) redPaint else greenPaint)
                y += 14f
            }

            document.finishPage(page)

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "Vault_Audit_Dossier.pdf")
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
                context.startActivity(Intent.createChooser(intent, "Share Vault Dossier").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
