package com.wegojim.android.data

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
) {
    val workingSets: List<SetEntry> get() = sets.filter { !it.isWarmup }
}

@Serializable
data class ActiveWorkout(
    val id: String = newId(),
    val templateId: String? = null,
    val name: String,
    val startedAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    val exercises: List<ExerciseEntry> = emptyList(),
    val restEndsAt: Long? = null,
    val restTotalSeconds: Int = 0,
    val restLabel: String? = null,
)
