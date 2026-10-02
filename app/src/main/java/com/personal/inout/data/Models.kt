package com.personal.inout.data

/**
 * UI Domain models and calculations adhering to BRD 37-rule specifications.
 */

data class PocketBalanceSummary(
    val pocket: LedgerPocket,
    val currentBalance: Double,
    val encumberedCreditDues: Double = 0.0,
    val availableCreditLimit: Double = 0.0
)

data class SolvencyMetricDeck(
    val trueSafeLiquid: Double,              // Rule 3: Spendable cash without debt
    val totalNetWorth: Double,               // Rule 28: Total Assets - Total Liabilities
    val dailyBurnRate: Double,               // Rule 13: Burn velocity today
    val dailyBurnCeiling: Double,            // Rule 13: Benchmark ceiling (e.g., ₹500)
    val burnStatus: BurnPacingStatus,        // ON_TRACK vs SPIKED
    val runwayDays: Long,                    // Rule 13: True Safe Liquid / Burn Ceiling
    val projectedClosingLiquid: Double,      // Rule 26: Deterministic Month-End Cash Horizon
    val hasEarlyDeficitAlert: Boolean        // Rule 26: True if Month-End < 0
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

/**
 * BRD Rule 30: Quick entry habit pill preset.
 */
data class HabitPillPreset(
    val label: String,
    val category: String,
    val defaultPocketId: Long,
    val defaultMovementNature: MovementNature = MovementNature.OPERATING_EXPENSE
)

/**
 * BRD Rule 28, 34: Indian Statutory Statements (ICAI / ITR Schedule AL & P&L).
 */
data class IndianBalanceSheetReport(
    val asOfDateEpoch: Long,
    // Liabilities & Capital
    val proprietorCapitalAccount: Double,
    val securedLoans: Double,
    val unsecuredLoans: Double,
    val sundryCreditorsAndCardDues: Double,
    val totalLiabilitiesAndCapital: Double,
    // Assets
    val fixedCapitalAssetsWDV: Double,
    val investmentsPortfolio: Double,
    val sundryDebtorsReceivable: Double,
    val bankAndPrepaidBalances: Double,
    val cashInHand: Double,
    val totalAssets: Double,
    val isBalanced: Boolean
)

data class IndianPnLStatement(
    val fromEpoch: Long,
    val toEpoch: Long,
    val grossInflows: Double,
    val operationalLivingExpenses: Double,
    val financeAndLoanCharges: Double,
    val depreciationWrittenOff: Double,
    val netSurplusSavings: Double,
    val taxDeductibleSummary: Map<String, Double>
)

/**
 * BRD Rule 29: Staged line item for Balance Sheet PDF OCR import.
 */
data class StagedStatementLineItem(
    val rawExtractedName: String,
    val inferredType: PocketType,
    val extractedAmount: Double,
    val isSelectedForCommit: Boolean = true,
    val matchedExistingPocketId: Long? = null
)
