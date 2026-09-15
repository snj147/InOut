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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import kotlin.math.cos
import kotlin.math.sin

enum class CockpitStyle(val label: String) {
    BATTERY_EQUALIZER("Battery & Equalizer"),
    ECLIPSE_SPOTLIGHT("Eclipse & Spotlight"),
    VAULT_ORBIT("Vault & Orbit")
}

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

    var activeCockpitStyle by remember {
        val savedCockpit = prefs.getString("cockpit_style", CockpitStyle.BATTERY_EQUALIZER.name)
        mutableStateOf(CockpitStyle.valueOf(savedCockpit ?: CockpitStyle.BATTERY_EQUALIZER.name))
    }

    val theme = when (activeThemeMode) {
        AppThemeMode.AMBER_OCHRE -> AmberTheme
        AppThemeMode.OLIVE_MATCHA -> OliveMatchaTheme
        AppThemeMode.SAND_DUNE -> SandDuneTheme
    }

    val accounts by db.vaultDao().getAllAccounts().collectAsState(initial = emptyList())
    val transactions by db.vaultDao().getAllTransactions().collectAsState(initial = emptyList())
    val stagedSms by db.vaultDao().getStagedSms().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) }

    var showMainTxDialog by remember { mutableStateOf(false) }
    var prefilledTx by remember { mutableStateOf<Transaction?>(null) }
    var showCreateCardDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var quickActionAccount by remember { mutableStateOf<Pair<Account, String>?>(null) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var showAllLedgerSheet by remember { mutableStateOf(false) }
    var showAllRecurringSheet by remember { mutableStateOf(false) }

    // Long press action flow
    var itemPendingAction by remember { mutableStateOf<Pair<String, Any>?>(null) }
    var itemPendingPinDelete by remember { mutableStateOf<Pair<String, Any>?>(null) }

    val savedPin = prefs.getString("user_pin", "1234") ?: "1234"

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

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraSnapLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission needed to scan receipts", Toast.LENGTH_SHORT).show()
        }
    }

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

    val currentCal = Calendar.getInstance()
    val thisMonthTxs = transactions.filter {
        val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
        c.get(Calendar.MONTH) == currentCal.get(Calendar.MONTH) && c.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR)
    }
    val totalIn = thisMonthTxs.filter { it.flowType == "IN" }.sumOf { it.amount }
    val totalOut = thisMonthTxs.filter { it.flowType == "OUT" }.sumOf { it.amount }

    CompositionLocalProvider(LocalThemeColors provides theme) {
        Scaffold(
            containerColor = theme.bg,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .background(theme.bg)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("InOut", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = theme.textBright)
                            Text("PERSONAL LEDGER", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = theme.accent)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = theme.mildGreen, modifier = Modifier.size(13.dp))
                                Text("₹${String.format("%,.0f", totalIn)}", color = theme.mildGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = theme.mildRed, modifier = Modifier.size(13.dp))
                                Text("₹${String.format("%,.0f", totalOut)}", color = theme.mildRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
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
                        accounts = accounts,
                        cockpitStyle = activeCockpitStyle,
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
                        currentCockpit = activeCockpitStyle,
                        onSelectTheme = { mode ->
                            activeThemeMode = mode
                            prefs.edit().putString("selected_theme", mode.name).apply()
                        },
                        onSelectCockpit = { style ->
                            activeCockpitStyle = style
                            prefs.edit().putString("cockpit_style", style.name).apply()
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

            if (showCreateCardDialog) {
                CreateAccountCardDialog(
                    availableSourceAccounts = accounts.filter { it.type in listOf("CASH", "BANK") },
                    onDismiss = { showCreateCardDialog = false },
                    onSave = { acc, sourceAccId, initialDate, initialNote ->
                        scope.launch {
                            val sourceAcc = accounts.firstOrNull { it.id == sourceAccId }
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

            quickActionAccount?.let { (acc, action) ->
                QuickActionDialog(
                    account = acc,
                    action = action,
                    availableFundingAccounts = accounts.filter { it.type in listOf("CASH", "BANK") && it.id != acc.id },
                    onDismiss = { quickActionAccount = null },
                    onConfirm = { deltaAmount, fundingAccId, actionDate, noteText ->
                        scope.launch {
                            val fundingAcc = accounts.firstOrNull { it.id == fundingAccId }
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
                        itemPendingPinDelete = Pair("ACCOUNT", acc)
                        editingAccount = null
                    }
                )
            }

            // Long-press bottom action pill
            itemPendingAction?.let { (type, item) ->
                MinimalLongPressSheet(
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
                        itemPendingPinDelete = Pair(type, item)
                        itemPendingAction = null
                    }
                )
            }

            // PIN challenge for deletions
            itemPendingPinDelete?.let { (type, item) ->
                ThemePinPadDialog(
                    title = "Confirm Deletion",
                    subtitle = "Enter your 4-digit PIN to permanently remove this entry.",
                    expectedPin = savedPin,
                    onDismiss = { itemPendingPinDelete = null },
                    onSuccess = {
                        scope.launch {
                            if (type == "ACCOUNT") {
                                db.vaultDao().deleteAccount((item as Account).id)
                            } else {
                                db.vaultDao().deleteTransaction((item as Transaction).id)
                            }
                            itemPendingPinDelete = null
                            Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            if (showAllLedgerSheet) {
                AllTransactionsSearchSheet(
                    transactions = transactions,
                    onDismiss = { showAllLedgerSheet = false },
                    onExportCsv = { CsvExporter.exportAndShareTransactions(context, transactions) }
                )
            }

            if (showAllRecurringSheet) {
                AllRecurringEntriesSheet(
                    recurringTransactions = transactions.filter { it.isRecurring },
                    recurringAccounts = accounts.filter { it.repaymentType == "INSTALLMENTS" },
                    onDismiss = { showAllRecurringSheet = false },
                    onLongPressRecurring = { tx -> itemPendingAction = Pair("RECURRING", tx) },
                    onLongPressAccount = { acc -> itemPendingAction = Pair("ACCOUNT", acc) }
                )
            }
        }
    }
}

// ---------------- TAB 0: LEDGER WITH COLORFUL COCKPIT ----------------

@Composable
fun LedgerTabScreen(
    transactions: List<Transaction>,
    accounts: List<Account>,
    cockpitStyle: CockpitStyle,
    onViewAllClick: () -> Unit
) {
    val theme = LocalThemeColors.current
    val recentTxs = transactions.take(6)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }

        item {
            CockpitInsightCard(
                style = cockpitStyle,
                accounts = accounts,
                transactions = transactions
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Activity", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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

// ---------------- DUAL-PANEL COCKPIT INSIGHT CARD ----------------

@Composable
fun CockpitInsightCard(
    style: CockpitStyle,
    accounts: List<Account>,
    transactions: List<Transaction>
) {
    val theme = LocalThemeColors.current

    val totalLiquid = accounts.filter { it.type in listOf("CASH", "BANK") }.sumOf { it.balance }
    val totalCcBills = accounts.filter { it.type == "CREDIT" }.sumOf { (it.totalLimit - it.balance).coerceAtLeast(0.0) }
    val totalLenderDebts = accounts.filter { it.type == "LENDER" }.sumOf { it.balance }
    val unencumberedCash = (totalLiquid - totalCcBills - totalLenderDebts).coerceAtLeast(0.0)
    val ratio = if (totalLiquid > 0) (unencumberedCash / totalLiquid).toFloat().coerceIn(0f, 1f) else 0f

    val currentCal = Calendar.getInstance()
    val thisMonthTxs = transactions.filter {
        val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
        c.get(Calendar.MONTH) == currentCal.get(Calendar.MONTH) && c.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR)
    }

    val totalSpent = thisMonthTxs.filter { it.flowType == "OUT" }.sumOf { it.amount }
    val totalIncome = thisMonthTxs.filter { it.flowType == "IN" }.sumOf { it.amount }
    val netSaved = (totalIncome - totalSpent).coerceAtLeast(0.0)
    val retentionPct = if (totalIncome > 0) ((netSaved / totalIncome) * 100).toInt() else 0

    val daysInMonth = currentCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val currentDay = currentCal.get(Calendar.DAY_OF_MONTH)
    val remainingDays = (daysInMonth - currentDay + 1).coerceAtLeast(1)
    val dailySafeBurn = (unencumberedCash / remainingDays)

    val topCategoryEntry = thisMonthTxs.filter { it.flowType == "OUT" }
        .groupBy { it.category }
        .maxByOrNull { entry -> entry.value.sumOf { it.amount } }

    val topCategoryName = topCategoryEntry?.key ?: "None"
    val topCategoryAmount = topCategoryEntry?.value?.sumOf { it.amount } ?: 0.0
    val topCategoryPct = if (totalSpent > 0) ((topCategoryAmount / totalSpent) * 100).toInt() else 0

    val last7DaysSpend = remember(transactions) {
        val list = mutableListOf<Double>()
        for (i in 6 downTo 0) {
            val targetCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val daySum = transactions.filter {
                val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                it.flowType == "OUT" &&
                        c.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR) &&
                        c.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)
            }.sumOf { it.amount }
            list.add(daySum)
        }
        list
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(154.dp),
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
                    // Left: Battery Cell
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("POCKET RESERVE", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        BatteryGaugeCanvas(ratio = ratio, themeColor = theme.accent, greenColor = theme.mildGreen)
                        Column {
                            Text("₹ ${String.format("%,.0f", unencumberedCash)} Free", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Dues safely reserved", color = theme.textMuted, fontSize = 9.5.sp)
                        }
                    }

                    Divider(color = theme.surfaceAlt, modifier = Modifier.fillMaxHeight().width(1.dp).padding(vertical = 4.dp))

                    // Right: 7-Day Equalizer Bars
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("WEEKLY RHYTHM", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        EqualizerCanvas(dailySpends = last7DaysSpend, theme = theme)
                        Text("Quiet week • spikes mapped", color = theme.textMuted, fontSize = 9.5.sp)
                    }
                }

                CockpitStyle.ECLIPSE_SPOTLIGHT -> {
                    // Left: Eclipse Speedometer Dial
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("DAILY SPEED LIMIT", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(70.dp)) {
                            EclipseGaugeCanvas(burn = dailySafeBurn, theme = theme)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("₹${String.format("%.0f", dailySafeBurn)}", color = theme.textBright, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                Text("/ day", color = theme.textMuted, fontSize = 9.sp)
                            }
                        }
                        Text("$remainingDays days remaining", color = theme.accent, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Divider(color = theme.surfaceAlt, modifier = Modifier.fillMaxHeight().width(1.dp).padding(vertical = 4.dp))

                    // Right: Category Spotlight Tile
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
                                Text(topCategoryName, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                Text("$topCategoryPct% of all spend", color = theme.mildRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("₹ ${String.format("%,.0f", topCategoryAmount)} spent this month", color = theme.textMuted, fontSize = 9.5.sp)
                    }
                }

                CockpitStyle.VAULT_ORBIT -> {
                    // Left: Vault Stash
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SAVINGS VAULT", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Savings, contentDescription = null, tint = theme.accent, modifier = Modifier.size(32.dp))
                            Column {
                                Text("+₹ ${String.format("%,.0f", netSaved)}", color = theme.mildGreen, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                Text("$retentionPct% retained", color = theme.textMuted, fontSize = 10.sp)
                            }
                        }
                        Text("Surplus accumulating", color = theme.textMuted, fontSize = 9.5.sp)
                    }

                    Divider(color = theme.surfaceAlt, modifier = Modifier.fillMaxHeight().width(1.dp).padding(vertical = 4.dp))

                    // Right: Habit Orbit
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("DISCIPLINE ORBIT", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(65.dp)) {
                            OrbitCanvas(dailySpends = last7DaysSpend, theme = theme)
                        }
                        val zeroSpendDays = last7DaysSpend.count { it == 0.0 }
                        Text("$zeroSpendDays calm days this week", color = theme.mildGreen, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------- CUSTOM CANVAS WIDGETS ----------------

@Composable
fun BatteryGaugeCanvas(ratio: Float, themeColor: Color, greenColor: Color) {
    val animatedRatio by animateFloatAsState(targetValue = ratio, animationSpec = tween(700, easing = FastOutSlowInEasing), label = "battery")

    Canvas(modifier = Modifier.fillMaxWidth(0.9f).height(24.dp)) {
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

    Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
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
fun EclipseGaugeCanvas(burn: Double, theme: ThemeColors) {
    Canvas(modifier = Modifier.size(64.dp)) {
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
            sweepAngle = 170f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun OrbitCanvas(dailySpends: List<Double>, theme: ThemeColors) {
    Canvas(modifier = Modifier.size(60.dp)) {
        val r = size.minDimension / 2.3f
        val center = Offset(size.width / 2, size.height / 2)

        drawCircle(color = theme.surfaceAlt, radius = r, style = Stroke(width = 1.dp.toPx()))

        val count = 7
        for (i in 0 until count) {
            val angle = Math.toRadians((i * (360.0 / count) - 90)).toFloat()
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)

            val isZeroSpend = (dailySpends.getOrNull(i) ?: 0.0) == 0.0
            drawCircle(
                color = if (isZeroSpend) theme.mildGreen else theme.accent,
                radius = if (isZeroSpend) 4.5.dp.toPx() else 3.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

// ---------------- SLIM LEDGER ROW (~48dp) ----------------

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

// ---------------- TAB 1: ACCOUNTS & CAROUSELS (REACTIVE EYE TOGGLE) ----------------

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

        // Carousel 1: Cash, Bank, Credit
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

        // Carousel 2: Borrowers & Lenders
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
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent)
            ) {
                Icon(Icons.Default.MultipleStop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Intra-Account Transfer", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        // Lower Section: Active Recurring Entries (Capped at 3)
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

// ---------------- FIXED CARD WITH REACTIVE EYE TOGGLE ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FixedWalletCard(
    account: Account,
    onLongPress: () -> Unit,
    onQuickAction: (String) -> Unit
) {
    val theme = LocalThemeColors.current
    var isRevealed by remember(account.hasEyeMask) { mutableStateOf(!account.hasEyeMask) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .size(width = 210.dp, height = 145.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
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
    var isRevealed by remember(account.hasEyeMask) { mutableStateOf(!account.hasEyeMask) }
    val scope = rememberCoroutineScope()
    val isLender = account.type == "LENDER"

    Card(
        modifier = Modifier
            .size(width = 210.dp, height = 145.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
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

// ---------------- RECURRING RIBBON ----------------

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
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
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

// ---------------- REVIEW QUEUE & SETTINGS ----------------

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
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = theme.bg, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Take Photo", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onGalleryClick,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
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
                    Text("Review Feed", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onManualSyncSms, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Sync, contentDescription = "Simulate SMS Scan", tint = theme.accent, modifier = Modifier.size(15.dp))
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
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(draft.merchant, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("From: ${draft.rawSender}", color = theme.textMuted, fontSize = 11.sp)
                            }
                            Text("₹ ${String.format("%.2f", draft.amount)}", color = theme.accent, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(Modifier.padding(top = 10.dp)) {
                                Text(draft.rawBody, color = theme.textMuted, fontSize = 11.sp, lineHeight = 15.sp)
                                Spacer(Modifier.height(12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = { onDiscard(draft.id) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.mildRed)
                                    ) {
                                        Text("Discard", fontSize = 11.sp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Button(
                                        onClick = { onConsider(draft) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.mildGreen)
                                    ) {
                                        Text("Consider", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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

@Composable
fun SettingsTabScreen(
    currentTheme: AppThemeMode,
    currentCockpit: CockpitStyle,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectCockpit: (CockpitStyle) -> Unit,
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
        item { Text("App Preferences", color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Palette Theme", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("Amber", AppThemeMode.AMBER_OCHRE, AmberTheme.accent),
                            Triple("Olive", AppThemeMode.OLIVE_MATCHA, OliveMatchaTheme.accent),
                            Triple("Sand", AppThemeMode.SAND_DUNE, SandDuneTheme.accent)
                        ).forEach { (label, mode, col) ->
                            val isSel = currentTheme == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) col.copy(alpha = 0.25f) else theme.surfaceAlt)
                                    .clickable { onSelectTheme(mode) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (isSel) col else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Divider(color = theme.surfaceAlt)

                    Text("Dashboard Cockpit Widget", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CockpitStyle.values().forEach { style ->
                            val isSel = currentCockpit == style
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) theme.accent.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { onSelectCockpit(style) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(style.label, color = if (isSel) theme.accent else theme.textBright, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                                }
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
                            Text("Security PIN", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text("Protects deletes and ledger wipes", color = theme.textMuted, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { showSetPinDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Change PIN", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(color = theme.surfaceAlt)

                    TextButton(
                        onClick = { showPinVerifyDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = theme.mildRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reset & Clear All Ledger Records", color = theme.mildRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

// ---------------- COMPACT REUSABLE INPUT HELPER ----------------

@Composable
fun CompactInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalThemeColors.current

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.surfaceAlt)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = theme.textMuted, fontSize = 12.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = theme.textBright, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(theme.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ---------------- MINIMAL LONG-PRESS ACTION SHEET ----------------

@Composable
fun MinimalLongPressSheet(
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalThemeColors.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = theme.surface,
            modifier = Modifier.width(220.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEdit() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                    Text("Edit Item", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Divider(color = theme.surfaceAlt, thickness = 0.5.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDelete() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = theme.mildRed, modifier = Modifier.size(16.dp))
                    Text("Delete (PIN)", color = theme.mildRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
    onLongPressRecurring: (Transaction) -> Unit,
    onLongPressAccount: (Account) -> Unit
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
                    Text("All Recurring Entries", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                            onLongPress = { onLongPressRecurring(rTx) }
                        )
                    }

                    items(recurringAccounts) { rAcc ->
                        RecurringRibbonItem(
                            title = "${rAcc.name} (${rAcc.type})",
                            subtitle = "Installment • ${rAcc.frequency}",
                            amount = rAcc.balance / rAcc.installmentCount.coerceAtLeast(1),
                            isPositive = rAcc.type == "BORROWER",
                            onLongPress = { onLongPressAccount(rAcc) }
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
                    Text("All Records (${filteredList.size})", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onExportCsv, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Export CSV", tint = theme.accent, modifier = Modifier.size(17.dp))
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textMuted)
                        }
                    }
                }

                CompactInputField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search note, amount, party...",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "IN", "OUT", "B/L").forEach { f ->
                        val isSel = selectedFilter == f
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else theme.surfaceAlt)
                                .clickable { selectedFilter = f }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(f, color = if (isSel) theme.bg else theme.textMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }

                if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No matching ledger transactions found.", color = theme.textMuted, fontSize = 12.sp)
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

// ---------------- CREATE CARD DIALOG ----------------

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
        title = { Text("Create Card", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
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
                            Text(t, color = if (isSel) theme.bg else theme.textBright, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                CompactInputField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = when (selectedType) {
                        "CASH" -> "Wallet Name"
                        "BANK" -> "Bank Name"
                        "CREDIT" -> "Card Name"
                        "LENDER" -> "Lender Name"
                        else -> "Borrower Name"
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactInputField(
                        value = amount,
                        onValueChange = { amount = it },
                        placeholder = if (selectedType == "CREDIT") "Avail Limit" else "Amount",
                        modifier = Modifier.weight(1f)
                    )

                    val cDateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(creationDateMillis))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .clickable { showCreationDatePicker = true }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Date: $cDateStr", color = theme.textBright, fontSize = 12.sp)
                    }
                }

                if (selectedType == "CREDIT") {
                    CompactInputField(
                        value = totalLimit,
                        onValueChange = { totalLimit = it },
                        placeholder = "Total Credit Limit (₹)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (selectedType in listOf("LENDER", "BORROWER") && availableSourceAccounts.isNotEmpty()) {
                    val activeSource = availableSourceAccounts.firstOrNull { it.id == selectedSourceAccId }?.name ?: "Select Funding"
                    ExposedDropdownMenuBox(
                        expanded = sourceAccExpanded,
                        onExpandedChange = { sourceAccExpanded = !sourceAccExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.surfaceAlt)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "${if (selectedType == "LENDER") "Deposit to" else "Fund from"}: $activeSource",
                                color = theme.textBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        ExposedDropdownMenu(
                            expanded = sourceAccExpanded,
                            onDismissRequest = { sourceAccExpanded = false },
                            modifier = Modifier.background(theme.surface)
                        ) {
                            availableSourceAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (₹${acc.balance.toInt()})", color = theme.textBright, fontSize = 12.sp) },
                                    onClick = {
                                        selectedSourceAccId = acc.id
                                        sourceAccExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (selectedType in listOf("LENDER", "BORROWER")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("BULLET", "INSTALLMENTS").forEach { rType ->
                            val isSel = repaymentType == rType
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) theme.accent else theme.surfaceAlt)
                                    .clickable { repaymentType = rType }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(rType, color = if (isSel) theme.bg else theme.textBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                                Box(
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.surfaceAlt)
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(frequency, color = theme.textBright, fontSize = 12.sp)
                                }
                                ExposedDropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }, modifier = Modifier.background(theme.surface)) {
                                    listOf("MONTHLY", "YEARLY").forEach { f ->
                                        DropdownMenuItem(text = { Text(f, color = theme.textBright, fontSize = 12.sp) }, onClick = { frequency = f; freqExpanded = false })
                                    }
                                }
                            }

                            CompactInputField(
                                value = installmentCount,
                                onValueChange = { installmentCount = it },
                                placeholder = "Count",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        val principal = amount.toDoubleOrNull() ?: 0.0
                        val count = installmentCount.toIntOrNull() ?: 1
                        if (principal > 0.0 && count > 0) {
                            Text(
                                "Plan: ₹${String.format("%.0f", principal / count)} / ${frequency.lowercase()}",
                                color = theme.accent,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (selectedType in listOf("CREDIT", "LENDER", "BORROWER")) {
                    val dDateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dueDateMillis))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .clickable { showDueDatePicker = true }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Due Date: $dDateStr", color = theme.textBright, fontSize = 12.sp)
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
                Text("Create Card", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted, fontSize = 12.sp) }
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
        "ADD" -> "Add to ${account.name}"
        "REFILL" -> "Refill ${account.name}"
        "REPAY" -> "Repay ${account.name}"
        else -> "Collect from ${account.name}"
    }

    AlertDialog(
        containerColor = theme.surface,
        titleContentColor = theme.textBright,
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactInputField(
                    value = deltaAmount,
                    onValueChange = {
                        deltaAmount = it
                        errorMsg = ""
                    },
                    placeholder = "Amount (₹)",
                    modifier = Modifier.fillMaxWidth()
                )

                if (needsChannel && availableFundingAccounts.isNotEmpty()) {
                    val activeFunding = availableFundingAccounts.firstOrNull { it.id == selectedFundingAccId }?.name ?: "Select Linked Account"
                    ExposedDropdownMenuBox(
                        expanded = fundingExpanded,
                        onExpandedChange = { fundingExpanded = !fundingExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.surfaceAlt)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "${if (action == "COLLECT") "Deposit to" else "Fund from"}: $activeFunding",
                                color = theme.textBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        ExposedDropdownMenu(
                            expanded = fundingExpanded,
                            onDismissRequest = { fundingExpanded = false },
                            modifier = Modifier.background(theme.surface)
                        ) {
                            availableFundingAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (₹${acc.balance.toInt()})", color = theme.textBright, fontSize = 12.sp) },
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.surfaceAlt)
                        .clickable { showActionDatePicker = true }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("Date: $dateFormatted", color = theme.textBright, fontSize = 12.sp)
                }

                CompactInputField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "Note (Optional)",
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = theme.mildRed, fontSize = 10.5.sp)
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
                        errorMsg = "Repayment cannot exceed ₹${account.balance}"
                        return@Button
                    }
                    if (action == "COLLECT" && amt > account.balance) {
                        errorMsg = "Collection cannot exceed ₹${account.balance}"
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
                Text("Confirm", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted, fontSize = 12.sp) }
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

// ---------------- EDIT ACCOUNT CARD DIALOG ----------------

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
        title = { Text("Edit ${account.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactInputField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Account Name",
                    modifier = Modifier.fillMaxWidth()
                )

                CompactInputField(
                    value = balance,
                    onValueChange = { balance = it },
                    placeholder = "Current Balance / Outstanding",
                    modifier = Modifier.fillMaxWidth()
                )

                if (account.type == "CREDIT") {
                    CompactInputField(
                        value = totalLimit,
                        onValueChange = { totalLimit = it },
                        placeholder = "Total Limit (₹)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (account.type in listOf("CREDIT", "LENDER", "BORROWER")) {
                    val dDateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dueDateMillis))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .clickable { showDueDatePicker = true }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Due Date: $dDateStr", color = theme.textBright, fontSize = 12.sp)
                    }
                }

                val newBal = balance.toDoubleOrNull() ?: account.balance
                if (newBal != account.balance) {
                    CompactInputField(
                        value = adjustmentNote,
                        onValueChange = { adjustmentNote = it },
                        placeholder = "Reason for adjustment",
                        modifier = Modifier.fillMaxWidth()
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
                Text("Save", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = theme.mildRed, fontSize = 12.sp) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted, fontSize = 12.sp) }
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

// ---------------- TRANSACTION DIALOG (WITH ACCURATE RECURRING RETENTION) ----------------

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

    // Accurate persistent recurring state initialization
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
        title = { Text(if (prefilled != null) "Edit Entry" else "Record Entry", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            flowType = "OUT"
                            category = outCategories.first()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (flowType == "OUT") theme.mildRed else theme.surfaceAlt),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("Out (Expense)", color = if (flowType == "OUT") Color.White else theme.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            flowType = "IN"
                            category = inCategories.first()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (flowType == "IN") theme.mildGreen else theme.surfaceAlt),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("In (Income)", color = if (flowType == "IN") Color.Black else theme.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactInputField(
                        value = amount,
                        onValueChange = { amount = it },
                        placeholder = "Amount (₹)",
                        modifier = Modifier.weight(1f)
                    )

                    val dateFormatted = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(selectedDateMillis))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Date: $dateFormatted", color = theme.textBright, fontSize = 12.sp)
                    }
                }

                CompactInputField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "Note / Merchant",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = catExpanded,
                        onExpandedChange = { catExpanded = !catExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.surfaceAlt)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(category, color = theme.textBright, fontSize = 12.sp)
                        }
                        ExposedDropdownMenu(
                            expanded = catExpanded,
                            onDismissRequest = { catExpanded = false },
                            modifier = Modifier.background(theme.surface)
                        ) {
                            val activeCats = if (flowType == "IN") inCategories else outCategories
                            activeCats.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat, color = theme.textBright, fontSize = 12.sp) },
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
                            Box(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.surfaceAlt)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(activeAccName, color = theme.textBright, fontSize = 12.sp)
                            }
                            ExposedDropdownMenu(
                                expanded = accExpanded,
                                onDismissRequest = { accExpanded = false },
                                modifier = Modifier.background(theme.surface)
                            ) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text("${acc.name} (${acc.type})", color = theme.textBright, fontSize = 12.sp) },
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
                            Box(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.surfaceAlt)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Repeat: $recurringFrequency", color = theme.textBright, fontSize = 12.sp)
                            }
                            ExposedDropdownMenu(
                                expanded = freqExpanded,
                                onDismissRequest = { freqExpanded = false },
                                modifier = Modifier.background(theme.surface)
                            ) {
                                listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY").forEach { f ->
                                    DropdownMenuItem(text = { Text(f, color = theme.textBright, fontSize = 12.sp) }, onClick = { recurringFrequency = f; freqExpanded = false })
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.surfaceAlt)
                                    .clickable { showEndDatePicker = true }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Ends on: $endFormatted", color = theme.textBright, fontSize = 12.sp)
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
                Text("Confirm", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted, fontSize = 12.sp) }
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
        title = { Text("Intra-Account Transfer", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = fromExpanded, onExpandedChange = { fromExpanded = !fromExpanded }, modifier = Modifier.fillMaxWidth()) {
                    val fromName = accounts.firstOrNull { it.id == fromAccId }?.name ?: "Select"
                    Box(
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("From: $fromName", color = theme.textBright, fontSize = 12.sp)
                    }
                    ExposedDropdownMenu(expanded = fromExpanded, onDismissRequest = { fromExpanded = false }, modifier = Modifier.background(theme.surface)) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text(acc.name, color = theme.textBright, fontSize = 12.sp) }, onClick = { fromAccId = acc.id; fromExpanded = false })
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = toExpanded, onExpandedChange = { toExpanded = !toExpanded }, modifier = Modifier.fillMaxWidth()) {
                    val toName = accounts.firstOrNull { it.id == toAccId }?.name ?: "Select"
                    Box(
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("To: $toName", color = theme.textBright, fontSize = 12.sp)
                    }
                    ExposedDropdownMenu(expanded = toExpanded, onDismissRequest = { toExpanded = false }, modifier = Modifier.background(theme.surface)) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text(acc.name, color = theme.textBright, fontSize = 12.sp) }, onClick = { toAccId = acc.id; toExpanded = false })
                        }
                    }
                }

                CompactInputField(
                    value = amount,
                    onValueChange = { amount = it },
                    placeholder = "Amount (₹)",
                    modifier = Modifier.fillMaxWidth()
                )

                val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(transferDateMillis))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.surfaceAlt)
                        .clickable { showTransferDatePicker = true }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("Date: $dateFormatted", color = theme.textBright, fontSize = 12.sp)
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
                Text("Transfer", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted, fontSize = 12.sp) }
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
                Text("Select", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted, fontSize = 12.sp) }
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

// ---------------- THEMED PIN DIALOGS ----------------

@Composable
fun ThemePinPadDialog(title: String, subtitle: String, expectedPin: String, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val theme = LocalThemeColors.current
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = theme.surface, modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(subtitle, color = theme.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier.size(15.dp).clip(CircleShape).background(
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
                    Text("Incorrect PIN. Try again.", color = theme.mildRed, fontSize = 11.5.sp)
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
                                    modifier = Modifier.size(56.dp).clip(CircleShape).background(theme.surfaceAlt).clickable {
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
                                    Text(key, color = theme.textBright, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted, fontSize = 12.sp) }
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
                Text("Set New PIN", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Enter a 4-digit security PIN for ledger actions.", color = theme.textMuted, fontSize = 12.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until 4) {
                        val isFilled = i < pinText.length
                        Box(modifier = Modifier.size(15.dp).clip(CircleShape).background(if (isFilled) theme.accent else theme.surfaceAlt))
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
                                    modifier = Modifier.size(56.dp).clip(CircleShape).background(theme.surfaceAlt).clickable {
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
                                    Text(key, color = theme.textBright, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted, fontSize = 12.sp) }
            }
        }
    }
}
