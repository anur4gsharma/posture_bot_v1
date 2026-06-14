package com.posturebot.app.network.websocket

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

/**
 * Bidirectional WebSocket client for the Python posture backend.
 * Receives JSON posture updates and sends text commands.
 *
 * Auto-reconnects up to [MAX_RECONNECT_ATTEMPTS] times on failure,
 * then emits [ConnectionState.RetriesExhausted].
 */
class PostureStreamClient(private val url: String) {

    companion object {
        private const val TAG = "PostureStreamClient"
        private const val NORMAL_CLOSE_CODE = 1000
        private const val MAX_RECONNECT_ATTEMPTS = 2
        private const val RECONNECT_DELAY_MS = 2000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // No read timeout for streaming
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS) // Keep-alive pings
        .build()

    private var webSocket: WebSocket? = null

    // Emits parsed posture updates from the Python backend
    private val _postureUpdateFlow = MutableSharedFlow<PostureUpdate>(replay = 0, extraBufferCapacity = 5)
    val postureUpdateFlow: SharedFlow<PostureUpdate> = _postureUpdateFlow

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected("Not connected"))
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    @Volatile
    private var isConnecting = false

    @Volatile
    private var isUserDisconnect = false

    @Volatile
    private var reconnectAttempt = 0

    private var reconnectTimer: Timer? = null

    fun connect() {
        isUserDisconnect = false
        reconnectAttempt = 0
        if (isConnecting) return
        isConnecting = true
        doConnect()
    }

    private fun doConnect() {
        Log.d(TAG, "Connecting to $url")
        _connectionState.value = if (reconnectAttempt > 0) {
            ConnectionState.Reconnecting(reconnectAttempt, MAX_RECONNECT_ATTEMPTS)
        } else {
            ConnectionState.Connecting
        }

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Connected to $url")
                isConnecting = false
                reconnectAttempt = 0
                _connectionState.value = ConnectionState.Connected
            }

            override fun onMessage(ws: WebSocket, text: String) {
                // Parse JSON posture updates from the Python backend
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type", "")
                    if (type == "posture_update") {
                        val metricsJson = json.optJSONObject("metrics")
                        val metrics = if (metricsJson != null) {
                            MetricsData(
                                forward = metricsJson.optDouble("forward", 0.0).toFloat(),
                                slope = metricsJson.optDouble("slope", 0.0).toFloat(),
                                tilt = metricsJson.optDouble("tilt", 0.0).toFloat(),
                                noseToShoulder = metricsJson.optDouble("nose_shoulder", 0.0).toFloat(),
                                shoulderYLeft = metricsJson.optDouble("shoulder_y_left", 0.0).toFloat(),
                                shoulderYRight = metricsJson.optDouble("shoulder_y_right", 0.0).toFloat()
                            )
                        } else null

                        val issuesArray = json.optJSONArray("issues")
                        val issues = mutableListOf<String>()
                        if (issuesArray != null) {
                            for (i in 0 until issuesArray.length()) {
                                issues.add(issuesArray.getString(i))
                            }
                        }

                        val update = PostureUpdate(
                            type = type,
                            state = json.optString("state", "IDLE"),
                            metrics = metrics,
                            issues = issues,
                            calibrationProgress = json.optDouble("calibration_progress", 0.0).toFloat(),
                            timestamp = json.optLong("timestamp", System.currentTimeMillis())
                        )

                        _postureUpdateFlow.tryEmit(update)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message: $text", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Connection failed: ${t.message}", t)
                isConnecting = false
                handleDisconnection(t.message ?: "Unknown error")
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: code=$code reason=$reason")
                ws.close(NORMAL_CLOSE_CODE, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Closed: code=$code reason=$reason")
                isConnecting = false
                handleDisconnection(reason.ifEmpty { "Connection closed" })
            }
        })
    }

    private fun handleDisconnection(reason: String) {
        if (isUserDisconnect) {
            _connectionState.value = ConnectionState.Disconnected("Disconnected by user")
            return
        }

        if (reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempt++
            Log.i(TAG, "Auto-reconnecting in ${RECONNECT_DELAY_MS}ms (attempt $reconnectAttempt/$MAX_RECONNECT_ATTEMPTS)")
            _connectionState.value = ConnectionState.Reconnecting(reconnectAttempt, MAX_RECONNECT_ATTEMPTS)

            reconnectTimer?.cancel()
            reconnectTimer = Timer().apply {
                schedule(object : TimerTask() {
                    override fun run() {
                        isConnecting = true
                        doConnect()
                    }
                }, RECONNECT_DELAY_MS)
            }
        } else {
            Log.w(TAG, "All reconnect attempts exhausted")
            _connectionState.value = ConnectionState.RetriesExhausted(reason)
        }
    }

    /** Send a JSON command to the Python backend. */
    fun sendCommand(cmd: String) {
        val json = JSONObject().put("command", cmd).toString()
        webSocket?.send(json)
    }

    fun disconnect() {
        isUserDisconnect = true
        isConnecting = false
        reconnectTimer?.cancel()
        reconnectTimer = null
        reconnectAttempt = 0
        webSocket?.close(NORMAL_CLOSE_CODE, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected("Disconnected by user")
        Log.d(TAG, "Disconnected by user")
    }

    sealed class ConnectionState {
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState()
        data class Disconnected(val reason: String) : ConnectionState()
        data class RetriesExhausted(val reason: String) : ConnectionState()
    }
}
