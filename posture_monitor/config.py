"""
config.py — Centralized configuration for the posture monitoring system.
==========================================================================
All tuneable parameters live here. Change these values to adjust
webcam source, serial port, detection sensitivity, and server settings.
"""

# ======================== WEBCAM ========================
# USB Webcam index (0 = default camera, 1+ for additional cameras)
WEBCAM_INDEX = 0

# ======================== SERIAL (ESP32) ========================
# Serial port for the ESP32 output controller (buzzer + TFT)
# Windows: "COM3", "COM4", etc.  Linux/Mac: "/dev/ttyUSB0"
SERIAL_PORT = "COM3"
SERIAL_BAUD = 115200

# Set False to run without serial (display-only mode)
ENABLE_SERIAL = True

# Minimum interval between serial commands (seconds) to avoid flooding
SERIAL_INTERVAL = 0.3

# ======================== CALIBRATION ========================
# How many seconds the user must sit with good posture during calibration
CALIBRATION_SECONDS = 5

# ======================== POSTURE DETECTION ========================
# Number of consecutive bad-posture frames before triggering persistent ALERT
ALERT_THRESHOLD = 30

# Threshold multipliers (relative to calibrated baseline)
# Higher = more tolerant.  Lower = stricter.
FORWARD_MULTIPLIER = 1.35       # forward head distance ratio
SLOPE_OFFSET = 0.15             # shoulder slope absolute offset
TILT_OFFSET = 15.0              # head tilt degrees offset
NOSE_SHOULDER_MULTIPLIER = 0.65 # nose-to-shoulder ratio (lower = stricter)
TORSO_MULTIPLIER = 0.90         # torso length ratio (lower = stricter)

# Default baseline if no person detected during calibration
DEFAULT_BASELINE = {
    'forward': 0.75,
    'slope': 0.08,
    'tilt': 5.0,
    'nose_shoulder': 0.7,
    'torso': 1.5,
}

# ======================== WEBSOCKET SERVER ========================
# The Android app connects to this address
WS_HOST = "0.0.0.0"   # Bind to all interfaces
WS_PORT = 8765         # Default WebSocket port

# How often to broadcast posture updates to the app (seconds)
WS_BROADCAST_INTERVAL = 0.5
