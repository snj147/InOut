package com.personal.inout.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * BRD Rule 1, 7, 20, 31, 35, 36, 37: Exhaustive Pocket Types.
 */
enum class PocketType {
    LIQUID,          // Cash in Hand (Rule 36), Savings/Current Bank Accounts
    CREDIT_CARD,     // Credit Cards with bill due date & limits (Rule 4, 5, 21)
    PREPAID_WALLET,  // Paytm, Amazon Pay, Metro transit cards (Rule 37)
    GOAL_POT,        // Quarantined liquid goal reserves (Rule 6)
    PEER_RECEIVABLE, // Sundry Debtors: Money lent to others (Rule 7)
    PEER_PAYABLE,    // Sundry Creditors: Money borrowed from others (Rule 7)
    FIXED_ASSET,     // Property, Gold, Vehicles, Gadgets subject to WDV (Rule 20, 29)
    INVESTMENT,      // Mutual Funds, Equities, FDs, PPF (Rule 31)
    LIABILITY_LOAN   // Bank loans, consumer EMIs (Rule 35)
}

/**
 * BRD Rule 11, 19, 29, 31, 35, 36: Double-Entry Movement Nature.
 */
enum class MovementNature {
    OPERATING_EXPENSE,   // Regular day-to-day living expense
    OPERATING_INCOME,    // Regular salary, business revenues
    TRANSFER,            // Atomic zero-sum movements between internal pockets
    OPENING_BASELINE,    // Day-zero net worth baseline (Rule 29: 0 P&L impact)
    VALUATION_MARK,      // Mark-to-market portfolio adjustments (Rule 31)
    DEPRECIATION_WRITE,  // Fixed asset wear-and-tear amortization (Rule 20)
    EMI_PRINCIPAL,       // Debt principal reduction leg (Rule 35)
    RECONCILIATION_ADJ   // Audited statement discrepancy plug (Rule 16, 36)
}

/**
 * BRD Rule 15: Transaction settlement state lifecycle.
 */
enum class SettlementStatus {
    CLEARED,
    PENDING
}

/**
 * BRD Rule 32: Category envelope rollover mode.
 */
enum class RolloverMode {
    RESET,
    ROLLOVER_SURPLUS,
    DEFICIT_CLAWBACK
}

/**
 * Represents a discrete physical or virtual account / pocket.
 */
@Entity(tableName = "ledger_pockets")
data class LedgerPocket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: PocketType,
    val currency: String = "INR",
    val creditLimit: Double = 0.0,            // Rule 5: Approved credit line limit
    val statementClosingDay: Int = 0,        // Rule 21: Day of month bill generates (1-31)
    val billDueDay: Int = 0,                 // Rule 21: Day of month payment is due (1-31)
    val targetGoalAmount: Double = 0.0,      // Rule 6: Target pot allocation
    val goalTargetDate: Long = 0L,           // Rule 6: Target goal fulfillment epoch
    val peerContactName: String = "",        // Rule 7: Sundry debtor/creditor contact
    val lastReconciledEpoch: Long = 0L,      // Rule 16, 33: Locked checkpoint epoch
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Transaction header record encapsulating metadata and statutory tax flags.
 */
@Entity(
    tableName = "ledger_transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["sourcePocketId"]),
        Index(value = ["targetPocketId"]),
        Index(value = ["movementNature"]),
        Index(value = ["status"])
    ]
)
data class LedgerTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val amount: Double,
    val description: String,
    val category: String = "General",
    val movementNature: MovementNature = MovementNature.OPERATING_EXPENSE,
    val status: SettlementStatus = SettlementStatus.CLEARED,
    val sourcePocketId: Long,
    val targetPocketId: Long? = null,        // Populated for Transfers, Contra, EMI legs
    val receiptUri: String? = null,
    val isTaxDeductible: Boolean = false,    // Rule 22, 28: Indian ITR 80C/80D/Business tag
    val isReimbursable: Boolean = false,     // Rule 22: Corporate claims
    val isSubscription: Boolean = false,     // Rule 25: Local stealth renewal tracker
    val isRecurring: Boolean = false,        // Rule 8: Deterministic cadence
    val recurringFrequency: String = "NONE", // NONE, DAILY, WEEKLY, MONTHLY
    val recurringEndDate: Long = 0L,
    val originalCurrency: String = "INR",    // Rule 18: Multi-currency base anchor
    val foreignAmount: Double = 0.0,         // Rule 18: Preserved original currency value
    val splitParentId: Long? = null,         // Rule 17: Multi-category split line items
    val idempotencyHash: String = ""         // Rule 27: Deduplication signature
)

/**
 * BRD Rule 32: Category budget allocations with envelope rollovers.
 */
@Entity(tableName = "category_budgets")
data class CategoryBudget(
    @PrimaryKey val categoryName: String,
    val monthlyLimit: Double,
    val rolloverMode: RolloverMode = RolloverMode.RESET,
    val accumulatedDelta: Double = 0.0
)

/**
 * BRD Rule 24: Append-only forensic audit trail.
 */
@Entity(
    tableName = "ledger_audit_journal",
    indices = [Index(value = ["recordId"]), Index(value = ["timestamp"])]
)
data class LedgerAuditEntry(
    @PrimaryKey(autoGenerate = true) val auditId: Long = 0L,
    val actionType: String,                  // INSERT, UPDATE, DELETE, RECONCILE
    val entityType: String,                  // TRANSACTION, POCKET, BUDGET
    val recordId: Long,
    val preStateJson: String,
    val postStateJson: String,
    val reasonNote: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * In-app notifications and solvency warnings (Rule 25, 26).
 */
@Entity(tableName = "system_notices")
data class SystemNotice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val message: String,
    val type: String,                       // DEFICIT_WARNING, RENEWAL_DUE, CARD_DUE
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
