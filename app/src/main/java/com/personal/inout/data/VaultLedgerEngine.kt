package com.personal.inout.data

import android.content.SharedPreferences
import java.util.Calendar

sealed class VaultExecutionResult {
    data class Success(
        val recordId: Long = 0L,
        val summary: String = "Transaction recorded successfully"
    ) : VaultExecutionResult()

    open class OverdraftError(
        open val message: String
    ) : VaultExecutionResult()

    data class Error(
        override val message: String
    ) : OverdraftError(message)
}

class VaultLedgerEngine(
    private val flowRecordDao: StateFlowDao,
    private val prefs: SharedPreferences? = null
) {

    suspend fun recordMovement(
        nature: MovementNature,
        sourcePocketId: Long? = null,
        targetPocketId: Long? = null,
        amount: Double,
        category: String,
        note: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): VaultExecutionResult {
        return recordMovementInternal(
            nature = nature,
            sourcePocketId = sourcePocketId,
            targetPocketId = targetPocketId,
            amount = amount,
            category = category,
            note = note,
            timestamp = timestamp
        )
    }

    suspend fun recordMovement(
        nature: MovementNature,
        sourcePocketId: Long? = null,
        targetPocketId: Long? = null,
        amount: Long,
        category: String,
        note: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): VaultExecutionResult {
        return recordMovementInternal(
            nature = nature,
            sourcePocketId = sourcePocketId,
            targetPocketId = targetPocketId,
            amount = amount.toDouble(),
            category = category,
            note = note,
            timestamp = timestamp
        )
    }

    suspend fun recordMovement(record: FlowRecord): VaultExecutionResult {
        val nature = try {
            MovementNature.valueOf(record.movementNature)
        } catch (_: Exception) {
            MovementNature.OUTFLOW
        }
        return recordMovementInternal(
            nature = nature,
            sourcePocketId = record.sourcePocketId,
            targetPocketId = record.targetPocketId,
            amount = record.amount,
            category = record.category,
            note = record.note,
            timestamp = record.timestamp
        )
    }

    suspend fun executeMovement(record: FlowRecord) {
        recordMovement(record)
    }

    private suspend fun recordMovementInternal(
        nature: MovementNature,
        sourcePocketId: Long?,
        targetPocketId: Long?,
        amount: Double,
        category: String,
        note: String,
        timestamp: Long
    ): VaultExecutionResult {
        return try {
            if (nature in listOf(MovementNature.OUTFLOW, MovementNature.CARD_PAYMENT, MovementNature.PEER_LEND, MovementNature.PEER_REPAY)) {
                if (sourcePocketId == null || sourcePocketId == 0L) {
                    return VaultExecutionResult.OverdraftError("Source account required for outflow")
                }
            }
            if (nature in listOf(MovementNature.INFLOW, MovementNature.PEER_BORROW, MovementNature.PEER_COLLECT)) {
                if (targetPocketId == null || targetPocketId == 0L) {
                    return VaultExecutionResult.OverdraftError("Target account required for inflow")
                }
            }
            if (nature == MovementNature.TRANSFER) {
                if (sourcePocketId == null || sourcePocketId == 0L) {
                    return VaultExecutionResult.OverdraftError("Source account required for transfer")
                }
                if (targetPocketId == null || targetPocketId == 0L) {
                    return VaultExecutionResult.OverdraftError("Destination account required for transfer")
                }
                if (sourcePocketId == targetPocketId) {
                    return VaultExecutionResult.OverdraftError("Cannot transfer to the same account")
                }
            }

            val entity = FlowRecord(
                sourcePocketId = sourcePocketId,
                targetPocketId = targetPocketId,
                amount = amount,
                movementNature = nature.name,
                category = category.ifBlank { "General" },
                note = note,
                timestamp = timestamp,
                isRecurring = false,
                recurringCadence = "NONE"
            )

            val id = flowRecordDao.insertFlowRecord(entity)
            VaultExecutionResult.Success(
                recordId = id,
                summary = "₹$amount logged for ${entity.note.ifBlank { entity.category }}"
            )
        } catch (e: Exception) {
            VaultExecutionResult.OverdraftError(e.message ?: "Failed to log transaction")
        }
    }

    suspend fun registerRecurringMovement(
        templateRecord: FlowRecord,
        cadence: CadenceType,
        firstDueDate: Long
    ) {
        val scheduleRecord = templateRecord.copy(
            timestamp = firstDueDate,
            isRecurring = true,
            recurringCadence = cadence.name
        )
        flowRecordDao.insertFlowRecord(scheduleRecord)
    }

    suspend fun catchUpRecurringRules() {
        val now = System.currentTimeMillis()
        val activeSchedules = flowRecordDao.getActiveRecurringSchedulesSync()

        for (schedule in activeSchedules) {
            val cadence = try {
                CadenceType.valueOf(schedule.recurringCadence)
            } catch (_: Exception) {
                CadenceType.NONE
            }
            if (cadence == CadenceType.NONE) continue

            var nextDue = calculateNextOccurrence(schedule.timestamp, cadence)
            while (nextDue <= now) {
                val execution = schedule.copy(
                    id = 0L,
                    timestamp = nextDue,
                    isRecurring = false
                )
                flowRecordDao.insertFlowRecord(execution)
                nextDue = calculateNextOccurrence(nextDue, cadence)
            }
        }
    }

    fun calculateNextOccurrence(currentTimestamp: Long, cadence: CadenceType): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimestamp }
        when (cadence) {
            CadenceType.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            CadenceType.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            CadenceType.MONTHLY -> cal.add(Calendar.MONTH, 1)
            CadenceType.NONE -> {}
        }
        return cal.timeInMillis
    }
}
