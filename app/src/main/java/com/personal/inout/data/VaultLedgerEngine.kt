package com.personal.inout.data

import kotlinx.coroutines.flow.first

sealed class VaultExecutionResult {
    data class Success(val summary: String) : VaultExecutionResult()
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
        timestamp: Long = System.currentTimeMillis(),
        autoSplitEnabled: Boolean = false
    ): VaultExecutionResult {
        if (amount <= 0.0) {
            return VaultExecutionResult.OverdraftError("Amount must be greater than zero.")
        }

        val allSummaries = dao.observePocketBalances().first()
        val liquidPockets = allSummaries.filter { it.pocketType == PocketType.LIQUID }

        // 1. TRANSFERS (Liquid -> Liquid)
        if (nature == MovementNature.TRANSFER) {
            if (sourcePocketId == null || targetPocketId == null) {
                return VaultExecutionResult.OverdraftError("Select valid source and destination accounts.")
            }
            if (sourcePocketId == targetPocketId) {
                return VaultExecutionResult.OverdraftError("Cannot transfer to the same account.")
            }
            val srcSummary = liquidPockets.firstOrNull { it.pocketId == sourcePocketId }
            val available = srcSummary?.currentBalance ?: 0.0
            if (available < amount) {
                return VaultExecutionResult.OverdraftError("Transfer Blocked: ${srcSummary?.name ?: "Source"} only has ₹${available.toInt()}.")
            }

            dao.insertFlowRecord(
                FlowRecord(
                    nature = MovementNature.TRANSFER,
                    sourcePocketId = sourcePocketId,
                    targetPocketId = targetPocketId,
                    amount = amount,
                    category = "Transfer",
                    note = note.ifBlank { "Account Transfer" },
                    timestamp = timestamp
                )
            )
            return VaultExecutionResult.Success("Transferred ₹${amount.toInt()}")
        }

        // 2. CASH OUTFLOWS (Spent, Card Settlement, Peer Lending, Peer Repay)
        val isSpendingCash = nature in listOf(
            MovementNature.OUTFLOW,
            MovementNature.CARD_PAYMENT,
            MovementNature.PEER_LEND,
            MovementNature.PEER_REPAY
        )

        if (isSpendingCash) {
            // STRICT RULE: Money cannot be spent out of thin air
            if (sourcePocketId == null) {
                return VaultExecutionResult.OverdraftError("No funding account available. Create or select a Cash/Bank account first.")
            }

            val srcSummary = allSummaries.firstOrNull { it.pocketId == sourcePocketId }
            if (srcSummary == null) {
                return VaultExecutionResult.OverdraftError("Selected funding account does not exist.")
            }

            // If spending from Bank/Cash, strictly verify balance
            if (srcSummary.pocketType == PocketType.LIQUID) {
                val available = srcSummary.currentBalance

                if (available < amount) {
                    if (!autoSplitEnabled) {
                        return VaultExecutionResult.OverdraftError("Overdraft Blocked: ${srcSummary.name} has ₹${available.toInt()}, cannot debit ₹${amount.toInt()}.")
                    }

                    val totalCombinedLiquid = liquidPockets.sumOf { it.currentBalance }
                    if (totalCombinedLiquid < amount) {
                        return VaultExecutionResult.OverdraftError("Combined bank balance (₹${totalCombinedLiquid.toInt()}) is insufficient for ₹${amount.toInt()}.")
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
                                note = "$note (${srcSummary.name})",
                                timestamp = timestamp
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
                                note = "$note (Auto-Split: ${other.name})",
                                timestamp = timestamp
                            )
                        )
                        remaining -= take
                    }
                    return VaultExecutionResult.Success("Auto-Split debit completed for ₹${amount.toInt()}")
                }
            }
        }

        // 3. INFLOWS & BORROWING (Inflow, Peer Borrow, Peer Collect)
        val isInwardCash = nature in listOf(
            MovementNature.INFLOW,
            MovementNature.PEER_BORROW,
            MovementNature.PEER_COLLECT
        )

        if (isInwardCash) {
            // STRICT RULE: Cash received must have a destination liquid pocket
            if (targetPocketId == null) {
                return VaultExecutionResult.OverdraftError("No receiving account available. Create or select a Cash/Bank account first.")
            }
        }

        // 4. COMMIT VERIFIED ENTRY
        dao.insertFlowRecord(
            FlowRecord(
                nature = nature,
                sourcePocketId = sourcePocketId,
                targetPocketId = targetPocketId,
                amount = amount,
                category = category,
                note = note,
                timestamp = timestamp
            )
        )
        return VaultExecutionResult.Success("Committed ₹${amount.toInt()} ($category)")
    }
}
