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
import traceback
from collections import deque
from typing import Optional
import time
from datetime import datetime
import numpy as np
from scipy.optimize import minimize

import fusion

from flask import Flask, render_template, send_from_directory
from flask_socketio import SocketIO

# ── Flask + SocketIO setup ─────────────────────────────────────────────────
app = Flask(__name__, static_folder="web")
app.config["SECRET_KEY"] = "imu-sensor-secret"
socketio = SocketIO(app, cors_allowed_origins="*", async_mode="threading")

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
    "raw_euler": {"yaw": 0.0, "pitch": 0.0, "roll": 0.0},
    "calib": [0, 0, 0, 0],
    "rate_hz": 0,
}

_lock          = threading.Lock()
target_rate_hz = 50    # output rate to browser — both sources decimated to this

# ── Analysis ring buffers ───────────────────────────────────────────
MAX_BUFFER     = 12000   # 4 min @ 50 Hz
phone_buffer   = deque(maxlen=MAX_BUFFER)   # list of {ts, gx,gy,gz, ax,ay,az, yaw,pitch,roll}
imu_buffer     = deque(maxlen=MAX_BUFFER)
recording      = False
record_start   = 0.0

# ── Zero reference offsets ────────────────────────────────────────────────
# When zeroed, all euler output = actual - offset (angle-wrapped to -180..+180)
phone_offset = {"yaw": 0.0, "pitch": 0.0, "roll": 0.0}
imu_offset   = {"yaw": 0.0, "pitch": 0.0, "roll": 0.0}
zeroed       = False
zero_pc_time = 0.0    # perf_counter() snapshot at the moment Set Zero was clicked

# ── Axis Alignment State ──────────────────────────────────────────────────
imu_axis_map = {"perm": (0, 1, 2), "sign": (1, 1, 1)}
aligning_axes = False
align_start_ts = 0.0
align_phone_buffer = []  # list of [yaw, pitch, roll]
align_imu_buffer = []    # list of [raw_yaw, raw_pitch, raw_roll]

# ── Sensor Fusion Benchmarking State ──────────────────────────────────────
fusion_filters = {
    "complementary": fusion.ComplementaryFilter(),
    "comp_quat": fusion.ComplementaryQuatFilter(),
    "madgwick": fusion.MadgwickFilter(),
    "mahony": fusion.MahonyFilter(),
    "ekf": fusion.EKFFilter()
}

# The latest Euler outputs from the fusion algorithms
fusion_results = {
    "complementary": {"yaw": 0.0, "pitch": 0.0, "roll": 0.0},
    "comp_quat": {"yaw": 0.0, "pitch": 0.0, "roll": 0.0},
    "madgwick": {"yaw": 0.0, "pitch": 0.0, "roll": 0.0},
    "mahony": {"yaw": 0.0, "pitch": 0.0, "roll": 0.0},
    "ekf": {"yaw": 0.0, "pitch": 0.0, "roll": 0.0}
}
fusion_compute_us = { name: 0 for name in fusion_filters }
last_fusion_ts = 0.0

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
              "gyro":  [0.0, 0.0, 0.0],
              "mag":   [0.0, 0.0, 0.0],
              "calib": [0, 0, 0, 0]}

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

        # Pipe-delimited or new format: ACC:0.1,4.8,9.3 | MAG:-16.0,33.5,0.6 | GYR:...
        if "ACC:" in line or "YPR:" in line:
            parts = [p.strip() for p in line.split("|")]
            valid = False
            for part in parts:
                if ":" in part:
                    k, v = part.split(":", 1)
                    k = k.strip().upper()
                    try:
                        nums = [float(x) for x in v.split(",")]
                        if k == "ACC" and len(nums) >= 3:
                            result["accel"] = nums[:3]
                            valid = True
                        elif k == "MAG" and len(nums) >= 3:
                            result["mag"] = nums[:3]
                            valid = True
                        elif k == "GYR" and len(nums) >= 3:
                            result["gyro"] = nums[:3]
                            valid = True
                        elif k == "YPR" and len(nums) >= 3:
                            result["euler"] = {"yaw": nums[0], "pitch": nums[1], "roll": nums[2]}
                            valid = True
                        elif k == "CAL" and len(nums) >= 4:
                            result["calib"] = [int(x) for x in nums[:4]]
                            valid = True
                    except ValueError:
                        pass
            if valid:
                return result

        # Key-value: YAW:127.30 PITCH:-12.10 ROLL:3.80  or  R:0 P:-4 Y:300
        kv = re.findall(r'(\w+)\s*[=:]\s*([+-]?\d+\.?\d*)', line, re.IGNORECASE)
        if kv:
            kv_dict = {k.lower(): float(v) for k, v in kv}
            
            # Map full names or single letter aliases
            val_y = kv_dict.get("yaw", kv_dict.get("y"))
            val_p = kv_dict.get("pitch", kv_dict.get("p"))
            val_r = kv_dict.get("roll", kv_dict.get("r"))
            
            if val_y is not None or val_p is not None or val_r is not None:
                result["euler"]["yaw"]   = val_y if val_y is not None else 0.0
                result["euler"]["pitch"] = val_p if val_p is not None else 0.0
                result["euler"]["roll"]  = val_r if val_r is not None else 0.0

            if "ax" in kv_dict:
                result["accel"] = [kv_dict.get("ax", 0), kv_dict.get("ay", 0), kv_dict.get("az", 0)]
            if "gx" in kv_dict:
                result["gyro"]  = [kv_dict.get("gx", 0), kv_dict.get("gy", 0), kv_dict.get("gz", 0)]
            if "mx" in kv_dict:
                result["mag"]  = [kv_dict.get("mx", 0), kv_dict.get("my", 0), kv_dict.get("mz", 0)]
                
            if val_y is not None or val_p is not None or val_r is not None:
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
                    pc_ts = time.perf_counter()
                    phone_data.update({
                        "connected": True,
                        "last_seen": time.time(),
                        "pc_ts":     pc_ts,
                        "accel":  parsed.get("accel", [0, 0, 0]),
                        "gyro":   parsed.get("gyro",  [0, 0, 0]),
                        "mag":    parsed.get("mag",   [0, 0, 0]),
                        "quat":   parsed.get("quat",  [1, 0, 0, 0]),
                        "euler":  parsed.get("euler", {"yaw": 0, "pitch": 0, "roll": 0}),
                        "rate_hz": round(rate, 1),
                    })
                    if recording:
                        e = phone_data["euler"]
                        g = phone_data["gyro"]
                        a = phone_data["accel"]
                        phone_buffer.append({
                            "ts": pc_ts - record_start,
                            "gx": g[0], "gy": g[1], "gz": g[2],
                            "ax": a[0], "ay": a[1], "az": a[2],
                            "mx": phone_data["mag"][0], "my": phone_data["mag"][1], "mz": phone_data["mag"][2],
                            "yaw": e["yaw"], "pitch": e["pitch"], "roll": e["roll"],
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
            print(f"[Serial] Connected to {port}", flush=True)
            ser.reset_input_buffer()

            try:
                pkt_count = 0
                t_window  = time.time()

                while True:
                    line = ser.readline().decode("utf-8", errors="ignore")
                    if not line.strip(): 
                        continue
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
                            pc_ts = time.perf_counter()
                            
                            raw_e = parsed.get("euler", {"yaw": 0.0, "pitch": 0.0, "roll": 0.0})
                            raw_acc = parsed.get("accel", [0.0, 0.0, 0.0])
                            raw_gyr = parsed.get("gyro",  [0.0, 0.0, 0.0])
                            raw_mag = parsed.get("mag",   [0.0, 0.0, 0.0])
                            
                            # Apply axis alignment: simple permutation + sign flip
                            perm = imu_axis_map["perm"]
                            sign = imu_axis_map["sign"]
                            
                            def map_vec(v):
                                if len(v) != 3: return [0,0,0]
                                return [sign[0]*v[perm[0]], sign[1]*v[perm[1]], sign[2]*v[perm[2]]]
                                
                            mapped_e = {
                                "yaw":   sign[0] * raw_e["yaw" if perm[0]==0 else "pitch" if perm[0]==1 else "roll"],
                                "pitch": sign[1] * raw_e["yaw" if perm[1]==0 else "pitch" if perm[1]==1 else "roll"],
                                "roll":  sign[2] * raw_e["yaw" if perm[2]==0 else "pitch" if perm[2]==1 else "roll"],
                            }
                            
                            # Also remap raw vectors for the fusion algorithms
                            acc_mapped = map_vec(raw_acc)
                            gyr_mapped = map_vec(raw_gyr)
                            mag_mapped = map_vec(raw_mag)
                            
                            # Run fusion algorithms
                            global last_fusion_ts
                            dt_fusion = pc_ts - last_fusion_ts if last_fusion_ts > 0 else 0.02
                            last_fusion_ts = pc_ts
                            
                            for name, filt in fusion_filters.items():
                                t_start = time.perf_counter()
                                filt.update(acc_mapped, gyr_mapped, mag_mapped, dt_fusion)
                                t_end = time.perf_counter()
                                fusion_results[name] = filt.get_euler()
                                
                                # Extremely simple low-pass filter on computation time
                                us = (t_end - t_start) * 1e6
                                fusion_compute_us[name] = fusion_compute_us[name] * 0.95 + us * 0.05

                            imu_data.update({
                                "connected": True,
                                "last_seen": time.time(),
                                "pc_ts":     pc_ts,
                                "accel":     acc_mapped,
                                "gyro":      gyr_mapped,
                                "mag":       mag_mapped,
                                "calib":     parsed.get("calib", [0, 0, 0, 0]),
                                "euler":     mapped_e,
                                "raw_euler": raw_e,
                                "rate_hz":   round(rate, 1),
                            })
                            if recording:

                                e = imu_data["euler"]
                                g = imu_data["gyro"]
                                a = imu_data["accel"]
                                imu_buffer.append({
                                    "ts": pc_ts - record_start,
                                    "gx": g[0], "gy": g[1], "gz": g[2],
                                    "ax": a[0], "ay": a[1], "az": a[2],
                                    "mx": imu_data["mag"][0], "my": imu_data["mag"][1], "mz": imu_data["mag"][2],
                                    "yaw": e["yaw"], "pitch": e["pitch"], "roll": e["roll"],
                                })
            finally:
                ser.close()
                print(f"[Serial] Closed connection to {port}", flush=True)

        except Exception as e:
            print(f"[Serial] FATAL Error: {e}", flush=True)
            traceback.print_exc()
            with _lock:
                imu_data["connected"] = False
            time.sleep(3)


# ── Broadcaster — pushes to all browser clients ───────────────────────────
#
# CORRELATION-BASED AXIS ALIGNMENT — How it works:
#
#  During the 10-second calibration window, we collect simultaneous
#  Euler angle samples from both the Phone (reference) and the raw
#  IMU (before any mapping).  After 10 seconds:
#
#  1. Unwrap angles to remove ±180° yaw discontinuities
#  2. Remove the mean (DC offset) from each axis — we only care
#     about how the signals CO-VARY, not their absolute values
#  3. For every possible (permutation × sign) combination (48 total):
#       For each phone axis i ∈ {Yaw, Pitch, Roll}:
#         Pair it with:  sign[i] × IMU_raw[perm[i]]
#         Compute Pearson correlation coefficient r ∈ [-1, +1]
#       Sum the 3 per-axis correlations → total_corr
#  4. The combo with the highest total_corr wins.
#
#  A perfect match gives r=+1 per axis, total=3.0.
#  If axes are swapped (e.g. pitch↔roll), only the correct permutation
#  will yield r≈+1 on all axes.  If a sign is flipped (e.g. -roll),
#  only the correct sign will yield positive r instead of negative.
#

def broadcaster():
    global aligning_axes, imu_axis_map
    import itertools
    while True:
        align_emit = None   # will hold dict to emit OUTSIDE the lock

        with _lock:
            rate_hz = target_rate_hz
            now_pc  = time.perf_counter()

            # ── Collect alignment samples ──────────────────────────
            if aligning_axes:
                p_e = phone_data.get("euler", {"yaw": 0, "pitch": 0, "roll": 0})
                r_e = imu_data.get("raw_euler", {"yaw": 0, "pitch": 0, "roll": 0})
                align_phone_buffer.append([p_e["yaw"], p_e["pitch"], p_e["roll"]])
                align_imu_buffer.append([r_e["yaw"], r_e["pitch"], r_e["roll"]])

                if now_pc - align_start_ts >= 10.0:
                    # Time's up — compute the best axis mapping
                    try:
                        n_samples = len(align_phone_buffer)
                        print(f"[Align] Collected {n_samples} samples. Computing...")

                        P = np.array(align_phone_buffer, dtype=float)
                        I = np.array(align_imu_buffer, dtype=float)

                        if n_samples > 20:
                            # Step 1: Unwrap to remove ±180° discontinuities
                            for col in range(3):
                                P[:, col] = np.unwrap(P[:, col] * np.pi / 180) * 180 / np.pi
                                I[:, col] = np.unwrap(I[:, col] * np.pi / 180) * 180 / np.pi

                            # Step 2: Remove DC offset (mean)
                            P -= np.mean(P, axis=0)
                            I -= np.mean(I, axis=0)

                            # Diagnostics — how much motion was there?
                            p_std = np.std(P, axis=0)
                            i_std = np.std(I, axis=0)
                            labels = ["Yaw", "Pitch", "Roll"]
                            print(f"[Align] Phone StdDev: {labels[0]}={p_std[0]:.2f}  {labels[1]}={p_std[1]:.2f}  {labels[2]}={p_std[2]:.2f}")
                            print(f"[Align] IMU   StdDev: {labels[0]}={i_std[0]:.2f}  {labels[1]}={i_std[1]:.2f}  {labels[2]}={i_std[2]:.2f}")

                            # Step 3: Brute-force all 48 combos
                            perms = list(itertools.permutations([0, 1, 2]))
                            signs_list = list(itertools.product([1, -1], repeat=3))

                            best_corr = -float('inf')
                            best_perm = (0, 1, 2)
                            best_sign = (1, 1, 1)

                            for perm in perms:
                                for sgn in signs_list:
                                    total_corr = 0.0
                                    for axis in range(3):
                                        phone_axis = P[:, axis]
                                        imu_axis   = sgn[axis] * I[:, perm[axis]]
                                        sp = np.std(phone_axis)
                                        si = np.std(imu_axis)
                                        if sp > 0.5 and si > 0.5:
                                            c = np.corrcoef(phone_axis, imu_axis)[0, 1]
                                            if not np.isnan(c):
                                                total_corr += c

                                    if total_corr > best_corr:
                                        best_corr = total_corr
                                        best_perm = perm
                                        best_sign = sgn

                            # Build detail strings
                            detail = []
                            for axis in range(3):
                                src = labels[best_perm[axis]]
                                sgn_str = "+" if best_sign[axis] > 0 else "-"
                                sp2 = np.std(P[:, axis])
                                si2 = np.std(best_sign[axis] * I[:, best_perm[axis]])
                                if sp2 > 0.5 and si2 > 0.5:
                                    c = np.corrcoef(P[:, axis], best_sign[axis] * I[:, best_perm[axis]])[0, 1]
                                else:
                                    c = 0.0
                                detail.append(f"Phone {labels[axis]} = {sgn_str}IMU {src} (r={c:.3f})")

                            imu_axis_map = {"perm": best_perm, "sign": best_sign}

                            print(f"[Align] DONE! Best mapping (total r = {best_corr:.3f}):")
                            for d in detail:
                                print(f"[Align]   {d}")

                            align_emit = {
                                "success": True,
                                "perm": list(best_perm),
                                "sign": list(best_sign),
                                "corr": round(best_corr, 3),
                                "detail": detail,
                            }
                        else:
                            print(f"[Align] Failed: only {n_samples} samples (need >20)")
                            align_emit = {"success": False}

                    except Exception as e:
                        print(f"[Align] ERROR during computation: {e}")
                        import traceback
                        traceback.print_exc()
                        align_emit = {"success": False}

                    aligning_axes = False

            # ── Build regular payload ──────────────────────────────
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
            
            # Additional fusion payload structure
            # To avoid sending too much data if not needed, we send it alongside but packed
            fusion_payload = {
                "phone": {"yaw": p_euler["yaw"], "pitch": p_euler["pitch"], "roll": p_euler["roll"]},
                "chip": {"yaw": m_euler["yaw"], "pitch": m_euler["pitch"], "roll": m_euler["roll"]},
                "complementary": dict(fusion_results["complementary"]),
                "comp_quat": dict(fusion_results["comp_quat"]),
                "madgwick": dict(fusion_results["madgwick"]),
                "mahony": dict(fusion_results["mahony"]),
                "ekf": dict(fusion_results["ekf"]),
                "compute_us": {k: int(v) for k, v in fusion_compute_us.items()}
            }

        # ── Emit OUTSIDE the lock (prevents deadlock) ──────────
        if align_emit is not None:
            socketio.emit("axis_align_complete", align_emit)
        socketio.emit("sensor_data", payload)
        socketio.emit("fusion_data", fusion_payload)
        socketio.sleep(1.0 / max(rate_hz, 1))


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

@socketio.on("set_fusion_param")
def handle_set_fusion_param(data):
    """Live-tune parameters for fusion algorithms."""
    filter_name = data.get("filter")
    param = data.get("param")
    value = float(data.get("value", 0))
    
    with _lock:
        if filter_name in fusion_filters:
            fusion_filters[filter_name].set_params(**{param: value})
            print(f"[Fusion] Updated {filter_name} {param} = {value}")
            socketio.emit("fusion_param_ack", {"filter": filter_name, "param": param, "value": value})

@socketio.on("reset_fusion")
def handle_reset_fusion():
    """Reset all fusion filter states."""
    with _lock:
        for f in fusion_filters.values():
            f.reset()
    print("[Fusion] Reset all filter states.")

@socketio.on("start_axis_align")
def on_start_axis_align():
    global aligning_axes, align_start_ts
    with _lock:
        aligning_axes = True
        align_start_ts = time.perf_counter()
        align_phone_buffer.clear()
        align_imu_buffer.clear()
    print("[Align] Started 10-second axis alignment calibration")
    socketio.emit("axis_align_ack", {"duration_s": 10})

@socketio.on("run_auto_tune")
def on_run_auto_tune():
    # Execute offline tuning on the recorded phone vs imu buffers
    with _lock:
        P = list(phone_buffer)
        I = list(imu_buffer)
        
    if len(I) < 50 or len(P) < 50:
        socketio.emit("auto_tune_result", {"error": "Not enough data recorded! Collect at least 5 seconds."})
        return
        
    print(f"[Fusion] Auto-Tuning over {len(I)} IMU samples...")
    
    # We must match IMU samples to Phone samples temporally. 
    # Since they both save ts = pc_ts - record_start, we can interpolate or pair them.
    # For simplicity, we just extract common points or assume comparable lengths.
    
    # Pair by closest timestamp
    paired = []
    p_idx = 0
    for im in I:
        while p_idx < len(P)-1 and P[p_idx+1]["ts"] < im["ts"]:
            p_idx += 1
        ref = P[p_idx]
        if abs(ref["ts"] - im["ts"]) < 0.1: # within 100ms
            paired.append((im, ref))
            
    if not paired:
        socketio.emit("auto_tune_result", {"error": "Sync issue between buffers."})
        return
        
    dt = paired[-1][0]["ts"] - paired[0][0]["ts"]
    mean_dt = dt / len(paired) if len(paired) > 1 else 0.02
    
    def simulate_filter(filt_class, params):
        f = filt_class()
        f.set_params(**params)
        rms_sq = 0.0
        for im, ref in paired:
            acc = [im["ax"], im["ay"], im["az"]]
            gyr = [im["gx"], im["gy"], im["gz"]]
            mag = [im["mx"], im["my"], im["mz"]]
            f.update(acc, gyr, mag, mean_dt)
            e = f.get_euler()
            rms_sq += angle_diff(ref["yaw"], e["yaw"])**2
            rms_sq += angle_diff(ref["pitch"], e["pitch"])**2
            rms_sq += angle_diff(ref["roll"], e["roll"])**2
        return math.sqrt(rms_sq / (len(paired) * 3))
        
    # Optimizing Complementary (alpha)
    res_comp = minimize(lambda x: simulate_filter(fusion.ComplementaryFilter, {"alpha": x[0]}), [0.98], bounds=[(0.5, 0.999)])
    opt_alpha = float(res_comp.x[0])
    
    # Optimizing Madgwick (beta)
    res_madg = minimize(lambda x: simulate_filter(fusion.MadgwickFilter, {"beta": x[0]}), [0.1], bounds=[(0.001, 1.0)])
    opt_beta = float(res_madg.x[0])
    
    # Optimizing Mahony Kp
    res_maho = minimize(lambda x: simulate_filter(fusion.MahonyFilter, {"kp": x[0], "ki": 0.0}), [1.0], bounds=[(0.1, 10.0)])
    opt_kp = float(res_maho.x[0])
    
    # Apply
    with _lock:
        fusion_filters["complementary"].set_params(alpha=opt_alpha)
        fusion_filters["comp_quat"].set_params(alpha=opt_alpha)
        fusion_filters["madgwick"].set_params(beta=opt_beta)
        fusion_filters["mahony"].set_params(kp=opt_kp)
        
    res_payload = {
        "success": True,
        "complementary": {"alpha": round(opt_alpha, 3)},
        "comp_quat": {"alpha": round(opt_alpha, 3)},
        "madgwick": {"beta": round(opt_beta, 3)},
        "mahony": {"kp": round(opt_kp, 2)}
    }
    print(f"[Fusion] Tuned: {res_payload}")
    socketio.emit("auto_tune_result", res_payload)


# ── Analysis recording events ──────────────────────────────────────────────

@socketio.on("start_recording")
def on_start_recording():
    global recording, record_start
    with _lock:
        phone_buffer.clear()
        imu_buffer.clear()
        record_start = time.perf_counter()
        recording    = True
    print("[Analysis] Recording started")
    socketio.emit("recording_ack", {"recording": True, "ts": record_start})

@socketio.on("stop_recording")
def on_stop_recording():
    global recording
    with _lock:
        recording = False
        n_phone = len(phone_buffer)
        n_imu   = len(imu_buffer)
    print(f"[Analysis] Recording stopped. Phone={n_phone} samples, IMU={n_imu} samples")
    socketio.emit("recording_ack", {"recording": False, "n_phone": n_phone, "n_imu": n_imu})

@socketio.on("get_analysis_data")
def on_get_analysis_data(source):
    """Send buffered raw samples to the browser for client-side analysis."""
    with _lock:
        if source == "phone":
            buf = list(phone_buffer)
            hz  = phone_data["rate_hz"]
        else:
            buf = list(imu_buffer)
            hz  = imu_data["rate_hz"]
    print(f"[Analysis] Sending {len(buf)} samples for {source}")
    socketio.emit("analysis_data", {
        "source": source,
        "samples": buf,
        "rate_hz": hz,
        "n": len(buf),
    })

@socketio.on("clear_buffers")
def on_clear_buffers():
    with _lock:
        phone_buffer.clear()
        imu_buffer.clear()
    socketio.emit("recording_ack", {"recording": False, "n_phone": 0, "n_imu": 0})

@socketio.on("compute_mag_calibration")
def on_compute_mag_calibration(data):
    points = np.array(data.get("points", []))
    if len(points) < 10:
        socketio.emit("mag_calibration_result", {"error": "Need at least 10 points"})
        return
        
    print(f"[Calibration] Solving hard/soft iron for {len(points)} points...")
    
    # We want to find vector V and symmetric matrix W such that || W * (P - V) || ~ B
    # Let's target B = 50 uT (Earth's average magnetic field)
    B_target = 50.0 
    
    def objective(params):
        # params: v0, v1, v2, w00, w11, w22, w01, w02, w12
        V = np.array([params[0], params[1], params[2]])
        W = np.array([
            [params[3], params[6], params[7]],
            [params[6], params[4], params[8]],
            [params[7], params[8], params[5]]
        ])
        
        # apply transformation
        P_shifted = points - V
        P_scaled = P_shifted @ W.T
        
        # magnitudes
        mags = np.linalg.norm(P_scaled, axis=1)
        
        # minimize difference from target B
        return np.sum((mags - B_target)**2)

    # Initial guess
    # V = mean of points
    v0 = np.mean(points, axis=0)
    # W = Identity
    p0 = [v0[0], v0[1], v0[2], 1.0, 1.0, 1.0, 0.0, 0.0, 0.0]
    
    # Minimize error
    res = minimize(objective, p0, method='Powell')
    params = res.x
    
    V = [float(params[0]), float(params[1]), float(params[2])]
    W = [
        [float(params[3]), float(params[6]), float(params[7])],
        [float(params[6]), float(params[4]), float(params[8])],
        [float(params[7]), float(params[8]), float(params[5])]
    ]
    
    print(f"[Calibration] Done. V={V}")
    socketio.emit("mag_calibration_result", {"hard_iron": V, "soft_iron": W})

@app.route("/analysis")
def analysis_page():
    return send_from_directory("web", "analysis.html")

@app.route("/calibration")
def calibration_page():
    return send_from_directory("web", "calibration.html")

@app.route("/fusion")
def fusion_page():
    return send_from_directory("web", "fusion.html")

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

    socketio.run(app, host="0.0.0.0", port=args.web_port, debug=False, allow_unsafe_werkzeug=True)


if __name__ == "__main__":
    main()
