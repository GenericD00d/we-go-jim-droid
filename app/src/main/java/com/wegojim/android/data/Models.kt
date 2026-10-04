package com.wegojim.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
enum class LoadUnit(val label: String) {
    kg("kg"),
    lb("lb"),
    bodyweight("BW");
}

/**
 * One prescribed or logged set. Templates only use the target fields;
 * active and completed workouts fill in the actual values.
 */
@Serializable
data class SetEntry(
    val id: String = newId(),
    val isWarmup: Boolean = false,
    val targetReps: Int? = null,
    val targetWeight: Double? = null,
    val reps: Int? = null,
    val weight: Double? = null,
    val unit: LoadUnit = LoadUnit.kg,
    val completed: Boolean = false,
    val isPr: Boolean = false,
    val dropStages: List<DropStage> = emptyList(),
)

/** A dropset stage that follows its parent set without rest. */
@Serializable
data class DropStage(
    val id: String = newId(),
    val targetReps: Int? = null,
    val targetWeight: Double? = null,
    val reps: Int? = null,
    val weight: Double? = null,
    val unit: LoadUnit = LoadUnit.kg,
    val completed: Boolean = false,
)

/** A catalog exercise reference, used for rotating alternatives on a template slot. */
@Serializable
data class ExerciseRef(
    val exerciseUuid: String,
    val name: String,
    val category: String = "",
    val muscleSummary: String = "",
)

@Serializable
data class ExerciseEntry(
    val id: String = newId(),
    val exerciseUuid: String,
    val name: String,
    val category: String = "",
    val muscleSummary: String = "",
    val notes: String = "",
    val restSeconds: Int = 120,
    val targetRepMin: Int? = null,
    val targetRepMax: Int? = null,
    val sets: List<SetEntry> = emptyList(),
    /** The template exercise this was started from, for syncing changes back. */
    val templateExerciseId: String? = null,
    /** Two consecutive exercises sharing a group id form a superset (A1/A2). */
    val supersetGroupId: String? = null,
    val supersetRestSeconds: Int? = null,
    /** Rotating alternatives for this slot; the next one is suggested each session. */
    val components: List<ExerciseRef> = emptyList(),
) {
    val workingSets: List<SetEntry> get() = sets.filter { !it.isWarmup }
    val hasDropsets: Boolean get() = sets.any { it.dropStages.isNotEmpty() }
    val ref: ExerciseRef get() = ExerciseRef(exerciseUuid, name, category, muscleSummary)
}

@Serializable
enum class CardioPhase(val label: String) { preWorkout("Before lifting"), postWorkout("After lifting") }

@Serializable
enum class CardioRole(val label: String) { warmUp("Warm-up"), main("Main"), finisher("Finisher") }

@Serializable
enum class CardioGoal(val label: String) { time("Time"), distance("Distance"), @SerialName("open") openEnded("Open") }

@Serializable
enum class DistanceUnit(val symbol: String, val metersPerUnit: Double) {
    kilometers("km", 1000.0),
    miles("mi", 1609.344),
    meters("m", 1.0);

    fun toMeters(value: Double) = value * metersPerUnit
    fun fromMeters(value: Double) = value / metersPerUnit
}

/** Port of WorkoutCardioTrackingProfile. */
@Serializable
enum class CardioProfile {
    walkRun, treadmill, machineDistance, rower, stairClimber, timeOnly;

    val tracksDistance: Boolean get() = this != timeOnly && this != stairClimber
    val tracksIncline: Boolean get() = this == treadmill
    val tracksResistance: Boolean get() = this == machineDistance || this == rower || this == stairClimber

    companion object {
        fun of(raw: String?): CardioProfile = entries.firstOrNull { it.name == raw } ?: timeOnly
    }
}

@Serializable
data class CardioBlock(
    val id: String = newId(),
    val templateCardioId: String? = null,
    val exerciseUuid: String,
    val name: String,
    val profile: CardioProfile = CardioProfile.timeOnly,
    val phase: CardioPhase = CardioPhase.preWorkout,
    val role: CardioRole = CardioRole.warmUp,
    val goal: CardioGoal = CardioGoal.time,
    val targetDurationSeconds: Int = 5 * 60,
    val targetDistanceMeters: Double? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.kilometers,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val inclinePercent: Double? = null,
    val resistanceLevel: Double? = null,
    val notes: String = "",
    val timerStartedAt: Long? = null,
    val timerAccumulatedSeconds: Int = 0,
    val completed: Boolean = false,
) {
    fun elapsedSeconds(now: Long = System.currentTimeMillis()): Int =
        timerAccumulatedSeconds + (timerStartedAt?.let { ((now - it) / 1000).toInt() } ?: 0)

    val isRunning: Boolean get() = timerStartedAt != null
}

@Serializable
data class ActiveWorkout(
    val id: String = newId(),
    val templateId: String? = null,
    val name: String,
    val startedAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    val exercises: List<ExerciseEntry> = emptyList(),
    val cardio: List<CardioBlock> = emptyList(),
    val restEndsAt: Long? = null,
    val restTotalSeconds: Int = 0,
    val restLabel: String? = null,
)
