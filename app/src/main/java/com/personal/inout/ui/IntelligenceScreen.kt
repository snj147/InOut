package com.personal.inout.ui

import androidx.compose.foundation.background
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
import com.personal.inout.data.FlowRecord
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketBalanceSummary
import com.personal.inout.data.PocketType
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IntelligenceScreen(
    pocketBalances: List<PocketBalanceSummary>,
    flowRecords: List<FlowRecord>,
    isPrivacyMode: Boolean,
    isProUser: Boolean,
    onUnlockPro: () -> Unit
) {
    val theme = LocalThemeColors.current

    // Ghost Burn Detector (micro-expenses under ₹150)
    val ghostBurnRecords = remember(flowRecords) {
        flowRecords.filter { it.nature == MovementNature.OUTFLOW && it.amount in 1.0..150.0 }
    }
    val totalGhostBurn = remember(ghostBurnRecords) { ghostBurnRecords.sumOf { it.amount } }

    // Top Expense Category Breakdown
    val categoryTotals = remember(flowRecords) {
        flowRecords
            .filter { it.nature == MovementNature.OUTFLOW }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }
    val totalOutflow = remember(categoryTotals) { categoryTotals.sumOf { it.second }.coerceAtLeast(1.0) }

    // Solvency Horizon
    val totalLiquid = remember(pocketBalances) {
        pocketBalances.filter { it.pocketType == PocketType.LIQUID }.sumOf { it.currentBalance }.coerceAtLeast(0.0)
    }
    val totalDebt = remember(pocketBalances) {
        pocketBalances.filter { it.pocketType == PocketType.CREDIT_LINE }.sumOf { it.currentBalance }.coerceAtLeast(0.0)
    }
    val netWorth = totalLiquid - totalDebt

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp)
    ) {
        item {
            Text(
                "Financial Intelligence",
                color = theme.textBright,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }

        // Net Solvency Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("NET CONSERVATION WORTH", color = theme.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Icon(Icons.Default.Shield, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                    }
                    val netStr = if (isPrivacyMode) "₹ ••••••" else "₹ ${String.format("%,.0f", netWorth)}"
                    Text(netStr, color = if (netWorth >= 0) theme.mildGreen else theme.mildRed, fontSize = 26.sp, fontWeight = FontWeight.Black)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Liquid Cash: ₹${String.format("%,.0f", totalLiquid)}", color = theme.textMuted, fontSize = 11.sp)
                        Text("Card Debts: ₹${String.format("%,.0f", totalDebt)}", color = theme.textMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        // Ghost Burn Radar Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = theme.mildRed, modifier = Modifier.size(18.dp))
                            Text("GHOST BURN RADAR", color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${ghostBurnRecords.size} Leaks", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        "Silent daily micro-transactions under ₹150 that slip under the radar.",
                        color = theme.textMuted,
                        fontSize = 11.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.surfaceAlt)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Ghost Leakage", color = theme.textBright, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("₹ ${String.format("%,.0f", totalGhostBurn)}", color = theme.mildRed, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // Category Leak Breakdown
        item {
            Text("Outflow Leak Breakdown", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        if (categoryTotals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface)
                ) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No outflow records recorded yet", color = theme.textMuted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(categoryTotals) { (cat, amount) ->
                val ratio = (amount / totalOutflow).toFloat()
                CategoryBarRow(name = cat, amount = amount, ratio = ratio, theme = theme)
            }
        }
    }
}

@Composable
private fun CategoryBarRow(name: String, amount: Double, ratio: Float, theme: ThemeColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, color = theme.textBright, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text("₹ ${String.format("%,.0f", amount)} (${(ratio * 100).toInt()}%)", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = ratio,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = theme.accent,
            trackColor = theme.surfaceAlt
        )
    }
}
