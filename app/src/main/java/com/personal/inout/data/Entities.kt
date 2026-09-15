package com.personal.inout.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val balance: Double,
    val totalLimit: Double = 0.0,
    val type: String, // "CASH", "BANK", "CREDIT", "LENDER", "BORROWER"
    val isDefault: Boolean = false,
    val hasEyeMask: Boolean = true,
    val billingDay: Int = 1,
    val dueDate: Long = 0L,
    val repaymentType: String = "BULLET", // "BULLET", "INSTALLMENTS"
    val frequency: String = "MONTHLY",
    val installmentCount: Int = 1,
    val originalAmount: Double = 0.0
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val flowType: String, // "IN", "OUT", "BORROW", "LEND", "REPAY", "COLLECT", "TRANSFER", "ADJUSTMENT"
    val type: String,     // "INCOME", "EXPENSE", "NEUTRAL"
    val category: String,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val partyName: String = "",
    val channelAccountName: String = "",
    val returnDate: Long = 0L,
    val isRecurring: Boolean = false,
    val frequency: String = "NONE", // "DAILY", "WEEKLY", "MONTHLY", "YEARLY"
    val recurringEndDate: Long = 0L
)

@Entity(tableName = "counterparties")
data class Counterparty(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val role: String,
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
