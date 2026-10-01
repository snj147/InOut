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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class WizardType {
    EXPENSE,
    INCOME,
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
    val ledgerEngine = remember { VaultLedgerEngine(db.stateFlowDao(), prefs) }

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
        val saved = prefs.getString("selected_theme", AppThemeMode.AMBER_OCHRE.name)
        mutableStateOf(AppThemeMode.valueOf(saved ?: AppThemeMode.AMBER_OCHRE.name))
    }

    var dailyBurnCeiling by remember {
        mutableStateOf(prefs.getFloat("daily_burn_ceiling", 450f).toDouble())
    }

    var autoSplitEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_split_debit", false))
    }

    var phantomLockEnabled by remember {
        mutableStateOf(prefs.getBoolean("phantom_lock_enabled", true))
    }

    val theme = when (activeThemeMode) {
        AppThemeMode.AMBER_OCHRE -> AmberTheme
        AppThemeMode.OLIVE_MATCHA -> OliveMatchaTheme
        AppThemeMode.NORDIC_SLATE -> NordicSlateTheme
    }

    val pocketBalances by db.stateFlowDao().observePocketBalances().collectAsState(initial = emptyList())
    val rawPockets by db.stateFlowDao().observeAllActivePockets().collectAsState(initial = emptyList())
    val flowRecords by db.stateFlowDao().observeAllFlowRecords().collectAsState(initial = emptyList())
    val unreadNoticeCount by db.stateFlowDao().observeUnreadNoticeCount().collectAsState(initial = 0)
    val systemNotices by db.stateFlowDao().observeAllNotices().collectAsState(initial = emptyList())

    val completedTransactions = remember(flowRecords) {
        flowRecords.filter { !it.isRecurring }
    }

    val recurringTemplates = remember(flowRecords) {
        flowRecords.filter { it.isRecurring && it.frequency != "NONE" }
            .distinctBy { "${it.note}_${it.amount}_${it.frequency}" }
    }

    val totalLiquid = remember(pocketBalances) {
        pocketBalances.filter { it.pocketType == PocketType.LIQUID }
            .sumOf { it.computedBalance }
            .coerceAtLeast(0.0)
    }

    val unpaidCardDues = remember(pocketBalances) {
        pocketBalances.filter { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE }
            .filter { it.computedBalance < 0.0 }
            .sumOf { Math.abs(it.computedBalance) }
    }

    val trueSafeLiquid = if (phantomLockEnabled) (totalLiquid - unpaidCardDues).coerceAtLeast(0.0) else totalLiquid
    val runwayDays = if (dailyBurnCeiling > 0) (trueSafeLiquid / dailyBurnCeiling).toInt() else 0

    val todayStartEpoch = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val spentToday = remember(completedTransactions) {
        completedTransactions.filter {
            it.timestamp >= todayStartEpoch && it.nature in listOf(
                MovementNature.OUTFLOW,
                MovementNature.PEER_LEND,
                MovementNature.PEER_REPAY
            )
        }.sumOf { it.amount ?: 0.0 }
    }

    val totalInflowLifetime = remember(completedTransactions) {
        completedTransactions.filter {
            it.nature in listOf(MovementNature.INFLOW, MovementNature.PEER_COLLECT)
        }.sumOf { it.amount ?: 0.0 }
    }

    val totalOutflowLifetime = remember(completedTransactions) {
        completedTransactions.filter {
            it.nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND)
        }.sumOf { it.amount ?: 0.0 }
    }

    val peerNet = remember(pocketBalances) {
        pocketBalances.filter { it.pocketType == PocketType.COUNTERPARTY || it.subType == "PEER" || it.pocketType == PocketType.PEER }
            .sumOf { it.computedBalance }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var isPrivacyMode by remember { mutableStateOf(false) }

    var activeWizard by remember { mutableStateOf<WizardType?>(null) }
    var showCommandHud by remember { mutableStateOf(false) }
    var selectedPocketIdForHud by remember { mutableStateOf<Long?>(null) }
    var hudInDialogError by remember { mutableStateOf<String?>(null) }

    var editingPocket by remember { mutableStateOf<VaultPocket?>(null) }
    var editingFlowRecord by remember { mutableStateOf<FlowRecord?>(null) }
    var editingRecurringRule by remember { mutableStateOf<FlowRecord?>(null) }
    var recordPendingDeletion by remember { mutableStateOf<FlowRecord?>(null) }
    var showCreatePocketDialog by remember { mutableStateOf(false) }
    var prefilledCreatePocketName by remember { mutableStateOf("") }
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
        completedTransactions.take(35).groupBy { flow ->
            val calRecord = Calendar.getInstance().apply { timeInMillis = flow.timestamp }
            when {
                calNow.get(Calendar.YEAR) == calRecord.get(Calendar.YEAR) &&
                        calNow.get(Calendar.DAY_OF_YEAR) == calRecord.get(Calendar.DAY_OF_YEAR) -> "Today"
                calNow.get(Calendar.YEAR) == calRecord.get(Calendar.YEAR) &&
                        calNow.get(Calendar.DAY_OF_YEAR) - calRecord.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
                else -> SimpleDateFormat("dd MMMM", Locale.getDefault()).format(Date(flow.timestamp))
            }
        }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = VaultBackupManager.exportEncryptedBackup(context, db, uri, "INOUT_LEDGER_MASTER_KEY")
                if (res.isSuccess) alertManager.showAlert("Encrypted backup saved (.vault)", AlertType.SUCCESS)
                else alertManager.showAlert("Export failed: ${res.exceptionOrNull()?.message}", AlertType.ERROR)
            }
        }
    }

    val backupRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = VaultBackupManager.restoreEncryptedBackup(context, db, uri, "INOUT_LEDGER_MASTER_KEY")
                if (res.isSuccess) alertManager.showAlert("Restored ${res.getOrNull()} records successfully", AlertType.SUCCESS)
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
                hudInDialogError = null
                selectedPocketIdForHud = rawPockets.firstOrNull { it.pocketType == PocketType.LIQUID }?.id
                showCommandHud = true
                alertManager.showAlert("Scanned: ${parsed.merchant} (₹${String.format("%,.0f", parsed.total ?: 0.0)})", AlertType.INFO)
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
        else alertManager.showAlert("Camera permission required for receipt scanning", AlertType.WARNING)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val parsed = ReceiptScanner.processReceipt(context, uri)
                    ocrPrefilledNote = parsed.merchant
                    ocrPrefilledAmount = parsed.total
                    hudInDialogError = null
                    selectedPocketIdForHud = rawPockets.firstOrNull { it.pocketType == PocketType.LIQUID }?.id
                    showCommandHud = true
                    alertManager.showAlert("Scanned: ${parsed.merchant} (₹${String.format("%,.0f", parsed.total ?: 0.0)})", AlertType.INFO)
                } catch (e: Exception) {
                    alertManager.showAlert("Receipt OCR Error: ${e.localizedMessage}", AlertType.ERROR)
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
                        unreadNoticeCount = unreadNoticeCount,
                        theme = theme,
                        onTogglePrivacy = { isPrivacyMode = !isPrivacyMode },
                        onOpenNotices = {
                            showNoticesSheet = true
                            scope.launch { db.stateFlowDao().markAllNoticesRead() }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = theme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
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
                                            hudInDialogError = null
                                            selectedPocketIdForHud = rawPockets.firstOrNull { it.pocketType == PocketType.LIQUID }?.id
                                            showCommandHud = true
                                        },
                                        modifier = Modifier.size(46.dp),
                                        containerColor = theme.accent,
                                        contentColor = theme.bg,
                                        shape = CircleShape
                                    ) {
                                        Icon(imageVector = Icons.Default.EditNote, contentDescription = "Full Entry", modifier = Modifier.size(22.dp))
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
                                        prefilledCreatePocketName = ""
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
                                                    tag = "RUNWAY SURVIVAL HORIZON",
                                                    status = if (runwayDays > 30) "Solvent" else "Tight",
                                                    isPositive = runwayDays > 30,
                                                    heroText = if (isPrivacyMode) "•• Days" else "$runwayDays Days",
                                                    leftSub = "Daily Target: ₹${dailyBurnCeiling.toInt()}/day ✎",
                                                    rightSub = "Safe Liquid: ₹${String.format("%,.0f", trueSafeLiquid)}",
                                                    theme = theme,
                                                    onCardClick = { showBurnEditDialog = true }
                                                )
                                                1 -> MetricCarouselCard(
                                                    tag = "TRUE LIQUID (PHANTOM LOCK)",
                                                    status = if (unpaidCardDues > 0) "Locked" else "Clean",
                                                    isPositive = unpaidCardDues == 0.0,
                                                    heroText = if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", trueSafeLiquid)}",
                                                    leftSub = "Gross Cash: ₹${totalLiquid.toInt()}",
                                                    rightSub = "Card Liability: ₹${unpaidCardDues.toInt()}",
                                                    theme = theme,
                                                    onCardClick = {}
                                                )
                                                2 -> MetricCarouselCard(
                                                    tag = "DAILY BURN VELOCITY",
                                                    status = if (spentToday <= dailyBurnCeiling) "On Track" else "Spiked",
                                                    isPositive = spentToday <= dailyBurnCeiling,
                                                    heroText = if (isPrivacyMode) "₹ •••" else "₹${spentToday.toInt()} / ₹${dailyBurnCeiling.toInt()}",
                                                    leftSub = "Today's Target: ₹${dailyBurnCeiling.toInt()}",
                                                    rightSub = if (spentToday <= dailyBurnCeiling) "Pacing Normal" else "Over Target",
                                                    theme = theme,
                                                    onCardClick = { showBurnEditDialog = true }
                                                )
                                                3 -> MetricCarouselCard(
                                                    tag = "PEER NET POSITION",
                                                    status = if (peerNet >= 0.0) "Receivable" else "Payable",
                                                    isPositive = peerNet >= 0.0,
                                                    heroText = if (isPrivacyMode) "₹ •••" else "${if (peerNet >= 0.0) "+" else "-"}₹${String.format("%,.0f", Math.abs(peerNet))}",
                                                    leftSub = if (peerNet >= 0.0) "Net lender" else "Net borrower",
                                                    rightSub = "Across all contacts",
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
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                                        modifier = Modifier.fillMaxWidth()
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
                                                    text = "Guided Flow",
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
                                                        title = "+ Income",
                                                        icon = Icons.Default.TrendingUp,
                                                        color = theme.mildGreen,
                                                        onClick = { activeWizard = WizardType.INCOME }
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
                                                        title = "💳 Card Bill",
                                                        icon = Icons.Default.CreditCard,
                                                        color = Color(0xFF64B5F6),
                                                        onClick = { activeWizard = WizardType.CARD_BILL }
                                                    )
                                                }
                                                item {
                                                    ActionPillButton(
                                                        title = "👥 Lent / Borrow",
                                                        icon = Icons.Default.People,
                                                        color = Color(0xFFBA68C8),
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
                                                text = "No records yet. Tap an action above to start.",
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
                                        items(records, key = { it.id }) { flow ->
                                            FlatStreamRow(
                                                flow = flow,
                                                rawPockets = rawPockets,
                                                isPrivacyMode = isPrivacyMode,
                                                theme = theme,
                                                onLongClick = { editingFlowRecord = flow }
                                            )
                                            HorizontalDivider(
                                                color = theme.surfaceAlt.copy(alpha = 0.4f),
                                                thickness = 0.5.dp
                                            )
                                        }
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
                                val bal = balance ?: 0.0
                                if (bal != 0.0) {
                                    alertManager.showAlert("Cannot delete account with active balance of ₹${bal.toInt()}", AlertType.WARNING)
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
                                    alertManager.showAlert(if (schedule.isPaused) "Resumed schedule" else "Paused schedule", AlertType.SUCCESS)
                                }
                            },
                            onEditRecurring = { schedule -> editingRecurringRule = schedule },
                            onDeleteRecurringSafe = { schedule ->
                                scope.launch {
                                    db.stateFlowDao().deleteFlowRecordById(schedule.id)
                                    alertManager.showAlert("Recurring schedule deleted", AlertType.SUCCESS)
                                }
                            },
                            onRequestCreateAccount = { reqType ->
                                prefilledCreatePocketName = ""
                                prefilledCreatePocketType = reqType
                                showCreatePocketDialog = true
                            }
                        )

                        2 -> IntelligenceScreen(
                            pocketBalances = pocketBalances,
                            flowRecords = flowRecords,
                            recurringSchedules = recurringTemplates,
                            stagedDesires = emptyList(),
                            dailyBurnCeiling = dailyBurnCeiling,
                            trueSafeLiquid = trueSafeLiquid,
                            isPrivacyMode = isPrivacyMode,
                            theme = theme
                        )

                        3 -> SettingsCardsList(
                            theme = theme,
                            currentSha = currentInstalledSha,
                            lastUpdatedDate = lastInstalledTimeFormatted,
                            autoSplitEnabled = autoSplitEnabled,
                            phantomLockEnabled = phantomLockEnabled,
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
                            onPhantomLockToggled = {
                                phantomLockEnabled = it
                                prefs.edit().putBoolean("phantom_lock_enabled", it).apply()
                            },
                            onThemeSelected = { mode ->
                                activeThemeMode = mode
                                prefs.edit().putString("selected_theme", mode.name).apply()
                            },
                            onExportPdf = {
                                scope.launch { PdfDossierExporter.generateAndShareDossier(context, pocketBalances, flowRecords) }
                            },
                            onExportCsv = {
                                val compatList = completedTransactions.map { flowRecord ->
                                    val sourceId = flowRecord.sourcePocketId ?: flowRecord.targetPocketId ?: 0L
                                    val isExpense = flowRecord.nature in listOf(
                                        MovementNature.OUTFLOW,
                                        MovementNature.PEER_LEND,
                                        MovementNature.PEER_REPAY,
                                        MovementNature.CARD_PAYMENT
                                    )
                                    Transaction(
                                        id = flowRecord.id,
                                        accountId = sourceId,
                                        flowType = if (isExpense) "OUT" else "IN",
                                        type = flowRecord.nature.name,
                                        category = flowRecord.category,
                                        amount = flowRecord.amount ?: 0.0,
                                        timestamp = flowRecord.timestamp,
                                        note = flowRecord.note,
                                        isRecurring = flowRecord.isRecurring,
                                        frequency = flowRecord.frequency
                                    )
                                }
                                CsvExporter.exportAndShareTransactions(context, compatList)
                            },
                            onExportEncryptedBackup = {
                                backupExportLauncher.launch("inout_vault_backup_${System.currentTimeMillis()}.vault")
                            },
                            onRestoreEncryptedBackup = {
                                backupRestoreLauncher.launch(arrayOf("application/octet-stream", "*/*"))
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

                // Symmetrical Guided Action Wizard Dialog
                activeWizard?.let { wizard ->
                    GuidedActionWizardDialog(
                        type = wizard,
                        rawPockets = rawPockets,
                        pocketBalances = pocketBalances,
                        theme = theme,
                        onDismiss = { activeWizard = null },
                        onRequestNewAccount = { reqType ->
                            prefilledCreatePocketName = ""
                            prefilledCreatePocketType = reqType
                            showCreatePocketDialog = true
                        },
                        onCommit = { nature, srcId, tgtId, amt, cat, note, date, isRec, freq ->
                            scope.launch {
                                if (nature == MovementNature.INFLOW && tgtId == null) {
                                    alertManager.showAlert("Please add a bank account before logging income.", AlertType.ERROR)
                                    prefilledCreatePocketName = ""
                                    prefilledCreatePocketType = PocketType.LIQUID
                                    showCreatePocketDialog = true
                                    return@launch
                                }

                                if (nature in listOf(MovementNature.OUTFLOW, MovementNature.TRANSFER) && srcId == null) {
                                    alertManager.showAlert("Please add a source account before logging expenses.", AlertType.ERROR)
                                    prefilledCreatePocketName = ""
                                    prefilledCreatePocketType = PocketType.LIQUID
                                    showCreatePocketDialog = true
                                    return@launch
                                }

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
                                    is VaultExecutionResult.OverdraftError -> alertManager.showAlert(res.message, AlertType.ERROR)
                                    is VaultExecutionResult.Success -> {
                                        alertManager.showAlert(res.summary, AlertType.SUCCESS)
                                        activeWizard = null
                                    }
                                }
                            }
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
                                    text = "System & Ledger Notices",
                                    color = theme.textBright,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (systemNotices.isNotEmpty()) {
                                    TextButton(onClick = {
                                        scope.launch { db.stateFlowDao().clearAllNotices() }
                                    }) {
                                        Text(text = "Clear All", color = theme.mildRed, fontSize = 12.sp)
                                    }
                                }
                            }

                            if (systemNotices.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "No notifications logged.", color = theme.textMuted, fontSize = 13.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 32.dp)
                                ) {
                                    items(systemNotices, key = { it.id }) { notice ->
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = theme.surfaceAlt),
                                            modifier = Modifier.fillMaxWidth()
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
                        initialName = prefilledCreatePocketName,
                        initialType = prefilledCreatePocketType,
                        theme = theme,
                        onDismiss = {
                            showCreatePocketDialog = false
                            prefilledCreatePocketName = ""
                        },
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
                                prefilledCreatePocketName = ""
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
                        title = { Text(text = "Update Daily Burn Ceiling", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = "Set daily spending limit. Runway days adapt dynamically.", color = theme.textMuted, fontSize = 12.sp)
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
                                CompactInputField(
                                    value = verificationInput,
                                    onValueChange = { verificationInput = it },
                                    placeholder = "Type PURGE"
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (verificationInput.trim() == "PURGE") {
                                        scope.launch {
                                            flowRecords.forEach { db.stateFlowDao().deleteFlowRecordById(it.id) }
                                            rawPockets.forEach { db.stateFlowDao().updatePocket(it.copy(isArchived = true)) }
                                            alertManager.showAlert("All vault records purged", AlertType.SUCCESS)
                                            showClearLedgerConfirmation = false
                                            verificationInput = ""
                                        }
                                    } else {
                                        alertManager.showAlert("Verification phrase did not match 'PURGE'", AlertType.ERROR)
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

                editingRecurringRule?.let { rule ->
                    EditRecurringRuleDialog(
                        rule = rule,
                        theme = theme,
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
                    val delAmt = (flow.amount ?: 0.0).toInt()
                    AlertDialog(
                        onDismissRequest = { recordPendingDeletion = null },
                        containerColor = theme.surface,
                        title = { Text(text = "Confirm Deletion", color = Color.White) },
                        text = { Text(text = "Delete entry of ₹$delAmt for '${flow.note.ifBlank { flow.category }}'? Balances will adjust.", color = Color(0xFFCCCCCC)) },
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
                            ) { Text(text = "Delete", color = Color.White) }
                        },
                        dismissButton = { TextButton(onClick = { recordPendingDeletion = null }) { Text(text = "Cancel", color = theme.textMuted) } }
                    )
                }

                if (showAllRecordsSheet) {
                    AllTransactionsSearchSheet(
                        flowRecords = completedTransactions,
                        isPrivacyMode = isPrivacyMode,
                        isProUser = isProUnlocked,
                        sheetState = allTransactionsSheetState,
                        onDismiss = { showAllRecordsSheet = false },
                        onEditRecord = { flow -> editingFlowRecord = flow },
                        onExportCsv = {
                            val compatList = completedTransactions.map { flowRecord ->
                                val sourceId = flowRecord.sourcePocketId ?: flowRecord.targetPocketId ?: 0L
                                val isExpense = flowRecord.nature in listOf(
                                    MovementNature.OUTFLOW,
                                    MovementNature.PEER_LEND,
                                    MovementNature.PEER_REPAY,
                                    MovementNature.CARD_PAYMENT
                                )
                                Transaction(
                                    id = flowRecord.id,
                                    accountId = sourceId,
                                    flowType = if (isExpense) "OUT" else "IN",
                                    type = flowRecord.nature.name,
                                    category = flowRecord.category,
                                    amount = flowRecord.amount ?: 0.0,
                                    timestamp = flowRecord.timestamp,
                                    note = flowRecord.note,
                                    isRecurring = flowRecord.isRecurring,
                                    frequency = flowRecord.frequency
                                )
                            }
                            CsvExporter.exportAndShareTransactions(context, compatList)
                        },
                        onExportPdfDossier = {
                            scope.launch { PdfDossierExporter.generateAndShareDossier(context, pocketBalances, flowRecords) }
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
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
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
    rawPockets: List<VaultPocket>,
    pocketBalances: List<PocketBalanceSummary>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onRequestNewAccount: (PocketType) -> Unit,
    onCommit: (
        nature: MovementNature,
        sourcePocketId: Long?,
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

    val liquidPockets = remember(rawPockets) { rawPockets.filter { it.pocketType == PocketType.LIQUID } }
    val cardPockets = remember(rawPockets) { rawPockets.filter { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE } }
    val peerPockets = remember(rawPockets) { rawPockets.filter { it.pocketType == PocketType.COUNTERPARTY || it.subType == "PEER" || it.pocketType == PocketType.PEER } }

    var selectedSourceId by remember { mutableStateOf(liquidPockets.firstOrNull()?.id) }
    var selectedTargetId by remember {
        mutableStateOf(
            when (type) {
                WizardType.INCOME -> liquidPockets.firstOrNull()?.id
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
        WizardType.INCOME -> listOf("Salary", "Investment", "Freelance", "Refund", "Income")
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
                    WizardType.INCOME -> "Record Income Inflow"
                    WizardType.TRANSFER -> "Account Transfer"
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

                // Amount Input
                Column {
                    CompactInputField(
                        value = rawAmount,
                        onValueChange = { rawAmount = it },
                        placeholder = "Amount in ₹ (e.g. 150+40)"
                    )
                    if (computedAmount != null && rawAmount.contains("+")) {
                        Text("Evaluated: ₹$computedAmount", color = theme.accent, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }

                // Interactive Transaction Date Selector
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

                // Source Account Card
                if (type in listOf(WizardType.EXPENSE, WizardType.TRANSFER, WizardType.CARD_BILL) || (type == WizardType.PEER_LEND_BORROW && peerModeIsLend)) {
                    Text("Pay From (Liquid Account)", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    val activeSrc = liquidPockets.firstOrNull { it.id == selectedSourceId } ?: liquidPockets.firstOrNull()
                    val srcBal = pocketBalances.firstOrNull { it.pocketId == (activeSrc?.id?.toString() ?: "") }?.computedBalance ?: 0.0

                    AccountCardSelector(
                        title = activeSrc?.name ?: "No Bank Account Found",
                        sub = if (activeSrc == null) "+ Add bank account to pay from" else "Available: ₹${String.format("%,.0f", srcBal.coerceAtLeast(0.0))}",
                        theme = theme,
                        accounts = liquidPockets.map { p ->
                            val b = pocketBalances.firstOrNull { it.pocketId == p.id.toString() }?.computedBalance ?: 0.0
                            Triple(p.id, p.name, "₹${String.format("%,.0f", b.coerceAtLeast(0.0))}")
                        },
                        onSelect = { selectedSourceId = it },
                        onAdd = { onRequestNewAccount(PocketType.LIQUID) }
                    )
                }

                // Income Destination Account Card
                if (type == WizardType.INCOME) {
                    Text("Deposit Into (Bank/Wallet)", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    val activeTgt = liquidPockets.firstOrNull { it.id == selectedTargetId } ?: liquidPockets.firstOrNull()
                    val tgtBal = pocketBalances.firstOrNull { it.pocketId == (activeTgt?.id?.toString() ?: "") }?.computedBalance ?: 0.0

                    AccountCardSelector(
                        title = activeTgt?.name ?: "No Bank Account Found",
                        sub = if (activeTgt == null) "+ Add bank account to deposit" else "Current: ₹${String.format("%,.0f", tgtBal)}",
                        theme = theme,
                        accounts = liquidPockets.map { p ->
                            val b = pocketBalances.firstOrNull { it.pocketId == p.id.toString() }?.computedBalance ?: 0.0
                            Triple(p.id, p.name, "₹${String.format("%,.0f", b)}")
                        },
                        onSelect = { selectedTargetId = it },
                        onAdd = { onRequestNewAccount(PocketType.LIQUID) }
                    )
                }

                if (type == WizardType.TRANSFER) {
                    Text("Transfer To", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    val validTargets = rawPockets.filter { it.id != selectedSourceId }
                    val activeTgt = validTargets.firstOrNull { it.id == selectedTargetId } ?: validTargets.firstOrNull()
                    val tgtBal = pocketBalances.firstOrNull { it.pocketId == (activeTgt?.id?.toString() ?: "") }?.computedBalance ?: 0.0

                    AccountCardSelector(
                        title = activeTgt?.name ?: "Select Destination",
                        sub = if (activeTgt == null) "Tap to select or add" else "Current Balance: ₹${String.format("%,.0f", tgtBal)}",
                        theme = theme,
                        accounts = validTargets.map { p ->
                            val b = pocketBalances.firstOrNull { it.pocketId == p.id.toString() }?.computedBalance ?: 0.0
                            Triple(p.id, p.name, "₹${String.format("%,.0f", b)}")
                        },
                        onSelect = { selectedTargetId = it },
                        onAdd = { onRequestNewAccount(PocketType.LIQUID) }
                    )
                }

                if (type == WizardType.CARD_BILL) {
                    Text("Card Liability to Clear", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    val activeCard = cardPockets.firstOrNull { it.id == selectedTargetId } ?: cardPockets.firstOrNull()
                    val cardBal = pocketBalances.firstOrNull { it.pocketId == (activeCard?.id?.toString() ?: "") }?.computedBalance ?: 0.0
                    val dues = if (cardBal < 0.0) Math.abs(cardBal) else 0.0

                    AccountCardSelector(
                        title = activeCard?.name ?: "No Credit Cards Linked",
                        sub = if (activeCard == null) "+ Add credit card to clear" else "Outstanding Dues: ₹${String.format("%,.0f", dues)}",
                        theme = theme,
                        accounts = cardPockets.map { p ->
                            val b = pocketBalances.firstOrNull { it.pocketId == p.id.toString() }?.computedBalance ?: 0.0
                            Triple(p.id, p.name, "Due: ₹${String.format("%,.0f", if (b < 0.0) Math.abs(b) else 0.0)}")
                        },
                        onSelect = { selectedTargetId = it },
                        onAdd = { onRequestNewAccount(PocketType.CREDIT_LINE) }
                    )
                }

                if (type == WizardType.PEER_LEND_BORROW) {
                    Text("Select Contact", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    val activePeer = peerPockets.firstOrNull { it.id == selectedTargetId } ?: peerPockets.firstOrNull()

                    AccountCardSelector(
                        title = activePeer?.name ?: "No Contacts Saved",
                        sub = if (activePeer == null) "Tap to add contact" else "Contact Entity",
                        theme = theme,
                        accounts = peerPockets.map { Triple(it.id, it.name, "Contact") },
                        onSelect = { selectedTargetId = it },
                        onAdd = { onRequestNewAccount(PocketType.COUNTERPARTY) }
                    )
                }

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

                CompactInputField(value = note, onValueChange = { note = it }, placeholder = "Merchant / Reference Note (Optional)")

                // Symmetrical Repeat Cadence Segmented Control
                Text("Repeat Cadence", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.surfaceAlt)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("NONE" to "Once", "DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly").forEach { (fCode, fName) ->
                        val isSel = (if (!isRecurring) "NONE" else frequency) == fCode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else Color.Transparent)
                                .clickable {
                                    if (fCode == "NONE") {
                                        isRecurring = false
                                    } else {
                                        isRecurring = true
                                        frequency = fCode
                                    }
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = fName,
                                color = if (isSel) theme.bg else theme.textBright,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = computedAmount
                    if (amt == null || amt <= 0.0) return@Button

                    val finalNature = when (type) {
                        WizardType.EXPENSE -> MovementNature.OUTFLOW
                        WizardType.INCOME -> MovementNature.INFLOW
                        WizardType.TRANSFER -> MovementNature.TRANSFER
                        WizardType.CARD_BILL -> MovementNature.CARD_PAYMENT
                        WizardType.PEER_LEND_BORROW -> if (peerModeIsLend) MovementNature.PEER_LEND else MovementNature.PEER_BORROW
                    }

                    val src = when (type) {
                        WizardType.INCOME -> null
                        WizardType.EXPENSE, WizardType.TRANSFER, WizardType.CARD_BILL -> selectedSourceId
                        WizardType.PEER_LEND_BORROW -> if (peerModeIsLend) selectedSourceId else selectedTargetId
                    }

                    val tgt = when (type) {
                        WizardType.EXPENSE -> null
                        WizardType.INCOME -> selectedTargetId ?: liquidPockets.firstOrNull()?.id
                        WizardType.TRANSFER, WizardType.CARD_BILL -> selectedTargetId
                        WizardType.PEER_LEND_BORROW -> if (peerModeIsLend) selectedTargetId else selectedSourceId
                    }

                    onCommit(
                        finalNature,
                        src,
                        tgt,
                        amt,
                        selectedCategoryId,
                        note.ifBlank { selectedCategoryId },
                        selectedDateEpoch,
                        isRecurring,
                        frequency
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                enabled = computedAmount != null && computedAmount > 0.0
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
            HorizontalDivider(color = theme.surfaceAlt)
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
                        .background(if (isPositive) theme.mildGreen.copy(alpha = 0.2f) else theme.mildRed.copy(alpha = 0.2f))
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
    flow: FlowRecord,
    rawPockets: List<VaultPocket>,
    isPrivacyMode: Boolean,
    theme: ThemeColors,
    onLongClick: () -> Unit
) {
    val isOut = flow.nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.PEER_REPAY, MovementNature.CARD_PAYMENT)
    val flowColor = if (isOut) theme.mildRed else theme.mildGreen

    val accountName = remember(flow, rawPockets) {
        val id = flow.targetPocketId?.takeIf { flow.nature == MovementNature.INFLOW }
            ?: flow.sourcePocketId
            ?: flow.targetPocketId
        rawPockets.firstOrNull { it.id == id }?.name ?: "Account"
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
            Text(text = flow.note.ifBlank { flow.category }, color = theme.textBright, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            Text(text = "${flow.category} • $accountName", color = theme.textMuted, fontSize = 10.5.sp)
        }

        val amtVal = flow.amount ?: 0.0
        val amtStr = if (isPrivacyMode) "₹ •••" else "${if (isOut) "-" else "+"}₹${String.format("%,.0f", amtVal)}"
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
    phantomLockEnabled: Boolean,
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
    onPhantomLockToggled: (Boolean) -> Unit,
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "ABOUT INOUT", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Column {
                        Text(text = "InOut Vault (Institutional Alpha)", color = theme.textBright, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Current Build: ${currentSha.take(7)}", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Last Installed: $lastUpdatedDate", color = theme.textMuted, fontSize = 10.5.sp)
                    }
                }
            }
        }

        // Theme-Styled In-App Update Component
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
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
                                    Text("Streaming Alpha Build: $percent%", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                        Text("Tap below to invoke the system installer.", color = theme.textBright, fontSize = 11.sp)
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent)
                    ) {
                        Icon(imageVector = Icons.Default.BugReport, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Send Feedback / Report Bug", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "LEDGER GUARDS", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "Cross-Account Auto-Split", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Auto-debit secondary accounts if primary lacks liquid funds.", color = theme.textMuted, fontSize = 10.5.sp)
                        }
                        Switch(
                            checked = autoSplitEnabled,
                            onCheckedChange = onAutoSplitToggled,
                            colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.surfaceAlt)
                        )
                    }

                    HorizontalDivider(color = theme.surfaceAlt, thickness = 0.5.dp)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "Credit Card Phantom Lock", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Isolate unbilled card dues from liquid balance to prevent phantom runway.", color = theme.textMuted, fontSize = 10.5.sp)
                        }
                        Switch(
                            checked = phantomLockEnabled,
                            onCheckedChange = onPhantomLockToggled,
                            colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.surfaceAlt)
                        )
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "WORKSPACE & THEME", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            AppThemeMode.AMBER_OCHRE,
                            AppThemeMode.OLIVE_MATCHA,
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
                                Text(text = mode.name.replace("_", " "), color = if (isSel) theme.bg else theme.textBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "DATA SOVEREIGNTY & BACKUP", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Button(
                        onClick = onExportEncryptedBackup,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Create Encrypted Backup (.vault)", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onRestoreEncryptedBackup,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Restore Ledger from Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = theme.surfaceAlt, thickness = 0.5.dp)

                    Button(
                        onClick = onExportPdf,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Export PDF Ledger Dossier", color = theme.textBright, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onExportCsv,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Export CSV Ledger", color = theme.textBright, fontSize = 12.sp)
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

        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "SANDBOX & INOUT PRO", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(text = "Current Tier", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = if (isProUnlocked) "Pro License Active" else "Free Sandbox Mode", color = if (isProUnlocked) theme.accent else theme.textMuted, fontSize = 11.sp)
                        }
                        Button(
                            onClick = onOpenPaywall,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = if (isProUnlocked) "Manage Pro" else "View Pro", color = theme.bg, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditRecurringRuleDialog(
    rule: FlowRecord,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (amount: Double, note: String, freq: String, timestamp: Long) -> Unit
) {
    var note by remember { mutableStateOf(rule.note) }
    var amountText by remember { mutableStateOf((rule.amount ?: 0.0).toInt().toString()) }
    var frequency by remember { mutableStateOf(if (rule.frequency != "NONE") rule.frequency else "MONTHLY") }
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
        title = { Text(text = "Edit Recurring Schedule", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactInputField(value = note, onValueChange = { note = it }, placeholder = "Schedule Description")
                CompactInputField(value = amountText, onValueChange = { amountText = it }, placeholder = "Amount in ₹")

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
                        Text(text = "Cadence / Starting Date", color = theme.textMuted, fontSize = 10.sp)
                        Text(text = dateFormatted, color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = theme.accent, modifier = Modifier.size(16.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: (rule.amount ?: 0.0)
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
        title = { Text(text = "Edit Entry", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val amtDisplay = (record.amount ?: 0.0).toInt()
                Text(text = "Amount: ₹$amtDisplay", color = theme.textMuted, fontSize = 12.sp)
                CompactInputField(value = note, onValueChange = { note = it }, placeholder = "Merchant / Note")

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

@Composable
private fun CreateAccountDialog(
    initialName: String = "",
    initialType: PocketType = PocketType.LIQUID,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (String, PocketType, Double, Double, Long) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var type by remember { mutableStateOf(initialType) }
    var limit by remember { mutableStateOf("") }
    var targetAmt by remember { mutableStateOf("") }
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
        title = { Text(text = "Add Account / Pot / Contact", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactInputField(value = name, onValueChange = { name = it }, placeholder = "Account / Pot / Person Name")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        PocketType.LIQUID to "Liquid",
                        PocketType.SAVING_GOAL to "Goal Pot",
                        PocketType.CREDIT_LINE to "Card (CC)",
                        PocketType.COUNTERPARTY to "Contact"
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
                            Text(text = lbl, color = if (isSel) theme.bg else theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (type == PocketType.CREDIT_LINE) {
                    CompactInputField(value = limit, onValueChange = { limit = it }, placeholder = "Credit Limit (e.g. 50000)")
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
                        Text(text = "Bill Due Date: $dateFormatted", color = theme.textBright, fontSize = 11.5.sp)
                        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                    }
                }
                if (type == PocketType.SAVING_GOAL) {
                    CompactInputField(value = targetAmt, onValueChange = { targetAmt = it }, placeholder = "Target Goal Amount (e.g. 60000)")
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
                        targetAmt.toDoubleOrNull() ?: 0.0,
                        targetDateEpoch
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
    account: VaultPocket,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Long) -> Unit
) {
    val accLimit = account.creditLimit ?: 0.0
    val accTarget = account.targetAmount ?: 0.0

    var name by remember { mutableStateOf(account.name) }
    var limit by remember { mutableStateOf(if (accLimit > 0.0) String.format("%.0f", accLimit) else "") }
    var targetAmt by remember { mutableStateOf(if (accTarget > 0.0) String.format("%.0f", accTarget) else "") }
    var targetDateEpoch by remember { mutableStateOf(if (account.targetDateEpoch > 0) account.targetDateEpoch else System.currentTimeMillis() + (30L * 24 * 3600 * 1000L)) }
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
            val titleText = when (account.pocketType) {
                PocketType.SAVING_GOAL -> "Edit Goal Pot"
                PocketType.CREDIT_LINE -> "Edit Credit Card"
                PocketType.COUNTERPARTY -> "Edit Contact"
                else -> "Edit Bank Account"
            }
            Text(text = titleText, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactInputField(value = name, onValueChange = { name = it }, placeholder = "Name")
                if (account.pocketType == PocketType.CREDIT_LINE) {
                    CompactInputField(value = limit, onValueChange = { limit = it }, placeholder = "Credit Limit")
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
                        Text(text = "Bill Due Date: $dateFormatted", color = theme.textBright, fontSize = 11.5.sp)
                        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                    }
                }
                if (account.pocketType == PocketType.SAVING_GOAL) {
                    CompactInputField(value = targetAmt, onValueChange = { targetAmt = it }, placeholder = "Target Goal Amount")
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
                        limit.toDoubleOrNull() ?: accLimit,
                        targetAmt.toDoubleOrNull() ?: accTarget,
                        targetDateEpoch
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) { Text(text = "Save Changes", color = theme.bg, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel", color = theme.textMuted) } }
    )
}
