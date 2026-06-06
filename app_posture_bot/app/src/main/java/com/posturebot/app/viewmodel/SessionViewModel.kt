package com.posturebot.app.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.posturebot.app.data.db.PostureDao
import com.posturebot.app.data.db.PostureSample
import com.posturebot.app.data.db.Session
import com.posturebot.app.domain.statemachine.PostureState
import com.posturebot.app.network.websocket.PostureStreamClient
import com.posturebot.app.network.websocket.PostureUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Orchestrates the posture monitoring pipeline:
 * WebSocket ← Python backend → posture updates → state machine → DB writes.
 *
 * The Python backend handles all webcam + MediaPipe processing.
 * This ViewModel receives posture state/metrics over WebSocket
 * and manages the Android-side UI state, session storage, and haptic feedback.
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SessionViewModel"
        const val DEFAULT_WS_URL = "ws://192.168.1.100:8765"
        private const val WRITE_INTERVAL_MS = 1_000L
        private const val ANGLE_HISTORY_MAX = 120   // ≈2 min at 1 sample/s
    }

    private val context: Context = application.applicationContext
    private var streamClient: PostureStreamClient? = null
    private var db: PostureDao? = null

    // ── Public state flows ───────────────────────────────────────────
    private val _stateFlow = MutableStateFlow<PostureState>(PostureState.Idle)
    val stateFlow: StateFlow<PostureState> = _stateFlow.asStateFlow()

    private val _connectionState = MutableStateFlow<PostureStreamClient.ConnectionState>(
        PostureStreamClient.ConnectionState.Disconnected("Not started")
    )
    val connectionState: StateFlow<PostureStreamClient.ConnectionState> = _connectionState.asStateFlow()

    private val _calibrationProgress = MutableStateFlow(0f)
    val calibrationProgress: StateFlow<Float> = _calibrationProgress.asStateFlow()

    // Live metrics for display (forward, slope, tilt, nose_shoulder, torso)
    private val _metricsText = MutableStateFlow<Map<String, Float>?>(null)
    val metricsText: StateFlow<Map<String, Float>?> = _metricsText.asStateFlow()

    private val _issuesFlow = MutableStateFlow<List<String>>(emptyList())
    val issuesFlow: StateFlow<List<String>> = _issuesFlow.asStateFlow()

    private val _postureStateHistory = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val postureStateHistory: StateFlow<List<Pair<Long, String>>> = _postureStateHistory.asStateFlow()

    // ── Body-part percentage tracking (computed on Android side) ─────
    // Maps body part name → percentage of frames with GOOD posture for that part
    private val _bodyPartPercentages = MutableStateFlow<Map<String, Float>>(emptyMap())
    val bodyPartPercentages: StateFlow<Map<String, Float>> = _bodyPartPercentages.asStateFlow()

    // Internal counters: total frames and good frames per body part
    private val bodyPartNames = listOf("Head Forward", "Shoulders", "Head Tilt", "Neck", "Spine")
    // Maps issue string from backend → body part display name
    private val issueToBodyPart = mapOf(
        "FORWARD HEAD" to "Head Forward",
        "UNEVEN SHOULDERS" to "Shoulders",
        "HEAD TILT" to "Head Tilt",
        "SLOUCHING" to "Neck",
        "SPINE NOT STRAIGHT" to "Spine"
    )
    private var bodyPartTotalFrames = 0
    private val bodyPartBadFrames = mutableMapOf<String, Int>().apply {
        bodyPartNames.forEach { put(it, 0) }
    }

    // ── Internal state ───────────────────────────────────────────────
    private var sessionId: String? = null
    private var pipelineJob: Job? = null
    private var writeJob: Job? = null
    private var currentWsUrl: String = DEFAULT_WS_URL
    private var calibrationCommandJob: Job? = null

    @Volatile private var lastState: PostureState = PostureState.Idle
    @Volatile private var lastUpdate: PostureUpdate? = null

    // ── API ──────────────────────────────────────────────────────────

    fun initialize(dao: PostureDao) {
        this.db = dao
    }

    /**
     * Called when the user taps "Start Calibration" on the welcome screen.
     * Connects to the Python backend and sends the calibration command
     * once the WebSocket is actually connected.
     */
    fun beginCalibration(wsUrl: String = DEFAULT_WS_URL) {
        currentWsUrl = wsUrl
        connect(wsUrl)
        startSession()
        // Wait for connection before sending the calibration command
        calibrationCommandJob?.cancel()
        calibrationCommandJob = viewModelScope.launch {
            _connectionState.first { it == PostureStreamClient.ConnectionState.Connected }
            streamClient?.sendCommand("START_CALIBRATION")
            Log.i(TAG, "START_CALIBRATION sent after connection established")
        }
    }

    /**
     * Called when the user taps "Start Calibration" on the live screen
     * (already connected to the backend).
     */
    fun requestCalibration() {
        _stateFlow.value = PostureState.Calibrating
        _calibrationProgress.value = 0f
        // Reset body-part counters for new calibration
        bodyPartTotalFrames = 0
        bodyPartBadFrames.keys.forEach { bodyPartBadFrames[it] = 0 }
        _bodyPartPercentages.value = emptyMap()
        // Start a new session if we don't have one
        if (sessionId == null) {
            startSession()
        }
        streamClient?.sendCommand("START_CALIBRATION")
        Log.i(TAG, "START_CALIBRATION sent from live screen")
    }

    /**
     * Called when the user taps "Stop Monitoring" on the live screen.
     * Tells the Python backend to stop and ends the local session,
     * but keeps the WebSocket connection alive for future commands.
     */
    fun stopMonitoring() {
        streamClient?.sendCommand("STOP_MONITORING")
        // Cancel data pipelines
        pipelineJob?.cancel()
        writeJob?.cancel()
        // Finalize session in DB
        val sid = sessionId
        val dao = db
        if (sid != null && dao != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val total = dao.getTotalCount(sid)
                    val good = dao.getGoodCount(sid)
                    val alerts = dao.getAlertCount(sid)
                    dao.getSession(sid)?.let { session ->
                        dao.updateSession(
                            session.copy(
                                endTime = System.currentTimeMillis(),
                                goodPercent = if (total > 0) 100f * good / total else null,
                                totalAlerts = alerts
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error finalizing session $sid", e)
                }
            }
        }
        sessionId = null
        _stateFlow.value = PostureState.Stopped
    }

    /**
     * Reconnect to the Python backend after a disconnection.
     * Re-creates the WebSocket connection and restarts the data pipeline.
     */
    fun reconnect() {
        connect(currentWsUrl)
        startPipeline()
        _stateFlow.value = PostureState.Idle
    }

    fun connect(wsUrl: String = DEFAULT_WS_URL) {
        streamClient?.disconnect()
        streamClient = PostureStreamClient(wsUrl).also { client ->
            client.connect()
            viewModelScope.launch {
                client.connectionState.collect { connState ->
                    _connectionState.value = connState
                }
            }
        }
    }

    fun startSession() {
        val sid = UUID.randomUUID().toString()
        sessionId = sid
        _stateFlow.value = PostureState.Calibrating
        lastState = PostureState.Calibrating
        _postureStateHistory.value = emptyList()
        _calibrationProgress.value = 0f

        // Reset body-part counters for new session
        bodyPartTotalFrames = 0
        bodyPartBadFrames.keys.forEach { bodyPartBadFrames[it] = 0 }
        _bodyPartPercentages.value = emptyMap()

        db?.let { dao ->
            viewModelScope.launch(Dispatchers.IO) {
                dao.insertSession(Session(sessionId = sid, startTime = System.currentTimeMillis()))
            }
        }

        startPipeline()
        startPeriodicWrites()
    }

    fun endSession() {
        pipelineJob?.cancel()
        writeJob?.cancel()
        streamClient?.sendCommand("STOP")

        val sid = sessionId ?: return
        val dao = db ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val total = dao.getTotalCount(sid)
                val good = dao.getGoodCount(sid)
                val alerts = dao.getAlertCount(sid)
                dao.getSession(sid)?.let { session ->
                    dao.updateSession(
                        session.copy(
                            endTime = System.currentTimeMillis(),
                            goodPercent = if (total > 0) 100f * good / total else null,
                            totalAlerts = alerts
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing session $sid", e)
            }
        }

        streamClient?.disconnect()
        sessionId = null
    }

    override fun onCleared() {
        super.onCleared()
        endSession()
    }

    // ── Pipeline ─────────────────────────────────────────────────────

    private fun startPipeline() {
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            val client = streamClient ?: return@launch
            client.postureUpdateFlow.collect { update ->
                processPostureUpdate(update)
            }
        }
    }

    private fun processPostureUpdate(update: PostureUpdate) {
        lastUpdate = update

        // Update calibration progress
        _calibrationProgress.value = update.calibrationProgress

        // Update metrics for display
        update.metrics?.let { m ->
            _metricsText.value = mapOf(
                "Forward head" to m.forward,
                "Shoulder slope" to m.slope,
                "Head tilt" to m.tilt,
                "Nose-shoulder" to m.noseToShoulder,
                "Torso length" to m.torso
            )
        }

        // Update issues
        _issuesFlow.value = update.issues

        // Map string state to PostureState enum
        val rawNewState = when (update.state) {
            "GOOD" -> PostureState.Good
            "BAD", "ALERT" -> PostureState.Bad
            "WARNING" -> PostureState.Warning
            "CALIBRATING" -> PostureState.Calibrating
            "STOPPED" -> PostureState.Stopped
            "NO_PERSON" -> PostureState.Idle
            else -> PostureState.Idle
        }

        // Don't let backend's IDLE override our Calibrating state —
        // the calibration command may still be in flight
        val newState = if (_stateFlow.value == PostureState.Calibrating && rawNewState == PostureState.Idle) {
            PostureState.Calibrating
        } else {
            rawNewState
        }

        // Handle calibration completion
        if (_stateFlow.value == PostureState.Calibrating &&
            newState != PostureState.Calibrating &&
            update.calibrationProgress >= 1f) {
            Log.i(TAG, "Calibration complete — transitioning to monitoring")
        }

        // ── Body-part percentage tracking (Android-side computation) ──
        // Only track during active monitoring (GOOD, BAD, ALERT, WARNING)
        if (newState == PostureState.Good || newState == PostureState.Bad || newState == PostureState.Warning) {
            bodyPartTotalFrames++
            // For each body part, check if its corresponding issue is present
            // If the issue is NOT present → that body part is good for this frame
            for ((issue, bodyPart) in issueToBodyPart) {
                if (issue in update.issues) {
                    bodyPartBadFrames[bodyPart] = (bodyPartBadFrames[bodyPart] ?: 0) + 1
                }
            }
            // Compute percentages: good% = (total - bad) / total * 100
            if (bodyPartTotalFrames > 0) {
                _bodyPartPercentages.value = bodyPartNames.associateWith { part ->
                    val bad = bodyPartBadFrames[part] ?: 0
                    ((bodyPartTotalFrames - bad).toFloat() / bodyPartTotalFrames * 100f)
                }
            }
        }

        // Track state changes for history graph
        if (newState != lastState && newState != PostureState.Calibrating &&
            newState != PostureState.Idle && newState != PostureState.Stopped) {
            val label = when (newState) {
                PostureState.Good -> "GOOD"
                PostureState.Warning -> "WARNING"
                PostureState.Bad -> "BAD"
                else -> return
            }
            appendPostureStateHistory(label)
            fireOutputs(newState)
        }

        // Handle STOPPED from backend — end session and notify UI
        if (newState == PostureState.Stopped && lastState != PostureState.Stopped) {
            Log.i(TAG, "Backend stopped monitoring — ending session")
            pipelineJob?.cancel()
            writeJob?.cancel()
            val sid = sessionId
            val dao = db
            if (sid != null && dao != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val total = dao.getTotalCount(sid)
                        val good = dao.getGoodCount(sid)
                        val alerts = dao.getAlertCount(sid)
                        dao.getSession(sid)?.let { session ->
                            dao.updateSession(
                                session.copy(
                                    endTime = System.currentTimeMillis(),
                                    goodPercent = if (total > 0) 100f * good / total else null,
                                    totalAlerts = alerts
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error finalizing session $sid", e)
                    }
                }
            }
            streamClient?.disconnect()
            sessionId = null
        }

        lastState = newState
        _stateFlow.value = newState
    }

    private fun appendPostureStateHistory(label: String) {
        val current = _postureStateHistory.value.toMutableList()
        current.add(System.currentTimeMillis() to label)
        if (current.size > 300) current.removeAt(0)
        _postureStateHistory.value = current
    }

    // ── Outputs ──────────────────────────────────────────────────────

    private fun fireOutputs(state: PostureState) {
        val vibrator = getVibrator()

        when (state) {
            PostureState.Good -> {
                // No vibration for good posture
            }
            PostureState.Warning -> {
                vibrator?.vibrate(VibrationEffect.createOneShot(200, 128))
            }
            PostureState.Bad -> {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100), -1)
                )
            }
            else -> { /* no output */ }
        }
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // ── Periodic DB writes ───────────────────────────────────────────

    private fun startPeriodicWrites() {
        writeJob?.cancel()
        writeJob = viewModelScope.launch {
            while (true) {
                delay(WRITE_INTERVAL_MS)
                writeSample()
            }
        }
    }

    private suspend fun writeSample() {
        val sid = sessionId ?: return
        val update = lastUpdate ?: return
        val metrics = update.metrics ?: return
        val state = _stateFlow.value
        val dao = db ?: return

        withContext(Dispatchers.IO) {
            try {
                dao.insertSample(
                    PostureSample(
                        sessionId = sid,
                        timestamp = System.currentTimeMillis(),
                        forwardHeadOffsetPx = metrics.forward,
                        neckInclinationDeg = metrics.tilt,
                        headTiltDeg = metrics.slope,
                        shoulderSymmetryPx = metrics.noseToShoulder,
                        state = when (state) {
                            PostureState.Good -> "GOOD"
                            PostureState.Warning -> "WARNING"
                            PostureState.Bad -> "BAD"
                            PostureState.Calibrating -> "CALIBRATING"
                            PostureState.Stopped -> "STOPPED"
                            PostureState.Idle -> "IDLE"
                        }
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error writing sample", e)
            }
        }
    }
}
