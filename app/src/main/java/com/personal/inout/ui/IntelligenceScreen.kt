package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun IntelligenceScreen(
    pocketBalances: List<PocketBalanceTuple>,
    flowRecords: List<FlowRecord>,
    recurringSchedules: List<FlowRecord>,
    stagedDesires: List<StagedDesire>,
    dailyBurnCeiling: Double,
    trueSafeLiquid: Double,
    isPrivacyMode: Boolean,
    theme: ThemeColors
) {
    val totalExpenseLifetime = remember(flowRecords) {
        flowRecords.filter {
            !it.isRecurring && it.nature in listOf(
                MovementNature.OUTFLOW,
                MovementNature.PEER_LEND,
                MovementNature.PEER_REPAY,
                MovementNature.CARD_PAYMENT
            )
        }.sumOf { it.amount ?: 0.0 }
    }

    val totalIncomeLifetime = remember(flowRecords) {
        flowRecords.filter {
            !it.isRecurring && it.nature in listOf(
                MovementNature.INFLOW,
                MovementNature.PEER_COLLECT
            )
        }.sumOf { it.amount ?: 0.0 }
    }

    val categoryBreakdown = remember(flowRecords) {
        flowRecords.filter {
            !it.isRecurring && it.nature in listOf(
                MovementNature.OUTFLOW,
                MovementNature.PEER_LEND,
                MovementNature.PEER_REPAY,
                MovementNature.CARD_PAYMENT
            )
        }
            .groupBy { it.category.ifBlank { "General" } }
            .mapValues { (_, list) -> list.sumOf { it.amount ?: 0.0 } }
            .toList()
            .sortedByDescending { it.second }
    }

    val runwayDays = if (dailyBurnCeiling > 0.0) {
        (trueSafeLiquid / dailyBurnCeiling).toInt()
    } else {
        0
    }

    val netWorth = remember(pocketBalances) {
        pocketBalances.sumOf { it.computedBalance ?: 0.0 }
    }

    val activeGoals = remember(pocketBalances) {
        pocketBalances.filter { it.pocketType == PocketType.SAVING_GOAL }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        item {
            Text(
                text = "Financial Intelligence",
                color = theme.textBright,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Summary Net Worth & Burn Rate Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "NET CAPITAL POSITION",
                            color = theme.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (netWorth >= 0.0) theme.mildGreen.copy(alpha = 0.2f) else theme.mildRed.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (netWorth >= 0.0) "Solvent" else "Deficit",
                                color = if (netWorth >= 0.0) theme.mildGreen else theme.mildRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val netWorthStr = if (isPrivacyMode) "₹ •••" else "${if (netWorth >= 0.0) "" else "-"}₹${String.format("%,.0f", abs(netWorth))}"
                    Text(netWorthStr, color = theme.textBright, fontSize = 26.sp, fontWeight = FontWeight.Black)

                    HorizontalDivider(color = theme.surfaceAlt, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Safe Liquid Runway", color = theme.textMuted, fontSize = 10.5.sp)
                            Text(
                                if (isPrivacyMode) "•• Days" else "$runwayDays Days",
                                color = theme.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Daily Ceiling", color = theme.textMuted, fontSize = 10.5.sp)
                            Text(
                                "₹${dailyBurnCeiling.toInt()}/day",
                                color = theme.textBright,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Active Staged Desires & Cool-off Quarantine
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "COOL-OFF DESIRES QUARANTINE",
                            color = theme.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "${stagedDesires.size} staged",
                            color = theme.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (stagedDesires.isEmpty()) {
                        Text(
                            "No impulse purchases staged. Type 'stage <item> <amount>' to place an item in quarantine.",
                            color = theme.textMuted,
                            fontSize = 11.5.sp
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            stagedDesires.forEach { desire ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.surfaceAlt)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(desire.name, color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        val ageDays = ((System.currentTimeMillis() - desire.createdAtEpoch) / (24L * 3600 * 1000L)).toInt()
                                        Text("In quarantine: $ageDays days", color = theme.textMuted, fontSize = 10.sp)
                                    }
                                    Text(
                                        "₹${desire.amount.toInt()}",
                                        color = theme.textBright,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Saving Goal Pots Progress
        if (activeGoals.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "SAVINGS GOAL POTS",
                            color = theme.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        activeGoals.forEach { goal ->
                            val currentAmt = (goal.computedBalance ?: 0.0).coerceAtLeast(0.0)
                            val targetAmt = (goal.targetAmount ?: 0.0).coerceAtLeast(1.0)
                            val progress = (currentAmt / targetAmt).toFloat().coerceIn(0f, 1f)
                            val percent = (progress * 100).toInt()

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(goal.pocketName ?: "Goal", color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "₹${currentAmt.toInt()} / ₹${targetAmt.toInt()} ($percent%)",
                                        color = theme.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = theme.accent,
                                    trackColor = theme.surfaceAlt
                                )
                            }
                        }
                    }
                }
            }
        }

        // Category Breakdown
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "TOP EXPENSE CHANNELS",
                        color = theme.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (categoryBreakdown.isEmpty()) {
                        Text("No spending history recorded yet.", color = theme.textMuted, fontSize = 11.5.sp)
                    } else {
                        val maxExpense = (categoryBreakdown.firstOrNull()?.second ?: 1.0).coerceAtLeast(1.0)
                        categoryBreakdown.take(6).forEach { (cat, amt) ->
                            val ratio = (amt / maxExpense).toFloat().coerceIn(0f, 1f)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(cat, color = theme.textBright, fontSize = 12.sp)
                                    Text(
                                        if (isPrivacyMode) "₹ •••" else "₹${amt.toInt()}",
                                        color = theme.textBright,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = theme.mildRed,
                                    trackColor = theme.surfaceAlt
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
