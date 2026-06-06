package com.posturebot.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PostureDao {

    @Insert
    suspend fun insertSample(sample: PostureSample)

    @Insert
    suspend fun insertSession(session: Session)

    @Update
    suspend fun updateSession(session: Session)

    @Query("SELECT * FROM posture_samples WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getSamplesForSession(sessionId: String): Flow<List<PostureSample>>

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): Session?

    @Query("SELECT COUNT(*) FROM posture_samples WHERE sessionId = :sessionId AND state = 'GOOD'")
    suspend fun getGoodCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM posture_samples WHERE sessionId = :sessionId")
    suspend fun getTotalCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM posture_samples WHERE sessionId = :sessionId AND state IN ('WARNING', 'BAD')")
    suspend fun getAlertCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM posture_samples WHERE sessionId = :sessionId AND state = 'BAD'")
    suspend fun getBadCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM posture_samples WHERE sessionId = :sessionId AND state = 'WARNING'")
    suspend fun getWarningCount(sessionId: String): Int
}
