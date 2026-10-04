package com.wegojim.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.wegojim.android.app
import com.wegojim.android.data.CardioBlock
import com.wegojim.android.data.CardioPhase
import com.wegojim.android.data.CardioRole
import com.wegojim.android.data.DropStage
import com.wegojim.android.data.ExerciseEntity
import com.wegojim.android.data.ExerciseEntry
import com.wegojim.android.data.LoadUnit
import com.wegojim.android.data.Repository
import com.wegojim.android.data.SetEntry
import com.wegojim.android.data.SettingsStore
import com.wegojim.android.data.TemplateEntity
import com.wegojim.android.data.TrainingGuidance
import com.wegojim.android.data.WorkoutEditing
import com.wegojim.android.data.newCardioBlock
import com.wegojim.android.data.newExerciseEntry
import com.wegojim.android.data.toRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TemplateEditorViewModel(
    private val repo: Repository,
    private val settings: SettingsStore,
    templateId: String,
    folderId: String?,
) : ViewModel() {
    val state = MutableStateFlow<TemplateEntity?>(null)
    private var original: TemplateEntity? = null
    val isNew = templateId == "new"

    init {
        viewModelScope.launch {
            val template = (if (isNew) null else repo.template(templateId)) ?: TemplateEntity(folderId = folderId)
            original = template
            state.value = template
        }
    }

    val isDirty: Boolean get() = state.value != original

    fun update(transform: (TemplateEntity) -> TemplateEntity) = state.update { it?.let(transform) }

    fun exercises(f: (List<ExerciseEntry>) -> List<ExerciseEntry>) = update { it.copy(exercises = f(it.exercises)) }

    fun updateExercise(id: String, f: (ExerciseEntry) -> ExerciseEntry) = exercises { WorkoutEditing.updateExercise(it, id, f) }

    fun updateSet(exerciseId: String, setId: String, f: (SetEntry) -> SetEntry) = exercises { WorkoutEditing.updateSet(it, exerciseId, setId, f) }

    fun updateDrop(exerciseId: String, setId: String, dropId: String, f: (DropStage) -> DropStage) =
        exercises { WorkoutEditing.updateDrop(it, exerciseId, setId, dropId, f) }

    fun addExercises(items: List<ExerciseEntity>) = exercises { list ->
        list + items.map { newExerciseEntry(it, settings.state.value.weightUnit, settings.state.value.defaultRestSeconds) }
    }

    fun addSet(exerciseId: String, warmup: Boolean) = exercises { WorkoutEditing.addSet(it, exerciseId, warmup, settings.state.value.weightUnit) }

    fun updateCardio(id: String, f: (CardioBlock) -> CardioBlock) = update { t -> t.copy(cardio = t.cardio.map { if (it.id == id) f(it) else it }) }

    suspend fun save() {
        val template = state.value ?: return
        repo.saveTemplate(template.copy(name = template.name.trim()))
        original = state.value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(nav: NavHostController, templateId: String, folderId: String? = null) {
    val app = LocalContext.current.app
    val vm: TemplateEditorViewModel = viewModel(key = "template-$templateId") {
        TemplateEditorViewModel(app.repository, app.settings, templateId, folderId)
    }
    val template by vm.state.collectAsStateWithLifecycle()
    val folders by app.repository.folders.collectAsStateWithLifecycle(emptyList())
    val distanceUnit = app.settings.state.collectAsStateWithLifecycle().value.distanceUnit
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var pickingCardio by remember { mutableStateOf(false) }
    var cardioSetup by remember { mutableStateOf<CardioBlock?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val leave = { if (vm.isDirty) confirmDiscard = true else nav.popBackStack() }
    BackHandler(onBack = { leave() })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New Template" else "Edit Template") },
                navigationIcon = { IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(
                        enabled = !template?.name.isNullOrBlank(),
                        onClick = { scope.launch { vm.save(); nav.popBackStack() } },
                    ) { Text("Save", fontWeight = FontWeight.SemiBold) }
                },
            )
        },
    ) { padding ->
        val t = template ?: return@Scaffold
        val labels = WorkoutEditing.supersetLabels(t.exercises)
        LazyColumn(
            modifier = Modifier.imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    OutlinedTextField(
                        value = t.name,
                        onValueChange = { v -> vm.update { it.copy(name = v) } },
                        label = { Text("Template name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = t.notes,
                        onValueChange = { v -> vm.update { it.copy(notes = v) } },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (folders.isNotEmpty()) {
                        val options = listOf("No folder") + folders.map { it.name }
                        Dropdown("Folder", folders.firstOrNull { it.id == t.folderId }?.name ?: "No folder", options) { name ->
                            vm.update { it.copy(folderId = folders.firstOrNull { f -> f.name == name }?.id) }
                        }
                    }
                }
            }
            items(t.cardio.filter { it.phase == CardioPhase.preWorkout }, key = { it.id }) { block ->
                CardioPlanCard(block, onChange = { c -> vm.updateCardio(block.id) { c } }, onRemove = { vm.update { it.copy(cardio = it.cardio.filterNot { c -> c.id == block.id }) } })
            }
            WorkoutEditing.blocks(t.exercises).forEach { block ->
                if (block.size > 1) {
                    item(key = "superset-" + block.first().supersetGroupId) {
                        val first = block.first()
                        val rest = first.supersetRestSeconds ?: first.restSeconds
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, null, tint = Wgj.colors.gold, modifier = Modifier.size(18.dp))
                            Text(" Superset · round rest ${formatRest(rest)}", Modifier.weight(1f), color = Wgj.colors.gold, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { vm.exercises { WorkoutEditing.setSupersetRest(it, first.supersetGroupId!!, rest - 15) } }) { Text("−15s") }
                            TextButton(onClick = { vm.exercises { WorkoutEditing.setSupersetRest(it, first.supersetGroupId!!, rest + 15) } }) { Text("+15s") }
                        }
                    }
                }
                items(block, key = { it.id }) { exercise ->
                    TemplateExerciseCard(exercise, labels[exercise.id], t.exercises, vm)
                }
            }
            items(t.cardio.filter { it.phase == CardioPhase.postWorkout }, key = { it.id }) { block ->
                CardioPlanCard(block, onChange = { c -> vm.updateCardio(block.id) { c } }, onRemove = { vm.update { it.copy(cardio = it.cardio.filterNot { c -> c.id == block.id }) } })
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picking = true }, Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Exercises")
                    }
                    OutlinedButton(onClick = { pickingCardio = true }, Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsRun, null); Spacer(Modifier.width(4.dp)); Text("Cardio")
                    }
                }
            }
        }
    }

    if (picking) {
        ExercisePickerDialog(onDismiss = { picking = false }, onConfirm = { vm.addExercises(it); picking = false })
    }
    if (pickingCardio) {
        ExercisePickerDialog(onDismiss = { pickingCardio = false }, mode = PickerMode.Cardio, single = true, onConfirm = {
            pickingCardio = false
            val hasExercises = template?.exercises?.isNotEmpty() == true
            cardioSetup = newCardioBlock(
                it.first(),
                if (hasExercises) CardioPhase.postWorkout else CardioPhase.preWorkout,
                if (hasExercises) CardioRole.finisher else CardioRole.warmUp,
                distanceUnit,
            )
        })
    }
    cardioSetup?.let { block ->
        CardioSetupDialog(block, onDismiss = { cardioSetup = null }, onSave = { c ->
            vm.update { it.copy(cardio = it.cardio + c) }
            cardioSetup = null
        })
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this template haven't been saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; nav.popBackStack() }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep Editing") } },
        )
    }
}

@Composable
private fun TemplateExerciseCard(exercise: ExerciseEntry, supersetLabel: String?, all: List<ExerciseEntry>, vm: TemplateEditorViewModel) {
    var addingAlternative by remember { mutableStateOf(false) }
    val guidanceEnabled = LocalContext.current.app.settings.state.collectAsStateWithLifecycle().value.trainingGuidance
    var replacing by remember { mutableStateOf(false) }
    var editingNotes by remember { mutableStateOf(false) }
    SectionCard {
        ExerciseCardHeader(
            exercise,
            badge = supersetLabel,
            onMove = { delta -> vm.exercises { WorkoutEditing.move(it, exercise.id, delta) } },
            onRemove = { vm.exercises { WorkoutEditing.remove(it, exercise.id) } },
        ) { dismiss ->
            DropdownMenuItem(text = { Text("Replace exercise") }, onClick = { dismiss(); replacing = true })
            DropdownMenuItem(text = { Text("Add rotating alternative") }, onClick = { dismiss(); addingAlternative = true })
            DropdownMenuItem(text = { Text(if (exercise.notes.isBlank()) "Add notes" else "Edit notes") }, onClick = { dismiss(); editingNotes = true })
            if (exercise.supersetGroupId != null) {
                DropdownMenuItem(text = { Text("Remove from superset") }, onClick = { dismiss(); vm.exercises { WorkoutEditing.unlinkSuperset(it, exercise.id) } })
            } else if (WorkoutEditing.canSupersetWithNext(all, exercise.id)) {
                DropdownMenuItem(text = { Text("Superset with next") }, onClick = { dismiss(); vm.exercises { WorkoutEditing.supersetWithNext(it, exercise.id) } })
            }
        }
        if (exercise.notes.isNotBlank()) {
            Text(exercise.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (guidanceEnabled) {
            val catalog by LocalContext.current.app.repository.exercise(exercise.exerciseUuid).collectAsStateWithLifecycle(null)
            val rec = TrainingGuidance.recommendation(TrainingGuidance.classify(exercise, catalog))
            Text(
                "${rec.title}: ${rec.summary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (exercise.components.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sync, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Text(" Rotates each session with:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            exercise.components.forEach { alt ->
                InputChip(
                    selected = false,
                    onClick = { vm.updateExercise(exercise.id) { e -> e.copy(components = e.components - alt) } },
                    label = { Text(alt.name) },
                    trailingIcon = { Icon(Icons.Default.Close, "Remove alternative", Modifier.size(16.dp)) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Rep range", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            NumberField(exercise.targetRepMin?.toDouble(), { v -> vm.updateExercise(exercise.id) { it.copy(targetRepMin = v?.toInt()) } }, Modifier.width(72.dp), "min", integer = true)
            Text("–")
            NumberField(exercise.targetRepMax?.toDouble(), { v -> vm.updateExercise(exercise.id) { it.copy(targetRepMax = v?.toInt()) } }, Modifier.width(72.dp), "max", integer = true)
        }
        if (exercise.supersetGroupId == null) {
            Stepper(
                "Rest",
                formatRest(exercise.restSeconds),
                onDecrement = { vm.updateExercise(exercise.id) { it.copy(restSeconds = (it.restSeconds - 15).coerceAtLeast(0)) } },
                onIncrement = { vm.updateExercise(exercise.id) { it.copy(restSeconds = it.restSeconds + 15) } },
            )
        }
        var workingIndex = 0
        exercise.sets.forEach { set ->
            val label = if (set.isWarmup) "W" else (++workingIndex).toString()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SetBadge(
                    label = label,
                    set = set,
                    onToggleWarmup = { vm.updateSet(exercise.id, set.id) { it.copy(isWarmup = !it.isWarmup, dropStages = emptyList()) } },
                    onUnit = { unit -> vm.updateSet(exercise.id, set.id) { it.copy(unit = unit) } },
                    onDelete = { vm.exercises { WorkoutEditing.removeSet(it, exercise.id, set.id) } },
                    onAddDrop = if (set.isWarmup) null else ({ vm.exercises { WorkoutEditing.addDrop(it, exercise.id, set.id) } }),
                )
                NumberField(
                    set.targetWeight,
                    { v -> vm.updateSet(exercise.id, set.id) { it.copy(targetWeight = v) } },
                    Modifier.weight(1f),
                    placeholder = if (set.unit == LoadUnit.bodyweight) "+0" else "Weight",
                    suffix = set.unit.label,
                )
                NumberField(
                    set.targetReps?.toDouble(),
                    { v -> vm.updateSet(exercise.id, set.id) { it.copy(targetReps = v?.toInt()) } },
                    Modifier.weight(0.8f),
                    placeholder = "Reps",
                    integer = true,
                )
            }
            set.dropStages.forEach { drop ->
                Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SubdirectoryArrowRight, "Drop stage", Modifier.size(20.dp), tint = Wgj.colors.warning)
                    NumberField(drop.targetWeight, { v -> vm.updateDrop(exercise.id, set.id, drop.id) { it.copy(targetWeight = v) } }, Modifier.weight(1f), "Weight", suffix = drop.unit.label)
                    NumberField(drop.targetReps?.toDouble(), { v -> vm.updateDrop(exercise.id, set.id, drop.id) { it.copy(targetReps = v?.toInt()) } }, Modifier.weight(0.8f), "Reps", integer = true)
                    IconButton(onClick = { vm.exercises { WorkoutEditing.removeDrop(it, exercise.id, set.id, drop.id) } }, Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Remove drop stage", Modifier.size(18.dp))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.addSet(exercise.id, warmup = false) }) { Icon(Icons.Default.Add, null); Text("Set") }
            TextButton(onClick = { vm.addSet(exercise.id, warmup = true) }) { Icon(Icons.Default.Add, null); Text("Warm-up") }
        }
    }

    if (addingAlternative) {
        ExercisePickerDialog(onDismiss = { addingAlternative = false }, single = true, title = "Alternative for ${exercise.name}", onConfirm = { picked ->
            val ref = picked.first().toRef()
            if (ref.exerciseUuid != exercise.exerciseUuid) {
                vm.updateExercise(exercise.id) { e -> e.copy(components = (e.components + ref).distinctBy { it.exerciseUuid }) }
            }
            addingAlternative = false
        })
    }
    if (replacing) {
        ExercisePickerDialog(onDismiss = { replacing = false }, single = true, title = "Replace ${exercise.name}", onConfirm = {
            vm.exercises { list -> WorkoutEditing.replaceExercise(list, exercise.id, it.first().toRef()) }
            replacing = false
        })
    }
    if (editingNotes) {
        NotesDialog(exercise.notes, onDismiss = { editingNotes = false }) { notes ->
            vm.updateExercise(exercise.id) { it.copy(notes = notes) }
            editingNotes = false
        }
    }
}

/** Exercise name, muscles and a move/remove menu. Shared by template and workout editors. */
@Composable
fun ExerciseCardHeader(
    exercise: ExerciseEntry,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    badge: String? = null,
    extraMenu: @Composable (dismiss: () -> Unit) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (badge != null) {
            Surface(color = Wgj.colors.gold.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) {
                Text(badge, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Wgj.colors.gold, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            if (exercise.muscleSummary.isNotBlank()) {
                Text(exercise.muscleSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Exercise actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                extraMenu { menu = false }
                DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(-1) })
                DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(1) })
                DropdownMenuItem(text = { Text("Remove exercise", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

/** Set number badge (or "W" for warm-ups) that opens the per-set menu. */
@Composable
fun SetBadge(
    label: String,
    set: SetEntry,
    onToggleWarmup: () -> Unit,
    onUnit: (LoadUnit) -> Unit,
    onDelete: () -> Unit,
    onAddDrop: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { menu = true },
            shape = MaterialTheme.shapes.small,
            color = if (set.isWarmup) Wgj.colors.gold.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(label, fontWeight = FontWeight.Bold, color = if (set.isWarmup) Wgj.colors.gold else Color.Unspecified)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (set.isWarmup) "Make working set" else "Make warm-up set") },
                onClick = { menu = false; onToggleWarmup() },
            )
            if (onAddDrop != null) {
                DropdownMenuItem(text = { Text("Add drop stage") }, onClick = { menu = false; onAddDrop() })
            }
            LoadUnit.entries.filter { it != set.unit }.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(if (unit == LoadUnit.bodyweight) "Use bodyweight" else "Use ${unit.label}") },
                    onClick = { menu = false; onUnit(unit) },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete set", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}
