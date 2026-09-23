package com.personal.inout.util

import android.content.Context
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

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

        // 3. Stage Desires
        val wantMatcher = Pattern.compile("(?i)(?:want|stage)\\s+(.+?)\\s+(\\d+)").matcher(expanded)
        if (wantMatcher.matches()) {
            val name = wantMatcher.group(1) ?: "Desire"
            val amt = wantMatcher.group(2)?.toDoubleOrNull() ?: 0.0
            return ParsedIntent.StageDesire(name.trim(), amt)
        }

        // 4. Triangular Peer Settle
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
                val cleaned = expanded.replace("(?i)transf|transfer|xfer|move".toRegex(), "").trim()
                val candidateTokens = cleaned.split("\\s+".toRegex()).filter { !it.matches("\\d+".toRegex()) && !it.equals("from", true) && !it.equals("to", true) }
                if (candidateTokens.isNotEmpty()) {
                    targetNameRaw = candidateTokens.last()
                    tgtPocket = activePockets.firstOrNull { it.name.equals(targetNameRaw, ignoreCase = true) }
                }
            }

            if (srcPocket == null) {
                srcPocket = activePockets.firstOrNull { it.pocketType == PocketType.LIQUID }
            }

            // Halt and prompt creation if destination bank does not exist
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

        // 6. Compound Transactions (coffee 120 and uber 250)
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

        // 7. General Transactions
        return parseSingleTransaction(expanded, activePockets)
    }

    private fun parseSingleTransaction(input: String, activePockets: List<VaultPocket>): ParsedIntent.Transaction? {
        val amountPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)")
        val matcher = amountPattern.matcher(input)
        if (!matcher.find()) return null

        val amountStr = matcher.group(1) ?: return null
        val amount = amountStr.toDoubleOrNull() ?: return null

        var cleaned = input.replace(amountStr, "").trim()
        val lower = cleaned.lowercase()

        // DETERMINISTIC RECURRING CONTRACT:
        // Must explicitly state cadence ("monthly", "weekly", "daily", "every month", etc.)
        var isRecurring = false
        var frequency = "NONE"
        var timestamp = System.currentTimeMillis()

        val isMonthly = lower.contains("monthly") || lower.contains("every month")
        val isWeekly = lower.contains("weekly") || lower.contains("every week")
        val isDaily = lower.contains("daily") || lower.contains("every day")

        if (isMonthly || isWeekly || isDaily) {
            isRecurring = true
            frequency = when {
                isMonthly -> "MONTHLY"
                isWeekly -> "WEEKLY"
                else -> "DAILY"
            }

            // Strip the recurrence keyword
            cleaned = cleaned.replace("(?i)monthly|every\\s+month|weekly|every\\s+week|daily|every\\s+day".toRegex(), "").trim()

            // Resolve date contract:
            // e.g. "from 01/10/2026", "start 10 Oct 2026", or "on 1st"
            timestamp = resolveScheduleDate(cleaned)
            cleaned = cleaned.replace("(?i)(?:from|start|on)\\s+\\d+(?:st|nd|rd|th)?(?:[/-]\\d+[/-]\\d+)?".toRegex(), "").trim()
        }

        var nature = MovementNature.OUTFLOW
        var category = "General"
        var merchant = "Transaction"
        var matchedPocketId: Long? = null
        var targetPocketId: Long? = null
        var targetPersonName: String? = null

        // Match accounts
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

        val tokens = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val finalLower = cleaned.lowercase()

        val isInflow = finalLower.contains("salary") || finalLower.contains("income") || finalLower.contains("deposit") ||
                finalLower.contains("earned") || finalLower.contains("refund") || finalLower.contains("cashback")

        val isLend = finalLower.startsWith("lend") || finalLower.startsWith("lent") || finalLower.contains(" gave to ")
        val isBorrow = finalLower.startsWith("borrow") || finalLower.contains(" took from ")
        val isCollect = finalLower.startsWith("collect") || finalLower.contains(" received from ")
        val isRepay = finalLower.startsWith("repay") || finalLower.contains(" paid back ")

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
                category = if (finalLower.contains("salary")) "Salary" else "Income"
                merchant = if (finalLower.contains("salary")) "Salary Deposit" else "Deposit"
                targetPocketId = matchedPocketId
            }
            else -> {
                nature = MovementNature.OUTFLOW
                category = categorizeExpense(finalLower)
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

    // STRICT DATE RESOLUTION: Never guesses ambiguously into the past
    private fun resolveScheduleDate(input: String): Long {
        val nowCal = Calendar.getInstance()

        // Check for full explicit date: dd/MM/yyyy
        val fullDateMatcher = Pattern.compile("(?i)(?:from|start|on)\\s+(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})").matcher(input)
        if (fullDateMatcher.find()) {
            val d = fullDateMatcher.group(1)?.toIntOrNull() ?: 1
            val m = (fullDateMatcher.group(2)?.toIntOrNull() ?: 1) - 1
            val y = fullDateMatcher.group(3)?.toIntOrNull() ?: nowCal.get(Calendar.YEAR)
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, y)
                set(Calendar.MONTH, m)
                set(Calendar.DAY_OF_MONTH, d)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        // Check for day of month: "on 1st", "on 5", "from 10th"
        val dayMatcher = Pattern.compile("(?i)(?:on|from|start)\\s+(\\d{1,2})(?:st|nd|rd|th)?").matcher(input)
        if (dayMatcher.find()) {
            val targetDay = dayMatcher.group(1)?.toIntOrNull() ?: 1
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val todayDay = cal.get(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, targetDay)

            // If the day is already past in this current month, roll forward to next month
            if (targetDay < todayDay) {
                cal.add(Calendar.MONTH, 1)
            }
            return cal.timeInMillis
        }

        // If no explicit date token is supplied, start from today
        return System.currentTimeMillis()
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
