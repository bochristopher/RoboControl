package com.example.robocontrol

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
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
    private lateinit var robotController: RobotController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force portrait orientation (90° clockwise from reverse landscape)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        // Initialize managers
        settingsManager = SettingsManager(this)
        webSocketClient = RobotWebSocketClient(lifecycleScope)
        
        // Initialize robot controller with WebSocket command sender
        robotController = RobotController(lifecycleScope) { direction ->
            webSocketClient.sendMove(direction)
        }

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
                    webSocketClient = webSocketClient,
                    robotController = robotController
                )
            }
        }
    }

    /**
     * Handle key events from Rokid touchpad/remote
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (robotController.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (robotController.onKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
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
    webSocketClient: RobotWebSocketClient,
    robotController: RobotController
) {
    val settings by settingsManager.settings.collectAsState(initial = RobotSettings())
    val connectionState by webSocketClient.connectionState.collectAsState()
    val currentAction by robotController.currentAction.collectAsState()
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
            currentAction = currentAction,
            robotController = robotController,
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
