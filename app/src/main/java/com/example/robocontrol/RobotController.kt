package com.example.robocontrol

import android.view.KeyEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Handles robot control input from Rokid glasses touchpad/remote.
 * 
 * Rokid touchpad sends key events:
 * - Swipe up/down/left/right -> DPAD_UP/DOWN/LEFT/RIGHT
 * - Tap -> DPAD_CENTER or ENTER
 * - Long press -> triggers key repeat
 */
class RobotController(
    private val scope: CoroutineScope,
    private val onCommand: (String) -> Unit
) {
    private val _currentAction = MutableStateFlow(ControlAction.NONE)
    val currentAction: StateFlow<ControlAction> = _currentAction

    private var commandJob: Job? = null
    private var keyHeldDown = false

    enum class ControlAction {
        NONE, FORWARD, BACKWARD, LEFT, RIGHT
    }

    /**
     * Handle key down events from Rokid touchpad
     */
    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val action = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> ControlAction.FORWARD
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> ControlAction.BACKWARD
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> ControlAction.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> ControlAction.RIGHT
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
     * Start continuous movement in a direction
     */
    fun startAction(action: ControlAction) {
        if (_currentAction.value == action && commandJob?.isActive == true) {
            return // Already doing this action
        }

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
        keyHeldDown = false
        commandJob?.cancel()
        commandJob = null
        _currentAction.value = ControlAction.NONE
        onCommand("stop")
    }

    fun ControlAction.toDirection(): String = when (this) {
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

