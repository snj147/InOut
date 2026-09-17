package com.personal.inout.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.AccountBalanceResult
import com.personal.inout.data.AccountClassification
import com.personal.inout.data.LedgerAccount
import com.personal.inout.data.LedgerTransaction
import kotlin.math.roundToInt

@Composable
fun IntelligenceScreen(
    accountsWithBalances: List<AccountBalanceResult>,
    rawAccounts: List<LedgerAccount>,
    transactions: List<LedgerTransaction>,
    isProUser: Boolean,
    onUnlockPro: () -> Unit,
    onGeneratePdfDossier: () -> Unit
) {
    val theme = LocalThemeColors.current

    val totalAssets = remember(accountsWithBalances) {
        accountsWithBalances
            .filter { it.classification == AccountClassification.ASSET || it.subType == "BORROWER" }
            .sumOf { it.netBalance }
    }

    val totalLiabilities = remember(accountsWithBalances) {
        accountsWithBalances
            .filter { it.classification == AccountClassification.LIABILITY || it.subType == "LENDER" }
            .sumOf { it.netBalance }
    }

    val netWorth = totalAssets - totalLiabilities

    var extraMonthlyPayoff by remember { mutableStateOf(2000f) }
    var payoffStrategy by remember { mutableStateOf("SNOWBALL") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        // --- 1. Net Worth Header Card ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TRUE NET WORTH",
                            color = theme.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(theme.accent.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "Live Assets - Debts",
                                color = theme.accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "₹ ${String.format("%,.0f", netWorth)}",
                        color = if (netWorth >= 0) theme.mildGreen else theme.mildRed,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.surfaceAlt)
                                .padding(10.dp)
                        ) {
                            Text("Total Assets", color = theme.textMuted, fontSize = 10.sp)
                            Text(
                                "₹ ${String.format("%,.0f", totalAssets)}",
                                color = theme.mildGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.surfaceAlt)
                                .padding(10.dp)
                        ) {
                            Text("Total Liabilities", color = theme.textMuted, fontSize = 10.sp)
                            Text(
                                "₹ ${String.format("%,.0f", totalLiabilities)}",
                                color = theme.mildRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    NetWorthCurveCanvas(theme = theme)
                }
            }
        }

        // --- 2. Interactive Debt Payoff Simulator ---
        item {
            val debts = remember(accountsWithBalances) {
                accountsWithBalances.filter {
                    (it.classification == AccountClassification.LIABILITY || it.subType == "LENDER") &&
                    it.netBalance > 0
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                            Text("Debt Payoff Simulator", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${debts.size} Active Debts",
                            color = theme.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (debts.isEmpty()) {
                        Text(
                            "You are currently 100% debt-free! No active loans or credit card dues to simulate.",
                            color = theme.mildGreen,
                            fontSize = 12.sp
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.surfaceAlt)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("SNOWBALL" to "Snowball (Smallest First)", "AVALANCHE" to "Avalanche (High Balance)").forEach { (key, label) ->
                                val isSel = payoffStrategy == key
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) theme.accent else Color.Transparent)
                                        .clickable { payoffStrategy = key }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) theme.bg else theme.textMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Extra Monthly Cash Allocated:", color = theme.textMuted, fontSize = 11.sp)
                                Text(
                                    "+ ₹ ${extraMonthlyPayoff.roundToInt()}",
                                    color = theme.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Slider(
                                value = extraMonthlyPayoff,
                                onValueChange = { extraMonthlyPayoff = it },
                                valueRange = 500f..25000f,
                                steps = 48,
                                colors = SliderDefaults.colors(
                                    thumbColor = theme.accent,
                                    activeTrackColor = theme.accent,
                                    inactiveTrackColor = theme.surfaceAlt
                                )
                            )
                        }

                        val monthsBase = (totalLiabilities / 3000.0).coerceAtLeast(1.0).roundToInt()
                        val monthsAccelerated = (totalLiabilities / (3000.0 + extraMonthlyPayoff)).coerceAtLeast(1.0).roundToInt()
                        val monthsSaved = (monthsBase - monthsAccelerated).coerceAtLeast(0)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.mildGreen.copy(alpha = 0.12f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Projected Freedom Date", color = theme.textBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                Text("Clear all dues in ~$monthsAccelerated months", color = theme.mildGreen, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.mildGreen)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("-$monthsSaved Months", color = theme.bg, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Accountant-Ready Dossier Banner ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Accountant PDF Dossier", color = theme.textBright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (!isProUser) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(theme.accent)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("PRO", color = theme.bg, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        Text(
                            "Export balanced audit sheet with attached receipt scans and tax tags.",
                            color = theme.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Button(
                        onClick = {
                            if (isProUser) onGeneratePdfDossier() else onUnlockPro()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = theme.bg, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export", color = theme.bg, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NetWorthCurveCanvas(theme: ThemeColors) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(0f, h * 0.85f)
            cubicTo(w * 0.25f, h * 0.70f, w * 0.40f, h * 0.80f, w * 0.60f, h * 0.40f)
            cubicTo(w * 0.75f, h * 0.15f, w * 0.90f, h * 0.30f, w, h * 0.10f)
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(theme.accent, theme.mildGreen)),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        drawCircle(
            color = theme.mildGreen,
            radius = 4.dp.toPx(),
            center = Offset(w, h * 0.10f)
        )
    }
}
