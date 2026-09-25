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
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE) }

    val alertManager = remember { VaultAlertManager() }
    val ledgerEngine = remember { VaultLedgerEngine(db.stateFlowDao(), prefs) }

    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    val currentInstalledSha = remember(packageInfo) {
        val vName = packageInfo?.versionName ?: "1dcb750"
        if (vName.startsWith("InOut-alpha-")) vName.removePrefix("InOut-alpha-").removeSuffix(".apk") else vName
    }
    val lastInstalledTimeFormatted = remember(packageInfo) {
        val t = packageInfo?.lastUpdateTime ?: System.currentTimeMillis()
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(t))
    }

    LaunchedEffect(currentInstalledSha) {
        val lastLoggedBuild = prefs.getString("last_logged_build_sha", "")
        if (lastLoggedBuild != currentInstalledSha && currentInstalledSha.isNotBlank()) {
            db.stateFlowDao().insertNotice(
                SystemNotice(
                    title = "App Updated",
                    message = "InOut upgraded to build ${currentInstalledSha.take(7)} on $lastInstalledTimeFormatted.",
                    type = "APP_UPDATE",
                    timestamp = System.currentTimeMillis(),
                    isRead = false
                )
            )
            prefs.edit().putString("last_logged_build_sha", currentInstalledSha).apply()
        }
    }

    LaunchedEffect(Unit) {
        val generated = ledgerEngine.catchUpRecurringRules()
        if (generated > 0) {
            db.stateFlowDao().insertNotice(
                SystemNotice(
                    title = "Recurring Ledger Catch-Up",
                    message = "Auto-executed $generated recurring schedule(s) successfully.",
                    type = "RECURRING_TRIGGER",
                    timestamp = System.currentTimeMillis(),
                    isRead = false
                )
            )
            alertManager.showAlert("Auto-recorded $generated recurring schedule(s)", AlertType.SUCCESS)
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
    val stagedDesires by db.stateFlowDao().observeActiveStagedDesires().collectAsState(initial = emptyList())
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
            .sumOf { it.currentBalance ?: 0.0 }
            .coerceAtLeast(0.0)
    }

    val unpaidCardDues = remember(pocketBalances) {
        pocketBalances.filter { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE }
            .filter { (it.computedBalance ?: 0.0) < 0.0 }
            .sumOf { Math.abs(it.computedBalance ?: 0.0) }
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
            it.nature in listOf(
                MovementNature.INFLOW,
                MovementNature.PEER_COLLECT
            )
        }.sumOf { it.amount ?: 0.0 }
    }

    val totalOutflowLifetime = remember(completedTransactions) {
        completedTransactions.filter {
            it.nature in listOf(
                MovementNature.OUTFLOW,
                MovementNature.PEER_LEND
            )
        }.sumOf { it.amount ?: 0.0 }
    }

    val peerNet = remember(pocketBalances) {
        pocketBalances.filter { it.pocketType == PocketType.COUNTERPARTY || it.pocketType == PocketType.PEER || it.subType == "PEER" }
            .sumOf { it.computedBalance ?: 0.0 }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var isPrivacyMode by remember { mutableStateOf(false) }

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

    var pendingActionAfterAccountCreation by remember { mutableStateOf<((Long) -> Unit)?>(null) }

    var availableUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isCheckingForUpdate by remember { mutableStateOf(false) }

    var showFeedbackDialog by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }

    val initialPage = remember { prefs.getInt("saved_carousel_page", 0).coerceIn(0, 3) }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 4 })
    LaunchedEffect(pagerState.currentPage) {
        prefs.edit().putInt("saved_carousel_page", pagerState.currentPage).apply()
    }

    var naturalLanguageInput by remember { mutableStateOf(TextFieldValue("")) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val quickBarFocusRequester = remember { FocusRequester() }

    val placeholderHints = listOf(
        "coffee 120 BANK",
        "groceries 850 cash",
        "salary 45k into BANK",
        "dinner 450 BANK monthly",
        "repay 4k to NAME from BANK",
        "collect 2000 from NAME",
        "lent 1500 to NAME",
        "borrow 3000 from NAME",
        "trf 5000 from BANK to BANK",
        "new bank BANK",
        "new card BANK limit 50k",
        "new goal Emergency 100k",
        "burn 500"
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

    val quickBarCommandPills = remember(naturalLanguageInput.text, rawPockets) {
        derivedStateOf {
            val q = naturalLanguageInput.text.trim().lowercase()
            when {
                q.isEmpty() -> listOf(
                    Triple("+ Bank", "create bank BANK", Icons.Filled.AccountBalance),
                    Triple("+ Borrower", "new borrower NAME", Icons.Filled.PersonAdd),
                    Triple("+ Card", "new card BANK limit 50k", Icons.Filled.CreditCard),
                    Triple("Transfer", "trf 5000 from BANK to BANK", Icons.Filled.SwapHoriz)
                )
                q.startsWith("create") || q.startsWith("new") || q.startsWith("add") -> listOf(
                    Triple("Bank Account", "$q bank ", Icons.Filled.AccountBalance),
                    Triple("Borrower (Person)", "$q borrower ", Icons.Filled.Person),
                    Triple("Lender (Person)", "$q lender ", Icons.Filled.PersonOutline),
                    Triple("Credit Card", "$q card ", Icons.Filled.CreditCard),
                    Triple("Goal Pot", "$q goal ", Icons.Filled.Savings)
                )
                q.contains("monthly") || q.contains("weekly") || q.contains("every") -> listOf(
                    Triple("From 5th", "${naturalLanguageInput.text.trim()} from 5th", Icons.Filled.CalendarMonth),
                    Triple("From 1st", "${naturalLanguageInput.text.trim()} from 1st", Icons.Filled.CalendarMonth),
                    Triple("From 10th", "${naturalLanguageInput.text.trim()} from 10th", Icons.Filled.CalendarMonth)
                )
                else -> QuickBarSuggester.evaluate(naturalLanguageInput.text, rawPockets).map {
                    Triple(it.title, it.template, it.icon)
                }
            }
        }
    }

    val groupedRecords = remember(completedTransactions) {
        val calNow = Calendar.getInstance()
        completedTransactions.take(20).groupBy { flow ->
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

    fun verifyLiquidAccountOrPrompt(onAccountReady: (Long) -> Unit) {
        val existingLiquid = rawPockets.firstOrNull { it.pocketType == PocketType.LIQUID }
        if (existingLiquid != null) {
            onAccountReady(existingLiquid.id)
        } else {
            pendingActionAfterAccountCreation = onAccountReady
            prefilledCreatePocketName = ""
            prefilledCreatePocketType = PocketType.LIQUID
            showCreatePocketDialog = true
            alertManager.showAlert("Please create your primary bank/cash account first", AlertType.INFO)
        }
    }

    fun executeQuickBarCommand(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val lower = trimmed.lowercase()
        when {
            lower in listOf("autosplit on", "enable autosplit", "autosplit 1") -> {
                autoSplitEnabled = true
                prefs.edit().putBoolean("auto_split_debit", true).apply()
                alertManager.showAlert("Cross-Account Auto-Split: ENABLED", AlertType.SUCCESS)
                naturalLanguageInput = TextFieldValue("")
                return
            }
            lower in listOf("autosplit off", "disable autosplit", "autosplit 0") -> {
                autoSplitEnabled = false
                prefs.edit().putBoolean("auto_split_debit", false).apply()
                alertManager.showAlert("Cross-Account Auto-Split: DISABLED", AlertType.INFO)
                naturalLanguageInput = TextFieldValue("")
                return
            }
            lower in listOf("autosplit toggle", "/autosplit") -> {
                autoSplitEnabled = !autoSplitEnabled
                prefs.edit().putBoolean("auto_split_debit", autoSplitEnabled).apply()
                alertManager.showAlert("Auto-Split: ${if (autoSplitEnabled) "ENABLED" else "DISABLED"}", AlertType.SUCCESS)
                naturalLanguageInput = TextFieldValue("")
                return
            }
            lower in listOf("phantom lock on", "phantom on", "lock cc on") -> {
                phantomLockEnabled = true
                prefs.edit().putBoolean("phantom_lock_enabled", true).apply()
                alertManager.showAlert("Credit Card Phantom Lock: ACTIVE", AlertType.SUCCESS)
                naturalLanguageInput = TextFieldValue("")
                return
            }
            lower in listOf("phantom lock off", "phantom off") -> {
                phantomLockEnabled = false
                prefs.edit().putBoolean("phantom_lock_enabled", false).apply()
                alertManager.showAlert("Credit Card Phantom Lock: OFF", AlertType.INFO)
                naturalLanguageInput = TextFieldValue("")
                return
            }
            lower in listOf("phantom lock toggle", "/phantom") -> {
                phantomLockEnabled = !phantomLockEnabled
                prefs.edit().putBoolean("phantom_lock_enabled", phantomLockEnabled).apply()
                alertManager.showAlert("Phantom Lock: ${if (phantomLockEnabled) "ACTIVE" else "OFF"}", AlertType.SUCCESS)
                naturalLanguageInput = TextFieldValue("")
                return
            }
            lower in listOf("privacy on", "hide balances", "mask") -> {
                isPrivacyMode = true
                alertManager.showAlert("Privacy Mode: Masked", AlertType.INFO)
                naturalLanguageInput = TextFieldValue("")
                return
            }
            lower in listOf("privacy off", "show balances", "unmask") -> {
                isPrivacyMode = false
                alertManager.showAlert("Privacy Mode: Visible", AlertType.INFO)
                naturalLanguageInput = TextFieldValue("")
                return
            }
            lower.startsWith("theme ") -> {
                val themeName = lower.removePrefix("theme ").trim()
                val targetMode = when {
                    themeName.contains("olive") || themeName.contains("matcha") -> AppThemeMode.OLIVE_MATCHA
                    themeName.contains("nordic") || themeName.contains("slate") -> AppThemeMode.NORDIC_SLATE
                    else -> AppThemeMode.AMBER_OCHRE
                }
                activeThemeMode = targetMode
                prefs.edit().putString("selected_theme", targetMode.name).apply()
                alertManager.showAlert("Applied theme: ${targetMode.name.replace("_", " ")}", AlertType.SUCCESS)
                naturalLanguageInput = TextFieldValue("")
                return
            }
            lower in listOf("export pdf", "/export pdf") -> {
                scope.launch { PdfDossierExporter.generateAndShareDossier(context, pocketBalances, flowRecords) }
                naturalLanguageInput = TextFieldValue("")
                return
            }
            lower in listOf("export csv", "/export csv") -> {
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
                naturalLanguageInput = TextFieldValue("")
                return
            }
            lower in listOf("backup now", "/backup") -> {
                backupExportLauncher.launch("inout_vault_backup_${System.currentTimeMillis()}.vault")
                naturalLanguageInput = TextFieldValue("")
                return
            }
        }

        val parsed = NaturalLanguageParser.parse(trimmed, rawPockets, context)
        if (parsed == null) {
            alertManager.showAlert("Syntax not recognized. Type / for commands.", AlertType.WARNING)
            return
        }

        scope.launch {
            when (parsed) {
                is ParsedIntent.CreateAccount -> {
                    db.stateFlowDao().insertPocket(
                        VaultPocket(
                            name = parsed.name,
                            pocketType = parsed.type,
                            subType = parsed.type.name,
                            creditLimit = 0.0,
                            targetAmount = 0.0,
                            targetDateEpoch = 0L
                        )
                    )
                    db.stateFlowDao().insertNotice(
                        SystemNotice(
                            title = "Account Created",
                            message = "Created new ${parsed.type.name.lowercase()} ledger: '${parsed.name}'",
                            type = "LEDGER_SYSTEM",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    alertManager.showAlert("Account '${parsed.name}' created", AlertType.SUCCESS)
                    naturalLanguageInput = TextFieldValue("")
                }
                is ParsedIntent.MissingAccountError -> {
                    prefilledCreatePocketName = parsed.missingAccountName
                    prefilledCreatePocketType = PocketType.LIQUID
                    showCreatePocketDialog = true
                    alertManager.showAlert("Account '${parsed.missingAccountName}' does not exist. Please confirm creation.", AlertType.WARNING)
                }
                is ParsedIntent.PeerNotFoundError -> {
                    alertManager.showAlert("Cannot ${parsed.action}: No contact named '${parsed.peerName}' found", AlertType.ERROR)
                }
                is ParsedIntent.SetDailyBurn -> {
                    dailyBurnCeiling = parsed.newRate
                    prefs.edit().putFloat("daily_burn_ceiling", parsed.newRate.toFloat()).apply()
                    alertManager.showAlert("Daily Burn set to ₹${parsed.newRate.toInt()}/day", AlertType.SUCCESS)
                    naturalLanguageInput = TextFieldValue("")
                }
                is ParsedIntent.StageDesire -> {
                    db.stateFlowDao().insertStagedDesire(StagedDesire(name = parsed.name, amount = parsed.amount))
                    alertManager.showAlert("Staged '${parsed.name}' in cool-off quarantine", AlertType.INFO)
                    naturalLanguageInput = TextFieldValue("")
                }
                is ParsedIntent.TriangularSettle -> {
                    when (val res = ledgerEngine.triangularPeerSettle(parsed.debtor, parsed.creditor, parsed.amount)) {
                        is VaultExecutionResult.OverdraftError -> alertManager.showAlert(res.message, AlertType.ERROR)
                        is VaultExecutionResult.Success -> {
                            alertManager.showAlert(res.summary, AlertType.SUCCESS)
                            naturalLanguageInput = TextFieldValue("")
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
                        alertManager.showAlert("Pot '${goal.name}' broken. Returned ₹${bal.toInt()}", AlertType.SUCCESS)
                        naturalLanguageInput = TextFieldValue("")
                    }
                }
                is ParsedIntent.Transaction -> {
                    verifyLiquidAccountOrPrompt { validLiquidId ->
                        scope.launch {
                            var sourceId: Long? = parsed.matchedPocketId
                            var targetId: Long? = parsed.targetPocketId

                            if (parsed.nature == MovementNature.INFLOW) {
                                sourceId = null
                                targetId = parsed.targetPocketId ?: parsed.matchedPocketId ?: validLiquidId
                            }

                            if (parsed.nature == MovementNature.OUTFLOW && sourceId == null) {
                                sourceId = validLiquidId
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

                                val liquidId = sourceId ?: validLiquidId
                                when (parsed.nature) {
                                    MovementNature.PEER_LEND -> {
                                        sourceId = liquidId
                                        targetId = personPocket.id
                                    }
                                    MovementNature.PEER_COLLECT -> {
                                        sourceId = personPocket.id
                                        targetId = liquidId
                                    }
                                    MovementNature.PEER_BORROW -> {
                                        sourceId = personPocket.id
                                        targetId = liquidId
                                    }
                                    MovementNature.PEER_REPAY -> {
                                        sourceId = liquidId
                                        targetId = personPocket.id
                                    }
                                    else -> {}
                                }
                            }

                            if (parsed.isRecurring) {
                                db.stateFlowDao().insertFlowRecord(
                                    FlowRecord(
                                        id = 0L,
                                        sourcePocketId = sourceId,
                                        targetPocketId = targetId,
                                        amount = parsed.amount,
                                        nature = parsed.nature,
                                        category = parsed.category,
                                        note = parsed.merchant,
                                        timestamp = parsed.timestamp,
                                        isRecurring = true,
                                        frequency = parsed.frequency,
                                        recurringCadence = parsed.frequency,
                                        isPaused = false
                                    )
                                )

                                val caughtUp = ledgerEngine.catchUpRecurringRules()
                                if (caughtUp > 0) {
                                    alertManager.showAlert("Scheduled rule saved and initial transaction recorded!", AlertType.SUCCESS)
                                } else {
                                    val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(parsed.timestamp))
                                    alertManager.showAlert("Scheduled ${parsed.frequency.lowercase()} rule starting $dateStr", AlertType.SUCCESS)
                                }
                                naturalLanguageInput = TextFieldValue("")
                                return@launch
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
                                isRecurring = false,
                                frequency = parsed.frequency
                            )) {
                                is VaultExecutionResult.OverdraftError -> alertManager.showAlert(res.message, AlertType.ERROR)
                                is VaultExecutionResult.Success -> {
                                    alertManager.showAlert(res.summary, AlertType.SUCCESS)
                                    naturalLanguageInput = TextFieldValue("")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

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
                                onClick = {
                                    selectedTab = idx
                                    isFabExpanded = false
                                },
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
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                        Icon(Icons.Default.EditNote, contentDescription = "Manual Entry", modifier = Modifier.size(22.dp))
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
                                        Icon(Icons.Default.PhotoCamera, contentDescription = "Camera OCR", modifier = Modifier.size(20.dp))
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
                                        Icon(Icons.Default.Image, contentDescription = "Gallery OCR", modifier = Modifier.size(20.dp))
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
                            Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                                    leftSub = "Burn Target: ₹${dailyBurnCeiling.toInt()}/day ✎",
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
                                                    leftSub = if (peerNet >= 0.0) "You are net lender" else "You are net borrower",
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
                                            repeat(4) { idx ->
                                                val isSel = pagerState.currentPage == idx
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

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = theme.accent.copy(alpha = 0.45f))
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(theme.surface)
                                            .border(1.5.dp, theme.accent.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                                    ) {
                                        Column {
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
                                                    if (naturalLanguageInput.text.isEmpty()) {
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
                                                        keyboardActions = KeyboardActions(onDone = { executeQuickBarCommand(naturalLanguageInput.text) }),
                                                        onTextLayout = { textLayoutResult = it },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .focusRequester(quickBarFocusRequester)
                                                            .pointerInput(naturalLanguageInput.text) {
                                                                detectTapGestures(
                                                                    onTap = { offset ->
                                                                        quickBarFocusRequester.requestFocus()
                                                                        val layout = textLayoutResult ?: return@detectTapGestures
                                                                        val tapPosition = layout.getOffsetForPosition(offset)
                                                                        val text = naturalLanguageInput.text
                                                                        if (text.isNotEmpty() && tapPosition in text.indices) {
                                                                            var start = tapPosition
                                                                            while (start > 0 && !text[start - 1].isWhitespace()) {
                                                                                start--
                                                                            }
                                                                            var end = tapPosition
                                                                            while (end < text.length && !text[end].isWhitespace()) {
                                                                                end++
                                                                            }
                                                                            naturalLanguageInput = naturalLanguageInput.copy(selection = TextRange(start, end))
                                                                        }
                                                                    },
                                                                    onDoubleTap = { offset ->
                                                                        quickBarFocusRequester.requestFocus()
                                                                        val layout = textLayoutResult ?: return@detectTapGestures
                                                                        val tapPosition = layout.getOffsetForPosition(offset)
                                                                        naturalLanguageInput = naturalLanguageInput.copy(selection = TextRange(tapPosition, tapPosition))
                                                                    }
                                                                )
                                                            }
                                                    )
                                                }

                                                if (naturalLanguageInput.text.isNotBlank()) {
                                                    IconButton(onClick = { executeQuickBarCommand(naturalLanguageInput.text) }) {
                                                        Icon(Icons.Default.Send, contentDescription = "Commit", tint = theme.accent, modifier = Modifier.size(20.dp))
                                                    }
                                                }
                                            }

                                            if (quickBarCommandPills.isNotEmpty()) {
                                                HorizontalDivider(color = theme.surfaceAlt.copy(alpha = 0.6f), thickness = 0.8.dp)
                                                LazyRow(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    items(quickBarCommandPills) { (pillTitle, pillTemplate, pillIcon) ->
                                                        Row(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(theme.surfaceAlt)
                                                                .clickable {
                                                                    val t = pillTemplate
                                                                    val placeholderTarget = listOf("BANK", "NAME", "<amount>", "<bank>", "<person>")
                                                                        .firstOrNull { t.contains(it) }

                                                                    if (placeholderTarget != null) {
                                                                        val start = t.indexOf(placeholderTarget)
                                                                        val end = start + placeholderTarget.length
                                                                        naturalLanguageInput = TextFieldValue(t, selection = TextRange(start, end))
                                                                    } else {
                                                                        naturalLanguageInput = TextFieldValue(t, selection = TextRange(t.length))
                                                                    }
                                                                    quickBarFocusRequester.requestFocus()
                                                                }
                                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                        ) {
                                                            Icon(pillIcon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(13.dp))
                                                            Text(pillTitle, color = theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Recent Flow", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        if (completedTransactions.isNotEmpty()) {
                                            Text(
                                                "View All (${completedTransactions.size}) →",
                                                color = theme.accent,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable { showAllRecordsSheet = true }
                                            )
                                        }
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    contentPadding = PaddingValues(bottom = 96.dp)
                                ) {
                                    if (completedTransactions.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("No records yet. Type above or tap '+' to commit flow.", color = theme.textMuted, fontSize = 12.sp)
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
                                                HorizontalDivider(color = theme.surfaceAlt.copy(alpha = 0.4f), thickness = 0.5.dp)
                                            }
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
                                        amount = amt ?: 0.0,
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
                                        amount = amt ?: 0.0,
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
                            recurringSchedules = recurringTemplates,
                            stagedDesires = stagedDesires,
                            dailyBurnCeiling = dailyBurnCeiling,
                            trueSafeLiquid = trueSafeLiquid,
                            isPrivacyMode = isPrivacyMode,
                            theme = theme
                        )

                        3 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 12.dp)
                                ) {
                                    Text(
                                        text = "Settings & Vault Controls",
                                        color = theme.textBright,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                SettingsCardsList(
                                    theme = theme,
                                    currentSha = currentInstalledSha,
                                    lastUpdatedDate = lastInstalledTimeFormatted,
                                    autoSplitEnabled = autoSplitEnabled,
                                    phantomLockEnabled = phantomLockEnabled,
                                    activeThemeMode = activeThemeMode,
                                    isProUnlocked = isProUnlocked,
                                    availableUpdate = availableUpdateInfo,
                                    isCheckingUpdate = isCheckingForUpdate,
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
                                    onInstallUpdate = { info ->
                                        AppUpdateEngine.startDownloadAndInstall(context, info.downloadUrl, info.latestVersion)
                                        alertManager.showAlert("Downloading update ${info.latestVersion}...", AlertType.INFO)
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
                    }
                }

                if (isFabExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { isFabExpanded = false }
                    )
                }

                if (showNoticesSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showNoticesSheet = false },
                        containerColor = theme.surface,
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("System & Ledger Notices", color = theme.textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                if (systemNotices.isNotEmpty()) {
                                    TextButton(onClick = {
                                        scope.launch { db.stateFlowDao().clearAllNotices() }
                                    }) {
                                        Text("Clear All", color = theme.mildRed, fontSize = 12.sp)
                                    }
                                }
                            }

                            if (systemNotices.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No system notifications logged.", color = theme.textMuted, fontSize = 12.5.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(systemNotices, key = { it.id }) { notice ->
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = theme.surfaceAlt),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(notice.title, color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(notice.timestamp))
                                                    Text(timeStr, color = theme.textMuted, fontSize = 10.sp)
                                                }
                                                Text(notice.message, color = theme.textBright, fontSize = 11.5.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
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
                    var verificationInput by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = {
                            showClearLedgerConfirmation = false
                            verificationInput = ""
                        },
                        containerColor = theme.surface,
                        title = { Text("CONFIRM TOTAL PURGE", color = theme.mildRed, fontWeight = FontWeight.Black) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "This action will permanently delete all records AND archive all accounts. To verify this destructive action, type PURGE below:",
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
                                            alertManager.showAlert("All vault records and accounts purged", AlertType.SUCCESS)
                                            showClearLedgerConfirmation = false
                                            verificationInput = ""
                                        }
                                    } else {
                                        alertManager.showAlert("Verification phrase did not match 'PURGE'", AlertType.ERROR)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed),
                                enabled = verificationInput.trim() == "PURGE"
                            ) { Text("Purge Everything", color = Color.White, fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showClearLedgerConfirmation = false
                                verificationInput = ""
                            }) { Text("Cancel", color = theme.textMuted) }
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
                            verifyLiquidAccountOrPrompt { validLiquidId ->
                                scope.launch {
                                    val effectiveSrc = if (nature == MovementNature.OUTFLOW && srcId == null) validLiquidId else srcId
                                    val effectiveTgt = if (nature == MovementNature.INFLOW && tgtId == null) validLiquidId else tgtId

                                    if (isRec) {
                                        db.stateFlowDao().insertFlowRecord(
                                            FlowRecord(
                                                id = 0L,
                                                sourcePocketId = effectiveSrc,
                                                targetPocketId = effectiveTgt,
                                                amount = amt,
                                                nature = nature,
                                                category = cat,
                                                note = note,
                                                timestamp = date,
                                                isRecurring = true,
                                                frequency = freq,
                                                recurringCadence = freq,
                                                isPaused = false
                                            )
                                        )

                                        val caughtUp = ledgerEngine.catchUpRecurringRules()
                                        hudInDialogError = null
                                        showCommandHud = false
                                        if (caughtUp > 0) {
                                            alertManager.showAlert("Recurring schedule saved and initial transaction recorded!", AlertType.SUCCESS)
                                        } else {
                                            alertManager.showAlert("Recurring schedule saved", AlertType.SUCCESS)
                                        }
                                        return@launch
                                    }

                                    when (val res = ledgerEngine.recordMovement(
                                        nature = nature,
                                        sourcePocketId = effectiveSrc,
                                        targetPocketId = effectiveTgt,
                                        amount = amt,
                                        category = cat,
                                        note = note,
                                        timestamp = date,
                                        autoSplitEnabled = autoSplitEnabled,
                                        isRecurring = false,
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
                        title = { Text("Confirm Deletion", color = Color.White) },
                        text = { Text("Delete entry of ₹$delAmt for '${flow.note.ifBlank { flow.category }}'? Balances will adjust.", color = Color(0xFFCCCCCC)) },
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
                        flowRecords = completedTransactions,
                        isPrivacyMode = isPrivacyMode,
                        isProUser = isProUnlocked,
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

                if (showCreatePocketDialog) {
                    CreateAccountDialog(
                        initialName = prefilledCreatePocketName,
                        initialType = prefilledCreatePocketType,
                        theme = theme,
                        onDismiss = {
                            showCreatePocketDialog = false
                            prefilledCreatePocketName = ""
                            pendingActionAfterAccountCreation = null
                        },
                        onSave = { name, type, limit, targetAmt, targetDateEpoch ->
                            scope.launch {
                                val newId = db.stateFlowDao().insertPocket(
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

                                pendingActionAfterAccountCreation?.invoke(newId)
                                pendingActionAfterAccountCreation = null
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
        modifier = Modifier.fillMaxWidth().clickable { onCardClick() }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tag, color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPositive) theme.mildGreen.copy(alpha = 0.2f) else theme.mildRed.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        status,
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
                Text(leftSub, color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(rightSub, color = theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            Text(flow.note.ifBlank { flow.category }, color = theme.textBright, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            Text("${flow.category} • $accountName", color = theme.textMuted, fontSize = 10.5.sp)
        }

        val amtVal = flow.amount ?: 0.0
        val amtStr = if (isPrivacyMode) "₹ •••" else "${if (isOut) "-" else "+"}₹${String.format("%,.0f", amtVal)}"
        Text(amtStr, color = flowColor, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
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
                                Text("$unreadNoticeCount", color = Color.White, fontSize = 9.sp)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notices",
                        tint = if (unreadNoticeCount > 0) theme.accent else theme.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
    onCheckUpdate: () -> Unit,
    onInstallUpdate: (UpdateInfo) -> Unit,
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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ABOUT INOUT", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("InOut Vault (Alpha)", color = theme.textBright, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text("Current Build: ${currentSha.take(7)}", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Last Installed: $lastUpdatedDate", color = theme.textMuted, fontSize = 10.5.sp)
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("APPLICATION UPDATES & SUPPORT", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    if (availableUpdate != null && availableUpdate.hasUpdate) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.accent.copy(alpha = 0.15f))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("New Version Available: ${availableUpdate.latestVersion}", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(availableUpdate.releaseNotes, color = theme.textBright, fontSize = 10.5.sp, maxLines = 2)
                            }
                        }

                        Button(
                            onClick = { onInstallUpdate(availableUpdate) },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = theme.bg, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download & Install ${availableUpdate.latestVersion}", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onCheckUpdate,
                            enabled = !isCheckingUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = theme.textBright, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isCheckingUpdate) "Checking Alpha Releases..." else "Check for App Update", color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenFeedback,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Send Feedback / Report Bug", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("LEDGER GUARDS", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Cross-Account Auto-Split", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Auto-debit secondary bank accounts if primary account lacks liquid funds.", color = theme.textMuted, fontSize = 10.5.sp)
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
                            Text("Credit Card Phantom Lock", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Isolate unbilled card dues from liquid balance to prevent phantom runway.", color = theme.textMuted, fontSize = 10.5.sp)
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
                    Text("WORKSPACE & THEME", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppThemeMode.entries.forEach { mode ->
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
                                Text(mode.name.replace("_", " "), color = if (isSel) theme.bg else theme.textBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DATA SOVEREIGNTY & BACKUP", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Button(
                        onClick = onExportEncryptedBackup,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create Encrypted Backup (.vault)", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onRestoreEncryptedBackup,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Restore Ledger from Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = theme.surfaceAlt, thickness = 0.5.dp)

                    Button(
                        onClick = onExportPdf,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export PDF Ledger Dossier", color = theme.textBright, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onExportCsv,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export CSV Ledger", color = theme.textBright, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onClearLedger,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Entire Ledger History", color = theme.mildRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SANDBOX & INOUT PRO", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Current Tier", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (isProUnlocked) "Pro License Active" else "Free Sandbox Mode", color = if (isProUnlocked) theme.accent else theme.textMuted, fontSize = 11.sp)
                        }
                        Button(
                            onClick = onOpenPaywall,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(if (isProUnlocked) "Manage Pro" else "View Pro", color = theme.bg, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                        .clickable { showDatePicker = true }
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
                    val amt = amountText.toDoubleOrNull() ?: (rule.amount ?: 0.0)
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
                val amtDisplay = (record.amount ?: 0.0).toInt()
                Text("Amount: ₹$amtDisplay", color = theme.textMuted, fontSize = 12.sp)
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
    var targetDateEpoch by remember { mutableStateOf(System.currentTimeMillis() + (90L * 24 * 3600 * 1000L)) }
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
                            .clickable { showDatePicker = true }
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
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Long) -> Unit
) {
    val accLimit = account.creditLimit ?: 0.0
    val accTarget = account.targetAmount ?: 0.0

    var name by remember { mutableStateOf(account.name) }
    var limit by remember { mutableStateOf(if (accLimit > 0.0) String.format("%.0f", accLimit) else "") }
    var targetAmt by remember { mutableStateOf(if (accTarget > 0.0) String.format("%.0f", accTarget) else "") }
    var targetDateEpoch by remember { mutableStateOf(if (account.targetDateEpoch > 0) account.targetDateEpoch else System.currentTimeMillis() + (90L * 24 * 3600 * 1000L)) }
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
                            .clickable { showDatePicker = true }
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
                        limit.toDoubleOrNull() ?: accLimit,
                        targetAmt.toDoubleOrNull() ?: accTarget,
                        targetDateEpoch
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) { Text("Save Changes", color = theme.bg, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } }
    )
}
