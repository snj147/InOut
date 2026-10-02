package com.personal.inout.ui

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.personal.inout.BuildConfig
import com.personal.inout.billing.PlayBillingManager
import com.personal.inout.data.*
import com.personal.inout.ocr.ReceiptScanner
import com.personal.inout.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

enum class WizardType {
    EXPENSE,
    INFLOW,
    TRANSFER,
    CARD_BILL,
    PEER_LEND_BORROW
}

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
        if (buildSha.isNotBlank() && buildSha != "localdev") buildSha else "98f9b7f"
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

    var activeWizard by remember { mutableStateOf<WizardType?>(null) }
    var showUnifiedEntrySheet by remember { mutableStateOf(false) }
    var selectedPocketIdForEntry by remember { mutableStateOf<Long?>(null) }

    var editingPocket by remember { mutableStateOf<LedgerPocket?>(null) }
    var editingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingRecurringRule by remember { mutableStateOf<LedgerTransaction?>(null) }
    var transactionPendingDeletion by remember { mutableStateOf<LedgerTransaction?>(null) }

    var showCreatePocketDialog by remember { mutableStateOf(false) }
    var prefilledCreatePocketType by remember { mutableStateOf(PocketType.LIQUID) }

    var showAllRecordsSheet by remember { mutableStateOf(false) }
    var showMockPaywall by remember { mutableStateOf(false) }
    var showBurnEditDialog by remember { mutableStateOf(false) }
    var showClearLedgerConfirmation by remember { mutableStateOf(false) }
    var showNoticesSheet by remember { mutableStateOf(false) }

    val allTransactionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val noticesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        onOpenNotices = { showNoticesSheet = true }
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
                                        Icon(imageVector = Icons.Default.EditNote, contentDescription = "Unified Entry", modifier = Modifier.size(22.dp))
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
                                                    heroText = if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", solvencyDeck.trueSafeLiquid)}",
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
                                                    heroText = if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", solvencyDeck.projectedClosingLiquid)}",
                                                    leftSub = "Deterministic Commitments",
                                                    rightSub = "Fixed Outflows Factored",
                                                    theme = theme,
                                                    onCardClick = {}
                                                )
                                                3 -> MetricCarouselCard(
                                                    tag = "STATUTORY NET WORTH",
                                                    status = if (solvencyDeck.totalNetWorth >= 0) "Positive" else "Insolvent",
                                                    isPositive = solvencyDeck.totalNetWorth >= 0,
                                                    heroText = if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", solvencyDeck.totalNetWorth)}",
                                                    leftSub = "Assets Less External Liabilities",
                                                    rightSub = "ICAI Balance Sheet",
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
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                                        modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "QUICK ACTIONS",
                                                    color = theme.textMuted,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.sp
                                                )
                                                Text(
                                                    text = "Instant Movement",
                                                    color = theme.accent,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            LazyRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                item {
                                                    ActionPillButton(
                                                        title = "- Expense",
                                                        icon = Icons.Default.TrendingDown,
                                                        color = theme.mildRed,
                                                        onClick = { activeWizard = WizardType.EXPENSE }
                                                    )
                                                }
                                                item {
                                                    ActionPillButton(
                                                        title = "+ Inflow",
                                                        icon = Icons.Default.TrendingUp,
                                                        color = theme.mildGreen,
                                                        onClick = { activeWizard = WizardType.INFLOW }
                                                    )
                                                }
                                                item {
                                                    ActionPillButton(
                                                        title = "⇄ Transfer",
                                                        icon = Icons.Default.SwapHoriz,
                                                        color = theme.accent,
                                                        onClick = { activeWizard = WizardType.TRANSFER }
                                                    )
                                                }
                                                item {
                                                    ActionPillButton(
                                                        title = "💳 Pay Card Bill",
                                                        icon = Icons.Default.CreditCard,
                                                        color = theme.accent,
                                                        onClick = { activeWizard = WizardType.CARD_BILL }
                                                    )
                                                }
                                                item {
                                                    ActionPillButton(
                                                        title = "👥 Lend / Borrow",
                                                        icon = Icons.Default.People,
                                                        color = theme.accent,
                                                        onClick = { activeWizard = WizardType.PEER_LEND_BORROW }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
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
                                                .padding(vertical = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No records logged yet. Tap an action above to start.",
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
                            autoSplitEnabled = autoSplitEnabled,
                            activeThemeMode = activeThemeMode,
                            isProUnlocked = isProUnlocked,
                            availableUpdate = availableUpdateInfo,
                            isCheckingUpdate = isCheckingForUpdate,
                            downloadState = downloadState,
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
                                AppUpdateEngine.triggerPackageInstaller(context, apkFile)
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
                            onClearLedger = { showClearLedgerConfirmation = true },
                            onOpenPaywall = { showMockPaywall = true }
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

                activeWizard?.let { wizard ->
                    GuidedActionWizardDialog(
                        type = wizard,
                        rawPockets = rawPockets,
                        pocketBalances = pocketBalances,
                        theme = theme,
                        onDismiss = { activeWizard = null },
                        onRequestNewAccount = { reqType ->
                            prefilledCreatePocketType = reqType
                            showCreatePocketDialog = true
                        },
                        onCommit = { nature, srcId, tgtId, amt, cat, note, date, isRec, freq ->
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
                                    recurringFrequency = freq
                                )) {
                                    is VaultExecutionResult.OverdraftError -> alertManager.showAlert(res.message, AlertType.ERROR)
                                    is VaultExecutionResult.DuplicateWarning -> alertManager.showAlert(res.message, AlertType.WARNING)
                                    is VaultExecutionResult.Success -> {
                                        alertManager.showAlert(res.summary, AlertType.SUCCESS)
                                        activeWizard = null
                                    }
                                }
                            }
                        }
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

                if (showNoticesSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showNoticesSheet = false },
                        sheetState = noticesSheetState,
                        containerColor = theme.surface,
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "System & Solvency Notices",
                                    color = theme.textBright,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (unreadNotices.isNotEmpty()) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            for (notice in unreadNotices) {
                                                db.ledgerDao().markNoticeAsRead(notice.id)
                                            }
                                        }
                                    }) {
                                        Text(text = "Mark All Read", color = theme.accent, fontSize = 12.sp)
                                    }
                                }
                            }

                            if (unreadNotices.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "No pending notifications.", color = theme.textMuted, fontSize = 13.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 32.dp)
                                ) {
                                    items(unreadNotices, key = { it.id }) { notice ->
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = theme.surfaceAlt),
                                            modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(10.dp))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = notice.title, color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(notice.timestamp))
                                                    Text(text = timeStr, color = theme.textMuted, fontSize = 10.5.sp)
                                                }
                                                Text(text = notice.message, color = theme.textBright, fontSize = 12.5.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                                Text(text = "Configure your baseline daily operational burn ceiling.", color = theme.textMuted, fontSize = 12.sp)
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
                    var verificationInput by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = {
                            showClearLedgerConfirmation = false
                            verificationInput = ""
                        },
                        containerColor = theme.surface,
                        title = { Text(text = "CONFIRM TOTAL PURGE", color = theme.mildRed, fontWeight = FontWeight.Black) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "This will permanently purge all transactions and archive all accounts. To proceed, type PURGE below:",
                                    color = theme.textMuted,
                                    fontSize = 12.5.sp
                                )
                                OutlinedTextField(
                                    value = verificationInput,
                                    onValueChange = { verificationInput = it },
                                    placeholder = { Text("PURGE") },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = theme.surfaceAlt,
                                        unfocusedContainerColor = theme.surfaceAlt,
                                        focusedBorderColor = theme.mildRed,
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
                                    if (verificationInput.trim() == "PURGE") {
                                        scope.launch {
                                            completedTransactions.forEach { db.ledgerDao().deleteTransaction(it) }
                                            rawPockets.forEach { db.ledgerDao().updatePocket(it.copy(isArchived = true)) }
                                            alertManager.showAlert("All vault records purged", AlertType.SUCCESS)
                                            showClearLedgerConfirmation = false
                                            verificationInput = ""
                                        }
                                    } else {
                                        alertManager.showAlert("Phrase must match 'PURGE'", AlertType.ERROR)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed),
                                enabled = verificationInput.trim() == "PURGE"
                            ) { Text(text = "Purge Everything", color = Color.White, fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showClearLedgerConfirmation = false
                                verificationInput = ""
                            }) { Text(text = "Cancel", color = theme.textMuted) }
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
                        sheetState = allTransactionsSheetState,
                        onDismiss = { showAllRecordsSheet = false },
                        onEditRecord = { tx -> editingTransaction = tx },
                        onExportCsv = { CsvExporter.exportAndShareTransactions(context, completedTransactions) },
                        onExportPdfDossier = {
                            scope.launch { PdfDossierExporter.generateAndShareDossier(context, pocketBalances, completedTransactions) }
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
fun ActionPillButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(16.dp))
        Text(text = title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidedActionWizardDialog(
    type: WizardType,
    rawPockets: List<LedgerPocket>,
    pocketBalances: Map<Long, Double>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onRequestNewAccount: (PocketType) -> Unit,
    onCommit: (
        nature: MovementNature,
        sourcePocketId: Long,
        targetPocketId: Long?,
        amount: Double,
        category: String,
        note: String,
        timestamp: Long,
        isRecurring: Boolean,
        frequency: String
    ) -> Unit
) {
    var rawAmount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("General") }

    val liquidPockets = remember(rawPockets) { rawPockets.filter { it.type == PocketType.LIQUID || it.type == PocketType.PREPAID_WALLET } }
    val cardPockets = remember(rawPockets) { rawPockets.filter { it.type == PocketType.CREDIT_CARD } }
    val peerPockets = remember(rawPockets) { rawPockets.filter { it.type == PocketType.PEER_RECEIVABLE || it.type == PocketType.PEER_PAYABLE } }

    var selectedSourceId by remember { mutableStateOf(liquidPockets.firstOrNull()?.id) }
    var selectedTargetId by remember {
        mutableStateOf(
            when (type) {
                WizardType.INFLOW -> liquidPockets.firstOrNull()?.id
                WizardType.TRANSFER -> rawPockets.firstOrNull { it.id != (liquidPockets.firstOrNull()?.id ?: -1L) }?.id
                WizardType.CARD_BILL -> cardPockets.firstOrNull()?.id
                WizardType.PEER_LEND_BORROW -> peerPockets.firstOrNull()?.id
                else -> null
            }
        )
    }

    var peerModeIsLend by remember { mutableStateOf(true) }
    var isRecurring by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf("MONTHLY") }

    var selectedDateEpoch by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedDatePreset by remember { mutableStateOf("TODAY") }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val computedAmount = remember(rawAmount) { MathEvaluator.evaluate(rawAmount) }

    val categories = when (type) {
        WizardType.EXPENSE -> listOf("Food & Dining", "Groceries", "Transport", "Bills", "Shopping", "Health", "General")
        WizardType.INFLOW -> listOf("Salary", "Investment", "Freelance", "Refund", "Income")
        WizardType.TRANSFER -> listOf("Internal Transfer", "Savings Pot")
        WizardType.CARD_BILL -> listOf("Card Payment", "Bill Payment")
        WizardType.PEER_LEND_BORROW -> listOf("Peer Debt", "Personal Loan")
    }

    if (showDatePickerDialog) {
        CustomCalendarDialog(
            initialDateMillis = selectedDateEpoch,
            onDismiss = { showDatePickerDialog = false },
            onDateSelected = { pickedMillis ->
                selectedDateEpoch = pickedMillis
                selectedDatePreset = "CUSTOM"
                showDatePickerDialog = false
            }
        )
    }

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (type) {
                    WizardType.EXPENSE -> "Record Expense Outflow"
                    WizardType.INFLOW -> "Record Inflow Receipt"
                    WizardType.TRANSFER -> "Internal Transfer"
                    WizardType.CARD_BILL -> "Pay Credit Card Liability"
                    WizardType.PEER_LEND_BORROW -> if (peerModeIsLend) "Lend Money to Contact" else "Borrow Money from Contact"
                },
                color = theme.textBright,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (type == WizardType.PEER_LEND_BORROW) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (peerModeIsLend) theme.accent else theme.surfaceAlt)
                                .clickable { peerModeIsLend = true }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("I Gave (Lent)", color = if (peerModeIsLend) theme.bg else theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!peerModeIsLend) theme.accent else theme.surfaceAlt)
                                .clickable { peerModeIsLend = false }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("I Took (Borrowed)", color = if (!peerModeIsLend) theme.bg else theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Column {
                    OutlinedTextField(
                        value = rawAmount,
                        onValueChange = { rawAmount = it },
                        placeholder = { Text("Amount in ₹ (e.g. 150+40)") },
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
                    if (computedAmount != null && rawAmount.contains("+")) {
                        Text("Evaluated: ₹$computedAmount", color = theme.accent, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }

                Text("Transaction Date", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.surfaceAlt)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val dateFormatted = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(selectedDateEpoch))
                    listOf(
                        "TODAY" to "Today",
                        "YESTERDAY" to "Yesterday",
                        "CUSTOM" to if (selectedDatePreset == "CUSTOM") dateFormatted else "Pick Date"
                    ).forEach { (presetKey, presetLabel) ->
                        val isSel = selectedDatePreset == presetKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else Color.Transparent)
                                .clickable {
                                    when (presetKey) {
                                        "TODAY" -> {
                                            selectedDatePreset = "TODAY"
                                            selectedDateEpoch = System.currentTimeMillis()
                                        }
                                        "YESTERDAY" -> {
                                            selectedDatePreset = "YESTERDAY"
                                            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                                            selectedDateEpoch = cal.timeInMillis
                                        }
                                        "CUSTOM" -> {
                                            showDatePickerDialog = true
                                        }
                                    }
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = presetLabel,
                                color = if (isSel) theme.bg else theme.textBright,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                val activeSrc = liquidPockets.firstOrNull { it.id == selectedSourceId } ?: liquidPockets.firstOrNull()
                val srcBal = pocketBalances[activeSrc?.id ?: 0L] ?: 0.0

                AccountCardSelector(
                    title = activeSrc?.name ?: "No Bank Account Found",
                    sub = if (activeSrc == null) "+ Add bank account" else "Available: ₹${String.format("%,.0f", srcBal.coerceAtLeast(0.0))}",
                    theme = theme,
                    accounts = liquidPockets.map { p ->
                        val b = pocketBalances[p.id] ?: 0.0
                        Triple(p.id, p.name, "₹${String.format("%,.0f", b.coerceAtLeast(0.0))}")
                    },
                    onSelect = { selectedSourceId = it },
                    onAdd = { onRequestNewAccount(PocketType.LIQUID) }
                )

                Text("Category", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSel = selectedCategoryId == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else theme.surfaceAlt)
                                .clickable { selectedCategoryId = cat }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(cat, color = if (isSel) theme.bg else theme.textBright, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = computedAmount ?: return@Button
                    val srcId = selectedSourceId ?: return@Button

                    val finalNature = when (type) {
                        WizardType.EXPENSE -> MovementNature.OPERATING_EXPENSE
                        WizardType.INFLOW -> MovementNature.OPERATING_INCOME
                        WizardType.TRANSFER -> MovementNature.TRANSFER
                        WizardType.CARD_BILL -> MovementNature.OPERATING_EXPENSE
                        WizardType.PEER_LEND_BORROW -> MovementNature.TRANSFER
                    }

                    onCommit(
                        finalNature,
                        srcId,
                        selectedTargetId,
                        amt,
                        selectedCategoryId,
                        note.ifBlank { selectedCategoryId },
                        selectedDateEpoch,
                        isRecurring,
                        frequency
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                enabled = computedAmount != null && computedAmount > 0.0 && selectedSourceId != null
            ) {
                Text("Commit to Ledger", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textMuted)
            }
        }
    )
}

@Composable
fun AccountCardSelector(
    title: String,
    sub: String,
    theme: ThemeColors,
    accounts: List<Triple<Long, String, String>>,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(theme.surfaceAlt)
                .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = title, color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(text = sub, color = theme.textMuted, fontSize = 10.5.sp)
            }
            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Select", tint = theme.accent)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(theme.surface)
        ) {
            accounts.forEach { (id, name, balanceStr) ->
                DropdownMenuItem(
                    text = {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, color = theme.textBright, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(16.dp))
                            Text(balanceStr, color = theme.textMuted)
                        }
                    },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
            HorizontalDivider(color = theme.borderLight)
            DropdownMenuItem(
                text = { Text("+ Add New Account", color = theme.accent, fontWeight = FontWeight.Bold) },
                onClick = {
                    expanded = false
                    onAdd()
                }
            )
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

        val amtStr = if (isPrivacyMode) "₹ •••" else "${if (isOut) "-" else "+"}₹${String.format("%,.0f", tx.amount)}"
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
private fun SettingsCardsList(
    theme: ThemeColors,
    currentSha: String,
    lastUpdatedDate: String,
    autoSplitEnabled: Boolean,
    activeThemeMode: AppThemeMode,
    isProUnlocked: Boolean,
    availableUpdate: UpdateInfo?,
    isCheckingUpdate: Boolean,
    downloadState: UpdateDownloadState,
    onCheckUpdate: () -> Unit,
    onStartStreamDownload: (UpdateInfo) -> Unit,
    onInstallDownloadedApk: (java.io.File) -> Unit,
    onOpenFeedback: () -> Unit,
    onAutoSplitToggled: (Boolean) -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onExportEncryptedBackup: () -> Unit,
    onRestoreEncryptedBackup: () -> Unit,
    onClearLedger: () -> Unit,
    onOpenPaywall: () -> Unit
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
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "ABOUT INOUT", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Column {
                        Text(text = "InOut Vault", color = theme.textBright, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Build SHA: ${currentSha.take(7)}", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Installed: $lastUpdatedDate", color = theme.textMuted, fontSize = 10.5.sp)
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
                    Text(text = "APPLICATION UPDATES & SUPPORT", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    when (val state = downloadState) {
                        is UpdateDownloadState.Idle -> {
                            if (availableUpdate != null && availableUpdate.hasUpdate) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.accent.copy(alpha = 0.15f))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(text = "New Build Available: ${availableUpdate.latestVersion}", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(text = availableUpdate.releaseNotes, color = theme.textBright, fontSize = 10.5.sp, maxLines = 2)
                                    }
                                }

                                Button(
                                    onClick = { onStartStreamDownload(availableUpdate) },
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = theme.bg, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(text = "Download Build ${availableUpdate.latestVersion}", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = onCheckUpdate,
                                    enabled = !isCheckingUpdate,
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = theme.textBright, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(text = if (isCheckingUpdate) "Checking Alpha Releases..." else "Check for App Update", color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        is UpdateDownloadState.Downloading -> {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val percent = (state.progress * 100).toInt()
                                val mbDownloaded = String.format("%.1f", state.bytesDownloaded / (1024f * 1024f))
                                val mbTotal = String.format("%.1f", state.totalBytes / (1024f * 1024f))

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Streaming Build: $percent%", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("$mbDownloaded MB / $mbTotal MB", color = theme.textMuted, fontSize = 11.sp)
                                }
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = theme.accent,
                                    trackColor = theme.surfaceAlt
                                )
                            }
                        }

                        is UpdateDownloadState.ReadyToInstall -> {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.mildGreen.copy(alpha = 0.15f))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text("Package Downloaded (Build ${state.versionTag})", color = theme.mildGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Tap below to invoke the system package installer.", color = theme.textBright, fontSize = 11.sp)
                                    }
                                }

                                Button(
                                    onClick = { onInstallDownloadedApk(state.apkFile) },
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.mildGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.InstallMobile, contentDescription = null, tint = theme.bg, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Install Update Now", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        is UpdateDownloadState.Error -> {
                            Text(state.message, color = theme.mildRed, fontSize = 11.5.sp)
                            Button(
                                onClick = onCheckUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Retry Update Check", color = theme.textBright, fontSize = 12.sp)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenFeedback,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)
                    ) {
                        Icon(imageVector = Icons.Default.BugReport, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Send Feedback / Bug Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "AUTOMATION PREFERENCES", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

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
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "WORKSPACE PALETTE", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "DATA PORTABILITY & EXPORT", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

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

                    Button(
                        onClick = onClearLedger,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Clear Entire Ledger History", color = theme.mildRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        Text(text = "Cadence Execution Anchor", color = theme.textMuted, fontSize = 10.sp)
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

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text(text = "Add Account / Asset / Liability", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Account Name / Contact") },
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

                Text(text = "Account Classification", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        PocketType.LIQUID to "Bank / Cash",
                        PocketType.PREPAID_WALLET to "Prepaid Wallet",
                        PocketType.CREDIT_CARD to "Credit Card",
                        PocketType.INVESTMENT to "Portfolio",
                        PocketType.FIXED_ASSET to "Fixed Asset",
                        PocketType.LIABILITY_LOAN to "Loan / EMI",
                        PocketType.PEER_RECEIVABLE to "Lent (Debtor)",
                        PocketType.PEER_PAYABLE to "Borrowed (Creditor)",
                        PocketType.GOAL_POT to "Goal Pot"
                    ).forEach { (t, lbl) ->
                        val isSel = type == t
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else theme.surfaceAlt)
                                .clickable { type = t }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = lbl, color = if (isSel) theme.bg else theme.textBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (type == PocketType.LIQUID || type == PocketType.PREPAID_WALLET) {
                    OutlinedTextField(
                        value = initialValuation,
                        onValueChange = { initialValuation = it },
                        placeholder = { Text("Current Opening Balance in ₹") },
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

                if (type == PocketType.CREDIT_CARD) {
                    OutlinedTextField(
                        value = limit,
                        onValueChange = { limit = it },
                        placeholder = { Text("Total Credit Limit in ₹ (e.g. 50000)") },
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
                    OutlinedTextField(
                        value = dueDay,
                        onValueChange = { dueDay = it },
                        placeholder = { Text("Bill Due Day of Month (1-31)") },
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

                if (type == PocketType.FIXED_ASSET || type == PocketType.INVESTMENT) {
                    OutlinedTextField(
                        value = initialValuation,
                        onValueChange = { initialValuation = it },
                        placeholder = { Text(if (type == PocketType.FIXED_ASSET) "Acquisition / Current WDV Value in ₹" else "Portfolio Initial Investment in ₹") },
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

                if (type == PocketType.LIABILITY_LOAN) {
                    OutlinedTextField(
                        value = initialValuation,
                        onValueChange = { initialValuation = it },
                        placeholder = { Text("Remaining Principal Outstanding in ₹") },
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
                    OutlinedTextField(
                        value = interestRate,
                        onValueChange = { interestRate = it },
                        placeholder = { Text("Annual Interest Rate % (Optional)") },
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

                if (type == PocketType.PEER_RECEIVABLE || type == PocketType.PEER_PAYABLE) {
                    OutlinedTextField(
                        value = initialValuation,
                        onValueChange = { initialValuation = it },
                        placeholder = { Text(if (type == PocketType.PEER_RECEIVABLE) "Initial Amount Lent in ₹" else "Initial Amount Borrowed in ₹") },
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

                if (type == PocketType.GOAL_POT) {
                    OutlinedTextField(
                        value = targetAmt,
                        onValueChange = { targetAmt = it },
                        placeholder = { Text("Target Goal Amount in ₹ (e.g. 60000)") },
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Target Date: $dateFormatted", color = theme.textBright, fontSize = 11.5.sp)
                        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
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
                        dueDay.toIntOrNull() ?: 0,
                        targetAmt.toDoubleOrNull() ?: 0.0,
                        targetDateEpoch,
                        initialValuation.toDoubleOrNull() ?: 0.0,
                        interestRate.toDoubleOrNull() ?: 0.0
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) { Text(text = "Save", color = theme.bg, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel", color = theme.textMuted) } }
    )
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

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Edit ${pocket.type.name.replace("_", " ")} Details", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Name") },
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

                if (pocket.type == PocketType.CREDIT_CARD) {
                    OutlinedTextField(
                        value = limit,
                        onValueChange = { limit = it },
                        placeholder = { Text("Credit Limit") },
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
                        value = dueDay,
                        onValueChange = { dueDay = it },
                        placeholder = { Text("Bill Due Day (1-31)") },
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

                if (pocket.type == PocketType.GOAL_POT) {
                    OutlinedTextField(
                        value = targetAmt,
                        onValueChange = { targetAmt = it },
                        placeholder = { Text("Target Goal Amount") },
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Target Date: $dateFormatted", color = theme.textBright, fontSize = 11.5.sp)
                        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                    }
                }
            }
        },
        confirmButton = {
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
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) { Text(text = "Save Changes", color = theme.bg, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel", color = theme.textMuted) } }
    )
}
