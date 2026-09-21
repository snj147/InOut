package com.personal.inout.util

import android.content.Context
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
import java.util.Locale
import java.util.regex.Pattern

sealed class ParsedIntent {
    data class Transaction(
        val nature: MovementNature,
        val amount: Double,
        val category: String,
        val merchant: String,
        val matchedPocketId: Long?,
        val targetPocketId: Long? = null,
        val targetPersonName: String? = null,
        val isRecurring: Boolean = false,
        val frequency: String = "NONE",
        val timestamp: Long = System.currentTimeMillis()
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
    data class TriangularSettle(val debtor: String, val creditor: String, val amount: Double? = null) : ParsedIntent()
}

object FuzzyCommandParser {

    private val AMOUNT_PATTERN = Pattern.compile("(?:₹|rs\\.?|inr)?\\s*(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)

    fun parse(rawInput: String, activePockets: List<VaultPocket>, context: Context? = null): ParsedIntent? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null

        var workingInput = trimmed
        if (context != null) {
            val prefs = context.getSharedPreferences("vault_macros", Context.MODE_PRIVATE)
            val matchedMacro = prefs.getString(trimmed.lowercase(Locale.ROOT), null)
            if (matchedMacro != null) {
                workingInput = matchedMacro
            }
        }

        val lower = workingInput.lowercase(Locale.ROOT)

        // 1. Shorthand Macro Alias
        if (lower.startsWith("alias ")) {
            val parts = workingInput.substring(6).split("=", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return ParsedIntent.SaveMacroAlias(parts[0].trim().lowercase(Locale.ROOT), parts[1].trim())
            }
        }

        // 2. Set Daily Burn
        if (lower.startsWith("burn ") || lower.startsWith("set burn ")) {
            val amtMatcher = AMOUNT_PATTERN.matcher(workingInput)
            if (amtMatcher.find()) {
                val amt = amtMatcher.group(1)?.toDoubleOrNull()
                if (amt != null && amt > 0) return ParsedIntent.SetDailyBurn(amt)
            }
        }

        // 3. Staged Cool-Off Desires
        if (lower.startsWith("want ") || lower.startsWith("hold ")) {
            val amtMatcher = AMOUNT_PATTERN.matcher(workingInput)
            if (amtMatcher.find()) {
                val amt = amtMatcher.group(1)?.toDoubleOrNull()
                val token = amtMatcher.group(0) ?: ""
                val item = workingInput.substringAfter(" ").replace(token, "").trim()
                if (amt != null && amt > 0 && item.isNotBlank()) {
                    return ParsedIntent.StageDesire(item, amt)
                }
            }
        }

        // 4. Triangular Peer Debt Resolution
        if (lower.startsWith("settle ")) {
            val clean = workingInput.substring(7).trim()
            val amtMatcher = AMOUNT_PATTERN.matcher(clean)
            var optAmt: Double? = null
            var textPart = clean
            if (amtMatcher.find()) {
                optAmt = amtMatcher.group(1)?.toDoubleOrNull()
                textPart = clean.replace(amtMatcher.group(0) ?: "", "").trim()
            }
            val tokens = textPart.split(Regex("(?i)\\bwith\\b"))
            if (tokens.size == 2) {
                return ParsedIntent.TriangularSettle(tokens[0].trim(), tokens[1].trim(), optAmt)
            }
        }

        // 5. Account / Pot Creation
        if (lower.startsWith("create ") || lower.startsWith("add ")) {
            val stripped = workingInput.substringAfter(" ").trim()
            val subLower = stripped.lowercase(Locale.ROOT)

            when {
                subLower.startsWith("bank ") || subLower.startsWith("account ") -> {
                    val name = stripped.substringAfter(" ").trim()
                    if (name.isNotBlank()) return ParsedIntent.CreateAccount(name, PocketType.LIQUID)
                }
                subLower.startsWith("cash ") || subLower.startsWith("wallet ") -> {
                    val name = stripped.substringAfter(" ").trim().ifBlank { "Cash Wallet" }
                    return ParsedIntent.CreateAccount(name, PocketType.LIQUID)
                }
                subLower.startsWith("card ") || subLower.startsWith("cc ") -> {
                    val body = stripped.substringAfter(" ").trim()
                    val amtMatcher = AMOUNT_PATTERN.matcher(body)
                    var limit = 0.0
                    var cardName = body
                    if (amtMatcher.find()) {
                        limit = amtMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                        cardName = body.replace(amtMatcher.group(0) ?: "", "").replace("limit", "").trim()
                    }
                    if (cardName.isNotBlank()) return ParsedIntent.CreateAccount(cardName, PocketType.CREDIT_LINE, limit)
                }
                subLower.startsWith("friend ") || subLower.startsWith("person ") || subLower.startsWith("peer ") -> {
                    val name = stripped.substringAfter(" ").trim()
                    if (name.isNotBlank()) return ParsedIntent.CreateAccount(name, PocketType.COUNTERPARTY)
                }
            }
        }

        // 6. Goal Pot Creation: goal phone 40000 by nov
        if (lower.startsWith("goal ") || lower.startsWith("pot ") || lower.startsWith("target ")) {
            val body = workingInput.substringAfter(" ").trim()
            val amtMatcher = AMOUNT_PATTERN.matcher(body)
            if (amtMatcher.find()) {
                val targetAmt = amtMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                val amtToken = amtMatcher.group(0) ?: ""
                val potName = body.replace(amtToken, "").replace(Regex("(?i)\\bby\\b.*"), "").trim()
                if (potName.isNotBlank() && targetAmt > 0.0) {
                    return ParsedIntent.CreateAccount(potName, PocketType.SAVING_GOAL, targetAmount = targetAmt)
                }
            }
        }

        // 7. General Transactions & Salary Recurring
        val matcher = AMOUNT_PATTERN.matcher(workingInput)
        var amount: Double? = null
        var amountToken = ""
        while (matcher.find()) {
            val candidate = matcher.group(1)?.toDoubleOrNull()
            if (candidate != null && candidate > 0.0) {
                amount = candidate
                amountToken = matcher.group(0) ?: ""
                break
            }
        }
        if (amount == null) return null

        val isRecurring = lower.contains("monthly") || lower.contains("weekly") || lower.contains("daily") || lower.contains("every month")
        val freq = when {
            lower.contains("monthly") || lower.contains("every month") -> "MONTHLY"
            lower.contains("weekly") || lower.contains("every week") -> "WEEKLY"
            lower.contains("daily") || lower.contains("every day") -> "DAILY"
            else -> "NONE"
        }

        val cleanPrompt = workingInput.replace(amountToken, " ").replace(Regex("\\s+"), " ").trim()
        val cleanLower = cleanPrompt.lowercase(Locale.ROOT)

        // Strict & Fuzzy Intent Mapping
        val isCollect = cleanLower.startsWith("collect") || cleanLower.contains("collected") || cleanLower.contains("got back")
        val isSalary = cleanLower.contains("salary") || cleanLower.contains("stipend")
        val isLend = (cleanLower.startsWith("lent") || cleanLower.startsWith("lend") || cleanLower.contains(" lend to ") || cleanLower.contains(" lent to ")) && !isCollect
        val isBorrow = cleanLower.startsWith("borrow") || cleanLower.startsWith("borrowed")
        val isRepay = cleanLower.startsWith("repay") || cleanLower.startsWith("repaid")
        val isInflow = (cleanLower.startsWith("got") || cleanLower.startsWith("received") || cleanLower.contains("credited") || isSalary) && !isCollect && !isBorrow

        val nature = when {
            isCollect -> MovementNature.PEER_COLLECT
            isRepay -> MovementNature.PEER_REPAY
            isLend -> MovementNature.PEER_LEND
            isBorrow -> MovementNature.PEER_BORROW
            isInflow -> MovementNature.INFLOW
            cleanLower.startsWith("spent") || cleanLower.contains(" paid ") || cleanLower.startsWith("paid") || cleanLower.contains(" buy ") || cleanLower.contains(" bought ") -> MovementNature.OUTFLOW
            cleanLower.startsWith("transferred") || cleanLower.contains(" transfer ") || cleanLower.contains(" save ") || cleanLower.contains(" to pot ") -> MovementNature.TRANSFER
            else -> MovementNature.OUTFLOW
        }

        val liquidPockets = activePockets.filter { it.pocketType == PocketType.LIQUID }
        val peerPockets = activePockets.filter { it.pocketType == PocketType.COUNTERPARTY || it.pocketType == PocketType.PEER }
        val goalPockets = activePockets.filter { it.pocketType == PocketType.SAVING_GOAL }

        var matchedPocketId: Long? = null
        var targetPocketId: Long? = null
        var targetPersonName: String? = null

        // Match Bank / Liquid Pocket
        var bestScore = Int.MAX_VALUE
        var bestPocket: VaultPocket? = null
        for (pocket in activePockets.filter { it.pocketType == PocketType.LIQUID || it.pocketType == PocketType.CREDIT_LINE }) {
            val pName = pocket.name.lowercase(Locale.ROOT)
            if (cleanLower.contains(pName)) {
                bestPocket = pocket
                break
            }
            for (word in cleanLower.split(" ")) {
                if (word.length >= 3) {
                    val dist = levenshtein(word, pName)
                    if (dist <= 1 && dist < bestScore) {
                        bestScore = dist
                        bestPocket = pocket
                    }
                }
            }
        }
        matchedPocketId = bestPocket?.id ?: liquidPockets.firstOrNull()?.id

        // Goal Pot destination
        if (cleanLower.contains("pot") || cleanLower.contains("goal") || cleanLower.contains("save")) {
            val matchedGoal = goalPockets.firstOrNull { cleanLower.contains(it.name.lowercase(Locale.ROOT)) }
            if (matchedGoal != null) targetPocketId = matchedGoal.id
        }

        // Peer name extraction
        if (nature in listOf(MovementNature.PEER_LEND, MovementNature.PEER_BORROW, MovementNature.PEER_COLLECT, MovementNature.PEER_REPAY)) {
            val peerKeywords = listOf("to", "from", "with", "by")
            val tokens = cleanPrompt.split(Regex("\\s+"))
            for (i in 0 until tokens.size - 1) {
                if (tokens[i].lowercase(Locale.ROOT) in peerKeywords) {
                    val candidate = tokens[i + 1].replace(Regex("[^a-zA-Z0-9]"), "")
                    if (candidate.length > 1 && !candidate.equals("bank", ignoreCase = true)) {
                        targetPersonName = candidate
                        val matchedPeer = peerPockets.firstOrNull { it.name.equals(candidate, ignoreCase = true) }
                        if (matchedPeer != null) targetPocketId = matchedPeer.id
                        break
                    }
                }
            }
            if (targetPersonName == null) {
                for (peer in peerPockets) {
                    if (cleanLower.contains(peer.name.lowercase(Locale.ROOT))) {
                        targetPersonName = peer.name
                        targetPocketId = peer.id
                        break
                    }
                }
            }
        }

        val category = if (isSalary) "Salary" else inferCategory(cleanLower)
        val note = cleanPrompt
            .replace(Regex("(?i)^(spent|paid|bought|got|received|lent|lend|borrowed|borrow|transferred|collected|collect|repaid|repay|salary|auto)"), "")
            .replace(Regex("(?i)\\b(on|for|at|from|to|in|via|monthly|weekly|daily|every month)\\b"), "")
            .trim()
            .ifBlank { category }

        return ParsedIntent.Transaction(
            nature = nature,
            amount = amount,
            category = category,
            merchant = note,
            matchedPocketId = matchedPocketId,
            targetPocketId = targetPocketId,
            targetPersonName = targetPersonName,
            isRecurring = isRecurring,
            frequency = freq
        )
    }

    private fun inferCategory(text: String): String {
        return when {
            text.contains("food") || text.contains("dinner") || text.contains("lunch") || text.contains("swiggy") || text.contains("zomato") || text.contains("cafe") || text.contains("tea") || text.contains("coffee") || text.contains("chai") -> "Food & Dining"
            text.contains("groceries") || text.contains("milk") || text.contains("blinkit") || text.contains("zepto") || text.contains("instamart") || text.contains("vegetables") -> "Groceries"
            text.contains("uber") || text.contains("ola") || text.contains("auto") || text.contains("petrol") || text.contains("fuel") || text.contains("metro") || text.contains("cab") -> "Transport"
            text.contains("recharge") || text.contains("bill") || text.contains("electricity") || text.contains("wifi") || text.contains("rent") -> "Bills"
            text.contains("amazon") || text.contains("flipkart") || text.contains("clothes") || text.contains("shoes") || text.contains("shopping") -> "Shopping"
            text.contains("medicine") || text.contains("doctor") || text.contains("pharma") || text.contains("hospital") -> "Health"
            text.contains("movie") || text.contains("netflix") || text.contains("party") || text.contains("trip") -> "Leisure"
            text.contains("salary") || text.contains("stipend") || text.contains("bonus") -> "Salary"
            text.contains("pot") || text.contains("goal") || text.contains("save") -> "Savings Pot"
            text.contains("lend") || text.contains("lent") || text.contains("borrow") || text.contains("collect") || text.contains("repay") -> "Peer Transfer"
            else -> "General"
        }
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        var costs = IntArray(rhs.length + 1) { it }
        for (i in 1..lhs.length) {
            val newCosts = IntArray(rhs.length + 1)
            newCosts[0] = i
            for (j in 1..rhs.length) {
                val match = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                val costReplace = costs[j - 1] + match
                val costInsert = costs[j] + 1
                val costDelete = newCosts[j - 1] + 1
                newCosts[j] = minOf(costInsert, costDelete, costReplace)
            }
            costs = newCosts
        }
        return costs[rhs.length]
    }
}
