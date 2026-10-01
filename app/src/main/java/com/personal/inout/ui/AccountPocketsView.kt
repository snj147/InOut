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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountPocketsView(
    pocketBalances: List<PocketBalanceSummary>,
    rawPockets: List<VaultPocket>,
    recurringSchedules: List<FlowRecord>,
    isPrivacyMode: Boolean,
    onTransactPocket: (VaultPocket) -> Unit,
    onEditPocket: (VaultPocket) -> Unit,
    onDeletePocketSafe: (VaultPocket, Double?) -> Unit,
    onTogglePauseRecurring: (FlowRecord) -> Unit,
    onEditRecurring: (FlowRecord) -> Unit,
    onDeleteRecurringSafe: (FlowRecord) -> Unit,
    onRequestCreateAccount: (PocketType) -> Unit = {}
) {
    val theme = LocalThemeColors.current

    val liquidPockets = remember(rawPockets) { rawPockets.filter { it.pocketType == PocketType.LIQUID } }
    val cardPockets = remember(rawPockets) { rawPockets.filter { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE } }
    val goalPockets = remember(rawPockets) { rawPockets.filter { it.pocketType == PocketType.SAVING_GOAL } }
    val peerPockets = remember(rawPockets) { rawPockets.filter { it.pocketType == PocketType.COUNTERPARTY || it.subType == "PEER" || it.pocketType == PocketType.PEER } }

    var selectedFilterTab by remember { mutableIntStateOf(0) }

    var activeActionSheetPocket by remember { mutableStateOf<Pair<VaultPocket, Double?>?>(null) }
    var activeActionSheetSchedule by remember { mutableStateOf<FlowRecord?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "All (${rawPockets.size})",
                    "Banks (${liquidPockets.size})",
                    "Goals (${goalPockets.size})",
                    "Cards (${cardPockets.size})",
                    "People (${peerPockets.size})"
                ).forEachIndexed { idx, label ->
                    val isSel = selectedFilterTab == idx
                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) theme.accent else theme.surface)
                                .clickable { selectedFilterTab = idx }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) theme.bg else theme.textMuted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 1. LIQUID / CASH & BANK
        if (selectedFilterTab == 0 || selectedFilterTab == 1) {
            item { SectionHeader("Cash & Bank Accounts", liquidPockets.size, theme) }
            if (liquidPockets.isEmpty()) {
                item {
                    EmptyActionCard(
                        title = "No Bank Accounts Found",
                        message = "Add your primary bank, wallet, or cash account to log income & expenses.",
                        buttonText = "+ Add Bank / Wallet",
                        theme = theme,
                        onClick = { onRequestCreateAccount(PocketType.LIQUID) }
                    )
                }
            } else {
                items(liquidPockets, key = { "liquid_${it.id}" }) { pocket ->
                    val bal = pocketBalances.firstOrNull { it.pocketId == pocket.id.toString() }?.computedBalance ?: 0.0
                    CleanAccountRow(
                        title = pocket.name,
                        subtitle = "Liquid Asset • Long press for options",
                        balanceDisplay = if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", bal)}",
                        balanceColor = theme.textBright,
                        icon = Icons.Default.AccountBalance,
                        iconColor = theme.mildGreen,
                        theme = theme,
                        onTransact = { onTransactPocket(pocket) },
                        onLongClick = { activeActionSheetPocket = pocket to bal }
                    )
                }
            }
        }

        // 2. GOAL POTS (SAVING TARGETS)
        if (selectedFilterTab == 0 || selectedFilterTab == 2) {
            item { SectionHeader("Goal Pots (Target Savings)", goalPockets.size, theme) }
            if (goalPockets.isEmpty()) {
                item {
                    EmptyActionCard(
                        title = "No Goal Pots Active",
                        message = "Earmark savings towards specific targets without opening multiple bank accounts.",
                        buttonText = "+ Add Goal Pot",
                        theme = theme,
                        onClick = { onRequestCreateAccount(PocketType.SAVING_GOAL) }
                    )
                }
            } else {
                items(goalPockets, key = { "goal_${it.id}" }) { pocket ->
                    val bal = pocketBalances.firstOrNull { it.pocketId == pocket.id.toString() }?.computedBalance ?: 0.0
                    val target = pocket.targetAmount ?: 0.0
                    val shortfall = (target - bal).coerceAtLeast(0.0)
                    val targetDateStr = if (pocket.targetDateEpoch > 0) {
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(pocket.targetDateEpoch))
                    } else "No deadline"

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onTransactPocket(pocket) },
                                onLongClick = { activeActionSheetPocket = pocket to bal }
                            )
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(pocket.name, color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", bal)} / ₹${String.format("%,.0f", target)}",
                                    color = theme.accent,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { if (target > 0) (bal / target).toFloat().coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = theme.accent,
                                trackColor = theme.surfaceAlt
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Due: $targetDateStr • Shortfall: ₹${String.format("%,.0f", shortfall)}", color = theme.textMuted, fontSize = 10.5.sp)
                                Text("Long-press to edit", color = theme.textMuted.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // 3. CREDIT CARDS & CREDIT LINES
        if (selectedFilterTab == 0 || selectedFilterTab == 3) {
            item { SectionHeader("Cards & Credit Lines", cardPockets.size, theme) }
            if (cardPockets.isEmpty()) {
                item {
                    EmptyActionCard(
                        title = "No Credit Cards Linked",
                        message = "Track unbilled expenses, credit limits, and bill due dates cleanly.",
                        buttonText = "+ Add Credit Card",
                        theme = theme,
                        onClick = { onRequestCreateAccount(PocketType.CREDIT_LINE) }
                    )
                }
            } else {
                items(cardPockets, key = { "card_${it.id}" }) { pocket ->
                    val rawBalance = pocketBalances.firstOrNull { it.pocketId == pocket.id.toString() }?.computedBalance ?: 0.0
                    val limit = pocket.creditLimit ?: 0.0
                    val outstandingDues = if (rawBalance < 0.0) Math.abs(rawBalance) else 0.0
                    val availableLimit = (limit - outstandingDues).coerceIn(0.0, limit)
                    val dueDateStr = if (pocket.targetDateEpoch > 0) {
                        " • Due: " + SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(pocket.targetDateEpoch))
                    } else ""

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onTransactPocket(pocket) },
                                onLongClick = { activeActionSheetPocket = pocket to rawBalance }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(pocket.name, color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Avail: ₹${String.format("%,.0f", availableLimit)} / Limit: ₹${String.format("%,.0f", limit)}$dueDateStr",
                                    color = theme.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", outstandingDues)}",
                                    color = if (outstandingDues > 0.0) theme.mildRed else theme.mildGreen,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = { onTransactPocket(pocket) },
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Transact", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. COUNTERPARTIES / PEER LEDGER
        if (selectedFilterTab == 0 || selectedFilterTab == 4) {
            item { SectionHeader("People (Owed & Lent)", peerPockets.size, theme) }
            if (peerPockets.isEmpty()) {
                item {
                    EmptyActionCard(
                        title = "No Contacts Tracked",
                        message = "Track borrowed and lent money with counterparty balances.",
                        buttonText = "+ Add Person",
                        theme = theme,
                        onClick = { onRequestCreateAccount(PocketType.COUNTERPARTY) }
                    )
                }
            } else {
                items(peerPockets, key = { "peer_${it.id}" }) { pocket ->
                    val bal = pocketBalances.firstOrNull { it.pocketId == pocket.id.toString() }?.computedBalance ?: 0.0
                    val isReceivable = bal >= 0.0

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onTransactPocket(pocket) },
                                onLongClick = { activeActionSheetPocket = pocket to bal }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(pocket.name, color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(if (isReceivable) "They owe you" else "You owe them", color = theme.textMuted, fontSize = 11.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isPrivacyMode) "₹ •••" else "${if (isReceivable) "+" else "-"}₹${String.format("%,.0f", Math.abs(bal))}",
                                    color = if (isReceivable) theme.mildGreen else theme.mildRed,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. RECURRING AUTOMATION PIPELINE
        if (selectedFilterTab == 0) {
            item { SectionHeader("Recurring Automation Pipeline", recurringSchedules.size, theme) }
            if (recurringSchedules.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No Recurring Rules Scheduled", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Select a repeat cadence (Daily, Weekly, Monthly) on any transaction to automate repeating entries.", color = theme.textMuted, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                items(recurringSchedules, key = { "rec_${it.id}_${it.timestamp}_${it.frequency}" }) { schedule ->
                    val nextDateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(schedule.timestamp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onEditRecurring(schedule) },
                                onLongClick = { activeActionSheetSchedule = schedule }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(schedule.note.ifBlank { schedule.category }, color = theme.textBright, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text("Repeats ${schedule.frequency} (due $nextDateStr) • ₹${(schedule.amount ?: 0.0).toInt()}", color = theme.textMuted, fontSize = 11.sp)
                            }
                            IconButton(onClick = { onTogglePauseRecurring(schedule) }) {
                                Icon(
                                    imageVector = if (schedule.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = "Toggle Pause",
                                    tint = theme.accent
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Actions for Card Long-Press
    activeActionSheetPocket?.let { (pocket, bal) ->
        AlertDialog(
            onDismissRequest = { activeActionSheetPocket = null },
            containerColor = theme.surface,
            title = { Text(pocket.name, color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Choose action for this account", color = theme.textMuted, fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        activeActionSheetPocket = null
                        onEditPocket(pocket)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) { Text("Edit Details", color = theme.bg, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        activeActionSheetPocket = null
                        onDeletePocketSafe(pocket, bal)
                    }) { Text("Delete", color = theme.mildRed) }
                    TextButton(onClick = { activeActionSheetPocket = null }) { Text("Cancel", color = theme.textMuted) }
                }
            }
        )
    }

    activeActionSheetSchedule?.let { schedule ->
        AlertDialog(
            onDismissRequest = { activeActionSheetSchedule = null },
            containerColor = theme.surface,
            title = { Text(schedule.note.ifBlank { schedule.category }, color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Manage recurring rule", color = theme.textMuted, fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        activeActionSheetSchedule = null
                        onEditRecurring(schedule)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) { Text("Edit Rule", color = theme.bg, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        activeActionSheetSchedule = null
                        onDeleteRecurringSafe(schedule)
                    }) { Text("Delete", color = theme.mildRed) }
                    TextButton(onClick = { activeActionSheetSchedule = null }) { Text("Cancel", color = theme.textMuted) }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CleanAccountRow(
    title: String,
    subtitle: String,
    balanceDisplay: String,
    balanceColor: Color,
    icon: ImageVector,
    iconColor: Color,
    theme: ThemeColors,
    onTransact: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTransact,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.clip(CircleShape).background(iconColor.copy(alpha = 0.15f)).padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(title, color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = theme.textMuted, fontSize = 10.5.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = balanceDisplay,
                    color = balanceColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onTransact,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Transact", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, theme: ThemeColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(count.toString(), color = theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyActionCard(
    title: String,
    message: String,
    buttonText: String,
    theme: ThemeColors,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = title, color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text = message, color = theme.textMuted, fontSize = 11.sp)
            OutlinedButton(
                onClick = onClick,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.accent.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = buttonText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
