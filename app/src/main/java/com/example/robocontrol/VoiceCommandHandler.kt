package com.example.robocontrol

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Handles voice command recognition using Android's SpeechRecognizer.
 * Parses recognized text into robot movement commands.
 */
class VoiceCommandHandler(
    private val context: Context,
    private val onCommand: (String) -> Unit // Callback to send command (forward, backward, etc.)
) {
    companion object {
        private const val TAG = "VoiceCommand"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening
    
    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText
    
    private val _lastCommand = MutableStateFlow<String?>(null)
    val lastCommand: StateFlow<String?> = _lastCommand
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Command keyword mappings
    private val commandPatterns = mapOf(
        "forward" to listOf("forward", "go forward", "move forward", "ahead", "go ahead"),
        "backward" to listOf("back", "backward", "reverse", "go back", "move back"),
        "left" to listOf("left", "turn left", "go left"),
        "right" to listOf("right", "turn right", "go right"),
        "stop" to listOf("stop", "halt", "freeze", "brake", "hold")
    )

    fun initialize(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available on this device")
            _errorMessage.value = "Speech recognition not available"
            return false
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(createRecognitionListener())
        
        Log.d(TAG, "VoiceCommandHandler initialized")
        return true
    }

    fun startListening() {
        if (_isListening.value) {
            Log.d(TAG, "Already listening")
            return
        }

        if (speechRecognizer == null) {
            if (!initialize()) {
                return
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Timeout settings
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }

        try {
            _recognizedText.value = null
            _lastCommand.value = null
            _errorMessage.value = null
            speechRecognizer?.startListening(intent)
            _isListening.value = true
            Log.d(TAG, "Started listening")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening: ${e.message}")
            _errorMessage.value = "Failed to start: ${e.message}"
            _isListening.value = false
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening.value = false
        Log.d(TAG, "Stopped listening")
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        _isListening.value = false
        Log.d(TAG, "VoiceCommandHandler destroyed")
    }

    /**
     * Parse recognized text and return command direction.
     * Returns null if no command matched.
     */
    private fun parseCommand(text: String): String? {
        val lowerText = text.lowercase().trim()
        Log.d(TAG, "Parsing text: '$lowerText'")

        for ((command, keywords) in commandPatterns) {
            for (keyword in keywords) {
                if (lowerText.contains(keyword)) {
                    Log.d(TAG, "Matched command: $command (keyword: $keyword)")
                    return command
                }
            }
        }

        Log.d(TAG, "No command matched for: '$lowerText'")
        return null
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
            _isListening.value = true
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech started")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Audio level changed - could use for visual feedback
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Audio buffer received
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech ended")
            _isListening.value = false
        }

        override fun onError(error: Int) {
            val errorText = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Need microphone permission"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
                else -> "Error code: $error"
            }
            Log.e(TAG, "Recognition error: $errorText")
            _errorMessage.value = errorText
            _isListening.value = false
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d(TAG, "Results: $matches")

            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]
                _recognizedText.value = recognizedText

                // Parse command from recognized text
                val command = parseCommand(recognizedText)
                if (command != null) {
                    _lastCommand.value = command
                    onCommand(command)
                } else {
                    _errorMessage.value = "Unknown: \"$recognizedText\""
                }
            }

            _isListening.value = false
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                Log.d(TAG, "Partial: ${matches[0]}")
                _recognizedText.value = matches[0]
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d(TAG, "Event: $eventType")
        }
    }
}

