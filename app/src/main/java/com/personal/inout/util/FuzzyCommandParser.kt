package com.personal.inout.util

import android.content.Context
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
import java.util.Calendar
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

    data class BreakGoalPot(
        val potName: String,
        val destinationPocketId: Long? = null
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
        if (lower.contains("burn") && (lower.contains("set") || lower.startsWith("burn "))) {
            val amtMatcher = AMOUNT_PATTERN.matcher(workingInput)
            if (amtMatcher.find()) {
                val amt = amtMatcher.group(1)?.toDoubleOrNull()
                if (amt != null && amt > 0) return ParsedIntent.SetDailyBurn(amt)
            }
        }

        // 3. Temptation Delay Quarantine
        if (lower.startsWith("want ") || lower.startsWith("hold ") || lower.contains("wishlist") || lower.contains("cool off")) {
            val amtMatcher = AMOUNT_PATTERN.matcher(workingInput)
            if (amtMatcher.find()) {
                val amt = amtMatcher.group(1)?.toDoubleOrNull()
                val token = amtMatcher.group(0) ?: ""
                val item = workingInput
                    .replace(Regex("(?i)^(want|hold|wishlist|cool\\s*off)"), "")
                    .replace(token, "")
                    .trim()
                if (amt != null && amt > 0 && item.isNotBlank()) {
                    return ParsedIntent.StageDesire(item, amt)
                }
            }
        }

        // 4. Triangular Peer Debt Resolution
        if (lower.startsWith("settle ") || lower.contains("square off")) {
            val clean = workingInput.replace(Regex("(?i)^(settle|square\\s*off)"), "").trim()
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

        // 5. Break Goal Pot
        if (lower.contains("break") && (lower.contains("pot") || lower.contains("goal"))) {
            val goalPockets = activePockets.filter { it.pocketType == PocketType.SAVING_GOAL }
            val matchedGoal = goalPockets.firstOrNull { lower.contains(it.name.lowercase(Locale.ROOT)) }
            val liquidPockets = activePockets.filter { it.pocketType == PocketType.LIQUID }
            val targetLiquid = liquidPockets.firstOrNull { lower.contains(it.name.lowercase(Locale.ROOT)) } ?: liquidPockets.firstOrNull()
            if (matchedGoal != null) {
                return ParsedIntent.BreakGoalPot(matchedGoal.name, targetLiquid?.id)
            }
        }

        // 6. Goal Pot Creation: "set goal phone 40000 by nov", "target 60000 macbook in january"
        val isGoalCreation = (lower.contains("goal") || lower.contains("pot") || lower.contains("target")) &&
                (lower.contains("set") || lower.contains("create") || lower.contains("add") || lower.contains("target") || lower.contains("by") || lower.contains("aim")) &&
                !lower.contains("stash") && !lower.contains("from")
        if (isGoalCreation) {
            val amtMatcher = AMOUNT_PATTERN.matcher(workingInput)
            if (amtMatcher.find()) {
                val targetAmt = amtMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                val amtToken = amtMatcher.group(0) ?: ""
                val targetDateEpoch = parseMonthTargetEpoch(lower)
                val potName = workingInput
                    .replace(Regex("(?i)\\b(set|create|add|target|aim|goal|pot|by|in|for)\\b"), "")
                    .replace(amtToken, "")
                    .replace(Regex("(?i)\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|january|february|march|april|june|july|august|september|october|november|december)\\b"), "")
                    .trim()
                if (potName.isNotBlank() && targetAmt > 0.0) {
                    return ParsedIntent.CreateAccount(
                        name = potName,
                        type = PocketType.SAVING_GOAL,
                        targetAmount = targetAmt,
                        targetDateEpoch = targetDateEpoch
                    )
                }
            }
        }

        // 7. Account Creation
        if (lower.startsWith("create ") || lower.startsWith("add ") || lower.startsWith("open ") || lower.startsWith("new ")) {
            val stripped = workingInput.replace(Regex("(?i)^(create|add|open|new)\\s+"), "").trim()
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

        // 8. Compound Transactions: "spent 450 groceries and 120 uber from sbi"
        if (lower.contains(" and ") && (lower.contains("spent") || lower.contains("paid"))) {
            val subClauses = workingInput.split(Regex("(?i)\\band\\b"))
            val list = mutableListOf<ParsedIntent.Transaction>()
            for (clause in subClauses) {
                val subParsed = parseSingleTransaction(clause.trim(), activePockets)
                if (subParsed != null) list.add(subParsed)
            }
            if (list.size > 1) return ParsedIntent.CompoundTransactions(list)
        }

        // 9. Single Transaction Parsing
        val single = parseSingleTransaction(workingInput, activePockets)
        if (single != null) return single

        return null
    }

    private fun parseSingleTransaction(input: String, activePockets: List<VaultPocket>): ParsedIntent.Transaction? {
        val lower = input.lowercase(Locale.ROOT)
        val matcher = AMOUNT_PATTERN.matcher(input)
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

        val cleanPrompt = input.replace(amountToken, " ").replace(Regex("\\s+"), " ").trim()
        val cleanLower = cleanPrompt.lowercase(Locale.ROOT)

        val isCollect = cleanLower.contains("collect") || cleanLower.contains("collected") || cleanLower.contains("got back")
        val isSalary = cleanLower.contains("salary") || cleanLower.contains("stipend")
        val isLend = (cleanLower.contains("lent") || cleanLower.contains("lend") || cleanLower.contains("give") || cleanLower.contains("gave")) && !isCollect
        val isBorrow = cleanLower.contains("borrow") || cleanLower.contains("borrowed")
        val isRepay = cleanLower.contains("repay") || cleanLower.contains("repaid")
        val isCardPay = (cleanLower.contains("card") || cleanLower.contains("bill") || cleanLower.contains("dues")) && (cleanLower.contains("pay") || cleanLower.contains("clear"))
        val isTransfer = cleanLower.contains("transfer") || cleanLower.contains("move") || cleanLower.contains("stash") || cleanLower.contains("to pot") || cleanLower.contains("save")

        val nature = when {
            isCardPay -> MovementNature.CARD_PAYMENT
            isCollect -> MovementNature.PEER_COLLECT
            isRepay -> MovementNature.PEER_REPAY
            isLend -> MovementNature.PEER_LEND
            isBorrow -> MovementNature.PEER_BORROW
            isSalary || cleanLower.startsWith("got") || cleanLower.startsWith("received") || cleanLower.contains("credited") -> MovementNature.INFLOW
            isTransfer -> MovementNature.TRANSFER
            else -> MovementNature.OUTFLOW
        }

        val liquidPockets = activePockets.filter { it.pocketType == PocketType.LIQUID }
        val peerPockets = activePockets.filter { it.pocketType == PocketType.COUNTERPARTY || it.pocketType == PocketType.PEER }
        val goalPockets = activePockets.filter { it.pocketType == PocketType.SAVING_GOAL }
        val cardPockets = activePockets.filter { it.pocketType == PocketType.CREDIT_LINE || it.pocketType == PocketType.CREDIT }

        var matchedPocketId: Long? = null
        var targetPocketId: Long? = null
        var targetPersonName: String? = null

        // Detect Source & Target Accounts
        for (pocket in liquidPockets + cardPockets) {
            if (cleanLower.contains(pocket.name.lowercase(Locale.ROOT))) {
                matchedPocketId = pocket.id
                break
            }
        }
        if (matchedPocketId == null) {
            matchedPocketId = liquidPockets.firstOrNull()?.id
        }

        if (nature == MovementNature.CARD_PAYMENT) {
            val card = cardPockets.firstOrNull { cleanLower.contains(it.name.lowercase(Locale.ROOT)) } ?: cardPockets.firstOrNull()
            targetPocketId = card?.id
        } else if (nature == MovementNature.TRANSFER) {
            val goal = goalPockets.firstOrNull { cleanLower.contains(it.name.lowercase(Locale.ROOT)) }
            if (goal != null) {
                targetPocketId = goal.id
            } else {
                val secondLiquid = liquidPockets.firstOrNull { it.id != matchedPocketId && cleanLower.contains(it.name.lowercase(Locale.ROOT)) }
                targetPocketId = secondLiquid?.id
            }
        }

        if (nature in listOf(MovementNature.PEER_LEND, MovementNature.PEER_BORROW, MovementNature.PEER_COLLECT, MovementNature.PEER_REPAY)) {
            val peerKeywords = listOf("to", "from", "with", "by")
            val tokens = cleanPrompt.split(Regex("\\s+"))
            for (i in 0 until tokens.size - 1) {
                if (tokens[i].lowercase(Locale.ROOT) in peerKeywords) {
                    val candidate = tokens[i + 1].replace(Regex("[^a-zA-Z0-9]"), "")
                    if (candidate.length > 1 && !candidate.equals("bank", ignoreCase = true) && !candidate.equals("cash", ignoreCase = true)) {
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

        val category = when {
            isSalary -> "Salary"
            nature == MovementNature.CARD_PAYMENT -> "Bill Payment"
            nature == MovementNature.TRANSFER && targetPocketId in goalPockets.map { it.id } -> "Savings Pot"
            else -> inferCategory(cleanLower)
        }

        val note = cleanPrompt
            .replace(Regex("(?i)^(spent|paid|bought|got|received|lent|lend|borrowed|borrow|transferred|transfer|stash|collected|collect|repaid|repay|salary|auto)"), "")
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
            text.contains("pot") || text.contains("goal") || text.contains("stash") -> "Savings Pot"
            text.contains("lend") || text.contains("lent") || text.contains("borrow") || text.contains("collect") || text.contains("repay") -> "Peer Transfer"
            else -> "General"
        }
    }

    private fun parseMonthTargetEpoch(text: String): Long {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val months = listOf("jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3, "may" to 4, "jun" to 5, "jul" to 6, "aug" to 7, "sep" to 8, "oct" to 9, "nov" to 10, "dec" to 11)
        for ((mStr, mIdx) in months) {
            if (text.contains(mStr)) {
                cal.set(Calendar.MONTH, mIdx)
                cal.set(Calendar.DAY_OF_MONTH, 28)
                if (cal.timeInMillis < System.currentTimeMillis()) {
                    cal.set(Calendar.YEAR, currentYear + 1)
                }
                return cal.timeInMillis
            }
        }
        cal.add(Calendar.MONTH, 3)
        return cal.timeInMillis
    }
}
