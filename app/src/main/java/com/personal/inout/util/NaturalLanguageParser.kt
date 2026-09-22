package com.personal.inout.util

import android.content.Context
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

object NaturalLanguageParser {

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else 1 + min(dp[i - 1][j - 1], min(dp[i - 1][j], dp[i][j - 1]))
            }
        }
        return dp[a.length][b.length]
    }

    private fun isFuzzyMatch(word: String, target: String, maxDist: Int = 2): Boolean {
        if (word.equals(target, ignoreCase = true)) return true
        if (word.length >= 3 && target.startsWith(word, ignoreCase = true)) return true
        return levenshtein(word.lowercase(), target.lowercase()) <= maxDist
    }

    fun parse(rawInput: String, activePockets: List<VaultPocket>, context: Context): ParsedIntent? {
        val text = rawInput.trim()
        if (text.isBlank()) return null

        val macroPrefs = context.getSharedPreferences("vault_macros", Context.MODE_PRIVATE)
        val expanded = macroPrefs.getString(text.lowercase(Locale.getDefault()), null) ?: text

        if (expanded.startsWith("alias ", ignoreCase = true)) {
            val parts = expanded.substring(6).split("=", limit = 2)
            if (parts.size == 2) {
                return ParsedIntent.SaveMacroAlias(parts[0].trim().lowercase(), parts[1].trim())
            }
        }

        val burnRegex = Regex("""^(?:burn|budget|daily)\s+(\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE)
        burnRegex.find(expanded)?.let {
            val amt = it.groupValues[1].toDoubleOrNull() ?: return null
            return ParsedIntent.SetDailyBurn(amt)
        }

        val stageRegex = Regex("""^(?:desire|want|stage)\s+(.+?)\s+(\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE)
        stageRegex.find(expanded)?.let {
            val name = it.groupValues[1].trim()
            val amt = it.groupValues[2].toDoubleOrNull() ?: return null
            return ParsedIntent.StageDesire(name, amt)
        }

        val settleRegex = Regex("""^settle\s+(\w+)\s+(?:with|to)\s+(\w+)\s+(\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE)
        settleRegex.find(expanded)?.let {
            val debtor = it.groupValues[1].trim()
            val creditor = it.groupValues[2].trim()
            val amt = it.groupValues[3].toDoubleOrNull() ?: return null
            return ParsedIntent.TriangularSettle(debtor, creditor, amt)
        }

        val breakRegex = Regex("""^break\s+(?:pot|goal)\s+(.+?)(?:\s+(?:to|into)\s+(.+))?$""", RegexOption.IGNORE_CASE)
        breakRegex.find(expanded)?.let {
            val potName = it.groupValues[1].trim()
            val destName = it.groupValues.getOrNull(2)?.trim()
            val destPocket = activePockets.firstOrNull { p -> p.pocketType == PocketType.LIQUID && (destName != null && p.name.contains(destName, ignoreCase = true)) }
                ?: activePockets.firstOrNull { p -> p.pocketType == PocketType.LIQUID }
            return ParsedIntent.BreakGoalPot(potName, destPocket?.id)
        }

        if (expanded.contains(" and ", ignoreCase = true)) {
            val parts = expanded.split(Regex("""\s+and\s+""", RegexOption.IGNORE_CASE))
            val list = mutableListOf<ParsedIntent.Transaction>()
            for (p in parts) {
                val sub = parse(p.trim(), activePockets, context)
                if (sub is ParsedIntent.Transaction) list.add(sub)
            }
            if (list.size >= 2) return ParsedIntent.CompoundTransactions(list)
        }

        val amountRegex = Regex("""(?:₹|rs\.?|inr)?\s*(\d+(?:\.\d+)?)(k)?""", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(expanded) ?: return null
        val rawNum = amountMatch.groupValues[1].toDoubleOrNull() ?: return null
        val isK = amountMatch.groupValues[2].equals("k", ignoreCase = true)
        val amount = if (isK) rawNum * 1000.0 else rawNum

        val matchedPockets = activePockets.filter { p ->
            expanded.contains(Regex("""\b${Regex.escape(p.name)}\b""", RegexOption.IGNORE_CASE))
        }.sortedByDescending { it.name.length }

        val defaultLiquid = activePockets.firstOrNull { it.pocketType == PocketType.LIQUID }
        val futureTimestamp = parseExplicitDate(expanded)
        val (isRecurring, frequency) = parseFrequency(expanded)

        val liquidMatches = matchedPockets.filter { it.pocketType == PocketType.LIQUID }
        if (liquidMatches.size >= 2) {
            val src = liquidMatches[0]
            val tgt = liquidMatches[1]
            return ParsedIntent.Transaction(
                nature = MovementNature.TRANSFER,
                matchedPocketId = src.id,
                targetPocketId = tgt.id,
                amount = amount,
                category = "Transfer",
                merchant = "Internal Transfer (${src.name} ➔ ${tgt.name})",
                timestamp = futureTimestamp ?: System.currentTimeMillis()
            )
        }

        val tokens = expanded.split(Regex("""\s+"""))
        val transferStems = listOf("transfer", "transf", "trans", "trf", "xfer", "move", "shift", "send")
        val isTransferIntent = tokens.any { word -> transferStems.any { stem -> isFuzzyMatch(word, stem) } }
        if (isTransferIntent && liquidMatches.isNotEmpty()) {
            val otherLiquid = activePockets.firstOrNull { it.pocketType == PocketType.LIQUID && it.id != liquidMatches[0].id }
            if (otherLiquid != null) {
                return ParsedIntent.Transaction(
                    nature = MovementNature.TRANSFER,
                    matchedPocketId = liquidMatches[0].id,
                    targetPocketId = otherLiquid.id,
                    amount = amount,
                    category = "Transfer",
                    merchant = "Transfer (${liquidMatches[0].name} ➔ ${otherLiquid.name})",
                    timestamp = futureTimestamp ?: System.currentTimeMillis()
                )
            }
        }

        val lower = expanded.lowercase()
        when {
            lower.contains("loan to") || lower.contains("loaned to") || lower.contains("lent to") -> {
                val peer = activePockets.firstOrNull { it.pocketType == PocketType.COUNTERPARTY && lower.contains(it.name.lowercase()) }
                val personName = peer?.name ?: extractUnknownName(expanded, listOf("loan to", "loaned to", "lent to"))
                return ParsedIntent.Transaction(
                    nature = MovementNature.PEER_LEND,
                    matchedPocketId = liquidMatches.firstOrNull()?.id ?: defaultLiquid?.id,
                    targetPocketId = peer?.id,
                    targetPersonName = personName,
                    amount = amount,
                    category = "Peer Transfer",
                    merchant = "Lent to $personName",
                    timestamp = futureTimestamp ?: System.currentTimeMillis()
                )
            }
            lower.contains("loan from") || lower.contains("borrowed from") || lower.contains("took from") -> {
                val peer = activePockets.firstOrNull { it.pocketType == PocketType.COUNTERPARTY && lower.contains(it.name.lowercase()) }
                val personName = peer?.name ?: extractUnknownName(expanded, listOf("loan from", "borrowed from", "took from"))
                return ParsedIntent.Transaction(
                    nature = MovementNature.PEER_BORROW,
                    matchedPocketId = liquidMatches.firstOrNull()?.id ?: defaultLiquid?.id,
                    targetPocketId = peer?.id,
                    targetPersonName = personName,
                    amount = amount,
                    category = "Peer Transfer",
                    merchant = "Borrowed from $personName",
                    timestamp = futureTimestamp ?: System.currentTimeMillis()
                )
            }
            lower.startsWith("collect") || lower.contains("received from") -> {
                val peer = activePockets.firstOrNull { it.pocketType == PocketType.COUNTERPARTY && lower.contains(it.name.lowercase()) }
                val personName = peer?.name ?: extractUnknownName(expanded, listOf("collect", "received from"))
                return ParsedIntent.Transaction(
                    nature = MovementNature.PEER_COLLECT,
                    matchedPocketId = liquidMatches.firstOrNull()?.id ?: defaultLiquid?.id,
                    targetPocketId = peer?.id,
                    targetPersonName = personName,
                    amount = amount,
                    category = "Peer Transfer",
                    merchant = "Collected from $personName",
                    timestamp = futureTimestamp ?: System.currentTimeMillis()
                )
            }
            lower.startsWith("repay") || lower.contains("returned to") -> {
                val peer = activePockets.firstOrNull { it.pocketType == PocketType.COUNTERPARTY && lower.contains(it.name.lowercase()) }
                val personName = peer?.name ?: extractUnknownName(expanded, listOf("repay", "returned to"))
                return ParsedIntent.Transaction(
                    nature = MovementNature.PEER_REPAY,
                    matchedPocketId = liquidMatches.firstOrNull()?.id ?: defaultLiquid?.id,
                    targetPocketId = peer?.id,
                    targetPersonName = personName,
                    amount = amount,
                    category = "Peer Transfer",
                    merchant = "Repaid to $personName",
                    timestamp = futureTimestamp ?: System.currentTimeMillis()
                )
            }
        }

        val cardMatch = matchedPockets.firstOrNull { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE }
        if (cardMatch != null && (lower.contains("pay") || lower.contains("bill") || lower.contains("clear"))) {
            return ParsedIntent.Transaction(
                nature = MovementNature.CARD_PAYMENT,
                matchedPocketId = liquidMatches.firstOrNull()?.id ?: defaultLiquid?.id,
                targetPocketId = cardMatch.id,
                amount = amount,
                category = "Bill Payment",
                merchant = "Card Bill (${cardMatch.name})",
                timestamp = futureTimestamp ?: System.currentTimeMillis()
            )
        }

        val incomeStems = listOf("salary", "salry", "credited", "bonus", "refund", "inflow", "earned")
        val isIncome = tokens.any { word -> incomeStems.any { stem -> isFuzzyMatch(word, stem) } }
        if (isIncome) {
            return ParsedIntent.Transaction(
                nature = MovementNature.INFLOW,
                matchedPocketId = liquidMatches.firstOrNull()?.id ?: defaultLiquid?.id,
                amount = amount,
                category = "Salary",
                merchant = "Income Deposit",
                timestamp = futureTimestamp ?: System.currentTimeMillis(),
                isRecurring = isRecurring,
                frequency = frequency
            )
        }

        val note = cleanMerchantNote(expanded, amountMatch.value, matchedPockets)
        val category = deduceCategory(note)

        return ParsedIntent.Transaction(
            nature = MovementNature.OUTFLOW,
            matchedPocketId = matchedPockets.firstOrNull()?.id ?: defaultLiquid?.id,
            amount = amount,
            category = category,
            merchant = note.ifBlank { "General Expense" },
            timestamp = futureTimestamp ?: System.currentTimeMillis(),
            isRecurring = isRecurring,
            frequency = frequency
        )
    }

    private fun parseExplicitDate(input: String): Long? {
        val dateRegex = Regex("""(?:from|starts|starting|on)\s+(\d{1,2})(?:st|nd|rd|th)?\s+([a-zA-Z]+)?""", RegexOption.IGNORE_CASE)
        val match = dateRegex.find(input) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val monthStr = match.groupValues.getOrNull(2)?.lowercase(Locale.getDefault())

        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)

        val monthIndex = when (monthStr?.take(3)) {
            "jan" -> 0; "feb" -> 1; "mar" -> 2; "apr" -> 3; "may" -> 4; "jun" -> 5
            "jul" -> 6; "aug" -> 7; "sep" -> 8; "oct" -> 9; "nov" -> 10; "dec" -> 11
            else -> currentMonth
        }

        cal.set(Calendar.MONTH, monthIndex)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)

        if (cal.timeInMillis < System.currentTimeMillis() - (24 * 3600 * 1000L)) {
            cal.set(Calendar.YEAR, currentYear + 1)
        }

        return cal.timeInMillis
    }

    private fun parseFrequency(input: String): Pair<Boolean, String> {
        val lower = input.lowercase()
        return when {
            lower.contains("daily") -> true to "DAILY"
            lower.contains("weekly") -> true to "WEEKLY"
            lower.contains("monthly") -> true to "MONTHLY"
            else -> false to "NONE"
        }
    }

    private fun extractUnknownName(input: String, triggerPhrases: List<String>): String {
        var clean = input
        triggerPhrases.forEach { trigger ->
            clean = clean.replace(Regex("""$trigger\s+""", RegexOption.IGNORE_CASE), "")
        }
        val words = clean.split(Regex("""\s+""")).filter { !it.matches(Regex("""\d+.*""")) }
        return words.firstOrNull { it.length > 2 }?.replaceFirstChar { it.uppercase() } ?: "Friend"
    }

    private fun cleanMerchantNote(input: String, amountToken: String, matchedPockets: List<VaultPocket>): String {
        var note = input.replace(amountToken, "", ignoreCase = true)
        matchedPockets.forEach { note = note.replace(it.name, "", ignoreCase = true) }
        val noiseWords = listOf("spent", "via", "from", "to", "on", "paid", "using", "for", "monthly", "daily", "weekly")
        noiseWords.forEach { w -> note = note.replace(Regex("""\b$w\b""", RegexOption.IGNORE_CASE), "") }
        return note.trim().replace(Regex("""\s+"""), " ")
    }

    private fun deduceCategory(note: String): String {
        val n = note.lowercase()
        return when {
            n.contains("food") || n.contains("chai") || n.contains("tea") || n.contains("coffee") || n.contains("lunch") || n.contains("dinner") || n.contains("swiggy") || n.contains("zomato") -> "Food & Dining"
            n.contains("uber") || n.contains("ola") || n.contains("metro") || n.contains("fuel") || n.contains("petrol") || n.contains("auto") -> "Transport"
            n.contains("groceries") || n.contains("blinkit") || n.contains("zepto") || n.contains("milk") || n.contains("supermarket") -> "Groceries"
            n.contains("amazon") || n.contains("flipkart") || n.contains("myntra") || n.contains("clothes") -> "Shopping"
            n.contains("wifi") || n.contains("electricity") || n.contains("rent") || n.contains("recharge") || n.contains("bill") -> "Bills"
            else -> "General"
        }
    }
}
