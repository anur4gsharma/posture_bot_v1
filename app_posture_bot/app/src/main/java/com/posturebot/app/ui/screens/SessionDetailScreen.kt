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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.posturebot.app.data.db.PostureDao
import com.posturebot.app.data.db.PostureSample
import com.posturebot.app.data.db.Session
import com.posturebot.app.ui.components.PostureTimeGraph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Detail screen for a single session.
 * Shows session info, posture score breakdown, and a posture-vs-time graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    session: Session,
    dao: PostureDao,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val samples by dao.getSamplesForSession(session.sessionId)
        .collectAsState(initial = emptyList())

    val dateFormat = SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault())

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Session Details") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── Session Info Card ────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = dateFormat.format(Date(session.startTime)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Duration
                    session.endTime?.let { end ->
                        val durationMs = end - session.startTime
                        val mins = TimeUnit.MILLISECONDS.toMinutes(durationMs)
                        val secs = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
                        InfoRow("Duration", "${mins}m ${secs}s")
                    }

                    // Posture score
                    session.goodPercent?.let { pct ->
                        InfoRow("Posture Score", "%.0f%%".format(pct))
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = when {
                                pct >= 80 -> Color(0xFF16A34A)  // Green
                                pct >= 50 -> Color(0xFFD97706)  // Yellow
                                else -> Color(0xFFDC2626)        // Red
                            }
                        )
                    }

                    // Alert count
                    session.totalAlerts?.let { alerts ->
                        Spacer(modifier = Modifier.height(4.dp))
                        InfoRow("Alerts", "$alerts")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Posture Breakdown Card ───────────────────────────
            if (samples.isNotEmpty()) {
                val goodCount = samples.count { it.state == "GOOD" }
                val warningCount = samples.count { it.state == "WARNING" }
                val badCount = samples.count { it.state == "BAD" }
                val total = samples.size.toFloat().coerceAtLeast(1f)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Posture Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        BreakdownBar("Good", goodCount, total, Color(0xFF16A34A))
                        BreakdownBar("Warning", warningCount, total, Color(0xFFD97706))
                        BreakdownBar("Bad", badCount, total, Color(0xFFDC2626))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Posture vs Time Graph ────────────────────────────
            if (samples.size >= 2) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val history = samples
                            .filter { it.state in listOf("GOOD", "WARNING", "BAD") }
                            .map { it.timestamp to it.state }

                        if (history.size >= 2) {
                            PostureTimeGraph(
                                history = history,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                "Not enough data for graph",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BreakdownBar(label: String, count: Int, total: Float, color: Color) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "%.0f%%".format(count / total * 100),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        LinearProgressIndicator(
            progress = { count / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color
        )
    }
}
