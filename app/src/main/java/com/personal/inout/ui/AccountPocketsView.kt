package com.personal.inout.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onStopRecurringSchedule: (schedule: FlowRecord) -> Unit = {},
    onRecordCardSettlement: (cardId: Long, liquidId: Long, amt: Double) -> Unit = { _, _, _ -> },
    onPeerAction: (nature: MovementNature, peerId: Long, liquidId: Long, amt: Double) -> Unit = { _, _, _, _ -> }
) {
    var selectedRecordForStop by remember { mutableStateOf<FlowRecord?>(null) }
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
                Text(
                    "${pocketBalances.count { it.pocketType == PocketType.LIQUID }}",
                    color = Color(0xFF666666),
                    fontSize = 13.sp
                )
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

        // 2. Cards & Loans
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cards & Loans (CC / Dues)", color = Color(0xFFCCCCCC), fontSize = 14.sp)
                Text(
                    "${pocketBalances.count { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE }}",
                    color = Color(0xFF666666),
                    fontSize = 13.sp
                )
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

        // 3. Counterparties (People)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("People (Owed & Lent)", color = Color(0xFFCCCCCC), fontSize = 14.sp)
                Text(
                    "${pocketBalances.count { it.pocketType == PocketType.PEER || it.pocketType == PocketType.COUNTERPARTY || it.subType == "PEER" }}",
                    color = Color(0xFF666666),
                    fontSize = 13.sp
                )
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

        // 4. Active Recurring Schedules
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Active Recurring Schedules", color = Color(0xFFCCCCCC), fontSize = 14.sp)
                Text("${recurringSchedules.size}", color = Color(0xFF666666), fontSize = 13.sp)
            }
        }
        items(recurringSchedules, key = { it.id }) { record ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { selectedRecordForStop = record }
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C1A))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = record.note.ifBlank { record.category },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val freqText = if (record.frequency != "NONE") record.frequency else record.recurringCadence
                        Text(
                            text = if (isPrivacyMode) "Repeats $freqText • ₹••••" else "Repeats $freqText • ₹${record.amount.toInt()}",
                            color = Color(0xFFE59C5C),
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = { selectedRecordForStop = record },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF332222)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Stop Rule", color = Color(0xFFE57373), fontSize = 12.sp)
                    }
                }
            }
        }
    }

    selectedRecordForStop?.let { record ->
        AlertDialog(
            onDismissRequest = { selectedRecordForStop = null },
            containerColor = Color(0xFF262320),
            title = { Text("Stop Recurring Rule", color = Color.White) },
            text = {
                Text(
                    "Are you sure you want to stop this recurring schedule? Past records remain intact.",
                    color = Color(0xFFCCCCCC)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onStopRecurringSchedule(record)
                        selectedRecordForStop = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) {
                    Text("Stop Rule", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedRecordForStop = null }) {
                    Text("Cancel", color = Color(0xFF9E9E9E))
                }
            }
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
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C1A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
