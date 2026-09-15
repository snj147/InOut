package com.personal.inout.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.personal.inout.data.Transaction
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {
    fun exportAndShareTransactions(context: Context, transactions: List<Transaction>) {
        try {
            val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val csvBuilder = StringBuilder().apply {
                append("ID,Date,Flow,Type,Category,Party/Channel,Amount,Note,Recurring,Frequency\n")
                transactions.forEach { tx ->
                    val cleanNote = tx.note.replace(",", " ").replace("\n", " ")
                    val cleanParty = tx.partyName.replace(",", " ")
                    val dStr = dateFmt.format(Date(tx.timestamp))
                    append("${tx.id},\"$dStr\",${tx.flowType},${tx.type},\"${tx.category}\",\"$cleanParty\",${tx.amount},\"$cleanNote\",${tx.isRecurring},${tx.frequency}\n")
                }
            }

            val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val file = File(exportDir, "InOut_Ledger_${System.currentTimeMillis()}.csv").apply {
                writeText(csvBuilder.toString())
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export Ledger CSV"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
