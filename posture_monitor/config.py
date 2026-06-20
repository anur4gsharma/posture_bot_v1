"""
config.py — Centralized configuration for the posture monitoring system.
==========================================================================
All tuneable parameters live here. Change these values to adjust
webcam source, serial ports, detection sensitivity, and server settings.

Hardware layout:
  - Arduino UNO  → TFT 2.4" ILI9341 shield (emoji faces)
  - Arduino Nano → 16x2 I2C LCD + active buzzer (text messages + alert)
"""

# ======================== WEBCAM ========================
# USB Webcam index (0 = default camera, 1+ for additional cameras)
WEBCAM_INDEX = 0

# ======================== SERIAL (Dual Arduino) ========================
# Arduino UNO — TFT 2.4" display
# Find your port in Device Manager → Ports (COM & LPT)
SERIAL_PORT_UNO = "COM3"
SERIAL_BAUD_UNO = 9600

# Arduino Nano — LCD 16x2 + Buzzer
SERIAL_PORT_NANO = "COM7"
SERIAL_BAUD_NANO = 9600

# Set False to run without serial (display-only mode)
ENABLE_SERIAL_UNO = True
ENABLE_SERIAL_NANO = True

# Minimum interval between serial commands (seconds) to avoid flooding
SERIAL_INTERVAL = 0.3

# ======================== CALIBRATION ========================
# How many seconds the user must sit with good posture during calibration
CALIBRATION_SECONDS = 5

# ======================== POSTURE DETECTION ========================
# --- Sliding Window Bad-Posture Rule ---
# The buzzer only activates when posture has been bad for at least
# BAD_POSTURE_THRESHOLD seconds within the last BAD_POSTURE_WINDOW seconds.
BAD_POSTURE_WINDOW = 30      # Rolling window size in seconds
BAD_POSTURE_THRESHOLD = 25   # Seconds of bad posture required to trigger buzzer

# Threshold multipliers (relative to calibrated baseline)
# Higher = more tolerant.  Lower = stricter.
# FORWARD_MULTIPLIER removed — forward head check is redundant with SLOUCHING
SLOPE_OFFSET = 0.15             # shoulder slope absolute offset
TILT_OFFSET = 15.0              # head tilt degrees offset
NOSE_SHOULDER_MULTIPLIER = 0.65 # nose-to-shoulder ratio (lower = stricter)
SHOULDER_DROP_TOLERANCE = 15    # max pixels a shoulder can drop below baseline

# Default baseline if no person detected during calibration
DEFAULT_BASELINE = {
    'slope': 0.08,
    'tilt': 5.0,
    'nose_shoulder': 0.7,
    'shoulder_y_left': 200,
    'shoulder_y_right': 200,
}

# ======================== WEBSOCKET SERVER ========================
# The Android app connects to this address
WS_HOST = "0.0.0.0"   # Bind to all interfaces
WS_PORT = 8765         # Default WebSocket port

# How often to broadcast posture updates to the app (seconds)
WS_BROADCAST_INTERVAL = 0.5
