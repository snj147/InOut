package com.personal.inout.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.inout.BuildConfig
import com.personal.inout.billing.PlayBillingManager
import com.personal.inout.data.*
import com.personal.inout.ocr.ReceiptScanner
import com.personal.inout.ui.*
import com.personal.inout.ui.components.*
import com.personal.inout.ui.dialogs.*
import com.personal.inout.util.*
import com.personal.inout.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch
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

    val viewModel: DashboardViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(db.ledgerDao(), prefs) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()

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
    
    val theme = when (activeThemeMode) {
        AppThemeMode.PALE_AMBER -> PaleAmberTheme
        AppThemeMode.MATCHA_OLIVE -> MatchaOliveTheme
        AppThemeMode.DESERT_SAND -> DesertSandTheme
        AppThemeMode.NORDIC_SLATE -> NordicSlateTheme
    }

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

    val groupedRecords = remember(uiState.completedTransactions) {
        val calNow = Calendar.getInstance()
        uiState.completedTransactions.take(35).groupBy { tx ->
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
                selectedPocketIdForEntry = uiState.liquidPockets.firstOrNull()?.id
                showUnifiedEntrySheet = true
                alertManager.showAlert("Parsed: ${parsed.merchant} (${(parsed.total ?: 0.0).toIndianRupee()})", AlertType.INFO)
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
                    selectedPocketIdForEntry = uiState.liquidPockets.firstOrNull()?.id
                    showUnifiedEntrySheet = true
                    alertManager.showAlert("Parsed: ${parsed.merchant} (${(parsed.total ?: 0.0).toIndianRupee()})", AlertType.INFO)
                } catch (e: Exception) {
                    alertManager.showAlert("OCR Failed: ${e.localizedMessage}", AlertType.ERROR)
                }
            }
        }
    }

    CompositionLocalProvider(LocalThemeColors provides theme, LocalVaultAlertManager provides alertManager) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = theme.bg,
                topBar = { 
                    DashboardHeader(uiState.totalInflowLifetime, uiState.totalOutflowLifetime, isProUnlocked, isPrivacyMode) { isPrivacyMode = !isPrivacyMode } 
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = theme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                            .border(1.dp, theme.borderLight, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    ) {
                        listOf(
                            Triple(0, "Ledger", Icons.Filled.AccountBalanceWallet),
                            Triple(1, "Passbook", Icons.Filled.AccountBalance),
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
                                            selectedPocketIdForEntry = uiState.liquidPockets.firstOrNull()?.id
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
                            ) { 
                                Icon(
                                    imageVector = if (selectedTab == 1) Icons.Default.AddCard else Icons.Default.Add, 
                                    contentDescription = "Action", 
                                    modifier = Modifier.size(24.dp).rotate(if (selectedTab == 0) rotation else 0f)
                                ) 
                            }
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
                                                0 -> MetricCarouselCard("SPENDABLE CASH (UPI/NEFT)", if (uiState.solvencyDeck.trueSafeLiquid > 0) "Solvent" else "Deficit", uiState.solvencyDeck.trueSafeLiquid > 0, if (isPrivacyMode) "₹ •••" else uiState.solvencyDeck.trueSafeLiquid.toIndianRupee(), "Spendable cash without debt", "Net Worth: ${uiState.solvencyDeck.totalNetWorth.toIndianRupee()}", theme) {}
                                                1 -> MetricCarouselCard("RUNWAY SURVIVAL HORIZON", if (uiState.solvencyDeck.runwayDays > 30) "Comfortable" else "Critical", uiState.solvencyDeck.runwayDays > 30, if (isPrivacyMode) "•• Days" else "${uiState.solvencyDeck.runwayDays} Days", "Daily Target: ${viewModel.dailyBurnCeiling.value.toIndianRupee()}/day ✎", if (uiState.solvencyDeck.burnStatus == BurnPacingStatus.ON_TRACK) "Pacing Normal" else "Burn Spiked", theme) { showBurnEditDialog = true }
                                                2 -> MetricCarouselCard("MONTH-END CASH FORECAST", if (uiState.solvencyDeck.hasEarlyDeficitAlert) "Deficit Alert" else "Healthy", !uiState.solvencyDeck.hasEarlyDeficitAlert, if (isPrivacyMode) "₹ •••" else uiState.solvencyDeck.projectedClosingLiquid.toIndianRupee(), "Deterministic Commitments", "Fixed Outflows Factored", theme) {}
                                                3 -> MetricCarouselCard("STATUTORY NET WORTH (CAPITAL)", if (uiState.solvencyDeck.totalNetWorth >= 0) "Positive" else "Insolvent", uiState.solvencyDeck.totalNetWorth >= 0, if (isPrivacyMode) "₹ •••" else uiState.solvencyDeck.totalNetWorth.toIndianRupee(), "Assets Less Liabilities", "Balance Sheet", theme) {}
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
                                        Text("Recent Ledger Flow", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        if (uiState.completedTransactions.isNotEmpty()) {
                                            Text("View All (${uiState.completedTransactions.size}) →", color = theme.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { prefilledHistoryFilterPocketId = null; showAllRecordsSheet = true })
                                        }
                                    }
                                }
                                if (uiState.completedTransactions.isEmpty()) {
                                    item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { Text("No records logged yet. Tap + below to add entry.", color = theme.textMuted, fontSize = 12.sp) } }
                                } else {
                                    groupedRecords.forEach { (dateHeader, records) ->
                                        item { Text(dateHeader.uppercase(Locale.getDefault()), color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                                        items(records, key = { it.id }) { tx ->
                                            FlatStreamRow(tx, uiState.rawPockets, isPrivacyMode, theme) { editingTransaction = tx }
                                            HorizontalDivider(color = theme.borderLight.copy(alpha = 0.4f), thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }

                        1 -> AccountPocketsView(
                            rawPockets = uiState.rawPockets,
                            pocketBalances = uiState.pocketBalances,
                            recurringTransactions = uiState.recurringTemplates,
                            isPrivacyMode = isPrivacyMode,
                            onTransactPocket = { pocket ->
                                ocrPrefilledNote = ""
                                ocrPrefilledAmount = null
                                when (pocket.type) {
                                    PocketType.CREDIT_CARD -> {
                                        forcedEntryRail = ActiveEntryRail.CARD_BILL
                                        selectedPocketIdForEntry = uiState.liquidPockets.firstOrNull()?.id
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
                                if (bal != 0.0) alertManager.showAlert("Cannot archive account with active balance of ${bal.toIndianRupee()}", AlertType.WARNING)
                                else scope.launch { db.ledgerDao().updatePocket(pocket.copy(isArchived = true)); alertManager.showAlert("${pocket.name} archived", AlertType.SUCCESS) }
                            },
                            onTogglePauseRecurring = { tx ->
                                scope.launch { db.ledgerDao().updateTransaction(tx.copy(recurringFrequency = if (tx.recurringFrequency == "NONE") "MONTHLY" else "NONE")) }
                            },
                            onEditRecurring = { editingRecurringRule = it },
                            onDeleteRecurringSafe = { terminateRecurringGuard = it },
                            onRequestCreateAccount = { reqType -> prefilledCreatePocketType = reqType; showCreatePocketDialog = true }
                        )

                        2 -> IntelligenceScreen(uiState.pocketBalances, uiState.completedTransactions, uiState.recurringTemplates, emptyList(), viewModel.dailyBurnCeiling.value, uiState.solvencyDeck.trueSafeLiquid, isPrivacyMode, theme)

                        3 -> SettingsCardsList(
                            theme = theme, currentSha = currentInstalledSha, lastUpdatedDate = lastInstalledTimeFormatted, masterPin = masterSecurityPin,
                            autoSplitEnabled = viewModel.autoSplitEnabled.collectAsState().value, activeThemeMode = activeThemeMode, availableUpdate = availableUpdateInfo,
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
                            onAutoSplitToggled = { viewModel.toggleAutoSplit(it) },
                            onThemeSelected = { mode -> activeThemeMode = mode; prefs.edit().putString("selected_theme", mode.name).apply() },
                            onExportPdf = { scope.launch { PdfDossierExporter.generateAndShareDossier(context, uiState.pocketBalances, uiState.completedTransactions) } },
                            onExportCsv = { CsvExporter.exportAndShareTransactions(context, uiState.completedTransactions) },
                            onExportEncryptedBackup = { backupExportLauncher.launch("inout_vault_backup_${System.currentTimeMillis()}.vault") },
                            onRestoreEncryptedBackup = { backupRestoreLauncher.launch(arrayOf("application/json", "*/*")) },
                            onClearLedger = { showClearLedgerConfirmation = true }
                        )
                    }
                }

                if (isFabExpanded) { Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { isFabExpanded = false }) }

                activeLoanRepaymentPocket?.let { loanPocket ->
                    val debtBal = abs(uiState.pocketBalances[loanPocket.id] ?: 0.0)
                    LogLoanRepaymentModal(
                        loanPocket = loanPocket, outstandingBalance = debtBal, liquidPockets = uiState.liquidPockets, theme = theme, onDismiss = { activeLoanRepaymentPocket = null },
                        onRecordRepayment = { amt, sourceBankId ->
                            viewModel.executeMasterEntryTrigger(MovementNature.EMI_PRINCIPAL, sourceBankId, loanPocket.id, amt, "Loan EMI", "EMI for ${loanPocket.name}", System.currentTimeMillis(), false, "NONE", false, false, { alertManager.showAlert(it, AlertType.SUCCESS); activeLoanRepaymentPocket = null }, { alertManager.showAlert(it, AlertType.ERROR) }, { alertManager.showAlert(it, AlertType.WARNING) })
                        }
                    )
                }

                activePeerSettlementPocket?.let { peerPocket ->
                    val peerBal = uiState.pocketBalances[peerPocket.id] ?: 0.0
                    SettlePeerBalanceModal(
                        peerPocket = peerPocket, currentBalance = peerBal, liquidPockets = uiState.liquidPockets, theme = theme, onDismiss = { activePeerSettlementPocket = null },
                        onSettle = { isLend, amt, bankId ->
                            val srcId = if (isLend) peerPocket.id else bankId
                            val tgtId = if (isLend) bankId else peerPocket.id
                            viewModel.executeMasterEntryTrigger(MovementNature.TRANSFER, srcId, tgtId, amt, "Peer Settlement", "Settlement with ${peerPocket.name}", System.currentTimeMillis(), false, "NONE", false, false, { alertManager.showAlert(it, AlertType.SUCCESS); activePeerSettlementPocket = null }, { alertManager.showAlert(it, AlertType.ERROR) }, { alertManager.showAlert(it, AlertType.WARNING) })
                        }
                    )
                }

                activeGoalSweepPocket?.let { goalPocket ->
                    val goalBal = uiState.pocketBalances[goalPocket.id] ?: 0.0
                    SweepGoalSavingsModal(
                        goalPocket = goalPocket, currentBalance = goalBal, liquidPockets = uiState.liquidPockets, theme = theme, onDismiss = { activeGoalSweepPocket = null },
                        onSweep = { isAdding, amt, bankId ->
                            val srcId = if (isAdding) bankId else goalPocket.id
                            val tgtId = if (isAdding) goalPocket.id else bankId
                            viewModel.executeMasterEntryTrigger(MovementNature.TRANSFER, srcId, tgtId, amt, "Goal Transfer", "Sweep for ${goalPocket.name}", System.currentTimeMillis(), false, "NONE", false, false, { alertManager.showAlert(it, AlertType.SUCCESS); activeGoalSweepPocket = null }, { alertManager.showAlert(it, AlertType.ERROR) }, { alertManager.showAlert(it, AlertType.WARNING) })
                        }
                    )
                }

                activeAssetRevaluationPocket?.let { assetPocket ->
                    val currentBal = uiState.pocketBalances[assetPocket.id] ?: 0.0
                    RevalueAssetPortfolioModal(
                        assetPocket = assetPocket, currentBookValue = currentBal, theme = theme, onDismiss = { activeAssetRevaluationPocket = null },
                        onUpdateValuation = { newMarketVal, note ->
                            val drift = newMarketVal - currentBal
                            if (drift != 0.0) {
                                val nature = if (drift > 0) MovementNature.VALUATION_MARK else MovementNature.DEPRECIATION_WRITE
                                viewModel.executeMasterEntryTrigger(nature, assetPocket.id, null, abs(drift), "Revaluation", note, System.currentTimeMillis(), false, "NONE", false, false, { alertManager.showAlert("Valuation updated to ${newMarketVal.toIndianRupee()}", AlertType.SUCCESS); activeAssetRevaluationPocket = null }, { alertManager.showAlert(it, AlertType.ERROR) }, { alertManager.showAlert(it, AlertType.WARNING) })
                            } else {
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
                        allPockets = uiState.rawPockets, prefilledPocketId = selectedPocketIdForEntry, prefilledTargetPocketId = selectedTargetPocketIdForEntry,
                        forcedRail = forcedEntryRail, prefilledNote = ocrPrefilledNote, prefilledAmount = ocrPrefilledAmount,
                        onDismiss = { showUnifiedEntrySheet = false },
                        onSubmitTransaction = { n, s, t, a, c, no, d, isR, f, tax, reimb -> 
                            viewModel.executeMasterEntryTrigger(n, s, t, a, c, no, d, isR, f, tax, reimb, { alertManager.showAlert(it, AlertType.SUCCESS); showUnifiedEntrySheet = false }, { alertManager.showAlert(it, AlertType.ERROR) }, { alertManager.showAlert(it, AlertType.WARNING) }) 
                        },
                        onNavigateToCreatePocket = { showUnifiedEntrySheet = false; showCreatePocketDialog = true }
                    )
                }

                if (showNoticesDialog) CenteredNoticesModal(uiState.unreadNotices, theme, { showNoticesDialog = false }) { scope.launch { for (notice in uiState.unreadNotices) db.ledgerDao().markNoticeAsRead(notice.id); showNoticesDialog = false } }
                if (showSetPinDialog) ThemeSetPinDialog({ showSetPinDialog = false }) { newPin -> masterSecurityPin = newPin; prefs.edit().putString("master_security_pin", newPin).apply(); showSetPinDialog = false; alertManager.showAlert("Master PIN updated", AlertType.SUCCESS) }
                
                if (showCreatePocketDialog) {
                    CreateAccountDialog(
                        initialType = prefilledCreatePocketType, allLiquidPockets = uiState.liquidPockets, theme = theme, onDismiss = { showCreatePocketDialog = false },
                        onSave = { name, type, limit, dueDay, targetAmt, targetDateEpoch, initialVal, interestRate, linkedAssetType ->
                            scope.launch {
                                val pocketId = db.ledgerDao().insertPocket(LedgerPocket(name = name, type = type, creditLimit = limit, billDueDay = dueDay, targetGoalAmount = targetAmt, goalTargetDate = targetDateEpoch))
                                if (type == PocketType.LIABILITY_LOAN && linkedAssetType != null && initialVal > 0.0) {
                                    val assetId = db.ledgerDao().insertPocket(LedgerPocket(name = "$name - Asset", type = linkedAssetType))
                                    viewModel.ledgerEngine.recordMovement(MovementNature.OPENING_BASELINE, pocketId, null, initialVal, "Initial Position", "Loan Principal baseline", System.currentTimeMillis())
                                    viewModel.ledgerEngine.recordMovement(MovementNature.OPENING_BASELINE, assetId, null, initialVal, "Initial Position", "Asset Acquisition via Loan", System.currentTimeMillis())
                                } else if (initialVal > 0.0) {
                                    viewModel.ledgerEngine.recordMovement(MovementNature.OPENING_BASELINE, pocketId, null, initialVal, "Initial Position", "Opening baseline for $name", System.currentTimeMillis())
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
                                    val res = viewModel.ledgerEngine.recordMovement(MovementNature.TRANSFER, srcId, tgtId, amount, if (isLend) "Peer Debt" else "Peer Advance", if (isLend) "Lent to $contactName" else "Borrowed from $contactName")
                                    if (res is VaultExecutionResult.OverdraftError) {
                                        val pocketToDelete = db.ledgerDao().getPocketById(peerPocketId)
                                        if (pocketToDelete != null) {
                                            db.ledgerDao().deletePocket(pocketToDelete)
                                        }
                                        alertManager.showAlert(res.message, AlertType.ERROR)
                                        return@launch
                                    }
                                } else if (amount > 0.0) {
                                    viewModel.ledgerEngine.recordMovement(MovementNature.OPENING_BASELINE, peerPocketId, null, amount, "Initial Position", "Opening peer ledger for $contactName")
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
                    var burnInput by remember { mutableStateOf(viewModel.dailyBurnCeiling.value.toInt().toString()) }
                    Dialog(onDismissRequest = { showBurnEditDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showBurnEditDialog = false }, contentAlignment = Alignment.Center) {
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp).border(1.dp, theme.borderLight, RoundedCornerShape(16.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Update Daily Burn Target", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Configure baseline daily operational burn ceiling.", color = theme.textMuted, fontSize = 11.sp)
                                    CompactInputField(value = burnInput, onValueChange = { input -> if (input.all { c -> c.isDigit() }) burnInput = input }, placeholder = "500", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                                    val isValid = (burnInput.toDoubleOrNull() ?: 0.0) > 0.0
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { if (isValid) { viewModel.updateBurnCeiling(burnInput.toDouble()); showBurnEditDialog = false } }, modifier = Modifier.weight(1.3f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight), colors = ButtonDefaults.buttonColors(containerColor = if (isValid) theme.accent else Color.Transparent, disabledContainerColor = Color.Transparent), enabled = isValid) {
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

                if (showClearLedgerConfirmation) CenteredMasterPinPurgeModal(masterSecurityPin, theme, { showClearLedgerConfirmation = false }) { scope.launch { uiState.completedTransactions.forEach { db.ledgerDao().deleteTransaction(it) }; uiState.rawPockets.forEach { db.ledgerDao().updatePocket(it.copy(isArchived = true)) }; alertManager.showAlert("All vault records purged", AlertType.SUCCESS); showClearLedgerConfirmation = false } }
                
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
                        flowRecords = if (prefilledHistoryFilterPocketId != null) uiState.completedTransactions.filter { it.sourcePocketId == prefilledHistoryFilterPocketId || it.targetPocketId == prefilledHistoryFilterPocketId } else uiState.completedTransactions,
                        rawPockets = uiState.rawPockets, isPrivacyMode = isPrivacyMode, isProUser = isProUnlocked, onDismiss = { showAllRecordsSheet = false },
                        onEditRecord = { tx -> editingTransaction = tx }, onExportCsv = { CsvExporter.exportAndShareTransactions(context, uiState.completedTransactions) },
                        onExportPdfDossier = { scope.launch { PdfDossierExporter.generateAndShareDossier(context, uiState.pocketBalances, uiState.completedTransactions) } }
                    )
                }
            }

            VaultFloatingTopOverlay(alertManager = alertManager, theme = theme)
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

        val amtStr = if (isPrivacyMode) "₹ •••" else "${if (isOut) "-" else "+"} ₹ ${tx.amount.toIndianRupeeRaw()}"
        Text(text = amtStr, color = flowColor, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    }
}
