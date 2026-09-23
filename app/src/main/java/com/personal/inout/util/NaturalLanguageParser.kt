package com.personal.inout.util

import android.content.Context
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
import java.util.Calendar
import java.util.regex.Pattern

sealed class ParsedIntent {
    data class Transaction(
        val nature: MovementNature,
        val amount: Double,
        val matchedPocketId: Long?,
        val targetPocketId: Long? = null,
        val category: String,
        val merchant: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isRecurring: Boolean = false,
        val frequency: String = "NONE",
        val targetPersonName: String? = null
    ) : ParsedIntent()

    data class CompoundTransactions(
        val transactions: List<Transaction>
    ) : ParsedIntent()

    data class CreateAccount(
        val name: String,
        val type: PocketType,
        val limit: Double = 0.0,
        val targetAmount: Double = 0.0,
        val targetDateEpoch: Long = 0L
    ) : ParsedIntent()

    data class SetDailyBurn(val newRate: Double) : ParsedIntent()
    data class SaveMacroAlias(val alias: String, val fullCommand: String) : ParsedIntent()
    data class StageDesire(val name: String, val amount: Double) : ParsedIntent()
    data class TriangularSettle(val debtor: String, val creditor: String, val amount: Double) : ParsedIntent()
    data class BreakGoalPot(val potName: String, val destinationPocketId: Long?) : ParsedIntent()
}

object NaturalLanguageParser {

    private val TRANSFER_STEMS = listOf("transf", "transfer", "xfer", "move", "send to", "pay into")

    fun parse(rawInput: String, activePockets: List<VaultPocket>, context: Context): ParsedIntent? {
        val input = rawInput.trim()
        if (input.isBlank()) return null

        val macroPrefs = context.getSharedPreferences("vault_macros", Context.MODE_PRIVATE)
        val expanded = macroPrefs.getString(input, null) ?: input
        val tokens = expanded.split("\\s+".toRegex())

        // 1. Explicit Account Creation
        if (tokens.size >= 3 && tokens[0].equals("new", ignoreCase = true)) {
            val typeWord = tokens[1].lowercase()
            val remaining = tokens.drop(2).joinToString(" ")
            when {
                typeWord.contains("bank") || typeWord.contains("liquid") -> {
                    return ParsedIntent.CreateAccount(name = remaining.trim(), type = PocketType.LIQUID)
                }
                typeWord.contains("card") || typeWord.contains("cc") -> {
                    val limitMatcher = Pattern.compile("(?i)limit\\s*(\\d+)").matcher(remaining)
                    val limit = if (limitMatcher.find()) limitMatcher.group(1)?.toDoubleOrNull() ?: 0.0 else 0.0
                    val cleanName = remaining.replace("(?i)limit\\s*\\d+".toRegex(), "").trim()
                    return ParsedIntent.CreateAccount(name = cleanName, type = PocketType.CREDIT_LINE, limit = limit)
                }
                typeWord.contains("goal") || typeWord.contains("pot") -> {
                    val targetMatcher = Pattern.compile("(?i)target\\s*(\\d+)").matcher(remaining)
                    val target = if (targetMatcher.find()) targetMatcher.group(1)?.toDoubleOrNull() ?: 0.0 else 0.0
                    val cleanName = remaining.replace("(?i)target\\s*\\d+".toRegex(), "").trim()
                    return ParsedIntent.CreateAccount(name = cleanName, type = PocketType.SAVING_GOAL, targetAmount = target)
                }
                typeWord.contains("peer") || typeWord.contains("person") -> {
                    return ParsedIntent.CreateAccount(name = remaining.trim(), type = PocketType.COUNTERPARTY)
                }
            }
        }

        // 2. Set Daily Burn Ceiling
        val burnMatcher = Pattern.compile("(?i)burn\\s+(\\d+)").matcher(expanded)
        if (burnMatcher.matches()) {
            val rate = burnMatcher.group(1)?.toDoubleOrNull()
            if (rate != null) return ParsedIntent.SetDailyBurn(rate)
        }

        // 3. Stage Desires (Cool-off quarantine)
        val wantMatcher = Pattern.compile("(?i)(?:want|stage)\\s+(.+?)\\s+(\\d+)").matcher(expanded)
        if (wantMatcher.matches()) {
            val name = wantMatcher.group(1) ?: "Desire"
            val amt = wantMatcher.group(2)?.toDoubleOrNull() ?: 0.0
            return ParsedIntent.StageDesire(name.trim(), amt)
        }

        // 4. Triangular Peer Settle (settle A to B 500)
        val triMatcher = Pattern.compile("(?i)settle\\s+([A-Za-z0-9_]+)\\s+to\\s+([A-Za-z0-9_]+)\\s+(\\d+)").matcher(expanded)
        if (triMatcher.matches()) {
            val debtor = triMatcher.group(1) ?: ""
            val creditor = triMatcher.group(2) ?: ""
            val amt = triMatcher.group(3)?.toDoubleOrNull() ?: 0.0
            return ParsedIntent.TriangularSettle(debtor, creditor, amt)
        }

        // 5. Transfer Handling (Strict: NEVER degrades to Outflow)
        val isTransferIntent = TRANSFER_STEMS.any { expanded.startsWith(it, ignoreCase = true) }
        if (isTransferIntent) {
            val amountMatcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(expanded)
            val amt = if (amountMatcher.find()) amountMatcher.group(1)?.toDoubleOrNull() ?: 0.0 else 0.0

            // Extract potential source and destination
            var srcPocket: VaultPocket? = null
            var tgtPocket: VaultPocket? = null

            val fromMatcher = Pattern.compile("(?i)from\\s+([A-Za-z0-9_]+)").matcher(expanded)
            if (fromMatcher.find()) {
                val fromName = fromMatcher.group(1)
                srcPocket = activePockets.firstOrNull { it.name.equals(fromName, ignoreCase = true) }
            }

            val toMatcher = Pattern.compile("(?i)to\\s+([A-Za-z0-9_]+)").matcher(expanded)
            var targetNameRaw: String? = null
            if (toMatcher.find()) {
                targetNameRaw = toMatcher.group(1)
                tgtPocket = activePockets.firstOrNull { it.name.equals(targetNameRaw, ignoreCase = true) }
            } else {
                // Heuristic: word directly following transfer stem
                val cleaned = expanded.replace("(?i)transf|transfer|xfer|move".toRegex(), "").trim()
                val candidateTokens = cleaned.split("\\s+".toRegex()).filter { !it.matches("\\d+".toRegex()) && !it.equals("from", true) && !it.equals("to", true) }
                if (candidateTokens.isNotEmpty()) {
                    targetNameRaw = candidateTokens.last()
                    tgtPocket = activePockets.firstOrNull { it.name.equals(targetNameRaw, ignoreCase = true) }
                }
            }

            // Default source to first liquid if not explicitly defined
            if (srcPocket == null) {
                srcPocket = activePockets.firstOrNull { it.pocketType == PocketType.LIQUID }
            }

            // If target account does not exist, HALT and prompt creation as LIQUID
            if (tgtPocket == null) {
                val missingName = targetNameRaw?.trim() ?: "Destination Account"
                return ParsedIntent.CreateAccount(
                    name = missingName.uppercase(),
                    type = PocketType.LIQUID
                )
            }

            return ParsedIntent.Transaction(
                nature = MovementNature.TRANSFER,
                amount = amt,
                matchedPocketId = srcPocket?.id,
                targetPocketId = tgtPocket.id,
                category = "Transfer",
                merchant = "Internal Transfer to ${tgtPocket.name}"
            )
        }

        // 6. Multi-Transaction Compound (e.g. coffee 120 and uber 250)
        if (expanded.contains(" and ", ignoreCase = true) || expanded.contains(" & ")) {
            val parts = expanded.split("(?i)\\s+(?:and|&)\\s+".toRegex())
            val subTransactions = mutableListOf<ParsedIntent.Transaction>()
            for (p in parts) {
                val subParsed = parse(p, activePockets, context)
                if (subParsed is ParsedIntent.Transaction) {
                    subTransactions.add(subParsed)
                }
            }
            if (subTransactions.size > 1) {
                return ParsedIntent.CompoundTransactions(subTransactions)
            }
        }

        // 7. General Transactions (Inflow / Outflow / Peer)
        return parseSingleTransaction(expanded, activePockets)
    }

    private fun parseSingleTransaction(input: String, activePockets: List<VaultPocket>): ParsedIntent.Transaction? {
        val amountPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)")
        val matcher = amountPattern.matcher(input)
        if (!matcher.find()) return null

        val amountStr = matcher.group(1) ?: return null
        val amount = amountStr.toDoubleOrNull() ?: return null

        val cleaned = input.replace(amountStr, "").trim()
        val tokens = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() }

        var nature = MovementNature.OUTFLOW
        var category = "General"
        var merchant = "Transaction"
        var matchedPocketId: Long? = null
        var targetPocketId: Long? = null
        var targetPersonName: String? = null
        var isRecurring = false
        var frequency = "NONE"
        var timestamp = System.currentTimeMillis()

        // Check matched accounts
        for (pocket in activePockets) {
            if (cleaned.contains(pocket.name, ignoreCase = true)) {
                if (pocket.pocketType == PocketType.COUNTERPARTY || pocket.subType == "PEER") {
                    targetPersonName = pocket.name
                } else {
                    matchedPocketId = pocket.id
                }
                break
            }
        }

        val lower = cleaned.lowercase()

        // Recurring cadence
        when {
            lower.contains("monthly") || lower.contains("every month") -> {
                isRecurring = true
                frequency = "MONTHLY"
            }
            lower.contains("weekly") || lower.contains("every week") -> {
                isRecurring = true
                frequency = "WEEKLY"
            }
            lower.contains("daily") || lower.contains("every day") -> {
                isRecurring = true
                frequency = "DAILY"
            }
        }

        // Inflow keywords
        val isInflow = lower.contains("salary") || lower.contains("income") || lower.contains("deposit") ||
                lower.contains("earned") || lower.contains("refund") || lower.contains("cashback")

        // Peer movement keywords
        val isLend = lower.startsWith("lend") || lower.startsWith("lent") || lower.contains(" gave to ")
        val isBorrow = lower.startsWith("borrow") || lower.contains(" took from ")
        val isCollect = lower.startsWith("collect") || lower.contains(" received from ")
        val isRepay = lower.startsWith("repay") || lower.contains(" paid back ")

        when {
            isLend -> {
                nature = MovementNature.PEER_LEND
                category = "Peer Lending"
                val name = tokens.firstOrNull { !it.equals("lend", true) && !it.equals("lent", true) }
                targetPersonName = name ?: "Contact"
                merchant = "Lent to $targetPersonName"
            }
            isBorrow -> {
                nature = MovementNature.PEER_BORROW
                category = "Peer Borrowing"
                val name = tokens.firstOrNull { !it.equals("borrow", true) }
                targetPersonName = name ?: "Contact"
                merchant = "Borrowed from $targetPersonName"
            }
            isCollect -> {
                nature = MovementNature.PEER_COLLECT
                category = "Peer Collection"
                val name = tokens.firstOrNull { !it.equals("collect", true) }
                targetPersonName = name ?: "Contact"
                merchant = "Collected from $targetPersonName"
            }
            isRepay -> {
                nature = MovementNature.PEER_REPAY
                category = "Peer Repayment"
                val name = tokens.firstOrNull { !it.equals("repay", true) }
                targetPersonName = name ?: "Contact"
                merchant = "Repaid $targetPersonName"
            }
            isInflow -> {
                nature = MovementNature.INFLOW
                category = if (lower.contains("salary")) "Salary" else "Income"
                merchant = if (lower.contains("salary")) "Salary Deposit" else "Deposit"
                targetPocketId = matchedPocketId
            }
            else -> {
                nature = MovementNature.OUTFLOW
                category = categorizeExpense(lower)
                merchant = tokens.firstOrNull {
                    it.lowercase() !in listOf("paid", "spent", "for", "at", "to", "from", "on", "in") &&
                            activePockets.none { p -> p.name.equals(it, true) }
                }?.replaceFirstChar { it.uppercase() } ?: category
            }
        }

        return ParsedIntent.Transaction(
            nature = nature,
            amount = amount,
            matchedPocketId = matchedPocketId,
            targetPocketId = targetPocketId,
            category = category,
            merchant = merchant,
            timestamp = timestamp,
            isRecurring = isRecurring,
            frequency = frequency,
            targetPersonName = targetPersonName
        )
    }

    private fun categorizeExpense(text: String): String {
        return when {
            text.contains("coffee") || text.contains("tea") || text.contains("cafe") ||
                    text.contains("food") || text.contains("dinner") || text.contains("lunch") ||
                    text.contains("swiggy") || text.contains("zomato") || text.contains("burger") ||
                    text.contains("pizza") || text.contains("restaurant") -> "Food & Dining"

            text.contains("groceries") || text.contains("grocery") || text.contains("blinkit") ||
                    text.contains("zepto") || text.contains("instamart") || text.contains("milk") ||
                    text.contains("veg") || text.contains("fruits") -> "Groceries"

            text.contains("uber") || text.contains("ola") || text.contains("auto") ||
                    text.contains("cab") || text.contains("metro") || text.contains("fuel") ||
                    text.contains("petrol") || text.contains("diesel") || text.contains("flight") -> "Transport"

            text.contains("amazon") || text.contains("flipkart") || text.contains("myntra") ||
                    text.contains("shoes") || text.contains("clothes") || text.contains("shopping") -> "Shopping"

            text.contains("wifi") || text.contains("electricity") || text.contains("recharge") ||
                    text.contains("bill") || text.contains("rent") || text.contains("water") -> "Bills"

            text.contains("doctor") || text.contains("medicine") || text.contains("pharmacy") ||
                    text.contains("hospital") || text.contains("health") -> "Health"

            text.contains("movie") || text.contains("netflix") || text.contains("spotify") ||
                    text.contains("game") || text.contains("pub") || text.contains("beer") -> "Leisure"

            else -> "General"
        }
    }
}
