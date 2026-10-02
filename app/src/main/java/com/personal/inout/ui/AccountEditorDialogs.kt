package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.personal.inout.data.PocketType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateAccountDialog(
    initialType: PocketType = PocketType.LIQUID,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        type: PocketType,
        limit: Double,
        dueDay: Int,
        targetAmt: Double,
        targetDateEpoch: Long,
        initialValuation: Double,
        interestRate: Double
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(initialType) }
    var limit by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }
    var targetAmt by remember { mutableStateOf("") }
    var initialValuation by remember { mutableStateOf("") }
    var interestRate by remember { mutableStateOf("") }
    var targetDateEpoch by remember { mutableStateOf(System.currentTimeMillis() + (30L * 24 * 3600 * 1000L)) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(targetDateEpoch))

    if (showDatePicker) {
        CustomCalendarDialog(
            initialDateMillis = targetDateEpoch,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                targetDateEpoch = it
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
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "NEW ACCOUNT SETUP", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple(PocketType.LIQUID, "Bank", Color(0xFF48BB78)),
                                Triple(PocketType.PREPAID_WALLET, "Wallet", Color(0xFF38A169)),
                                Triple(PocketType.CREDIT_CARD, "Card", Color(0xFFF56565)),
                                Triple(PocketType.GOAL_POT, "Goal", Color(0xFFECC94B))
                            ).forEach { (t, lbl, c) ->
                                val isSel = type == t
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) c.copy(alpha = 0.25f) else theme.surfaceAlt)
                                        .border(1.dp, if (isSel) c else theme.borderLight, RoundedCornerShape(6.dp))
                                        .clickable { type = t }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(lbl, color = if (isSel) c else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple(PocketType.INVESTMENT, "Invest", Color(0xFFED8936)),
                                Triple(PocketType.PEER_RECEIVABLE, "Peer", Color(0xFF4FD1C5)),
                                Triple(PocketType.LIABILITY_LOAN, "Loan", Color(0xFFE53E3E))
                            ).forEach { (t, lbl, c) ->
                                val isSel = type == t
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) c.copy(alpha = 0.25f) else theme.surfaceAlt)
                                        .border(1.dp, if (isSel) c else theme.borderLight, RoundedCornerShape(6.dp))
                                        .clickable { type = t }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(lbl, color = if (isSel) c else theme.textBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Account Name (e.g. HDFC Salary, SBI Home Loan)", fontSize = 12.sp) },
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

                    when (type) {
                        PocketType.LIQUID, PocketType.PREPAID_WALLET -> {
                            OutlinedTextField(
                                value = initialValuation,
                                onValueChange = { initialValuation = it },
                                placeholder = { Text("Current Opening Balance in ₹", fontSize = 12.sp) },
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
                        }

                        PocketType.CREDIT_CARD -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = limit,
                                    onValueChange = { limit = it },
                                    placeholder = { Text("Limit ₹", fontSize = 12.sp) },
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
                                OutlinedTextField(
                                    value = dueDay,
                                    onValueChange = { dueDay = it },
                                    placeholder = { Text("Due Day (1-31)", fontSize = 12.sp) },
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
                            }
                        }

                        PocketType.GOAL_POT -> {
                            OutlinedTextField(
                                value = targetAmt,
                                onValueChange = { targetAmt = it },
                                placeholder = { Text("Target Goal Amount in ₹", fontSize = 12.sp) },
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.surfaceAlt)
                                    .clickable { showDatePicker = true }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Target Date: $dateFormatted", color = theme.textBright, fontSize = 12.sp)
                                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                            }
                        }

                        PocketType.INVESTMENT, PocketType.FIXED_ASSET -> {
                            OutlinedTextField(
                                value = initialValuation,
                                onValueChange = { initialValuation = it },
                                placeholder = { Text("Current Portfolio / Valuation in ₹", fontSize = 12.sp) },
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
                        }

                        PocketType.PEER_RECEIVABLE, PocketType.PEER_PAYABLE -> {
                            OutlinedTextField(
                                value = initialValuation,
                                onValueChange = { initialValuation = it },
                                placeholder = { Text("Initial Amount Lent / Borrowed in ₹", fontSize = 12.sp) },
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
                        }

                        PocketType.LIABILITY_LOAN -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = initialValuation,
                                        onValueChange = { initialValuation = it },
                                        placeholder = { Text("Principal Debt ₹", fontSize = 12.sp) },
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
                                    OutlinedTextField(
                                        value = targetAmt,
                                        onValueChange = { targetAmt = it },
                                        placeholder = { Text("Monthly EMI ₹", fontSize = 12.sp) },
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
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = dueDay,
                                        onValueChange = { dueDay = it },
                                        placeholder = { Text("Due Day (1-31)", fontSize = 12.sp) },
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
                                    OutlinedTextField(
                                        value = interestRate,
                                        onValueChange = { interestRate = it },
                                        placeholder = { Text("Interest Rate %", fontSize = 12.sp) },
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
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (name.isNotBlank()) onSave(
                                    name,
                                    type,
                                    limit.toDoubleOrNull() ?: 0.0,
                                    dueDay.toIntOrNull() ?: 0,
                                    targetAmt.toDoubleOrNull() ?: 0.0,
                                    targetDateEpoch,
                                    initialValuation.toDoubleOrNull() ?: 0.0,
                                    interestRate.toDoubleOrNull() ?: 0.0
                                )
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                            enabled = name.isNotBlank()
                        ) {
                            Text("Save Account", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditAccountDialog(
    pocket: LedgerPocket,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onSave: (String, Double, Int, Double, Long) -> Unit
) {
    var name by remember { mutableStateOf(pocket.name) }
    var limit by remember { mutableStateOf(if (pocket.creditLimit > 0.0) String.format("%.0f", pocket.creditLimit) else "") }
    var dueDay by remember { mutableStateOf(if (pocket.billDueDay > 0) pocket.billDueDay.toString() else "") }
    var targetAmt by remember { mutableStateOf(if (pocket.targetGoalAmount > 0.0) String.format("%.0f", pocket.targetGoalAmount) else "") }
    var targetDateEpoch by remember { mutableStateOf(if (pocket.goalTargetDate > 0) pocket.goalTargetDate else System.currentTimeMillis() + (30L * 24 * 3600 * 1000L)) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(targetDateEpoch))

    if (showDatePicker) {
        CustomCalendarDialog(
            initialDateMillis = targetDateEpoch,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                targetDateEpoch = it
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
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 400.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "EDIT ${pocket.name.uppercase(Locale.getDefault())}", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Account Name") },
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

                    if (pocket.type == PocketType.CREDIT_CARD) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = limit,
                                onValueChange = { limit = it },
                                placeholder = { Text("Credit Limit") },
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
                            OutlinedTextField(
                                value = dueDay,
                                onValueChange = { dueDay = it },
                                placeholder = { Text("Due Day (1-31)") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(0.8f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = theme.surfaceAlt,
                                    unfocusedContainerColor = theme.surfaceAlt,
                                    focusedBorderColor = theme.accent,
                                    unfocusedBorderColor = theme.borderLight,
                                    focusedTextColor = theme.textBright,
                                    unfocusedTextColor = theme.textBright
                                )
                            )
                        }
                    }

                    if (pocket.type == PocketType.GOAL_POT) {
                        OutlinedTextField(
                            value = targetAmt,
                            onValueChange = { targetAmt = it },
                            placeholder = { Text("Target Goal Amount") },
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
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel", color = theme.textMuted)
                        }

                        Button(
                            onClick = {
                                if (name.isNotBlank()) onSave(
                                    name,
                                    limit.toDoubleOrNull() ?: pocket.creditLimit,
                                    dueDay.toIntOrNull() ?: pocket.billDueDay,
                                    targetAmt.toDoubleOrNull() ?: pocket.targetGoalAmount,
                                    targetDateEpoch
                                )
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                        ) {
                            Text("Save Changes", color = theme.bg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
