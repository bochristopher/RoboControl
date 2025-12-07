package com.example.robocontrol

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
 * - Single finger swipe left/right -> DPAD_LEFT/RIGHT -> Turn left/right
 * - Two finger swipe right -> Forward
 * - Two finger swipe left -> Backward
 * - Release -> Stop
 */
class RobotController(
    private val scope: CoroutineScope,
    private val onCommand: (String) -> Unit
) {
    companion object {
        private const val TAG = "RobotController"
        private const val SWIPE_THRESHOLD = 50f // Minimum swipe distance
    }

    private val _currentAction = MutableStateFlow(ControlAction.NONE)
    val currentAction: StateFlow<ControlAction> = _currentAction

    private var commandJob: Job? = null
    private var keyHeldDown = false

    // Touch tracking for two-finger gestures
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var pointerCount = 0
    private var twoFingerSwipeDetected = false

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
            // Keep DPAD_UP/DOWN for external keyboard testing
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
     * Handle touch events for two-finger swipe detection
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val currentPointerCount = event.pointerCount

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // First finger down
                initialTouchX = event.x
                initialTouchY = event.y
                pointerCount = 1
                twoFingerSwipeDetected = false
                Log.d(TAG, "Touch DOWN: x=$initialTouchX, pointers=$currentPointerCount")
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Additional finger(s) down
                pointerCount = currentPointerCount
                if (currentPointerCount >= 2) {
                    // Reset initial position for two-finger gesture
                    initialTouchX = (event.getX(0) + event.getX(1)) / 2f
                    initialTouchY = (event.getY(0) + event.getY(1)) / 2f
                    twoFingerSwipeDetected = false
                    Log.d(TAG, "Two fingers DOWN: x=$initialTouchX, pointers=$currentPointerCount")
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentPointerCount >= 2 && !twoFingerSwipeDetected) {
                    // Calculate two-finger swipe
                    val currentX = (event.getX(0) + event.getX(1)) / 2f
                    val currentY = (event.getY(0) + event.getY(1)) / 2f
                    val deltaX = currentX - initialTouchX
                    val deltaY = currentY - initialTouchY

                    Log.d(TAG, "Two finger MOVE: deltaX=$deltaX, deltaY=$deltaY")

                    // Check for horizontal two-finger swipe
                    if (abs(deltaX) > SWIPE_THRESHOLD && abs(deltaX) > abs(deltaY)) {
                        twoFingerSwipeDetected = true
                        if (deltaX > 0) {
                            // Two finger swipe RIGHT -> FORWARD
                            Log.d(TAG, "Two finger swipe RIGHT -> FORWARD")
                            startAction(ControlAction.FORWARD)
                        } else {
                            // Two finger swipe LEFT -> BACKWARD
                            Log.d(TAG, "Two finger swipe LEFT -> BACKWARD")
                            startAction(ControlAction.BACKWARD)
                        }
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // All fingers up
                Log.d(TAG, "Touch UP - stopping")
                if (twoFingerSwipeDetected || _currentAction.value == ControlAction.FORWARD || 
                    _currentAction.value == ControlAction.BACKWARD) {
                    stopAction()
                }
                pointerCount = 0
                twoFingerSwipeDetected = false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // One finger lifted but others still down
                pointerCount = currentPointerCount - 1
                Log.d(TAG, "Pointer UP: remaining pointers=$pointerCount")
            }
        }

        return twoFingerSwipeDetected
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
