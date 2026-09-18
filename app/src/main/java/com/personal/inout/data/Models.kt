package com.personal.inout.data

data class Account(
    val id: Long = 0L,
    val name: String = "",
    val type: String = "BANK",
    val balance: Double = 0.0,
    val totalLimit: Double = 0.0,
    val dueDate: Long = 0L
)

data class Transaction(
    val id: Long = 0L,
    val accountId: Long = 0L,
    val flowType: String = "OUT",
    val type: String = "EXPENSE",
    val category: String = "General",
    val amount: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val isRecurring: Boolean = false,
    val frequency: String = "NONE"
)
