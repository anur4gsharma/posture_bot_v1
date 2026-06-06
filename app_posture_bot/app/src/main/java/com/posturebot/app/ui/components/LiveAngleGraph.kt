package com.posturebot.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Simple live graph showing posture metrics over time.
 * Draws a line chart on a Canvas using the provided metric values.
 */
@Composable
fun LiveAngleGraph(
    values: List<Float>,
    label: String = "",
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) {
        Text("Collecting data…", style = MaterialTheme.typography.bodySmall)
        return
    }

    if (label.isNotEmpty()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }

    val lineColor = MaterialTheme.colorScheme.primary

    val maxVal = values.maxOrNull() ?: 1f
    val minVal = values.minOrNull() ?: 0f
    val range = (maxVal - minVal).coerceAtLeast(1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val width = size.width
        val height = size.height
        val stepX = if (values.size > 1) width / (values.size - 1) else width

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = height - ((v - minVal) / range * (height - 8) + 4)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3f)
        )
    }
}
