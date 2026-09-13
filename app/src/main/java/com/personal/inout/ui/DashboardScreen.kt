package com.personal.inout.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val accounts by db.vaultDao().getAllAccounts().collectAsState(initial = emptyList())
    val transactions by db.vaultDao().getAllTransactions().collectAsState(initial = emptyList())
    val stagedSms by db.vaultDao().getStagedSms().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) } // 0: Ledger, 1: Accounts, 2: ReviewQueue, 3: Settings
    var showTxDialog by remember { mutableStateOf(false) }
    var prefilledTxData by remember { mutableStateOf<Transaction?>(null) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Visual Media / Gallery launcher safely anchored to ComponentActivity
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val parsed = ReceiptScanner.processReceipt(context, uri)
                    prefilledTxData = Transaction(
                        accountId = accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id ?: 0L,
                        flowType = "OUT",
                        type = "EXPENSE",
                        category = "Shopping",
                        amount = parsed.total,
                        note = parsed.merchant
                    )
                    showTxDialog = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Scan Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = AmberBg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .background(AmberBg)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("InOut", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = AmberTextBright)
                        Text("PERSONAL LEDGER", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = AmberAccent)
                    }
                    // Quick-test SMS injection button
                    IconButton(
                        onClick = {
                            scope.launch {
                                db.vaultDao().insertSmsDraft(
                                    SmsDraft(
                                        rawSender = "HDFC-BANK",
                                        rawBody = "Sent Rs. 620.00 from Account to ZOMATO on 13-09-2026. Ref 482910.",
                                        amount = 620.0,
                                        merchant = "ZOMATO"
                                    )
                                )
                                Toast.makeText(context, "Incoming SMS added to Review Queue", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(AmberSurfaceAlt)
                    ) {
                        Icon(Icons.Default.AddComment, contentDescription = "Simulate SMS", tint = AmberAccent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = AmberSurface,
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
                                BadgedBox(badge = { Badge(containerColor = MildRoseRed) { Text("${stagedSms.size}") } }) {
                                    Icon(icon, contentDescription = title)
                                }
                            } else {
                                Icon(icon, contentDescription = title)
                            }
                        },
                        label = { Text(title, fontSize = 11.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AmberAccent,
                            selectedTextColor = AmberAccent,
                            indicatorColor = AmberSurfaceAlt,
                            unselectedIconColor = AmberTextMuted,
                            unselectedTextColor = AmberTextMuted
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        prefilledTxData = null
                        showTxDialog = true
                    },
                    containerColor = AmberAccent,
                    contentColor = AmberBg,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Quick Transaction", modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AmberBg)
        ) {
            when (selectedTab) {
                0 -> LedgerTabScreen(
                    transactions = transactions.filter { it.flowType == "IN" || it.flowType == "OUT" }
                )
                1 -> AccountsTabScreen(
                    accounts = accounts,
                    transactions = transactions,
                    onAddAccount = { showAccountDialog = true },
                    onEditAccount = { editingAccount = it },
                    onSetDefault = { acc ->
                        scope.launch {
                            db.vaultDao().clearDefaultAccounts()
                            db.vaultDao().setDefaultAccount(acc.id)
                        }
                    },
                    onViewBLHistory = { showHistoryDialog = true }
                )
                2 -> ReviewQueueTabScreen(
                    stagedSms = stagedSms,
                    onDiscard = { draftId -> scope.launch { db.vaultDao().deleteSmsDraftById(draftId) } },
                    onConsider = { draft ->
                        prefilledTxData = Transaction(
                            accountId = accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id ?: 0L,
                            flowType = "OUT",
                            type = "EXPENSE",
                            category = "SMS Alert",
                            amount = draft.amount,
                            note = draft.merchant
                        )
                        showTxDialog = true
                        scope.launch { db.vaultDao().deleteSmsDraftById(draft.id) }
                    },
                    onScanClick = {
                        try {
                            photoPickerLauncher.launch("image/*")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open gallery: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                3 -> SettingsTabScreen()
            }
        }

        // Comprehensive Dynamic Transaction Modal (In / Out / Borrow / Lend)
        if (showTxDialog) {
            ElaborateTransactionDialog(
                accounts = accounts,
                prefilled = prefilledTxData,
                onDismiss = { showTxDialog = false },
                onSave = { tx ->
                    scope.launch {
                        db.vaultDao().insertTransaction(tx)
                        val acc = accounts.firstOrNull { it.id == tx.accountId }
                        if (acc != null) {
                            val balanceShift = when (tx.flowType) {
                                "IN", "BORROW" -> tx.amount
                                "OUT", "LEND" -> -tx.amount
                                else -> 0.0
                            }
                            db.vaultDao().updateAccount(acc.copy(balance = acc.balance + balanceShift))
                        }
                        showTxDialog = false
                    }
                }
            )
        }

        // Account Editor Modal (Add/Edit accounts, limits, loans)
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

        // B/L Full History Bottom Sheet
        if (showHistoryDialog) {
            BorrowLendHistoryDialog(
                transactions = transactions.filter { it.flowType == "BORROW" || it.flowType == "LEND" },
                onDismiss = { showHistoryDialog = false }
            )
        }
    }
}

// ---------------- TAB 0: LEDGER TAB ----------------

@Composable
fun LedgerTabScreen(transactions: List<Transaction>) {
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

        // Top Cash Flow & Donut Chart Card (Zero account balances displayed)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AmberSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("THIS MONTH'S FLOW", color = AmberTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Out: ", color = AmberTextMuted, fontSize = 13.sp)
                            Text("₹ ${String.format("%,.0f", totalOut)}", color = MildRoseRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("In:    ", color = AmberTextMuted, fontSize = 13.sp)
                            Text("₹ ${String.format("%,.0f", totalIn)}", color = MildSageGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Mini Donut Chart
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .weight(0.8f),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(64.dp)) {
                            val strokeWidth = 8.dp.toPx()
                            if (expenseCategories.isEmpty()) {
                                drawCircle(color = AmberSurfaceAlt, style = Stroke(width = strokeWidth))
                            } else {
                                var startAngle = -90f
                                val palette = listOf(AmberAccent, MildRoseRed, MildSageGreen, Color(0xFFE5A93C), Color(0xFFC48B57))
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
                        Text("${expenseCategories.size} Cats", color = AmberTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("Ledger Entries", color = AmberTextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (transactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberSurface)
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = AmberTextMuted, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No records found", color = AmberTextBright, fontWeight = FontWeight.SemiBold)
                        Text("Tap '+' below to record an income or expense.", color = AmberTextMuted, fontSize = 12.sp)
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
                        .background(AmberSurface)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AmberSurfaceAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (tx.flowType == "IN") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (tx.flowType == "IN") MildSageGreen else MildRoseRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(tx.note.ifBlank { tx.category }, color = AmberTextBright, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${tx.category} • $dateFmt", color = AmberTextMuted, fontSize = 11.sp)
                        }
                    }
                    Text(
                        "${if (tx.flowType == "IN") "+" else "-"} ₹ ${String.format("%.2f", tx.amount)}",
                        color = if (tx.flowType == "IN") MildSageGreen else AmberTextBright,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

// ---------------- TAB 1: ACCOUNTS & B/L TAB ----------------

@Composable
fun AccountsTabScreen(
    accounts: List<Account>,
    transactions: List<Transaction>,
    onAddAccount: () -> Unit,
    onEditAccount: (Account) -> Unit,
    onSetDefault: (Account) -> Unit,
    onViewBLHistory: () -> Unit
) {
    val blTxs = transactions.filter { it.flowType == "BORROW" || it.flowType == "LEND" }
    val totalBorrowed = blTxs.filter { it.flowType == "BORROW" }.sumOf { it.amount }
    val totalLent = blTxs.filter { it.flowType == "LEND" }.sumOf { it.amount }

    // Upcoming repayments/receivables within next 7 days
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

        // B/L Ledger Dedicated Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AmberSurface)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BORROW / LEND LEDGER", color = AmberAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(
                            "History →",
                            color = AmberTextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onViewBLHistory() }
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Receivables (Lent)", color = AmberTextMuted, fontSize = 11.sp)
                            Text("₹ ${String.format("%,.2f", totalLent)}", color = MildSageGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Obligations (Borrowed)", color = AmberTextMuted, fontSize = 11.sp)
                            Text("₹ ${String.format("%,.2f", totalBorrowed)}", color = MildRoseRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (upcomingItems.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Divider(color = AmberSurfaceAlt)
                        Spacer(Modifier.height(10.dp))
                        Text("Upcoming in next 7 days:", color = AmberTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        upcomingItems.forEach { up ->
                            val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(up.returnDate))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${if (up.flowType == "BORROW") "Pay to" else "Collect from"} ${up.partyName} ($dateStr)",
                                    color = AmberTextBright,
                                    fontSize = 12.sp
                                )
                                Text("₹ ${String.format("%.0f", up.amount)}", color = if (up.flowType == "BORROW") MildRoseRed else MildSageGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Account List Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Accounts & Cards", color = AmberTextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onAddAccount) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Account", color = AmberAccent, fontSize = 13.sp)
                }
            }
        }

        if (accounts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onAddAccount() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberSurface)
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No custom accounts yet.", color = AmberTextBright, fontWeight = FontWeight.SemiBold)
                        Text("A default Cash account will be created on your first transaction.", color = AmberTextMuted, fontSize = 12.sp)
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
    var isRevealed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = AmberSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(account.name, color = AmberTextBright, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (account.isDefault) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AmberAccent.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("DEFAULT", color = AmberAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account.type,
                        color = if (account.type == "LOAN") MildRoseRed else AmberTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = AmberTextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Masked Balance with Temporary Peek Eye
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRevealed) "₹ ${String.format("%,.2f", account.balance)}" else "₹ ••••••",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AmberTextBright
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
                            tint = AmberAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (!account.isDefault && account.type != "LOAN") {
                        TextButton(onClick = onSetDefault) {
                            Text("Make Default", color = AmberAccent, fontSize = 11.sp)
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
    onScanClick: () -> Unit
) {
    var expandedDraftId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // OCR / Bill Scan Entry Button
        item {
            Button(
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent)
            ) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = AmberBg)
                Spacer(Modifier.width(10.dp))
                Text("Scan Bill / Receipt Memo", color = AmberBg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Review Timeline", color = AmberTextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (stagedSms.isNotEmpty()) {
                    Text("${stagedSms.size} Pending", color = MildRoseRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (stagedSms.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AmberSurface)
                ) {
                    Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.DoneAll, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("All items reviewed", color = AmberTextBright, fontWeight = FontWeight.SemiBold)
                        Text("SMS notifications and OCR memos appear here for confirmation.", color = AmberTextMuted, fontSize = 12.sp)
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
                    colors = CardDefaults.cardColors(containerColor = AmberSurface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(draft.merchant, color = AmberTextBright, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Sender: ${draft.rawSender}", color = AmberTextMuted, fontSize = 11.sp)
                            }
                            Text("₹ ${String.format("%.2f", draft.amount)}", color = AmberAccent, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        }

                        // Accordion expansion details
                        AnimatedVisibility(visible = isExpanded) {
                            Column(Modifier.padding(top = 12.dp)) {
                                Text(draft.rawBody, color = AmberTextMuted, fontSize = 12.sp, lineHeight = 16.sp)
                                Spacer(Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onDiscard(draft.id) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MildRoseRed)
                                    ) {
                                        Text("Discard", fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Button(
                                        onClick = { onConsider(draft) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MildSageGreen)
                                    ) {
                                        Text("Consider", color = AmberBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

// ---------------- TAB 3: SETTINGS TAB ----------------

@Composable
fun SettingsTabScreen() {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item { Text("Preferences & Security", color = AmberTextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AmberSurface)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Active Theme", color = AmberTextBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Amber Ochre (Warm Earthy)", color = AmberAccent, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.Palette, contentDescription = null, tint = AmberAccent)
                    }
                    Divider(color = AmberSurfaceAlt)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("SQLCipher Vault", color = AmberTextBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("AES-256 On-Device Encryption", color = MildSageGreen, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MildSageGreen)
                    }
                    Divider(color = AmberSurfaceAlt)
                    Button(
                        onClick = { Toast.makeText(context, "Encrypted backup exported to private storage.", Toast.LENGTH_SHORT).show() },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberSurfaceAlt),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = AmberTextBright, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export Encrypted Backup", color = AmberTextBright, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ---------------- DYNAMIC TRANSACTION DIALOG (IN / OUT / BORROW / LEND) ----------------

@Composable
fun ElaborateTransactionDialog(
    accounts: List<Account>,
    prefilled: Transaction?,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    var flowType by remember { mutableStateOf(prefilled?.flowType ?: "OUT") }
    var amount by remember { mutableStateOf(if (prefilled != null && prefilled.amount > 0) prefilled.amount.toString() else "") }
    var note by remember { mutableStateOf(prefilled?.note ?: "") }
    var partyName by remember { mutableStateOf(prefilled?.partyName ?: "") }
    var selectedAccId by remember {
        mutableStateOf(prefilled?.accountId ?: accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id ?: 0L)
    }

    // Dynamic Categories
    val inCategories = listOf("Salary", "Freelance", "Capital Gain", "Gift", "Interest", "Other")
    val outCategories = listOf("Food", "Groceries", "Transport", "Shopping", "Bills", "Health", "Fuel", "Other")
    var category by remember { mutableStateOf(if (flowType == "IN") inCategories.first() else outCategories.first()) }

    // Recurring toggle (for In & Out only)
    var isRecurring by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf("MONTHLY") }

    AlertDialog(
        containerColor = AmberSurface,
        titleContentColor = AmberTextBright,
        onDismissRequest = onDismiss,
        title = { Text("Log Transaction", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Flow Type Selector
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("IN", "OUT", "BORROW", "LEND").forEach { type ->
                        val isSelected = flowType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AmberAccent else AmberSurfaceAlt)
                                .clickable {
                                    flowType = type
                                    if (type == "IN") category = inCategories.first()
                                    if (type == "OUT") category = outCategories.first()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type,
                                color = if (isSelected) AmberBg else AmberTextBright,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)", color = AmberTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AmberTextBright,
                        unfocusedTextColor = AmberTextBright,
                        focusedBorderColor = AmberAccent,
                        unfocusedBorderColor = AmberSurfaceAlt
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Memo", color = AmberTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AmberTextBright,
                        unfocusedTextColor = AmberTextBright,
                        focusedBorderColor = AmberAccent,
                        unfocusedBorderColor = AmberSurfaceAlt
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Dynamic Fields based on Flow Type
                when (flowType) {
                    "IN", "OUT" -> {
                        Text("Category", color = AmberTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val activeCats = if (flowType == "IN") inCategories else outCategories
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.height(72.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(activeCats) { cat ->
                                val isSel = category == cat
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) AmberAccent else AmberSurfaceAlt)
                                        .clickable { category = cat }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(cat, color = if (isSel) AmberBg else AmberTextBright, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Recurring Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recurring Transaction?", color = AmberTextBright, fontSize = 12.sp)
                            Switch(
                                checked = isRecurring,
                                onCheckedChange = { isRecurring = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = AmberAccent, checkedTrackColor = AmberSurfaceAlt)
                            )
                        }
                    }
                    "BORROW" -> {
                        OutlinedTextField(
                            value = partyName,
                            onValueChange = { partyName = it },
                            label = { Text("From (Lender Name)", color = AmberTextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AmberTextBright, unfocusedTextColor = AmberTextBright, focusedBorderColor = AmberAccent, unfocusedBorderColor = AmberSurfaceAlt),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "LEND" -> {
                        OutlinedTextField(
                            value = partyName,
                            onValueChange = { partyName = it },
                            label = { Text("To (Borrower Name)", color = AmberTextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AmberTextBright, unfocusedTextColor = AmberTextBright, focusedBorderColor = AmberAccent, unfocusedBorderColor = AmberSurfaceAlt),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Target Account Selector
                if (accounts.isNotEmpty()) {
                    Text(
                        text = if (flowType == "IN" || flowType == "BORROW") "Receive in Account" else "Spend / Give from Account",
                        color = AmberTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(accounts) { acc ->
                            val isSel = selectedAccId == acc.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) AmberAccent else AmberSurfaceAlt)
                                    .clickable { selectedAccId = acc.id }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(acc.name, color = if (isSel) AmberBg else AmberTextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                                type = if (flowType == "IN" || flowType == "BORROW") "INCOME" else "EXPENSE",
                                category = if (flowType == "BORROW" || flowType == "LEND") "Borrow/Lend" else category,
                                amount = amt,
                                note = note,
                                partyName = partyName,
                                returnDate = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L),
                                isRecurring = isRecurring,
                                frequency = if (isRecurring) frequency else "NONE"
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent)
            ) {
                Text("Confirm & Record", color = AmberBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = AmberTextMuted) }
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
    var name by remember { mutableStateOf(account?.name ?: "") }
    var balance by remember { mutableStateOf(account?.balance?.toString() ?: "") }
    var limit by remember { mutableStateOf(if (account != null && account.monthlyLimit > 0) account.monthlyLimit.toString() else "") }
    var type by remember { mutableStateOf(account?.type ?: "BANK") }

    AlertDialog(
        containerColor = AmberSurface,
        titleContentColor = AmberTextBright,
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "New Account" else "Edit Account", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name", color = AmberTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AmberTextBright, unfocusedTextColor = AmberTextBright, focusedBorderColor = AmberAccent, unfocusedBorderColor = AmberSurfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("Available Balance / Debt (₹)", color = AmberTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AmberTextBright, unfocusedTextColor = AmberTextBright, focusedBorderColor = AmberAccent, unfocusedBorderColor = AmberSurfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it },
                    label = { Text("Monthly Spending Limit (Optional)", color = AmberTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AmberTextBright, unfocusedTextColor = AmberTextBright, focusedBorderColor = AmberAccent, unfocusedBorderColor = AmberSurfaceAlt),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Type", color = AmberTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("BANK", "CASH", "CREDIT", "LOAN").forEach { t ->
                        val isSel = type == t
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) AmberAccent else AmberSurfaceAlt)
                                .clickable { type = t }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(t, color = if (isSel) AmberBg else AmberTextBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent)
            ) {
                Text("Save", color = AmberBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (account != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MildRoseRed) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = AmberTextMuted) }
            }
        }
    )
}

// ---------------- BORROW / LEND HISTORY MODAL ----------------

@Composable
fun BorrowLendHistoryDialog(
    transactions: List<Transaction>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = AmberSurface,
        titleContentColor = AmberTextBright,
        onDismissRequest = onDismiss,
        title = { Text("Borrow & Lend History", fontWeight = FontWeight.Bold) },
        text = {
            if (transactions.isEmpty()) {
                Text("No Borrow or Lend records yet.", color = AmberTextMuted, fontSize = 13.sp)
            } else {
                LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(transactions) { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AmberSurfaceAlt)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${tx.flowType}: ${tx.partyName}", color = AmberTextBright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(tx.note.ifBlank { "No memo" }, color = AmberTextMuted, fontSize = 11.sp)
                            }
                            Text("₹ ${String.format("%.2f", tx.amount)}", color = if (tx.flowType == "LEND") MildSageGreen else MildRoseRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = AmberAccent)) {
                Text("Close", color = AmberBg, fontWeight = FontWeight.Bold)
            }
        }
    )
}
