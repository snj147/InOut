package com.personal.inout.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

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
                Text("DOUBLE-ENTRY VAULT", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = theme.accent)
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
            .clip(RoundedCornerShape(10.dp))
            .background(theme.surfaceAlt)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = theme.textMuted, fontSize = 12.5.sp)
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

@Composable
fun ThemePinPadDialog(
    title: String,
    subtitle: String,
    expectedPin: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val theme = LocalThemeColors.current
    var pin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(title, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = theme.textMuted, fontSize = 11.5.sp)

                Text(
                    text = "• ".repeat(pin.length).ifEmpty { "Enter PIN" },
                    color = if (isError) theme.mildRed else theme.accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (1..3).forEach { n -> PinKey(n.toString(), theme) { pin += it } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (4..6).forEach { n -> PinKey(n.toString(), theme) { pin += it } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (7..9).forEach { n -> PinKey(n.toString(), theme) { pin += it } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { pin = "" }) { Text("Clear", color = theme.textMuted) }
                    PinKey("0", theme) { pin += it }
                    TextButton(onClick = {
                        if (pin == expectedPin) onSuccess() else isError = true
                    }) { Text("OK", color = theme.accent, fontWeight = FontWeight.Bold) }
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Set New PIN", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Enter a 4-digit code to protect your ledger.", color = theme.textMuted, fontSize = 11.5.sp)

                CompactInputField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    placeholder = "4-digit PIN"
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) }
                    Button(
                        onClick = { if (pin.length == 4) onSavePin(pin) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                    ) {
                        Text("Save", color = theme.bg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(digit: String, theme: ThemeColors, onClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(theme.surfaceAlt)
            .clickable { onClick(digit) },
        contentAlignment = Alignment.Center
    ) {
        Text(digit, color = theme.textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
