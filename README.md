# IMU Sensor & Comparison Suite

A precision IMU testing toolkit built for hobbyist robotics and embedded systems development. Compare your hobby IMU (BNO085, BNO055, MPU6050, etc. running on STM32 or similar) against a high-accuracy smartphone IMU in real time — over a live web dashboard on your PC.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Android App](#android-app)
   - [Tech Stack](#android-tech-stack)
   - [Screens](#screens)
   - [Sensor Pipeline](#sensor-pipeline)
   - [UDP Streaming](#udp-streaming)
   - [Data Export](#data-export)
4. [PC Web Dashboard](#pc-web-dashboard)
   - [Tech Stack](#web-tech-stack)
   - [Modes](#modes)
   - [Zero Reference System](#zero-reference-system)
   - [Rate Matching](#rate-matching)
   - [Sync Monitoring](#sync-monitoring)
5. [Python Server](#python-server)
   - [Data Sources](#data-sources)
   - [Serial Parser](#serial-parser)
   - [WebSocket Broadcaster](#websocket-broadcaster)
6. [Setup & Running](#setup--running)
   - [Android App Setup](#android-app-setup)
   - [PC Server Setup](#pc-server-setup)
   - [Connecting Phone to Dashboard](#connecting-phone-to-dashboard)
   - [Connecting Hobby IMU](#connecting-hobby-imu)
7. [Network Setup](#network-setup)
8. [STM32 Serial Format](#stm32-serial-format)
9. [Project Structure](#project-structure)

---

## Overview

This project has two components that work together:

| Component | What it does |
|-----------|-------------|
| **Android App** | Reads phone IMU sensors in real time, visualizes them with a premium UI, and streams data to a PC over UDP |
| **PC Web Dashboard** | Receives data from the phone (UDP) and a hobby IMU (serial/USB CDC), displays both, and enables precise comparison |

**Primary use case:** You have a hobby IMU (e.g. BNO085 on an STM32 dev board). You want to verify its accuracy, drift characteristics, and noise floor against a reference. The phone IMU serves as that high-accuracy reference — phone IMUs are consumer-grade MEMS sensors running factory-calibrated fusion algorithms, making them excellent benchmarks for hobbyist hardware.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     ANDROID PHONE                           │
│                                                             │
│  SensorManager ──► SensorRepository ──► SensorViewModel    │
│  (Accel/Gyro/     (Quaternion→Euler    (StateFlow to UI)   │
│   Mag/RotVec)      conversion)                              │
│                          │                                  │
│                     UDPStreamer ──► UDP packets (CSV/JSON)  │
└────────────────────────────┬────────────────────────────────┘
                             │ Wi-Fi (same network)
                             ▼ port 8765
┌─────────────────────────────────────────────────────────────┐
│                     PC - server.py                          │
│                                                             │
│  [UDP Thread]  ──► phone_data dict                         │
│  [Serial Thread] ──► imu_data dict    ◄── STM32 USB CDC    │
│  [Broadcaster] ──► WebSocket @ 1/Hz                        │
│  [Flask]       ──► serves web/                             │
└────────────────────────────┬────────────────────────────────┘
                             │ WebSocket (localhost)
                             ▼ port 5000
┌─────────────────────────────────────────────────────────────┐
│                  BROWSER - index.html                       │
│                                                             │
│  SocketIO client ──► data handler                          │
│  Canvas charts (RollingChart)                              │
│  Canvas gauges  (ArcGauge)                                 │
│  Three.js cubes (Cube3D)                                   │
│  Mode: Phone / IMU / Compare                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Android App

### Android Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.0.21 |
| UI Framework | Jetpack Compose | BOM 2024.12.01 |
| Design System | Material 3 | via Compose BOM |
| Architecture | MVVM + StateFlow | — |
| Navigation | Navigation Compose + HorizontalPager | 2.8.5 |
| Lifecycle | Lifecycle Runtime + ViewModel Compose | 2.8.7 |
| Concurrency | Kotlin Coroutines + Flow | 1.9.0 |
| Networking | Java `DatagramSocket` (UDP) | built-in |
| Sensors | Android `SensorManager` | built-in |
| Build System | Gradle (Kotlin DSL) | 8.9 |
| Min SDK | Android 8.0 Oreo | API 26 |
| Target SDK | Android 15 | API 35 |

### Screens

The app uses a `HorizontalPager` for tab navigation — you can swipe between screens or tap the bottom nav. Navigation to sensor detail screens uses a standard NavHost push transition above the pager.

#### 1. Dashboard (Home)
- **YPR Banner**: Live Yaw/Pitch/Roll in degrees across the top, animating with spring physics
- **Sensor Cards**: Four cards — Accelerometer, Gyroscope, Magnetometer, Orientation — each with a mini rolling line chart (X/Y/Z axes in red/green/blue)
- **Statistics**: Each card shows min/max/mean/magnitude computed over the current data window
- **Tap to Drill Down**: Tapping any card navigates to a full-screen sensor detail view with larger charts and a scrollable history

#### 2. 3D View (Orientation)
- **3D Wireframe Cube**: A wireframe box rendered on Canvas with perspective projection, rotating in real time using Euler angles derived from the rotation vector sensor. XYZ axis lines are drawn in red/green/blue.
- **YPR Arc Gauges**: Three semi-circular arc gauges (270° sweep) for Yaw, Pitch, Roll with:
  - Correct display values (raw degrees, not shifted) using `displayValue` / `arcFraction` separation
  - Tick marks at major/minor intervals
  - Animated endpoint dot
  - Spring-animated fill fraction
  - Range labels (±180°, ±90°)
- **Euler Angles Readout**: Large numeric values with +/- prefix and colored vertical badge strips
- **Quaternion Display**: All four quaternion components (w, x, y, z) in monospace font
- **Rotation Matrix**: Full 3×3 rotation matrix rendered with AnimatedVisibility entrance

#### 3. Stream
- Target IP address and port configuration
- Sample rate selector (up to 200 Hz)
- Format toggle: CSV or JSON
- Animated START/STOP button with live packet counter
- Connection status

#### 4. Settings
- Sensor rate control
- CSV and JSON export to Documents directory

### Sensor Pipeline

```
Android SensorManager
    │
    ├── TYPE_ACCELEROMETER   → ax, ay, az  (m/s²)
    ├── TYPE_GYROSCOPE       → gx, gy, gz  (rad/s)
    ├── TYPE_MAGNETIC_FIELD  → mx, my, mz  (μT)
    └── TYPE_ROTATION_VECTOR → quaternion (w, x, y, z)
                                    │
                              SensorMath.kt
                                    │
                    quaternionToEuler() using atan2 / asin
                                    │
                    Yaw (-180..+180°), Pitch (-90..+90°), Roll (-180..+180°)
```

**Quaternion to Euler conversion:**
```kotlin
val sinr_cosp = 2f * (w*x + y*z)
val cosr_cosp = 1f - 2f * (x*x + y*y)
roll = atan2(sinr_cosp, cosr_cosp)           // -π..+π

val sinp = 2f * (w*y - z*x)
pitch = if (abs(sinp) >= 1f) copySign(π/2, sinp) else asin(sinp)  // -π/2..+π/2

val siny_cosp = 2f * (w*z + x*y)
val cosy_cosp = 1f - 2f * (y*y + z*z)
yaw = atan2(siny_cosp, cosy_cosp)            // -π..+π
```
All values converted to degrees before display and streaming.

### UDP Streaming

The `UDPStreamer` runs on a background coroutine and sends packets to the configured IP:port at the configured sample rate.

**CSV format** (17 fields):
```
timestamp_ms,ax,ay,az,gx,gy,gz,mx,my,mz,qw,qx,qy,qz,yaw,pitch,roll
```
Example:
```
1713483600123,0.12,-9.81,0.34,0.001,-0.002,0.003,22.1,-14.3,44.2,0.9999,-0.0001,0.0049,-0.0132,1.52,0.01,0.56
```

**JSON format:**
```json
{
  "t": 1713483600123,
  "accel": [0.12, -9.81, 0.34],
  "gyro":  [0.001, -0.002, 0.003],
  "mag":   [22.1, -14.3, 44.2],
  "quat":  [0.9999, -0.0001, 0.0049, -0.0132],
  "euler": {"yaw": 1.52, "pitch": 0.01, "roll": 0.56}
}
```

### Data Export

Tap **Export CSV** or **Export JSON** in Settings. Files are saved to:
```
/storage/emulated/0/Documents/IMU_<timestamp>.csv
/storage/emulated/0/Documents/IMU_<timestamp>.json
```

---

## PC Web Dashboard

### Web Tech Stack

| Component | Technology |
|-----------|-----------|
| Backend server | Python 3 + Flask |
| WebSocket | Flask-SocketIO + eventlet |
| Serial/USB CDC | pyserial |
| Frontend | Vanilla HTML + CSS + JavaScript |
| 3D rendering | Three.js (CDN) |
| Charts | Custom Canvas API (no chart library) |
| WebSocket client | Socket.IO JS client (CDN) |
| Fonts | Google Fonts — Inter + JetBrains Mono |

### Modes

Switch between modes using the three buttons in the header. Each mode shows different panels:

#### Phone Only
Shows everything the Android app is sending:
- YPR arc gauges (same style as app, correct fractions)
- Rolling line charts for Accelerometer, Gyroscope, Magnetometer (200-sample window)
- 3D wireframe cube rotating with phone orientation (Three.js WebGL)
- Live sample rate badge

#### IMU Only
Shows data arriving from the STM32 over serial/USB CDC:
- YPR arc gauges
- Gyroscope and Accelerometer charts
- 3D wireframe cube
- Live sample rate badge

#### Compare (IMU + Phone)
The core comparison mode:
- **Two 3D cubes side by side** — phone (cyan) and hobby IMU (orange) rotating independently but simultaneously. Visual misalignment is immediately obvious.
- **ΔYaw, ΔPitch, ΔRoll cards** — live angular error with 60-sample rolling charts, showing how the error evolves over time
- **Comparison table** — phone value | IMU value | Δ error, for all three Euler angles
- **Overlaid Yaw chart** — both sources on the same time axis in different colors

### Zero Reference System

A critical feature for fair comparison. Both IMUs have independent absolute orientations when powered on — the phone might show Yaw=127° while the IMU shows Yaw=34° even if physically aligned. The zero reference solves this.

**How it works:**
1. Place both IMUs in the same physical orientation
2. Click **"Set Zero"** in the dashboard header
3. Server records the current euler angles from both sources as offsets
4. All subsequent values are computed as: `output = actual - offset`
5. Both readings become relative — matched physical position = matched values

**Angle wrapping** is applied correctly using:
```python
def angle_diff(a, b):
    d = a - b
    while d >  180: d -= 360
    while d < -180: d += 360
    return d
```
This handles the ±180° boundary: e.g. phone at 179°, IMU at -178° → delta = +3°, not 357°.

Click **"Reset"** to return to absolute values.

### Rate Matching

The two sources will almost certainly produce data at different rates:
- Phone: configurable in app (e.g. 50–200 Hz)
- STM32: depends on firmware (typically 50–200 Hz)

The server's **broadcaster** runs at a configurable `target_rate_hz` (default 50 Hz). It wakes up at exactly `1/Hz` intervals and emits the **latest available value** from each source. This:
- Decimates the faster source to the target rate
- Gives both sources the same number of data points at the same cadence
- Means the browser always receives one phone value and one IMU value per frame — perfectly aligned

**Change rate live** using the `10 | 25 | 50 | 100 Hz` buttons in the sync bar. The server responds with `rate_ack` and highlights the active button.

**CLI:**
```bash
python server.py --rate 50    # default
python server.py --rate 100   # high speed
```

### Sync Monitoring

The sync bar below the header shows real-time synchronisation status:

| Field | Description |
|-------|-------------|
| **Phone** | Timestamp of last received phone packet |
| **IMU** | Timestamp of last received IMU packet |
| **Sync offset** | `phone_arrival_time - imu_arrival_time` in milliseconds |
| **Mode** | `absolute` or `relative (zeroed)` |
| **Output rate** | Active broadcaster rate |

Sync offset color coding:
- 🟢 **Green** `< 20ms` — excellent sync
- 🟡 **Yellow** `20–100ms` — acceptable (USB or network latency)
- 🔴 **Red** `> 100ms` — significant sync issue (check USB CDC driver or Wi-Fi)

**Timestamps** switch automatically:
- **Before zero**: wall-clock time (`HH:MM:SS.mmm`) of last packet arrival
- **After zero**: relative time from zero point (`t=+2.347s`)

Both sources use `time.perf_counter()` (PC high-resolution clock) as the common timestamp reference, eliminating clock skew between the phone's embedded timestamp and the STM32's internal tick counter.

---

## Python Server

### Data Sources

The server runs two background threads simultaneously:

**UDP Thread** — `udp_listener(port)`
- Binds to `0.0.0.0:8765` (all interfaces)
- Receives datagrams from the Android app
- Parses CSV or JSON format
- Updates `phone_data` dict under a lock
- Marks `connected=False` if no packet received for >3 seconds
- Tracks rolling sample rate (packets/second)

**Serial Thread** — `serial_listener(port, baud)`
- Opens the specified COM port (works identically for UART and USB CDC/VCP)
- Reads lines, attempts to parse each line
- Auto-reconnects on disconnect (3-second retry loop)
- Updates `imu_data` dict under a lock
- Tracks rolling sample rate

### Serial Parser

The `parse_imu_line()` function supports **three formats automatically** — no configuration needed:

**Key-value format** (most readable):
```
YAW:127.30 PITCH:-12.10 ROLL:3.80
```
Also supports extended key-value:
```
YAW:127.30 PITCH:-12.10 ROLL:3.80 AX:0.12 AY:-9.81 AZ:0.34 GX:0.001 GY:-0.002 GZ:0.003
```

**Plain CSV** (minimum overhead):
```
127.30,-12.10,3.80
```
Or extended with accel and gyro:
```
127.30,-12.10,3.80,0.12,-9.81,0.34,0.001,-0.002,0.003
```

**JSON** (most flexible):
```json
{"yaw": 127.30, "pitch": -12.10, "roll": 3.80}
```
Or with full sensor data:
```json
{"yaw": 127.30, "pitch": -12.10, "roll": 3.80, "accel": [0.12, -9.81, 0.34], "gyro": [0.001, -0.002, 0.003]}
```

### WebSocket Broadcaster

The broadcaster runs as a SocketIO background task (not a thread — eventlet greenlet):

```
every 1/target_rate_hz seconds:
    1. Read latest phone_data and imu_data (under lock)
    2. Apply zero offsets if zeroed (angle-wrapped subtraction)
    3. Compute relative timestamps from zero_pc_time
    4. Compute sync_offset_ms = phone.pc_ts - imu.pc_ts
    5. Emit "sensor_data" payload to all connected browsers
```

The payload structure:
```json
{
  "phone": {
    "connected": true,
    "euler": {"yaw": 1.52, "pitch": 0.01, "roll": 0.56},
    "accel": [0.12, -9.81, 0.34],
    "gyro": [0.001, -0.002, 0.003],
    "mag": [22.1, -14.3, 44.2],
    "quat": [0.9999, -0.0001, 0.0049, -0.0132],
    "rate_hz": 98.4,
    "rel_ts": 2.347
  },
  "imu": { ... same structure ... },
  "zeroed": true,
  "sync_offset_ms": 6.2,
  "target_rate_hz": 50,
  "ts": 1713483602.456
}
```

---

## Setup & Running

### Android App Setup

**Requirements:**
- Android Studio (Hedgehog or newer)
- Physical Android device (API 26+) — emulators have no real sensors
- USB cable for first deploy

**Steps:**
1. Open Android Studio → File → Open → `IMU_APP/`
2. Let Gradle sync complete (downloads all dependencies automatically)
3. Enable USB Debugging on your phone:
   - Settings → About Phone → tap Build Number 7 times
   - Settings → Developer Options → USB Debugging → ON
4. Connect phone via USB
5. Select your device in Android Studio toolbar
6. Click **Run ▶**

### PC Server Setup

**Requirements:**
- Python 3.8 or newer
- pip

```bash
cd IMU_APP/pc_receiver

# Install dependencies (one-time)
pip install flask flask-socketio eventlet pyserial

# Start server (phone data only)
python server.py

# Start server (phone + STM32 hobby IMU)
python server.py --serial COM4 --baud 115200

# All options
python server.py --help
```

**All CLI arguments:**
```
--serial   COM port for hobby IMU  (e.g. COM3, /dev/ttyUSB0)
--baud     Serial baud rate        (default: 115200)
--udp-port UDP port for phone data (default: 8765)
--web-port Web server port         (default: 5000)
--rate     Output rate to browser  (default: 50 Hz)
--no-serial  Disable serial input
--no-udp     Disable UDP input
```

Open `http://localhost:5000` in your browser.

### Connecting Phone to Dashboard

**Both phone and PC must be on the same network.**

> **Important:** `192.168.137.1` is your PC's own Mobile Hotspot IP — don't use this.
> Use the IP your PC gets on the Wi-Fi network it's *connected to*.

Find PC's IP:
```powershell
ipconfig
# Look for "Wi-Fi" adapter → IPv4 Address
# Example: 10.217.85.204
```

In the Android app:
1. Go to **Stream** tab
2. Enter the IP from above (e.g. `10.217.85.204`)
3. Port: `8765`
4. Hit **START STREAMING**

The **Phone** indicator in the dashboard header turns cyan within 1 second.

**Firewall** — if data doesn't appear, allow UDP 8765 (run PowerShell as Admin):
```powershell
netsh advfirewall firewall add rule name="IMU UDP 8765" protocol=UDP dir=in localport=8765 action=allow
```

### Connecting Hobby IMU

USB CDC/VCP works identically to UART — pyserial treats them the same.

Find the COM port:
1. Device Manager → Ports (COM & LPT)
2. Look for "STMicroelectronics Virtual COM Port" or "USB Serial Device"
3. Note the `COMx` number

```bash
python server.py --serial COM4 --baud 115200
```

Switch to **IMU only** or **Compare** mode in the browser.

---

## Network Setup

| Scenario | PC IP to use in app |
|----------|-------------------|
| Both phone and PC on home router Wi-Fi | `192.168.1.x` (from `ipconfig` → Wi-Fi adapter) |
| PC connected to phone's mobile hotspot | `10.x.x.x` or `192.168.43.x` (from `ipconfig` → Wi-Fi adapter) |
| PC's own Mobile Hotspot, phone connected to it | `192.168.137.1` (PC's hotspot gateway) |

**Never mix scenarios** — if the PC is connected to the phone's hotspot, it will have a different IP than its own hotspot address.

---

## STM32 Serial Format

Your firmware just needs to print one line per sample over USB CDC (or UART). Any of the following work without any server configuration:

```c
// Key-value (recommended — clear and debuggable)
printf("YAW:%.2f PITCH:%.2f ROLL:%.2f\r\n", yaw, pitch, roll);

// CSV (minimum bytes)
printf("%.2f,%.2f,%.2f\r\n", yaw, pitch, roll);

// JSON
printf("{\"yaw\":%.2f,\"pitch\":%.2f,\"roll\":%.2f}\r\n", yaw, pitch, roll);

// Extended with raw sensors
printf("YAW:%.2f PITCH:%.2f ROLL:%.2f AX:%.4f AY:%.4f AZ:%.4f GX:%.4f GY:%.4f GZ:%.4f\r\n",
       yaw, pitch, roll, ax, ay, az, gx, gy, gz);
```

**Baud rate for USB CDC:** Any value works — USB framing handles speed independently. `115200` is conventional.

**Expected units:**
- Yaw, Pitch, Roll: **degrees** (not radians)
- Range: Yaw ±180°, Pitch ±90°, Roll ±180°

---

## Project Structure

```
IMU_APP/
├── app/
│   └── src/main/java/com/robomanipal/imusensor/
│       ├── sensor/
│       │   ├── SensorData.kt          # Data models: SensorReading, OrientationData
│       │   └── SensorRepository.kt    # SensorManager wrapper, quaternion→euler
│       ├── streaming/
│       │   ├── StreamConfig.kt        # IP, port, rate, format configuration
│       │   └── UDPStreamer.kt         # Coroutine-based UDP sender
│       ├── export/
│       │   ├── CsvExporter.kt         # CSV file writer
│       │   └── JsonExporter.kt        # JSON file writer
│       ├── viewmodel/
│       │   ├── SensorViewModel.kt     # StateFlow for sensor data
│       │   └── StreamViewModel.kt     # Streaming start/stop state
│       └── ui/
│           ├── theme/                 # Colors, typography, MaterialTheme
│           ├── components/
│           │   ├── GlassCard.kt       # Glassmorphism card composable
│           │   ├── SensorChart.kt     # Canvas rolling line chart
│           │   ├── YPRGauges.kt       # Arc gauge row (fixed value/fraction split)
│           │   ├── OrientationCube.kt # 3D wireframe cube (Canvas)
│           │   └── AnimatedNavBar.kt  # Bottom navigation bar
│           ├── screens/
│           │   ├── DashboardScreen.kt
│           │   ├── OrientationScreen.kt
│           │   ├── StreamScreen.kt
│           │   ├── SettingsScreen.kt
│           │   └── SensorDetailScreen.kt
│           └── navigation/
│               └── AppNavigation.kt  # HorizontalPager + NavHost routing
│
├── pc_receiver/
│   ├── server.py          # Flask + SocketIO + UDP + Serial backend
│   ├── requirements.txt   # Python dependencies
│   └── web/
│       ├── index.html     # Dashboard HTML (three mode panels)
│       ├── style.css      # Dark glassmorphism CSS
│       └── app.js         # SocketIO client, Canvas charts, Three.js cubes
│
└── README.md
```

---

## Key Design Decisions

### Why UDP for phone streaming?
UDP is connectionless and has no retransmit overhead. A dropped IMU packet is meaningless — by the time a TCP retransmit arrives, the data is stale. UDP gives lowest possible latency with no head-of-line blocking.

### Why Canvas for charts, not a charting library?
Full control over rendering. Libraries add DOM overhead and opaque animation systems. The custom `RollingChart` draws at exactly the right time with exactly the right style. Glow effects, grid, zero-line, and axis colors are all precisely controllable.

### Why separate `displayValue` and `arcFraction` in the YPR gauge?
The arc requires a 0–1 fraction for its sweep angle. Pitch spans -90 to +90, requiring a `(pitch + 90) / 180` shift to get a fraction. If you display that shifted value, you show `90.1°` when pitch is `0.01°`. The separation ensures the text always shows the raw degree value while the arc uses the correct fraction for drawing.

### Why `time.perf_counter()` for sync timestamps?
`time.time()` is wall-clock and can jump on NTP corrections. `perf_counter()` is a monotonic high-resolution clock — guaranteed to never go backwards, with microsecond precision. Since we only care about the *difference* between phone and IMU arrival times, a monotonic clock is ideal.

### Why angle-wrapped diff for zero reference?
Simple subtraction fails at the ±180° boundary. A device at 178° that rotates 5° to 183° wraps to -177°. Angle wrapping ensures the delta correctly shows +5° in all boundary cases.
