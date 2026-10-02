package com.personal.inout.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    // --- Pockets Management ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPocket(pocket: LedgerPocket): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPocketsBatch(pockets: List<LedgerPocket>): List<Long>

    @Update
    suspend fun updatePocket(pocket: LedgerPocket)

    @Delete
    suspend fun deletePocket(pocket: LedgerPocket)

    @Query("SELECT * FROM ledger_pockets WHERE isArchived = 0 ORDER BY type ASC, name ASC")
    fun getAllActivePockets(): Flow<List<LedgerPocket>>

    @Query("SELECT * FROM ledger_pockets WHERE isArchived = 0")
    suspend fun getAllActivePocketsSnapshot(): List<LedgerPocket>

    @Query("SELECT * FROM ledger_pockets WHERE id = :id LIMIT 1")
    suspend fun getPocketById(id: Long): LedgerPocket?

    @Query("SELECT * FROM ledger_pockets WHERE type = 'LIQUID' AND LOWER(name) = LOWER(:name) AND isArchived = 0 LIMIT 1")
    suspend fun getLiquidPocketByName(name: String): LedgerPocket?

    @Query("SELECT COUNT(*) FROM ledger_pockets WHERE isArchived = 0")
    suspend fun getActivePocketCount(): Int

    // --- Transactions & Double Entry ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(tx: LedgerTransaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionsBatch(txs: List<LedgerTransaction>): List<Long>

    @Update
    suspend fun updateTransaction(tx: LedgerTransaction)

    @Delete
    suspend fun deleteTransaction(tx: LedgerTransaction)

    @Query("SELECT * FROM ledger_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): LedgerTransaction?

    @Query("SELECT COUNT(*) FROM ledger_transactions")
    suspend fun getTransactionCount(): Int

    @Query("SELECT * FROM ledger_transactions ORDER BY timestamp DESC")
    fun observeAllTransactions(): Flow<List<LedgerTransaction>>

    @Query("SELECT * FROM ledger_transactions WHERE timestamp <= :nowEpoch ORDER BY timestamp DESC")
    fun observeHistoricalTransactions(nowEpoch: Long = System.currentTimeMillis()): Flow<List<LedgerTransaction>>

    @Query("SELECT * FROM ledger_transactions WHERE timestamp > :nowEpoch ORDER BY timestamp ASC")
    fun observeScheduledHorizonTransactions(nowEpoch: Long = System.currentTimeMillis()): Flow<List<LedgerTransaction>>

    @Query("SELECT * FROM ledger_transactions WHERE timestamp BETWEEN :startEpoch AND :endEpoch ORDER BY timestamp ASC")
    suspend fun getTransactionsBetween(startEpoch: Long, endEpoch: Long): List<LedgerTransaction>

    @Query("SELECT * FROM ledger_transactions WHERE isSubscription = 1 ORDER BY timestamp ASC")
    fun observeActiveSubscriptions(): Flow<List<LedgerTransaction>>

    @Query("SELECT * FROM ledger_transactions WHERE isTaxDeductible = 1 AND timestamp BETWEEN :startEpoch AND :endEpoch")
    suspend fun getTaxDeductibleTransactions(startEpoch: Long, endEpoch: Long): List<LedgerTransaction>

    @Query("""
        SELECT * FROM ledger_transactions 
        WHERE sourcePocketId = :pocketId 
          AND amount = :amount 
          AND timestamp BETWEEN (:timestamp - 172800000) AND (:timestamp + 172800000)
        LIMIT 1
    """)
    suspend fun findPotentialDuplicate(pocketId: Long, amount: Double, timestamp: Long): LedgerTransaction?

    // --- Dynamic Balance Engine Computations ---

    @Query("""
        SELECT COALESCE(
            SUM(
                CASE 
                    WHEN targetPocketId = :pocketId THEN amount
                    WHEN sourcePocketId = :pocketId AND movementNature IN ('OPERATING_INCOME', 'OPENING_BASELINE', 'VALUATION_MARK') THEN amount
                    WHEN sourcePocketId = :pocketId AND movementNature IN ('OPERATING_EXPENSE', 'TRANSFER', 'DEPRECIATION_WRITE', 'EMI_PRINCIPAL') THEN -amount
                    ELSE 0.0 
                END
            ), 0.0
        )
        FROM ledger_transactions
        WHERE (sourcePocketId = :pocketId OR targetPocketId = :pocketId)
          AND timestamp <= :asOfEpoch
          AND status = 'CLEARED'
    """)
    suspend fun computePocketBalance(pocketId: Long, asOfEpoch: Long = System.currentTimeMillis()): Double

    // --- Forensic Audit Trail ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditEntry(auditEntry: LedgerAuditEntry)

    @Query("SELECT * FROM ledger_audit_journal ORDER BY timestamp DESC LIMIT 500")
    fun observeAuditJournal(): Flow<List<LedgerAuditEntry>>

    // --- System Notices ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotice(notice: SystemNotice)

    @Query("SELECT * FROM system_notices WHERE isRead = 0 ORDER BY timestamp DESC")
    fun observeUnreadNotices(): Flow<List<SystemNotice>>

    @Query("UPDATE system_notices SET isRead = 1 WHERE id = :id")
    suspend fun markNoticeAsRead(id: Long)
}
