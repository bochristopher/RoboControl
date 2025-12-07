package com.example.robocontrol

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ControlScreen(
    settings: RobotSettings,
    connectionState: ConnectionState,
    onGestureAction: (GestureAction) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentAction by remember { mutableStateOf(GestureAction.NONE) }
    var commandsSent by remember { mutableIntStateOf(0) }

    // Command sending loop
    LaunchedEffect(currentAction) {
        if (currentAction != GestureAction.NONE) {
            while (isActive && currentAction != GestureAction.NONE) {
                onGestureAction(currentAction)
                commandsSent++
                delay(100) // Send command every 100ms
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Video stream background (behind everything)
        MjpegStreamView(
            streamUrl = settings.videoStreamUrl,
            modifier = Modifier.fillMaxSize()
        )

        // Gesture area (covers most of screen but not top bar)
        GestureController(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp), // Leave space for top bar
            onActionChanged = { action ->
                currentAction = action
                if (action == GestureAction.NONE) {
                    // Immediately send stop
                    onGestureAction(GestureAction.NONE)
                }
            }
        ) {
            // Empty - gestures only, video is behind
        }

        // Top bar with status (OUTSIDE gesture controller - clickable!)
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

        // Debug info (bottom left)
        DebugInfo(
            connectionState = connectionState,
            commandsSent = commandsSent,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        // Gesture hints (bottom right)
        GestureHints(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
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
    action: GestureAction,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = action != GestureAction.NONE,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when (action) {
                        GestureAction.FORWARD -> Color(0xFF00AA44)
                        GestureAction.BACKWARD -> Color(0xFFAA4400)
                        GestureAction.LEFT -> Color(0xFF0066AA)
                        GestureAction.RIGHT -> Color(0xFF6600AA)
                        GestureAction.NONE -> Color.Transparent
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
fun GestureHints(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("↑ Swipe = Forward", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
        Text("↓ Swipe = Backward", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
        Text("1 Tap = Right", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
        Text("2 Tap = Left", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
    }
}

