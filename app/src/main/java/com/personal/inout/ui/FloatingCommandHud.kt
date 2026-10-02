package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
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
import com.personal.inout.data.LedgerPocket
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.util.MathEvaluator
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingCommandHud(
    activePockets: List<LedgerPocket>,
    prefilledPocketId: Long? = null,
    prefilledNote: String = "",
    prefilledAmount: Double? = null,
    inDialogErrorMessage: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (
        nature: MovementNature,
        sourceId: Long,
        targetId: Long?,
        amount: Double,
        cat: String,
        note: String,
        date: Long,
        isRec: Boolean,
        freq: String
    ) -> Unit
) {
    val theme = LocalThemeColors.current
    val scrollState = rememberScrollState()

    var primaryNature by remember { mutableStateOf(MovementNature.OPERATING_EXPENSE) }
    var expression by remember { mutableStateOf(prefilledAmount?.let { String.format("%.2f", it) } ?: "") }
    var note by remember { mutableStateOf(prefilledNote) }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showCustomCalendar by remember { mutableStateOf(false) }

    val liquidPockets = remember(activePockets) {
        activePockets.filter { it.type == PocketType.LIQUID || it.type == PocketType.PREPAID_WALLET }
    }
    val cardPockets = remember(activePockets) {
        activePockets.filter { it.type == PocketType.CREDIT_CARD }
    }
    val spendOptions = remember(liquidPockets, cardPockets) { liquidPockets + cardPockets }

    var selectedSpendPocket by remember(spendOptions, prefilledPocketId) {
        mutableStateOf(spendOptions.firstOrNull { it.id == prefilledPocketId } ?: spendOptions.firstOrNull())
    }

    var selectedTransferTarget by remember(liquidPockets, selectedSpendPocket) {
        mutableStateOf(liquidPockets.firstOrNull { it.id != selectedSpendPocket?.id })
    }

    val expenseCategories = listOf("Food & Dining", "Groceries", "Transport", "Shopping", "Bills", "Health", "Leisure", "General")
    val incomeCategories = listOf("Salary", "Freelance", "Investments", "Gifts", "Rental", "Refunds", "General")
    var selectedCategory by remember(primaryNature) {
        mutableStateOf(if (primaryNature == MovementNature.OPERATING_INCOME) "Salary" else "Food & Dining")
    }

    // 4-Pill Repeat Cadence: None, Daily, Weekly, Monthly
    var repeatCadence by remember { mutableStateOf("None") }
    val computedAmount = remember(expression) { MathEvaluator.evaluate(expression) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .border(1.dp, theme.borderLight, RoundedCornerShape(20.dp))
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                // Segmented Nature Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.surfaceAlt)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        MovementNature.OPERATING_EXPENSE to "Spent",
                        MovementNature.OPERATING_INCOME to "Received",
                        MovementNature.TRANSFER to "Transfer"
                    ).forEach { (nat, label) ->
                        val isSel = primaryNature == nat
                        val activeColor = when (nat) {
                            MovementNature.OPERATING_EXPENSE -> theme.mildRed
                            MovementNature.OPERATING_INCOME -> theme.mildGreen
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

                // Amount Input
                Column {
                    OutlinedTextField(
                        value = expression,
                        onValueChange = { expression = it },
                        placeholder = { Text("Amount in ₹ (e.g. 150+40)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = theme.surfaceAlt,
                            unfocusedContainerColor = theme.surfaceAlt,
                            focusedBorderColor = theme.accent,
                            unfocusedBorderColor = theme.borderLight,
                            focusedTextColor = theme.textBright,
                            unfocusedTextColor = theme.textBright
                        )
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

                // Account Selection
                if (primaryNature == MovementNature.TRANSFER) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillDropdown(
                            label = "From: ${selectedSpendPocket?.name ?: "Select"}",
                            modifier = Modifier.weight(1f),
                            items = liquidPockets.map { it.name },
                            onSelect = { name ->
                                val found = liquidPockets.firstOrNull { it.name == name }
                                selectedSpendPocket = found
                                selectedTransferTarget = liquidPockets.firstOrNull { it.id != found?.id }
                            },
                            theme = theme
                        )
                        PillDropdown(
                            label = "To: ${selectedTransferTarget?.name ?: "Select"}",
                            modifier = Modifier.weight(1f),
                            items = liquidPockets.filter { it.id != selectedSpendPocket?.id }.map { it.name },
                            onSelect = { name ->
                                selectedTransferTarget = liquidPockets.firstOrNull { it.name == name }
                            },
                            theme = theme
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val sourcePockets = if (primaryNature == MovementNature.OPERATING_EXPENSE) spendOptions else liquidPockets
                        PillDropdown(
                            label = "Account: ${selectedSpendPocket?.name ?: "Select"}",
                            modifier = Modifier.weight(1f),
                            items = sourcePockets.map { it.name },
                            onSelect = { name -> selectedSpendPocket = sourcePockets.firstOrNull { it.name == name } },
                            theme = theme
                        )
                        val activeList = if (primaryNature == MovementNature.OPERATING_INCOME) incomeCategories else expenseCategories
                        PillDropdown(
                            label = selectedCategory,
                            modifier = Modifier.weight(1f),
                            items = activeList,
                            onSelect = { selectedCategory = it },
                            theme = theme
                        )
                    }
                }

                // Narration Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Merchant / Narration") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = theme.surfaceAlt,
                        unfocusedContainerColor = theme.surfaceAlt,
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.borderLight,
                        focusedTextColor = theme.textBright,
                        unfocusedTextColor = theme.textBright
                    )
                )

                // Date Selector
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                        ) { showCustomCalendar = true }
                    }
                }

                // Repeat Cadence (Rule 8)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Repeat", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("None", "Daily", "Weekly", "Monthly").forEach { cadence ->
                            val isSel = repeatCadence == cadence
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) theme.accent else theme.surfaceAlt)
                                    .clickable { repeatCadence = cadence }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    cadence,
                                    color = if (isSel) theme.bg else theme.textBright,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // In-Dialog Error Banner
                if (inDialogErrorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.mildRed.copy(alpha = 0.2f))
                            .border(1.dp, theme.mildRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = theme.mildRed, modifier = Modifier.size(17.dp))
                        Text(
                            text = inDialogErrorMessage,
                            color = theme.mildRed,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Commit Button (Rule 10 Account Binding Guard)
                Button(
                    onClick = {
                        val amt = computedAmount ?: 0.0
                        val src = selectedSpendPocket ?: return@Button
                        if (amt > 0.0) {
                            val isRec = repeatCadence != "None"
                            val freq = if (isRec) repeatCadence.uppercase() else "NONE"
                            when (primaryNature) {
                                MovementNature.OPERATING_EXPENSE -> {
                                    onSubmit(MovementNature.OPERATING_EXPENSE, src.id, null, amt, selectedCategory, note, selectedDateMillis, isRec, freq)
                                }
                                MovementNature.OPERATING_INCOME -> {
                                    onSubmit(MovementNature.OPERATING_INCOME, src.id, null, amt, selectedCategory, note, selectedDateMillis, isRec, freq)
                                }
                                MovementNature.TRANSFER -> {
                                    val tgt = selectedTransferTarget
                                    if (tgt != null && src.id != tgt.id) {
                                        onSubmit(MovementNature.TRANSFER, src.id, tgt.id, amt, "Transfer", note, selectedDateMillis, false, "NONE")
                                    }
                                }
                                else -> Unit
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("Commit to Ledger", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }
            }
        }
    }

    if (showCustomCalendar) {
        CustomCalendarDialog(
            initialDateMillis = selectedDateMillis,
            onDismiss = { showCustomCalendar = false },
            onDateSelected = {
                selectedDateMillis = it
                showCustomCalendar = false
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
                .border(1.dp, theme.borderLight, RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(label, color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
