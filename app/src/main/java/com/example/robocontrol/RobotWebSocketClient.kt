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
    private var authSent = false

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _lastMessage = MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage

    fun connect(host: String, port: Int = 8765) {
        serverUrl = "ws://$host:$port"
        shouldReconnect = true
        authSent = false
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
                Log.d(TAG, "WebSocket connected, sending auth...")
                _connectionState.value = ConnectionState.CONNECTED
                authenticate()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
                _lastMessage.value = text
                
                try {
                    val json = JSONObject(text)
                    
                    // Check various auth success indicators
                    val status = json.optString("status", "")
                    val type = json.optString("type", "")
                    
                    // Auth successful if:
                    // 1. Explicit "authenticated" status
                    // 2. Receives heartbeat (server is accepting our connection)
                    // 3. Any "ok" or "success" status
                    if (status == "authenticated" || 
                        status == "ok" || 
                        status == "success" ||
                        type == "heartbeat" ||
                        type == "auth_success") {
                        
                        if (_connectionState.value != ConnectionState.AUTHENTICATED) {
                            _connectionState.value = ConnectionState.AUTHENTICATED
                            Log.d(TAG, "Authentication successful (indicator: status=$status, type=$type)")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing message: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _connectionState.value = ConnectionState.DISCONNECTED
                authSent = false
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                authSent = false
                scheduleReconnect()
            }
        })
    }

    private fun authenticate() {
        if (authSent) return
        authSent = true
        
        val authJson = JSONObject().apply {
            put("cmd", "auth")
            put("token", AUTH_TOKEN)
        }
        Log.d(TAG, "Sending auth: $authJson")
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
        // Allow sending if connected OR authenticated (some servers don't require auth)
        if (_connectionState.value != ConnectionState.AUTHENTICATED && 
            _connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send command - not connected (state: ${_connectionState.value})")
            return
        }
        
        val json = JSONObject().apply {
            put("cmd", "move")
            put("dir", direction)
        }
        Log.d(TAG, "Sending move: $json")
        sendRaw(json.toString())
    }

    private fun sendRaw(message: String) {
        val sent = webSocket?.send(message) ?: false
        Log.d(TAG, "Sent ($sent): $message")
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        authSent = false
    }
}
