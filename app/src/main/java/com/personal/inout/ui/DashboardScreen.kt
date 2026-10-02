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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (_: Exception) { null }
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
                    AlertDialog(
                        containerColor = theme.surface,
                        onDismissRequest = { editingRecurringRule = null },
                        title = { Text(text = "Edit Recurring Rule", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                        text = {
                            Text("Editing ${rule.description} (₹${rule.amount.toInt()})", color = theme.textMuted)
                        },
                        confirmButton = {
                            Button(
                                onClick = { editingRecurringRule = null },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                            ) { Text("Done", color = theme.bg) }
                        }
                    )
                }

                editingTransaction?.let { tx ->
                    AlertDialog(
                        containerColor = theme.surface,
                        onDismissRequest = { editingTransaction = null },
                        title = { Text(text = "Edit Ledger Entry", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Transaction: ${tx.description}", color = theme.textBright)
                                Text("Amount: ₹${tx.amount.toInt()}", color = theme.accent)
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { editingTransaction = null },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                            ) { Text("Done", color = theme.bg) }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                transactionPendingDeletion = tx
                                editingTransaction = null
                            }) { Text("Delete", color = theme.mildRed) }
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
