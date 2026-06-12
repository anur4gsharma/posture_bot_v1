"""
serial_controller.py — Dual-Arduino serial communication.
==========================================================================
Manages two serial connections simultaneously:
  - Arduino UNO  (TFT 2.4" display — shows emoji faces)
  - Arduino Nano (LCD 16x2 + buzzer — shows text + alert beeps)

Both Arduinos receive posture state commands. The Nano additionally
receives BUZZ_ON / BUZZ_OFF commands based on the 25/30s rule.

Serial Protocol (9600 baud, same for both):
    "GOOD\n"             → Good posture
    "BAD\n"              → Bad posture (no buzzer yet)
    "ALERT\n"            → Bad posture + buzzer active (25/30s rule met)
    "SLEEP\n"            → App not connected / processing off
    "CAL\n"              → Calibrating
    "READY\n"            → Calibration complete
    "OFF\n"              → Shutdown

Nano-only commands:
    "BUZZ_ON\n"          → Activate buzzer
    "BUZZ_OFF\n"         → Deactivate buzzer

Usage:
    dual = DualSerialController()
    dual.connect()
    dual.send_posture("GOOD", buzz_active=False)
    dual.disconnect()
"""

import time
from posture_monitor import config


class SerialController:
    """Rate-limited serial communication with a single Arduino."""

    def __init__(self, port, baud, enabled=True, name="Arduino"):
        """
        Args:
            port:    Serial port (e.g. "COM3").
            baud:    Baud rate (e.g. 9600).
            enabled: Whether this connection is enabled.
            name:    Human-readable name for logging.
        """
        self.port = port
        self.baud = baud
        self.enabled = enabled
        self.name = name

        self._ser = None
        self._last_time = 0
        self._last_cmd = ""

    def connect(self):
        """
        Attempt to open the serial port.
        Returns True if connected, False if failed.
        """
        if not self.enabled:
            print(f"[INFO] {self.name} serial disabled. Skipping.")
            return False

        try:
            import serial
            self._ser = serial.Serial(self.port, self.baud, timeout=1)
            time.sleep(2)  # Wait for Arduino to reset after serial connection
            print(f"[OK] {self.name} connected on {self.port} @ {self.baud}")
            return True
        except ImportError:
            print("[WARN] pyserial not installed. Run: pip install pyserial")
            self._ser = None
            return False
        except Exception as e:
            print(f"[WARN] Could not open {self.port} for {self.name}: {e}")
            self._ser = None
            return False

    def send(self, command):
        """
        Send a command string over serial, rate-limited.
        Only sends if enough time has passed or the command changed.
        """
        if not self._ser:
            return

        now = time.time()
        if command != self._last_cmd or now - self._last_time >= config.SERIAL_INTERVAL:
            try:
                self._ser.write((command + "\n").encode('utf-8'))
                self._last_time = now
                self._last_cmd = command
            except Exception as e:
                print(f"[WARN] {self.name} serial write error: {e}")

    @property
    def is_connected(self):
        """Check if serial port is open and usable."""
        return self._ser is not None and self._ser.is_open

    def disconnect(self):
        """Close the serial port gracefully."""
        if self._ser:
            try:
                self._ser.close()
                print(f"[OK] {self.name} serial port closed.")
            except Exception:
                pass
            self._ser = None


class DualSerialController:
    """
    Manages two Arduino serial connections (UNO + Nano).

    Sends posture state to both boards. The Nano additionally receives
    BUZZ_ON/BUZZ_OFF commands based on the sliding window rule.
    """

    def __init__(self):
        self.uno = SerialController(
            port=config.SERIAL_PORT_UNO,
            baud=config.SERIAL_BAUD_UNO,
            enabled=config.ENABLE_SERIAL_UNO,
            name="Arduino UNO (TFT)",
        )
        self.nano = SerialController(
            port=config.SERIAL_PORT_NANO,
            baud=config.SERIAL_BAUD_NANO,
            enabled=config.ENABLE_SERIAL_NANO,
            name="Arduino Nano (LCD+Buzzer)",
        )
        self._last_buzz_state = None

    def connect(self):
        """Open both serial connections."""
        self.uno.connect()
        self.nano.connect()

    def send_posture(self, state, buzz_active=False):
        """
        Send posture state to both Arduinos.

        Args:
            state:       "GOOD", "BAD", "ALERT", "SLEEP", "CAL", "READY", "OFF", "NONE"
            buzz_active: True if the 25/30s rule is met (buzzer should sound)
        """
        # Both boards get the same state command
        self.uno.send(state)
        self.nano.send(state)

        # Nano also gets explicit buzzer control
        if buzz_active and self._last_buzz_state != True:
            self.nano.send("BUZZ_ON")
            self._last_buzz_state = True
        elif not buzz_active and self._last_buzz_state != False:
            self.nano.send("BUZZ_OFF")
            self._last_buzz_state = False

    def send_raw(self, command):
        """Send a raw command to both boards (CAL, READY, OFF, etc.)."""
        self.uno.send(command)
        self.nano.send(command)

    @property
    def is_connected(self):
        """True if at least one board is connected."""
        return self.uno.is_connected or self.nano.is_connected

    @property
    def uno_connected(self):
        return self.uno.is_connected

    @property
    def nano_connected(self):
        return self.nano.is_connected

    def disconnect(self):
        """Close both serial connections."""
        self.uno.disconnect()
        self.nano.disconnect()
