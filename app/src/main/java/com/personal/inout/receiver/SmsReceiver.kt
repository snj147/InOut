package com.personal.inout.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.personal.inout.data.AppDatabase
import com.personal.inout.data.SmsDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }
        val sender = messages[0].displayOriginatingAddress ?: "Unknown"

        val parsed = parseTransactionSms(fullBody) ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                db.vaultDao().insertSmsDraft(
                    SmsDraft(
                        rawSender = sender,
                        rawBody = fullBody,
                        amount = parsed.amount,
                        merchant = parsed.merchant
                    )
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun parseTransactionSms(body: String): ParsedSmsData? {
        val lower = body.lowercase()
        val isTx = listOf("debited", "spent", "paid", "sent", "transferred", "withdrawn", "credited", "received")
            .any { lower.contains(it) }
        if (!isTx) return null

        val amountRegex = Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*([0-9]+(?:\\.[0-9]{1,2})?)")
        val matcher = amountRegex.matcher(body)
        val amount = if (matcher.find()) matcher.group(1)?.toDoubleOrNull() ?: 0.0 else return null
        if (amount <= 0.0) return null

        val merchantRegex = Pattern.compile("(?i)(?:at|to|info|vpa|ref)\\s+([A-Za-z0-9_@.\\-]{3,20})")
        val mMatcher = merchantRegex.matcher(body)
        val merchant = if (mMatcher.find()) mMatcher.group(1)?.trim() ?: "Parsed Transaction" else "Bank Alert"

        return ParsedSmsData(amount = amount, merchant = merchant)
    }

    private data class ParsedSmsData(val amount: Double, val merchant: String)
}
