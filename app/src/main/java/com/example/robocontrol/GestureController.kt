package com.example.robocontrol

import android.util.Log
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.*
import kotlin.math.abs

enum class GestureAction {
    NONE,
    FORWARD,
    BACKWARD,
    LEFT,
    RIGHT
}

@Composable
fun GestureController(
    modifier: Modifier = Modifier,
    onActionChanged: (GestureAction) -> Unit,
    content: @Composable () -> Unit
) {
    var currentAction by remember { mutableStateOf(GestureAction.NONE) }
    var pointerCount by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // Threshold for swipe detection
    val swipeThreshold = 50f

    fun updateAction(action: GestureAction) {
        if (currentAction != action) {
            currentAction = action
            onActionChanged(action)
        }
    }

    fun stopAction() {
        holdJob?.cancel()
        holdJob = null
        updateAction(GestureAction.NONE)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        val newPointerCount = activePointers.size
                        
                        when {
                            // Fingers lifted - stop everything
                            newPointerCount == 0 && pointerCount > 0 -> {
                                Log.d("Gesture", "All fingers lifted - STOP")
                                isDragging = false
                                dragOffset = Offset.Zero
                                stopAction()
                            }
                            
                            // Tap & hold detection
                            newPointerCount > 0 && !isDragging -> {
                                if (pointerCount == 0 && newPointerCount > 0) {
                                    // New touch started
                                    holdJob?.cancel()
                                    holdJob = scope.launch {
                                        delay(150) // Short delay to distinguish from swipe
                                        if (!isDragging && newPointerCount > 0) {
                                            val action = if (newPointerCount >= 2) {
                                                GestureAction.LEFT
                                            } else {
                                                GestureAction.RIGHT
                                            }
                                            updateAction(action)
                                        }
                                    }
                                }
                            }
                        }
                        
                        pointerCount = newPointerCount
                        
                        // Consume all events
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        holdJob?.cancel()
                        holdJob = null
                        dragOffset = Offset.Zero
                    },
                    onDragEnd = {
                        isDragging = false
                        dragOffset = Offset.Zero
                        stopAction()
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = Offset.Zero
                        stopAction()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                        
                        // Determine direction based on cumulative drag
                        val action = when {
                            dragOffset.y < -swipeThreshold -> GestureAction.FORWARD
                            dragOffset.y > swipeThreshold -> GestureAction.BACKWARD
                            else -> currentAction // Keep current action if within threshold
                        }
                        
                        if (action != GestureAction.NONE) {
                            updateAction(action)
                        }
                    }
                )
            }
    ) {
        content()
    }
}

fun GestureAction.toDirection(): String = when (this) {
    GestureAction.FORWARD -> "forward"
    GestureAction.BACKWARD -> "backward"
    GestureAction.LEFT -> "left"
    GestureAction.RIGHT -> "right"
    GestureAction.NONE -> "stop"
}

fun GestureAction.toDisplayText(): String = when (this) {
    GestureAction.FORWARD -> "FORWARD ▲"
    GestureAction.BACKWARD -> "BACKWARD ▼"
    GestureAction.LEFT -> "◄ LEFT"
    GestureAction.RIGHT -> "RIGHT ►"
    GestureAction.NONE -> ""
}

