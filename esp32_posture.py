"""
USB Webcam Posture Detection System
====================================
Reads frames from a USB webcam, runs MediaPipe pose detection,
displays posture status in an OpenCV window, and sends serial
commands to an ESP32 output controller (buzzer + LCD).

The output ESP32 is powered via the SAME USB cable used for serial.

Usage:
    python esp32_posture.py

Press ESC to quit.
"""

import cv2
import mediapipe as mp
import math
import time
import sys

# ======================== CONFIGURATION ========================
# USB Webcam index (0 = default camera, change if you have multiple cameras)
WEBCAM_INDEX = 1

# Serial port for the output ESP32 (buzzer + LCD controller)
# The ESP32 is powered over this same USB connection.
SERIAL_PORT = "COM3"       # Windows: "COM3", "COM4", etc.
SERIAL_BAUD = 115200

# Enable/disable serial output (set False to run display-only)
ENABLE_SERIAL = True

# Calibration duration in seconds
CALIBRATION_SECONDS = 5

# Number of consecutive bad-posture frames before persistent alert
ALERT_THRESHOLD = 30

# Serial command send interval (seconds) to avoid flooding
SERIAL_INTERVAL = 0.3
# ===============================================================

# ---------- Serial Setup (graceful fallback) ----------
ser = None
if ENABLE_SERIAL:
    try:
        import serial
        ser = serial.Serial(SERIAL_PORT, SERIAL_BAUD, timeout=1)
        time.sleep(2)  # Wait for ESP32 to reset after serial connection
        print(f"[OK] Serial connected on {SERIAL_PORT} @ {SERIAL_BAUD}")
        print(f"     (ESP32 is powered via this USB cable)")
    except ImportError:
        print("[WARN] pyserial not installed. Run: pip install pyserial")
        print("       Continuing in display-only mode.")
        ser = None
    except Exception as e:
        print(f"[WARN] Could not open {SERIAL_PORT}: {e}")
        print("       Continuing in display-only mode.")
        ser = None

last_serial_time = 0
last_serial_cmd = ""


def send_serial(command):
    """Send a command string over serial, rate-limited."""
    global last_serial_time, last_serial_cmd
    now = time.time()
    # Only send if enough time has passed or command changed
    if ser and (command != last_serial_cmd or now - last_serial_time >= SERIAL_INTERVAL):
        try:
            ser.write((command + "\n").encode('utf-8'))
            last_serial_time = now
            last_serial_cmd = command
        except Exception as e:
            print(f"[WARN] Serial write error: {e}")


# ---------- MediaPipe Setup ----------
mp_pose = mp.solutions.pose
mp_draw = mp.solutions.drawing_utils

pose = mp_pose.Pose(
    min_detection_confidence=0.5,
    min_tracking_confidence=0.5
)

# ---------- Camera Setup ----------
cap = cv2.VideoCapture(WEBCAM_INDEX)
print(f"[INFO] Connecting to USB webcam (index {WEBCAM_INDEX})")

if not cap.isOpened():
    print("[ERROR] Could not open USB webcam!")
    print("        Make sure your webcam is plugged in and not in use by another app.")
    print("        If you have multiple cameras, try changing WEBCAM_INDEX (0, 1, 2...).")
    sys.exit(1)

print("[OK] Video source opened successfully.")

bad_frames = 0


# ======================== HELPER FUNCTIONS ========================

def distance(p1, p2):
    """Euclidean distance between two 2D points."""
    return math.sqrt((p1[0] - p2[0]) ** 2 + (p1[1] - p2[1]) ** 2)


def shoulder_slope(ls, rs):
    """Slope between left and right shoulders."""
    return abs(rs[1] - ls[1]) / (abs(rs[0] - ls[0]) + 1e-5)


def head_tilt(le, re):
    """Head tilt angle from ear positions (degrees)."""
    angle = math.degrees(math.atan2(re[1] - le[1], re[0] - le[0]))
    angle = abs(angle)
    if angle > 90:
        angle = 180 - angle
    return angle


def nose_to_shoulder_dist(nose, ls, rs):
    """Normalized perpendicular distance from nose to shoulder line."""
    dx = rs[0] - ls[0]
    dy = rs[1] - ls[1]
    line_len = math.sqrt(dx * dx + dy * dy) + 1e-5
    dist = abs(dx * (ls[1] - nose[1]) - dy * (ls[0] - nose[0])) / line_len
    return dist / line_len


def get_landmarks(frame):
    """Extract pose landmarks from a frame using MediaPipe."""
    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    results = pose.process(rgb)

    if not results.pose_landmarks:
        return None, results

    h, w, _ = frame.shape
    lm = results.pose_landmarks.landmark

    pts = {
        'nose': (int(lm[0].x * w), int(lm[0].y * h)),
        'ls':   (int(lm[11].x * w), int(lm[11].y * h)),
        'rs':   (int(lm[12].x * w), int(lm[12].y * h)),
        'le':   (int(lm[7].x * w), int(lm[7].y * h)),
        're':   (int(lm[8].x * w), int(lm[8].y * h)),
        'lh':   (int(lm[23].x * w), int(lm[23].y * h)),
        'rh':   (int(lm[24].x * w), int(lm[24].y * h)),
    }

    return pts, results


def compute_metrics(pts):
    """Compute all five posture metrics from landmarks."""
    nose, ls, rs = pts['nose'], pts['ls'], pts['rs']
    le, re = pts['le'], pts['re']
    lh, rh = pts['lh'], pts['rh']

    sw = distance(ls, rs)

    sc = (int((ls[0] + rs[0]) / 2), int((ls[1] + rs[1]) / 2))
    hc = (int((lh[0] + rh[0]) / 2), int((lh[1] + rh[1]) / 2))

    torso_len = distance(sc, hc) / sw

    return {
        'forward':       distance(nose, sc) / sw,
        'slope':         shoulder_slope(ls, rs),
        'tilt':          head_tilt(le, re),
        'nose_shoulder': nose_to_shoulder_dist(nose, ls, rs),
        'torso':         torso_len,
    }


# ======================== CALIBRATION ========================

print(">>> Sit with GOOD POSTURE. Calibrating for", CALIBRATION_SECONDS, "seconds...")
send_serial("CAL")

cal_samples = {'forward': [], 'slope': [], 'tilt': [], 'nose_shoulder': [], 'torso': []}
cal_start = time.time()

while time.time() - cal_start < CALIBRATION_SECONDS:

    ret, frame = cap.read()

    if not ret:
        # ESP32-CAM streams can drop frames; retry briefly
        time.sleep(0.05)
        continue

    frame = cv2.flip(frame, 1)
    h, w, _ = frame.shape

    pts, results = get_landmarks(frame)

    remaining = max(0, CALIBRATION_SECONDS - (time.time() - cal_start))

    banner_color = (200, 120, 0)

    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 90), banner_color, -1)
    cv2.addWeighted(overlay, 0.6, frame, 0.4, 0, frame)

    cv2.putText(frame, "CALIBRATING - Sit with good posture",
                (20, 35), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)

    cv2.putText(frame, f"Time left: {remaining:.1f}s",
                (20, 70), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)

    if pts:
        metrics = compute_metrics(pts)

        for k in cal_samples:
            cal_samples[k].append(metrics[k])

        mp_draw.draw_landmarks(frame, results.pose_landmarks, mp_pose.POSE_CONNECTIONS)

    cv2.imshow("Posture Detection - USB Webcam", frame)

    if cv2.waitKey(1) & 0xFF == 27:
        cap.release()
        cv2.destroyAllWindows()
        if ser:
            ser.close()
        sys.exit(0)

# Compute baseline from calibration
if all(len(v) > 0 for v in cal_samples.values()):
    baseline = {k: sum(v) / len(v) for k, v in cal_samples.items()}
    print(">>> Calibration done!", {k: round(v, 3) for k, v in baseline.items()})
else:
    baseline = {'forward': 0.75, 'slope': 0.08, 'tilt': 5.0, 'nose_shoulder': 0.7, 'torso': 1.5}
    print(">>> No person detected during calibration — using defaults")

# Thresholds (relative to your calibrated good posture)
FORWARD_THRESH       = baseline['forward'] * 1.35
SLOPE_THRESH         = baseline['slope'] + 0.15
TILT_THRESH          = baseline['tilt'] + 15
NOSE_SHOULDER_THRESH = baseline['nose_shoulder'] * 0.65
TORSO_THRESH         = baseline['torso'] * 0.90


# ======================== MAIN DETECTION LOOP ========================

print(">>> Monitoring posture... Press ESC to quit.")
send_serial("READY")

while True:

    ret, frame = cap.read()

    if not ret:
        # ESP32-CAM stream may stall; retry
        time.sleep(0.05)
        continue

    frame = cv2.flip(frame, 1)
    h, w, _ = frame.shape

    pts, results = get_landmarks(frame)

    posture_status = "GOOD POSTURE"
    status_color = (0, 200, 0)
    detail_text = ""
    bad_posture = False

    if pts:

        metrics = compute_metrics(pts)

        nose, ls, rs = pts['nose'], pts['ls'], pts['rs']

        issues = []

        if metrics['forward'] > FORWARD_THRESH:
            issues.append("FORWARD HEAD")

        if metrics['slope'] > SLOPE_THRESH:
            issues.append("UNEVEN SHOULDERS")

        if metrics['tilt'] > TILT_THRESH:
            issues.append("HEAD TILT")

        if metrics['nose_shoulder'] < NOSE_SHOULDER_THRESH:
            issues.append("SLOUCHING")

        if metrics['torso'] < TORSO_THRESH:
            issues.append("SPINE NOT STRAIGHT")

        bad_posture = len(issues) > 0

        if bad_posture:
            bad_frames += 1
            posture_status = "BAD POSTURE"
            status_color = (0, 0, 255)
            detail_text = " | ".join(issues)
        else:
            bad_frames = 0

        # --- Send serial command ---
        if bad_posture:
            if bad_frames > ALERT_THRESHOLD:
                send_serial("ALERT")
            else:
                send_serial("BAD:" + ",".join(issues))
        else:
            send_serial("GOOD")

        # --- Draw landmarks ---
        mp_draw.draw_landmarks(frame, results.pose_landmarks, mp_pose.POSE_CONNECTIONS)
        cv2.line(frame, ls, rs, (255, 255, 255), 2)
        cv2.circle(frame, nose, 5, (0, 0, 255), -1)

    else:
        posture_status = "NO PERSON DETECTED"
        status_color = (128, 128, 128)
        send_serial("NONE")

    # --- Draw status banner ---
    banner_h = 80

    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, banner_h), status_color, -1)
    cv2.addWeighted(overlay, 0.55, frame, 0.45, 0, frame)

    cv2.putText(frame, posture_status, (20, 35),
                cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 3)

    if detail_text:
        cv2.putText(frame, detail_text, (20, 65),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2)

    # --- Persistent alert bar ---
    if bad_frames > ALERT_THRESHOLD:
        bar_y = h - 50
        cv2.rectangle(frame, (0, bar_y), (w, h), (0, 0, 180), -1)
        cv2.putText(frame, "!! FIX YOUR POSTURE !!",
                    (int(w / 2) - 180, bar_y + 35),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.9, (255, 255, 255), 2)

    # --- Show metrics overlay (bottom-left) ---
    if pts:
        y_off = h - 120
        metric_lines = [
            f"Forward: {metrics['forward']:.2f} / {FORWARD_THRESH:.2f}",
            f"Slope:   {metrics['slope']:.2f} / {SLOPE_THRESH:.2f}",
            f"Tilt:    {metrics['tilt']:.1f} / {TILT_THRESH:.1f}",
            f"NoseSh:  {metrics['nose_shoulder']:.2f} / {NOSE_SHOULDER_THRESH:.2f}",
            f"Torso:   {metrics['torso']:.2f} / {TORSO_THRESH:.2f}",
        ]
        for i, line in enumerate(metric_lines):
            cv2.putText(frame, line, (10, y_off + i * 20),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.45, (200, 200, 200), 1)

    # --- Serial status indicator (top-right) ---
    serial_label = "SERIAL: ON" if ser else "SERIAL: OFF"
    serial_color = (0, 255, 0) if ser else (0, 0, 255)
    cv2.putText(frame, serial_label, (w - 160, 25),
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, serial_color, 2)

    cv2.imshow("Posture Detection - USB Webcam", frame)

    if cv2.waitKey(1) & 0xFF == 27:
        break


# ======================== CLEANUP ========================
print(">>> Shutting down...")
send_serial("OFF")

cap.release()
cv2.destroyAllWindows()

if ser:
    ser.close()
    print("[OK] Serial port closed.")

print("[OK] Done.")
