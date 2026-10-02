package com.personal.inout.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.personal.inout.data.HabitPillPreset
import com.personal.inout.data.LedgerPocket
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.util.MathEvaluator
import java.text.SimpleDateFormat
import java.util.*

enum class ActiveEntryRail(val label: String, val nature: MovementNature) {
    EXPENSE("Expense", MovementNature.OPERATING_EXPENSE),
    INFLOW("Inflow", MovementNature.OPERATING_INCOME),
    TRANSFER("Transfer", MovementNature.TRANSFER),
    CARD_BILL("Card Due", MovementNature.OPERATING_EXPENSE),
    PEER("Lend / Borrow", MovementNature.TRANSFER)
}

data class SplitItem(
    val category: String,
    val amountExpression: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedEntrySheet(
    allPockets: List<LedgerPocket>,
    prefilledPocketId: Long? = null,
    prefilledNote: String = "",
    prefilledAmount: Double? = null,
    onDismiss: () -> Unit,
    onSubmitTransaction: (
        nature: MovementNature,
        sourcePocketId: Long,
        targetPocketId: Long?,
        amount: Double,
        category: String,
        note: String,
        date: Long,
        isRecurring: Boolean,
        frequency: String,
        isTaxDeductible: Boolean,
        isReimbursable: Boolean
    ) -> Unit,
    onNavigateToCreatePocket: () -> Unit
) {
    val theme = LocalThemeColors.current
    val scrollState = rememberScrollState()

    var selectedRail by remember { mutableStateOf(ActiveEntryRail.EXPENSE) }
    var amountExpression by remember { mutableStateOf(prefilledAmount?.let { String.format("%.2f", it) } ?: "") }
    var note by remember { mutableStateOf(prefilledNote) }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Flags: Rule 22
    var isTaxDeductible by remember { mutableStateOf(false) }
    var isReimbursable by remember { mutableStateOf(false) }

    // Recurring: Rule 8
    var isRecurring by remember { mutableStateOf(false) }
    var recurringFrequency by remember { mutableStateOf("MONTHLY") }

    // Multi-Category Splits: Rule 17
    var isSplitEnabled by remember { mutableStateOf(false) }
    val splitItems = remember {
        mutableStateListOf(
            SplitItem("Food & Dining", ""),
            SplitItem("Groceries", "")
        )
    }

    // Micro-Numpad Pop-up: Rule 30
    var activeHabitPopupPreset by remember { mutableStateOf<HabitPillPreset?>(null) }
    var habitPopupAmount by remember { mutableStateOf("") }

    val liquidPockets = remember(allPockets) {
        allPockets.filter {
            it.type in listOf(
                PocketType.LIQUID,
                PocketType.PREPAID_WALLET,
                PocketType.CREDIT_CARD
            )
        }
    }

    var selectedSourcePocket by remember(liquidPockets, prefilledPocketId) {
        mutableStateOf(
            liquidPockets.firstOrNull { it.id == prefilledPocketId }
                ?: liquidPockets.firstOrNull { it.type == PocketType.LIQUID }
                ?: liquidPockets.firstOrNull()
        )
    }

    var selectedTargetPocket by remember(allPockets) {
        mutableStateOf(
            allPockets.firstOrNull { it.id != selectedSourcePocket?.id }
        )
    }

    val categories = listOf(
        "Food & Dining", "Tea & Snacks", "Groceries", "Fuel & Commute",
        "Rent & Utilities", "Shopping", "Health & Pharmacy", "Entertainment",
        "Salary & Consulting", "Investments", "Finance & Interest", "General"
    )
    var selectedCategory by remember { mutableStateOf("Food & Dining") }

    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val evaluatedAmount = remember(amountExpression) {
        MathEvaluator.evaluate(amountExpression)
    }

    // Dynamic habit presets based on Rule 30
    val habitPresets = remember(selectedSourcePocket) {
        val defId = selectedSourcePocket?.id ?: 0L
        listOf(
            HabitPillPreset("Tea & Snacks", "Tea & Snacks", defId),
            HabitPillPreset("Lunch & Meals", "Food & Dining", defId),
            HabitPillPreset("Fuel & Auto", "Fuel & Commute", defId),
            HabitPillPreset("Groceries", "Groceries", defId)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.textMuted.copy(alpha = 0.35f)) },
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rule 30: Contextual Habit Pills Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                habitPresets.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                            .clickable {
                                if (allPockets.isNotEmpty()) {
                                    activeHabitPopupPreset = preset
                                    habitPopupAmount = ""
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            preset.label,
                            color = theme.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Segmented Mode Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.surfaceAlt)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                ActiveEntryRail.values().forEach { rail ->
                    val isSel = selectedRail == rail
                    val activeBg = when (rail) {
                        ActiveEntryRail.EXPENSE -> theme.mildRed
                        ActiveEntryRail.INFLOW -> theme.mildGreen
                        ActiveEntryRail.TRANSFER -> theme.accent
                        ActiveEntryRail.CARD_BILL -> theme.accent
                        ActiveEntryRail.PEER -> theme.accent
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isSel) activeBg else Color.Transparent)
                            .clickable { selectedRail = rail }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            rail.label,
                            color = if (isSel) theme.bg else theme.textMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Amount Input & Date Row
            if (!isSplitEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.3f)) {
                        OutlinedTextField(
                            value = amountExpression,
                            onValueChange = { amountExpression = it },
                            placeholder = { Text("0.00", color = theme.textMuted, fontSize = 14.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = theme.surfaceAlt,
                                unfocusedContainerColor = theme.surfaceAlt,
                                focusedBorderColor = theme.accent,
                                unfocusedBorderColor = theme.borderLight,
                                focusedTextColor = theme.textBright,
                                unfocusedTextColor = theme.textBright
                            ),
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
                            .border(1.dp, theme.borderLight, RoundedCornerShape(10.dp))
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(13.dp))
                            val dStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
                            Text(dStr, color = theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Rule 17: Split Transactions Drawer
            if (selectedRail == ActiveEntryRail.EXPENSE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Split into multiple categories?", color = theme.textMuted, fontSize = 11.5.sp)
                    Switch(
                        checked = isSplitEnabled,
                        onCheckedChange = { isSplitEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.bg,
                            checkedTrackColor = theme.accent,
                            uncheckedThumbColor = theme.textMuted,
                            uncheckedTrackColor = theme.surfaceAlt
                        )
                    )
                }

                if (isSplitEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.surfaceAlt)
                            .border(1.dp, theme.borderLight, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        splitItems.forEachIndexed { index, split ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}.", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(split.category, color = theme.textBright, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = split.amountExpression,
                                    onValueChange = { newVal -> splitItems[index] = split.copy(amountExpression = newVal) },
                                    placeholder = { Text("0", color = theme.textMuted, fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.width(90.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = theme.surface,
                                        unfocusedContainerColor = theme.surface,
                                        focusedBorderColor = theme.accent,
                                        unfocusedBorderColor = theme.borderLight,
                                        focusedTextColor = theme.textBright,
                                        unfocusedTextColor = theme.textBright
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Account & Category Selectors
            if (selectedRail in listOf(ActiveEntryRail.TRANSFER, ActiveEntryRail.CARD_BILL, ActiveEntryRail.PEER)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectorTile(
                        label = "From: ${selectedSourcePocket?.name ?: "Select"}",
                        modifier = Modifier.weight(1f),
                        theme = theme,
                        onClick = { showSourcePicker = true }
                    )
                    SelectorTile(
                        label = "To: ${selectedTargetPocket?.name ?: "Select"}",
                        modifier = Modifier.weight(1f),
                        theme = theme,
                        onClick = { showTargetPicker = true }
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectorTile(
                        label = "Account: ${selectedSourcePocket?.name ?: "Select"}",
                        modifier = Modifier.weight(1f),
                        theme = theme,
                        onClick = { showSourcePicker = true }
                    )
                    SelectorTile(
                        label = "Category: $selectedCategory",
                        modifier = Modifier.weight(1f),
                        theme = theme,
                        onClick = { showCategoryPicker = true }
                    )
                }
            }

            // Note / Narration
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("Narration / Description / Merchant", color = theme.textMuted, fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = theme.surfaceAlt,
                    unfocusedContainerColor = theme.surfaceAlt,
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = theme.borderLight,
                    focusedTextColor = theme.textBright,
                    unfocusedTextColor = theme.textBright
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Rule 22: Statutory Tax & Reimbursement Metadata Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isTaxDeductible,
                    onClick = { isTaxDeductible = !isTaxDeductible },
                    label = { Text("80C / Tax Deductible", fontSize = 10.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.accent,
                        selectedLabelColor = theme.bg,
                        containerColor = theme.surfaceAlt,
                        labelColor = theme.textMuted
                    )
                )

                FilterChip(
                    selected = isReimbursable,
                    onClick = { isReimbursable = !isReimbursable },
                    label = { Text("Corporate Reimbursable", fontSize = 10.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.accent,
                        selectedLabelColor = theme.bg,
                        containerColor = theme.surfaceAlt,
                        labelColor = theme.textMuted
                    )
                )
            }

            // Rule 8: Recurring Schedule Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Repeat", color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("NONE", "DAILY", "WEEKLY", "MONTHLY").forEach { freq ->
                        val isSel = (if (!isRecurring) "NONE" else recurringFrequency) == freq
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) theme.accent else theme.surfaceAlt)
                                .clickable {
                                    if (freq == "NONE") {
                                        isRecurring = false
                                    } else {
                                        isRecurring = true
                                        recurringFrequency = freq
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                freq,
                                color = if (isSel) theme.bg else theme.textMuted,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Rule 10: Mandatory Account Binding Guard
            val hasAccounts = allPockets.isNotEmpty()
            Button(
                onClick = {
                    if (!hasAccounts) {
                        onNavigateToCreatePocket()
                        return@Button
                    }
                    val src = selectedSourcePocket ?: return@Button
                    val finalAmount = if (isSplitEnabled) {
                        splitItems.sumOf { MathEvaluator.evaluate(it.amountExpression) ?: 0.0 }
                    } else {
                        evaluatedAmount ?: 0.0
                    }

                    if (finalAmount <= 0.0) return@Button

                    val movementNature = when (selectedRail) {
                        ActiveEntryRail.EXPENSE -> MovementNature.OPERATING_EXPENSE
                        ActiveEntryRail.INFLOW -> MovementNature.OPERATING_INCOME
                        ActiveEntryRail.TRANSFER -> MovementNature.TRANSFER
                        ActiveEntryRail.CARD_BILL -> MovementNature.OPERATING_EXPENSE
                        ActiveEntryRail.PEER -> MovementNature.TRANSFER
                    }

                    val targetId = if (selectedRail in listOf(ActiveEntryRail.TRANSFER, ActiveEntryRail.CARD_BILL, ActiveEntryRail.PEER)) {
                        selectedTargetPocket?.id
                    } else null

                    onSubmitTransaction(
                        movementNature,
                        src.id,
                        targetId,
                        finalAmount,
                        selectedCategory,
                        note,
                        selectedDateMillis,
                        isRecurring,
                        recurringFrequency,
                        isTaxDeductible,
                        isReimbursable
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasAccounts) theme.accent else theme.surfaceAlt
                )
            ) {
                Text(
                    text = if (hasAccounts) "Post to Ledger" else "Add Account First",
                    color = if (hasAccounts) theme.bg else theme.textMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // Micro-Numpad Pop-up for Rapid Habit Entry (Rule 30)
    activeHabitPopupPreset?.let { preset ->
        MicroNumpadDialog(
            preset = preset,
            currentAmount = habitPopupAmount,
            onAmountChange = { habitPopupAmount = it },
            onDismiss = { activeHabitPopupPreset = null },
            onConfirm = {
                val amt = habitPopupAmount.toDoubleOrNull() ?: 0.0
                if (amt > 0.0) {
                    val targetId = selectedSourcePocket?.id ?: preset.defaultPocketId
                    onSubmitTransaction(
                        preset.defaultMovementNature,
                        targetId,
                        null,
                        amt,
                        preset.category,
                        preset.label,
                        System.currentTimeMillis(),
                        false,
                        "NONE",
                        false,
                        false
                    )
                    activeHabitPopupPreset = null
                    onDismiss()
                }
            }
        )
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

    if (showSourcePicker) {
        PocketSelectionDialog(
            title = "Select Source Account",
            pockets = liquidPockets,
            onDismiss = { showSourcePicker = false },
            onSelect = {
                selectedSourcePocket = it
                showSourcePicker = false
            }
        )
    }

    if (showTargetPicker) {
        PocketSelectionDialog(
            title = "Select Destination Account",
            pockets = allPockets.filter { it.id != selectedSourcePocket?.id },
            onDismiss = { showTargetPicker = false },
            onSelect = {
                selectedTargetPocket = it
                showTargetPicker = false
            }
        )
    }

    if (showCategoryPicker) {
        CategorySelectionDialog(
            title = "Select Category Bucket",
            categories = categories,
            onDismiss = { showCategoryPicker = false },
            onSelect = {
                selectedCategory = it
                showCategoryPicker = false
            }
        )
    }
}

@Composable
private fun SelectorTile(
    label: String,
    modifier: Modifier,
    theme: ThemeColors,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(theme.surfaceAlt)
            .border(1.dp, theme.borderLight, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            label,
            color = theme.textBright,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * BRD Rule 30: Zero-friction Micro-Numpad Pop-up.
 */
@Composable
private fun MicroNumpadDialog(
    preset: HabitPillPreset,
    currentAmount: String,
    onAmountChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val theme = LocalThemeColors.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(theme.surface)
                .border(1.dp, theme.borderLight, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = preset.label,
                color = theme.textBright,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (currentAmount.isEmpty()) "₹ 0" else "₹ $currentAmount",
                color = theme.accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            // Numpad Grid
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf(".", "0", "⌫")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                keys.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.surfaceAlt)
                                    .clickable {
                                        when (key) {
                                            "⌫" -> {
                                                if (currentAmount.isNotEmpty()) {
                                                    onAmountChange(currentAmount.dropLast(1))
                                                }
                                            }
                                            "." -> {
                                                if (!currentAmount.contains(".")) {
                                                    onAmountChange(if (currentAmount.isEmpty()) "0." else "$currentAmount.")
                                                }
                                            }
                                            else -> onAmountChange(currentAmount + key)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    key,
                                    color = theme.textBright,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Confirm", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
private fun PocketSelectionDialog(
    title: String,
    pockets: List<LedgerPocket>,
    onDismiss: () -> Unit,
    onSelect: (LedgerPocket) -> Unit
) {
    val theme = LocalThemeColors.current
    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text(title, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pockets.forEach { pocket ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                            .clickable { onSelect(pocket) }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pocket.name, color = theme.textBright, fontWeight = FontWeight.Medium, fontSize = 12.5.sp)
                        Text(pocket.type.name, color = theme.textMuted, fontSize = 9.5.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } }
    )
}

@Composable
private fun CategorySelectionDialog(
    title: String,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val theme = LocalThemeColors.current
    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text(title, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categories.forEach { cat ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelect(cat) }
                            .padding(vertical = 9.dp, horizontal = 8.dp)
                    ) {
                        Text(cat, color = theme.textBright, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } }
    )
}
