package com.personal.inout.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.personal.inout.billing.PlayBillingManager
import com.personal.inout.data.*
import com.personal.inout.ocr.ReceiptScanner
import com.personal.inout.util.CsvExporter
import com.personal.inout.util.InAppUpdateHelper
import com.personal.inout.util.PdfDossierExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        activity?.let { InAppUpdateHelper.checkForUpdate(it) }
    }

    val billingManager = remember { PlayBillingManager(context, scope) }
    val isProUnlocked by billingManager.isProUnlocked.collectAsState()

    var activeThemeMode by remember {
        val saved = prefs.getString("selected_theme", AppThemeMode.AMBER_OCHRE.name)
        mutableStateOf(AppThemeMode.valueOf(saved ?: AppThemeMode.AMBER_OCHRE.name))
    }

    var cockpitMode by remember {
        val saved = prefs.getString("cockpit_mode", CockpitDisplayMode.SURVIVAL_DAYS_SLIDER.name)
        mutableStateOf(CockpitDisplayMode.valueOf(saved ?: CockpitDisplayMode.SURVIVAL_DAYS_SLIDER.name))
    }

    var dailyBurnCeiling by remember {
        mutableStateOf(prefs.getFloat("daily_burn_ceiling", 450f).toDouble())
    }

    val theme = when (activeThemeMode) {
        AppThemeMode.AMBER_OCHRE -> AmberTheme
        AppThemeMode.OLIVE_MATCHA -> OliveMatchaTheme
        AppThemeMode.NORDIC_SLATE -> NordicSlateTheme
    }

    val pocketBalances by db.stateFlowDao().observePocketBalances().collectAsState(initial = emptyList())
    val rawPockets by db.stateFlowDao().observeAllActivePockets().collectAsState(initial = emptyList())
    val flowRecords by db.stateFlowDao().observeAllFlowRecords().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) }
    var isPrivacyMode by remember { mutableStateOf(false) }
    var showCommandHud by remember { mutableStateOf(false) }
    var editingPocket by remember { mutableStateOf<VaultPocket?>(null) }
    var showCreatePocketDialog by remember { mutableStateOf(false) }
    var showAllRecordsSheet by remember { mutableStateOf(false) }
    var showMockPaywall by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var ocrPrefilledNote by remember { mutableStateOf("") }
    var ocrPrefilledAmount by remember { mutableStateOf<Double?>(null) }

    val cameraSnapLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            scope.launch {
                try {
                    val parsed = ReceiptScanner.processReceiptBitmap(bitmap)
                    ocrPrefilledNote = parsed.merchant
                    ocrPrefilledAmount = parsed.total
                    showCommandHud = true
                } catch (e: Exception) {
                    Toast.makeText(context, "OCR Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraSnapLauncher.launch(null)
        else Toast.makeText(context, "Camera permission needed for receipt OCR", Toast.LENGTH_SHORT).show()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val parsed = ReceiptScanner.processReceipt(context, uri)
                    ocrPrefilledNote = parsed.merchant
                    ocrPrefilledAmount = parsed.total
                    showCommandHud = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Receipt Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    CompositionLocalProvider(LocalThemeColors provides theme) {
        Scaffold(
            containerColor = theme.bg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    CleanVaultHeader(
                        totalLiquid = pocketBalances.filter { it.pocketType == PocketType.LIQUID }.sumOf { it.currentBalance }.coerceAtLeast(0.0),
                        totalSpent = flowRecords.filter { it.nature == MovementNature.OUTFLOW }.sumOf { it.amount },
                        isProUser = isProUnlocked,
                        isPrivacyMode = isPrivacyMode,
                        theme = theme,
                        onTogglePrivacy = { isPrivacyMode = !isPrivacyMode }
                    )
                    DevSandboxTogglePill(
                        isProUnlocked = isProUnlocked,
                        onOpenPaywall = { showMockPaywall = true }
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
                        Triple(1, "Accounts", Icons.Filled.AccountBalance),
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
                if (selectedTab == 0 || selectedTab == 1) {
                    FloatingActionButton(
                        onClick = {
                            if (selectedTab == 1) {
                                showCreatePocketDialog = true
                            } else {
                                ocrPrefilledNote = ""
                                ocrPrefilledAmount = null
                                showCommandHud = true
                            }
                        },
                        containerColor = theme.accent,
                        contentColor = theme.bg,
                        shape = CircleShape,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        Icon(if (selectedTab == 1) Icons.Default.AddCard else Icons.Default.Add, contentDescription = "Action", modifier = Modifier.size(26.dp))
                    }
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
                            item {
                                DynamicCockpit(
                                    pockets = pocketBalances,
                                    flows = flowRecords,
                                    mode = cockpitMode,
                                    configuredDailyBurn = dailyBurnCeiling,
                                    isPrivacyMode = isPrivacyMode
                                )
                            }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                cameraSnapLauncher.launch(null)
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt)
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Scan Receipt", color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("From Gallery", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Real-Time Flow Stream", color = theme.textBright, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                    if (flowRecords.isNotEmpty()) {
                                        Text(
                                            "View All (${flowRecords.size}) →",
                                            color = theme.accent,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { showAllRecordsSheet = true }
                                        )
                                    }
                                }
                            }

                            if (flowRecords.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = theme.surface)
                                    ) {
                                        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                                            Text("No records yet. Tap '+' to commit Spent or Received.", color = theme.textMuted, fontSize = 12.sp)
                                        }
                                    }
                                }
                            } else {
                                items(flowRecords.take(8), key = { it.id }) { flow ->
                                    FlowRecordDisplayRow(flow = flow, isPrivacyMode = isPrivacyMode, theme = theme)
                                    Divider(color = theme.surfaceAlt.copy(alpha = 0.5f), thickness = 0.5.dp)
                                }
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
                                scope.launch { snackbarHostState.showSnackbar("Cannot delete account with active balance of ₹$balance") }
                            } else {
                                scope.launch {
                                    db.stateFlowDao().updatePocket(pocket.copy(isArchived = true))
                                    snackbarHostState.showSnackbar("${pocket.name} removed")
                                }
                            }
                        },
                        onRecordCardSettlement = { cardId, liquidId, amt ->
                            val liquidBal = pocketBalances.firstOrNull { it.pocketId == liquidId }?.currentBalance ?: 0.0
                            if (amt > liquidBal) {
                                scope.launch { snackbarHostState.showSnackbar("Insufficient liquid balance (₹${liquidBal.toInt()}) to pay card bill") }
                            } else {
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
                            }
                        },
                        onPeerAction = { nature, peerId, liquidId, amt ->
                            val liquidBal = pocketBalances.firstOrNull { it.pocketId == liquidId }?.currentBalance ?: 0.0
                            // If spending cash to Lend or Repay, ensure balance doesn't go below 0
                            if ((nature == MovementNature.PEER_LEND || nature == MovementNature.PEER_REPAY) && amt > liquidBal) {
                                scope.launch { snackbarHostState.showSnackbar("Insufficient cash/bank balance to transfer ₹$amt") }
                            } else {
                                scope.launch {
                                    db.stateFlowDao().insertFlowRecord(
                                        FlowRecord(
                                            nature = nature,
                                            sourcePocketId = if (nature == MovementNature.PEER_LEND || nature == MovementNature.PEER_REPAY) liquidId else peerId,
                                            targetPocketId = if (nature == MovementNature.PEER_LEND || nature == MovementNature.PEER_REPAY) peerId else liquidId,
                                            amount = amt,
                                            category = "Peer Transfer"
                                        )
                                    )
                                }
                            }
                        }
                    )

                    2 -> IntelligenceScreen(
                        pocketBalances = pocketBalances,
                        flowRecords = flowRecords,
                        isPrivacyMode = isPrivacyMode
                    )

                    3 -> SettingsScreen(
                        currentTheme = activeThemeMode,
                        currentCockpitMode = cockpitMode,
                        configuredDailyBurn = dailyBurnCeiling,
                        isProUser = isProUnlocked,
                        onSelectTheme = { mode ->
                            activeThemeMode = mode
                            prefs.edit().putString("selected_theme", mode.name).apply()
                        },
                        onSelectCockpitMode = { mode ->
                            cockpitMode = mode
                            prefs.edit().putString("cockpit_mode", mode.name).apply()
                        },
                        onUpdateDailyBurn = { rate ->
                            dailyBurnCeiling = rate
                            prefs.edit().putFloat("daily_burn_ceiling", rate.toFloat()).apply()
                        },
                        onTriggerProPurchase = { showMockPaywall = true },
                        onExportPdfDossier = {
                            PdfDossierExporter.generateAndShareDossier(context, pocketBalances, flowRecords)
                        },
                        onExportCsv = {
                            val compatList = flowRecords.map {
                                Transaction(
                                    id = it.id,
                                    accountId = it.sourcePocketId ?: it.targetPocketId ?: 0L,
                                    flowType = if (it.nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.CARD_PAYMENT)) "OUT" else "IN",
                                    type = it.nature.name,
                                    category = it.category,
                                    amount = it.amount,
                                    timestamp = it.timestamp,
                                    note = it.note,
                                    isRecurring = it.isRecurring,
                                    frequency = it.frequency
                                )
                            }
                            CsvExporter.exportAndShareTransactions(context, compatList)
                        },
                        onClearLedger = {
                            scope.launch {
                                flowRecords.forEach { db.stateFlowDao().deleteFlowRecord(it.id) }
                                snackbarHostState.showSnackbar("All vault records cleared")
                            }
                        }
                    )
                }
            }

            if (showCommandHud) {
                FloatingCommandHud(
                    activePockets = rawPockets,
                    prefilledNote = ocrPrefilledNote,
                    prefilledAmount = ocrPrefilledAmount,
                    onDismiss = { showCommandHud = false },
                    onSubmit = { nature, srcId, tgtId, amt, cat, note, date, isRec, freq ->
                        val srcBal = pocketBalances.firstOrNull { it.pocketId == srcId }
                        val isSrcLiquid = srcBal?.pocketType == PocketType.LIQUID
                        // Overdraft Prevention Lock
                        if (nature == MovementNature.OUTFLOW && isSrcLiquid && amt > (srcBal?.currentBalance ?: 0.0)) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Insufficient funds in ${srcBal?.name}. Balance cannot go negative.")
                            }
                        } else {
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
                    }
                )
            }

            if (showAllRecordsSheet) {
                AllTransactionsSearchSheet(
                    flowRecords = flowRecords,
                    isPrivacyMode = isPrivacyMode,
                    isProUser = isProUnlocked,
                    onDismiss = { showAllRecordsSheet = false },
                    onExportCsv = {
                        val compatList = flowRecords.map {
                            Transaction(
                                id = it.id,
                                accountId = it.sourcePocketId ?: it.targetPocketId ?: 0L,
                                flowType = if (it.nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.CARD_PAYMENT)) "OUT" else "IN",
                                type = it.nature.name,
                                category = it.category,
                                amount = it.amount,
                                timestamp = it.timestamp,
                                note = it.note,
                                isRecurring = it.isRecurring,
                                frequency = it.frequency
                            )
                        }
                        CsvExporter.exportAndShareTransactions(context, compatList)
                    },
                    onExportPdfDossier = {
                        PdfDossierExporter.generateAndShareDossier(context, pocketBalances, flowRecords)
                    }
                )
            }

            if (showCreatePocketDialog) {
                CreateAccountDialog(
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

            if (showMockPaywall) {
                MockPaywallBottomSheet(
                    currentProState = isProUnlocked,
                    onDismiss = { showMockPaywall = false },
                    onSimulatePurchaseSuccess = { billingManager.simulatePurchaseSuccess() },
                    onSimulateRevokePro = { billingManager.simulateRevokePro() }
                )
            }
        }
    }
}

@Composable
private fun CleanVaultHeader(
    totalLiquid: Double,
    totalSpent: Double,
    isProUser: Boolean,
    isPrivacyMode: Boolean,
    theme: ThemeColors,
    onTogglePrivacy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("InOut", color = theme.textBright, fontSize = 22.sp, fontWeight = FontWeight.Black)
                if (isProUser) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.accent)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("PRO", color = theme.bg, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Text("THE VAULT", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isPrivacyMode) "↓ ₹ •••" else "↓ ₹ ${String.format("%,.0f", totalSpent)}",
                    color = theme.mildRed,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isPrivacyMode) "↑ ₹ •••" else "↑ ₹ ${String.format("%,.0f", totalLiquid)}",
                    color = theme.mildGreen,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onTogglePrivacy) {
                Icon(
                    imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle Privacy",
                    tint = theme.textMuted,
                    modifier = Modifier.size(20.dp)
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
                val label = when (flow.nature) {
                    MovementNature.OUTFLOW -> "Spent"
                    MovementNature.INFLOW -> "Received"
                    MovementNature.CARD_PAYMENT -> "Card Bill Paid"
                    MovementNature.PEER_LEND -> "Lent"
                    MovementNature.PEER_COLLECT -> "Collected"
                    MovementNature.PEER_BORROW -> "Borrowed"
                    MovementNature.PEER_REPAY -> "Repaid"
                    MovementNature.TRANSFER -> "Transferred"
                }
                Text(label, color = theme.textMuted, fontSize = 10.sp)
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
private fun CreateAccountDialog(
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
        title = { Text("Add Vault Account", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactInputField(value = name, onValueChange = { name = it }, placeholder = "Account Name (e.g. Cash, HDFC Bank, Rahul)")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        PocketType.LIQUID to "Cash & Bank",
                        PocketType.CREDIT_LINE to "Card (CC)",
                        PocketType.COUNTERPARTY to "Person"
                    ).forEach { (t, lbl) ->
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
                            Text(lbl, color = if (isSel) theme.bg else theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (type == PocketType.CREDIT_LINE) {
                    CompactInputField(value = limit, onValueChange = { limit = it }, placeholder = "Credit Limit (e.g. 50000)")
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
                Text("Save Account", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } }
    )
}
