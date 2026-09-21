package com.personal.inout.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

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

enum class CadenceType {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}

class FlowConverters {
    @TypeConverter
    fun fromMovementNature(nature: MovementNature?): String = nature?.name ?: MovementNature.OUTFLOW.name

    @TypeConverter
    fun toMovementNature(value: String?): MovementNature = try {
        MovementNature.valueOf(value ?: MovementNature.OUTFLOW.name)
    } catch (_: Exception) {
        MovementNature.OUTFLOW
    }

    @TypeConverter
    fun fromPocketType(type: PocketType?): String = type?.name ?: PocketType.LIQUID.name

    @TypeConverter
    fun toPocketType(value: String?): PocketType = try {
        PocketType.valueOf(value ?: PocketType.LIQUID.name)
    } catch (_: Exception) {
        PocketType.LIQUID
    }
}

@Entity(tableName = "flow_records")
@TypeConverters(FlowConverters::class)
data class FlowRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourcePocketId: Long? = null,
    val targetPocketId: Long? = null,
    val amount: Double = 0.0,
    val movementNature: MovementNature = MovementNature.OUTFLOW,
    val category: String = "",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false,
    val recurringCadence: String = "NONE",
    val frequency: String = "NONE"
) {
    val nature: MovementNature get() = movementNature
}

@Entity(tableName = "pockets")
@TypeConverters(FlowConverters::class)
data class VaultPocket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String = "",
    val pocketType: PocketType = PocketType.LIQUID,
    val subType: String? = null,
    val creditLimit: Double = 0.0,
    val isArchived: Boolean = false
) {
    val pocketId: String get() = id.toString()
    val pocketName: String get() = name
}

data class PocketBalanceSummary(
    val pocketId: String = "",
    val name: String = "",
    val pocketType: PocketType = PocketType.LIQUID,
    val subType: String? = null,
    val creditLimit: Double = 0.0,
    val computedBalance: Double = 0.0
) {
    val pocketName: String get() = name
    val currentBalance: Double get() = computedBalance
}
