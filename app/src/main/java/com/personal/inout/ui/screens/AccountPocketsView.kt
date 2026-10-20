package com.personal.inout.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.*
import com.personal.inout.ui.LocalThemeColors
import com.personal.inout.ui.ThemeColors
import com.personal.inout.ui.dialogs.*
import com.personal.inout.util.toIndianRupee
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountPocketsView(
    rawPockets: List<LedgerPocket>,
    pocketBalances: Map<Long, Long>,
    recurringTransactions: List<LedgerTransaction>,
    isPrivacyMode: Boolean,
    onTransactPocket: (LedgerPocket) -> Unit,
    onViewTransactions: (Long) -> Unit,
    onEditPocket: (LedgerPocket) -> Unit,
    onDeletePocketSafe: (LedgerPocket, Long) -> Unit,
    onTogglePauseRecurring: (LedgerTransaction) -> Unit,
    onEditRecurring: (LedgerTransaction) -> Unit,
    onDeleteRecurringSafe: (LedgerTransaction) -> Unit,
    onRequestCreateAccount: (PocketType) -> Unit = {}
) {
    val theme = LocalThemeColors.current

    val liquidPockets = remember(rawPockets) { rawPockets.filter { it.type == PocketType.LIQUID || it.type == PocketType.PREPAID_WALLET } }
    
    // DYNAMIC BALANCE-BASED FILTERING
    val capitalPockets = remember(rawPockets, pocketBalances) { 
        rawPockets.filter { 
            val bal = pocketBalances[it.id] ?: 0L
            when (it.type) {
                PocketType.INVESTMENT, PocketType.FIXED_ASSET, PocketType.GOAL_POT -> true
                PocketType.PEER_RECEIVABLE -> bal >= 0L // Normal asset
                PocketType.PEER_PAYABLE, PocketType.CREDIT_CARD -> bal > 0L // Overpaid liability becomes Asset!
                else -> false
            }
        } 
    }
    
    val debtPockets = remember(rawPockets, pocketBalances) { 
        rawPockets.filter { 
            val bal = pocketBalances[it.id] ?: 0L
            when (it.type) {
                PocketType.LIABILITY_LOAN -> true
                PocketType.PEER_PAYABLE, PocketType.CREDIT_CARD -> bal <= 0L // Normal liability
                PocketType.PEER_RECEIVABLE -> bal < 0L // Overdrawn asset becomes Liability!
                else -> false
            }
        } 
    }

    var activeModeIndex by remember { mutableIntStateOf(0) }

    var selectedContextPocket by remember { mutableStateOf<Pair<LedgerPocket, Long>?>(null) }
    var selectedContextRecurring by remember { mutableStateOf<LedgerTransaction?>(null) }
    var pendingSafeDeactivatePocket by remember { mutableStateOf<Pair<LedgerPocket, Long>?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("ALL ${rawPockets.size}", "💧 LIQ ${liquidPockets.size}", "💳 DEBT ${debtPockets.size}", "🏛 CAP ${capitalPockets.size}", "🔄 AUTO ${recurringTransactions.size}").forEachIndexed { idx, label ->
                    val isSel = activeModeIndex == idx
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.accent else Color.Transparent).clickable { activeModeIndex = idx }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = if (isSel) theme.bg else theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        if (activeModeIndex == 0 || activeModeIndex == 1) {
            val totalLiquid = liquidPockets.sumOf { pocketBalances[it.id] ?: 0L }
            item { SectionSummaryHeader("SPENDABLE LIQUID", totalLiquid / 100.0, isPrivacyMode, theme) }
            if (liquidPockets.isEmpty()) {
                item { CompactAddActionRow("No Liquid Bank Accounts", theme) { onRequestCreateAccount(PocketType.LIQUID) } }
            } else {
                item {
                    UnifiedStatementGroup(theme) {
                        liquidPockets.forEachIndexed { index, pocket ->
                            val bal = pocketBalances[pocket.id] ?: 0L
                            val isCash = pocket.name.equals("Cash in Hand", ignoreCase = true)
                            val icon = if (isCash) Icons.Default.Payments else if (pocket.type == PocketType.PREPAID_WALLET) Icons.Default.AccountBalanceWallet else Icons.Default.AccountBalance
                            
                            UnifiedAccountRow(
                                title = pocket.name,
                                sub = if (isCash) "Physical Cash Reserve" else if (pocket.type == PocketType.PREPAID_WALLET) "Prepaid Stored-Value" else "Liquid Bank Asset",
                                balanceDisplay = if (isPrivacyMode) "₹ •••" else if (bal == 0L) "₹ 0" else (bal / 100.0).toIndianRupee(),
                                balanceColor = theme.textBright,
                                icon = icon, iconColor = theme.mildGreen, theme = theme,
                                onLongPress = { selectedContextPocket = pocket to bal }
                            )
                            if (index < liquidPockets.lastIndex) HorizontalDivider(color = theme.borderLight.copy(alpha = 0.4f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        if (activeModeIndex == 0 || activeModeIndex == 2) {
            val totalDebt = debtPockets.sumOf { abs(pocketBalances[it.id] ?: 0L) }
            item { SectionSummaryHeader("LIABILITIES & BORROWINGS", totalDebt / 100.0, isPrivacyMode, theme) }
            if (debtPockets.isEmpty()) {
                item { CompactAddActionRow("No Active Debt or Credit Lines", theme) { onRequestCreateAccount(PocketType.CREDIT_CARD) } }
            } else {
                item {
                    UnifiedStatementGroup(theme) {
                        debtPockets.forEachIndexed { index, pocket ->
                            val bal = pocketBalances[pocket.id] ?: 0L
                            val outstanding = abs(bal)
                            val dueStr = if (pocket.billDueDay > 0) "Due ${pocket.billDueDay}th" else "Statement Balance"
                            
                            // It's a debt pocket, so if it's here, its balance is <= 0. Color is strictly red.
                            val balColor = if (outstanding == 0L) theme.textBright else theme.mildRed

                            UnifiedAccountRow(
                                title = pocket.name,
                                sub = if (pocket.type == PocketType.CREDIT_CARD) "Limit: ${(pocket.creditLimit / 100.0).toIndianRupee()} • $dueStr" else if (pocket.type == PocketType.PEER_PAYABLE || pocket.type == PocketType.PEER_RECEIVABLE) "Sundry Creditor (Payable)" else "Liability Loan",
                                balanceDisplay = if (isPrivacyMode) "₹ •••" else if (outstanding == 0L) "₹ 0" else (outstanding / 100.0).toIndianRupee(),
                                balanceColor = balColor,
                                icon = if (pocket.type == PocketType.PEER_PAYABLE || pocket.type == PocketType.PEER_RECEIVABLE) Icons.Default.People else Icons.Default.CreditCard, 
                                iconColor = theme.mildRed, theme = theme,
                                onLongPress = { selectedContextPocket = pocket to bal }
                            )
                            if (index < debtPockets.lastIndex) HorizontalDivider(color = theme.borderLight.copy(alpha = 0.4f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        if (activeModeIndex == 0 || activeModeIndex == 3) {
            val totalCapital = capitalPockets.sumOf { pocketBalances[it.id] ?: 0L }
            item { SectionSummaryHeader("CAPITAL RESERVES & WEALTH", totalCapital / 100.0, isPrivacyMode, theme) }
            if (capitalPockets.isEmpty()) {
                item { CompactAddActionRow("No Capital Holdings or Goal Reserves", theme) { onRequestCreateAccount(PocketType.INVESTMENT) } }
            } else {
                item {
                    UnifiedStatementGroup(theme) {
                        capitalPockets.forEachIndexed { index, pocket ->
                            val bal = pocketBalances[pocket.id] ?: 0L
                            val absoluteBal = abs(bal)
                            val icon = when (pocket.type) { PocketType.INVESTMENT -> Icons.Default.TrendingUp; PocketType.GOAL_POT -> Icons.Default.Adjust; PocketType.FIXED_ASSET -> Icons.Default.Domain; else -> Icons.Default.People }
                            
                            // It's a capital pocket, so if it's here, its balance is >= 0. Color is strictly green.
                            val balColor = if (absoluteBal == 0L) theme.textBright else theme.mildGreen

                            UnifiedAccountRow(
                                title = pocket.name,
                                sub = when (pocket.type) { PocketType.INVESTMENT -> "Portfolio Asset"; PocketType.GOAL_POT -> "Target: ${(pocket.targetGoalAmount / 100.0).toIndianRupee()}"; PocketType.FIXED_ASSET -> "Fixed Asset"; else -> "Sundry Debtor (Receivable)" },
                                balanceDisplay = if (isPrivacyMode) "₹ •••" else if (absoluteBal == 0L) "₹ 0" else (absoluteBal / 100.0).toIndianRupee(),
                                balanceColor = balColor,
                                icon = icon, iconColor = theme.mildGreen, theme = theme,
                                onLongPress = { selectedContextPocket = pocket to bal }
                            )
                            if (index < capitalPockets.lastIndex) HorizontalDivider(color = theme.borderLight.copy(alpha = 0.4f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        if (activeModeIndex == 0 || activeModeIndex == 4) {
            val totalRecurring = recurringTransactions.sumOf { it.amount }
            item { SectionSummaryHeader("ACTIVE AUTOMATION MANDATES", totalRecurring / 100.0, isPrivacyMode, theme) }
            if (recurringTransactions.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(12.dp))) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No Recurring Mandates Scheduled", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Automate subscriptions, SIPs, and bills with zero-drift execution.", color = theme.textMuted, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                item {
                    UnifiedStatementGroup(theme) {
                        recurringTransactions.forEachIndexed { index, schedule ->
                            val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(schedule.timestamp))
                            UnifiedAccountRow(
                                title = schedule.description.ifBlank { schedule.category },
                                sub = "Repeats ${schedule.recurringFrequency} • Next: $dateStr",
                                balanceDisplay = if (isPrivacyMode) "₹ •••" else (schedule.amount / 100.0).toIndianRupee(),
                                balanceColor = theme.accent,
                                icon = Icons.Default.Schedule, iconColor = theme.accent, theme = theme,
                                onLongPress = { selectedContextRecurring = schedule }
                            )
                            if (index < recurringTransactions.lastIndex) HorizontalDivider(color = theme.borderLight.copy(alpha = 0.4f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    selectedContextPocket?.let { (pocket, bal) ->
        CenteredAccountVoucherModal(
            pocket = pocket, 
            balance = bal / 100.0, 
            theme = theme,
            onDismiss = { selectedContextPocket = null },
            onQuickAction = { selectedContextPocket = null; onTransactPocket(pocket) },
            onViewHistory = { selectedContextPocket = null; onViewTransactions(pocket.id) },
            onEdit = { selectedContextPocket = null; onEditPocket(pocket) },
            onArchiveDelete = { selectedContextPocket = null; pendingSafeDeactivatePocket = pocket to bal }
        )
    }

    pendingSafeDeactivatePocket?.let { (pocket, bal) ->
        CenteredSafePurgeGuardModal(pocket, bal / 100.0, theme, { pendingSafeDeactivatePocket = null }) { pendingSafeDeactivatePocket = null; onDeletePocketSafe(pocket, bal) }
    }

    selectedContextRecurring?.let { schedule ->
        CenteredRecurringVoucherModal(
            rule = schedule, theme = theme, onDismiss = { selectedContextRecurring = null },
            onEdit = { selectedContextRecurring = null; onEditRecurring(schedule) },
            onDelete = { selectedContextRecurring = null; onDeleteRecurringSafe(schedule) }
        )
    }
}

@Composable
private fun SectionSummaryHeader(title: String, totalAmt: Double, isPrivacy: Boolean, theme: ThemeColors) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(text = if (isPrivacy) "₹ •••" else if (totalAmt == 0.0) "₹ 0" else totalAmt.toIndianRupee(), color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun UnifiedStatementGroup(theme: ThemeColors, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(12.dp))) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnifiedAccountRow(title: String, sub: String, balanceDisplay: String, balanceColor: Color, icon: ImageVector, iconColor: Color, theme: ThemeColors, onLongPress: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { }, onLongClick = onLongPress).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.clip(CircleShape).background(iconColor.copy(alpha = 0.12f)).padding(7.dp), contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp)) }
            Column { Text(title, color = theme.textBright, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold); Text(sub, color = theme.textMuted, fontSize = 10.5.sp, maxLines = 1) }
        }
        Text(text = balanceDisplay, color = balanceColor, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CompactAddActionRow(title: String, theme: ThemeColors, onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(10.dp)).clickable { onAdd() }.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = theme.textMuted, fontSize = 11.5.sp); Text("+ Create", color = theme.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}
