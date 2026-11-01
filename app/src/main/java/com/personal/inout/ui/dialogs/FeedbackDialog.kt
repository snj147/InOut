package com.personal.inout.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.inout.BuildConfig
import com.personal.inout.ui.ThemeColors
import com.personal.inout.ui.components.CompactInputField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Composable
fun FeedbackDialog(
    theme: ThemeColors,
    onResult: (msg: String, isError: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var feedbackText by remember { mutableStateOf("") }
    var attachedUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val telegramBotToken = BuildConfig.TELEGRAM_BOT_TOKEN
    val telegramChatId = BuildConfig.TELEGRAM_CHAT_ID

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        attachedUri = uri
    }

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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SEND FEEDBACK", color = theme.textBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach File", tint = theme.accent)
                        }
                    }

                    if (attachedUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(theme.surfaceAlt)
                                .border(1.dp, theme.borderLight, RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("File Attached", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Icon(
                                Icons.Default.Close, 
                                contentDescription = "Remove", 
                                tint = theme.mildRed, 
                                modifier = Modifier.size(16.dp).clickable { attachedUri = null }
                            )
                        }
                    }

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
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isValid) {
                                    isSending = true
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val responseCode = if (attachedUri != null) {
                                                // Multipart upload for Telegram sendDocument
                                                val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
                                                val url = URL("https://api.telegram.org/bot$telegramBotToken/sendDocument")
                                                val connection = url.openConnection() as HttpURLConnection
                                                connection.requestMethod = "POST"
                                                connection.doOutput = true
                                                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                                                
                                                val outputStream = DataOutputStream(connection.outputStream)
                                                outputStream.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n$telegramChatId\r\n")
                                                outputStream.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"caption\"\r\n\r\n$feedbackText\r\n")
                                                
                                                context.contentResolver.openInputStream(attachedUri!!)?.use { inputStream ->
                                                    outputStream.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"document\"; filename=\"attachment\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                                                    inputStream.copyTo(outputStream)
                                                    outputStream.writeBytes("\r\n")
                                                }
                                                outputStream.writeBytes("--$boundary--\r\n")
                                                outputStream.flush()
                                                outputStream.close()
                                                connection.responseCode
                                            } else {
                                                // Standard text send via GET
                                                val encodedText = URLEncoder.encode(feedbackText, "UTF-8")
                                                val url = URL("https://api.telegram.org/bot$telegramBotToken/sendMessage?chat_id=$telegramChatId&text=$encodedText")
                                                val connection = url.openConnection() as HttpURLConnection
                                                connection.requestMethod = "GET"
                                                connection.responseCode
                                            }

                                            withContext(Dispatchers.Main) {
                                                if (responseCode in 200..299) {
                                                    onResult("Feedback sent.", false)
                                                    onDismiss() // Dialog closes instantly on success
                                                } else {
                                                    onResult("Error: HTTP $responseCode", true)
                                                    isSending = false
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                onResult("Network failure: ${e.localizedMessage}", true)
                                                isSending = false
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.3f).height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isValid) theme.accent else theme.borderLight),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isValid) theme.accent else Color.Transparent,
                                disabledContainerColor = Color.Transparent
                            ),
                            enabled = isValid
                        ) {
                            Text(
                                if (isSending) "Sending..." else "Send Feedback",
                                color = if (isValid) theme.bg else theme.textMuted.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { if (!isSending) onDismiss() },
                            modifier = Modifier.weight(1f).height(44.dp),
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
