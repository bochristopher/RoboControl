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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun MjpegStreamView(
    streamUrl: String,
    modifier: Modifier = Modifier
) {
    var currentFrame by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(streamUrl) {
        if (streamUrl.isBlank()) {
            errorMessage = "No stream URL configured"
            isLoading = false
            return@LaunchedEffect
        }
        
        withContext(Dispatchers.IO) {
            try {
                val url = URL(streamUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                connection.doInput = true
                connection.connect()

                val inputStream = BufferedInputStream(connection.inputStream)
                val boundary = extractBoundary(connection.contentType)
                
                isLoading = false
                
                while (isActive) {
                    val frame = readMjpegFrame(inputStream, boundary)
                    if (frame != null) {
                        currentFrame = frame
                    }
                }
            } catch (e: Exception) {
                Log.e("MjpegStreamView", "Stream error: ${e.message}")
                errorMessage = "Stream error: ${e.message}"
                isLoading = false
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

private fun extractBoundary(contentType: String?): String {
    if (contentType == null) return "--"
    val parts = contentType.split("boundary=")
    return if (parts.size > 1) "--${parts[1].trim()}" else "--"
}

private fun readMjpegFrame(inputStream: BufferedInputStream, boundary: String): Bitmap? {
    try {
        // Read until we find Content-Length or start of image data
        val headerBuffer = StringBuilder()
        var contentLength = -1
        
        // Skip boundary and headers
        while (true) {
            val line = readLine(inputStream) ?: return null
            if (line.isEmpty()) break
            
            if (line.lowercase().startsWith("content-length:")) {
                contentLength = line.substringAfter(":").trim().toIntOrNull() ?: -1
            }
        }
        
        // Read image data
        val imageData = if (contentLength > 0) {
            val buffer = ByteArray(contentLength)
            var bytesRead = 0
            while (bytesRead < contentLength) {
                val read = inputStream.read(buffer, bytesRead, contentLength - bytesRead)
                if (read == -1) break
                bytesRead += read
            }
            buffer
        } else {
            // Read until we hit the boundary
            readUntilBoundary(inputStream, boundary)
        }
        
        return BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
    } catch (e: Exception) {
        Log.e("MjpegStreamView", "Error reading frame: ${e.message}")
        return null
    }
}

private fun readLine(inputStream: BufferedInputStream): String? {
    val buffer = ByteArrayOutputStream()
    var prev = -1
    while (true) {
        val current = inputStream.read()
        if (current == -1) return null
        if (prev == '\r'.code && current == '\n'.code) {
            val bytes = buffer.toByteArray()
            return String(bytes, 0, bytes.size - 1) // Remove trailing \r
        }
        buffer.write(current)
        prev = current
    }
}

private fun readUntilBoundary(inputStream: BufferedInputStream, boundary: String): ByteArray {
    val buffer = ByteArrayOutputStream()
    val boundaryBytes = boundary.toByteArray()
    
    // Simple implementation - read until we find JPEG end marker
    var prev = -1
    while (true) {
        val current = inputStream.read()
        if (current == -1) break
        buffer.write(current)
        
        // Check for JPEG end marker (FFD9)
        if (prev == 0xFF && current == 0xD9) {
            break
        }
        prev = current
    }
    
    return buffer.toByteArray()
}

