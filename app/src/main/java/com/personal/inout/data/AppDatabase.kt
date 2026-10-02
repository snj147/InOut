package com.personal.inout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        LedgerPocket::class,
        LedgerTransaction::class,
        CategoryBudget::class,
        LedgerAuditEntry::class,
        SystemNotice::class
    ],
    version = 5,
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
                    "inout_vault_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Clean slate rule: Zero auto-created cash accounts on initialization
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = getInstance(context).ledgerDao()
                                // Purge unseeded duplicate or empty cash accounts from previous runs
                                val pockets = dao.getAllActivePocketsSnapshot()
                                val emptyAutoCash = pockets.filter { 
                                    it.name.equals("Cash in Hand", ignoreCase = true) 
                                }
                                emptyAutoCash.forEach { cashPocket ->
                                    val bal = dao.computePocketBalance(cashPocket.id)
                                    if (bal == 0.0) {
                                        dao.updatePocket(cashPocket.copy(isArchived = true))
                                    }
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
