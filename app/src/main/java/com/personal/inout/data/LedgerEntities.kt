package com.personal.inout.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PocketType {
    LIQUID,          
    CREDIT_CARD,     
    PREPAID_WALLET,  
    GOAL_POT,        
    PEER_RECEIVABLE, 
    PEER_PAYABLE,    
    FIXED_ASSET,     
    INVESTMENT,      
    LIABILITY_LOAN   
}

enum class MovementNature {
    OPERATING_EXPENSE,   
    OPERATING_INCOME,    
    TRANSFER,            
    OPENING_BASELINE,    
    VALUATION_MARK,      
    DEPRECIATION_WRITE,  
    EMI_PRINCIPAL,       
    RECONCILIATION_ADJ   
}

enum class SettlementStatus {
    CLEARED,
    PENDING
}

enum class RolloverMode {
    RESET,
    ROLLOVER_SURPLUS,
    DEFICIT_CLAWBACK
}

@Entity(tableName = "ledger_pockets")
data class LedgerPocket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: PocketType,
    val currency: String = "INR",
    val creditLimit: Long = 0L,              // Stored in paise
    val statementClosingDay: Int = 0,        
    val billDueDay: Int = 0,                 
    val targetGoalAmount: Long = 0L,         // Stored in paise
    val peerContactName: String = "",        
    val lastReconciledEpoch: Long = 0L,      
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

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
    val amount: Long,                        // Stored in paise
    val description: String,
    val category: String = "General",
    val movementNature: MovementNature = MovementNature.OPERATING_EXPENSE,
    val status: SettlementStatus = SettlementStatus.CLEARED,
    val sourcePocketId: Long,
    val targetPocketId: Long? = null,        
    val receiptUri: String? = null,
    val isTaxDeductible: Boolean = false,    
    val isReimbursable: Boolean = false,     
    val isSubscription: Boolean = false,     
    val isRecurring: Boolean = false,        
    val recurringFrequency: String = "NONE", 
    val recurringEndDate: Long = 0L,
    val originalCurrency: String = "INR",    
    val foreignAmount: Long = 0L,            // Stored in paise
    val splitParentId: Long? = null,         
    val idempotencyHash: String = ""         
)

@Entity(tableName = "category_budgets")
data class CategoryBudget(
    @PrimaryKey val categoryName: String,
    val monthlyLimit: Long,                  // Stored in paise
    val rolloverMode: RolloverMode = RolloverMode.RESET,
    val accumulatedDelta: Long = 0L          // Stored in paise
)

@Entity(
    tableName = "ledger_audit_journal",
    indices = [Index(value = ["recordId"]), Index(value = ["timestamp"])]
)
data class LedgerAuditEntry(
    @PrimaryKey(autoGenerate = true) val auditId: Long = 0L,
    val actionType: String,                  
    val entityType: String,                  
    val recordId: Long,
    val preStateJson: String,
    val postStateJson: String,
    val reasonNote: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "system_notices")
data class SystemNotice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val message: String,
    val type: String,                       
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
