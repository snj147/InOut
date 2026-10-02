package com.personal.inout.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountPocketsView(
    rawPockets: List<LedgerPocket>,
    pocketBalances: Map<Long, Double>,
    recurringTransactions: List<LedgerTransaction>,
    isPrivacyMode: Boolean,
    onTransactPocket: (LedgerPocket) -> Unit,
    onEditPocket: (LedgerPocket) -> Unit,
    onDeletePocketSafe: (LedgerPocket, Double) -> Unit,
    onTogglePauseRecurring: (LedgerTransaction) -> Unit,
    onEditRecurring: (LedgerTransaction) -> Unit,
    onDeleteRecurringSafe: (LedgerTransaction) -> Unit,
    onRequestCreateAccount: (PocketType) -> Unit = {}
) {
    val theme = LocalThemeColors.current

    val liquidPockets = remember(rawPockets) {
        rawPockets.filter { it.type == PocketType.LIQUID || it.type == PocketType.PREPAID_WALLET }
    }
    val cardPockets = remember(rawPockets) {
        rawPockets.filter { it.type == PocketType.CREDIT_CARD }
    }
    val investmentPockets = remember(rawPockets) {
        rawPockets.filter { it.type == PocketType.INVESTMENT }
    }
    val fixedAssetPockets = remember(rawPockets) {
        rawPockets.filter { it.type == PocketType.FIXED_ASSET }
    }
    val loanPockets = remember(rawPockets) {
        rawPockets.filter { it.type == PocketType.LIABILITY_LOAN }
    }
    val peerPockets = remember(rawPockets) {
        rawPockets.filter { it.type == PocketType.PEER_RECEIVABLE || it.type == PocketType.PEER_PAYABLE }
    }
    val goalPockets = remember(rawPockets) {
        rawPockets.filter { it.type == PocketType.GOAL_POT }
    }

    var selectedFilterTab by remember { mutableIntStateOf(0) }

    var activeActionSheetPocket by remember { mutableStateOf<Pair<LedgerPocket, Double>?>(null) }
    var activeActionSheetSchedule by remember { mutableStateOf<LedgerTransaction?>(null) }

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
                    "Cash & Banks (${liquidPockets.size})",
                    "Cards (${cardPockets.size})",
                    "Portfolio (${investmentPockets.size})",
                    "Fixed Assets (${fixedAssetPockets.size})",
                    "Loans & EMIs (${loanPockets.size})",
                    "People (${peerPockets.size})",
                    "Goals (${goalPockets.size})"
                ).forEachIndexed { idx, label ->
                    val isSel = selectedFilterTab == idx
                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) theme.accent else theme.surface)
                                .border(1.dp, if (isSel) theme.accent else theme.borderLight, RoundedCornerShape(8.dp))
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

        // 1. LIQUID / CASH & BANK (Rules 1, 36, 37)
        if (selectedFilterTab == 0 || selectedFilterTab == 1) {
            item { SectionHeader("Cash, Bank & Prepaid Wallets", liquidPockets.size, theme) }
            if (liquidPockets.isEmpty()) {
                item {
                    EmptyActionCard(
                        title = "No Liquid Accounts Found",
                        message = "Add your primary bank, UPI wallet, or physical cash in hand to maintain your True Safe Liquid.",
                        buttonText = "+ Add Bank / Wallet",
                        theme = theme,
                        onClick = { onRequestCreateAccount(PocketType.LIQUID) }
                    )
                }
            } else {
                items(liquidPockets, key = { "liquid_${it.id}" }) { pocket ->
                    val bal = pocketBalances[pocket.id] ?: 0.0
                    val isCash = pocket.name.equals("Cash in Hand", ignoreCase = true)
                    val icon = if (isCash) Icons.Default.Payments else if (pocket.type == PocketType.PREPAID_WALLET) Icons.Default.AccountBalanceWallet else Icons.Default.AccountBalance
                    CleanAccountRow(
                        title = pocket.name,
                        subtitle = if (isCash) "Physical Cash • Rule 36" else if (pocket.type == PocketType.PREPAID_WALLET) "Prepaid Rail • Rule 37" else "Liquid Bank Asset",
                        balanceDisplay = if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", bal)}",
                        balanceColor = theme.textBright,
                        icon = icon,
                        iconColor = theme.mildGreen,
                        theme = theme,
                        onTransact = { onTransactPocket(pocket) },
                        onLongClick = { activeActionSheetPocket = pocket to bal }
                    )
                }
            }
        }

        // 2. CREDIT CARDS (Rules 4, 5, 21)
        if (selectedFilterTab == 0 || selectedFilterTab == 2) {
            item { SectionHeader("Credit Cards & Pay Later", cardPockets.size, theme) }
            if (cardPockets.isEmpty()) {
                item {
                    EmptyActionCard(
                        title = "No Credit Cards Linked",
                        message = "Track card limits, bill generation dates, and grace periods with 1:1 Phantom Ring-fencing.",
                        buttonText = "+ Add Credit Card",
                        theme = theme,
                        onClick = { onRequestCreateAccount(PocketType.CREDIT_CARD) }
                    )
                }
            } else {
                items(cardPockets, key = { "card_${it.id}" }) { pocket ->
                    val rawBalance = pocketBalances[pocket.id] ?: 0.0
                    val limit = pocket.creditLimit
                    val outstandingDues = if (rawBalance < 0.0) abs(rawBalance) else 0.0
                    val availableLimit = (limit - outstandingDues).coerceAtLeast(0.0)
                    val utilization = if (limit > 0.0) (outstandingDues / limit) * 100.0 else 0.0
                    val dueStr = if (pocket.billDueDay > 0) " • Bill Due: ${pocket.billDueDay}th" else ""

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, theme.borderLight, RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { onTransactPocket(pocket) },
                                onLongClick = { activeActionSheetPocket = pocket to rawBalance }
                            )
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(pocket.name, color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Avail: ₹${String.format("%,.0f", availableLimit)} / Limit: ₹${String.format("%,.0f", limit)}$dueStr",
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

                            if (limit > 0.0) {
                                LinearProgressIndicator(
                                    progress = { (utilization / 100.0).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = if (utilization > 75.0) theme.mildRed else if (utilization > 30.0) theme.accent else theme.mildGreen,
                                    trackColor = theme.surfaceAlt
                                )
                                Text(
                                    text = "Credit Utilization: ${String.format("%.1f", utilization)}% ${if (utilization > 30.0) "(Above 30% advisory)" else ""}",
                                    color = if (utilization > 30.0) theme.accent else theme.textMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. INVESTMENTS & PORTFOLIO (Rule 31)
        if (selectedFilterTab == 0 || selectedFilterTab == 3) {
            item { SectionHeader("Investments & Portfolio Wealth", investmentPockets.size, theme) }
            if (investmentPockets.isEmpty()) {
                item {
                    EmptyActionCard(
                        title = "No Investment Pockets",
                        message = "Track Mutual Funds, Equities, FDs, PPF, and Gold with mark-to-market valuations (excluded from daily runway).",
                        buttonText = "+ Add Investment Asset",
                        theme = theme,
                        onClick = { onRequestCreateAccount(PocketType.INVESTMENT) }
                    )
                }
            } else {
                items(investmentPockets, key = { "inv_${it.id}" }) { pocket ->
                    val bal = pocketBalances[pocket.id] ?: 0.0
                    CleanAccountRow(
                        title = pocket.name,
                        subtitle = "Mark-to-Market Asset • Safe from Daily Burn",
                        balanceDisplay = if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", bal)}",
                        balanceColor = theme.accent,
                        icon = Icons.Default.TrendingUp,
                        iconColor = theme.accent,
                        theme = theme,
                        onTransact = { onTransactPocket(pocket) },
                        onLongClick = { activeActionSheetPocket = pocket to bal }
                    )
                }
            }
        }

        // 4. FIXED CAPITAL ASSETS (Rule 20, 29)
        if (selectedFilterTab == 0 || selectedFilterTab == 4) {
            item { SectionHeader("Fixed Capital Assets (WDV)", fixedAssetPockets.size, theme) }
            if (fixedAssetPockets.isEmpty()) {
                item {
                    EmptyActionCard(
                        title = "No Fixed Assets Registered",
                        message = "Capitalize real estate, vehicles, and gadgets at Written Down Value (WDV) for statutory balance sheets.",
                        buttonText = "+ Add Fixed Asset",
                        theme = theme,
                        onClick = { onRequestCreateAccount(PocketType.FIXED_ASSET) }
                    )
                }
            } else {
                items(fixedAssetPockets, key = { "fa_${it.id}" }) { pocket ->
                    val bal = pocketBalances[pocket.id] ?: 0.0
                    CleanAccountRow(
                        title = pocket.name,
                        subtitle = "Capital Asset • Subject to Periodic WDV",
                        balanceDisplay = if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", bal)}",
                        balanceColor = theme.textBright,
                        icon = Icons.Default.Domain,
                        iconColor = theme.textMuted,
                        theme = theme,
                        onTransact = { onTransactPocket(pocket) },
                        onLongClick = { activeActionSheetPocket = pocket to bal }
                    )
                }
            }
        }

        // 5. CONSUMER LOANS & EMIs (Rule 35)
        if (selectedFilterTab == 0 || selectedFilterTab == 5) {
            item { SectionHeader("Loans, Mortgages & Consumer EMIs", loanPockets.size, theme) }
            if (loanPockets.isEmpty()) {
                item {
                    EmptyActionCard(
                        title = "No Debt Liabilities Tracked",
                        message = "Track home loans, auto loans, and retail consumer EMIs with principal-interest amortization.",
                        buttonText = "+ Add Loan Account",
                        theme = theme,
                        onClick = { onRequestCreateAccount(PocketType.LIABILITY_LOAN) }
                    )
                }
            } else {
                items(loanPockets, key = { "loan_${it.id}" }) { pocket ->
                    val bal = pocketBalances[pocket.id] ?: 0.0
                    val debt = abs(bal)
                    CleanAccountRow(
                        title = pocket.name,
                        subtitle = "Term Debt Liability • Locked in Cash Horizon",
                        balanceDisplay = if (isPrivacyMode) "₹ •••" else "-₹${String.format("%,.0f", debt)}",
                        balanceColor = theme.mildRed,
                        icon = Icons.Default.MoneyOff,
                        iconColor = theme.mildRed,
                        theme = theme,
                        onTransact = { onTransactPocket(pocket) },
                        onLongClick = { activeActionSheetPocket = pocket to bal }
                    )
                }
            }
        }

        // 6. COUNTERPARTIES / PEER LEDGER (Rule 7)
        if (selectedFilterTab == 0 || selectedFilterTab == 6) {
            item { SectionHeader("People (Sundry Debtors & Creditors)", peerPockets.size, theme) }
            if (peerPockets.isEmpty()) {
                item {
                    EmptyActionCard(
                        title = "No Peer Advances Tracked",
                        message = "Track money lent to peers (Sundry Debtors) or borrowed (Sundry Creditors) with zero P&L distortion.",
                        buttonText = "+ Add Counterparty",
                        theme = theme,
                        onClick = { onRequestCreateAccount(PocketType.PEER_RECEIVABLE) }
                    )
                }
            } else {
                items(peerPockets, key = { "peer_${it.id}" }) { pocket ->
                    val bal = pocketBalances[pocket.id] ?: 0.0
                    val isReceivable = pocket.type == PocketType.PEER_RECEIVABLE
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, theme.borderLight, RoundedCornerShape(12.dp))
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
                                Text(
                                    if (isReceivable) "Sundry Debtor • They owe you" else "Sundry Creditor • You owe them",
                                    color = theme.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isPrivacyMode) "₹ •••" else "${if (isReceivable) "+" else "-"}₹${String.format("%,.0f", abs(bal))}",
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

        // 7. GOAL POTS (Rule 6)
        if (selectedFilterTab == 0 || selectedFilterTab == 7) {
            item { SectionHeader("Goal Pots (Quarantined Savings)", goalPockets.size, theme) }
            if (goalPockets.isEmpty()) {
                item {
                    EmptyActionCard(
                        title = "No Goal Pots Active",
                        message = "Quarantine liquid capital away from True Safe Liquid to prevent accidental wealth leakage.",
                        buttonText = "+ Add Goal Pot",
                        theme = theme,
                        onClick = { onRequestCreateAccount(PocketType.GOAL_POT) }
                    )
                }
            } else {
                items(goalPockets, key = { "goal_${it.id}" }) { pocket ->
                    val bal = pocketBalances[pocket.id] ?: 0.0
                    val target = pocket.targetGoalAmount
                    val shortfall = (target - bal).coerceAtLeast(0.0)
                    val targetDateStr = if (pocket.goalTargetDate > 0) {
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(pocket.goalTargetDate))
                    } else "No deadline"

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, theme.borderLight, RoundedCornerShape(12.dp))
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
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                color = theme.accent,
                                trackColor = theme.surfaceAlt
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Target: $targetDateStr • Shortfall: ₹${String.format("%,.0f", shortfall)}", color = theme.textMuted, fontSize = 10.5.sp)
                                Text("Long-press to edit", color = theme.textMuted.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // 8. RECURRING PIPELINE (Rule 8)
        if (selectedFilterTab == 0) {
            item { SectionHeader("Recurring Automation Rules", recurringTransactions.size, theme) }
            if (recurringTransactions.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No Recurring Rules Scheduled", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Automate subscriptions, salaries, SIPs, and EMIs with zero-drift catch-up execution.", color = theme.textMuted, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                items(recurringTransactions, key = { "rec_${it.id}_${it.timestamp}" }) { schedule ->
                    val nextDateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(schedule.timestamp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, theme.borderLight, RoundedCornerShape(12.dp))
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
                                Text(schedule.description.ifBlank { schedule.category }, color = theme.textBright, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text("Repeats ${schedule.recurringFrequency} (due $nextDateStr) • ₹${schedule.amount.toInt()}", color = theme.textMuted, fontSize = 11.sp)
                            }
                            IconButton(onClick = { onTogglePauseRecurring(schedule) }) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Cadence",
                                    tint = theme.accent
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Actions for Account Long-Press
    activeActionSheetPocket?.let { (pocket, bal) ->
        AlertDialog(
            onDismissRequest = { activeActionSheetPocket = null },
            containerColor = theme.surface,
            title = { Text(pocket.name, color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Manage details, limits, or reconciliation benchmarks for this pocket.", color = theme.textMuted, fontSize = 12.sp) },
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
            title = { Text(schedule.description.ifBlank { schedule.category }, color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Modify or delete deterministic recurring automation schedule.", color = theme.textMuted, fontSize = 12.sp) },
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
            .border(1.dp, theme.borderLight, RoundedCornerShape(12.dp))
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
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(12.dp))
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
