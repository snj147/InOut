 package com.personal.inout.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    var showAddDialog by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var categoryText by remember { mutableStateOf("") }

    // OCR Scanning State
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
        topBar = { TopAppBar(title = { Text("InOut") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Quick Add")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Balance Card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("TOTAL LIQUID FUNDS", style = MaterialTheme.typography.labelSmall)
                        val total = accounts.sumOf { it.balance }
                        Text("₹ ${String.format("%.2f", total)}", style = MaterialTheme.typography.headlineLarge)
                    }
                }
            }

            // Quick Scan Action Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessingImage
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isProcessingImage) "Scanning..." else "📷 Scan Memo")
                    }
                }
            }

            // Staged SMS Review Inbox
            if (stagedSms.isNotEmpty()) {
                item {
                    Text(
                        "SMS Review Inbox (${stagedSms.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(stagedSms) { draft ->
                    SmsCard(
                        draft = draft,
                        onApprove = {
                            scope.launch {
                                val acc = accounts.firstOrNull() ?: Account(name = "Main Cash", balance = 0.0, type = "CASH").also {
                                    db.vaultDao().insertAccount(it)
                                }
                                db.vaultDao().insertTransaction(
                                    Transaction(
                                        accountId = acc.id,
                                        type = "EXPENSE",
                                        category = "SMS Parsed",
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

            // Recent Transactions List
            item {
                Text("Recent Ledger", style = MaterialTheme.typography.titleMedium)
            }
            if (transactions.isEmpty()) {
                item {
                    Text("No transactions logged yet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(transactions) { tx ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(tx.note.ifBlank { tx.category }, style = MaterialTheme.typography.bodyLarge)
                            Text(tx.category, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("- ₹ ${tx.amount}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // Receipt Allocation Modal (Opens automatically once photo is scanned)
        activeReceipt?.let { receipt ->
            ScanReceiptDialog(
                parsedData = receipt,
                onDismiss = { activeReceipt = null },
                onConfirm = { total, merchant, splits, _ ->
                    scope.launch {
                        val acc = accounts.firstOrNull() ?: Account(name = "Main Cash", balance = 0.0, type = "CASH").also {
                            db.vaultDao().insertAccount(it)
                        }

                        // Write splits or single expense to ledger
                        splits.forEach { split ->
                            val splitAmount = split.amountText.toDoubleOrNull() ?: 0.0
                            if (splitAmount > 0.0) {
                                db.vaultDao().insertTransaction(
                                    Transaction(
                                        accountId = acc.id,
                                        type = "EXPENSE",
                                        category = split.category.ifBlank { "Scanned" },
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

        // Manual Quick Entry Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Quick Out") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount (₹)") }
                        )
                        OutlinedTextField(
                            value = categoryText,
                            onValueChange = { categoryText = it },
                            label = { Text("Category / Note") }
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        scope.launch {
                            val acc = accounts.firstOrNull() ?: Account(name = "Main Cash", balance = 0.0, type = "CASH").also {
                                db.vaultDao().insertAccount(it)
                            }
                            db.vaultDao().insertTransaction(
                                Transaction(
                                    accountId = acc.id,
                                    type = "EXPENSE",
                                    category = categoryText.ifBlank { "General" },
                                    amount = amt,
                                    note = categoryText
                                )
                            )
                            db.vaultDao().updateAccount(acc.copy(balance = acc.balance - amt))
                            showAddDialog = false
                            amountText = ""
                            categoryText = ""
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
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
                    Text(draft.merchant, style = MaterialTheme.typography.titleMedium)
                    Text("From: ${draft.rawSender}", style = MaterialTheme.typography.bodySmall)
                }
                Text("₹ ${draft.amount}", style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(8.dp))
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
                    Text("Approve")
                }
            }
        }
    }
}

                                        Transaction(
                                            accountId = acc.id,
                                            type = "EXPENSE",
                                            category = "SMS Parsed",
                                            amount = draft.amount,
                                            note = draft.merchant
                                        )
                                    )
                                    db.vaultDao().updateAccount(acc.copy(balance = acc.balance - draft.amount))
                                }
                                db.vaultDao().deleteSmsDraft(draft)
                            }
                        },
                        onDiscard = {
                            scope.launch { db.vaultDao().deleteSmsDraft(draft) }
                        }
                    )
                }
            }

            // Recent Transactions Ledger
            item {
                Text("Recent Ledger", style = MaterialTheme.typography.titleMedium)
            }
            if (transactions.isEmpty()) {
                item {
                    Text("No transactions logged yet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(transactions) { tx ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(tx.note.ifBlank { tx.category }, style = MaterialTheme.typography.bodyLarge)
                            Text(tx.category, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("- ₹ ${tx.amount}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // Quick Entry Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Quick Out") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount (₹)") }
                        )
                        OutlinedTextField(
                            value = categoryText,
                            onValueChange = { categoryText = it },
                            label = { Text("Category / Note") }
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        scope.launch {
                            var acc = accounts.firstOrNull()
                            if (acc == null) {
                                acc = Account(name = "Main Cash", balance = 10000.0, type = "CASH")
                                db.vaultDao().insertAccount(acc)
                            }
                            db.vaultDao().insertTransaction(
                                Transaction(
                                    accountId = acc.id,
                                    type = "EXPENSE",
                                    category = categoryText.ifBlank { "General" },
                                    amount = amt,
                                    note = categoryText
                                )
                            )
                            db.vaultDao().updateAccount(acc.copy(balance = acc.balance - amt))
                            showAddDialog = false
                            amountText = ""
                            categoryText = ""
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
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
                    Text(draft.merchant, style = MaterialTheme.typography.titleMedium)
                    Text("From: ${draft.rawSender}", style = MaterialTheme.typography.bodySmall)
                }
                Text("₹ ${draft.amount}", style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(8.dp))
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
                    Text("Approve")
                }
            }
        }
    }
}
