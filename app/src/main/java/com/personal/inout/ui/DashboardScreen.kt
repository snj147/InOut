package com.personal.inout.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.personal.inout.data.Account
import com.personal.inout.data.AppDatabase
import com.personal.inout.data.SmsDraft
import com.personal.inout.data.Transaction
import com.personal.inout.ocr.ReceiptScanner
import com.personal.inout.util.CsvExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE) }

    var activeThemeMode by remember {
        val savedTheme = prefs.getString("selected_theme", AppThemeMode.AMBER_OCHRE.name)
        mutableStateOf(AppThemeMode.valueOf(savedTheme ?: AppThemeMode.AMBER_OCHRE.name))
    }

    val theme = when (activeThemeMode) {
        AppThemeMode.AMBER_OCHRE -> AmberTheme
        AppThemeMode.OLIVE_MATCHA -> OliveMatchaTheme
        AppThemeMode.SAND_DUNE -> SandDuneTheme
    }

    val accounts by db.vaultDao().getAllAccounts().collectAsState(initial = emptyList())
    val transactions by db.vaultDao().getAllTransactions().collectAsState(initial = emptyList())
    val stagedSms by db.vaultDao().getStagedSms().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) } // 0: Ledger, 1: Accounts, 2: Review, 3: Settings

    var showMainTxDialog by remember { mutableStateOf(false) }
    var prefilledTx by remember { mutableStateOf<Transaction?>(null) }
    var showCreateCardDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var quickActionAccount by remember { mutableStateOf<Pair<Account, String>?>(null) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var showAllLedgerSheet by remember { mutableStateOf(false) }
    var showAllRecurringSheet by remember { mutableStateOf(false) }

    var itemPendingAction by remember { mutableStateOf<Pair<String, Any>?>(null) } // "ACCOUNT" or "RECURRING"

    // High reliability camera snapshot
    val cameraSnapLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            scope.launch {
                try {
                    val parsed = ReceiptScanner.processReceiptBitmap(bitmap)
                    prefilledTx = Transaction(
                        accountId = accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id ?: 0L,
                        flowType = "OUT",
                        type = "EXPENSE",
                        category = "Shopping",
                        amount = parsed.total,
                        note = parsed.merchant
                    )
                    showMainTxDialog = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Camera OCR failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Camera permission check launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraSnapLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission needed to scan receipts", Toast.LENGTH_SHORT).show()
        }
    }

    // Modern Android Photo Picker (zero storage permissions required)
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val parsed = ReceiptScanner.processReceipt(context, uri)
                    prefilledTx = Transaction(
                        accountId = accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id ?: 0L,
                        flowType = "OUT",
                        type = "EXPENSE",
                        category = "Shopping",
                        amount = parsed.total,
                        note = parsed.merchant
                    )
                    showMainTxDialog = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Receipt parse failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    CompositionLocalProvider(LocalThemeColors provides theme) {
        Scaffold(
            containerColor = theme.bg,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .background(theme.bg)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("InOut", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = theme.textBright)
                            Text("PERSONAL LEDGER", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = theme.accent)
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = theme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                ) {
                    val navItems = listOf(
                        Triple(0, "Ledger", Icons.Filled.MenuBook),
                        Triple(1, "Accounts", Icons.Filled.AccountBalance),
                        Triple(2, "Review", Icons.Filled.Inbox),
                        Triple(3, "Settings", Icons.Filled.Settings)
                    )

                    navItems.forEach { (index, title, icon) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                if (index == 2 && stagedSms.isNotEmpty()) {
                                    BadgedBox(badge = { Badge(containerColor = theme.mildRed) { Text("${stagedSms.size}") } }) {
                                        Icon(icon, contentDescription = title)
                                    }
                                } else {
                                    Icon(icon, contentDescription = title)
                                }
                            },
                            label = { Text(title, fontSize = 11.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
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
                            prefilledTx = null
                            showMainTxDialog = true
                        },
                        containerColor = theme.accent,
                        contentColor = theme.bg,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Entry", modifier = Modifier.size(28.dp))
                    }
                } else if (selectedTab == 1) {
                    FloatingActionButton(
                        onClick = { showCreateCardDialog = true },
                        containerColor = theme.accent,
                        contentColor = theme.bg,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = "Add Card", modifier = Modifier.size(26.dp))
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
                        transactions = transactions,
                        onViewAllClick = { showAllLedgerSheet = true }
                    )
                    1 -> AccountsTabScreen(
                        accounts = accounts,
                        recurringTransactions = transactions.filter { it.isRecurring },
                        onOpenTransfer = { showTransferDialog = true },
                        onQuickAction = { acc, action -> quickActionAccount = Pair(acc, action) },
                        onLongPressAccount = { acc -> itemPendingAction = Pair("ACCOUNT", acc) },
                        onLongPressRecurring = { tx -> itemPendingAction = Pair("RECURRING", tx) },
                        onViewAllRecurring = { showAllRecurringSheet = true }
                    )
                    2 -> ReviewQueueTabScreen(
                        stagedSms = stagedSms,
                        onDiscard = { draftId -> scope.launch { db.vaultDao().deleteSmsDraftById(draftId) } },
                        onConsider = { draft ->
                            prefilledTx = Transaction(
                                accountId = accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id ?: 0L,
                                flowType = "OUT",
                                type = "EXPENSE",
                                category = "Shopping",
                                amount = draft.amount,
                                note = draft.merchant
                            )
                            showMainTxDialog = true
                            scope.launch { db.vaultDao().deleteSmsDraftById(draft.id) }
                        },
                        onCameraClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                cameraSnapLauncher.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onGalleryClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onManualSyncSms = {
                            scope.launch {
                                db.vaultDao().insertSmsDraft(
                                    SmsDraft(
                                        rawSender = "HDFC-BANK",
                                        rawBody = "Sent Rs. 380.00 to Blinkit on 15-09-2026. Ref 99018.",
                                        amount = 380.0,
                                        merchant = "Blinkit"
                                    )
                                )
                                Toast.makeText(context, "Incoming receipt staged in review", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    3 -> SettingsTabScreen(
                        currentTheme = activeThemeMode,
                        onSelectTheme = { mode ->
                            activeThemeMode = mode
                            prefs.edit().putString("selected_theme", mode.name).apply()
                        },
                        onClearLedger = {
                            scope.launch {
                                db.vaultDao().clearAllTransactions()
                                db.vaultDao().clearAllAccounts()
                                db.vaultDao().clearAllCounterparties()
                                db.vaultDao().clearAllSms()
                                Toast.makeText(context, "All data wiped clean", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // Quick Add Transaction Dialog (with state-retaining edit mode)
            if (showMainTxDialog) {
                MainLedgerTransactionDialog(
                    accounts = accounts.filter { it.type in listOf("CASH", "BANK", "CREDIT") },
                    prefilled = prefilledTx,
                    onDismiss = { showMainTxDialog = false },
                    onSave = { tx ->
                        scope.launch {
                            val acc = accounts.firstOrNull { it.id == tx.accountId }
                            if (acc != null && tx.flowType == "OUT" && acc.type != "CREDIT" && (acc.balance - tx.amount) < 0) {
                                Toast.makeText(context, "Insufficient balance in ${acc.name}", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            if (prefilledTx != null) {
                                db.vaultDao().deleteTransaction(prefilledTx!!.id)
                            }
                            db.vaultDao().insertTransaction(tx)
                            if (acc != null) {
                                val delta = if (tx.flowType == "IN") tx.amount else -tx.amount
                                db.vaultDao().updateAccount(acc.copy(balance = acc.balance + delta))
                            }
                            showMainTxDialog = false
                        }
                    }
                )
            }

            // Create New Account Card (with strict liquidity verification)
            if (showCreateCardDialog) {
                CreateAccountCardDialog(
                    availableSourceAccounts = accounts.filter { it.type in listOf("CASH", "BANK") },
                    onDismiss = { showCreateCardDialog = false },
                    onSave = { acc, sourceAccId, initialDate, initialNote ->
                        scope.launch {
                            val sourceAcc = accounts.firstOrNull { it.id == sourceAccId }
                            
                            // Liquidity check: cannot lend if wallet doesn't have balance
                            if (acc.type == "BORROWER" && sourceAcc != null && sourceAcc.balance < acc.balance) {
                                Toast.makeText(context, "Cannot lend ₹${acc.balance.toInt()}: ${sourceAcc.name} only has ₹${sourceAcc.balance.toInt()}", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            val createdId = db.vaultDao().insertAccount(acc)

                            when (acc.type) {
                                "LENDER" -> {
                                    if (sourceAcc != null && acc.balance > 0.0) {
                                        db.vaultDao().updateAccount(sourceAcc.copy(balance = sourceAcc.balance + acc.balance))
                                        val displayNote = initialNote.ifBlank { "Borrowed ₹${String.format("%.0f", acc.balance)} from ${acc.name} into ${sourceAcc.name}" }
                                        db.vaultDao().insertTransaction(
                                            Transaction(
                                                accountId = sourceAcc.id,
                                                flowType = "BORROW",
                                                type = "NEUTRAL",
                                                category = "Borrowing",
                                                amount = acc.balance,
                                                timestamp = initialDate,
                                                partyName = acc.name,
                                                channelAccountName = sourceAcc.name,
                                                note = displayNote,
                                                returnDate = acc.dueDate
                                            )
                                        )
                                    }
                                }
                                "BORROWER" -> {
                                    if (sourceAcc != null && acc.balance > 0.0) {
                                        db.vaultDao().updateAccount(sourceAcc.copy(balance = sourceAcc.balance - acc.balance))
                                        val displayNote = initialNote.ifBlank { "Lent ₹${String.format("%.0f", acc.balance)} to ${acc.name} from ${sourceAcc.name}" }
                                        db.vaultDao().insertTransaction(
                                            Transaction(
                                                accountId = sourceAcc.id,
                                                flowType = "LEND",
                                                type = "NEUTRAL",
                                                category = "Lending",
                                                amount = acc.balance,
                                                timestamp = initialDate,
                                                partyName = acc.name,
                                                channelAccountName = sourceAcc.name,
                                                note = displayNote,
                                                returnDate = acc.dueDate
                                            )
                                        )
                                    }
                                }
                                "CREDIT" -> {
                                    val debt = (acc.totalLimit - acc.balance).coerceAtLeast(0.0)
                                    if (debt > 0.0) {
                                        db.vaultDao().insertTransaction(
                                            Transaction(
                                                accountId = createdId,
                                                flowType = "OUT",
                                                type = "EXPENSE",
                                                category = "Existing CC Bill",
                                                amount = debt,
                                                timestamp = initialDate,
                                                note = initialNote.ifBlank { "Existing card balance on ${acc.name}" },
                                                returnDate = acc.dueDate
                                            )
                                        )
                                    }
                                }
                                else -> {
                                    if (acc.balance > 0.0) {
                                        db.vaultDao().insertTransaction(
                                            Transaction(
                                                accountId = createdId,
                                                flowType = "IN",
                                                type = "INCOME",
                                                category = "Starting Balance",
                                                amount = acc.balance,
                                                timestamp = initialDate,
                                                note = initialNote.ifBlank { "Initial balance for ${acc.name}" }
                                            )
                                        )
                                    }
                                }
                            }
                            showCreateCardDialog = false
                        }
                    }
                )
            }

            // Quick Actions with Funding Checks
            quickActionAccount?.let { (acc, action) ->
                QuickActionDialog(
                    account = acc,
                    action = action,
                    availableFundingAccounts = accounts.filter { it.type in listOf("CASH", "BANK") && it.id != acc.id },
                    onDismiss = { quickActionAccount = null },
                    onConfirm = { deltaAmount, fundingAccId, actionDate, noteText ->
                        scope.launch {
                            val fundingAcc = accounts.firstOrNull { it.id == fundingAccId }

                            // Solvency checks
                            if (action in listOf("REFILL", "REPAY") && fundingAcc != null && fundingAcc.balance < deltaAmount) {
                                Toast.makeText(context, "Cannot complete ${action.lowercase()}: ${fundingAcc.name} only has ₹${fundingAcc.balance.toInt()}", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            when (action) {
                                "ADD" -> {
                                    db.vaultDao().updateAccount(acc.copy(balance = acc.balance + deltaAmount))
                                    db.vaultDao().insertTransaction(
                                        Transaction(
                                            accountId = acc.id,
                                            flowType = "IN",
                                            type = "INCOME",
                                            category = "Deposit",
                                            amount = deltaAmount,
                                            timestamp = actionDate,
                                            note = noteText.ifBlank { "Direct balance deposit to ${acc.name}" }
                                        )
                                    )
                                }
                                "REFILL" -> {
                                    if (fundingAcc != null) {
                                        db.vaultDao().updateAccount(fundingAcc.copy(balance = fundingAcc.balance - deltaAmount))
                                    }
                                    val newBal = (acc.balance + deltaAmount).coerceAtMost(acc.totalLimit)
                                    db.vaultDao().updateAccount(acc.copy(balance = newBal))
                                    db.vaultDao().insertTransaction(
                                        Transaction(
                                            accountId = acc.id,
                                            flowType = "IN",
                                            type = "INCOME",
                                            category = "CC Payment",
                                            amount = deltaAmount,
                                            timestamp = actionDate,
                                            channelAccountName = fundingAcc?.name ?: "",
                                            note = noteText.ifBlank { "Card balance refill via ${fundingAcc?.name ?: "Cash"}" }
                                        )
                                    )
                                }
                                "REPAY" -> {
                                    if (fundingAcc != null) {
                                        db.vaultDao().updateAccount(fundingAcc.copy(balance = fundingAcc.balance - deltaAmount))
                                    }
                                    val newBal = (acc.balance - deltaAmount).coerceAtLeast(0.0)
                                    db.vaultDao().updateAccount(acc.copy(balance = newBal))
                                    db.vaultDao().insertTransaction(
                                        Transaction(
                                            accountId = acc.id,
                                            flowType = "REPAY",
                                            type = "NEUTRAL",
                                            category = "Repayment",
                                            amount = deltaAmount,
                                            timestamp = actionDate,
                                            partyName = acc.name,
                                            channelAccountName = fundingAcc?.name ?: "",
                                            note = noteText.ifBlank { "Repayment to ${acc.name} via ${fundingAcc?.name ?: "account"}" }
                                        )
                                    )
                                }
                                "COLLECT" -> {
                                    if (fundingAcc != null) {
                                        db.vaultDao().updateAccount(fundingAcc.copy(balance = fundingAcc.balance + deltaAmount))
                                    }
                                    val newBal = (acc.balance - deltaAmount).coerceAtLeast(0.0)
                                    db.vaultDao().updateAccount(acc.copy(balance = newBal))
                                    db.vaultDao().insertTransaction(
                                        Transaction(
                                            accountId = acc.id,
                                            flowType = "COLLECT",
                                            type = "NEUTRAL",
                                            category = "Collection",
                                            amount = deltaAmount,
                                            timestamp = actionDate,
                                            partyName = acc.name,
                                            channelAccountName = fundingAcc?.name ?: "",
                                            note = noteText.ifBlank { "Collection from ${acc.name} into ${fundingAcc?.name ?: "account"}" }
                                        )
                                    )
                                }
                            }
                            quickActionAccount = null
                        }
                    }
                )
            }

            // Intra-Account Transfer
            if (showTransferDialog) {
                IntraAccountTransferDialog(
                    accounts = accounts.filter { it.type in listOf("CASH", "BANK", "CREDIT") },
                    onDismiss = { showTransferDialog = false },
                    onTransfer = { fromAcc, toAcc, amount, transferDate ->
                        scope.launch {
                            if (fromAcc.type != "CREDIT" && (fromAcc.balance - amount) < 0) {
                                Toast.makeText(context, "Balance cannot drop below zero", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            db.vaultDao().updateAccount(fromAcc.copy(balance = fromAcc.balance - amount))
                            db.vaultDao().updateAccount(toAcc.copy(balance = toAcc.balance + amount))
                            db.vaultDao().insertTransaction(
                                Transaction(
                                    accountId = fromAcc.id,
                                    flowType = "TRANSFER",
                                    type = "NEUTRAL",
                                    category = "Internal Transfer",
                                    amount = amount,
                                    timestamp = transferDate,
                                    channelAccountName = toAcc.name,
                                    note = "Transfer from ${fromAcc.name} to ${toAcc.name}"
                                )
                            )
                            showTransferDialog = false
                            Toast.makeText(context, "Transferred ₹$amount", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Edit Account Card Dialog
            editingAccount?.let { acc ->
                EditAccountCardDialog(
                    account = acc,
                    onDismiss = { editingAccount = null },
                    onSave = { updated, adjustmentDiff, adjustNote ->
                        scope.launch {
                            db.vaultDao().updateAccount(updated)
                            if (adjustmentDiff != 0.0) {
                                val isPositive = adjustmentDiff > 0
                                val flow = if (isPositive) "IN" else "OUT"
                                val label = if (isPositive) "+₹${String.format("%.0f", adjustmentDiff)}" else "-₹${String.format("%.0f", -adjustmentDiff)}"
                                val finalNote = adjustNote.ifBlank { "Balance adjustment on ${updated.name}: $label" }
                                db.vaultDao().insertTransaction(
                                    Transaction(
                                        accountId = updated.id,
                                        flowType = flow,
                                        type = if (isPositive) "INCOME" else "EXPENSE",
                                        category = "Adjustment",
                                        amount = kotlin.math.abs(adjustmentDiff),
                                        note = finalNote
                                    )
                                )
                            }
                            editingAccount = null
                        }
                    },
                    onDelete = {
                        scope.launch {
                            db.vaultDao().deleteAccount(acc.id)
                            editingAccount = null
                        }
                    }
                )
            }

            // Long-Press Action Sheet (for Cards or Recurring Ribbons)
            itemPendingAction?.let { (type, item) ->
                LongPressActionDialog(
                    title = if (type == "ACCOUNT") (item as Account).name else (item as Transaction).note.ifBlank { (item as Transaction).category },
                    onDismiss = { itemPendingAction = null },
                    onEdit = {
                        if (type == "ACCOUNT") {
                            editingAccount = item as Account
                        } else {
                            prefilledTx = item as Transaction
                            showMainTxDialog = true
                        }
                        itemPendingAction = null
                    },
                    onDelete = {
                        scope.launch {
                            if (type == "ACCOUNT") {
                                db.vaultDao().deleteAccount((item as Account).id)
                            } else {
                                db.vaultDao().deleteTransaction((item as Transaction).id)
                            }
                            itemPendingAction = null
                            Toast.makeText(context, "Item deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Searchable Full Ledger Sheet (with CSV Export)
            if (showAllLedgerSheet) {
                AllTransactionsSearchSheet(
                    transactions = transactions,
                    onDismiss = { showAllLedgerSheet = false },
                    onExportCsv = { CsvExporter.exportAndShareTransactions(context, transactions) }
                )
            }

            // Dedicated View All Recurring Sheet
            if (showAllRecurringSheet) {
                AllRecurringEntriesSheet(
                    recurringTransactions = transactions.filter { it.isRecurring },
                    recurringAccounts = accounts.filter { it.repaymentType == "INSTALLMENTS" },
                    onDismiss = { showAllRecurringSheet = false },
                    onEditRecurring = { tx ->
                        prefilledTx = tx
                        showMainTxDialog = true
                        showAllRecurringSheet = false
                    },
                    onEditAccount = { acc ->
                        editingAccount = acc
                        showAllRecurringSheet = false
                    }
                )
            }
        }
    }
}

// ---------------- TAB 0: LEDGER (SLIM ROWS + 5-6 RECENT CAP) ----------------

@Composable
fun LedgerTabScreen(
    transactions: List<Transaction>,
    onViewAllClick: () -> Unit
) {
    val theme = LocalThemeColors.current
    val currentCal = Calendar.getInstance()
    val thisMonthTxs = transactions.filter {
        val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
        c.get(Calendar.MONTH) == currentCal.get(Calendar.MONTH) && c.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR)
    }

    val totalOut = thisMonthTxs.filter { it.flowType == "OUT" }.sumOf { it.amount }
    val totalIn = thisMonthTxs.filter { it.flowType == "IN" }.sumOf { it.amount }
    val expenseCategories = thisMonthTxs.filter { it.flowType == "OUT" }.groupBy { it.category }

    val recentTxs = transactions.take(6)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("THIS MONTH'S FLOW", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Spent: ", color = theme.textMuted, fontSize = 13.sp)
                            Text("₹ ${String.format("%,.0f", totalOut)}", color = theme.mildRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Income: ", color = theme.textMuted, fontSize = 13.sp)
                            Text("₹ ${String.format("%,.0f", totalIn)}", color = theme.mildGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier.size(72.dp).weight(0.8f),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(64.dp)) {
                            val strokeWidth = 8.dp.toPx()
                            if (expenseCategories.isEmpty()) {
                                drawCircle(color = theme.surfaceAlt, style = Stroke(width = strokeWidth))
                            } else {
                                var startAngle = -90f
                                val palette = listOf(theme.accent, theme.mildRed, theme.mildGreen, Color(0xFFE5A93C), Color(0xFFC48B57))
                                expenseCategories.entries.forEachIndexed { idx, entry ->
                                    val catSum = entry.value.sumOf { it.amount }
                                    val sweep = if (totalOut > 0) ((catSum / totalOut) * 360f).toFloat() else 0f
                                    drawArc(
                                        color = palette[idx % palette.size],
                                        startAngle = startAngle,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                    startAngle += sweep
                                }
                            }
                        }
                        Text("${expenseCategories.size} Cats", color = theme.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Activity", color = theme.textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (transactions.isNotEmpty()) {
                    Text(
                        "View All (${transactions.size}) →",
                        color = theme.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onViewAllClick() }
                    )
                }
            }
        }

        if (recentTxs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = theme.textMuted, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No entries recorded yet", color = theme.textBright, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Text("Tap the '+' button below to add your first expense or income.", color = theme.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(recentTxs) { tx ->
                SlimLedgerRow(tx = tx)
                Divider(color = theme.surfaceAlt.copy(alpha = 0.6f), thickness = 0.5.dp)
            }
        }
    }
}

// ---------------- HIGH DENSITY SLIM LEDGER ROW (~48dp) ----------------

@Composable
fun SlimLedgerRow(tx: Transaction) {
    val theme = LocalThemeColors.current
    val dateFmt = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(tx.timestamp))

    val isCreditFlow = tx.flowType in listOf("IN", "COLLECT")
    val isTransfer = tx.flowType in listOf("TRANSFER", "NEUTRAL")
    val flowColor = when {
        isCreditFlow -> theme.mildGreen
        isTransfer -> theme.accent
        else -> theme.mildRed
    }

    val arrowIcon = when {
        tx.flowType == "IN" -> Icons.Default.ArrowDownward
        tx.flowType == "OUT" -> Icons.Default.ArrowUpward
        tx.flowType == "BORROW" -> Icons.Default.CallReceived
        tx.flowType == "LEND" -> Icons.Default.CallMade
        tx.flowType == "COLLECT" -> Icons.Default.ArrowDownward
        tx.flowType == "REPAY" -> Icons.Default.ArrowUpward
        else -> Icons.Default.SwapHoriz
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(arrowIcon, contentDescription = null, tint = flowColor, modifier = Modifier.size(16.dp))
            Column {
                Text(
                    text = tx.note.ifBlank { tx.category },
                    color = theme.textBright,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    maxLines = 1
                )
                Text(
                    text = buildString {
                        if (tx.partyName.isNotBlank()) append("${tx.partyName} • ")
                        append(tx.category)
                        if (tx.isRecurring) append(" [${tx.frequency}]")
                    },
                    color = theme.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (isCreditFlow) "+" else if (!isTransfer) "-" else ""}₹ ${String.format("%.0f", tx.amount)}",
                color = flowColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(dateFmt, color = theme.textMuted, fontSize = 10.sp)
        }
    }
}

// ---------------- TAB 1: ACCOUNTS & CAROUSELS (LONG-PRESS ACTIONS) ----------------

@Composable
fun AccountsTabScreen(
    accounts: List<Account>,
    recurringTransactions: List<Transaction>,
    onOpenTransfer: () -> Unit,
    onQuickAction: (Account, String) -> Unit,
    onLongPressAccount: (Account) -> Unit,
    onLongPressRecurring: (Transaction) -> Unit,
    onViewAllRecurring: () -> Unit
) {
    val theme = LocalThemeColors.current

    val primaryAccounts = accounts.filter { it.type in listOf("CASH", "BANK", "CREDIT") }
    val blAccounts = accounts.filter { it.type in listOf("LENDER", "BORROWER") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Upper Carousel 1: Cash, Bank, Credit Card (Fixed 210dp x 145dp)
        item {
            Text("Wallets & Bank Accounts", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (primaryAccounts.isEmpty()) {
                Text("No Cash, Bank, or Credit cards added. Tap '+' to create one.", color = theme.textMuted, fontSize = 12.sp)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(primaryAccounts) { acc ->
                        FixedWalletCard(
                            account = acc,
                            onLongPress = { onLongPressAccount(acc) },
                            onQuickAction = { action -> onQuickAction(acc, action) }
                        )
                    }
                }
            }
        }

        // Upper Carousel 2: Borrowers & Lenders (Fixed 210dp x 145dp)
        item {
            Text("Borrowers & Lenders", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (blAccounts.isEmpty()) {
                Text("No Borrowers or Lenders recorded. Tap '+' to create one.", color = theme.textMuted, fontSize = 12.sp)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(blAccounts) { acc ->
                        FixedPartyCard(
                            account = acc,
                            onLongPress = { onLongPressAccount(acc) },
                            onQuickAction = { action -> onQuickAction(acc, action) }
                        )
                    }
                }
            }
        }

        // Middle Action: Intra-Account Transfer
        item {
            OutlinedButton(
                onClick = onOpenTransfer,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent)
            ) {
                Icon(Icons.Default.MultipleStop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Intra-Account Transfer", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        // Lower Section: Active Recurring Entries (Capped at 3 items)
        val allRecurringCount = recurringTransactions.size + accounts.count { it.repaymentType == "INSTALLMENTS" }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Active Recurring Entries", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (allRecurringCount > 3) {
                    Text(
                        "View All ($allRecurringCount) →",
                        color = theme.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onViewAllRecurring() }
                    )
                }
            }
        }

        val topRecurringTxs = recurringTransactions.take(3)
        if (topRecurringTxs.isEmpty() && accounts.none { it.repaymentType == "INSTALLMENTS" }) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface)
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No active recurring entries", color = theme.textBright, fontWeight = FontWeight.SemiBold)
                        Text("Recurring incomes, SIPs, and loan installments will appear here.", color = theme.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(topRecurringTxs) { rTx ->
                RecurringRibbonItem(
                    title = rTx.note.ifBlank { rTx.category },
                    subtitle = "${rTx.flowType} • ${rTx.frequency}",
                    amount = rTx.amount,
                    isPositive = rTx.flowType == "IN",
                    onLongPress = { onLongPressRecurring(rTx) }
                )
            }
        }
    }
}

// ---------------- UNIFORM FIXED CARDS (LONG-PRESS READY) ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FixedWalletCard(
    account: Account,
    onLongPress: () -> Unit,
    onQuickAction: (String) -> Unit
) {
    val theme = LocalThemeColors.current
    var isRevealed by remember { mutableStateOf(!account.hasEyeMask) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .size(width = 210.dp, height = 145.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = theme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(account.name, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text(account.type, color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            Column {
                val displayAmount = when (account.type) {
                    "CREDIT" -> "Avail: ₹ ${String.format("%,.0f", account.balance)}"
                    else -> "₹ ${String.format("%,.2f", account.balance)}"
                }
                Text(
                    text = if (isRevealed) displayAmount else "₹ ••••••",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.textBright
                )
                if (account.type == "CREDIT") {
                    val billAmount = (account.totalLimit - account.balance).coerceAtLeast(0.0)
                    Text("Due: ₹ ${String.format("%,.0f", billAmount)}", color = theme.mildRed, fontSize = 10.sp)
                } else {
                    Text("Liquid Balance", color = theme.textMuted, fontSize = 10.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (account.hasEyeMask) {
                    IconButton(
                        onClick = {
                            isRevealed = true
                            scope.launch {
                                delay(5000)
                                isRevealed = false
                            }
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(if (isRevealed) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.accent)
                        .clickable { onQuickAction(if (account.type == "CREDIT") "REFILL" else "ADD") }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(if (account.type == "CREDIT") "Refill" else "+ Add", color = theme.bg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FixedPartyCard(
    account: Account,
    onLongPress: () -> Unit,
    onQuickAction: (String) -> Unit
) {
    val theme = LocalThemeColors.current
    var isRevealed by remember { mutableStateOf(!account.hasEyeMask) }
    val scope = rememberCoroutineScope()
    val isLender = account.type == "LENDER"

    Card(
        modifier = Modifier
            .size(width = 210.dp, height = 145.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = theme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(account.name, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text(if (isLender) "LENDER" else "BORROWER", color = if (isLender) theme.mildRed else theme.mildGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            Column {
                Text(
                    text = if (isRevealed) "₹ ${String.format("%,.0f", account.balance)}" else "₹ ••••••",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isLender) theme.mildRed else theme.mildGreen
                )
                val dateStr = if (account.dueDate > 0L) SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(account.dueDate)) else "No due date"
                Text("Due: $dateStr", color = theme.textMuted, fontSize = 10.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (account.hasEyeMask) {
                    IconButton(
                        onClick = {
                            isRevealed = true
                            scope.launch {
                                delay(5000)
                                isRevealed = false
                            }
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(if (isRevealed) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isLender) theme.mildRed else theme.mildGreen)
                        .clickable { onQuickAction(if (isLender) "REPAY" else "COLLECT") }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(if (isLender) "Repay" else "Collect", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------- RECURRING RIBBON (LONG-PRESS ACTIVATED) ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecurringRibbonItem(
    title: String,
    subtitle: String,
    amount: Double,
    isPositive: Boolean,
    onLongPress: () -> Unit
) {
    val theme = LocalThemeColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surface)
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Text(subtitle, color = theme.textMuted, fontSize = 11.sp)
        }

        Text(
            "${if (isPositive) "+" else "-"} ₹ ${String.format("%.0f", amount)}",
            color = if (isPositive) theme.mildGreen else theme.mildRed,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

// ---------------- TAB 2: REVIEW QUEUE ----------------

@Composable
fun ReviewQueueTabScreen(
    stagedSms: List<SmsDraft>,
    onDiscard: (Long) -> Unit,
    onConsider: (SmsDraft) -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onManualSyncSms: () -> Unit
) {
    val theme = LocalThemeColors.current
    var expandedDraftId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCameraClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = theme.bg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Take Photo", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onGalleryClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pick Image", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Review Feed", color = theme.textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onManualSyncSms, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Sync, contentDescription = "Simulate SMS Scan", tint = theme.accent, modifier = Modifier.size(16.dp))
                    }
                }
                if (stagedSms.isNotEmpty()) {
                    Text("${stagedSms.size} Pending", color = theme.mildRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (stagedSms.isEmpty()) {
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
                        Icon(Icons.Outlined.DoneAll, contentDescription = null, tint = theme.accent, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Everything is caught up", color = theme.textBright, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Text("New SMS receipts and scanned memos will wait here for your review.", color = theme.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(stagedSms) { draft ->
                val isExpanded = expandedDraftId == draft.id
                Card(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { expandedDraftId = if (isExpanded) null else draft.id },
                    colors = CardDefaults.cardColors(containerColor = theme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(draft.merchant, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("From: ${draft.rawSender}", color = theme.textMuted, fontSize = 11.sp)
                            }
                            Text("₹ ${String.format("%.2f", draft.amount)}", color = theme.accent, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(Modifier.padding(top = 12.dp)) {
                                Text(draft.rawBody, color = theme.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
                                Spacer(Modifier.height(14.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = { onDiscard(draft.id) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.mildRed)
                                    ) {
                                        Text("Discard", fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Button(
                                        onClick = { onConsider(draft) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.mildGreen)
                                    ) {
                                        Text("Consider", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- TAB 3: SETTINGS ----------------

@Composable
fun SettingsTabScreen(
    currentTheme: AppThemeMode,
    onSelectTheme: (AppThemeMode) -> Unit,
    onClearLedger: () -> Unit
) {
    val theme = LocalThemeColors.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE) }

    var showPinVerifyDialog by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }

    val savedPin = prefs.getString("user_pin", "1234") ?: "1234"

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item { Text("App Preferences", color = theme.textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Palette Theme", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("Amber Ochre", AppThemeMode.AMBER_OCHRE, AmberTheme.accent),
                            Triple("Olive Matcha", AppThemeMode.OLIVE_MATCHA, OliveMatchaTheme.accent),
                            Triple("Sand Dune", AppThemeMode.SAND_DUNE, SandDuneTheme.accent)
                        ).forEach { (label, mode, col) ->
                            val isSel = currentTheme == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) col.copy(alpha = 0.25f) else theme.surfaceAlt)
                                    .clickable { onSelectTheme(mode) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (isSel) col else theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                    }

                    Divider(color = theme.surfaceAlt)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Security PIN", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Required to wipe all ledger data", color = theme.textMuted, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { showSetPinDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Change PIN", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(color = theme.surfaceAlt)

                    TextButton(
                        onClick = { showPinVerifyDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = theme.mildRed)
                        Spacer(Modifier.width(8.dp))
                        Text("Reset & Clear All Ledger Records", color = theme.mildRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showPinVerifyDialog) {
        ThemePinPadDialog(
            title = "Verify Security PIN",
            subtitle = "Enter your 4-digit PIN to authorize ledger reset.",
            expectedPin = savedPin,
            onDismiss = { showPinVerifyDialog = false },
            onSuccess = {
                showPinVerifyDialog = false
                onClearLedger()
            }
        )
    }

    if (showSetPinDialog) {
        ThemeSetPinDialog(
            onDismiss = { showSetPinDialog = false },
            onSavePin = { newPin ->
                prefs.edit().putString("user_pin", newPin).apply()
                showSetPinDialog = false
                Toast.makeText(context, "New security PIN saved", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ---------------- LONG-PRESS ACTION SHEET ----------------

@Composable
fun LongPressActionDialog(
    title: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalThemeColors.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = theme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Divider(color = theme.surfaceAlt)
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Item", color = theme.textBright, fontSize = 14.sp)
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = theme.mildRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Permanently", color = theme.mildRed, fontSize = 14.sp)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = theme.textMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

// ---------------- ALL RECURRING ENTRIES SHEET ----------------

@Composable
fun AllRecurringEntriesSheet(
    recurringTransactions: List<Transaction>,
    recurringAccounts: List<Account>,
    onDismiss: () -> Unit,
    onEditRecurring: (Transaction) -> Unit,
    onEditAccount: (Account) -> Unit
) {
    val theme = LocalThemeColors.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = theme.bg,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("All Recurring Entries", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textMuted)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recurringTransactions) { rTx ->
                        RecurringRibbonItem(
                            title = rTx.note.ifBlank { rTx.category },
                            subtitle = "${rTx.flowType} • ${rTx.frequency}",
                            amount = rTx.amount,
                            isPositive = rTx.flowType == "IN",
                            onLongPress = { onEditRecurring(rTx) }
                        )
                    }

                    items(recurringAccounts) { rAcc ->
                        RecurringRibbonItem(
                            title = "${rAcc.name} (${rAcc.type})",
                            subtitle = "Installment • ${rAcc.frequency}",
                            amount = rAcc.balance / rAcc.installmentCount.coerceAtLeast(1),
                            isPositive = rAcc.type == "BORROWER",
                            onLongPress = { onEditAccount(rAcc) }
                        )
                    }
                }
            }
        }
    }
}

// ---------------- ALL TRANSACTIONS INSTANT SEARCH SHEET (WITH CSV EXPORT) ----------------

@Composable
fun AllTransactionsSearchSheet(
    transactions: List<Transaction>,
    onDismiss: () -> Unit,
    onExportCsv: () -> Unit
) {
    val theme = LocalThemeColors.current
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredList = remember(query, selectedFilter, transactions) {
        transactions.filter { tx ->
            val matchesFilter = when (selectedFilter) {
                "IN" -> tx.flowType in listOf("IN", "COLLECT")
                "OUT" -> tx.flowType in listOf("OUT", "REPAY")
                "B/L" -> tx.flowType in listOf("BORROW", "LEND", "REPAY", "COLLECT")
                else -> true
            }

            val q = query.trim().lowercase()
            val matchesQuery = if (q.isEmpty()) {
                true
            } else {
                tx.note.lowercase().contains(q) ||
                        tx.category.lowercase().contains(q) ||
                        tx.partyName.lowercase().contains(q) ||
                        tx.channelAccountName.lowercase().contains(q) ||
                        tx.amount.toString().contains(q)
            }

            matchesFilter && matchesQuery
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = theme.bg,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("All Records (${filteredList.size})", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onExportCsv, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Export CSV", tint = theme.accent, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textMuted)
                        }
                    }
                }

                // Instant Search Bar
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search text, category, party, amount...", color = theme.textMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = theme.accent, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = theme.textMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textBright,
                        unfocusedTextColor = theme.textBright,
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.surfaceAlt
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Filter Chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "IN", "OUT", "B/L").forEach { f ->
                        val isSel = selectedFilter == f
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) theme.accent else theme.surfaceAlt)
                                .clickable { selectedFilter = f }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(f, color = if (isSel) theme.bg else theme.textMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                // High Density Result List
                if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No matching ledger transactions found.", color = theme.textMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredList) { tx ->
                            SlimLedgerRow(tx = tx)
                            Divider(color = theme.surfaceAlt.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- CREATE ACCOUNT CARD POPUP (INSTALLMENTS & COMPACT FORM) ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountCardDialog(
    availableSourceAccounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (Account, Long, Long, String) -> Unit
) {
    val theme = LocalThemeColors.current

    var selectedType by remember { mutableStateOf("CASH") }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var totalLimit by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var hasEyeMask by remember { mutableStateOf(true) }

    var repaymentType by remember { mutableStateOf("BULLET") }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var freqExpanded by remember { mutableStateOf(false) }
    var installmentCount by remember { mutableStateOf("12") }

    var selectedSourceAccId by remember { mutableStateOf(availableSourceAccounts.firstOrNull()?.id ?: 0L) }
    var sourceAccExpanded by remember { mutableStateOf(false) }

    var creationDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var dueDateMillis by remember { mutableStateOf(System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L)) }

    var showCreationDatePicker by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text("Create Account Card", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("CASH", "BANK", "CREDIT", "LENDER", "BORROWER").forEach { t ->
                        val isSel = selectedType == t
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else theme.surfaceAlt)
                                .clickable { selectedType = t }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(t, color = if (isSel) theme.bg else theme.textBright, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(when (selectedType) {
                        "CASH" -> "Wallet Name"
                        "BANK" -> "Bank Name"
                        "CREDIT" -> "Card Name"
                        "LENDER" -> "Lender Name"
                        else -> "Borrower Name"
                    }, color = theme.textMuted, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )

                // Compact two-column row: Amount + Date
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text(if (selectedType == "CREDIT") "Avail Limit" else "Amount", color = theme.textMuted, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                        modifier = Modifier.weight(1f).height(52.dp)
                    )

                    val cDateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(creationDateMillis))
                    OutlinedButton(
                        onClick = { showCreationDatePicker = true },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textBright)
                    ) {
                        Text("Date: $cDateStr", fontSize = 11.sp)
                    }
                }

                if (selectedType == "CREDIT") {
                    OutlinedTextField(
                        value = totalLimit,
                        onValueChange = { totalLimit = it },
                        label = { Text("Total Limit (₹)", color = theme.textMuted, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )
                }

                // Channel Account Selector
                if (selectedType in listOf("LENDER", "BORROWER") && availableSourceAccounts.isNotEmpty()) {
                    val activeSource = availableSourceAccounts.firstOrNull { it.id == selectedSourceAccId }?.name ?: "Select Funding Account"
                    ExposedDropdownMenuBox(
                        expanded = sourceAccExpanded,
                        onExpandedChange = { sourceAccExpanded = !sourceAccExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = activeSource,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (selectedType == "LENDER") "Receive Loan In" else "Give Loan From", color = theme.textMuted, fontSize = 11.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceAccExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                            modifier = Modifier.menuAnchor().fillMaxWidth().height(52.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = sourceAccExpanded,
                            onDismissRequest = { sourceAccExpanded = false },
                            modifier = Modifier.background(theme.surface)
                        ) {
                            availableSourceAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (₹${acc.balance.toInt()})", color = theme.textBright) },
                                    onClick = {
                                        selectedSourceAccId = acc.id
                                        sourceAccExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Installment Controls for Borrower/Lender
                if (selectedType in listOf("LENDER", "BORROWER")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("BULLET", "INSTALLMENTS").forEach { rType ->
                            val isSel = repaymentType == rType
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) theme.accent else theme.surfaceAlt)
                                    .clickable { repaymentType = rType }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(rType, color = if (isSel) theme.bg else theme.textBright, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (repaymentType == "INSTALLMENTS") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExposedDropdownMenuBox(
                                expanded = freqExpanded,
                                onExpandedChange = { freqExpanded = !freqExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = frequency,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Freq", color = theme.textMuted, fontSize = 11.sp) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                                    modifier = Modifier.menuAnchor().fillMaxWidth().height(52.dp)
                                )
                                ExposedDropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }, modifier = Modifier.background(theme.surface)) {
                                    listOf("MONTHLY", "YEARLY").forEach { f ->
                                        DropdownMenuItem(text = { Text(f, color = theme.textBright) }, onClick = { frequency = f; freqExpanded = false })
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = installmentCount,
                                onValueChange = { installmentCount = it },
                                label = { Text("Count", color = theme.textMuted, fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                                modifier = Modifier.weight(1f).height(52.dp)
                            )
                        }

                        val principal = amount.toDoubleOrNull() ?: 0.0
                        val count = installmentCount.toIntOrNull() ?: 1
                        if (principal > 0.0 && count > 0) {
                            Text(
                                "Installment: ₹${String.format("%.0f", principal / count)} / ${frequency.lowercase()}",
                                color = theme.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (selectedType in listOf("CREDIT", "LENDER", "BORROWER")) {
                    val dDateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dueDateMillis))
                    OutlinedButton(
                        onClick = { showDueDatePicker = true },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textBright)
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Due Date: $dDateStr", fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mask balance with eye toggle?", color = theme.textBright, fontSize = 11.sp)
                    Switch(
                        checked = hasEyeMask,
                        onCheckedChange = { hasEyeMask = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.surfaceAlt)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bal = amount.toDoubleOrNull() ?: 0.0
                    val totLim = totalLimit.toDoubleOrNull() ?: bal
                    if (name.isNotBlank()) {
                        onSave(
                            Account(
                                name = name,
                                balance = bal,
                                totalLimit = totLim,
                                type = selectedType,
                                hasEyeMask = hasEyeMask,
                                dueDate = dueDateMillis,
                                repaymentType = repaymentType,
                                frequency = frequency,
                                installmentCount = installmentCount.toIntOrNull() ?: 1,
                                originalAmount = bal
                            ),
                            selectedSourceAccId,
                            creationDateMillis,
                            note
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Create Card", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) }
        }
    )

    if (showCreationDatePicker) {
        ThemedDatePickerDialog(
            initialDateMillis = creationDateMillis,
            onDismiss = { showCreationDatePicker = false },
            onDateSelected = { creationDateMillis = it }
        )
    }

    if (showDueDatePicker) {
        ThemedDatePickerDialog(
            initialDateMillis = dueDateMillis,
            onDismiss = { showDueDatePicker = false },
            onDateSelected = { dueDateMillis = it }
        )
    }
}

// ---------------- QUICK ACTION MODAL (+ Add, Refill, Repay, Collect) ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionDialog(
    account: Account,
    action: String,
    availableFundingAccounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (Double, Long, Long, String) -> Unit
) {
    val theme = LocalThemeColors.current
    var deltaAmount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var actionDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showActionDatePicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    var selectedFundingAccId by remember { mutableStateOf(availableFundingAccounts.firstOrNull()?.id ?: 0L) }
    var fundingExpanded by remember { mutableStateOf(false) }

    val needsChannel = action in listOf("REFILL", "REPAY", "COLLECT")

    val title = when (action) {
        "ADD" -> "Add Balance to ${account.name}"
        "REFILL" -> "Refill ${account.name} Limit"
        "REPAY" -> "Repay ${account.name}"
        else -> "Collect from ${account.name}"
    }

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = deltaAmount,
                    onValueChange = {
                        deltaAmount = it
                        errorMsg = ""
                    },
                    label = { Text("Amount (₹)", color = theme.textMuted, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )

                if (needsChannel && availableFundingAccounts.isNotEmpty()) {
                    val activeFunding = availableFundingAccounts.firstOrNull { it.id == selectedFundingAccId }?.name ?: "Select Linked Account"
                    ExposedDropdownMenuBox(
                        expanded = fundingExpanded,
                        onExpandedChange = { fundingExpanded = !fundingExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = activeFunding,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (action == "COLLECT") "Deposit Collected Into" else "Fund / Pay From", color = theme.textMuted, fontSize = 11.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fundingExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                            modifier = Modifier.menuAnchor().fillMaxWidth().height(52.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = fundingExpanded,
                            onDismissRequest = { fundingExpanded = false },
                            modifier = Modifier.background(theme.surface)
                        ) {
                            availableFundingAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (Bal: ₹${acc.balance.toInt()})", color = theme.textBright) },
                                    onClick = {
                                        selectedFundingAccId = acc.id
                                        fundingExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(actionDateMillis))
                OutlinedButton(
                    onClick = { showActionDatePicker = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textBright)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Date: $dateFormatted", fontSize = 11.sp)
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Memo (Optional)", color = theme.textMuted, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )

                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = theme.mildRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = deltaAmount.toDoubleOrNull() ?: 0.0
                    if (amt <= 0.0) {
                        errorMsg = "Enter a valid amount"
                        return@Button
                    }
                    if (action == "REPAY" && amt > account.balance) {
                        errorMsg = "Repayment cannot exceed balance of ₹${account.balance}"
                        return@Button
                    }
                    if (action == "COLLECT" && amt > account.balance) {
                        errorMsg = "Collection cannot exceed receivable of ₹${account.balance}"
                        return@Button
                    }
                    if (action == "REFILL" && (account.balance + amt) > account.totalLimit) {
                        errorMsg = "Available limit cannot exceed ₹${account.totalLimit}"
                        return@Button
                    }
                    onConfirm(amt, selectedFundingAccId, actionDateMillis, note)
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Confirm", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) }
        }
    )

    if (showActionDatePicker) {
        ThemedDatePickerDialog(
            initialDateMillis = actionDateMillis,
            onDismiss = { showActionDatePicker = false },
            onDateSelected = { actionDateMillis = it }
        )
    }
}

// ---------------- EDIT ACCOUNT MODAL ----------------

@Composable
fun EditAccountCardDialog(
    account: Account,
    onDismiss: () -> Unit,
    onSave: (Account, Double, String) -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalThemeColors.current

    var name by remember { mutableStateOf(account.name) }
    var balance by remember { mutableStateOf(account.balance.toString()) }
    var totalLimit by remember { mutableStateOf(account.totalLimit.toString()) }
    var hasEyeMask by remember { mutableStateOf(account.hasEyeMask) }
    var adjustmentNote by remember { mutableStateOf("") }

    var dueDateMillis by remember { mutableStateOf(if (account.dueDate > 0L) account.dueDate else System.currentTimeMillis()) }
    var showDueDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text("Edit ${account.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name", color = theme.textMuted, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )

                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text(when (account.type) {
                        "CREDIT" -> "Available Limit (₹)"
                        "LENDER" -> "Current Outstanding (₹)"
                        "BORROWER" -> "Current Receivable (₹)"
                        else -> "Current Balance (₹)"
                    }, color = theme.textMuted, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )

                if (account.type == "CREDIT") {
                    OutlinedTextField(
                        value = totalLimit,
                        onValueChange = { totalLimit = it },
                        label = { Text("Total Limit (₹)", color = theme.textMuted, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )
                }

                if (account.type in listOf("CREDIT", "LENDER", "BORROWER")) {
                    val dDateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dueDateMillis))
                    OutlinedButton(
                        onClick = { showDueDatePicker = true },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textBright)
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Due Date: $dDateStr", fontSize = 11.sp)
                    }
                }

                val newBal = balance.toDoubleOrNull() ?: account.balance
                if (newBal != account.balance) {
                    OutlinedTextField(
                        value = adjustmentNote,
                        onValueChange = { adjustmentNote = it },
                        label = { Text("Reason for balance change", color = theme.textMuted, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mask balance with eye toggle?", color = theme.textBright, fontSize = 11.sp)
                    Switch(
                        checked = hasEyeMask,
                        onCheckedChange = { hasEyeMask = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.surfaceAlt)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bal = balance.toDoubleOrNull() ?: account.balance
                    val totLim = totalLimit.toDoubleOrNull() ?: account.totalLimit
                    val diff = bal - account.balance
                    onSave(
                        account.copy(name = name, balance = bal, totalLimit = totLim, dueDate = dueDateMillis, hasEyeMask = hasEyeMask),
                        diff,
                        adjustmentNote
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Save", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = theme.mildRed) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) }
            }
        }
    )

    if (showDueDatePicker) {
        ThemedDatePickerDialog(
            initialDateMillis = dueDateMillis,
            onDismiss = { showDueDatePicker = false },
            onDateSelected = { dueDateMillis = it }
        )
    }
}

// ---------------- MAIN LEDGER POPUP (IN / OUT ONLY) ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLedgerTransactionDialog(
    accounts: List<Account>,
    prefilled: Transaction?,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    val theme = LocalThemeColors.current

    var flowType by remember { mutableStateOf(prefilled?.flowType ?: "OUT") }
    var amount by remember { mutableStateOf(if (prefilled != null && prefilled.amount > 0) prefilled.amount.toString() else "") }
    var note by remember { mutableStateOf(prefilled?.note ?: "") }
    var selectedDateMillis by remember { mutableStateOf(prefilled?.timestamp ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val inCategories = listOf("Salary", "Freelance", "Capital Gain", "Gift", "Interest", "Other")
    val outCategories = listOf("Food", "Groceries", "Transport", "Shopping", "Bills", "Health", "Fuel", "Other")
    var category by remember { mutableStateOf(prefilled?.category ?: if (flowType == "IN") inCategories.first() else outCategories.first()) }
    var catExpanded by remember { mutableStateOf(false) }

    var selectedAccId by remember {
        mutableStateOf(prefilled?.accountId ?: accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var accExpanded by remember { mutableStateOf(false) }

    // Retain state if editing existing recurring entry
    var isRecurring by remember { mutableStateOf(prefilled?.isRecurring ?: false) }
    var recurringFrequency by remember { mutableStateOf(if (prefilled != null && prefilled.frequency != "NONE") prefilled.frequency else "MONTHLY") }
    var freqExpanded by remember { mutableStateOf(false) }
    var hasEndDate by remember { mutableStateOf((prefilled?.recurringEndDate ?: 0L) > 0L) }
    var recurringEndDateMillis by remember { mutableStateOf(if ((prefilled?.recurringEndDate ?: 0L) > 0L) prefilled!!.recurringEndDate else System.currentTimeMillis() + (180 * 24 * 60 * 60 * 1000L)) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text(if (prefilled != null) "Edit Entry" else "Record Entry", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            flowType = "OUT"
                            category = outCategories.first()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (flowType == "OUT") theme.mildRed else theme.surfaceAlt),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Out (Expense)", color = if (flowType == "OUT") Color.White else theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            flowType = "IN"
                            category = inCategories.first()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (flowType == "IN") theme.mildGreen else theme.surfaceAlt),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("In (Income)", color = if (flowType == "IN") Color.Black else theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Compact Amount & Date row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (₹)", color = theme.textMuted, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                        modifier = Modifier.weight(1f).height(52.dp)
                    )

                    val dateFormatted = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(selectedDateMillis))
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textBright)
                    ) {
                        Text("Date: $dateFormatted", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Merchant", color = theme.textMuted, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )

                // Compact Category & Account row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = catExpanded,
                        onExpandedChange = { catExpanded = !catExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category", color = theme.textMuted, fontSize = 11.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                            modifier = Modifier.menuAnchor().fillMaxWidth().height(52.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = catExpanded,
                            onDismissRequest = { catExpanded = false },
                            modifier = Modifier.background(theme.surface)
                        ) {
                            val activeCats = if (flowType == "IN") inCategories else outCategories
                            activeCats.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat, color = theme.textBright) },
                                    onClick = {
                                        category = cat
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (accounts.isNotEmpty()) {
                        val activeAccName = accounts.firstOrNull { it.id == selectedAccId }?.name ?: "Select Account"
                        ExposedDropdownMenuBox(
                            expanded = accExpanded,
                            onExpandedChange = { accExpanded = !accExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = activeAccName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(if (flowType == "IN") "Into" else "From", color = theme.textMuted, fontSize = 11.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                                modifier = Modifier.menuAnchor().fillMaxWidth().height(52.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = accExpanded,
                                onDismissRequest = { accExpanded = false },
                                modifier = Modifier.background(theme.surface)
                            ) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text("${acc.name} (${acc.type})", color = theme.textBright) },
                                        onClick = {
                                            selectedAccId = acc.id
                                            accExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Recurring Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recurring Entry?", color = theme.textBright, fontSize = 11.sp)
                    Switch(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.surfaceAlt)
                    )
                }

                AnimatedVisibility(visible = isRecurring) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = freqExpanded,
                            onExpandedChange = { freqExpanded = !freqExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = recurringFrequency,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Repeat Frequency", color = theme.textMuted, fontSize = 11.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                                modifier = Modifier.menuAnchor().fillMaxWidth().height(52.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = freqExpanded,
                                onDismissRequest = { freqExpanded = false },
                                modifier = Modifier.background(theme.surface)
                            ) {
                                listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY").forEach { f ->
                                    DropdownMenuItem(text = { Text(f, color = theme.textBright) }, onClick = { recurringFrequency = f; freqExpanded = false })
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Set End / Till Date?", color = theme.textMuted, fontSize = 11.sp)
                            Switch(
                                checked = hasEndDate,
                                onCheckedChange = { hasEndDate = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.surfaceAlt)
                            )
                        }

                        if (hasEndDate) {
                            val endFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(recurringEndDateMillis))
                            OutlinedButton(
                                onClick = { showEndDatePicker = true },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textBright)
                            ) {
                                Icon(Icons.Default.Event, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Ends on: $endFormatted", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) {
                        onSave(
                            Transaction(
                                id = prefilled?.id ?: 0L,
                                accountId = selectedAccId,
                                flowType = flowType,
                                type = if (flowType == "IN") "INCOME" else "EXPENSE",
                                category = category,
                                amount = amt,
                                timestamp = selectedDateMillis,
                                note = note,
                                isRecurring = isRecurring,
                                frequency = if (isRecurring) recurringFrequency else "NONE",
                                recurringEndDate = if (isRecurring && hasEndDate) recurringEndDateMillis else 0L
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Confirm", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) }
        }
    )

    if (showDatePicker) {
        ThemedDatePickerDialog(
            initialDateMillis = selectedDateMillis,
            onDismiss = { showDatePicker = false },
            onDateSelected = { selectedDateMillis = it }
        )
    }

    if (showEndDatePicker) {
        ThemedDatePickerDialog(
            initialDateMillis = recurringEndDateMillis,
            onDismiss = { showEndDatePicker = false },
            onDateSelected = { recurringEndDateMillis = it }
        )
    }
}

// ---------------- INTRA-ACCOUNT TRANSFER MODAL ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntraAccountTransferDialog(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onTransfer: (Account, Account, Double, Long) -> Unit
) {
    val theme = LocalThemeColors.current
    var fromAccId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var toAccId by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: accounts.firstOrNull()?.id ?: 0L) }
    var amount by remember { mutableStateOf("") }
    var transferDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showTransferDatePicker by remember { mutableStateOf(false) }

    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text("Intra-Account Transfer", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = fromExpanded, onExpandedChange = { fromExpanded = !fromExpanded }, modifier = Modifier.fillMaxWidth()) {
                    val fromName = accounts.firstOrNull { it.id == fromAccId }?.name ?: "Select"
                    OutlinedTextField(value = fromName, onValueChange = {}, readOnly = true, label = { Text("From", color = theme.textMuted, fontSize = 11.sp) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromExpanded) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt), modifier = Modifier.menuAnchor().fillMaxWidth().height(52.dp))
                    ExposedDropdownMenu(expanded = fromExpanded, onDismissRequest = { fromExpanded = false }, modifier = Modifier.background(theme.surface)) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text(acc.name, color = theme.textBright) }, onClick = { fromAccId = acc.id; fromExpanded = false })
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = toExpanded, onExpandedChange = { toExpanded = !toExpanded }, modifier = Modifier.fillMaxWidth()) {
                    val toName = accounts.firstOrNull { it.id == toAccId }?.name ?: "Select"
                    OutlinedTextField(value = toName, onValueChange = {}, readOnly = true, label = { Text("To", color = theme.textMuted, fontSize = 11.sp) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toExpanded) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt), modifier = Modifier.menuAnchor().fillMaxWidth().height(52.dp))
                    ExposedDropdownMenu(expanded = toExpanded, onDismissRequest = { toExpanded = false }, modifier = Modifier.background(theme.surface)) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text(acc.name, color = theme.textBright) }, onClick = { toAccId = acc.id; toExpanded = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Transfer Amount (₹)", color = theme.textMuted, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )

                val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(transferDateMillis))
                OutlinedButton(
                    onClick = { showTransferDatePicker = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textBright)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Date: $dateFormatted", fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    val fAcc = accounts.firstOrNull { it.id == fromAccId }
                    val tAcc = accounts.firstOrNull { it.id == toAccId }
                    if (amt > 0.0 && fAcc != null && tAcc != null && fAcc.id != tAcc.id) {
                        onTransfer(fAcc, tAcc, amt, transferDateMillis)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Transfer", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) }
        }
    )

    if (showTransferDatePicker) {
        ThemedDatePickerDialog(
            initialDateMillis = transferDateMillis,
            onDismiss = { showTransferDatePicker = false },
            onDateSelected = { transferDateMillis = it }
        )
    }
}

// ---------------- THEMED DATE PICKER ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemedDatePickerDialog(
    initialDateMillis: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val theme = LocalThemeColors.current
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Select", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) }
        },
        colors = DatePickerDefaults.colors(containerColor = theme.surface)
    ) {
        DatePicker(
            state = datePickerState,
            colors = DatePickerDefaults.colors(
                containerColor = theme.surface,
                titleContentColor = theme.textBright,
                headlineContentColor = theme.accent,
                weekdayContentColor = theme.textMuted,
                subheadContentColor = theme.textBright,
                yearContentColor = theme.textBright,
                currentYearContentColor = theme.accent,
                selectedYearContentColor = theme.bg,
                selectedYearContainerColor = theme.accent,
                dayContentColor = theme.textBright,
                selectedDayContentColor = theme.bg,
                selectedDayContainerColor = theme.accent,
                todayContentColor = theme.accent,
                todayDateBorderColor = theme.accent
            )
        )
    }
}

// ---------------- THEMED PIN KEYPADS ----------------

@Composable
fun ThemePinPadDialog(title: String, subtitle: String, expectedPin: String, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val theme = LocalThemeColors.current
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = theme.surface, modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(subtitle, color = theme.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier.size(16.dp).clip(CircleShape).background(
                                when {
                                    isError -> theme.mildRed
                                    isFilled -> theme.accent
                                    else -> theme.surfaceAlt
                                }
                            )
                        )
                    }
                }

                if (isError) {
                    Text("Incorrect PIN. Try again.", color = theme.mildRed, fontSize = 12.sp)
                }

                val keypadKeys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "⌫")
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    keypadKeys.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier.size(60.dp).clip(CircleShape).background(theme.surfaceAlt).clickable {
                                        when (key) {
                                            "C" -> { enteredPin = ""; isError = false }
                                            "⌫" -> { if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1); isError = false }
                                            else -> {
                                                if (enteredPin.length < 4) {
                                                    enteredPin += key
                                                    isError = false
                                                    if (enteredPin.length == 4) {
                                                        if (enteredPin == expectedPin) onSuccess() else { isError = true; enteredPin = "" }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(key, color = theme.textBright, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
fun ThemeSetPinDialog(onDismiss: () -> Unit, onSavePin: (String) -> Unit) {
    val theme = LocalThemeColors.current
    var pinText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = theme.surface, modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Set New PIN", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Enter a 4-digit security PIN for ledger actions.", color = theme.textMuted, fontSize = 12.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until 4) {
                        val isFilled = i < pinText.length
                        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(if (isFilled) theme.accent else theme.surfaceAlt))
                    }
                }

                val keypadKeys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "⌫")
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    keypadKeys.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier.size(60.dp).clip(CircleShape).background(theme.surfaceAlt).clickable {
                                        when (key) {
                                            "C" -> pinText = ""
                                            "⌫" -> if (pinText.isNotEmpty()) pinText = pinText.dropLast(1)
                                            else -> {
                                                if (pinText.length < 4) {
                                                    pinText += key
                                                    if (pinText.length == 4) onSavePin(pinText)
                                                }
                                            }
                                        }
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(key, color = theme.textBright, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted, fontSize = 13.sp) }
            }
        }
    }
}
