package com.inout.vault.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.inout.vault.data.MovementNature

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePocketId: String?,
    val targetPocketId: String?,
    val amount: Long,
    val movementNature: MovementNature,
    val category: String,
    val note: String,
    val cadence: String, // DAILY, WEEKLY, MONTHLY
    val nextExecutionTimestamp: Long,
    val isActive: Boolean = true
)
