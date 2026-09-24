package com.personal.inout.util

import android.content.Context
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

sealed class ParsedIntent {
    data class Transaction(
        val amount: Double,
        val nature: MovementNature,
        val matchedPocketId: Long?,
        val targetPocketId: Long?,
        val category: String,
        val merchant: String,
        val timestamp: Long,
        val isRecurring: Boolean,
        val frequency: String,
        val targetPersonName: String?
    ) : ParsedIntent()

    data class CompoundTransactions(
        val transactions: List<Transaction>
    ) : ParsedIntent()

    data class CreateAccount(
        val name: String,
        val type: PocketType
    ) : ParsedIntent()

    data class SetDailyBurn(val newRate: Double) : ParsedIntent()
    data class SaveMacroAlias(val alias: String, val fullCommand: String) : ParsedIntent()
    data class StageDesire(val name: String, val amount: Double) : ParsedIntent()
    data class TriangularSettle(val debtor: String, val creditor: String, val amount: Double) : ParsedIntent()
    data class BreakGoalPot(val potName: String, val destinationPocketId: Long?) : ParsedIntent()
}

object NaturalLanguageParser {

    fun parse(input: String, rawPockets: List<VaultPocket>, context: Context): ParsedIntent? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val lower = trimmed.lowercase()

        // 1. Burn Ceiling Command
        val burnMatcher = Pattern.compile("^(?:set\\s+)?burn(?:\\s+target|\\s+ceiling)?\\s+(\\d+(?:\\.\\d+)?)$", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        if (burnMatcher.find()) {
            val amt = burnMatcher.group(1)?.toDoubleOrNull() ?: return null
            return ParsedIntent.SetDailyBurn(amt)
        }

        // 2. Account Creation Command
        val createAccMatcher = Pattern.compile("^new\\s+(bank|card|cash|goal|person)\\s+(.+)$", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        if (createAccMatcher.find()) {
            val typeStr = createAccMatcher.group(1)?.lowercase() ?: ""
            val nameStr = createAccMatcher.group(2)?.trim() ?: ""
            val pType = when (typeStr) {
                "card" -> PocketType.CREDIT_LINE
                "goal" -> PocketType.SAVING_GOAL
                "person" -> PocketType.COUNTERPARTY
                else -> PocketType.LIQUID
            }
            return ParsedIntent.CreateAccount(nameStr, pType)
        }

        // 3. Stage / Quarantine Desire
        val stageMatcher = Pattern.compile("^stage\\s+(.+?)\\s+(\\d+(?:\\.\\d+)?)$", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        if (stageMatcher.find()) {
            val desireName = stageMatcher.group(1)?.trim() ?: "Item"
            val amt = stageMatcher.group(2)?.toDoubleOrNull() ?: 0.0
            return ParsedIntent.StageDesire(desireName, amt)
        }

        // 4. Peer Commands (Collect, Lent, Borrow, Repay)
        // Syntax: collect 2000 from ABC  OR  collect from ABC 2000
        val collectMatcher = Pattern.compile("^(?:collect|received|got)\\s+(\\d+(?:\\.\\d+)?)\\s+(?:from\\s+)?([a-zA-Z0-9_\\s]+)$", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        if (collectMatcher.find()) {
            val amt = collectMatcher.group(1)?.toDoubleOrNull() ?: 0.0
            val personRaw = collectMatcher.group(2)?.trim()?.removePrefix("from ")?.trim() ?: ""
            if (personRaw.isNotBlank()) {
                val matchedPocket = rawPockets.firstOrNull { it.pocketType == PocketType.COUNTERPARTY && it.name.equals(personRaw, ignoreCase = true) }
                return ParsedIntent.Transaction(
                    amount = amt,
                    nature = MovementNature.PEER_COLLECT,
                    matchedPocketId = null,
                    targetPocketId = matchedPocket?.id,
                    category = "Peer Collection",
                    merchant = "Collected from $personRaw",
                    timestamp = System.currentTimeMillis(),
                    isRecurring = false,
                    frequency = "NONE",
                    targetPersonName = personRaw
                )
            }
        }

        // Syntax: lent 2000 to ABC  OR  lend 2000 to ABC
        val lendMatcher = Pattern.compile("^(?:lent|lend|gave)\\s+(\\d+(?:\\.\\d+)?)\\s+(?:to\\s+)?([a-zA-Z0-9_\\s]+)$", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        if (lendMatcher.find()) {
            val amt = lendMatcher.group(1)?.toDoubleOrNull() ?: 0.0
            val personRaw = lendMatcher.group(2)?.trim()?.removePrefix("to ")?.trim() ?: ""
            if (personRaw.isNotBlank()) {
                val matchedPocket = rawPockets.firstOrNull { it.pocketType == PocketType.COUNTERPARTY && it.name.equals(personRaw, ignoreCase = true) }
                return ParsedIntent.Transaction(
                    amount = amt,
                    nature = MovementNature.PEER_LEND,
                    matchedPocketId = null,
                    targetPocketId = matchedPocket?.id,
                    category = "Peer Transfer",
                    merchant = "Lent to $personRaw",
                    timestamp = System.currentTimeMillis(),
                    isRecurring = false,
                    frequency = "NONE",
                    targetPersonName = personRaw
                )
            }
        }

        // Syntax: borrowed 2000 from ABC  OR  borrow 2000 from ABC
        val borrowMatcher = Pattern.compile("^(?:borrowed|borrow)\\s+(\\d+(?:\\.\\d+)?)\\s+(?:from\\s+)?([a-zA-Z0-9_\\s]+)$", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        if (borrowMatcher.find()) {
            val amt = borrowMatcher.group(1)?.toDoubleOrNull() ?: 0.0
            val personRaw = borrowMatcher.group(2)?.trim()?.removePrefix("from ")?.trim() ?: ""
            if (personRaw.isNotBlank()) {
                val matchedPocket = rawPockets.firstOrNull { it.pocketType == PocketType.COUNTERPARTY && it.name.equals(personRaw, ignoreCase = true) }
                return ParsedIntent.Transaction(
                    amount = amt,
                    nature = MovementNature.PEER_BORROW,
                    matchedPocketId = null,
                    targetPocketId = matchedPocket?.id,
                    category = "Peer Borrowing",
                    merchant = "Borrowed from $personRaw",
                    timestamp = System.currentTimeMillis(),
                    isRecurring = false,
                    frequency = "NONE",
                    targetPersonName = personRaw
                )
            }
        }

        // 5. Transfer between accounts: transfer 5000 from SBI to HDFC
        val transferMatcher = Pattern.compile("^transfer\\s+(\\d+(?:\\.\\d+)?)\\s+from\\s+(.+?)\\s+to\\s+(.+)$", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        if (transferMatcher.find()) {
            val amt = transferMatcher.group(1)?.toDoubleOrNull() ?: 0.0
            val srcName = transferMatcher.group(2)?.trim() ?: ""
            val tgtName = transferMatcher.group(3)?.trim() ?: ""
            val srcPocket = rawPockets.firstOrNull { it.name.equals(srcName, ignoreCase = true) }
            val tgtPocket = rawPockets.firstOrNull { it.name.equals(tgtName, ignoreCase = true) }
            return ParsedIntent.Transaction(
                amount = amt,
                nature = MovementNature.TRANSFER,
                matchedPocketId = srcPocket?.id,
                targetPocketId = tgtPocket?.id,
                category = "Account Transfer",
                merchant = "Transfer $srcName → $tgtName",
                timestamp = System.currentTimeMillis(),
                isRecurring = false,
                frequency = "NONE",
                targetPersonName = null
            )
        }

        // 6. Generic Standard Flow: <merchant/category> <amount> [from/in <account>]
        // E.g.: "coffee 150 sbi" OR "salary 50000 into hdfc" OR "shoes 2000 hdfc"
        var isRecurring = false
        var cadence = "NONE"
        if (lower.contains("every month") || lower.contains("monthly")) {
            isRecurring = true
            cadence = "MONTHLY"
        } else if (lower.contains("every week") || lower.contains("weekly")) {
            isRecurring = true
            cadence = "WEEKLY"
        } else if (lower.contains("every day") || lower.contains("daily")) {
            isRecurring = true
            cadence = "DAILY"
        }

        val tokens = trimmed.split("\\s+".toRegex())
        var amount: Double? = null
        var amountIndex = -1

        for (i in tokens.indices) {
            val parsedDouble = tokens[i].replace("₹", "").replace(",", "").toDoubleOrNull()
            if (parsedDouble != null && parsedDouble > 0) {
                amount = parsedDouble
                amountIndex = i
                break
            }
        }

        if (amount == null || amountIndex == -1) return null

        val beforeAmt = tokens.subList(0, amountIndex).joinToString(" ").trim()
        val afterAmt = tokens.subList(amountIndex + 1, tokens.size).joinToString(" ").trim()

        var matchedPocket: VaultPocket? = null
        for (pocket in rawPockets) {
            if (afterAmt.contains(pocket.name, ignoreCase = true) || beforeAmt.contains(pocket.name, ignoreCase = true)) {
                matchedPocket = pocket
                break
            }
        }

        val isInflow = lower.startsWith("salary") || lower.startsWith("income") || lower.startsWith("deposit") || lower.contains("credit")
        val nature = if (isInflow) MovementNature.INFLOW else MovementNature.OUTFLOW

        val cleanedNote = beforeAmt
            .removePrefix("every month")
            .removePrefix("monthly")
            .removePrefix("weekly")
            .removePrefix("daily")
            .trim()
            .ifBlank { if (isInflow) "Income" else "General Expense" }

        val category = when {
            cleanedNote.contains("coffee", true) || cleanedNote.contains("food", true) || cleanedNote.contains("dinner", true) -> "Food & Dining"
            cleanedNote.contains("shoe", true) || cleanedNote.contains("cloth", true) || cleanedNote.contains("shopping", true) -> "Shopping"
            cleanedNote.contains("rent", true) || cleanedNote.contains("bill", true) || cleanedNote.contains("sip", true) -> "Bills"
            cleanedNote.contains("salary", true) -> "Salary"
            else -> if (isInflow) "Salary" else "General"
        }

        return ParsedIntent.Transaction(
            amount = amount,
            nature = nature,
            matchedPocketId = if (isInflow) null else matchedPocket?.id,
            targetPocketId = if (isInflow) matchedPocket?.id else null,
            category = category,
            merchant = cleanedNote,
            timestamp = System.currentTimeMillis(),
            isRecurring = isRecurring,
            frequency = cadence,
            targetPersonName = null
        )
    }
}
