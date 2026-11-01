package com.personal.inout.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LedgerPocket::class,
        LedgerTransaction::class,
        CategoryBudget::class,
        LedgerAuditEntry::class,
        SystemNotice::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `category_budgets` (`categoryName` TEXT NOT NULL, `monthlyLimit` INTEGER NOT NULL, `rolloverMode` TEXT NOT NULL, `accumulatedDelta` INTEGER NOT NULL, PRIMARY KEY(`categoryName`))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_audit_journal` (`auditId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `actionType` TEXT NOT NULL, `entityType` TEXT NOT NULL, `recordId` INTEGER NOT NULL, `preStateJson` TEXT NOT NULL, `postStateJson` TEXT NOT NULL, `reasonNote` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_audit_journal_recordId` ON `ledger_audit_journal` (`recordId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_audit_journal_timestamp` ON `ledger_audit_journal` (`timestamp`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `system_notices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `type` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isRead` INTEGER NOT NULL DEFAULT 0)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ledger_transactions` ADD COLUMN `isSubscription` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ledger_transactions` ADD COLUMN `isRecurring` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ledger_transactions` ADD COLUMN `recurringFrequency` TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE `ledger_transactions` ADD COLUMN `recurringEndDate` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ledger_transactions` ADD COLUMN `originalCurrency` TEXT NOT NULL DEFAULT 'INR'")
                db.execSQL("ALTER TABLE `ledger_transactions` ADD COLUMN `foreignAmount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ledger_transactions` ADD COLUMN `splitParentId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `ledger_transactions` ADD COLUMN `idempotencyHash` TEXT NOT NULL DEFAULT ''")
                
                db.execSQL("ALTER TABLE `ledger_pockets` ADD COLUMN `currency` TEXT NOT NULL DEFAULT 'INR'")
                db.execSQL("ALTER TABLE `ledger_pockets` ADD COLUMN `creditLimit` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ledger_pockets` ADD COLUMN `statementClosingDay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ledger_pockets` ADD COLUMN `billDueDay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ledger_pockets` ADD COLUMN `targetGoalAmount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ledger_pockets` ADD COLUMN `goalTargetDate` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ledger_pockets` ADD COLUMN `peerContactName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `ledger_pockets` ADD COLUMN `lastReconciledEpoch` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `ledger_pockets` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inout_vault_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
