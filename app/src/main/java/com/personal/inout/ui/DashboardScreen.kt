package com.personal.inout.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.personal.inout.data.Account
import com.personal.inout.data.AppDatabase
import com.personal.inout.data.SmsDraft
import com.personal.inout.data.Transaction
import com.personal.inout.ocr.ReceiptScanner
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
    var showBLHistorySheet by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
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
                    Toast.makeText(context, "Camera scan failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
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
                    Toast.makeText(context, "Image read failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                        transactions = transactions.filter { it.flowType == "IN" || it.flowType == "OUT" }
                    )
                    1 -> AccountsTabScreen(
                        accounts = accounts,
                        transactions = transactions,
                        onOpenTransfer = { showTransferDialog = true },
                        onEditAccount = { editingAccount = it },
                        onQuickAction = { acc, action -> quickActionAccount = Pair(acc, action) },
                        onViewBLHistory = { showBLHistorySheet = true }
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
                            try { cameraLauncher.launch(null) } catch (e: Exception) {
                                Toast.makeText(context, "Camera permission needed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onGalleryClick = {
                            try { galleryLauncher.launch("image/*") } catch (e: Exception) {
                                Toast.makeText(context, "Photo picker error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onManualSyncSms = {
                            scope.launch {
                                db.vaultDao().insertSmsDraft(
                                    SmsDraft(
                                        rawSender = "HDFC-BANK",
                                        rawBody = "Sent Rs. 380.00 to Blinkit on 14-09-2026. Ref 99018.",
                                        amount = 380.0,
                                        merchant = "Blinkit"
                                    )
                                )
                                Toast.makeText(context, "New transaction waiting in review", Toast.LENGTH_SHORT).show()
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

            if (showMainTxDialog) {
                MainLedgerTransactionDialog(
                    accounts = accounts.filter { it.type in listOf("CASH", "BANK", "CREDIT") },
                    prefilled = prefilledTx,
                    onDismiss = { showMainTxDialog = false },
                    onSave = { tx ->
                        scope.launch {
                            val acc = accounts.firstOrNull { it.id == tx.accountId }
                            if (acc != null && tx.flowType == "OUT" && acc.type != "CREDIT" && (acc.balance - tx.amount) < 0) {
                                Toast.makeText(context, "Insufficient funds in ${acc.name}", Toast.LENGTH_SHORT).show()
                                return@launch
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

            if (showCreateCardDialog) {
                CreateAccountCardDialog(
                    onDismiss = { showCreateCardDialog = false },
                    onSave = { acc ->
                        scope.launch {
                            db.vaultDao().insertAccount(acc)
                            showCreateCardDialog = false
                        }
                    }
                )
            }

            quickActionAccount?.let { (acc, action) ->
                QuickActionDialog(
                    account = acc,
                    action = action,
                    onDismiss = { quickActionAccount = null },
                    onConfirm = { deltaAmount ->
                        scope.launch {
                            when (action) {
                                "ADD" -> {
                                    db.vaultDao().updateAccount(acc.copy(balance = acc.balance + deltaAmount))
                                    db.vaultDao().insertTransaction(
                                        Transaction(accountId = acc.id, flowType = "IN", type = "INCOME", category = "Deposit", amount = deltaAmount, note = "Quick Top-up")
                                    )
                                }
                                "REFILL" -> {
                                    val newBal = (acc.balance + deltaAmount).coerceAtMost(acc.totalLimit)
                                    db.vaultDao().updateAccount(acc.copy(balance = newBal))
                                    db.vaultDao().insertTransaction(
                                        Transaction(accountId = acc.id, flowType = "IN", type = "INCOME", category = "CC Payment", amount = deltaAmount, note = "Limit Refill")
                                    )
                                }
                                "REPAY" -> {
                                    val newBal = (acc.balance - deltaAmount).coerceAtLeast(0.0)
                                    db.vaultDao().updateAccount(acc.copy(balance = newBal))
                                    db.vaultDao().insertTransaction(
                                        Transaction(accountId = acc.id, flowType = "REPAY", type = "NEUTRAL", category = "Repayment", amount = deltaAmount, partyName = acc.name, note = "Paid back loan")
                                    )
                                }
                                "COLLECT" -> {
                                    val newBal = (acc.balance - deltaAmount).coerceAtLeast(0.0)
                                    db.vaultDao().updateAccount(acc.copy(balance = newBal))
                                    db.vaultDao().insertTransaction(
                                        Transaction(accountId = acc.id, flowType = "COLLECT", type = "NEUTRAL", category = "Collection", amount = deltaAmount, partyName = acc.name, note = "Collected debt")
                                    )
                                }
                            }
                            quickActionAccount = null
                        }
                    }
                )
            }

            if (showTransferDialog) {
                IntraAccountTransferDialog(
                    accounts = accounts.filter { it.type in listOf("CASH", "BANK", "CREDIT") },
                    onDismiss = { showTransferDialog = false },
                    onTransfer = { fromAcc, toAcc, amount ->
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
                                    note = "Transfer from ${fromAcc.name} to ${toAcc.name}"
                                )
                            )
                            showTransferDialog = false
                            Toast.makeText(context, "Transferred ₹$amount", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            editingAccount?.let { acc ->
                EditAccountCardDialog(
                    account = acc,
                    onDismiss = { editingAccount = null },
                    onSave = { updated ->
                        scope.launch {
                            db.vaultDao().updateAccount(updated)
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

            if (showBLHistorySheet) {
                BorrowLendHistoryDialog(
                    transactions = transactions.filter { it.flowType in listOf("BORROW", "LEND", "REPAY", "COLLECT") },
                    onDismiss = { showBLHistorySheet = false }
                )
            }
        }
    }
}

// ---------------- TAB 0: LEDGER ----------------

@Composable
fun LedgerTabScreen(transactions: List<Transaction>) {
    val theme = LocalThemeColors.current
    val currentCal = Calendar.getInstance()
    val thisMonthTxs = transactions.filter {
        val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
        c.get(Calendar.MONTH) == currentCal.get(Calendar.MONTH) && c.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR)
    }

    val totalOut = thisMonthTxs.filter { it.flowType == "OUT" }.sumOf { it.amount }
    val totalIn = thisMonthTxs.filter { it.flowType == "IN" }.sumOf { it.amount }
    val expenseCategories = thisMonthTxs.filter { it.flowType == "OUT" }.groupBy { it.category }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
            Text("Ledger Entries", color = theme.textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (transactions.isEmpty()) {
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
            items(transactions) { tx ->
                val dateFmt = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(tx.timestamp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(theme.surface).padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(theme.surfaceAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (tx.flowType == "IN") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (tx.flowType == "IN") theme.mildGreen else theme.mildRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(tx.note.ifBlank { tx.category }, color = theme.textBright, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${tx.category} • $dateFmt", color = theme.textMuted, fontSize = 11.sp)
                        }
                    }
                    Text(
                        "${if (tx.flowType == "IN") "+" else "-"} ₹ ${String.format("%.2f", tx.amount)}",
                        color = if (tx.flowType == "IN") theme.mildGreen else theme.textBright,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// ---------------- TAB 1: ACCOUNTS & B/L ----------------

@Composable
fun AccountsTabScreen(
    accounts: List<Account>,
    transactions: List<Transaction>,
    onOpenTransfer: () -> Unit,
    onEditAccount: (Account) -> Unit,
    onQuickAction: (Account, String) -> Unit,
    onViewBLHistory: () -> Unit
) {
    val theme = LocalThemeColors.current

    val primaryAccounts = accounts.filter { it.type in listOf("CASH", "BANK", "CREDIT") }
    val blAccounts = accounts.filter { it.type in listOf("LENDER", "BORROWER") }

    val totalReceivables = blAccounts.filter { it.type == "BORROWER" }.sumOf { it.balance }
    val totalObligations = blAccounts.filter { it.type == "LENDER" }.sumOf { it.balance } +
            primaryAccounts.filter { it.type == "CREDIT" }.sumOf { (it.totalLimit - it.balance).coerceAtLeast(0.0) }

    val now = System.currentTimeMillis()
    val next7Days = now + (7 * 24 * 60 * 60 * 1000L)
    val upcomingDueAccounts = accounts.filter { it.dueDate in now..next7Days && it.balance > 0.0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BORROW & LEND LEDGER", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("History →", color = theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onViewBLHistory() })
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Receivables (To Collect)", color = theme.textMuted, fontSize = 11.sp)
                            Text("₹ ${String.format("%,.2f", totalReceivables)}", color = theme.mildGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Obligations (To Pay)", color = theme.textMuted, fontSize = 11.sp)
                            Text("₹ ${String.format("%,.2f", totalObligations)}", color = theme.mildRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (upcomingDueAccounts.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Divider(color = theme.surfaceAlt)
                        Spacer(Modifier.height(8.dp))
                        Text("Upcoming payments in 7 days:", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        upcomingDueAccounts.forEach { dueAcc ->
                            val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(dueAcc.dueDate))
                            val isPayable = dueAcc.type == "LENDER" || dueAcc.type == "CREDIT"
                            val dueAmount = if (dueAcc.type == "CREDIT") (dueAcc.totalLimit - dueAcc.balance).coerceAtLeast(0.0) else dueAcc.balance
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${if (isPayable) "Pay to" else "Collect from"} ${dueAcc.name} ($dateStr)",
                                    color = theme.textBright,
                                    fontSize = 12.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("₹ ${String.format("%.0f", dueAmount)}", color = if (isPayable) theme.mildRed else theme.mildGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isPayable) theme.mildRed.copy(alpha = 0.2f) else theme.mildGreen.copy(alpha = 0.2f))
                                            .clickable { onQuickAction(dueAcc, if (isPayable) "REPAY" else "COLLECT") }
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(if (isPayable) "Repay" else "Collect", color = if (isPayable) theme.mildRed else theme.mildGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenTransfer,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent)
            ) {
                Icon(Icons.Default.MultipleStop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Intra-Account Transfer", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        item {
            Text("Wallets & Bank Accounts", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (primaryAccounts.isEmpty()) {
                Text("No Cash, Bank, or Credit cards added. Tap '+' to create one.", color = theme.textMuted, fontSize = 12.sp)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(primaryAccounts) { acc ->
                        InteractiveWalletCard(
                            account = acc,
                            onEdit = { onEditAccount(acc) },
                            onQuickAction = { action -> onQuickAction(acc, action) }
                        )
                    }
                }
            }
        }

        item {
            Text("Borrowers & Lenders", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (blAccounts.isEmpty()) {
                Text("No Borrowers or Lenders recorded. Tap '+' to create one.", color = theme.textMuted, fontSize = 12.sp)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(blAccounts) { acc ->
                        InteractivePartyCard(
                            account = acc,
                            onEdit = { onEditAccount(acc) },
                            onQuickAction = { action -> onQuickAction(acc, action) }
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
fun InteractiveWalletCard(account: Account, onEdit: () -> Unit, onQuickAction: (String) -> Unit) {
    val theme = LocalThemeColors.current
    var isRevealed by remember { mutableStateOf(!account.hasEyeMask) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.width(200.dp).clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = theme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(account.name, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(account.type, color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = theme.textMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val displayAmount = when (account.type) {
                "CREDIT" -> "Avail: ₹ ${String.format("%,.0f", account.balance)}"
                else -> "₹ ${String.format("%,.2f", account.balance)}"
            }

            Text(
                text = if (isRevealed) displayAmount else "₹ ••••••",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = theme.textBright
            )

            if (account.type == "CREDIT") {
                val billAmount = (account.totalLimit - account.balance).coerceAtLeast(0.0)
                Text("Bill: ₹ ${String.format("%,.0f", billAmount)}", color = theme.mildRed, fontSize = 10.sp)
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(if (account.type == "CREDIT") "Refill" else "+ Add", color = theme.bg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun InteractivePartyCard(account: Account, onEdit: () -> Unit, onQuickAction: (String) -> Unit) {
    val theme = LocalThemeColors.current
    var isRevealed by remember { mutableStateOf(!account.hasEyeMask) }
    val scope = rememberCoroutineScope()
    val isLender = account.type == "LENDER"

    Card(
        modifier = Modifier.width(200.dp).clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = theme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(account.name, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isLender) "LENDER" else "BORROWER", color = if (isLender) theme.mildRed else theme.mildGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = theme.textMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isRevealed) "₹ ${String.format("%,.0f", account.balance)}" else "₹ ••••••",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isLender) theme.mildRed else theme.mildGreen
            )

            val dateStr = if (account.dueDate > 0L) SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(account.dueDate)) else "No date"
            Text("Due: $dateStr", color = theme.textMuted, fontSize = 10.sp)

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(if (isLender) "Repay" else "Collect", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
        item { Spacer(Modifier.height(30.dp)) }
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

                    // Set / Change App PIN
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Security PIN", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Configured for data wipe verification", color = theme.textMuted, fontSize = 12.sp)
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
            subtitle = "Enter your 4-digit PIN to authorize ledger wipe.",
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

// ---------------- THEME-ALIGNED PIN PAD MODAL ----------------

@Composable
fun ThemePinPadDialog(
    title: String,
    subtitle: String,
    expectedPin: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val theme = LocalThemeColors.current
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = theme.surface,
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(title, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(subtitle, color = theme.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)

                // 4-Dot Display
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
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

                // 3x4 Keypad
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
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(theme.surfaceAlt)
                                        .clickable {
                                            when (key) {
                                                "C" -> {
                                                    enteredPin = ""
                                                    isError = false
                                                }
                                                "⌫" -> {
                                                    if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                                    isError = false
                                                }
                                                else -> {
                                                    if (enteredPin.length < 4) {
                                                        enteredPin += key
                                                        isError = false
                                                        if (enteredPin.length == 4) {
                                                            if (enteredPin == expectedPin) {
                                                                onSuccess()
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
                                    Text(key, color = theme.textBright, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = theme.textMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

// ---------------- SET UP NEW PIN MODAL ----------------

@Composable
fun ThemeSetPinDialog(
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit
) {
    val theme = LocalThemeColors.current
    var pinText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = theme.surface,
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Set New PIN", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Enter a 4-digit security PIN for ledger actions.", color = theme.textMuted, fontSize = 12.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until 4) {
                        val isFilled = i < pinText.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) theme.accent else theme.surfaceAlt)
                        )
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
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(theme.surfaceAlt)
                                        .clickable {
                                            when (key) {
                                                "C" -> pinText = ""
                                                "⌫" -> if (pinText.isNotEmpty()) pinText = pinText.dropLast(1)
                                                else -> {
                                                    if (pinText.length < 4) {
                                                        pinText += key
                                                        if (pinText.length == 4) {
                                                            onSavePin(pinText)
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

                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = theme.textMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

// ---------------- THEME-ALIGNED COMPOSE DATE PICKER ----------------

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
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = theme.textMuted)
            }
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
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showThemedDatePicker by remember { mutableStateOf(false) }

    val inCategories = listOf("Salary", "Freelance", "Capital Gain", "Gift", "Interest", "Other")
    val outCategories = listOf("Food", "Groceries", "Transport", "Shopping", "Bills", "Health", "Fuel", "Other")
    var category by remember { mutableStateOf(if (flowType == "IN") inCategories.first() else outCategories.first()) }
    var catExpanded by remember { mutableStateOf(false) }

    var selectedAccId by remember {
        mutableStateOf(prefilled?.accountId ?: accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var accExpanded by remember { mutableStateOf(false) }

    var isRecurring by remember { mutableStateOf(false) }
    var recurringFrequency by remember { mutableStateOf("MONTHLY") }

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text("Record Entry", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)", color = theme.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                )

                val dateFormatted = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
                OutlinedButton(
                    onClick = { showThemedDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textBright)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Date: $dateFormatted", fontSize = 13.sp)
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Merchant", color = theme.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = !catExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category", color = theme.textMuted) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = activeAccName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (flowType == "IN") "Receive Into" else "Spend From", color = theme.textMuted) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recurring Entry?", color = theme.textBright, fontSize = 13.sp)
                    Switch(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.surfaceAlt)
                    )
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
                                accountId = selectedAccId,
                                flowType = flowType,
                                type = if (flowType == "IN") "INCOME" else "EXPENSE",
                                category = category,
                                amount = amt,
                                timestamp = selectedDateMillis,
                                note = note,
                                isRecurring = isRecurring,
                                frequency = if (isRecurring) recurringFrequency else "NONE"
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

    if (showThemedDatePicker) {
        ThemedDatePickerDialog(
            initialDateMillis = selectedDateMillis,
            onDismiss = { showThemedDatePicker = false },
            onDateSelected = { selectedDateMillis = it }
        )
    }
}

// ---------------- CREATE ACCOUNT CARD POPUP ----------------

@Composable
fun CreateAccountCardDialog(
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit
) {
    val theme = LocalThemeColors.current

    var selectedType by remember { mutableStateOf("CASH") }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var totalLimit by remember { mutableStateOf("") }
    var hasEyeMask by remember { mutableStateOf(true) }
    var repaymentType by remember { mutableStateOf("BULLET") }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var installmentCount by remember { mutableStateOf("12") }

    var dueDateMillis by remember { mutableStateOf(System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L)) }
    var showThemedDueDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text("New Account Card", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    }, color = theme.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(when (selectedType) {
                        "CREDIT" -> "Available Limit (₹)"
                        "LENDER" -> "Borrowed Amount (₹)"
                        "BORROWER" -> "Lent Amount (₹)"
                        else -> "Starting Balance (₹)"
                    }, color = theme.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedType == "CREDIT") {
                    OutlinedTextField(
                        value = totalLimit,
                        onValueChange = { totalLimit = it },
                        label = { Text("Total Limit (₹)", color = theme.textMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (selectedType in listOf("CREDIT", "LENDER", "BORROWER")) {
                    val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dueDateMillis))
                    OutlinedButton(
                        onClick = { showThemedDueDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textBright)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Due Date: $dateFormatted", fontSize = 12.sp)
                    }
                }

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
                                Text(rType, color = if (isSel) theme.bg else theme.textBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mask balance with eye toggle?", color = theme.textBright, fontSize = 12.sp)
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
                                installmentCount = installmentCount.toIntOrNull() ?: 1
                            )
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

    if (showThemedDueDatePicker) {
        ThemedDatePickerDialog(
            initialDateMillis = dueDateMillis,
            onDismiss = { showThemedDueDatePicker = false },
            onDateSelected = { dueDateMillis = it }
        )
    }
}

// ---------------- QUICK ACTION MODAL ----------------

@Composable
fun QuickActionDialog(account: Account, action: String, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    val theme = LocalThemeColors.current
    var deltaAmount by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

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
                    label = { Text("Amount (₹)", color = theme.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth()
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
                        errorMsg = "Repayment cannot exceed outstanding of ₹${account.balance}"
                        return@Button
                    }
                    if (action == "COLLECT" && amt > account.balance) {
                        errorMsg = "Collection cannot exceed receivable of ₹${account.balance}"
                        return@Button
                    }
                    if (action == "REFILL" && (account.balance + amt) > account.totalLimit) {
                        errorMsg = "Available limit cannot exceed total limit of ₹${account.totalLimit}"
                        return@Button
                    }
                    onConfirm(amt)
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
}

// ---------------- INTRA-ACCOUNT TRANSFER MODAL ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntraAccountTransferDialog(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onTransfer: (Account, Account, Double) -> Unit
) {
    val theme = LocalThemeColors.current
    var fromAccId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var toAccId by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: accounts.firstOrNull()?.id ?: 0L) }
    var amount by remember { mutableStateOf("") }

    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text("Intra-Account Transfer", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = fromExpanded, onExpandedChange = { fromExpanded = !fromExpanded }, modifier = Modifier.fillMaxWidth()) {
                    val fromName = accounts.firstOrNull { it.id == fromAccId }?.name ?: "Select"
                    OutlinedTextField(value = fromName, onValueChange = {}, readOnly = true, label = { Text("From", color = theme.textMuted) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromExpanded) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt), modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = fromExpanded, onDismissRequest = { fromExpanded = false }, modifier = Modifier.background(theme.surface)) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text(acc.name, color = theme.textBright) }, onClick = { fromAccId = acc.id; fromExpanded = false })
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = toExpanded, onExpandedChange = { toExpanded = !toExpanded }, modifier = Modifier.fillMaxWidth()) {
                    val toName = accounts.firstOrNull { it.id == toAccId }?.name ?: "Select"
                    OutlinedTextField(value = toName, onValueChange = {}, readOnly = true, label = { Text("To", color = theme.textMuted) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toExpanded) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt), modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = toExpanded, onDismissRequest = { toExpanded = false }, modifier = Modifier.background(theme.surface)) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text(acc.name, color = theme.textBright) }, onClick = { toAccId = acc.id; toExpanded = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Transfer Amount (₹)", color = theme.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    val fAcc = accounts.firstOrNull { it.id == fromAccId }
                    val tAcc = accounts.firstOrNull { it.id == toAccId }
                    if (amt > 0.0 && fAcc != null && tAcc != null && fAcc.id != tAcc.id) {
                        onTransfer(fAcc, tAcc, amt)
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
}

// ---------------- EDIT ACCOUNT MODAL ----------------

@Composable
fun EditAccountCardDialog(
    account: Account,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalThemeColors.current
    var name by remember { mutableStateOf(account.name) }
    var balance by remember { mutableStateOf(account.balance.toString()) }
    var hasEyeMask by remember { mutableStateOf(account.hasEyeMask) }

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text("Edit ${account.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = theme.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("Current Amount (₹)", color = theme.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Eye mask toggle?", color = theme.textBright, fontSize = 12.sp)
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
                    onSave(account.copy(name = name, balance = bal, hasEyeMask = hasEyeMask))
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
}

// ---------------- B/L HISTORY MODAL ----------------

@Composable
fun BorrowLendHistoryDialog(
    transactions: List<Transaction>,
    onDismiss: () -> Unit
) {
    val theme = LocalThemeColors.current

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text("Borrow & Lend History", fontWeight = FontWeight.Bold) },
        text = {
            if (transactions.isEmpty()) {
                Text("No recorded B/L transactions yet.", color = theme.textMuted, fontSize = 12.sp)
            } else {
                LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(transactions) { tx ->
                        val dStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(tx.timestamp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${tx.flowType} • ${tx.partyName}", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("$dStr • ${tx.note.ifBlank { "No notes" }}", color = theme.textMuted, fontSize = 10.sp)
                            }
                            Text("₹ ${String.format("%.0f", tx.amount)}", color = if (tx.flowType in listOf("LEND", "COLLECT")) theme.mildGreen else theme.mildRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = theme.accent)) {
                Text("Close", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        }
    )
}
