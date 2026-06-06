package com.posturebot.app.domain.statemachine

import com.posturebot.app.domain.computation.PostureMetrics
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Compares smoothed metrics against baseline and produces PostureState.
 * Requires breach duration before transitioning to WARNING or BAD.
 */
class PostureStateMachine(private val baseline: PostureMetrics) {

    companion object {
        private const val TOLERANCE_FORWARD_PX = 15f
        private const val TOLERANCE_NECK_DEG = 8f
        private const val TOLERANCE_HEAD_TILT_DEG = 5f
        private const val TOLERANCE_SHOULDER_PX = 10f
        private const val SEVERE_MULTIPLIER = 2f

        private const val WARNING_DURATION_MS = 3_000L
        private const val BAD_DURATION_MS = 10_000L
    }

    private var breachStartTime: Long? = null

    private val _stateFlow = MutableStateFlow<PostureState>(PostureState.Good)
    val stateFlow: StateFlow<PostureState> = _stateFlow

    fun evaluate(metrics: PostureMetrics) {
        val breachCount = countBreaches(metrics)
        val hasSevereBreach = hasSevereBreach(metrics)

        if (breachCount == 0 && !hasSevereBreach) {
            breachStartTime = null
            _stateFlow.value = PostureState.Good
            return
        }

        val now = System.currentTimeMillis()
        if (breachStartTime == null) breachStartTime = now
        val duration = now - breachStartTime!!

        val isBadCondition = breachCount >= 2 || hasSevereBreach
        _stateFlow.value = when {
            isBadCondition && duration > BAD_DURATION_MS -> PostureState.Bad
            duration > WARNING_DURATION_MS -> PostureState.Warning
            else -> _stateFlow.value
        }
    }

    private fun countBreaches(metrics: PostureMetrics): Int {
        var count = 0
        if (abs(metrics.forwardHeadOffsetPx - baseline.forwardHeadOffsetPx) > TOLERANCE_FORWARD_PX) count++
        if (abs(metrics.neckInclinationDeg - baseline.neckInclinationDeg) > TOLERANCE_NECK_DEG) count++
        if (abs(metrics.headTiltDeg - baseline.headTiltDeg) > TOLERANCE_HEAD_TILT_DEG) count++
        if (abs(metrics.shoulderSymmetryPx - baseline.shoulderSymmetryPx) > TOLERANCE_SHOULDER_PX) count++
        return count
    }

    private fun hasSevereBreach(metrics: PostureMetrics): Boolean {
        if (abs(metrics.forwardHeadOffsetPx - baseline.forwardHeadOffsetPx) > TOLERANCE_FORWARD_PX * SEVERE_MULTIPLIER) return true
        if (abs(metrics.neckInclinationDeg - baseline.neckInclinationDeg) > TOLERANCE_NECK_DEG * SEVERE_MULTIPLIER) return true
        if (abs(metrics.headTiltDeg - baseline.headTiltDeg) > TOLERANCE_HEAD_TILT_DEG * SEVERE_MULTIPLIER) return true
        if (abs(metrics.shoulderSymmetryPx - baseline.shoulderSymmetryPx) > TOLERANCE_SHOULDER_PX * SEVERE_MULTIPLIER) return true
        return false
    }
}
