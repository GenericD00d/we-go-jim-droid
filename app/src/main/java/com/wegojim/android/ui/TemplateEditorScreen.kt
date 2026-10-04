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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.wegojim.android.data.ExerciseEntity
import com.wegojim.android.data.ExerciseEntry
import com.wegojim.android.data.LoadUnit
import com.wegojim.android.data.Repository
import com.wegojim.android.data.SetEntry
import com.wegojim.android.data.SettingsStore
import com.wegojim.android.data.TemplateEntity
import com.wegojim.android.data.moved
import com.wegojim.android.data.newExerciseEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TemplateEditorViewModel(
    private val repo: Repository,
    private val settings: SettingsStore,
    templateId: String,
) : ViewModel() {
    val state = MutableStateFlow<TemplateEntity?>(null)
    private var original: TemplateEntity? = null
    val isNew = templateId == "new"

    init {
        viewModelScope.launch {
            val template = (if (isNew) null else repo.template(templateId)) ?: TemplateEntity()
            original = template
            state.value = template
        }
    }

    val isDirty: Boolean get() = state.value != original

    fun update(transform: (TemplateEntity) -> TemplateEntity) = state.update { it?.let(transform) }

    fun updateExercise(id: String, transform: (ExerciseEntry) -> ExerciseEntry) =
        update { t -> t.copy(exercises = t.exercises.map { if (it.id == id) transform(it) else it }) }

    fun updateSet(exerciseId: String, setId: String, transform: (SetEntry) -> SetEntry) =
        updateExercise(exerciseId) { e -> e.copy(sets = e.sets.map { if (it.id == setId) transform(it) else it }) }

    fun addExercises(items: List<ExerciseEntity>) =
        update { t -> t.copy(exercises = t.exercises + items.map { newExerciseEntry(it, settings.state.value) }) }

    fun addSet(exerciseId: String, warmup: Boolean) = updateExercise(exerciseId) { e ->
        val last = e.sets.lastOrNull { it.isWarmup == warmup } ?: e.sets.lastOrNull()
        val set = SetEntry(
            isWarmup = warmup,
            targetReps = last?.targetReps,
            targetWeight = last?.targetWeight,
            unit = last?.unit ?: settings.state.value.weightUnit,
        )
        val sets = if (warmup) e.sets.toMutableList().apply { add(e.sets.indexOfLast { it.isWarmup } + 1, set) } else e.sets + set
        e.copy(sets = sets)
    }

    suspend fun save() {
        val template = state.value ?: return
        repo.saveTemplate(template.copy(name = template.name.trim()))
        original = state.value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(nav: NavHostController, templateId: String) {
    val app = LocalContext.current.app
    val vm: TemplateEditorViewModel = viewModel(key = "template-$templateId") {
        TemplateEditorViewModel(app.repository, app.settings, templateId)
    }
    val template by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
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
                }
            }
            items(t.exercises, key = { it.id }) { exercise ->
                TemplateExerciseCard(
                    exercise = exercise,
                    vm = vm,
                    onMove = { delta -> vm.update { it.copy(exercises = it.exercises.moved(exercise.id, delta) { e -> e.id }) } },
                    onRemove = { vm.update { it.copy(exercises = it.exercises.filterNot { e -> e.id == exercise.id }) } },
                )
            }
            item {
                Button(onClick = { picking = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add Exercises")
                }
            }
        }
    }

    if (picking) {
        ExercisePickerDialog(onDismiss = { picking = false }, onConfirm = { vm.addExercises(it); picking = false })
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
private fun TemplateExerciseCard(
    exercise: ExerciseEntry,
    vm: TemplateEditorViewModel,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    SectionCard {
        ExerciseCardHeader(exercise, onMove = onMove, onRemove = onRemove)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Rep range", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            NumberField(exercise.targetRepMin?.toDouble(), { v -> vm.updateExercise(exercise.id) { it.copy(targetRepMin = v?.toInt()) } }, Modifier.width(72.dp), "min", integer = true)
            Text("–")
            NumberField(exercise.targetRepMax?.toDouble(), { v -> vm.updateExercise(exercise.id) { it.copy(targetRepMax = v?.toInt()) } }, Modifier.width(72.dp), "max", integer = true)
        }
        Stepper(
            "Rest",
            formatRest(exercise.restSeconds),
            onDecrement = { vm.updateExercise(exercise.id) { it.copy(restSeconds = (it.restSeconds - 15).coerceAtLeast(0)) } },
            onIncrement = { vm.updateExercise(exercise.id) { it.copy(restSeconds = it.restSeconds + 15) } },
        )
        var workingIndex = 0
        exercise.sets.forEach { set ->
            val label = if (set.isWarmup) "W" else (++workingIndex).toString()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SetBadge(
                    label = label,
                    set = set,
                    onToggleWarmup = { vm.updateSet(exercise.id, set.id) { it.copy(isWarmup = !it.isWarmup) } },
                    onUnit = { unit -> vm.updateSet(exercise.id, set.id) { it.copy(unit = unit) } },
                    onDelete = { vm.updateExercise(exercise.id) { e -> e.copy(sets = e.sets.filterNot { it.id == set.id }) } },
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
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.addSet(exercise.id, warmup = false) }) { Icon(Icons.Default.Add, null); Text("Set") }
            TextButton(onClick = { vm.addSet(exercise.id, warmup = true) }) { Icon(Icons.Default.Add, null); Text("Warm-up") }
        }
    }
}

/** Exercise name, muscles and a move/remove menu. Shared by template and workout editors. */
@Composable
fun ExerciseCardHeader(
    exercise: ExerciseEntry,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    extraMenu: @Composable (dismiss: () -> Unit) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
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
