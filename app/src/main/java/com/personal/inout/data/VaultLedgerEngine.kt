package com.personal.inout.data

import android.content.SharedPreferences
import java.util.Calendar

sealed class VaultExecutionResult {
    data class Success(
        val recordId: Long = 0L,
        val summary: String = "Transaction recorded successfully",
        val spikePacingMessage: String? = null
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
        timestamp: Long = System.currentTimeMillis(),
        autoSplitEnabled: Boolean = false,
        isRecurring: Boolean = false,
        frequency: String = "NONE"
    ): VaultExecutionResult {
        return try {
            if (amount <= 0.0) {
                return VaultExecutionResult.OverdraftError("Amount must be greater than zero")
            }

            val balances = flowRecordDao.getPocketBalancesSync(timestamp)
            val pockets = flowRecordDao.getActivePocketsSync()

            if (nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.PEER_REPAY)) {
                if (sourcePocketId == null || sourcePocketId == 0L) {
                    return VaultExecutionResult.OverdraftError("Please specify a source account")
                }

                val primaryPocket = pockets.firstOrNull { it.id == sourcePocketId }
                    ?: return VaultExecutionResult.OverdraftError("Source account not found")

                val primaryBal = balances.firstOrNull { it.pocketId == sourcePocketId.toString() }?.computedBalance ?: 0.0

                if (primaryPocket.pocketType == PocketType.LIQUID) {
                    if (primaryBal < amount) {
                        if (!autoSplitEnabled) {
                            return VaultExecutionResult.OverdraftError(
                                "Insufficient funds in ${primaryPocket.name}. Balance: ₹${primaryBal.toInt()}, Required: ₹${amount.toInt()}"
                            )
                        }

                        val otherLiquids = balances.filter {
                            it.pocketType == PocketType.LIQUID && it.pocketId != sourcePocketId.toString() && it.computedBalance > 0.0
                        }.sortedByDescending { it.computedBalance }

                        val totalAvailable = primaryBal.coerceAtLeast(0.0) + otherLiquids.sumOf { it.computedBalance }
                        if (totalAvailable < amount) {
                            return VaultExecutionResult.OverdraftError(
                                "Total liquid balance across all accounts is only ₹${totalAvailable.toInt()}. Cannot cover ₹${amount.toInt()}"
                            )
                        }

                        var remainderNeeded = amount
                        val splitSummary = StringBuilder("Auto-Split: ")

                        if (primaryBal > 0.0) {
                            val chunk = primaryBal
                            flowRecordDao.insertFlowRecord(
                                FlowRecord(
                                    sourcePocketId = sourcePocketId,
                                    targetPocketId = targetPocketId,
                                    amount = chunk,
                                    movementNature = nature,
                                    category = category.ifBlank { "General" },
                                    note = "${note.ifBlank { category }} (Leg 1)",
                                    timestamp = timestamp,
                                    isRecurring = isRecurring,
                                    frequency = frequency
                                )
                            )
                            remainderNeeded -= chunk
                            splitSummary.append("${primaryPocket.name}: ₹${chunk.toInt()} ")
                        }

                        for (secondary in otherLiquids) {
                            if (remainderNeeded <= 0.0) break
                            val chunk = minOf(secondary.computedBalance, remainderNeeded)
                            flowRecordDao.insertFlowRecord(
                                FlowRecord(
                                    sourcePocketId = secondary.pocketId.toLong(),
                                    targetPocketId = targetPocketId,
                                    amount = chunk,
                                    movementNature = nature,
                                    category = category.ifBlank { "General" },
                                    note = "${note.ifBlank { category }} (Split Cover)",
                                    timestamp = timestamp
                                )
                            )
                            remainderNeeded -= chunk
                            splitSummary.append("+ ${secondary.name}: ₹${chunk.toInt()} ")
                        }

                        return VaultExecutionResult.Success(
                            recordId = 0L,
                            summary = splitSummary.toString().trim()
                        )
                    }
                }
            }

            if (nature == MovementNature.TRANSFER) {
                if (sourcePocketId == null || targetPocketId == null || sourcePocketId == targetPocketId) {
                    return VaultExecutionResult.OverdraftError("Invalid transfer source or destination")
                }
                val srcBal = balances.firstOrNull { it.pocketId == sourcePocketId.toString() }?.computedBalance ?: 0.0
                val srcPocket = pockets.firstOrNull { it.id == sourcePocketId }
                if (srcPocket?.pocketType == PocketType.LIQUID && srcBal < amount) {
                    return VaultExecutionResult.OverdraftError("Cannot transfer ₹${amount.toInt()} from ${srcPocket.name}. Available: ₹${srcBal.toInt()}")
                }
            }

            val entity = FlowRecord(
                sourcePocketId = sourcePocketId,
                targetPocketId = targetPocketId,
                amount = amount,
                movementNature = nature,
                category = category.ifBlank { "General" },
                note = note,
                timestamp = timestamp,
                isRecurring = isRecurring,
                recurringCadence = frequency,
                frequency = frequency
            )

            val id = flowRecordDao.insertFlowRecord(entity)

            val dailyBurn = prefs?.getFloat("daily_burn_ceiling", 450f)?.toDouble() ?: 450.0
            val spikeMessage = if (nature == MovementNature.OUTFLOW && amount > (dailyBurn * 1.5)) {
                val excess = amount - dailyBurn
                val recoveryDaily = ((dailyBurn * 5 - excess) / 5).coerceAtLeast(100.0)
                "Over daily target by ₹${excess.toInt()}. Spend ₹${recoveryDaily.toInt()}/day for 5 days to recover runway."
            } else null

            VaultExecutionResult.Success(
                recordId = id,
                summary = "₹${amount.toInt()} logged for ${entity.note.ifBlank { entity.category }}",
                spikePacingMessage = spikeMessage
            )
        } catch (e: Exception) {
            VaultExecutionResult.OverdraftError(e.message ?: "Failed to record transaction")
        }
    }

    suspend fun triangularPeerSettle(debtorName: String, creditorName: String, amount: Double? = null): VaultExecutionResult {
        return try {
            val activePockets = flowRecordDao.getActivePocketsSync()
            val debtor = activePockets.firstOrNull { it.pocketType == PocketType.COUNTERPARTY && it.name.equals(debtorName, ignoreCase = true) }
                ?: return VaultExecutionResult.OverdraftError("Counterparty '$debtorName' not found")
            val creditor = activePockets.firstOrNull { it.pocketType == PocketType.COUNTERPARTY && it.name.equals(creditorName, ignoreCase = true) }
                ?: return VaultExecutionResult.OverdraftError("Counterparty '$creditorName' not found")

            val balances = flowRecordDao.getPocketBalancesSync()
            val debtorBal = balances.firstOrNull { it.pocketId == debtor.id.toString() }?.computedBalance ?: 0.0
            val creditorBal = balances.firstOrNull { it.pocketId == creditor.id.toString() }?.computedBalance ?: 0.0

            val settleAmount = amount ?: minOf(Math.abs(debtorBal), Math.abs(creditorBal))
            if (settleAmount <= 0.0) {
                return VaultExecutionResult.OverdraftError("No overlapping debt balance found to settle between $debtorName and $creditorName")
            }

            flowRecordDao.insertFlowRecord(
                FlowRecord(
                    sourcePocketId = debtor.id,
                    targetPocketId = creditor.id,
                    amount = settleAmount,
                    movementNature = MovementNature.TRANSFER,
                    category = "Peer Transfer",
                    note = "Triangular Debt Settle ($debtorName -> $creditorName)"
                )
            )

            VaultExecutionResult.Success(
                summary = "Settled ₹${settleAmount.toInt()} directly between $debtorName and $creditorName"
            )
        } catch (e: Exception) {
            VaultExecutionResult.OverdraftError("Triangular settle failed: ${e.message}")
        }
    }

    suspend fun catchUpRecurringRules(): Int {
        val now = System.currentTimeMillis()
        val activeSchedules = flowRecordDao.getActiveRecurringSchedulesSync()
        var generatedCount = 0

        for (schedule in activeSchedules) {
            val cadenceStr = if (schedule.frequency != "NONE") schedule.frequency else schedule.recurringCadence
            val cadence = try {
                CadenceType.valueOf(cadenceStr)
            } catch (_: Exception) {
                CadenceType.NONE
            }
            if (cadence == CadenceType.NONE) continue

            var nextDue = calculateNextOccurrence(schedule.timestamp, cadence)
            while (nextDue <= now) {
                val execution = schedule.copy(
                    id = 0L,
                    timestamp = nextDue,
                    isRecurring = false,
                    isPaused = false
                )
                flowRecordDao.insertFlowRecord(execution)
                generatedCount++
                nextDue = calculateNextOccurrence(nextDue, cadence)
            }
        }
        return generatedCount
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
