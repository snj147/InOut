package com.personal.inout.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    currentTheme: AppThemeMode,
    configuredDailyBurn: Double,
    isProUser: Boolean,
    onSelectTheme: (AppThemeMode) -> Unit,
    onUpdateDailyBurn: (Double) -> Unit,
    onTriggerProPurchase: () -> Unit,
    onExportPdfDossier: () -> Unit,
    onExportCsv: () -> Unit,
    onClearLedger: () -> Unit
) {
    val theme = LocalThemeColors.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE) }

    var useCloudVision by remember { mutableStateOf(prefs.getBoolean("use_cloud_vision", false)) }
    var cloudApiKey by remember { mutableStateOf(prefs.getString("cloud_vision_api_key", "") ?: "") }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var autoSplitEnabled by remember { mutableStateOf(prefs.getBoolean("auto_split_debit", false)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp)
    ) {
        item {
            Text("Vault Configuration", color = theme.textBright, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }

        // Theme Selection
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("THEME PALETTE", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            AppThemeMode.AMBER_OCHRE to "Amber",
                            AppThemeMode.OLIVE_MATCHA to "Matcha",
                            AppThemeMode.NORDIC_SLATE to "Slate"
                        ).forEach { (mode, lbl) ->
                            val isSel = currentTheme == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) theme.accent else theme.surfaceAlt)
                                    .clickable { onSelectTheme(mode) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(lbl, color = if (isSel) theme.bg else theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Auto-Split Across Bank Accounts
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Split Cross-Account Debit", color = theme.textBright, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Automatically debit secondary bank accounts when primary account has insufficient balance.",
                            color = theme.textMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = autoSplitEnabled,
                        onCheckedChange = { checked ->
                            autoSplitEnabled = checked
                            prefs.edit().putBoolean("auto_split_debit", checked).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = theme.bg, checkedTrackColor = theme.accent)
                    )
                }
            }
        }

        // Google Cloud Vision Assist
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Cloud Vision API", color = theme.textBright, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Enhanced cloud OCR for damaged receipts. Default is 100% offline on-device.",
                                color = theme.textMuted,
                                fontSize = 10.5.sp
                            )
                        }
                        Switch(
                            checked = useCloudVision,
                            onCheckedChange = { checked ->
                                useCloudVision = checked
                                prefs.edit().putBoolean("use_cloud_vision", checked).apply()
                                if (checked && cloudApiKey.isBlank()) {
                                    showApiKeyDialog = true
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = theme.bg, checkedTrackColor = theme.accent)
                        )
                    }

                    if (useCloudVision) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.surfaceAlt)
                                .clickable { showApiKeyDialog = true }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (cloudApiKey.isNotBlank()) "Key: ••••••••••••${cloudApiKey.takeLast(4)}" else "Set Google Cloud API Key →",
                                color = theme.accent,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Daily Burn Rate
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DAILY RUNWAY BURN TARGET", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(300.0, 450.0, 600.0, 1000.0).forEach { rate ->
                            val isSel = configuredDailyBurn == rate
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) theme.accent else theme.surfaceAlt)
                                    .clickable { onUpdateDailyBurn(rate) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("₹${rate.toInt()}", color = if (isSel) theme.bg else theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Data & Exports
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DATA & DOSSIERS", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onExportCsv,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt)
                        ) {
                            Text("Export CSV", color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onExportPdfDossier,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                        ) {
                            Text("PDF Dossier", color = theme.bg, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onClearLedger,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.mildRed)
                    ) {
                        Text("Clear All Ledger Entries", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showApiKeyDialog) {
        var keyInput by remember { mutableStateOf(cloudApiKey) }
        AlertDialog(
            containerColor = theme.surface,
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Google Cloud Vision API Key", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your GCP Vision API key. When active, requests are made directly over HTTPS.", color = theme.textMuted, fontSize = 11.sp)
                    CompactInputField(value = keyInput, onValueChange = { keyInput = it }, placeholder = "AIzaSy...")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        cloudApiKey = keyInput.trim()
                        prefs.edit().putString("cloud_vision_api_key", cloudApiKey).apply()
                        showApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    Text("Save Key", color = theme.bg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel", color = theme.textMuted)
                }
            }
        )
    }
}
