package com.personal.inout.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.util.UUID

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
    @PrimaryKey val pocketId: String = UUID.randomUUID().toString(),
    val pocketName: String = "",
    val pocketTypeStr: String = PocketType.LIQUID.name,
    val subType: String? = null,
    val creditLimit: Long = 0L,
    val isArchived: Boolean = false
) {
    // Property aliases for WidgetCommandActivity compatibility
    val id: String get() = pocketId
    val name: String get() = pocketName

    val pocketType: PocketType
        get() = try {
            PocketType.valueOf(pocketTypeStr)
        } catch (_: Exception) {
            PocketType.LIQUID
        }
}

// Factory function matching legacy parameter names (id, name, pocketType: PocketType)
@Suppress("FunctionName")
fun VaultPocket(
    id: String = UUID.randomUUID().toString(),
    name: String,
    pocketType: PocketType,
    subType: String? = null,
    creditLimit: Long = 0L,
    isArchived: Boolean = false
): VaultPocket = VaultPocket(
    pocketId = id,
    pocketName = name,
    pocketTypeStr = pocketType.name,
    subType = subType,
    creditLimit = creditLimit,
    isArchived = isArchived
)

// Factory function matching legacy parameter names (id as Long/String)
@Suppress("FunctionName")
fun VaultPocket(
    id: Long,
    name: String,
    pocketType: PocketType,
    subType: String? = null,
    creditLimit: Long = 0L,
    isArchived: Boolean = false
): VaultPocket = VaultPocket(
    pocketId = id.toString(),
    pocketName = name,
    pocketTypeStr = pocketType.name,
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
