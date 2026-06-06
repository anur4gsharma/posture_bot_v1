"""
serial_controller.py — Serial communication with ESP32 output controller.
==========================================================================
Sends posture status commands over USB serial to the ESP32 which drives
a buzzer and TFT display. Rate-limited to avoid flooding the serial bus.

Serial Protocol (must match esp32_output_controller.ino):
    "GOOD\n"            → Good posture
    "BAD:issue1,issue2\n" → Bad posture with specific issues
    "ALERT\n"           → Persistent bad posture alarm
    "NONE\n"            → No person detected
    "CAL\n"             → Calibrating
    "READY\n"           → Calibration complete, monitoring started
    "OFF\n"             → Shutdown

Usage:
    serial_ctrl = SerialController("COM3", 115200)
    serial_ctrl.connect()
    serial_ctrl.send("GOOD")
    serial_ctrl.disconnect()
"""

import time
from posture_monitor import config


class SerialController:
    """Rate-limited serial communication with the ESP32 output controller."""

    def __init__(self, port=None, baud=None, enabled=None):
        """
        Args:
            port:    Serial port (default from config).
            baud:    Baud rate (default from config).
            enabled: Whether serial is enabled (default from config).
        """
        self.port = port or config.SERIAL_PORT
        self.baud = baud or config.SERIAL_BAUD
        self.enabled = enabled if enabled is not None else config.ENABLE_SERIAL

        self._ser = None
        self._last_time = 0
        self._last_cmd = ""

    def connect(self):
        """
        Attempt to open the serial port.
        Returns True if connected, False if failed (runs in display-only mode).
        """
        if not self.enabled:
            print("[INFO] Serial disabled in config. Running display-only.")
            return False

        try:
            import serial
            self._ser = serial.Serial(self.port, self.baud, timeout=1)
            time.sleep(2)  # Wait for ESP32 to reset after serial connection
            print(f"[OK] Serial connected on {self.port} @ {self.baud}")
            return True
        except ImportError:
            print("[WARN] pyserial not installed. Run: pip install pyserial")
            print("       Continuing in display-only mode.")
            self._ser = None
            return False
        except Exception as e:
            print(f"[WARN] Could not open {self.port}: {e}")
            print("       Continuing in display-only mode.")
            self._ser = None
            return False

    def send(self, command):
        """
        Send a command string over serial, rate-limited.
        Only sends if enough time has passed or the command changed.

        Args:
            command: String to send (e.g. "GOOD", "BAD:FORWARD HEAD,SLOUCHING")
        """
        if not self._ser:
            return

        now = time.time()
        # Only send if command changed or interval elapsed
        if command != self._last_cmd or now - self._last_time >= config.SERIAL_INTERVAL:
            try:
                self._ser.write((command + "\n").encode('utf-8'))
                self._last_time = now
                self._last_cmd = command
            except Exception as e:
                print(f"[WARN] Serial write error: {e}")

    @property
    def is_connected(self):
        """Check if serial port is open and usable."""
        return self._ser is not None and self._ser.is_open

    def disconnect(self):
        """Close the serial port gracefully."""
        if self._ser:
            try:
                self._ser.close()
                print("[OK] Serial port closed.")
            except Exception:
                pass
            self._ser = None
