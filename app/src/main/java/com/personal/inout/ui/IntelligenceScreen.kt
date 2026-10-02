package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.LedgerPocket
import com.personal.inout.data.LedgerTransaction
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketType
import kotlin.math.abs

@Composable
fun IntelligenceScreen(
    pocketBalances: Map<Long, Double>,
    flowRecords: List<LedgerTransaction>,
    recurringSchedules: List<LedgerTransaction>,
    stagedDesires: List<Any> = emptyList(),
    dailyBurnCeiling: Double,
    trueSafeLiquid: Double,
    isPrivacyMode: Boolean,
    theme: ThemeColors
) {
    val totalExpenseLifetime = remember(flowRecords) {
        flowRecords.filter { it.movementNature == MovementNature.OPERATING_EXPENSE }.sumOf { it.amount }
    }

    val totalIncomeLifetime = remember(flowRecords) {
        flowRecords.filter { it.movementNature == MovementNature.OPERATING_INCOME }.sumOf { it.amount }
    }

    val categoryBreakdown = remember(flowRecords) {
        flowRecords.filter { it.movementNature == MovementNature.OPERATING_EXPENSE }
            .groupBy { it.category.ifBlank { "General" } }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val runwayDays = if (dailyBurnCeiling > 0.0) {
        (trueSafeLiquid / dailyBurnCeiling).toLong()
    } else {
        0L
    }

    // Statutory Net Worth across all active accounts
    val netWorth = remember(pocketBalances) {
        pocketBalances.values.sum()
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
                text = "Financial Intelligence & Runway",
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
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "STATUTORY NET CAPITAL (RULE 28)",
                            color = theme.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (netWorth >= 0.0) theme.mildGreen.copy(alpha = 0.18f) else theme.mildRed.copy(alpha = 0.18f))
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

                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Safe Liquid Runway (Rule 13)", color = theme.textMuted, fontSize = 10.5.sp)
                            Text(
                                if (isPrivacyMode) "•• Days" else "$runwayDays Days",
                                color = theme.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Daily Benchmark Ceiling", color = theme.textMuted, fontSize = 10.5.sp)
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

        // Top Category Breakdown
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "TOP EXPENDITURE CHANNELS",
                        color = theme.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (categoryBreakdown.isEmpty()) {
                        Text("No expenses registered in ledger yet.", color = theme.textMuted, fontSize = 11.5.sp)
                    } else {
                        val maxExpense = (categoryBreakdown.firstOrNull()?.second ?: 1.0).coerceAtLeast(1.0)
                        categoryBreakdown.take(7).forEach { (cat, amt) ->
                            val ratio = (amt / maxExpense).toFloat().coerceIn(0f, 1f)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(cat, color = theme.textBright, fontSize = 12.sp)
                                    Text(
                                        if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", amt)}",
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
                                    color = theme.accent,
                                    trackColor = theme.surfaceAlt
                                )
                            }
                        }
                    }
                }
            }
        }

        // Lifetime Cashflow Summary
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "LIFETIME CASHFLOW RECONCILIATION",
                        color = theme.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Living Inflow", color = theme.textMuted, fontSize = 11.sp)
                            Text(
                                if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", totalIncomeLifetime)}",
                                color = theme.mildGreen,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Consumed Outflow", color = theme.textMuted, fontSize = 11.sp)
                            Text(
                                if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", totalExpenseLifetime)}",
                                color = theme.mildRed,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
