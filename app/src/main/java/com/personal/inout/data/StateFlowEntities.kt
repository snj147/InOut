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
    PEER
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
    val movementNature: String,
    val category: String,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false,
    val recurringCadence: String = "NONE"
)

@Entity(tableName = "pockets")
data class VaultPocket(
    @PrimaryKey val pocketId: String,
    val pocketName: String,
    val pocketType: String,
    val subType: String? = null,
    val creditLimit: Long = 0L,
    val isArchived: Boolean = false
)

// Factory function matching legacy parameter names (id, name, type: PocketType)
@Suppress("FunctionName")
fun VaultPocket(
    id: String,
    name: String,
    type: PocketType,
    subType: String? = null,
    creditLimit: Long = 0L,
    isArchived: Boolean = false
): VaultPocket = VaultPocket(
    pocketId = id,
    pocketName = name,
    pocketType = type.name,
    subType = subType,
    creditLimit = creditLimit,
    isArchived = isArchived
)

// Factory function matching legacy parameter names (id, name, pocketType: String)
@Suppress("FunctionName")
fun VaultPocket(
    id: String,
    name: String,
    pocketType: String,
    subType: String? = null,
    creditLimit: Long = 0L,
    isArchived: Boolean = false
): VaultPocket = VaultPocket(
    pocketId = id,
    pocketName = name,
    pocketType = pocketType,
    subType = subType,
    creditLimit = creditLimit,
    isArchived = isArchived
)

data class PocketBalanceSummary(
    val pocketId: String,
    val pocketName: String,
    val pocketType: String,
    val subType: String?,
    val creditLimit: Long,
    val computedBalance: Long
)
