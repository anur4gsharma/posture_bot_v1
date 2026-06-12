# Hardware Setup Guide — Dual Arduino Posture Alert System

## Overview

This system uses **two Arduino boards** to provide visual and audio feedback based on posture detection:

| Board | Display | Extra | Role |
|-------|---------|-------|------|
| **Arduino UNO** | TFT 2.4" ILI9341 shield | — | Shows emoji faces (happy/scared/sleeping) |
| **Arduino Nano** | 16×2 I2C LCD | Active buzzer | Shows text messages + beeps when posture is bad for 25s/30s |

Both boards connect to the PC via USB and receive commands from the Python posture detection program through separate COM ports.

```
┌───────────────────────────────────────────────────────────┐
│                    YOUR PC / LAPTOP                       │
│                                                           │
│   Python Program (posture_monitor)                        │
│   ├── OpenCV + MediaPipe (webcam processing)             │
│   ├── WebSocket server (→ Android app)                   │
│   ├── Serial COM3 (→ Arduino UNO)                        │
│   └── Serial COM4 (→ Arduino Nano)                       │
│                                                           │
│         USB Cable 1              USB Cable 2             │
└─────────┬──────────────────────────┬─────────────────────┘
          │                          │
   ┌──────▼──────────┐      ┌───────▼──────────┐
   │  ARDUINO UNO    │      │  ARDUINO NANO    │
   │  + TFT 2.4"     │      │  + LCD 16x2 I2C  │
   │    ILI9341       │      │  + Active Buzzer  │
   │    Shield        │      │                   │
   │                  │      │  SDA → A4         │
   │  (plugs on top)  │      │  SCL → A5         │
   │                  │      │  Buzzer → D8      │
   └──────────────────┘      └───────────────────┘
```

---

## Required Components

| # | Component | Quantity | Notes |
|---|-----------|----------|-------|
| 1 | Arduino UNO | 1 | Any UNO R3 compatible |
| 2 | Arduino Nano | 1 | ATmega328P (CH340 or FTDI) |
| 3 | TFT 2.4" ILI9341 Shield | 1 | Must be UNO shield type (plugs directly) |
| 4 | I2C LCD 16×2 | 1 | With I2C backpack (PCF8574, address 0x27) |
| 5 | Active Buzzer | 1 | 5V active buzzer module |
| 6 | USB cables | 2 | USB-B for UNO, Mini-USB/Micro-USB for Nano |
| 7 | Jumper wires | 6 | For Nano connections (LCD + buzzer) |
| 8 | Breadboard (optional) | 1 | For Nano wiring |

---

## Step 1: Arduino UNO + TFT 2.4" Shield

The TFT shield **plugs directly onto the UNO** — no wiring required.

1. Align the TFT shield pins with the UNO headers
2. Press down firmly until all pins are seated
3. The shield occupies **all** UNO pins

See [WIRING_UNO_TFT.md](WIRING_UNO_TFT.md) for pin mapping details.

---

## Step 2: Arduino Nano + LCD + Buzzer

Connect the LCD and buzzer to the Nano using jumper wires:

### LCD Wiring (I2C)
| LCD Pin | Nano Pin |
|---------|----------|
| VCC     | 5V       |
| GND     | GND      |
| SDA     | A4       |
| SCL     | A5       |

### Buzzer Wiring
| Buzzer Pin | Nano Pin |
|------------|----------|
| + (Signal) | D8       |
| - (GND)    | GND      |

See [WIRING_NANO_LCD_BUZZER.md](WIRING_NANO_LCD_BUZZER.md) for detailed diagram.

---

## Step 3: Arduino IDE Setup

### 3.1 Install Arduino IDE
Download from: https://www.arduino.cc/en/software

### 3.2 Install Required Libraries

Open Arduino IDE → **Sketch** → **Include Library** → **Manage Libraries...**

**For Arduino UNO (TFT):**
- Search and install: `MCUFRIEND_kbv`
- Search and install: `Adafruit GFX Library`

**For Arduino Nano (LCD + Buzzer):**
- Search and install: `LiquidCrystal I2C` (by Frank de Brabander)

### 3.3 Upload UNO Sketch
1. Connect the Arduino UNO to your PC via USB
2. Open `uno_tft_display/uno_tft_display/uno_tft_display.ino` in Arduino IDE
3. Go to **Tools** → **Board** → Select **Arduino UNO**
4. Go to **Tools** → **Port** → Select the UNO's COM port (e.g., COM3)
5. Click **Upload** (→ button)
6. Wait for "Done uploading."

### 3.4 Upload Nano Sketch
1. Connect the Arduino Nano to your PC via a **different** USB port
2. Open `nano_lcd_buzzer/nano_lcd_buzzer/nano_lcd_buzzer.ino` in Arduino IDE
3. Go to **Tools** → **Board** → Select **Arduino Nano**
4. Go to **Tools** → **Processor** → Select **ATmega328P** (or **ATmega328P (Old Bootloader)** if upload fails)
5. Go to **Tools** → **Port** → Select the Nano's COM port (e.g., COM4)
6. Click **Upload**
7. Wait for "Done uploading."

> **Tip:** If the Nano upload fails with "not in sync", try selecting **ATmega328P (Old Bootloader)** under Processor.

---

## Step 4: Identify COM Ports

Both Arduinos must be plugged in simultaneously. You need to know which COM port each one uses.

### Windows:
1. Open **Device Manager** (Win+X → Device Manager)
2. Expand **Ports (COM & LPT)**
3. You'll see two entries, e.g.:
   - `USB-SERIAL CH340 (COM3)` — this is typically the Nano
   - `Arduino UNO (COM4)` — or similar
4. Note which is which

### Quick Test:
- Unplug one Arduino and see which COM port disappears from Device Manager

---

## Step 5: Configure Python

Edit `posture_monitor/config.py` and set the correct COM ports:

```python
# Arduino UNO — TFT 2.4" display
SERIAL_PORT_UNO = "COM3"      # ← Change to your UNO's port
SERIAL_BAUD_UNO = 9600

# Arduino Nano — LCD 16x2 + Buzzer
SERIAL_PORT_NANO = "COM4"     # ← Change to your Nano's port
SERIAL_BAUD_NANO = 9600
```

---

## Step 6: Run the System

1. Make sure **both Arduinos** are plugged in
2. Run the Python program:
   ```
   cd ASEP2_Project
   python -m posture_monitor.main
   ```
3. You should see:
   ```
   [OK] Arduino UNO (TFT) connected on COM3 @ 9600
   [OK] Arduino Nano (LCD+Buzzer) connected on COM4 @ 9600
   [OK] Webcam opened (index 0)
   ```

---

## How the System Works

### Display States

| State | TFT (UNO) | LCD (Nano) | Buzzer |
|-------|-----------|------------|--------|
| Good posture | 😊 Happy face (green bg) | `"GOOD POSTURE ♥"` / `"Keep it up!"` | OFF |
| Bad posture (<25s) | 😨 Scared face (red bg) | `"BAD POSTURE! ☠"` / `"Fix ur posture!"` | OFF |
| Bad posture (≥25s/30s) | 😨 Scared face (red bg) | `"BAD POSTURE! ☠"` / `"Fix ur posture!"` | **ON** (beeping) |
| Not connected / idle | 😴 Sleeping face (blue bg) | `"NOT CONNECTED"` / `"Zzz..."` | OFF |
| Calibrating | ⏳ Spinner (blue bg) | `"CALIBRATING..."` / `"Sit straight!"` | OFF |

### Buzzer Rule (25/30 Second Window)

The buzzer does **NOT** beep immediately when posture goes bad. Instead:

1. The system tracks posture over a **rolling 30-second window**
2. It counts how many seconds of **bad posture** occurred in that window
3. The buzzer only activates when bad posture reaches **≥25 seconds** in the window
4. The buzzer turns off as soon as posture improves enough to drop below 25 seconds

This prevents false alarms from brief movements.

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| **"Could not open COM port"** | Check Device Manager for correct port. Close Arduino IDE Serial Monitor (it locks the port). |
| **Nano upload fails** | Try **ATmega328P (Old Bootloader)** in Tools → Processor |
| **LCD shows nothing** | Check I2C address — try `0x3F` instead of `0x27`. Run I2C scanner sketch. |
| **LCD shows squares** | Adjust contrast potentiometer on the back of the LCD I2C module |
| **TFT is white/blank** | Verify shield is properly seated. Check that MCUFRIEND_kbv library is installed. |
| **Buzzer never beeps** | Check wiring (D8 → buzzer +, GND → buzzer -). Verify active buzzer (not passive). |
| **Wrong faces on TFT** | Ensure you uploaded `uno_tft_display.ino` to the UNO, not the Nano |
| **Both boards show same COM** | Unplug one and re-check. Each must have a unique COM port. |

### LCD I2C Address Scanner

If the LCD doesn't show anything, upload this sketch to the Nano to find the correct address:

```cpp
#include <Wire.h>

void setup() {
  Wire.begin();
  Serial.begin(9600);
  Serial.println("I2C Scanner");
}

void loop() {
  for (byte addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    if (Wire.endTransmission() == 0) {
      Serial.print("Found device at 0x");
      Serial.println(addr, HEX);
    }
  }
  delay(5000);
}
```

Open Serial Monitor (9600 baud) to see the detected address. Update the `LiquidCrystal_I2C lcd(0x27, 16, 2);` line in `nano_lcd_buzzer.ino` if needed.
