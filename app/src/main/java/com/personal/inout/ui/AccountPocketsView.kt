package com.personal.inout.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketBalanceSummary
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountPocketsView(
    pocketBalances: List<PocketBalanceSummary>,
    rawPockets: List<VaultPocket>,
    isPrivacyMode: Boolean,
    onEditPocket: (VaultPocket) -> Unit,
    onDeletePocketSafe: (VaultPocket, Double) -> Unit,
    onRecordCardSettlement: (creditPocketId: Long, liquidPocketId: Long, amount: Double) -> Unit,
    onPeerAction: (nature: MovementNature, counterpartyId: Long, liquidId: Long, amount: Double) -> Unit
) {
    val theme = LocalThemeColors.current

    val cashAndBank = remember(pocketBalances) { pocketBalances.filter { it.pocketType == PocketType.LIQUID } }
    val creditAndLoans = remember(pocketBalances) { pocketBalances.filter { it.pocketType == PocketType.CREDIT_LINE } }
    val people = remember(pocketBalances) { pocketBalances.filter { it.pocketType == PocketType.COUNTERPARTY } }

    var settlingCard by remember { mutableStateOf<PocketBalanceSummary?>(null) }
    var peerModalTarget by remember { mutableStateOf<PocketBalanceSummary?>(null) }
    var selectedPocketForMenu by remember { mutableStateOf<Pair<VaultPocket, Double>?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        item {
            AccountSectionHeader(title = "Cash & Bank Accounts", count = cashAndBank.size, theme = theme)
        }
        items(cashAndBank, key = { it.pocketId }) { acc ->
            val raw = rawPockets.firstOrNull { it.id == acc.pocketId }
            AccountCardRow(
                summary = acc,
                isPrivacyMode = isPrivacyMode,
                theme = theme,
                onTap = { raw?.let { onEditPocket(it) } },
                onLongPress = { raw?.let { selectedPocketForMenu = it to acc.currentBalance } },
                actionLabel = "Edit",
                actionColor = theme.accent,
                onActionClick = { raw?.let { onEditPocket(it) } }
            )
        }

        item {
            AccountSectionHeader(title = "Cards & Loans (CC / Dues)", count = creditAndLoans.size, theme = theme)
        }
        items(creditAndLoans, key = { it.pocketId }) { card ->
            val raw = rawPockets.firstOrNull { it.id == card.pocketId }
            val dues = card.currentBalance.coerceAtLeast(0.0)
            val available = (card.creditLimit - dues).coerceIn(0.0, card.creditLimit)

            AccountCardRow(
                summary = card,
                subLabel = if (isPrivacyMode) "Avail: ₹ •••" else "Avail: ₹${String.format("%,.0f", available)} / Limit: ₹${String.format("%,.0f", card.creditLimit)}",
                isPrivacyMode = isPrivacyMode,
                theme = theme,
                onTap = { raw?.let { onEditPocket(it) } },
                onLongPress = { raw?.let { selectedPocketForMenu = it to card.currentBalance } },
                actionLabel = if (dues > 0) "Pay Bill" else "Settled",
                actionColor = if (dues > 0) theme.mildRed else theme.textMuted,
                onActionClick = { if (dues > 0) settlingCard = card }
            )
        }

        item {
            AccountSectionHeader(title = "People (Owed & Lent)", count = people.size, theme = theme)
        }
        items(people, key = { it.pocketId }) { peer ->
            val raw = rawPockets.firstOrNull { it.id == peer.pocketId }
            val net = peer.currentBalance
            val isOwedToYou = net > 0
            val isEven = net == 0.0

            AccountCardRow(
                summary = peer,
                subLabel = when {
                    isEven -> "Even • Settled"
                    isOwedToYou -> "They owe you"
                    else -> "You owe them"
                },
                isPrivacyMode = isPrivacyMode,
                theme = theme,
                onTap = { raw?.let { onEditPocket(it) } },
                onLongPress = { raw?.let { selectedPocketForMenu = it to peer.currentBalance } },
                actionLabel = if (isEven) "Record" else if (isOwedToYou) "Collect" else "Settle",
                actionColor = if (isEven) theme.accent else if (isOwedToYou) theme.mildGreen else theme.mildRed,
                onActionClick = { peerModalTarget = peer }
            )
        }
    }

    // Long Press Context Menu
    selectedPocketForMenu?.let { (pocket, balance) ->
        AlertDialog(
            containerColor = theme.surface,
            onDismissRequest = { selectedPocketForMenu = null },
            title = { Text(pocket.name, color = theme.textBright, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedPocketForMenu = null
                                onEditPocket(pocket)
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = theme.accent, modifier = Modifier.size(18.dp))
                        Text("Edit Account Details", color = theme.textBright, fontSize = 13.sp)
                    }

                    Divider(color = theme.surfaceAlt, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val target = pocket
                                selectedPocketForMenu = null
                                onDeletePocketSafe(target, balance)
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = theme.mildRed, modifier = Modifier.size(18.dp))
                        Text("Delete / Archive Account", color = theme.mildRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedPocketForMenu = null }) {
                    Text("Close", color = theme.textMuted)
                }
            }
        )
    }

    settlingCard?.let { card ->
        CardPayDialog(
            card = card,
            liquidOptions = rawPockets.filter { it.pocketType == PocketType.LIQUID },
            theme = theme,
            onDismiss = { settlingCard = null },
            onConfirm = { liquidId, amt ->
                onRecordCardSettlement(card.pocketId, liquidId, amt)
                settlingCard = null
            }
        )
    }

    peerModalTarget?.let { peer ->
        PeerActionDialog(
            peer = peer,
            liquidOptions = rawPockets.filter { it.pocketType == PocketType.LIQUID },
            theme = theme,
            onDismiss = { peerModalTarget = null },
            onConfirm = { nature, liquidId, amt ->
                onPeerAction(nature, peer.pocketId, liquidId, amt)
                peerModalTarget = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountCardRow(
    summary: PocketBalanceSummary,
    subLabel: String? = null,
    isPrivacyMode: Boolean,
    theme: ThemeColors,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    actionLabel: String,
    actionColor: Color,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = theme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(summary.name, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subLabel ?: summary.subType, color = theme.textMuted, fontSize = 11.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val balanceText = if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", abs(summary.currentBalance))}"
                Text(
                    text = balanceText,
                    color = if (summary.currentBalance < 0 || summary.pocketType == PocketType.CREDIT_LINE) theme.mildRed else theme.textBright,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(actionColor.copy(alpha = 0.2f))
                        .clickable { onActionClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(actionLabel, color = actionColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AccountSectionHeader(title: String, count: Int, theme: ThemeColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("$count", color = theme.textMuted, fontSize = 11.sp)
    }
}

@Composable
private fun CardPayDialog(
    card: PocketBalanceSummary,
    liquidOptions: List<VaultPocket>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onConfirm: (liquidId: Long, amount: Double) -> Unit
) {
    val maxDues = card.currentBalance.coerceAtLeast(0.0)
    var payAmount by remember { mutableStateOf(String.format("%.0f", maxDues)) }
    var selectedLiquidId by remember { mutableStateOf(liquidOptions.firstOrNull()?.id ?: 0L) }

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text("Pay Credit Dues: ${card.name}", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Outstanding Dues: ₹${String.format("%,.2f", maxDues)}", color = theme.mildRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                CompactInputField(
                    value = payAmount,
                    onValueChange = { input ->
                        val parsed = input.toDoubleOrNull() ?: 0.0
                        if (parsed <= maxDues) payAmount = input
                    },
                    placeholder = "Amount to pay"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = payAmount.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0 && selectedLiquidId != 0L) onConfirm(selectedLiquidId, amt)
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Confirm Clearance", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } }
    )
}

@Composable
private fun PeerActionDialog(
    peer: PocketBalanceSummary,
    liquidOptions: List<VaultPocket>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onConfirm: (nature: MovementNature, liquidId: Long, amount: Double) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var selectedNature by remember { mutableStateOf(if (peer.currentBalance >= 0) MovementNature.PEER_LEND else MovementNature.PEER_REPAY) }
    var selectedLiquidId by remember { mutableStateOf(liquidOptions.firstOrNull()?.id ?: 0L) }

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text("Transact with ${peer.name}", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(MovementNature.PEER_LEND to "Lend", MovementNature.PEER_COLLECT to "Collect").forEach { (nat, lbl) ->
                        val isSel = selectedNature == nat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else theme.surfaceAlt)
                                .clickable { selectedNature = nat }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(lbl, color = if (isSel) theme.bg else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                CompactInputField(value = amount, onValueChange = { amount = it }, placeholder = "Amount")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0 && selectedLiquidId != 0L) onConfirm(selectedNature, selectedLiquidId, amt)
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Commit", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } }
    )
}
