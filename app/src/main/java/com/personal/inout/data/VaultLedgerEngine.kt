package com.personal.inout.data

import kotlinx.coroutines.flow.first

sealed class VaultExecutionResult {
    object Success : VaultExecutionResult()
    data class OverdraftError(val message: String) : VaultExecutionResult()
}

class VaultLedgerEngine(private val dao: StateFlowDao) {

    suspend fun recordMovement(
        nature: MovementNature,
        sourcePocketId: Long?,
        targetPocketId: Long?,
        amount: Double,
        category: String,
        note: String,
        autoSplitEnabled: Boolean = false
    ): VaultExecutionResult {
        if (amount <= 0.0) {
            return VaultExecutionResult.OverdraftError("Amount must be greater than zero")
        }

        val allSummaries = dao.observePocketBalances().first()
        val liquidPockets = allSummaries.filter { it.pocketType == PocketType.LIQUID }

        // 1. TRANSFERS: Source must have balance >= amount
        if (nature == MovementNature.TRANSFER) {
            if (sourcePocketId == null || targetPocketId == null) {
                return VaultExecutionResult.OverdraftError("Select valid source and destination accounts")
            }
            if (sourcePocketId == targetPocketId) {
                return VaultExecutionResult.OverdraftError("Cannot transfer to the same account")
            }
            val srcSummary = liquidPockets.firstOrNull { it.pocketId == sourcePocketId }
            val available = srcSummary?.currentBalance ?: 0.0
            if (available < amount) {
                return VaultExecutionResult.OverdraftError("Cannot transfer ₹${amount.toInt()}. ${srcSummary?.name ?: "Source"} only has ₹${available.toInt()}.")
            }

            dao.insertFlowRecord(
                FlowRecord(
                    nature = MovementNature.TRANSFER,
                    sourcePocketId = sourcePocketId,
                    targetPocketId = targetPocketId,
                    amount = amount,
                    category = "Transfer",
                    note = note.ifBlank { "Account Transfer" }
                )
            )
            return VaultExecutionResult.Success
        }

        // 2. OUTFLOW (SPENT), CARD PAYMENT, PEER LEND, PEER REPAY: Strict balance validation
        val isSpendingCash = nature in listOf(
            MovementNature.OUTFLOW,
            MovementNature.CARD_PAYMENT,
            MovementNature.PEER_LEND,
            MovementNature.PEER_REPAY
        )

        if (isSpendingCash && sourcePocketId != null) {
            val srcSummary = allSummaries.firstOrNull { it.pocketId == sourcePocketId }
            if (srcSummary?.pocketType == PocketType.LIQUID) {
                val available = srcSummary.currentBalance

                if (available < amount) {
                    if (!autoSplitEnabled) {
                        return VaultExecutionResult.OverdraftError("Insufficient balance. ${srcSummary.name} has ₹${available.toInt()}, cannot debit ₹${amount.toInt()}.")
                    }

                    // Auto-Split execution across other bank accounts
                    val totalCombinedLiquid = liquidPockets.sumOf { it.currentBalance }
                    if (totalCombinedLiquid < amount) {
                        return VaultExecutionResult.OverdraftError("Combined balance across all accounts is only ₹${totalCombinedLiquid.toInt()}. Transaction blocked.")
                    }

                    var remaining = amount
                    if (available > 0.0) {
                        dao.insertFlowRecord(
                            FlowRecord(
                                nature = nature,
                                sourcePocketId = sourcePocketId,
                                targetPocketId = targetPocketId,
                                amount = available,
                                category = category,
                                note = "$note (${srcSummary.name})"
                            )
                        )
                        remaining -= available
                    }

                    val others = liquidPockets.filter { it.pocketId != sourcePocketId }
                    for (other in others) {
                        if (remaining <= 0.0) break
                        val otherAvail = other.currentBalance.coerceAtLeast(0.0)
                        if (otherAvail <= 0.0) continue
                        val take = Math.min(otherAvail, remaining)

                        dao.insertFlowRecord(
                            FlowRecord(
                                nature = nature,
                                sourcePocketId = other.pocketId,
                                targetPocketId = targetPocketId,
                                amount = take,
                                category = category,
                                note = "$note (Auto-Split: ${other.name})"
                            )
                        )
                        remaining -= take
                    }
                    return VaultExecutionResult.Success
                }
            }
        }

        // 3. INFLOW / DEFAULT COMMITS
        dao.insertFlowRecord(
            FlowRecord(
                nature = nature,
                sourcePocketId = sourcePocketId,
                targetPocketId = targetPocketId,
                amount = amount,
                category = category,
                note = note
            )
        )
        return VaultExecutionResult.Success
    }
}
