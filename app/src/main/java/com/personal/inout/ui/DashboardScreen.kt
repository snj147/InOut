package com.personal.inout.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.billing.PlayBillingManager
import com.personal.inout.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE) }

    val billingManager = remember { PlayBillingManager(context, scope) }
    val isProUnlocked by billingManager.isProUnlocked.collectAsState()

    val pocketBalances by db.stateFlowDao().observePocketBalances().collectAsState(initial = emptyList())
    val rawPockets by db.stateFlowDao().observeAllActivePockets().collectAsState(initial = emptyList())
    val flowRecords by db.stateFlowDao().observeAllFlowRecords().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) }
    var isPrivacyMode by remember { mutableStateOf(false) }
    var showCommandHud by remember { mutableStateOf(false) }
    var editingPocket by remember { mutableStateOf<VaultPocket?>(null) }
    var showCreatePocketDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val theme = AmberTheme

    CompositionLocalProvider(LocalThemeColors provides theme) {
        Scaffold(
            containerColor = theme.bg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    DashboardHeader(
                        totalIn = pocketBalances.filter { it.pocketType == PocketType.LIQUID }.sumOf { it.currentBalance }.coerceAtLeast(0.0),
                        totalOut = flowRecords.filter { it.nature == MovementNature.OUTFLOW }.sumOf { it.amount },
                        isProUser = isProUnlocked,
                        isPrivacyMode = isPrivacyMode,
                        onTogglePrivacy = { isPrivacyMode = !isPrivacyMode }
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = theme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.navigationBarsPadding().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                ) {
                    listOf(
                        Triple(0, "Vault", Icons.Filled.MenuBook),
                        Triple(1, "Pockets", Icons.Filled.AccountBalance),
                        Triple(2, "Insights", Icons.Filled.Insights),
                        Triple(3, "Settings", Icons.Filled.Settings)
                    ).forEach { (idx, title, icon) ->
                        NavigationBarItem(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            icon = { Icon(icon, contentDescription = title) },
                            label = { Text(title, fontSize = 11.sp, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = theme.accent,
                                selectedTextColor = theme.accent,
                                indicatorColor = theme.surfaceAlt,
                                unselectedIconColor = theme.textMuted,
                                unselectedTextColor = theme.textMuted
                            )
                        )
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        if (selectedTab == 1) showCreatePocketDialog = true else showCommandHud = true
                    },
                    containerColor = theme.accent,
                    contentColor = theme.bg,
                    shape = CircleShape,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Icon(if (selectedTab == 1) Icons.Default.AddCard else Icons.Default.Add, contentDescription = "Action", modifier = Modifier.size(26.dp))
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding).background(theme.bg)) {
                when (selectedTab) {
                    0 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp)
                        ) {
                            item { DynamicCockpit(pockets = pocketBalances, isPrivacyMode = isPrivacyMode) }

                            item {
                                Text("Real-Time Flow Stream", color = theme.textBright, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                            }

                            items(flowRecords, key = { it.id }) { flow ->
                                FlowRecordDisplayRow(flow = flow, isPrivacyMode = isPrivacyMode, theme = theme)
                                Divider(color = theme.surfaceAlt.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }
                        }
                    }

                    1 -> AccountPocketsView(
                        pocketBalances = pocketBalances,
                        rawPockets = rawPockets,
                        isPrivacyMode = isPrivacyMode,
                        onEditPocket = { editingPocket = it },
                        onDeletePocketSafe = { pocket, balance ->
                            if (balance != 0.0) {
                                scope.launch { snackbarHostState.showSnackbar("Cannot delete pocket with active balance of ₹$balance") }
                            } else {
                                scope.launch {
                                    db.stateFlowDao().updatePocket(pocket.copy(isArchived = true))
                                    snackbarHostState.showSnackbar("${pocket.name} removed")
                                }
                            }
                        },
                        onRecordCardSettlement = { cardId, liquidId, amt ->
                            scope.launch {
                                db.stateFlowDao().insertFlowRecord(
                                    FlowRecord(
                                        nature = MovementNature.CARD_PAYMENT,
                                        sourcePocketId = liquidId,
                                        targetPocketId = cardId,
                                        amount = amt,
                                        category = "Bill Payment",
                                        note = "Card Dues Clearance"
                                    )
                                )
                            }
                        },
                        onPeerAction = { nature, peerId, liquidId, amt ->
                            scope.launch {
                                db.stateFlowDao().insertFlowRecord(
                                    FlowRecord(
                                        nature = nature,
                                        sourcePocketId = if (nature == MovementNature.PEER_LEND) liquidId else peerId,
                                        targetPocketId = if (nature == MovementNature.PEER_LEND) peerId else liquidId,
                                        amount = amt,
                                        category = "Peer Transfer"
                                    )
                                )
                            }
                        }
                    )

                    2 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Insights Engine Active", color = theme.textMuted)
                    }

                    3 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Settings & Security Vault", color = theme.textMuted)
                    }
                }
            }

            if (showCommandHud) {
                FloatingCommandHud(
                    activePockets = rawPockets,
                    onDismiss = { showCommandHud = false },
                    onSubmit = { nature, srcId, tgtId, amt, cat, note, date, isRec, freq ->
                        scope.launch {
                            db.stateFlowDao().insertFlowRecord(
                                FlowRecord(
                                    nature = nature,
                                    sourcePocketId = srcId,
                                    targetPocketId = tgtId,
                                    amount = amt,
                                    category = cat,
                                    note = note,
                                    timestamp = date,
                                    isRecurring = isRec,
                                    frequency = freq
                                )
                            )
                            showCommandHud = false
                        }
                    }
                )
            }

            if (showCreatePocketDialog) {
                CreatePocketDialog(
                    theme = theme,
                    onDismiss = { showCreatePocketDialog = false },
                    onSave = { name, type, limit ->
                        scope.launch {
                            db.stateFlowDao().insertPocket(
                                VaultPocket(
                                    name = name,
                                    pocketType = type,
                                    subType = type.name,
                                    creditLimit = limit
                                )
                            )
                            showCreatePocketDialog = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FlowRecordDisplayRow(flow: FlowRecord, isPrivacyMode: Boolean, theme: ThemeColors) {
    val isOut = flow.nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.CARD_PAYMENT)
    val flowColor = if (isOut) theme.mildRed else theme.mildGreen
    val dStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(flow.timestamp))

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = if (isOut) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = flowColor,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(flow.note.ifBlank { flow.category }, color = theme.textBright, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                Text(flow.nature.name, color = theme.textMuted, fontSize = 10.sp)
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            val amtStr = if (isPrivacyMode) "₹ •••" else "${if (isOut) "-" else "+"}₹ ${String.format("%,.0f", flow.amount)}"
            Text(amtStr, color = flowColor, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text(dStr, color = theme.textMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun CreatePocketDialog(
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (String, PocketType, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PocketType.LIQUID) }
    var limit by remember { mutableStateOf("") }

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text("Add Vault Pocket", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactInputField(value = name, onValueChange = { name = it }, placeholder = "Pocket Name (e.g., Cash, SBI, Rahul)")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PocketType.values().forEach { t ->
                        val isSel = type == t
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else theme.surfaceAlt)
                                .clickable { type = t }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(t.name.take(4), color = if (isSel) theme.bg else theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (type == PocketType.CREDIT_LINE) {
                    CompactInputField(value = limit, onValueChange = { limit = it }, placeholder = "Credit Limit")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onSave(name, type, limit.toDoubleOrNull() ?: 0.0)
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Save Pocket", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } }
    )
}
