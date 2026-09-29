package com.personal.inout.data

import android.content.SharedPreferences
import java.util.Calendar

sealed class VaultExecutionResult {
    data class Success(val summary: String) : VaultExecutionResult()
    data class OverdraftError(val message: String) : VaultExecutionResult()
}

class VaultLedgerEngine(
    private val dao: StateFlowDao,
    private val prefs: SharedPreferences
) {

    suspend fun recordMovement(
        nature: MovementNature,
        sourcePocketId: Long?,
        targetPocketId: Long?,
        amount: Double,
        category: String,
        note: String,
        timestamp: Long = System.currentTimeMillis(),
        autoSplitEnabled: Boolean = false,
        isRecurring: Boolean = false,
        frequency: String = "NONE"
    ): VaultExecutionResult {
        if (amount <= 0.0) {
            return VaultExecutionResult.OverdraftError("Amount must be greater than zero.")
        }

        val allPockets = dao.getActivePocketsSync()
        val balances = dao.getPocketBalancesSync()

        // 1. Strict Outflow & Transfer Double-Entry Validation
        if (nature in listOf(MovementNature.OUTFLOW, MovementNature.TRANSFER, MovementNature.PEER_LEND, MovementNature.PEER_REPAY)) {
            if (sourcePocketId == null) {
                return VaultExecutionResult.OverdraftError("Source account required.")
            }
            val srcPocket = allPockets.firstOrNull { it.id == sourcePocketId }
                ?: return VaultExecutionResult.OverdraftError("Source account not found.")

            val srcBalance = balances.firstOrNull { it.pocketId == sourcePocketId.toString() }?.computedBalance ?: 0.0

            if (srcPocket.pocketType == PocketType.LIQUID && srcBalance < amount) {
                if (!autoSplitEnabled) {
                    val available = srcBalance.coerceAtLeast(0.0)
                    return VaultExecutionResult.OverdraftError(
                        "Insufficient funds in ${srcPocket.name} (Available: ₹${String.format("%,.0f", available)}). Transaction blocked."
                    )
                }
            }
        }

        // 2. Strict Credit Card Payment Invariant
        // A payment cannot exceed outstanding liability (Available limit cannot exceed approved limit)
        if (nature == MovementNature.CARD_PAYMENT) {
            if (targetPocketId == null) {
                return VaultExecutionResult.OverdraftError("Target credit card required.")
            }
            val cardPocket = allPockets.firstOrNull { it.id == targetPocketId }
                ?: return VaultExecutionResult.OverdraftError("Credit card not found.")

            val cardBalance = balances.firstOrNull { it.pocketId == targetPocketId.toString() }?.computedBalance ?: 0.0
            val outstandingDues = if (cardBalance < 0.0) Math.abs(cardBalance) else 0.0

            if (outstandingDues == 0.0) {
                return VaultExecutionResult.OverdraftError(
                    "${cardPocket.name} has no outstanding dues. Limit is fully available."
                )
            }

            if (amount > outstandingDues + 0.01) {
                return VaultExecutionResult.OverdraftError(
                    "Payment of ₹${amount.toInt()} exceeds outstanding dues of ₹${outstandingDues.toInt()} on ${cardPocket.name}."
                )
            }
        }

        // 3. Execution
        dao.insertFlowRecord(
            FlowRecord(
                id = 0L,
                sourcePocketId = sourcePocketId,
                targetPocketId = targetPocketId,
                amount = amount,
                movementNature = nature,
                category = category,
                note = note.ifBlank { category },
                timestamp = timestamp,
                isRecurring = isRecurring,
                frequency = frequency,
                recurringCadence = frequency,
                isPaused = false
            )
        )

        val formattedAmt = String.format("%,.0f", amount)
        val summary = when (nature) {
            MovementNature.OUTFLOW -> "Logged expense of ₹$formattedAmt ($category)"
            MovementNature.INFLOW -> "Credited ₹$formattedAmt to account"
            MovementNature.TRANSFER -> "Transferred ₹$formattedAmt"
            MovementNature.CARD_PAYMENT -> "Cleared ₹$formattedAmt card dues"
            MovementNature.PEER_LEND -> "Lent ₹$formattedAmt"
            MovementNature.PEER_BORROW -> "Borrowed ₹$formattedAmt"
            MovementNature.PEER_COLLECT -> "Collected ₹$formattedAmt"
            MovementNature.PEER_REPAY -> "Repaid ₹$formattedAmt"
        }

        return VaultExecutionResult.Success(summary)
    }

    suspend fun catchUpRecurringRules(): Int {
        val recurringRules = dao.getActiveRecurringSchedulesSync()
        var catchUpCount = 0
        val now = System.currentTimeMillis()

        for (rule in recurringRules) {
            val lastRun = rule.timestamp
            val diff = now - lastRun
            val interval = when (rule.frequency) {
                "DAILY" -> 24 * 3600 * 1000L
                "WEEKLY" -> 7 * 24 * 3600 * 1000L
                "MONTHLY" -> 30 * 24 * 3600 * 1000L
                else -> Long.MAX_VALUE
            }

            if (diff >= interval) {
                recordMovement(
                    nature = rule.movementNature,
                    sourcePocketId = rule.sourcePocketId,
                    targetPocketId = rule.targetPocketId,
                    amount = rule.amount ?: 0.0,
                    category = rule.category,
                    note = rule.note,
                    timestamp = now,
                    isRecurring = false,
                    frequency = rule.frequency
                )
                dao.updateFlowRecord(rule.copy(timestamp = now))
                catchUpCount++
            }
        }
        return catchUpCount
    }
}
