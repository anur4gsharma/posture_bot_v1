package com.posturebot.app.network.websocket

/**
 * PostureUpdate — Data model for JSON messages from the Python backend.
 *
 * Example JSON:
 * {
 *   "type": "posture_update",
 *   "state": "GOOD",
 *   "metrics": {"forward": 0.72, "slope": 0.05, "tilt": 3.2, "nose_shoulder": 0.68, "shoulder_y_left": 210, "shoulder_y_right": 212},
 *   "issues": [],
 *   "calibration_progress": 1.0,
 *   "timestamp": 1716735290000
 * }
 */
data class PostureUpdate(
    val type: String,
    val state: String,          // GOOD, BAD, ALERT, CALIBRATING, NO_PERSON, IDLE, STOPPED
    val metrics: MetricsData?,
    val issues: List<String>,
    val calibrationProgress: Float,
    val timestamp: Long
)

data class MetricsData(
    val forward: Float,
    val slope: Float,
    val tilt: Float,
    val noseToShoulder: Float,
    val shoulderYLeft: Float,
    val shoulderYRight: Float
)

