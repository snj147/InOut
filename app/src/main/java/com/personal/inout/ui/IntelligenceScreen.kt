package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.FlowRecord
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketBalanceSummary
import com.personal.inout.data.StagedDesire
import java.util.Calendar

@Composable
fun IntelligenceScreen(
    pocketBalances: List<PocketBalanceSummary> = emptyList(),
    flowRecords: List<FlowRecord> = emptyList(),
    recurringSchedules: List<FlowRecord> = emptyList(),
    stagedDesires: List<StagedDesire> = emptyList(),
    dailyBurnCeiling: Double = 450.0,
    trueSafeLiquid: Double = 0.0,
    isPrivacyMode: Boolean = false,
    theme: ThemeColors
) {
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val spentToday = remember(flowRecords) {
        flowRecords.filter { it.timestamp >= todayStart && it.nature in listOf(MovementNature.OUTFLOW, MovementNature.PEER_LEND, MovementNature.PEER_REPAY) }
            .sumOf { it.amount }
    }

    val burnDelta = dailyBurnCeiling - spentToday
    val isPacingHealthy = burnDelta >= 0

    // Leakage / Zombie Rule Detection
    val inactiveRecurring = remember(recurringSchedules, flowRecords) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 3600 * 1000L)
        recurringSchedules.filter { rule ->
            val hasRecent = flowRecords.any { it.note.equals(rule.note, ignoreCase = true) && it.timestamp >= thirtyDaysAgo && !it.isRecurring }
            !hasRecent && !rule.isPaused
        }
    }

    val totalImpulseSaved = remember(stagedDesires) {
        stagedDesires.sumOf { it.amount }
    }

    val burnProgress = if (dailyBurnCeiling > 0) (spentToday / dailyBurnCeiling).toFloat().coerceIn(0f, 1f) else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        item {
            Text("Decision Console & Intelligence", color = theme.textBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // 1. Pacing & Trajectory Gauge (At-a-Glance Audit)
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TRAJECTORY & PACING GAUGE", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isPrivacyMode) "₹ ••• / ₹ •••" else "₹${spentToday.toInt()} spent of ₹${dailyBurnCeiling.toInt()} target",
                            color = theme.textBright,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isPacingHealthy) "+₹${burnDelta.toInt()} buffer" else "-₹${Math.abs(burnDelta).toInt()} spiked",
                            color = if (isPacingHealthy) theme.mildGreen else theme.mildRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = burnProgress,
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = if (isPacingHealthy) theme.mildGreen else theme.mildRed,
                        trackColor = theme.surfaceAlt
                    )

                    Text(
                        text = if (isPacingHealthy)
                            "Pacing on track. Spending within your daily ceiling preserves your survival horizon."
                        else
                            "Spend spiked above ceiling. Running below ₹${(dailyBurnCeiling * 0.8).toInt()}/day for 3 days will restore runway equilibrium.",
                        color = theme.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 2. Leakage & Zombie Rule Detector
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("LEAKAGE & ZOMBIE RULES", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)

                    if (inactiveRecurring.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.mildGreen, modifier = Modifier.size(16.dp))
                            Text("Zero leakage detected across active recurring schedules.", color = theme.textBright, fontSize = 12.sp)
                        }
                    } else {
                        inactiveRecurring.forEach { rule ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(theme.surfaceAlt).padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = theme.mildRed, modifier = Modifier.size(16.dp))
                                    Column {
                                        Text(rule.note.ifBlank { rule.category }, color = theme.textBright, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text("No manual transactions logged in 30+ days", color = theme.textMuted, fontSize = 10.sp)
                                    }
                                }
                                Text("₹${rule.amount.toInt()}", color = theme.mildRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 3. Impulse Capital Saved Trophy Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("IMPULSE CAPITAL RECLAIMED", color = theme.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)

                    Text(
                        text = if (isPrivacyMode) "₹ ••• Saved" else "₹${String.format("%,.0f", totalImpulseSaved)} Saved",
                        color = theme.accent,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )

                    Text(
                        text = "Money reclaimed by holding desire items past the 48-hour cool-off quarantine without purchasing.",
                        color = theme.textMuted,
                        fontSize = 11.sp
                    )

                    val recentDropped = stagedDesires.take(3)
                    if (recentDropped.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            recentDropped.forEach { item ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• ${item.name}", color = theme.textBright, fontSize = 11.5.sp)
                                    Text("₹${item.amount.toInt()}", color = theme.mildGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
