package com.personal.inout.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM accounts")
    fun getAllAccounts(): Flow<List<Account>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Update
    suspend fun updateAccount(account: Account)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 20")
    fun getRecentTransactions(): Flow<List<Transaction>>

    @Insert
    suspend fun insertTransaction(transaction: Transaction): Long

    @Query("SELECT * FROM sms_drafts ORDER BY timestamp DESC")
    fun getStagedSms(): Flow<List<SmsDraft>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmsDraft(draft: SmsDraft): Long

    @Delete
    suspend fun deleteSmsDraft(draft: SmsDraft)
}
