package com.personal.inout.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM accounts")
    fun getAllAccounts(): Flow<List<Account>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account)

    @Update
    suspend fun updateAccount(account: Account)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 50")
    fun getRecentTransactions(): Flow<List<Transaction>>

    @Insert
    suspend fun insertTransaction(tx: Transaction)

    @Query("SELECT * FROM debts WHERE isSilent = 0")
    fun getActiveDebts(): Flow<List<Debt>>

    @Query("SELECT * FROM debts WHERE isSilent = 1")
    fun getSilentDebts(): Flow<List<Debt>>

    @Insert
    suspend fun insertDebt(debt: Debt)

    @Query("SELECT * FROM sms_inbox ORDER BY timestamp DESC")
    fun getStagedSms(): Flow<List<SmsDraft>>

    @Insert
    suspend fun insertSmsDraft(draft: SmsDraft)

    @Delete
    suspend fun deleteSmsDraft(draft: SmsDraft)
}
