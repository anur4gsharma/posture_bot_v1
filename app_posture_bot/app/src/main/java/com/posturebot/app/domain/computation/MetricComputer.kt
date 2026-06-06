package com.posturebot.app.domain.computation

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2

data class PostureMetrics(
    val forwardHeadOffsetPx: Float,
    val neckInclinationDeg: Float,
    val headTiltDeg: Float,
    val shoulderSymmetryPx: Float
)

object MetricComputer {

    fun compute(lm: List<NormalizedLandmark>, frameWidth: Int, frameHeight: Int): PostureMetrics {
        val leftEar = lm[7]
        val rightEar = lm[8]
        val leftShoulder = lm[11]
        val rightShoulder = lm[12]

        val earX = (leftEar.x() + rightEar.x()) / 2f
        val earY = (leftEar.y() + rightEar.y()) / 2f
        val shoulderX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val shoulderY = (leftShoulder.y() + rightShoulder.y()) / 2f

        val forwardOffset = (earX - shoulderX) * frameWidth

        val neckAngle = Math.toDegrees(
            atan2(
                (shoulderY - earY).toDouble(),
                abs(shoulderX - earX).toDouble()
            )
        ).toFloat()

        val headTilt = Math.toDegrees(
            atan2(
                (leftEar.y() - rightEar.y()).toDouble(),
                abs(leftEar.x() - rightEar.x()).toDouble()
            )
        ).toFloat()

        val shoulderSym = (leftShoulder.y() - rightShoulder.y()) * frameHeight

        return PostureMetrics(
            forwardHeadOffsetPx = forwardOffset,
            neckInclinationDeg = neckAngle,
            headTiltDeg = headTilt,
            shoulderSymmetryPx = shoulderSym
        )
    }
}