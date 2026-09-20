package com.personal.inout.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MovementNature {
    OUTFLOW,
    INFLOW,
    TRANSFER,
    CARD_PAYMENT,
    PEER_LEND,
    PEER_BORROW,
    PEER_COLLECT,
    PEER_REPAY
}

enum class PocketType {
    LIQUID,
    CREDIT,
    PEER,
    COUNTERPARTY
}

enum class CadenceType {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}

@Entity(tableName = "flow_records")
data class FlowRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourcePocketId: Long? = null,
    val targetPocketId: Long? = null,
    val amount: Long,
    val movementNature: String,
    val category: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false,
    val recurringCadence: String = "NONE"
) {
    // Alias for PdfDossierExporter.kt
    val nature: String get() = movementNature
}

@Entity(tableName = "pockets")
data class VaultPocket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String = "",
    val pocketType: PocketType = PocketType.LIQUID,
    val subType: String? = null,
    val creditLimit: Long = 0L,
    val isArchived: Boolean = false
) {
    val pocketId: String get() = id.toString()
    val pocketName: String get() = name
}

data class PocketBalanceSummary(
    val pocketId: String,
    val pocketName: String,
    val pocketType: String,
    val subType: String?,
    val creditLimit: Long,
    val computedBalance: Long
) {
    // Aliases for PdfDossierExporter.kt
    val name: String get() = pocketName
    val currentBalance: Long get() = computedBalance
}
