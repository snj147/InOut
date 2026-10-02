package com.personal.inout.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.personal.inout.data.LedgerPocket
import com.personal.inout.data.LedgerTransaction
import com.personal.inout.data.MovementNature
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {

    /**
     * BRD Rule 34.1: Exports an Indian Statutory Multi-Column Journal (readable by Tally, BUSY, Zoho Books, and Excel)
     */
    fun exportAndShareTransactions(
        context: Context,
        transactions: List<LedgerTransaction>,
        pockets: List<LedgerPocket> = emptyList()
    ) {
        try {
            val exportDir = File(context.cacheDir, "dossiers")
            if (!exportDir.exists()) exportDir.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(exportDir, "InOut_Indian_Ledger_$timeStamp.csv")
            val writer = FileWriter(file)

            val pocketMap = pockets.associateBy { it.id }

            // Statutory Multi-Column Format: Date, Voucher Type, Account / Particulars, Dr/Cr, Amount, Narration, Tax Deductible (80C), Reimbursable
            writer.append("Voucher ID,Date,Time,Voucher Type,Source Ledger,Target Ledger,Particulars/Category,Direction,Amount,Narration,Section 80C Tax Deductible,Corporate Reimbursable\n")

            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            for (tx in transactions) {
                val date = Date(tx.timestamp)
                val safeDesc = tx.description.replace("\"", "\"\"").replace("\n", " ")
                val sourceName = pocketMap[tx.sourcePocketId]?.name ?: "Bank/Cash"
                val targetName = tx.targetPocketId?.let { pocketMap[it]?.name } ?: "None"

                val voucherType = when (tx.movementNature) {
                    MovementNature.OPERATING_EXPENSE -> "Payment"
                    MovementNature.OPERATING_INCOME -> "Receipt"
                    MovementNature.TRANSFER -> "Contra"
                    MovementNature.OPENING_BASELINE -> "Opening Balance"
                    MovementNature.VALUATION_MARK -> "Journal"
                    MovementNature.DEPRECIATION_WRITE -> "Journal"
                    MovementNature.EMI_PRINCIPAL -> "Payment"
                    MovementNature.RECONCILIATION_ADJ -> "Journal"
                }

                val direction = when (tx.movementNature) {
                    MovementNature.OPERATING_INCOME -> "CR"
                    else -> "DR"
                }

                writer.append("${tx.id},")
                writer.append("${dateFormat.format(date)},")
                writer.append("${timeFormat.format(date)},")
                writer.append("\"$voucherType\",")
                writer.append("\"$sourceName\",")
                writer.append("\"$targetName\",")
                writer.append("\"${tx.category}\",")
                writer.append("$direction,")
                writer.append("${tx.amount},")
                writer.append("\"$safeDesc\",")
                writer.append("${if (tx.isTaxDeductible) "YES" else "NO"},")
                writer.append("${if (tx.isReimbursable) "YES" else "NO"}\n")
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
            context.startActivity(Intent.createChooser(shareIntent, "Share Indian Statutory Ledger (CSV)"))

        } catch (e: Exception) {
            Toast.makeText(context, "CSV export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * BRD Rule 34.1: Native TallyPrime / Tally ERP 9 XML Export.
     * Imports directly into Tally via: Import Data -> Transactions.
     */
    fun exportAndShareTallyXml(
        context: Context,
        transactions: List<LedgerTransaction>,
        pockets: List<LedgerPocket>
    ) {
        try {
            val exportDir = File(context.cacheDir, "dossiers")
            if (!exportDir.exists()) exportDir.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(exportDir, "InOut_TallyPrime_Import_$timeStamp.xml")
            val writer = FileWriter(file)

            val pocketMap = pockets.associateBy { it.id }
            val tallyDateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

            writer.append("""<ENVELOPE>
  <HEADER>
    <TALLYREQUEST>Import Data</TALLYREQUEST>
  </HEADER>
  <BODY>
    <IMPORTDATA>
      <REQUESTDESC>
        <REPORTNAME>Vouchers</REPORTNAME>
      </REQUESTDESC>
      <REQUESTDATA>
""")

            for (tx in transactions) {
                val dateStr = tallyDateFormat.format(Date(tx.timestamp))
                val safeDesc = tx.description.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                val sourceLedger = pocketMap[tx.sourcePocketId]?.name?.replace("&", "&amp;") ?: "Cash"

                val (vType, isDebit) = when (tx.movementNature) {
                    MovementNature.OPERATING_EXPENSE -> "Payment" to true
                    MovementNature.OPERATING_INCOME -> "Receipt" to false
                    MovementNature.TRANSFER -> "Contra" to true
                    else -> "Journal" to true
                }

                writer.append("""        <VOUCHER VCHTYPE="$vType" ACTION="Create">
          <DATE>$dateStr</DATE>
          <NARRATION>$safeDesc</NARRATION>
          <VOUCHERTYPENAME>$vType</VOUCHERTYPENAME>
          <ALLLEDGERENTRIES.LIST>
            <LEDGERNAME>$sourceLedger</LEDGERNAME>
            <ISDEEMEDPOSITIVE>${if (isDebit) "No" else "Yes"}</ISDEEMEDPOSITIVE>
            <AMOUNT>${if (isDebit) tx.amount else -tx.amount}</AMOUNT>
          </ALLLEDGERENTRIES.LIST>
          <ALLLEDGERENTRIES.LIST>
            <LEDGERNAME>${tx.category.replace("&", "&amp;")}</LEDGERNAME>
            <ISDEEMEDPOSITIVE>${if (isDebit) "Yes" else "No"}</ISDEEMEDPOSITIVE>
            <AMOUNT>${if (isDebit) -tx.amount else tx.amount}</AMOUNT>
          </ALLLEDGERENTRIES.LIST>
        </VOUCHER>
""")
            }

            writer.append("""      </REQUESTDATA>
    </IMPORTDATA>
  </BODY>
</ENVELOPE>""")

            writer.flush()
            writer.close()

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share TallyPrime XML Voucher"))

        } catch (e: Exception) {
            Toast.makeText(context, "Tally XML export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
