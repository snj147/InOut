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
        val balances = dao.getPocketBalancesSync()

        if (nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.CARD_PAYMENT, MovementNature.TRANSFER)) {
            val sourceBalance = balances.firstOrNull { it.pocketId == sourcePocketId?.toString() }
            val currentAvail = sourceBalance?.computedBalance ?: 0.0

            if (currentAvail < amount && !autoSplitEnabled) {
                val accName = sourceBalance?.pocketName ?: "Source Account"
                return VaultExecutionResult.OverdraftError(
                    "Insufficient balance in '$accName' (Available: ₹${currentAvail.toInt()}). Enable Auto-Split or choose another account."
                )
            }
        }

        dao.insertFlowRecord(
            FlowRecord(
                id = 0L,
                sourcePocketId = sourcePocketId,
                targetPocketId = targetPocketId,
                amount = amount,
                movementNature = nature,
                category = category,
                note = note,
                timestamp = timestamp,
                isRecurring = isRecurring,
                frequency = frequency,
                recurringCadence = frequency,
                isPaused = false
            )
        )

        val actionVerb = when (nature) {
            MovementNature.INFLOW -> "Received"
            MovementNature.OUTFLOW -> "Spent"
            MovementNature.TRANSFER -> "Transferred"
            MovementNature.CARD_PAYMENT -> "Cleared card dues"
            MovementNature.PEER_LEND -> "Lent"
            MovementNature.PEER_BORROW -> "Borrowed"
            MovementNature.PEER_COLLECT -> "Collected"
            MovementNature.PEER_REPAY -> "Repaid"
        }

        return VaultExecutionResult.Success("$actionVerb ₹${amount.toInt()} ($note)")
    }

    suspend fun triangularPeerSettle(debtorName: String, creditorName: String, amount: Double): VaultExecutionResult {
        val activePockets = dao.getActivePocketsSync()
        val debtor = activePockets.firstOrNull { it.pocketType == PocketType.COUNTERPARTY && it.name.equals(debtorName, ignoreCase = true) }
        val creditor = activePockets.firstOrNull { it.pocketType == PocketType.COUNTERPARTY && it.name.equals(creditorName, ignoreCase = true) }

        if (debtor == null || creditor == null) {
            return VaultExecutionResult.OverdraftError("Could not locate contacts '$debtorName' or '$creditorName'")
        }

        dao.insertFlowRecord(
            FlowRecord(
                id = 0L,
                sourcePocketId = debtor.id,
                targetPocketId = creditor.id,
                amount = amount,
                movementNature = MovementNature.TRANSFER,
                category = "Triangular Settle",
                note = "Settlement: $debtorName -> $creditorName",
                timestamp = System.currentTimeMillis()
            )
        )

        return VaultExecutionResult.Success("Triangular settlement of ₹${amount.toInt()} recorded.")
    }

    suspend fun catchUpRecurringRules(): Int {
        val activeRules = dao.getActiveRecurringSchedulesSync()
        var catchUpCount = 0

        for (rule in activeRules) {
            val cadence = when (rule.recurringCadence.uppercase()) {
                "DAILY" -> CadenceType.DAILY
                "WEEKLY" -> CadenceType.WEEKLY
                "MONTHLY" -> CadenceType.MONTHLY
                else -> CadenceType.NONE
            }

            if (cadence == CadenceType.NONE) continue

            val cal = Calendar.getInstance().apply { timeInMillis = rule.timestamp }
            val now = Calendar.getInstance()

            while (cal.timeInMillis < now.timeInMillis) {
                val startWindow = cal.timeInMillis - (12 * 3600 * 1000L)
                val endWindow = cal.timeInMillis + (12 * 3600 * 1000L)

                val count = dao.countRecordsWithFingerprint(
                    rule.note,
                    rule.amount ?: 0.0,
                    startWindow,
                    endWindow
                )

                if (count == 0) {
                    dao.insertFlowRecord(
                        FlowRecord(
                            id = 0L,
                            sourcePocketId = rule.sourcePocketId,
                            targetPocketId = rule.targetPocketId,
                            amount = rule.amount,
                            movementNature = rule.movementNature,
                            category = rule.category,
                            note = rule.note,
                            timestamp = cal.timeInMillis,
                            isRecurring = false,
                            frequency = rule.frequency,
                            recurringCadence = rule.recurringCadence,
                            isPaused = false
                        )
                    )
                    catchUpCount++
                }

                when (cadence) {
                    CadenceType.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                    CadenceType.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                    CadenceType.MONTHLY -> cal.add(Calendar.MONTH, 1)
                    CadenceType.NONE -> break
                }
            }
        }
        return catchUpCount
    }
}
