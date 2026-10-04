package com.wegojim.android.data

import android.content.Context
import com.wegojim.android.RestTimerAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Memory-first active workout state, snapshotted to a local file so the
 * session survives process death and relaunch (like the iOS draft store).
 */
@OptIn(FlowPreview::class)
class ActiveWorkoutStore(
    private val context: Context,
    private val repository: Repository,
    private val settings: SettingsStore,
    scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "active_workout.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ActiveWorkout?> = _state.asStateFlow()

    init {
        _state.drop(1).debounce(300).onEach { persist(it) }.flowOn(Dispatchers.IO).launchIn(scope)
    }

    val isActive: Boolean get() = _state.value != null

    fun startEmpty() = start(ActiveWorkout(name = "Workout"))

    fun startFromTemplate(template: TemplateEntity) = start(
        ActiveWorkout(
            templateId = template.id,
            name = template.name,
            notes = template.notes,
            exercises = template.exercises.map { e ->
                e.copy(id = newId(), sets = e.sets.map { s ->
                    s.copy(id = newId(), weight = s.targetWeight, reps = s.targetReps, completed = false, isPr = false)
                })
            },
        )
    )

    private fun start(workout: ActiveWorkout) {
        _state.value = workout
        persist(workout)
    }

    fun update(transform: (ActiveWorkout) -> ActiveWorkout) = _state.update { it?.let(transform) }

    fun discard() {
        RestTimerAlarm.cancel(context)
        _state.value = null
        persist(null)
    }

    /** Saves to history. Returns the workout id, or null if nothing was logged. */
    suspend fun finish(): String? {
        val workout = _state.value ?: return null
        val id = repository.finishWorkout(workout)
        discard()
        return id
    }

    // region set editing

    fun updateExercise(exerciseId: String, transform: (ExerciseEntry) -> ExerciseEntry) = update { w ->
        w.copy(exercises = w.exercises.map { if (it.id == exerciseId) transform(it) else it })
    }

    fun updateSet(exerciseId: String, setId: String, transform: (SetEntry) -> SetEntry) =
        updateExercise(exerciseId) { e -> e.copy(sets = e.sets.map { if (it.id == setId) transform(it) else it }) }

    /** Returns false when the set can't be completed because no reps are known. */
    fun toggleSetCompleted(exerciseId: String, setId: String, fallback: SetFactEntity? = null): Boolean {
        val workout = _state.value ?: return false
        val exercise = workout.exercises.firstOrNull { it.id == exerciseId } ?: return false
        val index = exercise.sets.indexOfFirst { it.id == setId }
        val set = exercise.sets.getOrNull(index) ?: return false
        val completing = !set.completed
        if (completing) {
            // An untouched set logs its prescribed target, or last session's numbers.
            val reps = set.reps ?: set.targetReps ?: fallback?.reps
            if (reps == null || reps <= 0) return false
            val useFallback = set.reps == null && set.targetReps == null
            updateSet(exerciseId, setId) {
                it.copy(
                    completed = true,
                    reps = reps,
                    weight = it.weight ?: it.targetWeight ?: fallback?.weight?.takeIf { useFallback },
                    unit = if (useFallback && it.weight == null && fallback != null) fallback.unit else it.unit,
                )
            }
        } else {
            updateSet(exerciseId, setId) { it.copy(completed = false) }
        }
        if (completing && settings.state.value.autoStartRestTimer && exercise.restSeconds > 0) {
            val label = if (set.isWarmup) "Warm-up" else "Set ${exercise.sets.take(index + 1).count { !it.isWarmup }}"
            startRest(exercise.restSeconds, "${exercise.name} · $label")
        }
        return true
    }

    fun addSet(exerciseId: String, warmup: Boolean = false) = updateExercise(exerciseId) { e ->
        val template = (if (warmup) e.sets.lastOrNull { it.isWarmup } else e.sets.lastOrNull { !it.isWarmup }) ?: e.sets.lastOrNull()
        val newSet = SetEntry(
            isWarmup = warmup,
            weight = template?.weight,
            reps = template?.reps,
            targetReps = template?.targetReps,
            targetWeight = template?.targetWeight,
            unit = template?.unit ?: settings.state.value.weightUnit,
        )
        val sets = if (warmup) {
            val insertAt = e.sets.indexOfLast { it.isWarmup } + 1
            e.sets.toMutableList().apply { add(insertAt, newSet) }
        } else e.sets + newSet
        e.copy(sets = sets)
    }

    fun removeSet(exerciseId: String, setId: String) =
        updateExercise(exerciseId) { e -> e.copy(sets = e.sets.filterNot { it.id == setId }) }

    fun addExercises(items: List<ExerciseEntity>) = update { w ->
        w.copy(exercises = w.exercises + items.map { newExerciseEntry(it, settings.state.value) })
    }

    fun removeExercise(exerciseId: String) = update { w -> w.copy(exercises = w.exercises.filterNot { it.id == exerciseId }) }

    fun moveExercise(exerciseId: String, delta: Int) = update { w -> w.copy(exercises = w.exercises.moved(exerciseId, delta) { it.id }) }

    // endregion

    // region rest timer

    fun startRest(seconds: Int, label: String) {
        val endsAt = System.currentTimeMillis() + seconds * 1000L
        update { it.copy(restEndsAt = endsAt, restTotalSeconds = seconds, restLabel = label) }
        RestTimerAlarm.schedule(context, endsAt, label)
    }

    fun adjustRest(deltaSeconds: Int) {
        val workout = _state.value ?: return
        val endsAt = workout.restEndsAt ?: return
        val next = maxOf(System.currentTimeMillis(), endsAt + deltaSeconds * 1000L)
        update { it.copy(restEndsAt = next, restTotalSeconds = maxOf(1, it.restTotalSeconds + deltaSeconds)) }
        RestTimerAlarm.schedule(context, next, workout.restLabel ?: "Rest")
    }

    /** Natural end of a rest period: clear the dock but leave the alarm's notification alone. */
    fun finishRest(endsAt: Long) = update { if (it.restEndsAt == endsAt) it.copy(restEndsAt = null, restLabel = null) else it }

    fun skipRest() {
        RestTimerAlarm.cancel(context)
        update { it.copy(restEndsAt = null, restLabel = null) }
    }

    // endregion

    private fun load(): ActiveWorkout? = runCatching {
        if (file.exists()) AppJson.decodeFromString(ActiveWorkout.serializer(), file.readText()) else null
    }.getOrNull()

    @Synchronized
    private fun persist(workout: ActiveWorkout?) {
        if (workout == null) {
            file.delete()
            return
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(AppJson.encodeToString(ActiveWorkout.serializer(), workout))
        tmp.renameTo(file)
    }
}

fun newExerciseEntry(exercise: ExerciseEntity, settings: UserSettings, setCount: Int = 3) = ExerciseEntry(
    exerciseUuid = exercise.uuid,
    name = exercise.name,
    category = exercise.category,
    muscleSummary = exercise.muscleSummary,
    restSeconds = settings.defaultRestSeconds,
    sets = List(setCount) { SetEntry(unit = settings.weightUnit) },
)

fun <T> List<T>.moved(id: String, delta: Int, idOf: (T) -> String): List<T> {
    val from = indexOfFirst { idOf(it) == id }
    val to = from + delta
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
