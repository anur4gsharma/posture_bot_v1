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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.posturebot.app.viewmodel.SessionViewModel

/**
 * Session report screen shown after the user taps "Stop Analyzing".
 * Displays session duration, overall good posture %, total alerts,
 * and per-body-part breakdown.
 */
@Composable
fun SessionReportScreen(
    report: SessionViewModel.SessionReport,
    onReturnToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationSeconds = report.durationMs / 1000
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val durationText = "%02d:%02d".format(minutes, seconds)

    val overallColor = when {
        report.goodPercent >= 80f -> Color(0xFF16A34A)
        report.goodPercent >= 50f -> Color(0xFFD97706)
        else -> Color(0xFFDC2626)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Title ────────────────────────────────────────────────────
        Text(
            text = "📊",
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Session Report",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Here's how you did",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Summary stats ────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Duration
                StatRow(label = "Duration", value = durationText)

                // Good posture %
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Good Posture",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "%.0f%%".format(report.goodPercent),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = overallColor
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (report.goodPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = overallColor,
                    trackColor = overallColor.copy(alpha = 0.15f)
                )

                // Total alerts
                Spacer(modifier = Modifier.height(8.dp))
                StatRow(label = "Total Alerts", value = "${report.totalAlerts}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Body part breakdown ──────────────────────────────────────
        if (report.bodyPartBreakdown.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Body Part Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Good posture % for each area",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    report.bodyPartBreakdown.forEach { (bodyPart, percentage) ->
                        ReportBodyPartRow(bodyPart, percentage)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Return to Home button ────────────────────────────────────
        Button(
            onClick = onReturnToHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                "Return to Home",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ReportBodyPartRow(bodyPart: String, percentage: Float) {
    val clampedPct = percentage.coerceIn(0f, 100f)
    val barColor = when {
        clampedPct >= 80f -> Color(0xFF16A34A) // Green
        clampedPct >= 50f -> Color(0xFFD97706) // Amber
        else -> Color(0xFFDC2626)              // Red
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(bodyPart, style = MaterialTheme.typography.bodyMedium)
            Text(
                "%.0f%%".format(clampedPct),
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
