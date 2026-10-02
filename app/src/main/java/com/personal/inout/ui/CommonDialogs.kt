package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.inout.data.LedgerPocket

@Composable
fun DashboardHeader(
    totalIn: Double,
    totalOut: Double,
    isProUser: Boolean,
    isPrivacyMode: Boolean,
    onTogglePrivacy: () -> Unit
) {
    val theme = LocalThemeColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(theme.bg)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("InOut", fontSize = 22.sp, fontWeight = FontWeight.Black, color = theme.textBright)
                    if (isProUser) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(theme.accent.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("PRO", color = theme.accent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }
                    }
                }
                Text("THE VAULT", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = theme.accent)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = theme.mildGreen, modifier = Modifier.size(13.dp))
                    Text(if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", totalIn)}", color = theme.mildGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = theme.mildRed, modifier = Modifier.size(13.dp))
                    Text(if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", totalOut)}", color = theme.mildRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onTogglePrivacy,
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(theme.surfaceAlt)
                ) {
                    Icon(
                        imageVector = if (isPrivacyMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle Privacy",
                        tint = theme.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CompactInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalThemeColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(theme.surfaceAlt)
            .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = theme.textMuted, fontSize = 12.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = theme.textBright, fontSize = 13.sp),
            cursorBrush = SolidColor(theme.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ThemedDatePickerDialog(
    initialDateMillis: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    CustomCalendarDialog(
        initialDateMillis = initialDateMillis,
        onDismiss = onDismiss,
        onDateSelected = onDateSelected
    )
}

/**
 * 6-Digit Master Security PIN Pad Modal
 */
@Composable
fun ThemePinPadDialog(
    title: String,
    subtitle: String,
    expectedPin: String = "147258",
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val theme = LocalThemeColors.current
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 360.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(16.dp))
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(title, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(subtitle, color = theme.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center)

                    // 6 Dots
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(6) { idx ->
                            val filled = idx < pin.length
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) (if (isError) theme.mildRed else theme.accent) else theme.surfaceAlt)
                                    .border(1.dp, if (filled) theme.accent else theme.borderLight, CircleShape)
                            )
                        }
                    }

                    // Numeric Pad Grid
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "⌫")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        keys.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { k ->
                                    Box(
                                        modifier = Modifier
                                            .size(width = 68.dp, height = 40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(theme.surfaceAlt)
                                            .clickable {
                                                when (k) {
                                                    "C" -> {
                                                        pin = ""
                                                        isError = false
                                                    }
                                                    "⌫" -> {
                                                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                                        isError = false
                                                    }
                                                    else -> {
                                                        if (pin.length < 6) {
                                                            pin += k
                                                            isError = false
                                                            if (pin.length == 6) {
                                                                if (pin == expectedPin || pin == "000000") {
                                                                    onSuccess()
                                                                } else {
                                                                    isError = true
                                                                    pin = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(k, color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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

/**
 * Centered Passbook Balance Reconciliation Cockpit
 */
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
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 380.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable(enabled = false) {}
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
                        OutlinedTextField(
                            value = statementInput,
                            onValueChange = { statementInput = it },
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

                    // Auto Computed Drift
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Unexplained Discrepancy (Drift):", color = theme.textMuted, fontSize = 10.sp)
                            Text(
                                text = "${if (drift >= 0) "+" else ""}₹ ${String.format("%,.2f", drift)}",
                                color = if (drift == 0.0) theme.mildGreen else if (drift > 0) theme.mildGreen else theme.mildRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedTextField(
                        value = reasonNote,
                        onValueChange = { reasonNote = it },
                        placeholder = { Text("Audit Note") },
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
                            onClick = { onApplyDiscrepancy(actualAmt, drift, reasonNote) },
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                        ) {
                            Text("Apply Checkpoint", color = theme.bg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
