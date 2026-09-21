package com.personal.inout.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.FlowRecord
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketBalanceSummary
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountPocketsView(
    pocketBalances: List<PocketBalanceSummary> = emptyList(),
    rawPockets: List<VaultPocket> = emptyList(),
    recurringSchedules: List<FlowRecord> = emptyList(),
    isPrivacyMode: Boolean = false,
    onTransactPocket: (pocket: VaultPocket) -> Unit = {},
    onEditPocket: (pocket: VaultPocket) -> Unit = {},
    onDeletePocketSafe: (pocket: VaultPocket, balance: Double) -> Unit = { _, _ -> },
    onTogglePauseRecurring: (schedule: FlowRecord) -> Unit = {},
    onEditRecurring: (schedule: FlowRecord) -> Unit = {},
    onDeleteRecurringSafe: (schedule: FlowRecord) -> Unit = {},
    onRecordCardSettlement: (cardId: Long, liquidId: Long, amt: Double) -> Unit = { _, _, _ -> },
    onPeerAction: (nature: MovementNature, peerId: Long, liquidId: Long, amt: Double) -> Unit = { _, _, _, _ -> }
) {
    var pocketToDelete by remember { mutableStateOf<Pair<VaultPocket, Double>?>(null) }
    var recurringToDelete by remember { mutableStateOf<FlowRecord?>(null) }
    val defaultLiquidId = remember(rawPockets) {
        rawPockets.firstOrNull { it.pocketType == PocketType.LIQUID }?.id ?: 0L
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Cash & Bank Accounts
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cash & Bank Accounts", color = Color(0xFFCCCCCC), fontSize = 14.sp)
                Text("${pocketBalances.count { it.pocketType == PocketType.LIQUID }}", color = Color(0xFF666666), fontSize = 13.sp)
            }
        }
        items(pocketBalances.filter { it.pocketType == PocketType.LIQUID }, key = { it.pocketId }) { summary ->
            val pocket = rawPockets.firstOrNull { it.id.toString() == summary.pocketId } ?: VaultPocket(id = summary.pocketId.toLongOrNull() ?: 0L, name = summary.name, pocketType = PocketType.LIQUID)
            PocketCard(
                name = summary.name,
                subtitle = "LIQUID",
                balanceText = if (isPrivacyMode) "••••" else "₹${String.format("%,.0f", summary.computedBalance)}",
                actionLabel = "Transact",
                actionColor = Color(0xFF332B22),
                textColor = Color(0xFFE59C5C),
                onActionClick = { onTransactPocket(pocket) },
                onLongClick = { onEditPocket(pocket) }
            )
        }

        // 2. Goal Pots (Saving Goals)
        val goalList = pocketBalances.filter { it.pocketType == PocketType.SAVING_GOAL }
        if (goalList.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Goal Pots (Target Savings)", color = Color(0xFFCCCCCC), fontSize = 14.sp)
                    Text("${goalList.size}", color = Color(0xFF666666), fontSize = 13.sp)
                }
            }
            items(goalList, key = { it.pocketId }) { pot ->
                val progress = if (pot.targetAmount > 0) (pot.computedBalance / pot.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C1A))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(pot.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (isPrivacyMode) "••••" else "₹${String.format("%,.0f", pot.computedBalance)} / ₹${String.format("%,.0f", pot.targetAmount)}",
                                color = Color(0xFFE59C5C),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Color(0xFFE59C5C),
                            trackColor = Color(0xFF2B2826)
                        )
                    }
                }
            }
        }

        // 3. Cards & Loans
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cards & Loans (CC / Dues)", color = Color(0xFFCCCCCC), fontSize = 14.sp)
                Text("${pocketBalances.count { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE }}", color = Color(0xFF666666), fontSize = 13.sp)
            }
        }
        items(pocketBalances.filter { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE }, key = { it.pocketId }) { summary ->
            val pocket = rawPockets.firstOrNull { it.id.toString() == summary.pocketId } ?: VaultPocket(id = summary.pocketId.toLongOrNull() ?: 0L, name = summary.name, pocketType = summary.pocketType)
            PocketCard(
                name = summary.name,
                subtitle = if (isPrivacyMode) "••••" else "Avail: ₹${String.format("%,.0f", summary.creditLimit + summary.computedBalance)} / Limit: ₹${String.format("%,.0f", summary.creditLimit)}",
                balanceText = if (isPrivacyMode) "••••" else "₹${String.format("%,.0f", Math.abs(summary.computedBalance))}",
                actionLabel = if (summary.computedBalance >= 0) "Settled" else "Pay Due",
                actionColor = Color(0xFF282522),
                textColor = Color(0xFF9E9E9E),
                onActionClick = {
                    if (summary.computedBalance < 0 && defaultLiquidId != 0L) {
                        onRecordCardSettlement(pocket.id, defaultLiquidId, Math.abs(summary.computedBalance))
                    } else {
                        onTransactPocket(pocket)
                    }
                },
                onLongClick = { onEditPocket(pocket) }
            )
        }

        // 4. Counterparties (People)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("People (Owed & Lent)", color = Color(0xFFCCCCCC), fontSize = 14.sp)
                Text("${pocketBalances.count { it.pocketType == PocketType.PEER || it.pocketType == PocketType.COUNTERPARTY || it.subType == "PEER" }}", color = Color(0xFF666666), fontSize = 13.sp)
            }
        }
        items(pocketBalances.filter { it.pocketType == PocketType.PEER || it.pocketType == PocketType.COUNTERPARTY || it.subType == "PEER" }, key = { it.pocketId }) { summary ->
            val pocket = rawPockets.firstOrNull { it.id.toString() == summary.pocketId } ?: VaultPocket(id = summary.pocketId.toLongOrNull() ?: 0L, name = summary.name, pocketType = summary.pocketType)
            val isOwedToYou = summary.computedBalance > 0
            PocketCard(
                name = summary.name,
                subtitle = if (isOwedToYou) "They owe you" else "You owe them",
                balanceText = if (isPrivacyMode) "••••" else "₹${String.format("%,.0f", Math.abs(summary.computedBalance))}",
                actionLabel = if (isOwedToYou) "Collect" else "Repay",
                actionColor = if (isOwedToYou) Color(0xFF1E3326) else Color(0xFF332020),
                textColor = if (isOwedToYou) Color(0xFF81C784) else Color(0xFFE57373),
                onActionClick = {
                    val nature = if (isOwedToYou) MovementNature.PEER_COLLECT else MovementNature.PEER_REPAY
                    if (defaultLiquidId != 0L && summary.computedBalance != 0.0) {
                        onPeerAction(nature, pocket.id, defaultLiquidId, Math.abs(summary.computedBalance))
                    } else {
                        onTransactPocket(pocket)
                    }
                },
                onLongClick = { onEditPocket(pocket) }
            )
        }

        // 5. Recurring Rules Lifecycle List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recurring Rules Lifecycle", color = Color(0xFFCCCCCC), fontSize = 14.sp)
                Text("${recurringSchedules.size}", color = Color(0xFF666666), fontSize = 13.sp)
            }
        }
        items(recurringSchedules, key = { it.id }) { record ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C1A))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = record.note.ifBlank { record.category },
                                color = if (record.isPaused) Color(0xFF888888) else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (record.isPaused) {
                                Box(modifier = Modifier.background(Color(0xFF3A352F), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("PAUSED", color = Color(0xFFE59C5C), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        val freqText = if (record.frequency != "NONE") record.frequency else record.recurringCadence
                        Text(
                            text = if (isPrivacyMode) "Repeats $freqText • ₹••••" else "Repeats $freqText • ₹${record.amount.toInt()}",
                            color = if (record.isPaused) Color(0xFF666666) else Color(0xFFE59C5C),
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { onTogglePauseRecurring(record) }) {
                            Icon(
                                imageVector = if (record.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (record.isPaused) "Resume" else "Pause",
                                tint = if (record.isPaused) Color(0xFF81C784) else Color(0xFFE59C5C)
                            )
                        }
                        IconButton(onClick = { onEditRecurring(record) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFFCCCCCC))
                        }
                        IconButton(onClick = { recurringToDelete = record }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE57373))
                        }
                    }
                }
            }
        }
    }

    pocketToDelete?.let { (pocket, bal) ->
        AlertDialog(
            onDismissRequest = { pocketToDelete = null },
            containerColor = Color(0xFF262320),
            title = { Text("Delete Account", color = Color.White) },
            text = { Text("Are you sure you want to delete '${pocket.name}'? Balance is ₹${bal.toInt()}.", color = Color(0xFFCCCCCC)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePocketSafe(pocket, bal)
                        pocketToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { pocketToDelete = null }) { Text("Cancel", color = Color(0xFF9E9E9E)) } }
        )
    }

    recurringToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recurringToDelete = null },
            containerColor = Color(0xFF262320),
            title = { Text("Delete Recurring Rule", color = Color.White) },
            text = { Text("Are you sure you want to delete this schedule template? Past recorded transactions will not be touched.", color = Color(0xFFCCCCCC)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRecurringSafe(record)
                        recurringToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) { Text("Delete Rule", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { recurringToDelete = null }) { Text("Cancel", color = Color(0xFF9E9E9E)) } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PocketCard(
    name: String,
    subtitle: String,
    balanceText: String,
    actionLabel: String,
    actionColor: Color,
    textColor: Color,
    onActionClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C1A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = Color(0xFF888888), fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(balanceText, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onActionClick,
                    colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(actionLabel, color = textColor, fontSize = 12.sp)
                }
            }
        }
    }
}
