package com.posturebot.app.domain.mediapipe

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PoseInferenceEngine(context: Context) {

    private val poseLandmarker: PoseLandmarker

    init {
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("pose_landmarker_full.task")
                    .build()
            )
            .setMinPoseDetectionConfidence(0.7f)
            .setMinTrackingConfidence(0.7f)
            .setMinPosePresenceConfidence(0.7f)
            .setNumPoses(1)
            .setRunningMode(RunningMode.IMAGE)
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    suspend fun processFrame(bitmap: Bitmap): List<NormalizedLandmark>? = withContext(Dispatchers.Default) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = poseLandmarker.detect(mpImage)
        result.landmarks().firstOrNull()
    }

    fun close() {
        poseLandmarker.close()
    }

    companion object {
        fun isReliable(landmarks: List<NormalizedLandmark>): Boolean {
            val keyIndices = listOf(7, 8, 11, 12)
            return keyIndices.all { i ->
                i < landmarks.size && landmarks[i].visibility().orElse(0f) > 0.7f
            }
        }
    }
}