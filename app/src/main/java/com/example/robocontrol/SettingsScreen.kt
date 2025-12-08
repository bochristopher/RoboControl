package com.example.robocontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentSettings: RobotSettings,
    onSave: (String, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var serverHost by remember { mutableStateOf(currentSettings.serverHost) }
    var websocketPort by remember { mutableStateOf(currentSettings.websocketPort.toString()) }
    var videoPort by remember { mutableStateOf(currentSettings.videoPort.toString()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(400.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2E)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "⚙️ Connection Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00D9FF)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Server Host
                OutlinedTextField(
                    value = serverHost,
                    onValueChange = { serverHost = it },
                    label = { Text("Server IP Address", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00D9FF),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00D9FF)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                // WebSocket Port
                OutlinedTextField(
                    value = websocketPort,
                    onValueChange = { websocketPort = it.filter { c -> c.isDigit() } },
                    label = { Text("WebSocket Port", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00D9FF),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00D9FF)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Video Port
                OutlinedTextField(
                    value = videoPort,
                    onValueChange = { videoPort = it.filter { c -> c.isDigit() } },
                    label = { Text("Video Stream Port", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00D9FF),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF00D9FF)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Preview URLs
                Text(
                    text = "WebSocket: ws://$serverHost:$websocketPort",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "Video: http://$serverHost:$videoPort/stream",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Gray
                        )
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val wsPort = websocketPort.toIntOrNull() ?: 8765
                            val vidPort = videoPort.toIntOrNull() ?: 8080
                            onSave(serverHost, wsPort, vidPort)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00D9FF)
                        )
                    ) {
                        Text("Save & Connect", color = Color.Black)
                    }
                }
            }
        }
    }
}

