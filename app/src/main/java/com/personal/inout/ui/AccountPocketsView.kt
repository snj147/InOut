package com.personal.inout.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val capitalPockets = remember(rawPockets) {
        rawPockets.filter {
            it.type in listOf(
                PocketType.INVESTMENT,
                PocketType.FIXED_ASSET,
                PocketType.GOAL_POT,
                PocketType.PEER_RECEIVABLE
            )
        }
    }
    val debtPockets = remember(rawPockets) {
        rawPockets.filter {
            it.type in listOf(
                PocketType.CREDIT_CARD,
                PocketType.LIABILITY_LOAN,
                PocketType.PEER_PAYABLE
            )
        }
    }

    // 0: ALL, 1: LIQUID, 2: DEBT, 3: CAPITAL, 4: AUTO
    var activeModeIndex by remember { mutableIntStateOf(0) }

    var selectedContextPocket by remember { mutableStateOf<Pair<LedgerPocket, Double>?>(null) }
    var selectedContextRecurring by remember { mutableStateOf<LedgerTransaction?>(null) }
    var pendingSafeDeactivatePocket by remember { mutableStateOf<Pair<LedgerPocket, Double>?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp)
    ) {
        // Universal 5-Pill Rail (Zero Horizontal Scroll)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.surfaceAlt)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    "ALL ${rawPockets.size}",
                    "💧 LIQ ${liquidPockets.size}",
                    "💳 DEBT ${debtPockets.size}",
                    "🏛 CAP ${capitalPockets.size}",
                    "🔄 AUTO ${recurringTransactions.size}"
                ).forEachIndexed { idx, label ->
                    val isSel = activeModeIndex == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) theme.accent else Color.Transparent)
                            .clickable { activeModeIndex = idx }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) theme.bg else theme.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // VIEW 1: LIQUID ACCOUNTS
        if (activeModeIndex == 0 || activeModeIndex == 1) {
            val totalLiquid = liquidPockets.sumOf { pocketBalances[it.id] ?: 0.0 }
            item {
                SectionSummaryHeader("SPENDABLE LIQUID", totalLiquid, isPrivacyMode, theme)
            }

            if (liquidPockets.isEmpty()) {
                item {
                    CompactAddActionRow(
                        title = "No Liquid Bank Accounts",
                        theme = theme,
                        onAdd = { onRequestCreateAccount(PocketType.LIQUID) }
                    )
                }
            } else {
                item {
                    UnifiedStatementGroup(theme = theme) {
                        liquidPockets.forEachIndexed { index, pocket ->
                            val bal = pocketBalances[pocket.id] ?: 0.0
                            val isCash = pocket.name.equals("Cash in Hand", ignoreCase = true)
                            val icon = if (isCash) Icons.Default.Payments else if (pocket.type == PocketType.PREPAID_WALLET) Icons.Default.AccountBalanceWallet else Icons.Default.AccountBalance

                            UnifiedAccountRow(
                                title = pocket.name,
                                sub = if (isCash) "Physical Cash Reserve" else if (pocket.type == PocketType.PREPAID_WALLET) "Prepaid Stored-Value" else "Liquid Bank Asset",
                                balanceDisplay = if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", bal)}",
                                balanceColor = theme.textBright,
                                icon = icon,
                                iconColor = theme.mildGreen,
                                theme = theme,
                                onTap = { onTransactPocket(pocket) },
                                onLongPress = { selectedContextPocket = pocket to bal }
                            )

                            if (index < liquidPockets.lastIndex) {
                                HorizontalDivider(color = theme.borderLight.copy(alpha = 0.4f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }

        // VIEW 2: LIABILITIES & DEBT
        if (activeModeIndex == 0 || activeModeIndex == 2) {
            val totalDebt = debtPockets.sumOf { abs(pocketBalances[it.id] ?: 0.0) }
            item {
                SectionSummaryHeader("LIABILITIES & BORROWINGS", -totalDebt, isPrivacyMode, theme)
            }

            if (debtPockets.isEmpty()) {
                item {
                    CompactAddActionRow(
                        title = "No Active Debt or Credit Lines",
                        theme = theme,
                        onAdd = { onRequestCreateAccount(PocketType.CREDIT_CARD) }
                    )
                }
            } else {
                item {
                    UnifiedStatementGroup(theme = theme) {
                        debtPockets.forEachIndexed { index, pocket ->
                            val bal = pocketBalances[pocket.id] ?: 0.0
                            val outstanding = if (bal < 0.0) abs(bal) else 0.0
                            val dueStr = if (pocket.billDueDay > 0) "Due ${pocket.billDueDay}th" else "Statement Balance"

                            UnifiedAccountRow(
                                title = pocket.name,
                                sub = if (pocket.type == PocketType.CREDIT_CARD) "Limit: ₹${String.format("%,.0f", pocket.creditLimit)} • $dueStr" else "Liability Loan",
                                balanceDisplay = if (isPrivacyMode) "₹ •••" else "- ₹ ${String.format("%,.0f", outstanding)}",
                                balanceColor = theme.mildRed,
                                icon = Icons.Default.CreditCard,
                                iconColor = theme.mildRed,
                                theme = theme,
                                onTap = { onTransactPocket(pocket) },
                                onLongPress = { selectedContextPocket = pocket to bal }
                            )

                            if (index < debtPockets.lastIndex) {
                                HorizontalDivider(color = theme.borderLight.copy(alpha = 0.4f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }

        // VIEW 3: CAPITAL & RESERVES
        if (activeModeIndex == 0 || activeModeIndex == 3) {
            val totalCapital = capitalPockets.sumOf { pocketBalances[it.id] ?: 0.0 }
            item {
                SectionSummaryHeader("CAPITAL RESERVES & WEALTH", totalCapital, isPrivacyMode, theme)
            }

            if (capitalPockets.isEmpty()) {
                item {
                    CompactAddActionRow(
                        title = "No Capital Holdings or Goal Reserves",
                        theme = theme,
                        onAdd = { onRequestCreateAccount(PocketType.INVESTMENT) }
                    )
                }
            } else {
                item {
                    UnifiedStatementGroup(theme = theme) {
                        capitalPockets.forEachIndexed { index, pocket ->
                            val bal = pocketBalances[pocket.id] ?: 0.0
                            val icon = when (pocket.type) {
                                PocketType.INVESTMENT -> Icons.Default.TrendingUp
                                PocketType.GOAL_POT -> Icons.Default.Adjust
                                PocketType.FIXED_ASSET -> Icons.Default.Domain
                                else -> Icons.Default.People
                            }

                            UnifiedAccountRow(
                                title = pocket.name,
                                sub = when (pocket.type) {
                                    PocketType.INVESTMENT -> "Mark-to-Market Portfolio Asset"
                                    PocketType.GOAL_POT -> "Quarantined Target: ₹${String.format("%,.0f", pocket.targetGoalAmount)}"
                                    PocketType.FIXED_ASSET -> "WDV Capital Asset"
                                    else -> "Sundry Debtor (Receivable)"
                                },
                                balanceDisplay = if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", bal)}",
                                balanceColor = theme.accent,
                                icon = icon,
                                iconColor = theme.accent,
                                theme = theme,
                                onTap = { onTransactPocket(pocket) },
                                onLongPress = { selectedContextPocket = pocket to bal }
                            )

                            if (index < capitalPockets.lastIndex) {
                                HorizontalDivider(color = theme.borderLight.copy(alpha = 0.4f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }

        // VIEW 4: RECURRING MANDATES (AUTO)
        if (activeModeIndex == 0 || activeModeIndex == 4) {
            val totalRecurring = recurringTransactions.sumOf { it.amount }
            item {
                SectionSummaryHeader("ACTIVE AUTOMATION MANDATES", totalRecurring, isPrivacyMode, theme)
            }

            if (recurringTransactions.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, theme.borderLight, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No Recurring Mandates Scheduled", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Automate subscriptions, SIPs, and bills with zero-drift execution.", color = theme.textMuted, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                item {
                    UnifiedStatementGroup(theme = theme) {
                        recurringTransactions.forEachIndexed { index, schedule ->
                            val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(schedule.timestamp))
                            UnifiedAccountRow(
                                title = schedule.description.ifBlank { schedule.category },
                                sub = "Repeats ${schedule.recurringFrequency} • Next: $dateStr",
                                balanceDisplay = if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", schedule.amount)}",
                                balanceColor = theme.accent,
                                icon = Icons.Default.Schedule,
                                iconColor = theme.accent,
                                theme = theme,
                                onTap = { onEditRecurring(schedule) },
                                onLongPress = { selectedContextRecurring = schedule }
                            )

                            if (index < recurringTransactions.lastIndex) {
                                HorizontalDivider(color = theme.borderLight.copy(alpha = 0.4f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Centered Context Action Voucher Modal
    selectedContextPocket?.let { (pocket, bal) ->
        CenteredAccountVoucherModal(
            pocket = pocket,
            balance = bal,
            theme = theme,
            onDismiss = { selectedContextPocket = null },
            onQuickAction = {
                selectedContextPocket = null
                onTransactPocket(pocket)
            },
            onEdit = {
                selectedContextPocket = null
                onEditPocket(pocket)
            },
            onArchiveDelete = {
                selectedContextPocket = null
                pendingSafeDeactivatePocket = pocket to bal
            }
        )
    }

    // Centered Safe Deactivation Guard Modal
    pendingSafeDeactivatePocket?.let { (pocket, bal) ->
        CenteredSafePurgeGuardModal(
            pocket = pocket,
            balance = bal,
            theme = theme,
            onDismiss = { pendingSafeDeactivatePocket = null },
            onConfirm = {
                pendingSafeDeactivatePocket = null
                onDeletePocketSafe(pocket, bal)
            }
        )
    }

    // Centered Recurring Mandate Context Modal
    selectedContextRecurring?.let { schedule ->
        CenteredRecurringVoucherModal(
            rule = schedule,
            theme = theme,
            onDismiss = { selectedContextRecurring = null },
            onEdit = {
                selectedContextRecurring = null
                onEditRecurring(schedule)
            },
            onDelete = {
                selectedContextRecurring = null
                onDeleteRecurringSafe(schedule)
            }
        )
    }
}

@Composable
private fun SectionSummaryHeader(title: String, totalAmt: Double, isPrivacy: Boolean, theme: ThemeColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(
            text = if (isPrivacy) "₹ •••" else "₹ ${String.format("%,.0f", totalAmt)}",
            color = theme.textBright,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun UnifiedStatementGroup(theme: ThemeColors, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, theme.borderLight, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnifiedAccountRow(
    title: String,
    sub: String,
    balanceDisplay: String,
    balanceColor: Color,
    icon: ImageVector,
    iconColor: Color,
    theme: ThemeColors,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f))
                    .padding(7.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
            }
            Column {
                Text(title, color = theme.textBright, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = theme.textMuted, fontSize = 10.5.sp, maxLines = 1)
            }
        }

        Text(
            text = balanceDisplay,
            color = balanceColor,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CompactAddActionRow(title: String, theme: ThemeColors, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(theme.surfaceAlt)
            .border(1.dp, theme.borderLight, RoundedCornerShape(10.dp))
            .clickable { onAdd() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = theme.textMuted, fontSize = 11.5.sp)
        Text("+ Create", color = theme.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CenteredAccountVoucherModal(
    pocket: LedgerPocket,
    balance: Double,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onQuickAction: () -> Unit,
    onEdit: () -> Unit,
    onArchiveDelete: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 380.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pocket.name, color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            pocket.type.name.replace("_", " "),
                            color = theme.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Current Book Balance: ₹ ${String.format("%,.2f", balance)}",
                        color = theme.textMuted,
                        fontSize = 11.sp
                    )

                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    TactileActionButton(
                        label = when (pocket.type) {
                            PocketType.CREDIT_CARD -> "💳 Settle Card Dues"
                            PocketType.GOAL_POT -> "🎯 Sweep Savings (In / Out)"
                            PocketType.INVESTMENT -> "📈 Revalue / Liquidate Holding"
                            PocketType.PEER_RECEIVABLE, PocketType.PEER_PAYABLE -> "🤝 Settle Peer Balance"
                            PocketType.LIABILITY_LOAN -> "🏛 Log Monthly EMI Principal"
                            else -> "⚡ Quick Transact"
                        },
                        isPrimary = true,
                        theme = theme,
                        onClick = onQuickAction
                    )

                    TactileActionButton(
                        label = "✎ Edit Name & Configuration",
                        isPrimary = false,
                        theme = theme,
                        onClick = onEdit
                    )

                    TactileActionButton(
                        label = "✕ Deactivate / Archive Account",
                        isPrimary = false,
                        isDanger = true,
                        theme = theme,
                        onClick = onArchiveDelete
                    )

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close", color = theme.textMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredSafePurgeGuardModal(
    pocket: LedgerPocket,
    balance: Double,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val canDeactivate = balance == 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 380.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("DEACTIVATE ACCOUNT", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Are you sure you want to deactivate \"${pocket.name}\"?",
                        color = theme.textMuted,
                        fontSize = 12.sp
                    )

                    if (!canDeactivate) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.mildRed.copy(alpha = 0.15f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Active balance is ₹${String.format("%,.0f", balance)}. Settle balance to ₹0 first before deactivating.",
                                color = theme.mildRed,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Text(
                            text = "Balance is zero. All historical entries will remain safely in the audit register.",
                            color = theme.mildGreen,
                            fontSize = 11.5.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = theme.textMuted)
                        }

                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed),
                            enabled = canDeactivate
                        ) {
                            Text("Deactivate", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredRecurringVoucherModal(
    rule: LedgerTransaction,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 380.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(rule.description.ifBlank { rule.category }, color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Cadence: ${rule.recurringFrequency} • Scheduled: ₹${rule.amount.toInt()}", color = theme.textMuted, fontSize = 11.sp)

                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    TactileActionButton(
                        label = "✎ Edit Mandate Amount & Dates",
                        isPrimary = true,
                        theme = theme,
                        onClick = onEdit
                    )

                    TactileActionButton(
                        label = "✕ Terminate & Remove Mandate",
                        isPrimary = false,
                        isDanger = true,
                        theme = theme,
                        onClick = onDelete
                    )

                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Close", color = theme.textMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TactileActionButton(
    label: String,
    isPrimary: Boolean = false,
    isDanger: Boolean = false,
    theme: ThemeColors,
    onClick: () -> Unit
) {
    val bg = when {
        isPrimary -> theme.accent
        isDanger -> theme.mildRed.copy(alpha = 0.12f)
        else -> theme.surfaceAlt
    }
    val fg = when {
        isPrimary -> theme.bg
        isDanger -> theme.mildRed
        else -> theme.textBright
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, if (isDanger) theme.mildRed.copy(alpha = 0.3f) else theme.borderLight, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = "→", color = fg.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
