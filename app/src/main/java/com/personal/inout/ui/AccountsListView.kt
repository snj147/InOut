package com.personal.inout.ui

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.personal.inout.data.AccountBalanceResult
import com.personal.inout.data.AccountClassification
import com.personal.inout.data.LedgerAccount
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountsListView(
    accountsWithBalances: List<AccountBalanceResult>,
    rawAccounts: List<LedgerAccount>,
    isPrivacyMode: Boolean,
    onAddCardClick: () -> Unit,
    onEditAccount: (LedgerAccount) -> Unit,
    onDeleteAccountWithUndo: (LedgerAccount) -> Unit,
    onQuickDepositOrRefill: (LedgerAccount) -> Unit
) {
    val theme = LocalThemeColors.current

    // Grouping into 3 clear vertical buckets
    val walletAccounts = remember(accountsWithBalances) {
        accountsWithBalances.filter {
            it.classification == AccountClassification.ASSET &&
            it.subType in listOf("CASH", "BANK", "GENERAL")
        }
    }

    val liabilityAccounts = remember(accountsWithBalances) {
        accountsWithBalances.filter {
            it.classification == AccountClassification.LIABILITY &&
            it.subType in listOf("CREDIT", "LOAN")
        }
    }

    val peopleAccounts = remember(accountsWithBalances) {
        accountsWithBalances.filter {
            it.subType in listOf("BORROWER", "LENDER")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        // --- Section 1: Wallets & Banks ---
        item {
            AccountSectionHeader(
                title = "Wallets & Bank Accounts",
                count = walletAccounts.size,
                icon = Icons.Outlined.AccountBalanceWallet,
                theme = theme
            )
        }

        if (walletAccounts.isEmpty()) {
            item {
                EmptyAccountCard(message = "No cash or bank accounts added yet.", theme = theme)
            }
        } else {
            items(walletAccounts, key = { it.accountId }) { acc ->
                val raw = rawAccounts.firstOrNull { it.id == acc.accountId }
                AccountVerticalRow(
                    account = acc,
                    rawAccount = raw,
                    isPrivacyMode = isPrivacyMode,
                    theme = theme,
                    onEdit = { raw?.let { onEditAccount(it) } },
                    onDelete = { raw?.let { onDeleteAccountWithUndo(it) } },
                    onAction = { raw?.let { onQuickDepositOrRefill(it) } },
                    actionLabel = "+ Deposit",
                    actionColor = theme.mildGreen
                )
            }
        }

        // --- Section 2: Credit Cards & Dues ---
        item {
            Spacer(Modifier.height(6.dp))
            AccountSectionHeader(
                title = "Credit Cards & Formal Debts",
                count = liabilityAccounts.size,
                icon = Icons.Outlined.CreditCard,
                theme = theme
            )
        }

        if (liabilityAccounts.isEmpty()) {
            item {
                EmptyAccountCard(message = "No credit cards or formal loans tracked.", theme = theme)
            }
        } else {
            items(liabilityAccounts, key = { it.accountId }) { acc ->
                val raw = rawAccounts.firstOrNull { it.id == acc.accountId }
                val isSettled = acc.netBalance <= 0.0

                AccountVerticalRow(
                    account = acc,
                    rawAccount = raw,
                    isPrivacyMode = isPrivacyMode,
                    theme = theme,
                    onEdit = { raw?.let { onEditAccount(it) } },
                    onDelete = { raw?.let { onDeleteAccountWithUndo(it) } },
                    onAction = { raw?.let { onQuickDepositOrRefill(it) } },
                    actionLabel = if (isSettled) "Cleared" else "Pay Bill",
                    actionColor = if (isSettled) theme.textMuted else theme.mildRed,
                    isSettled = isSettled
                )
            }
        }

        // --- Section 3: People / Informal Borrow & Lend ---
        item {
            Spacer(Modifier.height(6.dp))
            AccountSectionHeader(
                title = "People (Lent & Borrowed)",
                count = peopleAccounts.size,
                icon = Icons.Outlined.PeopleAlt,
                theme = theme
            )
        }

        if (peopleAccounts.isEmpty()) {
            item {
                EmptyAccountCard(message = "No informal peer loans tracked.", theme = theme)
            }
        } else {
            items(peopleAccounts, key = { it.accountId }) { acc ->
                val raw = rawAccounts.firstOrNull { it.id == acc.accountId }
                val isLender = acc.subType == "LENDER"
                val isSettled = acc.netBalance == 0.0

                AccountVerticalRow(
                    account = acc,
                    rawAccount = raw,
                    isPrivacyMode = isPrivacyMode,
                    theme = theme,
                    onEdit = { raw?.let { onEditAccount(it) } },
                    onDelete = { raw?.let { onDeleteAccountWithUndo(it) } },
                    onAction = { raw?.let { onQuickDepositOrRefill(it) } },
                    actionLabel = if (isSettled) "Settled" else if (isLender) "Repay" else "Collect",
                    actionColor = if (isSettled) theme.textMuted else if (isLender) theme.mildRed else theme.mildGreen,
                    isSettled = isSettled
                )
            }
        }
    }
}

// ---------------- SUB-COMPONENTS ----------------

@Composable
private fun AccountSectionHeader(
    title: String,
    count: Int,
    icon: ImageVector,
    theme: ThemeColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
            Text(title, color = theme.textBright, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
        Text("$count", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountVerticalRow(
    account: AccountBalanceResult,
    rawAccount: LedgerAccount?,
    isPrivacyMode: Boolean,
    theme: ThemeColors,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAction: () -> Unit,
    actionLabel: String,
    actionColor: Color,
    isSettled: Boolean = false
) {
    var showActionMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onAction,
                onLongClick = { showActionMenu = true }
            ),
        colors = CardDefaults.cardColors(containerColor = theme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(theme.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (account.subType) {
                        "BANK" -> Icons.Default.AccountBalance
                        "CASH" -> Icons.Default.Payments
                        "CREDIT" -> Icons.Default.CreditCard
                        "LENDER" -> Icons.Default.CallMade
                        "BORROWER" -> Icons.Default.CallReceived
                        else -> Icons.Default.Wallet
                    }
                    Icon(icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(18.dp))
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = account.accountName,
                            color = theme.textBright,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (isSettled) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(theme.mildGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("SETTLED", color = theme.mildGreen, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    val sublabel = when (account.subType) {
                        "CREDIT" -> {
                            val limit = rawAccount?.creditLimit ?: 0.0
                            val avail = (limit - account.netBalance).coerceAtLeast(0.0)
                            if (isPrivacyMode) "Limit: ₹ •••" else "Avail limit: ₹${String.format("%,.0f", avail)}"
                        }
                        "LENDER" -> "You owe them"
                        "BORROWER" -> "Owes you"
                        else -> "Liquid Available"
                    }
                    Text(sublabel, color = theme.textMuted, fontSize = 11.sp)
                }
            }

            // Right amount & action button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    val formatted = if (isPrivacyMode) {
                        "₹ ••••••"
                    } else {
                        "₹ ${String.format("%,.0f", account.netBalance)}"
                    }

                    Text(
                        text = formatted,
                        color = when {
                            account.classification == AccountClassification.LIABILITY && account.netBalance > 0 -> theme.mildRed
                            account.subType == "BORROWER" -> theme.mildGreen
                            else -> theme.textBright
                        },
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    if (rawAccount != null && rawAccount.dueDateMillis > 0L && !isSettled) {
                        val dStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(rawAccount.dueDateMillis))
                        Text("Due $dStr", color = theme.textMuted, fontSize = 10.sp)
                    }
                }

                // Quick Action Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(actionColor.copy(alpha = 0.18f))
                        .clickable { onAction() }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = actionLabel,
                        color = actionColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Two-row action sheet on long press (No PIN)
    if (showActionMenu) {
        AlertDialog(
            containerColor = theme.surface,
            onDismissRequest = { showActionMenu = false },
            title = { Text(account.accountName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = theme.textBright) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showActionMenu = false
                                onEdit()
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                        Text("Edit Account Details", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Divider(color = theme.surfaceAlt, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showActionMenu = false
                                onDelete()
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = theme.mildRed, modifier = Modifier.size(16.dp))
                        Text("Delete Account", color = theme.mildRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showActionMenu = false }) {
                    Text("Close", color = theme.textMuted, fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
private fun EmptyAccountCard(message: String, theme: ThemeColors) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface.copy(alpha = 0.6f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(message, color = theme.textMuted, fontSize = 11.5.sp)
        }
    }
}
