package com.posturebot.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

private val GoodColor    = Color(0xFF16A34A)
private val WarningColor = Color(0xFFD97706)
private val BadColor     = Color(0xFFDC2626)

/**
 * Draws a posture-state timeline graph.
 * Y-axis: Good (top) → Warning (middle) → Bad (bottom).
 * Line color changes with the state at each point.
 *
 * @param history list of (timestamp-ms, state-label) pairs in chronological order.
 */
@Composable
fun PostureTimeGraph(
    history: List<Pair<Long, String>>,
    modifier: Modifier = Modifier
) {
    if (history.size < 2) {
        Text(
            "Collecting posture data…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(modifier = modifier) {
        Text(
            "Posture Over Time",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val leftPadding = 60f
            val rightPadding = 16f
            val topPadding = 12f
            val bottomPadding = 12f
            val chartWidth = size.width - leftPadding - rightPadding
            val chartHeight = size.height - topPadding - bottomPadding

            // Y positions for each state band
            val yGood    = topPadding
            val yWarning = topPadding + chartHeight / 2f
            val yBad     = topPadding + chartHeight

            // Draw Y-axis labels
            drawYLabels(leftPadding, yGood, yWarning, yBad, labelColor)

            // Draw faint horizontal guide lines
            val guideColor = labelColor.copy(alpha = 0.15f)
            listOf(yGood, yWarning, yBad).forEach { y ->
                drawLine(guideColor, Offset(leftPadding, y), Offset(size.width - rightPadding, y))
            }

            // Map timestamps to X
            val tMin = history.first().first
            val tMax = history.last().first
            val tRange = (tMax - tMin).coerceAtLeast(1L)

            fun xOf(ts: Long) = leftPadding + ((ts - tMin).toFloat() / tRange) * chartWidth
            fun yOf(state: String) = when (state) {
                "GOOD"    -> yGood
                "WARNING" -> yWarning
                else      -> yBad
            }
            fun colorOf(state: String) = when (state) {
                "GOOD"    -> GoodColor
                "WARNING" -> WarningColor
                else      -> BadColor
            }

            // Draw line segments colored by state
            for (i in 1 until history.size) {
                val (t0, s0) = history[i - 1]
                val (t1, s1) = history[i]
                val segColor = colorOf(s0)

                drawLine(
                    color = segColor,
                    start = Offset(xOf(t0), yOf(s0)),
                    end = Offset(xOf(t1), yOf(s1)),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }

            // Draw dots at each data point
            history.forEach { (ts, state) ->
                drawCircle(
                    color = colorOf(state),
                    radius = 5f,
                    center = Offset(xOf(ts), yOf(state))
                )
            }
        }
    }
}

private fun DrawScope.drawYLabels(
    x: Float,
    yGood: Float,
    yWarning: Float,
    yBad: Float,
    color: Color
) {
    val paint = android.graphics.Paint().apply {
        this.color = color.hashCode()
        textSize = 28f
        isAntiAlias = true
    }

    drawContext.canvas.nativeCanvas.apply {
        drawText("Good", 4f, yGood + 10f, paint)
        drawText("Warn", 4f, yWarning + 10f, paint)
        drawText("Bad", 4f, yBad + 10f, paint)
    }
}
