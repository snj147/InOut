package com.personal.inout.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StateFlowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlowRecord(record: FlowRecord): Long

    @Update
    suspend fun updateFlowRecord(record: FlowRecord)

    @Delete
    suspend fun deleteFlowRecord(record: FlowRecord)

    @Query("DELETE FROM flow_records WHERE id = :id")
    suspend fun deleteFlowRecordById(id: Long)

    @Query("SELECT * FROM flow_records ORDER BY timestamp DESC")
    fun getAllFlowRecords(): Flow<List<FlowRecord>>

    @Query("SELECT * FROM flow_records ORDER BY timestamp DESC")
    fun observeAllFlowRecords(): Flow<List<FlowRecord>>

    @Query("SELECT * FROM flow_records WHERE isRecurring = 1 ORDER BY timestamp DESC")
    fun getRecurringSchedules(): Flow<List<FlowRecord>>

    @Query("SELECT * FROM flow_records WHERE isRecurring = 1 AND isPaused = 0")
    suspend fun getActiveRecurringSchedulesSync(): List<FlowRecord>

    @Query("UPDATE flow_records SET isPaused = :isPaused WHERE id = :id")
    suspend fun setRecurringPausedState(id: Long, isPaused: Boolean)

    @Query("UPDATE flow_records SET isRecurring = 0 WHERE id = :id")
    suspend fun stopRecurringSchedule(id: Long)

    @Query("""
        SELECT 
            CAST(pockets.id AS TEXT) AS pocketId,
            pockets.name AS name,
            pockets.pocketType AS pocketType,
            pockets.subType AS subType,
            pockets.creditLimit AS creditLimit,
            pockets.targetAmount AS targetAmount,
            pockets.targetDateEpoch AS targetDateEpoch,
            COALESCE(SUM(
                CASE 
                    WHEN flow_records.targetPocketId = pockets.id AND flow_records.timestamp <= :currentTime THEN flow_records.amount
                    WHEN flow_records.sourcePocketId = pockets.id AND flow_records.timestamp <= :currentTime THEN -flow_records.amount
                    ELSE 0.0 
                END
            ), 0.0) AS computedBalance
        FROM pockets
        LEFT JOIN flow_records ON (pockets.id = flow_records.sourcePocketId OR pockets.id = flow_records.targetPocketId)
        WHERE pockets.isArchived = 0
        GROUP BY pockets.id
    """)
    fun observePocketBalances(currentTime: Long = System.currentTimeMillis()): Flow<List<PocketBalanceSummary>>

    @Query("""
        SELECT 
            CAST(pockets.id AS TEXT) AS pocketId,
            pockets.name AS name,
            pockets.pocketType AS pocketType,
            pockets.subType AS subType,
            pockets.creditLimit AS creditLimit,
            pockets.targetAmount AS targetAmount,
            pockets.targetDateEpoch AS targetDateEpoch,
            COALESCE(SUM(
                CASE 
                    WHEN flow_records.targetPocketId = pockets.id AND flow_records.timestamp <= :currentTime THEN flow_records.amount
                    WHEN flow_records.sourcePocketId = pockets.id AND flow_records.timestamp <= :currentTime THEN -flow_records.amount
                    ELSE 0.0 
                END
            ), 0.0) AS computedBalance
        FROM pockets
        LEFT JOIN flow_records ON (pockets.id = flow_records.sourcePocketId OR pockets.id = flow_records.targetPocketId)
        WHERE pockets.isArchived = 0
        GROUP BY pockets.id
    """)
    suspend fun getPocketBalancesSync(currentTime: Long = System.currentTimeMillis()): List<PocketBalanceSummary>

    @Query("SELECT * FROM pockets WHERE isArchived = 0")
    fun observeAllActivePockets(): Flow<List<VaultPocket>>

    @Query("SELECT * FROM pockets WHERE isArchived = 0")
    suspend fun getActivePocketsSync(): List<VaultPocket>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPocket(pocket: VaultPocket): Long

    @Update
    suspend fun updatePocket(pocket: VaultPocket)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStagedDesire(desire: StagedDesire): Long

    @Update
    suspend fun updateStagedDesire(desire: StagedDesire)

    @Query("SELECT * FROM staged_desires WHERE status = 'STAGED' ORDER BY coolOffUntil ASC")
    fun observeActiveStagedDesires(): Flow<List<StagedDesire>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM staged_desires WHERE status = 'ARCHIVED'")
    fun observeSavedImpulseTotal(): Flow<Double>
}
