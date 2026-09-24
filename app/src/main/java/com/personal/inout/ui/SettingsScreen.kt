package com.personal.inout.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.util.AppUpdateEngine
import com.personal.inout.util.UpdateInfo
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    burnTarget: Long,
    onBurnTargetChange: (Long) -> Unit,
    autoSplitEnabled: Boolean,
    onAutoSplitToggle: (Boolean) -> Unit,
    cloudVisionEnabled: Boolean,
    onCloudVisionToggle: (Boolean) -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text("Settings & Ledger Config", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))

        // Application Updates Section
        Text("APPLICATION UPDATES & SUPPORT", color = Color(0xFFCCCCCC), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C1A))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (!isCheckingUpdate) {
                            isCheckingUpdate = true
                            updateResult = null
                            updateStatusMessage = null
                            scope.launch {
                                val res = AppUpdateEngine.checkForUpdate(context)
                                isCheckingUpdate = false
                                res.onSuccess { info ->
                                    updateResult = info
                                    if (!info.hasUpdate) {
                                        updateStatusMessage = "You are currently running the latest alpha build."
                                    }
                                }.onFailure {
                                    updateStatusMessage = "You are currently running the latest alpha build."
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2826))
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFFE59C5C), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Checking for updates...", color = Color.White, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFE59C5C), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for App Update", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Clean Status Banner (No raw JSON dump)
                if (updateResult?.hasUpdate == true) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE59C5C).copy(alpha = 0.15f))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color(0xFFE59C5C), modifier = Modifier.size(18.dp))
                                Text("New Version Available: ${updateResult?.latestVersion}", color = Color(0xFFE59C5C), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    val url = updateResult?.downloadUrl
                                    if (!url.isNullOrBlank()) {
                                        AppUpdateEngine.startDownloadAndInstall(context, url, updateResult?.latestVersion ?: "InOut")
                                    } else {
                                        Toast.makeText(context, "No package asset available", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE59C5C))
                            ) {
                                Text("Download & Install Update", color = Color(0xFF1C1917), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }
                    }
                } else if (updateStatusMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2B2826))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF68D391), modifier = Modifier.size(16.dp))
                            Text(updateStatusMessage ?: "", color = Color(0xFFCCCCCC), fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Runway Configuration
        Text("RUNWAY CONFIGURATION", color = Color(0xFFCCCCCC), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C1A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Daily Burn Target", color = Color.White, fontSize = 15.sp)
                    Text("Calculates daily survival runway", color = Color(0xFF888888), fontSize = 12.sp)
                }
                Text("₹$burnTarget/day", color = Color(0xFFE59C5C), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Automation Toggles
        Text("PREFERENCES & AUTOMATION", color = Color(0xFFCCCCCC), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C1A))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Split Cross Account Debit", color = Color.White, fontSize = 15.sp)
                        Text("Allows overdraft split across liquid accounts", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoSplitEnabled,
                        onCheckedChange = onAutoSplitToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF1C1917),
                            checkedTrackColor = Color(0xFFE59C5C)
                        )
                    )
                }

                HorizontalDivider(color = Color(0xFF2B2826), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cloud Vision Assist", color = Color.White, fontSize = 15.sp)
                        Text("Off by default (App uses 100% offline ML Kit)", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Switch(
                        checked = cloudVisionEnabled,
                        onCheckedChange = onCloudVisionToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF1C1917),
                            checkedTrackColor = Color(0xFFE59C5C)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Dossiers & Exports
        Text("DATA & DOSSIERS", color = Color(0xFFCCCCCC), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C1A))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExportPdf() }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Export Dossier (PDF)", color = Color.White, fontSize = 15.sp)
                        Text("Formatted financial statement & insights", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Text("Export →", color = Color(0xFFE59C5C), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = Color(0xFF2B2826), thickness = 1.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExportCsv() }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Export Ledger (CSV)", color = Color.White, fontSize = 15.sp)
                        Text("Raw spreadsheet data for backup or custom analysis", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Text("Export →", color = Color(0xFFE59C5C), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}
