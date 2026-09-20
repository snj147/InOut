package com.personal.inout.util

import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
import java.util.*

data class ParsedCommand(
    val amount: Double,
    val nature: MovementNature,
    val category: String,
    val merchant: String,
    val matchedPocketId: Long?,
    val targetPersonName: String? = null,
    val targetPocketId: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object NaturalLanguageParser {
    private val expenseCategoryKeywords = mapOf(
        "Groceries" to listOf("grocery", "groceries", "supermarket", "milk", "vegetables", "fruits", "dmart", "blinkit", "zepto", "instamart", "kirana", "paneer", "atta", "dal", "bread", "eggs"),
        "Food & Dining" to listOf("coffee", "cafe", "tea", "chai", "lunch", "dinner", "breakfast", "swiggy", "zomato", "restaurant", "starbucks", "burger", "pizza", "biryani", "food", "dosa", "snack"),
        "Transport" to listOf("uber", "ola", "auto", "metro", "fuel", "petrol", "diesel", "cab", "taxi", "bus", "train", "flight", "fastag", "toll"),
        "Shopping" to listOf("amazon", "flipkart", "clothes", "shoes", "zara", "myntra", "shopping", "electronics", "gadget", "croma"),
        "Bills" to listOf("bill", "electricity", "water", "wifi", "internet", "recharge", "rent", "broadband", "maintenance", "gas", "jio", "airtel"),
        "Health" to listOf("medicine", "doctor", "hospital", "pharmacy", "clinic", "meds", "apollo", "lab"),
        "Leisure" to listOf("movie", "cinema", "netflix", "spotify", "game", "party", "pub", "bar", "outing")
    )

    fun parse(input: String, activePockets: List<VaultPocket>): ParsedCommand? {
        val raw = input.trim()
        if (raw.isBlank()) return null

        // 1. EXTRACT NUMERICAL AMOUNT
        var amount: Double? = null
        val kMatch = Regex("""(?i)(?:rs\.?|inr|₹)?\s*([0-9]+(?:\.[0-9]+)?)\s*k\b""").find(raw)
        if (kMatch != null) {
            amount = (kMatch.groupValues[1].toDoubleOrNull() ?: 0.0) * 1000.0
        } else {
            val normalMatch = Regex("""(?i)(?:rs\.?|inr|₹)?\s*([0-9]+(?:\.[0-9]{1,2})?)""").find(raw)
            if (normalMatch != null) {
                amount = normalMatch.groupValues[1].toDoubleOrNull()
            }
        }
        if (amount == null || amount <= 0.0) return null

        var workingText = raw

        // 2. PARSE OPTIONAL DATE
        var parsedTimestamp = System.currentTimeMillis()
        val dateMatch = Regex("""(?i)\bon\s+([0-3]?[0-9][/\-.][0-1]?[0-9](?:[/\-.](?:20)?[0-9]{2})?)\b""").find(workingText)
        if (dateMatch != null) {
            val rawDate = dateMatch.groupValues[1].replace("-", "/").replace(".", "/")
            try {
                val parts = rawDate.split("/")
                val day = parts[0].toInt()
                val month = parts[1].toInt() - 1
                val year = if (parts.size > 2) {
                    val y = parts[2].toInt()
                    if (y < 100) 2000 + y else y
                } else Calendar.getInstance().get(Calendar.YEAR)

                val cal = Calendar.getInstance().apply { set(year, month, day, 12, 0, 0) }
                parsedTimestamp = cal.timeInMillis
                workingText = workingText.replace(dateMatch.value, " ")
            } catch (_: Exception) {}
        } else if (workingText.contains(Regex("""(?i)\byesterday\b"""))) {
            parsedTimestamp = System.currentTimeMillis() - (1000 * 60 * 60 * 24)
            workingText = workingText.replace(Regex("""(?i)\byesterday\b"""), " ")
        }

        // 3. ACTION VERB & INTENT DETECTION
        var detectedNature: MovementNature? = null
        var targetPerson: String? = null
        var sourcePocketId: Long? = null
        var destPocketId: Long? = null

        val liquidPockets = activePockets.filter { it.pocketType == PocketType.LIQUID }

        // A. Transfer: "Transferred 3000 from IDFC to SBI"
        if (Regex("""(?i)\b(transferred|transfer|moved|sent)\b""").containsMatchIn(workingText)) {
            detectedNature = MovementNature.TRANSFER

            val transferRegex = Regex("""(?i)\bfrom\s+([A-Za-z0-9_-]+)\s+to\s+([A-Za-z0-9_-]+)""")
            val match = transferRegex.find(workingText)
            if (match != null) {
                val srcName = match.groupValues[1].trim()
                val tgtName = match.groupValues[2].trim()
                sourcePocketId = liquidPockets.firstOrNull { it.name.equals(srcName, ignoreCase = true) }?.id
                destPocketId = liquidPockets.firstOrNull { it.name.equals(tgtName, ignoreCase = true) }?.id
                workingText = workingText.replace(match.value, " ")
            } else {
                for (pocket in liquidPockets) {
                    if (workingText.contains(pocket.name, ignoreCase = true)) {
                        if (sourcePocketId == null) {
                            sourcePocketId = pocket.id
                            workingText = workingText.replaceFirst(pocket.name, " ", ignoreCase = true)
                        } else if (destPocketId == null && pocket.id != sourcePocketId) {
                            destPocketId = pocket.id
                            workingText = workingText.replaceFirst(pocket.name, " ", ignoreCase = true)
                            break
                        }
                    }
                }
            }
        } else {
            // B. Peer Collection: "got 2000 from Rahul"
            val fromPersonMatch = Regex("""(?i)\b(?:got|received|collected|collect)\s+.*?\bfrom\s+([A-Za-z0-9_-]+)""").find(workingText)
            if (fromPersonMatch != null) {
                detectedNature = MovementNature.PEER_COLLECT
                targetPerson = fromPersonMatch.groupValues[1].trim()
                workingText = workingText.replace(fromPersonMatch.value, " ")
            } else if (Regex("""(?i)\b(lent|loaned|lend)\b""").containsMatchIn(workingText)) {
                detectedNature = MovementNature.PEER_LEND
                val pMatch = Regex("""(?i)\b(?:to\s+)?([A-Za-z0-9_-]+)""").find(
                    workingText.replace(Regex("""(?i)\b(lent|loaned|lend|rs\.?|inr|₹|[0-9]+(?:\.[0-9]+)?\s*k?)\b"""), " ").trim()
                )
                targetPerson = pMatch?.groupValues?.get(1)?.trim()
            } else if (Regex("""(?i)\b(borrowed|borrow)\b""").containsMatchIn(workingText)) {
                detectedNature = MovementNature.PEER_BORROW
                val pMatch = Regex("""(?i)\b(?:from\s+)?([A-Za-z0-9_-]+)""").find(
                    workingText.replace(Regex("""(?i)\b(borrowed|borrow|rs\.?|inr|₹|[0-9]+(?:\.[0-9]+)?\s*k?)\b"""), " ").trim()
                )
                targetPerson = pMatch?.groupValues?.get(1)?.trim()
            } else if (Regex("""(?i)\b(spent|paid|gave|bought|spend|pay)\b""").containsMatchIn(workingText)) {
                detectedNature = MovementNature.OUTFLOW
            } else if (Regex("""(?i)\b(got|received|salary|earned|income|receive)\b""").containsMatchIn(workingText)) {
                detectedNature = MovementNature.INFLOW
            }

            if (detectedNature == null) return null

            // Match single liquid pocket for non-transfer actions
            var matchedPocket: VaultPocket? = null
            for (pocket in liquidPockets) {
                if (workingText.contains(pocket.name, ignoreCase = true)) {
                    matchedPocket = pocket
                    workingText = workingText.replace(pocket.name, " ", ignoreCase = true)
                    break
                }
            }
            sourcePocketId = matchedPocket?.id ?: liquidPockets.firstOrNull()?.id
        }

        if (detectedNature == null) return null

        // 4. CATEGORY EXTRACTION
        var matchedCategory = if (detectedNature == MovementNature.INFLOW) "Salary" else "General"
        if (detectedNature == MovementNature.OUTFLOW) {
            for ((category, keywords) in expenseCategoryKeywords) {
                if (keywords.any { workingText.contains(Regex("""(?i)\b$it\b""")) }) {
                    matchedCategory = category
                    break
                }
            }
        }

        // 5. CLEAN NARRATION
        var cleanNote = workingText
            .replace(Regex("""(?i)\b(?:rs\.?|inr|₹|[0-9]+(?:\.[0-9]+)?\s*k?)\b"""), " ")
            .replace(Regex("""(?i)\b(?:spent|paid|gave|bought|got|received|salary|lent|borrowed|collected|transferred|transfer|moved|sent|for|at|to|from|on|via|in|today|with)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleanNote.isBlank()) {
            cleanNote = targetPerson ?: if (detectedNature == MovementNature.INFLOW) "Income" else if (detectedNature == MovementNature.TRANSFER) "Account Transfer" else matchedCategory
        } else {
            cleanNote = cleanNote.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }

        return ParsedCommand(
            amount = amount,
            nature = detectedNature,
            category = if (targetPerson != null) "Peer Transfer" else if (detectedNature == MovementNature.TRANSFER) "Transfer" else matchedCategory,
            merchant = cleanNote,
            matchedPocketId = sourcePocketId,
            targetPersonName = targetPerson,
            targetPocketId = destPocketId,
            timestamp = parsedTimestamp
        )
    }
}
