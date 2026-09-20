package com.inout.vault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text("Settings & Ledger Config", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))

        // Burn Target Section
        Text("Runway Configuration", color = Color(0xFFCCCCCC), fontSize = 14.sp)
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
        Text("Preferences & Automation", color = Color(0xFFCCCCCC), fontSize = 14.sp)
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

        // Direct One-Click Dossiers & Exports (No Radio Pre-selection)
        Text("Data & Dossiers", color = Color(0xFFCCCCCC), fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C1A))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // PDF Dossier
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

                // CSV Ledger
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
