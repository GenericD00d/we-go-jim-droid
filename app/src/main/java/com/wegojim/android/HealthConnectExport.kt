package com.wegojim.android

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
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
        val start = Instant.ofEpochMilli(workout.startedAt)
        val end = Instant.ofEpochMilli(maxOf(workout.endedAt, workout.startedAt + 60_000))
        val offset = ZoneId.systemDefault().rules.getOffset(start)
        val strength = workout.exercises.isNotEmpty()
        val records = mutableListOf<Record>(
            ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = offset,
                endTime = end,
                endZoneOffset = offset,
                metadata = Metadata.manualEntry(clientRecordId = "wgj-${workout.id}"),
                exerciseType = if (strength) ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING else ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
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
}
