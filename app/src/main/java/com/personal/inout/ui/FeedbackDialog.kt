package com.personal.inout.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.inout.util.TelegramFeedbackSender
import kotlinx.coroutines.launch

@Composable
fun FeedbackDialog(
    theme: ThemeColors,
    onShowAlert: (message: String, isError: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedImageUri = uri
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        containerColor = theme.surface,
        title = {
            Text(
                text = "Send Feedback / Bug Report",
                color = theme.textBright,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Report unexpected behavior or suggest workflow enhancements. Screenshots help diagnose layout or calculation issues immediately.",
                    color = theme.textMuted,
                    fontSize = 11.5.sp
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text("What happened?", color = theme.textMuted.copy(alpha = 0.5f), fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.borderLight,
                        focusedTextColor = theme.textBright,
                        unfocusedTextColor = theme.textBright,
                        cursorColor = theme.accent,
                        focusedContainerColor = theme.surfaceAlt,
                        unfocusedContainerColor = theme.surfaceAlt
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                if (selectedImageUri == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceAlt)
                            .border(1.dp, theme.borderLight, RoundedCornerShape(8.dp))
                            .clickable {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                            Text("Attach Screenshot", color = theme.accent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.mildGreen.copy(alpha = 0.15f))
                            .border(1.dp, theme.mildGreen.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Screenshot attached ✓", color = theme.mildGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { selectedImageUri = null }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = theme.mildRed, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (message.isBlank()) {
                        onShowAlert("Please add a brief description", true)
                        return@Button
                    }
                    scope.launch {
                        isSending = true
                        val res = TelegramFeedbackSender.sendFeedback(
                            context = context,
                            userMessage = message.trim(),
                            imageUri = selectedImageUri
                        )
                        isSending = false
                        if (res.isSuccess) {
                            onShowAlert("Feedback delivered to developer! Thank you.", false)
                            onDismiss()
                        } else {
                            onShowAlert("Failed to send: ${res.exceptionOrNull()?.message}", true)
                        }
                    }
                },
                enabled = !isSending && message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(color = theme.bg, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = theme.bg, modifier = Modifier.size(14.dp))
                        Text("Send", color = theme.bg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) {
                Text("Cancel", color = theme.textMuted)
            }
        }
    )
}
