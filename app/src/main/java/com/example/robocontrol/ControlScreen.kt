package com.example.robocontrol

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ControlScreen(
    settings: RobotSettings,
    connectionState: ConnectionState,
    currentAction: RobotController.ControlAction,
    robotController: RobotController,
    voiceHandler: VoiceCommandHandler?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var commandsSent by remember { mutableIntStateOf(0) }
    
    // Voice state
    val isListening = voiceHandler?.isListening?.collectAsState()?.value ?: false
    val recognizedText = voiceHandler?.recognizedText?.collectAsState()?.value
    val lastCommand = voiceHandler?.lastCommand?.collectAsState()?.value
    val errorMessage = voiceHandler?.errorMessage?.collectAsState()?.value
    val isModelLoaded = voiceHandler?.isModelLoaded?.collectAsState()?.value ?: false
    val isLoading = voiceHandler?.isLoading?.collectAsState()?.value ?: false

    // Count commands when action changes
    LaunchedEffect(currentAction) {
        if (currentAction != RobotController.ControlAction.NONE) {
            while (isActive && currentAction != RobotController.ControlAction.NONE) {
                commandsSent++
                delay(100)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Video stream background
        MjpegStreamView(
            streamUrl = settings.videoStreamUrl,
            modifier = Modifier.fillMaxSize()
        )

        // Top bar with status (clickable for settings)
        TopStatusBar(
            connectionState = connectionState,
            settings = settings,
            onSettingsClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        )

        // Current action display
        ActionDisplay(
            action = currentAction,
            modifier = Modifier.align(Alignment.Center)
        )

        // Voice feedback overlay (above center)
        VoiceFeedbackOverlay(
            isListening = isListening,
            isLoading = isLoading,
            recognizedText = recognizedText,
            lastCommand = lastCommand,
            errorMessage = errorMessage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
        )

        // Debug info (bottom left)
        DebugInfo(
            connectionState = connectionState,
            commandsSent = commandsSent,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        // Control hints (bottom right)
        ControlHints(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        // Touch controls for screen tap (fallback)
        TouchControls(
            robotController = robotController,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )

        // Mic button (top right, below settings)
        MicButton(
            isListening = isListening,
            isModelLoaded = isModelLoaded,
            isLoading = isLoading,
            onMicClick = {
                if (isListening) {
                    voiceHandler?.stopListening()
                } else {
                    voiceHandler?.startListening()
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 16.dp)
        )
    }
}

@Composable
fun MicButton(
    isListening: Boolean,
    isModelLoaded: Boolean,
    isLoading: Boolean,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulsing animation when listening or loading
    var pulseAlpha by remember { mutableFloatStateOf(1f) }
    
    if (isListening || isLoading) {
        LaunchedEffect(isListening, isLoading) {
            while (isActive) {
                pulseAlpha = 0.5f
                delay(300)
                pulseAlpha = 1f
                delay(300)
            }
        }
    } else {
        pulseAlpha = 1f
    }

    val backgroundColor = when {
        isListening -> Color(0xFFFF4444).copy(alpha = pulseAlpha)
        isLoading -> Color(0xFFFFAA00).copy(alpha = pulseAlpha * 0.6f)
        isModelLoaded -> Color(0xFF00D9FF).copy(alpha = 0.4f)
        else -> Color.Gray.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(enabled = isModelLoaded && !isLoading) { onMicClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                isLoading -> "⏳"
                isListening -> "🎤"
                isModelLoaded -> "🎙"
                else -> "🎙"
            },
            fontSize = 28.sp,
            color = if (isModelLoaded || isLoading) Color.Unspecified else Color.Gray
        )
    }
}

@Composable
fun VoiceFeedbackOverlay(
    isListening: Boolean,
    isLoading: Boolean,
    recognizedText: String?,
    lastCommand: String?,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isListening || isLoading || recognizedText != null || lastCommand != null || errorMessage != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            when {
                isLoading -> {
                    Text(
                        text = "⏳ Loading voice model...",
                        color = Color(0xFFFFAA00),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "First time setup",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                isListening -> {
                    Text(
                        text = "🎤 Listening...",
                        color = Color(0xFFFF4444),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (recognizedText != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "\"$recognizedText\"",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                lastCommand != null -> {
                    Text(
                        text = "✓ ${lastCommand.uppercase()}",
                        color = Color(0xFF00FF88),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (recognizedText != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"$recognizedText\"",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
                errorMessage != null -> {
                    Text(
                        text = "⚠ $errorMessage",
                        color = Color(0xFFFFAA00),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    
    // Auto-hide after showing command/error
    LaunchedEffect(lastCommand, errorMessage) {
        if (lastCommand != null || errorMessage != null) {
            delay(2000)
        }
    }
}

@Composable
fun TopStatusBar(
    connectionState: ConnectionState,
    settings: RobotSettings,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Connection status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConnectionIndicator(connectionState)
            Text(
                text = when (connectionState) {
                    ConnectionState.DISCONNECTED -> "Disconnected"
                    ConnectionState.CONNECTING -> "Connecting..."
                    ConnectionState.CONNECTED -> "Connected"
                    ConnectionState.AUTHENTICATED -> "Ready"
                },
                color = Color.White,
                fontSize = 14.sp
            )
        }

        // Server info
        Text(
            text = settings.serverHost,
            color = Color.Gray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        // Settings button - larger touch target
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF00D9FF).copy(alpha = 0.3f))
                .clickable { onSettingsClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚙",
                fontSize = 28.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun ConnectionIndicator(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.DISCONNECTED -> Color.Red
        ConnectionState.CONNECTING -> Color.Yellow
        ConnectionState.CONNECTED -> Color(0xFFFFAA00)
        ConnectionState.AUTHENTICATED -> Color(0xFF00FF88)
    }

    // Pulsing animation for connecting state
    var alpha by remember { mutableFloatStateOf(1f) }
    
    if (state == ConnectionState.CONNECTING) {
        LaunchedEffect(Unit) {
            while (isActive) {
                alpha = 0.3f
                delay(500)
                alpha = 1f
                delay(500)
            }
        }
    } else {
        alpha = 1f
    }

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun ActionDisplay(
    action: RobotController.ControlAction,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = action != RobotController.ControlAction.NONE,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when (action) {
                        RobotController.ControlAction.FORWARD -> Color(0xFF00AA44)
                        RobotController.ControlAction.BACKWARD -> Color(0xFFAA4400)
                        RobotController.ControlAction.LEFT -> Color(0xFF0066AA)
                        RobotController.ControlAction.RIGHT -> Color(0xFF6600AA)
                        RobotController.ControlAction.NONE -> Color.Transparent
                    }.copy(alpha = 0.85f)
                )
                .padding(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Text(
                text = action.toDisplayText(),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun DebugInfo(
    connectionState: ConnectionState,
    commandsSent: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(8.dp)
    ) {
        Text(
            text = "Commands: $commandsSent",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ControlHints(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("Controls:", color = Color(0xFF00D9FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("Swipe ← = Left", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
        Text("Swipe → = Right", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
        Text("Hold = Forward", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
        Text("Double Tap = Back", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
        Text("🎙 = Voice", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
    }
}

/**
 * On-screen touch buttons as fallback controls
 */
@Composable
fun TouchControls(
    robotController: RobotController,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left button
        DirectionButton(
            text = "◄",
            color = Color(0xFF0066AA),
            onPress = { robotController.startAction(RobotController.ControlAction.LEFT) },
            onRelease = { robotController.stopAction() }
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Forward button
            DirectionButton(
                text = "▲",
                color = Color(0xFF00AA44),
                onPress = { robotController.startAction(RobotController.ControlAction.FORWARD) },
                onRelease = { robotController.stopAction() }
            )

            // Backward button
            DirectionButton(
                text = "▼",
                color = Color(0xFFAA4400),
                onPress = { robotController.startAction(RobotController.ControlAction.BACKWARD) },
                onRelease = { robotController.stopAction() }
            )
        }

        // Right button
        DirectionButton(
            text = "►",
            color = Color(0xFF6600AA),
            onPress = { robotController.startAction(RobotController.ControlAction.RIGHT) },
            onRelease = { robotController.stopAction() }
        )
    }
}

@Composable
fun DirectionButton(
    text: String,
    color: Color,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(
                if (isPressed) color else color.copy(alpha = 0.5f)
            )
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                // Toggle behavior for simple taps
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when {
                            event.changes.any { it.pressed } && !isPressed -> {
                                isPressed = true
                                onPress()
                            }
                            event.changes.none { it.pressed } && isPressed -> {
                                isPressed = false
                                onRelease()
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
