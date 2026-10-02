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
                    // Upgrades from legacy version 4 schema to institutional 37-rule v5 schema cleanly
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Initialize Rule 36 default Cash in Hand pocket atomically
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).ledgerDao().insertPocket(
                                    LedgerPocket(
                                        name = "Cash in Hand",
                                        type = PocketType.LIQUID,
                                        currency = "INR"
                                    )
                                )
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
