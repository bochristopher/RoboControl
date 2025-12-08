package com.example.robocontrol

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
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
    private var voiceCommandHandler: VoiceCommandHandler? = null

    companion object {
        private const val TAG = "MainActivity"
    }

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "RECORD_AUDIO permission granted")
            initializeVoiceHandler()
        } else {
            Log.w(TAG, "RECORD_AUDIO permission denied")
            Toast.makeText(this, "Microphone permission needed for voice control", Toast.LENGTH_LONG).show()
        }
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

        // Request microphone permission for voice control
        checkAndRequestMicrophonePermission()

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
                    robotController = robotController,
                    voiceHandler = voiceCommandHandler
                )
            }
        }
    }

    private fun checkAndRequestMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                Log.d(TAG, "RECORD_AUDIO permission already granted")
                initializeVoiceHandler()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // Show explanation then request
                Toast.makeText(this, "Microphone needed for voice commands", Toast.LENGTH_SHORT).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun initializeVoiceHandler() {
        voiceCommandHandler = VoiceCommandHandler(this) { command ->
            // Voice command received - send to robot
            Log.d(TAG, "Voice command: $command")
            webSocketClient.sendMove(command)
        }
        voiceCommandHandler?.initialize()
        
        // Update the UI with the new handler
        setContent {
            RoboControlTheme {
                RoboControlApp(
                    settingsManager = settingsManager,
                    webSocketClient = webSocketClient,
                    robotController = robotController,
                    voiceHandler = voiceCommandHandler
                )
            }
        }
    }

    /**
     * Handle key events from Rokid touchpad/remote at Activity level
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyDown: keyCode=$keyCode")
        if (robotController.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyUp: keyCode=$keyCode")
        if (robotController.onKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Dispatch key events to ensure they reach our handler
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "dispatchKeyEvent: action=${event.action}, keyCode=${event.keyCode}")
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (robotController.onKeyDown(event.keyCode, event)) {
                    return true
                }
            }
            KeyEvent.ACTION_UP -> {
                if (robotController.onKeyUp(event.keyCode, event)) {
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Dispatch touch events for gesture detection
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (robotController.onTouchEvent(event)) {
            return true
        }
        return super.dispatchTouchEvent(event)
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
        voiceCommandHandler?.destroy()
        webSocketClient.disconnect()
    }
}

@Composable
fun RoboControlApp(
    settingsManager: SettingsManager,
    webSocketClient: RobotWebSocketClient,
    robotController: RobotController,
    voiceHandler: VoiceCommandHandler?
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
            voiceHandler = voiceHandler,
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
