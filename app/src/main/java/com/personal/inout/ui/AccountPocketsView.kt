package com.personal.inout.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
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

private enum class AccountFilter { ALL, BANKS, GOALS, CARDS, PEOPLE, RULES }

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
    var selectedFilter by remember { mutableStateOf(AccountFilter.ALL) }

    var peerActionTarget by remember { mutableStateOf<Triple<VaultPocket, Double, MovementNature>?>(null) }
    var peerActionAmount by remember { mutableStateOf("") }
    var activePeerNature by remember { mutableStateOf(MovementNature.PEER_LEND) }

    var pocketToDelete by remember { mutableStateOf<Pair<VaultPocket, Double>?>(null) }
    var recurringToDelete by remember { mutableStateOf<FlowRecord?>(null) }

    val liquidPockets = remember(rawPockets) { rawPockets.filter { it.pocketType == PocketType.LIQUID } }
    val defaultLiquidId = remember(liquidPockets) { liquidPockets.firstOrNull()?.id ?: 0L }

    val bankBalances = remember(pocketBalances) { pocketBalances.filter { it.pocketType == PocketType.LIQUID } }
    val goalBalances = remember(pocketBalances) { pocketBalances.filter { it.pocketType == PocketType.SAVING_GOAL } }
    val cardBalances = remember(pocketBalances) { pocketBalances.filter { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE } }
    val peerBalances = remember(pocketBalances) { pocketBalances.filter { it.pocketType == PocketType.PEER || it.pocketType == PocketType.COUNTERPARTY || it.subType == "PEER" } }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Sticky Top Quick-Filter Chip Row
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(AccountFilter.entries) { filter ->
                val isSel = selectedFilter == filter
                val count = when (filter) {
                    AccountFilter.ALL -> pocketBalances.size + recurringSchedules.size
                    AccountFilter.BANKS -> bankBalances.size
                    AccountFilter.GOALS -> goalBalances.size
                    AccountFilter.CARDS -> cardBalances.size
                    AccountFilter.PEOPLE -> peerBalances.size
                    AccountFilter.RULES -> recurringSchedules.size
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSel) Color(0xFFE59C5C) else Color(0xFF1E1C1A))
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${filter.name.lowercase().replaceFirstChar { it.uppercase() }} ($count)",
                        color = if (isSel) Color(0xFF141211) else Color(0xFF888888),
                        fontSize = 11.5.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Banks & Liquid Accounts (Slate Edge Indicator)
            if (selectedFilter == AccountFilter.ALL || selectedFilter == AccountFilter.BANKS) {
                item {
                    SectionHeader("Cash & Bank Accounts", bankBalances.size)
                }
                items(bankBalances, key = { it.pocketId }) { summary ->
                    val pocket = rawPockets.firstOrNull { it.id.toString() == summary.pocketId }
                        ?: VaultPocket(id = summary.pocketId.toLongOrNull() ?: 0L, name = summary.name, pocketType = PocketType.LIQUID)

                    StructuralDeckRow(
                        indicatorColor = Color(0xFF6E6963), // Slate indicator
                        onLongClick = { onEditPocket(pocket) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(summary.name, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                Text("Liquid Account", color = Color(0xFF888888), fontSize = 10.5.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", summary.computedBalance)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                ActionPill(label = "Transact", bg = Color(0xFF282420), text = Color(0xFFE59C5C)) { onTransactPocket(pocket) }
                            }
                        }
                    }
                }
            }

            // 2. Goal Pots (Warm Amber Indicator, Inert Click, Long-Press Edit, Overdue Badges)
            if (selectedFilter == AccountFilter.ALL || selectedFilter == AccountFilter.GOALS) {
                if (goalBalances.isNotEmpty()) {
                    item {
                        SectionHeader("Goal Pots (Target Savings)", goalBalances.size)
                    }
                    items(goalBalances, key = { it.pocketId }) { pot ->
                        val rawPocket = rawPockets.firstOrNull { it.id.toString() == pot.pocketId }
                            ?: VaultPocket(id = pot.pocketId.toLongOrNull() ?: 0L, name = pot.name, pocketType = PocketType.SAVING_GOAL)
                        val progress = if (pot.targetAmount > 0) (pot.computedBalance / pot.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                        val shortfall = (pot.targetAmount - pot.computedBalance).coerceAtLeast(0.0)

                        val targetDateStr = if (pot.targetDateEpoch > 0) {
                            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(pot.targetDateEpoch))
                        } else "No Target Date"

                        val isOverdue = pot.targetDateEpoch > 0 && System.currentTimeMillis() > pot.targetDateEpoch && shortfall > 0

                        StructuralDeckRow(
                            indicatorColor = Color(0xFFE59C5C), // Amber indicator
                            onLongClick = { onEditPocket(rawPocket) }
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(pot.name, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                            if (isOverdue) {
                                                Box(modifier = Modifier.background(Color(0xFF332020), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                    Text("OVERDUE", color = Color(0xFFE57373), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Text("Due: $targetDateStr • Shortfall: ₹${shortfall.toInt()}", color = if (isOverdue) Color(0xFFE57373) else Color(0xFF888888), fontSize = 10.5.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            if (isPrivacyMode) "••••" else "₹${String.format("%,.0f", pot.computedBalance)} / ₹${String.format("%,.0f", pot.targetAmount)}",
                                            color = Color(0xFFE59C5C),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(onClick = { pocketToDelete = rawPocket to pot.computedBalance }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Break Pot", tint = Color(0xFFE57373), modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = if (isOverdue) Color(0xFFE57373) else Color(0xFFE59C5C),
                                    trackColor = Color(0xFF282420)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Cards & Loans (Terracotta Warning Indicator, Dual Buttons)
            if (selectedFilter == AccountFilter.ALL || selectedFilter == AccountFilter.CARDS) {
                if (cardBalances.isNotEmpty()) {
                    item {
                        SectionHeader("Cards & Credit Lines", cardBalances.size)
                    }
                    items(cardBalances, key = { it.pocketId }) { summary ->
                        val pocket = rawPockets.firstOrNull { it.id.toString() == summary.pocketId }
                            ?: VaultPocket(id = summary.pocketId.toLongOrNull() ?: 0L, name = summary.name, pocketType = summary.pocketType)
                        val hasOutstanding = summary.computedBalance < 0.0

                        StructuralDeckRow(
                            indicatorColor = Color(0xFFE57373), // Terracotta indicator
                            onLongClick = { onEditPocket(pocket) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(summary.name, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (isPrivacyMode) "••••" else "Avail: ₹${String.format("%,.0f", summary.creditLimit + summary.computedBalance)} / ₹${String.format("%,.0f", summary.creditLimit)}",
                                        color = Color(0xFF888888),
                                        fontSize = 10.5.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        if (isPrivacyMode) "••••" else "₹${String.format("%,.0f", Math.abs(summary.computedBalance))}",
                                        color = if (hasOutstanding) Color(0xFFE57373) else Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ActionPill(label = "Transact", bg = Color(0xFF282420), text = Color(0xFFE59C5C)) { onTransactPocket(pocket) }
                                    if (hasOutstanding) {
                                        ActionPill(label = "Pay Bill", bg = Color(0xFF332020), text = Color(0xFFE57373)) {
                                            if (defaultLiquidId != 0L) onRecordCardSettlement(pocket.id, defaultLiquidId, Math.abs(summary.computedBalance))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. People (Avatar Initial Badges, Contextual Dynamic Pill)
            if (selectedFilter == AccountFilter.ALL || selectedFilter == AccountFilter.PEOPLE) {
                if (peerBalances.isNotEmpty()) {
                    item {
                        SectionHeader("People (Owed & Lent)", peerBalances.size)
                    }
                    items(peerBalances, key = { it.pocketId }) { summary ->
                        val pocket = rawPockets.firstOrNull { it.id.toString() == summary.pocketId }
                            ?: VaultPocket(id = summary.pocketId.toLongOrNull() ?: 0L, name = summary.name, pocketType = summary.pocketType)
                        val bal = summary.computedBalance

                        val pillLabel = when {
                            bal > 0 -> "Collect"
                            bal < 0 -> "Repay"
                            else -> "Transact"
                        }
                        val pillBg = when {
                            bal > 0 -> Color(0xFF1E3326)
                            bal < 0 -> Color(0xFF332020)
                            else -> Color(0xFF282420)
                        }
                        val pillText = when {
                            bal > 0 -> Color(0xFF81C784)
                            bal < 0 -> Color(0xFFE57373)
                            else -> Color(0xFFE59C5C)
                        }

                        val indicator = when {
                            bal > 0 -> Color(0xFF81C784)
                            bal < 0 -> Color(0xFFE57373)
                            else -> Color(0xFF6E6963)
                        }

                        StructuralDeckRow(
                            indicatorColor = indicator,
                            onLongClick = { onEditPocket(pocket) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF262320)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = summary.name.take(2).uppercase(Locale.getDefault()),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column {
                                        Text(summary.name, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                        val sub = when {
                                            bal > 0 -> "They owe you"
                                            bal < 0 -> "You owe them"
                                            else -> "Settled cleanly"
                                        }
                                        Text(sub, color = if (bal > 0) Color(0xFF81C784) else if (bal < 0) Color(0xFFE57373) else Color(0xFF888888), fontSize = 10.5.sp)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        if (isPrivacyMode) "••••" else "₹${String.format("%,.0f", Math.abs(bal))}",
                                        color = when {
                                            bal > 0 -> Color(0xFF81C784)
                                            bal < 0 -> Color(0xFFE57373)
                                            else -> Color(0xFF888888)
                                        },
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    ActionPill(label = pillLabel, bg = pillBg, text = pillText) {
                                        peerActionTarget = Triple(pocket, bal, if (bal > 0) MovementNature.PEER_COLLECT else if (bal < 0) MovementNature.PEER_REPAY else MovementNature.PEER_LEND)
                                        activePeerNature = if (bal > 0) MovementNature.PEER_COLLECT else if (bal < 0) MovementNature.PEER_REPAY else MovementNature.PEER_LEND
                                        peerActionAmount = if (bal != 0.0) Math.abs(bal).toInt().toString() else ""
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Recurring Rules Lifecycle (Slim Dashed-Border Ticket Stubs)
            if (selectedFilter == AccountFilter.ALL || selectedFilter == AccountFilter.RULES) {
                if (recurringSchedules.isNotEmpty()) {
                    item {
                        SectionHeader("Recurring Automation Pipeline", recurringSchedules.size)
                    }
                    items(recurringSchedules, key = { it.id }) { record ->
                        StructuralDeckRow(
                            indicatorColor = Color(0xFFA88950), // Muted bronze indicator
                            onLongClick = { onEditRecurring(record) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            record.note.ifBlank { record.category },
                                            color = if (record.isPaused) Color(0xFF888888) else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (record.isPaused) {
                                            Box(modifier = Modifier.background(Color(0xFF3A352F), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                                Text("PAUSED", color = Color(0xFFE59C5C), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    val freq = if (record.frequency != "NONE") record.frequency else record.recurringCadence
                                    val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(record.timestamp))
                                    Text("Repeats $freq (due $dateStr) • ₹${record.amount.toInt()}", color = Color(0xFF888888), fontSize = 10.5.sp)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { onTogglePauseRecurring(record) }, modifier = Modifier.size(28.dp)) {
                                        Icon(if (record.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = "Toggle Pause", tint = if (record.isPaused) Color(0xFF81C784) else Color(0xFFE59C5C), modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { onEditRecurring(record) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Rule", tint = Color(0xFFCCCCCC), modifier = Modifier.size(15.dp))
                                    }
                                    IconButton(onClick = { recurringToDelete = record }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Rule", tint = Color(0xFFE57373), modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Compact Contextual Peer Dual-Option Modal
    peerActionTarget?.let { (peer, bal, _) ->
        val options = when {
            bal > 0 -> listOf(MovementNature.PEER_COLLECT to "Collect", MovementNature.PEER_LEND to "Lend More")
            bal < 0 -> listOf(MovementNature.PEER_REPAY to "Repay", MovementNature.PEER_BORROW to "Borrow More")
            else -> listOf(MovementNature.PEER_LEND to "Lend", MovementNature.PEER_BORROW to "Borrow")
        }

        AlertDialog(
            onDismissRequest = { peerActionTarget = null },
            containerColor = Color(0xFF1E1C1A),
            title = { Text("Peer Transaction: ${peer.name}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { (nat, lbl) ->
                            val isSel = activePeerNature == nat
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSel) Color(0xFFE59C5C) else Color(0xFF282420), RoundedCornerShape(6.dp))
                                    .clickable { activePeerNature = nat }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(lbl, color = if (isSel) Color(0xFF141211) else Color(0xFFCCCCCC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            onPeerAction(activePeerNature, peer.id, defaultLiquidId, amt)
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
            text = { Text("Are you sure you want to delete this schedule template? Past transactions remain intact.", color = Color(0xFFCCCCCC)) },
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

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color(0xFFCCCCCC), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        Text("$count", color = Color(0xFF666666), fontSize = 11.5.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StructuralDeckRow(
    indicatorColor: Color,
    onLongClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1917))
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .background(indicatorColor)
            )
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    bg: Color,
    text: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bg),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
