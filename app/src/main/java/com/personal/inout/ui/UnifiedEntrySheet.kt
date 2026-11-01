package com.personal.inout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.LedgerPocket
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import com.personal.inout.ui.LocalThemeColors
import java.text.SimpleDateFormat
import java.util.*

enum class ActiveEntryRail(val title: String, val nature: MovementNature) {
    EXPENSE("EXP", MovementNature.OPERATING_EXPENSE),
    INCOME("IN", MovementNature.OPERATING_INCOME),
    TRANSFER("XFER", MovementNature.TRANSFER),
    CARD_BILL("CARD", MovementNature.TRANSFER),
    PEER_SETTLEMENT("PEER", MovementNature.TRANSFER)
}

// INLINE CALCULATOR ENGINE
fun evaluateBasicMath(expr: String): Double? {
    try {
        val sanitized = expr.replace(" ", "").replace(",", "")
        if (!sanitized.matches(Regex("^[\\d\\.\\+\\-\\*\\/]+$"))) return null
        var current = ""
        val tokens = mutableListOf<String>()
        for (char in sanitized) {
            if (char in listOf('+', '-', '*', '/')) {
                if (current.isNotEmpty()) tokens.add(current)
                tokens.add(char.toString())
                current = ""
            } else { current += char }
        }
        if (current.isNotEmpty()) tokens.add(current)
        
        var i = 0
        while (i < tokens.size) {
            if (tokens[i] == "*" || tokens[i] == "/") {
                val left = tokens[i-1].toDouble()
                val right = tokens[i+1].toDouble()
                val res = if (tokens[i] == "*") left * right else left / right
                tokens[i-1] = res.toString()
                tokens.removeAt(i)
                tokens.removeAt(i)
            } else { i++ }
        }
        i = 0
        while (i < tokens.size) {
            if (tokens[i] == "+" || tokens[i] == "-") {
                val left = tokens[i-1].toDouble()
                val right = tokens[i+1].toDouble()
                val res = if (tokens[i] == "+") left + right else left - right
                tokens[i-1] = res.toString()
                tokens.removeAt(i)
                tokens.removeAt(i)
            } else { i++ }
        }
        return tokens.firstOrNull()?.toDoubleOrNull()
    } catch (e: Exception) { return null }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        nature: MovementNature, sourcePocketId: Long, targetPocketId: Long?,
        amountDouble: Double, category: String, narration: String,
        timestamp: Long, isRecurring: Boolean, frequency: String,
        isTaxDeductible: Boolean, isReimbursable: Boolean
    ) -> Unit,
    onNavigateToCreatePocket: () -> Unit
) {
    val theme = LocalThemeColors.current

    val liquidPockets = remember(allPockets) { allPockets.filter { it.type == PocketType.LIQUID || it.type == PocketType.PREPAID_WALLET } }
    val creditCards = remember(allPockets) { allPockets.filter { it.type == PocketType.CREDIT_CARD } }
    val peerAccounts = remember(allPockets) { allPockets.filter { it.type == PocketType.PEER_PAYABLE || it.type == PocketType.PEER_RECEIVABLE } }
    val allDestinations = remember(allPockets) { allPockets.filter { it.type != PocketType.CREDIT_CARD && it.type != PocketType.PEER_PAYABLE && it.type != PocketType.PEER_RECEIVABLE } }

    var activeRail by remember { mutableStateOf(forcedRail ?: ActiveEntryRail.EXPENSE) }
    var rawAmountInput by remember { mutableStateOf(prefilledAmount?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
    var selectedSourceId by remember { mutableStateOf(prefilledPocketId ?: liquidPockets.firstOrNull()?.id) }
    var selectedTargetId by remember { mutableStateOf(prefilledTargetPocketId) }
    
    var showSourceDropdown by remember { mutableStateOf(false) }
    var showTargetDropdown by remember { mutableStateOf(false) }
    
    var narration by remember { mutableStateOf(prefilledNote) }
    var selectedDateEpoch by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var isTaxDeductible by remember { mutableStateOf(false) }
    var isReimbursable by remember { mutableStateOf(false) }
    var repeatCadence by remember { mutableStateOf("NONE") }

    val dateFormatted = remember(selectedDateEpoch) { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateEpoch)) }

    val categoryMatrix = remember(activeRail) {
        when (activeRail) {
            ActiveEntryRail.EXPENSE -> listOf("Food & Dining", "Groceries", "Transport", "Bills", "Shopping", "Health", "General")
            ActiveEntryRail.INCOME -> listOf("Salary", "Freelance", "Investment", "Cashback", "Reward Credit", "Merchant Refund", "Income")
            ActiveEntryRail.TRANSFER -> listOf("Internal Transfer", "Loan Repayment", "Goal Pot", "ATM Withdrawal")
            ActiveEntryRail.CARD_BILL -> listOf("Card Payment", "Bill Settlement")
            ActiveEntryRail.PEER_SETTLEMENT -> listOf("Peer Advance", "Debt Settlement")
        }
    }
    
    var activeCategory by remember(activeRail) { mutableStateOf(categoryMatrix.first()) }
    
    // Evaluate math inline
    val evaluatedAmount = evaluateBasicMath(rawAmountInput)
    val showEvaluatedResult = evaluatedAmount != null && rawAmountInput.any { it in listOf('+', '-', '*', '/') }

    if (showDatePicker) CustomCalendarDialog(initialDateMillis = selectedDateEpoch, onDismiss = { showDatePicker = false }) { selectedDateEpoch = it; showDatePicker = false }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.borderLight) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("NEW TRANSACTION", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)

            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ActiveEntryRail.values().forEach { rail ->
                    val isSel = activeRail == rail
                    val railColor = if (isSel) {
                        when (rail) { ActiveEntryRail.EXPENSE -> theme.mildRed; ActiveEntryRail.INCOME -> theme.mildGreen; ActiveEntryRail.CARD_BILL -> Color(0xFFF6AD55); ActiveEntryRail.PEER_SETTLEMENT -> Color(0xFF4FD1C5); else -> theme.accent }
                    } else Color.Transparent

                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(railColor).clickable { activeRail = rail; selectedTargetId = null }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                        Text(rail.title, color = if (isSel) theme.bg else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    CompactInputField(
                        value = rawAmountInput, 
                        onValueChange = { input -> if (input.all { c -> c.isDigit() || c in listOf('.', '+', '-', '*', '/') }) rawAmountInput = input }, 
                        placeholder = "Amount ₹", 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showEvaluatedResult && evaluatedAmount != null) {
                        Text("= ₹${String.format(Locale.US, "%.2f", evaluatedAmount)}", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }
                }
                Box(modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).clickable { showDatePicker = true }.padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(dateFormatted, color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).clickable { showSourceDropdown = true }.padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                    val srcName = allPockets.firstOrNull { it.id == selectedSourceId }?.name ?: "Tap to Select"
                    Text(if (activeRail == ActiveEntryRail.INCOME) "Deposit To: $srcName" else "Paid From: $srcName", color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    DropdownMenu(expanded = showSourceDropdown, onDismissRequest = { showSourceDropdown = false }, modifier = Modifier.background(theme.surface)) {
                        val validSources = if (activeRail == ActiveEntryRail.CARD_BILL) liquidPockets else allPockets
                        validSources.forEach { p -> DropdownMenuItem(text = { Text(p.name, color = theme.textBright) }, onClick = { selectedSourceId = p.id; showSourceDropdown = false }) }
                    }
                }
                
                if (activeRail in listOf(ActiveEntryRail.TRANSFER, ActiveEntryRail.CARD_BILL, ActiveEntryRail.PEER_SETTLEMENT)) {
                    Box(modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).border(1.dp, theme.borderLight, RoundedCornerShape(8.dp)).clickable { showTargetDropdown = true }.padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                        val tgtName = allPockets.firstOrNull { it.id == selectedTargetId }?.name ?: "Select"
                        Text("To: $tgtName", color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        DropdownMenu(expanded = showTargetDropdown, onDismissRequest = { showTargetDropdown = false }, modifier = Modifier.background(theme.surface)) {
                            val validTargets = when (activeRail) {
                                ActiveEntryRail.CARD_BILL -> creditCards
                                ActiveEntryRail.PEER_SETTLEMENT -> peerAccounts
                                else -> allDestinations
                            }
                            validTargets.forEach { p -> DropdownMenuItem(text = { Text(p.name, color = theme.textBright) }, onClick = { selectedTargetId = p.id; showTargetDropdown = false }) }
                        }
                    }
                }
            }

            Text("CATEGORY", color = theme.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            
            // STRICT 3x3 CATEGORY GRID
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categoryMatrix.chunked(3).forEach { rowCats ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowCats.forEach { cat ->
                            val isSel = activeCategory == cat
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.accent.copy(alpha = 0.22f) else theme.surfaceAlt).border(1.dp, if (isSel) theme.accent else theme.borderLight, RoundedCornerShape(6.dp)).clickable { activeCategory = cat }.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(cat, color = if (isSel) theme.accent else theme.textBright, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 1)
                            }
                        }
                        repeat(3 - rowCats.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }

            CompactInputField(value = narration, onValueChange = { narration = it }, placeholder = "Narration / Merchant (Optional)", modifier = Modifier.fillMaxWidth())

            Text("REPEAT CADENCE", color = theme.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("NONE" to "None", "DAILY" to "D", "WEEKLY" to "W", "MONTHLY" to "M", "YEARLY" to "Y").forEach { (key, label) ->
                    val isSel = repeatCadence == key
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isSel) theme.mildGreen.copy(alpha = 0.25f) else theme.surfaceAlt).border(1.dp, if (isSel) theme.mildGreen else theme.borderLight, RoundedCornerShape(6.dp)).clickable { repeatCadence = key }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = if (isSel) theme.mildGreen else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isTaxDeductible) theme.accent.copy(alpha = 0.2f) else theme.surfaceAlt).border(1.dp, if (isTaxDeductible) theme.accent else theme.borderLight, RoundedCornerShape(6.dp)).clickable { isTaxDeductible = !isTaxDeductible }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) { 
                    Text("🏷 80C Tag", color = if (isTaxDeductible) theme.accent else theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) 
                }
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (isReimbursable) theme.mildGreen.copy(alpha = 0.2f) else theme.surfaceAlt).border(1.dp, if (isReimbursable) theme.mildGreen else theme.borderLight, RoundedCornerShape(6.dp)).clickable { isReimbursable = !isReimbursable }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) { 
                    Text("💼 Reimbursable", color = if (isReimbursable) theme.mildGreen else theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) 
                }
            }

            val finalAmount = evaluatedAmount ?: rawAmountInput.toDoubleOrNull() ?: 0.0
            val isValid = finalAmount > 0.0 && selectedSourceId != null && (activeRail == ActiveEntryRail.EXPENSE || activeRail == ActiveEntryRail.INCOME || selectedTargetId != null)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (liquidPockets.isEmpty()) {
                    Button(onClick = onNavigateToCreatePocket, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt)) {
                        Text("Add Bank First", color = theme.textBright, fontSize = 12.sp)
                    }
                } else {
                    Button(onClick = { 
                        if (isValid) {
                            onSubmitTransaction(activeRail.nature, selectedSourceId!!, selectedTargetId, finalAmount, activeCategory, narration, selectedDateEpoch, repeatCadence != "NONE", repeatCadence, isTaxDeductible, isReimbursable)
                        } 
                    }, modifier = Modifier.weight(1.3f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight), colors = ButtonDefaults.buttonColors(containerColor = if (isValid) theme.accent else Color.Transparent, disabledContainerColor = Color.Transparent), enabled = isValid) {
                        Text("Post Entry", color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight), colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textMuted)) {
                    Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                }
            }
        }
    }
}
