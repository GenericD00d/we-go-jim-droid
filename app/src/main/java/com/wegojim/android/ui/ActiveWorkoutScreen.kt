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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.wegojim.android.data.CardioBlock
import com.wegojim.android.data.CardioPhase
import com.wegojim.android.data.CardioRole
import com.wegojim.android.data.DropStage
import com.wegojim.android.data.ExerciseEntry
import com.wegojim.android.data.LoadUnit
import com.wegojim.android.data.SetEntry
import com.wegojim.android.data.SetFactEntity
import com.wegojim.android.data.TemplateSyncPreview
import com.wegojim.android.data.WorkoutEditing
import com.wegojim.android.data.WorkoutEditor
import com.wegojim.android.data.newCardioBlock
import com.wegojim.android.data.toRef
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
    val context = LocalContext.current

    var renaming by remember { mutableStateOf(false) }
    var savingTemplate by remember { mutableStateOf(false) }
    var syncPreview by remember { mutableStateOf<TemplateSyncPreview?>(null) }
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
                    Button(onClick = {
                        scope.launch {
                            syncPreview = store.templateSyncPreview()
                            confirmFinish = true
                        }
                    }) { Text("Finish") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Workout actions") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename workout") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Save as new template") }, onClick = { menu = false; savingTemplate = true })
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
            workoutEditorItems(w, store)
            item {
                TextButton(onClick = { confirmCancel = true }, Modifier.fillMaxWidth()) {
                    Text("Cancel Workout", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (renaming) {
        NameDialog("Rename workout", w.name, onDismiss = { renaming = false }) { name ->
            store.update { it.copy(name = name) }
            renaming = false
        }
    }

    if (savingTemplate) {
        NameDialog("Save as template", w.name, onDismiss = { savingTemplate = false }) { name ->
            savingTemplate = false
            scope.launch {
                app.repository.saveAsTemplate(name, w.exercises, w.cardio, w.notes)
                Toast.makeText(context, "Template \"$name\" saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (confirmFinish) {
        FinishDialog(
            workout = w,
            sync = syncPreview,
            onDismiss = { confirmFinish = false },
            onFinish = { syncTemplate ->
                confirmFinish = false
                scope.launch {
                    val id = store.finish(syncTemplate)
                    if (id != null) {
                        nav.navigate(Routes.workoutDetail(id, summary = true)) { popUpTo(Routes.START) }
                    }
                }
            },
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
private fun FinishDialog(workout: ActiveWorkout, sync: TemplateSyncPreview?, onDismiss: () -> Unit, onFinish: (Boolean) -> Unit) {
    val done = workout.exercises.sumOf { e -> e.sets.count { it.completed && (it.reps ?: 0) > 0 } }
    val open = workout.exercises.sumOf { e -> e.sets.count { !it.completed || (it.reps ?: 0) <= 0 } }
    val cardio = workout.cardio.count { it.completed || it.durationSeconds != null || it.elapsedSeconds() > 0 }
    val nothing = done == 0 && cardio == 0
    var syncTemplate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (nothing) "Nothing logged yet" else "Finish workout?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        nothing -> "Complete at least one set or cardio block to save this workout, or cancel it instead."
                        open > 0 -> "${plural(done, "set")} completed. ${plural(open, "unfinished set")} will be left out."
                        else -> "All ${plural(done, "set")} completed. Nice work."
                    }
                )
                if (!nothing && sync != null) {
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = syncTemplate, onCheckedChange = { syncTemplate = it })
                        Text("Update the template with today's changes", fontWeight = FontWeight.SemiBold)
                    }
                    sync.lines.take(8).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    if (sync.lines.size > 8) Text("…and ${sync.lines.size - 8} more", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { if (!nothing) TextButton(onClick = { onFinish(syncTemplate) }) { Text("Finish") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep Going") } },
    )
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String = "Save", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The exercise, superset and cardio cards plus add buttons, shared by the
 * live workout and completed-workout editing.
 */
fun LazyListScope.workoutEditorItems(w: ActiveWorkout, editor: WorkoutEditor) {
    val labels = WorkoutEditing.supersetLabels(w.exercises)
    val blocks = WorkoutEditing.blocks(w.exercises)
    items(w.cardio.filter { it.phase == CardioPhase.preWorkout }, key = { it.id }) { ActiveCardioCard(it, editor) }
    if (w.exercises.isEmpty() && w.cardio.isEmpty()) {
        item { EmptyState(Icons.Default.Add, "No exercises yet", "Add exercises or cardio to start logging.") }
    }
    blocks.forEach { block ->
        if (block.size > 1) {
            item(key = "superset-" + block.first().supersetGroupId) { SupersetHeader(block, labels, editor) }
        }
        items(block, key = { it.id }) { exercise ->
            ExerciseCard(exercise, labels[exercise.id], w.exercises, editor)
        }
    }
    items(w.cardio.filter { it.phase == CardioPhase.postWorkout }, key = { it.id }) { ActiveCardioCard(it, editor) }
    item { AddButtons(editor, hasExercises = w.exercises.isNotEmpty()) }
    item {
        OutlinedTextField(
            value = w.notes,
            onValueChange = { v -> editor.update { it.copy(notes = v) } },
            label = { Text("Workout notes") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AddButtons(editor: WorkoutEditor, hasExercises: Boolean) {
    val distanceUnit = LocalContext.current.app.settings.state.collectAsStateWithLifecycle().value.distanceUnit
    var picking by remember { mutableStateOf(false) }
    var pickingCardio by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf<CardioBlock?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { picking = true }, Modifier.weight(1f)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Exercises")
        }
        OutlinedButton(onClick = { pickingCardio = true }, Modifier.weight(1f)) {
            Icon(Icons.AutoMirrored.Filled.DirectionsRun, null); Spacer(Modifier.width(4.dp)); Text("Cardio")
        }
    }
    if (picking) ExercisePickerDialog(onDismiss = { picking = false }, onConfirm = { editor.addExercises(it); picking = false })
    if (pickingCardio) {
        ExercisePickerDialog(onDismiss = { pickingCardio = false }, mode = PickerMode.Cardio, single = true, onConfirm = {
            pickingCardio = false
            val phase = if (hasExercises) CardioPhase.postWorkout else CardioPhase.preWorkout
            setup = newCardioBlock(it.first(), phase, if (hasExercises) CardioRole.finisher else CardioRole.warmUp, distanceUnit)
        })
    }
    setup?.let { block ->
        CardioSetupDialog(block, onDismiss = { setup = null }, onSave = { editor.addCardio(it); setup = null })
    }
}

@Composable
private fun SupersetHeader(block: List<ExerciseEntry>, labels: Map<String, String>, editor: WorkoutEditor) {
    val first = block.first()
    val rest = first.supersetRestSeconds ?: first.restSeconds
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Link, null, tint = Wgj.colors.gold, modifier = Modifier.size(18.dp))
        Text(
            " Superset ${labels[first.id]?.take(1) ?: ""} · round rest ${formatRest(rest)}",
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = Wgj.colors.gold,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(onClick = { editor.setSupersetRest(first.supersetGroupId!!, rest - 15) }) { Text("−15s") }
        TextButton(onClick = { editor.setSupersetRest(first.supersetGroupId!!, rest + 15) }) { Text("+15s") }
    }
}

@Composable
private fun WorkoutProgressHeader(w: ActiveWorkout) {
    val total = w.exercises.sumOf { it.sets.size } + w.cardio.size
    val done = w.exercises.sumOf { e -> e.sets.count { it.completed } } + w.cardio.count { it.completed }
    SectionCard {
        Row {
            Text("$done of $total done", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(plural(w.exercises.size, "exercise"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done / total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = Wgj.colors.success,
        )
    }
}

@Composable
private fun ExerciseCard(exercise: ExerciseEntry, supersetLabel: String?, all: List<ExerciseEntry>, editor: WorkoutEditor) {
    val repo = LocalContext.current.app.repository
    val previous by produceState(emptyList<SetFactEntity>(), exercise.exerciseUuid) {
        value = repo.lastSessionSets(exercise.exerciseUuid)
    }
    val previousWarmups = previous.filter { it.isWarmup }
    val previousWorking = previous.filter { !it.isWarmup }
    var replacing by remember { mutableStateOf(false) }
    var editingNotes by remember { mutableStateOf(false) }

    SectionCard {
        ExerciseCardHeader(
            exercise,
            badge = supersetLabel,
            onMove = { editor.moveExercise(exercise.id, it) },
            onRemove = { editor.removeExercise(exercise.id) },
        ) { dismiss ->
            exercise.components.filter { it.exerciseUuid != exercise.exerciseUuid }.forEach { alt ->
                DropdownMenuItem(text = { Text("Switch to ${alt.name}") }, onClick = { dismiss(); editor.replaceExercise(exercise.id, alt) })
            }
            DropdownMenuItem(text = { Text("Replace exercise") }, onClick = { dismiss(); replacing = true })
            DropdownMenuItem(text = { Text(if (exercise.notes.isBlank()) "Add notes" else "Edit notes") }, onClick = { dismiss(); editingNotes = true })
            if (exercise.supersetGroupId != null) {
                DropdownMenuItem(text = { Text("Remove from superset") }, onClick = { dismiss(); editor.unlinkSuperset(exercise.id) })
            } else if (WorkoutEditing.canSupersetWithNext(all, exercise.id)) {
                DropdownMenuItem(text = { Text("Superset with next") }, onClick = { dismiss(); editor.supersetWithNext(exercise.id) })
            }
        }
        if (exercise.notes.isNotBlank()) {
            Text(exercise.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val repRange = when {
            exercise.targetRepMin != null && exercise.targetRepMax != null && exercise.targetRepMin != exercise.targetRepMax ->
                "Target ${exercise.targetRepMin}-${exercise.targetRepMax} reps"
            exercise.targetRepMin != null -> "Target ${exercise.targetRepMin} reps"
            else -> null
        }
        if (exercise.supersetGroupId == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    " Rest ${formatRest(exercise.restSeconds)}" + (repRange?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editor.updateExercise(exercise.id) { it.copy(restSeconds = (it.restSeconds - 15).coerceAtLeast(0)) } }) { Text("−15s") }
                TextButton(onClick = { editor.updateExercise(exercise.id) { it.copy(restSeconds = it.restSeconds + 15) } }) { Text("+15s") }
            }
        } else if (repRange != null) {
            Text(repRange, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        var warmupIndex = 0
        var workingIndex = 0
        exercise.sets.forEach { set ->
            val prev = if (set.isWarmup) previousWarmups.getOrNull(warmupIndex++) else previousWorking.getOrNull(workingIndex++)
            val label = if (set.isWarmup) "W" else workingIndex.toString()
            SetRow(exercise, set, label, prev, editor)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { editor.addSet(exercise.id) }, Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text("Add Set") }
            TextButton(onClick = { editor.addSet(exercise.id, warmup = true) }) { Text("+ Warm-up") }
        }
    }

    if (replacing) {
        ExercisePickerDialog(onDismiss = { replacing = false }, single = true, title = "Replace ${exercise.name}", onConfirm = {
            editor.replaceExercise(exercise.id, it.first().toRef())
            replacing = false
        })
    }
    if (editingNotes) {
        NotesDialog(exercise.notes, onDismiss = { editingNotes = false }) { notes ->
            editor.updateExercise(exercise.id) { it.copy(notes = notes) }
            editingNotes = false
        }
    }
}

@Composable
fun NotesDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exercise notes") },
        text = { OutlinedTextField(text, { text = it }, minLines = 3, placeholder = { Text("Seat height, grip, cues…") }) },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SetRow(
    exercise: ExerciseEntry,
    set: SetEntry,
    label: String,
    previous: SetFactEntity?,
    editor: WorkoutEditor,
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
                    onToggleWarmup = { editor.updateSet(exercise.id, set.id) { it.copy(isWarmup = !it.isWarmup, dropStages = emptyList()) } },
                    onUnit = { unit -> editor.updateSet(exercise.id, set.id) { it.copy(unit = unit) } },
                    onDelete = { editor.removeSet(exercise.id, set.id) },
                    onAddDrop = if (set.isWarmup) null else ({ editor.addDrop(exercise.id, set.id) }),
                )
                NumberField(
                    value = set.weight,
                    onValueChange = { v -> editor.updateSet(exercise.id, set.id) { it.copy(weight = v) } },
                    modifier = Modifier.weight(1f),
                    placeholder = if (set.unit == LoadUnit.bodyweight) "+0" else set.targetWeight?.let(::formatNumber) ?: "0",
                    suffix = set.unit.label,
                )
                NumberField(
                    value = set.reps?.toDouble(),
                    onValueChange = { v -> editor.updateSet(exercise.id, set.id) { it.copy(reps = v?.toInt()) } },
                    modifier = Modifier.weight(0.75f),
                    placeholder = set.targetReps?.toString() ?: "reps",
                    integer = true,
                )
                FilledIconToggleButton(
                    checked = set.completed,
                    onCheckedChange = {
                        if (editor.toggleSetCompleted(exercise.id, set.id, previous)) {
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
            set.dropStages.forEach { drop -> DropRow(exercise, set, drop, editor) }
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
                            editor.updateSet(exercise.id, set.id) { it.copy(weight = previous.weight, reps = previous.reps, unit = previous.unit) }
                        }) { Text("Fill Last") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropRow(exercise: ExerciseEntry, set: SetEntry, drop: DropStage, editor: WorkoutEditor) {
    val context = LocalContext.current
    Row(
        Modifier.padding(start = 12.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.SubdirectoryArrowRight, "Drop stage", Modifier.size(20.dp), tint = Wgj.colors.warning)
        NumberField(
            drop.weight, { v -> editor.updateDrop(exercise.id, set.id, drop.id) { it.copy(weight = v) } },
            Modifier.weight(1f), placeholder = drop.targetWeight?.let(::formatNumber) ?: "0", suffix = drop.unit.label,
        )
        NumberField(
            drop.reps?.toDouble(), { v -> editor.updateDrop(exercise.id, set.id, drop.id) { it.copy(reps = v?.toInt()) } },
            Modifier.weight(0.75f), placeholder = drop.targetReps?.toString() ?: "reps", integer = true,
        )
        IconButton(onClick = { editor.removeDrop(exercise.id, set.id, drop.id) }, Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, "Remove drop stage", Modifier.size(18.dp))
        }
        FilledIconToggleButton(
            checked = drop.completed,
            onCheckedChange = {
                if (!editor.toggleDropCompleted(exercise.id, set.id, drop.id)) Toast.makeText(context, "Enter reps first", Toast.LENGTH_SHORT).show()
            },
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                checkedContainerColor = Wgj.colors.warning,
                checkedContentColor = MaterialTheme.colorScheme.surface,
            ),
        ) { Icon(Icons.Default.Check, if (drop.completed) "Mark drop incomplete" else "Complete drop") }
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
