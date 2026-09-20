package com.personal.inout.util

import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket

data class ParsedCommand(
    val amount: Double,
    val nature: MovementNature,
    val category: String,
    val merchant: String,
    val matchedPocketId: Long?
)

object NaturalLanguageParser {
    private val expenseCategoryKeywords = mapOf(
        "Groceries" to listOf("grocery", "groceries", "supermarket", "milk", "vegetables", "fruits", "dmart", "blinkit", "zepto", "instamart", "kirana"),
        "Food & Dining" to listOf("coffee", "cafe", "tea", "chai", "lunch", "dinner", "breakfast", "swiggy", "zomato", "restaurant", "starbucks", "burger", "pizza", "biryani", "food"),
        "Transport" to listOf("uber", "ola", "auto", "metro", "fuel", "petrol", "diesel", "cab", "taxi", "bus", "train", "flight"),
        "Shopping" to listOf("amazon", "flipkart", "clothes", "shoes", "zara", "myntra", "shopping", "electronics", "gadget"),
        "Bills" to listOf("bill", "electricity", "water", "wifi", "internet", "recharge", "rent", "broadband", "maintenance", "gas"),
        "Health" to listOf("medicine", "doctor", "hospital", "pharmacy", "clinic", "meds", "apollo", "lab"),
        "Leisure" to listOf("movie", "cinema", "netflix", "spotify", "game", "party", "pub", "bar", "outing")
    )

    private val incomeKeywords = listOf("salary", "stipend", "dividend", "interest", "cashback", "refund", "bonus", "freelance", "earned", "received", "got")

    fun parse(input: String, activePockets: List<VaultPocket>): ParsedCommand? {
        val raw = input.trim()
        if (raw.isBlank()) return null

        // 1. Extract Numeric Amount
        val amountMatch = Regex("""(?i)(?:rs\.?|inr|₹)?\s*([0-9]+(?:\.[0-9]{1,2})?)""").find(raw)
        val amount = amountMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: return null

        // Strip amount token from parsing string
        var remainder = raw.replaceFirst(amountMatch.value, "").trim()

        // 2. Determine Movement Nature (Inflow vs Outflow)
        val isIncome = incomeKeywords.any { remainder.contains(it, ignoreCase = true) }
        val nature = if (isIncome) MovementNature.INFLOW else MovementNature.OUTFLOW

        // 3. Match Account if mentioned
        var matchedPocket: VaultPocket? = null
        for (pocket in activePockets) {
            if (remainder.contains(pocket.name, ignoreCase = true)) {
                matchedPocket = pocket
                remainder = remainder.replace(pocket.name, "", ignoreCase = true).trim()
                break
            }
        }
        if (matchedPocket == null) {
            matchedPocket = activePockets.firstOrNull { it.pocketType == PocketType.LIQUID }
        }

        // 4. Detect Category
        var matchedCategory = if (isIncome) "Salary" else "General"
        if (!isIncome) {
            for ((category, keywords) in expenseCategoryKeywords) {
                if (keywords.any { remainder.contains(it, ignoreCase = true) }) {
                    matchedCategory = category
                    break
                }
            }
        }

        // 5. Clean Remainder for Merchant / Note
        // Remove prepositions like "for", "at", "to", "on", "from", "in", "via", "paid"
        var cleanNote = remainder
            .replace(Regex("""(?i)\b(for|at|to|on|from|in|via|paid|spent|got|received)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleanNote.isBlank()) {
            cleanNote = if (isIncome) "Income" else matchedCategory
        } else {
            cleanNote = cleanNote.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }

        return ParsedCommand(
            amount = amount,
            nature = nature,
            category = matchedCategory,
            merchant = cleanNote,
            matchedPocketId = matchedPocket?.id
        )
    }
}
