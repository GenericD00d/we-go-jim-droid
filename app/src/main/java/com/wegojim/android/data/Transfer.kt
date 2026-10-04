package com.wegojim.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * `.wgjtemplate` files, wire-compatible with the iOS app's TemplateTransferEnvelope
 * (format version 8), so templates can be shared between iPhone and Android.
 */
object TemplateTransfer {
    const val FORMAT_VERSION = 8
    const val EXTENSION = "wgjtemplate"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        explicitNulls = false
    }

    @Serializable
    data class Envelope(
        val formatVersion: Int,
        // Swift's .iso8601 strategy rejects fractional seconds.
        val exportedAt: String,
        val artifact: Artifact? = null,
        // Pre-v6 files put a single template at the top level.
        val template: TTemplate? = null,
    )

    @Serializable
    data class Artifact(val kind: String, val template: TTemplate? = null, val folder: TFolder? = null)

    @Serializable
    data class TFolder(val name: String, val templates: List<TTemplate>)

    @Serializable
    data class TTemplate(
        val name: String,
        val notes: String = "",
        val preWorkoutCardio: TCardio? = null,
        val postWorkoutCardio: TCardio? = null,
        val cardioActivities: List<TCardio>? = null,
        val exercises: List<TExercise> = emptyList(),
    )

    @Serializable
    data class TCardio(
        val role: CardioRole? = null,
        val sortOrder: Int? = null,
        val catalogExerciseUUID: String,
        val exerciseNameSnapshot: String,
        val categorySnapshot: String = "",
        val muscleSummarySnapshot: String = "",
        val trackingProfile: CardioProfile? = null,
        val goalKind: CardioGoal? = null,
        val targetDurationSeconds: Int,
        val targetDistanceMeters: Double? = null,
        val preferredDistanceUnit: DistanceUnit? = null,
    )

    @Serializable
    data class TExercise(
        val catalogExerciseUUID: String,
        val exerciseNameSnapshot: String,
        val categorySnapshot: String = "",
        val muscleSummarySnapshot: String = "",
        val notes: String = "",
        val targetRepMin: Int? = null,
        val targetRepMax: Int? = null,
        val restSeconds: Int = 120,
        val sets: List<TSet> = emptyList(),
        val components: List<TComponent>? = null,
        val superset: TSuperset? = null,
    )

    @Serializable
    data class TComponent(
        val catalogExerciseUUID: String,
        val exerciseNameSnapshot: String,
        val categorySnapshot: String = "",
        val muscleSummarySnapshot: String = "",
    )

    @Serializable
    data class TSet(
        val targetReps: Int? = null,
        val targetWeight: Double? = null,
        val loadUnit: LoadUnit = LoadUnit.kg,
        val restSeconds: Int = 120,
        val isWarmup: Boolean = false,
        val isLocked: Boolean = false,
        val dropStages: List<TDrop> = emptyList(),
    )

    @Serializable
    data class TDrop(val id: String = newId().uppercase(), val targetReps: Int? = null, val targetWeight: Double? = null, val loadUnit: LoadUnit = LoadUnit.kg)

    @Serializable
    data class TSuperset(val groupID: String, val position: String, val roundRestSeconds: Int)

    // region export

    fun exportTemplate(t: TemplateEntity): String = encode(Artifact(kind = "template", template = toTransfer(t)))

    fun exportFolder(name: String, templates: List<TemplateEntity>): String =
        encode(Artifact(kind = "folder", folder = TFolder(name, templates.map(::toTransfer))))

    private fun encode(artifact: Artifact) = json.encodeToString(
        Envelope.serializer(),
        Envelope(FORMAT_VERSION, Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), artifact),
    )

    private fun toTransfer(t: TemplateEntity): TTemplate {
        val groups = mutableMapOf<String, Int>()
        return TTemplate(
            name = t.name,
            notes = t.notes,
            cardioActivities = t.cardio.mapIndexed { i, c ->
                TCardio(
                    role = c.role, sortOrder = i, catalogExerciseUUID = c.exerciseUuid, exerciseNameSnapshot = c.name,
                    trackingProfile = c.profile, goalKind = c.goal, targetDurationSeconds = c.targetDurationSeconds,
                    targetDistanceMeters = c.targetDistanceMeters, preferredDistanceUnit = c.distanceUnit,
                )
            },
            exercises = t.exercises.map { e ->
                TExercise(
                    catalogExerciseUUID = e.exerciseUuid,
                    exerciseNameSnapshot = e.name,
                    categorySnapshot = e.category,
                    muscleSummarySnapshot = e.muscleSummary,
                    notes = e.notes,
                    targetRepMin = e.targetRepMin,
                    targetRepMax = e.targetRepMax,
                    restSeconds = e.restSeconds,
                    sets = e.sets.map { s ->
                        TSet(
                            targetReps = s.targetReps, targetWeight = s.targetWeight, loadUnit = s.unit,
                            restSeconds = e.restSeconds, isWarmup = s.isWarmup,
                            dropStages = s.dropStages.map { TDrop(targetReps = it.targetReps, targetWeight = it.targetWeight, loadUnit = it.unit) },
                        )
                    },
                    components = e.components.takeIf { it.isNotEmpty() }?.map {
                        TComponent(it.exerciseUuid, it.name, it.category, it.muscleSummary)
                    },
                    superset = e.supersetGroupId?.let { g ->
                        val position = groups.getOrDefault(g, 0)
                        groups[g] = position + 1
                        TSuperset(g.uppercase(), if (position == 0) "first" else "second", e.supersetRestSeconds ?: e.restSeconds)
                    },
                )
            },
        )
    }

    // endregion

    // region import

    class ImportResult(val folderName: String?, val templates: List<TemplateEntity>)

    /** Parses a file and returns ready-to-save templates (new ids), or throws with a readable message. */
    fun import(text: String): ImportResult {
        val envelope = runCatching { json.decodeFromString(Envelope.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("This file isn't a We Go Jim template.") }
        if (envelope.formatVersion !in 1..FORMAT_VERSION) {
            throw IllegalArgumentException("This template was made by a newer app version (format ${envelope.formatVersion}).")
        }
        val artifact = envelope.artifact ?: envelope.template?.let { Artifact("template", template = it) }
            ?: throw IllegalArgumentException("This file doesn't contain a template.")
        return when (artifact.kind) {
            "folder" -> {
                val folder = artifact.folder ?: throw IllegalArgumentException("This folder file is empty.")
                ImportResult(folder.name, folder.templates.map(::fromTransfer))
            }
            else -> ImportResult(null, listOf(fromTransfer(artifact.template ?: throw IllegalArgumentException("This file doesn't contain a template."))))
        }
    }

    private fun fromTransfer(t: TTemplate): TemplateEntity {
        val groupIds = mutableMapOf<String, String>()
        val cardio = (t.cardioActivities ?: listOfNotNull(
            t.preWorkoutCardio?.copy(role = t.preWorkoutCardio.role ?: CardioRole.warmUp),
            t.postWorkoutCardio?.copy(role = t.postWorkoutCardio.role ?: CardioRole.finisher),
        )).sortedBy { it.sortOrder ?: 0 }
        return TemplateEntity(
            name = t.name,
            notes = t.notes,
            cardio = cardio.map { c ->
                val role = c.role ?: CardioRole.warmUp
                CardioBlock(
                    exerciseUuid = c.catalogExerciseUUID,
                    name = c.exerciseNameSnapshot,
                    profile = c.trackingProfile ?: CardioProfile.timeOnly,
                    phase = if (role == CardioRole.warmUp) CardioPhase.preWorkout else CardioPhase.postWorkout,
                    role = role,
                    goal = c.goalKind ?: CardioGoal.time,
                    targetDurationSeconds = c.targetDurationSeconds,
                    targetDistanceMeters = c.targetDistanceMeters,
                    distanceUnit = c.preferredDistanceUnit ?: regionalDistanceUnit(),
                )
            },
            exercises = t.exercises.map { e ->
                ExerciseEntry(
                    exerciseUuid = e.catalogExerciseUUID,
                    name = e.exerciseNameSnapshot,
                    category = e.categorySnapshot,
                    muscleSummary = e.muscleSummarySnapshot,
                    notes = e.notes,
                    restSeconds = e.restSeconds,
                    targetRepMin = e.targetRepMin,
                    targetRepMax = e.targetRepMax,
                    sets = e.sets.map { s ->
                        SetEntry(
                            isWarmup = s.isWarmup, targetReps = s.targetReps, targetWeight = s.targetWeight, unit = s.loadUnit,
                            dropStages = s.dropStages.map { DropStage(targetReps = it.targetReps, targetWeight = it.targetWeight, unit = it.loadUnit) },
                        )
                    },
                    supersetGroupId = e.superset?.let { groupIds.getOrPut(it.groupID) { newId() } },
                    supersetRestSeconds = e.superset?.roundRestSeconds,
                    components = e.components.orEmpty().map { ExerciseRef(it.catalogExerciseUUID, it.exerciseNameSnapshot, it.categorySnapshot, it.muscleSummarySnapshot) },
                )
            },
        )
    }

    // endregion
}

/** Full local backup: everything the user created, restorable on any Android device. */
@Serializable
data class BackupFile(
    val app: String = "We Go Jim Android",
    @SerialName("version") val version: Int = 1,
    val exportedAt: String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
    val folders: List<FolderBackup> = emptyList(),
    val templates: List<TemplateBackup> = emptyList(),
    val workouts: List<WorkoutBackup> = emptyList(),
    val customExercises: List<CustomExerciseBackup> = emptyList(),
)

@Serializable
data class FolderBackup(val id: String, val name: String, val sortOrder: Int)

@Serializable
data class TemplateBackup(
    val id: String, val name: String, val notes: String, val folderId: String?, val sortOrder: Int,
    val exercises: List<ExerciseEntry>, val cardio: List<CardioBlock>, val updatedAt: Long,
)

@Serializable
data class WorkoutBackup(
    val id: String, val templateId: String?, val name: String, val startedAt: Long, val endedAt: Long,
    val notes: String, val exercises: List<ExerciseEntry>, val cardio: List<CardioBlock>,
)

@Serializable
data class CustomExerciseBackup(
    val uuid: String, val name: String, val category: String, val equipment: String,
    val primaryMuscles: String, val secondaryMuscles: String, val cardioProfile: String?,
)
