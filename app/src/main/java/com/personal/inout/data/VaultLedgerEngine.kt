package com.personal.inout.data

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
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
        movementNature: MovementNature,
        sourcePocketId: Long,
        targetPocketId: Long? = null,
        amount: Double,
        category: String,
        description: String,
        timestamp: Long = System.currentTimeMillis(),
        status: SettlementStatus = SettlementStatus.CLEARED,
        autoSplitEnabled: Boolean = false,
        isTaxDeductible: Boolean = false,
        isReimbursable: Boolean = false,
        isSubscription: Boolean = false,
        isRecurring: Boolean = false,
        recurringFrequency: String = "NONE"
    ): VaultExecutionResult = withContext(Dispatchers.IO) {
        
        if (amount <= 0) return@withContext VaultExecutionResult.OverdraftError("Transaction amount must be strictly positive.")

        val srcPocket = dao.getPocketById(sourcePocketId) ?: return@withContext VaultExecutionResult.OverdraftError("Source account missing.")
        val tgtPocket = targetPocketId?.let { dao.getPocketById(it) }

        if (movementNature == MovementNature.OPERATING_EXPENSE || movementNature == MovementNature.TRANSFER || movementNature == MovementNature.EMI_PRINCIPAL) {
            val availableBal = dao.computePocketBalance(sourcePocketId)
            
            // FIX: Exempt Peer Accounts from overdraft so you can borrow money into a 0 balance account
            val isExemptFromOverdraft = srcPocket.type in listOf(PocketType.CREDIT_CARD, PocketType.LIABILITY_LOAN, PocketType.PEER_PAYABLE, PocketType.PEER_RECEIVABLE)

            if (availableBal < amount && !isExemptFromOverdraft) {
                if (autoSplitEnabled && movementNature == MovementNature.OPERATING_EXPENSE) {
                    val shortfall = amount - availableBal
                    val secondaryAccounts = dao.getAllActivePocketsSnapshot()
                        .filter { it.type == PocketType.LIQUID && it.id != sourcePocketId }
                    
                    var remainingShortfall = shortfall
                    val splitTxList = mutableListOf<LedgerTransaction>()

                    if (availableBal > 0) {
                        splitTxList.add(LedgerTransaction(amount = availableBal, movementNature = movementNature, sourcePocketId = sourcePocketId, category = category, description = "$description (Split)", timestamp = timestamp, status = status, isTaxDeductible = isTaxDeductible, isReimbursable = isReimbursable, isSubscription = isSubscription, isRecurring = isRecurring, recurringFrequency = recurringFrequency))
                    }

                    for (sec in secondaryAccounts) {
                        if (remainingShortfall <= 0) break
                        val secBal = dao.computePocketBalance(sec.id)
                        if (secBal > 0) {
                            val draw = minOf(secBal, remainingShortfall)
                            splitTxList.add(LedgerTransaction(amount = draw, movementNature = movementNature, sourcePocketId = sec.id, category = category, description = "$description (Auto-Split from ${sec.name})", timestamp = timestamp, status = status, isTaxDeductible = isTaxDeductible, isReimbursable = isReimbursable, isSubscription = isSubscription, isRecurring = isRecurring, recurringFrequency = recurringFrequency))
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

        val duplicateCheck = dao.findPotentialDuplicate(sourcePocketId, amount, timestamp)
        if (duplicateCheck != null && !isRecurring) {
            return@withContext VaultExecutionResult.DuplicateWarning("Identical transaction detected within 48 hours. Proceed manually if intentional.")
        }

        val tx = LedgerTransaction(
            amount = amount,
            movementNature = movementNature,
            sourcePocketId = sourcePocketId,
            targetPocketId = targetPocketId,
            category = category,
            description = description,
            timestamp = timestamp,
            status = status,
            isTaxDeductible = isTaxDeductible,
            isReimbursable = isReimbursable,
            isSubscription = isSubscription,
            isRecurring = isRecurring,
            recurringFrequency = recurringFrequency
        )

        val txId = dao.insertTransaction(tx)
        
        dao.insertAuditEntry(LedgerAuditEntry(
            actionType = "INSERT",
            entityType = "TRANSACTION",
            recordId = txId,
            preStateJson = "{}",
            postStateJson = "{\"amount\": $amount, \"nature\": \"${movementNature.name}\"}",
            reasonNote = "System generated entry"
        ))

        val targetNameStr = tgtPocket?.let { " to ${it.name}" } ?: ""
        VaultExecutionResult.Success("Logged ₹${amount.toInt()} ${movementNature.name} from ${srcPocket.name}$targetNameStr")
    }

    suspend fun computeSolvencyDeck(): SolvencyMetricDeck = withContext(Dispatchers.IO) {
        val pockets = dao.getAllActivePocketsSnapshot()
        val balances = pockets.associate { it.id to dao.computePocketBalance(it.id) }

        val liquidBanks = pockets.filter { it.type == PocketType.LIQUID || it.type == PocketType.PREPAID_WALLET }
        val creditCards = pockets.filter { it.type == PocketType.CREDIT_CARD }

        val totalLiquid = liquidBanks.sumOf { balances[it.id]?.coerceAtLeast(0.0) ?: 0.0 }
        val totalCardDues = creditCards.sumOf { abs(balances[it.id]?.coerceAtMost(0.0) ?: 0.0) }

        val trueSafeLiquid = (totalLiquid - totalCardDues).coerceAtLeast(0.0)

        val assetTypes = listOf(PocketType.LIQUID, PocketType.PREPAID_WALLET, PocketType.INVESTMENT, PocketType.FIXED_ASSET, PocketType.GOAL_POT, PocketType.PEER_RECEIVABLE)
        val liabilityTypes = listOf(PocketType.CREDIT_CARD, PocketType.LIABILITY_LOAN, PocketType.PEER_PAYABLE)

        val grossAssets = pockets.filter { it.type in assetTypes }.sumOf { balances[it.id] ?: 0.0 }
        val grossLiabilities = pockets.filter { it.type in liabilityTypes }.sumOf { abs(balances[it.id] ?: 0.0) }
        val netWorth = grossAssets - grossLiabilities

        val dailyBurnCeiling = prefs.getFloat("daily_burn_ceiling", 500f).toDouble()
        val runwayDays = if (dailyBurnCeiling > 0) (trueSafeLiquid / dailyBurnCeiling).toLong() else 0L

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val txs = dao.observeHistoricalTransactions().firstOrNull() ?: emptyList()
        val todayBurn = txs.filter { it.timestamp >= todayStart && it.movementNature == MovementNature.OPERATING_EXPENSE }.sumOf { it.amount }

        SolvencyMetricDeck(
            trueSafeLiquid = trueSafeLiquid,
            projectedClosingLiquid = totalLiquid, 
            totalNetWorth = netWorth,
            dailyBurnRate = todayBurn,
            dailyBurnCeiling = dailyBurnCeiling,
            burnStatus = if (todayBurn > dailyBurnCeiling) BurnPacingStatus.SPIKED else BurnPacingStatus.ON_TRACK,
            runwayDays = runwayDays,
            hasEarlyDeficitAlert = totalLiquid < totalCardDues
        )
    }

    suspend fun generateIndianStatements(fromEpoch: Long, toEpoch: Long): Pair<IndianBalanceSheetReport, IndianPnLStatement> = withContext(Dispatchers.IO) {
        val pockets = dao.getAllActivePocketsSnapshot()
        val balances = pockets.associate { it.id to dao.computePocketBalance(it.id) }
        val txs = dao.getTransactionsBetween(fromEpoch, toEpoch)

        val assetTypes = listOf(PocketType.LIQUID, PocketType.PREPAID_WALLET, PocketType.INVESTMENT, PocketType.FIXED_ASSET, PocketType.GOAL_POT, PocketType.PEER_RECEIVABLE)
        val liabilityTypes = listOf(PocketType.CREDIT_CARD, PocketType.LIABILITY_LOAN, PocketType.PEER_PAYABLE)

        val grossAssets = pockets.filter { it.type in assetTypes }.sumOf { balances[it.id] ?: 0.0 }
        val grossLiabilities = pockets.filter { it.type in liabilityTypes }.sumOf { abs(balances[it.id] ?: 0.0) }
        val netWorth = grossAssets - grossLiabilities

        val securedLoans = pockets.filter { it.type == PocketType.LIABILITY_LOAN }.sumOf { abs(balances[it.id] ?: 0.0) }
        val unsecuredLoans = 0.0 
        val sundryCreditorsAndCardDues = pockets.filter { it.type == PocketType.CREDIT_CARD || it.type == PocketType.PEER_PAYABLE }.sumOf { abs(balances[it.id] ?: 0.0) }
        val totalLiabilitiesAndCapital = netWorth + securedLoans + unsecuredLoans + sundryCreditorsAndCardDues

        val fixedCapitalAssetsWDV = pockets.filter { it.type == PocketType.FIXED_ASSET }.sumOf { balances[it.id] ?: 0.0 }
        val investmentsPortfolio = pockets.filter { it.type == PocketType.INVESTMENT || it.type == PocketType.GOAL_POT }.sumOf { balances[it.id] ?: 0.0 }
        val sundryDebtorsReceivable = pockets.filter { it.type == PocketType.PEER_RECEIVABLE }.sumOf { balances[it.id] ?: 0.0 }
        val bankAndPrepaidBalances = pockets.filter { it.type == PocketType.LIQUID || it.type == PocketType.PREPAID_WALLET }.sumOf { balances[it.id] ?: 0.0 }
        val cashInHand = pockets.filter { it.name.contains("Cash", ignoreCase = true) }.sumOf { balances[it.id] ?: 0.0 }
        
        val bs = IndianBalanceSheetReport(
            asOfDateEpoch = System.currentTimeMillis(),
            proprietorCapitalAccount = netWorth,
            securedLoans = securedLoans,
            unsecuredLoans = unsecuredLoans,
            sundryCreditorsAndCardDues = sundryCreditorsAndCardDues,
            totalLiabilitiesAndCapital = totalLiabilitiesAndCapital,
            fixedCapitalAssetsWDV = fixedCapitalAssetsWDV,
            investmentsPortfolio = investmentsPortfolio,
            sundryDebtorsReceivable = sundryDebtorsReceivable,
            bankAndPrepaidBalances = bankAndPrepaidBalances,
            cashInHand = cashInHand,
            totalAssets = grossAssets,
            isBalanced = true
        )

        val grossInflows = txs.filter { it.movementNature == MovementNature.OPERATING_INCOME }.sumOf { it.amount }
        val operationalLivingExpenses = txs.filter { it.movementNature == MovementNature.OPERATING_EXPENSE }.sumOf { it.amount }
        val financeAndLoanCharges = txs.filter { it.movementNature == MovementNature.EMI_PRINCIPAL }.sumOf { it.amount } 
        val depreciationWrittenOff = txs.filter { it.movementNature == MovementNature.DEPRECIATION_WRITE }.sumOf { it.amount }
        val netSurplusSavings = grossInflows - operationalLivingExpenses - financeAndLoanCharges - depreciationWrittenOff
        
        val taxDeductibleSummary = txs.filter { it.isTaxDeductible }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val pnl = IndianPnLStatement(
            fromEpoch = fromEpoch,
            toEpoch = toEpoch,
            grossInflows = grossInflows,
            operationalLivingExpenses = operationalLivingExpenses,
            financeAndLoanCharges = financeAndLoanCharges,
            depreciationWrittenOff = depreciationWrittenOff,
            netSurplusSavings = netSurplusSavings,
            taxDeductibleSummary = taxDeductibleSummary
        )

        Pair(bs, pnl)
    }

    suspend fun catchUpRecurringRules(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val templates = dao.observeHistoricalTransactions(now).firstOrNull()?.filter { it.isRecurring && it.recurringFrequency != "NONE" } ?: return@withContext 0
        
        var executedCount = 0
        val newTxs = mutableListOf<LedgerTransaction>()

        for (template in templates) {
            var executionTime = template.timestamp
            
            // FIX: Write the transaction for the CURRENT cycle before advancing the clock
            while (executionTime <= now) {
                newTxs.add(template.copy(id = 0, timestamp = executionTime, isRecurring = false, recurringFrequency = "NONE"))
                executedCount++
                
                val cal = Calendar.getInstance().apply { timeInMillis = executionTime }
                when (template.recurringFrequency) {
                    "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                    "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                    "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                    "YEARLY" -> cal.add(Calendar.YEAR, 1)
                    else -> break
                }
                executionTime = cal.timeInMillis
            }
            
            // Save the next future date back to the template
            if (executionTime > template.timestamp) {
                dao.updateTransaction(template.copy(timestamp = executionTime))
            }
        }

        if (newTxs.isNotEmpty()) {
            dao.insertTransactionsBatch(newTxs)
        }

        executedCount
    }
}
