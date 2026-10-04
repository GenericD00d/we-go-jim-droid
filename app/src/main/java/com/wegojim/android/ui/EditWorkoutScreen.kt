package com.wegojim.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.wegojim.android.app
import com.wegojim.android.data.ActiveWorkout
import com.wegojim.android.data.LoadUnit
import com.wegojim.android.data.Repository
import com.wegojim.android.data.SettingsStore
import com.wegojim.android.data.WorkoutEditor
import com.wegojim.android.data.WorkoutEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Edits a completed workout in memory; saving recomputes its facts and PRs. */
class EditWorkoutViewModel(
    private val repo: Repository,
    private val settings: SettingsStore,
    workoutId: String,
) : ViewModel(), WorkoutEditor {
    private val _state = MutableStateFlow<ActiveWorkout?>(null)
    override val state: StateFlow<ActiveWorkout?> = _state
    override val defaultUnit: LoadUnit get() = settings.state.value.weightUnit
    override val defaultRestSeconds: Int get() = settings.state.value.defaultRestSeconds
    override val isLive = false

    private var original: WorkoutEntity? = null
    private var loaded: ActiveWorkout? = null

    init {
        viewModelScope.launch {
            val workout = repo.workout(workoutId).first() ?: return@launch
            original = workout
            loaded = ActiveWorkout(
                id = workout.id,
                templateId = workout.templateId,
                name = workout.name,
                startedAt = workout.startedAt,
                notes = workout.notes,
                exercises = workout.exercises,
                cardio = workout.cardio,
            )
            _state.value = loaded
        }
    }

    val isDirty: Boolean get() = _state.value != loaded

    override fun update(transform: (ActiveWorkout) -> ActiveWorkout) = _state.update { it?.let(transform) }

    suspend fun save() {
        val workout = original ?: return
        val edited = _state.value ?: return
        repo.updateCompletedWorkout(workout, edited)
        loaded = edited
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkoutScreen(nav: NavHostController, workoutId: String) {
    val app = LocalContext.current.app
    val vm: EditWorkoutViewModel = viewModel(key = "edit-$workoutId") { EditWorkoutViewModel(app.repository, app.settings, workoutId) }
    val workout by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmDiscard by remember { mutableStateOf(false) }
    val leave = { if (vm.isDirty) confirmDiscard = true else nav.popBackStack() }
    BackHandler(onBack = { leave() })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Workout") },
                navigationIcon = { IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = { scope.launch { vm.save(); nav.popBackStack() } }) { Text("Save", fontWeight = FontWeight.SemiBold) }
                },
            )
        },
    ) { padding ->
        val w = workout ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Only checked sets are kept when you save. Uncheck a set to remove it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            workoutEditorItems(w, vm)
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this workout haven't been saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; nav.popBackStack() }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep Editing") } },
        )
    }
}
