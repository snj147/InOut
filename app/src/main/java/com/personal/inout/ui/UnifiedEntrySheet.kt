package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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

    var selectedRail by remember { mutableStateOf(ActiveEntryRail.EXPENSE) }
    var amountExpression by remember { mutableStateOf(prefilledAmount?.let { String.format("%.0f", it) } ?: "") }
    var note by remember { mutableStateOf(prefilledNote) }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var isTaxDeductible by remember { mutableStateOf(false) }
    var isReimbursable by remember { mutableStateOf(false) }
    var selectedCadence by remember { mutableStateOf("None") }

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

    val categories = when (selectedRail) {
        ActiveEntryRail.EXPENSE -> listOf("Food & Dining", "Groceries", "Transport", "Bills", "Shopping", "Health", "General")
        ActiveEntryRail.INFLOW -> listOf("Salary", "Investment", "Freelance", "Refund", "Income")
        ActiveEntryRail.TRANSFER -> listOf("Internal Transfer", "Goal Pot", "ATM Withdrawal")
        ActiveEntryRail.CARD_BILL -> listOf("Card Payment", "Bill Settlement")
        ActiveEntryRail.PEER -> listOf("Peer Advance", "Debt Settlement")
    }
    var selectedCategory by remember(selectedRail) { mutableStateOf(categories.first()) }

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
                .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 420.dp)
                    .border(1.dp, theme.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Text(
                        text = "NEW TRANSACTION",
                        color = theme.textBright,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // 5-Pill Color-Coded Mode Rail
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

                    // Symmetrical 50/50 Amount and Date Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = amountExpression,
                            onValueChange = { amountExpression = it },
                            placeholder = { Text("Amount ₹", color = theme.textMuted, fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = theme.surfaceAlt,
                                unfocusedContainerColor = theme.surfaceAlt,
                                focusedBorderColor = theme.accent,
                                unfocusedBorderColor = theme.borderLight,
                                focusedTextColor = theme.textBright,
                                unfocusedTextColor = theme.textBright
                            )
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.surfaceAlt)
                                .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                                .clickable { showDatePicker = true }
                                .padding(horizontal = 12.dp, vertical = 15.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                                Text(dateFormatted, color = theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (evaluatedAmount != null && amountExpression.any { it in "+-*/" }) {
                        Text(
                            "= ₹ ${String.format("%.0f", evaluatedAmount)}",
                            color = theme.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }

                    // Account Selection
                    if (selectedRail in listOf(ActiveEntryRail.TRANSFER, ActiveEntryRail.CARD_BILL, ActiveEntryRail.PEER)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectorPillTile(
                                label = "From: ${selectedSourcePocket?.name ?: "Select"}",
                                modifier = Modifier.weight(1f),
                                theme = theme,
                                onClick = { showSourcePicker = true }
                            )
                            SelectorPillTile(
                                label = "To: ${selectedTargetPocket?.name ?: "Select"}",
                                modifier = Modifier.weight(1f),
                                theme = theme,
                                onClick = { showTargetPicker = true }
                            )
                        }
                    } else {
                        SelectorPillTile(
                            label = "Account: ${selectedSourcePocket?.name ?: "Tap to Select"}",
                            modifier = Modifier.fillMaxWidth(),
                            theme = theme,
                            onClick = { showSourcePicker = true }
                        )
                    }

                    // Vibrant Category Allocation Chips
                    Text("CATEGORY", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
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
                                Text(
                                    cat,
                                    color = if (isSel) theme.accent else theme.textBright,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Narration / Merchant (Fully Interactive)
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text("Narration / Merchant (Optional)", color = theme.textMuted, fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = theme.surfaceAlt,
                            unfocusedContainerColor = theme.surfaceAlt,
                            focusedBorderColor = theme.accent,
                            unfocusedBorderColor = theme.borderLight,
                            focusedTextColor = theme.textBright,
                            unfocusedTextColor = theme.textBright
                        )
                    )

                    // Repeat Cadence (Symmetrical Full-Width 5-Pill Rail)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("REPEAT CADENCE", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "None" to "None",
                                "DAILY" to "D",
                                "WEEKLY" to "W",
                                "MONTHLY" to "M",
                                "YEARLY" to "Y"
                            ).forEach { (cadenceKey, cadenceLabel) ->
                                val isSel = selectedCadence == cadenceKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) theme.accent else theme.surfaceAlt)
                                        .border(1.dp, if (isSel) theme.accent else theme.borderLight, RoundedCornerShape(6.dp))
                                        .clickable { selectedCadence = cadenceKey }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cadenceLabel,
                                        color = if (isSel) theme.bg else theme.textBright,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Statutory Tags (Equal 50/50 Proportion)
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
                            Text(
                                text = "🏷 80C Tag",
                                color = if (isTaxDeductible) theme.accent else theme.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                            Text(
                                text = "💼 Reimbursable",
                                color = if (isReimbursable) theme.mildGreen else theme.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Action Controls
                    val hasAccounts = allPockets.isNotEmpty()
                    val isValid = evaluatedAmount != null && evaluatedAmount > 0.0 && selectedSourcePocket != null

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)
                        ) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (!hasAccounts) {
                                    onNavigateToCreatePocket()
                                    return@Button
                                }
                                val src = selectedSourcePocket ?: return@Button
                                val amt = evaluatedAmount ?: return@Button

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
                                    amt,
                                    selectedCategory,
                                    note.ifBlank { selectedCategory },
                                    selectedDateMillis,
                                    selectedCadence != "None",
                                    if (selectedCadence == "None") "NONE" else selectedCadence,
                                    isTaxDeductible,
                                    isReimbursable
                                )
                                onDismiss()
                            },
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isValid && hasAccounts) theme.accent else theme.surfaceAlt
                            ),
                            enabled = hasAccounts && isValid
                        ) {
                            Text(
                                text = if (hasAccounts) "Post Entry" else "Add Account",
                                color = if (isValid && hasAccounts) theme.bg else theme.textMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSourcePicker) {
        CenteredPocketPickerDialog(
            title = "Select Source Account",
            pockets = liquidPockets,
            theme = theme,
            onDismiss = { showSourcePicker = false },
            onSelect = {
                selectedSourcePocket = it
                showSourcePicker = false
            }
        )
    }

    if (showTargetPicker) {
        CenteredPocketPickerDialog(
            title = "Select Destination Account",
            pockets = allPockets.filter { it.id != selectedSourcePocket?.id },
            theme = theme,
            onDismiss = { showTargetPicker = false },
            onSelect = {
                selectedTargetPocket = it
                showTargetPicker = false
            }
        )
    }
}

@Composable
private fun SelectorPillTile(
    label: String,
    modifier: Modifier,
    theme: ThemeColors,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(theme.surfaceAlt)
            .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            color = theme.textBright,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun CenteredPocketPickerDialog(
    title: String,
    pockets: List<LedgerPocket>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSelect: (LedgerPocket) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 380.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = title, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pockets.forEach { pocket ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.surfaceAlt)
                                    .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                                    .clickable { onSelect(pocket) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pocket.name, color = theme.textBright, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                Text(pocket.type.name, color = theme.textMuted, fontSize = 10.sp)
                            }
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
