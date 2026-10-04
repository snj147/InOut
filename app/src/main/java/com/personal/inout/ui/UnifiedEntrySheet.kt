package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.inout.data.LedgerPocket
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.ui.components.CompactInputField
import com.personal.inout.ui.components.CustomCalendarDialog
import com.personal.inout.util.MathEvaluator
import java.text.SimpleDateFormat
import java.util.*

enum class ActiveEntryRail(val label: String, val nature: MovementNature) {
    EXPENSE("EXP", MovementNature.OPERATING_EXPENSE),
    INFLOW("IN", MovementNature.OPERATING_INCOME),
    TRANSFER("XFER", MovementNature.TRANSFER),
    CARD_BILL("CARD", MovementNature.OPERATING_EXPENSE),
    PEER("PEER", MovementNature.TRANSFER)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedEntrySheet(
    allPockets: List<LedgerPocket>,
    prefilledPocketId: Long? = null,
    prefilledTargetPocketId: Long? = null,
    forcedRail: ActiveEntryRail? = null,
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

    var selectedRail by remember { mutableStateOf(forcedRail ?: ActiveEntryRail.EXPENSE) }
    var amountExpression by remember { mutableStateOf(prefilledAmount?.let { String.format("%.0f", it) } ?: "") }
    var note by remember { mutableStateOf(prefilledNote) }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var isTaxDeductible by remember { mutableStateOf(false) }
    var isReimbursable by remember { mutableStateOf(false) }
    var selectedCadence by remember { mutableStateOf("None") }

    val categories = when (selectedRail) {
        ActiveEntryRail.EXPENSE -> listOf("Food & Dining", "Groceries", "Transport", "Bills", "Shopping", "Health", "General")
        ActiveEntryRail.INFLOW -> listOf("Salary", "Freelance", "Investment", "Cashback", "Reward Credit", "Merchant Refund", "Income")
        ActiveEntryRail.TRANSFER -> listOf("Internal Transfer", "Loan Repayment", "Goal Pot", "ATM Withdrawal")
        ActiveEntryRail.CARD_BILL -> listOf("Card Payment", "Bill Settlement")
        ActiveEntryRail.PEER -> listOf("Peer Advance", "Debt Settlement")
    }
    var selectedCategory by remember(selectedRail) { mutableStateOf(categories.first()) }

    val isCreditCardRefundCategory = selectedCategory in listOf("Cashback", "Reward Credit", "Merchant Refund")

    val selectableSourcePockets = remember(allPockets, selectedRail, isCreditCardRefundCategory) {
        when (selectedRail) {
            ActiveEntryRail.INFLOW -> {
                allPockets.filter {
                    if (isCreditCardRefundCategory) it.type in listOf(PocketType.LIQUID, PocketType.PREPAID_WALLET, PocketType.CREDIT_CARD)
                    else it.type in listOf(PocketType.LIQUID, PocketType.PREPAID_WALLET)
                }
            }
            ActiveEntryRail.CARD_BILL -> allPockets.filter { it.type in listOf(PocketType.LIQUID, PocketType.PREPAID_WALLET) }
            ActiveEntryRail.EXPENSE -> allPockets.filter { it.type in listOf(PocketType.LIQUID, PocketType.PREPAID_WALLET, PocketType.CREDIT_CARD) }
            ActiveEntryRail.TRANSFER, ActiveEntryRail.PEER -> allPockets.filter { it.type in listOf(PocketType.LIQUID, PocketType.PREPAID_WALLET) }
        }
    }

    var selectedSourcePocket by remember(selectableSourcePockets, prefilledPocketId) {
        mutableStateOf(
            selectableSourcePockets.firstOrNull { it.id == prefilledPocketId }
                ?: selectableSourcePockets.firstOrNull { it.type == PocketType.LIQUID }
                ?: selectableSourcePockets.firstOrNull()
        )
    }

    LaunchedEffect(selectableSourcePockets) {
        if (selectedSourcePocket !in selectableSourcePockets) {
            selectedSourcePocket = selectableSourcePockets.firstOrNull()
        }
    }

    val selectableTargetPockets = remember(allPockets, selectedRail, selectedSourcePocket) {
        when (selectedRail) {
            ActiveEntryRail.CARD_BILL -> allPockets.filter { it.type == PocketType.CREDIT_CARD }
            ActiveEntryRail.PEER -> allPockets.filter { it.type in listOf(PocketType.PEER_RECEIVABLE, PocketType.PEER_PAYABLE) }
            ActiveEntryRail.TRANSFER -> allPockets.filter { it.id != selectedSourcePocket?.id && it.type in listOf(PocketType.LIQUID, PocketType.PREPAID_WALLET, PocketType.LIABILITY_LOAN, PocketType.GOAL_POT) }
            else -> emptyList()
        }
    }

    var selectedTargetPocket by remember(selectableTargetPockets, prefilledTargetPocketId) {
        mutableStateOf(
            selectableTargetPockets.firstOrNull { it.id == prefilledTargetPocketId }
                ?: selectableTargetPockets.firstOrNull()
        )
    }

    LaunchedEffect(selectableTargetPockets) {
        if (selectedTargetPocket !in selectableTargetPockets) {
            selectedTargetPocket = selectableTargetPockets.firstOrNull()
        }
    }

    val isCardInflow = selectedRail == ActiveEntryRail.INFLOW && selectedSourcePocket?.type == PocketType.CREDIT_CARD
    LaunchedEffect(isCardInflow) {
        if (isCardInflow) selectedCadence = "None"
    }

    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }

    val evaluatedAmount = remember(amountExpression) {
        MathEvaluator.evaluate(amountExpression)
    }

    val dateFormatted = remember(selectedDateMillis) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
    }

    if (showDatePicker) {
        CustomCalendarDialog(
            initialDateMillis = selectedDateMillis,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                selectedDateMillis = it
                showDatePicker = false
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 420.dp)
                    .border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Text(
                        text = "NEW TRANSACTION",
                        color = theme.textBright,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ActiveEntryRail.values().forEach { rail ->
                            val isSel = selectedRail == rail
                            val activeBg = when (rail) {
                                ActiveEntryRail.EXPENSE -> theme.mildRed
                                ActiveEntryRail.INFLOW -> theme.mildGreen
                                ActiveEntryRail.TRANSFER -> Color(0xFF6B8AFD)
                                ActiveEntryRail.CARD_BILL -> Color(0xFFE5A93C)
                                ActiveEntryRail.PEER -> Color(0xFF38B2AC)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) activeBg else Color.Transparent)
                                    .clickable { selectedRail = rail }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = rail.label,
                                    color = if (isSel) Color.Black else theme.textMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactInputField(
                            value = amountExpression,
                            onValueChange = { input -> if (input.all { c -> c.isDigit() || c in "+-*/." }) amountExpression = input },
                            placeholder = "Amount ₹",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.surfaceAlt)
                                .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                                .clickable { showDatePicker = true }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(dateFormatted, color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    if (computedAmount != null && amountExpression.any { c -> "+-*/".contains(c) }) {
                        val colorEval = if (computedAmount == 0.0) theme.textBright else theme.accent
                        Text("= ₹ ${String.format("%.2f", computedAmount)}", color = colorEval, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp))
                    }

                    if (selectedRail in listOf(ActiveEntryRail.TRANSFER, ActiveEntryRail.CARD_BILL, ActiveEntryRail.PEER)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectorPillTile(label = "From: ${selectedSourcePocket?.name ?: "Select"}", modifier = Modifier.weight(1f), theme = theme) { showSourcePicker = true }
                            SelectorPillTile(label = "To: ${selectedTargetPocket?.name ?: "Select"}", modifier = Modifier.weight(1f), theme = theme) { showTargetPicker = true }
                        }
                    } else {
                        SelectorPillTile(
                            label = if (selectedRail == ActiveEntryRail.INFLOW) "Deposit To: ${selectedSourcePocket?.name ?: "Tap to Select"}" else "Paid From: ${selectedSourcePocket?.name ?: "Tap to Select"}",
                            modifier = Modifier.fillMaxWidth(),
                            theme = theme
                        ) { showSourcePicker = true }
                    }

                    Text("CATEGORY", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSel = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) theme.accent.copy(alpha = 0.22f) else theme.surfaceAlt)
                                    .border(1.dp, if (isSel) theme.accent else theme.borderLight, RoundedCornerShape(6.dp))
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                            ) {
                                Text(cat, color = if (isSel) theme.accent else theme.textBright, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }

                    CompactInputField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = "Narration / Merchant (Optional)",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("REPEAT CADENCE", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            if (isCardInflow) Text("Card refund/reward must be one-time", color = theme.mildRed, fontSize = 9.5.sp)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("None" to "None", "DAILY" to "D", "WEEKLY" to "W", "MONTHLY" to "M", "YEARLY" to "Y").forEach { (cadenceKey, cadenceLabel) ->
                                val isSel = selectedCadence == cadenceKey
                                val isOptionEnabled = !isCardInflow || cadenceKey == "None"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) theme.accent else if (isOptionEnabled) theme.surfaceAlt else theme.surfaceAlt.copy(alpha = 0.4f))
                                        .border(1.dp, if (isSel) theme.accent else theme.borderLight, RoundedCornerShape(6.dp))
                                        .clickable(enabled = isOptionEnabled) { selectedCadence = cadenceKey }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(cadenceLabel, color = if (isSel) theme.bg else if (isOptionEnabled) theme.textBright else theme.textMuted.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isTaxDeductible) theme.accent.copy(alpha = 0.2f) else theme.surfaceAlt)
                                .border(1.dp, if (isTaxDeductible) theme.accent else theme.borderLight, RoundedCornerShape(6.dp))
                                .clickable { isTaxDeductible = !isTaxDeductible }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🏷 80C Tag", color = if (isTaxDeductible) theme.accent else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isReimbursable) theme.mildGreen.copy(alpha = 0.2f) else theme.surfaceAlt)
                                .border(1.dp, if (isReimbursable) theme.mildGreen else theme.borderLight, RoundedCornerShape(6.dp))
                                .clickable { isReimbursable = !isReimbursable }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💼 Reimbursable", color = if (isReimbursable) theme.mildGreen else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    val hasAccounts = selectableSourcePockets.isNotEmpty()
                    val isValid = evaluatedAmount != null && evaluatedAmount > 0.0 && selectedSourcePocket != null && (!listOf(ActiveEntryRail.TRANSFER, ActiveEntryRail.CARD_BILL, ActiveEntryRail.PEER).contains(selectedRail) || selectedTargetPocket != null)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!hasAccounts) { onNavigateToCreatePocket(); return@Button }
                                val src = selectedSourcePocket ?: return@Button
                                val amt = evaluatedAmount ?: return@Button
                                val movementNature = when (selectedRail) {
                                    ActiveEntryRail.EXPENSE -> MovementNature.OPERATING_EXPENSE
                                    ActiveEntryRail.INFLOW -> MovementNature.OPERATING_INCOME
                                    ActiveEntryRail.TRANSFER -> MovementNature.TRANSFER
                                    ActiveEntryRail.CARD_BILL -> MovementNature.OPERATING_EXPENSE
                                    ActiveEntryRail.PEER -> MovementNature.TRANSFER
                                }
                                val targetId = if (selectedRail in listOf(ActiveEntryRail.TRANSFER, ActiveEntryRail.CARD_BILL, ActiveEntryRail.PEER)) selectedTargetPocket?.id else null
                                onSubmitTransaction(movementNature, src.id, targetId, amt, selectedCategory, note.ifBlank { selectedCategory }, selectedDateMillis, selectedCadence != "None", if (selectedCadence == "None") "NONE" else selectedCadence, isTaxDeductible, isReimbursable)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1.3f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid && hasAccounts) theme.accent else theme.borderLight),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isValid && hasAccounts) theme.accent else Color.Transparent,
                                disabledContainerColor = Color.Transparent
                            ),
                            enabled = hasAccounts && isValid
                        ) {
                            Text(if (hasAccounts) "Post Entry" else "Add Bank First", color = if (isValid && hasAccounts) theme.bg else theme.textMuted.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textMuted)
                        ) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showSourcePicker) CenteredPocketPickerDialog("Select Account", selectableSourcePockets, theme, { showSourcePicker = false }) { selectedSourcePocket = it; showSourcePicker = false }
    if (showTargetPicker) CenteredPocketPickerDialog("Select Destination", selectableTargetPockets, theme, { showTargetPicker = false }) { selectedTargetPocket = it; showTargetPicker = false }
}

@Composable
private fun SelectorPillTile(label: String, modifier: Modifier, theme: ThemeColors, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text = label, color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun CenteredPocketPickerDialog(title: String, pockets: List<LedgerPocket>, theme: ThemeColors, onDismiss: () -> Unit, onSelect: (LedgerPocket) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }, contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = theme.surface), modifier = Modifier.fillMaxWidth(0.88f).widthIn(max = 380.dp).border(1.dp, theme.borderLight, RoundedCornerShape(14.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = title, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        pockets.forEach { pocket ->
                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).clickable { onSelect(pocket) }.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(pocket.name, color = theme.textBright, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                Text(pocket.type.name.replace("_", " "), color = theme.textMuted, fontSize = 10.sp)
                            }
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel", color = theme.textMuted, fontSize = 12.sp) }
                }
            }
        }
    }
}
