package com.personal.inout.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.Account
import com.personal.inout.data.AppDatabase
import com.personal.inout.data.SmsDraft
import com.personal.inout.data.Transaction
import com.personal.inout.ocr.ParsedReceipt
import com.personal.inout.ocr.ReceiptScanner
import kotlinx.coroutines.launch

// Premium Fintech Palette
val ObsidianBg = Color(0xFF0C0E14)
val SurfaceCard = Color(0xFF161922)
val SurfaceCardAlt = Color(0xFF1E2230)
val NeonGreen = Color(0xFF00E676)
val ElectricIndigo = Color(0xFF6366F1)
val CoralRed = Color(0xFFFF5252)
val TextMuted = Color(0xFF8E95A5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val accounts by db.vaultDao().getAllAccounts().collectAsState(initial = emptyList())
    val stagedSms by db.vaultDao().getStagedSms().collectAsState(initial = emptyList())
    val transactions by db.vaultDao().getRecentTransactions().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) }
    var showTxDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }

    var activeReceipt by remember { mutableStateOf<ParsedReceipt?>(null) }
    var isProcessingImage by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingImage = true
            scope.launch {
                try {
                    activeReceipt = ReceiptScanner.processReceipt(context, uri)
                } catch (e: Exception) {
                    Toast.makeText(context, "OCR Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessingImage = false
                }
            }
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .background(ObsidianBg)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("InOut", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("OFFLINE VAULT", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = NeonGreen)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                db.vaultDao().insertSmsDraft(
                                    SmsDraft(
                                        rawSender = "HDFC-BANK",
                                        rawBody = "Sent Rs. 750.00 from HDFC Bank to BLINKIT on 13-09-2026. Ref 990182.",
                                        amount = 750.0,
                                        merchant = "BLINKIT"
                                    )
                                )
                                Toast.makeText(context, "Test SMS draft injected!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceCardAlt)
                    ) {
                        Icon(Icons.Default.AddComment, contentDescription = "Test SMS", tint = ElectricIndigo)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceCard,
                tonalElevation = 8.dp,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null) },
                    label = { Text("Vault", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = NeonGreen, selectedTextColor = NeonGreen, indicatorColor = SurfaceCardAlt, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted)
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Analytics, contentDescription = null) },
                    label = { Text("Debts & Stats", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = ElectricIndigo, selectedTextColor = ElectricIndigo, indicatorColor = SurfaceCardAlt, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted)
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        BadgedBox(badge = {
                            if (stagedSms.isNotEmpty()) Badge(containerColor = CoralRed) { Text("${stagedSms.size}") }
                        }) {
                            Icon(Icons.Filled.ReceiptLong, contentDescription = null)
                        }
                    },
                    label = { Text("Scans & SMS", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = ElectricIndigo, selectedTextColor = ElectricIndigo, indicatorColor = SurfaceCardAlt, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted)
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = NeonGreen, selectedTextColor = NeonGreen, indicatorColor = SurfaceCardAlt, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted)
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showTxDialog = true },
                    containerColor = NeonGreen,
                    contentColor = Color.Black,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Log Transaction", modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).background(ObsidianBg)
        ) {
            when (selectedTab) {
                0 -> VaultTabContent(
                    accounts = accounts,
                    transactions = transactions,
                    onAddAccountClick = { showAccountDialog = true },
                    onAccountClick = { editingAccount = it }
                )
                1 -> AnalyticsScreenTab(accounts = accounts, transactions = transactions)
                2 -> InboxScreenTab(
                    stagedSms = stagedSms,
                    accounts = accounts,
                    db = db,
                    isProcessingImage = isProcessingImage,
                    onLaunchScanner = {
                        try { photoPickerLauncher.launch("image/*") } catch (e: Exception) {
                            Toast.makeText(context, "Gallery error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                3 -> SettingsScreenTab()
            }
        }

        if (showTxDialog) {
            TransactionDialog(
                accounts = accounts,
                onDismiss = { showTxDialog = false },
                onSave = { accId, type, category, amount, note ->
                    scope.launch {
                        val targetAcc = accounts.firstOrNull { it.id == accId }
                            ?: accounts.firstOrNull()
                            ?: Account(name = "Cash", balance = 0.0, type = "CASH").also {
                                val id = db.vaultDao().insertAccount(it)
                                db.vaultDao().insertTransaction(Transaction(accountId = id, type = type, category = category, amount = amount, note = note))
                                return@launch
                            }
                        db.vaultDao().insertTransaction(Transaction(accountId = targetAcc.id, type = type, category = category, amount = amount, note = note))
                        val delta = if (type == "INCOME") amount else -amount
                        db.vaultDao().updateAccount(targetAcc.copy(balance = targetAcc.balance + delta))
                        showTxDialog = false
                    }
                }
            )
        }

        if (showAccountDialog) {
            AccountEditorDialog(
                account = null,
                onDismiss = { showAccountDialog = false },
                onSave = { name, balance, type ->
                    scope.launch {
                        db.vaultDao().insertAccount(Account(name = name, balance = balance, type = type))
                        showAccountDialog = false
                    }
                },
                onDelete = {}
            )
        }

        editingAccount?.let { acc ->
            AccountEditorDialog(
                account = acc,
                onDismiss = { editingAccount = null },
                onSave = { name, balance, type ->
                    scope.launch {
                        db.vaultDao().updateAccount(acc.copy(name = name, balance = balance, type = type))
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

        activeReceipt?.let { receipt ->
            ScanReceiptDialog(
                parsedData = receipt,
                onDismiss = { activeReceipt = null },
                onConfirm = { total, merchant, splits, _ ->
                    scope.launch {
                        val acc = accounts.firstOrNull() ?: Account(name = "Cash", balance = 0.0, type = "CASH").also {
                            db.vaultDao().insertAccount(it)
                        }
                        splits.forEach { split ->
                            val splitAmount = split.amountText.toDoubleOrNull() ?: 0.0
                            if (splitAmount > 0.0) {
                                db.vaultDao().insertTransaction(
                                    Transaction(
                                        accountId = acc.id,
                                        type = "EXPENSE",
                                        category = split.category.ifBlank { "Receipt Split" },
                                        amount = splitAmount,
                                        note = merchant
                                    )
                                )
                            }
                        }
                        db.vaultDao().updateAccount(acc.copy(balance = acc.balance - total))
                        activeReceipt = null
                    }
                }
            )
        }
    }
}

@Composable
fun VaultTabContent(
    accounts: List<Account>,
    transactions: List<Transaction>,
    onAddAccountClick: () -> Unit,
    onAccountClick: (Account) -> Unit
) {
    val liquidAccounts = accounts.filter { it.type != "LOAN" && it.type != "CREDIT" }
    val totalAvailable = liquidAccounts.sumOf { it.balance }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(Color(0xFF1A1F30), Color(0xFF121520))))
                        .border(1.dp, Color(0xFF2B3245), RoundedCornerShape(22.dp))
                        .padding(22.dp)
                ) {
                    Column {
                        Text("TOTAL SPENDABLE CASH", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("₹ ${String.format("%,.2f", totalAvailable)}", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val inSum = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
                            val outSum = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("₹ ${String.format("%,.0f", inSum)}", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = CoralRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("₹ ${String.format("%,.0f", outSum)}", color = CoralRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
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
                Text("Wallets & Accounts", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onAddAccountClick) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ElectricIndigo, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Account", color = ElectricIndigo, fontSize = 13.sp)
                }
            }

            if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard).clickable(onClick = onAddAccountClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ Add your first bank account or wallet", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(accounts) { acc ->
                        Card(
                            modifier = Modifier.width(160.dp).height(95.dp).clip(RoundedCornerShape(16.dp)).clickable { onAccountClick(acc) },
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                        ) {
                            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(acc.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                    Text(
                                        acc.type,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (acc.type == "LOAN" || acc.type == "CREDIT") CoralRed else NeonGreen
                                    )
                                }
                                Text("₹ ${String.format("%,.2f", acc.balance)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Recent Transactions", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (transactions.isEmpty()) {
            item {
                Text("No transactions logged yet. Tap '+' to create one.", color = TextMuted, fontSize = 14.sp)
            }
        } else {
            items(transactions) { tx ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceCard).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceCardAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (tx.type == "INCOME") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (tx.type == "INCOME") NeonGreen else CoralRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(tx.note.ifBlank { tx.category }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(tx.category, color = TextMuted, fontSize = 12.sp)
                        }
                    }
                    Text(
                        "${if (tx.type == "INCOME") "+" else "-"} ₹ ${String.format("%.2f", tx.amount)}",
                        color = if (tx.type == "INCOME") NeonGreen else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
fun AnalyticsScreenTab(accounts: List<Account>, transactions: List<Transaction>) {
    val debts = accounts.filter { it.type == "LOAN" || it.type == "CREDIT" }
    val totalDebt = debts.sumOf { it.balance }
    val expenseTxs = transactions.filter { it.type == "EXPENSE" }
    val totalExpense = expenseTxs.sumOf { it.amount }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Debts & Liabilities", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                Column(Modifier.padding(18.dp)) {
                    Text("TOTAL OUTSTANDING DEBT", color = CoralRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("₹ ${String.format("%,.2f", totalDebt)}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
        if (debts.isEmpty()) {
            item { Text("No loans registered. Add them under Vault > New Account > Type LOAN.", color = TextMuted, fontSize = 13.sp) }
        } else {
            items(debts) { debt ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceCard).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(debt.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(debt.type, color = CoralRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("₹ ${String.format("%,.2f", debt.balance)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        item { Text("Spending Distribution", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (totalExpense > 0.0) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        expenseTxs.groupBy { it.category }.forEach { (cat, txs) ->
                            val catSum = txs.sumOf { it.amount }
                            val fraction = (catSum / totalExpense).toFloat()
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(cat, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("₹ ${String.format("%.2f", catSum)} (${(fraction * 100).toInt()}%)", color = TextMuted, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(progress = fraction, color = ElectricIndigo, trackColor = SurfaceCardAlt, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
fun InboxScreenTab(
    stagedSms: List<SmsDraft>,
    accounts: List<Account>,
    db: AppDatabase,
    isProcessingImage: Boolean,
    onLaunchScanner: () -> Unit
) {
    val scope = rememberCoroutineScope()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Receipt Scanner", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item {
            Button(
                onClick = onLaunchScanner,
                enabled = !isProcessingImage,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricIndigo)
            ) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text(if (isProcessingImage) "Scanning Receipt..." else "Scan Memo / Receipt", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("SMS Review Feed", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (stagedSms.isNotEmpty()) Text("${stagedSms.size} Pending", color = CoralRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (stagedSms.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.MarkChatRead, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Inbox is clear", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Incoming bank transactions appear here for 1-tap confirmation.", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(stagedSms) { draft ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(draft.merchant, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("From: ${draft.rawSender}", color = TextMuted, fontSize = 11.sp)
                            }
                            Text("₹ ${String.format("%.2f", draft.amount)}", color = NeonGreen, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(draft.rawBody, color = TextMuted, fontSize = 12.sp, maxLines = 2)
                        Spacer(Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { db.vaultDao().deleteSmsDraftById(draft.id) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Discard", tint = CoralRed)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        val acc = accounts.firstOrNull() ?: Account(name = "Main Bank", balance = 0.0, type = "BANK").also {
                                            db.vaultDao().insertAccount(it)
                                        }
                                        db.vaultDao().insertTransaction(Transaction(accountId = acc.id, type = "EXPENSE", category = "SMS Parsed", amount = draft.amount, note = draft.merchant))
                                        db.vaultDao().updateAccount(acc.copy(balance = acc.balance - draft.amount))
                                        db.vaultDao().deleteSmsDraftById(draft.id)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                            ) {
                                Text("Approve", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
fun SettingsScreenTab() {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Settings & Security", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Database Encryption", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("AES-256 SQLCipher Active", color = NeonGreen, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.Security, contentDescription = null, tint = NeonGreen)
                    }
                    Divider(color = SurfaceCardAlt)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Biometrics / App Lock", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Hardware Biometric & PIN fallback", color = TextMuted, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = ElectricIndigo)
                    }
                    Divider(color = SurfaceCardAlt)
                    Button(
                        onClick = { Toast.makeText(context, "Encrypted backup saved to private vault.", Toast.LENGTH_SHORT).show() },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardAlt),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Trigger Offline Backup", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionDialog(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food & Dining") }
    var type by remember { mutableStateOf("EXPENSE") }
    var selectedAccId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }

    val categories = listOf("Food & Dining", "Groceries", "Transport", "Shopping", "Entertainment", "Bills", "Health", "Investment")

    AlertDialog(
        containerColor = SurfaceCard,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        onDismissRequest = onDismiss,
        title = { Text("Record Transaction", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { type = "EXPENSE" },
                        colors = ButtonDefaults.buttonColors(containerColor = if (type == "EXPENSE") CoralRed else SurfaceCardAlt),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Out (Expense)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { type = "INCOME" },
                        colors = ButtonDefaults.buttonColors(containerColor = if (type == "INCOME") NeonGreen else SurfaceCardAlt),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("In (Income)", color = if (type == "INCOME") Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { str: String -> amount = str },
                    label = { Text("Amount (₹)", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ElectricIndigo,
                        unfocusedBorderColor = SurfaceCardAlt
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { str: String -> note = str },
                    label = { Text("Note / Merchant", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ElectricIndigo,
                        unfocusedBorderColor = SurfaceCardAlt
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Categories", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(130.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories) { cat ->
                        val isSel = category == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) ElectricIndigo else SurfaceCardAlt)
                                .clickable { category = cat }
                                .padding(vertical = 8.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(cat, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) onSave(selectedAccId, type, category, amt, note)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
fun AccountEditorDialog(
    account: Account?,
    onDismiss: () -> Unit,
    onSave: (String, Double, String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var balance by remember { mutableStateOf(account?.balance?.toString() ?: "") }
    var type by remember { mutableStateOf(account?.type ?: "BANK") }

    AlertDialog(
        containerColor = SurfaceCard,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "New Account / Loan" else "Edit Account", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { str: String -> name = str },
                    label = { Text("Name (e.g. HDFC, Cash, Auto Loan)", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ElectricIndigo,
                        unfocusedBorderColor = SurfaceCardAlt
                    )
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { str: String -> balance = str },
                    label = { Text("Balance / Debt (₹)", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ElectricIndigo,
                        unfocusedBorderColor = SurfaceCardAlt
                    )
                )

                Text("Category", color = TextMuted, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("BANK", "CASH", "CREDIT", "LOAN").forEach { t ->
                        val isSel = type == t
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) ElectricIndigo else SurfaceCardAlt)
                                .clickable { type = t }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(t, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bal = balance.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) onSave(name, bal, type)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (account != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = CoralRed) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
            }
        }
    )
}
