package com.inout.vault.engine

import android.content.SharedPreferences
import com.inout.vault.data.*
import com.inout.vault.data.dao.RecurringRuleDao
import com.inout.vault.data.entity.RecurringRuleEntity
import java.util.Calendar

class VaultLedgerEngine(
    private val flowRecordDao: StateFlowDao,
    private val recurringRuleDao: RecurringRuleDao,
    private val prefs: SharedPreferences
) {

    suspend fun executeMovement(record: FlowRecord) {
        val nature = record.movementNature

        // 1. Validation: Outflows require sourcePocketId; Inflows require targetPocketId
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
        val now = System.currentTimeMillis()

        // 1. Decouple into independent recurring rule table
        val rule = RecurringRuleEntity(
            sourcePocketId = templateRecord.sourcePocketId,
            targetPocketId = templateRecord.targetPocketId,
            amount = templateRecord.amount,
            movementNature = templateRecord.movementNature,
            category = templateRecord.category,
            note = templateRecord.note,
            cadence = cadence.name,
            nextExecutionTimestamp = if (firstDueDate <= now) calculateNextOccurrence(firstDueDate, cadence) else firstDueDate,
            isActive = true
        )
        recurringRuleDao.insertRule(rule)

        // 2. Only write immediate record to ledger if date is current or past
        if (firstDueDate <= now) {
            val concreteRecord = templateRecord.copy(
                timestamp = firstDueDate,
                isRecurring = true
            )
            flowRecordDao.insertFlowRecord(concreteRecord)
        }
    }

    suspend fun catchUpRecurringRules() {
        val now = System.currentTimeMillis()
        val activeRules = recurringRuleDao.getActiveRules()

        for (rule in activeRules) {
            var nextDue = rule.nextExecutionTimestamp
            val cadence = CadenceType.valueOf(rule.cadence)
            var hasPosted = false

            while (nextDue <= now) {
                val record = FlowRecord(
                    sourcePocketId = rule.sourcePocketId,
                    targetPocketId = rule.targetPocketId,
                    amount = rule.amount,
                    movementNature = rule.movementNature,
                    category = rule.category,
                    note = rule.note,
                    timestamp = nextDue,
                    isRecurring = true
                )
                flowRecordDao.insertFlowRecord(record)
                hasPosted = true
                nextDue = calculateNextOccurrence(nextDue, cadence)
            }

            if (hasPosted) {
                recurringRuleDao.updateNextExecution(rule.id, nextDue)
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
