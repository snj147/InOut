package com.personal.inout.util

import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
import java.text.SimpleDateFormat
import java.util.*

data class ParsedCommand(
    val amount: Double,
    val nature: MovementNature,
    val category: String,
    val merchant: String,
    val matchedPocketId: Long?,
    val targetPersonName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object NaturalLanguageParser {
    private val expenseCategoryKeywords = mapOf(
        "Groceries" to listOf("grocery", "groceries", "supermarket", "milk", "vegetables", "fruits", "dmart", "blinkit", "zepto", "instamart", "kirana", "reliance", "vegetable", "paneer", "atta", "dal"),
        "Food & Dining" to listOf("coffee", "cafe", "tea", "chai", "lunch", "dinner", "breakfast", "swiggy", "zomato", "restaurant", "starbucks", "burger", "pizza", "biryani", "food", "dosa"),
        "Transport" to listOf("uber", "ola", "auto", "metro", "fuel", "petrol", "diesel", "cab", "taxi", "bus", "train", "flight", "fastag"),
        "Shopping" to listOf("amazon", "flipkart", "clothes", "shoes", "zara", "myntra", "shopping", "electronics", "gadget", "croma"),
        "Bills" to listOf("bill", "electricity", "water", "wifi", "internet", "recharge", "rent", "broadband", "maintenance", "gas", "jio", "airtel"),
        "Health" to listOf("medicine", "doctor", "hospital", "pharmacy", "clinic", "meds", "apollo", "lab"),
        "Leisure" to listOf("movie", "cinema", "netflix", "spotify", "game", "party", "pub", "bar", "outing")
    )

    private val actionKeywords = mapOf(
        MovementNature.OUTFLOW to listOf("spent", "paid", "gave", "bought", "spend", "purchased"),
        MovementNature.INFLOW to listOf("got", "received", "salary", "earned", "income", "freelance"),
        MovementNature.PEER_LEND to listOf("lent", "loaned", "lend"),
        MovementNature.PEER_BORROW to listOf("borrowed", "borrow"),
        MovementNature.TRANSFER to listOf("transferred", "transfer", "moved", "sent")
    )

    fun parse(input: String, activePockets: List<VaultPocket>): ParsedCommand? {
        val raw = input.trim()
        if (raw.isBlank()) return null

        // 1. EXTRACT NUMERICAL AMOUNT (e.g. 2000, 2.5k, ₹450)
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

        // 2. PARSE OPTIONAL EXPLICIT DATE (e.g., on 25/08/2026, on 25-08-2026, yesterday, today)
        var parsedTimestamp = System.currentTimeMillis()
        var workingText = raw

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
                } else {
                    Calendar.getInstance().get(Calendar.YEAR)
                }
                val cal = Calendar.getInstance().apply {
                    set(year, month, day, 12, 0, 0)
                }
                parsedTimestamp = cal.timeInMillis
                workingText = workingText.replace(dateMatch.value, " ")
            } catch (_: Exception) {}
        } else if (workingText.contains(Regex("""(?i)\byesterday\b"""))) {
            parsedTimestamp = System.currentTimeMillis() - (1000 * 60 * 60 * 24)
            workingText = workingText.replace(Regex("""(?i)\byesterday\b"""), " ")
        }

        // 3. DETECT NATURE (Action Verb)
        var nature = MovementNature.OUTFLOW
        for ((nat, verbs) in actionKeywords) {
            if (verbs.any { workingText.contains(Regex("""(?i)\b$it\b""")) }) {
                nature = nat
                break
            }
        }

        // 4. EXTRACT MATCHED WALLET/ACCOUNT
        var matchedPocket: VaultPocket? = null
        for (pocket in activePockets.filter { it.pocketType == PocketType.LIQUID }) {
            if (workingText.contains(pocket.name, ignoreCase = true)) {
                matchedPocket = pocket
                workingText = workingText.replace(pocket.name, " ", ignoreCase = true)
                break
            }
        }
        if (matchedPocket == null) {
            matchedPocket = activePockets.firstOrNull { it.pocketType == PocketType.LIQUID }
        }

        // 5. PEER/COUNTERPARTY LOGIC
        var targetPerson: String? = null
        if (nature == MovementNature.PEER_LEND || nature == MovementNature.PEER_BORROW) {
            val personMatch = Regex("""(?i)\b(?:to|from)\s+([A-Za-z0-9_-]+)""").find(workingText)
            if (personMatch != null) {
                targetPerson = personMatch.groupValues[1].trim()
                workingText = workingText.replace(personMatch.value, " ")
            }
        }

        // 6. CATEGORY DETECTION
        var matchedCategory = if (nature == MovementNature.INFLOW) "Salary" else "General"
        if (nature == MovementNature.OUTFLOW) {
            for ((category, keywords) in expenseCategoryKeywords) {
                if (keywords.any { workingText.contains(Regex("""(?i)\b$it\b""")) }) {
                    matchedCategory = category
                    break
                }
            }
        }

        // 7. CLEAN MERCHANT / CONTEXT NARRATION
        var cleanNote = workingText
            .replace(Regex("""(?i)\b(?:rs\.?|inr|₹|[0-9]+(?:\.[0-9]+)?\s*k?)\b"""), " ")
            .replace(Regex("""(?i)\b(?:spent|paid|gave|bought|got|received|salary|lent|borrowed|transferred|for|at|to|from|on|via|in|today|with)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleanNote.isBlank()) {
            cleanNote = targetPerson ?: if (nature == MovementNature.INFLOW) "Income" else matchedCategory
        } else {
            cleanNote = cleanNote.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }

        return ParsedCommand(
            amount = amount,
            nature = nature,
            category = if (targetPerson != null) "Peer Transfer" else matchedCategory,
            merchant = cleanNote,
            matchedPocketId = matchedPocket?.id,
            targetPersonName = targetPerson,
            timestamp = parsedTimestamp
        )
    }
}
