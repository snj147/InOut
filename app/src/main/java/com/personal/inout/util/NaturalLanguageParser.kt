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

    data class CreateAccount(
        val name: String,
        val type: PocketType
    ) : ParsedIntent()

    data class SetDailyBurn(val newRate: Double) : ParsedIntent()
    data class StageDesire(val name: String, val amount: Double) : ParsedIntent()
    data class TriangularSettle(val debtor: String, val creditor: String, val amount: Double) : ParsedIntent()
    data class BreakGoalPot(val potName: String, val destinationPocketId: Long?) : ParsedIntent()
    data class MissingAccountError(val missingAccountName: String) : ParsedIntent()
    data class PeerNotFoundError(val peerName: String, val action: String) : ParsedIntent()
}

object NaturalLanguageParser {

    private val CREATION_VERBS = setOf("create", "new", "add", "open", "make", "register")
    private val LIQUID_SYNONYMS = setOf("bank", "account", "acc", "ledger", "cash", "wallet")
    private val PEER_SYNONYMS = setOf("borrower", "lender", "person", "peer", "contact", "friend", "party", "client")
    private val CARD_SYNONYMS = setOf("card", "cc", "creditcard")
    private val GOAL_SYNONYMS = setOf("goal", "pot", "saving", "savings", "piggy")

    private val REPAY_KEYWORDS = setOf("repay", "repaid", "paidback", "payback", "settle", "cleared", "clear")
    private val COLLECT_KEYWORDS = setOf("collect", "collected", "col", "rec", "received", "got", "recovered")
    private val LEND_KEYWORDS = setOf("lent", "lend", "gave", "give", "lended")
    private val BORROW_KEYWORDS = setOf("borrowed", "borrow", "bor", "took")
    private val TRANSFER_KEYWORDS = setOf("transfer", "trf", "xfer", "tfr", "move", "shift")

    fun parse(input: String, rawPockets: List<VaultPocket>, context: Context): ParsedIntent? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        // 1. Burn Ceiling Command
        val burnMatcher = Pattern.compile("^(?:set\\s+)?burn(?:\\s+target|\\s+ceiling)?\\s+(\\d+(?:\\.\\d+)?)$", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        if (burnMatcher.find()) {
            val amt = burnMatcher.group(1)?.toDoubleOrNull() ?: return null
            return ParsedIntent.SetDailyBurn(amt)
        }

        // 2. Comprehensive Entity/Account Creation Parser (e.g., "Create bank Jupiter", "new borrower ABC")
        val createPattern = Pattern.compile("^(?:create|new|add|open|make|register)\\s+([a-zA-Z]+)(?:\\s+card)?\\s+(.+)$", Pattern.CASE_INSENSITIVE)
        val createMatcher = createPattern.matcher(trimmed)
        if (createMatcher.find()) {
            val typeToken = createMatcher.group(1)?.lowercase() ?: ""
            val entityName = createMatcher.group(2)?.trim() ?: ""

            val pocketType = when {
                typeToken in LIQUID_SYNONYMS -> PocketType.LIQUID
                typeToken in PEER_SYNONYMS -> PocketType.COUNTERPARTY
                typeToken in CARD_SYNONYMS || typeToken == "credit" -> PocketType.CREDIT_LINE
                typeToken in GOAL_SYNONYMS -> PocketType.SAVING_GOAL
                else -> null
            }

            if (pocketType != null && entityName.isNotBlank()) {
                return ParsedIntent.CreateAccount(entityName, pocketType)
            }
        }

        // 3. Stage Desires
        val stageMatcher = Pattern.compile("^stage\\s+(.+?)\\s+(\\d+(?:\\.\\d+)?)$", Pattern.CASE_INSENSITIVE).matcher(trimmed)
        if (stageMatcher.find()) {
            val desireName = stageMatcher.group(1)?.trim() ?: "Item"
            val amt = stageMatcher.group(2)?.toDoubleOrNull() ?: 0.0
            return ParsedIntent.StageDesire(desireName, amt)
        }

        // 4. Temporal & Recurrence Slot Harvesting (Absorbs dates completely so they never pollute notes)
        var workingText = trimmed
        var targetTimestamp = System.currentTimeMillis()
        var isRecurring = false
        var cadence = "NONE"

        // Check for Recurrence Cadence
        val lowerText = workingText.lowercase()
        when {
            lowerText.contains("every month") || lowerText.contains("monthly") -> {
                isRecurring = true
                cadence = "MONTHLY"
                workingText = workingText.replace("(?i)every\\s+month".toRegex(), "").replace("(?i)monthly".toRegex(), "")
            }
            lowerText.contains("every week") || lowerText.contains("weekly") -> {
                isRecurring = true
                cadence = "WEEKLY"
                workingText = workingText.replace("(?i)every\\s+week".toRegex(), "").replace("(?i)weekly".toRegex(), "")
            }
            lowerText.contains("every day") || lowerText.contains("daily") -> {
                isRecurring = true
                cadence = "DAILY"
                workingText = workingText.replace("(?i)every\\s+day".toRegex(), "").replace("(?i)daily".toRegex(), "")
            }
        }

        // Extract Explicit Dates: "start DD/MM/YYYY", "from DD-MM-YYYY", or "DD/MM/YYYY"
        val fullDateRegex = Pattern.compile("(?:(?:start|starting|from|on)\\s+)?(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})", Pattern.CASE_INSENSITIVE)
        val fullDateMatcher = fullDateRegex.matcher(workingText)
        if (fullDateMatcher.find()) {
            val dateStr = fullDateMatcher.group(1)
            val parsedTime = parseDateString(dateStr)
            if (parsedTime != null) {
                targetTimestamp = parsedTime
                workingText = fullDateMatcher.replaceFirst("").trim()
            }
        } else {
            // Extract Day of Month Dates: "from 5th", "on 10th", "start 1st", "on the 25th"
            val dayRegex = Pattern.compile("(?:(?:start|starting|from|on|on\\s+the)\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\b", Pattern.CASE_INSENSITIVE)
            val dayMatcher = dayRegex.matcher(workingText)
            if (dayMatcher.find()) {
                val dayNum = dayMatcher.group(1)?.toIntOrNull()
                if (dayNum != null && dayNum in 1..31) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, dayNum)
                        if (timeInMillis < System.currentTimeMillis()) {
                            add(Calendar.MONTH, 1)
                        }
                    }
                    targetTimestamp = cal.timeInMillis
                    workingText = dayMatcher.replaceFirst("").trim()
                }
            }
        }

        // 5. Amount Harvesting (4000, 4k, ₹4,000)
        val rawTokens = workingText.split("\\s+".toRegex()).filter { it.isNotBlank() }
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

        // 6. Transfer Intent (Entity Linked)
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
                    timestamp = targetTimestamp,
                    isRecurring = false,
                    frequency = "NONE",
                    targetPersonName = null
                )
            }
        }

        // 7. Peer Intent Disambiguation (Slot-Filling)
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

            val matchedAccount = rawPockets.firstOrNull { p ->
                p.pocketType != PocketType.COUNTERPARTY && filteredTokens.any { it.equals(p.name, ignoreCase = true) }
            }

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
                    timestamp = targetTimestamp,
                    isRecurring = false,
                    frequency = "NONE",
                    targetPersonName = existingPeer.name
                )
            }

            val nature = if (hasLendWord) MovementNature.PEER_LEND else MovementNature.PEER_BORROW
            val effectivePeerName = existingPeer?.name ?: peerCandidate.ifBlank { "Contact" }

            return ParsedIntent.Transaction(
                amount = amount,
                nature = nature,
                matchedPocketId = matchedAccount?.id,
                targetPocketId = existingPeer?.id,
                category = if (hasLendWord) "Peer Transfer" else "Peer Borrowing",
                merchant = if (hasLendWord) "Lent to $effectivePeerName" else "Borrowed from $effectivePeerName",
                timestamp = targetTimestamp,
                isRecurring = false,
                frequency = "NONE",
                targetPersonName = effectivePeerName
            )
        }

        // 8. General Transactions & Inflow/Outflow Disambiguation
        var matchedPocket: VaultPocket? = null
        for (pocket in rawPockets) {
            if (tokens.any { it.equals(pocket.name, ignoreCase = true) }) {
                matchedPocket = pocket
                break
            }
        }

        val isInflow = firstWord in listOf("salary", "income", "deposit", "credit", "cashback")
        val nature = if (isInflow) MovementNature.INFLOW else MovementNature.OUTFLOW

        // Clean tokens of prepositions and entities to preserve pure description
        val descTokens = tokens.filter { token ->
            matchedPocket == null || !token.equals(matchedPocket.name, ignoreCase = true)
        }.filter { token ->
            !token.equals("from", ignoreCase = true) &&
            !token.equals("to", ignoreCase = true) &&
            !token.equals("in", ignoreCase = true) &&
            !token.equals("into", ignoreCase = true) &&
            !token.equals("start", ignoreCase = true) &&
            !token.equals("starting", ignoreCase = true) &&
            !token.equals("on", ignoreCase = true)
        }

        val cleanedDesc = descTokens.joinToString(" ").trim().ifBlank {
            if (isInflow) "Salary" else "Expense"
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
            timestamp = targetTimestamp,
            isRecurring = isRecurring,
            frequency = cadence,
            targetPersonName = null
        )
    }

    private fun parseDateString(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        val delimiters = listOf("dd/MM/yyyy", "dd-MM-yyyy")
        for (format in delimiters) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault()).apply { isLenient = false }
                val parsed = sdf.parse(str.trim())
                if (parsed != null) return parsed.time
            } catch (ignored: Exception) {}
        }
        return null
    }
}
