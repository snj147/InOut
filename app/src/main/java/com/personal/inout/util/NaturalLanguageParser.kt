package com.personal.inout.util

import com.personal.inout.data.MovementNature
import com.personal.inout.data.VaultPocket

data class ParsedMovementCommand(
    val nature: MovementNature,
    val amount: Double,
    val category: String,
    val merchant: String,
    val matchedPocketId: Long?,
    val targetPocketId: Long? = null,
    val targetPersonName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object NaturalLanguageParser {

    fun parse(input: String, activePockets: List<VaultPocket>): ParsedMovementCommand? {
        val parsed = FuzzyCommandParser.parse(input, activePockets) ?: return null
        return ParsedMovementCommand(
            nature = parsed.nature,
            amount = parsed.amount,
            category = parsed.category,
            merchant = parsed.merchant,
            matchedPocketId = parsed.matchedPocketId,
            targetPocketId = parsed.targetPocketId,
            targetPersonName = parsed.targetPersonName,
            timestamp = parsed.timestamp
        )
    }
}
