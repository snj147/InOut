package com.personal.inout.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.personal.inout.billing.PlayBillingManager
import com.personal.inout.data.*
import com.personal.inout.ocr.ReceiptScanner
import com.personal.inout.util.*
import kotlinx.coroutines.delay
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

    val alertManager = remember { VaultAlertManager() }
    val ledgerEngine = remember { VaultLedgerEngine(db.stateFlowDao()) }

    LaunchedEffect(Unit) {
        activity?.let { InAppUpdateHelper.checkForUpdate(it) }
        val generated = ledgerEngine.catchUpRecurringRules()
        if (generated > 0) {
            alertManager.showAlert("Auto-recorded $generated recurring transaction(s)", AlertType.SUCCESS)
        }
    }

    val billingManager = remember { PlayBillingManager(context, scope) }
    val isProUnlocked by billingManager.isProUnlocked.collectAsState()

    var activeThemeMode by remember {
        val saved = prefs.getString("selected_theme", AppThemeMode.AMBER_OCHRE.name)
        mutableStateOf(AppThemeMode.valueOf(saved ?: AppThemeMode.AMBER_OCHRE.name))
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

    val recurringTemplates = remember(flowRecords) {
        flowRecords.filter { it.isRecurring && it.frequency != "NONE" }
            .distinctBy { "${it.note}_${it.amount}_${it.frequency}" }
    }

    var selectedTab by remember { mutableStateOf(0) }
    var isPrivacyMode by remember { mutableStateOf(false) }

    var showCommandHud by remember { mutableStateOf(false) }
    var selectedPocketIdForHud by remember { mutableStateOf<Long?>(null) }
    var hudInDialogError by remember { mutableStateOf<String?>(null) }

    var editingPocket by remember { mutableStateOf<VaultPocket?>(null) }
    var editingFlowRecord by remember { mutableStateOf<FlowRecord?>(null) }
    var showCreatePocketDialog by remember { mutableStateOf(false) }
    var showAllRecordsSheet by remember { mutableStateOf(false) }
    var showMockPaywall by remember { mutableStateOf(false) }

    var naturalLanguageInput by remember { mutableStateOf("") }
    val placeholderHints = listOf(
        "Spent [Amount] on [Item]",
        "Lent [Amount] to [Name]",
        "Got [Amount] from [Name/Source]",
        "Borrowed [Amount] from [Name]",
        "Transferred [Amount] from [Bank] to [Bank]"
    )
    var currentHintIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3400)
            currentHintIndex = (currentHintIndex + 1) % placeholderHints.size
        }
    }

    var ocrPrefilledNote by remember { mutableStateOf("") }
    var ocrPrefilledAmount by remember { mutableStateOf<Double?>(null) }

    fun processReceiptResult(bitmap: Bitmap) {
        scope.launch {
            try {
                val useCloud = prefs.getBoolean("use_cloud_vision", false)
                val cloudKey = prefs.getString("cloud_vision_api_key", "") ?: ""
                val parsed = ReceiptScanner.processReceiptBitmap(bitmap, useCloud, cloudKey)

                ocrPrefilledNote = parsed.merchant
                ocrPrefilledAmount = parsed.total
                hudInDialogError = null
                selectedPocketIdForHud = rawPockets.firstOrNull { it.pocketType == PocketType.LIQUID }?.id
                showCommandHud = true
                alertManager.showAlert("Scanned: ${parsed.merchant} (₹${parsed.total ?: 0.0})", AlertType.INFO)
            } catch (e: Exception) {
                alertManager.showAlert("Receipt OCR Error: ${e.localizedMessage}", AlertType.ERROR)
            }
        }
    }

    val cameraSnapLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) processReceiptResult(bitmap)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraSnapLauncher.launch(null)
        else alertManager.showAlert("Camera permission needed for receipt scanning", AlertType.WARNING)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val useCloud = prefs.getBoolean("use_cloud_vision", false)
                    val cloudKey = prefs.getString("cloud_vision_api_key", "") ?: ""
                    val parsed = ReceiptScanner.processReceipt(context, uri, useCloud, cloudKey)

                    ocrPrefilledNote = parsed.merchant
                    ocrPrefilledAmount = parsed.total
                    hudInDialogError = null
                    selectedPocketIdForHud = rawPockets.firstOrNull { it.pocketType == PocketType.LIQUID }?.id
                    showCommandHud = true
                    alertManager.showAlert("Scanned: ${parsed.merchant} (₹${parsed.total ?: 0.0})", AlertType.INFO)
                } catch (e: Exception) {
                    alertManager.showAlert("Receipt Parse Error: ${e.localizedMessage}", AlertType.ERROR)
                }
            }
        }
    }

    fun executeNaturalLanguageCommand(text: String) {
        val parsed = NaturalLanguageParser.parse(text, rawPockets)
        if (parsed != null) {
            scope.launch {
                val autoSplit = prefs.getBoolean("auto_split_debit", false)

                var sourceId: Long?
                var targetId: Long?

                if (parsed.nature == MovementNature.TRANSFER) {
                    sourceId = parsed.matchedPocketId
                    targetId = parsed.targetPocketId
                } else if (parsed.nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND)) {
                    sourceId = parsed.matchedPocketId
                    targetId = null
                } else {
                    sourceId = null
                    targetId = parsed.matchedPocketId
                }

                if (parsed.targetPersonName != null) {
                    var personPocket = rawPockets.firstOrNull {
                        it.pocketType == PocketType.COUNTERPARTY && it.name.equals(parsed.targetPersonName, ignoreCase = true)
                    }
                    if (personPocket == null) {
                        val newId = db.stateFlowDao().insertPocket(
                            VaultPocket(
                                name = parsed.targetPersonName,
                                pocketType = PocketType.COUNTERPARTY,
                                subType = "PEER"
                            )
                        )
                        personPocket = VaultPocket(id = newId, name = parsed.targetPersonName, pocketType = PocketType.COUNTERPARTY, subType = "PEER")
                    }

                    if (parsed.nature == MovementNature.PEER_LEND) {
                        sourceId = parsed.matchedPocketId
                        targetId = personPocket.id
                    } else {
                        sourceId = personPocket.id
                        targetId = parsed.matchedPocketId
                    }
                }

                when (val res = ledgerEngine.recordMovement(
                    nature = parsed.nature,
                    sourcePocketId = sourceId,
                    targetPocketId = targetId,
                    amount = parsed.amount,
                    category = parsed.category,
                    note = parsed.merchant,
                    timestamp = parsed.timestamp,
                    autoSplitEnabled = autoSplit
                )) {
                    is VaultExecutionResult.OverdraftError -> alertManager.showAlert(res.message, AlertType.ERROR)
                    is VaultExecutionResult.Success -> {
                        alertManager.showAlert(res.summary, AlertType.SUCCESS)
                        naturalLanguageInput = ""
                    }
                }
            }
        } else {
            alertManager.showAlert("Action missing. Start with: Spent, Lent, Got, Borrowed, Transferred", AlertType.WARNING)
        }
    }

    CompositionLocalProvider(
        LocalThemeColors provides theme,
        LocalVaultAlertManager provides alertManager
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = theme.bg,
                topBar = {
                    Column {
                        CleanVaultHeader(
                            totalLiquid = pocketBalances.filter { it.pocketType == PocketType.LIQUID }.sumOf { it.currentBalance }.coerceAtLeast(0.0),
                            totalSpent = flowRecords.filter { it.nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.PEER_REPAY) }.sumOf { it.amount },
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
                                    hudInDialogError = null
                                    selectedPocketIdForHud = rawPockets.firstOrNull { it.pocketType == PocketType.LIQUID }?.id
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
                                    val totalLiquid = pocketBalances.filter { it.pocketType == PocketType.LIQUID }.sumOf { it.currentBalance }.coerceAtLeast(0.0)
                                    val runwayDays = if (dailyBurnCeiling > 0) (totalLiquid / dailyBurnCeiling).toInt() else 0

                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("RUNWAY SURVIVAL HORIZON", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (runwayDays > 30) theme.mildGreen.copy(alpha = 0.2f) else theme.mildRed.copy(alpha = 0.2f))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        if (runwayDays > 30) "Solvent" else "Tight",
                                                        color = if (runwayDays > 30) theme.mildGreen else theme.mildRed,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            Text(
                                                text = if (isPrivacyMode) "•• Days" else "$runwayDays Days",
                                                color = theme.textBright,
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Black
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Burn Target: ₹${dailyBurnCeiling.toInt()}/day", color = theme.textMuted, fontSize = 11.sp)
                                                Text("Available: ₹${String.format("%,.0f", totalLiquid)}", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.5.dp, theme.accent.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(theme.accent.copy(alpha = 0.2f))
                                                    .padding(6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Bolt, contentDescription = null, tint = theme.accent, modifier = Modifier.size(18.dp))
                                            }

                                            Spacer(Modifier.width(10.dp))

                                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                                if (naturalLanguageInput.isEmpty()) {
                                                    Text(
                                                        text = placeholderHints[currentHintIndex],
                                                        color = theme.textMuted.copy(alpha = 0.7f),
                                                        fontSize = 13.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                                BasicTextField(
                                                    value = naturalLanguageInput,
                                                    onValueChange = { naturalLanguageInput = it },
                                                    singleLine = true,
                                                    textStyle = TextStyle(
                                                        color = theme.textBright,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    cursorBrush = SolidColor(theme.accent),
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(onDone = {
                                                        executeNaturalLanguageCommand(naturalLanguageInput)
                                                    }),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            if (naturalLanguageInput.isNotBlank()) {
                                                IconButton(onClick = { executeNaturalLanguageCommand(naturalLanguageInput) }) {
                                                    Icon(Icons.Default.Send, contentDescription = "Commit", tint = theme.accent, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
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
                                                Text("No records yet. Type above or tap '+' to commit flow.", color = theme.textMuted, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                } else {
                                    items(flowRecords.take(8), key = { it.id }) { flow ->
                                        FlowRecordDisplayRow(
                                            flow = flow,
                                            isPrivacyMode = isPrivacyMode,
                                            theme = theme,
                                            onClick = { editingFlowRecord = flow }
                                        )
                                        Divider(color = theme.surfaceAlt.copy(alpha = 0.5f), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }

                        1 -> AccountPocketsView(
                            pocketBalances = pocketBalances,
                            rawPockets = rawPockets,
                            recurringSchedules = recurringTemplates,
                            isPrivacyMode = isPrivacyMode,
                            onTransactPocket = { pocket ->
                                selectedPocketIdForHud = pocket.id
                                ocrPrefilledNote = ""
                                ocrPrefilledAmount = null
                                hudInDialogError = null
                                showCommandHud = true
                            },
                            onEditPocket = { editingPocket = it },
                            onDeletePocketSafe = { pocket, balance ->
                                if (balance != 0.0) {
                                    alertManager.showAlert("Cannot delete account with active balance of ₹${balance.toInt()}", AlertType.WARNING)
                                } else {
                                    scope.launch {
                                        db.stateFlowDao().updatePocket(pocket.copy(isArchived = true))
                                        alertManager.showAlert("${pocket.name} removed", AlertType.SUCCESS)
                                    }
                                }
                            },
                            onStopRecurringSchedule = { schedule ->
                                scope.launch {
                                    db.stateFlowDao().updateFlowRecord(schedule.copy(isRecurring = false, frequency = "NONE"))
                                    alertManager.showAlert("Recurring rule cancelled for ${schedule.note.ifBlank { schedule.category }}", AlertType.SUCCESS)
                                }
                            },
                            onRecordCardSettlement = { cardId, liquidId, amt ->
                                scope.launch {
                                    val autoSplit = prefs.getBoolean("auto_split_debit", false)
                                    when (val res = ledgerEngine.recordMovement(
                                        nature = MovementNature.CARD_PAYMENT,
                                        sourcePocketId = liquidId,
                                        targetPocketId = cardId,
                                        amount = amt,
                                        category = "Bill Payment",
                                        note = "Card Dues Clearance",
                                        autoSplitEnabled = autoSplit
                                    )) {
                                        is VaultExecutionResult.OverdraftError -> alertManager.showAlert(res.message, AlertType.ERROR)
                                        is VaultExecutionResult.Success -> alertManager.showAlert(res.summary, AlertType.SUCCESS)
                                    }
                                }
                            },
                            onPeerAction = { nature, peerId, liquidId, amt ->
                                scope.launch {
                                    val autoSplit = prefs.getBoolean("auto_split_debit", false)
                                    val (src, tgt) = if (nature in listOf(MovementNature.PEER_LEND, MovementNature.PEER_REPAY)) {
                                        liquidId to peerId
                                    } else {
                                        peerId to liquidId
                                    }
                                    when (val res = ledgerEngine.recordMovement(
                                        nature = nature,
                                        sourcePocketId = src,
                                        targetPocketId = tgt,
                                        amount = amt,
                                        category = "Peer Transfer",
                                        note = nature.name,
                                        autoSplitEnabled = autoSplit
                                    )) {
                                        is VaultExecutionResult.OverdraftError -> alertManager.showAlert(res.message, AlertType.ERROR)
                                        is VaultExecutionResult.Success -> alertManager.showAlert(res.summary, AlertType.SUCCESS)
                                    }
                                }
                            }
                        )

                        2 -> IntelligenceScreen(
                            pocketBalances = pocketBalances,
                            flowRecords = flowRecords,
                            isPrivacyMode = isPrivacyMode
                        )

                        3 -> {
                            // Settings Tab: Rendered inline to cleanly match all preference operations
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("Settings & Vault Controls", color = theme.textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("Theme Mode", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            AppThemeMode.values().forEach { mode ->
                                                val isSel = activeThemeMode == mode
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isSel) theme.accent else theme.surfaceAlt)
                                                        .clickable {
                                                            activeThemeMode = mode
                                                            prefs.edit().putString("selected_theme", mode.name).apply()
                                                        }
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        mode.name.replace("_", " "),
                                                        color = if (isSel) theme.bg else theme.textBright,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("Data & Ledger Operations", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    PdfDossierExporter.generateAndShareDossier(context, pocketBalances, flowRecords)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Export PDF Ledger Dossier", color = theme.textBright, fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = {
                                                val compatList = flowRecords.map {
                                                    Transaction(
                                                        id = it.id,
                                                        accountId = it.sourcePocketId ?: it.targetPocketId ?: 0L,
                                                        flowType = if (it.nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.PEER_REPAY, MovementNature.CARD_PAYMENT)) "OUT" else "IN",
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
                                            colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Export CSV Ledger", color = theme.textBright, fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    flowRecords.forEach { db.stateFlowDao().deleteFlowRecordById(it.id) }
                                                    alertManager.showAlert("All vault records cleared", AlertType.SUCCESS)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed.copy(alpha = 0.2f)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Clear Entire Ledger History", color = theme.mildRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showCommandHud) {
                    FloatingCommandHud(
                        activePockets = rawPockets,
                        prefilledPocketId = selectedPocketIdForHud,
                        prefilledNote = ocrPrefilledNote,
                        prefilledAmount = ocrPrefilledAmount,
                        inDialogErrorMessage = hudInDialogError,
                        onDismiss = {
                            showCommandHud = false
                            hudInDialogError = null
                        },
                        onSubmit = { nature, srcId, tgtId, amt, cat, note, date, isRec, freq ->
                            scope.launch {
                                val autoSplit = prefs.getBoolean("auto_split_debit", false)
                                when (val res = ledgerEngine.recordMovement(
                                    nature = nature,
                                    sourcePocketId = srcId,
                                    targetPocketId = tgtId,
                                    amount = amt,
                                    category = cat,
                                    note = note,
                                    timestamp = date,
                                    autoSplitEnabled = autoSplit,
                                    isRecurring = isRec,
                                    frequency = freq
                                )) {
                                    is VaultExecutionResult.OverdraftError -> {
                                        hudInDialogError = res.message
                                        alertManager.showAlert(res.message, AlertType.ERROR)
                                    }
                                    is VaultExecutionResult.Success -> {
                                        hudInDialogError = null
                                        showCommandHud = false
                                        alertManager.showAlert(res.summary, AlertType.SUCCESS)
                                    }
                                }
                            }
                        }
                    )
                }

                editingFlowRecord?.let { flow ->
                    EditTransactionDialog(
                        record = flow,
                        theme = theme,
                        onDismiss = { editingFlowRecord = null },
                        onSave = { updatedCategory, updatedNote ->
                            scope.launch {
                                db.stateFlowDao().updateFlowRecord(
                                    flow.copy(category = updatedCategory, note = updatedNote)
                                )
                                editingFlowRecord = null
                                alertManager.showAlert("Transaction updated", AlertType.SUCCESS)
                            }
                        },
                        onDelete = {
                            scope.launch {
                                db.stateFlowDao().deleteFlowRecordById(flow.id)
                                editingFlowRecord = null
                                alertManager.showAlert("Transaction deleted and balance restored", AlertType.SUCCESS)
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
                        onEditRecord = { flow ->
                            editingFlowRecord = flow
                        },
                        onExportCsv = {
                            val compatList = flowRecords.map {
                                Transaction(
                                    id = it.id,
                                    accountId = it.sourcePocketId ?: it.targetPocketId ?: 0L,
                                    flowType = if (it.nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.PEER_REPAY, MovementNature.CARD_PAYMENT)) "OUT" else "IN",
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
                            scope.launch {
                                PdfDossierExporter.generateAndShareDossier(context, pocketBalances, flowRecords)
                            }
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
                                alertManager.showAlert("Created account '$name'", AlertType.SUCCESS)
                            }
                        }
                    )
                }

                editingPocket?.let { pocket ->
                    EditAccountDialog(
                        account = pocket,
                        theme = theme,
                        onDismiss = { editingPocket = null },
                        onSave = { updatedName, updatedLimit ->
                            scope.launch {
                                db.stateFlowDao().updatePocket(
                                    pocket.copy(
                                        name = updatedName,
                                        creditLimit = updatedLimit
                                    )
                                )
                                editingPocket = null
                                alertManager.showAlert("${pocket.name} updated", AlertType.SUCCESS)
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

            VaultFloatingTopOverlay(alertManager = alertManager, theme = theme)
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
fun FlowRecordDisplayRow(
    flow: FlowRecord,
    isPrivacyMode: Boolean,
    theme: ThemeColors,
    onClick: () -> Unit
) {
    val isOut = flow.nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.PEER_REPAY, MovementNature.CARD_PAYMENT)
    val flowColor = if (isOut) theme.mildRed else theme.mildGreen
    val dStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(flow.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTransactionDialog(
    record: FlowRecord,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (category: String, note: String) -> Unit,
    onDelete: () -> Unit
) {
    var note by remember { mutableStateOf(record.note) }
    var category by remember { mutableStateOf(record.category) }

    val allCategories = listOf(
        "Food & Dining", "Groceries", "Transport", "Shopping", 
        "Bills", "Health", "Leisure", "Salary", "General", "Peer Transfer"
    )

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text("Edit Entry", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Amount: ₹${record.amount.toInt()}", color = theme.textMuted, fontSize = 12.sp)
                CompactInputField(value = note, onValueChange = { note = it }, placeholder = "Merchant / Note")

                Text("Category", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allCategories.forEach { cat ->
                        val isSel = category == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else theme.surfaceAlt)
                                .clickable { category = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                cat,
                                color = if (isSel) theme.bg else theme.textBright,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(category, note) },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Update", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) { Text("Delete", color = theme.mildRed) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) }
            }
        }
    )
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

@Composable
private fun EditAccountDialog(
    account: VaultPocket,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var name by remember { mutableStateOf(account.name) }
    var limit by remember { mutableStateOf(if (account.creditLimit > 0) String.format("%.0f", account.creditLimit) else "") }

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text("Edit Account", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactInputField(value = name, onValueChange = { name = it }, placeholder = "Account Name")
                if (account.pocketType == PocketType.CREDIT_LINE) {
                    CompactInputField(value = limit, onValueChange = { limit = it }, placeholder = "Credit Limit")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onSave(name, limit.toDoubleOrNull() ?: account.creditLimit)
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Save Changes", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } }
    )
}
