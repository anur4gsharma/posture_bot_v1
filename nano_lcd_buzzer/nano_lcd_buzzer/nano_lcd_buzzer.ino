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
bool buzzerState = false;
unsigned long lastBeepTime = 0;

// ======================== CUSTOM CHARACTERS ========================
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

  // LCD init — use begin() for correct hardware initialization
  lcd.begin();           // FIX iter 2: was lcd.init()
  lcd.backlight();
  lcd.noCursor();        // FIX iter 2: suppress blinking cursor artifact
  lcd.noBlink();

  // Create custom characters (must be done after begin())
  lcd.createChar(0, heartChar);
  lcd.createChar(1, skullChar);
  lcd.createChar(2, sleepChar);
  lcd.createChar(3, checkChar);

  // Show startup message
  // FIX iter 1: all strings are exactly 16 chars (custom char counts as 1)
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print(" NOT CONNECTED  ");  // 16 chars
  lcd.setCursor(0, 1);
  lcd.print("     Zzz...     ");  // 16 chars
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

  // FIX iter 2: lcd.clear() before every write prevents ghost characters
  // from previous longer strings bleeding into shorter new strings.
  lcd.clear();

  if (cmd == "GOOD") {
    // FIX iter 1: " GOOD POSTURE  " (15) + heart (1) = 16 total
    lcd.setCursor(0, 0);
    lcd.print(" GOOD POSTURE  ");  // 15 chars
    lcd.write(byte(0));            // heart = 1 char → total 16
    // FIX iter 1: "  Keep it up!   " = 16 chars
    lcd.setCursor(0, 1);
    lcd.print("  Keep it up!   "); // 16 chars

    buzzerActive = false;
    buzzerState = false;
    digitalWrite(BUZZER_PIN, LOW);

  } else if (cmd == "BAD" || cmd == "ALERT") {
    // FIX iter 1: "BAD POSTURE!!! " (15) + skull (1) = 16 total
    lcd.setCursor(0, 0);
    lcd.print("BAD POSTURE!!! "); // 15 chars
    lcd.write(byte(1));           // skull = 1 char → total 16
    // FIX iter 1: "Fix your posture" = 16 chars (was "Fix ur posture! " with trailing space issues)
    lcd.setCursor(0, 1);
    lcd.print("Fix your posture"); // 16 chars

  } else if (cmd == "SLEEP" || cmd == "NONE") {
    lcd.setCursor(0, 0);
    lcd.print(" NOT CONNECTED  ");  // 16 chars
    lcd.setCursor(0, 1);
    lcd.print("     Zzz...     ");  // 16 chars

    buzzerActive = false;
    buzzerState = false;
    digitalWrite(BUZZER_PIN, LOW);

  } else if (cmd == "CAL") {
    lcd.setCursor(0, 0);
    lcd.print(" CALIBRATING... ");  // 16 chars
    lcd.setCursor(0, 1);
    lcd.print("  Sit straight! ");  // 16 chars

  } else if (cmd == "READY") {
    // FIX iter 1: "    READY!    " (14) + check (1) + " " (1) = 16 total
    lcd.setCursor(0, 0);
    lcd.print("    READY!    "); // 14 chars
    lcd.write(byte(3));          // check = 1 char
    lcd.print(" ");              // 1 char → total 16
    lcd.setCursor(0, 1);
    lcd.print("  Monitoring... ");  // 16 chars

  } else if (cmd == "OFF") {
    lcd.setCursor(0, 0);
    lcd.print("   SYSTEM OFF   ");  // 16 chars
    lcd.setCursor(0, 1);
    lcd.print("                ");  // 16 spaces

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
    if (now - lastBeepTime >= BEEP_ON_MS) {
      digitalWrite(BUZZER_PIN, LOW);
      buzzerState = false;
      lastBeepTime = now;
    }
  } else {
    if (now - lastBeepTime >= BEEP_OFF_MS) {
      digitalWrite(BUZZER_PIN, HIGH);
      buzzerState = true;
      lastBeepTime = now;
    }
  }
}
