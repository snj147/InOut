package com.personal.inout.data

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

sealed class VaultExecutionResult {
    data class Success(val summary: String, val transactionId: Long) : VaultExecutionResult()
    data class OverdraftError(val message: String) : VaultExecutionResult()
    data class DuplicateWarning(val existingTx: LedgerTransaction, val message: String) : VaultExecutionResult()
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
        category: String = "General",
        description: String = "",
        timestamp: Long = System.currentTimeMillis(),
        status: SettlementStatus = SettlementStatus.CLEARED,
        autoSplitEnabled: Boolean = false,
        isTaxDeductible: Boolean = false,
        isReimbursable: Boolean = false,
        isSubscription: Boolean = false,
        isRecurring: Boolean = false,
        recurringFrequency: String = "NONE",
        originalCurrency: String = "INR",
        foreignAmount: Double = 0.0,
        bypassDuplicateCheck: Boolean = false
    ): VaultExecutionResult = withContext(Dispatchers.IO) {
        if (amount <= 0.0) {
            return@withContext VaultExecutionResult.OverdraftError("Amount must be strictly greater than zero.")
        }

        val sourcePocket = dao.getPocketById(sourcePocketId)
            ?: return@withContext VaultExecutionResult.OverdraftError("Source account not found. Please select an active account.")

        // Enforce credit card inflow rules: only allow specific categories and disallow overcrediting
        if (movementNature == MovementNature.OPERATING_INCOME && sourcePocket.type == PocketType.CREDIT_CARD) {
            val validCardInflowCategories = listOf("Cashback", "Reward Credit", "Merchant Refund")
            if (!validCardInflowCategories.any { it.equals(category, ignoreCase = true) }) {
                return@withContext VaultExecutionResult.OverdraftError(
                    "Credit Card inflows are restricted to Cashback, Reward Credit, or Merchant Refund only."
                )
            }
            if (isRecurring && recurringFrequency != "NONE") {
                return@withContext VaultExecutionResult.OverdraftError("Credit card refunds and rewards cannot be scheduled as recurring.")
            }
            val currentBalance = dao.computePocketBalance(sourcePocket.id, System.currentTimeMillis())
            val currentOutstandingDue = if (currentBalance < 0.0) abs(currentBalance) else 0.0
            if (currentOutstandingDue <= 0.0) {
                return@withContext VaultExecutionResult.OverdraftError("Card has no outstanding debt. Inflow rejected.")
            }
            if (amount > currentOutstandingDue) {
                return@withContext VaultExecutionResult.OverdraftError(
                    "Inflow of ₹${String.format("%,.0f", amount)} exceeds current outstanding debt of ₹${String.format("%,.0f", currentOutstandingDue)}."
                )
            }
        }

        if (movementNature == MovementNature.TRANSFER) {
            if (targetPocketId == null || targetPocketId == sourcePocketId) {
                return@withContext VaultExecutionResult.OverdraftError("Transfer requires distinct source and destination accounts.")
            }
            dao.getPocketById(targetPocketId)
                ?: return@withContext VaultExecutionResult.OverdraftError("Destination account not found.")
        }

        if (!bypassDuplicateCheck) {
            val duplicate = dao.findPotentialDuplicate(sourcePocketId, amount, timestamp)
            if (duplicate != null) {
                return@withContext VaultExecutionResult.DuplicateWarning(
                    existingTx = duplicate,
                    message = "Duplicate Detected: An identical entry of ₹${String.format("%,.0f", amount)} exists on ${sourcePocket.name} around this date."
                )
            }
        }

        val currentSourceBalance = dao.computePocketBalance(sourcePocketId, System.currentTimeMillis())

        if (movementNature in listOf(MovementNature.OPERATING_EXPENSE, MovementNature.TRANSFER)) {
            if (sourcePocket.type in listOf(PocketType.LIQUID, PocketType.PREPAID_WALLET)) {
                if (currentSourceBalance < amount) {
                    if (autoSplitEnabled) {
                        return@withContext executeAutoSplitPayment(
                            primaryPocket = sourcePocket,
                            primaryAvailable = currentSourceBalance.coerceAtLeast(0.0),
                            totalAmount = amount,
                            targetPocketId = targetPocketId,
                            movementNature = movementNature,
                            category = category,
                            description = description,
                            timestamp = timestamp,
                            isTaxDeductible = isTaxDeductible,
                            isReimbursable = isReimbursable
                        )
                    } else {
                        return@withContext VaultExecutionResult.OverdraftError(
                            "Insufficient funds in ${sourcePocket.name} (Available: ₹${String.format("%,.2f", currentSourceBalance)}). Outflow of ₹${String.format("%,.2f", amount)} blocked."
                        )
                    }
                }
            } else if (sourcePocket.type == PocketType.CREDIT_CARD) {
                val outstandingDues = abs(currentSourceBalance)
                val remainingCredit = sourcePocket.creditLimit - outstandingDues
                if (sourcePocket.creditLimit > 0.0 && amount > remainingCredit) {
                    return@withContext VaultExecutionResult.OverdraftError(
                        "Charge of ₹${String.format("%,.0f", amount)} exceeds available limit on ${sourcePocket.name} (Available: ₹${String.format("%,.0f", remainingCredit)})."
                    )
                }
            }
        }

        val tx = LedgerTransaction(
            timestamp = timestamp,
            amount = amount,
            description = description.ifBlank { category },
            category = category,
            movementNature = movementNature,
            status = status,
            sourcePocketId = sourcePocketId,
            targetPocketId = targetPocketId,
            isTaxDeductible = isTaxDeductible,
            isReimbursable = isReimbursable,
            isSubscription = isSubscription,
            isRecurring = isRecurring,
            recurringFrequency = recurringFrequency,
            originalCurrency = originalCurrency,
            foreignAmount = foreignAmount
        )

        val txId = dao.insertTransaction(tx)

        dao.insertAuditEntry(
            LedgerAuditEntry(
                actionType = "INSERT",
                entityType = "TRANSACTION",
                recordId = txId,
                preStateJson = "{}",
                postStateJson = "{\"amount\":$amount,\"nature\":\"$movementNature\",\"pocketId\":$sourcePocketId}",
                reasonNote = "Executed movement"
            )
        )

        val summary = when (movementNature) {
            MovementNature.OPERATING_EXPENSE -> "Logged expense of ₹${String.format("%,.0f", amount)} ($category)"
            MovementNature.OPERATING_INCOME -> "Credited ₹${String.format("%,.0f", amount)} to ${sourcePocket.name}"
            MovementNature.TRANSFER -> "Transferred ₹${String.format("%,.0f", amount)} to destination"
            MovementNature.OPENING_BASELINE -> "Registered baseline of ₹${String.format("%,.0f", amount)}"
            MovementNature.VALUATION_MARK -> "Portfolio valuation adjusted by ₹${String.format("%,.0f", amount)}"
            MovementNature.DEPRECIATION_WRITE -> "Depreciation write-down of ₹${String.format("%,.0f", amount)} logged"
            MovementNature.EMI_PRINCIPAL -> "EMI principal paid: ₹${String.format("%,.0f", amount)}"
            MovementNature.RECONCILIATION_ADJ -> "Balance reconciliation adjustment: ₹${String.format("%,.0f", amount)}"
        }

        VaultExecutionResult.Success(summary, txId)
    }

    private suspend fun executeAutoSplitPayment(
        primaryPocket: LedgerPocket,
        primaryAvailable: Double,
        totalAmount: Double,
        targetPocketId: Long?,
        movementNature: MovementNature,
        category: String,
        description: String,
        timestamp: Long,
        isTaxDeductible: Boolean,
        isReimbursable: Boolean
    ): VaultExecutionResult {
        val shortfall = totalAmount - primaryAvailable
        val activePockets = dao.getAllActivePocketsSnapshot()
        
        val candidateSecondary = activePockets
            .filter { it.id != primaryPocket.id && it.type in listOf(PocketType.LIQUID, PocketType.PREPAID_WALLET) }
            .map { pocket -> Pair(pocket, dao.computePocketBalance(pocket.id)) }
            .filter { it.second >= shortfall }
            .maxByOrNull { it.second }

        if (candidateSecondary == null) {
            return VaultExecutionResult.OverdraftError(
                "Insufficient funds across all liquid accounts. Primary ${primaryPocket.name} short by ₹${String.format("%,.0f", shortfall)}."
            )
        }

        val secondaryPocket = candidateSecondary.first

        if (primaryAvailable > 0.0) {
            val primaryTx = LedgerTransaction(
                timestamp = timestamp,
                amount = primaryAvailable,
                description = "${description.ifBlank { category }} [Split 1/2 Primary]",
                category = category,
                movementNature = movementNature,
                sourcePocketId = primaryPocket.id,
                targetPocketId = targetPocketId,
                isTaxDeductible = isTaxDeductible,
                isReimbursable = isReimbursable
            )
            dao.insertTransaction(primaryTx)
        }

        val secondaryTx = LedgerTransaction(
            timestamp = timestamp,
            amount = shortfall,
            description = "${description.ifBlank { category }} [Split 2/2 Auto-Debit from ${secondaryPocket.name}]",
            category = category,
            movementNature = movementNature,
            sourcePocketId = secondaryPocket.id,
            targetPocketId = targetPocketId,
            isTaxDeductible = isTaxDeductible,
            isReimbursable = isReimbursable
        )
        val secondTxId = dao.insertTransaction(secondaryTx)

        return VaultExecutionResult.Success(
            "Auto-split executed: ₹${String.format("%,.0f", primaryAvailable)} from ${primaryPocket.name} & ₹${String.format("%,.0f", shortfall)} from ${secondaryPocket.name}",
            secondTxId
        )
    }

    suspend fun computeSolvencyDeck(): SolvencyMetricDeck = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val pockets = dao.getAllActivePocketsSnapshot()
        val totalTxCount = dao.getTransactionCount()
        
        var sumLiquid = 0.0
        var sumCardDues = 0.0
        var sumGoalPots = 0.0
        var totalAssets = 0.0
        var totalLiabilities = 0.0

        for (pocket in pockets) {
            val balance = dao.computePocketBalance(pocket.id, now)
            when (pocket.type) {
                PocketType.LIQUID, PocketType.PREPAID_WALLET -> {
                    sumLiquid += max(0.0, balance)
                    totalAssets += balance
                }
                PocketType.CREDIT_CARD -> {
                    if (balance < 0.0) {
                        val debt = abs(balance)
                        sumCardDues += debt
                        totalLiabilities += debt
                    }
                }
                PocketType.GOAL_POT -> {
                    val pot = max(0.0, balance)
                    sumGoalPots += pot
                    totalAssets += pot
                }
                PocketType.PEER_RECEIVABLE, PocketType.FIXED_ASSET, PocketType.INVESTMENT -> {
                    totalAssets += balance
                }
                PocketType.PEER_PAYABLE, PocketType.LIABILITY_LOAN -> {
                    totalLiabilities += abs(balance)
                }
            }
        }

        val trueSafeLiquid = max(0.0, sumLiquid - sumCardDues) - sumGoalPots
        val finalSafeLiquid = max(0.0, trueSafeLiquid)
        val netWorth = totalAssets - totalLiabilities

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calendar.timeInMillis
        val todaysTx = dao.getTransactionsBetween(startOfToday, now)
        val dailyBurnRate = todaysTx
            .filter { it.movementNature == MovementNature.OPERATING_EXPENSE }
            .sumOf { it.amount }

        val dailyBurnCeiling = prefs.getFloat("daily_burn_ceiling", 500f).toDouble()
        val burnStatus = if (dailyBurnRate > dailyBurnCeiling) BurnPacingStatus.SPIKED else BurnPacingStatus.ON_TRACK
        val runwayDays = if (dailyBurnCeiling > 0.0) (finalSafeLiquid / dailyBurnCeiling).toLong() else 0L

        val maxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val daysRemainingInMonth = max(0, maxDays - currentDay)

        val scheduledFuture = dao.getTransactionsBetween(now + 1, now + (30L * 24 * 3600 * 1000L))
        val expectedInflows = scheduledFuture
            .filter { it.movementNature == MovementNature.OPERATING_INCOME }
            .sumOf { it.amount }
        val knownFixedDues = scheduledFuture
            .filter { it.movementNature in listOf(MovementNature.OPERATING_EXPENSE, MovementNature.EMI_PRINCIPAL) }
            .sumOf { it.amount }

        val projectedClosingLiquid = if (totalTxCount == 0 && finalSafeLiquid == 0.0) {
            0.0
        } else {
            finalSafeLiquid + expectedInflows - knownFixedDues - (daysRemainingInMonth * dailyBurnCeiling)
        }

        val hasDeficit = if (totalTxCount == 0 && sumCardDues == 0.0) {
            false
        } else {
            projectedClosingLiquid < 0.0
        }

        SolvencyMetricDeck(
            trueSafeLiquid = finalSafeLiquid,
            totalNetWorth = netWorth,
            dailyBurnRate = dailyBurnRate,
            dailyBurnCeiling = dailyBurnCeiling,
            burnStatus = burnStatus,
            runwayDays = runwayDays,
            projectedClosingLiquid = projectedClosingLiquid,
            hasEarlyDeficitAlert = hasDeficit
        )
    }

    suspend fun catchUpRecurringRules(): Int = withContext(Dispatchers.IO) {
        val allTx = dao.getTransactionsBetween(0L, System.currentTimeMillis())
        val recurringTemplates = allTx.filter { it.isRecurring && it.recurringFrequency != "NONE" }
        var catchUpCount = 0
        val now = System.currentTimeMillis()

        for (template in recurringTemplates) {
            val interval = when (template.recurringFrequency) {
                "DAILY" -> 24 * 3600 * 1000L
                "WEEKLY" -> 7 * 24 * 3600 * 1000L
                "MONTHLY" -> 30 * 24 * 3600 * 1000L
                else -> Long.MAX_VALUE
            }

            val diff = now - template.timestamp
            if (diff >= interval) {
                recordMovement(
                    movementNature = template.movementNature,
                    sourcePocketId = template.sourcePocketId,
                    targetPocketId = template.targetPocketId,
                    amount = template.amount,
                    category = template.category,
                    description = template.description,
                    timestamp = now,
                    status = SettlementStatus.CLEARED,
                    isRecurring = false,
                    isTaxDeductible = template.isTaxDeductible,
                    isReimbursable = template.isReimbursable,
                    bypassDuplicateCheck = true
                )
                dao.updateTransaction(template.copy(timestamp = now))
                catchUpCount++
            }
        }
        catchUpCount
    }

    suspend fun generateIndianStatements(startEpoch: Long, endEpoch: Long): Pair<IndianBalanceSheetReport, IndianPnLStatement> = withContext(Dispatchers.IO) {
        val pockets = dao.getAllActivePocketsSnapshot()
        val txs = dao.getTransactionsBetween(startEpoch, endEpoch)

        var fixedAssets = 0.0
        var investments = 0.0
        var debtors = 0.0
        var bankBalances = 0.0
        var cashInHand = 0.0

        var securedLoans = 0.0
        var unsecuredLoans = 0.0
        var currentLiabilities = 0.0

        for (p in pockets) {
            val bal = dao.computePocketBalance(p.id, endEpoch)
            when (p.type) {
                PocketType.FIXED_ASSET -> fixedAssets += bal
                PocketType.INVESTMENT -> investments += bal
                PocketType.PEER_RECEIVABLE -> debtors += bal
                PocketType.LIQUID, PocketType.PREPAID_WALLET, PocketType.GOAL_POT -> {
                    if (p.name.equals("Cash in Hand", ignoreCase = true)) {
                        cashInHand += bal
                    } else {
                        bankBalances += bal
                    }
                }
                PocketType.PEER_PAYABLE -> unsecuredLoans += abs(bal)
                PocketType.LIABILITY_LOAN -> securedLoans += abs(bal)
                PocketType.CREDIT_CARD -> if (bal < 0.0) currentLiabilities += abs(bal)
            }
        }

        val totalAssets = fixedAssets + investments + debtors + bankBalances + cashInHand
        val totalExternalLiabilities = securedLoans + unsecuredLoans + currentLiabilities
        val capitalAccount = totalAssets - totalExternalLiabilities
        val totalLiabilitiesAndCapital = totalExternalLiabilities + capitalAccount

        val balanceSheet = IndianBalanceSheetReport(
            asOfDateEpoch = endEpoch,
            proprietorCapitalAccount = capitalAccount,
            securedLoans = securedLoans,
            unsecuredLoans = unsecuredLoans,
            sundryCreditorsAndCardDues = currentLiabilities,
            totalLiabilitiesAndCapital = totalLiabilitiesAndCapital,
            fixedCapitalAssetsWDV = fixedAssets,
            investmentsPortfolio = investments,
            sundryDebtorsReceivable = debtors,
            bankAndPrepaidBalances = bankBalances,
            cashInHand = cashInHand,
            totalAssets = totalAssets,
            isBalanced = abs(totalAssets - totalLiabilitiesAndCapital) < 0.01
        )

        val grossInflows = txs.filter { it.movementNature == MovementNature.OPERATING_INCOME }.sumOf { it.amount }
        val operatingLiving = txs.filter { it.movementNature == MovementNature.OPERATING_EXPENSE }.sumOf { it.amount }
        val financeCharges = txs.filter { it.category.contains("Interest", ignoreCase = true) || it.category.contains("Finance", ignoreCase = true) }.sumOf { it.amount }
        val depreciation = txs.filter { it.movementNature == MovementNature.DEPRECIATION_WRITE }.sumOf { it.amount }
        val netSurplus = grossInflows - (operatingLiving + financeCharges + depreciation)

        val taxDeductibles = txs.filter { it.isTaxDeductible }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val pnl = IndianPnLStatement(
            fromEpoch = startEpoch,
            toEpoch = endEpoch,
            grossInflows = grossInflows,
            operationalLivingExpenses = operatingLiving,
            financeAndLoanCharges = financeCharges,
            depreciationWrittenOff = depreciation,
            netSurplusSavings = netSurplus,
            taxDeductibleSummary = taxDeductibles
        )

        Pair(balanceSheet, pnl)
    }
}
