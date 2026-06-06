package com.posturebot.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val sessionId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val goodPercent: Float? = null,
    val totalAlerts: Int? = null
)
