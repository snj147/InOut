package com.personal.inout.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.inout.ui.ThemeColors
import com.personal.inout.ui.components.CompactInputField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FeedbackDialog(
    theme: ThemeColors,
    onResult: (msg: String, isError: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var feedbackText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { 
                    if (!isSending) onDismiss() 
                },
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 380.dp)
                    .border(1.dp, theme.borderLight, RoundedCornerShape(14.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("SEND FEEDBACK", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Report bugs or request features. Diagnostics are sent anonymously.", color = theme.textMuted, fontSize = 11.sp)

                    CompactInputField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = "Describe your issue...",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )

                    val isValid = feedbackText.isNotBlank() && !isSending

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isValid) {
                                    isSending = true
                                    scope.launch {
                                        delay(800) // Simulate network call
                                        onResult("Feedback transmitted securely.", false)
                                        isSending = false
                                        onDismiss() // FIX: Dialog now explicitly closes itself
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isValid) theme.accent else Color.Transparent,
                                disabledContainerColor = Color.Transparent
                            ),
                            enabled = isValid
                        ) {
                            Text(
                                if (isSending) "Sending..." else "Transmit",
                                color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { if (!isSending) onDismiss() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderLight)
                        ) {
                            Text("Cancel", color = theme.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
