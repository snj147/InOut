package com.inout.vault.data

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

    @Query("SELECT * FROM flow_records ORDER BY timestamp DESC")
    fun getAllFlowRecords(): Flow<List<FlowRecord>>

    @Query("""
        SELECT 
            pockets.pocketId,
            pockets.pocketName,
            pockets.pocketType,
            pockets.subType,
            pockets.creditLimit,
            pockets.billingCycleDay,
            pockets.gracePeriodDays,
            COALESCE(SUM(
                CASE 
                    -- Only calculate flows that occurred at or before the requested cutoff
                    WHEN flow_records.targetPocketId = pockets.pocketId AND flow_records.timestamp <= :currentTime THEN flow_records.amount
                    WHEN flow_records.sourcePocketId = pockets.pocketId AND flow_records.timestamp <= :currentTime THEN -flow_records.amount
                    ELSE 0 
                END
            ), 0) AS computedBalance
        FROM pockets
        LEFT JOIN flow_records ON (pockets.pocketId = flow_records.sourcePocketId OR pockets.pocketId = flow_records.targetPocketId)
        WHERE pockets.isArchived = 0
        GROUP BY pockets.pocketId
    """)
    fun getPocketBalanceSummaries(currentTime: Long = System.currentTimeMillis()): Flow<List<PocketBalanceSummary>>

    @Query("SELECT * FROM pockets WHERE isArchived = 0")
    fun getAllActivePockets(): Flow<List<PocketEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPocket(pocket: PocketEntity)

    @Update
    suspend fun updatePocket(pocket: PocketEntity)
}
