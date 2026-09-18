package com.personal.inout.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class TransactionDisplayRow(
    val id: Long,
    val timestamp: Long,
    val description: String,
    val amount: Double,
    val categoryOrAccount: String,
    val isRecurring: Boolean,
    val recurringFrequency: String
)

@Dao
interface LedgerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(account: LedgerAccount): Long

    @Update
    suspend fun updateAccount(account: LedgerAccount)

    @Query("SELECT * FROM ledger_accounts WHERE isArchived = 0 ORDER BY name ASC")
    fun getAllActiveAccounts(): Flow<List<LedgerAccount>>

    @Query("SELECT * FROM ledger_accounts WHERE subType = :subType AND isArchived = 0")
    fun getAccountsBySubType(subType: String): Flow<List<LedgerAccount>>

    @Query("SELECT * FROM ledger_accounts WHERE name = :name LIMIT 1")
    suspend fun getAccountByName(name: String): LedgerAccount?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactionRecord(tx: LedgerTransaction): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(entries: List<LedgerEntry>)

    @androidx.room.Transaction
    suspend fun recordBalancedPosting(
        transaction: LedgerTransaction,
        debitAccountId: Long,
        creditAccountId: Long,
        amount: Double,
        memo: String = ""
    ): Long {
        if (amount <= 0.0) return -1L
        if (debitAccountId == creditAccountId || debitAccountId == 0L || creditAccountId == 0L) {
            return -1L
        }

        val txId = insertTransactionRecord(transaction)
        val debitEntry = LedgerEntry(
            transactionId = txId,
            accountId = debitAccountId,
            direction = EntryDirection.DEBIT,
            amount = amount,
            memo = memo
        )
        val creditEntry = LedgerEntry(
            transactionId = txId,
            accountId = creditAccountId,
            direction = EntryDirection.CREDIT,
            amount = amount,
            memo = memo
        )
        insertEntries(listOf(debitEntry, creditEntry))
        return txId
    }

    @androidx.room.Transaction
    suspend fun postExpenseOrIncome(
        title: String,
        amount: Double,
        timestamp: Long,
        assetAccountId: Long,
        categoryName: String,
        isExpense: Boolean,
        isRecurring: Boolean,
        frequency: String
    ): Long {
        val catType = if (isExpense) AccountClassification.EXPENSE else AccountClassification.REVENUE
        var catAccount = getAccountByName(categoryName)
        if (catAccount == null) {
            val newId = insertAccount(
                LedgerAccount(
                    name = categoryName,
                    classification = catType,
                    subType = "CATEGORY"
                )
            )
            catAccount = LedgerAccount(
                id = newId,
                name = categoryName,
                classification = catType,
                subType = "CATEGORY"
            )
        }

        val debitId = if (isExpense) catAccount.id else assetAccountId
        val creditId = if (isExpense) assetAccountId else catAccount.id

        val tx = LedgerTransaction(
            timestamp = timestamp,
            description = title.ifBlank { categoryName },
            isRecurring = isRecurring,
            recurringFrequency = frequency
        )
        return recordBalancedPosting(tx, debitId, creditId, amount, categoryName)
    }

    @Query("""
        SELECT 
            a.id AS accountId,
            a.name AS accountName,
            a.classification AS classification,
            a.subType AS subType,
            COALESCE(SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amount ELSE 0.0 END), 0.0) AS totalDebit,
            COALESCE(SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE 0.0 END), 0.0) AS totalCredit,
            CASE 
                WHEN a.classification IN ('ASSET', 'EXPENSE') 
                THEN COALESCE(SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amount ELSE -e.amount END), 0.0)
                ELSE COALESCE(SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END), 0.0)
            END AS netBalance
        FROM ledger_accounts a
        LEFT JOIN ledger_entries e ON a.id = e.accountId
        WHERE a.isArchived = 0
        GROUP BY a.id
    """)
    fun observeAccountBalances(): Flow<List<AccountBalanceResult>>

    @Query("SELECT * FROM ledger_transactions ORDER BY timestamp DESC")
    fun observeAllTransactions(): Flow<List<LedgerTransaction>>

    @Query("""
        SELECT 
            t.id AS id,
            t.timestamp AS timestamp,
            t.description AS description,
            e.amount AS amount,
            a.name AS categoryOrAccount,
            t.isRecurring AS isRecurring,
            t.recurringFrequency AS recurringFrequency
        FROM ledger_transactions t
        JOIN ledger_entries e ON t.id = e.transactionId
        JOIN ledger_accounts a ON e.accountId = a.id
        WHERE e.direction = 'DEBIT'
        ORDER BY t.timestamp DESC
    """)
    fun observeTransactionDisplayRows(): Flow<List<TransactionDisplayRow>>

    @Query("DELETE FROM ledger_transactions WHERE id = :txId")
    suspend fun deleteTransactionById(txId: Long)
}
