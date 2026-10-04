package com.personal.inout.data

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.abs

sealed class VaultExecutionResult {
    data class Success(val summary: String) : VaultExecutionResult()
    data class OverdraftError(val message: String) : VaultExecutionResult()
    data class DuplicateWarning(val message: String) : VaultExecutionResult()
}

class VaultLedgerEngine(
    private val dao: LedgerDao,
    private val prefs: SharedPreferences
) {
    suspend fun recordMovement(
        nature: MovementNature,
        sourcePocketId: Long,
        targetPocketId: Long? = null,
        amount: Double,
        category: String,
        note: String,
        overrideTimestamp: Long = System.currentTimeMillis(),
        status: SettlementStatus = SettlementStatus.CLEARED,
        allowAutoSplit: Boolean = false,
        isTaxDeductible: Boolean = false,
        isReimbursable: Boolean = false,
        isSubscription: Boolean = false,
        isRecurring: Boolean = false,
        recurringFrequency: String = "NONE"
    ): VaultExecutionResult = withContext(Dispatchers.IO) {
        
        if (amount <= 0) return@withContext VaultExecutionResult.OverdraftError("Transaction amount must be strictly positive.")

        val srcPocket = dao.getPocketById(sourcePocketId) ?: return@withContext VaultExecutionResult.OverdraftError("Source account missing.")
        val tgtPocket = targetPocketId?.let { dao.getPocketById(it) }

        if (nature == MovementNature.OPERATING_EXPENSE || nature == MovementNature.TRANSFER || nature == MovementNature.EMI_PRINCIPAL) {
            val availableBal = dao.computePocketBalance(sourcePocketId)
            
            if (availableBal < amount && srcPocket.type != PocketType.CREDIT_CARD && srcPocket.type != PocketType.LIABILITY_LOAN) {
                if (allowAutoSplit && nature == MovementNature.OPERATING_EXPENSE) {
                    val shortfall = amount - availableBal
                    val secondaryAccounts = dao.getAllActivePocketsSnapshot()
                        .filter { it.type == PocketType.LIQUID && it.id != sourcePocketId }
                    
                    var remainingShortfall = shortfall
                    val splitTxList = mutableListOf<LedgerTransaction>()

                    if (availableBal > 0) {
                        splitTxList.add(LedgerTransaction(amount = availableBal, movementNature = nature, sourcePocketId = sourcePocketId, category = category, description = "$note (Split)", timestamp = overrideTimestamp, status = status, isTaxDeductible = isTaxDeductible, isReimbursable = isReimbursable, isSubscription = isSubscription, isRecurring = isRecurring, recurringFrequency = recurringFrequency))
                    }

                    for (sec in secondaryAccounts) {
                        if (remainingShortfall <= 0) break
                        val secBal = dao.computePocketBalance(sec.id)
                        if (secBal > 0) {
                            val draw = minOf(secBal, remainingShortfall)
                            splitTxList.add(LedgerTransaction(amount = draw, movementNature = nature, sourcePocketId = sec.id, category = category, description = "$note (Auto-Split from ${sec.name})", timestamp = overrideTimestamp, status = status, isTaxDeductible = isTaxDeductible, isReimbursable = isReimbursable, isSubscription = isSubscription, isRecurring = isRecurring, recurringFrequency = recurringFrequency))
                            remainingShortfall -= draw
                        }
                    }

                    if (remainingShortfall > 0) {
                        return@withContext VaultExecutionResult.OverdraftError("Insufficient combined liquid funds. Shortfall: ₹${remainingShortfall.toInt()}")
                    }

                    dao.insertTransactionsBatch(splitTxList)
                    return@withContext VaultExecutionResult.Success("Auto-split across accounts successful.")
                } else {
                    return@withContext VaultExecutionResult.OverdraftError("Insufficient funds in ${srcPocket.name}. Available: ₹${availableBal.toInt()}")
                }
            }
        }

        val duplicateCheck = dao.findPotentialDuplicate(sourcePocketId, amount, overrideTimestamp)
        if (duplicateCheck != null && !isRecurring) {
            return@withContext VaultExecutionResult.DuplicateWarning("Identical transaction detected within 48 hours. Proceed manually if intentional.")
        }

        val tx = LedgerTransaction(
            amount = amount,
            movementNature = nature,
            sourcePocketId = sourcePocketId,
            targetPocketId = targetPocketId,
            category = category,
            description = note,
            timestamp = overrideTimestamp,
            status = status,
            isTaxDeductible = isTaxDeductible,
            isReimbursable = isReimbursable,
            isSubscription = isSubscription,
            isRecurring = isRecurring,
            recurringFrequency = recurringFrequency
        )

        val txId = dao.insertTransaction(tx)
        dao.insertAuditEntry(LedgerAuditEntry(transactionId = txId, eventType = "CREATED", payloadSnapshot = "Amount: $amount, Nature: ${nature.name}", signatureHash = "SYS_AUTH_${System.currentTimeMillis()}"))

        val targetNameStr = tgtPocket?.let { " to ${it.name}" } ?: ""
        VaultExecutionResult.Success("Logged ₹${amount.toInt()} ${nature.name} from ${srcPocket.name}$targetNameStr")
    }

    suspend fun computeSolvencyDeck(): SolvencyMetricDeck = withContext(Dispatchers.IO) {
        val pockets = dao.getAllActivePocketsSnapshot()
        val balances = pockets.associate { it.id to dao.computePocketBalance(it.id) }

        val liquidBanks = pockets.filter { it.type == PocketType.LIQUID || it.type == PocketType.PREPAID_WALLET }
        val creditCards = pockets.filter { it.type == PocketType.CREDIT_CARD }

        val totalLiquid = liquidBanks.sumOf { balances[it.id]?.coerceAtLeast(0.0) ?: 0.0 }
        val totalCardDues = creditCards.sumOf { abs(balances[it.id]?.coerceAtMost(0.0) ?: 0.0) }

        val trueSafeLiquid = (totalLiquid - totalCardDues).coerceAtLeast(0.0)

        // FIX: Strict type-based asset and liability calculation for Indian accounting principles
        val assetTypes = listOf(PocketType.LIQUID, PocketType.PREPAID_WALLET, PocketType.INVESTMENT, PocketType.FIXED_ASSET, PocketType.GOAL_POT, PocketType.PEER_RECEIVABLE)
        val liabilityTypes = listOf(PocketType.CREDIT_CARD, PocketType.LIABILITY_LOAN, PocketType.PEER_PAYABLE)

        val grossAssets = pockets.filter { it.type in assetTypes }.sumOf { balances[it.id] ?: 0.0 }
        val grossLiabilities = pockets.filter { it.type in liabilityTypes }.sumOf { abs(balances[it.id] ?: 0.0) }
        val netWorth = grossAssets - grossLiabilities

        val dailyBurnCeiling = prefs.getFloat("daily_burn_ceiling", 500f).toDouble()
        val runwayDays = if (dailyBurnCeiling > 0) (trueSafeLiquid / dailyBurnCeiling).toLong() else 0L

        SolvencyMetricDeck(
            trueSafeLiquid = trueSafeLiquid,
            projectedClosingLiquid = totalLiquid, // Simplification for now
            totalNetWorth = netWorth,
            dailyBurnCeiling = dailyBurnCeiling,
            burnStatus = BurnPacingStatus.ON_TRACK,
            runwayDays = runwayDays,
            monthlyBudgetConsumed = 0.0,
            hasEarlyDeficitAlert = totalLiquid < totalCardDues
        )
    }

    suspend fun catchUpRecurringRules(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val templates = dao.observeHistoricalTransactions(now).firstOrNull()?.filter { it.isRecurring && it.recurringFrequency != "NONE" } ?: return@withContext 0
        
        var executedCount = 0
        val newTxs = mutableListOf<LedgerTransaction>()

        for (template in templates) {
            var nextExecution = template.timestamp
            while (nextExecution <= now) {
                val cal = Calendar.getInstance().apply { timeInMillis = nextExecution }
                when (template.recurringFrequency) {
                    "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                    "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                    "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                    "YEARLY" -> cal.add(Calendar.YEAR, 1)
                    else -> break
                }
                nextExecution = cal.timeInMillis

                if (nextExecution <= now) {
                    newTxs.add(template.copy(id = 0, timestamp = nextExecution, isRecurring = false, recurringFrequency = "NONE"))
                    executedCount++
                }
            }
            if (nextExecution > template.timestamp) {
                dao.updateTransaction(template.copy(timestamp = nextExecution))
            }
        }

        if (newTxs.isNotEmpty()) {
            dao.insertTransactionsBatch(newTxs)
        }

        executedCount
    }
}
