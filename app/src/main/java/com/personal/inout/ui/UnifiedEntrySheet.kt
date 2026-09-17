package com.personal.inout.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
    onDismiss: () -> Unit,
    onSubmit: (
        mode: EntryMode,
        debitAccountId: Long,
        creditAccountId: Long,
        amount: Double,
        description: String,
        timestamp: Long,
        isRecurring: Boolean,
        recurringFrequency: String
    ) -> Unit
) {
    val theme = LocalThemeColors.current

    var selectedMode by remember { mutableStateOf(EntryMode.EXPENSE) }
    var mathInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Evaluated amount
    val calculatedAmount = remember(mathInput) {
        MathEvaluator.evaluate(mathInput)
    }

    // Filter account types
    val assetAccounts = remember(allAccounts) {
        allAccounts.filter { it.classification == AccountClassification.ASSET || it.classification == AccountClassification.LIABILITY }
    }
    val expenseCategories = remember(allAccounts) {
        allAccounts.filter { it.classification == AccountClassification.EXPENSE }
    }
    val revenueCategories = remember(allAccounts) {
        allAccounts.filter { it.classification == AccountClassification.REVENUE }
    }

    // Selection IDs
    var primaryAccountId by remember(assetAccounts) {
        mutableStateOf(assetAccounts.firstOrNull()?.id ?: 0L)
    }
    var targetAccountId by remember(assetAccounts) {
        mutableStateOf(assetAccounts.getOrNull(1)?.id ?: assetAccounts.firstOrNull()?.id ?: 0L)
    }
    var selectedCategoryId by remember(expenseCategories, revenueCategories, selectedMode) {
        mutableStateOf(
            if (selectedMode == EntryMode.EXPENSE) expenseCategories.firstOrNull()?.id ?: 0L
            else revenueCategories.firstOrNull()?.id ?: 0L
        )
    }

    var primaryAccExpanded by remember { mutableStateOf(false) }
    var targetAccExpanded by remember { mutableStateOf(false) }
    var catExpanded by remember { mutableStateOf(false) }

    // Recurring (gated by Pro)
    var isRecurring by remember { mutableStateOf(false) }
    var recurringFrequency by remember { mutableStateOf("MONTHLY") }
    var freqExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.textMuted.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Segmented Mode Switcher
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
                    val activeTextColor = if (mode == EntryMode.INCOME) Color.Black else Color.White

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
                            text = mode.label,
                            color = if (isSel) activeTextColor else theme.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // In-Field Calculator + Date Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Calculator Input Box
                Column(modifier = Modifier.weight(1.3f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.surfaceAlt)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (mathInput.isEmpty()) {
                            Text("Amount (e.g. 450+60)", color = theme.textMuted, fontSize = 12.5.sp)
                        }
                        BasicTextField(
                            value = mathInput,
                            onValueChange = { mathInput = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = theme.textBright,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            cursorBrush = SolidColor(theme.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Live evaluation preview
                    if (calculatedAmount != null && mathInput.any { it in "+-*/" }) {
                        Text(
                            text = "= ₹ ${String.format("%,.2f", calculatedAmount)}",
                            color = theme.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                        )
                    }
                }

                // Compact Date Box
                val dateLabel = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.surfaceAlt)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                        Text(dateLabel, color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Dynamic Account / Category Selectors
            when (selectedMode) {
                EntryMode.EXPENSE -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Source Account (Debit source)
                        ExposedDropdownMenuBox(
                            expanded = primaryAccExpanded,
                            onExpandedChange = { primaryAccExpanded = !primaryAccExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            val accName = assetAccounts.firstOrNull { it.id == primaryAccountId }?.name ?: "Wallet"
                            Box(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.surfaceAlt)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Paid via: $accName", color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            ExposedDropdownMenu(
                                expanded = primaryAccExpanded,
                                onDismissRequest = { primaryAccExpanded = false },
                                modifier = Modifier.background(theme.surface)
                            ) {
                                assetAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.name, color = theme.textBright, fontSize = 12.sp) },
                                        onClick = {
                                            primaryAccountId = acc.id
                                            primaryAccExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Expense Category
                        ExposedDropdownMenuBox(
                            expanded = catExpanded,
                            onExpandedChange = { catExpanded = !catExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            val catName = expenseCategories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Category"
                            Box(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.surfaceAlt)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Bucket: $catName", color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            ExposedDropdownMenu(
                                expanded = catExpanded,
                                onDismissRequest = { catExpanded = false },
                                modifier = Modifier.background(theme.surface)
                            ) {
                                expenseCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name, color = theme.textBright, fontSize = 12.sp) },
                                        onClick = {
                                            selectedCategoryId = cat.id
                                            catExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                EntryMode.INCOME -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Deposit Destination
                        ExposedDropdownMenuBox(
                            expanded = primaryAccExpanded,
                            onExpandedChange = { primaryAccExpanded = !primaryAccExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            val accName = assetAccounts.firstOrNull { it.id == primaryAccountId }?.name ?: "Deposit Account"
                            Box(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.surfaceAlt)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Deposit to: $accName", color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            ExposedDropdownMenu(
                                expanded = primaryAccExpanded,
                                onDismissRequest = { primaryAccExpanded = false },
                                modifier = Modifier.background(theme.surface)
                            ) {
                                assetAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.name, color = theme.textBright, fontSize = 12.sp) },
                                        onClick = {
                                            primaryAccountId = acc.id
                                            primaryAccExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Income Source Category
                        ExposedDropdownMenuBox(
                            expanded = catExpanded,
                            onExpandedChange = { catExpanded = !catExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            val catName = revenueCategories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Source"
                            Box(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.surfaceAlt)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("From: $catName", color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            ExposedDropdownMenu(
                                expanded = catExpanded,
                                onDismissRequest = { catExpanded = false },
                                modifier = Modifier.background(theme.surface)
                            ) {
                                revenueCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name, color = theme.textBright, fontSize = 12.sp) },
                                        onClick = {
                                            selectedCategoryId = cat.id
                                            catExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                EntryMode.TRANSFER -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // From Account
                        ExposedDropdownMenuBox(
                            expanded = primaryAccExpanded,
                            onExpandedChange = { primaryAccExpanded = !primaryAccExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            val fromName = assetAccounts.firstOrNull { it.id == primaryAccountId }?.name ?: "Source"
                            Box(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.surfaceAlt)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("From: $fromName", color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            ExposedDropdownMenu(
                                expanded = primaryAccExpanded,
                                onDismissRequest = { primaryAccExpanded = false },
                                modifier = Modifier.background(theme.surface)
                            ) {
                                assetAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.name, color = theme.textBright, fontSize = 12.sp) },
                                        onClick = {
                                            primaryAccountId = acc.id
                                            primaryAccExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // To Account
                        ExposedDropdownMenuBox(
                            expanded = targetAccExpanded,
                            onExpandedChange = { targetAccExpanded = !targetAccExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            val toName = assetAccounts.firstOrNull { it.id == targetAccountId }?.name ?: "Target"
                            Box(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.surfaceAlt)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("To: $toName", color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            ExposedDropdownMenu(
                                expanded = targetAccExpanded,
                                onDismissRequest = { targetAccExpanded = false },
                                modifier = Modifier.background(theme.surface)
                            ) {
                                assetAccounts.filter { it.id != primaryAccountId }.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.name, color = theme.textBright, fontSize = 12.sp) },
                                        onClick = {
                                            targetAccountId = acc.id
                                            targetAccExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Note / Merchant Input
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.surfaceAlt)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (note.isEmpty()) {
                    Text("Note / Merchant / Narration", color = theme.textMuted, fontSize = 12.sp)
                }
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    singleLine = true,
                    textStyle = TextStyle(color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(theme.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Recurring Entry Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recurring Schedule?", color = theme.textBright, fontSize = 12.sp)
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
                    Box(
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.surfaceAlt)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Frequency: $recurringFrequency", color = theme.textBright, fontSize = 12.sp)
                    }
                    ExposedDropdownMenu(
                        expanded = freqExpanded,
                        onDismissRequest = { freqExpanded = false },
                        modifier = Modifier.background(theme.surface)
                    ) {
                        listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY").forEach { freq ->
                            DropdownMenuItem(
                                text = { Text(freq, color = theme.textBright, fontSize = 12.sp) },
                                onClick = {
                                    recurringFrequency = freq
                                    freqExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Action Buttons
            Button(
                onClick = {
                    val finalAmt = calculatedAmount ?: return@Button
                    if (finalAmt <= 0.0) return@Button

                    val (debitAccId, creditAccId) = when (selectedMode) {
                        EntryMode.EXPENSE -> Pair(selectedCategoryId, primaryAccountId)
                        EntryMode.INCOME -> Pair(primaryAccountId, selectedCategoryId)
                        EntryMode.TRANSFER -> Pair(targetAccountId, primaryAccountId)
                    }

                    onSubmit(
                        selectedMode,
                        debitAccId,
                        creditAccId,
                        finalAmt,
                        note,
                        selectedDateMillis,
                        isRecurring,
                        recurringFrequency
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text(
                    text = "Post to Ledger",
                    color = theme.bg,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp
                )
            }

            Spacer(Modifier.height(16.dp))
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
}
