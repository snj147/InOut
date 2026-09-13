package com.personal.inout.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val balance: Double,
    val type: String // CASH, BANK, CREDIT
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val type: String, // INCOME, EXPENSE, TRANSFER
    val category: String,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity(tableName = "sms_drafts")
data class SmsDraft(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawSender: String,
    val rawBody: String,
    val amount: Double,
    val merchant: String,
    val timestamp: Long = System.currentTimeMillis()
)
