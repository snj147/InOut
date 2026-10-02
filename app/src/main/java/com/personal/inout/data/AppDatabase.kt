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
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = getInstance(context).ledgerDao()
                                // Strict guard: Only insert Cash in Hand if it doesn't already exist
                                val existing = dao.getDefaultCashInHandPocket()
                                if (existing == null) {
                                    dao.insertPocket(
                                        LedgerPocket(
                                            name = "Cash in Hand",
                                            type = PocketType.LIQUID,
                                            currency = "INR"
                                        )
                                    )
                                }
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = getInstance(context).ledgerDao()
                                // Deduplicate on open: Ensure only one active "Cash in Hand" exists
                                val pockets = dao.getAllActivePocketsSnapshot()
                                val cashPockets = pockets.filter { it.name.equals("Cash in Hand", ignoreCase = true) }
                                if (cashPockets.size > 1) {
                                    // Keep the first, archive duplicate spares
                                    cashPockets.drop(1).forEach { dup ->
                                        val bal = dao.computePocketBalance(dup.id)
                                        if (bal == 0.0) {
                                            dao.updatePocket(dup.copy(isArchived = true))
                                        }
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
