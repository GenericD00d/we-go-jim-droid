package com.wegojim.android.data

import kotlinx.serialization.Serializable
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class CardioRoutePoint(
    val latitude: Double,
    val longitude: Double,
    /** Epoch millis of the GPS fix. */
    val timestamp: Long,
    val horizontalAccuracy: Double,
    val segment: Int,
)

/**
 * Port of CardioRoute.swift. One recorded GPS route per cardio block (keyed by
 * the block id). Coordinates stay on the device and never go to Health Connect
 * or the widget.
 */
@Serializable
data class CardioRoute(
    val sessionId: String,
    val activityId: String,
    val points: List<CardioRoutePoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val segment: Int = 0,
    val isRecording: Boolean = false,
    // Includes valid fixes filtered out as stationary noise, so filtering cannot
    // turn a continuous stream of fixes into an apparent signal gap.
    val lastValidFixTimestamp: Long? = null,
) {
    /** Checked before a route is shared or restored from a backup. */
    fun isValid(): Boolean {
        if (isRecording || !distanceMeters.isFinite() || distanceMeters < 0 || segment < 0) return false
        val pointsOk = points.all {
            it.latitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude.isFinite() && it.longitude in -180.0..180.0 &&
                it.horizontalAccuracy.isFinite() && it.horizontalAccuracy in 0.0..MAX_ACCURACY && it.segment in 0..segment
        }
        return pointsOk && points.zipWithNext().all { (a, b) -> b.timestamp > a.timestamp && b.segment >= a.segment }
    }

    fun beginSegment() = copy(lastValidFixTimestamp = null, segment = segment + 1, isRecording = true)

    fun stop() = copy(lastValidFixTimestamp = null, isRecording = false)

    /**
     * Rejects stale or inaccurate fixes and GPS jumps, and never joins pauses or
     * signal gaps. Returns the updated route; a rejected fix leaves [points] unchanged.
     */
    fun append(
        latitude: Double,
        longitude: Double,
        timestamp: Long,
        accuracy: Double,
        now: Long,
        recordingStartedAt: Long,
        maximumSpeedMetersPerSecond: Double = 12.0,
    ): CardioRoute {
        if (!isRecording || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
            !accuracy.isFinite() || accuracy !in 0.0..MAX_ACCURACY ||
            timestamp < recordingStartedAt || (now - timestamp) !in -5_000L..15_000L
        ) return this

        val previousFix = lastValidFixTimestamp ?: points.lastOrNull()?.timestamp
        if (previousFix != null && timestamp <= previousFix) return this
        val withFix = copy(lastValidFixTimestamp = timestamp)

        val previous = points.lastOrNull()
        var segment = segment
        var distanceMeters = distanceMeters
        if (previous != null && previous.segment == segment) {
            val seconds = (timestamp - previous.timestamp) / 1000.0
            val distance = distance(latitude, longitude, previous.latitude, previous.longitude)
            val fixGap = (timestamp - (previousFix ?: previous.timestamp)) / 1000.0
            if (fixGap > 30 || distance / seconds > maximumSpeedMetersPerSecond) {
                // Reacquire an anchor without inventing travel through a GPS gap.
                segment += 1
            } else {
                val noiseFloor = max(5.0, min(15.0, (accuracy + previous.horizontalAccuracy) * 0.25))
                if (distance < noiseFloor) return withFix
                distanceMeters += distance
            }
        }
        return withFix.copy(
            segment = segment,
            distanceMeters = distanceMeters,
            points = points + CardioRoutePoint(latitude, longitude, timestamp, accuracy, segment),
        )
    }

    companion object {
        const val MAX_ACCURACY = 35.0

        fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val radians = Math.PI / 180
            val dLat = (lat2 - lat1) * radians
            val dLon = (lon2 - lon1) * radians
            val a = sin(dLat / 2).pow(2) + cos(lat1 * radians) * cos(lat2 * radians) * sin(dLon / 2).pow(2)
            return 6_371_000 * 2 * asin(sqrt(min(1.0, max(0.0, a))))
        }
    }
}

/** Port of CardioRecordingPolicy. */
object CardioRecording {
    private val outdoorIds = setOf("seed-outdoor-walk", "seed-outdoor-run", "seed-outdoor-bike")
    private val cyclingIds = setOf("seed-bike", "seed-outdoor-bike", "seed-assault-bike-sprint")

    /** Explicit outdoor identity is required: a generic or custom walk never collects location. */
    fun recordsGps(exerciseUuid: String) = exerciseUuid in outdoorIds

    fun isCycling(exerciseUuid: String) = exerciseUuid in cyclingIds

    fun maximumSpeedMetersPerSecond(exerciseUuid: String) = if (exerciseUuid == "seed-outdoor-bike") 35.0 else 12.0

    /** Walks, runs and GPS activities get the dedicated recording screen instead of the inline card. */
    fun usesSessionScreen(block: CardioBlock) =
        recordsGps(block.exerciseUuid) || block.profile == CardioProfile.walkRun || block.profile == CardioProfile.treadmill

    /** A workout holding just one such activity is shown as the recording screen itself. */
    fun standaloneActivity(workout: ActiveWorkout): CardioBlock? =
        workout.cardio.singleOrNull()?.takeIf { workout.exercises.isEmpty() && usesSessionScreen(it) }
}
