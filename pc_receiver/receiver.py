#!/usr/bin/env python3
"""
IMU Sensor — UDP Receiver for PC
=================================
Listens for UDP packets from the Android IMU Sensor app and displays
sensor data in real time. Supports both CSV and JSON formats.

Usage:
    python receiver.py                     # Listen on default port 8765
    python receiver.py --port 9000         # Custom port
    python receiver.py --plot              # Enable live matplotlib plot
    python receiver.py --log data.csv      # Save to file

Make sure your phone and PC are on the same Wi-Fi network.
Set the PC's local IP address in the app's Stream settings.
"""

import argparse
import json
import socket
import sys
import time
from datetime import datetime


def parse_csv(data: str) -> dict:
    """Parse a CSV-formatted sensor packet."""
    parts = data.strip().split(",")
    if len(parts) < 17:
        return {}
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


def parse_json(data: str) -> dict:
    """Parse a JSON-formatted sensor packet."""
    return json.loads(data.strip())


def format_table(d: dict) -> str:
    """Pretty-print a parsed sensor packet."""
    if not d:
        return "[empty packet]"

    a = d.get("accel", [0, 0, 0])
    g = d.get("gyro",  [0, 0, 0])
    m = d.get("mag",   [0, 0, 0])
    e = d.get("euler", {"yaw": 0, "pitch": 0, "roll": 0})

    lines = [
        f"  Accel  │ X:{a[0]:+9.4f}  Y:{a[1]:+9.4f}  Z:{a[2]:+9.4f}  m/s²",
        f"  Gyro   │ X:{g[0]:+9.4f}  Y:{g[1]:+9.4f}  Z:{g[2]:+9.4f}  rad/s",
        f"  Mag    │ X:{m[0]:+9.4f}  Y:{m[1]:+9.4f}  Z:{m[2]:+9.4f}  μT",
        f"  Euler  │ Yaw:{e['yaw']:+8.2f}°  Pitch:{e['pitch']:+8.2f}°  Roll:{e['roll']:+8.2f}°",
    ]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="IMU Sensor UDP Receiver")
    parser.add_argument("--port", type=int, default=8765, help="UDP port to listen on")
    parser.add_argument("--log", type=str, default=None, help="Path to save received data as CSV")
    parser.add_argument("--plot", action="store_true", help="Enable live matplotlib plot (requires matplotlib)")
    args = parser.parse_args()

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("0.0.0.0", args.port))
    sock.settimeout(1.0)

    print(f"╔══════════════════════════════════════════════════╗")
    print(f"║        IMU Sensor — UDP Receiver                ║")
    print(f"║        Listening on port {args.port:<5}                  ║")
    print(f"╚══════════════════════════════════════════════════╝")
    print()

    log_file = None
    if args.log:
        log_file = open(args.log, "w")
        log_file.write("timestamp,ax,ay,az,gx,gy,gz,mx,my,mz,yaw,pitch,roll\n")
        print(f"  Logging to: {args.log}")

    # Optional live plot
    plotter = None
    if args.plot:
        try:
            import matplotlib.pyplot as plt
            import matplotlib.animation as animation

            plt.style.use("dark_background")
            fig, axes = plt.subplots(3, 1, figsize=(10, 7), sharex=True)
            fig.suptitle("IMU Sensor — Live Data", fontsize=14, color="#00D4FF")

            max_points = 200
            accel_data = {"x": [], "y": [], "z": []}
            gyro_data  = {"x": [], "y": [], "z": []}
            euler_data = {"yaw": [], "pitch": [], "roll": []}

            titles   = ["Accelerometer (m/s²)", "Gyroscope (rad/s)", "Euler Angles (°)"]
            datasets = [accel_data, gyro_data, euler_data]
            keys_per = [["x", "y", "z"], ["x", "y", "z"], ["yaw", "pitch", "roll"]]
            colors   = [["#FF5252", "#69F0AE", "#448AFF"]] * 2 + [["#00E5FF", "#AA00FF", "#FF6D00"]]

            for ax, title in zip(axes, titles):
                ax.set_title(title, fontsize=10, color="white")
                ax.set_facecolor("#0A0A12")
                ax.tick_params(colors="gray")

            plotter = {
                "fig": fig, "axes": axes, "datasets": datasets,
                "keys": keys_per, "colors": colors, "max": max_points,
            }

            plt.ion()
            plt.tight_layout()
            plt.show(block=False)
        except ImportError:
            print("  ⚠  matplotlib not installed — plotting disabled")
            print("     Install with: pip install matplotlib")
            args.plot = False

    packet_count = 0
    start_time = time.time()

    try:
        while True:
            try:
                data, addr = sock.recvfrom(4096)
                text = data.decode("utf-8", errors="ignore")
            except socket.timeout:
                continue

            # Auto-detect format
            text = text.strip()
            if text.startswith("{"):
                parsed = parse_json(text)
            else:
                parsed = parse_csv(text)

            if not parsed:
                continue

            packet_count += 1
            elapsed = time.time() - start_time
            rate = packet_count / elapsed if elapsed > 0 else 0

            # Print to console
            sys.stdout.write(f"\033[2J\033[H")  # Clear screen
            print(f"╔══════════════════════════════════════════════════╗")
            print(f"║  IMU Sensor — Live  │  {rate:.0f} pkt/s  │  #{packet_count:<8} ║")
            print(f"╠══════════════════════════════════════════════════╣")
            print(format_table(parsed))
            print(f"╚══════════════════════════════════════════════════╝")
            print(f"  Source: {addr[0]}:{addr[1]}")

            # Log to file
            if log_file:
                a = parsed.get("accel", [0, 0, 0])
                g = parsed.get("gyro",  [0, 0, 0])
                m = parsed.get("mag",   [0, 0, 0])
                e = parsed.get("euler", {"yaw": 0, "pitch": 0, "roll": 0})
                log_file.write(
                    f"{parsed.get('t', 0)},"
                    f"{a[0]},{a[1]},{a[2]},"
                    f"{g[0]},{g[1]},{g[2]},"
                    f"{m[0]},{m[1]},{m[2]},"
                    f"{e['yaw']},{e['pitch']},{e['roll']}\n"
                )
                log_file.flush()

            # Update plot
            if args.plot and plotter:
                a = parsed.get("accel", [0, 0, 0])
                g = parsed.get("gyro",  [0, 0, 0])
                e = parsed.get("euler", {"yaw": 0, "pitch": 0, "roll": 0})

                for key, val_ in zip(["x", "y", "z"], a):
                    plotter["datasets"][0][key].append(val_)
                    plotter["datasets"][0][key] = plotter["datasets"][0][key][-plotter["max"]:]
                for key, val_ in zip(["x", "y", "z"], g):
                    plotter["datasets"][1][key].append(val_)
                    plotter["datasets"][1][key] = plotter["datasets"][1][key][-plotter["max"]:]
                for key, val_ in zip(["yaw", "pitch", "roll"], [e["yaw"], e["pitch"], e["roll"]]):
                    plotter["datasets"][2][key].append(val_)
                    plotter["datasets"][2][key] = plotter["datasets"][2][key][-plotter["max"]:]

                # Redraw every 5th packet to reduce CPU usage
                if packet_count % 5 == 0:
                    for ax, dataset, keys, cols in zip(
                        plotter["axes"], plotter["datasets"], plotter["keys"], plotter["colors"]
                    ):
                        ax.cla()
                        for k, c in zip(keys, cols):
                            ax.plot(dataset[k], color=c, linewidth=1.2, label=k)
                        ax.legend(loc="upper left", fontsize=8)

                    plotter["fig"].canvas.draw_idle()
                    plotter["fig"].canvas.flush_events()

    except KeyboardInterrupt:
        print("\n\n  Stopped. Received", packet_count, "packets.")
    finally:
        sock.close()
        if log_file:
            log_file.close()


if __name__ == "__main__":
    main()
