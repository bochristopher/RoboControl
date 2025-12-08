package com.example.robocontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Handles voice command recognition using Vosk offline speech recognition.
 * Works on devices without Google Play Services (like Rokid glasses).
 */
class VoiceCommandHandler(
    private val context: Context,
    private val onCommand: (String) -> Unit
) {
    companion object {
        private const val TAG = "VoiceCommand"
        private const val SAMPLE_RATE = 16000
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
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
        
        scope.launch {
            try {
                // Unpack the model from assets
                StorageService.unpack(context, "model-en-us", "model",
                    { loadedModel ->
                        model = loadedModel
                        recognizer = Recognizer(loadedModel, SAMPLE_RATE.toFloat())
                        _isModelLoaded.value = true
                        _isLoading.value = false
                        _errorMessage.value = null
                        Log.d(TAG, "Vosk model loaded successfully")
                    },
                    { exception ->
                        Log.e(TAG, "Failed to load Vosk model: ${exception.message}")
                        _errorMessage.value = "Voice model failed to load"
                        _isLoading.value = false
                    }
                )
            } catch (e: IOException) {
                Log.e(TAG, "Failed to initialize Vosk: ${e.message}")
                _errorMessage.value = "Voice init failed: ${e.message}"
                _isLoading.value = false
            }
        }
        
        return true
    }

    fun startListening() {
        if (_isListening.value) {
            Log.d(TAG, "Already listening")
            return
        }

        if (!_isModelLoaded.value) {
            _errorMessage.value = "Voice model not loaded yet"
            Log.w(TAG, "Model not loaded")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            _errorMessage.value = "Microphone permission denied"
            return
        }

        _recognizedText.value = null
        _lastCommand.value = null
        _errorMessage.value = null
        _isListening.value = true

        recordingJob = scope.launch {
            try {
                startRecording()
            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}")
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Recording error: ${e.message}"
                    _isListening.value = false
                }
            }
        }
        
        // Auto-stop after 5 seconds
        scope.launch {
            delay(5000)
            if (_isListening.value) {
                stopListening()
            }
        }
    }

    private suspend fun startRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            withContext(Dispatchers.Main) {
                _errorMessage.value = "Failed to initialize audio"
                _isListening.value = false
            }
            return
        }

        audioRecord?.startRecording()
        Log.d(TAG, "Recording started")

        val buffer = ShortArray(bufferSize / 2)
        recognizer?.reset()

        while (_isListening.value && isActive) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
            
            if (read > 0) {
                // Convert short array to byte array for Vosk
                val byteBuffer = ByteArray(read * 2)
                for (i in 0 until read) {
                    byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                    byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                }

                val isFinal = recognizer?.acceptWaveForm(byteBuffer, byteBuffer.size) ?: false
                
                val result = if (isFinal) {
                    recognizer?.result
                } else {
                    recognizer?.partialResult
                }

                result?.let { parseVoskResult(it, isFinal) }
            }
        }

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        // Get final result
        recognizer?.finalResult?.let { parseVoskResult(it, true) }
        
        Log.d(TAG, "Recording stopped")
    }

    private suspend fun parseVoskResult(jsonResult: String, isFinal: Boolean) {
        try {
            val json = JSONObject(jsonResult)
            val text = if (isFinal) {
                json.optString("text", "")
            } else {
                json.optString("partial", "")
            }

            if (text.isNotBlank()) {
                withContext(Dispatchers.Main) {
                    _recognizedText.value = text
                    Log.d(TAG, "Recognized${if (isFinal) " (final)" else ""}: $text")

                    if (isFinal) {
                        val command = parseCommand(text)
                        if (command != null) {
                            _lastCommand.value = command
                            onCommand(command)
                        } else if (text.isNotBlank()) {
                            _errorMessage.value = "Unknown: \"$text\""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Vosk result: ${e.message}")
        }
    }

    fun stopListening() {
        _isListening.value = false
        recordingJob?.cancel()
        recordingJob = null
        Log.d(TAG, "Stopped listening")
    }

    fun destroy() {
        stopListening()
        scope.cancel()
        recognizer?.close()
        model?.close()
        recognizer = null
        model = null
        Log.d(TAG, "VoiceCommandHandler destroyed")
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
