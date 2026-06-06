package com.posturebot.app.network.websocket

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * Bidirectional WebSocket client for ESP32-CAM.
 * Receives JPEG frames and sends text commands.
 * Automatically reconnects with exponential backoff on failure.
 */
class Esp32StreamClient(private val url: String) {

    companion object {
        private const val TAG = "Esp32StreamClient"
        private const val MAX_RETRIES = 5
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val NORMAL_CLOSE_CODE = 1000
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // No read timeout for streaming
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS) // Keep-alive pings
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _frameFlow = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 2)
    val frameFlow: SharedFlow<ByteArray> = _frameFlow

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected("Not connected"))
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    @Volatile
    private var retryCount = 0

    @Volatile
    private var shouldReconnect = true

    fun connect() {
        shouldReconnect = true
        retryCount = 0
        doConnect()
    }

    private fun doConnect() {
        Log.d(TAG, "Connecting to $url (attempt ${retryCount + 1})")
        _connectionState.value = if (retryCount > 0) {
            ConnectionState.Reconnecting(retryCount)
        } else {
            ConnectionState.Connecting
        }

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Connected to $url")
                retryCount = 0
                _connectionState.value = ConnectionState.Connected
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                _frameFlow.tryEmit(bytes.toByteArray())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "Text message: $text")
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Connection failed: ${t.message}", t)
                _connectionState.value = ConnectionState.Disconnected(t.message ?: "Unknown error")
                scheduleReconnect()
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: code=$code reason=$reason")
                ws.close(NORMAL_CLOSE_CODE, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Closed: code=$code reason=$reason")
                _connectionState.value = ConnectionState.Disconnected(reason)
                if (code != NORMAL_CLOSE_CODE) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect || retryCount >= MAX_RETRIES) {
            Log.w(TAG, "Giving up reconnection after $retryCount attempts")
            _connectionState.value = ConnectionState.Disconnected("Max retries exceeded")
            return
        }

        retryCount++
        val backoff = (INITIAL_BACKOFF_MS * (1L shl (retryCount - 1).coerceAtMost(4)))
            .coerceAtMost(MAX_BACKOFF_MS)

        Log.d(TAG, "Reconnecting in ${backoff}ms (attempt $retryCount/$MAX_RETRIES)")
        _connectionState.value = ConnectionState.Reconnecting(retryCount)

        scope.launch {
            delay(backoff)
            if (shouldReconnect) {
                doConnect()
            }
        }
    }

    fun sendCommand(cmd: String) {
        webSocket?.send(cmd)
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(NORMAL_CLOSE_CODE, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected("Disconnected by user")
        Log.d(TAG, "Disconnected by user")
    }

    sealed class ConnectionState {
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Reconnecting(val attempt: Int) : ConnectionState()
        data class Disconnected(val reason: String) : ConnectionState()
    }
}
