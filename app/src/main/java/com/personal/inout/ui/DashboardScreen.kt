package com.personal.inout.ui

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.personal.inout.data.Account
import com.personal.inout.data.AppDatabase
import com.personal.inout.data.Counterparty
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

    var activeThemeMode by remember { mutableStateOf(AppThemeMode.AMBER_OCHRE) }
    val theme = when (activeThemeMode) {
        AppThemeMode.AMBER_OCHRE -> AmberTheme
        AppThemeMode.OLIVE_MATCHA -> OliveMatchaTheme
        AppThemeMode.SAND_DUNE -> SandDuneTheme
    }

    val accounts by db.vaultDao().getAllAccounts().collectAsState(initial = emptyList())
    val transactions by db.vaultDao().getAllTransactions().collectAsState(initial = emptyList())
    val counterparties by db.vaultDao().getAllCounterparties().collectAsState(initial = emptyList())
    val stagedSms by db.vaultDao().getStagedSms().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) } // 0: Ledger, 1: Accounts, 2: Review, 3: Settings

    var showMainTxDialog by remember { mutableStateOf(false) }
    var prefilledTx by remember { mutableStateOf<Transaction?>(null) }
    var showBLDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var showBLHistorySheet by remember { mutableStateOf(false) }

    // Direct Camera Photo Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
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

    // Direct Gallery File Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
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
                    Toast.makeText(context, "Gallery OCR failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                        counterparties = counterparties,
                        onAddAccount = { showAccountDialog = true },
                        onEditAccount = { editingAccount = it },
                        onOpenBLDialog = { showBLDialog = true },
                        onOpenTransfer = { showTransferDialog = true },
                        onSetDefault = { acc ->
                            scope.launch {
                                db.vaultDao().clearDefaultAccounts()
                                db.vaultDao().setDefaultAccount(acc.id)
                            }
                        },
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
                            try {
                                cameraLauncher.launch(null)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot launch camera: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onGalleryClick = {
                            try {
                                galleryLauncher.launch("image/*")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open gallery: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onManualSyncSms = {
                            scope.launch {
                                db.vaultDao().insertSmsDraft(
                                    SmsDraft(
                                        rawSender = "HDFC-BANK",
                                        rawBody = "Sent Rs. 480.00 from Account to UBER on 13-09-2026. Ref 109281.",
                                        amount = 480.0,
                                        merchant = "UBER"
                                    )
                                )
                                Toast.makeText(context, "Simulated SMS queued", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    3 -> SettingsTabScreen(
                        currentTheme = activeThemeMode,
                        onSelectTheme = { activeThemeMode = it },
                        onClearLedger = {
                            scope.launch {
                                db.vaultDao().clearAllTransactions()
                                db.vaultDao().clearAllSms()
                                Toast.makeText(context, "Ledger records cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            if (showMainTxDialog) {
                MainLedgerTransactionDialog(
                    accounts = accounts,
                    prefilled = prefilledTx,
                    onDismiss = { showMainTxDialog = false },
                    onSave = { tx ->
                        scope.launch {
                            db.vaultDao().insertTransaction(tx)
                            val acc = accounts.firstOrNull { it.id == tx.accountId }
                            if (acc != null) {
                                val delta = if (tx.flowType == "IN") tx.amount else -tx.amount
                                db.vaultDao().updateAccount(acc.copy(balance = acc.balance + delta))
                            }
                            showMainTxDialog = false
                        }
                    }
                )
            }

            if (showBLDialog) {
                BorrowLendDialog(
                    accounts = accounts,
                    counterparties = counterparties,
                    onDismiss = { showBLDialog = false },
                    onSave = { tx, partyName, partyRole ->
                        scope.launch {
                            var party = counterparties.firstOrNull { it.name.equals(partyName, ignoreCase = true) }
                            if (party == null) {
                                val newId = db.vaultDao().insertCounterparty(
                                    Counterparty(name = partyName, role = partyRole, currentBalance = tx.amount)
                                )
                                party = Counterparty(id = newId, name = partyName, role = partyRole, currentBalance = tx.amount)
                            } else {
                                val updatedBalance = when (tx.flowType) {
                                    "BORROW", "LEND" -> party.currentBalance + tx.amount
                                    "REPAY", "COLLECT" -> (party.currentBalance - tx.amount).coerceAtLeast(0.0)
                                    else -> party.currentBalance
                                }
                                db.vaultDao().updateCounterparty(party.copy(currentBalance = updatedBalance))
                            }

                            db.vaultDao().insertTransaction(tx.copy(counterpartyId = party.id, partyName = party.name))

                            val acc = accounts.firstOrNull { it.id == tx.accountId }
                            if (acc != null) {
                                val delta = when (tx.flowType) {
                                    "BORROW", "COLLECT" -> tx.amount
                                    "LEND", "REPAY" -> -tx.amount
                                    else -> 0.0
                                }
                                db.vaultDao().updateAccount(acc.copy(balance = acc.balance + delta))
                            }
                            showBLDialog = false
                        }
                    }
                )
            }

            if (showTransferDialog) {
                IntraAccountTransferDialog(
                    accounts = accounts,
                    onDismiss = { showTransferDialog = false },
                    onTransfer = { fromAcc, toAcc, amount ->
                        scope.launch {
                            db.vaultDao().updateAccount(fromAcc.copy(balance = fromAcc.balance - amount))
                            db.vaultDao().updateAccount(toAcc.copy(balance = toAcc.balance + amount))
                            db.vaultDao().insertTransaction(
                                Transaction(
                                    accountId = fromAcc.id,
                                    flowType = "TRANSFER",
                                    type = "NEUTRAL",
                                    category = "Internal Transfer",
                                    amount = amount,
                                    note = "Transfer to ${toAcc.name}"
                                )
                            )
                            showTransferDialog = false
                            Toast.makeText(context, "Transferred ₹$amount", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            if (showAccountDialog || editingAccount != null) {
                val target = editingAccount
                AccountEditorModal(
                    account = target,
                    onDismiss = {
                        showAccountDialog = false
                        editingAccount = null
                    },
                    onSave = { name, balance, type, limit ->
                        scope.launch {
                            if (target == null) {
                                db.vaultDao().insertAccount(
                                    Account(name = name, balance = balance, type = type, monthlyLimit = limit, isDefault = accounts.isEmpty())
                                )
                            } else {
                                db.vaultDao().updateAccount(
                                    target.copy(name = name, balance = balance, type = type, monthlyLimit = limit)
                                )
                            }
                            showAccountDialog = false
                            editingAccount = null
                        }
                    },
                    onDelete = {
                        target?.let {
                            scope.launch {
                                db.vaultDao().deleteAccount(it.id)
                                editingAccount = null
                            }
                        }
                    }
                )
            }

            if (showBLHistorySheet) {
                BorrowLendHistoryDialog(
                    transactions = transactions.filter { it.flowType in listOf("BORROW", "LEND", "REPAY", "COLLECT") },
                    counterparties = counterparties,
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
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("THIS MONTH'S FLOW", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Out: ", color = theme.textMuted, fontSize = 13.sp)
                            Text("₹ ${String.format("%,.0f", totalOut)}", color = theme.mildRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("In:    ", color = theme.textMuted, fontSize = 13.sp)
                            Text("₹ ${String.format("%,.0f", totalIn)}", color = theme.mildGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .weight(0.8f),
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
                        Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = theme.textMuted, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No records found", color = theme.textBright, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Text("Tap '+' below to record an income or expense.", color = theme.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(transactions) { tx ->
                val dateFmt = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(tx.timestamp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.surface)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(theme.surfaceAlt),
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
    counterparties: List<Counterparty>,
    onAddAccount: () -> Unit,
    onEditAccount: (Account) -> Unit,
    onOpenBLDialog: () -> Unit,
    onOpenTransfer: () -> Unit,
    onSetDefault: (Account) -> Unit,
    onViewBLHistory: () -> Unit
) {
    val theme = LocalThemeColors.current
    val blTxs = transactions.filter { it.flowType in listOf("BORROW", "LEND", "REPAY", "COLLECT") }

    val totalBorrowed = blTxs.filter { it.flowType == "BORROW" }.sumOf { it.amount } - blTxs.filter { it.flowType == "REPAY" }.sumOf { it.amount }
    val totalLent = blTxs.filter { it.flowType == "LEND" }.sumOf { it.amount } - blTxs.filter { it.flowType == "COLLECT" }.sumOf { it.amount }

    val now = System.currentTimeMillis()
    val sevenDaysFromNow = now + (7 * 24 * 60 * 60 * 1000L)
    val upcomingItems = blTxs.filter { it.returnDate in now..sevenDaysFromNow }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
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
                        Text("BORROW / LEND LEDGER", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("History →", color = theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onViewBLHistory() })
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Receivables (Lent)", color = theme.textMuted, fontSize = 11.sp)
                            Text("₹ ${String.format("%,.2f", totalLent.coerceAtLeast(0.0))}", color = theme.mildGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Obligations (Borrowed)", color = theme.textMuted, fontSize = 11.sp)
                            Text("₹ ${String.format("%,.2f", totalBorrowed.coerceAtLeast(0.0))}", color = theme.mildRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (upcomingItems.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Divider(color = theme.surfaceAlt)
                        Spacer(Modifier.height(8.dp))
                        Text("Upcoming within 7 days:", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        upcomingItems.forEach { up ->
                            val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(up.returnDate))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${if (up.flowType == "BORROW") "Pay to" else "Collect from"} ${up.partyName} ($dateStr)", color = theme.textBright, fontSize = 12.sp)
                                Text("₹ ${String.format("%.0f", up.amount)}", color = if (up.flowType == "BORROW") theme.mildRed else theme.mildGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onOpenBLDialog,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = theme.bg, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New Borrow / Lend Action", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Accounts & Cards", color = theme.textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onAddAccount) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Account", color = theme.accent, fontSize = 13.sp)
                }
            }
        }

        if (accounts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddAccount() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("No accounts added yet.", color = theme.textBright, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Text("Tap 'Add Account' above to configure your Cash or Bank accounts.", color = theme.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(accounts) { acc ->
                AccountCardItem(
                    account = acc,
                    onEdit = { onEditAccount(acc) },
                    onSetDefault = { onSetDefault(acc) }
                )
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
fun AccountCardItem(
    account: Account,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit
) {
    val theme = LocalThemeColors.current
    var isRevealed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = theme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(account.name, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (account.isDefault) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(theme.accent.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("DEFAULT", color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account.type,
                        color = if (account.type == "LOAN") theme.mildRed else theme.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = theme.textMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRevealed) "₹ ${String.format("%,.2f", account.balance)}" else "₹ ••••••",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.textBright
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            isRevealed = true
                            scope.launch {
                                delay(5000)
                                isRevealed = false
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isRevealed) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Peek Balance",
                            tint = theme.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (!account.isDefault && account.type != "LOAN") {
                        TextButton(onClick = onSetDefault) {
                            Text("Make Default", color = theme.accent, fontSize = 11.sp)
                        }
                    }
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
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
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
                        Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.DoneAll, contentDescription = null, tint = theme.accent, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Queue is empty", color = theme.textBright, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Text("Auto-parsed SMS transactions and OCR drafts stage here.", color = theme.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(stagedSms) { draft ->
                val isExpanded = expandedDraftId == draft.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { expandedDraftId = if (isExpanded) null else draft.id },
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
    var showConfirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
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
                    Text("Palette Selection", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                            Text("Automatic SMS Scan", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Background WorkManager active hourly", color = theme.mildGreen, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = theme.mildGreen)
                    }

                    Divider(color = theme.surfaceAlt)

                    TextButton(
                        onClick = { showConfirmClear = true },
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

    if (showConfirmClear) {
        AlertDialog(
            containerColor = theme.surface,
            titleContentColor = theme.textBright,
            textContentColor = theme.textMuted,
            onDismissRequest = { showConfirmClear = false },
            title = { Text("Clear Entire Ledger?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently wipe all logged transactions, loans, and staged review drafts. Account balances will not be reset.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearLedger()
                        showConfirmClear = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed)
                ) {
                    Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) { Text("Cancel", color = theme.textMuted) }
            }
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
    val context = LocalContext.current

    var flowType by remember { mutableStateOf(prefilled?.flowType ?: "OUT") }
    var amount by remember { mutableStateOf(if (prefilled != null && prefilled.amount > 0) prefilled.amount.toString() else "") }
    var note by remember { mutableStateOf(prefilled?.note ?: "") }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }

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
    var freqExpanded by remember { mutableStateOf(false) }

    val cal = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            cal.set(year, month, dayOfMonth)
            selectedDateMillis = cal.timeInMillis
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text("Log Cash Flow Entry", fontWeight = FontWeight.Bold) },
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textBright,
                        unfocusedTextColor = theme.textBright,
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.surfaceAlt
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                val dateFormatted = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
                OutlinedButton(
                    onClick = { datePickerDialog.show() },
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textBright,
                        unfocusedTextColor = theme.textBright,
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.surfaceAlt
                    ),
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.textBright,
                            unfocusedTextColor = theme.textBright,
                            focusedBorderColor = theme.accent,
                            unfocusedBorderColor = theme.surfaceAlt
                        ),
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
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = theme.textBright,
                                unfocusedTextColor = theme.textBright,
                                focusedBorderColor = theme.accent,
                                unfocusedBorderColor = theme.surfaceAlt
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = accExpanded,
                            onDismissRequest = { accExpanded = false },
                            modifier = Modifier.background(theme.surface)
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name, color = theme.textBright) },
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
                    Text("Recurring Payment?", color = theme.textBright, fontSize = 13.sp)
                    Switch(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.surfaceAlt)
                    )
                }

                AnimatedVisibility(visible = isRecurring) {
                    ExposedDropdownMenuBox(
                        expanded = freqExpanded,
                        onExpandedChange = { freqExpanded = !freqExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = recurringFrequency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Frequency", color = theme.textMuted) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = theme.textBright,
                                unfocusedTextColor = theme.textBright,
                                focusedBorderColor = theme.accent,
                                unfocusedBorderColor = theme.surfaceAlt
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = freqExpanded,
                            onDismissRequest = { freqExpanded = false },
                            modifier = Modifier.background(theme.surface)
                        ) {
                            listOf("DAILY", "WEEKLY", "MONTHLY").forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text(freq, color = theme.textBright) },
                                    onClick = {
                                        recurringFrequency = freq
                                        freqExpanded = false
                                    }
                                )
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
}

// ---------------- DEDICATED BORROW / LEND / REPAY / COLLECT MODAL ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowLendDialog(
    accounts: List<Account>,
    counterparties: List<Counterparty>,
    onDismiss: () -> Unit,
    onSave: (Transaction, String, String) -> Unit
) {
    val theme = LocalThemeColors.current
    val context = LocalContext.current

    var blType by remember { mutableStateOf("BORROW") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var partyName by remember { mutableStateOf("") }
    var partyDropdownExpanded by remember { mutableStateOf(false) }

    var actionDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var returnDateMillis by remember { mutableStateOf(System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)) }

    var selectedAccId by remember { mutableStateOf(accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id ?: 0L) }
    var accDropdownExpanded by remember { mutableStateOf(false) }

    val cal = Calendar.getInstance()
    val actionDatePicker = DatePickerDialog(
        context,
        { _, y, m, d ->
            cal.set(y, m, d)
            actionDateMillis = cal.timeInMillis
        },
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
    )

    val returnDatePicker = DatePickerDialog(
        context,
        { _, y, m, d ->
            cal.set(y, m, d)
            returnDateMillis = cal.timeInMillis
        },
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
    )

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text("Borrow & Lend Operation", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("BORROW", "REPAY", "LEND", "COLLECT").forEach { mode ->
                        val isSel = blType == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else theme.surfaceAlt)
                                .clickable { blType = mode }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(mode, color = if (isSel) theme.bg else theme.textBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)", color = theme.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textBright,
                        unfocusedTextColor = theme.textBright,
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.surfaceAlt
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                val relevantParties = counterparties.filter {
                    if (blType in listOf("BORROW", "REPAY")) it.role == "LENDER" else it.role == "BORROWER"
                }

                if (relevantParties.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = partyDropdownExpanded,
                        onExpandedChange = { partyDropdownExpanded = !partyDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = partyName,
                            onValueChange = { partyName = it },
                            label = { Text(if (blType in listOf("BORROW", "REPAY")) "Lender Name" else "Borrower Name", color = theme.textMuted) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyDropdownExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = partyDropdownExpanded,
                            onDismissRequest = { partyDropdownExpanded = false },
                            modifier = Modifier.background(theme.surface)
                        ) {
                            relevantParties.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("${p.name} (Bal: ₹${p.currentBalance.toInt()})", color = theme.textBright) },
                                    onClick = {
                                        partyName = p.name
                                        partyDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = partyName,
                        onValueChange = { partyName = it },
                        label = { Text(if (blType in listOf("BORROW", "REPAY")) "Lender Name" else "Borrower Name", color = theme.textMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val aStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(actionDateMillis))
                    OutlinedButton(
                        onClick = { actionDatePicker.show() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textBright)
                    ) {
                        Text("Date: $aStr", fontSize = 11.sp)
                    }

                    if (blType in listOf("BORROW", "LEND")) {
                        val rStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(returnDateMillis))
                        OutlinedButton(
                            onClick = { returnDatePicker.show() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textBright)
                        ) {
                            Text("Due: $rStr", fontSize = 11.sp)
                        }
                    }
                }

                if (accounts.isNotEmpty()) {
                    val activeAccName = accounts.firstOrNull { it.id == selectedAccId }?.name ?: "Select Account"
                    ExposedDropdownMenuBox(
                        expanded = accDropdownExpanded,
                        onExpandedChange = { accDropdownExpanded = !accDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = activeAccName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Account Channel", color = theme.textMuted) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accDropdownExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = accDropdownExpanded,
                            onDismissRequest = { accDropdownExpanded = false },
                            modifier = Modifier.background(theme.surface)
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name, color = theme.textBright) },
                                    onClick = {
                                        selectedAccId = acc.id
                                        accDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Memo / Terms", color = theme.textMuted) },
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
                    if (amt > 0.0 && partyName.isNotBlank()) {
                        val role = if (blType in listOf("BORROW", "REPAY")) "LENDER" else "BORROWER"
                        onSave(
                            Transaction(
                                accountId = selectedAccId,
                                flowType = blType,
                                type = "NEUTRAL",
                                category = "Borrow/Lend",
                                amount = amt,
                                timestamp = actionDateMillis,
                                note = note,
                                returnDate = returnDateMillis
                            ),
                            partyName,
                            role
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
                ExposedDropdownMenuBox(
                    expanded = fromExpanded,
                    onExpandedChange = { fromExpanded = !fromExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val fromName = accounts.firstOrNull { it.id == fromAccId }?.name ?: "Select"
                    OutlinedTextField(
                        value = fromName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Transfer From", color = theme.textMuted) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = fromExpanded, onDismissRequest = { fromExpanded = false }, modifier = Modifier.background(theme.surface)) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text(acc.name, color = theme.textBright) }, onClick = { fromAccId = acc.id; fromExpanded = false })
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = toExpanded,
                    onExpandedChange = { toExpanded = !toExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val toName = accounts.firstOrNull { it.id == toAccId }?.name ?: "Select"
                    OutlinedTextField(
                        value = toName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Transfer To", color = theme.textMuted) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
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

// ---------------- ACCOUNT EDITOR MODAL ----------------

@Composable
fun AccountEditorModal(
    account: Account?,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, Double) -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalThemeColors.current
    var name by remember { mutableStateOf(account?.name ?: "") }
    var balance by remember { mutableStateOf(account?.balance?.toString() ?: "") }
    var limit by remember { mutableStateOf(if (account != null && account.monthlyLimit > 0) account.monthlyLimit.toString() else "") }
    var type by remember { mutableStateOf(account?.type ?: "BANK") }

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "New Account" else "Edit Account", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name", color = theme.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("Available Balance / Debt (₹)", color = theme.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it },
                    label = { Text("Monthly Spending Limit", color = theme.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textBright, unfocusedTextColor = theme.textBright, focusedBorderColor = theme.accent, unfocusedBorderColor = theme.surfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Type", color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("BANK", "CASH", "CREDIT", "LOAN").forEach { t ->
                        val isSel = type == t
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else theme.surfaceAlt)
                                .clickable { type = t }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(t, color = if (isSel) theme.bg else theme.textBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bal = balance.toDoubleOrNull() ?: 0.0
                    val lim = limit.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) onSave(name, bal, type, lim)
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Save", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (account != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = theme.mildRed) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) }
            }
        }
    )
}

// ---------------- B/L HISTORY MODAL ----------------

@Composable
fun BorrowLendHistoryDialog(
    transactions: List<Transaction>,
    counterparties: List<Counterparty>,
    onDismiss: () -> Unit
) {
    val theme = LocalThemeColors.current

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text("B/L Counterparty Ledger", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Active Parties", color = theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(counterparties) { p ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = theme.surfaceAlt),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(p.name, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${p.role}: ₹${p.currentBalance.toInt()}", color = if (p.role == "LENDER") theme.mildRed else theme.mildGreen, fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Transaction Log", color = theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (transactions.isEmpty()) {
                    Text("No records found.", color = theme.textMuted, fontSize = 12.sp)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(transactions) { tx ->
                            val dStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(tx.timestamp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.surfaceAlt)
                                    .padding(8.dp),
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
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = theme.accent)) {
                Text("Close", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        }
    )
}
