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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var peerActionTarget by remember { mutableStateOf<VaultPocket?>(null) }
    var peerActionAmount by remember { mutableStateOf("") }
    var peerSelectedNature by remember { mutableStateOf(MovementNature.PEER_LEND) }

    var pocketToDelete by remember { mutableStateOf<Pair<VaultPocket, Double>?>(null) }
    var recurringToDelete by remember { mutableStateOf<FlowRecord?>(null) }

    val liquidPockets = remember(rawPockets) { rawPockets.filter { it.pocketType == PocketType.LIQUID } }
    val defaultLiquidId = remember(liquidPockets) { liquidPockets.firstOrNull()?.id ?: 0L }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Cash & Bank Accounts
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cash & Bank Accounts", color = Color(0xFFCCCCCC), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text("${pocketBalances.count { it.pocketType == PocketType.LIQUID }}", color = Color(0xFF666666), fontSize = 12.sp)
            }
        }
        items(pocketBalances.filter { it.pocketType == PocketType.LIQUID }, key = { it.pocketId }) { summary ->
            val pocket = rawPockets.firstOrNull { it.id.toString() == summary.pocketId } ?: VaultPocket(id = summary.pocketId.toLongOrNull() ?: 0L, name = summary.name, pocketType = PocketType.LIQUID)
            PocketCard(
                name = summary.name,
                subtitle = "LIQUID ACCOUNT",
                balanceText = if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", summary.computedBalance)}",
                actionLabel = "Transact",
                actionColor = Color(0xFF282420),
                textColor = Color(0xFFE59C5C),
                onActionClick = { onTransactPocket(pocket) },
                onLongClick = { onEditPocket(pocket) }
            )
        }

        // 2. Goal Pots (Saving Goals) with Target Date & Overdue Indicators
        val goalList = pocketBalances.filter { it.pocketType == PocketType.SAVING_GOAL }
        if (goalList.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Goal Pots (Target Savings)", color = Color(0xFFCCCCCC), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("${goalList.size}", color = Color(0xFF666666), fontSize = 12.sp)
                }
            }
            items(goalList, key = { it.pocketId }) { pot ->
                val rawPocket = rawPockets.firstOrNull { it.id.toString() == pot.pocketId } ?: VaultPocket(id = pot.pocketId.toLongOrNull() ?: 0L, name = pot.name, pocketType = PocketType.SAVING_GOAL)
                val progress = if (pot.targetAmount > 0) (pot.computedBalance / pot.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                val shortfall = (pot.targetAmount - pot.computedBalance).coerceAtLeast(0.0)

                val targetDateStr = if (pot.targetDateEpoch > 0) {
                    SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(pot.targetDateEpoch))
                } else "No Date"

                val isOverdue = pot.targetDateEpoch > 0 && System.currentTimeMillis() > pot.targetDateEpoch && shortfall > 0

                Card(
                    modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onTransactPocket(rawPocket) }, onLongClick = { onEditPocket(rawPocket) }),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1917))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(pot.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    if (isOverdue) {
                                        Box(modifier = Modifier.background(Color(0xFF332020), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text("OVERDUE", color = Color(0xFFE57373), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text("Target: $targetDateStr • Remaining: ₹${shortfall.toInt()}", color = if (isOverdue) Color(0xFFE57373) else Color(0xFF888888), fontSize = 11.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (isPrivacyMode) "••••" else "₹${String.format("%,.0f", pot.computedBalance)} / ₹${String.format("%,.0f", pot.targetAmount)}",
                                    color = Color(0xFFE59C5C),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { pocketToDelete = rawPocket to pot.computedBalance },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Break Pot", tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = if (isOverdue) Color(0xFFE57373) else Color(0xFFE59C5C),
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
                Text("Cards & Loans (CC / Dues)", color = Color(0xFFCCCCCC), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text("${pocketBalances.count { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE }}", color = Color(0xFF666666), fontSize = 12.sp)
            }
        }
        items(pocketBalances.filter { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE }, key = { it.pocketId }) { summary ->
            val pocket = rawPockets.firstOrNull { it.id.toString() == summary.pocketId } ?: VaultPocket(id = summary.pocketId.toLongOrNull() ?: 0L, name = summary.name, pocketType = summary.pocketType)
            val hasOutstanding = summary.computedBalance < 0.0

            Card(
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onEditPocket(pocket) }),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1917))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(summary.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (isPrivacyMode) "••••" else "Avail: ₹${String.format("%,.0f", summary.creditLimit + summary.computedBalance)} / Limit: ₹${String.format("%,.0f", summary.creditLimit)}",
                            color = Color(0xFF888888),
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isPrivacyMode) "••••" else "₹${String.format("%,.0f", Math.abs(summary.computedBalance))}",
                            color = if (hasOutstanding) Color(0xFFE57373) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { onTransactPocket(pocket) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282420)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("Transact", color = Color(0xFFE59C5C), fontSize = 11.5.sp)
                        }
                        if (hasOutstanding) {
                            Button(
                                onClick = {
                                    if (defaultLiquidId != 0L) {
                                        onRecordCardSettlement(pocket.id, defaultLiquidId, Math.abs(summary.computedBalance))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF332020)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("Pay Bill", color = Color(0xFFE57373), fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        }

        // 4. People (Owed & Lent) - Permanent Transact Button & Clean Status Pill
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("People (Owed & Lent)", color = Color(0xFFCCCCCC), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text("${pocketBalances.count { it.pocketType == PocketType.PEER || it.pocketType == PocketType.COUNTERPARTY || it.subType == "PEER" }}", color = Color(0xFF666666), fontSize = 12.sp)
            }
        }
        items(pocketBalances.filter { it.pocketType == PocketType.PEER || it.pocketType == PocketType.COUNTERPARTY || it.subType == "PEER" }, key = { it.pocketId }) { summary ->
            val pocket = rawPockets.firstOrNull { it.id.toString() == summary.pocketId } ?: VaultPocket(id = summary.pocketId.toLongOrNull() ?: 0L, name = summary.name, pocketType = summary.pocketType)
            val bal = summary.computedBalance

            Card(
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onEditPocket(pocket) }),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1917))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(summary.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        val subText = when {
                            bal > 0 -> "They owe you"
                            bal < 0 -> "You owe them"
                            else -> "Settled"
                        }
                        Text(subText, color = if (bal > 0) Color(0xFF81C784) else if (bal < 0) Color(0xFFE57373) else Color(0xFF888888), fontSize = 11.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isPrivacyMode) "••••" else "₹${String.format("%,.0f", Math.abs(bal))}",
                            color = when {
                                bal > 0 -> Color(0xFF81C784)
                                bal < 0 -> Color(0xFFE57373)
                                else -> Color(0xFF888888)
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = {
                                peerActionTarget = pocket
                                peerActionAmount = if (bal != 0.0) Math.abs(bal).toInt().toString() else ""
                                peerSelectedNature = if (bal > 0) MovementNature.PEER_COLLECT else if (bal < 0) MovementNature.PEER_REPAY else MovementNature.PEER_LEND
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282420)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("Transact", color = Color(0xFFE59C5C), fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        // 5. Recurring Rules Lifecycle
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recurring Rules Lifecycle", color = Color(0xFFCCCCCC), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text("${recurringSchedules.size}", color = Color(0xFF666666), fontSize = 12.sp)
            }
        }
        items(recurringSchedules, key = { it.id }) { record ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1917))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = record.note.ifBlank { record.category },
                                color = if (record.isPaused) Color(0xFF888888) else Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (record.isPaused) {
                                Box(modifier = Modifier.background(Color(0xFF3A352F), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                    Text("PAUSED", color = Color(0xFFE59C5C), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        val freqText = if (record.frequency != "NONE") record.frequency else record.recurringCadence
                        val dateDayStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(record.timestamp))
                        Text(
                            text = if (isPrivacyMode) "Repeats $freqText • ₹••••" else "Repeats $freqText on $dateDayStr • ₹${record.amount.toInt()}",
                            color = if (record.isPaused) Color(0xFF666666) else Color(0xFFE59C5C),
                            fontSize = 11.5.sp
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

    // Compact Lend / Borrow / Settle Dialog
    peerActionTarget?.let { peer ->
        AlertDialog(
            onDismissRequest = { peerActionTarget = null },
            containerColor = Color(0xFF1E1C1A),
            title = { Text("Peer Transaction: ${peer.name}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            MovementNature.PEER_LEND to "Lend",
                            MovementNature.PEER_COLLECT to "Collect",
                            MovementNature.PEER_BORROW to "Borrow",
                            MovementNature.PEER_REPAY to "Repay"
                        ).forEach { (nat, lbl) ->
                            val isSel = peerSelectedNature == nat
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSel) Color(0xFFE59C5C) else Color(0xFF282420), RoundedCornerShape(6.dp))
                                    .combinedClickable(onClick = { peerSelectedNature = nat })
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(lbl, color = if (isSel) Color(0xFF141211) else Color(0xFFCCCCCC), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    CompactInputField(value = peerActionAmount, onValueChange = { peerActionAmount = it }, placeholder = "Amount in ₹")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = peerActionAmount.toDoubleOrNull() ?: 0.0
                        if (amt > 0 && defaultLiquidId != 0L) {
                            onPeerAction(peerSelectedNature, peer.id, defaultLiquidId, amt)
                        }
                        peerActionTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE59C5C))
                ) { Text("Confirm", color = Color(0xFF141211), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { peerActionTarget = null }) { Text("Cancel", color = Color(0xFF888888)) } }
        )
    }

    pocketToDelete?.let { (pocket, bal) ->
        AlertDialog(
            onDismissRequest = { pocketToDelete = null },
            containerColor = Color(0xFF262320),
            title = { Text(if (pocket.pocketType == PocketType.SAVING_GOAL) "Break / Delete Goal Pot" else "Delete Account", color = Color.White) },
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
            text = { Text("Are you sure you want to delete this schedule template? Past transactions will remain intact.", color = Color(0xFFCCCCCC)) },
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1917))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = Color(0xFF888888), fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(balanceText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Button(
                    onClick = onActionClick,
                    colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(actionLabel, color = textColor, fontSize = 11.5.sp)
                }
            }
        }
    }
}
