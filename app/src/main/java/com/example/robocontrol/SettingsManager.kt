package com.example.robocontrol

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class RobotSettings(
    val serverHost: String = "192.168.1.219",
    val websocketPort: Int = 8765,
    val videoPort: Int = 8080
) {
    val websocketUrl: String get() = "ws://$serverHost:$websocketPort"
    val videoStreamUrl: String get() = "http://$serverHost:$videoPort/"
}

class SettingsManager(private val context: Context) {
    companion object {
        private val KEY_SERVER_HOST = stringPreferencesKey("server_host")
        private val KEY_WEBSOCKET_PORT = intPreferencesKey("websocket_port")
        private val KEY_VIDEO_PORT = intPreferencesKey("video_port")
    }

    val settings: Flow<RobotSettings> = context.dataStore.data.map { prefs ->
        RobotSettings(
            serverHost = prefs[KEY_SERVER_HOST] ?: "192.168.1.219",
            websocketPort = prefs[KEY_WEBSOCKET_PORT] ?: 8765,
            videoPort = prefs[KEY_VIDEO_PORT] ?: 8080
        )
    }

    suspend fun updateSettings(
        serverHost: String? = null,
        websocketPort: Int? = null,
        videoPort: Int? = null
    ) {
        context.dataStore.edit { prefs ->
            serverHost?.let { prefs[KEY_SERVER_HOST] = it }
            websocketPort?.let { prefs[KEY_WEBSOCKET_PORT] = it }
            videoPort?.let { prefs[KEY_VIDEO_PORT] = it }
        }
    }
}

