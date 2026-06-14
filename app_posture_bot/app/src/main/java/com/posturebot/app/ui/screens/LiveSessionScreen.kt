package com.posturebot.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.posturebot.app.domain.statemachine.PostureState
import com.posturebot.app.network.websocket.PostureStreamClient
import com.posturebot.app.ui.components.PostureStateCard
import com.posturebot.app.ui.components.PostureTimeGraph

@Composable
fun LiveSessionScreen(
    state: PostureState,
    metrics: Map<String, Float>?,
    issues: List<String>,
    bodyPartPercentages: Map<String, Float> = emptyMap(),
    calibrationProgress: Float = 0f,
    postureStateHistory: List<Pair<Long, String>> = emptyList(),
    connectionState: PostureStreamClient.ConnectionState? = null,
    onFinishCalibration: (() -> Unit)? = null,
    onStopAnalyzing: (() -> Unit)? = null,
    onStartCalibration: (() -> Unit)? = null,
    onReconnect: (() -> Unit)? = null,
    onGoBackToCalibration: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── Connection indicator ─────────────────────────────────────
        connectionState?.let { connState ->
            ConnectionStatusBar(connState)
            Spacer(modifier = Modifier.height(8.dp))

            // Show "Go Back to Calibration" when retries exhausted
            if (connState is PostureStreamClient.ConnectionState.RetriesExhausted) {
                Button(
                    onClick = { onGoBackToCalibration?.invoke() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        "Go Back to Calibration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            // Show Reconnect button when disconnected (but not retries exhausted)
            else if (connState is PostureStreamClient.ConnectionState.Disconnected) {
                Button(
                    onClick = { onReconnect?.invoke() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text(
                        "Reconnect",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ── Posture state card ───────────────────────────────────────
        PostureStateCard(state = state)

        // ── Issues display ───────────────────────────────────────────
        if (issues.isNotEmpty() && state == PostureState.Bad) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Issues Detected",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    issues.forEach { issue ->
                        Text(
                            "• $issue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // ── Calibration section ──────────────────────────────────────
        if (state is PostureState.Calibrating) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Calibration", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Sit with good posture for a few seconds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { calibrationProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "%.0f%%".format(calibrationProgress * 100),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (calibrationProgress >= 1f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { onFinishCalibration?.invoke() }) {
                            Text("Finish Calibration")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Posture-vs-time graph ─────────────────────────────────────
        if (postureStateHistory.size >= 2) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PostureTimeGraph(
                        history = postureStateHistory,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Body Part Health (percentage bars) ───────────────────────
        if (bodyPartPercentages.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Body Part Health",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Good posture % for each body part",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    bodyPartPercentages.forEach { (bodyPart, percentage) ->
                        BodyPartPercentageRow(bodyPart, percentage)
                    }
                }
            }
        } else if (state != PostureState.Calibrating) {
            Text(
                "Waiting for posture data…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Start Calibration button (when connected but idle/stopped) ──
        if (state == PostureState.Idle || state == PostureState.Stopped) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onStartCalibration?.invoke() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    "Start Calibration",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        // ── Stop Analyzing button (only during active monitoring) ────
        if (state == PostureState.Good || state == PostureState.Bad || state == PostureState.Warning) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onStopAnalyzing?.invoke() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    "Stop Analyzing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusBar(state: PostureStreamClient.ConnectionState) {
    val (text, color) = when (state) {
        PostureStreamClient.ConnectionState.Connecting ->
            "Connecting to laptop…" to MaterialTheme.colorScheme.tertiary
        PostureStreamClient.ConnectionState.Connected ->
            "Connected" to MaterialTheme.colorScheme.primary
        is PostureStreamClient.ConnectionState.Reconnecting ->
            "Reconnecting… (${state.attempt}/${state.maxAttempts})" to MaterialTheme.colorScheme.tertiary
        is PostureStreamClient.ConnectionState.Disconnected ->
            "Disconnected: ${state.reason}" to MaterialTheme.colorScheme.error
        is PostureStreamClient.ConnectionState.RetriesExhausted ->
            "Connection failed — all retries exhausted" to MaterialTheme.colorScheme.error
    }
    Text(
        text = "● $text",
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun BodyPartPercentageRow(bodyPart: String, percentage: Float) {
    val clampedPct = percentage.coerceIn(0f, 100f)
    val barColor = when {
        clampedPct >= 80f -> Color(0xFF16A34A) // Green
        clampedPct >= 50f -> Color(0xFFD97706) // Yellow/amber
        else -> Color(0xFFDC2626)              // Red
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(bodyPart, style = MaterialTheme.typography.bodyMedium)
            Text(
                "%.0f%% good".format(clampedPct),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = barColor
            )
        }
        LinearProgressIndicator(
            progress = { clampedPct / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = barColor,
            trackColor = barColor.copy(alpha = 0.15f)
        )
    }
}
