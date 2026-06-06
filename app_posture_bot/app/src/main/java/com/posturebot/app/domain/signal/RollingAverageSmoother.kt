package com.posturebot.app.domain.signal

import com.posturebot.app.domain.computation.PostureMetrics
import kotlin.collections.ArrayDeque

/**
 * Rolling average smoother to reduce landmark jitter.
 * Raw metrics jump frame-to-frame; smoothing produces stable values for the state machine.
 */
class RollingAverageSmoother(private val windowSize: Int = 10) {

    private val buffer = ArrayDeque<PostureMetrics>()

    fun addAndGet(raw: PostureMetrics): PostureMetrics {
        buffer.addLast(raw)
        if (buffer.size > windowSize) buffer.removeFirst()

        return PostureMetrics(
            forwardHeadOffsetPx = buffer.map { it.forwardHeadOffsetPx }.average().toFloat(),
            neckInclinationDeg = buffer.map { it.neckInclinationDeg }.average().toFloat(),
            headTiltDeg = buffer.map { it.headTiltDeg }.average().toFloat(),
            shoulderSymmetryPx = buffer.map { it.shoulderSymmetryPx }.average().toFloat()
        )
    }

    fun reset() {
        buffer.clear()
    }
}
