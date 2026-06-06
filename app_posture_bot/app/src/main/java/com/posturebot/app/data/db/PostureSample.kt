package com.posturebot.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posture_samples")
data class PostureSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestamp: Long,
    val forwardHeadOffsetPx: Float,
    val neckInclinationDeg: Float,
    val headTiltDeg: Float,
    val shoulderSymmetryPx: Float,
    val state: String  // 'GOOD', 'WARNING', 'BAD'
)
