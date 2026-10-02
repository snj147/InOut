package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.inout.data.LedgerTransaction
import com.personal.inout.data.MovementNature
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

enum class TimeHorizon(val label: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    FINANCIAL_YEAR("FY 26-27 (ITR)")
}

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
    var selectedHorizon by remember { mutableStateOf(TimeHorizon.THIS_MONTH) }

    var selectedEnvelopeCategory by remember { mutableStateOf<String?>(null) }
    var showCapitalStructureModal by remember { mutableStateOf(false) }
    var showTaxDeductionsModal by remember { mutableStateOf(false) }

    val filteredRecords = remember(flowRecords, selectedHorizon) {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH)

        flowRecords.filter { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            val txYear = txCal.get(Calendar.YEAR)
            val txMonth = txCal.get(Calendar.MONTH)

            when (selectedHorizon) {
                TimeHorizon.THIS_MONTH -> txYear == currentYear && txMonth == currentMonth
                TimeHorizon.LAST_MONTH -> {
                    val lastMonthCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                    txYear == lastMonthCal.get(Calendar.YEAR) && txMonth == lastMonthCal.get(Calendar.MONTH)
                }
                TimeHorizon.FINANCIAL_YEAR -> {
                    // Indian Financial Year 2026-27: 01 Apr 2026 to 31 Mar 2027
                    val fyStart = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 1, 0, 0, 0) }.timeInMillis
                    val fyEnd = Calendar.getInstance().apply { set(2027, Calendar.MARCH, 31, 23, 59, 59) }.timeInMillis
                    tx.timestamp in fyStart..fyEnd
                }
            }
        }
    }

    val periodExpenses = remember(filteredRecords) {
        filteredRecords.filter { it.movementNature == MovementNature.OPERATING_EXPENSE }
    }
    val totalPeriodSpent = remember(periodExpenses) {
        periodExpenses.sumOf { it.amount }
    }

    val periodIncomes = remember(filteredRecords) {
        filteredRecords.filter { it.movementNature == MovementNature.OPERATING_INCOME }
    }
    val totalPeriodIncome = remember(periodIncomes) {
        periodIncomes.sumOf { it.amount }
    }

    val categoryEnvelopes = remember(periodExpenses) {
        periodExpenses
            .groupBy { it.category.ifBlank { "General" } }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    // Capital structure calculation
    val grossAssets = remember(pocketBalances) {
        pocketBalances.values.filter { it > 0.0 }.sum()
    }
    val grossLiabilities = remember(pocketBalances) {
        pocketBalances.values.filter { it < 0.0 }.sumOf { abs(it) }
    }
    val netCapitalPosition = grossAssets - grossLiabilities
    val equityRatio = if ((grossAssets + grossLiabilities) > 0.0) {
        ((grossAssets / (grossAssets + grossLiabilities)) * 100.0).toFloat().coerceIn(0f, 100f)
    } else 100f

    // Daily pacing metrics
    val calNow = Calendar.getInstance()
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val todayBurn = remember(flowRecords) {
        flowRecords.filter {
            it.timestamp >= todayStart && it.movementNature == MovementNature.OPERATING_EXPENSE
        }.sumOf { it.amount }
    }

    val daysInMonth = calNow.getActualMaximum(Calendar.DAY_OF_MONTH)
    val monthlyTargetBudget = dailyBurnCeiling * daysInMonth
    val burnProgress = if (monthlyTargetBudget > 0.0) {
        (totalPeriodSpent / monthlyTargetBudget).toFloat().coerceIn(0f, 1f)
    } else 0f

    val runwayDays = if (dailyBurnCeiling > 0.0) {
        (trueSafeLiquid / dailyBurnCeiling).toLong()
    } else 0L

    val taxDeductibleTotal = remember(filteredRecords) {
        filteredRecords.filter { it.isTaxDeductible }.sumOf { it.amount }
    }
    val reimbursableTotal = remember(filteredRecords) {
        filteredRecords.filter { it.isReimbursable }.sumOf { it.amount }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp)
    ) {
        // Horizon Time Rail
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.surfaceAlt)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TimeHorizon.values().forEach { horizon ->
                    val isSel = selectedHorizon == horizon
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) theme.accent else Color.Transparent)
                            .clickable { selectedHorizon = horizon }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = horizon.label,
                            color = if (isSel) theme.bg else theme.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 1. Net Capital & Equity Position Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable { showCapitalStructureModal = true }
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "NET WORTH & CAPITAL STRUCTURE",
                            color = theme.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (netCapitalPosition >= 0.0) theme.mildGreen.copy(alpha = 0.18f) else theme.mildRed.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (netCapitalPosition >= 0.0) "Solvent" else "Deficit Alert",
                                color = if (netCapitalPosition >= 0.0) theme.mildGreen else theme.mildRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", netCapitalPosition)}",
                        color = theme.textBright,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isPrivacyMode) "Assets: ₹ •••" else "Assets: ₹${String.format("%,.0f", grossAssets)}",
                            color = theme.mildGreen,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isPrivacyMode) "Dues: ₹ •••" else "External Dues: ₹${String.format("%,.0f", grossLiabilities)}",
                            color = theme.mildRed,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { equityRatio / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = theme.mildGreen,
                        trackColor = theme.mildRed.copy(alpha = 0.5f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${String.format("%.1f", equityRatio)}% Equity Ownership",
                            color = theme.textMuted,
                            fontSize = 10.5.sp
                        )
                        Text(
                            "Tap for balance sheet audit →",
                            color = theme.accent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Liquid Burn Pacing & Runway Horizon
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "RUNWAY PACING & VELOCITY",
                            color = theme.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            if (runwayDays > 30) "Comfortable" else "Tight",
                            color = if (runwayDays > 30) theme.mildGreen else theme.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Safe Liquid Runway", color = theme.textMuted, fontSize = 10.5.sp)
                            Text(
                                if (isPrivacyMode) "•• Days" else "$runwayDays Days",
                                color = theme.accent,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Today's Burn", color = theme.textMuted, fontSize = 10.5.sp)
                            Text(
                                if (isPrivacyMode) "₹ •••" else "₹${String.format("%,.0f", todayBurn)}",
                                color = if (todayBurn > dailyBurnCeiling) theme.mildRed else theme.textBright,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { burnProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = if (burnProgress > 0.85f) theme.mildRed else theme.accent,
                        trackColor = theme.surfaceAlt
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (isPrivacyMode) "Spent: ₹ •••" else "Spent: ₹${String.format("%,.0f", totalPeriodSpent)} / ₹${String.format("%,.0f", monthlyTargetBudget)} Target",
                            color = theme.textMuted,
                            fontSize = 10.5.sp
                        )
                        Text(
                            "Benchmark: ₹${dailyBurnCeiling.toInt()}/day",
                            color = theme.textMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 3. Spending Envelopes Breakdown
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "SPENDING ENVELOPES",
                            color = theme.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            if (isPrivacyMode) "₹ •••" else "Total: ₹${String.format("%,.0f", totalPeriodSpent)}",
                            color = theme.textBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (categoryEnvelopes.isEmpty()) {
                        Text(
                            text = "No outflow transactions registered for this period.",
                            color = theme.textMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        val maxAmt = (categoryEnvelopes.firstOrNull()?.second ?: 1.0).coerceAtLeast(1.0)

                        categoryEnvelopes.forEach { (category, amt) ->
                            val pct = if (totalPeriodSpent > 0.0) ((amt / totalPeriodSpent) * 100.0) else 0.0
                            val barRatio = (amt / maxAmt).toFloat().coerceIn(0f, 1f)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedEnvelopeCategory = category }
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(category, color = theme.textBright, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text("(${String.format("%.0f", pct)}%)", color = theme.textMuted, fontSize = 10.5.sp)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", amt)}",
                                            color = theme.textBright,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { barRatio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(CircleShape),
                                    color = theme.accent,
                                    trackColor = theme.surfaceAlt
                                )
                            }
                            HorizontalDivider(color = theme.borderLight.copy(alpha = 0.4f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // 4. Statutory Deductions & Claims
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable { showTaxDeductionsModal = true }
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "TAX DEDUCTIONS & CLAIMS (INDIA ITR)",
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
                            Text("🏷 Section 80C Tagged", color = theme.textMuted, fontSize = 11.sp)
                            Text(
                                if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", taxDeductibleTotal)}",
                                color = theme.accent,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("💼 Corporate Claims", color = theme.textMuted, fontSize = 11.sp)
                            Text(
                                if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", reimbursableTotal)}",
                                color = theme.mildGreen,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text("Tap to audit schedules & export CSV →", color = theme.accent, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Modal 1: Category Drill-Down Voucher
    selectedEnvelopeCategory?.let { cat ->
        val txList = periodExpenses.filter { it.category.equals(cat, ignoreCase = true) }
        CenteredCategoryDrilldownModal(
            categoryName = cat,
            transactions = txList,
            totalSpent = txList.sumOf { it.amount },
            theme = theme,
            onDismiss = { selectedEnvelopeCategory = null }
        )
    }

    // Modal 2: Capital Structure Audit Voucher
    if (showCapitalStructureModal) {
        CenteredCapitalStructureModal(
            grossAssets = grossAssets,
            grossLiabilities = grossLiabilities,
            netWorth = netCapitalPosition,
            theme = theme,
            onDismiss = { showCapitalStructureModal = false }
        )
    }

    // Modal 3: Tax Deductions Audit Voucher
    if (showTaxDeductionsModal) {
        CenteredTaxAuditModal(
            taxRecords = filteredRecords.filter { it.isTaxDeductible },
            reimbursableRecords = filteredRecords.filter { it.isReimbursable },
            theme = theme,
            onDismiss = { showTaxDeductionsModal = false }
        )
    }
}

@Composable
private fun CenteredCategoryDrilldownModal(
    categoryName: String,
    transactions: List<LedgerTransaction>,
    totalSpent: Double,
    theme: ThemeColors,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 400.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(categoryName, color = theme.textBright, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "₹ ${String.format("%,.0f", totalSpent)}",
                            color = theme.mildRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text("Major Outflow Drivers for this Period", color = theme.textMuted, fontSize = 11.sp)
                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(transactions.take(15), key = { it.id }) { tx ->
                            val dStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(tx.timestamp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.surfaceAlt)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.description.ifBlank { tx.category }, color = theme.textBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(dStr, color = theme.textMuted, fontSize = 10.sp)
                                }
                                Text("- ₹ ${String.format("%,.0f", tx.amount)}", color = theme.mildRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Close", color = theme.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredCapitalStructureModal(
    grossAssets: Double,
    grossLiabilities: Double,
    netWorth: Double,
    theme: ThemeColors,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 400.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("CAPITAL STRUCTURE AUDIT", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Balance sheet valuation across all active ledger pockets.", color = theme.textMuted, fontSize = 11.sp)
                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Owned Assets", color = theme.textBright, fontSize = 12.5.sp)
                            Text("₹ ${String.format("%,.0f", grossAssets)}", color = theme.mildGreen, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total External Liabilities", color = theme.textBright, fontSize = 12.5.sp)
                            Text("- ₹ ${String.format("%,.0f", grossLiabilities)}", color = theme.mildRed, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Net Proprietor Capital", color = theme.accent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text("₹ ${String.format("%,.0f", netWorth)}", color = theme.textBright, fontSize = 13.5.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Close", color = theme.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredTaxAuditModal(
    taxRecords: List<LedgerTransaction>,
    reimbursableRecords: List<LedgerTransaction>,
    theme: ThemeColors,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 400.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("STATUTORY TAX & CLAIMS AUDIT", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Tagging verified for Income Tax Return schedules.", color = theme.textMuted, fontSize = 11.sp)
                    HorizontalDivider(color = theme.borderLight, thickness = 0.5.dp)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Section 80C Tagged (${taxRecords.size} records)", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        taxRecords.forEach { tx ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(tx.description.ifBlank { tx.category }, color = theme.textBright, fontSize = 11.5.sp)
                                Text("₹${String.format("%,.0f", tx.amount)}", color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text("Pending Reimbursable Claims (${reimbursableRecords.size} records)", color = theme.mildGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        reimbursableRecords.forEach { tx ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(tx.description.ifBlank { tx.category }, color = theme.textBright, fontSize = 11.5.sp)
                                Text("₹${String.format("%,.0f", tx.amount)}", color = theme.mildGreen, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Close", color = theme.textMuted)
                    }
                }
            }
        }
    }
}
