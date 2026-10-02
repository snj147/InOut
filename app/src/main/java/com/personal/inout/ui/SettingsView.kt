package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.util.UpdateDownloadState
import com.personal.inout.util.UpdateInfo
import java.io.File

@Composable
fun SettingsCardsList(
    theme: ThemeColors,
    currentSha: String,
    lastUpdatedDate: String,
    masterPin: String,
    autoSplitEnabled: Boolean,
    activeThemeMode: AppThemeMode,
    availableUpdate: UpdateInfo?,
    isCheckingUpdate: Boolean,
    downloadState: UpdateDownloadState,
    onOpenSetPinDialog: () -> Unit,
    onCheckUpdate: () -> Unit,
    onStartStreamDownload: (UpdateInfo) -> Unit,
    onInstallDownloadedApk: (File) -> Unit,
    onOpenFeedback: () -> Unit,
    onAutoSplitToggled: (Boolean) -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onExportEncryptedBackup: () -> Unit,
    onRestoreEncryptedBackup: () -> Unit,
    onClearLedger: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "WORKSPACE AESTHETICS", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            AppThemeMode.PALE_AMBER,
                            AppThemeMode.MATCHA_OLIVE,
                            AppThemeMode.DESERT_SAND,
                            AppThemeMode.NORDIC_SLATE
                        ).forEach { mode ->
                            val isSel = activeThemeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) theme.accent else theme.surfaceAlt)
                                    .clickable { onThemeSelected(mode) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.name.replace("_", "\n"),
                                    color = if (isSel) theme.bg else theme.textBright,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "AUTOMATION & LEDGER ENGINE", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "Cross-Account Auto-Split", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Draw shortfalls automatically from secondary liquid accounts.", color = theme.textMuted, fontSize = 10.5.sp)
                        }
                        Switch(
                            checked = autoSplitEnabled,
                            onCheckedChange = onAutoSplitToggled,
                            colors = SwitchDefaults.colors(checkedThumbColor = theme.accent, checkedTrackColor = theme.surfaceAlt)
                        )
                    }

                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSetPinDialog() }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "Master Security PIN", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Current PIN: ${if (masterPin == "000000") "000000 (Default)" else "••••••"} • Tap to change", color = theme.accent, fontSize = 11.sp)
                        }
                        Text(text = "Change ✎", color = theme.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "DATA PORTABILITY & BACKUP", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Button(
                        onClick = onExportEncryptedBackup,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Export Cryptographic Archive (.vault)", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onRestoreEncryptedBackup,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Restore Cryptographic Archive", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    Button(
                        onClick = onExportPdf,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Export Statutory PDF Dossier (ICAI Format)", color = theme.textBright, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onExportCsv,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceAlt),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Export TallyPrime / Excel CSV Ledger", color = theme.textBright, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "SYSTEM STATUS & DANGER ZONE", color = theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .clickable { if (!isCheckingUpdate) onCheckUpdate() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "InOut Vault Build (${currentSha.take(7)})", color = theme.textBright, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Installed: $lastUpdatedDate", color = theme.textMuted, fontSize = 10.sp)
                        }

                        if (isCheckingUpdate) {
                            Text(text = "Checking...", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else if (availableUpdate != null && availableUpdate.hasUpdate) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.accent)
                                    .clickable { onStartStreamDownload(availableUpdate) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(text = "UPDATE", color = theme.bg, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        } else {
                            Text(text = "Up to Date [✓]", color = theme.mildGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    when (val state = downloadState) {
                        is UpdateDownloadState.Downloading -> {
                            val percent = (state.progress * 100).toInt()
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Downloading update: $percent%", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = theme.accent,
                                    trackColor = theme.surfaceAlt
                                )
                            }
                        }
                        is UpdateDownloadState.ReadyToInstall -> {
                            Button(
                                onClick = { onInstallDownloadedApk(state.apkFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.mildGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Install Update Now", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        is UpdateDownloadState.Error -> {
                            Text(state.message, color = theme.mildRed, fontSize = 11.sp)
                        }
                        else -> {}
                    }

                    OutlinedButton(
                        onClick = onOpenFeedback,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.accent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)
                    ) {
                        Text(text = "Send Feedback / Bug Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    Button(
                        onClick = onClearLedger,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.mildRed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Purge Master Database & Reset Ledger", color = theme.mildRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
