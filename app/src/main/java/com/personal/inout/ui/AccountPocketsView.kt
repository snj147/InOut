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
import com.personal.inout.data.PocketBalanceSummary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountPocketsView(
    pocketSummaries: List<PocketBalanceSummary>,
    recurringRecords: List<FlowRecord>,
    onTransact: (pocketId: String) -> Unit,
    onStopRecurring: (recordId: Long) -> Unit,
    onPocketLongClick: (pocketId: String) -> Unit = {}
) {
    var selectedRecordForStop by remember { mutableStateOf<FlowRecord?>(null) }

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
                    "${pocketSummaries.count { it.pocketType == "LIQUID" }}",
                    color = Color(0xFF666666),
                    fontSize = 13.sp
                )
            }
        }
        items(pocketSummaries.filter { it.pocketType == "LIQUID" }, key = { it.pocketId }) { pocket ->
            PocketCard(
                name = pocket.pocketName,
                subtitle = "LIQUID",
                balanceText = "₹${pocket.computedBalance}",
                actionLabel = "Transact",
                actionColor = Color(0xFF332B22),
                textColor = Color(0xFFE59C5C),
                onActionClick = { onTransact(pocket.pocketId) },
                onLongClick = { onPocketLongClick(pocket.pocketId) }
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
                    "${pocketSummaries.count { it.pocketType == "CREDIT" }}",
                    color = Color(0xFF666666),
                    fontSize = 13.sp
                )
            }
        }
        items(pocketSummaries.filter { it.pocketType == "CREDIT" }, key = { it.pocketId }) { card ->
            PocketCard(
                name = card.pocketName,
                subtitle = "Avail: ₹${card.creditLimit + card.computedBalance} / Limit: ₹${card.creditLimit}",
                balanceText = "₹${Math.abs(card.computedBalance)}",
                actionLabel = if (card.computedBalance >= 0) "Settled" else "Pay Due",
                actionColor = Color(0xFF282522),
                textColor = Color(0xFF9E9E9E),
                onActionClick = { onTransact(card.pocketId) },
                onLongClick = { onPocketLongClick(card.pocketId) }
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
                    "${pocketSummaries.count { it.pocketType == "PEER" || it.subType == "PEER" }}",
                    color = Color(0xFF666666),
                    fontSize = 13.sp
                )
            }
        }
        items(pocketSummaries.filter { it.pocketType == "PEER" || it.subType == "PEER" }, key = { it.pocketId }) { peer ->
            val isOwedToYou = peer.computedBalance > 0
            PocketCard(
                name = peer.pocketName,
                subtitle = if (isOwedToYou) "They owe you" else "You owe them",
                balanceText = "₹${Math.abs(peer.computedBalance)}",
                actionLabel = if (isOwedToYou) "Collect" else "Repay",
                actionColor = if (isOwedToYou) Color(0xFF1E3326) else Color(0xFF332020),
                textColor = if (isOwedToYou) Color(0xFF81C784) else Color(0xFFE57373),
                onActionClick = { onTransact(peer.pocketId) },
                onLongClick = { onPocketLongClick(peer.pocketId) }
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
                Text("${recurringRecords.size}", color = Color(0xFF666666), fontSize = 13.sp)
            }
        }
        items(recurringRecords, key = { it.id }) { record ->
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
                        Text(
                            text = "Repeats ${record.recurringCadence} • ₹${record.amount}",
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
                    "Are you sure you want to stop this recurring schedule? Existing past ledger records will remain safe.",
                    color = Color(0xFFCCCCCC)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onStopRecurring(record.id)
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
