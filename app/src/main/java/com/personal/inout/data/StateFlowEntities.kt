package com.inout.vault.data

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

enum class CadenceType {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}

@Entity(tableName = "flow_records")
data class FlowRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePocketId: String? = null,
    val targetPocketId: String? = null,
    val amount: Long,
    val movementNature: MovementNature,
    val category: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false
)

@Entity(tableName = "pockets")
data class PocketEntity(
    @PrimaryKey val pocketId: String,
    val pocketName: String,
    val pocketType: String, // LIQUID, CREDIT, PEER
    val subType: String? = null,
    val creditLimit: Long = 0L,
    val billingCycleDay: Int = 1,
    val gracePeriodDays: Int = 20,
    val isArchived: Boolean = false
)

data class PocketBalanceSummary(
    val pocketId: String,
    val pocketName: String,
    val pocketType: String,
    val subType: String?,
    val creditLimit: Long,
    val billingCycleDay: Int,
    val gracePeriodDays: Int,
    val computedBalance: Long
)
