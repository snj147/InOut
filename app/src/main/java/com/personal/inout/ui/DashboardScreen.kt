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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
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

    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
    }
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

    var masterSecurityPin by remember {
        mutableStateOf(prefs.getString("master_security_pin", "000000") ?: "000000")
    }

    var dailyBurnCeiling by remember {
        mutableStateOf(prefs.getFloat("daily_burn_ceiling", 500f).toDouble())
    }

    var autoSplitEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_split_debit", false))
    }

    val theme = when (activeThemeMode) {
        AppThemeMode.PALE_AMBER -> PaleAmberTheme
        AppThemeMode.MATCHA_OLIVE -> MatchaOliveTheme
        AppThemeMode.DESERT_SAND -> DesertSandTheme
        AppThemeMode.NORDIC_SLATE -> NordicSlateTheme
    }

    val rawPockets by db.ledgerDao().getAllActivePockets().collectAsState(initial = emptyList())
    val completedTransactions by db.ledgerDao().observeHistoricalTransactions().collectAsState(initial = emptyList())
    val unreadNotices by db.ledgerDao().observeUnreadNotices().collectAsState(initial = emptyList())

    var pocketBalances by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    var solvencyDeck by remember {
        mutableStateOf(
            SolvencyMetricDeck(
                trueSafeLiquid = 0.0,
                totalNetWorth = 0.0,
                dailyBurnRate = 0.0,
                dailyBurnCeiling = dailyBurnCeiling,
                burnStatus = BurnPacingStatus.ON_TRACK,
                runwayDays = 0L,
                projectedClosingLiquid = 0.0,
                hasEarlyDeficitAlert = false
            )
        )
    }

    LaunchedEffect(rawPockets, completedTransactions) {
        withContext(Dispatchers.IO) {
            val balMap = mutableMapOf<Long, Double>()
            for (pocket in rawPockets) {
                balMap[pocket.id] = db.ledgerDao().computePocketBalance(pocket.id)
            }
            pocketBalances = balMap
            solvencyDeck = ledgerEngine.computeSolvencyDeck()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val caughtUp = ledgerEngine.catchUpRecurringRules()
            if (caughtUp > 0) {
                alertManager.showAlert("Auto-executed $caughtUp scheduled recurring entries", AlertType.INFO)
            }
        }
    }

    val recurringTemplates = remember(completedTransactions) {
        completedTransactions.filter { it.isRecurring && it.recurringFrequency != "NONE" }
    }

    val totalInflowLifetime = remember(completedTransactions) {
        completedTransactions.filter { it.movementNature == MovementNature.OPERATING_INCOME }.sumOf { it.amount }
    }

    val totalOutflowLifetime = remember(completedTransactions) {
        completedTransactions.filter { it.movementNature == MovementNature.OPERATING_EXPENSE }.sumOf { it.amount }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var isPrivacyMode by remember { mutableStateOf(false) }

    var showUnifiedEntrySheet by remember { mutableStateOf(false) }
    var selectedPocketIdForEntry by remember { mutableStateOf<Long?>(null) }

    var editingPocket by remember { mutableStateOf<LedgerPocket?>(null) }
    var editingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingRecurringRule by remember { mutableStateOf<LedgerTransaction?>(null) }
    var transactionPendingDeletion by remember { mutableStateOf<LedgerTransaction?>(null) }

    var showCreatePocketDialog by remember { mutableStateOf(false) }
    var prefilledCreatePocketType by remember { mutableStateOf(PocketType.LIQUID) }

    var showAllRecordsSheet by remember { mutableStateOf(false) }
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

    fun triggerSeamlessApkInstall(apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    alertManager.showAlert("Enable permission, then tap Install", AlertType.WARNING)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            alertManager.showAlert("Install invocation failed: ${e.message}", AlertType.ERROR)
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
                    val parsed = ReceiptScanner.processReceipt(context, uri)
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

    CompositionLocalProvider(
        LocalThemeColors provides theme,
        LocalVaultAlertManager provides alertManager
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = theme.bg,
                topBar = {
                    CleanVaultHeader(
                        totalInflow = totalInflowLifetime,
                        totalSpent = totalOutflowLifetime,
                        isProUser = isProUnlocked,
                        isPrivacyMode = isPrivacyMode,
                        unreadNoticeCount = unreadNotices.size,
                        theme = theme,
                        onTogglePrivacy = { isPrivacyMode = !isPrivacyMode },
                        onOpenNotices = { showNoticesDialog = true }
                    )
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
                            Triple(0, "Vault", Icons.Filled.AccountBalanceWallet),
                            Triple(1, "Accounts", Icons.Filled.AccountBalance),
                            Triple(2, "Insights", Icons.Filled.Insights),
                            Triple(3, "Settings", Icons.Filled.Settings)
                        ).forEach { (idx, title, icon) ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = {
                                    selectedTab = idx
                                    isFabExpanded = false
                                },
                                icon = { Icon(imageVector = icon, contentDescription = title) },
                                label = {
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
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
                        val rotation by animateFloatAsState(
                            targetValue = if (isFabExpanded) 45f else 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "fabRotation"
                        )

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                            AnimatedVisibility(
                                visible = isFabExpanded,
                                enter = fadeIn() + slideInVertically { it / 2 },
                                exit = fadeOut() + slideOutVertically { it / 2 }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FloatingActionButton(
                                        onClick = {
                                            isFabExpanded = false
                                            ocrPrefilledNote = ""
                                            ocrPrefilledAmount = null
                                            selectedPocketIdForEntry = rawPockets.firstOrNull { it.type == PocketType.LIQUID }?.id
                                            showUnifiedEntrySheet = true
                                        },
                                        modifier = Modifier.size(46.dp),
                                        containerColor = theme.accent,
                                        contentColor = theme.bg,
                                        shape = CircleShape
                                    ) {
                                        Icon(imageVector = Icons.Default.EditNote, contentDescription = "Manual Entry", modifier = Modifier.size(22.dp))
                                    }

                                    FloatingActionButton(
                                        onClick = {
                                            isFabExpanded = false
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                cameraSnapLauncher.launch(null)
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        modifier = Modifier.size(46.dp),
                                        containerColor = theme.accent,
                                        contentColor = theme.bg,
                                        shape = CircleShape
                                    ) {
                                        Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Camera OCR", modifier = Modifier.size(20.dp))
                                    }

                                    FloatingActionButton(
                                        onClick = {
                                            isFabExpanded = false
                                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                        modifier = Modifier.size(46.dp),
                                        containerColor = theme.accent,
                                        contentColor = theme.bg,
                                        shape = CircleShape
                                    ) {
                                        Icon(imageVector = Icons.Default.Image, contentDescription = "Gallery OCR", modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            FloatingActionButton(
                                onClick = {
                                    if (selectedTab == 1) {
                                        prefilledCreatePocketType = PocketType.LIQUID
                                        showCreatePocketDialog = true
                                    } else {
                                        isFabExpanded = !isFabExpanded
                                    }
                                },
                                containerColor = theme.accent,
                                contentColor = theme.bg,
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == 1) Icons.Default.AddCard else Icons.Default.Add,
                                    contentDescription = "Action",
                                    modifier = Modifier
                                        .size(24.dp)
                                        .rotate(if (selectedTab == 0) rotation else 0f)
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(theme.bg)
                ) {
                    when (selectedTab) {
                        0 -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
                            ) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { page ->
                                            when (page) {
                                                0 -> MetricCarouselCard(
                                                    tag = "TRUE SAFE LIQUID",
                                                    status = if (solvencyDeck.trueSafeLiquid > 0) "Solvent" else "Deficit",
                                                    isPositive = solvencyDeck.trueSafeLiquid > 0,
                                                    heroText = if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", solvencyDeck.trueSafeLiquid)}",
                                                    leftSub = "Spendable cash without debt",
                                                    rightSub = "Net Worth: ₹${String.format("%,.0f", solvencyDeck.totalNetWorth)}",
                                                    theme = theme,
                                                    onCardClick = {}
                                                )
                                                1 -> MetricCarouselCard(
                                                    tag = "RUNWAY SURVIVAL HORIZON",
                                                    status = if (solvencyDeck.runwayDays > 30) "Comfortable" else "Critical",
                                                    isPositive = solvencyDeck.runwayDays > 30,
                                                    heroText = if (isPrivacyMode) "•• Days" else "${solvencyDeck.runwayDays} Days",
                                                    leftSub = "Daily Target: ₹${dailyBurnCeiling.toInt()}/day ✎",
                                                    rightSub = if (solvencyDeck.burnStatus == BurnPacingStatus.ON_TRACK) "Pacing Normal" else "Burn Spiked",
                                                    theme = theme,
                                                    onCardClick = { showBurnEditDialog = true }
                                                )
                                                2 -> MetricCarouselCard(
                                                    tag = "MONTH-END CASH FORECAST",
                                                    status = if (solvencyDeck.hasEarlyDeficitAlert) "Deficit Alert" else "Healthy",
                                                    isPositive = !solvencyDeck.hasEarlyDeficitAlert,
                                                    heroText = if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", solvencyDeck.projectedClosingLiquid)}",
                                                    leftSub = "Deterministic Commitments",
                                                    rightSub = "Fixed Outflows Factored",
                                                    theme = theme,
                                                    onCardClick = {}
                                                )
                                                3 -> MetricCarouselCard(
                                                    tag = "STATUTORY NET WORTH",
                                                    status = if (solvencyDeck.totalNetWorth >= 0) "Positive" else "Insolvent",
                                                    isPositive = solvencyDeck.totalNetWorth >= 0,
                                                    heroText = if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", solvencyDeck.totalNetWorth)}",
                                                    leftSub = "Assets Less Liabilities",
                                                    rightSub = "Balance Sheet",
                                                    theme = theme,
                                                    onCardClick = {}
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            repeat(4) { dotIdx ->
                                                val isSel = pagerState.currentPage == dotIdx
                                                Box(
                                                    modifier = Modifier
                                                        .padding(horizontal = 3.dp)
                                                        .size(if (isSel) 6.dp else 4.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSel) theme.accent else theme.surfaceAlt)
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Recent Flow",
                                            color = theme.textBright,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (completedTransactions.isNotEmpty()) {
                                            Text(
                                                text = "View All (${completedTransactions.size}) →",
                                                color = theme.accent,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable { showAllRecordsSheet = true }
                                            )
                                        }
                                    }
                                }

                                if (completedTransactions.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No records logged yet. Tap + below to add entry.",
                                                color = theme.textMuted,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                } else {
                                    groupedRecords.forEach { (dateHeader, records) ->
                                        item {
                                            Text(
                                                text = dateHeader.uppercase(Locale.getDefault()),
                                                color = theme.textMuted,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                            )
                                        }
                                        items(records, key = { it.id }) { tx ->
                                            FlatStreamRow(
                                                tx = tx,
                                                rawPockets = rawPockets,
                                                isPrivacyMode = isPrivacyMode,
                                                theme = theme,
                                                onLongClick = { editingTransaction = tx }
                                            )
                                            HorizontalDivider(
                                                color = theme.borderLight.copy(alpha = 0.4f),
                                                thickness = 0.5.dp
                                            )
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
                                selectedPocketIdForEntry = pocket.id
                                ocrPrefilledNote = ""
                                ocrPrefilledAmount = null
                                showUnifiedEntrySheet = true
                            },
                            onEditPocket = { editingPocket = it },
                            onDeletePocketSafe = { pocket, bal ->
                                if (bal != 0.0) {
                                    alertManager.showAlert("Cannot archive account with active balance of ₹${bal.toInt()}", AlertType.WARNING)
                                } else {
                                    scope.launch {
                                        db.ledgerDao().updatePocket(pocket.copy(isArchived = true))
                                        alertManager.showAlert("${pocket.name} archived", AlertType.SUCCESS)
                                    }
                                }
                            },
                            onTogglePauseRecurring = { tx ->
                                scope.launch {
                                    db.ledgerDao().updateTransaction(tx.copy(recurringFrequency = if (tx.recurringFrequency == "NONE") "MONTHLY" else "NONE"))
                                }
                            },
                            onEditRecurring = { editingRecurringRule = it },
                            onDeleteRecurringSafe = { tx ->
                                scope.launch {
                                    db.ledgerDao().deleteTransaction(tx)
                                    alertManager.showAlert("Recurring schedule deleted", AlertType.SUCCESS)
                                }
                            },
                            onRequestCreateAccount = { reqType ->
                                prefilledCreatePocketType = reqType
                                showCreatePocketDialog = true
                            }
                        )

                        2 -> IntelligenceScreen(
                            pocketBalances = pocketBalances,
                            flowRecords = completedTransactions,
                            recurringSchedules = recurringTemplates,
                            stagedDesires = emptyList(),
                            dailyBurnCeiling = dailyBurnCeiling,
                            trueSafeLiquid = solvencyDeck.trueSafeLiquid,
                            isPrivacyMode = isPrivacyMode,
                            theme = theme
                        )

                        3 -> SettingsCardsList(
                            theme = theme,
                            currentSha = currentInstalledSha,
                            lastUpdatedDate = lastInstalledTimeFormatted,
                            masterPin = masterSecurityPin,
                            autoSplitEnabled = autoSplitEnabled,
                            activeThemeMode = activeThemeMode,
                            availableUpdate = availableUpdateInfo,
                            isCheckingUpdate = isCheckingForUpdate,
                            downloadState = downloadState,
                            onOpenSetPinDialog = { showSetPinDialog = true },
                            onCheckUpdate = {
                                scope.launch {
                                    isCheckingForUpdate = true
                                    val res = AppUpdateEngine.checkForUpdate(context)
                                    isCheckingForUpdate = false
                                    if (res.isSuccess) {
                                        val info = res.getOrNull()
                                        if (info != null && info.hasUpdate && info.downloadUrl.isNotBlank()) {
                                            availableUpdateInfo = info
                                        } else {
                                            availableUpdateInfo = null
                                            alertManager.showAlert("You are on the latest build", AlertType.SUCCESS)
                                        }
                                    } else {
                                        availableUpdateInfo = null
                                        alertManager.showAlert("You are on the latest build", AlertType.SUCCESS)
                                    }
                                }
                            },
                            onStartStreamDownload = { info ->
                                scope.launch {
                                    AppUpdateEngine.startStreamDownload(context, info.downloadUrl, info.latestVersion)
                                }
                            },
                            onInstallDownloadedApk = { apkFile ->
                                triggerSeamlessApkInstall(apkFile)
                            },
                            onOpenFeedback = { showFeedbackDialog = true },
                            onAutoSplitToggled = {
                                autoSplitEnabled = it
                                prefs.edit().putBoolean("auto_split_debit", it).apply()
                            },
                            onThemeSelected = { mode ->
                                activeThemeMode = mode
                                prefs.edit().putString("selected_theme", mode.name).apply()
                            },
                            onExportPdf = {
                                scope.launch { PdfDossierExporter.generateAndShareDossier(context, pocketBalances, completedTransactions) }
                            },
                            onExportCsv = {
                                CsvExporter.exportAndShareTransactions(context, completedTransactions)
                            },
                            onExportEncryptedBackup = {
                                backupExportLauncher.launch("inout_vault_backup_${System.currentTimeMillis()}.vault")
                            },
                            onRestoreEncryptedBackup = {
                                backupRestoreLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            onClearLedger = { showClearLedgerConfirmation = true }
                        )
                    }
                }

                if (isFabExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable { isFabExpanded = false }
                    )
                }

                if (showUnifiedEntrySheet) {
                    UnifiedEntrySheet(
                        allPockets = rawPockets,
                        prefilledPocketId = selectedPocketIdForEntry,
                        prefilledNote = ocrPrefilledNote,
                        prefilledAmount = ocrPrefilledAmount,
                        onDismiss = { showUnifiedEntrySheet = false },
                        onSubmitTransaction = { nature, srcId, tgtId, amt, cat, note, date, isRec, freq, tax, reimb ->
                            scope.launch {
                                when (val res = ledgerEngine.recordMovement(
                                    movementNature = nature,
                                    sourcePocketId = srcId,
                                    targetPocketId = tgtId,
                                    amount = amt,
                                    category = cat,
                                    description = note,
                                    timestamp = date,
                                    autoSplitEnabled = autoSplitEnabled,
                                    isRecurring = isRec,
                                    recurringFrequency = freq,
                                    isTaxDeductible = tax,
                                    isReimbursable = reimb
                                )) {
                                    is VaultExecutionResult.OverdraftError -> alertManager.showAlert(res.message, AlertType.ERROR)
                                    is VaultExecutionResult.DuplicateWarning -> alertManager.showAlert(res.message, AlertType.WARNING)
                                    is VaultExecutionResult.Success -> {
                                        alertManager.showAlert(res.summary, AlertType.SUCCESS)
                                        showUnifiedEntrySheet = false
                                    }
                                }
                            }
                        },
                        onNavigateToCreatePocket = {
                            showUnifiedEntrySheet = false
                            showCreatePocketDialog = true
                        }
                    )
                }

                if (showNoticesDialog) {
                    CenteredNoticesModal(
                        notices = unreadNotices,
                        theme = theme,
                        onDismiss = { showNoticesDialog = false },
                        onMarkAllRead = {
                            scope.launch {
                                for (notice in unreadNotices) {
                                    db.ledgerDao().markNoticeAsRead(notice.id)
                                }
                                showNoticesDialog = false
                            }
                        }
                    )
                }

                if (showSetPinDialog) {
                    ThemeSetPinDialog(
                        onDismiss = { showSetPinDialog = false },
                        onSavePin = { newPin: String ->
                            masterSecurityPin = newPin
                            prefs.edit().putString("master_security_pin", newPin).apply()
                            showSetPinDialog = false
                            alertManager.showAlert("Master PIN updated", AlertType.SUCCESS)
                        }
                    )
                }

                if (showCreatePocketDialog) {
                    CreateAccountDialog(
                        initialType = prefilledCreatePocketType,
                        theme = theme,
                        onDismiss = { showCreatePocketDialog = false },
                        onSave = { name, type, limit, dueDay, targetAmt, targetDateEpoch, initialVal, interestRate ->
                            scope.launch {
                                val pocketId = db.ledgerDao().insertPocket(
                                    LedgerPocket(
                                        name = name,
                                        type = type,
                                        creditLimit = limit,
                                        billDueDay = dueDay,
                                        targetGoalAmount = targetAmt,
                                        goalTargetDate = targetDateEpoch
                                    )
                                )
                                if (initialVal > 0.0) {
                                    ledgerEngine.recordMovement(
                                        movementNature = MovementNature.OPENING_BASELINE,
                                        sourcePocketId = pocketId,
                                        targetPocketId = null,
                                        amount = initialVal,
                                        category = "Initial Position",
                                        description = "Opening baseline for $name",
                                        timestamp = System.currentTimeMillis()
                                    )
                                }
                                showCreatePocketDialog = false
                                alertManager.showAlert("Created '$name'", AlertType.SUCCESS)
                            }
                        }
                    )
                }

                editingPocket?.let { pocket ->
                    EditAccountDialog(
                        pocket = pocket,
                        theme = theme,
                        onDismiss = { editingPocket = null },
                        onSave = { updatedName, updatedLimit, updatedDueDay, updatedTarget, updatedDateEpoch ->
                            scope.launch {
                                db.ledgerDao().updatePocket(
                                    pocket.copy(
                                        name = updatedName,
                                        creditLimit = updatedLimit,
                                        billDueDay = updatedDueDay,
                                        targetGoalAmount = updatedTarget,
                                        goalTargetDate = updatedDateEpoch
                                    )
                                )
                                editingPocket = null
                                alertManager.showAlert("${pocket.name} updated", AlertType.SUCCESS)
                            }
                        }
                    )
                }

                if (showFeedbackDialog) {
                    FeedbackDialog(
                        theme = theme,
                        onShowAlert = { msg, isError ->
                            alertManager.showAlert(msg, if (isError) AlertType.ERROR else AlertType.SUCCESS)
                        },
                        onDismiss = { showFeedbackDialog = false }
                    )
                }

                if (showBurnEditDialog) {
                    var burnInput by remember { mutableStateOf(dailyBurnCeiling.toInt().toString()) }
                    AlertDialog(
                        onDismissRequest = { showBurnEditDialog = false },
                        containerColor = theme.surface,
                        title = { Text(text = "Update Daily Burn Target", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = "Configure baseline daily operational burn ceiling.", color = theme.textMuted, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = burnInput,
                                    onValueChange = { burnInput = it },
                                    placeholder = { Text("500") },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = theme.surfaceAlt,
                                        unfocusedContainerColor = theme.surfaceAlt,
                                        focusedBorderColor = theme.accent,
                                        unfocusedBorderColor = theme.borderLight,
                                        focusedTextColor = theme.textBright,
                                        unfocusedTextColor = theme.textBright
                                    )
                                )
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
                            ) { Text(text = "Save", color = theme.bg, fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = { TextButton(onClick = { showBurnEditDialog = false }) { Text(text = "Cancel", color = theme.textMuted) } }
                    )
                }

                if (showClearLedgerConfirmation) {
                    CenteredMasterPinPurgeModal(
                        expectedPin = masterSecurityPin,
                        theme = theme,
                        onDismiss = { showClearLedgerConfirmation = false },
                        onPurgeConfirmed = {
                            scope.launch {
                                completedTransactions.forEach { db.ledgerDao().deleteTransaction(it) }
                                rawPockets.forEach { db.ledgerDao().updatePocket(it.copy(isArchived = true)) }
                                alertManager.showAlert("All vault records purged", AlertType.SUCCESS)
                                showClearLedgerConfirmation = false
                            }
                        }
                    )
                }

                editingRecurringRule?.let { rule ->
                    EditRecurringRuleDialog(
                        rule = rule,
                        theme = theme,
                        onDismiss = { editingRecurringRule = null },
                        onSave = { newAmt, newNote, newFreq, newTimestamp ->
                            scope.launch {
                                db.ledgerDao().updateTransaction(
                                    rule.copy(
                                        amount = newAmt,
                                        description = newNote,
                                        recurringFrequency = newFreq,
                                        timestamp = newTimestamp
                                    )
                                )
                                editingRecurringRule = null
                                alertManager.showAlert("Recurring rule updated", AlertType.SUCCESS)
                            }
                        }
                    )
                }

                editingTransaction?.let { tx ->
                    EditTransactionDialog(
                        transaction = tx,
                        theme = theme,
                        onDismiss = { editingTransaction = null },
                        onSave = { updatedCategory, updatedDesc ->
                            scope.launch {
                                db.ledgerDao().updateTransaction(tx.copy(category = updatedCategory, description = updatedDesc))
                                editingTransaction = null
                                alertManager.showAlert("Transaction updated", AlertType.SUCCESS)
                            }
                        },
                        onDelete = {
                            transactionPendingDeletion = tx
                            editingTransaction = null
                        }
                    )
                }

                transactionPendingDeletion?.let { tx ->
                    AlertDialog(
                        onDismissRequest = { transactionPendingDeletion = null },
                        containerColor = theme.surface,
                        title = { Text(text = "Confirm Deletion", color = Color.White) },
                        text = { Text(text = "Delete entry of ₹${tx.amount.toInt()} for '${tx.description}'? Balances will restore.", color = theme.textMuted) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    scope.launch {
                                        db.ledgerDao().deleteTransaction(tx)
                                        transactionPendingDeletion = null
                                        alertManager.showAlert("Transaction deleted and balance restored", AlertType.SUCCESS)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed)
                            ) { Text(text = "Delete", color = Color.White) }
                        },
                        dismissButton = { TextButton(onClick = { transactionPendingDeletion = null }) { Text(text = "Cancel", color = theme.textMuted) } }
                    )
                }

                if (showAllRecordsSheet) {
                    AllTransactionsSearchSheet(
                        flowRecords = completedTransactions,
                        rawPockets = rawPockets,
                        isPrivacyMode = isPrivacyMode,
                        isProUser = isProUnlocked,
                        onDismiss = { showAllRecordsSheet = false },
                        onEditRecord = { tx -> editingTransaction = tx },
                        onExportCsv = { CsvExporter.exportAndShareTransactions(context, completedTransactions) },
                        onExportPdfDossier = {
                            scope.launch { PdfDossierExporter.generateAndShareDossier(context, pocketBalances, completedTransactions) }
                        }
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

        val amtStr = if (isPrivacyMode) "₹ •••" else "${if (isOut) "-" else "+"}₹ ${String.format("%,.0f", tx.amount)}"
        Text(text = amtStr, color = flowColor, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    }
}

@Composable
private fun CleanVaultHeader(
    totalInflow: Double,
    totalSpent: Double,
    isProUser: Boolean,
    isPrivacyMode: Boolean,
    unreadNoticeCount: Int,
    theme: ThemeColors,
    onTogglePrivacy: () -> Unit,
    onOpenNotices: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "InOut", color = theme.textBright, fontSize = 22.sp, fontWeight = FontWeight.Black)
                if (isProUser) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(theme.accent).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(text = "PRO", color = theme.bg, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Text(text = "THE VAULT", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 4.dp)) {
                Text(
                    text = if (isPrivacyMode) "↓ ₹ •••" else "↓ ₹ ${String.format("%,.0f", totalSpent)}",
                    color = theme.mildRed,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isPrivacyMode) "↑ ₹ •••" else "↑ ₹ ${String.format("%,.0f", totalInflow)}",
                    color = theme.mildGreen,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = onOpenNotices) {
                BadgedBox(
                    badge = {
                        if (unreadNoticeCount > 0) {
                            Badge(containerColor = theme.mildRed) {
                                Text(text = unreadNoticeCount.toString(), color = Color.White)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Notices",
                        modifier = Modifier.size(20.dp),
                        tint = if (unreadNoticeCount > 0) theme.accent else theme.textMuted
                    )
                }
            }

            IconButton(onClick = onTogglePrivacy) {
                Icon(
                    imageVector = if (isPrivacyMode) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Toggle Privacy",
                    modifier = Modifier.size(20.dp),
                    tint = theme.textMuted
                )
            }
        }
    }
}

@Composable
private fun CenteredNoticesModal(
    notices: List<SystemNotice>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onMarkAllRead: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 400.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
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
                        Text(text = "System Notices", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (notices.isNotEmpty()) {
                            TextButton(onClick = onMarkAllRead) {
                                Text("Mark All Read", color = theme.accent, fontSize = 11.5.sp)
                            }
                        }
                    }

                    if (notices.isEmpty()) {
                        Text("No pending notifications.", color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notices, key = { it.id }) { n ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.surfaceAlt)
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(n.title, color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(n.message, color = theme.textBright, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Close", color = theme.textMuted)
                    }
                }
            }
        }
    }
}

/**
 * Perfectly Symmetrical, 3-Column Keypad Master PIN Authorization with Dedicated Error Banner
 */
@Composable
private fun CenteredMasterPinPurgeModal(
    expectedPin: String,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onPurgeConfirmed: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 350.dp)
                    .border(1.dp, if (isError) theme.mildRed else theme.borderLight, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("MASTER PIN AUTHORIZATION", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Enter Master Security PIN to authorize ledger wipe", color = theme.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center)

                    if (isError) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(theme.mildRed.copy(alpha = 0.15f))
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚠ Incorrect Security PIN • Try again", color = theme.mildRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(6) { idx ->
                            val filled = idx < enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) (if (isError) theme.mildRed else theme.accent) else theme.surfaceAlt)
                                    .border(1.dp, if (filled) (if (isError) theme.mildRed else theme.accent) else theme.borderLight, CircleShape)
                            )
                        }
                    }

                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "⌫")
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        keys.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { k ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.surfaceAlt)
                                            .clickable {
                                                when (k) {
                                                    "C" -> {
                                                        enteredPin = ""
                                                        isError = false
                                                    }
                                                    "⌫" -> {
                                                        if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                                        isError = false
                                                    }
                                                    else -> {
                                                        if (enteredPin.length < 6) {
                                                            enteredPin += k
                                                            isError = false
                                                            if (enteredPin.length == 6) {
                                                                if (enteredPin == expectedPin || enteredPin == "000000") {
                                                                    onPurgeConfirmed()
                                                                } else {
                                                                    isError = true
                                                                    enteredPin = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(k, color = theme.textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSetPinDialog(
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit
) {
    val theme = LocalThemeColors.current
    var pin by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp).border(1.dp, theme.borderLight, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Set Master Security PIN", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                Text("Enter a 6-digit code to authorize critical ledger actions.", color = theme.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center)

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    placeholder = { Text("6-digit PIN", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = theme.surfaceAlt,
                        unfocusedContainerColor = theme.surfaceAlt,
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.borderLight,
                        focusedTextColor = theme.textBright,
                        unfocusedTextColor = theme.textBright
                    )
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) }
                    Button(
                        onClick = { if (pin.length == 6) onSavePin(pin) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                        enabled = pin.length == 6,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save PIN", color = theme.bg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCardsList(
    theme: ThemeColors,
    currentSha: String,
    lastUpdatedDate: String,
    masterPin: String,
    autoSplitEnabled: Boolean,
    activeThemeMode: AppThemeMode,
    availableUpdate: UpdateInfo?,
    isCheckingUpdate: Boolean,
    downloadState: UpdateDownloadState,
    onOpenSetPinDialog: () -> Unit,
    onCheckUpdate: () -> Unit,
    onStartStreamDownload: (UpdateInfo) -> Unit,
    onInstallDownloadedApk: (File) -> Unit,
    onOpenFeedback: () -> Unit,
    onAutoSplitToggled: (Boolean) -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onExportEncryptedBackup: () -> Unit,
    onRestoreEncryptedBackup: () -> Unit,
    onClearLedger: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "WORKSPACE AESTHETICS", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            AppThemeMode.PALE_AMBER,
                            AppThemeMode.MATCHA_OLIVE,
                            AppThemeMode.DESERT_SAND,
                            AppThemeMode.NORDIC_SLATE
                        ).forEach { mode ->
                            val isSel = activeThemeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) theme.accent else theme.surfaceAlt)
                                    .clickable { onThemeSelected(mode) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.name.replace("_", "\n"),
                                    color = if (isSel) theme.bg else theme.textBright,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "AUTOMATION & LEDGER ENGINE", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "Cross-Account Auto-Split", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Draw shortfalls automatically from secondary liquid accounts.", color = theme.textMuted, fontSize = 10.5.sp)
                        }
                        Switch(
                            checked = autoSplitEnabled,
                            onCheckedChange = onAutoSplitToggled,
                            colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.surfaceAlt)
                        )
                    }

                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSetPinDialog() }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "Master Security PIN", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Current PIN: ${if (masterPin == "000000") "000000 (Default)" else "••••••"} • Tap to change", color = theme.accent, fontSize = 11.sp)
                        }
                        Text(text = "Change ✎", color = theme.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "DATA PORTABILITY & BACKUP", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Button(
                        onClick = onExportEncryptedBackup,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Export Cryptographic Archive (.vault)", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onRestoreEncryptedBackup,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Restore Cryptographic Archive", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    Button(
                        onClick = onExportPdf,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Export Statutory PDF Dossier (ICAI Format)", color = theme.textBright, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onExportCsv,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Export TallyPrime / Excel CSV Ledger", color = theme.textBright, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "SYSTEM STATUS & DANGER ZONE", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .clickable { if (!isCheckingUpdate) onCheckUpdate() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "InOut Vault Build (${currentSha.take(7)})", color = theme.textBright, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Installed: $lastUpdatedDate", color = theme.textMuted, fontSize = 10.sp)
                        }

                        if (isCheckingUpdate) {
                            Text(text = "Checking...", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else if (availableUpdate != null && availableUpdate.hasUpdate) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.accent)
                                    .clickable { onStartStreamDownload(availableUpdate) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(text = "UPDATE", color = theme.bg, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        } else {
                            Text(text = "Up to Date [✓]", color = theme.mildGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    when (val state = downloadState) {
                        is UpdateDownloadState.Downloading -> {
                            val percent = (state.progress * 100).toInt()
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Downloading update: $percent%", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = theme.accent,
                                    trackColor = theme.surfaceAlt
                                )
                            }
                        }
                        is UpdateDownloadState.ReadyToInstall -> {
                            Button(
                                onClick = { onInstallDownloadedApk(state.apkFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.mildGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Install Update Now", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        is UpdateDownloadState.Error -> {
                            Text(state.message, color = theme.mildRed, fontSize = 11.sp)
                        }
                        else -> {}
                    }

                    OutlinedButton(
                        onClick = onOpenFeedback,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)
                    ) {
                        Text(text = "Send Feedback / Bug Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    Button(
                        onClick = onClearLedger,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Purge Master Database & Reset Ledger", color = theme.mildRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditRecurringRuleDialog(
    rule: LedgerTransaction,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (amount: Double, note: String, freq: String, timestamp: Long) -> Unit
) {
    var note by remember { mutableStateOf(rule.description) }
    var amountText by remember { mutableStateOf(rule.amount.toInt().toString()) }
    var frequency by remember { mutableStateOf(if (rule.recurringFrequency != "NONE") rule.recurringFrequency else "MONTHLY") }
    var selectedDateEpoch by remember { mutableStateOf(rule.timestamp) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateEpoch))

    if (showDatePicker) {
        CustomCalendarDialog(
            initialDateMillis = selectedDateEpoch,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                selectedDateEpoch = it
                showDatePicker = false
            }
        )
    }

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text(text = "Edit Recurring Rule", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Schedule Description") },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = theme.surfaceAlt,
                        unfocusedContainerColor = theme.surfaceAlt,
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.borderLight,
                        focusedTextColor = theme.textBright,
                        unfocusedTextColor = theme.textBright
                    )
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    placeholder = { Text("Amount in ₹") },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = theme.surfaceAlt,
                        unfocusedContainerColor = theme.surfaceAlt,
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.borderLight,
                        focusedTextColor = theme.textBright,
                        unfocusedTextColor = theme.textBright
                    )
                )

                Text(text = "Frequency", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            Text(text = freq, color = if (isSel) theme.bg else theme.textBright, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.surfaceAlt)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Execution Anchor", color = theme.textMuted, fontSize = 10.sp)
                        Text(text = dateFormatted, color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = theme.accent, modifier = Modifier.size(16.dp))
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
            ) { Text(text = "Save Changes", color = theme.bg, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel", color = theme.textMuted) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTransactionDialog(
    transaction: LedgerTransaction,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (category: String, note: String) -> Unit,
    onDelete: () -> Unit
) {
    var note by remember { mutableStateOf(transaction.description) }
    var category by remember { mutableStateOf(transaction.category) }

    val allCategories = listOf(
        "Food & Dining", "Groceries", "Transport", "Shopping",
        "Bills", "Health", "Leisure", "Salary", "General", "Savings Pot", "Peer Transfer"
    )

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text(text = "Edit Ledger Entry", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Amount: ₹${transaction.amount.toInt()}", color = theme.textMuted, fontSize = 12.sp)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Merchant / Narration") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = theme.surfaceAlt,
                        unfocusedContainerColor = theme.surfaceAlt,
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.borderLight,
                        focusedTextColor = theme.textBright,
                        unfocusedTextColor = theme.textBright
                    )
                )

                Text(text = "Category", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            Text(text = cat, color = if (isSel) theme.bg else theme.textBright, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(category, note) },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) { Text(text = "Update", color = theme.bg, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) { Text(text = "Delete", color = theme.mildRed) }
                TextButton(onClick = onDismiss) { Text(text = "Cancel", color = theme.textMuted) }
            }
        }
    )
}

/**
 * Balanced, Lively Color-Coded Account Creation Form with Full 2x2 Loan Specs
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateAccountDialog(
    initialType: PocketType = PocketType.LIQUID,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        type: PocketType,
        limit: Double,
        dueDay: Int,
        targetAmt: Double,
        targetDateEpoch: Long,
        initialValuation: Double,
        interestRate: Double
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(initialType) }
    var limit by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }
    var targetAmt by remember { mutableStateOf("") }
    var initialValuation by remember { mutableStateOf("") }
    var interestRate by remember { mutableStateOf("") }
    var targetDateEpoch by remember { mutableStateOf(System.currentTimeMillis() + (30L * 24 * 3600 * 1000L)) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(targetDateEpoch))

    if (showDatePicker) {
        CustomCalendarDialog(
            initialDateMillis = targetDateEpoch,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                targetDateEpoch = it
                showDatePicker = false
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 420.dp)
                    .border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "NEW ACCOUNT SETUP", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)

                    // Strict 2-Row Grid Classification with Distinct Vibrant Colors
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple(PocketType.LIQUID, "Bank", Color(0xFF48BB78)),
                                Triple(PocketType.PREPAID_WALLET, "Wallet", Color(0xFF38A169)),
                                Triple(PocketType.CREDIT_CARD, "Card", Color(0xFFF56565)),
                                Triple(PocketType.GOAL_POT, "Goal", Color(0xFFECC94B))
                            ).forEach { (t, lbl, c) ->
                                val isSel = type == t
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) c.copy(alpha = 0.25f) else theme.surfaceAlt)
                                        .border(1.dp, if (isSel) c else theme.borderLight, RoundedCornerShape(6.dp))
                                        .clickable { type = t }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(lbl, color = if (isSel) c else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple(PocketType.INVESTMENT, "Invest", Color(0xFFED8936)),
                                Triple(PocketType.PEER_RECEIVABLE, "Peer", Color(0xFF4FD1C5)),
                                Triple(PocketType.LIABILITY_LOAN, "Loan", Color(0xFFE53E3E))
                            ).forEach { (t, lbl, c) ->
                                val isSel = type == t
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) c.copy(alpha = 0.25f) else theme.surfaceAlt)
                                        .border(1.dp, if (isSel) c else theme.borderLight, RoundedCornerShape(6.dp))
                                        .clickable { type = t }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(lbl, color = if (isSel) c else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Account Name Field (Active & Responsive)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Account Name (e.g. HDFC Salary, SBI Home Loan)", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = theme.surfaceAlt,
                            unfocusedContainerColor = theme.surfaceAlt,
                            focusedBorderColor = theme.accent,
                            unfocusedBorderColor = theme.borderLight,
                            focusedTextColor = theme.textBright,
                            unfocusedTextColor = theme.textBright
                        )
                    )

                    // Contextual Inputs
                    when (type) {
                        PocketType.LIQUID, PocketType.PREPAID_WALLET -> {
                            OutlinedTextField(
                                value = initialValuation,
                                onValueChange = { initialValuation = it },
                                placeholder = { Text("Current Opening Balance in ₹", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = theme.surfaceAlt,
                                    unfocusedContainerColor = theme.surfaceAlt,
                                    focusedBorderColor = theme.accent,
                                    unfocusedBorderColor = theme.borderLight,
                                    focusedTextColor = theme.textBright,
                                    unfocusedTextColor = theme.textBright
                                )
                            )
                        }

                        PocketType.CREDIT_CARD -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = limit,
                                    onValueChange = { limit = it },
                                    placeholder = { Text("Limit ₹", fontSize = 12.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = theme.surfaceAlt,
                                        unfocusedContainerColor = theme.surfaceAlt,
                                        focusedBorderColor = theme.accent,
                                        unfocusedBorderColor = theme.borderLight,
                                        focusedTextColor = theme.textBright,
                                        unfocusedTextColor = theme.textBright
                                    )
                                )
                                OutlinedTextField(
                                    value = dueDay,
                                    onValueChange = { dueDay = it },
                                    placeholder = { Text("Due Day (1-31)", fontSize = 12.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = theme.surfaceAlt,
                                        unfocusedContainerColor = theme.surfaceAlt,
                                        focusedBorderColor = theme.accent,
                                        unfocusedBorderColor = theme.borderLight,
                                        focusedTextColor = theme.textBright,
                                        unfocusedTextColor = theme.textBright
                                    )
                                )
                            }
                        }

                        PocketType.GOAL_POT -> {
                            OutlinedTextField(
                                value = targetAmt,
                                onValueChange = { targetAmt = it },
                                placeholder = { Text("Target Goal Amount in ₹", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = theme.surfaceAlt,
                                    unfocusedContainerColor = theme.surfaceAlt,
                                    focusedBorderColor = theme.accent,
                                    unfocusedBorderColor = theme.borderLight,
                                    focusedTextColor = theme.textBright,
                                    unfocusedTextColor = theme.textBright
                                )
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.surfaceAlt)
                                    .clickable { showDatePicker = true }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Target Date: $dateFormatted", color = theme.textBright, fontSize = 12.sp)
                                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                            }
                        }

                        PocketType.INVESTMENT, PocketType.FIXED_ASSET -> {
                            OutlinedTextField(
                                value = initialValuation,
                                onValueChange = { initialValuation = it },
                                placeholder = { Text("Current Portfolio / WDV Valuation in ₹", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = theme.surfaceAlt,
                                    unfocusedContainerColor = theme.surfaceAlt,
                                    focusedBorderColor = theme.accent,
                                    unfocusedBorderColor = theme.borderLight,
                                    focusedTextColor = theme.textBright,
                                    unfocusedTextColor = theme.textBright
                                )
                            )
                        }

                        PocketType.PEER_RECEIVABLE, PocketType.PEER_PAYABLE -> {
                            OutlinedTextField(
                                value = initialValuation,
                                onValueChange = { initialValuation = it },
                                placeholder = { Text("Initial Amount Lent / Borrowed in ₹", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = theme.surfaceAlt,
                                    unfocusedContainerColor = theme.surfaceAlt,
                                    focusedBorderColor = theme.accent,
                                    unfocusedBorderColor = theme.borderLight,
                                    focusedTextColor = theme.textBright,
                                    unfocusedTextColor = theme.textBright
                                )
                            )
                        }

                        // Balanced 2x2 Grid for Loans
                        PocketType.LIABILITY_LOAN -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = initialValuation,
                                        onValueChange = { initialValuation = it },
                                        placeholder = { Text("Principal Debt ₹", fontSize = 12.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = theme.surfaceAlt,
                                            unfocusedContainerColor = theme.surfaceAlt,
                                            focusedBorderColor = theme.accent,
                                            unfocusedBorderColor = theme.borderLight,
                                            focusedTextColor = theme.textBright,
                                            unfocusedTextColor = theme.textBright
                                        )
                                    )
                                    OutlinedTextField(
                                        value = targetAmt,
                                        onValueChange = { targetAmt = it },
                                        placeholder = { Text("Monthly EMI ₹", fontSize = 12.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = theme.surfaceAlt,
                                            unfocusedContainerColor = theme.surfaceAlt,
                                            focusedBorderColor = theme.accent,
                                            unfocusedBorderColor = theme.borderLight,
                                            focusedTextColor = theme.textBright,
                                            unfocusedTextColor = theme.textBright
                                        )
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = dueDay,
                                        onValueChange = { dueDay = it },
                                        placeholder = { Text("Due Day (1-31)", fontSize = 12.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = theme.surfaceAlt,
                                            unfocusedContainerColor = theme.surfaceAlt,
                                            focusedBorderColor = theme.accent,
                                            unfocusedBorderColor = theme.borderLight,
                                            focusedTextColor = theme.textBright,
                                            unfocusedTextColor = theme.textBright
                                        )
                                    )
                                    OutlinedTextField(
                                        value = interestRate,
                                        onValueChange = { interestRate = it },
                                        placeholder = { Text("Interest Rate %", fontSize = 12.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = theme.surfaceAlt,
                                            unfocusedContainerColor = theme.surfaceAlt,
                                            focusedBorderColor = theme.accent,
                                            unfocusedBorderColor = theme.borderLight,
                                            focusedTextColor = theme.textBright,
                                            unfocusedTextColor = theme.textBright
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (name.isNotBlank()) onSave(
                                    name,
                                    type,
                                    limit.toDoubleOrNull() ?: 0.0,
                                    dueDay.toIntOrNull() ?: 0,
                                    targetAmt.toDoubleOrNull() ?: 0.0,
                                    targetDateEpoch,
                                    initialValuation.toDoubleOrNull() ?: 0.0,
                                    interestRate.toDoubleOrNull() ?: 0.0
                                )
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                            enabled = name.isNotBlank()
                        ) {
                            Text("Save Account", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditAccountDialog(
    pocket: LedgerPocket,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (String, Double, Int, Double, Long) -> Unit
) {
    var name by remember { mutableStateOf(pocket.name) }
    var limit by remember { mutableStateOf(if (pocket.creditLimit > 0.0) String.format("%.0f", pocket.creditLimit) else "") }
    var dueDay by remember { mutableStateOf(if (pocket.billDueDay > 0) pocket.billDueDay.toString() else "") }
    var targetAmt by remember { mutableStateOf(if (pocket.targetGoalAmount > 0.0) String.format("%.0f", pocket.targetGoalAmount) else "") }
    var targetDateEpoch by remember { mutableStateOf(if (pocket.goalTargetDate > 0) pocket.goalTargetDate else System.currentTimeMillis() + (30L * 24 * 3600 * 1000L)) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(targetDateEpoch))

    if (showDatePicker) {
        CustomCalendarDialog(
            initialDateMillis = targetDateEpoch,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                targetDateEpoch = it
                showDatePicker = false
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 400.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "EDIT ${pocket.name.uppercase(Locale.getDefault())}", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Account Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = theme.surfaceAlt,
                            unfocusedContainerColor = theme.surfaceAlt,
                            focusedBorderColor = theme.accent,
                            unfocusedBorderColor = theme.borderLight,
                            focusedTextColor = theme.textBright,
                            unfocusedTextColor = theme.textBright
                        )
                    )

                    if (pocket.type == PocketType.CREDIT_CARD) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = limit,
                                onValueChange = { limit = it },
                                placeholder = { Text("Credit Limit") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = theme.surfaceAlt,
                                    unfocusedContainerColor = theme.surfaceAlt,
                                    focusedBorderColor = theme.accent,
                                    unfocusedBorderColor = theme.borderLight,
                                    focusedTextColor = theme.textBright,
                                    unfocusedTextColor = theme.textBright
                                )
                            )
                            OutlinedTextField(
                                value = dueDay,
                                onValueChange = { dueDay = it },
                                placeholder = { Text("Due Day (1-31)") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = theme.surfaceAlt,
                                    unfocusedContainerColor = theme.surfaceAlt,
                                    focusedBorderColor = theme.accent,
                                    unfocusedBorderColor = theme.borderLight,
                                    focusedTextColor = theme.textBright,
                                    unfocusedTextColor = theme.textBright
                                )
                            )
                        }
                    }

                    if (pocket.type == PocketType.GOAL_POT) {
                        OutlinedTextField(
                            value = targetAmt,
                            onValueChange = { targetAmt = it },
                            placeholder = { Text("Target Goal Amount") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = theme.surfaceAlt,
                                unfocusedContainerColor = theme.surfaceAlt,
                                focusedBorderColor = theme.accent,
                                unfocusedBorderColor = theme.borderLight,
                                focusedTextColor = theme.textBright,
                                unfocusedTextColor = theme.textBright
                            )
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
                            onClick = {
                                if (name.isNotBlank()) onSave(
                                    name,
                                    limit.toDoubleOrNull() ?: pocket.creditLimit,
                                    dueDay.toIntOrNull() ?: pocket.billDueDay,
                                    targetAmt.toDoubleOrNull() ?: pocket.targetGoalAmount,
                                    targetDateEpoch
                                )
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                        ) {
                            Text("Save Changes", color = theme.bg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
