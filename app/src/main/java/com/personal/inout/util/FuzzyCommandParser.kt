package com.personal.inout.util

import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
import java.util.Locale
import java.util.regex.Pattern

data class ParsedVaultCommand(
    val nature: MovementNature,
    val amount: Double,
    val category: String,
    val merchant: String,
    val matchedPocketId: Long?,
    val targetPocketId: Long? = null,
    val targetPersonName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object FuzzyCommandParser {

    private val AMOUNT_PATTERN = Pattern.compile("(?:₹|rs\\.?|inr)?\\s*(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)

    fun parse(input: String, activePockets: List<VaultPocket>): ParsedVaultCommand? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val matcher = AMOUNT_PATTERN.matcher(trimmed)
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

        val cleanPrompt = trimmed.replace(amountToken, " ").replace(Regex("\\s+"), " ").trim()
        val cleanLower = cleanPrompt.toLowerCase(Locale.ROOT)

        val nature = when {
            cleanLower.startsWith("spent") || cleanLower.contains(" paid ") || cleanLower.startsWith("paid") || cleanLower.contains(" buy ") || cleanLower.contains(" bought ") -> MovementNature.OUTFLOW
            cleanLower.startsWith("lent") || cleanLower.contains(" lend ") || cleanLower.contains(" lent to ") -> MovementNature.PEER_LEND
            cleanLower.startsWith("borrowed") || cleanLower.contains(" borrow ") || cleanLower.contains(" borrowed from ") -> MovementNature.PEER_BORROW
            cleanLower.startsWith("got") || cleanLower.startsWith("received") || cleanLower.contains(" credited ") || cleanLower.contains(" salary ") -> MovementNature.INFLOW
            cleanLower.startsWith("collected") || cleanLower.contains(" collect ") -> MovementNature.PEER_COLLECT
            cleanLower.startsWith("repaid") || cleanLower.contains(" repay ") -> MovementNature.PEER_REPAY
            cleanLower.startsWith("transferred") || cleanLower.contains(" transfer ") || cleanLower.contains(" move ") -> MovementNature.TRANSFER
            else -> MovementNature.OUTFLOW
        }

        var matchedPocketId: Long? = null
        var targetPocketId: Long? = null
        var targetPersonName: String? = null

        val liquidPockets = activePockets.filter { it.pocketType == PocketType.LIQUID }
        val peerPockets = activePockets.filter { it.pocketType == PocketType.COUNTERPARTY || it.pocketType == PocketType.PEER }

        var bestPocketScore = Int.MAX_VALUE
        var bestPocket: VaultPocket? = null

        for (pocket in activePockets) {
            val pocketNameLower = pocket.name.toLowerCase(Locale.ROOT)
            if (cleanLower.contains(pocketNameLower)) {
                bestPocket = pocket
                bestPocketScore = 0
                break
            }
            val words = cleanLower.split(" ")
            for (word in words) {
                if (word.length >= 3) {
                    val dist = levenshtein(word, pocketNameLower)
                    if (dist <= 1 && dist < bestPocketScore) {
                        bestPocketScore = dist
                        bestPocket = pocket
                    }
                }
            }
        }

        matchedPocketId = bestPocket?.id ?: liquidPockets.firstOrNull()?.id

        if (nature in listOf(MovementNature.PEER_LEND, MovementNature.PEER_BORROW, MovementNature.PEER_COLLECT, MovementNature.PEER_REPAY)) {
            val peerKeywords = listOf("to", "from", "with")
            val tokens = cleanPrompt.split(Regex("\\s+"))
            for (i in 0 until tokens.size - 1) {
                if (tokens[i].toLowerCase(Locale.ROOT) in peerKeywords) {
                    val candidateName = tokens[i + 1].replace(Regex("[^a-zA-Z0-9]"), "")
                    if (candidateName.length > 1 && !candidateName.equals("bank", ignoreCase = true)) {
                        targetPersonName = candidateName
                        val matchedPeer = peerPockets.firstOrNull { it.name.equals(candidateName, ignoreCase = true) }
                        if (matchedPeer != null) {
                            targetPocketId = matchedPeer.id
                        }
                        break
                    }
                }
            }
        }

        val category = inferCategory(cleanLower)
        val note = cleanPrompt
            .replace(Regex("(?i)^(spent|paid|bought|got|received|lent|borrowed|transferred|collected|repaid)"), "")
            .replace(Regex("(?i)\\b(on|for|at|from|to|in|via)\\b"), "")
            .trim()
            .ifBlank { category }

        return ParsedVaultCommand(
            nature = nature,
            amount = amount,
            category = category,
            merchant = note,
            matchedPocketId = matchedPocketId,
            targetPocketId = targetPocketId,
            targetPersonName = targetPersonName
        )
    }

    private fun inferCategory(text: String): String {
        return when {
            text.contains("food") || text.contains("dinner") || text.contains("lunch") || text.contains("swiggy") || text.contains("zomato") || text.contains("cafe") || text.contains("tea") || text.contains("coffee") -> "Food & Dining"
            text.contains("groceries") || text.contains("milk") || text.contains("blinkit") || text.contains("zepto") || text.contains("instamart") || text.contains("vegetables") -> "Groceries"
            text.contains("uber") || text.contains("ola") || text.contains("auto") || text.contains("petrol") || text.contains("fuel") || text.contains("metro") || text.contains("cab") -> "Transport"
            text.contains("recharge") || text.contains("bill") || text.contains("electricity") || text.contains("wifi") || text.contains("rent") -> "Bills"
            text.contains("amazon") || text.contains("flipkart") || text.contains("clothes") || text.contains("shoes") || text.contains("shopping") -> "Shopping"
            text.contains("medicine") || text.contains("doctor") || text.contains("pharma") || text.contains("hospital") -> "Health"
            text.contains("movie") || text.contains("netflix") || text.contains("party") || text.contains("trip") -> "Leisure"
            text.contains("salary") || text.contains("stipend") || text.contains("bonus") -> "Salary"
            text.contains("lend") || text.contains("lent") || text.contains("borrow") -> "Peer Transfer"
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
