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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.personal.inout.billing.PlayBillingManager
import com.personal.inout.data.*
import com.personal.inout.ocr.ReceiptScanner
import com.personal.inout.util.CsvExporter
import com.personal.inout.util.InAppUpdateHelper
import com.personal.inout.util.PdfDossierGenerator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

enum class CockpitStyle(val label: String) {
    BATTERY_EQUALIZER("Battery & Equalizer"),
    ECLIPSE_SPOTLIGHT("Eclipse & Spotlight"),
    VAULT_ORBIT("Vault & Orbit")
}

data class DisplayTransaction(
    val id: Long,
    val timestamp: Long,
    val description: String,
    val categoryName: String,
    val accountName: String,
    val amount: Double,
    val flowType: String,
    val isRecurring: Boolean,
    val frequency: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE) }
    val activity = context as? Activity

    LaunchedEffect(Unit) {
        activity?.let { InAppUpdateHelper.checkForUpdate(it) }
    }

    val billingManager = remember { PlayBillingManager(context, scope) }
    val isProUnlocked by billingManager.isProUnlocked.collectAsState()

    var activeThemeMode by remember {
        val savedTheme = prefs.getString("selected_theme", AppThemeMode.AMBER_OCHRE.name)
        mutableStateOf(AppThemeMode.valueOf(savedTheme ?: AppThemeMode.AMBER_OCHRE.name))
    }

    var activeCockpitStyle by remember {
        val savedCockpit = prefs.getString("cockpit_style", CockpitStyle.BATTERY_EQUALIZER.name)
        mutableStateOf(CockpitStyle.valueOf(savedCockpit ?: CockpitStyle.BATTERY_EQUALIZER.name))
    }

    val theme = when (activeThemeMode) {
        AppThemeMode.AMBER_OCHRE -> AmberTheme
        AppThemeMode.OLIVE_MATCHA -> OliveMatchaTheme
        AppThemeMode.SAND_DUNE -> SandDuneTheme
    }

    val accountsWithBalances by db.ledgerDao().observeAccountBalances().collectAsState(initial = emptyList())
    val rawAccounts by db.ledgerDao().getAllActiveAccounts().collectAsState(initial = emptyList())
    val transactionRows by db.ledgerDao().observeTransactionDisplayRows().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) }
    var isPrivacyMode by remember { mutableStateOf(false) }
    var showUnifiedEntrySheet by remember { mutableStateOf(false) }
    var entryTargetAccountId by remember { mutableStateOf<Long?>(null) }

    var showCreateCardDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<LedgerAccount?>(null) }
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
                    entryTargetAccountId = null
                    showUnifiedEntrySheet = true
                } catch (e: Exception) {
                    Toast.makeText(context, "OCR parse failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraSnapLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission needed for receipt OCR", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val parsed = ReceiptScanner.processReceipt(context, uri)
                    ocrPrefilledNote = parsed.merchant
                    ocrPrefilledAmount = parsed.total
                    entryTargetAccountId = null
                    showUnifiedEntrySheet = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Receipt parse failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val displayTransactions = remember(transactionRows) {
        transactionRows.map { row ->
            DisplayTransaction(
                id = row.id,
                timestamp = row.timestamp,
                description = row.description,
                categoryName = row.categoryOrAccount,
                accountName = row.categoryOrAccount,
                amount = row.amount,
                flowType = if (row.description.startsWith("Deposit") || row.description.startsWith("Income")) "IN" else "OUT",
                isRecurring = row.isRecurring,
                frequency = row.recurringFrequency
            )
        }
    }

    val totalIn = remember(accountsWithBalances) {
        accountsWithBalances.filter { it.classification == AccountClassification.REVENUE }.sumOf { it.netBalance }
    }
    val totalOut = remember(accountsWithBalances) {
        accountsWithBalances.filter { it.classification == AccountClassification.EXPENSE }.sumOf { it.netBalance }
    }

    CompositionLocalProvider(LocalThemeColors provides theme) {
        Scaffold(
            containerColor = theme.bg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    DashboardHeader(
                        totalIn = totalIn,
                        totalOut = totalOut,
                        isProUser = isProUnlocked,
                        isPrivacyMode = isPrivacyMode,
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
                    modifier = Modifier
                        .navigationBarsPadding()
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                ) {
                    val navItems = listOf(
                        Triple(0, "Ledger", Icons.Filled.MenuBook),
                        Triple(1, "Accounts", Icons.Filled.AccountBalance),
                        Triple(2, "Insights", Icons.Filled.Insights),
                        Triple(3, "Settings", Icons.Filled.Settings)
                    )

                    navItems.forEach { (index, title, icon) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icon, contentDescription = title) },
                            label = {
                                Text(
                                    title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
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
                if (selectedTab == 0) {
                    FloatingActionButton(
                        onClick = {
                            ocrPrefilledNote = ""
                            ocrPrefilledAmount = null
                            entryTargetAccountId = null
                            showUnifiedEntrySheet = true
                        },
                        containerColor = theme.accent,
                        contentColor = theme.bg,
                        shape = CircleShape,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Entry", modifier = Modifier.size(28.dp))
                    }
                } else if (selectedTab == 1) {
                    FloatingActionButton(
                        onClick = { showCreateCardDialog = true },
                        containerColor = theme.accent,
                        contentColor = theme.bg,
                        shape = CircleShape,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = "Add Account", modifier = Modifier.size(26.dp))
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
                    0 -> LedgerTabScreen(
                        accountsWithBalances = accountsWithBalances,
                        displayTransactions = displayTransactions,
                        cockpitStyle = activeCockpitStyle,
                        isPrivacyMode = isPrivacyMode,
                        onScanReceipt = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                cameraSnapLauncher.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onPickReceiptImage = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onViewAllClick = { showAllRecordsSheet = true }
                    )

                    1 -> AccountsListView(
                        accountsWithBalances = accountsWithBalances,
                        rawAccounts = rawAccounts,
                        isPrivacyMode = isPrivacyMode,
                        onAddCardClick = { showCreateCardDialog = true },
                        onEditAccount = { editingAccount = it },
                        onDeleteAccountWithUndo = { accToDelete ->
                            scope.launch {
                                db.ledgerDao().updateAccount(accToDelete.copy(isArchived = true))
                                val res = snackbarHostState.showSnackbar(
                                    message = "${accToDelete.name} deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (res == SnackbarResult.ActionPerformed) {
                                    db.ledgerDao().updateAccount(accToDelete.copy(isArchived = false))
                                }
                            }
                        },
                        onQuickDepositOrRefill = { account ->
                            entryTargetAccountId = account.id
                            ocrPrefilledNote = "Deposit"
                            ocrPrefilledAmount = null
                            showUnifiedEntrySheet = true
                        }
                    )

                    2 -> {
                        val transactionsForInsights by db.ledgerDao().observeAllTransactions().collectAsState(initial = emptyList())
                        IntelligenceScreen(
                            accountsWithBalances = accountsWithBalances,
                            rawAccounts = rawAccounts,
                            transactions = transactionsForInsights,
                            isProUser = isProUnlocked,
                            onUnlockPro = { showMockPaywall = true },
                            onGeneratePdfDossier = {
                                PdfDossierGenerator.generateAndShare(context, accountsWithBalances, transactionsForInsights, isProUnlocked)
                            }
                        )
                    }

                    3 -> SettingsScreen(
                        currentTheme = activeThemeMode,
                        currentCockpit = activeCockpitStyle,
                        isProUser = isProUnlocked,
                        onSelectTheme = { mode ->
                            activeThemeMode = mode
                            prefs.edit().putString("selected_theme", mode.name).apply()
                        },
                        onSelectCockpit = { style ->
                            activeCockpitStyle = style
                            prefs.edit().putString("cockpit_style", style.name).apply()
                        },
                        onTriggerProPurchase = { showMockPaywall = true },
                        onClearLedger = {
                            scope.launch {
                                val txList = transactionRows
                                txList.forEach { db.ledgerDao().deleteTransactionById(it.id) }
                                snackbarHostState.showSnackbar("All ledger records cleared")
                            }
                        }
                    )
                }
            }

            if (showUnifiedEntrySheet) {
                UnifiedEntrySheet(
                    allAccounts = rawAccounts,
                    isProUser = isProUnlocked,
                    prefilledAccountId = entryTargetAccountId,
                    prefilledNote = ocrPrefilledNote,
                    prefilledAmount = ocrPrefilledAmount,
                    onDismiss = { showUnifiedEntrySheet = false },
                    onSubmitExpenseIncome = { isExpense, assetAccountId, category, amount, note, date, isRec, freq ->
                        scope.launch {
                            db.ledgerDao().postExpenseOrIncome(
                                title = note,
                                amount = amount,
                                timestamp = date,
                                assetAccountId = assetAccountId,
                                categoryName = category,
                                isExpense = isExpense,
                                isRecurring = isRec,
                                frequency = freq
                            )
                            showUnifiedEntrySheet = false
                        }
                    },
                    onSubmitTransfer = { fromId, toId, amount, note, date ->
                        scope.launch {
                            val tx = LedgerTransaction(
                                timestamp = date,
                                description = note.ifBlank { "Intra-Account Transfer" }
                            )
                            db.ledgerDao().recordBalancedPosting(tx, toId, fromId, amount, "Transfer")
                            showUnifiedEntrySheet = false
                        }
                    }
                )
            }

            if (showCreateCardDialog) {
                CreateAccountCardDialog(
                    availableSourceAccounts = rawAccounts.filter { it.classification == AccountClassification.ASSET }.map {
                        Account(id = it.id, name = it.name, balance = 0.0, type = it.subType, totalLimit = it.creditLimit, dueDate = it.dueDateMillis)
                    },
                    onDismiss = { showCreateCardDialog = false },
                    onSave = { acc, _, _, _ ->
                        scope.launch {
                            val classification = when (acc.type) {
                                "CASH", "BANK", "BORROWER" -> AccountClassification.ASSET
                                "CREDIT", "LENDER" -> AccountClassification.LIABILITY
                                else -> AccountClassification.ASSET
                            }
                            db.ledgerDao().insertAccount(
                                LedgerAccount(
                                    name = acc.name,
                                    classification = classification,
                                    subType = acc.type,
                                    creditLimit = acc.totalLimit,
                                    dueDateMillis = acc.dueDate
                                )
                            )
                            showCreateCardDialog = false
                        }
                    }
                )
            }

            editingAccount?.let { acc ->
                EditAccountCardDialog(
                    account = Account(
                        id = acc.id,
                        name = acc.name,
                        balance = 0.0,
                        totalLimit = acc.creditLimit,
                        dueDate = acc.dueDateMillis,
                        type = acc.subType
                    ),
                    onDismiss = { editingAccount = null },
                    onSave = { updated, _, _ ->
                        scope.launch {
                            db.ledgerDao().updateAccount(
                                acc.copy(
                                    name = updated.name,
                                    creditLimit = updated.totalLimit,
                                    dueDateMillis = updated.dueDate
                                )
                            )
                            editingAccount = null
                        }
                    },
                    onDelete = {
                        scope.launch {
                            db.ledgerDao().updateAccount(acc.copy(isArchived = true))
                            editingAccount = null
                            snackbarHostState.showSnackbar("${acc.name} archived")
                        }
                    }
                )
            }

            if (showAllRecordsSheet) {
                AllTransactionsSearchSheet(
                    transactions = displayTransactions.map {
                        Transaction(
                            id = it.id,
                            accountId = 0L,
                            flowType = it.flowType,
                            type = if (it.flowType == "IN") "INCOME" else "EXPENSE",
                            category = it.categoryName,
                            amount = it.amount,
                            timestamp = it.timestamp,
                            note = it.description,
                            isRecurring = it.isRecurring,
                            frequency = it.frequency
                        )
                    },
                    onDismiss = { showAllRecordsSheet = false },
                    onExportCsv = {
                        val compatList = displayTransactions.map {
                            Transaction(
                                id = it.id,
                                accountId = 0L,
                                flowType = it.flowType,
                                type = if (it.flowType == "IN") "INCOME" else "EXPENSE",
                                category = it.categoryName,
                                amount = it.amount,
                                timestamp = it.timestamp,
                                note = it.description,
                                isRecurring = it.isRecurring,
                                frequency = it.frequency
                            )
                        }
                        CsvExporter.exportAndShareTransactions(context, compatList)
                    }
                )
            }

            if (showMockPaywall) {
                MockPaywallBottomSheet(
                    currentProState = isProUnlocked,
                    onDismiss = { showMockPaywall = false },
                    onSimulatePurchaseSuccess = {
                        billingManager.simulatePurchaseSuccess()
                    },
                    onSimulateRevokePro = {
                        billingManager.simulateRevokePro()
                    }
                )
            }
        }
    }
}

// ---------------- LEDGER TAB & CANVAS IMPLEMENTATIONS ----------------

@Composable
fun LedgerTabScreen(
    accountsWithBalances: List<AccountBalanceResult>,
    displayTransactions: List<DisplayTransaction>,
    cockpitStyle: CockpitStyle,
    isPrivacyMode: Boolean,
    onScanReceipt: () -> Unit,
    onPickReceiptImage: () -> Unit,
    onViewAllClick: () -> Unit
) {
    val theme = LocalThemeColors.current
    val recentList = displayTransactions.take(8)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp)
    ) {
        item {
            CockpitInsightCard(
                style = cockpitStyle,
                accountsWithBalances = accountsWithBalances,
                isPrivacyMode = isPrivacyMode
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onScanReceipt,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan Receipt", color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onPickReceiptImage,
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
                Text("Recent Ledger Postings", color = theme.textBright, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                if (displayTransactions.isNotEmpty()) {
                    Text(
                        "View All (${displayTransactions.size}) →",
                        color = theme.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onViewAllClick() }
                    )
                }
            }
        }

        if (recentList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = theme.textMuted, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No ledger records yet", color = theme.textBright, fontWeight = FontWeight.SemiBold)
                        Text("Tap '+' to post your first balanced expense or income.", color = theme.textMuted, fontSize = 11.5.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(recentList, key = { it.id }) { item ->
                SlimLedgerDisplayRow(item = item, isPrivacyMode = isPrivacyMode)
                Divider(color = theme.surfaceAlt.copy(alpha = 0.5f), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun CockpitInsightCard(
    style: CockpitStyle,
    accountsWithBalances: List<AccountBalanceResult>,
    isPrivacyMode: Boolean
) {
    val theme = LocalThemeColors.current

    val totalLiquid = remember(accountsWithBalances) {
        accountsWithBalances
            .filter { it.classification == AccountClassification.ASSET && it.subType in listOf("CASH", "BANK") }
            .sumOf { it.netBalance }
    }

    val totalLiabilities = remember(accountsWithBalances) {
        accountsWithBalances
            .filter { it.classification == AccountClassification.LIABILITY || it.subType == "LENDER" }
            .sumOf { it.netBalance }
    }

    val unencumberedCash = (totalLiquid - totalLiabilities).coerceAtLeast(0.0)
    val batteryRatio = if (totalLiquid > 0) (unencumberedCash / totalLiquid).toFloat().coerceIn(0f, 1f) else 0f

    val currentCal = Calendar.getInstance()
    val daysInMonth = currentCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val currentDay = currentCal.get(Calendar.DAY_OF_MONTH)
    val remainingDays = (daysInMonth - currentDay + 1).coerceAtLeast(1)
    val dailySafeBurn = (unencumberedCash / remainingDays)

    val simulated7Days = remember { listOf(450.0, 120.0, 800.0, 0.0, 310.0, 1400.0, 250.0) }

    Card(
        modifier = Modifier.fillMaxWidth().height(156.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (style) {
                CockpitStyle.BATTERY_EQUALIZER -> {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("POCKET RESERVE", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        BatteryGaugeCanvas(ratio = batteryRatio, themeColor = theme.accent, greenColor = theme.mildGreen)
                        Column {
                            val cashStr = if (isPrivacyMode) "₹ ••••••" else "₹ ${String.format("%,.0f", unencumberedCash)}"
                            Text(cashStr, color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Dues safely reserved", color = theme.textMuted, fontSize = 9.5.sp)
                        }
                    }

                    Divider(color = theme.surfaceAlt, modifier = Modifier.fillMaxHeight().width(1.dp).padding(vertical = 4.dp))

                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("WEEKLY RHYTHM", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        EqualizerCanvas(dailySpends = simulated7Days, theme = theme)
                        Text("Quiet week • 1 spike", color = theme.textMuted, fontSize = 9.5.sp)
                    }
                }

                CockpitStyle.ECLIPSE_SPOTLIGHT -> {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("DAILY SPEED LIMIT", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(68.dp)) {
                            EclipseGaugeCanvas(theme = theme)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val burnStr = if (isPrivacyMode) "₹ •••" else "₹${String.format("%.0f", dailySafeBurn)}"
                                Text(burnStr, color = theme.textBright, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                                Text("/ day", color = theme.textMuted, fontSize = 8.5.sp)
                            }
                        }
                        Text("$remainingDays days remaining", color = theme.accent, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Divider(color = theme.surfaceAlt, modifier = Modifier.fillMaxHeight().width(1.dp).padding(vertical = 4.dp))

                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("TOP OUTFLOW LEAK", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(theme.accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = theme.accent, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Food & Dining", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("38% of all spend", color = theme.mildRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("Active category lead", color = theme.textMuted, fontSize = 9.5.sp)
                    }
                }

                CockpitStyle.VAULT_ORBIT -> {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SAVINGS VAULT", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Savings, contentDescription = null, tint = theme.accent, modifier = Modifier.size(32.dp))
                            Column {
                                val savedStr = if (isPrivacyMode) "₹ ••••••" else "+₹ 14,200"
                                Text(savedStr, color = theme.mildGreen, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp)
                                Text("48% retained", color = theme.textMuted, fontSize = 10.sp)
                            }
                        }
                        Text("Surplus accumulating", color = theme.textMuted, fontSize = 9.5.sp)
                    }

                    Divider(color = theme.surfaceAlt, modifier = Modifier.fillMaxHeight().width(1.dp).padding(vertical = 4.dp))

                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("DISCIPLINE ORBIT", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(65.dp)) {
                            OrbitCanvas(dailySpends = simulated7Days, theme = theme)
                        }
                        Text("4 calm days this week", color = theme.mildGreen, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryGaugeCanvas(ratio: Float, themeColor: Color, greenColor: Color) {
    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "battery_ratio"
    )

    Canvas(modifier = Modifier.fillMaxWidth(0.92f).height(24.dp)) {
        val corner = 5.dp.toPx()
        val stroke = 1.5.dp.toPx()
        val capWidth = 4.dp.toPx()
        val capHeight = 10.dp.toPx()
        val shellWidth = size.width - capWidth - 3.dp.toPx()

        drawRoundRect(
            color = themeColor.copy(alpha = 0.5f),
            size = Size(shellWidth, size.height),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = stroke)
        )

        drawRoundRect(
            color = themeColor.copy(alpha = 0.5f),
            topLeft = Offset(shellWidth + 2.dp.toPx(), (size.height - capHeight) / 2),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        val innerPadding = 3.dp.toPx()
        val maxFillWidth = shellWidth - (innerPadding * 2)
        val fillWidth = maxFillWidth * animatedRatio

        if (fillWidth > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(themeColor, greenColor)),
                topLeft = Offset(innerPadding, innerPadding),
                size = Size(fillWidth, size.height - (innerPadding * 2)),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
    }
}

@Composable
fun EqualizerCanvas(dailySpends: List<Double>, theme: ThemeColors) {
    val max = (dailySpends.maxOrNull() ?: 1.0).coerceAtLeast(1.0)

    Canvas(modifier = Modifier.fillMaxWidth().height(46.dp)) {
        val barCount = 7
        val space = 6.dp.toPx()
        val totalSpacing = space * (barCount - 1)
        val barWidth = (size.width - totalSpacing) / barCount

        dailySpends.take(7).forEachIndexed { i, spend ->
            val barHeight = ((spend / max).toFloat() * size.height).coerceAtLeast(4.dp.toPx())
            val x = i * (barWidth + space)
            val y = size.height - barHeight

            val col = when {
                spend == 0.0 -> theme.mildGreen
                spend > (max * 0.7) -> theme.mildRed
                else -> theme.accent
            }

            drawRoundRect(
                color = col,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

@Composable
fun EclipseGaugeCanvas(theme: ThemeColors) {
    Canvas(modifier = Modifier.size(62.dp)) {
        val stroke = 6.dp.toPx()
        drawArc(
            color = theme.surfaceAlt,
            startAngle = 150f,
            sweepAngle = 240f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            brush = Brush.sweepGradient(listOf(theme.mildGreen, theme.accent, theme.mildRed)),
            startAngle = 150f,
            sweepAngle = 160f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun OrbitCanvas(dailySpends: List<Double>, theme: ThemeColors) {
    Canvas(modifier = Modifier.size(58.dp)) {
        val r = size.minDimension / 2.3f
        val center = Offset(size.width / 2, size.height / 2)

        drawCircle(color = theme.surfaceAlt, radius = r, style = Stroke(width = 1.dp.toPx()))

        val count = 7
        for (i in 0 until count) {
            val angle = Math.toRadians((i * (360.0 / count) - 90)).toFloat()
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)

            val isZero = (dailySpends.getOrNull(i) ?: 0.0) == 0.0
            drawCircle(
                color = if (isZero) theme.mildGreen else theme.accent,
                radius = if (isZero) 4.5.dp.toPx() else 3.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun SlimLedgerDisplayRow(item: DisplayTransaction, isPrivacyMode: Boolean) {
    val theme = LocalThemeColors.current
    val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.timestamp))

    val isCredit = item.flowType == "IN"
    val flowColor = if (isCredit) theme.mildGreen else theme.mildRed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = flowColor,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = item.description,
                    color = theme.textBright,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    maxLines = 1
                )
                Text(
                    text = "${item.categoryName}${if (item.isRecurring) " [${item.frequency}]" else ""}",
                    color = theme.textMuted,
                    fontSize = 11.sp
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            val amtText = if (isPrivacyMode) "₹ •••" else "${if (isCredit) "+" else "-"}₹ ${String.format("%.0f", item.amount)}"
            Text(amtText, color = flowColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(dateStr, color = theme.textMuted, fontSize = 10.sp)
        }
    }
}
