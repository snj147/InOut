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
        val targetPersonName: String? = null,
        val category: String,
        val merchant: String,
        val timestamp: Long,
        val isRecurring: Boolean = false,
        val frequency: String = "NONE"
    ) : ParsedIntent()

    data class CreateAccount(
        val name: String,
        val type: PocketType
    ) : ParsedIntent()

    data class SetDailyBurn(
        val newRate: Double
    ) : ParsedIntent()

    data class StageDesire(
        val name: String,
        val amount: Double
    ) : ParsedIntent()

    data class TriangularSettle(
        val debtor: String,
        val creditor: String,
        val amount: Double
    ) : ParsedIntent()

    data class BreakGoalPot(
        val potName: String,
        val destinationPocketId: Long?
    ) : ParsedIntent()

    data class MissingAccountError(
        val missingAccountName: String
    ) : ParsedIntent()

    data class PeerNotFoundError(
        val action: String,
        val peerName: String
    ) : ParsedIntent()
}

object NaturalLanguageParser {

    fun parse(rawText: String, activePockets: List<VaultPocket>, context: Context? = null): ParsedIntent? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null
        val lower = trimmed.lowercase()

        // 1. Account Creation Commands
        if (lower.startsWith("new ") || lower.startsWith("create ") || lower.startsWith("add ")) {
            val tokens = trimmed.split("\\s+".toRegex())
            if (tokens.size >= 3) {
                val kind = tokens[1].lowercase()
                val name = tokens.drop(2).joinToString(" ").replace("limit.*".toRegex(), "").trim()
                val type = when (kind) {
                    "bank", "account", "savings" -> PocketType.LIQUID
                    "card", "credit" -> PocketType.CREDIT_LINE
                    "goal", "pot" -> PocketType.SAVING_GOAL
                    "person", "borrower", "lender", "contact" -> PocketType.COUNTERPARTY
                    else -> PocketType.LIQUID
                }
                return ParsedIntent.CreateAccount(name = name, type = type)
            }
        }

        // 2. Daily Burn Settings
        val burnMatcher = Pattern.compile("""(?i)^(?:burn|daily\s*burn|set\s*burn)\s*(?:limit|target)?\s*[:=]?\s*[₹rs\.]*\s*(\d+(?:\.\d+)?)$""").matcher(trimmed)
        if (burnMatcher.find()) {
            val rate = burnMatcher.group(1)?.toDoubleOrNull() ?: 450.0
            return ParsedIntent.SetDailyBurn(rate)
        }

        // 3. Staged Desires / Impulse Quarantine
        val desireMatcher = Pattern.compile("""(?i)^(?:wish|desire|quarantine|cooloff|stage)\s+(.+?)\s+[₹rs\.]*\s*(\d+(?:\.\d+)?)$""").matcher(trimmed)
        if (desireMatcher.find()) {
            val name = desireMatcher.group(1)?.trim() ?: "Item"
            val amt = desireMatcher.group(2)?.toDoubleOrNull() ?: 0.0
            return ParsedIntent.StageDesire(name, amt)
        }

        // 4. Triangular Peer Settlement (e.g. "settle Alice to Bob 1500")
        val triMatcher = Pattern.compile("""(?i)^settle\s+(.+?)\s+(?:to|->)\s+(.+?)\s+[₹rs\.]*\s*(\d+(?:\.\d+)?)$""").matcher(trimmed)
        if (triMatcher.find()) {
            val debtor = triMatcher.group(1)?.trim() ?: ""
            val creditor = triMatcher.group(2)?.trim() ?: ""
            val amt = triMatcher.group(3)?.toDoubleOrNull() ?: 0.0
            return ParsedIntent.TriangularSettle(debtor, creditor, amt)
        }

        // 5. Pot Break / Return
        val breakMatcher = Pattern.compile("""(?i)^break\s+pot\s+(.+?)(?:\s+into\s+(.+))?$""").matcher(trimmed)
        if (breakMatcher.find()) {
            val potName = breakMatcher.group(1)?.trim() ?: ""
            val destName = breakMatcher.group(2)?.trim()
            val destPocket = if (destName != null) activePockets.firstOrNull { it.name.equals(destName, ignoreCase = true) } else activePockets.firstOrNull { it.pocketType == PocketType.LIQUID }
            return ParsedIntent.BreakGoalPot(potName, destPocket?.id)
        }

        // 6. Extraction for Financial Movements (Inflow, Outflow, Transfer, Card Payment, Peer)
        val amountPattern = Pattern.compile("""(?i)(?:^|\s)[₹rs\.]*(\d+(?:\.\d+)?)(?:k|l)?(?:\s|$)""")
        val numMatcher = amountPattern.matcher(trimmed)
        var parsedAmount: Double? = null

        val amountTokens = trimmed.split("\\s+".toRegex())
        for (tok in amountTokens) {
            val cleanTok = tok.replace("[₹rs\$,]".toRegex(), "").lowercase()
            if (cleanTok.endsWith("k") && cleanTok.dropLast(1).toDoubleOrNull() != null) {
                parsedAmount = cleanTok.dropLast(1).toDouble() * 1000.0
                break
            } else if (cleanTok.endsWith("l") && cleanTok.dropLast(1).toDoubleOrNull() != null) {
                parsedAmount = cleanTok.dropLast(1).toDouble() * 100000.0
                break
            } else if (cleanTok.toDoubleOrNull() != null && !cleanTok.contains("-") && cleanTok.toDouble() > 0) {
                parsedAmount = cleanTok.toDouble()
                break
            }
        }

        if (parsedAmount == null && numMatcher.find()) {
            parsedAmount = numMatcher.group(1)?.toDoubleOrNull()
        }
        if (parsedAmount == null) return null

        // Recurrence Extraction
        var isRecurring = false
        var frequency = "NONE"
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

        // Date Timing Extraction
        var transactionEpoch = System.currentTimeMillis()
        val dayMatcher = Pattern.compile("""(?i)(?:from|on|due)\s+(\d{1,2})(?:st|nd|rd|th)?""").matcher(trimmed)
        if (dayMatcher.find()) {
            val dayNum = dayMatcher.group(1)?.toIntOrNull()
            if (dayNum != null && dayNum in 1..31) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, dayNum)
                transactionEpoch = cal.timeInMillis
            }
        } else if (lower.contains("yesterday")) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            transactionEpoch = cal.timeInMillis
        }

        // Determine Nature and Parties
        var nature = MovementNature.OUTFLOW
        var category = "General"
        var targetPerson: String? = null
        var sourcePocket: VaultPocket? = null
        var targetPocket: VaultPocket? = null

        // Match accounts from user's active pockets
        for (pocket in activePockets) {
            val pattern = Pattern.compile("(?i)\\b${Pattern.quote(pocket.name)}\\b")
            if (pattern.matcher(trimmed).find()) {
                if (pocket.pocketType == PocketType.COUNTERPARTY) {
                    targetPerson = pocket.name
                } else if (sourcePocket == null) {
                    sourcePocket = pocket
                } else {
                    targetPocket = pocket
                }
            }
        }

        when {
            // Salary / Inflow Patterns
            lower.contains("salary") || lower.contains("received") || lower.contains("into") || lower.contains("credit into") || lower.contains("got paid") -> {
                nature = MovementNature.INFLOW
                category = if (lower.contains("salary")) "Salary" else "Income"
            }
            // Card Payment Patterns
            lower.contains("card bill") || lower.contains("clear card") || lower.contains("pay card") || lower.contains("pay cc") -> {
                nature = MovementNature.CARD_PAYMENT
                category = "Bill Payment"
                targetPocket = activePockets.firstOrNull { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE }
            }
            // Transfer Patterns
            lower.startsWith("trf") || lower.startsWith("transfer") || lower.contains(" transfer ") || (lower.contains("from ") && lower.contains("to ")) -> {
                nature = MovementNature.TRANSFER
                category = "Transfer"
            }
            // Lending to Peer
            lower.contains("lent ") || lower.contains("lend ") || lower.contains("gave ") -> {
                nature = MovementNature.PEER_LEND
                category = "Peer Transfer"
                val pMatcher = Pattern.compile("""(?i)(?:to|lent|gave)\s+([A-Za-z]+)""").matcher(trimmed)
                if (pMatcher.find() && targetPerson == null) {
                    targetPerson = pMatcher.group(1)?.replace("from", "")?.trim()
                }
            }
            // Borrowing from Peer
            lower.contains("borrow") || lower.contains("borrowed") -> {
                nature = MovementNature.PEER_BORROW
                category = "Peer Transfer"
                val pMatcher = Pattern.compile("""(?i)(?:from|borrowed)\s+([A-Za-z]+)""").matcher(trimmed)
                if (pMatcher.find() && targetPerson == null) {
                    targetPerson = pMatcher.group(1)?.trim()
                }
            }
            // Collecting Peer Dues
            lower.contains("collect") || lower.contains("collected") || lower.contains("received from") -> {
                nature = MovementNature.PEER_COLLECT
                category = "Peer Transfer"
            }
            // Repaying Peer Dues
            lower.contains("repay") || lower.contains("repaid") || lower.contains("returned to") -> {
                nature = MovementNature.PEER_REPAY
                category = "Peer Transfer"
            }
            // Goal Deposit
            lower.contains("save ") || lower.contains("pot ") || lower.contains("goal ") -> {
                val goal = activePockets.firstOrNull { it.pocketType == PocketType.SAVING_GOAL && lower.contains(it.name.lowercase()) }
                if (goal != null) {
                    nature = MovementNature.TRANSFER
                    targetPocket = goal
                    category = "Savings Pot"
                }
            }
            // Standard Outflow
            else -> {
                nature = MovementNature.OUTFLOW
                category = when {
                    lower.contains("coffee") || lower.contains("dinner") || lower.contains("lunch") || lower.contains("burger") || lower.contains("food") || lower.contains("tea") -> "Food & Dining"
                    lower.contains("groceries") || lower.contains("supermarket") || lower.contains("milk") || lower.contains("vegetables") -> "Groceries"
                    lower.contains("uber") || lower.contains("ola") || lower.contains("petrol") || lower.contains("fuel") || lower.contains("metro") -> "Transport"
                    lower.contains("rent") || lower.contains("wifi") || lower.contains("electricity") || lower.contains("bill") -> "Bills"
                    lower.contains("gym") || lower.contains("doctor") || lower.contains("medicine") -> "Health"
                    else -> "General"
                }
            }
        }

        // Clean Description / Merchant Note
        val cleanedNote = trimmed
            .replace("(?i)\\b(?:from|into|to|via|monthly|weekly|daily|today|yesterday|every|on|due|spend|spent|paid|received|repay|lent|borrowed|save|new|pot|goal)\\b".toRegex(), "")
            .replace("[₹rs\$,]".toRegex(), "")
            .replace("\\b\\d+(?:\\.\\d+)?(?:k|l)?\\b".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()

        val finalMerchant = cleanedNote.ifBlank { category }

        return ParsedIntent.Transaction(
            nature = nature,
            amount = parsedAmount,
            matchedPocketId = sourcePocket?.id,
            targetPocketId = targetPocket?.id,
            targetPersonName = targetPerson,
            category = category,
            merchant = finalMerchant,
            timestamp = transactionEpoch,
            isRecurring = isRecurring,
            frequency = frequency
        )
    }
}
