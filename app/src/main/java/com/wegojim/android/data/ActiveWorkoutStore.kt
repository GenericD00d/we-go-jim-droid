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
) : WorkoutEditor {
    private val file = File(context.filesDir, "active_workout.json")
    private val _state = MutableStateFlow(load())
    override val state: StateFlow<ActiveWorkout?> = _state.asStateFlow()

    override val defaultUnit: LoadUnit get() = settings.state.value.weightUnit
    override val defaultRestSeconds: Int get() = settings.state.value.defaultRestSeconds
    override val isLive: Boolean = true

    init {
        _state.drop(1).debounce(300).onEach { persist(it) }.flowOn(Dispatchers.IO).launchIn(scope)
    }

    val isActive: Boolean get() = _state.value != null

    fun startEmpty() = start(ActiveWorkout(name = "Workout"))

    suspend fun startFromTemplate(template: TemplateEntity) {
        val picks = repository.rotationPicks(template)
        start(WorkoutEditing.workoutFromTemplate(template) { picks[it.id] })
    }

    private fun start(workout: ActiveWorkout) {
        _state.value = workout
        persist(workout)
    }

    override fun update(transform: (ActiveWorkout) -> ActiveWorkout) = _state.update { it?.let(transform) }

    fun discard() {
        RestTimerAlarm.cancel(context)
        _state.value?.cardio?.forEach { RestTimerAlarm.cancelCardio(context, it.id) }
        _state.value = null
        persist(null)
    }

    /** Saves to history. Returns the workout id, or null if nothing was logged. */
    suspend fun finish(syncTemplate: Boolean = false): String? {
        val workout = _state.value ?: return null
        if (syncTemplate && workout.templateId != null) repository.applyWorkoutToTemplate(workout.templateId, workout)
        val id = repository.finishWorkout(workout)
        discard()
        return id
    }

    suspend fun templateSyncPreview(): TemplateSyncPreview? {
        val workout = _state.value ?: return null
        val template = workout.templateId?.let { repository.template(it) } ?: return null
        return TemplateSyncPreview.build(template, workout).takeUnless { it.isEmpty }
    }

    /**
     * Rest follows a completed set, except between the two halves of a
     * superset, where the round rest starts after the second exercise.
     */
    override fun onSetCompleted(exercise: ExerciseEntry, set: SetEntry) {
        if (!settings.state.value.autoStartRestTimer) return
        val exercises = _state.value?.exercises ?: return
        val group = exercise.supersetGroupId
        val rest: Int
        val label: String
        if (group != null) {
            val members = exercises.filter { it.supersetGroupId == group }
            if (members.lastOrNull()?.id != exercise.id) return
            rest = exercise.supersetRestSeconds ?: exercise.restSeconds
            label = members.joinToString(" + ") { it.name }
        } else {
            rest = exercise.restSeconds
            val index = exercise.sets.indexOfFirst { it.id == set.id }
            label = exercise.name + " · " + if (set.isWarmup) "Warm-up" else "Set ${exercise.sets.take(index + 1).count { !it.isWarmup }}"
        }
        if (rest > 0) startRest(rest, label)
    }

    override fun onCardioTimerChanged(block: CardioBlock) {
        val remaining = block.targetDurationSeconds - block.elapsedSeconds()
        if (block.isRunning && block.goal == CardioGoal.time && remaining > 0) {
            RestTimerAlarm.scheduleCardio(context, block.id, System.currentTimeMillis() + remaining * 1000L, block.name)
        } else {
            RestTimerAlarm.cancelCardio(context, block.id)
        }
    }

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

fun <T> List<T>.moved(id: String, delta: Int, idOf: (T) -> String): List<T> {
    val from = indexOfFirst { idOf(it) == id }
    val to = from + delta
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
