package com.personal.inout.widget

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.personal.inout.data.*
import com.personal.inout.ui.*
import com.personal.inout.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetCommandActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(applicationContext)
        val prefs = getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE)
        val ledgerEngine = VaultLedgerEngine(db.stateFlowDao(), prefs)

        setContent {
            var inputText by remember { mutableStateOf("") }
            var isProcessing by remember { mutableStateOf(false) }
            val autoSplitEnabled = remember { prefs.getBoolean("auto_split_debit", false) }

            val amberAccent = Color(0xFFE59C5C)
            val darkSurface = Color(0xFF1E1C1A)
            val darkBg = Color(0xFF141211)

            Dialog(onDismissRequest = { finish() }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = darkSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .border(1.5.dp, amberAccent.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                        .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = amberAccent.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(amberAccent.copy(alpha = 0.2f))
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = amberAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        "Quick Terminal",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Instant Vault Input",
                                        color = Color(0xFF888888),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            IconButton(
                                onClick = { finish() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF888888),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Input Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(darkBg)
                                .border(1.dp, Color(0xFF33302C), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "e.g. coffee 120 cash, transf 2000 sbi idfc",
                                    color = Color(0xFF666666),
                                    fontSize = 12.5.sp
                                )
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(amberAccent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (inputText.isNotBlank() && !isProcessing) {
                                            isProcessing = true
                                            executeWidgetCommand(
                                                context = this@WidgetCommandActivity,
                                                commandText = inputText,
                                                db = db,
                                                ledgerEngine = ledgerEngine,
                                                autoSplitEnabled = autoSplitEnabled,
                                                onComplete = { successMsg, errorMsg ->
                                                    isProcessing = false
                                                    if (successMsg != null) {
                                                        Toast.makeText(this@WidgetCommandActivity, successMsg, Toast.LENGTH_SHORT).show()
                                                        finish()
                                                    } else if (errorMsg != null) {
                                                        Toast.makeText(this@WidgetCommandActivity, errorMsg, Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { finish() }) {
                                Text("Cancel", color = Color(0xFF888888), fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (inputText.isNotBlank() && !isProcessing) {
                                        isProcessing = true
                                        executeWidgetCommand(
                                            context = this@WidgetCommandActivity,
                                            commandText = inputText,
                                            db = db,
                                            ledgerEngine = ledgerEngine,
                                            autoSplitEnabled = autoSplitEnabled,
                                            onComplete = { successMsg, errorMsg ->
                                                isProcessing = false
                                                if (successMsg != null) {
                                                    Toast.makeText(this@WidgetCommandActivity, successMsg, Toast.LENGTH_SHORT).show()
                                                    finish()
                                                } else if (errorMsg != null) {
                                                    Toast.makeText(this@WidgetCommandActivity, errorMsg, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = amberAccent),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isProcessing
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        color = darkBg,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Send,
                                        contentDescription = null,
                                        tint = darkBg,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Commit",
                                        color = darkBg,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun executeWidgetCommand(
        context: Context,
        commandText: String,
        db: AppDatabase,
        ledgerEngine: VaultLedgerEngine,
        autoSplitEnabled: Boolean,
        onComplete: (success: String?, error: String?) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val activePockets = db.stateFlowDao().observeAllActivePockets().first()
                val parsed = NaturalLanguageParser.parse(commandText, activePockets, context)

                if (parsed == null) {
                    withContext(Dispatchers.Main) {
                        onComplete(null, "Syntax not recognized.")
                    }
                    return@launch
                }

                when (parsed) {
                    is ParsedIntent.Transaction -> {
                        var sourceId: Long? = parsed.matchedPocketId
                        var targetId: Long? = parsed.targetPocketId

                        if (parsed.nature == MovementNature.INFLOW) {
                            sourceId = null
                            targetId = parsed.matchedPocketId
                        }

                        if (parsed.targetPersonName != null) {
                            var personPocket = activePockets.firstOrNull {
                                it.pocketType == PocketType.COUNTERPARTY && it.name.equals(parsed.targetPersonName, ignoreCase = true)
                            }
                            if (personPocket == null) {
                                val newId = db.stateFlowDao().insertPocket(
                                    VaultPocket(
                                        name = parsed.targetPersonName,
                                        pocketType = PocketType.COUNTERPARTY,
                                        subType = "PEER"
                                    )
                                )
                                personPocket = VaultPocket(
                                    id = newId,
                                    name = parsed.targetPersonName,
                                    pocketType = PocketType.COUNTERPARTY,
                                    subType = "PEER"
                                )
                            }

                            when (parsed.nature) {
                                MovementNature.PEER_LEND -> {
                                    sourceId = parsed.matchedPocketId
                                    targetId = personPocket.id
                                }
                                MovementNature.PEER_COLLECT -> {
                                    sourceId = personPocket.id
                                    targetId = parsed.matchedPocketId
                                }
                                MovementNature.PEER_BORROW -> {
                                    sourceId = personPocket.id
                                    targetId = parsed.matchedPocketId
                                }
                                MovementNature.PEER_REPAY -> {
                                    sourceId = parsed.matchedPocketId
                                    targetId = personPocket.id
                                }
                                else -> {}
                            }
                        }

                        val result = ledgerEngine.recordMovement(
                            nature = parsed.nature,
                            sourcePocketId = sourceId,
                            targetPocketId = targetId,
                            amount = parsed.amount,
                            category = parsed.category,
                            note = parsed.merchant,
                            timestamp = parsed.timestamp,
                            autoSplitEnabled = autoSplitEnabled,
                            isRecurring = parsed.isRecurring,
                            frequency = parsed.frequency
                        )

                        withContext(Dispatchers.Main) {
                            when (result) {
                                is VaultExecutionResult.OverdraftError -> onComplete(null, result.message)
                                is VaultExecutionResult.Success -> onComplete(result.summary, null)
                            }
                        }
                    }

                    is ParsedIntent.CompoundTransactions -> {
                        var successCount = 0
                        for (sub in parsed.transactions) {
                            val res = ledgerEngine.recordMovement(
                                nature = sub.nature,
                                sourcePocketId = sub.matchedPocketId,
                                targetPocketId = sub.targetPocketId,
                                amount = sub.amount,
                                category = sub.category,
                                note = sub.merchant,
                                autoSplitEnabled = autoSplitEnabled
                            )
                            if (res is VaultExecutionResult.Success) successCount++
                        }
                        withContext(Dispatchers.Main) {
                            onComplete("Recorded $successCount transactions", null)
                        }
                    }

                    is ParsedIntent.TriangularSettle -> {
                        val res = ledgerEngine.triangularPeerSettle(parsed.debtor, parsed.creditor, parsed.amount)
                        withContext(Dispatchers.Main) {
                            when (res) {
                                is VaultExecutionResult.OverdraftError -> onComplete(null, res.message)
                                is VaultExecutionResult.Success -> onComplete(res.summary, null)
                            }
                        }
                    }

                    is ParsedIntent.SetDailyBurn -> {
                        val prefs = context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putFloat("daily_burn_ceiling", parsed.newRate.toFloat()).apply()
                        withContext(Dispatchers.Main) {
                            onComplete("Daily Burn set to ₹${parsed.newRate.toInt()}/day", null)
                        }
                    }

                    is ParsedIntent.SaveMacroAlias -> {
                        val macroPrefs = context.getSharedPreferences("vault_macros", Context.MODE_PRIVATE)
                        macroPrefs.edit().putString(parsed.alias, parsed.fullCommand).apply()
                        withContext(Dispatchers.Main) {
                            onComplete("Macro '${parsed.alias}' saved", null)
                        }
                    }

                    is ParsedIntent.StageDesire -> {
                        db.stateFlowDao().insertStagedDesire(
                            com.personal.inout.data.StagedDesire(name = parsed.name, amount = parsed.amount)
                        )
                        withContext(Dispatchers.Main) {
                            onComplete("Staged '${parsed.name}' in cool-off quarantine", null)
                        }
                    }

                    is ParsedIntent.BreakGoalPot -> {
                        val goal = activePockets.firstOrNull { it.pocketType == PocketType.SAVING_GOAL && it.name.equals(parsed.potName, ignoreCase = true) }
                        if (goal != null) {
                            val balances = db.stateFlowDao().observePocketBalances().first()
                            val bal = balances.firstOrNull { it.pocketId == goal.id.toString() }?.computedBalance ?: 0.0
                            if (bal > 0 && parsed.destinationPocketId != null) {
                                ledgerEngine.recordMovement(
                                    nature = MovementNature.TRANSFER,
                                    sourcePocketId = goal.id,
                                    targetPocketId = parsed.destinationPocketId,
                                    amount = bal,
                                    category = "Savings Pot",
                                    note = "Broken Pot Funds Return"
                                )
                            }
                            db.stateFlowDao().updatePocket(goal.copy(isArchived = true))
                            withContext(Dispatchers.Main) {
                                onComplete("Pot '${goal.name}' broken. Returned ₹${bal.toInt()}", null)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                onComplete(null, "Goal pot '${parsed.potName}' not found")
                            }
                        }
                    }

                    is ParsedIntent.CreateAccount -> {
                        db.stateFlowDao().insertPocket(
                            VaultPocket(
                                name = parsed.name,
                                pocketType = parsed.type,
                                subType = parsed.type.name,
                                creditLimit = parsed.limit,
                                targetAmount = parsed.targetAmount,
                                targetDateEpoch = parsed.targetDateEpoch
                            )
                        )
                        withContext(Dispatchers.Main) {
                            onComplete("Created account '${parsed.name}'", null)
                        }
                    }

                    else -> {
                        withContext(Dispatchers.Main) {
                            onComplete("Command processed", null)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(null, "Error: ${e.localizedMessage}")
                }
            }
        }
    }
}
