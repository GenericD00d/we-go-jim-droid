package com.wegojim.android

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import com.wegojim.android.data.CardioBlock
import com.wegojim.android.data.CardioProfile
import com.wegojim.android.data.WorkoutEntity
import java.time.Instant
import java.time.ZoneId

/**
 * Optional export of finished workouts to Health Connect (the Android
 * counterpart of the iOS Apple Health export). Nothing is read back.
 */
object HealthConnectExport {
    val permissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
    )

    fun isAvailable(context: Context) =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(context: Context): Boolean = isAvailable(context) &&
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions)

    /** Writes the workout as an exercise session (plus estimated calories when known). */
    suspend fun export(context: Context, workout: WorkoutEntity, activeCalories: Int?): Result<Unit> = runCatching {
        if (!hasPermissions(context)) return@runCatching
        val strength = workout.exercises.isNotEmpty()
        val cardio = workout.cardio.filter { it.completed && (it.durationSeconds ?: 0) > 0 }
        // Cardio timers exclude pauses, and only totals are stored, so a cardio-only
        // workout is one interval of its recorded time ending when it was saved.
        // Strength and mixed sessions keep their session clock, rest included.
        val end = Instant.ofEpochMilli(maxOf(workout.endedAt, workout.startedAt + 60_000))
        val start = if (strength || cardio.isEmpty()) Instant.ofEpochMilli(workout.startedAt)
        else end.minusSeconds(cardio.sumOf { it.durationSeconds ?: 0 }.toLong().coerceIn(60, 24 * 3600))
        val offset = ZoneId.systemDefault().rules.getOffset(start)
        val records = mutableListOf<Record>(
            ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = offset,
                endTime = end,
                endZoneOffset = offset,
                metadata = Metadata.manualEntry(clientRecordId = "wgj-${workout.id}"),
                exerciseType = when {
                    strength -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
                    else -> cardio.map(::exerciseType).distinct().singleOrNull() ?: ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
                },
                title = workout.name,
                notes = workout.notes.ifBlank { null },
            ),
        )
        if (activeCalories != null && activeCalories > 0) {
            records += ActiveCaloriesBurnedRecord(
                startTime = start,
                startZoneOffset = offset,
                endTime = end,
                endZoneOffset = offset,
                energy = Energy.kilocalories(activeCalories.toDouble()),
                metadata = Metadata.manualEntry(clientRecordId = "wgj-kcal-${workout.id}"),
            )
        }
        HealthConnectClient.getOrCreate(context).insertRecords(records)
    }

    private fun exerciseType(block: CardioBlock): Int {
        val id = "${block.exerciseUuid} ${block.name}".lowercase()
        return when {
            "bike" in id || "cycl" in id -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            "row" in id || block.profile == CardioProfile.rower -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
            "stair" in id || block.profile == CardioProfile.stairClimber -> ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE
            "elliptical" in id || "cross" in id -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
            "treadmill" in id && "run" in id -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
            "run" in id -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            "walk" in id || "hik" in id || block.profile == CardioProfile.walkRun || block.profile == CardioProfile.treadmill ->
                ExerciseSessionRecord.EXERCISE_TYPE_WALKING
            else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        }
    }
}
