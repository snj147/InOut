package com.personal.inout.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class AlertType {
    ERROR,
    WARNING,
    SUCCESS,
    INFO
}

data class VaultAlert(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val type: AlertType = AlertType.INFO,
    val durationMillis: Long = 3200L
)

class VaultAlertManager {
    var activeAlert by mutableStateOf<VaultAlert?>(null)
        private set

    fun showAlert(message: String, type: AlertType = AlertType.INFO, durationMillis: Long = 3200L) {
        activeAlert = VaultAlert(message = message, type = type, durationMillis = durationMillis)
    }

    fun dismiss() {
        activeAlert = null
    }
}

val LocalVaultAlertManager = staticCompositionLocalOf { VaultAlertManager() }

@Composable
fun VaultFloatingTopOverlay(alertManager: VaultAlertManager, theme: ThemeColors) {
    val alert = alertManager.activeAlert

    LaunchedEffect(alert?.id) {
        if (alert != null) {
            delay(alert.durationMillis)
            alertManager.dismiss()
        }
    }

    AnimatedVisibility(
        visible = alert != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        if (alert != null) {
            val (bgColor, borderColor, textColor, icon) = when (alert.type) {
                AlertType.ERROR -> Quadruple(Color(0xFF2B1414), theme.mildRed, theme.mildRed, Icons.Default.Warning)
                AlertType.WARNING -> Quadruple(Color(0xFF261D12), theme.accent, theme.accent, Icons.Default.Warning)
                AlertType.SUCCESS -> Quadruple(Color(0xFF132517), theme.mildGreen, theme.mildGreen, Icons.Default.CheckCircle)
                AlertType.INFO -> Quadruple(theme.surface, theme.accent.copy(alpha = 0.5f), theme.textBright, Icons.Default.Info)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = alert.message,
                    color = textColor,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
