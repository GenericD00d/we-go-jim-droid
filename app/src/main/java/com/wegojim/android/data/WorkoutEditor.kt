package com.wegojim.android.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Editing surface shared by the live workout and completed-workout editing,
 * so both render with the same cards. Implementations provide state, update
 * and the hooks for side effects (rest timers, alarms).
 */
interface WorkoutEditor {
    val state: StateFlow<ActiveWorkout?>
    val defaultUnit: LoadUnit
    val defaultRestSeconds: Int

    /** Live sessions run rest and cardio timers; history edits don't. */
    val isLive: Boolean

    fun update(transform: (ActiveWorkout) -> ActiveWorkout)

    /** Called after a set or drop stage is checked off. */
    fun onSetCompleted(exercise: ExerciseEntry, set: SetEntry) {}

    fun onCardioTimerChanged(block: CardioBlock) {}

    /** Called after cardio blocks leave the workout, so their recorded routes can go too. */
    fun onCardioRemoved(ids: List<String>) {}

    private fun exercises(f: (List<ExerciseEntry>) -> List<ExerciseEntry>) = update { it.copy(exercises = f(it.exercises)) }

    fun updateExercise(id: String, f: (ExerciseEntry) -> ExerciseEntry) = exercises { WorkoutEditing.updateExercise(it, id, f) }
    fun updateSet(exerciseId: String, setId: String, f: (SetEntry) -> SetEntry) = exercises { WorkoutEditing.updateSet(it, exerciseId, setId, f) }
    fun updateDrop(exerciseId: String, setId: String, dropId: String, f: (DropStage) -> DropStage) =
        exercises { WorkoutEditing.updateDrop(it, exerciseId, setId, dropId, f) }

    fun addSet(exerciseId: String, warmup: Boolean = false) = exercises { WorkoutEditing.addSet(it, exerciseId, warmup, defaultUnit) }
    fun removeSet(exerciseId: String, setId: String) = exercises { WorkoutEditing.removeSet(it, exerciseId, setId) }
    fun addDrop(exerciseId: String, setId: String) = exercises { WorkoutEditing.addDrop(it, exerciseId, setId) }
    fun removeDrop(exerciseId: String, setId: String, dropId: String) = exercises { WorkoutEditing.removeDrop(it, exerciseId, setId, dropId) }
    fun moveExercise(id: String, delta: Int) = exercises { WorkoutEditing.move(it, id, delta) }
    fun removeExercise(id: String) = exercises { WorkoutEditing.remove(it, id) }
    fun supersetWithNext(id: String) = exercises { WorkoutEditing.supersetWithNext(it, id) }
    fun unlinkSuperset(id: String) = exercises { WorkoutEditing.unlinkSuperset(it, id) }
    fun setSupersetRest(groupId: String, seconds: Int) = exercises { WorkoutEditing.setSupersetRest(it, groupId, seconds) }
    fun replaceExercise(id: String, with: ExerciseRef) = exercises { WorkoutEditing.replaceExercise(it, id, with) }

    fun addExercises(items: List<ExerciseEntity>) = exercises { list ->
        list + items.map { newExerciseEntry(it, defaultUnit, defaultRestSeconds) }
    }

    /**
     * Toggles completion. An untouched set logs its target, or last session's
     * numbers. Returns false when no reps are known, so nothing is logged.
     */
    fun toggleSetCompleted(exerciseId: String, setId: String, fallback: SetFactEntity? = null): Boolean {
        val exercise = state.value?.exercises?.firstOrNull { it.id == exerciseId } ?: return false
        val set = exercise.sets.firstOrNull { it.id == setId } ?: return false
        if (set.completed) {
            updateSet(exerciseId, setId) { it.copy(completed = false) }
            return true
        }
        val reps = set.reps ?: set.targetReps ?: fallback?.reps
        if (reps == null || reps <= 0) return false
        val useFallback = set.reps == null && set.targetReps == null && fallback != null
        val done = set.copy(
            completed = true,
            reps = reps,
            weight = set.weight ?: set.targetWeight ?: fallback?.weight?.takeIf { useFallback },
            unit = if (useFallback && set.weight == null) fallback!!.unit else set.unit,
        )
        updateSet(exerciseId, setId) { done }
        if (done.isCycleCompleted) state.value?.exercises?.firstOrNull { it.id == exerciseId }?.let { onSetCompleted(it, done) }
        return true
    }

    /** Drop stages complete in sequence; rest starts after the last one. */
    fun toggleDropCompleted(exerciseId: String, setId: String, dropId: String): Boolean {
        val exercise = state.value?.exercises?.firstOrNull { it.id == exerciseId } ?: return false
        val set = exercise.sets.firstOrNull { it.id == setId } ?: return false
        val drop = set.dropStages.firstOrNull { it.id == dropId } ?: return false
        if (drop.completed) {
            updateDrop(exerciseId, setId, dropId) { it.copy(completed = false) }
            return true
        }
        val reps = drop.reps ?: drop.targetReps
        if (reps == null || reps <= 0) return false
        updateDrop(exerciseId, setId, dropId) { it.copy(completed = true, reps = reps, weight = it.weight ?: it.targetWeight) }
        val updated = state.value?.exercises?.firstOrNull { it.id == exerciseId }
        val done = updated?.sets?.firstOrNull { it.id == setId }
        if (updated != null && done != null && done.isCycleCompleted) onSetCompleted(updated, done)
        return true
    }

    // region cardio

    fun addCardio(block: CardioBlock) = update { it.copy(cardio = it.cardio + block) }
    fun removeCardio(id: String) {
        update { it.copy(cardio = it.cardio.filterNot { c -> c.id == id }) }
        onCardioRemoved(listOf(id))
    }
    fun updateCardio(id: String, f: (CardioBlock) -> CardioBlock) = update { w -> w.copy(cardio = w.cardio.map { if (it.id == id) f(it) else it }) }

    /** Returns false when another activity's timer is running; only one records at a time. */
    fun startCardioTimer(id: String): Boolean {
        if (state.value?.cardio?.any { it.id != id && it.isRunning } == true) return false
        updateCardio(id) { if (it.isRunning) it else it.copy(timerStartedAt = System.currentTimeMillis(), completed = false) }
        state.value?.cardio?.firstOrNull { it.id == id }?.let(::onCardioTimerChanged)
        return true
    }

    fun pauseCardioTimer(id: String) {
        updateCardio(id) { it.copy(timerAccumulatedSeconds = it.elapsedSeconds(), timerStartedAt = null) }
        state.value?.cardio?.firstOrNull { it.id == id }?.let(::onCardioTimerChanged)
    }

    fun resetCardioTimer(id: String) {
        updateCardio(id) { it.copy(timerAccumulatedSeconds = 0, timerStartedAt = null) }
        state.value?.cardio?.firstOrNull { it.id == id }?.let(::onCardioTimerChanged)
    }

    /** Completing stops the timer and records its time unless one was typed in. */
    fun toggleCardioCompleted(id: String) {
        updateCardio(id) { c ->
            if (c.completed) c.copy(completed = false)
            else {
                val elapsed = c.elapsedSeconds()
                c.copy(
                    completed = true,
                    timerStartedAt = null,
                    timerAccumulatedSeconds = elapsed,
                    durationSeconds = c.durationSeconds ?: elapsed.takeIf { it > 0 } ?: c.targetDurationSeconds.takeIf { c.goal == CardioGoal.time },
                    distanceMeters = c.distanceMeters ?: c.targetDistanceMeters.takeIf { c.goal == CardioGoal.distance },
                )
            }
        }
        state.value?.cardio?.firstOrNull { it.id == id }?.let(::onCardioTimerChanged)
    }

    /**
     * Ends a recorded activity: the timer's time becomes the result, and a GPS
     * route's distance is used when one was recorded.
     */
    fun finishCardio(id: String, recordedDistanceMeters: Double?) {
        updateCardio(id) { c ->
            val elapsed = c.elapsedSeconds()
            c.copy(
                completed = true,
                timerStartedAt = null,
                timerAccumulatedSeconds = elapsed,
                durationSeconds = elapsed.takeIf { it > 0 } ?: c.durationSeconds,
                distanceMeters = recordedDistanceMeters?.takeIf { it > 0 } ?: c.distanceMeters,
            )
        }
        state.value?.cardio?.firstOrNull { it.id == id }?.let(::onCardioTimerChanged)
    }

    /** Saves a reviewed result; the activity counts as completed afterwards. */
    fun saveCardioResult(id: String, result: CardioResult) {
        updateCardio(id) { c ->
            c.copy(
                durationSeconds = result.durationSeconds,
                distanceMeters = result.distanceMeters,
                distanceUnit = result.distanceUnit,
                inclinePercent = result.inclinePercent,
                resistanceLevel = result.resistanceLevel,
                notes = result.notes,
                completed = true,
                timerAccumulatedSeconds = if (c.completed) c.timerAccumulatedSeconds else c.elapsedSeconds(),
                timerStartedAt = null,
            )
        }
        state.value?.cardio?.firstOrNull { it.id == id }?.let(::onCardioTimerChanged)
    }

    // endregion
}

fun newExerciseEntry(exercise: ExerciseEntity, unit: LoadUnit, restSeconds: Int, setCount: Int = 3) = ExerciseEntry(
    exerciseUuid = exercise.uuid,
    name = exercise.name,
    category = exercise.category,
    muscleSummary = exercise.muscleSummary,
    restSeconds = restSeconds,
    sets = List(setCount) { SetEntry(unit = if (exercise.equipment.contains("Bodyweight", ignoreCase = true)) LoadUnit.bodyweight else unit) },
)

fun ExerciseEntity.toRef() = ExerciseRef(uuid, name, category, muscleSummary)

fun newCardioBlock(exercise: ExerciseEntity, phase: CardioPhase, role: CardioRole, distanceUnit: DistanceUnit): CardioBlock {
    val profile = CardioProfile.of(exercise.cardioProfile)
    return CardioBlock(
        exerciseUuid = exercise.uuid,
        name = exercise.name,
        profile = profile,
        phase = phase,
        role = role,
        goal = CardioGoal.time,
        targetDurationSeconds = if (phase == CardioPhase.preWorkout) 5 * 60 else 20 * 60,
        distanceUnit = distanceUnit,
    )
}
