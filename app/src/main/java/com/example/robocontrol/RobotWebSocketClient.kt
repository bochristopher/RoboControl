package com.example.robocontrol

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATED
}

class RobotWebSocketClient(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "RobotWebSocket"
        private const val AUTH_TOKEN = "robot_secret_2024"
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var serverUrl: String = ""
    private var shouldReconnect = true
    private var reconnectJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _lastMessage = MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage

    fun connect(host: String, port: Int = 8765) {
        serverUrl = "ws://$host:$port"
        shouldReconnect = true
        doConnect()
    }

    private fun doConnect() {
        if (_connectionState.value == ConnectionState.CONNECTING) return
        
        _connectionState.value = ConnectionState.CONNECTING
        Log.d(TAG, "Connecting to $serverUrl")

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                _connectionState.value = ConnectionState.CONNECTED
                authenticate()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
                _lastMessage.value = text
                
                // Check for auth success
                try {
                    val json = JSONObject(text)
                    if (json.optString("status") == "authenticated") {
                        _connectionState.value = ConnectionState.AUTHENTICATED
                        Log.d(TAG, "Authentication successful")
                    }
                } catch (e: Exception) {
                    // Ignore parse errors
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        })
    }

    private fun authenticate() {
        val authJson = JSONObject().apply {
            put("cmd", "auth")
            put("token", AUTH_TOKEN)
        }
        sendRaw(authJson.toString())
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (shouldReconnect && _connectionState.value == ConnectionState.DISCONNECTED) {
                Log.d(TAG, "Attempting reconnect...")
                doConnect()
            }
        }
    }

    fun sendMove(direction: String) {
        if (_connectionState.value != ConnectionState.AUTHENTICATED) {
            Log.w(TAG, "Cannot send command - not authenticated")
            return
        }
        
        val json = JSONObject().apply {
            put("cmd", "move")
            put("dir", direction)
        }
        sendRaw(json.toString())
    }

    private fun sendRaw(message: String) {
        Log.d(TAG, "Sending: $message")
        webSocket?.send(message)
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}

