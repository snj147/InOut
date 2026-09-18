package com.personal.inout.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.AccountClassification
import com.personal.inout.data.LedgerAccount
import com.personal.inout.util.MathEvaluator
import java.text.SimpleDateFormat
import java.util.*

enum class EntryMode(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedEntrySheet(
    allAccounts: List<LedgerAccount>,
    isProUser: Boolean,
    prefilledAccountId: Long? = null,
    prefilledNote: String = "",
    prefilledAmount: Double? = null,
    onDismiss: () -> Unit,
    onSubmitExpenseIncome: (isExpense: Boolean, assetAccountId: Long, category: String, amount: Double, note: String, date: Long, isRecurring: Boolean, frequency: String) -> Unit,
    onSubmitTransfer: (fromAccountId: Long, toAccountId: Long, amount: Double, note: String, date: Long) -> Unit
) {
    val theme = LocalThemeColors.current
    val scrollState = rememberScrollState()

    var selectedMode by remember { mutableStateOf(EntryMode.EXPENSE) }
    var amountExpression by remember { mutableStateOf(prefilledAmount?.let { String.format("%.2f", it) } ?: "") }
    var note by remember { mutableStateOf(prefilledNote) }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val assetAccounts = remember(allAccounts) {
        allAccounts.filter { it.classification == AccountClassification.ASSET || it.classification == AccountClassification.LIABILITY }
    }

    var selectedAssetAccount by remember(assetAccounts, prefilledAccountId) {
        mutableStateOf(
            assetAccounts.firstOrNull { it.id == prefilledAccountId }
                ?: assetAccounts.firstOrNull { it.subType == "BANK" }
                ?: assetAccounts.firstOrNull()
        )
    }

    var selectedDestinationAccount by remember(assetAccounts) {
        mutableStateOf(assetAccounts.firstOrNull { it.id != selectedAssetAccount?.id } ?: assetAccounts.firstOrNull())
    }

    val categories = listOf("Food & Dining", "Groceries", "Transport", "Shopping", "Rent & Utilities", "Entertainment", "Health", "Salary", "Investment", "General")
    var selectedCategory by remember { mutableStateOf("Food & Dining") }

    var isRecurring by remember { mutableStateOf(false) }
    var recurringFrequency by remember { mutableStateOf("MONTHLY") }

    var showAccountPicker by remember { mutableStateOf(false) }
    var showDestAccountPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val evaluatedAmount = remember(amountExpression) {
        MathEvaluator.evaluate(amountExpression)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.textMuted.copy(alpha = 0.4f)) },
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Segmented Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.surfaceAlt)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EntryMode.values().forEach { mode ->
                    val isSel = selectedMode == mode
                    val activeBg = when (mode) {
                        EntryMode.EXPENSE -> theme.mildRed
                        EntryMode.INCOME -> theme.mildGreen
                        EntryMode.TRANSFER -> theme.accent
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) activeBg else Color.Transparent)
                            .clickable { selectedMode = mode }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            mode.label,
                            color = if (isSel) theme.bg else theme.textMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Amount Input & Date Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.3f)) {
                    CompactInputField(
                        value = amountExpression,
                        onValueChange = { amountExpression = it },
                        placeholder = "Amount (e.g. 450+60)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (evaluatedAmount != null && amountExpression.any { it in "+-*/" }) {
                        Text(
                            "= ₹ ${String.format("%.2f", evaluatedAmount)}",
                            color = theme.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.surfaceAlt)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 10.dp, vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                        val dStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
                        Text(dStr, color = theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Accounts / Category Selectors
            if (selectedMode == EntryMode.TRANSFER) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectorTile(
                        label = "From: ${selectedAssetAccount?.name ?: "Select"}",
                        modifier = Modifier.weight(1f),
                        theme = theme,
                        onClick = { showAccountPicker = true }
                    )
                    SelectorTile(
                        label = "To: ${selectedDestinationAccount?.name ?: "Select"}",
                        modifier = Modifier.weight(1f),
                        theme = theme,
                        onClick = { showDestAccountPicker = true }
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectorTile(
                        label = "Via: ${selectedAssetAccount?.name ?: "Select Asset"}",
                        modifier = Modifier.weight(1f),
                        theme = theme,
                        onClick = { showAccountPicker = true }
                    )
                    SelectorTile(
                        label = "Bucket: $selectedCategory",
                        modifier = Modifier.weight(1f),
                        theme = theme,
                        onClick = { showCategoryPicker = true }
                    )
                }
            }

            // Description / Merchant
            CompactInputField(
                value = note,
                onValueChange = { note = it },
                placeholder = "Note / Merchant / Narration",
                modifier = Modifier.fillMaxWidth()
            )

            // Recurring Schedule Toggle
            if (selectedMode != EntryMode.TRANSFER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recurring Schedule?", color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.bg,
                            checkedTrackColor = theme.accent,
                            uncheckedThumbColor = theme.textMuted,
                            uncheckedTrackColor = theme.surfaceAlt
                        )
                    )
                }

                AnimatedVisibility(visible = isRecurring) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("DAILY", "WEEKLY", "MONTHLY").forEach { freq ->
                            val isSel = recurringFrequency == freq
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) theme.accent else Color.Transparent)
                                    .clickable { recurringFrequency = freq }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(freq, color = if (isSel) theme.bg else theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Post Button
            Button(
                onClick = {
                    val finalAmount = evaluatedAmount ?: 0.0
                    if (finalAmount > 0.0 && selectedAssetAccount != null) {
                        if (selectedMode == EntryMode.TRANSFER) {
                            val dest = selectedDestinationAccount
                            if (dest != null && dest.id != selectedAssetAccount!!.id) {
                                onSubmitTransfer(
                                    selectedAssetAccount!!.id,
                                    dest.id,
                                    finalAmount,
                                    note,
                                    selectedDateMillis
                                )
                            }
                        } else {
                            onSubmitExpenseIncome(
                                selectedMode == EntryMode.EXPENSE,
                                selectedAssetAccount!!.id,
                                selectedCategory,
                                finalAmount,
                                note,
                                selectedDateMillis,
                                isRecurring,
                                recurringFrequency
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Post to Ledger", color = theme.bg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showDatePicker) {
        ThemedDatePickerDialog(
            initialDateMillis = selectedDateMillis,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                selectedDateMillis = it
                showDatePicker = false
            }
        )
    }

    if (showAccountPicker) {
        AccountSelectionDialog(
            title = "Select Source Asset",
            accounts = assetAccounts,
            onDismiss = { showAccountPicker = false },
            onSelect = {
                selectedAssetAccount = it
                showAccountPicker = false
            }
        )
    }

    if (showDestAccountPicker) {
        AccountSelectionDialog(
            title = "Select Destination Asset",
            accounts = assetAccounts.filter { it.id != selectedAssetAccount?.id },
            onDismiss = { showDestAccountPicker = false },
            onSelect = {
                selectedDestinationAccount = it
                showDestAccountPicker = false
            }
        )
    }

    if (showCategoryPicker) {
        GenericListDialog(
            title = "Select Category",
            items = categories,
            onDismiss = { showCategoryPicker = false },
            onSelect = {
                selectedCategory = it
                showCategoryPicker = false
            }
        )
    }
}

@Composable
private fun SelectorTile(label: String, modifier: Modifier, theme: ThemeColors, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(theme.surfaceAlt)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun AccountSelectionDialog(
    title: String,
    accounts: List<LedgerAccount>,
    onDismiss: () -> Unit,
    onSelect: (LedgerAccount) -> Unit
) {
    val theme = LocalThemeColors.current
    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text(title, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                accounts.forEach { acc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .clickable { onSelect(acc) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(acc.name, color = theme.textBright, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(acc.subType, color = theme.textMuted, fontSize = 10.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } }
    )
}

@Composable
private fun GenericListDialog(
    title: String,
    items: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val theme = LocalThemeColors.current
    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text(title, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelect(item) }
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                    ) {
                        Text(item, color = theme.textBright, fontSize = 12.5.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } }
    )
}
