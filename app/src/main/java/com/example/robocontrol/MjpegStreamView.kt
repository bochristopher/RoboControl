package com.example.robocontrol

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties

private const val TAG = "MjpegStreamView"

@Composable
fun MjpegStreamView(
    streamUrl: String,
    modifier: Modifier = Modifier
) {
    var currentFrame by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var frameCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(streamUrl) {
        if (streamUrl.isBlank()) {
            errorMessage = "No stream URL configured"
            isLoading = false
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            while (isActive) {
                try {
                    Log.d(TAG, "Connecting to stream: $streamUrl")
                    val url = URL(streamUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 30000
                    connection.doInput = true
                    connection.setRequestProperty("Connection", "keep-alive")
                    connection.connect()

                    Log.d(TAG, "Connected, content-type: ${connection.contentType}")
                    
                    val inputStream = DataInputStream(connection.inputStream)
                    isLoading = false
                    errorMessage = null

                    // Read MJPEG stream
                    readMjpegStream(inputStream) { frame ->
                        currentFrame = frame
                        frameCount++
                    }

                    connection.disconnect()
                } catch (e: Exception) {
                    Log.e(TAG, "Stream error: ${e.message}", e)
                    errorMessage = "Stream error: ${e.message}"
                    isLoading = false
                    // Wait before retry
                    kotlinx.coroutines.delay(2000)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            currentFrame != null -> {
                Image(
                    bitmap = currentFrame!!.asImageBitmap(),
                    contentDescription = "Camera stream",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            isLoading -> {
                Text(
                    text = "Connecting to camera...",
                    color = Color.White
                )
            }
            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "Unknown error",
                    color = Color.Red
                )
            }
        }
    }
}

private suspend fun readMjpegStream(
    inputStream: DataInputStream,
    onFrame: (Bitmap) -> Unit
) {
    val SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) // JPEG start
    val EOI = byteArrayOf(0xFF.toByte(), 0xD9.toByte()) // JPEG end
    
    while (currentCoroutineContext().isActive) {
        try {
            // Find JPEG start marker (FFD8)
            if (!findMarker(inputStream, SOI)) {
                Log.w(TAG, "Could not find JPEG start marker")
                break
            }

            // Read until JPEG end marker (FFD9)
            val imageData = readUntilMarker(inputStream, EOI, SOI)
            if (imageData != null && imageData.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                if (bitmap != null) {
                    onFrame(bitmap)
                } else {
                    Log.w(TAG, "Failed to decode frame, size: ${imageData.size}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading frame: ${e.message}")
            break
        }
    }
}

private fun findMarker(inputStream: DataInputStream, marker: ByteArray): Boolean {
    var matchIndex = 0
    var bytesRead = 0
    val maxSearch = 100000 // Don't search forever
    
    while (bytesRead < maxSearch) {
        val b = try {
            inputStream.readByte()
        } catch (e: Exception) {
            return false
        }
        bytesRead++
        
        if (b == marker[matchIndex]) {
            matchIndex++
            if (matchIndex == marker.size) {
                return true
            }
        } else {
            matchIndex = if (b == marker[0]) 1 else 0
        }
    }
    return false
}

private fun readUntilMarker(
    inputStream: DataInputStream, 
    endMarker: ByteArray,
    startMarker: ByteArray
): ByteArray? {
    val buffer = java.io.ByteArrayOutputStream()
    
    // Write the start marker first (we already consumed it in findMarker)
    buffer.write(startMarker)
    
    var prev = 0
    val maxSize = 5 * 1024 * 1024 // 5MB max frame size
    
    while (buffer.size() < maxSize) {
        val current = try {
            inputStream.readUnsignedByte()
        } catch (e: Exception) {
            return null
        }
        
        buffer.write(current)
        
        // Check for end marker (FFD9)
        if (prev == 0xFF && current == 0xD9) {
            return buffer.toByteArray()
        }
        
        prev = current
    }
    
    Log.w(TAG, "Frame exceeded max size")
    return null
}
