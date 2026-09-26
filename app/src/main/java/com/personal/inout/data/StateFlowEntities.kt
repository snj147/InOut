package com.personal.inout.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PocketType {
    LIQUID,
    CREDIT,
    CREDIT_LINE,
    SAVING_GOAL,
    COUNTERPARTY,
    PEER
}

enum class MovementNature {
    INFLOW,
    OUTFLOW,
    TRANSFER,
    CARD_PAYMENT,
    PEER_LEND,
    PEER_BORROW,
    PEER_COLLECT,
    PEER_REPAY
}

enum class CadenceType {
    DAILY,
    WEEKLY,
    MONTHLY,
    NONE
}

@Entity(tableName = "vault_pockets")
data class VaultPocket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val pocketType: PocketType,
    val subType: String = "",
    val creditLimit: Double? = 0.0,
    val targetAmount: Double? = 0.0,
    val targetDateEpoch: Long = 0L,
    val isArchived: Boolean = false
)

@Entity(tableName = "flow_records")
data class FlowRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourcePocketId: Long? = null,
    val targetPocketId: Long? = null,
    val amount: Double? = 0.0,
    val movementNature: MovementNature = MovementNature.OUTFLOW,
    val category: String = "General",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false,
    val frequency: String = "NONE",
    val recurringCadence: String = "NONE",
    val isPaused: Boolean = false
) {
    val nature: MovementNature get() = movementNature
}

@Entity(tableName = "staged_desires")
data class StagedDesire(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val amount: Double,
    val createdAtEpoch: Long = System.currentTimeMillis(),
    val isFulfilled: Boolean = false
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

data class PocketBalanceSummary(
    @ColumnInfo(name = "pocketId") val pocketId: String = "",
    @ColumnInfo(name = "pocketName") val pocketName: String = "",
    @ColumnInfo(name = "pocketType") val pocketType: PocketType = PocketType.LIQUID,
    @ColumnInfo(name = "subType") val subType: String = "",
    @ColumnInfo(name = "creditLimit") val creditLimit: Double = 0.0,
    @ColumnInfo(name = "targetAmount") val targetAmount: Double = 0.0,
    @ColumnInfo(name = "targetDateEpoch") val targetDateEpoch: Long = 0L,
    @ColumnInfo(name = "currentBalance") val currentBalance: Double = 0.0,
    @ColumnInfo(name = "computedBalance") val computedBalance: Double = 0.0
) {
    val name: String get() = pocketName
    val id: Long get() = pocketId.toLongOrNull() ?: 0L
}

typealias PocketBalanceTuple = PocketBalanceSummary
