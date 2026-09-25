package com.personal.inout.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class PocketType {
    LIQUID,
    CREDIT,
    CREDIT_LINE,
    SAVING_GOAL,
    COUNTERPARTY,
    PEER
}

enum class MovementNature {
    INFLOW,
    OUTFLOW,
    TRANSFER,
    CARD_PAYMENT,
    PEER_LEND,
    PEER_BORROW,
    PEER_COLLECT,
    PEER_REPAY
}

@Entity(tableName = "vault_pockets")
data class VaultPocket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val pocketType: PocketType,
    val subType: String = "",
    val creditLimit: Double? = 0.0,
    val targetAmount: Double? = 0.0,
    val targetDateEpoch: Long = 0L,
    val isArchived: Boolean = false
)

@Entity(tableName = "flow_records")
data class FlowRecord @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourcePocketId: Long? = null,
    val targetPocketId: Long? = null,
    val amount: Double? = 0.0,
    @ColumnInfo(name = "movementNature") val nature: MovementNature = MovementNature.OUTFLOW,
    val category: String = "General",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false,
    val frequency: String = "NONE",
    val recurringCadence: String = "NONE",
    val isPaused: Boolean = false
) {
    @Ignore
    constructor(
        id: Long = 0L,
        sourcePocketId: Long? = null,
        targetPocketId: Long? = null,
        amount: Double? = 0.0,
        movementNature: MovementNature,
        category: String = "General",
        note: String = "",
        timestamp: Long = System.currentTimeMillis(),
        isRecurring: Boolean = false,
        frequency: String = "NONE",
        recurringCadence: String = "NONE",
        isPaused: Boolean = false
    ) : this(
        id = id,
        sourcePocketId = sourcePocketId,
        targetPocketId = targetPocketId,
        amount = amount,
        nature = movementNature,
        category = category,
        note = note,
        timestamp = timestamp,
        isRecurring = isRecurring,
        frequency = frequency,
        recurringCadence = recurringCadence,
        isPaused = isPaused
    )

    val movementNature: MovementNature get() = nature
}

@Entity(tableName = "staged_desires")
data class StagedDesire(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val amount: Double,
    val createdAtEpoch: Long = System.currentTimeMillis(),
    val isFulfilled: Boolean = false
)

@Entity(tableName = "system_notices")
data class SystemNotice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val message: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

data class PocketBalanceSummary(
    @ColumnInfo(name = "pocketId") val pocketId: String = "",
    @ColumnInfo(name = "pocketName") val pocketName: String = "",
    @ColumnInfo(name = "pocketType") val pocketType: PocketType = PocketType.LIQUID,
    @ColumnInfo(name = "subType") val subType: String = "",
    @ColumnInfo(name = "creditLimit") val creditLimit: Double = 0.0,
    @ColumnInfo(name = "targetAmount") val targetAmount: Double = 0.0,
    @ColumnInfo(name = "targetDateEpoch") val targetDateEpoch: Long = 0L,
    @ColumnInfo(name = "currentBalance") val currentBalance: Double = 0.0,
    @ColumnInfo(name = "computedBalance") val computedBalance: Double = 0.0
) {
    // Backwards compatibility properties for older callers (PdfDossierExporter, DynamicCockpit)
    val name: String get() = pocketName
    val id: Long get() = pocketId.toLongOrNull() ?: 0L
}

typealias PocketBalanceTuple = PocketBalanceSummary

@Dao
interface StateFlowDao {
    @Query("SELECT * FROM vault_pockets WHERE isArchived = 0 ORDER BY id ASC")
    fun observeAllActivePockets(): Flow<List<VaultPocket>>

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
}

@Database(
    entities = [VaultPocket::class, FlowRecord::class, StagedDesire::class, SystemNotice::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stateFlowDao(): StateFlowDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS system_notices (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        message TEXT NOT NULL,
                        type TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isRead INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inout_vault_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
