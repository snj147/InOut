package com.personal.inout.data

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
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
        amount: Long, // STRICTLY PAISE
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
        
        if (amount <= 0L) return@withContext VaultExecutionResult.OverdraftError("Transaction amount must be strictly positive.")

        val srcPocket = dao.getPocketById(sourcePocketId) ?: return@withContext VaultExecutionResult.OverdraftError("Source account missing.")
        val tgtPocket = targetPocketId?.let { dao.getPocketById(it) }

        if (movementNature == MovementNature.OPERATING_EXPENSE || movementNature == MovementNature.TRANSFER || movementNature == MovementNature.EMI_PRINCIPAL) {
            val availableBal = dao.computePocketBalance(sourcePocketId)
            
            // Exempt Peer Accounts & Cards from overdraft blocks so you can borrow money
            val isExemptFromOverdraft = srcPocket.type in listOf(PocketType.CREDIT_CARD, PocketType.LIABILITY_LOAN, PocketType.PEER_PAYABLE, PocketType.PEER_RECEIVABLE)

            if (availableBal < amount && !isExemptFromOverdraft) {
                if (autoSplitEnabled && movementNature == MovementNature.OPERATING_EXPENSE) {
                    val shortfall = amount - availableBal
                    val secondaryAccounts = dao.getAllActivePocketsSnapshot()
                        .filter { it.type == PocketType.LIQUID && it.id != sourcePocketId }
                    
                    var remainingShortfall = shortfall
                    val splitTxList = mutableListOf<LedgerTransaction>()

                    if (availableBal > 0L) {
                        splitTxList.add(LedgerTransaction(amount = availableBal, movementNature = movementNature, sourcePocketId = sourcePocketId, category = category, description = "$description (Split)", timestamp = timestamp, status = status, isTaxDeductible = isTaxDeductible, isReimbursable = isReimbursable, isSubscription = isSubscription, isRecurring = isRecurring, recurringFrequency = recurringFrequency))
                    }

                    for (sec in secondaryAccounts) {
                        if (remainingShortfall <= 0L) break
                        val secBal = dao.computePocketBalance(sec.id)
                        if (secBal > 0L) {
                            val draw = minOf(secBal, remainingShortfall)
                            splitTxList.add(LedgerTransaction(amount = draw, movementNature = movementNature, sourcePocketId = sec.id, category = category, description = "$description (Auto-Split from ${sec.name})", timestamp = timestamp, status = status, isTaxDeductible = isTaxDeductible, isReimbursable = isReimbursable, isSubscription = isSubscription, isRecurring = isRecurring, recurringFrequency = recurringFrequency))
                            remainingShortfall -= draw
                        }
                    }

                    if (remainingShortfall > 0L) {
                        return@withContext VaultExecutionResult.OverdraftError("Insufficient combined liquid funds. Shortfall: ₹${remainingShortfall / 100.0}")
                    }

                    dao.insertTransactionsBatch(splitTxList)
                    return@withContext VaultExecutionResult.Success("Auto-split across accounts successful.")
                } else {
                    return@withContext VaultExecutionResult.OverdraftError("Insufficient funds in ${srcPocket.name}. Available: ₹${availableBal / 100.0}")
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
        VaultExecutionResult.Success("Logged ₹${amount / 100.0} ${movementNature.name} from ${srcPocket.name}$targetNameStr")
    }

    suspend fun computeSolvencyDeck(): SolvencyMetricDeck = withContext(Dispatchers.IO) {
        val pockets = dao.getAllActivePocketsSnapshot()
        val balances = pockets.associate { it.id to dao.computePocketBalance(it.id) }

        var grossAssets = 0L
        var grossLiabilities = 0L
        var totalLiquid = 0L
        var totalCardDues = 0L

        pockets.forEach { pocket ->
            val bal = balances[pocket.id] ?: 0L
            
            if (pocket.type == PocketType.LIQUID || pocket.type == PocketType.PREPAID_WALLET) {
                if (bal > 0L) totalLiquid += bal
            }

            val isCard = pocket.type == PocketType.CREDIT_CARD
            val isPayable = pocket.type == PocketType.PEER_PAYABLE
            val isReceivable = pocket.type == PocketType.PEER_RECEIVABLE
            val isLoan = pocket.type == PocketType.LIABILITY_LOAN

            // DYNAMIC ZERO-CROSSING EVALUATION
            if (isCard || isLoan || isPayable) {
                if (bal >= 0L && (isCard || isPayable)) {
                    grossAssets += bal // Overpaid card/creditor is an Asset
                } else {
                    grossLiabilities += abs(bal)
                    if (isCard) totalCardDues += abs(bal)
                }
            } else {
                if (bal < 0L && isReceivable) {
                    grossLiabilities += abs(bal) // Negative receivable is a Liability
                } else {
                    grossAssets += bal
                }
            }
        }

        val trueSafeLiquid = (totalLiquid - totalCardDues).coerceAtLeast(0L)
        val netWorth = grossAssets - grossLiabilities

        val dailyBurnCeilingDouble = prefs.getFloat("daily_burn_ceiling", 500f).toDouble()
        val dailyBurnCeilingPaise = (dailyBurnCeilingDouble * 100).toLong()
        
        val runwayDays = if (dailyBurnCeilingPaise > 0L) (trueSafeLiquid / dailyBurnCeilingPaise) else 0L

        // TIMEZONE FIX: ZonedDateTime ensures midnight aligns with local Indian time properly
        val todayStart = ZonedDateTime.now(ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        val txs = dao.observeHistoricalTransactions(Long.MAX_VALUE).firstOrNull() ?: emptyList()
        val todayBurn = txs.filter { it.timestamp >= todayStart && it.movementNature == MovementNature.OPERATING_EXPENSE }.sumOf { it.amount }

        SolvencyMetricDeck(
            trueSafeLiquid = trueSafeLiquid,
            projectedClosingLiquid = totalLiquid, 
            totalNetWorth = netWorth,
            dailyBurnRate = todayBurn,
            dailyBurnCeiling = dailyBurnCeilingPaise,
            burnStatus = if (todayBurn > dailyBurnCeilingPaise) BurnPacingStatus.SPIKED else BurnPacingStatus.ON_TRACK,
            runwayDays = runwayDays,
            hasEarlyDeficitAlert = totalLiquid < totalCardDues
        )
    }

    suspend fun generateFinancialStatements(fromEpoch: Long, toEpoch: Long): Pair<FinancialBalanceSheetReport, FinancialPnLStatement> = withContext(Dispatchers.IO) {
        val pockets = dao.getAllActivePocketsSnapshot()
        val balances = pockets.associate { it.id to dao.computePocketBalance(it.id) }
        val txs = dao.getTransactionsBetween(fromEpoch, toEpoch)

        var grossAssets = 0L
        var grossLiabilities = 0L

        var securedLoans = 0L
        var unsecuredLoans = 0L
        var sundryCreditorsAndCardDues = 0L

        var fixedCapitalAssetsWDV = 0L
        var investmentsPortfolio = 0L
        var sundryDebtorsReceivable = 0L
        var bankAndPrepaidBalances = 0L
        var cashInHand = 0L

        pockets.forEach { pocket ->
            val bal = balances[pocket.id] ?: 0L
            val isCard = pocket.type == PocketType.CREDIT_CARD
            val isPayable = pocket.type == PocketType.PEER_PAYABLE
            val isReceivable = pocket.type == PocketType.PEER_RECEIVABLE
            val isLoan = pocket.type == PocketType.LIABILITY_LOAN

            if (isCard || isLoan || isPayable) {
                if (bal >= 0L && (isCard || isPayable)) {
                    grossAssets += bal
                    sundryDebtorsReceivable += bal // Overpaid mapped to receivables
                } else {
                    val absBal = abs(bal)
                    grossLiabilities += absBal
                    if (isLoan) securedLoans += absBal
                    if (isCard || isPayable) sundryCreditorsAndCardDues += absBal
                }
            } else {
                if (bal < 0L && isReceivable) {
                    val absBal = abs(bal)
                    grossLiabilities += absBal
                    sundryCreditorsAndCardDues += absBal
                } else {
                    grossAssets += bal
                    when (pocket.type) {
                        PocketType.FIXED_ASSET -> fixedCapitalAssetsWDV += bal
                        PocketType.INVESTMENT, PocketType.GOAL_POT -> investmentsPortfolio += bal
                        PocketType.PEER_RECEIVABLE -> sundryDebtorsReceivable += bal
                        PocketType.LIQUID, PocketType.PREPAID_WALLET -> {
                            if (pocket.name.contains("Cash", ignoreCase = true)) cashInHand += bal
                            else bankAndPrepaidBalances += bal
                        }
                        else -> {}
                    }
                }
            }
        }

        val netWorth = grossAssets - grossLiabilities
        val totalLiabilitiesAndCapital = netWorth + securedLoans + unsecuredLoans + sundryCreditorsAndCardDues

        val bs = FinancialBalanceSheetReport(
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

        val pnl = FinancialPnLStatement(
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
        val allTxs = dao.observeHistoricalTransactions(Long.MAX_VALUE).firstOrNull() ?: emptyList()
        val activeTemplates = allTxs.filter { it.isRecurring && it.recurringFrequency != "NONE" }
        
        var executedCount = 0

        for (template in activeTemplates) {
            if (template.timestamp <= now) {
                var currentExecutionTime = template.timestamp
                
                // Convert current instance into a normal entry
                dao.updateTransaction(template.copy(isRecurring = false, recurringFrequency = "NONE"))
                
                // TIMEZONE FIX: ZonedDateTime prevents drift across leap years and DST
                while (true) {
                    var executionTimeZdt = Instant.ofEpochMilli(currentExecutionTime).atZone(ZoneId.systemDefault())
                    
                    executionTimeZdt = when (template.recurringFrequency) {
                        "DAILY" -> executionTimeZdt.plusDays(1)
                        "WEEKLY" -> executionTimeZdt.plusWeeks(1)
                        "MONTHLY" -> executionTimeZdt.plusMonths(1)
                        "YEARLY" -> executionTimeZdt.plusYears(1)
                        else -> break
                    }
                    val nextExecutionTime = executionTimeZdt.toInstant().toEpochMilli()
                    
                    if (nextExecutionTime <= now) {
                        // Spawning missed executions
                        dao.insertTransaction(template.copy(id = 0, timestamp = nextExecutionTime, isRecurring = false, recurringFrequency = "NONE"))
                        executedCount++
                        currentExecutionTime = nextExecutionTime
                    } else {
                        // Spawning the next future template
                        dao.insertTransaction(template.copy(id = 0, timestamp = nextExecutionTime, isRecurring = true, recurringFrequency = template.recurringFrequency))
                        break
                    }
                }
            }
        }
        executedCount
    }
}
