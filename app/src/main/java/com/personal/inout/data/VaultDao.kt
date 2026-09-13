package com.personal.inout.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun getAllAccounts(): Flow<List<Account>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Update
    suspend fun updateAccount(account: Account)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccount(accountId: Long)

    @Query("UPDATE accounts SET isDefault = 0")
    suspend fun clearDefaultAccounts()

    @Query("UPDATE accounts SET isDefault = 1 WHERE id = :accountId")
    suspend fun setDefaultAccount(accountId: Long)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Insert
    suspend fun insertTransaction(transaction: Transaction): Long

    @Query("DELETE FROM transactions WHERE id = :txId")
    suspend fun deleteTransaction(txId: Long)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    // Counterparties (Lenders & Borrowers)
    @Query("SELECT * FROM counterparties ORDER BY name ASC")
    fun getAllCounterparties(): Flow<List<Counterparty>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCounterparty(party: Counterparty): Long

    @Update
    suspend fun updateCounterparty(party: Counterparty)

    @Query("SELECT * FROM counterparties WHERE id = :id LIMIT 1")
    suspend fun getCounterpartyById(id: Long): Counterparty?

    // SMS Drafts
    @Query("SELECT * FROM sms_drafts ORDER BY timestamp DESC")
    fun getStagedSms(): Flow<List<SmsDraft>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmsDraft(draft: SmsDraft): Long

    @Query("DELETE FROM sms_drafts WHERE id = :draftId")
    suspend fun deleteSmsDraftById(draftId: Long)

    @Query("DELETE FROM sms_drafts")
    suspend fun clearAllSms()
}
