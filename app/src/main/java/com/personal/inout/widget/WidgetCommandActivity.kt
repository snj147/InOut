package com.personal.inout.widget

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.personal.inout.data.AppDatabase
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
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
        val ledgerEngine = VaultLedgerEngine(db.stateFlowDao(), prefs)

        CoroutineScope(Dispatchers.IO).launch {
            val liquidAccounts = db.stateFlowDao().getAllActivePocketsSync().filter { it.pocketType == PocketType.LIQUID }
            val primaryLiquid = liquidAccounts.firstOrNull()

            if (primaryLiquid == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "No liquid account found. Open app first.", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@launch
            }

            val nature = when (action) {
                "INFLOW" -> MovementNature.INFLOW
                else -> MovementNature.OUTFLOW
            }

            val srcId = if (nature == MovementNature.OUTFLOW) primaryLiquid.id else null
            val tgtId = if (nature == MovementNature.INFLOW) primaryLiquid.id else null

            ledgerEngine.recordMovement(
                nature = nature,
                sourcePocketId = srcId,
                targetPocketId = tgtId,
                amount = amount,
                category = category,
                note = note,
                timestamp = System.currentTimeMillis()
            )

            VaultWidgetProvider.updateAllWidgets(applicationContext)

            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "Recorded ₹${amount.toInt()} ($category)", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
