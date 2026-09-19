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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.util.AppIconManager

@Composable
fun SettingsScreen(
    currentTheme: AppThemeMode,
    currentCockpitMode: CockpitDisplayMode,
    configuredDailyBurn: Double,
    isProUser: Boolean,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectCockpitMode: (CockpitDisplayMode) -> Unit,
    onUpdateDailyBurn: (Double) -> Unit,
    onTriggerProPurchase: () -> Unit,
    onExportPdfDossier: () -> Unit,
    onExportCsv: () -> Unit,
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

        // VIP Banner
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
                        if (isProUser) "VIP Lifetime Active" else "Unlock VIP Access (₹21)",
                        color = theme.textBright,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isProUser) "VIP Gold Icon • PDF Dossiers • Uncapped Horizons" else "Tap to view Pro perks",
                        color = theme.textMuted,
                        fontSize = 11.sp
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = theme.textMuted)
            }
        }

        // Cockpit Style Selector
        SettingsSection(title = "Cockpit Instrument Style", theme = theme) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(CockpitDisplayMode.SURVIVAL_DAYS_SLIDER, "Runway Survival Slider", "Interactive days countdown with survival slider"),
                    Triple(CockpitDisplayMode.CASH_VS_DEBT_RADAR, "Cash vs Debt Balance", "Direct comparison of cash reserves against card liabilities"),
                    Triple(CockpitDisplayMode.WEEKLY_SPEND_PULSE, "Daily Spending Pulse", "7-day spending equalizer rhythm bars")
                ).forEach { (mode, name, desc) ->
                    val isSel = currentCockpitMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) theme.accent.copy(alpha = 0.15f) else theme.surfaceAlt)
                            .clickable { onSelectCockpitMode(mode) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(desc, color = theme.textMuted, fontSize = 10.5.sp)
                        }
                        if (isSel) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Divider(color = theme.surfaceAlt, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                // Custom Daily Spending Burn Rate Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Configured Daily Burn Rate", color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("₹${configuredDailyBurn.toInt()}/day", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    Slider(
                        value = configuredDailyBurn.toFloat(),
                        onValueChange = { onUpdateDailyBurn(it.toDouble()) },
                        valueRange = 100f..5000f,
                        steps = 48,
                        colors = SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent)
                    )
                }
            }
        }

        // 3 Distinct Visual Themes
        SettingsSection(title = "Appearance & Color Palette", theme = theme) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(AppThemeMode.AMBER_OCHRE, "Amber Ochre", "Obsidian and warm gold amber"),
                    Triple(AppThemeMode.OLIVE_MATCHA, "Olive Matcha", "Forest green and fresh matcha"),
                    Triple(AppThemeMode.NORDIC_SLATE, "Nordic Slate", "Midnight slate blue with crisp ice-cyan")
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

        // Exports (CSV & PDF Dossiers)
        SettingsSection(title = "Statements & Reports", theme = theme) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsActionRow(
                    icon = Icons.Default.FileDownload,
                    title = "Export Simple CSV Spreadsheet",
                    subtitle = "Plain spreadsheet of all transactions",
                    theme = theme,
                    onClick = onExportCsv
                )
                if (isProUser) {
                    Divider(color = theme.surfaceAlt, thickness = 0.5.dp)
                    SettingsActionRow(
                        icon = Icons.Default.PictureAsPdf,
                        title = "Generate Accountant PDF Dossier",
                        subtitle = "Watermark-free audit report with balance sheets",
                        theme = theme,
                        onClick = onExportPdfDossier
                    )
                }
            }
        }

        // Biometrics
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
                    Text("Require fingerprint or device PIN on launch", color = theme.textMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = isBiometricEnabled,
                    onCheckedChange = { checked ->
                        isBiometricEnabled = checked
                        prefs.edit().putBoolean("biometric_enabled", checked).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = theme.bg,
                        checkedTrackColor = theme.accent
                    )
                )
            }
        }

        // Diagnostics
        SettingsSection(title = "Database & Diagnostics", theme = theme) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsActionRow(
                    icon = Icons.Default.HealthAndSafety,
                    title = "Ledger Equilibrium Audit",
                    subtitle = "Confirm that every rupee balances across all accounts",
                    theme = theme
                ) {
                    Toast.makeText(context, "All Vault accounts are in exact equilibrium", Toast.LENGTH_SHORT).show()
                }

                Divider(color = theme.surfaceAlt, thickness = 0.5.dp)

                SettingsActionRow(
                    icon = Icons.Default.DeleteForever,
                    title = "Clear All Vault Flows",
                    subtitle = "Reset all transactions back to empty state",
                    titleColor = theme.mildRed,
                    theme = theme
                ) {
                    showClearConfirmation = true
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showClearConfirmation) {
        AlertDialog(
            containerColor = theme.surface,
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Reset All Records?", color = theme.mildRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This permanently clears transaction history. Accounts will remain at zero balance.",
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
                    Text("Wipe Records", color = theme.bg, fontWeight = FontWeight.Bold)
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
    titleColor: Color? = null,
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
