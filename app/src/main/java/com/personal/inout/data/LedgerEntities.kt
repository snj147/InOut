package com.personal.inout.data

import androidx.room.*

enum class AccountClassification {
    ASSET,       // Cash, Bank Savings, Wallets
    LIABILITY,   // Credit Cards, Loans/Lenders
    EQUITY,      // Starting capital, retained balances
    REVENUE,     // Salary, Freelance, Dividends
    EXPENSE      // Food, Groceries, Rent, Transport
}

enum class EntryDirection {
    DEBIT,
    CREDIT
}

@Entity(tableName = "ledger_accounts")
data class LedgerAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val classification: AccountClassification,
    val subType: String = "GENERAL", // CASH, BANK, CREDIT, BORROWER, LENDER, CATEGORY
    val currency: String = "INR",
    val creditLimit: Double = 0.0,
    val dueDateMillis: Long = 0L,
    val isSystemCategory: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ledger_transactions",
    indices = [Index(value = ["timestamp"])]
)
data class LedgerTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String,
    val referenceCode: String = "",
    val receiptUri: String? = null,
    val isReconciled: Boolean = true,
    val isTaxDeductible: Boolean = false,
    val isRecurring: Boolean = false,
    val recurringFrequency: String = "NONE",
    val recurringEndDate: Long = 0L
)

@Entity(
    tableName = "ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = LedgerTransaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LedgerAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["accountId"]),
        Index(value = ["accountId", "direction"])
    ]
)
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val transactionId: Long,
    val accountId: Long,
    val direction: EntryDirection,
    val amount: Double,
    val memo: String = ""
)

// POJO representing dynamic account balance derived by SQL
data class AccountBalanceResult(
    val accountId: Long,
    val accountName: String,
    val classification: AccountClassification,
    val subType: String,
    val totalDebit: Double,
    val totalCredit: Double,
    val netBalance: Double
)
