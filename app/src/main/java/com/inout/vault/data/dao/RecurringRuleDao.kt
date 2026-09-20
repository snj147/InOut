package com.inout.vault.data.dao

import androidx.room.*
import com.inout.vault.data.entity.RecurringRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RecurringRuleEntity): Long

    @Query("SELECT * FROM recurring_rules WHERE isActive = 1")
    fun getActiveRulesFlow(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE isActive = 1")
    suspend fun getActiveRules(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): RecurringRuleEntity?

    @Query("UPDATE recurring_rules SET nextExecutionTimestamp = :nextTimestamp WHERE id = :id")
    suspend fun updateNextExecution(id: Long, nextTimestamp: Long)

    @Query("UPDATE recurring_rules SET isActive = :isActive WHERE id = :id")
    suspend fun setRuleActiveStatus(id: Long, isActive: Boolean)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)
}
