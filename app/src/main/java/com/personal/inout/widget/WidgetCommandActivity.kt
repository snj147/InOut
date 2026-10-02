package com.personal.inout.widget

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.personal.inout.data.AppDatabase
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultExecutionResult
import com.personal.inout.data.VaultLedgerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetCommandActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.getStringExtra("WIDGET_ACTION")
        val amount = intent.getDoubleExtra("WIDGET_AMOUNT", 0.0)
        val category = intent.getStringExtra("WIDGET_CATEGORY") ?: "General"
        val note = intent.getStringExtra("WIDGET_NOTE") ?: "Quick Widget Flow"

        if (amount <= 0.0 && action != "OPEN_APP") {
            finish()
            return
        }

        val db = AppDatabase.getInstance(applicationContext)
        val prefs = getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE)
        val ledgerEngine = VaultLedgerEngine(db.ledgerDao(), prefs)

        CoroutineScope(Dispatchers.IO).launch {
            val allPockets = db.ledgerDao().getAllActivePocketsSnapshot()
            val liquidAccounts = allPockets.filter { it.type == PocketType.LIQUID }
            val primaryLiquid = liquidAccounts.firstOrNull()

            if (primaryLiquid == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "No liquid account found. Open InOut first.", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@launch
            }

            val nature = when (action) {
                "INFLOW" -> MovementNature.OPERATING_INCOME
                else -> MovementNature.OPERATING_EXPENSE
            }

            val autoSplitEnabled = prefs.getBoolean("auto_split_debit", false)

            val result = ledgerEngine.recordMovement(
                movementNature = nature,
                sourcePocketId = primaryLiquid.id,
                targetPocketId = null,
                amount = amount,
                category = category,
                description = note,
                timestamp = System.currentTimeMillis(),
                autoSplitEnabled = autoSplitEnabled
            )

            VaultWidgetProvider.updateAllWidgets(applicationContext)

            withContext(Dispatchers.Main) {
                when (result) {
                    is VaultExecutionResult.Success -> {
                        Toast.makeText(applicationContext, "Logged ₹${amount.toInt()} ($category)", Toast.LENGTH_SHORT).show()
                    }
                    is VaultExecutionResult.OverdraftError -> {
                        Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
                    }
                    is VaultExecutionResult.DuplicateWarning -> {
                        Toast.makeText(applicationContext, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
                finish()
            }
        }
    }
}
