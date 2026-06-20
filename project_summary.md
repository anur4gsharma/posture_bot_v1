# ASEP2 Posture Monitoring System — Complete Project Summary

> **Purpose of this document:** Give another AI everything it needs to understand, modify, or extend this project with **zero additional exploration**. Every file, protocol, formula, threshold, and data flow is documented below.

---

## 1. What This Project Does

A real-time posture monitoring system that:
1. Captures video from a USB webcam
2. Uses MediaPipe Pose to extract body landmarks
3. Computes posture metrics and compares them against a per-user calibrated baseline
4. Alerts the user through **4 parallel output channels**: an OpenCV laptop window, a TFT display (Arduino UNO), an LCD + buzzer (Arduino Nano), and an Android app via WebSocket

---

## 2. Tech Stack

| Layer | Technology | Language |
|---|---|---|
| **Backend (Brain)** | Python 3 — OpenCV, MediaPipe, `websockets`, PySerial | Python |
| **Android App** | Kotlin — Jetpack Compose, Room DB, OkHttp WebSocket, Navigation Compose | Kotlin |
| **TFT Display** | Arduino UNO + 2.4" ILI9341 TFT shield — MCUFRIEND_kbv + Adafruit GFX | C++ (Arduino) |
| **LCD + Buzzer** | Arduino Nano + 16×2 I2C LCD + active buzzer — LiquidCrystal_I2C | C++ (Arduino) |
| **Protocols** | WebSocket JSON (Python ↔ Android), USB Serial 9600 baud newline-terminated text (Python ↔ Arduinos) | — |

### Python Dependencies (`requirements.txt`)
```
opencv-python
mediapipe
pyserial
websockets
```

---

## 3. Directory Structure

```
ASEP2_Project/
├── posture_monitor/                    # Python backend package (THE BRAIN)
│   ├── __init__.py
│   ├── config.py                       # All tuneable parameters
│   ├── main.py                         # Entry point + state machine + OpenCV loop
│   ├── engine/
│   │   ├── pose_detector.py            # MediaPipe Pose wrapper
│   │   ├── metrics.py                  # Pure math: 6 posture metrics
│   │   └── posture_analyzer.py         # Threshold comparison + sliding window
│   ├── calibration/
│   │   └── calibrator.py              # 5-second baseline collection
│   ├── hardware/
│   │   └── serial_controller.py       # DualSerialController (UNO + Nano)
│   └── server/
│       └── ws_server.py               # Async WebSocket server (background thread)
│
├── uno_tft_display/
│   └── uno_tft_display/
│       └── uno_tft_display.ino         # Arduino UNO firmware (TFT emoji faces)
│
├── nano_lcd_buzzer/
│   └── nano_lcd_buzzer/
│       └── nano_lcd_buzzer.ino         # Arduino Nano firmware (LCD text + buzzer)
│
├── hardware_setup/
│   ├── README.md                       # Full wiring + setup guide
│   ├── WIRING_UNO_TFT.md             # UNO shield pin mapping
│   └── WIRING_NANO_LCD_BUZZER.md      # Nano wiring diagram
│
├── app_posture_bot/                    # Android app (Gradle project)
│   └── app/src/main/java/com/posturebot/app/
│       ├── MainActivity.kt             # NavHost, bottom bar, screen routing
│       ├── PostureBotApp.kt            # Application class
│       ├── viewmodel/
│       │   └── SessionViewModel.kt     # Central orchestrator
│       ├── network/websocket/
│       │   └── PostureStreamClient.kt  # OkHttp WebSocket client
│       ├── data/db/                    # Room DB: Session + PostureSample entities, DAO
│       ├── domain/statemachine/        # PostureState enum
│       └── ui/
│           ├── screens/
│           │   ├── WelcomeScreen.kt         # Enter IP, connect
│           │   ├── CalibrationScreen.kt     # Progress indicator
│           │   ├── LiveSessionScreen.kt     # Real-time metrics, body-part %, history
│           │   ├── SessionReportScreen.kt   # End-of-session summary
│           │   ├── HistoryScreen.kt         # Past sessions list
│           │   └── SessionDetailScreen.kt   # Single session detail view
│           └── theme/                       # Material3 theming
│
├── requirements.txt
└── venv/                               # Python virtual environment
```

---

## 4. How to Run

```bash
# Terminal (Python backend):
cd ASEP2_Project
python -m posture_monitor.main

# Arduino IDE:
# 1. Upload uno_tft_display.ino to Arduino UNO (Board: Arduino UNO)
# 2. Upload nano_lcd_buzzer.ino to Arduino Nano (Board: Arduino Nano, Processor: ATmega328P)

# Android:
# Build & install app_posture_bot via Android Studio
```

---

## 5. Python Backend — Detailed Breakdown

### 5.1 Configuration (`config.py`)

| Parameter | Value | Purpose |
|---|---|---|
| `WEBCAM_INDEX` | `0` | USB webcam device index |
| `SERIAL_PORT_UNO` | `"COM3"` | Arduino UNO serial port |
| `SERIAL_PORT_NANO` | `"COM6"` | Arduino Nano serial port |
| `SERIAL_BAUD_*` | `9600` | Baud rate for both Arduinos |
| `CALIBRATION_SECONDS` | `5` | Duration of calibration phase |
| `BAD_POSTURE_WINDOW` | `30` | Rolling window size (seconds) |
| `BAD_POSTURE_THRESHOLD` | `25` | Bad seconds needed to trigger buzzer |
| `SLOPE_OFFSET` | `0.15` | Shoulder slope tolerance |
| `TILT_OFFSET` | `15.0` | Head tilt tolerance (degrees) |
| `NOSE_SHOULDER_MULTIPLIER` | `0.65` | Slouching threshold (lower = stricter) |
| `SHOULDER_DROP_TOLERANCE` | `15` | Max pixel drop for spine check |
| `WS_HOST` | `"0.0.0.0"` | WebSocket bind address |
| `WS_PORT` | `8765` | WebSocket port |
| `WS_BROADCAST_INTERVAL` | `0.5` | Min seconds between broadcasts |
| `SERIAL_INTERVAL` | `0.3` | Min seconds between serial commands |

Default baseline (used if no person detected during calibration):
```python
{'slope': 0.08, 'tilt': 5.0, 'nose_shoulder': 0.7,
 'shoulder_y_left': 200, 'shoulder_y_right': 200}
```

### 5.2 State Machine (`main.py`)

```
WAITING → CALIBRATING → MONITORING → STOPPED
   ↑______________|          |
   |_________________________|  (user stops)
```

**Main loop per frame:**
1. Check `ws_server.get_pending_command()` for app commands
2. `cap.read()` → `cv2.flip(frame, 1)` (mirror)
3. `detector.detect(frame)` → 7 landmark points or `None`
4. Based on state:
   - **WAITING**: Show "Ready" overlay, broadcast `IDLE`
   - **CALIBRATING**: `compute_metrics()` → `calibrator.add_sample()`, broadcast progress. When complete: create `PostureAnalyzer(baseline)`, transition to MONITORING
   - **MONITORING**: `compute_metrics()` → `analyzer.analyze()` → draw overlays, send serial, broadcast JSON
5. Draw connection indicators (UNO/Nano/App status)
6. `cv2.imshow()` + keyboard handling: `ESC`=quit, `C`=calibrate, `S`=stop

Entry: `python -m posture_monitor.main`

### 5.3 Pose Detection (`pose_detector.py`)

- Wraps `mp.solutions.pose.Pose(min_detection_confidence=0.5, min_tracking_confidence=0.5)`
- Converts BGR→RGB, runs MediaPipe, extracts **7 landmarks**:

| Key | MediaPipe Index | Body Part |
|---|---|---|
| `nose` | 0 | Nose tip |
| `ls` | 11 | Left shoulder |
| `rs` | 12 | Right shoulder |
| `le` | 7 | Left ear |
| `re` | 8 | Right ear |
| `lh` | 23 | Left hip |
| `rh` | 24 | Right hip |

- Landmarks converted from normalized (0–1) → pixel coords: `(int(lm.x * w), int(lm.y * h))`
- Returns `(pts_dict, raw_results)` or `(None, raw_results)` if no person

### 5.4 Metrics Computation (`metrics.py`)

6 metrics computed from landmarks. **No external dependencies** (pure math).

**Metric 1: `forward`** (Forward Head Distance)
```
shoulder_center = midpoint(ls, rs)
shoulder_width = distance(ls, rs)
forward = distance(nose, shoulder_center) / shoulder_width
```
- Higher = head further forward. Typical good: ~0.75
- **NOTE: This metric is computed but NOT checked by the posture analyzer** — it was found to be redundant with SLOUCHING and has been removed from threshold checks.

**Metric 2: `slope`** (Shoulder Slope)
```
slope = |rs_y - ls_y| / (|rs_x - ls_x| + 1e-5)
```
- Higher = more uneven shoulders. Typical good: ~0.08

**Metric 3: `tilt`** (Head Tilt)
```
angle = |atan2(re_y - le_y, re_x - le_x)| in degrees
if angle > 90: angle = 180 - angle
```
- Higher = more tilted head. Typical good: ~5°

**Metric 4: `nose_shoulder`** (Nose-to-Shoulder Perpendicular Distance)
```
line_len = distance(ls, rs)
dist = |cross_product(shoulder_line_vec, ls_to_nose_vec)| / line_len
nose_shoulder = dist / line_len
```
- **Lower = slouching** (nose dropping toward shoulder line). Typical good: ~0.7

**Metric 5 & 6: `shoulder_y_left`, `shoulder_y_right`** (Raw Y-coordinates)
```
shoulder_y_left = ls[1]   # pixel Y of left shoulder
shoulder_y_right = rs[1]  # pixel Y of right shoulder
```
- Used for spine drop detection (Y increases downward in image coords)

### 5.5 Calibration (`calibrator.py`)

1. User sits with good posture for 5 seconds
2. Every frame → `compute_metrics()` → append to sample lists
3. After 5 seconds → average each metric:
   ```python
   baseline = {k: sum(v) / len(v) for k, v in samples.items()}
   ```
4. If no person detected → use `DEFAULT_BASELINE`
5. Calibrated keys: `slope`, `tilt`, `nose_shoulder`, `shoulder_y_left`, `shoulder_y_right`

### 5.6 Posture Analysis (`posture_analyzer.py`)

**Thresholds derived from calibration baseline:**

| Check | Threshold Formula | Trigger Condition | Issue String |
|---|---|---|---|
| Shoulder slope | `baseline.slope + 0.15` | `current > threshold` | `"UNEVEN SHOULDERS"` |
| Head tilt | `baseline.tilt + 15.0°` | `current > threshold` | `"HEAD TILT"` |
| Slouching | `baseline.nose_shoulder × 0.65` | `current < threshold` | `"SLOUCHING"` |
| Spine drop | `baseline.shoulder_y_* + 15px` | `current_y - baseline_y > 15` | `"SPINE NOT STRAIGHT"` |

> **Note:** `forward` metric is computed but NOT checked — it was removed as redundant with SLOUCHING.

**Sliding Window Buzzer Rule (25/30s):**
- A `deque` stores `(timestamp, is_bad)` entries
- Old entries (>30s) are pruned each frame
- Bad seconds calculated by summing time gaps between consecutive bad samples
- Buzzer activates when `bad_seconds >= 25`
- This prevents false alarms from brief movements

**State determination per frame:**
```
if issues AND buzz_active → "ALERT"
elif issues             → "BAD"
else                    → "GOOD"
```

**`PostureResult` dataclass fields:**
- `state`: `"GOOD"` | `"BAD"` | `"ALERT"`
- `is_bad`: bool
- `issues`: list of issue strings
- `metrics`: raw metric dict
- `bad_frame_count`: consecutive bad frames
- `buzz_active`: bool (25/30s rule met)
- `bad_seconds_in_window`: float (rounded to 1 decimal)

### 5.7 Serial Controller (`serial_controller.py`)

**Architecture:** `DualSerialController` manages two `SerialController` instances.

**Rate limiting:** Only sends if command changed OR 0.3 seconds elapsed.

**Graceful fallback:** If pyserial not installed or port unavailable → runs in display-only mode (no crash).

**Protocol (9600 baud, newline-terminated strings):**

| Command | Meaning | Sent To |
|---|---|---|
| `GOOD\n` | Good posture | Both |
| `BAD\n` | Bad posture (no buzzer) | Both |
| `ALERT\n` | Persistent bad posture | Both |
| `SLEEP\n` | Idle / not connected | Both |
| `CAL\n` | Calibrating | Both |
| `READY\n` | Calibration complete | Both |
| `OFF\n` | System shutdown | Both |
| `BUZZ_ON\n` | Activate buzzer | Nano only |
| `BUZZ_OFF\n` | Deactivate buzzer | Nano only |

**`send_posture(state, buzz_active)`** sends state to both, plus explicit `BUZZ_ON`/`BUZZ_OFF` to Nano when buzz state changes.

### 5.8 WebSocket Server (`ws_server.py`)

- Async server on `0.0.0.0:8765` in a background daemon thread
- Uses `asyncio.new_event_loop()` in the thread
- Main thread calls `broadcast(data)` which schedules via `asyncio.run_coroutine_threadsafe()`
- Rate-limited: max 1 broadcast per 0.5s
- App commands → thread-safe `queue.Queue` → `get_pending_command()` (non-blocking)

**JSON sent to app (every 0.5s):**
```json
{
  "type": "posture_update",
  "state": "GOOD" | "BAD" | "ALERT" | "CALIBRATING" | "IDLE" | "STOPPED" | "NO_PERSON",
  "metrics": {"forward": 0.74, "slope": 0.05, "tilt": 3.2, "nose_shoulder": 0.68, "shoulder_y_left": 195, "shoulder_y_right": 198},
  "issues": ["SLOUCHING", "UNEVEN SHOULDERS"],
  "calibration_progress": 0.0–1.0,
  "buzz_active": false,
  "bad_seconds": 12.3,
  "timestamp": 1717600000000
}
```

**JSON received from app:**
```json
{"command": "START_CALIBRATION" | "START_MONITORING" | "STOP_MONITORING" | "STOP"}
```

---

## 6. Arduino Firmware

### 6.1 Arduino UNO — TFT Display (`uno_tft_display.ino`)

- **Libraries:** MCUFRIEND_kbv, Adafruit GFX
- **Shield:** 2.4" ILI9341 TFT — plugs directly onto UNO (all pins)
- **Baud:** 9600, landscape rotation
- **Screen avoids redraws** — only updates when command changes (`lastStatus != cmd`)

| Command | Display |
|---|---|
| `GOOD` | 😊 Happy face — yellow circle on dark green bg, black eyes with white highlights, curved smile, "GOOD POSTURE" text |
| `BAD` / `ALERT` | 😨 Scared face — orange circle on dark red bg, wide white eyes with small pupils, raised eyebrows, O-mouth, sweat drop, "FIX YOUR POSTURE!" text |
| `SLEEP` / `NONE` | 😴 Sleeping face — muted blue-gray circle on dark blue bg, closed eye arcs, flat mouth, cascading "zZZ" text, "NOT CONNECTED" |
| `CAL` | Calibration screen — concentric cyan/blue rings, person silhouette icon, "CALIBRATING" text |
| `READY` | "READY! Monitoring..." on dark green bg |
| `OFF` | "System OFF" in dark gray on black |

### 6.2 Arduino Nano — LCD + Buzzer (`nano_lcd_buzzer.ino`)

- **Libraries:** Wire (built-in), LiquidCrystal_I2C
- **LCD:** 16×2 I2C at address `0x27`
- **Buzzer:** Active buzzer on pin D8
- **Custom LCD characters:** ♥ (heart), ☠ (skull), Zzz (sleep), ✓ (check)

| Command | LCD Line 1 | LCD Line 2 | Buzzer |
|---|---|---|---|
| `GOOD` | `" GOOD POSTURE ♥"` | `"  Keep it up!  "` | OFF (forced off) |
| `BAD` / `ALERT` | `" BAD POSTURE! ☠"` | `"Fix ur posture!"` | Unchanged (controlled by BUZZ_ON/OFF) |
| `SLEEP` / `NONE` | `" NOT CONNECTED "` | `"    Zzz...     "` | OFF (forced off) |
| `CAL` | `" CALIBRATING..."` | `" Sit straight! "` | — |
| `READY` | `"    READY! ✓   "` | `"  Monitoring..."` | — |
| `OFF` | `"  SYSTEM OFF   "` | `"               "` | OFF (forced off) |
| `BUZZ_ON` | (no LCD change) | (no LCD change) | Start beeping: 200ms on / 300ms off |
| `BUZZ_OFF` | (no LCD change) | (no LCD change) | Stop immediately |

**Buzzer is non-blocking** — uses `millis()` timing in `loop()`.

### 6.3 Wiring

**UNO:** TFT shield plugs directly on top — no wires needed.

**Nano:**
| Component | Pin |
|---|---|
| LCD SDA | A4 |
| LCD SCL | A5 |
| LCD VCC | 5V |
| LCD GND | GND |
| Buzzer + | D8 |
| Buzzer – | GND |

---

## 7. Android App

### 7.1 Architecture

```
MainActivity (NavHost + Bottom Bar)
├── WelcomeScreen      — Enter server IP, tap "Connect"
├── LiveSessionScreen  — Real-time posture, metrics, body-part %, state timeline
├── SessionReportScreen — End-of-session summary (duration, good%, alerts, per-part breakdown)
├── HistoryScreen      — List of past sessions
└── SessionDetailScreen — Drill into a single session

SessionViewModel (AndroidViewModel)
├── PostureStreamClient (OkHttp WebSocket → Python backend)
├── Room Database (Session + PostureSample tables)
└── Vibrator (haptic feedback)
```

### 7.2 App Flow

1. **WelcomeScreen** → User enters `ws://<laptop-IP>:8765`, taps **Connect**
   - `connectToBackend(wsUrl)` → opens WebSocket, starts pipeline, sets state to `Idle`
2. **LiveSessionScreen** → User taps **Start Calibration**
   - `requestCalibration()` → sends `START_CALIBRATION` command, creates session in DB
3. Backend calibrates (5s) → sends `CALIBRATING` updates with progress → then `GOOD`
4. **LiveSessionScreen** shows real-time:
   - Current posture state (GOOD/BAD/ALERT)
   - Live metrics (slope, tilt, nose-shoulder, shoulder Y values)
   - Per-body-part good percentages (Shoulders, Head Tilt, Neck, Spine)
   - State history timeline
5. User taps **Stop Analyzing** → `stopAndShowReport()`
   - Sends `STOP_MONITORING`, generates `SessionReport`, navigates to report screen
6. **SessionReportScreen** → Shows duration, overall good%, total alerts, per-body-part breakdown
7. User taps **Return to Home** → `goBackToCalibration()` → full reset to WelcomeScreen

### 7.3 WebSocket Client (`PostureStreamClient.kt`)

- Uses OkHttp WebSocket
- Connects to `ws://<laptop-IP>:8765`
- Exposes `postureUpdateFlow` (Kotlin Flow of `PostureUpdate`)
- Exposes `connectionState` (Connected / Disconnected / Reconnecting)
- `sendCommand(cmd)` → sends `{"command": "<cmd>"}` JSON
- **Manual reconnect only** — no auto-reconnect (prevents conflicts with backend restarts)

### 7.4 SessionViewModel State Mapping

| Backend State String | Android `PostureState` Enum |
|---|---|
| `"GOOD"` | `PostureState.Good` |
| `"BAD"`, `"ALERT"` | `PostureState.Bad` |
| `"WARNING"` | `PostureState.Warning` |
| `"CALIBRATING"` | `PostureState.Calibrating` |
| `"STOPPED"` | `PostureState.Stopped` |
| `"NO_PERSON"`, `"IDLE"` | `PostureState.Idle` |

**Guard:** If current state is `Calibrating` and backend sends `IDLE`, the app stays in `Calibrating` (command may still be in flight).

### 7.5 Body-Part Percentage Tracking (Android-side)

During monitoring frames (GOOD/BAD/WARNING):
- Increments `bodyPartTotalFrames` 
- For each issue present → increments that body part's bad frame count
- Good% = `(total - bad) / total × 100`

| Backend Issue String | Android Body Part Name |
|---|---|
| `"UNEVEN SHOULDERS"` | `"Shoulders"` |
| `"HEAD TILT"` | `"Head Tilt"` |
| `"SLOUCHING"` | `"Neck"` |
| `"SPINE NOT STRAIGHT"` | `"Spine"` |

### 7.6 Haptic Feedback

| State | Vibration |
|---|---|
| Good | None |
| Warning | 200ms single pulse at 50% intensity |
| Bad | Triple pulse: 100ms on, 100ms off, 100ms on |

### 7.7 Room Database

**`sessions` table:**
- `sessionId` (PK, UUID string)
- `startTime`, `endTime` (Long, epoch ms)
- `goodPercent` (Float, nullable)
- `totalAlerts` (Int)

**`posture_samples` table:**
- `id` (auto PK)
- `sessionId` (FK → sessions)
- `timestamp` (Long)
- `forwardHeadOffsetPx`, `neckInclinationDeg`, `headTiltDeg`, `shoulderSymmetryPx` (Float)
- `state` (String: "GOOD"/"BAD"/"WARNING"/"CALIBRATING"/"STOPPED"/"IDLE")

Samples written every 1 second via a periodic coroutine. Session finalized on stop (computes goodPercent and totalAlerts from stored samples).

---

## 8. End-to-End Data Flow (Per Frame)

```
1. USB Webcam → BGR frame
        ↓
2. cv2.flip(frame, 1)  → mirror
        ↓
3. PoseDetector.detect(frame)
   → BGR→RGB → MediaPipe Pose → 33 landmarks → extract 7 key points
        ↓
4. compute_metrics(pts)
   → 6 numbers: forward, slope, tilt, nose_shoulder, shoulder_y_left, shoulder_y_right
        ↓
5. PostureAnalyzer.analyze(metrics)
   → Compare 4 active thresholds (slope, tilt, nose_shoulder, shoulder_drop)
   → Determine issues list
   → Update sliding window (25/30s rule)
   → Return PostureResult(state, issues, metrics, buzz_active, bad_seconds)
        ↓ (4 parallel outputs)
   ┌──────────────┬──────────────┬───────────────────┬──────────────┐
   ↓              ↓              ↓                   ↓
6a. OpenCV     6b. Serial     6c. WebSocket       6d. On-screen
    Window         → UNO          → Android app      metrics overlay
    (laptop)       → Nano         (ws://0.0.0.0:8765)
                    ↓              ↓
               7a. UNO TFT    7b. ViewModel
                   draws emoji     parses JSON
                   face            updates StateFlow
                    ↓              writes to Room DB
               7b. Nano LCD       triggers vibration
                   shows text      computes body-part %
               7c. Nano Buzzer
                   beeps if
                   BUZZ_ON active
```

---

## 9. Key Design Decisions & Gotchas

1. **`forward` metric exists but is NOT checked** — it was found redundant with `SLOUCHING` (nose_shoulder) and was removed from threshold checks in `posture_analyzer.py`. The metric is still computed for display/logging but doesn't trigger any issue.

2. **Buzzer is decoupled from display state** — The Nano receives both display commands (GOOD/BAD/etc.) and independent buzzer commands (BUZZ_ON/BUZZ_OFF). The buzzer only activates via the 25/30s sliding window rule, NOT immediately when posture goes bad.

3. **WebSocket is NOT the control bus for serial** — Serial commands are sent directly from the main loop, not triggered by WebSocket events.

4. **No auto-reconnect on Android** — Deliberate. Auto-reconnect caused race conditions with backend restarts. User manually taps "Reconnect" when connection drops.

5. **Calibration is decoupled from connection** — User first connects (WelcomeScreen), then starts calibration (LiveSessionScreen). These are separate steps.

6. **OpenCV text is black (`(0, 0, 0)`)** — All overlay text on the processing display uses black for visibility on colored banners.

7. **Serial uses 9600 baud** (not 115200) — Both Arduinos use 9600 for reliability with cheap USB-serial chips (CH340).

8. **`cv2.waitKey(1)` runs in the main thread** — The OpenCV window must be on the main thread. WebSocket server and serial are on background threads.

9. **Image coordinates:** Y increases downward. A shoulder "dropping" means its Y value *increases* above the baseline Y.
