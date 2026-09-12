package com.personal.inout.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.personal.inout.InOutApp
import com.personal.inout.data.SmsDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val fullBody = StringBuilder()
        var sender = ""

        for (msg in messages) {
            sender = msg.displayOriginatingAddress ?: ""
            fullBody.append(msg.displayMessageBody)
        }

        val text = fullBody.toString()

        // Ignore common spam keywords
        val lower = text.lowercase()
        if (lower.contains("win") || lower.contains("apply now") || lower.contains("approved for")) {
            return
        }

        // Parse amounts (Supports formats like: Rs. 500, INR 1,450.00, Rs 200)
        val amountPattern = Pattern.compile("(?i)(?:INR|RS\\.?)\\s*([\\d,]+\\.?\\d*)")
        val amountMatcher = amountPattern.matcher(text)

        if (amountMatcher.find()) {
            val rawAmount = amountMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: return
            
            // Extract basic vendor text following 'at', 'to', or 'vpa'
            val merchantPattern = Pattern.compile("(?i)(?:at|to|vpa)\\s+([A-Za-z0-9_]+)")
            val merchantMatcher = merchantPattern.matcher(text)
            val merchant = if (merchantMatcher.find()) merchantMatcher.group(1) ?: "Unknown" else "Merchant"

            val draft = SmsDraft(
                rawSender = sender,
                amount = rawAmount,
                merchant = merchant,
                rawBody = text
            )

            val app = context.applicationContext as InOutApp
            CoroutineScope(Dispatchers.IO).launch {
                app.database.vaultDao().insertSmsDraft(draft)
            }
        }
    }
}
