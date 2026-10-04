package com.personal.inout.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.personal.inout.BuildConfig
import com.personal.inout.billing.PlayBillingManager
import com.personal.inout.data.*
import com.personal.inout.ocr.ReceiptScanner
import com.personal.inout.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE) }

    val alertManager = remember { VaultAlertManager() }
    val ledgerEngine = remember { VaultLedgerEngine(db.ledgerDao(), prefs) }

    val currentInstalledSha = remember {
        val buildSha = BuildConfig.GIT_SHA
        if (buildSha.isNotBlank() && buildSha != "localdev") buildSha else "0d7352d"
    }

    val packageInfo = remember { try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (_: Exception) { null } }
    val lastInstalledTimeFormatted = remember(packageInfo) {
        val t = packageInfo?.lastUpdateTime ?: System.currentTimeMillis()
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(t))
    }

    val billingManager = remember { PlayBillingManager(context, scope) }
    val isProUnlocked by billingManager.isProUnlocked.collectAsState()

    var activeThemeMode by remember {
        val saved = prefs.getString("selected_theme", AppThemeMode.PALE_AMBER.name)
        mutableStateOf(try { AppThemeMode.valueOf(saved ?: AppThemeMode.PALE_AMBER.name) } catch (_: Exception) { AppThemeMode.PALE_AMBER })
    }

    var masterSecurityPin by remember { mutableStateOf(prefs.getString("master_security_pin", "000000") ?: "000000") }
    var dailyBurnCeiling by remember { mutableStateOf(prefs.getFloat("daily_burn_ceiling", 500f).toDouble()) }
    var autoSplitEnabled by remember { mutableStateOf(prefs.getBoolean("auto_split_debit", false)) }

    val theme = when (activeThemeMode) {
        AppThemeMode.PALE_AMBER -> PaleAmberTheme
        AppThemeMode.MATCHA_OLIVE -> MatchaOliveTheme
        AppThemeMode.DESERT_SAND -> DesertSandTheme
        AppThemeMode.NORDIC_SLATE -> NordicSlateTheme
    }

    val rawPockets by db.ledgerDao().getAllActivePockets().collectAsState(initial = emptyList())
    val completedTransactions by db.ledgerDao().observeHistoricalTransactions().collectAsState(initial = emptyList())
    val unreadNotices by db.ledgerDao().observeUnreadNotices().collectAsState(initial = emptyList())

    val liquidPockets = remember(rawPockets) {
        rawPockets.filter { it.type in listOf(PocketType.LIQUID, PocketType.PREPAID_WALLET) }
    }

    var pocketBalances by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    var solvencyDeck by remember {
        mutableStateOf(SolvencyMetricDeck(0.0, 0.0, 0.0, dailyBurnCeiling, BurnPacingStatus.ON_TRACK, 0L, 0.0, false))
    }

    LaunchedEffect(rawPockets, completedTransactions) {
        withContext(Dispatchers.IO) {
            val balMap = mutableMapOf<Long, Double>()
            for (pocket in rawPockets) balMap[pocket.id] = db.ledgerDao().computePocketBalance(pocket.id)
            pocketBalances = balMap
            solvencyDeck = ledgerEngine.computeSolvencyDeck()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val caughtUp = ledgerEngine.catchUpRecurringRules()
            if (caughtUp > 0) alertManager.showAlert("Auto-executed $caughtUp scheduled recurring entries", AlertType.INFO)
        }
    }

    val recurringTemplates = remember(completedTransactions) { completedTransactions.filter { it.isRecurring && it.recurringFrequency != "NONE" } }
    val totalInflowLifetime = remember(completedTransactions) { completedTransactions.filter { it.movementNature == MovementNature.OPERATING_INCOME }.sumOf { it.amount } }
    val totalOutflowLifetime = remember(completedTransactions) { completedTransactions.filter { it.movementNature == MovementNature.OPERATING_EXPENSE }.sumOf { it.amount } }

    var selectedTab by remember { mutableIntStateOf(0) }
    var isPrivacyMode by remember { mutableStateOf(false) }

    var showUnifiedEntrySheet by remember { mutableStateOf(false) }
    var selectedPocketIdForEntry by remember { mutableStateOf<Long?>(null) }
    var selectedTargetPocketIdForEntry by remember { mutableStateOf<Long?>(null) }
    var forcedEntryRail by remember { mutableStateOf<ActiveEntryRail?>(null) }

    var activeLoanRepaymentPocket by remember { mutableStateOf<LedgerPocket?>(null) }
    var activePeerSettlementPocket by remember { mutableStateOf<LedgerPocket?>(null) }
    var activeGoalSweepPocket by remember { mutableStateOf<LedgerPocket?>(null) }
    var activeAssetRevaluationPocket by remember { mutableStateOf<LedgerPocket?>(null) }

    var editingPocket by remember { mutableStateOf<LedgerPocket?>(null) }
    var editingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingRecurringRule by remember { mutableStateOf<LedgerTransaction?>(null) }
    var transactionPendingDeletion by remember { mutableStateOf<LedgerTransaction?>(null) }
    var terminateRecurringGuard by remember { mutableStateOf<LedgerTransaction?>(null) }

    var showCreatePocketDialog by remember { mutableStateOf(false) }
    var prefilledCreatePocketType by remember { mutableStateOf(PocketType.LIQUID) }

    var showAllRecordsSheet by remember { mutableStateOf(false) }
    var prefilledHistoryFilterPocketId by remember { mutableStateOf<Long?>(null) }

    var showBurnEditDialog by remember { mutableStateOf(false) }
    var showClearLedgerConfirmation by remember { mutableStateOf(false) }
    var showNoticesDialog by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }

    var availableUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isCheckingForUpdate by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }

    val downloadState by AppUpdateEngine.downloadState.collectAsState()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })

    var ocrPrefilledNote by remember { mutableStateOf("") }
    var ocrPrefilledAmount by remember { mutableStateOf<Double?>(null) }

    val groupedRecords = remember(completedTransactions) {
        val calNow = Calendar.getInstance()
        completedTransactions.take(35).groupBy { tx ->
            val calRecord = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            when {
                calNow.get(Calendar.YEAR) == calRecord.get(Calendar.YEAR) &&
                        calNow.get(Calendar.DAY_OF_YEAR) == calRecord.get(Calendar.DAY_OF_YEAR) -> "Today"
                calNow.get(Calendar.YEAR) == calRecord.get(Calendar.YEAR) &&
                        calNow.get(Calendar.DAY_OF_YEAR) - calRecord.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
                else -> SimpleDateFormat("dd MMMM", Locale.getDefault()).format(Date(tx.timestamp))
            }
        }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = VaultBackupManager.exportEncryptedBackup(context, db, uri, "INOUT_LEDGER_MASTER_KEY")
                if (res.isSuccess) alertManager.showAlert("Verified .vault snapshot exported", AlertType.SUCCESS)
                else alertManager.showAlert("Export failed: ${res.exceptionOrNull()?.message}", AlertType.ERROR)
            }
        }
    }

    val backupRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = VaultBackupManager.restoreEncryptedBackup(context, db, uri, "INOUT_LEDGER_MASTER_KEY")
                if (res.isSuccess) alertManager.showAlert("Vault restored cleanly", AlertType.SUCCESS)
                else alertManager.showAlert("Restore failed: ${res.exceptionOrNull()?.message}", AlertType.ERROR)
            }
        }
    }

    fun processReceiptResult(bitmap: Bitmap) {
        scope.launch {
            try {
                val parsed = ReceiptScanner.processReceiptBitmap(bitmap)
                ocrPrefilledNote = parsed.merchant
                ocrPrefilledAmount = parsed.total
                selectedPocketIdForEntry = rawPockets.firstOrNull { it.type == PocketType.LIQUID }?.id
                showUnifiedEntrySheet = true
                alertManager.showAlert("Parsed: ${parsed.merchant} (₹${String.format("%,.0f", parsed.total ?: 0.0)})", AlertType.INFO)
            } catch (e: Exception) {
                alertManager.showAlert("OCR Failed: ${e.localizedMessage}", AlertType.ERROR)
            }
        }
    }

    val cameraSnapLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) processReceiptResult(bitmap)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraSnapLauncher.launch(null)
        else alertManager.showAlert("Camera permission required for OCR receipts", AlertType.WARNING)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val parsed = ReceiptScanner.processDocumentUri(context, uri)
                    ocrPrefilledNote = parsed.merchant
                    ocrPrefilledAmount = parsed.total
                    selectedPocketIdForEntry = rawPockets.firstOrNull { it.type == PocketType.LIQUID }?.id
                    showUnifiedEntrySheet = true
                    alertManager.showAlert("Parsed: ${parsed.merchant} (₹${String.format("%,.0f", parsed.total ?: 0.0)})", AlertType.INFO)
                } catch (e: Exception) {
                    alertManager.showAlert("OCR Failed: ${e.localizedMessage}", AlertType.ERROR)
                }
            }
        }
    }

    fun executeMasterEntryTrigger(nature: MovementNature, srcId: Long, tgtId: Long?, amt: Double, cat: String, note: String, date: Long, isRec: Boolean, freq: String, tax: Boolean, reimb: Boolean) {
        scope.launch {
            when (val res = ledgerEngine.recordMovement(nature, srcId, tgtId, amt, cat, note, date, SettlementStatus.CLEARED, autoSplitEnabled, tax, reimb, false, isRec, freq)) {
                is VaultExecutionResult.OverdraftError -> alertManager.showAlert(res.message, AlertType.ERROR)
                is VaultExecutionResult.DuplicateWarning -> alertManager.showAlert(res.message, AlertType.WARNING)
                is VaultExecutionResult.Success -> {
                    alertManager.showAlert(res.summary, AlertType.SUCCESS)
                    showUnifiedEntrySheet = false
                }
            }
        }
    }

    CompositionLocalProvider(LocalThemeColors provides theme, LocalVaultAlertManager provides alertManager) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = theme.bg,
                topBar = { DashboardHeader(totalInflowLifetime, totalOutflowLifetime, isProUnlocked, isPrivacyMode) { isPrivacyMode = !isPrivacyMode } },
                bottomBar = {
                    NavigationBar(
                        containerColor = theme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.navigationBarsPadding().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).border(1.dp, theme.borderLight, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    ) {
                        listOf(
                            Triple(0, "Vault", Icons.Filled.AccountBalanceWallet),
                            Triple(1, "Accounts", Icons.Filled.AccountBalance),
                            Triple(2, "Insights", Icons.Filled.Insights),
                            Triple(3, "Settings", Icons.Filled.Settings)
                        ).forEach { (idx, title, icon) ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx; isFabExpanded = false },
                                icon = { Icon(imageVector = icon, contentDescription = title) },
                                label = { Text(text = title, fontSize = 11.sp, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = theme.accent, selectedTextColor = theme.accent, indicatorColor = theme.surfaceAlt, unselectedIconColor = theme.textMuted, unselectedTextColor = theme.textMuted)
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (selectedTab == 0 || selectedTab == 1) {
                        val rotation by animateFloatAsState(targetValue = if (isFabExpanded) 45f else 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "fabRotation")
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.navigationBarsPadding()) {
                            AnimatedVisibility(visible = isFabExpanded, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FloatingActionButton(
                                        onClick = {
                                            isFabExpanded = false
                                            ocrPrefilledNote = ""
                                            ocrPrefilledAmount = null
                                            selectedPocketIdForEntry = rawPockets.firstOrNull { it.type == PocketType.LIQUID }?.id
                                            selectedTargetPocketIdForEntry = null
                                            forcedEntryRail = null
                                            showUnifiedEntrySheet = true
                                        },
                                        modifier = Modifier.size(46.dp), containerColor = theme.accent, contentColor = theme.bg, shape = CircleShape
                                    ) { Icon(imageVector = Icons.Default.EditNote, contentDescription = "Manual Entry", modifier = Modifier.size(22.dp)) }

                                    FloatingActionButton(
                                        onClick = {
                                            isFabExpanded = false
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                cameraSnapLauncher.launch(null)
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        modifier = Modifier.size(46.dp), containerColor = theme.accent, contentColor = theme.bg, shape = CircleShape
                                    ) { Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Camera OCR", modifier = Modifier.size(20.dp)) }

                                    FloatingActionButton(
                                        onClick = {
                                            isFabExpanded = false
                                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                        modifier = Modifier.size(46.dp), containerColor = theme.accent, contentColor = theme.bg, shape = CircleShape
                                    ) { Icon(imageVector = Icons.Default.Image, contentDescription = "Gallery OCR", modifier = Modifier.size(20.dp)) }
                                }
                            }
                            FloatingActionButton(
                                onClick = {
                                    if (selectedTab == 1) { prefilledCreatePocketType = PocketType.LIQUID; showCreatePocketDialog = true }
                                    else { isFabExpanded = !isFabExpanded }
                                },
                                containerColor = theme.accent, contentColor = theme.bg, shape = CircleShape
                            ) { Icon(imageVector = if (selectedTab == 1) Icons.Default.AddCard else Icons.Default.Add, contentDescription = "Action", modifier = Modifier.size(24.dp).rotate(if (selectedTab == 0) rotation else 0f)) }
                        }
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding).background(theme.bg)) {
                    when (selectedTab) {
                        0 -> {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                                            when (page) {
                                                0 -> MetricCarouselCard("TRUE SAFE LIQUID", if (solvencyDeck.trueSafeLiquid > 0) "Solvent" else "Deficit", solvencyDeck.trueSafeLiquid > 0, if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", solvencyDeck.trueSafeLiquid)}", "Spendable cash without debt", "Net Worth: ₹${String.format("%,.0f", solvencyDeck.totalNetWorth)}", theme) {}
                                                1 -> MetricCarouselCard("RUNWAY SURVIVAL HORIZON", if (solvencyDeck.runwayDays > 30) "Comfortable" else "Critical", solvencyDeck.runwayDays > 30, if (isPrivacyMode) "•• Days" else "${solvencyDeck.runwayDays} Days", "Daily Target: ₹${dailyBurnCeiling.toInt()}/day ✎", if (solvencyDeck.burnStatus == BurnPacingStatus.ON_TRACK) "Pacing Normal" else "Burn Spiked", theme) { showBurnEditDialog = true }
                                                2 -> MetricCarouselCard("MONTH-END CASH FORECAST", if (solvencyDeck.hasEarlyDeficitAlert) "Deficit Alert" else "Healthy", !solvencyDeck.hasEarlyDeficitAlert, if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", solvencyDeck.projectedClosingLiquid)}", "Deterministic Commitments", "Fixed Outflows Factored", theme) {}
                                                3 -> MetricCarouselCard("STATUTORY NET WORTH", if (solvencyDeck.totalNetWorth >= 0) "Positive" else "Insolvent", solvencyDeck.totalNetWorth >= 0, if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", solvencyDeck.totalNetWorth)}", "Assets Less Liabilities", "Balance Sheet", theme) {}
                                            }
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                            repeat(4) { dotIdx ->
                                                val isSel = pagerState.currentPage == dotIdx
                                                Box(modifier = Modifier.padding(horizontal = 3.dp).size(if (isSel) 6.dp else 4.dp).clip(CircleShape).background(if (isSel) theme.accent else theme.surfaceAlt))
                                            }
                                        }
                                    }
                                }
                                item {
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Recent Flow", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        if (completedTransactions.isNotEmpty()) {
                                            Text("View All (${completedTransactions.size}) →", color = theme.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { prefilledHistoryFilterPocketId = null; showAllRecordsSheet = true })
                                        }
                                    }
                                }
                                if (completedTransactions.isEmpty()) {
                                    item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { Text("No records logged yet. Tap + below to add entry.", color = theme.textMuted, fontSize = 12.sp) } }
                                } else {
                                    groupedRecords.forEach { (dateHeader, records) ->
                                        item { Text(dateHeader.uppercase(Locale.getDefault()), color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                                        items(records, key = { it.id }) { tx ->
                                            FlatStreamRow(tx, rawPockets, isPrivacyMode, theme) { editingTransaction = tx }
                                            HorizontalDivider(color = theme.borderLight.copy(alpha = 0.4f), thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }

                        1 -> AccountPocketsView(
                            rawPockets = rawPockets,
                            pocketBalances = pocketBalances,
                            recurringTransactions = recurringTemplates,
                            isPrivacyMode = isPrivacyMode,
                            onTransactPocket = { pocket ->
                                ocrPrefilledNote = ""
                                ocrPrefilledAmount = null
                                when (pocket.type) {
                                    PocketType.CREDIT_CARD -> {
                                        forcedEntryRail = ActiveEntryRail.CARD_BILL
                                        selectedPocketIdForEntry = liquidPockets.firstOrNull()?.id
                                        selectedTargetPocketIdForEntry = pocket.id
                                        showUnifiedEntrySheet = true
                                    }
                                    PocketType.LIABILITY_LOAN -> activeLoanRepaymentPocket = pocket
                                    PocketType.PEER_RECEIVABLE, PocketType.PEER_PAYABLE -> activePeerSettlementPocket = pocket
                                    PocketType.GOAL_POT -> activeGoalSweepPocket = pocket
                                    PocketType.INVESTMENT, PocketType.FIXED_ASSET -> activeAssetRevaluationPocket = pocket
                                    else -> {
                                        forcedEntryRail = null
                                        selectedPocketIdForEntry = pocket.id
                                        selectedTargetPocketIdForEntry = null
                                        showUnifiedEntrySheet = true
                                    }
                                }
                            },
                            onViewTransactions = { pocketId ->
                                prefilledHistoryFilterPocketId = pocketId
                                showAllRecordsSheet = true
                            },
                            onEditPocket = { editingPocket = it },
                            onDeletePocketSafe = { pocket, bal ->
                                if (bal != 0.0) alertManager.showAlert("Cannot archive account with active balance of ₹${bal.toInt()}", AlertType.WARNING)
                                else scope.launch { db.ledgerDao().updatePocket(pocket.copy(isArchived = true)); alertManager.showAlert("${pocket.name} archived", AlertType.SUCCESS) }
                            },
                            onTogglePauseRecurring = { tx ->
                                scope.launch { db.ledgerDao().updateTransaction(tx.copy(recurringFrequency = if (tx.recurringFrequency == "NONE") "MONTHLY" else "NONE")) }
                            },
                            onEditRecurring = { editingRecurringRule = it },
                            onDeleteRecurringSafe = { terminateRecurringGuard = it },
                            onRequestCreateAccount = { reqType -> prefilledCreatePocketType = reqType; showCreatePocketDialog = true }
                        )

                        2 -> IntelligenceScreen(pocketBalances, completedTransactions, recurringTemplates, emptyList(), dailyBurnCeiling, solvencyDeck.trueSafeLiquid, isPrivacyMode, theme)

                        3 -> SettingsCardsList(
                            theme = theme, currentSha = currentInstalledSha, lastUpdatedDate = lastInstalledTimeFormatted, masterPin = masterSecurityPin,
                            autoSplitEnabled = autoSplitEnabled, activeThemeMode = activeThemeMode, availableUpdate = availableUpdateInfo,
                            isCheckingUpdate = isCheckingForUpdate, downloadState = downloadState,
                            onOpenSetPinDialog = { showSetPinDialog = true },
                            onCheckUpdate = {
                                scope.launch {
                                    isCheckingForUpdate = true
                                    val res = AppUpdateEngine.checkForUpdate(context)
                                    isCheckingForUpdate = false
                                    if (res.isSuccess) {
                                        val info = res.getOrNull()
                                        if (info != null && info.hasUpdate && info.downloadUrl.isNotBlank()) availableUpdateInfo = info
                                        else { availableUpdateInfo = null; alertManager.showAlert("You are on the latest build", AlertType.SUCCESS) }
                                    } else { availableUpdateInfo = null; alertManager.showAlert("You are on the latest build", AlertType.SUCCESS) }
                                }
                            },
                            onStartStreamDownload = { info -> scope.launch { AppUpdateEngine.startStreamDownload(context, info.downloadUrl, info.latestVersion) } },
                            onInstallDownloadedApk = {},
                            onOpenFeedback = { showFeedbackDialog = true },
                            onAutoSplitToggled = { autoSplitEnabled = it; prefs.edit().putBoolean("auto_split_debit", it).apply() },
                            onThemeSelected = { mode -> activeThemeMode = mode; prefs.edit().putString("selected_theme", mode.name).apply() },
                            onExportPdf = { scope.launch { PdfDossierExporter.generateAndShareDossier(context, pocketBalances, completedTransactions) } },
                            onExportCsv = { CsvExporter.exportAndShareTransactions(context, completedTransactions) },
                            onExportEncryptedBackup = { backupExportLauncher.launch("inout_vault_backup_${System.currentTimeMillis()}.vault") },
                            onRestoreEncryptedBackup = { backupRestoreLauncher.launch(arrayOf("application/json", "*/*")) },
                            onClearLedger = { showClearLedgerConfirmation = true }
                        )
                    }
                }

                if (isFabExpanded) { Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { isFabExpanded = false }) }

                activeLoanRepaymentPocket?.let { loanPocket ->
                    val debtBal = abs(pocketBalances[loanPocket.id] ?: 0.0)
                    LogLoanRepaymentModal(
                        loanPocket = loanPocket, outstandingBalance = debtBal, liquidPockets = liquidPockets, theme = theme, onDismiss = { activeLoanRepaymentPocket = null },
                        onRecordRepayment = { amt, sourceBankId ->
                            scope.launch {
                                val res = ledgerEngine.recordMovement(MovementNature.EMI_PRINCIPAL, sourceBankId, loanPocket.id, amt, "Loan EMI", "EMI for ${loanPocket.name}")
                                if (res is VaultExecutionResult.Success) { alertManager.showAlert("Logged repayment of ₹${amt.toInt()}", AlertType.SUCCESS); activeLoanRepaymentPocket = null }
                                else if (res is VaultExecutionResult.OverdraftError) alertManager.showAlert(res.message, AlertType.ERROR)
                            }
                        }
                    )
                }

                activePeerSettlementPocket?.let { peerPocket ->
                    val peerBal = pocketBalances[peerPocket.id] ?: 0.0
                    SettlePeerBalanceModal(
                        peerPocket = peerPocket, currentBalance = peerBal, liquidPockets = liquidPockets, theme = theme, onDismiss = { activePeerSettlementPocket = null },
                        onSettle = { isLend, amt, bankId ->
                            scope.launch {
                                val srcId = if (isLend) peerPocket.id else bankId
                                val tgtId = if (isLend) bankId else peerPocket.id
                                val res = ledgerEngine.recordMovement(MovementNature.TRANSFER, srcId, tgtId, amt, "Peer Settlement", "Settlement with ${peerPocket.name}")
                                if (res is VaultExecutionResult.Success) { alertManager.showAlert("Settled ₹${amt.toInt()} with ${peerPocket.name}", AlertType.SUCCESS); activePeerSettlementPocket = null }
                                else if (res is VaultExecutionResult.OverdraftError) alertManager.showAlert(res.message, AlertType.ERROR)
                            }
                        }
                    )
                }

                activeGoalSweepPocket?.let { goalPocket ->
                    val goalBal = pocketBalances[goalPocket.id] ?: 0.0
                    SweepGoalSavingsModal(
                        goalPocket = goalPocket, currentBalance = goalBal, liquidPockets = liquidPockets, theme = theme, onDismiss = { activeGoalSweepPocket = null },
                        onSweep = { isAdding, amt, bankId ->
                            scope.launch {
                                val srcId = if (isAdding) bankId else goalPocket.id
                                val tgtId = if (isAdding) goalPocket.id else bankId
                                val res = ledgerEngine.recordMovement(MovementNature.TRANSFER, srcId, tgtId, amt, "Goal Transfer", "Sweep for ${goalPocket.name}")
                                if (res is VaultExecutionResult.Success) { alertManager.showAlert("Swept ₹${amt.toInt()}", AlertType.SUCCESS); activeGoalSweepPocket = null }
                                else if (res is VaultExecutionResult.OverdraftError) alertManager.showAlert(res.message, AlertType.ERROR)
                            }
                        }
                    )
                }

                activeAssetRevaluationPocket?.let { assetPocket ->
                    val currentBal = pocketBalances[assetPocket.id] ?: 0.0
                    RevalueAssetPortfolioModal(
                        assetPocket = assetPocket, currentBookValue = currentBal, theme = theme, onDismiss = { activeAssetRevaluationPocket = null },
                        onUpdateValuation = { newMarketVal, note ->
                            scope.launch {
                                val drift = newMarketVal - currentBal
                                if (drift != 0.0) {
                                    val nature = if (drift > 0) MovementNature.VALUATION_MARK else MovementNature.DEPRECIATION_WRITE
                                    ledgerEngine.recordMovement(nature, assetPocket.id, null, abs(drift), "Revaluation", note)
                                    alertManager.showAlert("Valuation updated to ₹${newMarketVal.toInt()}", AlertType.SUCCESS)
                                }
                                activeAssetRevaluationPocket = null
                            }
                        }
                    )
                }

                terminateRecurringGuard?.let { rule ->
                    CenteredSafePurgeGuardModal(
                        pocket = LedgerPocket(name = "Recurring Mandate: ${rule.description}", type = PocketType.LIQUID), balance = 0.0, theme = theme, onDismiss = { terminateRecurringGuard = null },
                        onConfirm = {
                            scope.launch {
                                db.ledgerDao().updateTransaction(rule.copy(isRecurring = false, recurringFrequency = "NONE"))
                                terminateRecurringGuard = null
                                alertManager.showAlert("Mandate terminated. History preserved.", AlertType.SUCCESS)
                            }
                        }
                    )
                }

                if (showUnifiedEntrySheet) {
                    UnifiedEntrySheet(
                        allPockets = rawPockets, prefilledPocketId = selectedPocketIdForEntry, prefilledTargetPocketId = selectedTargetPocketIdForEntry,
                        forcedRail = forcedEntryRail, prefilledNote = ocrPrefilledNote, prefilledAmount = ocrPrefilledAmount,
                        onDismiss = { showUnifiedEntrySheet = false },
                        onSubmitTransaction = { n, s, t, a, c, no, d, isR, f, tax, reimb -> executeMasterEntryTrigger(n, s, t, a, c, no, d, isR, f, tax, reimb) },
                        onNavigateToCreatePocket = { showUnifiedEntrySheet = false; showCreatePocketDialog = true }
                    )
                }

                if (showNoticesDialog) CenteredNoticesModal(unreadNotices, theme, { showNoticesDialog = false }) { scope.launch { for (notice in unreadNotices) db.ledgerDao().markNoticeAsRead(notice.id); showNoticesDialog = false } }
                if (showSetPinDialog) ThemeSetPinDialog({ showSetPinDialog = false }) { newPin -> masterSecurityPin = newPin; prefs.edit().putString("master_security_pin", newPin).apply(); showSetPinDialog = false; alertManager.showAlert("Master PIN updated", AlertType.SUCCESS) }
                
                if (showCreatePocketDialog) {
                    CreateAccountDialog(
                        initialType = prefilledCreatePocketType, allLiquidPockets = liquidPockets, theme = theme, onDismiss = { showCreatePocketDialog = false },
                        onSave = { name, type, limit, dueDay, targetAmt, targetDateEpoch, initialVal, interestRate, linkedAssetType ->
                            scope.launch {
                                val pocketId = db.ledgerDao().insertPocket(LedgerPocket(name = name, type = type, creditLimit = limit, billDueDay = dueDay, targetGoalAmount = targetAmt, goalTargetDate = targetDateEpoch))
                                
                                if (type == PocketType.LIABILITY_LOAN && linkedAssetType != null && initialVal > 0.0) {
                                    val assetId = db.ledgerDao().insertPocket(LedgerPocket(name = "$name - Asset", type = linkedAssetType))
                                    ledgerEngine.recordMovement(MovementNature.OPENING_BASELINE, pocketId, null, initialVal, "Initial Position", "Loan Principal baseline", System.currentTimeMillis())
                                    ledgerEngine.recordMovement(MovementNature.OPENING_BASELINE, assetId, null, initialVal, "Initial Position", "Asset Acquisition via Loan", System.currentTimeMillis())
                                } else if (initialVal > 0.0) {
                                    ledgerEngine.recordMovement(MovementNature.OPENING_BASELINE, pocketId, null, initialVal, "Initial Position", "Opening baseline for $name", System.currentTimeMillis())
                                }
                                showCreatePocketDialog = false; alertManager.showAlert("Created '$name'", AlertType.SUCCESS)
                            }
                        },
                        onSavePeerWithFunding = { contactName, isLend, amount, fundingPocketId ->
                            scope.launch {
                                val peerType = if (isLend) PocketType.PEER_RECEIVABLE else PocketType.PEER_PAYABLE
                                val peerPocketId = db.ledgerDao().insertPocket(LedgerPocket(name = contactName, type = peerType))
                                if (amount > 0.0 && fundingPocketId != null) {
                                    val srcId = if (isLend) fundingPocketId else peerPocketId
                                    val tgtId = if (isLend) peerPocketId else fundingPocketId
                                    val res = ledgerEngine.recordMovement(MovementNature.TRANSFER, srcId, tgtId, amount, if (isLend) "Peer Debt" else "Peer Advance", if (isLend) "Lent to $contactName" else "Borrowed from $contactName")
                                    if (res is VaultExecutionResult.OverdraftError) {
                                        db.ledgerDao().deletePocket(peerPocketId)
                                        alertManager.showAlert(res.message, AlertType.ERROR)
                                        return@launch
                                    }
                                } else if (amount > 0.0) {
                                    ledgerEngine.recordMovement(MovementNature.OPENING_BASELINE, peerPocketId, null, amount, "Initial Position", "Opening peer ledger for $contactName")
                                }
                                showCreatePocketDialog = false
                                alertManager.showAlert("Added Peer ledger for '$contactName'", AlertType.SUCCESS)
                            }
                        }
                    )
                }

                editingPocket?.let { pocket ->
                    EditAccountDialog(
                        pocket = pocket, theme = theme, onDismiss = { editingPocket = null },
                        onSave = { updatedName, updatedLimit, updatedDueDay, updatedTarget, updatedDateEpoch ->
                            scope.launch { db.ledgerDao().updatePocket(pocket.copy(name = updatedName, creditLimit = updatedLimit, billDueDay = updatedDueDay, targetGoalAmount = updatedTarget, goalTargetDate = updatedDateEpoch)); editingPocket = null; alertManager.showAlert("${pocket.name} updated", AlertType.SUCCESS) }
                        }
                    )
                }

                if (showFeedbackDialog) FeedbackDialog(theme, { msg, isErr -> alertManager.showAlert(msg, if (isErr) AlertType.ERROR else AlertType.SUCCESS) }) { showFeedbackDialog = false }

                if (showBurnEditDialog) {
                    var burnInput by remember { mutableStateOf(dailyBurnCeiling.toInt().toString()) }
                    Dialog(onDismissRequest = { showBurnEditDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showBurnEditDialog = false }, contentAlignment = Alignment.Center) {
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp).border(1.dp, theme.borderLight, RoundedCornerShape(16.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Update Daily Burn Target", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Configure baseline daily operational burn ceiling.", color = theme.textMuted, fontSize = 11.sp)
                                    CompactInputField(value = burnInput, onValueChange = { input -> if (input.all { c -> c.isDigit() }) burnInput = input }, placeholder = "500", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                                    val isValid = (burnInput.toDoubleOrNull() ?: 0.0) > 0.0
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { if (isValid) { dailyBurnCeiling = burnInput.toDouble(); prefs.edit().putFloat("daily_burn_ceiling", burnInput.toFloat()).apply(); showBurnEditDialog = false } }, modifier = Modifier.weight(1.3f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight), colors = ButtonDefaults.buttonColors(containerColor = if (isValid) theme.accent else Color.Transparent, disabledContainerColor = Color.Transparent), enabled = isValid) {
                                            Text("Save", color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        OutlinedButton(onClick = { showBurnEditDialog = false }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)) {
                                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showClearLedgerConfirmation) CenteredMasterPinPurgeModal(masterSecurityPin, theme, { showClearLedgerConfirmation = false }) { scope.launch { completedTransactions.forEach { db.ledgerDao().deleteTransaction(it) }; rawPockets.forEach { db.ledgerDao().updatePocket(it.copy(isArchived = true)) }; alertManager.showAlert("All vault records purged", AlertType.SUCCESS); showClearLedgerConfirmation = false } }
                
                editingRecurringRule?.let { rule ->
                    EditRecurringRuleModal(
                        rule = rule, theme = theme, onDismiss = { editingRecurringRule = null },
                        onSave = { updatedDesc, updatedAmt, updatedFreq, updatedDateEpoch -> scope.launch { db.ledgerDao().updateTransaction(rule.copy(description = updatedDesc, amount = updatedAmt, recurringFrequency = updatedFreq, timestamp = updatedDateEpoch)); editingRecurringRule = null; alertManager.showAlert("Mandate updated", AlertType.SUCCESS) } }
                    )
                }

                editingTransaction?.let { tx ->
                    EditLedgerEntryModal(
                        tx = tx, theme = theme, onDismiss = { editingTransaction = null },
                        onSave = { updatedAmount, updatedDesc, updatedCategory, updatedDateEpoch, updatedTax, updatedReimb -> scope.launch { db.ledgerDao().updateTransaction(tx.copy(amount = updatedAmount, description = updatedDesc, category = updatedCategory, timestamp = updatedDateEpoch, isTaxDeductible = updatedTax, isReimbursable = updatedReimb)); editingTransaction = null; alertManager.showAlert("Ledger entry updated", AlertType.SUCCESS) } },
                        onDelete = { transactionPendingDeletion = tx; editingTransaction = null }
                    )
                }

                transactionPendingDeletion?.let { tx ->
                    CenteredSafePurgeGuardModal(
                        pocket = LedgerPocket(name = "Transaction: ${tx.amount}", type = PocketType.LIQUID), balance = 0.0, theme = theme, onDismiss = { transactionPendingDeletion = null },
                        onConfirm = { scope.launch { db.ledgerDao().deleteTransaction(tx); transactionPendingDeletion = null; alertManager.showAlert("Transaction deleted. Balance restored.", AlertType.SUCCESS) } }
                    )
                }

                if (showAllRecordsSheet) {
                    AllTransactionsSearchSheet(
                        flowRecords = if (prefilledHistoryFilterPocketId != null) completedTransactions.filter { it.sourcePocketId == prefilledHistoryFilterPocketId || it.targetPocketId == prefilledHistoryFilterPocketId } else completedTransactions,
                        rawPockets = rawPockets, isPrivacyMode = isPrivacyMode, isProUser = isProUnlocked, onDismiss = { showAllRecordsSheet = false },
                        onEditRecord = { tx -> editingTransaction = tx }, onExportCsv = { CsvExporter.exportAndShareTransactions(context, completedTransactions) },
                        onExportPdfDossier = { scope.launch { PdfDossierExporter.generateAndShareDossier(context, pocketBalances, completedTransactions) } }
                    )
                }
            }

            VaultFloatingTopOverlay(alertManager = alertManager, theme = theme)
        }
    }
}

@Composable
private fun LogLoanRepaymentModal(
    loanPocket: LedgerPocket,
    outstandingBalance: Double,
    liquidPockets: List<LedgerPocket>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onRecordRepayment: (amount: Double, sourceBankId: Long) -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var selectedBankId by remember(liquidPockets) { mutableStateOf(liquidPockets.firstOrNull()?.id) }
    var showDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp).border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🏛 LOG LOAN REPAYMENT", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Account: ${loanPocket.name}", color = theme.textMuted, fontSize = 11.5.sp)
                        val balColor = if (outstandingBalance == 0.0) theme.textBright else theme.mildRed
                        Text("Outstanding Balance: ₹ ${String.format("%,.0f", outstandingBalance)}", color = balColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    CompactInputField(value = amountInput, onValueChange = { input -> if (input.all { c -> c.isDigit() || c == '.' }) amountInput = input }, placeholder = "Repayment Amount ₹", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

                    val activeBank = liquidPockets.firstOrNull { it.id == selectedBankId }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).clickable { showDropdown = true }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Paid From: ${activeBank?.name ?: "Select Bank"}", color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = theme.accent)
                        }
                        DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }, modifier = Modifier.background(theme.surface)) {
                            liquidPockets.forEach { pocket -> DropdownMenuItem(text = { Text(pocket.name, color = theme.textBright) }, onClick = { selectedBankId = pocket.id; showDropdown = false }) }
                        }
                    }

                    val isValid = (amountInput.toDoubleOrNull() ?: 0.0) > 0.0 && selectedBankId != null
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRecordRepayment(amountInput.toDouble(), selectedBankId!!) }, modifier = Modifier.weight(1.3f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight), colors = ButtonDefaults.buttonColors(containerColor = if (isValid) theme.accent else Color.Transparent, disabledContainerColor = Color.Transparent), enabled = isValid) {
                            Text("Record Repayment", color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettlePeerBalanceModal(
    peerPocket: LedgerPocket,
    currentBalance: Double,
    liquidPockets: List<LedgerPocket>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSettle: (isReceiving: Boolean, amount: Double, bankId: Long) -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var selectedBankId by remember(liquidPockets) { mutableStateOf(liquidPockets.firstOrNull()?.id) }
    var isLend by remember { mutableStateOf(currentBalance > 0) }
    var showDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp).border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🤝 LEND / BORROW OR TRANSACT", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Peer: ${peerPocket.name}", color = theme.textMuted, fontSize = 11.5.sp)
                        val statText = if (currentBalance > 0) "They Owe You ₹ ${String.format("%,.0f", currentBalance)}" else if (currentBalance < 0) "You Owe Them ₹ ${String.format("%,.0f", abs(currentBalance))}" else "Settled (₹0)"
                        val statColor = if (currentBalance > 0) theme.mildGreen else if (currentBalance < 0) theme.mildRed else theme.textBright
                        Text("Current Status: $statText", color = statColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isLend) theme.mildGreen.copy(alpha = 0.25f) else theme.surfaceAlt).border(1.dp, if (isLend) theme.mildGreen else theme.borderLight, RoundedCornerShape(6.dp)).clickable { isLend = true }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("Lend", color = if (isLend) theme.mildGreen else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (!isLend) theme.mildRed.copy(alpha = 0.25f) else theme.surfaceAlt).border(1.dp, if (!isLend) theme.mildRed else theme.borderLight, RoundedCornerShape(6.dp)).clickable { isLend = false }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("Borrow", color = if (!isLend) theme.mildRed else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    CompactInputField(value = amountInput, onValueChange = { input -> if (input.all { c -> c.isDigit() || c == '.' }) amountInput = input }, placeholder = "Settlement Amount ₹", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

                    val activeBank = liquidPockets.firstOrNull { it.id == selectedBankId }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).clickable { showDropdown = true }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Bank Account: ${activeBank?.name ?: "Select Bank"}", color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = theme.accent)
                        }
                        DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }, modifier = Modifier.background(theme.surface)) {
                            liquidPockets.forEach { pocket -> DropdownMenuItem(text = { Text(pocket.name, color = theme.textBright) }, onClick = { selectedBankId = pocket.id; showDropdown = false }) }
                        }
                    }

                    val isValid = (amountInput.toDoubleOrNull() ?: 0.0) > 0.0 && selectedBankId != null
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSettle(isLend, amountInput.toDouble(), selectedBankId!!) }, modifier = Modifier.weight(1.3f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight), colors = ButtonDefaults.buttonColors(containerColor = if (isValid) theme.accent else Color.Transparent, disabledContainerColor = Color.Transparent), enabled = isValid) {
                            Text("Settle Balance", color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SweepGoalSavingsModal(
    goalPocket: LedgerPocket,
    currentBalance: Double,
    liquidPockets: List<LedgerPocket>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSweep: (isAdding: Boolean, amount: Double, bankId: Long) -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var selectedBankId by remember(liquidPockets) { mutableStateOf(liquidPockets.firstOrNull()?.id) }
    var isAdding by remember { mutableStateOf(true) }
    var showDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp).border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🎯 SWEEP SAVINGS", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Goal: ${goalPocket.name}", color = theme.textMuted, fontSize = 11.5.sp)
                        Text("Target: ₹ ${String.format("%,.0f", goalPocket.targetGoalAmount)} • Saved: ₹ ${String.format("%,.0f", currentBalance)}", color = theme.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isAdding) theme.mildGreen.copy(alpha = 0.25f) else theme.surfaceAlt).border(1.dp, if (isAdding) theme.mildGreen else theme.borderLight, RoundedCornerShape(6.dp)).clickable { isAdding = true }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("Add to Goal", color = if (isAdding) theme.mildGreen else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (!isAdding) theme.mildRed.copy(alpha = 0.25f) else theme.surfaceAlt).border(1.dp, if (!isAdding) theme.mildRed else theme.borderLight, RoundedCornerShape(6.dp)).clickable { isAdding = false }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("Withdraw to Bank", color = if (!isAdding) theme.mildRed else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    CompactInputField(value = amountInput, onValueChange = { input -> if (input.all { c -> c.isDigit() || c == '.' }) amountInput = input }, placeholder = "Sweep Amount ₹", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

                    val activeBank = liquidPockets.firstOrNull { it.id == selectedBankId }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).clickable { showDropdown = true }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Source/Dest Bank: ${activeBank?.name ?: "Select Bank"}", color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = theme.accent)
                        }
                        DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }, modifier = Modifier.background(theme.surface)) {
                            liquidPockets.forEach { pocket -> DropdownMenuItem(text = { Text(pocket.name, color = theme.textBright) }, onClick = { selectedBankId = pocket.id; showDropdown = false }) }
                        }
                    }

                    val isValid = (amountInput.toDoubleOrNull() ?: 0.0) > 0.0 && selectedBankId != null
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSweep(isAdding, amountInput.toDouble(), selectedBankId!!) }, modifier = Modifier.weight(1.3f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight), colors = ButtonDefaults.buttonColors(containerColor = if (isValid) theme.accent else Color.Transparent, disabledContainerColor = Color.Transparent), enabled = isValid) {
                            Text("Sweep Funds", color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RevalueAssetPortfolioModal(
    assetPocket: LedgerPocket,
    currentBookValue: Double,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onUpdateValuation: (newValuation: Double, note: String) -> Unit
) {
    var valuationInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp).border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("📈 REVALUE ASSET PORTFOLIO", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Asset: ${assetPocket.name}", color = theme.textMuted, fontSize = 11.5.sp)
                        val balColor = if (currentBookValue == 0.0) theme.textBright else theme.accent
                        Text("Current Book Value: ₹ ${String.format("%,.0f", currentBookValue)}", color = balColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    CompactInputField(value = valuationInput, onValueChange = { input -> if (input.all { c -> c.isDigit() || c == '.' }) valuationInput = input }, placeholder = "New Market Valuation ₹", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    CompactInputField(value = note, onValueChange = { note = it }, placeholder = "Audit Note (e.g. Q3 Rally)", modifier = Modifier.fillMaxWidth())

                    val isValid = (valuationInput.toDoubleOrNull() ?: 0.0) > 0.0
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onUpdateValuation(valuationInput.toDouble(), note) }, modifier = Modifier.weight(1.3f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight), colors = ButtonDefaults.buttonColors(containerColor = if (isValid) theme.accent else Color.Transparent, disabledContainerColor = Color.Transparent), enabled = isValid) {
                            Text("Update Valuation", color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditLedgerEntryModal(
    tx: LedgerTransaction,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (amount: Double, desc: String, category: String, date: Long, isTax: Boolean, isReimb: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var rawAmount by remember { mutableStateOf(String.format("%.0f", tx.amount)) }
    var description by remember { mutableStateOf(tx.description) }
    var selectedCategory by remember { mutableStateOf(tx.category) }
    var selectedDateEpoch by remember { mutableStateOf(tx.timestamp) }
    var isTaxDeductible by remember { mutableStateOf(tx.isTaxDeductible) }
    var isReimbursable by remember { mutableStateOf(tx.isReimbursable) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormatted = remember(selectedDateEpoch) { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateEpoch)) }
    val categories = listOf("Food & Dining", "Groceries", "Transport", "Bills", "Shopping", "Health", "General", "Salary", "Income")

    if (showDatePicker) CustomCalendarDialog(initialDateMillis = selectedDateEpoch, onDismiss = { showDatePicker = false }) { selectedDateEpoch = it; showDatePicker = false }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 420.dp).border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("EDIT TRANSACTION ENTRY", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                        Text("Delete 🗑", color = theme.mildRed, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onDelete() })
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CompactInputField(value = rawAmount, onValueChange = { input -> if (input.all { c -> c.isDigit() || c == '.' }) rawAmount = input }, placeholder = "Amount ₹", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        Box(modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).clickable { showDatePicker = true }.padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(dateFormatted, color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    CompactInputField(value = description, onValueChange = { description = it }, placeholder = "Merchant / Narration", modifier = Modifier.fillMaxWidth())

                    Text("CATEGORY", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        categories.forEach { cat ->
                            val isSel = selectedCategory == cat
                            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.accent.copy(alpha = 0.22f) else theme.surfaceAlt).border(1.dp, if (isSel) theme.accent else theme.borderLight, RoundedCornerShape(6.dp)).clickable { selectedCategory = cat }.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                Text(cat, color = if (isSel) theme.accent else theme.textBright, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isTaxDeductible) theme.accent.copy(alpha = 0.2f) else theme.surfaceAlt).border(1.dp, if (isTaxDeductible) theme.accent else theme.borderLight, RoundedCornerShape(6.dp)).clickable { isTaxDeductible = !isTaxDeductible }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) { Text("🏷 80C Tag", color = if (isTaxDeductible) theme.accent else theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isReimbursable) theme.mildGreen.copy(alpha = 0.2f) else theme.surfaceAlt).border(1.dp, if (isReimbursable) theme.mildGreen else theme.borderLight, RoundedCornerShape(6.dp)).clickable { isReimbursable = !isReimbursable }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) { Text("💼 Reimbursable", color = if (isReimbursable) theme.mildGreen else theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                    }

                    val isValid = (rawAmount.toDoubleOrNull() ?: 0.0) > 0.0
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { val amt = rawAmount.toDoubleOrNull() ?: return@Button; onSave(amt, description, selectedCategory, selectedDateEpoch, isTaxDeductible, isReimbursable) }, modifier = Modifier.weight(1.3f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight), colors = ButtonDefaults.buttonColors(containerColor = if (isValid) theme.accent else Color.Transparent, disabledContainerColor = Color.Transparent), enabled = isValid) {
                            Text("Update Entry", color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight), colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textMuted)) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth(0.88f).widthIn(max = 380.dp).border(1.dp, theme.borderLight, RoundedCornerShape(14.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (pocket.name.startsWith("Recurring")) "TERMINATE MANDATE" else if (pocket.name.startsWith("Transaction")) "DELETE ENTRY" else "DEACTIVATE ACCOUNT", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Are you sure you want to proceed with \"${pocket.name}\"?", color = theme.textMuted, fontSize = 12.sp)

                    if (!canDeactivate) {
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.mildRed.copy(alpha = 0.15f)).padding(10.dp)) {
                            Text("Active balance is ₹${String.format("%,.0f", abs(balance))}. Settle balance to ₹0 first before deactivating.", color = theme.mildRed, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text("Action confirmed. Affected balances will recalculate automatically.", color = theme.mildGreen, fontSize = 11.5.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onConfirm, modifier = Modifier.weight(1.3f).height(44.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed), enabled = canDeactivate) {
                            Text("Confirm & Delete", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)) {
                            Text("Cancel", color = theme.textMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCarouselCard(
    tag: String,
    status: String,
    isPositive: Boolean,
    heroText: String,
    leftSub: String,
    rightSub: String,
    theme: ThemeColors,
    onCardClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, theme.borderLight, RoundedCornerShape(18.dp))
            .clickable { onCardClick() }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = tag, color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPositive) theme.mildGreen.copy(alpha = 0.18f) else theme.mildRed.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = status,
                        color = if (isPositive) theme.mildGreen else theme.mildRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = heroText,
                color = theme.textBright,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = leftSub, color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(text = rightSub, color = theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlatStreamRow(
    tx: LedgerTransaction,
    rawPockets: List<LedgerPocket>,
    isPrivacyMode: Boolean,
    theme: ThemeColors,
    onLongClick: () -> Unit
) {
    val isOut = tx.movementNature in listOf(MovementNature.OPERATING_EXPENSE, MovementNature.TRANSFER, MovementNature.DEPRECIATION_WRITE, MovementNature.EMI_PRINCIPAL)
    val flowColor = if (isOut) theme.mildRed else theme.mildGreen

    val accountName = remember(tx, rawPockets) {
        rawPockets.firstOrNull { it.id == tx.sourcePocketId }?.name ?: "Account"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = tx.description.ifBlank { tx.category }, color = theme.textBright, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            Text(text = "${tx.category} • $accountName", color = theme.textMuted, fontSize = 10.5.sp)
        }

        val amtStr = if (isPrivacyMode) "₹ •••" else "${if (isOut) "-" else "+"}₹ ${String.format("%,.0f", tx.amount)}"
        Text(text = amtStr, color = flowColor, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    }
}
