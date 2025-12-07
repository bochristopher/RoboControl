package com.example.robocontrol

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
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

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force portrait orientation
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
     * Handle key events from Rokid touchpad/remote at Activity level
     * This ensures we catch all key events including from the touchpad
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyDown: keyCode=$keyCode, event=$event")
        if (robotController.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyUp: keyCode=$keyCode, event=$event")
        if (robotController.onKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Dispatch key events to ensure they reach our handler
     */
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        event?.let {
            Log.d(TAG, "dispatchKeyEvent: action=${it.action}, keyCode=${it.keyCode}")
            when (it.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (robotController.onKeyDown(it.keyCode, it)) {
                        return true
                    }
                }
                KeyEvent.ACTION_UP -> {
                    if (robotController.onKeyUp(it.keyCode, it)) {
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
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
    
    // Focus handling for key events
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current

    // Request focus when the composable is first displayed
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // Also set the view as focusable
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                Log.d("RoboControlApp", "Compose onKeyEvent: key=${keyEvent.key}, type=${keyEvent.type}")
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> {
                        when (keyEvent.key) {
                            Key.DirectionUp, Key.W -> {
                                robotController.startAction(RobotController.ControlAction.FORWARD)
                                true
                            }
                            Key.DirectionDown, Key.S -> {
                                robotController.startAction(RobotController.ControlAction.BACKWARD)
                                true
                            }
                            Key.DirectionLeft, Key.A -> {
                                robotController.startAction(RobotController.ControlAction.LEFT)
                                true
                            }
                            Key.DirectionRight, Key.D -> {
                                robotController.startAction(RobotController.ControlAction.RIGHT)
                                true
                            }
                            else -> false
                        }
                    }
                    KeyEventType.KeyUp -> {
                        when (keyEvent.key) {
                            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                            Key.W, Key.A, Key.S, Key.D -> {
                                robotController.stopAction()
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
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
