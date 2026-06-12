# Wiring: Arduino Nano + LCD 16×2 I2C + Active Buzzer

## Components

- **Arduino Nano** (ATmega328P)
- **16×2 I2C LCD** (PCF8574 backpack, address 0x27)
- **Active buzzer** (5V, 2-pin)
- **4 jumper wires** (for LCD)
- **2 jumper wires** (for buzzer)

## Wiring Diagram

```
                    ARDUINO NANO
                  ┌──────────────┐
                  │   [Mini-USB]  │ ← Connect to PC
                  │              │
              D13 │              │ D12
              3V3 │              │ D11
             AREF │              │ D10
              A0  │              │ D9
              A1  │              │ D8  ─────── Buzzer (+)
              A2  │              │ D7
              A3  │              │ D6
    LCD SDA ─ A4  │              │ D5
    LCD SCL ─ A5  │              │ D4
              A6  │              │ D3
              A7  │              │ D2
              5V  │              │ GND ─┬─ Buzzer (-)
             RST  │              │ RST  │
             GND  │              │ RX0  │
             VIN  │              │ TX1  │
                  └──────────────┘      │
                    │                   │
                    │                   │
                    5V ────── LCD VCC   │
                    GND ───── LCD GND ──┘
```

## LCD Wiring Table

| LCD I2C Pin | Arduino Nano Pin | Wire Color (suggestion) |
|-------------|-----------------|------------------------|
| **VCC**     | **5V**          | Red                    |
| **GND**     | **GND**         | Black                  |
| **SDA**     | **A4**          | Blue                   |
| **SCL**     | **A5**          | Yellow                 |

## Buzzer Wiring Table

| Buzzer Pin  | Arduino Nano Pin | Wire Color (suggestion) |
|-------------|-----------------|------------------------|
| **+ (Signal)** | **D8**       | Orange                 |
| **- (GND)**    | **GND**      | Black                  |

> **Important**: Use an **active** buzzer (not passive). An active buzzer produces sound when you just apply HIGH voltage — no PWM/tone generation needed. If your buzzer has a white sticker on top, it's usually active.

## Breadboard Layout (Optional)

If using a breadboard for cleaner wiring:

```
┌─────────────────────────────────────────────┐
│  BREADBOARD                                  │
│                                              │
│  [Nano]  ←plugged into breadboard→           │
│                                              │
│  Row for LCD:                                │
│    VCC → 5V rail                             │
│    GND → GND rail                            │
│    SDA → A4 (wire)                           │
│    SCL → A5 (wire)                           │
│                                              │
│  Row for Buzzer:                             │
│    (+) → D8 (wire)                           │
│    (-) → GND rail                            │
│                                              │
│  Power rails:                                │
│    + rail ← Nano 5V                          │
│    - rail ← Nano GND                         │
└─────────────────────────────────────────────┘
```

## LCD Contrast Adjustment

The I2C LCD module has a **small blue potentiometer** on the back. If the display shows all squares or is blank:

1. Upload the sketch to the Nano
2. Power it on
3. Gently turn the potentiometer with a small screwdriver
4. Adjust until text is clearly visible

## I2C Address

The default I2C address is **0x27**. Some modules use **0x3F**.

If the LCD doesn't work, run the I2C scanner (see `hardware_setup/README.md` troubleshooting section) and update the address in `nano_lcd_buzzer.ino`:

```cpp
// Change 0x27 to your detected address
LiquidCrystal_I2C lcd(0x27, 16, 2);
```

## Power Notes

- The Nano is powered via USB from the PC (same cable used for serial)
- The LCD is powered from the Nano's 5V pin (max ~500mA available)
- The active buzzer draws ~30mA at 5V — well within limits
- Total current: LCD (~40mA) + Buzzer (~30mA) = ~70mA — safe for USB power
