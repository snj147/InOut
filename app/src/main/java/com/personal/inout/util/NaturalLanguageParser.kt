package com.personal.inout.util

import android.content.Context
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
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
    data class MissingAccountError(val missingAccountName: String) : ParsedIntent()
    data class PeerNotFoundError(val peerName: String, val action: String) : ParsedIntent()
}

object NaturalLanguageParser {

    private val REPAY_KEYWORDS = setOf("repay", "repaid", "paidback", "payback", "settle", "cleared", "clear")
    private val COLLECT_KEYWORDS = setOf("collect", "collected", "col", "rec", "received", "got", "recovered")
    private val LEND_KEYWORDS = setOf("lent", "lend", "gave", "give", "lended")
    private val BORROW_KEYWORDS = setOf("borrowed", "borrow", "bor", "took")
    private val TRANSFER_KEYWORDS = setOf("transfer", "trf", "xfer", "tfr", "move", "shift")

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

        // 2. Direct Account Creation Command
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

        // 3. Stage Desires
        val stageMatcher = Pattern.compile("^stage\\s+(.+?)\\s+(\\d+(?:\\.\\d+)?)$", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        if (stageMatcher.find()) {
            val desireName = stageMatcher.group(1)?.trim() ?: "Item"
            val amt = stageMatcher.group(2)?.toDoubleOrNull() ?: 0.0
            return ParsedIntent.StageDesire(desireName, amt)
        }

        // 4. Token Normalization & Amount Extraction (supports 4000, 4k, ₹4,000)
        val rawTokens = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        var amount: Double? = null
        var amountTokenIdx = -1

        for (i in rawTokens.indices) {
            val clean = rawTokens[i].replace("₹", "").replace(",", "")
            if (clean.endsWith("k", ignoreCase = true)) {
                val numPart = clean.dropLast(1).toDoubleOrNull()
                if (numPart != null && numPart > 0) {
                    amount = numPart * 1000.0
                    amountTokenIdx = i
                    break
                }
            } else {
                val numPart = clean.toDoubleOrNull()
                if (numPart != null && numPart > 0) {
                    amount = numPart
                    amountTokenIdx = i
                    break
                }
            }
        }

        if (amount == null || amountTokenIdx == -1) return null

        val tokens = rawTokens.filterIndexed { index, _ -> index != amountTokenIdx }
        val firstWord = rawTokens[0].lowercase()

        // 5. Transfer Intent (Entity Linked)
        if (firstWord in TRANSFER_KEYWORDS || tokens.any { it.lowercase() in TRANSFER_KEYWORDS }) {
            val fromIdx = rawTokens.indexOfFirst { it.equals("from", ignoreCase = true) }
            val toIdx = rawTokens.indexOfFirst { it.equals("to", ignoreCase = true) }

            if (fromIdx != -1 && toIdx != -1) {
                val (srcSegment, tgtSegment) = if (fromIdx < toIdx) {
                    val s = rawTokens.subList(fromIdx + 1, toIdx).joinToString(" ").trim()
                    val t = rawTokens.subList(toIdx + 1, rawTokens.size).filterIndexed { idx, _ -> (toIdx + 1 + idx) != amountTokenIdx }.joinToString(" ").trim()
                    s to t
                } else {
                    val t = rawTokens.subList(toIdx + 1, fromIdx).joinToString(" ").trim()
                    val s = rawTokens.subList(fromIdx + 1, rawTokens.size).filterIndexed { idx, _ -> (fromIdx + 1 + idx) != amountTokenIdx }.joinToString(" ").trim()
                    s to t
                }

                val srcPocket = rawPockets.firstOrNull { it.name.equals(srcSegment, ignoreCase = true) }
                val tgtPocket = rawPockets.firstOrNull { it.name.equals(tgtSegment, ignoreCase = true) }

                if (srcPocket == null) return ParsedIntent.MissingAccountError(srcSegment)
                if (tgtPocket == null) return ParsedIntent.MissingAccountError(tgtSegment)

                return ParsedIntent.Transaction(
                    amount = amount,
                    nature = MovementNature.TRANSFER,
                    matchedPocketId = srcPocket.id,
                    targetPocketId = tgtPocket.id,
                    category = "Account Transfer",
                    merchant = "Transfer ${srcPocket.name} → ${tgtPocket.name}",
                    timestamp = System.currentTimeMillis(),
                    isRecurring = false,
                    frequency = "NONE",
                    targetPersonName = null
                )
            }
        }

        // 6. Peer Intent Disambiguation (Slot-Filling)
        val hasRepayWord = tokens.any { it.lowercase() in REPAY_KEYWORDS }
        val hasCollectWord = tokens.any { it.lowercase() in COLLECT_KEYWORDS }
        val hasLendWord = tokens.any { it.lowercase() in LEND_KEYWORDS }
        val hasBorrowWord = tokens.any { it.lowercase() in BORROW_KEYWORDS }

        if (hasRepayWord || hasCollectWord || hasLendWord || hasBorrowWord) {
            val actionTokens = REPAY_KEYWORDS + COLLECT_KEYWORDS + LEND_KEYWORDS + BORROW_KEYWORDS

            val filteredTokens = tokens.filter {
                it.lowercase() !in actionTokens &&
                !it.equals("to", ignoreCase = true) &&
                !it.equals("from", ignoreCase = true) &&
                !it.equals("via", ignoreCase = true) &&
                !it.equals("with", ignoreCase = true) &&
                !it.equals("in", ignoreCase = true)
            }

            // Identify explicit account in command (e.g., SBI, HDFC)
            val matchedAccount = rawPockets.firstOrNull { p ->
                p.pocketType != PocketType.COUNTERPARTY && filteredTokens.any { it.equals(p.name, ignoreCase = true) }
            }

            // Identify peer candidate tokens
            val peerCandidate = filteredTokens.filter {
                matchedAccount == null || !it.equals(matchedAccount.name, ignoreCase = true)
            }.joinToString(" ").trim()

            val existingPeer = rawPockets.firstOrNull {
                it.pocketType == PocketType.COUNTERPARTY && it.name.equals(peerCandidate, ignoreCase = true)
            }

            if (hasRepayWord || hasCollectWord) {
                if (existingPeer == null) {
                    val actionName = if (hasRepayWord) "repay" else "collect from"
                    return ParsedIntent.PeerNotFoundError(peerCandidate.ifBlank { "unknown" }, actionName)
                }

                val nature = if (hasRepayWord) MovementNature.PEER_REPAY else MovementNature.PEER_COLLECT
                return ParsedIntent.Transaction(
                    amount = amount,
                    nature = nature,
                    matchedPocketId = matchedAccount?.id,
                    targetPocketId = existingPeer.id,
                    category = if (hasRepayWord) "Peer Settlement" else "Peer Collection",
                    merchant = if (hasRepayWord) "Repaid to ${existingPeer.name}" else "Collected from ${existingPeer.name}",
                    timestamp = System.currentTimeMillis(),
                    isRecurring = false,
                    frequency = "NONE",
                    targetPersonName = existingPeer.name
                )
            }

            // Lending or Borrowing
            val nature = if (hasLendWord) MovementNature.PEER_LEND else MovementNature.PEER_BORROW
            val effectivePeerName = existingPeer?.name ?: peerCandidate.ifBlank { "Contact" }

            return ParsedIntent.Transaction(
                amount = amount,
                nature = nature,
                matchedPocketId = matchedAccount?.id,
                targetPocketId = existingPeer?.id,
                category = if (hasLendWord) "Peer Transfer" else "Peer Borrowing",
                merchant = if (hasLendWord) "Lent to $effectivePeerName" else "Borrowed from $effectivePeerName",
                timestamp = System.currentTimeMillis(),
                isRecurring = false,
                frequency = "NONE",
                targetPersonName = effectivePeerName
            )
        }

        // 7. General Transactions & Recurring Cadence
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

        var matchedPocket: VaultPocket? = null
        for (pocket in rawPockets) {
            if (tokens.any { it.equals(pocket.name, ignoreCase = true) }) {
                matchedPocket = pocket
                break
            }
        }

        val isInflow = firstWord in listOf("salary", "income", "deposit", "credit", "cashback")
        val nature = if (isInflow) MovementNature.INFLOW else MovementNature.OUTFLOW

        val descTokens = tokens.filter { token ->
            matchedPocket == null || !token.equals(matchedPocket.name, ignoreCase = true)
        }.filter { token ->
            !token.equals("from", ignoreCase = true) &&
            !token.equals("to", ignoreCase = true) &&
            !token.equals("in", ignoreCase = true) &&
            !token.equals("into", ignoreCase = true) &&
            !token.equals("monthly", ignoreCase = true) &&
            !token.equals("weekly", ignoreCase = true) &&
            !token.equals("daily", ignoreCase = true) &&
            !token.equals("every", ignoreCase = true) &&
            !token.equals("month", ignoreCase = true) &&
            !token.equals("week", ignoreCase = true) &&
            !token.equals("day", ignoreCase = true)
        }

        val cleanedDesc = descTokens.joinToString(" ").trim().ifBlank {
            if (isInflow) "Income" else "General Expense"
        }

        val category = when {
            cleanedDesc.contains("coffee", true) || cleanedDesc.contains("food", true) || cleanedDesc.contains("dinner", true) -> "Food & Dining"
            cleanedDesc.contains("shoe", true) || cleanedDesc.contains("cloth", true) || cleanedDesc.contains("shopping", true) -> "Shopping"
            cleanedDesc.contains("rent", true) || cleanedDesc.contains("bill", true) || cleanedDesc.contains("sip", true) -> "Bills"
            cleanedDesc.contains("salary", true) -> "Salary"
            else -> if (isInflow) "Salary" else "General"
        }

        return ParsedIntent.Transaction(
            amount = amount,
            nature = nature,
            matchedPocketId = if (isInflow) null else matchedPocket?.id,
            targetPocketId = if (isInflow) matchedPocket?.id else null,
            category = category,
            merchant = cleanedDesc,
            timestamp = System.currentTimeMillis(),
            isRecurring = isRecurring,
            frequency = cadence,
            targetPersonName = null
        )
    }
}
