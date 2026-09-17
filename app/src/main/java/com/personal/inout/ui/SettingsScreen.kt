package com.personal.inout.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.personal.inout.util.BackupManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentTheme: AppThemeMode,
    currentCockpit: CockpitStyle,
    isProUser: Boolean,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectCockpit: (CockpitStyle) -> Unit,
    onTriggerProPurchase: () -> Unit,
    onClearLedger: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme = LocalThemeColors.current
    val prefs = remember { context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE) }

    var showPinVerifyDialog by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    val savedPin = prefs.getString("user_pin", "1234") ?: "1234"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        // --- 1. Pro Membership Status Banner ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isProUser) Icons.Default.WorkspacePremium else Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = theme.accent,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (isProUser) "Founder Lifetime Member" else "Free Tier (7-Day Pass)",
                                color = theme.textBright,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        if (isProUser) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.accent)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("ACTIVE", color = theme.bg, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    Text(
                        text = if (isProUser) {
                            "You have unlocked unlimited accounts, all 3 Cockpit styles, watermarked-free PDF dossiers, and cloud vault exports."
                        } else {
                            "Early supporter offer: Unlock InOut Lifetime Pro for ₹21. Price increases to ₹51/₹99 for latecomers."
                        },
                        color = theme.textMuted,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )

                    if (!isProUser) {
                        Button(
                            onClick = onTriggerProPurchase,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                        ) {
                            Text(
                                "Unlock Lifetime Pro — ₹21",
                                color = theme.bg,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
            }
        }

        // --- 2. Palette & Cockpit Appearance ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Palette Theme", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("Amber Ochre", AppThemeMode.AMBER_OCHRE, AmberTheme.accent),
                            Triple("Olive Matcha", AppThemeMode.OLIVE_MATCHA, OliveMatchaTheme.accent),
                            Triple("Sand Dune", AppThemeMode.SAND_DUNE, SandDuneTheme.accent)
                        ).forEach { (label, mode, col) ->
                            val isSel = currentTheme == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) col.copy(alpha = 0.25f) else theme.surfaceAlt)
                                    .clickable { onSelectTheme(mode) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (isSel) col else theme.textMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Divider(color = theme.surfaceAlt)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cockpit Widget", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        if (!isProUser) {
                            Text("Default Locked", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CockpitStyle.values().forEach { style ->
                            val isSel = currentCockpit == style
                            val isLocked = !isProUser && style != CockpitStyle.BATTERY_EQUALIZER

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) theme.accent.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable {
                                        if (isLocked) {
                                            onTriggerProPurchase()
                                        } else {
                                            onSelectCockpit(style)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        style.label,
                                        color = if (isSel) theme.accent else theme.textBright,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isLocked) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = theme.textMuted, modifier = Modifier.size(12.dp))
                                    }
                                }
                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Backup & Security ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Vault Backup & Storage", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.surfaceAlt)
                            .clickable {
                                scope.launch {
                                    BackupManager.createAndShareEncryptedBackup(context)
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = theme.accent, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Export Encrypted Backup", color = theme.textBright, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                Text("Save .db file to Google Drive or local files", color = theme.textMuted, fontSize = 10.5.sp)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = theme.textMuted)
                    }

                    Divider(color = theme.surfaceAlt)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Security PIN", color = theme.textBright, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text("Protects ledger wipe & sensitive actions", color = theme.textMuted, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { showSetPinDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Change PIN", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(color = theme.surfaceAlt)

                    // In-App Feedback Sheet for Closed Testing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.surfaceAlt)
                            .clickable { showFeedbackDialog = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.RateReview, contentDescription = null, tint = theme.accent, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Tester Feedback & Bug Report", color = theme.textBright, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                Text("Send direct feedback to developer", color = theme.textMuted, fontSize = 10.5.sp)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = theme.textMuted)
                    }

                    Divider(color = theme.surfaceAlt)

                    TextButton(
                        onClick = { showPinVerifyDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = theme.mildRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reset & Clear All Ledger Records", color = theme.mildRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showPinVerifyDialog) {
        ThemePinPadDialog(
            title = "Verify Security PIN",
            subtitle = "Enter your 4-digit PIN to authorize ledger reset.",
            expectedPin = savedPin,
            onDismiss = { showPinVerifyDialog = false },
            onSuccess = {
                showPinVerifyDialog = false
                onClearLedger()
            }
        )
    }

    if (showSetPinDialog) {
        ThemeSetPinDialog(
            onDismiss = { showSetPinDialog = false },
            onSavePin = { newPin ->
                prefs.edit().putString("user_pin", newPin).apply()
                showSetPinDialog = false
                Toast.makeText(context, "New security PIN saved", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showFeedbackDialog) {
        TesterFeedbackDialog(onDismiss = { showFeedbackDialog = false })
    }
}

@Composable
fun TesterFeedbackDialog(onDismiss: () -> Unit) {
    val theme = LocalThemeColors.current
    val context = LocalContext.current
    var feedbackText by remember { mutableStateOf("") }

    AlertDialog(
        containerColor = theme.surface,
        onDismissRequest = onDismiss,
        title = { Text("Send Feedback", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = theme.textBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Found a bug or have a suggestion? Send your notes directly to the developer during closed testing.",
                    color = theme.textMuted,
                    fontSize = 11.5.sp
                )
                CompactInputField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    placeholder = "Describe your issue or thought...",
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (feedbackText.isNotBlank()) {
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_SUBJECT, "InOut Android App Feedback")
                            putExtra(Intent.EXTRA_TEXT, feedbackText)
                        }
                        context.startActivity(Intent.createChooser(emailIntent, "Send Feedback"))
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Send Email", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted, fontSize = 12.sp) }
        }
    )
}
