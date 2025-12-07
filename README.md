# RoboControl - Android Robot Controller for Rokid Glasses

A simple Android app for controlling a robot via Rokid AR glasses. Features live video streaming from the robot's camera and intuitive gesture controls.

## Features

- 📹 **Live MJPEG Video Stream** - Fullscreen camera feed from robot
- 🎮 **Gesture Controls** - Intuitive touch-based robot control
- 🔌 **WebSocket Communication** - Real-time command transmission
- 🔄 **Auto-Reconnect** - Automatic reconnection on disconnect
- ⚙️ **Settings Screen** - Configure IP address and ports

## Gesture Controls

| Gesture | Action |
|---------|--------|
| Swipe Up | Move Forward (continuous) |
| Swipe Down | Move Backward (continuous) |
| 1 Finger Tap & Hold | Turn Right (while held) |
| 2 Finger Tap & Hold | Turn Left (while held) |
| Release Fingers | Stop |

## Requirements

- Android 9.0 (API 28) or higher
- Network connectivity to robot
- Rokid AR glasses or any Android device

## Default Configuration

- **WebSocket Server**: `ws://192.168.1.219:8765`
- **Video Stream**: `http://192.168.1.219:8080/`
- **Auth Token**: `robot_secret_2024`

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11+
- Android SDK with API 34

### Build Steps

1. **Clone/Open the project** in Android Studio

2. **Sync Gradle** - Android Studio should automatically sync. If not:
   ```
   ./gradlew --refresh-dependencies
   ```

3. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
   APK location: `app/build/outputs/apk/debug/app-debug.apk`

4. **Build Release APK**:
   ```bash
   ./gradlew assembleRelease
   ```
   APK location: `app/build/outputs/apk/release/app-release-unsigned.apk`

### Install on Device

```bash
# Debug build
adb install app/build/outputs/apk/debug/app-debug.apk

# Or build and install in one step
./gradlew installDebug
```

## Sideloading to Rokid Glasses

1. Enable Developer Mode on Rokid glasses
2. Connect via USB or ADB over WiFi
3. Install the APK:
   ```bash
   adb install app-debug.apk
   ```

## API Commands

The app sends JSON commands over WebSocket:

```json
// Authentication (sent automatically on connect)
{"cmd": "auth", "token": "robot_secret_2024"}

// Movement commands
{"cmd": "move", "dir": "forward"}
{"cmd": "move", "dir": "backward"}
{"cmd": "move", "dir": "left"}
{"cmd": "move", "dir": "right"}
{"cmd": "move", "dir": "stop"}
```

## Architecture

```
com.example.robocontrol/
├── MainActivity.kt          # Main entry point, lifecycle management
├── ControlScreen.kt         # Main UI with video + controls overlay
├── GestureController.kt     # Touch/gesture detection
├── MjpegStreamView.kt       # MJPEG video stream decoder
├── RobotWebSocketClient.kt  # WebSocket client with auto-reconnect
├── SettingsManager.kt       # DataStore-based settings persistence
├── SettingsScreen.kt        # Settings UI
└── ui/theme/                # Material3 theming
```

## Troubleshooting

**Video not showing?**
- Check if the video stream URL is accessible in a browser
- Ensure `android:usesCleartextTraffic="true"` is set (already configured)
- Check network connectivity

**Can't connect to WebSocket?**
- Verify the IP address in settings
- Check if the WebSocket server is running
- Look for connection status indicator (top-left corner)

**Gestures not working?**
- Ensure the connection status shows "Ready" (green dot)
- Check WebSocket authentication
- Look at command counter (bottom-left) to verify commands are being sent

## License

MIT License - Feel free to modify and distribute.

