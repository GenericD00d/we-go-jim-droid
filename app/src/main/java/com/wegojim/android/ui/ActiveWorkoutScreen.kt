package com.wegojim.android.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrackChanges
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
import androidx.compose.runtime.MutableState
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
import com.wegojim.android.data.CardioGoal
import com.wegojim.android.data.CardioRecording
import com.wegojim.android.data.CardioPhase
import com.wegojim.android.data.CardioRole
import com.wegojim.android.data.DropStage
import com.wegojim.android.data.ExerciseEntry
import com.wegojim.android.data.LoadUnit
import com.wegojim.android.data.SetEntry
import com.wegojim.android.data.SetFactEntity
import com.wegojim.android.data.SupersetProgression
import com.wegojim.android.data.TemplateSyncPreview
import com.wegojim.android.data.TrainingGuidance
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

    // A session that is just one walk, run or ride is the recording screen itself.
    CardioRecording.standaloneActivity(w)?.let { activity ->
        CardioRecordingScreen(
            nav,
            activity.id,
            embedded = true,
            onCancelWorkout = { store.discard() },
            onSaveWorkout = {
                scope.launch {
                    val id = store.finish()
                    if (id != null) nav.navigate(Routes.workoutDetail(id, summary = true)) { popUpTo(Routes.START) }
                }
            },
        )
        return
    }

    val elapsed = rememberElapsedSeconds(w.startedAt)
    val scrollTarget = remember { mutableStateOf<String?>(null) }

    fun finishNow(syncTemplate: Boolean) {
        scope.launch {
            val id = store.finish(syncTemplate)
            if (id != null) nav.navigate(Routes.workoutDetail(id, summary = true)) { popUpTo(Routes.START) }
        }
    }

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
                        // Cardio-only sessions have nothing left to confirm once their results are in.
                        if (w.exercises.isEmpty() && w.cardio.isNotEmpty() && w.cardio.all { it.completed }) finishNow(false)
                        else scope.launch {
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
            workoutEditorItems(w, store, onOpenCardio = { nav.navigate(Routes.cardio(it)) }, scrollTarget = scrollTarget)
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
                finishNow(syncTemplate)
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
fun LazyListScope.workoutEditorItems(
    w: ActiveWorkout,
    editor: WorkoutEditor,
    onOpenCardio: ((String) -> Unit)? = null,
    scrollTarget: MutableState<String?>? = null,
) {
    val labels = WorkoutEditing.supersetLabels(w.exercises)
    val blocks = WorkoutEditing.blocks(w.exercises)
    val cardioCard: @Composable (CardioBlock) -> Unit = { block ->
        // Walks, runs and GPS activities record on their own screen in a live session.
        if (onOpenCardio != null && editor.isLive && CardioRecording.usesSessionScreen(block)) {
            CardioSessionCard(block, editor) { onOpenCardio(block.id) }
        } else ActiveCardioCard(block, editor)
    }
    items(w.cardio.filter { it.phase == CardioPhase.preWorkout }, key = { it.id }) { cardioCard(it) }
    if (w.exercises.isEmpty() && w.cardio.isEmpty()) {
        item { EmptyState(Icons.Default.Add, "No exercises yet", "Add exercises or cardio to start logging.") }
    }
    blocks.forEach { block ->
        if (block.size > 1) {
            item(key = "superset-" + block.first().supersetGroupId) { SupersetHeader(block, labels, editor, w.restEndsAt != null, scrollTarget) }
        }
        items(block, key = { it.id }) { exercise ->
            ExerciseCard(exercise, labels[exercise.id], w.exercises, editor, w.restEndsAt != null, scrollTarget)
        }
    }
    items(w.cardio.filter { it.phase == CardioPhase.postWorkout }, key = { it.id }) { cardioCard(it) }
    item { AddButtons(editor, hasExercises = w.exercises.isNotEmpty(), onOpenCardio = onOpenCardio) }
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
private fun AddButtons(editor: WorkoutEditor, hasExercises: Boolean, onOpenCardio: ((String) -> Unit)?) {
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
        CardioQuickPicker(onDismiss = { pickingCardio = false }) { exercise ->
            pickingCardio = false
            val phase = if (hasExercises) CardioPhase.postWorkout else CardioPhase.preWorkout
            val block = newCardioBlock(exercise, phase, if (hasExercises) CardioRole.finisher else CardioRole.warmUp, distanceUnit)
            if (onOpenCardio != null && editor.isLive && CardioRecording.usesSessionScreen(block)) {
                // Walks and runs go straight to recording; a goal can be set from the card later.
                val open = block.copy(goal = CardioGoal.openEnded, targetDurationSeconds = 0)
                editor.addCardio(open)
                onOpenCardio(open.id)
            } else setup = block
        }
    }
    setup?.let { block ->
        CardioSetupDialog(block, onDismiss = { setup = null }, onSave = { editor.addCardio(it); setup = null })
    }
}

@Composable
private fun SupersetHeader(
    block: List<ExerciseEntry>,
    labels: Map<String, String>,
    editor: WorkoutEditor,
    resting: Boolean,
    scrollTarget: MutableState<String?>?,
) {
    val first = block.first()
    val rest = first.supersetRestSeconds ?: first.restSeconds
    Column(Modifier.padding(top = 4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
    // Port of the iOS round guidance: what to do next, tap to jump there.
    if (editor.isLive && block.size == 2) {
        val next = SupersetProgression.nextStep(block[0], block[1], labels)
        if (next == null) {
            Text("Superset complete", color = Wgj.colors.cyan, fontWeight = FontWeight.SemiBold)
        } else Surface(onClick = { scrollTarget?.value = next.setId }, color = Color.Transparent) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${if (resting) "Rest, then" else "Go to"} ${next.label}", color = Wgj.colors.cyan, fontWeight = FontWeight.SemiBold)
                    Text(block.first { it.id == next.exerciseId }.name, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.ArrowCircleDown, "Show next set", tint = Wgj.colors.cyan)
            }
        }
    }
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
private fun ExerciseCard(
    exercise: ExerciseEntry,
    supersetLabel: String?,
    all: List<ExerciseEntry>,
    editor: WorkoutEditor,
    resting: Boolean,
    scrollTarget: MutableState<String?>?,
) {
    val repo = LocalContext.current.app.repository
    val previous by produceState(emptyList<SetFactEntity>(), exercise.exerciseUuid) {
        value = repo.lastSessionSets(exercise.exerciseUuid)
    }
    val previousWarmups = previous.filter { it.isWarmup }
    val previousWorking = previous.filter { !it.isWarmup }
    var replacing by remember { mutableStateOf(false) }
    var editingNotes by remember { mutableStateOf(false) }
    val guidanceEnabled = LocalContext.current.app.settings.state.collectAsStateWithLifecycle().value.trainingGuidance

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
        if (guidanceEnabled && editor.isLive) GuidanceBanner(exercise)
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

        // In a superset, point at the set to do next (port of WorkoutSupersetSetCue).
        val next = SupersetProgression.pair(exercise, all)?.takeIf { editor.isLive }?.let { (a, b) ->
            SupersetProgression.nextStep(a, b, WorkoutEditing.supersetLabels(all))
        }?.takeIf { it.exerciseId == exercise.id }
        var warmupIndex = 0
        var workingIndex = 0
        exercise.sets.forEach { set ->
            val prev = if (set.isWarmup) previousWarmups.getOrNull(warmupIndex++) else previousWorking.getOrNull(workingIndex++)
            val label = if (set.isWarmup) "W" else workingIndex.toString()
            val cue = next?.takeIf { it.setId == set.id }?.let { "${if (resting) "After rest:" else "Do now:"} ${it.label} · ${exercise.name}" }
            SetRow(exercise, set, label, prev, editor, cue, scrollTarget)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetRow(
    exercise: ExerciseEntry,
    set: SetEntry,
    label: String,
    previous: SetFactEntity?,
    editor: WorkoutEditor,
    cue: String? = null,
    scrollTarget: MutableState<String?>? = null,
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val background by animateColorAsState(
        if (set.completed) Wgj.colors.success.copy(alpha = 0.14f) else Color.Transparent,
        label = "setBackground",
    )
    val bringIntoView = remember { BringIntoViewRequester() }
    val requested = scrollTarget?.value
    LaunchedEffect(requested) {
        if (requested == set.id) {
            bringIntoView.bringIntoView()
            scrollTarget.value = null
        }
    }
    // After checking off a superset set, move on to whichever set comes next.
    fun advanceSuperset() {
        val all = editor.state.value?.exercises ?: return
        val (a, b) = SupersetProgression.pair(exercise, all) ?: return
        SupersetProgression.nextStep(a, b, WorkoutEditing.supersetLabels(all))?.let { scrollTarget?.value = it.setId }
    }
    Surface(color = background, shape = MaterialTheme.shapes.medium, modifier = Modifier.bringIntoViewRequester(bringIntoView)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            if (cue != null) {
                Row(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowCircleRight, null, Modifier.size(16.dp), tint = Wgj.colors.cyan)
                    Text(" $cue", style = MaterialTheme.typography.labelLarge, color = Wgj.colors.cyan, fontWeight = FontWeight.SemiBold)
                }
            }
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
                    max = MAX_WEIGHT,
                )
                NumberField(
                    value = set.reps?.toDouble(),
                    onValueChange = { v -> editor.updateSet(exercise.id, set.id) { it.copy(reps = v?.toInt()) } },
                    modifier = Modifier.weight(0.75f),
                    placeholder = set.targetReps?.toString() ?: "reps",
                    integer = true,
                    max = MAX_REPS,
                )
                FilledIconToggleButton(
                    checked = set.completed,
                    onCheckedChange = {
                        if (editor.toggleSetCompleted(exercise.id, set.id, previous)) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (editor.isLive && !set.completed) advanceSuperset()
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
            set.dropStages.forEachIndexed { i, drop -> DropRow(exercise, set, drop, i, editor) }
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
private fun DropRow(exercise: ExerciseEntry, set: SetEntry, drop: DropStage, index: Int, editor: WorkoutEditor) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.padding(start = 12.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.SubdirectoryArrowRight, "Drop stage", Modifier.size(20.dp), tint = Wgj.colors.warning)
        NumberField(
            drop.weight, { v -> editor.updateDrop(exercise.id, set.id, drop.id) { it.copy(weight = v) } },
            Modifier.weight(1f), placeholder = drop.targetWeight?.let(::formatNumber) ?: "0", suffix = drop.unit.label, max = MAX_WEIGHT,
        )
        NumberField(
            drop.reps?.toDouble(), { v -> editor.updateDrop(exercise.id, set.id, drop.id) { it.copy(reps = v?.toInt()) } },
            Modifier.weight(0.75f), placeholder = drop.targetReps?.toString() ?: "reps", integer = true, max = MAX_REPS,
        )
        Box {
            IconButton(onClick = { menu = true }, Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, "Drop ${index + 1} options", Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Delete drop", color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; editor.removeDrop(exercise.id, set.id, drop.id) },
                )
            }
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
                    Text(workout.restLabel ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

/** Upper bounds for typed loads and reps, so a slip of the finger can't skew records. */
const val MAX_WEIGHT = 5000.0
const val MAX_REPS = 999.0

@Composable
fun PrBadge() {
    Surface(color = Wgj.colors.gold.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.EmojiEvents, null, Modifier.size(14.dp), tint = Wgj.colors.gold)
            Text(" PR", style = MaterialTheme.typography.labelSmall, color = Wgj.colors.gold, fontWeight = FontWeight.Bold)
        }
    }
}

/** Collapsible coaching line from [TrainingGuidance] (port of TrainingGuidanceBannerView). */
@Composable
private fun GuidanceBanner(exercise: ExerciseEntry) {
    val repo = LocalContext.current.app.repository
    val catalog by repo.exercise(exercise.exerciseUuid).collectAsStateWithLifecycle(null)
    val guidance = TrainingGuidance.forExercise(exercise, catalog)
    var expanded by remember { mutableStateOf(false) }
    val tint = when (guidance.tone) {
        TrainingGuidance.Tone.Success -> Wgj.colors.success
        TrainingGuidance.Tone.Caution -> Wgj.colors.warning
        TrainingGuidance.Tone.Accent -> MaterialTheme.colorScheme.secondary
    }
    Surface(onClick = { expanded = !expanded }, color = tint.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TrackChanges, null, Modifier.size(18.dp), tint = tint)
                Text("  " + guidance.title, Modifier.weight(1f), color = tint, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                guidance.badgeDetail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = tint) }
            }
            if (expanded) Text(guidance.summary, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}
