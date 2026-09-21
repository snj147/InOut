package com.personal.inout.ui

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
    val ledgerEngine = remember { VaultLedgerEngine(db.stateFlowDao(), prefs) }

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

    var autoSplitEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_split_debit", false))
    }

    val theme = when (activeThemeMode) {
        AppThemeMode.AMBER_OCHRE -> AmberTheme
        AppThemeMode.OLIVE_MATCHA -> OliveMatchaTheme
        AppThemeMode.NORDIC_SLATE -> NordicSlateTheme
    }

    val pocketBalances by db.stateFlowDao().observePocketBalances().collectAsState(initial = emptyList())
    val rawPockets by db.stateFlowDao().observeAllActivePockets().collectAsState(initial = emptyList())
    val flowRecords by db.stateFlowDao().observeAllFlowRecords().collectAsState(initial = emptyList())
    val stagedDesires by db.stateFlowDao().observeActiveStagedDesires().collectAsState(initial = emptyList())

    val recurringTemplates = remember(flowRecords) {
        flowRecords.filter { it.isRecurring && it.frequency != "NONE" }
            .distinctBy { "${it.note}_${it.amount}_${it.frequency}" }
    }

    val totalLiquid = remember(pocketBalances) {
        pocketBalances.filter { it.pocketType == PocketType.LIQUID }.sumOf { it.currentBalance }.coerceAtLeast(0.0)
    }
    val unpaidCardDues = remember(pocketBalances) {
        pocketBalances.filter { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE }
            .filter { it.computedBalance < 0.0 }
            .sumOf { Math.abs(it.computedBalance) }
    }
    val trueSafeLiquid = (totalLiquid - unpaidCardDues).coerceAtLeast(0.0)
    val runwayDays = if (dailyBurnCeiling > 0) (trueSafeLiquid / dailyBurnCeiling).toInt() else 0

    var selectedTab by remember { mutableStateOf(0) }
    var isPrivacyMode by remember { mutableStateOf(false) }

    var showCommandHud by remember { mutableStateOf(false) }
    var selectedPocketIdForHud by remember { mutableStateOf<Long?>(null) }
    var hudInDialogError by remember { mutableStateOf<String?>(null) }

    var editingPocket by remember { mutableStateOf<VaultPocket?>(null) }
    var editingFlowRecord by remember { mutableStateOf<FlowRecord?>(null) }
    var editingRecurringRule by remember { mutableStateOf<FlowRecord?>(null) }
    var recordPendingDeletion by remember { mutableStateOf<FlowRecord?>(null) }
    var showCreatePocketDialog by remember { mutableStateOf(false) }
    var showAllRecordsSheet by remember { mutableStateOf(false) }
    var showMockPaywall by remember { mutableStateOf(false) }
    var showBurnEditDialog by remember { mutableStateOf(false) }
    var showClearLedgerConfirmation by remember { mutableStateOf(false) }

    var naturalLanguageInput by remember { mutableStateOf("") }
    val placeholderHints = listOf(
        "Spent [Amount] on [Item]",
        "set goal phone 40000 by nov",
        "salary 25000 idfc monthly",
        "collected 2000 from rahul to sbi",
        "pay 12000 axis card bill from sbi",
        "burn 600 (update daily target)"
    )
    var currentHintIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3600)
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

    fun executeQuickBarCommand(text: String) {
        val parsed = NaturalLanguageParser.parse(text, rawPockets, context)
        if (parsed == null) {
            alertManager.showAlert("Syntax not recognized.", AlertType.WARNING)
            return
        }

        scope.launch {
            when (parsed) {
                is ParsedIntent.SetDailyBurn -> {
                    dailyBurnCeiling = parsed.newRate
                    prefs.edit().putFloat("daily_burn_ceiling", parsed.newRate.toFloat()).apply()
                    alertManager.showAlert("Daily Burn set to ₹${parsed.newRate.toInt()}/day", AlertType.SUCCESS)
                    naturalLanguageInput = ""
                }
                is ParsedIntent.SaveMacroAlias -> {
                    val macroPrefs = context.getSharedPreferences("vault_macros", Context.MODE_PRIVATE)
                    macroPrefs.edit().putString(parsed.alias, parsed.fullCommand).apply()
                    alertManager.showAlert("Macro '${parsed.alias}' mapped", AlertType.SUCCESS)
                    naturalLanguageInput = ""
                }
                is ParsedIntent.StageDesire -> {
                    db.stateFlowDao().insertStagedDesire(StagedDesire(name = parsed.name, amount = parsed.amount))
                    alertManager.showAlert("Staged '${parsed.name}' in cool-off quarantine", AlertType.INFO)
                    naturalLanguageInput = ""
                }
                is ParsedIntent.TriangularSettle -> {
                    when (val res = ledgerEngine.triangularPeerSettle(parsed.debtor, parsed.creditor, parsed.amount)) {
                        is VaultExecutionResult.OverdraftError -> alertManager.showAlert(res.message, AlertType.ERROR)
                        is VaultExecutionResult.Success -> {
                            alertManager.showAlert(res.summary, AlertType.SUCCESS)
                            naturalLanguageInput = ""
                        }
                    }
                }
                is ParsedIntent.BreakGoalPot -> {
                    val goal = rawPockets.firstOrNull { it.pocketType == PocketType.SAVING_GOAL && it.name.equals(parsed.potName, ignoreCase = true) }
                    if (goal != null) {
                        val bal = pocketBalances.firstOrNull { it.pocketId == goal.id.toString() }?.computedBalance ?: 0.0
                        if (bal > 0 && parsed.destinationPocketId != null) {
                            ledgerEngine.recordMovement(
                                nature = MovementNature.TRANSFER,
                                sourcePocketId = goal.id,
                                targetPocketId = parsed.destinationPocketId,
                                amount = bal,
                                category = "Savings Pot",
                                note = "Broken Pot Funds Return"
                            )
                        }
                        db.stateFlowDao().updatePocket(goal.copy(isArchived = true))
                        alertManager.showAlert("Pot '${goal.name}' broken. ₹${bal.toInt()} returned.", AlertType.SUCCESS)
                        naturalLanguageInput = ""
                    }
                }
                is ParsedIntent.CreateAccount -> {
                    db.stateFlowDao().insertPocket(
                        VaultPocket(
                            name = parsed.name,
                            pocketType = parsed.type,
                            subType = parsed.type.name,
                            creditLimit = parsed.limit,
                            targetAmount = parsed.targetAmount,
                            targetDateEpoch = parsed.targetDateEpoch
                        )
                    )
                    alertManager.showAlert("Created '${parsed.name}'", AlertType.SUCCESS)
                    naturalLanguageInput = ""
                }
                is ParsedIntent.CompoundTransactions -> {
                    var successCount = 0
                    for (sub in parsed.transactions) {
                        val res = ledgerEngine.recordMovement(
                            nature = sub.nature,
                            sourcePocketId = sub.matchedPocketId,
                            targetPocketId = sub.targetPocketId,
                            amount = sub.amount,
                            category = sub.category,
                            note = sub.merchant,
                            autoSplitEnabled = autoSplitEnabled
                        )
                        if (res is VaultExecutionResult.Success) successCount++
                    }
                    alertManager.showAlert("Recorded $successCount transactions", AlertType.SUCCESS)
                    naturalLanguageInput = ""
                }
                is ParsedIntent.Transaction -> {
                    var sourceId: Long? = parsed.matchedPocketId
                    var targetId: Long? = parsed.targetPocketId

                    if (parsed.nature == MovementNature.INFLOW) {
                        sourceId = null
                        targetId = parsed.matchedPocketId
                    }

                    if (parsed.targetPersonName != null) {
                        var personPocket = rawPockets.firstOrNull {
                            it.pocketType == PocketType.COUNTERPARTY && it.name.equals(parsed.targetPersonName, ignoreCase = true)
                        }
                        if (personPocket == null) {
                            val newId = db.stateFlowDao().insertPocket(
                                VaultPocket(name = parsed.targetPersonName, pocketType = PocketType.COUNTERPARTY, subType = "PEER")
                            )
                            personPocket = VaultPocket(id = newId, name = parsed.targetPersonName, pocketType = PocketType.COUNTERPARTY, subType = "PEER")
                        }

                        when (parsed.nature) {
                            MovementNature.PEER_LEND -> {
                                sourceId = parsed.matchedPocketId
                                targetId = personPocket.id
                            }
                            MovementNature.PEER_COLLECT -> {
                                sourceId = personPocket.id
                                targetId = parsed.matchedPocketId
                            }
                            MovementNature.PEER_BORROW -> {
                                sourceId = personPocket.id
                                targetId = parsed.matchedPocketId
                            }
                            MovementNature.PEER_REPAY -> {
                                sourceId = parsed.matchedPocketId
                                targetId = personPocket.id
                            }
                            else -> {}
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
                        autoSplitEnabled = autoSplitEnabled,
                        isRecurring = parsed.isRecurring,
                        frequency = parsed.frequency
                    )) {
                        is VaultExecutionResult.OverdraftError -> alertManager.showAlert(res.message, AlertType.ERROR)
                        is VaultExecutionResult.Success -> {
                            alertManager.showAlert(res.summary, AlertType.SUCCESS)
                            naturalLanguageInput = ""
                        }
                    }
                }
            }
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
                            totalLiquid = trueSafeLiquid,
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
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp)
                            ) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                                        modifier = Modifier.fillMaxWidth().clickable { showBurnEditDialog = true }
                                    ) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                                Text("Burn Target: ₹${dailyBurnCeiling.toInt()}/day ✎", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                Text("Safe Liquid: ₹${String.format("%,.0f", trueSafeLiquid)}", color = theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            if (unpaidCardDues > 0.0) {
                                                Text("₹${unpaidCardDues.toInt()} auto-reserved for unpaid card liability (Phantom Lock)", color = theme.textMuted, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }

                                // Prominent, Animated Glowing Border on Quick Bar
                                item {
                                    val infiniteTransition = rememberInfiniteTransition(label = "glowTransition")
                                    val angle by infiniteTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 360f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(durationMillis = 3500, easing = LinearEasing),
                                            repeatMode = RepeatMode.Restart
                                        ),
                                        label = "rotateGlow"
                                    )

                                    val glowingBorderBrush = Brush.sweepGradient(
                                        colors = listOf(
                                            theme.accent,
                                            theme.mildGreen,
                                            theme.mildRed,
                                            theme.accent
                                        )
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .shadow(10.dp, RoundedCornerShape(16.dp), spotColor = theme.accent.copy(alpha = 0.6f))
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(theme.surface)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .rotate(angle)
                                                .background(glowingBorderBrush)
                                        )

                                        Card(
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = theme.surface),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier.clip(CircleShape).background(theme.accent.copy(alpha = 0.2f)).padding(6.dp),
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
                                                            fontSize = 12.5.sp,
                                                            maxLines = 1
                                                        )
                                                    }
                                                    BasicTextField(
                                                        value = naturalLanguageInput,
                                                        onValueChange = { naturalLanguageInput = it },
                                                        singleLine = true,
                                                        textStyle = TextStyle(color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                                        cursorBrush = SolidColor(theme.accent),
                                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                        keyboardActions = KeyboardActions(onDone = { executeQuickBarCommand(naturalLanguageInput) }),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }

                                                if (naturalLanguageInput.isNotBlank()) {
                                                    IconButton(onClick = { executeQuickBarCommand(naturalLanguageInput) }) {
                                                        Icon(Icons.Default.Send, contentDescription = "Commit", tint = theme.accent, modifier = Modifier.size(20.dp))
                                                    }
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
                                            onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
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
                                        Text("Real-Time Flow Stream", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = theme.surface)) {
                                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
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
                                        HorizontalDivider(color = theme.surfaceAlt.copy(alpha = 0.5f), thickness = 0.5.dp)
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
                            onTogglePauseRecurring = { schedule ->
                                scope.launch {
                                    db.stateFlowDao().setRecurringPausedState(schedule.id, !schedule.isPaused)
                                    alertManager.showAlert(if (schedule.isPaused) "Resumed rule" else "Paused rule", AlertType.SUCCESS)
                                }
                            },
                            onEditRecurring = { schedule -> editingRecurringRule = schedule },
                            onDeleteRecurringSafe = { schedule ->
                                scope.launch {
                                    db.stateFlowDao().deleteFlowRecordById(schedule.id)
                                    alertManager.showAlert("Recurring schedule deleted", AlertType.SUCCESS)
                                }
                            },
                            onRecordCardSettlement = { cardId, liquidId, amt ->
                                scope.launch {
                                    when (val res = ledgerEngine.recordMovement(
                                        nature = MovementNature.CARD_PAYMENT,
                                        sourcePocketId = liquidId,
                                        targetPocketId = cardId,
                                        amount = amt,
                                        category = "Bill Payment",
                                        note = "Card Dues Clearance",
                                        autoSplitEnabled = autoSplitEnabled
                                    )) {
                                        is VaultExecutionResult.OverdraftError -> alertManager.showAlert(res.message, AlertType.ERROR)
                                        is VaultExecutionResult.Success -> alertManager.showAlert(res.summary, AlertType.SUCCESS)
                                    }
                                }
                            },
                            onPeerAction = { nature, peerId, liquidId, amt ->
                                scope.launch {
                                    val (src, tgt) = if (nature in listOf(MovementNature.PEER_LEND, MovementNature.PEER_REPAY)) liquidId to peerId else peerId to liquidId
                                    when (val res = ledgerEngine.recordMovement(
                                        nature = nature,
                                        sourcePocketId = src,
                                        targetPocketId = tgt,
                                        amount = amt,
                                        category = "Peer Transfer",
                                        note = nature.name,
                                        autoSplitEnabled = autoSplitEnabled
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
                            Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("Settings & Vault Controls", color = theme.textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Theme Mode", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            AppThemeMode.entries.forEach { mode ->
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
                                                    Text(mode.name.replace("_", " "), color = if (isSel) theme.bg else theme.textBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Cross-Account Auto-Split", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Prevent overdrafts by auto-debiting shortfall from secondary bank accounts.", color = theme.textMuted, fontSize = 11.sp)
                                        }
                                        Switch(
                                            checked = autoSplitEnabled,
                                            onCheckedChange = {
                                                autoSplitEnabled = it
                                                prefs.edit().putBoolean("auto_split_debit", it).apply()
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.surfaceAlt)
                                        )
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Data & Ledger Operations", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                                        Button(
                                            onClick = { scope.launch { PdfDossierExporter.generateAndShareDossier(context, pocketBalances, flowRecords) } },
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
                                            onClick = { showClearLedgerConfirmation = true },
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

                if (showBurnEditDialog) {
                    var burnInput by remember { mutableStateOf(dailyBurnCeiling.toInt().toString()) }
                    AlertDialog(
                        onDismissRequest = { showBurnEditDialog = false },
                        containerColor = theme.surface,
                        title = { Text("Update Daily Burn Ceiling", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Set daily spending limit. Runway days adapt dynamically.", color = theme.textMuted, fontSize = 12.sp)
                                CompactInputField(value = burnInput, onValueChange = { burnInput = it }, placeholder = "Daily amount in ₹")
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val parsed = burnInput.toDoubleOrNull()
                                    if (parsed != null && parsed > 0) {
                                        dailyBurnCeiling = parsed
                                        prefs.edit().putFloat("daily_burn_ceiling", parsed.toFloat()).apply()
                                    }
                                    showBurnEditDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                            ) { Text("Save", color = theme.bg, fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = { TextButton(onClick = { showBurnEditDialog = false }) { Text("Cancel", color = theme.textMuted) } }
                    )
                }

                if (showClearLedgerConfirmation) {
                    AlertDialog(
                        onDismissRequest = { showClearLedgerConfirmation = false },
                        containerColor = theme.surface,
                        title = { Text("Confirm Clear Entire Ledger", color = Color.White) },
                        text = { Text("This will permanently delete all records and zero out balances. Proceed?", color = Color(0xFFCCCCCC)) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    scope.launch {
                                        flowRecords.forEach { db.stateFlowDao().deleteFlowRecordById(it.id) }
                                        alertManager.showAlert("All vault records purged", AlertType.SUCCESS)
                                        showClearLedgerConfirmation = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed)
                            ) { Text("Purge Everything", color = Color.White) }
                        },
                        dismissButton = { TextButton(onClick = { showClearLedgerConfirmation = false }) { Text("Cancel", color = theme.textMuted) } }
                    )
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
                                when (val res = ledgerEngine.recordMovement(
                                    nature = nature,
                                    sourcePocketId = srcId,
                                    targetPocketId = tgtId,
                                    amount = amt,
                                    category = cat,
                                    note = note,
                                    timestamp = date,
                                    autoSplitEnabled = autoSplitEnabled,
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

                // Dedicated Recurring Rule Editor with Native DatePickerDialog Trigger
                editingRecurringRule?.let { rule ->
                    EditRecurringRuleDialog(
                        rule = rule,
                        theme = theme,
                        context = context,
                        onDismiss = { editingRecurringRule = null },
                        onSave = { newAmt, newNote, newFreq, newTimestamp ->
                            scope.launch {
                                db.stateFlowDao().updateFlowRecord(
                                    rule.copy(
                                        amount = newAmt,
                                        note = newNote,
                                        frequency = newFreq,
                                        recurringCadence = newFreq,
                                        timestamp = newTimestamp
                                    )
                                )
                                editingRecurringRule = null
                                alertManager.showAlert("Recurring schedule updated", AlertType.SUCCESS)
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
                                db.stateFlowDao().updateFlowRecord(flow.copy(category = updatedCategory, note = updatedNote))
                                editingFlowRecord = null
                                alertManager.showAlert("Transaction updated", AlertType.SUCCESS)
                            }
                        },
                        onDelete = {
                            recordPendingDeletion = flow
                            editingFlowRecord = null
                        }
                    )
                }

                recordPendingDeletion?.let { flow ->
                    AlertDialog(
                        onDismissRequest = { recordPendingDeletion = null },
                        containerColor = theme.surface,
                        title = { Text("Confirm Deletion", color = Color.White) },
                        text = { Text("Delete entry of ₹${flow.amount.toInt()} for '${flow.note.ifBlank { flow.category }}'? Balances will adjust.", color = Color(0xFFCCCCCC)) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    scope.launch {
                                        db.stateFlowDao().deleteFlowRecordById(flow.id)
                                        recordPendingDeletion = null
                                        alertManager.showAlert("Transaction deleted and balance restored", AlertType.SUCCESS)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed)
                            ) { Text("Delete", color = Color.White) }
                        },
                        dismissButton = { TextButton(onClick = { recordPendingDeletion = null }) { Text("Cancel", color = theme.textMuted) } }
                    )
                }

                if (showAllRecordsSheet) {
                    AllTransactionsSearchSheet(
                        flowRecords = flowRecords,
                        isPrivacyMode = isPrivacyMode,
                        isProUser = isProUnlocked,
                        onDismiss = { showAllRecordsSheet = false },
                        onEditRecord = { flow -> editingFlowRecord = flow },
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
                            scope.launch { PdfDossierExporter.generateAndShareDossier(context, pocketBalances, flowRecords) }
                        }
                    )
                }

                if (showCreatePocketDialog) {
                    CreateAccountDialog(
                        theme = theme,
                        context = context,
                        onDismiss = { showCreatePocketDialog = false },
                        onSave = { name, type, limit, targetAmt, targetDateEpoch ->
                            scope.launch {
                                db.stateFlowDao().insertPocket(
                                    VaultPocket(
                                        name = name,
                                        pocketType = type,
                                        subType = type.name,
                                        creditLimit = limit,
                                        targetAmount = targetAmt,
                                        targetDateEpoch = targetDateEpoch
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
                        context = context,
                        onDismiss = { editingPocket = null },
                        onSave = { updatedName, updatedLimit, updatedTarget, updatedDateEpoch ->
                            scope.launch {
                                db.stateFlowDao().updatePocket(
                                    pocket.copy(
                                        name = updatedName,
                                        creditLimit = updatedLimit,
                                        targetAmount = updatedTarget,
                                        targetDateEpoch = updatedDateEpoch
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
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("InOut", color = theme.textBright, fontSize = 22.sp, fontWeight = FontWeight.Black)
                if (isProUser) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(theme.accent).padding(horizontal = 6.dp, vertical = 2.dp)) {
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 8.dp),
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
private fun EditRecurringRuleDialog(
    rule: FlowRecord,
    theme: ThemeColors,
    context: Context,
    onDismiss: () -> Unit,
    onSave: (amount: Double, note: String, freq: String, timestamp: Long) -> Unit
) {
    var note by remember { mutableStateOf(rule.note) }
    var amountText by remember { mutableStateOf(rule.amount.toInt().toString()) }
    var frequency by remember { mutableStateOf(if (rule.frequency != "NONE") rule.frequency else "MONTHLY") }
    var selectedDateEpoch by remember { mutableStateOf(rule.timestamp) }

    val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateEpoch))

    val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateEpoch }
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val updatedCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDateEpoch = updatedCal.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text("Edit Recurring Schedule", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactInputField(value = note, onValueChange = { note = it }, placeholder = "Schedule Description")
                CompactInputField(value = amountText, onValueChange = { amountText = it }, placeholder = "Amount in ₹")

                Text("Frequency", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("DAILY", "WEEKLY", "MONTHLY").forEach { freq ->
                        val isSel = frequency == freq
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else theme.surfaceAlt)
                                .clickable { frequency = freq }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(freq, color = if (isSel) theme.bg else theme.textBright, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.surfaceAlt)
                        .clickable { datePickerDialog.show() }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Cadence / Starting Date", color = theme.textMuted, fontSize = 10.sp)
                        Text(dateFormatted, color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = theme.accent, modifier = Modifier.size(16.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: rule.amount
                    onSave(amt, note, frequency, selectedDateEpoch)
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) { Text("Save Changes", color = theme.bg, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } }
    )
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
        "Bills", "Health", "Leisure", "Salary", "General", "Savings Pot", "Peer Transfer"
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
                            Text(cat, color = if (isSel) theme.bg else theme.textBright, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(category, note) },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) { Text("Update", color = theme.bg, fontWeight = FontWeight.Bold) }
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
    context: Context,
    onDismiss: () -> Unit,
    onSave: (String, PocketType, Double, Double, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PocketType.LIQUID) }
    var limit by remember { mutableStateOf("") }
    var targetAmt by remember { mutableStateOf("") }
    var targetDateEpoch by remember { mutableStateOf(System.currentTimeMillis() + (90L * 24 * 3600 * 1000L)) }

    val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(targetDateEpoch))
    val calendar = Calendar.getInstance().apply { timeInMillis = targetDateEpoch }
    val datePicker = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                targetDateEpoch = cal.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text("Add Vault Account / Goal", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactInputField(value = name, onValueChange = { name = it }, placeholder = "Account / Pot Name")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        PocketType.LIQUID to "Liquid",
                        PocketType.SAVING_GOAL to "Goal Pot",
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
                            Text(lbl, color = if (isSel) theme.bg else theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (type == PocketType.CREDIT_LINE) {
                    CompactInputField(value = limit, onValueChange = { limit = it }, placeholder = "Credit Limit (e.g. 50000)")
                }
                if (type == PocketType.SAVING_GOAL) {
                    CompactInputField(value = targetAmt, onValueChange = { targetAmt = it }, placeholder = "Target Goal Amount (e.g. 60000)")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .clickable { datePicker.show() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Target: $dateFormatted", color = theme.textBright, fontSize = 11.5.sp)
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onSave(
                        name,
                        type,
                        limit.toDoubleOrNull() ?: 0.0,
                        targetAmt.toDoubleOrNull() ?: 0.0,
                        targetDateEpoch
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) { Text("Save", color = theme.bg, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } }
    )
}

@Composable
private fun EditAccountDialog(
    account: VaultPocket,
    theme: ThemeColors,
    context: Context,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Long) -> Unit
) {
    var name by remember { mutableStateOf(account.name) }
    var limit by remember { mutableStateOf(if (account.creditLimit > 0) String.format("%.0f", account.creditLimit) else "") }
    var targetAmt by remember { mutableStateOf(if (account.targetAmount > 0) String.format("%.0f", account.targetAmount) else "") }
    var targetDateEpoch by remember { mutableStateOf(if (account.targetDateEpoch > 0) account.targetDateEpoch else System.currentTimeMillis() + (90L * 24 * 3600 * 1000L)) }

    val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(targetDateEpoch))
    val calendar = Calendar.getInstance().apply { timeInMillis = targetDateEpoch }
    val datePicker = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                targetDateEpoch = cal.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text(if (account.pocketType == PocketType.SAVING_GOAL) "Edit Goal Pot" else "Edit Account", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactInputField(value = name, onValueChange = { name = it }, placeholder = "Name")
                if (account.pocketType == PocketType.CREDIT_LINE) {
                    CompactInputField(value = limit, onValueChange = { limit = it }, placeholder = "Credit Limit")
                }
                if (account.pocketType == PocketType.SAVING_GOAL) {
                    CompactInputField(value = targetAmt, onValueChange = { targetAmt = it }, placeholder = "Target Goal Amount")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .clickable { datePicker.show() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Target: $dateFormatted", color = theme.textBright, fontSize = 11.5.sp)
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onSave(
                        name,
                        limit.toDoubleOrNull() ?: account.creditLimit,
                        targetAmt.toDoubleOrNull() ?: account.targetAmount,
                        targetDateEpoch
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) { Text("Save Changes", color = theme.bg, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } }
    )
}
