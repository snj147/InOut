package com.personal.inout.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val accounts by db.vaultDao().getAllAccounts().collectAsState(initial = emptyList())
    val stagedSms by db.vaultDao().getStagedSms().collectAsState(initial = emptyList())
    val transactions by db.vaultDao().getRecentTransactions().collectAsState(initial = emptyList())

    var showTxDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

    var activeReceipt by remember { mutableStateOf<ParsedReceipt?>(null) }
    var isProcessingImage by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingImage = true
            scope.launch {
                val parsed = ReceiptScanner.processReceipt(context, uri)
                isProcessingImage = false
                activeReceipt = parsed
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("InOut Vault", fontWeight = FontWeight.Bold)
                        Text("AES-256 Encrypted Ledger", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Quick SMS simulation for instant testing
                        scope.launch {
                            db.vaultDao().insertSmsDraft(
                                SmsDraft(
                                    rawSender = "HDFC-BANK",
                                    rawBody = "Sent Rs. 450.00 from HDFC Bank to SWIGGY on 13-09-2026 via UPI Ref 628192.",
                                    amount = 450.0,
                                    merchant = "SWIGGY"
                                )
                            )
                        }
                    }) {
                        Icon(Icons.Default.MailOutline, contentDescription = "Simulate SMS", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showTxDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Record In/Out", tint = Color.White)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Total Liquidity Banner
            item {
                val totalLiquid = accounts.filter { it.type != "CREDIT" }.sumOf { it.balance }
                val totalDebt = accounts.filter { it.type == "CREDIT" }.sumOf { it.balance }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("NET LIQUID POSITION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            text = "₹ ${String.format("%,.2f", totalLiquid)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (totalDebt > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text("Credit Card Liability: ₹ ${String.format("%,.2f", totalDebt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // 2. Multi-Account Cards Carousel
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Accounts & Cards", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { showAccountDialog = true }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Account")
                    }
                }

                if (accounts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showAccountDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+ Tap here to add your first Bank/Cash account", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(accounts) { acc ->
                            AccountCard(account = acc)
                        }
                    }
                }
            }

            // 3. Quick Receipt Scan Button
            item {
                Button(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isProcessingImage
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isProcessingImage) "Scanning Receipt via ML Kit..." else "📷 Scan Bill / Receipt (OCR)")
                }
            }

            // 4. Staged SMS Review Inbox
            if (stagedSms.isNotEmpty()) {
                item {
                    Text(
                        "SMS Review Inbox (${stagedSms.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(stagedSms) { draft ->
                    SmsCard(
                        draft = draft,
                        onApprove = {
                            scope.launch {
                                val acc = accounts.firstOrNull() ?: Account(name = "Primary Bank", balance = 50000.0, type = "BANK").also {
                                    db.vaultDao().insertAccount(it)
                                }
                                db.vaultDao().insertTransaction(
                                    Transaction(
                                        accountId = acc.id,
                                        type = "EXPENSE",
                                        category = "Online / Merchant",
                                        amount = draft.amount,
                                        note = draft.merchant
                                    )
                                )
                                db.vaultDao().updateAccount(acc.copy(balance = acc.balance - draft.amount))
                                db.vaultDao().deleteSmsDraft(draft)
                            }
                        },
                        onDiscard = {
                            scope.launch { db.vaultDao().deleteSmsDraft(draft) }
                        }
                    )
                }
            }

            // 5. Category Breakdown / Spending Summary
            val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            if (totalExpense > 0) {
                item {
                    Text("Spending Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val categories = transactions.filter { it.type == "EXPENSE" }.groupBy { it.category }
                            categories.forEach { (cat, txList) ->
                                val catSum = txList.sumOf { it.amount }
                                val pct = (catSum / totalExpense).toFloat()
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(cat, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text("₹ ${String.format("%.2f", catSum)} (${(pct * 100).toInt()}%)", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = pct,
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Recent Ledger
            item {
                Text("Recent Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (transactions.isEmpty()) {
                item {
                    Text("No transactions logged yet.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(transactions) { tx ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(tx.note.ifBlank { tx.category }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(tx.category, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            val isIncome = tx.type == "INCOME"
                            Text(
                                text = "${if (isIncome) "+ " else "- "}₹ ${String.format("%.2f", tx.amount)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isIncome) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }

        // Dialog: Create New Transaction
        if (showTxDialog) {
            TransactionDialog(
                accounts = accounts,
                onDismiss = { showTxDialog = false },
                onSave = { accId, type, category, amount, note ->
                    scope.launch {
                        val acc = accounts.firstOrNull { it.id == accId }
                            ?: accounts.firstOrNull()
                            ?: Account(name = "Cash", balance = 0.0, type = "CASH").also {
                                val newId = db.vaultDao().insertAccount(it)
                                db.vaultDao().insertTransaction(Transaction(accountId = newId, type = type, category = category, amount = amount, note = note))
                                return@launch
                            }

                        db.vaultDao().insertTransaction(
                            Transaction(
                                accountId = acc.id,
                                type = type,
                                category = category,
                                amount = amount,
                                note = note
                            )
                        )
                        val delta = if (type == "INCOME") amount else -amount
                        db.vaultDao().updateAccount(acc.copy(balance = acc.balance + delta))
                        showTxDialog = false
                    }
                }
            )
        }

        // Dialog: Add New Account
        if (showAccountDialog) {
            AddAccountDialog(
                onDismiss = { showAccountDialog = false },
                onSave = { name, balance, type ->
                    scope.launch {
                        db.vaultDao().insertAccount(Account(name = name, balance = balance, type = type))
                        showAccountDialog = false
                    }
                }
            )
        }

        // Dialog: OCR Receipt Splitter
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
                                        note = "$merchant (${split.assignedTo})"
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
fun AccountCard(account: Account) {
    Card(
        modifier = Modifier.width(170.dp).height(100.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(account.name, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 14.sp)
                Text(account.type, fontSize = 10.sp, color = Color.Gray)
            }
            Text("₹ ${String.format("%,.2f", account.balance)}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
    var category by remember { mutableStateOf("Food & Drinks") }
    var type by remember { mutableStateOf("EXPENSE") }
    var selectedAccId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }

    val categories = listOf("Food & Drinks", "Shopping", "Transport", "Bills", "Salary", "Investment")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "EXPENSE",
                        onClick = { type = "EXPENSE" },
                        label = { Text("Out (Expense)") }
                    )
                    FilterChip(
                        selected = type == "INCOME",
                        onClick = { type = "INCOME" },
                        label = { Text("In (Income)") }
                    )
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Merchant") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (amt > 0.0) onSave(selectedAccId, type, category, amt, note)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddAccountDialog(onDismiss: () -> Unit, onSave: (String, Double, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("BANK") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Account Name (e.g. HDFC, Cash)") })
                OutlinedTextField(value = balance, onValueChange = { balance = it }, label = { Text("Starting Balance (₹)") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("BANK", "CASH", "CREDIT").forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val bal = balance.toDoubleOrNull() ?: 0.0
                if (name.isNotBlank()) onSave(name, bal, type)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SmsCard(draft: SmsDraft, onApprove: () -> Unit, onDiscard: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(draft.merchant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Sender: ${draft.rawSender}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Text("₹ ${String.format("%.2f", draft.amount)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(draft.rawBody, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDiscard) {
                    Icon(Icons.Default.Delete, contentDescription = "Discard", tint = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onApprove) {
                    Icon(Icons.Default.Check, contentDescription = "Approve")
                    Spacer(Modifier.width(4.dp))
                    Text("Approve & Log")
                }
            }
        }
    }
}
