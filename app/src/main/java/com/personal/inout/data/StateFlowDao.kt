package com.personal.inout.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StateFlowDao {
    @Query("SELECT * FROM vault_pockets WHERE isArchived = 0 ORDER BY id ASC")
    fun observeAllActivePockets(): Flow<List<VaultPocket>>

    @Query("SELECT * FROM vault_pockets WHERE isArchived = 0 ORDER BY id ASC")
    suspend fun getActivePocketsSync(): List<VaultPocket>

    @Query("SELECT * FROM vault_pockets WHERE id = :id LIMIT 1")
    suspend fun getPocketById(id: Long): VaultPocket?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPocket(pocket: VaultPocket): Long

    @Update
    suspend fun updatePocket(pocket: VaultPocket)

    @Query("SELECT * FROM flow_records ORDER BY timestamp DESC")
    fun observeAllFlowRecords(): Flow<List<FlowRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlowRecord(record: FlowRecord): Long

    @Update
    suspend fun updateFlowRecord(record: FlowRecord)

    @Query("DELETE FROM flow_records WHERE id = :id")
    suspend fun deleteFlowRecordById(id: Long)

    @Query("UPDATE flow_records SET isPaused = :isPaused WHERE id = :id")
    suspend fun setRecurringPausedState(id: Long, isPaused: Boolean)

    @Query("SELECT * FROM flow_records WHERE isRecurring = 1 AND isPaused = 0")
    suspend fun getActiveRecurringSchedulesSync(): List<FlowRecord>

    @Query("""
        SELECT COUNT(*) FROM flow_records 
        WHERE note = :note 
          AND amount = :amount 
          AND timestamp BETWEEN :startTimestamp AND :endTimestamp
    """)
    suspend fun countRecordsWithFingerprint(
        note: String,
        amount: Double,
        startTimestamp: Long,
        endTimestamp: Long
    ): Int

    @Query("SELECT * FROM staged_desires WHERE isFulfilled = 0 ORDER BY createdAtEpoch DESC")
    fun observeActiveStagedDesires(): Flow<List<StagedDesire>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStagedDesire(desire: StagedDesire): Long

    @Query("SELECT * FROM system_notices ORDER BY timestamp DESC")
    fun observeAllNotices(): Flow<List<SystemNotice>>

    @Query("SELECT COUNT(*) FROM system_notices WHERE isRead = 0")
    fun observeUnreadNoticeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotice(notice: SystemNotice): Long

    @Query("UPDATE system_notices SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllNoticesRead()

    @Query("DELETE FROM system_notices")
    suspend fun clearAllNotices()

    @Query("""
        SELECT 
            CAST(p.id AS TEXT) AS pocketId,
            p.name AS pocketName,
            p.pocketType AS pocketType,
            p.subType AS subType,
            COALESCE(p.creditLimit, 0.0) AS creditLimit,
            COALESCE(p.targetAmount, 0.0) AS targetAmount,
            p.targetDateEpoch AS targetDateEpoch,
            COALESCE(SUM(CASE 
                WHEN f.targetPocketId = p.id AND f.isRecurring = 0 THEN f.amount 
                WHEN f.sourcePocketId = p.id AND f.isRecurring = 0 THEN -f.amount 
                ELSE 0.0 
            END), 0.0) AS currentBalance,
            COALESCE(SUM(CASE 
                WHEN f.targetPocketId = p.id AND f.isRecurring = 0 THEN f.amount 
                WHEN f.sourcePocketId = p.id AND f.isRecurring = 0 THEN -f.amount 
                ELSE 0.0 
            END), 0.0) AS computedBalance
        FROM vault_pockets p
        LEFT JOIN flow_records f ON (p.id = f.sourcePocketId OR p.id = f.targetPocketId)
        WHERE p.isArchived = 0
        GROUP BY p.id
        ORDER BY p.id ASC
    """)
    fun observePocketBalances(): Flow<List<PocketBalanceSummary>>

    @Query("""
        SELECT 
            CAST(p.id AS TEXT) AS pocketId,
            p.name AS pocketName,
            p.pocketType AS pocketType,
            p.subType AS subType,
            COALESCE(p.creditLimit, 0.0) AS creditLimit,
            COALESCE(p.targetAmount, 0.0) AS targetAmount,
            p.targetDateEpoch AS targetDateEpoch,
            COALESCE(SUM(CASE 
                WHEN f.targetPocketId = p.id AND f.isRecurring = 0 THEN f.amount 
                WHEN f.sourcePocketId = p.id AND f.isRecurring = 0 THEN -f.amount 
                ELSE 0.0 
            END), 0.0) AS currentBalance,
            COALESCE(SUM(CASE 
                WHEN f.targetPocketId = p.id AND f.isRecurring = 0 THEN f.amount 
                WHEN f.sourcePocketId = p.id AND f.isRecurring = 0 THEN -f.amount 
                ELSE 0.0 
            END), 0.0) AS computedBalance
        FROM vault_pockets p
        LEFT JOIN flow_records f ON (p.id = f.sourcePocketId OR p.id = f.targetPocketId)
        WHERE p.isArchived = 0
        GROUP BY p.id
        ORDER BY p.id ASC
    """)
    suspend fun getPocketBalancesSync(epoch: Long = System.currentTimeMillis()): List<PocketBalanceSummary>
}
