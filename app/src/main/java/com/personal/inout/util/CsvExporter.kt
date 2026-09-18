package com.personal.inout.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.personal.inout.data.Transaction
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {

    fun exportAndShareTransactions(context: Context, transactions: List<Transaction>) {
        try {
            val exportDir = File(context.cacheDir, "dossiers")
            if (!exportDir.exists()) exportDir.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(exportDir, "InOut_Ledger_$timeStamp.csv")
            val writer = FileWriter(file)

            // CSV Header
            writer.append("ID,Date,Time,Type,Category,Description,Amount,Recurring,Frequency\n")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            for (tx in transactions) {
                val date = Date(tx.timestamp)
                val safeNote = tx.note.replace("\"", "\"\"").replace("\n", " ")

                writer.append("${tx.id},")
                writer.append("${dateFormat.format(date)},")
                writer.append("${timeFormat.format(date)},")
                writer.append("${tx.flowType},")
                writer.append("\"${tx.category}\",")
                writer.append("\"$safeNote\",")
                writer.append("${tx.amount},")
                writer.append("${tx.isRecurring},")
                writer.append("${tx.frequency}\n")
            }

            writer.flush()
            writer.close()

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Ledger CSV"))

        } catch (e: Exception) {
            Toast.makeText(context, "CSV export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
