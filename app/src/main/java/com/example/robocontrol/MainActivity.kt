package com.example.robocontrol

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.robocontrol.ui.theme.RoboControlTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var webSocketClient: RobotWebSocketClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force portrait orientation (90° clockwise from reverse landscape)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        // Initialize managers
        settingsManager = SettingsManager(this)
        webSocketClient = RobotWebSocketClient(lifecycleScope)

        // Full immersive mode
        enableEdgeToEdge()
        hideSystemUI()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Connect to WebSocket on startup
        lifecycleScope.launch {
            val settings = settingsManager.settings.first()
            webSocketClient.connect(settings.serverHost, settings.websocketPort)
        }

        setContent {
            RoboControlTheme {
                RoboControlApp(
                    settingsManager = settingsManager,
                    webSocketClient = webSocketClient
                )
            }
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = 
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketClient.disconnect()
    }
}

@Composable
fun RoboControlApp(
    settingsManager: SettingsManager,
    webSocketClient: RobotWebSocketClient
) {
    val settings by settingsManager.settings.collectAsState(initial = RobotSettings())
    val connectionState by webSocketClient.connectionState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        ControlScreen(
            settings = settings,
            connectionState = connectionState,
            onGestureAction = { action ->
                webSocketClient.sendMove(action.toDirection())
            },
            onOpenSettings = { showSettings = true }
        )

        if (showSettings) {
            SettingsScreen(
                currentSettings = settings,
                onSave = { host, wsPort, vidPort ->
                    scope.launch {
                        settingsManager.updateSettings(
                            serverHost = host,
                            websocketPort = wsPort,
                            videoPort = vidPort
                        )
                        // Reconnect with new settings
                        webSocketClient.disconnect()
                        webSocketClient.connect(host, wsPort)
                    }
                    showSettings = false
                },
                onDismiss = { showSettings = false }
            )
        }
    }
}
