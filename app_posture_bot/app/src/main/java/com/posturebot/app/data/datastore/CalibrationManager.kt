package com.posturebot.app.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.posturebot.app.domain.computation.PostureMetrics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val FORWARD_HEAD_KEY = floatPreferencesKey("calibration_forward_head_px")
private val NECK_ANGLE_KEY = floatPreferencesKey("calibration_neck_angle_deg")
private val HEAD_TILT_KEY = floatPreferencesKey("calibration_head_tilt_deg")
private val SHOULDER_SYM_KEY = floatPreferencesKey("calibration_shoulder_symmetry_px")

/**
 * Records user's good posture during calibration and persists as baseline.
 */
class CalibrationManager(private val dataStore: DataStore<Preferences>) {

    private val samples = mutableListOf<PostureMetrics>()

    fun addSample(m: PostureMetrics) {
        samples.add(m)
    }

    suspend fun finalizeAndSave() {
        val baseline = PostureMetrics(
            forwardHeadOffsetPx = samples.map { it.forwardHeadOffsetPx }.average().toFloat(),
            neckInclinationDeg = samples.map { it.neckInclinationDeg }.average().toFloat(),
            headTiltDeg = samples.map { it.headTiltDeg }.average().toFloat(),
            shoulderSymmetryPx = samples.map { it.shoulderSymmetryPx }.average().toFloat()
        )
        dataStore.edit { prefs ->
            prefs[FORWARD_HEAD_KEY] = baseline.forwardHeadOffsetPx
            prefs[NECK_ANGLE_KEY] = baseline.neckInclinationDeg
            prefs[HEAD_TILT_KEY] = baseline.headTiltDeg
            prefs[SHOULDER_SYM_KEY] = baseline.shoulderSymmetryPx
        }
        samples.clear()
    }

    fun clearSamples() {
        samples.clear()
    }

    suspend fun getBaseline(): PostureMetrics? {
        val prefs = dataStore.data.first()
        val fh = prefs[FORWARD_HEAD_KEY] ?: return null
        val neck = prefs[NECK_ANGLE_KEY] ?: return null
        val tilt = prefs[HEAD_TILT_KEY] ?: return null
        val sym = prefs[SHOULDER_SYM_KEY] ?: return null
        return PostureMetrics(fh, neck, tilt, sym)
    }

    val hasBaseline = dataStore.data.map { prefs ->
        prefs[FORWARD_HEAD_KEY] != null
    }
}
