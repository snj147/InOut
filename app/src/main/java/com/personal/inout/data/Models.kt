package com.personal.inout.data

data class PocketBalanceSummary(
    val pocket: LedgerPocket,
    val currentBalance: Long,
    val encumberedCreditDues: Long = 0L,
    val availableCreditLimit: Long = 0L
)

data class SolvencyMetricDeck(
    val trueSafeLiquid: Long,              
    val totalNetWorth: Long,               
    val dailyBurnRate: Long,               
    val dailyBurnCeiling: Long,            
    val burnStatus: BurnPacingStatus,      
    val runwayDays: Long,                  
    val projectedClosingLiquid: Long,      
    val hasEarlyDeficitAlert: Boolean      
)

enum class BurnPacingStatus {
    ON_TRACK,
    SPIKED
}

data class TransactionDetailItem(
    val transaction: LedgerTransaction,
    val sourcePocketName: String,
    val targetPocketName: String? = null
)

data class HabitPillPreset(
    val label: String,
    val category: String,
    val defaultPocketId: Long,
    val defaultMovementNature: MovementNature = MovementNature.OPERATING_EXPENSE
)

data class FinancialBalanceSheetReport(
    val asOfDateEpoch: Long,
    val proprietorCapitalAccount: Long,
    val securedLoans: Long,
    val unsecuredLoans: Long,
    val sundryCreditorsAndCardDues: Long,
    val totalLiabilitiesAndCapital: Long,
    val fixedCapitalAssetsWDV: Long,
    val investmentsPortfolio: Long,
    val sundryDebtorsReceivable: Long,
    val bankAndPrepaidBalances: Long,
    val cashInHand: Long,
    val totalAssets: Long,
    val isBalanced: Boolean
)

data class FinancialPnLStatement(
    val fromEpoch: Long,
    val toEpoch: Long,
    val grossInflows: Long,
    val operationalLivingExpenses: Long,
    val financeAndLoanCharges: Long,
    val depreciationWrittenOff: Long,
    val netSurplusSavings: Long,
    val taxDeductibleSummary: Map<String, Long>
)

data class StagedStatementLineItem(
    val rawExtractedName: String,
    val inferredType: PocketType,
    val extractedAmount: Long,
    val isSelectedForCommit: Boolean = true,
    val matchedExistingPocketId: Long? = null
)
