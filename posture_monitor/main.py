"""
main.py — Entry point for the Posture Monitoring System.
=========================================================
Orchestrates all components:
  1. WebSocket server (background thread) — pushes data to Android app
  2. Dual serial controller — sends commands to Arduino UNO (TFT) + Nano (LCD+Buzzer)
  3. Pose detector + metrics — MediaPipe landmark extraction
  4. Calibrator — collects baseline posture data
  5. Posture analyzer — compares frames against baseline (with 25/30s sliding window)
  6. OpenCV window — displays annotated video on laptop

Hardware layout:
  - Arduino UNO  → TFT 2.4" ILI9341 (emoji faces: happy/scared/sleeping)
  - Arduino Nano → 16x2 I2C LCD + active buzzer (text + alert beeps)
  - Buzzer only activates when bad posture ≥25s in last 30s

Run with:
    python -m posture_monitor.main

Press ESC to quit.
"""

import cv2
import sys
import time

from posture_monitor import config
from posture_monitor.engine.pose_detector import PoseDetector
from posture_monitor.engine.metrics import compute_metrics
from posture_monitor.engine.posture_analyzer import PostureAnalyzer
from posture_monitor.calibration.calibrator import Calibrator
from posture_monitor.hardware.serial_controller import DualSerialController
from posture_monitor.server.ws_server import PostureWebSocketServer


# ======================== STATE MACHINE ========================

class AppState:
    """Simple state tracker for the application lifecycle."""
    WAITING = "WAITING"           # Waiting for calibration command
    CALIBRATING = "CALIBRATING"   # Collecting baseline samples
    MONITORING = "MONITORING"     # Active posture monitoring
    STOPPED = "STOPPED"           # Shutting down


# ======================== DRAWING HELPERS ========================

def draw_calibration_overlay(frame, remaining, w):
    """Draw the calibration banner on the OpenCV frame."""
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 90), (200, 120, 0), -1)
    cv2.addWeighted(overlay, 0.6, frame, 0.4, 0, frame)
    cv2.putText(frame, "CALIBRATING - Sit with good posture",
                (20, 35), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
    cv2.putText(frame, f"Time left: {remaining:.1f}s",
                (20, 70), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)


def draw_monitoring_overlay(frame, result, w, h):
    """Draw posture status banner, issues, metrics, and alert bar."""
    # Status banner
    if result.state == "GOOD":
        status_text = "GOOD POSTURE"
        color = (0, 200, 0)
    elif result.state in ("BAD", "ALERT"):
        status_text = "BAD POSTURE"
        color = (0, 0, 255)
    else:
        status_text = "NO PERSON DETECTED"
        color = (128, 128, 128)

    banner_h = 80
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, banner_h), color, -1)
    cv2.addWeighted(overlay, 0.55, frame, 0.45, 0, frame)
    cv2.putText(frame, status_text, (20, 35),
                cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 3)

    # Issue details
    if result.issues:
        detail = " | ".join(result.issues)
        cv2.putText(frame, detail, (20, 65),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2)

    # Sliding window info bar (shows bad seconds / threshold)
    window_text = f"Bad: {result.bad_seconds_in_window:.0f}s / {config.BAD_POSTURE_THRESHOLD}s"
    buzz_label = "BUZZER: ON" if result.buzz_active else "BUZZER: OFF"
    buzz_color = (0, 0, 255) if result.buzz_active else (0, 200, 0)

    cv2.putText(frame, window_text, (20, banner_h + 20),
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 200, 200), 1)
    cv2.putText(frame, buzz_label, (w - 150, banner_h + 20),
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, buzz_color, 2)

    # Persistent alert bar when buzzer is active
    if result.buzz_active:
        bar_y = h - 50
        cv2.rectangle(frame, (0, bar_y), (w, h), (0, 0, 180), -1)
        cv2.putText(frame, "!! FIX YOUR POSTURE !!",
                    (int(w / 2) - 180, bar_y + 35),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.9, (255, 255, 255), 2)

    # Metrics overlay (bottom-left)
    if result.metrics:
        y_off = h - 140
        lines = [
            f"Forward: {result.metrics['forward']:.2f}",
            f"Slope:   {result.metrics['slope']:.2f}",
            f"Tilt:    {result.metrics['tilt']:.1f}",
            f"NoseSh:  {result.metrics['nose_shoulder']:.2f}",
            f"Torso:   {result.metrics['torso']:.2f}",
        ]
        for i, line in enumerate(lines):
            cv2.putText(frame, line, (10, y_off + i * 20),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.45, (200, 200, 200), 1)


def draw_no_person(frame, w):
    """Draw 'no person detected' banner."""
    banner_h = 80
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, banner_h), (128, 128, 128), -1)
    cv2.addWeighted(overlay, 0.55, frame, 0.45, 0, frame)
    cv2.putText(frame, "NO PERSON DETECTED", (20, 35),
                cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 3)


def draw_serial_indicator(frame, dual_serial, w):
    """Draw dual serial connection status in top-right corner."""
    # UNO status
    uno_label = "UNO: ON" if dual_serial.uno_connected else "UNO: OFF"
    uno_color = (0, 255, 0) if dual_serial.uno_connected else (0, 0, 255)
    cv2.putText(frame, uno_label, (w - 130, 20),
                cv2.FONT_HERSHEY_SIMPLEX, 0.45, uno_color, 2)

    # Nano status
    nano_label = "NANO: ON" if dual_serial.nano_connected else "NANO: OFF"
    nano_color = (0, 255, 0) if dual_serial.nano_connected else (0, 0, 255)
    cv2.putText(frame, nano_label, (w - 130, 40),
                cv2.FONT_HERSHEY_SIMPLEX, 0.45, nano_color, 2)


def draw_ws_indicator(frame, client_count, w):
    """Draw WebSocket connection count below serial indicator."""
    label = f"APP: {client_count} connected" if client_count > 0 else "APP: waiting..."
    color = (0, 255, 0) if client_count > 0 else (200, 200, 0)
    cv2.putText(frame, label, (w - 220, 60),
                cv2.FONT_HERSHEY_SIMPLEX, 0.45, color, 2)


# ======================== MAIN ========================

def main():
    print("=" * 60)
    print("  POSTURE MONITORING SYSTEM")
    print("  Webcam + MediaPipe + Android App")
    print("  Arduino UNO (TFT) + Arduino Nano (LCD+Buzzer)")
    print("=" * 60)

    # --- Initialize components ---
    detector = PoseDetector()
    calibrator = Calibrator()
    serial_ctrl = DualSerialController()
    ws_server = PostureWebSocketServer()
    analyzer = None  # Created after calibration

    # --- Connect serial (both Arduinos) ---
    serial_ctrl.connect()

    # --- Start WebSocket server ---
    ws_server.start()

    # --- Open webcam ---
    cap = cv2.VideoCapture(config.WEBCAM_INDEX)
    if not cap.isOpened():
        print(f"[ERROR] Could not open webcam (index {config.WEBCAM_INDEX})")
        print("        Make sure your webcam is plugged in.")
        sys.exit(1)
    print(f"[OK] Webcam opened (index {config.WEBCAM_INDEX})")

    # --- Send initial sleep state to Arduinos ---
    serial_ctrl.send_raw("SLEEP")

    # --- Application state ---
    state = AppState.WAITING
    print("\n>>> Waiting for calibration...")
    print("    Press 'C' on the OpenCV window OR send START_CALIBRATION from the app.")

    # ======================== MAIN LOOP ========================
    try:
        while state != AppState.STOPPED:
            # --- Check for commands from the Android app ---
            cmd = ws_server.get_pending_command()
            if cmd == "START_CALIBRATION" and state == AppState.WAITING:
                state = AppState.CALIBRATING
                calibrator.start()
                serial_ctrl.send_raw("CAL")
                print("\n>>> Calibration started! Sit with good posture...")

            elif cmd == "START_MONITORING" and state == AppState.WAITING:
                # App says start monitoring (re-use existing calibration)
                if analyzer is not None:
                    state = AppState.MONITORING
                    serial_ctrl.send_raw("READY")
                    print("\n>>> Resuming monitoring from app command...")
                    ws_server.broadcast({
                        "type": "posture_update",
                        "state": "GOOD",
                        "metrics": None,
                        "issues": [],
                        "calibration_progress": 1.0,
                        "buzz_active": False,
                        "bad_seconds": 0.0,
                        "timestamp": int(time.time() * 1000),
                    })

            elif cmd == "STOP_MONITORING" and state == AppState.MONITORING:
                state = AppState.WAITING
                serial_ctrl.send_raw("SLEEP")
                print("\n>>> Monitoring stopped from app.")
                ws_server.broadcast({
                    "type": "posture_update",
                    "state": "STOPPED",
                    "metrics": None,
                    "issues": [],
                    "calibration_progress": 0.0,
                    "buzz_active": False,
                    "bad_seconds": 0.0,
                    "timestamp": int(time.time() * 1000),
                })
                print(">>> Press C to recalibrate, or use the app to restart.")

            elif cmd == "STOP":
                state = AppState.STOPPED
                continue

            # --- Read frame ---
            ret, frame = cap.read()
            if not ret:
                time.sleep(0.05)
                continue

            frame = cv2.flip(frame, 1)
            h, w, _ = frame.shape

            # --- Detect pose ---
            pts, results = detector.detect(frame)

            # ---- STATE: WAITING ----
            if state == AppState.WAITING:
                overlay = frame.copy()
                cv2.rectangle(overlay, (0, 0), (w, 90), (80, 80, 80), -1)
                cv2.addWeighted(overlay, 0.6, frame, 0.4, 0, frame)
                cv2.putText(frame, "POSTURE MONITOR - Ready",
                            (20, 35), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
                cv2.putText(frame, "Press C to calibrate or use the app",
                            (20, 70), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (200, 200, 200), 2)

                if pts:
                    detector.draw_landmarks(frame, results)

                # Broadcast waiting state to app
                ws_server.broadcast({
                    "type": "posture_update",
                    "state": "IDLE",
                    "metrics": None,
                    "issues": [],
                    "calibration_progress": 0.0,
                    "buzz_active": False,
                    "bad_seconds": 0.0,
                    "timestamp": int(time.time() * 1000),
                })

            # ---- STATE: CALIBRATING ----
            elif state == AppState.CALIBRATING:
                remaining = calibrator.get_remaining()
                progress = calibrator.get_progress()

                draw_calibration_overlay(frame, remaining, w)

                if pts:
                    metrics = compute_metrics(pts)
                    calibrator.add_sample(metrics)
                    detector.draw_landmarks(frame, results)

                # Broadcast calibration progress to app
                ws_server.broadcast({
                    "type": "posture_update",
                    "state": "CALIBRATING",
                    "metrics": None,
                    "issues": [],
                    "calibration_progress": progress,
                    "buzz_active": False,
                    "bad_seconds": 0.0,
                    "timestamp": int(time.time() * 1000),
                })

                # Check if calibration is complete
                if calibrator.is_complete():
                    baseline = calibrator.compute_baseline()
                    analyzer = PostureAnalyzer(baseline)
                    serial_ctrl.send_raw("READY")
                    state = AppState.MONITORING
                    print("\n>>> Monitoring posture... Press ESC to quit.")

                    ws_server.broadcast({
                        "type": "posture_update",
                        "state": "GOOD",
                        "metrics": None,
                        "issues": [],
                        "calibration_progress": 1.0,
                        "buzz_active": False,
                        "bad_seconds": 0.0,
                        "timestamp": int(time.time() * 1000),
                    })

            # ---- STATE: MONITORING ----
            elif state == AppState.MONITORING:
                if pts:
                    metrics = compute_metrics(pts)
                    result = analyzer.analyze(metrics)

                    # Draw everything on the OpenCV frame
                    detector.draw_landmarks(frame, results)
                    cv2.line(frame, pts['ls'], pts['rs'], (255, 255, 255), 2)
                    cv2.circle(frame, pts['nose'], 5, (0, 0, 255), -1)
                    draw_monitoring_overlay(frame, result, w, h)

                    # Send to both Arduinos via serial
                    if result.state == "ALERT":
                        serial_ctrl.send_posture("BAD", buzz_active=True)
                    elif result.state == "BAD":
                        serial_ctrl.send_posture("BAD", buzz_active=False)
                    else:
                        serial_ctrl.send_posture("GOOD", buzz_active=False)

                    # Broadcast to Android app
                    ws_server.broadcast({
                        "type": "posture_update",
                        "state": result.state,
                        "metrics": result.metrics,
                        "issues": result.issues,
                        "calibration_progress": 1.0,
                        "buzz_active": result.buzz_active,
                        "bad_seconds": result.bad_seconds_in_window,
                        "timestamp": int(time.time() * 1000),
                    })
                else:
                    draw_no_person(frame, w)
                    serial_ctrl.send_raw("SLEEP")

                    ws_server.broadcast({
                        "type": "posture_update",
                        "state": "NO_PERSON",
                        "metrics": None,
                        "issues": [],
                        "calibration_progress": 1.0,
                        "buzz_active": False,
                        "bad_seconds": 0.0,
                        "timestamp": int(time.time() * 1000),
                    })

            # --- Draw connection indicators ---
            draw_serial_indicator(frame, serial_ctrl, w)
            draw_ws_indicator(frame, ws_server.client_count, w)

            # --- Show frame ---
            cv2.imshow("Posture Monitor", frame)

            # --- Handle keyboard input ---
            key = cv2.waitKey(1) & 0xFF
            if key == 27:  # ESC — full shutdown
                ws_server.broadcast({
                    "type": "posture_update",
                    "state": "STOPPED",
                    "metrics": None,
                    "issues": [],
                    "calibration_progress": 0.0,
                    "buzz_active": False,
                    "bad_seconds": 0.0,
                    "timestamp": int(time.time() * 1000),
                })
                state = AppState.STOPPED
            elif key == ord('s') or key == ord('S'):
                if state == AppState.MONITORING:
                    state = AppState.WAITING
                    serial_ctrl.send_raw("SLEEP")
                    ws_server.broadcast({
                        "type": "posture_update",
                        "state": "STOPPED",
                        "metrics": None,
                        "issues": [],
                        "calibration_progress": 0.0,
                        "buzz_active": False,
                        "bad_seconds": 0.0,
                        "timestamp": int(time.time() * 1000),
                    })
                    print("\n>>> Monitoring stopped. Press C to recalibrate or use the app.")
            elif key == ord('c') or key == ord('C'):
                if state == AppState.WAITING:
                    state = AppState.CALIBRATING
                    calibrator.start()
                    serial_ctrl.send_raw("CAL")
                    print("\n>>> Calibration started! Sit with good posture...")

    except KeyboardInterrupt:
        print("\n>>> Interrupted by user.")

    # ======================== CLEANUP ========================
    print("\n>>> Shutting down...")
    serial_ctrl.send_raw("OFF")
    serial_ctrl.disconnect()
    ws_server.stop()
    detector.release()
    cap.release()
    cv2.destroyAllWindows()
    print("[OK] Done.")


if __name__ == "__main__":
    main()
