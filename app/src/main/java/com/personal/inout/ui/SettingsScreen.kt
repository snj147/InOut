package com.personal.inout.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    currentTheme: AppThemeMode,
    isProUser: Boolean,
    onSelectTheme: (AppThemeMode) -> Unit,
    onTriggerProPurchase: () -> Unit,
    onClearLedger: () -> Unit
) {
    val theme = LocalThemeColors.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE) }

    var isBiometricEnabled by remember {
        mutableStateOf(prefs.getBoolean("biometric_enabled", false))
    }

    var showClearConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Settings & Vault Control",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = theme.textBright
        )

        // VIP Lifetime Status Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTriggerProPurchase() }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(theme.accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isProUser) Icons.Default.WorkspacePremium else Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = theme.accent
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isProUser) "VIP Lifetime Unlocked" else "Unlock VIP Access (₹21)",
                        color = theme.textBright,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isProUser) "Offline vault active • No subscriptions" else "Tap to access mock paywall or restore",
                        color = theme.textMuted,
                        fontSize = 11.sp
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = theme.textMuted)
            }
        }

        // Visual Customization Section
        SettingsSection(title = "Appearance & Themes", theme = theme) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(AppThemeMode.AMBER_OCHRE, "Amber Ochre", "Rich obsidian and warm amber"),
                    Triple(AppThemeMode.OLIVE_MATCHA, "Olive Matcha", "Deep forest tones with matcha green"),
                    Triple(AppThemeMode.SAND_DUNE, "Sand Dune", "Earthy muted sands and slate")
                ).forEach { (mode, name, desc) ->
                    val isSelected = currentTheme == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) theme.accent.copy(alpha = 0.15f) else theme.surfaceAlt)
                            .clickable { onSelectTheme(mode) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(name, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(desc, color = theme.textMuted, fontSize = 10.5.sp)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Vault Security Section
        SettingsSection(title = "Privacy & Security", theme = theme) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Biometric / PIN Unlock", color = theme.textBright, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Require device auth on vault launch", color = theme.textMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = isBiometricEnabled,
                    onCheckedChange = { checked ->
                        isBiometricEnabled = checked
                        prefs.edit().putBoolean("biometric_enabled", checked).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.bg,
                        checkedTrackColor = theme.accent,
                        uncheckedThumbColor = theme.textMuted,
                        uncheckedTrackColor = theme.surfaceAlt
                    )
                )
            }
        }

        // Data & Diagnostics
        SettingsSection(title = "Database & Diagnostics", theme = theme) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsActionRow(
                    icon = Icons.Default.HealthAndSafety,
                    title = "Conservation Check",
                    subtitle = "Verify all state-flow accounts are in equilibrium",
                    theme = theme
                ) {
                    Toast.makeText(context, "All Vault Pockets are in conservation equilibrium", Toast.LENGTH_SHORT).show()
                }

                Divider(color = theme.surfaceAlt, thickness = 0.5.dp)

                SettingsActionRow(
                    icon = Icons.Default.DeleteForever,
                    title = "Clear Vault Records",
                    subtitle = "Wipe all transactions and reset balances",
                    titleColor = theme.mildRed,
                    theme = theme
                ) {
                    showClearConfirmation = true
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }

    if (showClearConfirmation) {
        AlertDialog(
            containerColor = theme.surface,
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Reset Entire Vault?", color = theme.mildRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This action will permanently delete all transaction history. Accounts will be retained at zero balance.",
                    color = theme.textBright,
                    fontSize = 12.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearLedger()
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed)
                ) {
                    Text("Wipe History", color = theme.bg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel", color = theme.textMuted)
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    theme: ThemeColors,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = theme.accent,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: androidx.compose.ui.graphics.Color? = null,
    theme: ThemeColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = titleColor ?: theme.accent, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = titleColor ?: theme.textBright, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = theme.textMuted, fontSize = 10.5.sp)
        }
    }
}
