package com.personal.inout.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val balance: Double,
    val type: String // "BANK", "CASH", "CREDIT"
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val type: String, // "EXPENSE", "INCOME", "TRANSFER"
    val category: String,
    val amount: Double,
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lender: String,
    val principal: Double,
    val isSilent: Boolean, // Silent loans don't trigger reminders or clutter the dashboard
    val monthlyInterest: Double = 0.0,
    val dueDate: Long? = null
)

@Entity(tableName = "sms_inbox")
data class SmsDraft(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawSender: String,
    val amount: Double,
    val merchant: String,
    val rawBody: String,
    val timestamp: Long = System.currentTimeMillis()
)
