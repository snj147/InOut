package com.personal.inout.util

import com.personal.inout.data.CadenceType
import com.personal.inout.data.MovementNature
import com.personal.inout.data.VaultPocket
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

sealed class ParsedQuickCommand {
    data class CreatePocketCommand(
        val name: String,
        val type: String,
        val subType: String? = null
    ) : ParsedQuickCommand()

    data class TransactionCommand(
        val nature: MovementNature,
        val amount: Long,
        val note: String,
        val sourcePocketName: String?,
        val targetPocketName: String?,
        val cadence: CadenceType,
        val targetTimestamp: Long
    ) : ParsedQuickCommand()

    data class InvalidCommand(val reason: String) : ParsedQuickCommand()
}

class FuzzyCommandParser(private val activePocketsProvider: () -> List<VaultPocket>) {

    fun parse(rawInput: String): ParsedQuickCommand {
        val text = rawInput.trim()
        if (text.isBlank()) return ParsedQuickCommand.InvalidCommand("Empty input")

        val tokens = text.split("\\s+".toRegex())
        val firstToken = tokens.first().lowercase()

        // 1. Account / Pocket Creation Intent
        if (isFuzzyMatch(firstToken, listOf("create", "add", "new", "make", "setup"), threshold = 2)) {
            return parsePocketCreation(tokens)
        }

        // 2. Transaction / Ledger / Recurring Intent
        return parseTransactionCommand(tokens, text)
    }

    private fun parsePocketCreation(tokens: List<String>): ParsedQuickCommand {
        if (tokens.size < 3) {
            return ParsedQuickCommand.InvalidCommand("Format: create <bank|card|person> <name>")
        }
        val typeToken = tokens[1].lowercase()
        val pocketName = tokens.drop(2).joinToString(" ")

        val (type, subType) = when {
            isFuzzyMatch(typeToken, listOf("bank", "account", "liquid"), threshold = 1) -> "LIQUID" to null
            isFuzzyMatch(typeToken, listOf("card", "credit", "cc"), threshold = 1) -> "CREDIT" to null
            isFuzzyMatch(typeToken, listOf("person", "friend", "peer"), threshold = 1) -> "PEER" to "PEER"
            else -> "LIQUID" to null
        }

        return ParsedQuickCommand.CreatePocketCommand(name = pocketName, type = type, subType = subType)
    }

    private fun parseTransactionCommand(tokens: List<String>, fullText: String): ParsedQuickCommand {
        val amount = extractAmount(fullText) ?: return ParsedQuickCommand.InvalidCommand("No amount detected")
        val lowerText = fullText.lowercase()

        val firstWord = tokens.first().lowercase()
        val nature = when {
            isFuzzyMatch(firstWord, listOf("borrowed", "borrow"), 2) -> MovementNature.PEER_BORROW
            isFuzzyMatch(firstWord, listOf("lent", "lend", "loaned"), 1) -> MovementNature.PEER_LEND
            isFuzzyMatch(firstWord, listOf("got", "received", "collected"), 2) -> MovementNature.PEER_COLLECT
            isFuzzyMatch(firstWord, listOf("repaid", "settled", "paidback"), 2) -> MovementNature.PEER_REPAY
            isFuzzyMatch(firstWord, listOf("transferred", "transfer", "moved"), 2) -> MovementNature.TRANSFER
            isFuzzyMatch(firstWord, listOf("salary", "earned", "deposit"), 2) -> MovementNature.INFLOW
            else -> MovementNature.OUTFLOW
        }

        val cadence = when {
            lowerText.contains("daily") || lowerText.contains("every day") -> CadenceType.DAILY
            lowerText.contains("weekly") || lowerText.contains("every week") -> CadenceType.WEEKLY
            lowerText.contains("monthly") || lowerText.contains("every month") -> CadenceType.MONTHLY
            else -> CadenceType.NONE
        }

        val targetTimestamp = parseStartingDate(lowerText)
        val activePockets = activePocketsProvider()
        val matchedPockets = findReferencedPockets(tokens, activePockets)

        var sourceName: String? = null
        var targetName: String? = null

        when (nature) {
            MovementNature.TRANSFER -> {
                if (matchedPockets.size >= 2) {
                    sourceName = matchedPockets[0].pocketName
                    targetName = matchedPockets[1].pocketName
                }
            }
            MovementNature.INFLOW, MovementNature.PEER_BORROW, MovementNature.PEER_COLLECT -> {
                targetName = matchedPockets.firstOrNull()?.pocketName
            }
            MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.PEER_REPAY, MovementNature.CARD_PAYMENT -> {
                sourceName = matchedPockets.firstOrNull()?.pocketName
            }
        }

        val cleanNote = extractCleanNote(fullText, nature)

        return ParsedQuickCommand.TransactionCommand(
            nature = nature,
            amount = amount,
            note = cleanNote,
            sourcePocketName = sourceName,
            targetPocketName = targetName,
            cadence = cadence,
            targetTimestamp = targetTimestamp
        )
    }

    private fun extractAmount(text: String): Long? {
        val regex = Regex("""(?i)(?:rs\.?|inr|₹)?\s*(\d+(?:,\d+)*(?:\.\d+)?)""")
        return regex.find(text)?.groups?.get(1)?.value?.replace(",", "")?.toDoubleOrNull()?.toLong()
    }

    private fun parseStartingDate(lowerText: String): Long {
        val now = Calendar.getInstance()
        if (lowerText.contains("tomorrow")) {
            now.add(Calendar.DAY_OF_YEAR, 1)
            return now.timeInMillis
        }

        val dateRegex = Regex("""(?:starting|from|on)?\s*(\d{1,2})[-/\s]([a-zA-Z]{3,9})""")
        val match = dateRegex.find(lowerText)
        if (match != null) {
            val day = match.groupValues[1].toIntOrNull() ?: 1
            val monthStr = match.groupValues[2]
            val sdf = SimpleDateFormat("MMM", Locale.ENGLISH)
            return try {
                val parsedCal = Calendar.getInstance()
                val monthDate = sdf.parse(monthStr)
                if (monthDate != null) {
                    val calTemp = Calendar.getInstance().apply { time = monthDate }
                    parsedCal.set(Calendar.MONTH, calTemp.get(Calendar.MONTH))
                    parsedCal.set(Calendar.DAY_OF_MONTH, day)
                    if (parsedCal.before(now)) {
                        parsedCal.add(Calendar.YEAR, 1)
                    }
                    parsedCal.timeInMillis
                } else now.timeInMillis
            } catch (_: Exception) {
                now.timeInMillis
            }
        }

        return now.timeInMillis
    }

    private fun findReferencedPockets(tokens: List<String>, available: List<VaultPocket>): List<VaultPocket> {
        val matches = mutableListOf<VaultPocket>()
        for (pocket in available) {
            val pName = pocket.pocketName.lowercase()
            for (token in tokens) {
                val t = token.lowercase().replace(Regex("[^a-zA-Z0-9]"), "")
                if (t.length >= 2 && (pName.contains(t) || isFuzzyMatch(t, listOf(pName), threshold = 2))) {
                    if (!matches.contains(pocket)) {
                        matches.add(pocket)
                    }
                }
            }
        }
        return matches
    }

    private fun extractCleanNote(text: String, nature: MovementNature): String {
        var clean = text
        val noiseWords = listOf(
            "spent", "spend", "paid", "pay", "bought", "transferred", "transfer",
            "borrowed", "lent", "received", "got", "daily", "weekly", "monthly",
            "starting", "today", "tomorrow"
        )
        for (w in noiseWords) {
            clean = clean.replace(Regex("(?i)\\b$w\\b"), "")
        }
        clean = clean.replace(Regex("""(?i)(?:rs\.?|inr|₹)?\s*(\d+(?:,\d+)*(?:\.\d+)?)"""), "")
        clean = clean.replace(Regex("""(?i)\b(on|for|in|from|to|using|via)\b"""), " ")
        clean = clean.replace("\\s+".toRegex(), " ").trim()
        return clean.ifBlank { nature.name.replace("_", " ") }
    }

    private fun isFuzzyMatch(word: String, candidates: List<String>, threshold: Int): Boolean {
        return candidates.any { candidate ->
            if (word == candidate) true
            else levenshtein(word, candidate) <= threshold
        }
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        var prev = IntArray(rhs.length + 1) { it }
        var curr = IntArray(rhs.length + 1)
        for (i in lhs.indices) {
            curr[0] = i + 1
            for (j in rhs.indices) {
                val cost = if (lhs[i] == rhs[j]) 0 else 1
                curr[j + 1] = min(min(curr[j] + 1, prev[j + 1] + 1), prev[j] + cost)
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[rhs.length]
    }
}
