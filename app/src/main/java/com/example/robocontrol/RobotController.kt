package com.example.robocontrol

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Handles robot control input from Rokid glasses touchpad/remote.
 * 
 * Controls:
 * - Single finger swipe left/right -> Turn left/right
 * - Press and hold -> Forward (continuous while held)
 * - Double tap -> Backward (continuous until released)
 * - Release -> Stop
 */
class RobotController(
    private val scope: CoroutineScope,
    private val onCommand: (String) -> Unit
) {
    companion object {
        private const val TAG = "RobotController"
        private const val SWIPE_THRESHOLD = 30f // Minimum swipe distance
        private const val HOLD_THRESHOLD = 400L // ms to trigger hold
        private const val DOUBLE_TAP_TIMEOUT = 300L // ms between taps for double tap
        private const val TAP_MOVEMENT_THRESHOLD = 20f // Max movement for a tap
    }

    private val _currentAction = MutableStateFlow(ControlAction.NONE)
    val currentAction: StateFlow<ControlAction> = _currentAction

    private var commandJob: Job? = null
    private var keyHeldDown = false

    // Touch tracking
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var touchDownTime = 0L
    private var lastTapTime = 0L
    private var isHolding = false
    private var swipeDetected = false
    private var tapCount = 0

    // Handler for hold detection
    private val handler = Handler(Looper.getMainLooper())
    private var holdRunnable: Runnable? = null

    enum class ControlAction {
        NONE, FORWARD, BACKWARD, LEFT, RIGHT
    }

    /**
     * Handle key down events from Rokid touchpad (single finger swipes)
     */
    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyDown: keyCode=$keyCode")
        val action = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> ControlAction.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> ControlAction.RIGHT
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> ControlAction.FORWARD
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> ControlAction.BACKWARD
            else -> null
        }

        if (action != null) {
            startAction(action)
            return true
        }
        return false
    }

    /**
     * Handle key up events - stop movement
     */
    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyUp: keyCode=$keyCode")
        val isMovementKey = keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D
        )

        if (isMovementKey) {
            stopAction()
            return true
        }
        return false
    }

    /**
     * Handle touch events for gesture detection
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                touchDownTime = System.currentTimeMillis()
                isHolding = false
                swipeDetected = false
                
                Log.d(TAG, "Touch DOWN at ($initialTouchX, $initialTouchY)")

                // Check for double tap
                val timeSinceLastTap = touchDownTime - lastTapTime
                if (timeSinceLastTap < DOUBLE_TAP_TIMEOUT) {
                    tapCount++
                    Log.d(TAG, "Tap count: $tapCount, time since last: $timeSinceLastTap")
                    if (tapCount >= 2) {
                        // Double tap detected -> Backward
                        Log.d(TAG, "DOUBLE TAP -> BACKWARD")
                        tapCount = 0
                        cancelHoldDetection()
                        startAction(ControlAction.BACKWARD)
                        return true
                    }
                } else {
                    tapCount = 1
                }

                // Schedule hold detection
                scheduleHoldDetection()
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - initialTouchX
                val deltaY = event.y - initialTouchY
                val distance = abs(deltaX) + abs(deltaY)

                // If moved too much, cancel hold detection and check for swipe
                if (distance > TAP_MOVEMENT_THRESHOLD && !swipeDetected && !isHolding) {
                    cancelHoldDetection()
                    tapCount = 0
                    
                    // Check for horizontal swipe
                    if (abs(deltaX) > SWIPE_THRESHOLD && abs(deltaX) > abs(deltaY)) {
                        swipeDetected = true
                        if (deltaX > 0) {
                            Log.d(TAG, "SWIPE RIGHT -> RIGHT")
                            startAction(ControlAction.RIGHT)
                        } else {
                            Log.d(TAG, "SWIPE LEFT -> LEFT")
                            startAction(ControlAction.LEFT)
                        }
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val touchDuration = System.currentTimeMillis() - touchDownTime
                val deltaX = event.x - initialTouchX
                val deltaY = event.y - initialTouchY
                val distance = abs(deltaX) + abs(deltaY)
                
                Log.d(TAG, "Touch UP: duration=$touchDuration, distance=$distance, isHolding=$isHolding")

                cancelHoldDetection()

                // Record tap time for double-tap detection
                if (distance < TAP_MOVEMENT_THRESHOLD && touchDuration < HOLD_THRESHOLD) {
                    lastTapTime = System.currentTimeMillis()
                    Log.d(TAG, "Registered as tap, tapCount=$tapCount")
                }

                // Stop any action on release
                if (isHolding || swipeDetected || _currentAction.value != ControlAction.NONE) {
                    stopAction()
                }
                
                isHolding = false
                swipeDetected = false
            }
        }

        return false
    }

    private fun scheduleHoldDetection() {
        cancelHoldDetection()
        holdRunnable = Runnable {
            if (!swipeDetected) {
                Log.d(TAG, "HOLD DETECTED -> FORWARD")
                isHolding = true
                tapCount = 0
                startAction(ControlAction.FORWARD)
            }
        }
        handler.postDelayed(holdRunnable!!, HOLD_THRESHOLD)
    }

    private fun cancelHoldDetection() {
        holdRunnable?.let { handler.removeCallbacks(it) }
        holdRunnable = null
    }

    /**
     * Start continuous movement in a direction
     */
    fun startAction(action: ControlAction) {
        if (_currentAction.value == action && commandJob?.isActive == true) {
            return // Already doing this action
        }

        Log.d(TAG, "Starting action: $action")
        _currentAction.value = action
        keyHeldDown = true

        // Cancel any existing command job
        commandJob?.cancel()

        // Start sending commands continuously
        commandJob = scope.launch {
            while (isActive && keyHeldDown && _currentAction.value == action) {
                onCommand(action.toDirection())
                delay(100) // Send every 100ms
            }
        }
    }

    /**
     * Stop all movement
     */
    fun stopAction() {
        Log.d(TAG, "Stopping action")
        keyHeldDown = false
        commandJob?.cancel()
        commandJob = null
        _currentAction.value = ControlAction.NONE
        onCommand("stop")
    }

    private fun ControlAction.toDirection(): String = when (this) {
        ControlAction.FORWARD -> "forward"
        ControlAction.BACKWARD -> "backward"
        ControlAction.LEFT -> "left"
        ControlAction.RIGHT -> "right"
        ControlAction.NONE -> "stop"
    }
}

fun RobotController.ControlAction.toDisplayText(): String = when (this) {
    RobotController.ControlAction.FORWARD -> "▲ FORWARD"
    RobotController.ControlAction.BACKWARD -> "▼ BACKWARD"
    RobotController.ControlAction.LEFT -> "◄ LEFT"
    RobotController.ControlAction.RIGHT -> "RIGHT ►"
    RobotController.ControlAction.NONE -> ""
}
