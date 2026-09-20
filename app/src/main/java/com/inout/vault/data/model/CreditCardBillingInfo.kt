package com.inout.vault.data.model

data class CreditCardBillingInfo(
    val pocketId: String,
    val creditLimit: Long,
    val unbilledAmount: Long,
    val billedAmount: Long,
    val totalAvailableLimit: Long,
    val paymentDueDateMillis: Long,
    val daysUntilDue: Int,
    val isOverdue: Boolean
)
