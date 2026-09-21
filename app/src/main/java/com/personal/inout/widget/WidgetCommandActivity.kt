package com.personal.inout.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.*
import com.personal.inout.ui.AmberTheme
import com.personal.inout.util.NaturalLanguageParser
import com.personal.inout.util.ParsedIntent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetCommandActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(applicationContext)
        val prefs = getSharedPreferences("inout_app_prefs", MODE_PRIVATE)
        val engine = VaultLedgerEngine(db.stateFlowDao(), prefs)

        setContent {
            var input by remember { mutableStateOf("") }
            val theme = AmberTheme

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, theme.accent, RoundedCornerShape(16.dp))
                        .padding(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = theme.accent)
                            Text("Fast Vault Entry", color = theme.textBright, fontSize = 14.sp)
                        }

                        TextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("e.g. Spent 400 for groceries at reliance", color = theme.textMuted, fontSize = 12.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = theme.textBright,
                                unfocusedTextColor = theme.textBright
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                submitInput(input, db, engine)
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { finish() }) {
                                Text("Cancel", color = theme.textMuted)
                            }
                            Button(
                                onClick = { submitInput(input, db, engine) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                            ) {
                                Text("Record", color = theme.bg)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun submitInput(input: String, db: AppDatabase, engine: VaultLedgerEngine) {
        if (input.isBlank()) {
            finish()
            return
        }

        val prefs = getSharedPreferences("inout_app_prefs", MODE_PRIVATE)
        val autoSplit = prefs.getBoolean("auto_split_debit", false)

        kotlinx.coroutines.MainScope().launch {
            val rawPockets = db.stateFlowDao().observeAllActivePockets().first()
            val parsed = NaturalLanguageParser.parse(input, rawPockets, applicationContext)

            if (parsed == null) {
                Toast.makeText(applicationContext, "Syntax not recognized. Try: 'Spent 400 for groceries'", Toast.LENGTH_LONG).show()
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
                        var personPocket = rawPockets.firstOrNull {
                            it.pocketType == PocketType.COUNTERPARTY && it.name.equals(parsed.targetPersonName, ignoreCase = true)
                        }
                        if (personPocket == null) {
                            val newId = db.stateFlowDao().insertPocket(
                                VaultPocket(name = parsed.targetPersonName, pocketType = PocketType.COUNTERPARTY, subType = "PEER")
                            )
                            personPocket = VaultPocket(id = newId, name = parsed.targetPersonName, pocketType = PocketType.COUNTERPARTY)
                        }
                        val targetPocketId = personPocket.id
                        if (parsed.nature == MovementNature.PEER_LEND) {
                            sourceId = parsed.matchedPocketId
                            targetId = targetPocketId
                        } else {
                            sourceId = targetPocketId
                            targetId = parsed.matchedPocketId
                        }
                    }

                    when (val res = engine.recordMovement(
                        nature = parsed.nature,
                        sourcePocketId = sourceId,
                        targetPocketId = targetId,
                        amount = parsed.amount,
                        category = parsed.category,
                        note = parsed.merchant,
                        timestamp = parsed.timestamp,
                        autoSplitEnabled = autoSplit,
                        isRecurring = parsed.isRecurring,
                        frequency = parsed.frequency
                    )) {
                        is VaultExecutionResult.OverdraftError -> Toast.makeText(applicationContext, res.message, Toast.LENGTH_LONG).show()
                        is VaultExecutionResult.Success -> {
                            Toast.makeText(applicationContext, res.summary, Toast.LENGTH_SHORT).show()
                            finish()
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
                    Toast.makeText(applicationContext, "Account '${parsed.name}' created", Toast.LENGTH_SHORT).show()
                    finish()
                }
                is ParsedIntent.SetDailyBurn -> {
                    prefs.edit().putFloat("daily_burn_ceiling", parsed.newRate.toFloat()).apply()
                    Toast.makeText(applicationContext, "Daily Burn set to ₹${parsed.newRate.toInt()}/day", Toast.LENGTH_SHORT).show()
                    finish()
                }
                is ParsedIntent.StageDesire -> {
                    db.stateFlowDao().insertStagedDesire(
                        StagedDesire(name = parsed.name, amount = parsed.amount)
                    )
                    Toast.makeText(applicationContext, "Staged '${parsed.name}' in cool-off quarantine", Toast.LENGTH_SHORT).show()
                    finish()
                }
                is ParsedIntent.TriangularSettle -> {
                    when (val res = engine.triangularPeerSettle(parsed.debtor, parsed.creditor, parsed.amount)) {
                        is VaultExecutionResult.OverdraftError -> Toast.makeText(applicationContext, res.message, Toast.LENGTH_LONG).show()
                        is VaultExecutionResult.Success -> {
                            Toast.makeText(applicationContext, res.summary, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
                is ParsedIntent.SaveMacroAlias -> {
                    val macroPrefs = getSharedPreferences("vault_macros", MODE_PRIVATE)
                    macroPrefs.edit().putString(parsed.alias, parsed.fullCommand).apply()
                    Toast.makeText(applicationContext, "Macro '${parsed.alias}' saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
