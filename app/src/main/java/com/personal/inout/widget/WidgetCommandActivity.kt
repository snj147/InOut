package com.personal.inout.widget

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.*
import com.personal.inout.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetCommandActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(applicationContext)
        val prefs = getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE)
        val ledgerEngine = VaultLedgerEngine(db.stateFlowDao(), prefs)

        setContent {
            val scope = rememberCoroutineScope()
            var commandText by remember { mutableStateOf("") }
            val rawPockets by db.stateFlowDao().observeAllActivePockets().collectAsState(initial = emptyList())

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C1A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color(0xFFE59C5C),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Quick HUD Command",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF888888)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2B2826))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            if (commandText.isEmpty()) {
                                Text(
                                    "e.g. coffee 120 sbi, collect 2000 from ABC",
                                    color = Color(0xFF888888),
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = commandText,
                                onValueChange = { commandText = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(Color(0xFFE59C5C)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    executeWidgetCommand(
                                        this@WidgetCommandActivity,
                                        commandText,
                                        rawPockets,
                                        ledgerEngine,
                                        db,
                                        prefs,
                                        scope
                                    )
                                }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                executeWidgetCommand(
                                    this@WidgetCommandActivity,
                                    commandText,
                                    rawPockets,
                                    ledgerEngine,
                                    db,
                                    prefs,
                                    scope
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE59C5C))
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = null,
                                tint = Color(0xFF1C1917),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Commit Transaction",
                                color = Color(0xFF1C1917),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    private fun executeWidgetCommand(
        activity: Activity,
        text: String,
        rawPockets: List<VaultPocket>,
        ledgerEngine: VaultLedgerEngine,
        db: AppDatabase,
        prefs: android.content.SharedPreferences,
        scope: CoroutineScope
    ) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val parsed = NaturalLanguageParser.parse(trimmed, rawPockets, activity)
        if (parsed == null) {
            Toast.makeText(activity, "Command syntax not recognized", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch(Dispatchers.IO) {
            val autoSplit = prefs.getBoolean("auto_split_debit", false)
            when (parsed) {
                is ParsedIntent.Transaction -> {
                    val defaultLiquid = rawPockets.firstOrNull { it.pocketType == PocketType.LIQUID }?.id ?: 1L
                    var sourceId = parsed.matchedPocketId
                    var targetId = parsed.targetPocketId

                    if (parsed.nature == MovementNature.INFLOW) {
                        sourceId = null
                        targetId = parsed.targetPocketId ?: parsed.matchedPocketId ?: defaultLiquid
                    } else if (parsed.nature == MovementNature.OUTFLOW && sourceId == null) {
                        sourceId = defaultLiquid
                    }

                    if (parsed.targetPersonName != null) {
                        var personPocket = rawPockets.firstOrNull {
                            it.pocketType == PocketType.COUNTERPARTY && it.name.equals(parsed.targetPersonName, ignoreCase = true)
                        }
                        if (personPocket == null) {
                            val newId = db.stateFlowDao().insertPocket(
                                VaultPocket(name = parsed.targetPersonName, pocketType = PocketType.COUNTERPARTY, subType = "PEER")
                            )
                            personPocket = VaultPocket(id = newId, name = parsed.targetPersonName, pocketType = PocketType.COUNTERPARTY, subType = "PEER")
                        }

                        val liquidId = sourceId ?: defaultLiquid
                        when (parsed.nature) {
                            MovementNature.PEER_LEND -> {
                                sourceId = liquidId
                                targetId = personPocket.id
                            }
                            MovementNature.PEER_COLLECT -> {
                                sourceId = personPocket.id
                                targetId = liquidId
                            }
                            MovementNature.PEER_BORROW -> {
                                sourceId = personPocket.id
                                targetId = liquidId
                            }
                            MovementNature.PEER_REPAY -> {
                                sourceId = liquidId
                                targetId = personPocket.id
                            }
                            else -> {}
                        }
                    }

                    val res = ledgerEngine.recordMovement(
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
                    )

                    withContext(Dispatchers.Main) {
                        when (res) {
                            is VaultExecutionResult.Success -> {
                                Toast.makeText(activity, res.summary, Toast.LENGTH_SHORT).show()
                                activity.finish()
                            }
                            is VaultExecutionResult.OverdraftError -> {
                                Toast.makeText(activity, res.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                is ParsedIntent.CreateAccount -> {
                    db.stateFlowDao().insertPocket(
                        VaultPocket(
                            name = parsed.name,
                            pocketType = parsed.type,
                            subType = parsed.type.name,
                            creditLimit = 0.0,
                            targetAmount = 0.0,
                            targetDateEpoch = 0L
                        )
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Created ${parsed.name}", Toast.LENGTH_SHORT).show()
                        activity.finish()
                    }
                }
                is ParsedIntent.SetDailyBurn -> {
                    prefs.edit().putFloat("daily_burn_ceiling", parsed.newRate.toFloat()).apply()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Daily burn updated to ₹${parsed.newRate.toInt()}", Toast.LENGTH_SHORT).show()
                        activity.finish()
                    }
                }
                else -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Command executed", Toast.LENGTH_SHORT).show()
                        activity.finish()
                    }
                }
            }
        }
    }
}
