package com.personal.inout.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.FlowRecord
import com.personal.inout.data.MovementNature
import com.personal.inout.data.PocketBalanceSummary
import com.personal.inout.data.PocketType
import kotlin.math.max

enum class CockpitDisplayMode {
    SURVIVAL_DAYS_SLIDER,
    CASH_VS_DEBT_RADAR,
    WEEKLY_SPEND_PULSE
}

@Composable
fun DynamicCockpit(
    pockets: List<PocketBalanceSummary>,
    flows: List<FlowRecord>,
    mode: CockpitDisplayMode,
    configuredDailyBurn: Double,
    isPrivacyMode: Boolean
) {
    val theme = LocalThemeColors.current

    val totalLiquid = remember(pockets) {
        pockets.filter { it.pocketType == PocketType.LIQUID }.sumOf { it.currentBalance }.coerceAtLeast(0.0)
    }
    val totalDues = remember(pockets) {
        pockets.filter { it.pocketType == PocketType.CREDIT_LINE }.sumOf { it.currentBalance }.coerceAtLeast(0.0)
    }

    val dailyPace = if (configuredDailyBurn > 0) configuredDailyBurn else 500.0
    val maxAvailableDays = if (dailyPace > 0) ((totalLiquid - totalDues).coerceAtLeast(0.0) / dailyPace).toInt() else 0

    var selectedSurvivalDayTarget by remember { mutableFloatStateOf(maxAvailableDays.toFloat().coerceAtMost(90f)) }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (mode) {
                CockpitDisplayMode.SURVIVAL_DAYS_SLIDER -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("RUNWAY SURVIVAL HORIZON", color = theme.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                            val daysText = if (isPrivacyMode) "•• Days" else "$maxAvailableDays Days"
                            Text(daysText, color = theme.textBright, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            Text("Cash cushion at ₹${dailyPace.toInt()}/day burn", color = theme.textMuted, fontSize = 10.sp)
                        }

                        val reserveRatio = if (totalLiquid > 0) ((totalLiquid - totalDues) / totalLiquid).toFloat().coerceIn(0f, 1f) else 0f
                        BatteryCanvas(ratio = reserveRatio, theme = theme)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Survival Plan Slider", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            val planCashNeeded = (selectedSurvivalDayTarget * dailyPace).toInt()
                            Text("Target: ${selectedSurvivalDayTarget.toInt()} Days (₹$planCashNeeded)", color = theme.textBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Slider(
                            value = selectedSurvivalDayTarget,
                            onValueChange = { selectedSurvivalDayTarget = it },
                            valueRange = 0f..90f,
                            colors = SliderDefaults.colors(
                                thumbColor = theme.accent,
                                activeTrackColor = theme.accent,
                                inactiveTrackColor = theme.surfaceAlt
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(7, 15, 30, 60, 90).forEach { dayStep ->
                                Text(
                                    "${dayStep}d",
                                    color = if (selectedSurvivalDayTarget.toInt() == dayStep) theme.accent else theme.textMuted,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { selectedSurvivalDayTarget = dayStep.toFloat() }
                                )
                            }
                        }
                    }
                }

                CockpitDisplayMode.CASH_VS_DEBT_RADAR -> {
                    Text("CASH VS DEBT HORIZON", color = theme.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Cash & Bank", color = theme.textMuted, fontSize = 11.sp)
                            Text(if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", totalLiquid)}", color = theme.mildGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Cards & Loans", color = theme.textMuted, fontSize = 11.sp)
                            Text(if (isPrivacyMode) "₹ •••" else "₹ ${String.format("%,.0f", totalDues)}", color = theme.mildRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    val netAvailable = (totalLiquid - totalDues).coerceAtLeast(0.0)
                    LinearProgressIndicator(
                        progress = if (totalLiquid + totalDues > 0) (totalLiquid / (totalLiquid + totalDues)).toFloat() else 1f,
                        color = theme.mildGreen,
                        trackColor = theme.mildRed,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Text("Net Solvent Headroom: ₹${String.format("%,.0f", netAvailable)}", color = theme.accent, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                }

                CockpitDisplayMode.WEEKLY_SPEND_PULSE -> {
                    Text("DAILY SPENDING EQUALIZER (LAST 7 DAYS)", color = theme.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    val last7DaysSpend = remember(flows) {
                        val dayBuckets = DoubleArray(7) { 0.0 }
                        val now = System.currentTimeMillis()
                        flows.filter { it.nature == MovementNature.OUTFLOW }.forEach { f ->
                            val diffDays = ((now - f.timestamp) / (1000 * 60 * 60 * 24)).toInt()
                            if (diffDays in 0..6) {
                                dayBuckets[6 - diffDays] += f.amount
                            }
                        }
                        dayBuckets.toList()
                    }
                    val maxVal = max(last7DaysSpend.maxOrNull() ?: 1.0, 100.0).toFloat()
                    Row(
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        last7DaysSpend.forEachIndexed { idx, amt ->
                            val hRatio = (amt.toFloat() / maxVal).coerceIn(0.08f, 1f)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 3.dp)
                                    .fillMaxHeight(hRatio)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(theme.accent.copy(alpha = if (idx == 6) 1f else 0.55f))
                            )
                        }
                    }
                    Text("Today's burn: ₹${last7DaysSpend.last().toInt()}", color = theme.textBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BatteryCanvas(ratio: Float, theme: ThemeColors) {
    val animRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "cockpit_battery"
    )

    Canvas(modifier = Modifier.size(width = 68.dp, height = 28.dp)) {
        val stroke = 1.5.dp.toPx()
        val capW = 4.dp.toPx()
        val bodyW = size.width - capW - 2.dp.toPx()

        drawRoundRect(
            color = theme.accent.copy(alpha = 0.5f),
            size = Size(bodyW, size.height),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(stroke)
        )

        drawRoundRect(
            color = theme.accent.copy(alpha = 0.5f),
            topLeft = Offset(bodyW + 2.dp.toPx(), size.height * 0.25f),
            size = Size(capW, size.height * 0.5f),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
        )

        val fillW = (bodyW - 4.dp.toPx()) * animRatio
        if (fillW > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(theme.mildRed, theme.accent, theme.mildGreen)),
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(fillW, size.height - 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}
