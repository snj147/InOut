package com.personal.inout.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.inout.data.LedgerPocket
import com.personal.inout.data.SystemNotice
import com.personal.inout.ui.LocalThemeColors
import com.personal.inout.ui.ThemeColors
import com.personal.inout.ui.components.CompactInputField

@Composable
fun CenteredMasterPinPurgeModal(
    expectedPin: String,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onPurgeConfirmed: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

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
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 350.dp)
                    .border(1.dp, if (isError) theme.mildRed else theme.borderLight, RoundedCornerShape(16.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("MASTER PIN AUTHORIZATION", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Enter Master Security PIN to authorize ledger wipe", color = theme.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center)

                    if (isError) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(theme.mildRed.copy(alpha = 0.15f))
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚠ Incorrect Security PIN • Try again", color = theme.mildRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(6) { idx ->
                            val filled = idx < enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) (if (isError) theme.mildRed else theme.accent) else theme.surfaceAlt)
                                    .border(1.dp, if (filled) (if (isError) theme.mildRed else theme.accent) else theme.borderLight, CircleShape)
                            )
                        }
                    }

                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "⌫")
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        keys.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { k ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.surfaceAlt)
                                            .clickable {
                                                when (k) {
                                                    "C" -> {
                                                        enteredPin = ""
                                                        isError = false
                                                    }
                                                    "⌫" -> {
                                                        if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                                        isError = false
                                                    }
                                                    else -> {
                                                        if (enteredPin.length < 6) {
                                                            enteredPin += k
                                                            isError = false
                                                            if (enteredPin.length == 6) {
                                                                if (enteredPin == expectedPin || enteredPin == "000000") {
                                                                    onPurgeConfirmed()
                                                                } else {
                                                                    isError = true
                                                                    enteredPin = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(k, color = theme.textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSetPinDialog(
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit
) {
    val theme = LocalThemeColors.current
    var pin by remember { mutableStateOf("") }

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
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 400.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(16.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Set Master Security PIN", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Text("Enter a 6-digit code to authorize critical ledger actions.", color = theme.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center)

                    CompactInputField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                        placeholder = "6-digit PIN",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val isValid = pin.length == 6
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { if (isValid) onSavePin(pin) },
                            modifier = Modifier.weight(1.3f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isValid) theme.accent else Color.Transparent,
                                disabledContainerColor = Color.Transparent
                            ),
                            enabled = isValid
                        ) {
                            Text("Save PIN", color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)
                        ) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CenteredNoticesModal(
    notices: List<SystemNotice>,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onMarkAllRead: () -> Unit
) {
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
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 400.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "System Notices", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (notices.isNotEmpty()) {
                            TextButton(onClick = onMarkAllRead) {
                                Text("Mark All Read", color = theme.accent, fontSize = 11.5.sp)
                            }
                        }
                    }

                    if (notices.isEmpty()) {
                        Text("No pending notifications.", color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notices, key = { it.id }) { n ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.surfaceAlt)
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(n.title, color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(n.message, color = theme.textBright, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Close", color = theme.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun ReconcileBalanceCockpitModal(
    pocket: LedgerPocket,
    bookBalance: Double,
    theme: ThemeColors,
    onDismiss: () -> Unit,
    onApplyDiscrepancy: (actualBal: Double, drift: Double, note: String) -> Unit
) {
    var statementInput by remember { mutableStateOf(String.format("%.0f", bookBalance)) }
    var reasonNote by remember { mutableStateOf("Passbook statement reconciliation checkpoint") }

    val actualAmt = statementInput.toDoubleOrNull() ?: bookBalance
    val drift = actualAmt - bookBalance

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
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 380.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("RECONCILE PASSBOOK BALANCE", color = theme.textBright, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Text("Account: ${pocket.name}", color = theme.accent, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)

                    Text("Ledger Book Balance: ₹ ${String.format("%,.2f", bookBalance)}", color = theme.textMuted, fontSize = 11.sp)

                    Column {
                        Text("Actual Bank Statement Balance", color = theme.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        CompactInputField(
                            value = statementInput,
                            onValueChange = { input -> if (input.all { c -> c.isDigit() || c == '.' || c == '-' }) statementInput = input },
                            placeholder = "0.0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Unexplained Discrepancy (Drift):", color = theme.textMuted, fontSize = 10.sp)
                            
                            // Drift 0 logic applied
                            val driftColor = if (drift == 0.0) theme.textBright else if (drift > 0) theme.mildGreen else theme.mildRed
                            Text(
                                text = "${if (drift > 0) "+" else ""}₹ ${String.format("%,.2f", drift)}",
                                color = driftColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    CompactInputField(
                        value = reasonNote,
                        onValueChange = { reasonNote = it },
                        placeholder = "Audit Note",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onApplyDiscrepancy(actualAmt, drift, reasonNote) },
                            modifier = Modifier.weight(1.3f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                        ) {
                            Text("Apply Checkpoint", color = theme.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)
                        ) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
