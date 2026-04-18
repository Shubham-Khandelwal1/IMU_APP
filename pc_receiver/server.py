#!/usr/bin/env python3
from __future__ import annotations
"""
IMU Comparison Server
=====================
Receives data from:
  • Android phone  → UDP packets (JSON or CSV) on port 8765
  • Hobby IMU (STM32) → Serial/UART on configurable COM port

Serves a web dashboard at http://localhost:5000
Pushes live data to the browser via WebSocket (SocketIO).

Usage:
    python server.py
    python server.py --serial COM3 --baud 115200
    python server.py --udp-port 8765 --web-port 5000
    python server.py --serial COM3 --no-udp      # IMU only
    python server.py --no-serial                  # Phone only
"""

import argparse
import json
import re
import socket
import threading
from typing import Optional
import time
from datetime import datetime

import eventlet
eventlet.monkey_patch()

from flask import Flask, render_template, send_from_directory
from flask_socketio import SocketIO

# ── Flask + SocketIO setup ─────────────────────────────────────────────────
app = Flask(__name__, static_folder="web")
app.config["SECRET_KEY"] = "imu-sensor-secret"
socketio = SocketIO(app, cors_allowed_origins="*", async_mode="eventlet")

# ── Shared state ───────────────────────────────────────────────────────────
phone_data = {
    "connected": False,
    "last_seen": 0,
    "pc_ts":    0.0,    # time.perf_counter() at PC arrival
    "accel": [0.0, 0.0, 0.0],
    "gyro":  [0.0, 0.0, 0.0],
    "mag":   [0.0, 0.0, 0.0],
    "quat":  [1.0, 0.0, 0.0, 0.0],
    "euler": {"yaw": 0.0, "pitch": 0.0, "roll": 0.0},
    "rate_hz": 0,
}

imu_data = {
    "connected": False,
    "last_seen": 0,
    "pc_ts":    0.0,    # time.perf_counter() at PC arrival
    "accel": [0.0, 0.0, 0.0],
    "gyro":  [0.0, 0.0, 0.0],
    "mag":   [0.0, 0.0, 0.0],
    "quat":  [1.0, 0.0, 0.0, 0.0],
    "euler": {"yaw": 0.0, "pitch": 0.0, "roll": 0.0},
    "rate_hz": 0,
}

_lock        = threading.Lock()
target_rate_hz = 50    # output rate to browser — both sources decimated to this

# ── Zero reference offsets ────────────────────────────────────────────────
# When zeroed, all euler output = actual - offset (angle-wrapped to -180..+180)
phone_offset = {"yaw": 0.0, "pitch": 0.0, "roll": 0.0}
imu_offset   = {"yaw": 0.0, "pitch": 0.0, "roll": 0.0}
zeroed       = False
zero_pc_time = 0.0    # perf_counter() snapshot at the moment Set Zero was clicked


def angle_diff(a: float, b: float) -> float:
    """Subtract two angles with wrapping to -180..+180."""
    d = a - b
    while d >  180: d -= 360
    while d < -180: d += 360
    return d


def apply_offset(euler: dict, offset: dict) -> dict:
    """Return euler angles relative to their offset reference."""
    return {
        "yaw":   angle_diff(euler["yaw"],   offset["yaw"]),
        "pitch": angle_diff(euler["pitch"], offset["pitch"]),
        "roll":  angle_diff(euler["roll"],  offset["roll"]),
    }


# ── Parsers ────────────────────────────────────────────────────────────────

def parse_phone_packet(raw: str) -> Optional[dict]:
    """Parse JSON or CSV packet from Android app."""
    raw = raw.strip()
    if not raw:
        return None
    try:
        if raw.startswith("{"):
            return json.loads(raw)
        else:
            # CSV: timestamp,ax,ay,az,gx,gy,gz,mx,my,mz,qw,qx,qy,qz,yaw,pitch,roll
            parts = raw.split(",")
            if len(parts) < 17:
                return None
            return {
                "t":     int(parts[0]),
                "accel": [float(parts[1]), float(parts[2]), float(parts[3])],
                "gyro":  [float(parts[4]), float(parts[5]), float(parts[6])],
                "mag":   [float(parts[7]), float(parts[8]), float(parts[9])],
                "quat":  [float(parts[10]), float(parts[11]), float(parts[12]), float(parts[13])],
                "euler": {
                    "yaw":   float(parts[14]),
                    "pitch": float(parts[15]),
                    "roll":  float(parts[16]),
                },
            }
    except Exception:
        return None


def parse_imu_line(line: str) -> Optional[dict]:
    """
    Parse a line from STM32 serial.  Supports multiple common formats:

      • Key-value:  YAW:127.30 PITCH:-12.10 ROLL:3.80
      • CSV header: yaw,pitch,roll   → values only (no header)
      • JSON:       {"yaw":127.30,"pitch":-12.10,"roll":3.80}
      • Extended:   a:0.1,0.2,0.3 g:0.01,0.02,0.03 y:127.3,p:-12.1,r:3.8

    Returns a dict with keys: euler, accel (optional), gyro (optional)
    """
    line = line.strip()
    if not line:
        return None

    result = {"euler": {"yaw": 0.0, "pitch": 0.0, "roll": 0.0},
              "accel": [0.0, 0.0, 0.0],
              "gyro":  [0.0, 0.0, 0.0]}

    try:
        # JSON format
        if line.startswith("{"):
            d = json.loads(line)
            if "yaw" in d:
                result["euler"] = {k: float(d[k]) for k in ("yaw", "pitch", "roll")}
            elif "euler" in d:
                result["euler"] = d["euler"]
            if "accel" in d:
                result["accel"] = d["accel"]
            if "gyro" in d:
                result["gyro"] = d["gyro"]
            return result

        # Key-value: YAW:127.30 PITCH:-12.10 ROLL:3.80
        kv = re.findall(r'(\w+)\s*[=:]\s*([+-]?\d+\.?\d*)', line, re.IGNORECASE)
        if kv:
            kv_dict = {k.lower(): float(v) for k, v in kv}
            if "yaw" in kv_dict:
                result["euler"]["yaw"]   = kv_dict["yaw"]
                result["euler"]["pitch"] = kv_dict.get("pitch", 0.0)
                result["euler"]["roll"]  = kv_dict.get("roll", 0.0)
            if "ax" in kv_dict:
                result["accel"] = [kv_dict.get("ax", 0), kv_dict.get("ay", 0), kv_dict.get("az", 0)]
            if "gx" in kv_dict:
                result["gyro"]  = [kv_dict.get("gx", 0), kv_dict.get("gy", 0), kv_dict.get("gz", 0)]
            if "yaw" in kv_dict:
                return result

        # Plain CSV: yaw,pitch,roll  or  yaw,pitch,roll,ax,ay,az,gx,gy,gz
        parts = line.split(",")
        nums  = []
        for p in parts:
            try:
                nums.append(float(p.strip()))
            except ValueError:
                pass
        if len(nums) >= 3:
            result["euler"] = {"yaw": nums[0], "pitch": nums[1], "roll": nums[2]}
            if len(nums) >= 6:
                result["accel"] = nums[3:6]
            if len(nums) >= 9:
                result["gyro"] = nums[6:9]
            return result

    except Exception:
        pass

    return None


# ── UDP listener thread (phone data) ──────────────────────────────────────

def udp_listener(port: int):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("0.0.0.0", port))
    sock.settimeout(1.0)
    print(f"[UDP]  Listening on port {port}")

    pkt_count = 0
    t_window  = time.time()

    while True:
        try:
            data, _ = sock.recvfrom(4096)
            parsed   = parse_phone_packet(data.decode("utf-8", errors="ignore"))
            if parsed:
                pkt_count += 1
                now = time.time()
                elapsed = now - t_window
                if elapsed >= 1.0:
                    rate      = pkt_count / elapsed
                    pkt_count = 0
                    t_window  = now
                else:
                    rate = phone_data["rate_hz"]

                with _lock:
                    phone_data.update({
                        "connected": True,
                        "last_seen": time.time(),
                        "pc_ts":     time.perf_counter(),
                        "accel":  parsed.get("accel", [0, 0, 0]),
                        "gyro":   parsed.get("gyro",  [0, 0, 0]),
                        "mag":    parsed.get("mag",   [0, 0, 0]),
                        "quat":   parsed.get("quat",  [1, 0, 0, 0]),
                        "euler":  parsed.get("euler", {"yaw": 0, "pitch": 0, "roll": 0}),
                        "rate_hz": round(rate, 1),
                    })
        except socket.timeout:
            # Mark disconnected if no packet for >3 s
            with _lock:
                if time.time() - phone_data["last_seen"] > 3:
                    phone_data["connected"] = False


# ── Serial listener thread (hobby IMU data) ───────────────────────────────

def serial_listener(port: str, baud: int):
    import serial  # lazy import so --no-serial doesn't require pyserial

    while True:
        try:
            print(f"[Serial] Connecting to {port} @ {baud}")
            ser = serial.Serial(port, baud, timeout=1)
            print(f"[Serial] Connected to {port}")

            pkt_count = 0
            t_window  = time.time()

            while True:
                line = ser.readline().decode("utf-8", errors="ignore")
                parsed = parse_imu_line(line)
                if parsed:
                    pkt_count += 1
                    now     = time.time()
                    elapsed = now - t_window
                    if elapsed >= 1.0:
                        rate      = pkt_count / elapsed
                        pkt_count = 0
                        t_window  = now
                    else:
                        rate = imu_data["rate_hz"]

                    with _lock:
                        imu_data.update({
                            "connected": True,
                            "last_seen": time.time(),
                            "pc_ts":     time.perf_counter(),
                            "accel":  parsed.get("accel", [0, 0, 0]),
                            "gyro":   parsed.get("gyro",  [0, 0, 0]),
                            "euler":  parsed["euler"],
                            "rate_hz": round(rate, 1),
                        })

        except Exception as e:
            print(f"[Serial] Error: {e} — retrying in 3 s")
            with _lock:
                imu_data["connected"] = False
            time.sleep(3)


# ── Broadcaster — pushes to all browser clients at 30 fps ─────────────────

def broadcaster():
    while True:
        with _lock:
            rate_hz = target_rate_hz
            now_pc  = time.perf_counter()

            p_rel_ts = round(phone_data["pc_ts"] - zero_pc_time, 3) if zeroed else round(phone_data["pc_ts"], 3)
            m_rel_ts = round(imu_data["pc_ts"]   - zero_pc_time, 3) if zeroed else round(imu_data["pc_ts"],   3)
            sync_offset_ms = round((phone_data["pc_ts"] - imu_data["pc_ts"]) * 1000, 1)

            p_euler = apply_offset(phone_data["euler"], phone_offset) if zeroed else phone_data["euler"]
            m_euler = apply_offset(imu_data["euler"],   imu_offset)   if zeroed else imu_data["euler"]

            p_out = dict(phone_data)
            m_out = dict(imu_data)
            p_out["euler"]  = p_euler
            m_out["euler"]  = m_euler
            p_out["rel_ts"] = p_rel_ts
            m_out["rel_ts"] = m_rel_ts

            payload = {
                "phone":          p_out,
                "imu":            m_out,
                "zeroed":         zeroed,
                "sync_offset_ms": sync_offset_ms,
                "target_rate_hz": rate_hz,
                "ts":             time.time(),
            }
        socketio.emit("sensor_data", payload)
        eventlet.sleep(1.0 / max(rate_hz, 1))


# ── Flask routes ───────────────────────────────────────────────────────────

@app.route("/")
def index():
    return send_from_directory("web", "index.html")

@app.route("/<path:filename>")
def static_files(filename):
    return send_from_directory("web", filename)

@socketio.on("connect")
def on_connect():
    print("[WS] Client connected")

@socketio.on("disconnect")
def on_disconnect():
    print("[WS] Client disconnected")

@socketio.on("zero_reference")
def on_zero():
    global zeroed, zero_pc_time
    with _lock:
        zero_pc_time          = time.perf_counter()
        phone_offset["yaw"]   = phone_data["euler"]["yaw"]
        phone_offset["pitch"] = phone_data["euler"]["pitch"]
        phone_offset["roll"]  = phone_data["euler"]["roll"]
        imu_offset["yaw"]     = imu_data["euler"]["yaw"]
        imu_offset["pitch"]   = imu_data["euler"]["pitch"]
        imu_offset["roll"]    = imu_data["euler"]["roll"]
        zeroed = True
    print(f"[Zero] t=0 set. Phone ref yaw={phone_offset['yaw']:.1f} | IMU ref yaw={imu_offset['yaw']:.1f}")

@socketio.on("reset_reference")
def on_reset():
    global zeroed, zero_pc_time
    with _lock:
        zeroed       = False
        zero_pc_time = 0.0
    print("[Zero] Cleared — showing absolute values")

@socketio.on("set_rate")
def on_set_rate(hz):
    global target_rate_hz
    hz = max(1, min(int(hz), 200))   # clamp 1-200 Hz
    with _lock:
        target_rate_hz = hz
    print(f"[Rate] Output rate set to {hz} Hz")
    socketio.emit("rate_ack", {"target_rate_hz": hz})


# ── Entry point ────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="IMU Comparison Web Server")
    parser.add_argument("--serial",   type=str,  default=None,  help="Serial port (e.g. COM3 or /dev/ttyUSB0)")
    parser.add_argument("--baud",     type=int,  default=115200, help="Serial baud rate")
    parser.add_argument("--udp-port", type=int,  default=8765,  help="UDP port for phone data")
    parser.add_argument("--web-port", type=int,  default=5000,  help="Web server port")
    parser.add_argument("--rate",     type=int,  default=50,    help="Output data rate to browser in Hz (default 50)")
    parser.add_argument("--no-serial",action="store_true",      help="Disable serial (phone only)")
    parser.add_argument("--no-udp",   action="store_true",      help="Disable UDP (IMU only)")
    args = parser.parse_args()

    global target_rate_hz
    target_rate_hz = args.rate

    print("=" * 44)
    print("     IMU Comparison Dashboard Server")
    print(f"     http://localhost:{args.web_port}")
    print("=" * 44)

    # Start background threads
    if not args.no_udp:
        t = threading.Thread(target=udp_listener, args=(args.udp_port,), daemon=True)
        t.start()

    if not args.no_serial and args.serial:
        t = threading.Thread(target=serial_listener, args=(args.serial, args.baud), daemon=True)
        t.start()
    elif not args.no_serial and not args.serial:
        print("[Serial] No --serial port specified. Serial input disabled.")
        print("         Run with --serial COM3 to enable hobby IMU data.")

    # Start WebSocket broadcaster
    socketio.start_background_task(broadcaster)

    socketio.run(app, host="0.0.0.0", port=args.web_port, debug=False)


if __name__ == "__main__":
    main()
