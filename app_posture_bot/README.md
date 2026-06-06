# PostureBot Android App

The Android app is the brain of the PostureBot system. It runs inference, processes signals, manages state, controls the bot, and persists history.

## Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **Kotlin** 1.9+
- **Minimum SDK** API 26 (Android 8.0)

## Setup

### 1. MediaPipe Model

Place the pose landmarker model in assets before building:

```
app/src/main/assets/pose_landmarker_full.task
```

**Download:**
```
https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task
```

Create the `assets` folder if it doesn't exist:
```bash
mkdir -p app/src/main/assets
# Then download the file to that location
```

### 2. Open in Android Studio

1. File → Open → select `app_posture_bot` folder
2. Sync Project with Gradle Files (Android Studio will create Gradle wrapper if needed)
3. Connect a device or start an emulator
4. Run the app

> **Note:** If MediaPipe imports fail (e.g. `BitmapImageBuilder`, `NormalizedLandmark`), check the [MediaPipe Tasks Vision API](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker) for the correct package names for your library version.

### 3. ESP32 WebSocket URL

The default WebSocket URL is `ws://192.168.4.1:81`. Update this in `MainActivity.kt` to match your ESP32 access point IP and port.

## Architecture

```
com.posturebot.app/
  ui/
    screens/         → Calibration, Live Session, History
    components/      → PostureStateCard, LiveAngleGraph
  data/
    db/              → Room: PostureSample, Session, PostureDao
    datastore/       → CalibrationManager (baseline persistence)
  domain/
    mediapipe/       → PoseInferenceEngine (33 landmarks)
    computation/     → MetricComputer (4 metrics)
    signal/          → RollingAverageSmoother
    statemachine/    → PostureState, PostureStateMachine
  network/
    websocket/       → Esp32StreamClient (frames + commands)
  viewmodel/         → SessionViewModel (orchestration)
```

## Build Order (Verification)

1. ✅ Project setup + folder structure
2. ✅ WebSocket stream receiver
3. ✅ MediaPipe inference
4. ✅ Metric computation
5. ✅ Signal processing (smoothing)
6. ✅ Calibration + State machine
7. ✅ Room DB writes
8. ✅ Output channels (vibration, WebSocket commands)
9. ✅ Live dashboard
10. ✅ History dashboard

## Output Commands to ESP32

| State   | WebSocket Commands   |
|---------|----------------------|
| Good    | `HAPPY`              |
| Warning | `WARNING`, `BUZZ_WARNING` |
| Bad     | `SAD`, `BUZZ_BAD`    |
