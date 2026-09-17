package com.personal.inout.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactionRecord(tx: LedgerTransaction): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(entries: List<LedgerEntry>)

    @Transaction
    suspend fun recordBalancedPosting(
        transaction: LedgerTransaction,
        debitAccountId: Long,
        creditAccountId: Long,
        amount: Double,
        memo: String = ""
    ): Long {
        require(amount > 0.0) { "Transaction amount must be positive" }
        require(debitAccountId != creditAccountId) { "Debit and Credit accounts must be distinct" }

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

    /**
     * Mathematical balance invariant:
     * Assets & Expenses normally carry Debit balances: Balance = (Debits - Credits)
     * Liabilities, Equity & Revenues carry Credit balances: Balance = (Credits - Debits)
     */
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

    @Query("""
        SELECT 
            t.id,
            t.timestamp,
            t.description,
            t.isTaxDeductible,
            t.isRecurring,
            t.recurringFrequency,
            t.receiptUri
        FROM ledger_transactions t
        ORDER BY t.timestamp DESC
    """)
    fun observeAllTransactions(): Flow<List<LedgerTransaction>>

    @Query("DELETE FROM ledger_transactions WHERE id = :txId")
    suspend fun deleteTransactionById(txId: Long)
}
