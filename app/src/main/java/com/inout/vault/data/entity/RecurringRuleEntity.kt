package com.inout.vault.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.inout.vault.data.CadenceType
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
    val cadence: CadenceType,
    val nextExecutionTimestamp: Long,
    val isActive: Boolean = true
)
