package com.wegojim.android.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class SeedFile(
    val version: Int,
    val muscles: List<SeedMuscle>,
    val exercises: List<SeedExercise>,
)

@Serializable
private data class SeedMuscle(val id: Int, @SerialName("name_en") val nameEn: String)

@Serializable
private data class SeedExercise(
    val uuid: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val category: String,
    val equipment: String = "",
    val instructions: String = "",
    @SerialName("cardio_tracking_profile") val cardioProfile: String? = null,
    @SerialName("primary_muscles") val primary: List<Int> = emptyList(),
    @SerialName("secondary_muscles") val secondary: List<Int> = emptyList(),
)

class Repository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val routes: RouteStore,
) {
    val exercises: Flow<List<ExerciseEntity>> = db.exercises().all()
    val templates: Flow<List<TemplateEntity>> = db.templates().all()
    val folders: Flow<List<TemplateFolderEntity>> = db.templates().folders()
    val workouts: Flow<List<WorkoutEntity>> = db.workouts().all()
    val exerciseBests: Flow<List<ExerciseBest>> = db.workouts().exerciseBests()
    val facts: Flow<List<SetFactEntity>> = db.workouts().allFacts()

    fun exercise(uuid: String) = db.exercises().byUuid(uuid)
    fun workout(id: String) = db.workouts().byId(id)
    fun factsForExercise(uuid: String) = db.workouts().factsForExercise(uuid)

    /** Imports the bundled catalog when it is new or has a newer version. */
    suspend fun seedCatalogIfNeeded() = withContext(Dispatchers.IO) {
        val seed = context.assets.open("ExercisesSeed.json").bufferedReader().use {
            AppJson.decodeFromString(SeedFile.serializer(), it.readText())
        }
        if (seed.version <= settings.seedVersion) return@withContext
        val muscles = seed.muscles.associate { it.id to it.nameEn }
        fun names(ids: List<Int>) = ids.mapNotNull { muscles[it] }.joinToString(",")
        db.exercises().upsert(seed.exercises.map {
            ExerciseEntity(
                uuid = it.uuid,
                name = it.name,
                category = it.category,
                equipment = it.equipment,
                instructions = it.instructions,
                primaryMuscles = names(it.primary),
                secondaryMuscles = names(it.secondary),
                aliases = it.aliases.joinToString(","),
                cardioProfile = it.cardioProfile,
                isCustom = false,
            )
        })
        settings.seedVersion = seed.version
    }

    suspend fun createCustomExercise(name: String, category: String, equipment: String, primaryMuscles: String) {
        db.exercises().upsert(listOf(
            ExerciseEntity(
                uuid = "custom-" + newId(),
                name = name.trim(),
                category = category,
                equipment = equipment.trim(),
                instructions = "",
                primaryMuscles = primaryMuscles,
                secondaryMuscles = "",
                aliases = "",
                cardioProfile = if (category == "Cardio") "timeOnly" else null,
                isCustom = true,
            )
        ))
    }

    suspend fun deleteCustomExercise(uuid: String) = db.exercises().deleteCustom(uuid)

    suspend fun template(id: String): TemplateEntity? = db.templates().get(id)

    suspend fun saveTemplate(template: TemplateEntity) {
        val sortOrder = if (db.templates().get(template.id) == null) db.templates().maxSortOrder() + 1 else template.sortOrder
        db.templates().upsert(template.copy(sortOrder = sortOrder, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTemplate(id: String) = db.templates().delete(id)

    /** Copies get their own ids throughout, including superset groups, drops and cardio. */
    suspend fun duplicateTemplate(id: String) {
        val source = db.templates().get(id) ?: return
        val groups = mutableMapOf<String, String>()
        saveTemplate(source.copy(
            id = newId(),
            name = source.name + " (copy)",
            exercises = source.exercises.map { e ->
                e.copy(
                    id = newId(),
                    supersetGroupId = e.supersetGroupId?.let { groups.getOrPut(it) { newId() } },
                    sets = e.sets.map { s -> s.copy(id = newId(), dropStages = s.dropStages.map { it.copy(id = newId()) }) },
                )
            },
            cardio = source.cardio.map { it.copy(id = newId()) },
        ))
    }

    suspend fun createFolder(name: String): String {
        val folder = TemplateFolderEntity(name = name.trim(), sortOrder = db.templates().maxFolderSortOrder() + 1)
        db.templates().upsertFolder(folder)
        return folder.id
    }

    suspend fun renameFolder(folder: TemplateFolderEntity, name: String) = db.templates().upsertFolder(folder.copy(name = name.trim()))

    /** Deleting a folder keeps its templates; they move to "No folder". */
    suspend fun deleteFolder(id: String) = db.withTransaction {
        db.templates().clearFolder(id)
        db.templates().deleteFolder(id)
    }

    suspend fun saveAsTemplate(name: String, exercises: List<ExerciseEntry>, cardio: List<CardioBlock>, notes: String = ""): String {
        val template = TemplateEntity(
            name = name.trim(),
            notes = notes,
            exercises = WorkoutEditing.templateExercisesFrom(exercises),
            cardio = WorkoutEditing.templateCardioFrom(cardio),
        )
        saveTemplate(template)
        return template.id
    }

    /**
     * Applies a workout's structure back onto its source template. Matched
     * slots keep their ids, rotating alternatives and primary exercise.
     */
    suspend fun applyWorkoutToTemplate(templateId: String, workout: ActiveWorkout) {
        val template = db.templates().get(templateId) ?: return
        val original = template.exercises.associateBy { it.id }
        val exercises = WorkoutEditing.templateExercisesFrom(workout.exercises, keepIds = true).zip(workout.exercises) { t, w ->
            val source = original[w.templateExerciseId] ?: return@zip t
            val rotated = source.components.any { it.exerciseUuid == w.exerciseUuid }
            if (rotated) t.copy(exerciseUuid = source.exerciseUuid, name = source.name, category = source.category, muscleSummary = source.muscleSummary, components = source.components)
            else t.copy(components = source.components)
        }
        saveTemplate(template.copy(exercises = exercises, cardio = WorkoutEditing.templateCardioFrom(workout.cardio, keepIds = true), notes = workout.notes))
    }

    /**
     * Picks the next rotating alternative for each template slot: the one
     * after whichever was performed most recently.
     */
    suspend fun rotationPicks(template: TemplateEntity): Map<String, ExerciseRef> = template.exercises
        .filter { it.components.isNotEmpty() }
        .associate { slot ->
            val options = listOf(slot.ref) + slot.components
            val lastDone = options.map { db.workouts().lastCompletedAt(it.exerciseUuid) }
            val lastIndex = lastDone.withIndex().filter { it.value != null }.maxByOrNull { it.value!! }?.index
            slot.id to options[lastIndex?.let { (it + 1) % options.size } ?: 0]
        }

    /** Saves imported templates; a folder file becomes a new folder. Returns the number imported. */
    suspend fun importTemplates(result: TemplateTransfer.ImportResult): Int {
        val folderId = result.folderName?.let { createFolder(it) }
        result.templates.forEach { saveTemplate(it.copy(folderId = folderId)) }
        return result.templates.size
    }

    suspend fun exportBackup(): String = AppJson.encodeToString(BackupFile.serializer(), BackupFile(
        folders = db.templates().foldersNow().map { FolderBackup(it.id, it.name, it.sortOrder) },
        templates = db.templates().allNow().map { TemplateBackup(it.id, it.name, it.notes, it.folderId, it.sortOrder, it.exercises, it.cardio, it.updatedAt) },
        workouts = db.workouts().allNow().map { WorkoutBackup(it.id, it.templateId, it.name, it.startedAt, it.endedAt, it.notes, it.exercises, it.cardio) },
        customExercises = db.exercises().customExercises().map {
            CustomExerciseBackup(it.uuid, it.name, it.category, it.equipment, it.primaryMuscles, it.secondaryMuscles, it.cardioProfile)
        },
        cardioRoutes = withContext(Dispatchers.IO) { savedRoutes() },
    ))

    /** Completed routes that belong to saved workouts; live or orphaned ones stay out of backups. */
    private suspend fun savedRoutes(): List<CardioRoute> {
        val owners = db.workouts().allNow().flatMap { w -> w.cardio.map { it.id to w.id } }.toMap()
        return routes.all().filter { owners[it.activityId] == it.sessionId && it.isValid() && it.points.isNotEmpty() }
    }

    /**
     * Replaces all user data with a backup. Workouts are re-saved oldest first
     * so PRs and set facts are recomputed consistently.
     */
    suspend fun restoreBackup(text: String, keepRouteIds: Set<String> = emptySet()): BackupFile {
        val backup = runCatching { AppJson.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("This file isn't a We Go Jim backup.") }
        val owners = backup.workouts.flatMap { w -> w.cardio.map { it.id to w.id } }.toMap()
        val restoredRoutes = backup.cardioRoutes.filter { owners[it.activityId] == it.sessionId && it.isValid() }
        db.withTransaction {
            db.workouts().deleteAllFacts()
            db.workouts().deleteAll()
            db.templates().deleteAll()
            db.templates().deleteAllFolders()
            db.exercises().deleteAllCustom()
            db.exercises().upsert(backup.customExercises.map {
                ExerciseEntity(it.uuid, it.name, it.category, it.equipment, "", it.primaryMuscles, it.secondaryMuscles, "", it.cardioProfile, true)
            })
            backup.folders.forEach { db.templates().upsertFolder(TemplateFolderEntity(it.id, it.name, it.sortOrder)) }
            backup.templates.forEach {
                db.templates().upsert(TemplateEntity(it.id, it.name, it.notes, it.folderId, it.sortOrder, it.exercises, it.updatedAt, it.cardio))
            }
            backup.workouts.sortedBy { it.startedAt }.forEach {
                saveWorkout(it.id, it.templateId, it.name, it.notes, it.startedAt, it.endedAt, it.exercises, it.cardio)
            }
        }
        // Routes follow the restored history; the in-progress workout keeps its own.
        withContext(Dispatchers.IO) { routes.replaceAll(restoredRoutes, keepRouteIds) }
        return backup
    }

    suspend fun lastSessionSets(exerciseUuid: String): List<SetFactEntity> = db.workouts().lastSessionSets(exerciseUuid)

    /**
     * Commits an active workout to history. Only completed sets are kept and
     * exercises without any completed set are dropped. Returns the saved id,
     * or null when nothing was logged.
     */
    suspend fun finishWorkout(active: ActiveWorkout, endedAt: Long = System.currentTimeMillis()): String? {
        val exercises = completedOnly(active.exercises)
        val cardio = completedCardio(active.cardio, endedAt)
        if (exercises.isEmpty() && cardio.isEmpty()) return null
        return saveWorkout(
            id = active.id,
            templateId = active.templateId,
            name = active.name,
            notes = active.notes,
            startedAt = active.startedAt,
            endedAt = endedAt,
            exercises = exercises,
            cardio = cardio,
        )
    }

    /** Re-saves an edited completed workout, recomputing facts and PRs. */
    suspend fun updateCompletedWorkout(original: WorkoutEntity, edited: ActiveWorkout) {
        val kept = edited.cardio.filter { it.completed }.map { it.id }.toSet()
        withContext(Dispatchers.IO) { routes.delete(original.cardio.map { it.id }.filter { it !in kept }) }
        saveWorkout(
            id = original.id,
            templateId = original.templateId,
            name = edited.name,
            notes = edited.notes,
            startedAt = original.startedAt,
            endedAt = original.endedAt,
            exercises = completedOnly(edited.exercises),
            cardio = edited.cardio.filter { it.completed },
        )
    }

    private fun completedOnly(exercises: List<ExerciseEntry>) = exercises
        .map { e ->
            e.copy(sets = e.sets.filter { it.completed && (it.reps ?: 0) > 0 }.map { s ->
                s.copy(dropStages = s.dropStages.filter { it.completed && (it.reps ?: 0) > 0 })
            })
        }
        .filter { it.sets.isNotEmpty() }

    /** Keeps finished cardio; a running timer is stopped and its time recorded. */
    private fun completedCardio(cardio: List<CardioBlock>, now: Long) = cardio.mapNotNull { c ->
        val elapsed = c.elapsedSeconds(now)
        val duration = c.durationSeconds ?: elapsed.takeIf { it > 0 }
        if (!c.completed && duration == null && c.distanceMeters == null) null
        else c.copy(completed = true, durationSeconds = duration, timerStartedAt = null, timerAccumulatedSeconds = elapsed)
    }

    /** Deleting a workout also removes the routes it recorded. */
    suspend fun deleteWorkout(id: String) {
        val cardio = db.workouts().allNow().firstOrNull { it.id == id }?.cardio.orEmpty().map { it.id }
        db.withTransaction {
            db.workouts().deleteFacts(id)
            db.workouts().delete(id)
        }
        withContext(Dispatchers.IO) { routes.delete(cardio) }
    }

    private suspend fun saveWorkout(
        id: String,
        templateId: String?,
        name: String,
        notes: String,
        startedAt: Long,
        endedAt: Long,
        exercises: List<ExerciseEntry>,
        cardio: List<CardioBlock>,
    ): String = db.withTransaction {
        val dao = db.workouts()
        var prCount = 0
        val facts = mutableListOf<SetFactEntity>()
        val marked = exercises.map { exercise ->
            val priorBest = dao.bestE1rm(exercise.exerciseUuid, excludeWorkoutId = id)
            var bestSetId: String? = null
            var bestE1rm = priorBest ?: 0.0
            val sets = exercise.sets.mapIndexed { index, set ->
                val reps = set.reps ?: 0
                val weight = set.weight
                val weightKg = weight?.takeIf { set.unit != LoadUnit.bodyweight }?.let { PerformanceMath.toKg(it, set.unit) }
                val e1rm = weightKg?.let { PerformanceMath.estimatedOneRepMax(it, reps) }
                if (!set.isWarmup && e1rm != null && priorBest != null && e1rm > bestE1rm) {
                    bestE1rm = e1rm
                    bestSetId = set.id
                }
                facts += SetFactEntity(
                    setId = set.id,
                    workoutId = id,
                    exerciseUuid = exercise.exerciseUuid,
                    exerciseName = exercise.name,
                    completedAt = endedAt,
                    setIndex = index,
                    isWarmup = set.isWarmup,
                    reps = reps,
                    weight = weight,
                    unit = set.unit,
                    weightKg = weightKg,
                    e1rmKg = e1rm,
                    volumeKg = if (set.isWarmup) 0.0 else (weight?.let { PerformanceMath.volumeKg(it, reps, set.unit) } ?: 0.0) +
                        set.dropStages.sumOf { d -> d.weight?.let { PerformanceMath.volumeKg(it, d.reps ?: 0, d.unit) } ?: 0.0 },
                )
                set
            }
            if (bestSetId != null) prCount++
            exercise.copy(sets = sets.map { it.copy(isPr = it.id == bestSetId) })
        }
        dao.deleteFacts(id)
        dao.upsertFacts(facts)
        dao.upsert(
            WorkoutEntity(
                id = id,
                templateId = templateId,
                name = name.ifBlank { "Workout" },
                startedAt = startedAt,
                endedAt = endedAt,
                durationSeconds = ((endedAt - startedAt) / 1000).toInt().coerceAtLeast(0),
                totalVolumeKg = facts.sumOf { it.volumeKg },
                prCount = prCount,
                notes = notes,
                exercises = marked,
                cardio = cardio,
            )
        )
        id
    }
}
