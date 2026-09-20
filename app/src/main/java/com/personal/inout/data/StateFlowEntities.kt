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
    CREDIT_LINE,
    PEER,
    COUNTERPARTY
}

// Global operator overloads allowing seamless comparison between String and Enums across UI
operator fun String?.equals(other: PocketType): Boolean = this?.equals(other.name, ignoreCase = true) == true
operator fun PocketType?.equals(other: String): Boolean = this?.name?.equals(other, ignoreCase = true) == true
operator fun String?.equals(other: MovementNature): Boolean = this?.equals(other.name, ignoreCase = true) == true
operator fun MovementNature?.equals(other: String): Boolean = this?.name?.equals(other, ignoreCase = true) == true

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
    val amount: Double = 0.0,
    val movementNature: String = MovementNature.OUTFLOW.name,
    val category: String = "",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false,
    val recurringCadence: String = "NONE"
) {
    val nature: String get() = movementNature
}

@Entity(tableName = "pockets")
data class VaultPocket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String = "",
    val pocketType: String = PocketType.LIQUID.name,
    val subType: String? = null,
    val creditLimit: Double = 0.0,
    val isArchived: Boolean = false
) {
    val pocketId: String get() = id.toString()
    val pocketName: String get() = name
}

// Factory constructors
@Suppress("FunctionName")
fun VaultPocket(
    id: Long = 0L,
    name: String,
    pocketType: PocketType,
    subType: String? = null,
    creditLimit: Double = 0.0,
    isArchived: Boolean = false
): VaultPocket = VaultPocket(
    id = id,
    name = name,
    pocketType = pocketType.name,
    subType = subType,
    creditLimit = creditLimit,
    isArchived = isArchived
)

@Suppress("FunctionName")
fun VaultPocket(
    name: String,
    pocketType: PocketType,
    subType: String? = null
): VaultPocket = VaultPocket(
    name = name,
    pocketType = pocketType.name,
    subType = subType
)

data class PocketBalanceSummary(
    val pocketId: String = "",
    val name: String = "",
    val pocketType: String = "",
    val subType: String? = null,
    val creditLimit: Double = 0.0,
    val computedBalance: Double = 0.0
) {
    val pocketName: String get() = name
    val currentBalance: Double get() = computedBalance
}
