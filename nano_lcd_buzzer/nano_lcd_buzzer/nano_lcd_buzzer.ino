/*
 * Arduino Nano — LCD 16x2 + Active Buzzer Posture Controller
 * ============================================================
 * Receives posture status commands from the Python script
 * via USB Serial and drives:
 *   - 16x2 I2C LCD (address 0x27) — shows text messages
 *   - Active buzzer (digital pin D8) — beeps ONLY when BUZZ_ON received
 *
 * The buzzer is controlled independently from the display state.
 * Python sends BUZZ_ON only when bad posture >= 25s in the last 30s.
 *
 * Serial Protocol (9600 baud):
 *   "GOOD\n"      -> LCD: "  GOOD POSTURE  " / "   Keep it up!  "
 *   "BAD\n"       -> LCD: " BAD POSTURE!!! " / "Fix your posture!"
 *   "ALERT\n"     -> LCD: " BAD POSTURE!!! " / "Fix your posture!" (same as BAD)
 *   "SLEEP\n"     -> LCD: " NOT CONNECTED  " / "    Zzz...      "
 *   "CAL\n"       -> LCD: " CALIBRATING... " / " Sit straight!  "
 *   "READY\n"     -> LCD: "    READY!      " / "  Monitoring... "
 *   "OFF\n"       -> LCD: "  SYSTEM OFF    " / "                "
 *   "BUZZ_ON\n"   -> Start buzzer beeping pattern
 *   "BUZZ_OFF\n"  -> Stop buzzer immediately
 *
 * Wiring:
 *   LCD SDA  -> A4 (Nano)
 *   LCD SCL  -> A5 (Nano)
 *   LCD VCC  -> 5V
 *   LCD GND  -> GND
 *   Buzzer + -> D8
 *   Buzzer - -> GND
 *
 * Libraries: LiquidCrystal_I2C (install via Arduino Library Manager)
 *            Wire (built-in)
 *
 * Board: Arduino Nano (ATmega328P / ATmega328P Old Bootloader)
 */

#include <Wire.h>
#include <LiquidCrystal_I2C.h>

// ======================== PIN CONFIG ========================
#define BUZZER_PIN 8

// LCD at I2C address 0x27, 16 columns x 2 rows
LiquidCrystal_I2C lcd(0x27, 16, 2);

// ======================== BUZZER TIMING ========================
#define BEEP_ON_MS   200    // Buzzer on duration (ms)
#define BEEP_OFF_MS  300    // Silence between beeps (ms)

// ======================== GLOBALS ========================
String currentCommand = "";
String lastDisplayState = "";
bool buzzerActive = false;
bool buzzerState = false;        // Current on/off in beep cycle
unsigned long lastBeepTime = 0;

// Custom characters for LCD
byte heartChar[8] = {
  0b00000,
  0b01010,
  0b11111,
  0b11111,
  0b11111,
  0b01110,
  0b00100,
  0b00000
};

byte skullChar[8] = {
  0b00000,
  0b01110,
  0b10101,
  0b11011,
  0b01110,
  0b01110,
  0b00000,
  0b00000
};

byte sleepChar[8] = {
  0b11100,
  0b00100,
  0b01000,
  0b11100,
  0b00000,
  0b00000,
  0b00000,
  0b00000
};

byte checkChar[8] = {
  0b00000,
  0b00001,
  0b00010,
  0b10100,
  0b01000,
  0b00000,
  0b00000,
  0b00000
};


void setup() {
  Serial.begin(9600);
  Serial.println(F("Nano LCD+Buzzer Controller Starting..."));

  // Buzzer pin
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);

  // LCD init
  lcd.init();
  lcd.backlight();

  // Create custom characters
  lcd.createChar(0, heartChar);
  lcd.createChar(1, skullChar);
  lcd.createChar(2, sleepChar);
  lcd.createChar(3, checkChar);

  // Show startup message
  lcd.setCursor(0, 0);
  lcd.print(" NOT CONNECTED  ");
  lcd.setCursor(0, 1);
  lcd.print("    Zzz...      ");
}


void loop() {
  // Read serial commands
  while (Serial.available() > 0) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      if (currentCommand.length() > 0) {
        processCommand(currentCommand);
        currentCommand = "";
      }
    } else {
      currentCommand += c;
    }
  }

  // Handle buzzer beeping pattern (non-blocking)
  handleBuzzer();
}


// ======================== COMMAND PROCESSOR ========================

void processCommand(String cmd) {
  cmd.trim();

  // --- Buzzer commands (independent of display) ---
  if (cmd == "BUZZ_ON") {
    buzzerActive = true;
    lastBeepTime = millis();
    buzzerState = false;
    return;
  }

  if (cmd == "BUZZ_OFF") {
    buzzerActive = false;
    buzzerState = false;
    digitalWrite(BUZZER_PIN, LOW);
    return;
  }

  // --- Display commands (only update if changed) ---
  if (cmd == lastDisplayState) return;

  if (cmd == "GOOD") {
    lcd.setCursor(0, 0);
    lcd.print(" GOOD POSTURE ");
    lcd.write(byte(0));  // heart
    lcd.print(" ");
    lcd.setCursor(0, 1);
    lcd.print("  Keep it up!   ");

    // Turn off buzzer when posture is good
    buzzerActive = false;
    buzzerState = false;
    digitalWrite(BUZZER_PIN, LOW);

  } else if (cmd == "BAD" || cmd == "ALERT") {
    lcd.setCursor(0, 0);
    lcd.print(" BAD POSTURE! ");
    lcd.write(byte(1));  // skull
    lcd.print(" ");
    lcd.setCursor(0, 1);
    lcd.print("Fix ur posture! ");

  } else if (cmd == "SLEEP" || cmd == "NONE") {
    lcd.setCursor(0, 0);
    lcd.print(" NOT CONNECTED  ");
    lcd.setCursor(0, 1);
    lcd.print("    Zzz...      ");

    // Turn off buzzer when sleeping
    buzzerActive = false;
    buzzerState = false;
    digitalWrite(BUZZER_PIN, LOW);

  } else if (cmd == "CAL") {
    lcd.setCursor(0, 0);
    lcd.print(" CALIBRATING... ");
    lcd.setCursor(0, 1);
    lcd.print(" Sit straight!  ");

  } else if (cmd == "READY") {
    lcd.setCursor(0, 0);
    lcd.print("    READY! ");
    lcd.write(byte(3));  // check
    lcd.print("    ");
    lcd.setCursor(0, 1);
    lcd.print("  Monitoring... ");

  } else if (cmd == "OFF") {
    lcd.setCursor(0, 0);
    lcd.print("  SYSTEM OFF    ");
    lcd.setCursor(0, 1);
    lcd.print("                ");

    // Turn off buzzer
    buzzerActive = false;
    buzzerState = false;
    digitalWrite(BUZZER_PIN, LOW);
  }

  lastDisplayState = cmd;
}


// ======================== BUZZER HANDLER ========================

void handleBuzzer() {
  if (!buzzerActive) return;

  unsigned long now = millis();

  if (buzzerState) {
    // Currently beeping — check if on-time elapsed
    if (now - lastBeepTime >= BEEP_ON_MS) {
      digitalWrite(BUZZER_PIN, LOW);
      buzzerState = false;
      lastBeepTime = now;
    }
  } else {
    // Currently silent — check if off-time elapsed
    if (now - lastBeepTime >= BEEP_OFF_MS) {
      digitalWrite(BUZZER_PIN, HIGH);
      buzzerState = true;
      lastBeepTime = now;
    }
  }
}
