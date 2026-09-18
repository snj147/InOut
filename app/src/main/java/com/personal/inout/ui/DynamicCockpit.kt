package com.personal.inout.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.data.PocketBalanceSummary
import com.personal.inout.data.PocketType

@Composable
fun DynamicCockpit(
    pockets: List<PocketBalanceSummary>,
    isPrivacyMode: Boolean
) {
    val theme = LocalThemeColors.current

    val totalLiquid = remember(pockets) {
        pockets.filter { it.pocketType == PocketType.LIQUID }.sumOf { it.currentBalance }.coerceAtLeast(0.0)
    }

    val totalDues = remember(pockets) {
        pockets.filter { it.pocketType == PocketType.CREDIT_LINE }.sumOf { it.currentBalance }.coerceAtLeast(0.0)
    }

    var whatIfSpend by remember { mutableFloatStateOf(0f) }
    val effectiveLiquid = (totalLiquid - totalDues - whatIfSpend).coerceAtLeast(0.0)
    val dailyBurnPace = 420.0
    val safeRunwayDays = if (dailyBurnPace > 0) (effectiveLiquid / dailyBurnPace).toInt() else 0

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("LIQUID RUNWAY VELOCITY", color = theme.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    val daysText = if (isPrivacyMode) "•• Days" else "$safeRunwayDays Days"
                    Text(daysText, color = theme.textBright, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("Zero-debt survival cushion", color = theme.textMuted, fontSize = 10.sp)
                }

                Box(contentAlignment = Alignment.Center) {
                    val reserveRatio = if (totalLiquid > 0) (effectiveLiquid / totalLiquid).toFloat().coerceIn(0f, 1f) else 0f
                    BatteryCanvas(ratio = reserveRatio, theme = theme)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = theme.accent, modifier = Modifier.size(12.dp))
                        Text("What-If Expense Simulation", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(if (whatIfSpend > 0f) "- ₹${whatIfSpend.toInt()}" else "Dial neutral", color = if (whatIfSpend > 0f) theme.mildRed else theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = whatIfSpend,
                    onValueChange = { whatIfSpend = it },
                    valueRange = 0f..50000f,
                    colors = SliderDefaults.colors(
                        thumbColor = theme.accent,
                        activeTrackColor = theme.accent,
                        inactiveTrackColor = theme.surfaceAlt
                    )
                )
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
