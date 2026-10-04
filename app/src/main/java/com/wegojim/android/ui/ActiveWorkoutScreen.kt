package com.wegojim.android.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wegojim.android.app
import com.wegojim.android.data.ActiveWorkout
import com.wegojim.android.data.ActiveWorkoutStore
import com.wegojim.android.data.ExerciseEntry
import com.wegojim.android.data.LoadUnit
import com.wegojim.android.data.SetEntry
import com.wegojim.android.data.SetFactEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(nav: NavHostController) {
    val app = LocalContext.current.app
    val store = app.activeWorkout
    val workout by store.state.collectAsStateWithLifecycle()
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var picking by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    // Leave the screen once the workout has been finished or discarded.
    val w = workout
    if (w == null) {
        // Guarded: after Finish we've already navigated to the summary.
        LaunchedEffect(Unit) { if (nav.currentDestination?.route == Routes.WORKOUT) nav.popBackStack() }
        return
    }

    val view = LocalView.current
    DisposableEffect(settings.keepScreenAwake) {
        view.keepScreenOn = settings.keepScreenAwake
        onDispose { view.keepScreenOn = false }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val elapsed = rememberElapsedSeconds(w.startedAt)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.KeyboardArrowDown, "Minimize") } },
                title = {
                    Column(Modifier.clickableNoRipple { renaming = true }) {
                        Text(w.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(formatClock(elapsed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                },
                actions = {
                    Button(onClick = { confirmFinish = true }) { Text("Finish") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Workout actions") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename workout") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(
                                text = { Text("Cancel workout", color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmCancel = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = { RestTimerDock(w, store) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { WorkoutProgressHeader(w) }
            if (w.exercises.isEmpty()) {
                item { EmptyState(Icons.Default.Add, "No exercises yet", "Add exercises to start logging sets.") }
            }
            items(w.exercises, key = { it.id }) { exercise ->
                ActiveExerciseCard(exercise, store)
            }
            item {
                Button(onClick = { picking = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add Exercises")
                }
            }
            item {
                OutlinedTextField(
                    value = w.notes,
                    onValueChange = { v -> store.update { it.copy(notes = v) } },
                    label = { Text("Workout notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                TextButton(onClick = { confirmCancel = true }, Modifier.fillMaxWidth()) {
                    Text("Cancel Workout", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (picking) {
        ExercisePickerDialog(onDismiss = { picking = false }, onConfirm = { store.addExercises(it); picking = false })
    }

    if (renaming) {
        var name by remember { mutableStateOf(w.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename workout") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    store.update { it.copy(name = name.trim()) }
                    renaming = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }

    if (confirmFinish) {
        val done = w.exercises.sumOf { e -> e.sets.count { it.completed && (it.reps ?: 0) > 0 } }
        val open = w.exercises.sumOf { e -> e.sets.count { !it.completed || (it.reps ?: 0) <= 0 } }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(if (done == 0) "Nothing logged yet" else "Finish workout?") },
            text = {
                Text(
                    when {
                        done == 0 -> "Complete at least one set (with reps) to save this workout, or cancel it instead."
                        open > 0 -> "${plural(done, "set")} completed. ${plural(open, "unfinished set")} will be left out."
                        else -> "All ${plural(done, "set")} completed. Nice work."
                    }
                )
            },
            confirmButton = {
                if (done > 0) {
                    TextButton(onClick = {
                        confirmFinish = false
                        scope.launch {
                            val id = store.finish()
                            if (id != null) {
                                nav.navigate(Routes.workoutDetail(id, summary = true)) {
                                    popUpTo(Routes.START)
                                }
                            }
                        }
                    }) { Text("Finish") }
                }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep Going") } },
        )
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel workout?") },
            text = { Text("All progress in this session will be discarded.") },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; store.discard() }) {
                    Text("Discard Workout", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep Going") } },
        )
    }
}

@Composable
private fun WorkoutProgressHeader(w: ActiveWorkout) {
    val total = w.exercises.sumOf { it.sets.size }
    val done = w.exercises.sumOf { e -> e.sets.count { it.completed } }
    SectionCard {
        Row {
            Text("$done of $total sets", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("${w.exercises.size} exercises", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done / total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = Wgj.colors.success,
        )
    }
}

@Composable
private fun ActiveExerciseCard(exercise: ExerciseEntry, store: ActiveWorkoutStore) {
    val repo = LocalContext.current.app.repository
    val previous by produceState(emptyList<SetFactEntity>(), exercise.exerciseUuid) {
        value = repo.lastSessionSets(exercise.exerciseUuid)
    }
    val previousWarmups = previous.filter { it.isWarmup }
    val previousWorking = previous.filter { !it.isWarmup }

    SectionCard {
        ExerciseCardHeader(
            exercise,
            onMove = { store.moveExercise(exercise.id, it) },
            onRemove = { store.removeExercise(exercise.id) },
        )
        if (exercise.notes.isNotBlank()) {
            Text(exercise.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val repRange = when {
            exercise.targetRepMin != null && exercise.targetRepMax != null && exercise.targetRepMin != exercise.targetRepMax ->
                "Target ${exercise.targetRepMin}-${exercise.targetRepMax} reps"
            exercise.targetRepMin != null -> "Target ${exercise.targetRepMin} reps"
            else -> null
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Timer, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                " Rest ${formatRest(exercise.restSeconds)}" + (repRange?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { store.updateExercise(exercise.id) { it.copy(restSeconds = (it.restSeconds - 15).coerceAtLeast(0)) } }) { Text("−15s") }
            TextButton(onClick = { store.updateExercise(exercise.id) { it.copy(restSeconds = it.restSeconds + 15) } }) { Text("+15s") }
        }

        var warmupIndex = 0
        var workingIndex = 0
        exercise.sets.forEach { set ->
            val prev = if (set.isWarmup) previousWarmups.getOrNull(warmupIndex++) else previousWorking.getOrNull(workingIndex++)
            val label = if (set.isWarmup) "W" else workingIndex.toString()
            ActiveSetRow(exercise, set, label, prev, store)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { store.addSet(exercise.id) }, Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text("Add Set") }
            TextButton(onClick = { store.addSet(exercise.id, warmup = true) }) { Text("+ Warm-up") }
        }
    }
}

@Composable
private fun ActiveSetRow(
    exercise: ExerciseEntry,
    set: SetEntry,
    label: String,
    previous: SetFactEntity?,
    store: ActiveWorkoutStore,
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val background by animateColorAsState(
        if (set.completed) Wgj.colors.success.copy(alpha = 0.14f) else Color.Transparent,
        label = "setBackground",
    )
    Surface(color = background, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SetBadge(
                    label = label,
                    set = set,
                    onToggleWarmup = { store.updateSet(exercise.id, set.id) { it.copy(isWarmup = !it.isWarmup) } },
                    onUnit = { unit -> store.updateSet(exercise.id, set.id) { it.copy(unit = unit) } },
                    onDelete = { store.removeSet(exercise.id, set.id) },
                )
                NumberField(
                    value = set.weight,
                    onValueChange = { v -> store.updateSet(exercise.id, set.id) { it.copy(weight = v) } },
                    modifier = Modifier.weight(1f),
                    placeholder = if (set.unit == LoadUnit.bodyweight) "+0" else set.targetWeight?.let(::formatNumber) ?: "0",
                    suffix = set.unit.label,
                )
                NumberField(
                    value = set.reps?.toDouble(),
                    onValueChange = { v -> store.updateSet(exercise.id, set.id) { it.copy(reps = v?.toInt()) } },
                    modifier = Modifier.weight(0.75f),
                    placeholder = set.targetReps?.toString() ?: "reps",
                    integer = true,
                )
                FilledIconToggleButton(
                    checked = set.completed,
                    onCheckedChange = {
                        if (store.toggleSetCompleted(exercise.id, set.id, previous)) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else {
                            Toast.makeText(context, "Enter reps first", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        checkedContainerColor = Wgj.colors.success,
                        checkedContentColor = MaterialTheme.colorScheme.surface,
                    ),
                ) { Icon(Icons.Default.Check, if (set.completed) "Mark set incomplete" else "Complete set") }
            }
            if (previous != null) {
                Row(Modifier.padding(start = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Last: ${formatSet(SetEntry(reps = previous.reps, weight = previous.weight, unit = previous.unit))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (!set.completed && (set.weight != previous.weight || set.reps != previous.reps || set.unit != previous.unit)) {
                        TextButton(onClick = {
                            store.updateSet(exercise.id, set.id) { it.copy(weight = previous.weight, reps = previous.reps, unit = previous.unit) }
                        }) { Text("Fill Last") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RestTimerDock(workout: ActiveWorkout, store: ActiveWorkoutStore) {
    val endsAt = workout.restEndsAt ?: return
    val haptics = LocalHapticFeedback.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            now = System.currentTimeMillis()
            delay(200)
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        store.finishRest(endsAt)
    }
    val remaining = ((endsAt - now + 999) / 1000).coerceAtLeast(0)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rest Timer", style = MaterialTheme.typography.labelMedium, color = Wgj.colors.success)
                    Text(workout.restLabel ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { store.adjustRest(-15) }) { Text("−15") }
                Text(formatClock(remaining), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Wgj.colors.success)
                TextButton(onClick = { store.adjustRest(15) }) { Text("+15") }
                IconButton(onClick = { store.skipRest() }) { Icon(Icons.Default.Close, "Skip rest") }
            }
            LinearProgressIndicator(
                progress = { if (workout.restTotalSeconds == 0) 0f else (remaining.toFloat() / workout.restTotalSeconds).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = Wgj.colors.success,
            )
        }
    }
}

@Composable
fun PrBadge() {
    Surface(color = Wgj.colors.gold.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.EmojiEvents, null, Modifier.size(14.dp), tint = Wgj.colors.gold)
            Text(" PR", style = MaterialTheme.typography.labelSmall, color = Wgj.colors.gold, fontWeight = FontWeight.Bold)
        }
    }
}
