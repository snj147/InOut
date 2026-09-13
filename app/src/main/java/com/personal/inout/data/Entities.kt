package com.personal.inout.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val balance: Double,
    val type: String, // "BANK", "CASH", "CREDIT", "LOAN"
    val isDefault: Boolean = false,
    val monthlyLimit: Double = 0.0
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val flowType: String, // "IN", "OUT", "BORROW", "LEND"
    val type: String,     // Legacy fallback: "INCOME" or "EXPENSE"
    val category: String,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val partyName: String = "", // Lender or Borrower name
    val returnDate: Long = 0L,  // Due date for Borrow/Lend
    val isRecurring: Boolean = false,
    val frequency: String = "NONE" // "DAILY", "WEEKLY", "MONTHLY"
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
