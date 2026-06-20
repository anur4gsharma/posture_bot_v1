/*
 * Arduino UNO — TFT 2.4" ILI9341 Posture Display
 * =================================================
 * Receives posture status commands from the Python script
 * via USB Serial and shows emoji faces on the TFT shield.
 *
 * This sketch is for a 2.4" ILI9341 TFT shield that plugs
 * directly onto the Arduino UNO (occupies all pins).
 *
 * Serial Protocol (9600 baud):
 *   "GOOD\n"    -> Happy/smiling face (green)
 *   "BAD\n"     -> Scared face (red/orange)
 *   "ALERT\n"   -> Scared face with flashing (same as BAD for display)
 *   "SLEEP\n"   -> Sleeping face (dark blue)
 *   "CAL\n"     -> Calibrating screen (blue)
 *   "READY\n"   -> Ready screen (green flash)
 *   "OFF\n"     -> Screen off (black)
 *
 * Library: MCUFRIEND_kbv (install via Arduino Library Manager)
 *          Adafruit GFX Library (dependency, install via Library Manager)
 *
 * Board: Arduino UNO
 * The TFT shield plugs directly onto the UNO headers.
 */

#include <Adafruit_GFX.h>
#include <MCUFRIEND_kbv.h>

// ======================== COLORS (RGB565) ========================
#define COL_BLACK       0x0000
#define COL_WHITE       0xFFFF
#define COL_RED         0xF800
#define COL_GREEN       0x07E0
#define COL_BLUE        0x001F
#define COL_CYAN        0x07FF
#define COL_YELLOW      0xFFE0
#define COL_ORANGE      0xFD20
#define COL_DARK_GREEN  0x0320
#define COL_DARK_RED    0x8000
#define COL_DARK_BLUE   0x0010
#define COL_DARK_AMBER  0x4200
#define COL_DARK_GRAY   0x2104
#define COL_GRAY        0x7BEF


// ======================== GLOBALS ========================
MCUFRIEND_kbv tft;

String currentCommand = "";
String lastStatus = "";

int SCREEN_W;
int SCREEN_H;
int CENTER_X;
int CENTER_Y;
int FACE_R;


void setup() {
  Serial.begin(9600);
  Serial.println(F("UNO TFT Posture Display Starting..."));

  // Initialize TFT
  uint16_t ID = tft.readID();
  if (ID == 0xD3D3) ID = 0x9481;  // Common fix for some shields
  tft.begin(ID);
  tft.setRotation(1);  // Landscape

  SCREEN_W = tft.width();
  SCREEN_H = tft.height();
  CENTER_X = SCREEN_W / 2;
  CENTER_Y = SCREEN_H / 2 - 20;
  FACE_R   = min(SCREEN_W, SCREEN_H) / 4;

  // Show sleeping face on startup (not connected yet)
  drawSleepingFace();
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
}


// ======================== COMMAND PROCESSOR ========================

void processCommand(String cmd) {
  cmd.trim();

  // Avoid redrawing if state hasn't changed
  if (cmd == lastStatus) return;

  if (cmd == "GOOD") {
    drawHappyFace();
  } else if (cmd == "BAD" || cmd == "ALERT") {
    drawScaredFace();
  } else if (cmd == "SLEEP" || cmd == "NONE") {
    drawSleepingFace();
  } else if (cmd == "CAL") {
    drawCalibrationScreen();
  } else if (cmd == "READY") {
    drawReadyScreen();
  } else if (cmd == "OFF") {
    tft.fillScreen(COL_BLACK);
  }

  lastStatus = cmd;
}



// ======================== HAPPY FACE (Good Posture) ========================

void drawHappyFace() {
  tft.fillScreen(COL_DARK_GREEN);

  int cx = CENTER_X;
  int cy = CENTER_Y;
  int r  = FACE_R;

  // Face circle — bright yellow with white outline
  tft.fillCircle(cx, cy, r, COL_YELLOW);
  tft.drawCircle(cx, cy, r, COL_WHITE);
  tft.drawCircle(cx, cy, r + 1, COL_WHITE);

  // Eyes — solid black with white highlights
  int eyeY = cy - r / 4;
  int eyeSpacing = r / 3;
  int eyeR = r / 7;

  // Left eye
  tft.fillCircle(cx - eyeSpacing, eyeY, eyeR, COL_BLACK);
  tft.fillCircle(cx - eyeSpacing + 2, eyeY - 2, 3, COL_WHITE);  // highlight

  // Right eye
  tft.fillCircle(cx + eyeSpacing, eyeY, eyeR, COL_BLACK);
  tft.fillCircle(cx + eyeSpacing + 2, eyeY - 2, 3, COL_WHITE);  // highlight

  // Cute smile — gentle arc, positioned well below eyes
  int smileY = cy + r / 2;
  int smileW = r * 2 / 5;
  for (int i = -smileW; i <= smileW; i++) {
    int y = smileY - (i * i) / (smileW * 2);
    tft.drawPixel(cx + i, y, COL_BLACK);
    tft.drawPixel(cx + i, y + 1, COL_BLACK);
    tft.drawPixel(cx + i, y + 2, COL_BLACK);
  }

}


// ======================== SCARED FACE (Bad Posture) ========================

void drawScaredFace() {
  tft.fillScreen(COL_DARK_RED);

  int cx = CENTER_X;
  int cy = CENTER_Y;
  int r  = FACE_R;

  // Face circle — orange/yellow
  tft.fillCircle(cx, cy, r, COL_ORANGE);
  tft.drawCircle(cx, cy, r, COL_WHITE);
  tft.drawCircle(cx, cy, r + 1, COL_RED);

  // Scared wide eyes — large white circles with small pupils
  int eyeY = cy - r / 5;
  int eyeSpacing = r / 3;
  int eyeR = r / 5;

  // Left eye — wide open
  tft.fillCircle(cx - eyeSpacing, eyeY, eyeR, COL_WHITE);
  tft.drawCircle(cx - eyeSpacing, eyeY, eyeR, COL_BLACK);
  tft.fillCircle(cx - eyeSpacing, eyeY + 2, eyeR / 2, COL_BLACK);  // pupil looking down

  // Right eye — wide open
  tft.fillCircle(cx + eyeSpacing, eyeY, eyeR, COL_WHITE);
  tft.drawCircle(cx + eyeSpacing, eyeY, eyeR, COL_BLACK);
  tft.fillCircle(cx + eyeSpacing, eyeY + 2, eyeR / 2, COL_BLACK);  // pupil looking down

  // Raised eyebrows (scared look)
  int browY = eyeY - eyeR - 6;
  tft.drawLine(cx - eyeSpacing - eyeR, browY + 4,
               cx - eyeSpacing + eyeR, browY, COL_BLACK);
  tft.drawLine(cx - eyeSpacing - eyeR, browY + 5,
               cx - eyeSpacing + eyeR, browY + 1, COL_BLACK);
  tft.drawLine(cx + eyeSpacing + eyeR, browY + 4,
               cx + eyeSpacing - eyeR, browY, COL_BLACK);
  tft.drawLine(cx + eyeSpacing + eyeR, browY + 5,
               cx + eyeSpacing - eyeR, browY + 1, COL_BLACK);

  // Open mouth (scared 'O' shape)
  int mouthY = cy + r / 3;
  int mouthW = r / 5;
  int mouthH = r / 4;
  tft.fillEllipse(cx, mouthY, mouthW, mouthH, COL_BLACK);
  tft.fillEllipse(cx, mouthY, mouthW - 3, mouthH - 3, COL_DARK_RED);

  // Sweat drop
  tft.fillCircle(cx + eyeSpacing + eyeR + 5, eyeY - 3, 3, COL_CYAN);
  tft.fillTriangle(cx + eyeSpacing + eyeR + 5, eyeY - 9,
                   cx + eyeSpacing + eyeR + 3, eyeY - 4,
                   cx + eyeSpacing + eyeR + 7, eyeY - 4, COL_CYAN);

}


// ======================== SLEEPING FACE (Not Connected) ========================

void drawSleepingFace() {
  tft.fillScreen(COL_DARK_BLUE);

  int cx = CENTER_X;
  int cy = CENTER_Y;
  int r  = FACE_R;

  // Face circle — soft blue/gray
  tft.fillCircle(cx, cy, r, 0x4A69);  // Muted blue-gray
  tft.drawCircle(cx, cy, r, COL_WHITE);

  // Closed eyes — curved lines (sleeping)
  int eyeY = cy - r / 6;
  int eyeSpacing = r / 3;
  int eyeW = r / 5;

  // Left closed eye — small arc
  for (int i = -eyeW; i <= eyeW; i++) {
    int y = eyeY + abs(i) / 2;
    tft.drawPixel(cx - eyeSpacing + i, y, COL_BLACK);
    tft.drawPixel(cx - eyeSpacing + i, y + 1, COL_BLACK);
  }

  // Right closed eye — small arc
  for (int i = -eyeW; i <= eyeW; i++) {
    int y = eyeY + abs(i) / 2;
    tft.drawPixel(cx + eyeSpacing + i, y, COL_BLACK);
    tft.drawPixel(cx + eyeSpacing + i, y + 1, COL_BLACK);
  }

  // Small sleepy mouth — flat line with slight droop
  int mouthY = cy + r / 4;
  tft.drawLine(cx - r / 6, mouthY, cx + r / 6, mouthY, COL_BLACK);
  tft.drawLine(cx - r / 6, mouthY + 1, cx + r / 6, mouthY + 1, COL_BLACK);

  // "Zzz" bubbles — drawn as small circles (no text)
  tft.fillCircle(cx + r + 2, cy - r + 8, 4, COL_WHITE);
  tft.fillCircle(cx + r + 14, cy - r - 8, 6, COL_WHITE);
  tft.fillCircle(cx + r + 28, cy - r - 28, 9, COL_WHITE);
}


// ======================== CALIBRATION SCREEN ========================

void drawCalibrationScreen() {
  tft.fillScreen(COL_DARK_BLUE);

  int cx = CENTER_X;
  int cy = CENTER_Y;
  int r  = FACE_R;

  // Pulsing rings
  tft.drawCircle(cx, cy, r, COL_CYAN);
  tft.drawCircle(cx, cy, r + 1, COL_CYAN);
  tft.drawCircle(cx, cy, r + 8, COL_BLUE);
  tft.drawCircle(cx, cy, r + 9, COL_BLUE);
  tft.drawCircle(cx, cy, r + 16, 0x0018);
  tft.drawCircle(cx, cy, r + 17, 0x0018);

  // Inner filled circle
  tft.fillCircle(cx, cy, r - 5, COL_BLUE);
  tft.drawCircle(cx, cy, r - 5, COL_CYAN);

  // Simple person icon
  tft.fillCircle(cx, cy - 10, 8, COL_WHITE);             // Head
  tft.fillRoundRect(cx - 10, cy + 2, 20, 18, 4, COL_WHITE);  // Body

}


// ======================== READY SCREEN ========================

void drawReadyScreen() {
  tft.fillScreen(COL_DARK_GREEN);

  int cx = CENTER_X;
  int cy = CENTER_Y;
  int r  = FACE_R;

  // Large checkmark icon
  tft.fillCircle(cx, cy, r, COL_GREEN);
  tft.drawCircle(cx, cy, r, COL_WHITE);
  tft.drawCircle(cx, cy, r + 1, COL_WHITE);

  // Draw thick checkmark
  int startX = cx - r / 3;
  int midX   = cx - r / 8;
  int endX   = cx + r / 3;
  int startY = cy;
  int midY   = cy + r / 4;
  int endY   = cy - r / 3;

  for (int t = -2; t <= 2; t++) {
    tft.drawLine(startX, startY + t, midX, midY + t, COL_WHITE);
    tft.drawLine(midX, midY + t, endX, endY + t, COL_WHITE);
  }
}
