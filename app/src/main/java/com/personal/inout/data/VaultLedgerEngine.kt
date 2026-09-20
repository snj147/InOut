package com.inout.vault.engine

import android.content.SharedPreferences
import com.inout.vault.data.*
import java.util.Calendar

class VaultLedgerEngine(
    private val flowRecordDao: StateFlowDao,
    private val prefs: SharedPreferences
) {

    suspend fun executeMovement(record: FlowRecord) {
        val nature = MovementNature.valueOf(record.movementNature)

        // Strict account assignment enforcement
        if (nature in listOf(MovementNature.OUTFLOW, MovementNature.CARD_PAYMENT, MovementNature.PEER_LEND, MovementNature.PEER_REPAY)) {
            requireNotNull(record.sourcePocketId) { "Source account must be specified for outflows" }
        }
        if (nature in listOf(MovementNature.INFLOW, MovementNature.PEER_BORROW, MovementNature.PEER_COLLECT)) {
            requireNotNull(record.targetPocketId) { "Target account must be specified for inflows" }
        }
        if (nature == MovementNature.TRANSFER) {
            requireNotNull(record.sourcePocketId) { "Source account required for transfer" }
            requireNotNull(record.targetPocketId) { "Destination account required for transfer" }
            require(record.sourcePocketId != record.targetPocketId) { "Cannot transfer to the same account" }
        }

        flowRecordDao.insertFlowRecord(record)
    }

    suspend fun registerRecurringMovement(
        templateRecord: FlowRecord,
        cadence: CadenceType,
        firstDueDate: Long
    ) {
        // Enforces future gating: If firstDueDate is strictly > currentTimeMillis,
        // it registers as a recurring schedule, but does NOT impact balance until that date passes.
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
            val cadence = CadenceType.valueOf(schedule.recurringCadence)
            if (cadence == CadenceType.NONE) continue

            var nextDue = calculateNextOccurrence(schedule.timestamp, cadence)
            while (nextDue <= now) {
                val execution = schedule.copy(
                    id = 0,
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
