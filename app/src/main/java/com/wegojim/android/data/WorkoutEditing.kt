package com.wegojim.android.data

/**
 * Pure structure edits shared by the template editor, the active workout and
 * completed-workout editing. Keeping them here keeps the screens thin.
 */
object WorkoutEditing {

    fun updateExercise(list: List<ExerciseEntry>, id: String, f: (ExerciseEntry) -> ExerciseEntry) =
        list.map { if (it.id == id) f(it) else it }

    fun updateSet(list: List<ExerciseEntry>, exerciseId: String, setId: String, f: (SetEntry) -> SetEntry) =
        updateExercise(list, exerciseId) { e -> e.copy(sets = e.sets.map { if (it.id == setId) f(it) else it }) }

    fun updateDrop(list: List<ExerciseEntry>, exerciseId: String, setId: String, dropId: String, f: (DropStage) -> DropStage) =
        updateSet(list, exerciseId, setId) { s -> s.copy(dropStages = s.dropStages.map { if (it.id == dropId) f(it) else it }) }

    /** New sets copy the last set of the same kind so loads carry forward. */
    fun addSet(list: List<ExerciseEntry>, exerciseId: String, warmup: Boolean, defaultUnit: LoadUnit) =
        updateExercise(list, exerciseId) { e ->
            val like = e.sets.lastOrNull { it.isWarmup == warmup } ?: e.sets.lastOrNull()
            val set = SetEntry(
                isWarmup = warmup,
                targetReps = like?.targetReps,
                targetWeight = like?.targetWeight,
                reps = like?.reps,
                weight = like?.weight,
                unit = like?.unit ?: defaultUnit,
                dropStages = if (warmup) emptyList() else like?.dropStages.orEmpty().map { it.copy(id = newId(), completed = false) },
            )
            val sets = if (warmup) e.sets.toMutableList().apply { add(e.sets.indexOfLast { it.isWarmup } + 1, set) } else e.sets + set
            e.copy(sets = sets)
        }

    fun removeSet(list: List<ExerciseEntry>, exerciseId: String, setId: String) =
        updateExercise(list, exerciseId) { e -> e.copy(sets = e.sets.filterNot { it.id == setId }) }

    /** Adds a drop stage at roughly 80% of the previous load, like a typical strip-set. */
    fun addDrop(list: List<ExerciseEntry>, exerciseId: String, setId: String) = updateSet(list, exerciseId, setId) { s ->
        val base = s.dropStages.lastOrNull()?.let { it.weight ?: it.targetWeight } ?: s.weight ?: s.targetWeight
        val next = base?.let { roundLoad(it * 0.8, s.unit) }
        s.copy(dropStages = s.dropStages + DropStage(targetWeight = next, weight = next, targetReps = s.targetReps, unit = s.unit))
    }

    fun removeDrop(list: List<ExerciseEntry>, exerciseId: String, setId: String, dropId: String) =
        updateSet(list, exerciseId, setId) { s -> s.copy(dropStages = s.dropStages.filterNot { it.id == dropId }) }

    private fun roundLoad(value: Double, unit: LoadUnit): Double {
        val step = if (unit == LoadUnit.lb) 5.0 else 2.5
        return (Math.round(value / step) * step).coerceAtLeast(0.0)
    }

    /** Consecutive exercises with the same superset id move and render as one block. */
    fun blocks(list: List<ExerciseEntry>): List<List<ExerciseEntry>> {
        val out = mutableListOf<MutableList<ExerciseEntry>>()
        list.forEach { e ->
            val last = out.lastOrNull()
            if (e.supersetGroupId != null && last != null && last.first().supersetGroupId == e.supersetGroupId) last += e
            else out += mutableListOf(e)
        }
        return out
    }

    fun move(list: List<ExerciseEntry>, id: String, delta: Int): List<ExerciseEntry> {
        val blocks = blocks(list).toMutableList()
        val from = blocks.indexOfFirst { b -> b.any { it.id == id } }
        val to = from + delta
        if (from < 0 || to !in blocks.indices) return list
        blocks.add(to, blocks.removeAt(from))
        return blocks.flatten()
    }

    fun remove(list: List<ExerciseEntry>, id: String): List<ExerciseEntry> {
        val group = list.firstOrNull { it.id == id }?.supersetGroupId
        return list.filterNot { it.id == id }.map {
            if (group != null && it.supersetGroupId == group) it.copy(supersetGroupId = null, supersetRestSeconds = null) else it
        }
    }

    fun canSupersetWithNext(list: List<ExerciseEntry>, id: String): Boolean {
        val i = list.indexOfFirst { it.id == id }
        return i >= 0 && i + 1 < list.size && list[i].supersetGroupId == null && list[i + 1].supersetGroupId == null
    }

    fun supersetWithNext(list: List<ExerciseEntry>, id: String): List<ExerciseEntry> {
        if (!canSupersetWithNext(list, id)) return list
        val i = list.indexOfFirst { it.id == id }
        val group = newId()
        val rest = list[i].restSeconds
        return list.mapIndexed { index, e ->
            if (index == i || index == i + 1) e.copy(supersetGroupId = group, supersetRestSeconds = rest) else e
        }
    }

    fun unlinkSuperset(list: List<ExerciseEntry>, id: String): List<ExerciseEntry> {
        val group = list.firstOrNull { it.id == id }?.supersetGroupId ?: return list
        return list.map { if (it.supersetGroupId == group) it.copy(supersetGroupId = null, supersetRestSeconds = null) else it }
    }

    fun setSupersetRest(list: List<ExerciseEntry>, groupId: String, seconds: Int) =
        list.map { if (it.supersetGroupId == groupId) it.copy(supersetRestSeconds = seconds.coerceIn(0, 3600)) else it }

    /** "A1"/"A2" style labels for superset members, lettered in order of appearance. */
    fun supersetLabels(list: List<ExerciseEntry>): Map<String, String> {
        val labels = mutableMapOf<String, String>()
        var letter = 'A'
        blocks(list).filter { it.size > 1 }.forEach { block ->
            block.forEachIndexed { i, e -> labels[e.id] = "$letter${i + 1}" }
            letter++
        }
        return labels
    }

    /** Swaps the catalog exercise in a slot, keeping its sets and settings. */
    fun replaceExercise(list: List<ExerciseEntry>, id: String, with: ExerciseRef) = updateExercise(list, id) {
        it.copy(exerciseUuid = with.exerciseUuid, name = with.name, category = with.category, muscleSummary = with.muscleSummary)
    }

    // region template <-> workout conversion

    /** Fresh workout copy of a template; `pick` chooses the rotating component for each slot. */
    fun workoutFromTemplate(template: TemplateEntity, pick: (ExerciseEntry) -> ExerciseRef?): ActiveWorkout {
        val groupIds = mutableMapOf<String, String>()
        return ActiveWorkout(
            templateId = template.id,
            name = template.name,
            notes = template.notes,
            exercises = template.exercises.map { e ->
                val chosen = pick(e)
                val base = e.copy(
                    id = newId(),
                    templateExerciseId = e.id,
                    // In a session the alternatives include the template's primary, so it can be switched back.
                    components = if (e.components.isEmpty()) emptyList() else (listOf(e.ref) + e.components).distinctBy { it.exerciseUuid },
                    supersetGroupId = e.supersetGroupId?.let { groupIds.getOrPut(it) { newId() } },
                    sets = e.sets.map { s ->
                        s.copy(
                            id = newId(), weight = s.targetWeight, reps = s.targetReps, completed = false, isPr = false,
                            dropStages = s.dropStages.map { d -> d.copy(id = newId(), weight = d.targetWeight, reps = d.targetReps, completed = false) },
                        )
                    },
                )
                if (chosen != null) base.copy(exerciseUuid = chosen.exerciseUuid, name = chosen.name, category = chosen.category, muscleSummary = chosen.muscleSummary) else base
            },
            cardio = template.cardio.map { c -> c.copy(id = newId(), templateCardioId = c.id) },
        )
    }

    /** Template structure from a workout; logged values become the new targets. */
    fun templateExercisesFrom(exercises: List<ExerciseEntry>, keepIds: Boolean = false): List<ExerciseEntry> = exercises.map { e ->
        e.copy(
            id = if (keepIds) e.templateExerciseId ?: newId() else newId(),
            templateExerciseId = null,
            sets = e.sets.map { s ->
                SetEntry(
                    isWarmup = s.isWarmup,
                    targetReps = s.reps ?: s.targetReps,
                    targetWeight = s.weight ?: s.targetWeight,
                    unit = s.unit,
                    dropStages = s.dropStages.map { d -> DropStage(targetReps = d.reps ?: d.targetReps, targetWeight = d.weight ?: d.targetWeight, unit = d.unit) },
                )
            },
        )
    }

    fun templateCardioFrom(cardio: List<CardioBlock>, keepIds: Boolean = false) = cardio.map {
        CardioBlock(
            id = if (keepIds) it.templateCardioId ?: newId() else newId(),
            exerciseUuid = it.exerciseUuid, name = it.name, profile = it.profile, phase = it.phase, role = it.role,
            goal = it.goal, targetDurationSeconds = it.targetDurationSeconds, targetDistanceMeters = it.targetDistanceMeters,
            distanceUnit = it.distanceUnit,
        )
    }

    // endregion
}

/** Port of WorkoutCardioMetricsCalculator: pace, speed and rowing split. */
object CardioMetrics {
    data class Result(val paceSecondsPerUnit: Double?, val speedPerHour: Double?, val rowSplitSecondsPer500m: Double?)

    fun calculate(durationSeconds: Int?, distanceMeters: Double?, unit: DistanceUnit, profile: CardioProfile): Result {
        if (durationSeconds == null || durationSeconds <= 0 || distanceMeters == null || distanceMeters <= 0) return Result(null, null, null)
        val displayDistance = unit.fromMeters(distanceMeters)
        val pace = durationSeconds / displayDistance
        val speed = displayDistance / (durationSeconds / 3600.0)
        val split = durationSeconds * 500 / distanceMeters
        return Result(
            paceSecondsPerUnit = pace.takeIf { profile == CardioProfile.walkRun || profile == CardioProfile.treadmill },
            speedPerHour = speed.takeIf { profile == CardioProfile.walkRun || profile == CardioProfile.treadmill || profile == CardioProfile.machineDistance },
            rowSplitSecondsPer500m = split.takeIf { profile == CardioProfile.rower },
        )
    }
}

/** Structural differences between a template and a workout started from it. */
data class TemplateSyncPreview(
    val added: List<String>,
    val removed: List<String>,
    val edited: List<String>,
    val reordered: Boolean,
    val cardioChanged: Boolean,
    val notesChanged: Boolean,
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && edited.isEmpty() && !reordered && !cardioChanged && !notesChanged

    val lines: List<String>
        get() = buildList {
            added.forEach { add("Add $it") }
            removed.forEach { add("Remove $it") }
            edited.forEach { add(it) }
            if (reordered) add("Reorder exercises")
            if (cardioChanged) add("Update cardio")
            if (notesChanged) add("Update workout notes")
        }

    companion object {
        fun build(template: TemplateEntity, workout: ActiveWorkout): TemplateSyncPreview {
            val byId = template.exercises.associateBy { it.id }
            val matched = workout.exercises.filter { it.templateExerciseId in byId }
            val edited = matched.mapNotNull { e ->
                val t = byId.getValue(e.templateExerciseId!!)
                val changes = buildList {
                    if (t.exerciseUuid != e.exerciseUuid && t.components.none { it.exerciseUuid == e.exerciseUuid }) add("swap to ${e.name}")
                    if (t.workingSets.size != e.workingSets.size) add("${t.workingSets.size} → ${e.workingSets.size} sets")
                    val tw = t.sets.count { it.isWarmup }
                    val ew = e.sets.count { it.isWarmup }
                    if (tw != ew) add("$tw → $ew warm-ups")
                    if (t.restSeconds != e.restSeconds) add("rest ${t.restSeconds}s → ${e.restSeconds}s")
                    if ((t.supersetGroupId != null) != (e.supersetGroupId != null)) add(if (e.supersetGroupId != null) "now in a superset" else "no longer a superset")
                    if (t.hasDropsets != e.hasDropsets) add(if (e.hasDropsets) "add dropsets" else "remove dropsets")
                    if (t.notes.trim() != e.notes.trim()) add("notes")
                }
                if (changes.isEmpty()) null else "${t.name}: ${changes.joinToString(", ")}"
            }
            val templateOrder = template.exercises.map { it.id }.filter { id -> matched.any { it.templateExerciseId == id } }
            val cardioKey = { list: List<CardioBlock> -> list.map { Triple(it.exerciseUuid, it.role, it.goal to it.targetDurationSeconds) } }
            return TemplateSyncPreview(
                added = workout.exercises.filter { it.templateExerciseId !in byId }.map { it.name },
                removed = template.exercises.filter { t -> workout.exercises.none { it.templateExerciseId == t.id } }.map { it.name },
                edited = edited,
                reordered = matched.map { it.templateExerciseId } != templateOrder,
                cardioChanged = cardioKey(template.cardio) != cardioKey(workout.cardio.map {
                    it.copy(targetDurationSeconds = it.targetDurationSeconds)
                }),
                notesChanged = template.notes.trim() != workout.notes.trim(),
            )
        }
    }
}
