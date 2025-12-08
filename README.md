# RoboControl - Android Robot Controller for Rokid Glasses

A simple Android app for controlling a robot via Rokid AR glasses. Features live video streaming from the robot's camera and intuitive gesture/touch controls.

## Features

- 📹 **Live MJPEG Video Stream** - Fullscreen camera feed from robot
- 🎮 **Multiple Control Methods** - Touchpad gestures, on-screen buttons, DPAD
- 🔌 **WebSocket Communication** - Real-time command transmission
- 🔄 **Auto-Reconnect** - Automatic reconnection on disconnect
- ⚙️ **Settings Screen** - Configure IP address and ports

## Controls

| Input | Action |
|-------|--------|
| **Swipe Left** | Turn Left |
| **Swipe Right** | Turn Right |
| **Hold/Press** | Move Forward |
| **Double Tap** | Move Backward |
| **On-screen Buttons** | All directions |
| **DPAD Keys** | All directions |

## Requirements

- Android 9.0 (API 28) or higher
- Network connectivity to robot
- Rokid AR glasses or any Android device

## Server Requirements

This app connects to a robot control server. See the companion project:
- **Server**: [robot_control](https://github.com/bochristopher/robot_control/tree/raspberry-pi-code) (Raspberry Pi/Jetson)

## Default Configuration

- **WebSocket Server**: `ws://192.168.1.219:8765`
- **Video Stream**: `http://192.168.1.219:8080/stream`
- **Auth Token**: `robot_secret_2024`

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11+
- Android SDK with API 34

### Build Steps

1. **Clone the project**:
   ```bash
   git clone https://github.com/bochristopher/RoboControl.git
   cd RoboControl
   ```

2. **Open in Android Studio** and sync Gradle

3. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
   APK location: `app/build/outputs/apk/debug/app-debug.apk`

4. **Build & Install**:
   ```bash
   ./gradlew installDebug
   ```

## Sideloading to Rokid Glasses

1. Enable Developer Mode on Rokid glasses
2. Connect via USB
3. Install the APK:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
4. Launch the app:
   ```bash
   adb shell am start -n com.example.robocontrol/.MainActivity
   ```

## WebSocket API

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
├── MainActivity.kt          # Main entry, key/touch event handling
├── ControlScreen.kt         # Main UI with video + controls overlay
├── RobotController.kt       # Gesture/key detection, command sending
├── MjpegStreamView.kt       # MJPEG video stream decoder
├── RobotWebSocketClient.kt  # WebSocket client with auto-reconnect
├── SettingsManager.kt       # DataStore-based settings persistence
├── SettingsScreen.kt        # Settings UI
└── ui/theme/                # Material3 theming
```

## Troubleshooting

**Video not showing?**
- Check if `http://<ip>:8080/stream` is accessible in a browser
- Ensure network connectivity between glasses and robot
- Check that the robot server is running

**Can't connect to WebSocket?**
- Verify the IP address in settings (tap ⚙️ gear icon)
- Check if the WebSocket server is running on port 8765
- Look for connection status indicator (top-left corner)

**Controls not working?**
- Ensure connection shows "Ready" (green dot)
- Check command counter (bottom-left) to verify commands are sent
- Try the on-screen arrow buttons as fallback

## License

MIT License - See [LICENSE](LICENSE) file.

## Related Projects

- [robot_control](https://github.com/bochristopher/robot_control/tree/raspberry-pi-code) - Raspberry Pi/Jetson server for robot control
