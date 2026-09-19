package com.personal.inout.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.data.VaultPocket
import com.personal.inout.util.MathEvaluator
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingCommandHud(
    activePockets: List<VaultPocket>,
    prefilledPocketId: Long? = null,
    prefilledNote: String = "",
    prefilledAmount: Double? = null,
    onDismiss: () -> Unit,
    onSubmit: (nature: MovementNature, sourceId: Long?, targetId: Long?, amount: Double, cat: String, note: String, date: Long, isRec: Boolean, freq: String) -> Unit
) {
    val theme = LocalThemeColors.current
    val scrollState = rememberScrollState()

    var primaryNature by remember { mutableStateOf(MovementNature.OUTFLOW) }
    var expression by remember { mutableStateOf(prefilledAmount?.let { String.format("%.2f", it) } ?: "") }
    var note by remember { mutableStateOf(prefilledNote) }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showCustomDatePicker by remember { mutableStateOf(false) }

    val liquidPockets = remember(activePockets) { activePockets.filter { it.pocketType == PocketType.LIQUID } }
    val creditPockets = remember(activePockets) { activePockets.filter { it.pocketType == PocketType.CREDIT_LINE } }
    val spendOptions = remember(liquidPockets, creditPockets) { liquidPockets + creditPockets }

    var selectedSpendPocket by remember(spendOptions, prefilledPocketId) {
        mutableStateOf(spendOptions.firstOrNull { it.id == prefilledPocketId } ?: spendOptions.firstOrNull())
    }

    // Safeguard: Filter out identical source pocket for transfers
    val validTransferTargets = remember(liquidPockets, selectedSpendPocket) {
        liquidPockets.filter { it.id != selectedSpendPocket?.id }
    }
    var selectedTransferTarget by remember(validTransferTargets) {
        mutableStateOf(validTransferTargets.firstOrNull())
    }

    // Context-Aware Category Sets
    val expenseCategories = listOf("Food & Dining", "Groceries", "Transport", "Shopping", "Bills", "Health", "Leisure", "General")
    val incomeCategories = listOf("Salary", "Freelance", "Investments", "Gifts", "Rental", "Refunds", "General")
    var selectedCategory by remember(primaryNature) {
        mutableStateOf(if (primaryNature == MovementNature.INFLOW) "Salary" else "Food & Dining")
    }

    var isRecurring by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var showMoreOptions by remember { mutableStateOf(false) }

    val computedAmount = remember(expression) { MathEvaluator.evaluate(expression) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Flow Nature Pill Selector (Spent / Received / Transfer)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.surfaceAlt)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        MovementNature.OUTFLOW to "Spent",
                        MovementNature.INFLOW to "Received",
                        MovementNature.TRANSFER to "Transfer"
                    ).forEach { (nat, label) ->
                        val isSel = primaryNature == nat
                        val activeColor = when (nat) {
                            MovementNature.OUTFLOW -> theme.mildRed
                            MovementNature.INFLOW -> theme.mildGreen
                            else -> theme.accent
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) activeColor else Color.Transparent)
                                .clickable { primaryNature = nat }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (isSel) theme.bg else theme.textMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Amount Field
                Column {
                    CompactInputField(
                        value = expression,
                        onValueChange = { expression = it },
                        placeholder = "Amount (e.g. 150+40)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (computedAmount != null && expression.any { it in "+-*/" }) {
                        Text(
                            "= ₹ ${String.format("%.2f", computedAmount)}",
                            color = theme.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                        )
                    }
                }

                // Accounts & Category Pickers
                if (primaryNature == MovementNature.TRANSFER) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillDropdown(
                            label = "From: ${selectedSpendPocket?.name ?: "Select"}",
                            modifier = Modifier.weight(1f),
                            items = liquidPockets.map { it.name },
                            onSelect = { name ->
                                selectedSpendPocket = liquidPockets.firstOrNull { it.name == name }
                                selectedTransferTarget = liquidPockets.firstOrNull { it.id != selectedSpendPocket?.id }
                            },
                            theme = theme
                        )
                        PillDropdown(
                            label = "To: ${selectedTransferTarget?.name ?: "None"}",
                            modifier = Modifier.weight(1f),
                            items = validTransferTargets.map { it.name },
                            onSelect = { name -> selectedTransferTarget = validTransferTargets.firstOrNull { it.name == name } },
                            theme = theme
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val sourcePockets = if (primaryNature == MovementNature.OUTFLOW) spendOptions else liquidPockets
                        PillDropdown(
                            label = "Account: ${selectedSpendPocket?.name ?: "Select"}",
                            modifier = Modifier.weight(1f),
                            items = sourcePockets.map { it.name },
                            onSelect = { name -> selectedSpendPocket = sourcePockets.firstOrNull { it.name == name } },
                            theme = theme
                        )
                        val activeCategoryList = if (primaryNature == MovementNature.INFLOW) incomeCategories else expenseCategories
                        PillDropdown(
                            label = selectedCategory,
                            modifier = Modifier.weight(1f),
                            items = activeCategoryList,
                            onSelect = { selectedCategory = it },
                            theme = theme
                        )
                    }
                }

                // Narration Note
                CompactInputField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "Merchant / Note (e.g. Starbucks, Client Payment)",
                    modifier = Modifier.fillMaxWidth()
                )

                // High-Contrast Date Quick-Select Strip
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Transaction Date", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val todayMillis = remember { System.currentTimeMillis() }
                        val yesterdayMillis = remember { todayMillis - (1000 * 60 * 60 * 24) }

                        val isToday = isSameDay(selectedDateMillis, todayMillis)
                        val isYesterday = isSameDay(selectedDateMillis, yesterdayMillis)
                        val isCustom = !isToday && !isYesterday

                        DateQuickPill("Today", isToday, theme, Modifier.weight(1f)) { selectedDateMillis = todayMillis }
                        DateQuickPill("Yesterday", isYesterday, theme, Modifier.weight(1f)) { selectedDateMillis = yesterdayMillis }
                        DateQuickPill(
                            if (isCustom) SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(selectedDateMillis)) else "Custom",
                            isCustom,
                            theme,
                            Modifier.weight(1f)
                        ) { showCustomDatePicker = true }
                    }
                }

                // Recurring Accordion
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMoreOptions = !showMoreOptions }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto Recurring & Notes", color = theme.textMuted, fontSize = 11.5.sp)
                    Text(if (showMoreOptions) "▲ Less" else "▼ More", color = theme.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                AnimatedVisibility(visible = showMoreOptions) {
                    if (primaryNature != MovementNature.TRANSFER) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Repeat Every Month", color = theme.textBright, fontSize = 12.sp)
                            Switch(
                                checked = isRecurring,
                                onCheckedChange = { isRecurring = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = theme.bg,
                                    checkedTrackColor = theme.accent
                                )
                            )
                        }
                    }
                }

                // Commit Button
                Button(
                    onClick = {
                        val amt = computedAmount ?: 0.0
                        if (amt > 0.0) {
                            when (primaryNature) {
                                MovementNature.OUTFLOW -> {
                                    selectedSpendPocket?.let {
                                        onSubmit(MovementNature.OUTFLOW, it.id, null, amt, selectedCategory, note, selectedDateMillis, isRecurring, frequency)
                                    }
                                }
                                MovementNature.INFLOW -> {
                                    selectedSpendPocket?.let {
                                        onSubmit(MovementNature.INFLOW, null, it.id, amt, selectedCategory, note, selectedDateMillis, isRecurring, frequency)
                                    }
                                }
                                MovementNature.TRANSFER -> {
                                    if (selectedSpendPocket != null && selectedTransferTarget != null && selectedSpendPocket!!.id != selectedTransferTarget!!.id) {
                                        onSubmit(MovementNature.TRANSFER, selectedSpendPocket!!.id, selectedTransferTarget!!.id, amt, "Transfer", note, selectedDateMillis, false, "NONE")
                                    }
                                }
                                else -> Unit
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Commit to Vault", color = theme.bg, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
        }
    }

    if (showCustomDatePicker) {
        ReadableCalendarDialog(
            initialDateMillis = selectedDateMillis,
            onDismiss = { showCustomDatePicker = false },
            onDateSelected = {
                selectedDateMillis = it
                showCustomDatePicker = false
            }
        )
    }
}

@Composable
private fun DateQuickPill(label: String, isSelected: Boolean, theme: ThemeColors, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) theme.accent else theme.surfaceAlt)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (isSelected) theme.bg else theme.textBright,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadableCalendarDialog(
    initialDateMillis: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val theme = LocalThemeColors.current
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { pickerState.selectedDateMillis?.let { onDateSelected(it) } }) {
                Text("Select", color = theme.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } },
        colors = DatePickerDefaults.colors(
            containerColor = theme.surface,
            titleContentColor = theme.textBright,
            headlineContentColor = theme.accent,
            weekdayContentColor = theme.accent,
            subheadContentColor = theme.textBright,
            yearContentColor = theme.textBright,
            currentYearContentColor = theme.accent,
            selectedYearContentColor = theme.bg,
            selectedYearContainerColor = theme.accent,
            dayContentColor = Color.White,
            selectedDayContentColor = theme.bg,
            selectedDayContainerColor = theme.accent,
            todayContentColor = theme.accent,
            todayDateBorderColor = theme.accent
        )
    ) {
        DatePicker(state = pickerState)
    }
}

private fun isSameDay(d1: Long, d2: Long): Boolean {
    val f = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return f.format(Date(d1)) == f.format(Date(d2))
}

@Composable
private fun PillDropdown(
    label: String,
    modifier: Modifier,
    items: List<String>,
    onSelect: (String) -> Unit,
    theme: ThemeColors
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(theme.surfaceAlt)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(label, color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(theme.surface)
        ) {
            items.forEach { itm ->
                DropdownMenuItem(
                    text = { Text(itm, color = theme.textBright, fontSize = 12.sp) },
                    onClick = {
                        onSelect(itm)
                        expanded = false
                    }
                )
            }
        }
    }
}
