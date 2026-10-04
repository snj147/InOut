package com.personal.inout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import com.personal.inout.ui.LocalThemeColors

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
                // Neutral formatting applied for 0.0 values
                val inColor = if (totalIn == 0.0) theme.textMuted else theme.mildGreen
                val outColor = if (totalOut == 0.0) theme.textMuted else theme.mildRed

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = inColor, modifier = Modifier.size(13.dp))
                    Text(if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", totalIn)}", color = inColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = outColor, modifier = Modifier.size(13.dp))
                    Text(if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", totalOut)}", color = outColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val theme = LocalThemeColors.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.surfaceAlt)
            .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = theme.textMuted, fontSize = 12.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = theme.textBright, fontSize = 13.sp),
            cursorBrush = SolidColor(theme.accent),
            keyboardOptions = keyboardOptions,
            singleLine = true,
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
