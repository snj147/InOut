package com.personal.inout.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class PocketType {
    LIQUID,       // Cash, Bank Accounts, UPI Wallets
    CREDIT_LINE,  // Credit Cards, BNPL, Formal Loans
    COUNTERPARTY  // People (Friends, Colleagues, Family)
}

enum class MovementNature {
    OUTFLOW,      // Liquid or Credit -> Expense
    INFLOW,       // Income -> Liquid
    TRANSFER,     // Liquid -> Liquid
    CARD_PAYMENT, // Liquid -> Credit Line (Pay bill)
    PEER_LEND,    // Liquid -> Counterparty
    PEER_COLLECT, // Counterparty -> Liquid
    PEER_BORROW,  // Counterparty -> Liquid
    PEER_REPAY    // Liquid -> Counterparty
}

@Entity(tableName = "vault_pockets")
data class VaultPocket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val pocketType: PocketType,
    val subType: String = "GENERAL",
    val creditLimit: Double = 0.0,
    val dueDateMillis: Long = 0L,
    val isArchived: Boolean = false
)

@Entity(
    tableName = "flow_records",
    indices = [Index(value = ["sourcePocketId"]), Index(value = ["targetPocketId"])]
)
data class FlowRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val nature: MovementNature,
    val sourcePocketId: Long? = null,
    val targetPocketId: Long? = null,
    val amount: Double,
    val category: String = "General",
    val note: String = "",
    val isRecurring: Boolean = false,
    val frequency: String = "NONE"
)

data class PocketBalanceSummary(
    val pocketId: Long,
    val name: String,
    val pocketType: PocketType,
    val subType: String,
    val creditLimit: Double,
    val dueDateMillis: Long,
    val currentBalance: Double
)

@Dao
interface StateFlowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPocket(pocket: VaultPocket): Long

    @Update
    suspend fun updatePocket(pocket: VaultPocket)

    @Query("SELECT * FROM vault_pockets WHERE isArchived = 0 ORDER BY name ASC")
    fun observeAllActivePockets(): Flow<List<VaultPocket>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFlowRecord(record: FlowRecord): Long

    @Query("SELECT * FROM flow_records ORDER BY timestamp DESC")
    fun observeAllFlowRecords(): Flow<List<FlowRecord>>

    @Query("DELETE FROM flow_records WHERE id = :id")
    suspend fun deleteFlowRecord(id: Long)

    @Query("""
        SELECT 
            p.id AS pocketId,
            p.name AS name,
            p.pocketType AS pocketType,
            p.subType AS subType,
            p.creditLimit AS creditLimit,
            p.dueDateMillis AS dueDateMillis,
            CASE 
                -- LIQUID: Inflows add, Outflows subtract
                WHEN p.pocketType = 'LIQUID' THEN
                    COALESCE((SELECT SUM(amount) FROM flow_records WHERE targetPocketId = p.id), 0.0) -
                    COALESCE((SELECT SUM(amount) FROM flow_records WHERE sourcePocketId = p.id), 0.0)

                -- CREDIT_LINE: Outflow spend increases debt, payments reduce debt
                WHEN p.pocketType = 'CREDIT_LINE' THEN
                    COALESCE((SELECT SUM(amount) FROM flow_records WHERE sourcePocketId = p.id AND nature = 'OUTFLOW'), 0.0) -
                    COALESCE((SELECT SUM(amount) FROM flow_records WHERE targetPocketId = p.id AND nature = 'CARD_PAYMENT'), 0.0)

                -- COUNTERPARTY: Positive = They owe you, Negative = You owe them
                WHEN p.pocketType = 'COUNTERPARTY' THEN
                    COALESCE((SELECT SUM(amount) FROM flow_records WHERE targetPocketId = p.id AND nature IN ('PEER_LEND', 'PEER_REPAY')), 0.0) -
                    COALESCE((SELECT SUM(amount) FROM flow_records WHERE sourcePocketId = p.id AND nature IN ('PEER_COLLECT', 'PEER_BORROW')), 0.0)

                ELSE 0.0
            END AS currentBalance
        FROM vault_pockets p
        WHERE p.isArchived = 0
        GROUP BY p.id
    """)
    fun observePocketBalances(): Flow<List<PocketBalanceSummary>>
}
