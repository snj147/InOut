package com.personal.inout.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val balance: Double,
    val totalLimit: Double = 0.0,    // Credit Card limit
    val type: String,                // "CASH", "BANK", "CREDIT", "LENDER", "BORROWER"
    val isDefault: Boolean = false,
    val hasEyeMask: Boolean = true,
    val billingDay: Int = 1,         // CC billing date or due date
    val dueDate: Long = 0L,          // Repayment target date
    val repaymentType: String = "BULLET", // "BULLET", "INSTALLMENT"
    val frequency: String = "MONTHLY",    // "MONTHLY", "YEARLY"
    val installmentCount: Int = 1
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val flowType: String, // "IN", "OUT", "BORROW", "LEND", "REPAY", "COLLECT", "TRANSFER"
    val type: String,     // "INCOME", "EXPENSE", "NEUTRAL"
    val category: String,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val partyName: String = "",
    val returnDate: Long = 0L,
    val isRecurring: Boolean = false,
    val frequency: String = "NONE"
)

@Entity(tableName = "counterparties")
data class Counterparty(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val role: String, // "LENDER", "BORROWER"
    val currentBalance: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(tableName = "sms_drafts")
data class SmsDraft(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawSender: String,
    val rawBody: String,
    val amount: Double,
    val merchant: String,
    val timestamp: Long = System.currentTimeMillis()
)
