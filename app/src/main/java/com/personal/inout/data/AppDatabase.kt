package com.personal.inout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        LedgerAccount::class,
        LedgerTransaction::class,
        LedgerEntry::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inout_ledger.db"
                )
                .fallbackToDestructiveMigration() // Use structured migration once live on Play Store
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedDefaultAccounts(getInstance(context).ledgerDao())
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedDefaultAccounts(dao: LedgerDao) {
            // Default Asset Accounts
            val cash = dao.insertAccount(LedgerAccount(name = "Cash", classification = AccountClassification.ASSET, subType = "CASH"))
            dao.insertAccount(LedgerAccount(name = "Bank Account", classification = AccountClassification.ASSET, subType = "BANK"))

            // Equity (Starting Balances)
            val equity = dao.insertAccount(LedgerAccount(name = "Opening Equity", classification = AccountClassification.EQUITY, isSystemCategory = true))

            // Default Expense Categories
            listOf("Food & Dining", "Groceries", "Transport & Fuel", "Shopping", "Bills & Utilities", "Health", "Entertainment").forEach { cat ->
                dao.insertAccount(LedgerAccount(name = cat, classification = AccountClassification.EXPENSE, subType = "CATEGORY", isSystemCategory = true))
            }

            // Default Income Sources
            listOf("Salary", "Freelance", "Investment Returns", "Gifts").forEach { inc ->
                dao.insertAccount(LedgerAccount(name = inc, classification = AccountClassification.REVENUE, subType = "CATEGORY", isSystemCategory = true))
            }

            // Seed initial 0 reconciliation leg
            dao.recordBalancedPosting(
                transaction = LedgerTransaction(description = "Initial Ledger Open"),
                debitAccountId = cash,
                creditAccountId = equity,
                amount = 0.01,
                memo = "System Genesis Record"
            )
        }
    }
}
