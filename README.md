# IMU Sensor — Android App

A premium Android app for visualizing and streaming phone IMU sensor data, built with **Kotlin + Jetpack Compose + Material 3**.

Designed for testing hobby IMUs (BNO055, BNO085, etc.) against high-quality phone sensors.

## Features

- **Real-time sensor visualization** — Accelerometer, Gyroscope, Magnetometer with smooth animated charts
- **3D Orientation** — Live wireframe cube + Yaw/Pitch/Roll gauges in degrees
- **UDP Streaming** — Stream sensor data to PC over Wi-Fi (CSV or JSON format)
- **Data Export** — Save buffered data as CSV or JSON files
- **Premium UI** — Dark glassmorphism theme, smooth animations, clean typography

## Getting Started

### Prerequisites
- Android Studio (latest stable)
- Android device with IMU sensors (emulators don't have real sensors)
- PC on the same Wi-Fi network (for streaming)

### Build & Run

1. **Open in Android Studio**: File → Open → select this folder (`IMU_APP/`)
2. Android Studio will sync Gradle and download dependencies automatically
3. Connect your Android device via USB (enable USB debugging)
4. Click **Run** (▶) to build and install

### PC Receiver

To receive streamed sensor data on your PC:

```bash
cd pc_receiver

# Basic usage (listen on port 8765)
python receiver.py

# With live matplotlib plot
pip install matplotlib
python receiver.py --plot

# Log to file
python receiver.py --log sensor_data.csv
```

## UDP Packet Format

**CSV:**
```
timestamp,ax,ay,az,gx,gy,gz,mx,my,mz,qw,qx,qy,qz,yaw,pitch,roll
```

**JSON:**
```json
{
  "t": 1713500000000,
  "accel": [0.12, 9.79, 0.45],
  "gyro": [0.01, -0.02, 0.00],
  "mag": [22.3, -5.1, 41.8],
  "quat": [0.95, 0.01, -0.03, 0.31],
  "euler": {"yaw": 127.3, "pitch": -12.1, "roll": 3.8}
}
```

## Architecture

```
app/src/main/java/com/robomanipal/imusensor/
├── sensor/          # SensorManager wrapper + data models
├── streaming/       # UDP streamer + config
├── viewmodel/       # MVVM state management
├── export/          # CSV & JSON file export
├── ui/
│   ├── theme/       # Color, Typography, Shapes
│   ├── components/  # GlassCard, SensorChart, 3D Cube, Gauges
│   ├── screens/     # Dashboard, SensorDetail, Orientation, Stream, Settings
│   └── navigation/  # Navigation graph + bottom bar
└── MainActivity.kt
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM (ViewModel + StateFlow) |
| Navigation | Navigation Compose |
| Networking | UDP DatagramSocket |
| Build | Gradle 8.9 + AGP 8.7.3 |
