package com.personal.inout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
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
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CustomCalendarDialog(
    initialDateMillis: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val theme = LocalThemeColors.current

    val calendar = remember { Calendar.getInstance().apply { timeInMillis = initialDateMillis } }
    var displayedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) }
    var selectedDayMillis by remember { mutableLongStateOf(initialDateMillis) }

    val daysInMonth = remember(displayedYear, displayedMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        firstDayOfWeek to maxDay
    }

    val monthName = remember(displayedMonth, displayedYear) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
        }
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = theme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .border(1.dp, theme.borderLight, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Month & Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (displayedMonth == 0) {
                            displayedMonth = 11
                            displayedYear -= 1
                        } else {
                            displayedMonth -= 1
                        }
                    }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Prev", tint = theme.accent)
                    }

                    Text(
                        text = monthName,
                        color = theme.textBright,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = {
                        if (displayedMonth == 11) {
                            displayedMonth = 0
                            displayedYear += 1
                        } else {
                            displayedMonth += 1
                        }
                    }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = theme.accent)
                    }
                }

                // Days of Week Header
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                        Text(
                            text = day,
                            color = theme.textMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Days Grid
                val (firstDayOfWeek, totalDays) = daysInMonth
                val emptySlotsBefore = firstDayOfWeek - 1
                val totalSlots = emptySlotsBefore + totalDays
                val rows = (totalSlots + 6) / 7

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0 until rows) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 7) {
                                val slotIndex = row * 7 + col
                                val dayNumber = slotIndex - emptySlotsBefore + 1

                                if (dayNumber in 1..totalDays) {
                                    val dayCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, displayedYear)
                                        set(Calendar.MONTH, displayedMonth)
                                        set(Calendar.DAY_OF_MONTH, dayNumber)
                                        set(Calendar.HOUR_OF_DAY, 12)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                    }
                                    val isSelected = isSameDay(dayCal.timeInMillis, selectedDayMillis)

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .background(if (isSelected) theme.accent else Color.Transparent)
                                            .clickable { selectedDayMillis = dayCal.timeInMillis },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$dayNumber",
                                            color = if (isSelected) theme.bg else theme.textBright,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = theme.textMuted)
                    }
                    Button(
                        onClick = { onDateSelected(selectedDayMillis) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                    ) {
                        Text("Select", color = theme.bg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun isSameDay(d1: Long, d2: Long): Boolean {
    val f = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return f.format(Date(d1)) == f.format(Date(d2))
}
