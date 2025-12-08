package com.example.robocontrol

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Handles voice command recognition using Vosk offline speech recognition.
 * Works on devices without Google Play Services (like Rokid glasses).
 */
class VoiceCommandHandler(
    private val context: Context,
    private val onCommand: (String) -> Unit
) : RecognitionListener {
    
    companion object {
        private const val TAG = "VoiceCommand"
        private const val SAMPLE_RATE = 16000.0f
    }

    private var model: Model? = null
    private var speechService: SpeechService? = null
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening
    
    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText
    
    private val _lastCommand = MutableStateFlow<String?>(null)
    val lastCommand: StateFlow<String?> = _lastCommand
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    
    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Command keyword mappings
    private val commandPatterns = mapOf(
        "forward" to listOf("forward", "go forward", "move forward", "ahead", "go ahead", "go"),
        "backward" to listOf("back", "backward", "reverse", "go back", "move back"),
        "left" to listOf("left", "turn left", "go left"),
        "right" to listOf("right", "turn right", "go right"),
        "stop" to listOf("stop", "halt", "freeze", "brake", "hold", "wait")
    )

    fun initialize(): Boolean {
        Log.d(TAG, "Initializing Vosk voice handler...")
        _isLoading.value = true
        _errorMessage.value = "Loading voice model..."
        
        // Set Vosk log level
        LibVosk.setLogLevel(LogLevel.INFO)
        
        // Unpack the model from assets to internal storage
        StorageService.unpack(context, "model-en-us", "model",
            { loadedModel: Model ->
                Log.d(TAG, "Vosk model loaded successfully")
                model = loadedModel
                _isModelLoaded.value = true
                _isLoading.value = false
                _errorMessage.value = null
            },
            { exception: IOException ->
                Log.e(TAG, "Failed to load Vosk model: ${exception.message}", exception)
                _errorMessage.value = "Voice model failed to load"
                _isLoading.value = false
            }
        )
        
        return true
    }

    fun startListening() {
        if (_isListening.value) {
            Log.d(TAG, "Already listening")
            return
        }

        if (!_isModelLoaded.value || model == null) {
            _errorMessage.value = "Voice model not loaded yet"
            Log.w(TAG, "Model not loaded")
            return
        }

        _recognizedText.value = null
        _lastCommand.value = null
        _errorMessage.value = null

        try {
            val recognizer = Recognizer(model, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            speechService?.startListening(this)
            _isListening.value = true
            Log.d(TAG, "Started listening")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start listening: ${e.message}")
            _errorMessage.value = "Failed to start: ${e.message}"
        }
    }

    fun stopListening() {
        speechService?.stop()
        speechService = null
        _isListening.value = false
        Log.d(TAG, "Stopped listening")
    }

    fun destroy() {
        stopListening()
        model?.close()
        model = null
        Log.d(TAG, "VoiceCommandHandler destroyed")
    }

    // RecognitionListener callbacks
    
    override fun onPartialResult(hypothesis: String?) {
        hypothesis?.let {
            try {
                val json = JSONObject(it)
                val partial = json.optString("partial", "")
                if (partial.isNotBlank()) {
                    Log.d(TAG, "Partial: $partial")
                    _recognizedText.value = partial
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse partial: ${e.message}")
            }
        }
    }

    override fun onResult(hypothesis: String?) {
        hypothesis?.let {
            try {
                val json = JSONObject(it)
                val text = json.optString("text", "")
                if (text.isNotBlank()) {
                    Log.d(TAG, "Result: $text")
                    _recognizedText.value = text
                    
                    val command = parseCommand(text)
                    if (command != null) {
                        _lastCommand.value = command
                        onCommand(command)
                    } else {
                        _errorMessage.value = "Unknown: \"$text\""
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse result: ${e.message}")
            }
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        Log.d(TAG, "Final result: $hypothesis")
        // Final result received - stop listening
        _isListening.value = false
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Recognition error: ${exception?.message}")
        _errorMessage.value = "Error: ${exception?.message}"
        _isListening.value = false
    }

    override fun onTimeout() {
        Log.d(TAG, "Recognition timeout")
        _isListening.value = false
    }

    /**
     * Parse recognized text and return command direction.
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
}
