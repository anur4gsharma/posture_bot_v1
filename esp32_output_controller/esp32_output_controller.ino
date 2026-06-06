/*
 * ESP32 Posture Output Controller — TFT + Buzzer Edition
 * =======================================================
 * Receives posture status commands from the Python script
 * via USB Serial and drives output devices:
 *   - Buzzer (GPIO 4)         — beeps on bad posture, alarm on alert
 *   - TFT Display (SPI)      — shows colorful emoji faces + status text
 *
 * The ESP32 is POWERED through the same USB cable used for serial.
 *
 * Serial Protocol (115200 baud):
 *   "GOOD\n"               -> Good posture  (green happy face)
 *   "BAD:issue1,issue2\n"  -> Bad posture   (yellow worried face)
 *   "ALERT\n"              -> Persistent bad (red sad face + alarm)
 *   "NONE\n"               -> No person      (gray question mark)
 *   "CAL\n"                -> Calibrating    (blue spinner)
 *   "READY\n"              -> Monitoring started
 *   "OFF\n"                -> Shutdown
 *
 * TFT Wiring (ESP32 → TFT module):
 *   VCC  → 3.3V
 *   GND  → GND
 *   CS   → GPIO 5
 *   RST  → GPIO 16
 *   DC   → GPIO 17
 *   SDA  → GPIO 23 (MOSI)
 *   SCL  → GPIO 18 (SCLK)
 *   BLK  → 3.3V (or a GPIO for brightness control)
 *
 * Buzzer Wiring:
 *   Buzzer (+) → GPIO 4
 *   Buzzer (-) → GND
 *
 * Library: TFT_eSPI (install via Arduino Library Manager)
 *   Configure User_Setup.h for your TFT model (ST7735/ILI9341).
 */

#include <TFT_eSPI.h>
#include <SPI.h>

// ======================== PIN CONFIG ========================
#define BUZZER_PIN    4

// TFT pins are configured in TFT_eSPI/User_Setup.h
// Typical ST7735 128x160 or ILI9341 240x320

// Buzzer tone settings
#define BEEP_FREQ     2000    // Hz for bad posture beep
#define ALARM_FREQ    3000    // Hz for persistent alert
#define BEEP_ON_MS    150     // Beep duration (ms)
#define BEEP_OFF_MS   350     // Silence between beeps (ms)
#define ALARM_ON_MS   100     // Alarm pulse on (ms)
#define ALARM_OFF_MS  100     // Alarm pulse off (ms)

// LEDC PWM channel for buzzer (ESP32 doesn't have tone())
#define LEDC_CHANNEL  0
#define LEDC_RESOLUTION 8

// ======================== COLORS ========================
// Using RGB565 format for TFT

// Background colors
#define BG_GOOD       0x0400   // Dark green
#define BG_BAD        0x4000   // Dark yellow/orange
#define BG_ALERT      0x4000   // Dark red
#define BG_NONE       0x2104   // Dark gray
#define BG_CAL        0x0010   // Dark blue
#define BG_OFF        0x0000   // Black

// Accent colors for faces
#define COL_GREEN     0x07E0   // Bright green
#define COL_LIME      0x47E0   // Lighter green
#define COL_YELLOW    0xFFE0   // Bright yellow
#define COL_ORANGE    0xFBE0   // Orange
#define COL_RED       0xF800   // Bright red
#define COL_PINK      0xF81F   // Pink
#define COL_BLUE      0x001F   // Bright blue
#define COL_CYAN      0x07FF   // Cyan
#define COL_WHITE     0xFFFF   // White
#define COL_GRAY      0x7BEF   // Gray
#define COL_DARK_GRAY 0x39E7   // Dark gray
#define COL_BLACK     0x0000   // Black

// ============================================================

TFT_eSPI tft = TFT_eSPI();

String currentCommand = "";
String lastStatus = "";
String lastIssues = "";

unsigned long lastBeepTime = 0;
bool beepState = false;

// Screen dimensions (detected at runtime)
int SCREEN_W;
int SCREEN_H;
int CENTER_X;
int CENTER_Y;
int FACE_RADIUS;


void setup() {
  // Serial — same USB cable that powers this board
  Serial.begin(115200);
  Serial.println("ESP32 Posture Controller (TFT + Buzzer)");

  // Buzzer setup using LEDC PWM
  ledcSetup(LEDC_CHANNEL, BEEP_FREQ, LEDC_RESOLUTION);
  ledcAttachPin(BUZZER_PIN, LEDC_CHANNEL);
  ledcWrite(LEDC_CHANNEL, 0);  // Start silent

  // TFT setup
  tft.init();
  tft.setRotation(1);  // Landscape mode
  tft.fillScreen(COL_BLACK);

  SCREEN_W = tft.width();
  SCREEN_H = tft.height();
  CENTER_X = SCREEN_W / 2;
  CENTER_Y = SCREEN_H / 2 - 10;  // Shift face up slightly for text
  FACE_RADIUS = min(SCREEN_W, SCREEN_H) / 4;

  // Startup screen
  drawStartupScreen();
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

  // Handle buzzer patterns (non-blocking)
  handleBuzzer();
}


// ======================== COMMAND PROCESSOR ========================

void processCommand(String cmd) {
  cmd.trim();

  // Avoid redrawing if nothing changed
  String newStatus = "";
  String newIssues = "";

  if (cmd == "GOOD") {
    newStatus = "GOOD";
    if (newStatus != lastStatus) {
      setBuzzerOff();
      drawGoodScreen();
    }

  } else if (cmd.startsWith("BAD:")) {
    newIssues = cmd.substring(4);
    newStatus = "BAD";
    if (newStatus != lastStatus || newIssues != lastIssues) {
      setBuzzerBeep();
      drawBadScreen(newIssues);
    }

  } else if (cmd == "ALERT") {
    newStatus = "ALERT";
    if (newStatus != lastStatus) {
      setBuzzerAlarm();
      drawAlertScreen();
    }

  } else if (cmd == "NONE") {
    newStatus = "NONE";
    if (newStatus != lastStatus) {
      setBuzzerOff();
      drawNoPersonScreen();
    }

  } else if (cmd == "CAL") {
    newStatus = "CAL";
    if (newStatus != lastStatus) {
      setBuzzerOff();
      drawCalibrationScreen();
    }

  } else if (cmd == "READY") {
    newStatus = "READY";
    // Quick confirmation beep
    ledcWriteTone(LEDC_CHANNEL, 1500);
    delay(100);
    ledcWriteTone(LEDC_CHANNEL, 2000);
    delay(100);
    ledcWrite(LEDC_CHANNEL, 0);
    drawReadyScreen();

  } else if (cmd == "OFF") {
    newStatus = "OFF";
    setBuzzerOff();
    drawOffScreen();
  }

  lastStatus = newStatus;
  lastIssues = newIssues;
}


// ======================== SCREEN DRAWING ========================

void drawStartupScreen() {
  tft.fillScreen(COL_BLACK);
  tft.setTextColor(COL_CYAN, COL_BLACK);
  tft.setTextDatum(MC_DATUM);
  tft.setTextSize(2);
  tft.drawString("PostureBot", CENTER_X, CENTER_Y - 15);
  tft.setTextSize(1);
  tft.setTextColor(COL_GRAY, COL_BLACK);
  tft.drawString("Waiting for connection...", CENTER_X, CENTER_Y + 15);
}


// ---- GOOD POSTURE: Green happy face ----
void drawGoodScreen() {
  tft.fillScreen(0x0320);  // Deep green background

  int cx = CENTER_X;
  int cy = CENTER_Y - 5;
  int r = FACE_RADIUS;

  // Face circle — bright green with white outline
  tft.fillCircle(cx, cy, r, COL_GREEN);
  tft.drawCircle(cx, cy, r, COL_WHITE);
  tft.drawCircle(cx, cy, r + 1, COL_WHITE);

  // Eyes — solid black circles with white highlights
  int eyeY = cy - r / 4;
  int eyeSpacing = r / 3;
  int eyeR = r / 7;
  tft.fillCircle(cx - eyeSpacing, eyeY, eyeR, COL_BLACK);
  tft.fillCircle(cx + eyeSpacing, eyeY, eyeR, COL_BLACK);
  // Eye highlights (small white dot in each eye)
  tft.fillCircle(cx - eyeSpacing + 2, eyeY - 2, 2, COL_WHITE);
  tft.fillCircle(cx + eyeSpacing + 2, eyeY - 2, 2, COL_WHITE);

  // Smile — arc using line segments
  int smileY = cy + r / 5;
  int smileW = r / 2;
  for (int i = -smileW; i <= smileW; i++) {
    int y = smileY + (i * i) / (smileW * 2);
    tft.drawPixel(cx + i, y, COL_BLACK);
    tft.drawPixel(cx + i, y + 1, COL_BLACK);
  }

  // Cheek blush (subtle pink circles)
  tft.fillCircle(cx - eyeSpacing - 3, smileY - 2, 4, 0xFBCF);
  tft.fillCircle(cx + eyeSpacing + 3, smileY - 2, 4, 0xFBCF);

  // Status text
  tft.setTextDatum(MC_DATUM);
  tft.setTextColor(COL_WHITE, 0x0320);
  tft.setTextSize(2);
  tft.drawString("GOOD POSTURE", CENTER_X, SCREEN_H - 25);
  tft.setTextSize(1);
  tft.drawString("Keep it up!", CENTER_X, SCREEN_H - 8);
}


// ---- BAD POSTURE: Yellow worried face ----
void drawBadScreen(String issues) {
  tft.fillScreen(0x4200);  // Dark amber background

  int cx = CENTER_X;
  int cy = CENTER_Y - 10;
  int r = FACE_RADIUS;

  // Face circle — bright yellow
  tft.fillCircle(cx, cy, r, COL_YELLOW);
  tft.drawCircle(cx, cy, r, COL_WHITE);
  tft.drawCircle(cx, cy, r + 1, COL_WHITE);

  // Worried eyes — open wide (larger circles)
  int eyeY = cy - r / 4;
  int eyeSpacing = r / 3;
  int eyeR = r / 6;
  tft.fillCircle(cx - eyeSpacing, eyeY, eyeR + 1, COL_BLACK);
  tft.fillCircle(cx + eyeSpacing, eyeY, eyeR + 1, COL_BLACK);
  // White part for worried look
  tft.fillCircle(cx - eyeSpacing, eyeY - 1, eyeR - 2, COL_WHITE);
  tft.fillCircle(cx + eyeSpacing, eyeY - 1, eyeR - 2, COL_WHITE);
  // Pupils
  tft.fillCircle(cx - eyeSpacing, eyeY, 3, COL_BLACK);
  tft.fillCircle(cx + eyeSpacing, eyeY, 3, COL_BLACK);

  // Worried eyebrows — angled lines
  tft.drawLine(cx - eyeSpacing - eyeR, eyeY - eyeR - 4,
               cx - eyeSpacing + eyeR, eyeY - eyeR - 2, COL_BLACK);
  tft.drawLine(cx + eyeSpacing + eyeR, eyeY - eyeR - 4,
               cx + eyeSpacing - eyeR, eyeY - eyeR - 2, COL_BLACK);

  // Frown — inverted arc
  int frownY = cy + r / 3;
  int frownW = r / 3;
  for (int i = -frownW; i <= frownW; i++) {
    int y = frownY - (i * i) / (frownW * 3);
    tft.drawPixel(cx + i, y, COL_BLACK);
    tft.drawPixel(cx + i, y + 1, COL_BLACK);
  }

  // Status text
  tft.setTextDatum(MC_DATUM);
  tft.setTextColor(COL_WHITE, 0x4200);
  tft.setTextSize(2);
  tft.drawString("BAD POSTURE", CENTER_X, SCREEN_H - 30);

  // Show issues in smaller text
  tft.setTextSize(1);
  issues.replace(",", " ");
  if (issues.length() > 30) {
    issues = issues.substring(0, 29) + ">";
  }
  tft.drawString(issues, CENTER_X, SCREEN_H - 10);
}


// ---- ALERT: Red sad face with flashing ----
void drawAlertScreen() {
  tft.fillScreen(COL_RED);  // Full red background

  int cx = CENTER_X;
  int cy = CENTER_Y - 10;
  int r = FACE_RADIUS;

  // Face circle — dark red/maroon
  tft.fillCircle(cx, cy, r, 0xA000);
  tft.drawCircle(cx, cy, r, COL_WHITE);
  tft.drawCircle(cx, cy, r + 1, COL_WHITE);
  tft.drawCircle(cx, cy, r + 2, COL_YELLOW);

  // Sad eyes — angled closed eyes (X shape)
  int eyeY = cy - r / 5;
  int eyeSpacing = r / 3;
  int s = r / 7;
  // Left X eye
  tft.drawLine(cx - eyeSpacing - s, eyeY - s, cx - eyeSpacing + s, eyeY + s, COL_WHITE);
  tft.drawLine(cx - eyeSpacing + s, eyeY - s, cx - eyeSpacing - s, eyeY + s, COL_WHITE);
  // Right X eye
  tft.drawLine(cx + eyeSpacing - s, eyeY - s, cx + eyeSpacing + s, eyeY + s, COL_WHITE);
  tft.drawLine(cx + eyeSpacing + s, eyeY - s, cx + eyeSpacing - s, eyeY + s, COL_WHITE);

  // Thick sad frown
  int frownY = cy + r / 3;
  int frownW = r / 3;
  for (int i = -frownW; i <= frownW; i++) {
    int y = frownY - (i * i) / (frownW * 2);
    tft.drawPixel(cx + i, y, COL_WHITE);
    tft.drawPixel(cx + i, y + 1, COL_WHITE);
    tft.drawPixel(cx + i, y - 1, COL_WHITE);
  }

  // Warning text with emphasis
  tft.setTextDatum(MC_DATUM);
  tft.setTextColor(COL_YELLOW, COL_RED);
  tft.setTextSize(2);
  tft.drawString("!! ALERT !!", CENTER_X, SCREEN_H - 30);
  tft.setTextSize(1);
  tft.setTextColor(COL_WHITE, COL_RED);
  tft.drawString("FIX YOUR POSTURE NOW", CENTER_X, SCREEN_H - 10);
}


// ---- NO PERSON: Gray question mark ----
void drawNoPersonScreen() {
  tft.fillScreen(0x2104);  // Dark gray

  int cx = CENTER_X;
  int cy = CENTER_Y - 5;
  int r = FACE_RADIUS;

  // Face circle — medium gray
  tft.fillCircle(cx, cy, r, COL_GRAY);
  tft.drawCircle(cx, cy, r, COL_WHITE);

  // Question mark
  tft.setTextDatum(MC_DATUM);
  tft.setTextColor(COL_WHITE, COL_GRAY);
  tft.setTextSize(4);
  tft.drawString("?", cx, cy);

  // Status text
  tft.setTextColor(COL_WHITE, 0x2104);
  tft.setTextSize(1);
  tft.drawString("No Person Detected", CENTER_X, SCREEN_H - 15);
}


// ---- CALIBRATING: Blue pulsing circle ----
void drawCalibrationScreen() {
  tft.fillScreen(0x0010);  // Dark blue

  int cx = CENTER_X;
  int cy = CENTER_Y - 5;
  int r = FACE_RADIUS;

  // Pulsing rings
  for (int i = 0; i < 3; i++) {
    uint16_t ringColor = (i == 0) ? COL_CYAN : (i == 1) ? COL_BLUE : 0x0018;
    tft.drawCircle(cx, cy, r + i * 8, ringColor);
    tft.drawCircle(cx, cy, r + i * 8 + 1, ringColor);
  }

  // Inner circle
  tft.fillCircle(cx, cy, r - 5, COL_BLUE);
  tft.drawCircle(cx, cy, r - 5, COL_CYAN);

  // Person icon (simple)
  tft.fillCircle(cx, cy - 8, 6, COL_WHITE);              // Head
  tft.fillRoundRect(cx - 8, cy, 16, 14, 3, COL_WHITE);   // Body

  // Text
  tft.setTextDatum(MC_DATUM);
  tft.setTextColor(COL_CYAN, 0x0010);
  tft.setTextSize(2);
  tft.drawString("CALIBRATING", CENTER_X, SCREEN_H - 30);
  tft.setTextSize(1);
  tft.setTextColor(COL_WHITE, 0x0010);
  tft.drawString("Sit straight & still!", CENTER_X, SCREEN_H - 10);
}


// ---- READY: Transition screen ----
void drawReadyScreen() {
  tft.fillScreen(0x0320);  // Green

  tft.setTextDatum(MC_DATUM);
  tft.setTextColor(COL_WHITE, 0x0320);
  tft.setTextSize(2);
  tft.drawString("READY!", CENTER_X, CENTER_Y - 10);
  tft.setTextSize(1);
  tft.drawString("Monitoring your posture...", CENTER_X, CENTER_Y + 15);
}


// ---- OFF: Shutdown screen ----
void drawOffScreen() {
  tft.fillScreen(COL_BLACK);
  tft.setTextDatum(MC_DATUM);
  tft.setTextColor(COL_DARK_GRAY, COL_BLACK);
  tft.setTextSize(1);
  tft.drawString("System OFF", CENTER_X, CENTER_Y);
}


// ======================== BUZZER CONTROL ========================

enum BuzzerMode { BUZZER_OFF, BUZZER_BEEP, BUZZER_ALARM };
BuzzerMode buzzerMode = BUZZER_OFF;


void setBuzzerOff() {
  buzzerMode = BUZZER_OFF;
  ledcWrite(LEDC_CHANNEL, 0);
  beepState = false;
}


void setBuzzerBeep() {
  if (buzzerMode != BUZZER_BEEP) {
    buzzerMode = BUZZER_BEEP;
    lastBeepTime = millis();
    beepState = false;
  }
}


void setBuzzerAlarm() {
  if (buzzerMode != BUZZER_ALARM) {
    buzzerMode = BUZZER_ALARM;
    lastBeepTime = millis();
    beepState = false;
  }
}


void handleBuzzer() {
  unsigned long now = millis();

  switch (buzzerMode) {
    case BUZZER_OFF:
      break;

    case BUZZER_BEEP:
      // Intermittent beeping pattern
      if (beepState) {
        if (now - lastBeepTime >= BEEP_ON_MS) {
          ledcWrite(LEDC_CHANNEL, 0);
          beepState = false;
          lastBeepTime = now;
        }
      } else {
        if (now - lastBeepTime >= BEEP_OFF_MS) {
          ledcWriteTone(LEDC_CHANNEL, BEEP_FREQ);
          beepState = true;
          lastBeepTime = now;
        }
      }
      break;

    case BUZZER_ALARM:
      // Rapid alarm pattern
      if (beepState) {
        if (now - lastBeepTime >= ALARM_ON_MS) {
          ledcWrite(LEDC_CHANNEL, 0);
          beepState = false;
          lastBeepTime = now;
        }
      } else {
        if (now - lastBeepTime >= ALARM_OFF_MS) {
          ledcWriteTone(LEDC_CHANNEL, ALARM_FREQ);
          beepState = true;
          lastBeepTime = now;
        }
      }
      break;
  }
}
