# Wiring: Arduino UNO + TFT 2.4" ILI9341 Shield

## Connection Method

The TFT 2.4" ILI9341 shield is designed to **plug directly onto the Arduino UNO** headers. **No separate wiring is needed.**

Simply align the shield pins with the UNO's female headers and press down firmly.

```
    ┌─────────────────────────────┐
    │    TFT 2.4" ILI9341 Shield  │
    │    ┌───────────────────┐    │
    │    │                   │    │
    │    │   240 x 320 px    │    │
    │    │   Color Display   │    │
    │    │                   │    │
    │    │                   │    │
    │    └───────────────────┘    │
    │                             │
    │  ║║║║║║║║║║║║║║║║║║║║║║║║  │  ← Pins plug into UNO
    └──╨╨╨╨╨╨╨╨╨╨╨╨╨╨╨╨╨╨╨╨╨╨╨──┘
       │││││││││││││││││││││││
    ┌──╥╥╥╥╥╥╥╥╥╥╥╥╥╥╥╥╥╥╥╥╥╥╥──┐
    │  ARDUINO UNO                 │
    │                              │
    │   [USB-B Port]               │  ← Connect to PC
    │                              │
    └──────────────────────────────┘
```

## Pin Mapping (for reference)

The shield uses these UNO pins internally:

| TFT Function | UNO Pin | Notes |
|-------------|---------|-------|
| LCD_RD      | A0      | Read strobe |
| LCD_WR      | A1      | Write strobe |
| LCD_RS (DC) | A2      | Data/Command select |
| LCD_CS      | A3      | Chip select |
| LCD_RST     | A4      | Reset |
| LCD_D0      | D8      | Data bit 0 |
| LCD_D1      | D9      | Data bit 1 |
| LCD_D2      | D2      | Data bit 2 |
| LCD_D3      | D3      | Data bit 3 |
| LCD_D4      | D4      | Data bit 4 |
| LCD_D5      | D5      | Data bit 5 |
| LCD_D6      | D6      | Data bit 6 |
| LCD_D7      | D7      | Data bit 7 |
| SD_CS       | D10     | SD card chip select (if present) |
| SD_MOSI     | D11     | SD card MOSI |
| SD_MISO     | D12     | SD card MISO |
| SD_SCK      | D13     | SD card clock |
| 5V          | 5V      | Power |
| 3.3V        | 3.3V    | Power |
| GND         | GND     | Ground |

> **Note:** The shield occupies virtually **all** UNO pins. This is why we use a separate Arduino Nano for the LCD and buzzer.

## Important Notes

1. **Orientation**: Make sure the shield's USB cutout aligns with the UNO's USB port
2. **Library**: The `MCUFRIEND_kbv` library auto-detects the shield's driver chip (ILI9341, ILI9481, etc.)
3. **No SD card**: This sketch does not use the SD card slot on the shield
4. **Power**: The shield is powered through the UNO's 5V and 3.3V rails via USB
